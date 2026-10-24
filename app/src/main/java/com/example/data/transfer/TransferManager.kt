package com.example.data.transfer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.sync.VaultSyncManager
import com.example.data.remote.FileManifest
import com.example.data.remote.ManifestChunk
import com.example.data.remote.TelegramApiException
import com.example.data.remote.TelegramRepository
import com.example.domain.ChecksumUtil
import com.example.domain.CryptoStreamEngine
import com.example.domain.DownloadStorageManager
import com.example.domain.RollingSpeedEstimator
import com.example.domain.StorageUtil
import com.example.domain.ThumbnailUtil
import com.example.domain.model.TransferProgress
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * Core engine responsible for chunking, uploading, downloading, and reassembling files
 * using the Telegram Bot API as a secure personal cloud backend.
 *
 * CHUNKING ARCHITECTURE & FAULT TOLERANCE:
 * 1. Files are sliced into sequential binary parts (default 45MB) to remain safely within
 *    Telegram's 50MB Bot API payload threshold.
 * 2. Every chunk is individually hashed with SHA-256 before upload.
 *    The chunk's hash and index metadata are embedded into the Telegram document caption.
 * 3. As soon as a chunk upload completes, its Telegram message ID and remote file ID are
 *    persisted into the local Room database (SQLite). If network drops or the process dies,
 *    subsequent transfer attempts inspect Room and resume directly from the first incomplete chunk.
 * 4. Once all chunks are archived on Telegram, a final "File Manifest" JSON message is posted
 *    to the chat. This manifest links all chunk message IDs, original file size, MIME type,
 *    and overall SHA-256 file checksum, serving as the immutable cloud source of truth.
 * 5. Reassembly fetches all chunks in order, validates each chunk's SHA-256 hash in-flight,
 *    concatenates the bytes into the final file, and performs a final end-to-end checksum
 *    verification before revealing the file to the user.
 */
class TransferManager private constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val repository: TelegramRepository,
    private val credentialsManager: EncryptedCredentialsManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pauseRequestedFiles = ConcurrentHashMap.newKeySet<String>()
    private val cancelledFiles = ConcurrentHashMap.newKeySet<String>()

    fun isCancelled(fileId: String): Boolean = cancelledFiles.contains(fileId)
    fun isPaused(fileId: String): Boolean = pauseRequestedFiles.contains(fileId)

    // Sequential Upload Queue & Batch Indexing State
    private val uploadQueue = ConcurrentLinkedQueue<String>()
    private val isUploadingActive = AtomicBoolean(false)
    private val activeUploadFileId = AtomicReference<String?>(null)
    private val completedInCurrentBatch = AtomicInteger(0)

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val manifestAdapter = moshi.adapter(FileManifest::class.java)

    private val _transfers = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val transfers: StateFlow<Map<String, TransferProgress>> = _transfers.asStateFlow()

    private val _recentlyCompleted = MutableStateFlow<List<TransferProgress>>(emptyList())
    val recentlyCompleted: StateFlow<List<TransferProgress>> = _recentlyCompleted.asStateFlow()

    private val _transferErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val transferErrorEvents: SharedFlow<String> = _transferErrorEvents.asSharedFlow()

    private val _transferNotificationEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val transferNotificationEvents: SharedFlow<String> = _transferNotificationEvents.asSharedFlow()

    init {
        restorePersistedTransfers()
    }

    /**
     * Restores pending, paused, or interrupted transfers from Room on process initialization.
     */
    private fun restorePersistedTransfers() {
        scope.launch {
            try {
                val incompleteFiles = database.fileDao().getFilesByStatus(
                    listOf(
                        FileStatus.PENDING,
                        FileStatus.UPLOADING,
                        FileStatus.DOWNLOADING,
                        FileStatus.PAUSED,
                        FileStatus.FAILED
                    )
                )
                val restoredMap = mutableMapOf<String, TransferProgress>()
                for (file in incompleteFiles) {
                    val chunks = database.chunkDao().getChunksForFile(file.id)
                    val isUpload = file.manifestMessageId == null && (chunks.isEmpty() || chunks.any { !it.isUploaded })
                    val downloadTempDir = File(context.cacheDir, "downloads_temp/${file.id}")
                    val completedChunks = if (isUpload) {
                        chunks.count { it.isUploaded }
                    } else {
                        chunks.count { c ->
                            val partFile = File(downloadTempDir, "chunk_${c.chunkIndex}.part")
                            c.isDownloaded && partFile.exists() && partFile.length() == c.size
                        }
                    }
                    val bytesTransferred = if (isUpload) {
                        chunks.filter { it.isUploaded }.sumOf { it.size }
                    } else {
                        chunks.sumOf { c ->
                            val partFile = File(downloadTempDir, "chunk_${c.chunkIndex}.part")
                            when {
                                partFile.exists() && partFile.length() == c.size -> c.size
                                partFile.exists() && partFile.length() in 1 until c.size -> partFile.length()
                                else -> 0L
                            }
                        }.coerceAtMost(file.size)
                    }
                    val fraction = if (file.size > 0) {
                        (bytesTransferred.toFloat() / file.size.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    // If app died while actively uploading/downloading, mark as PAUSED
                    val restoredStatus = when (file.status) {
                        FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
                            database.fileDao().updateStatus(file.id, FileStatus.PAUSED, "Paused (interrupted)")
                            FileStatus.PAUSED
                        }
                        else -> file.status
                    }
                    if (restoredStatus == FileStatus.PAUSED) {
                        pauseRequestedFiles.add(file.id)
                    }

                    restoredMap[file.id] = TransferProgress(
                        fileId = file.id,
                        fileName = file.name,
                        isUpload = isUpload,
                        currentChunk = completedChunks,
                        totalChunks = file.totalChunks,
                        progressFraction = fraction,
                        bytesTransferred = bytesTransferred,
                        totalBytes = file.size,
                        speedBytesPerSec = 0L,
                        status = restoredStatus,
                        errorMessage = file.errorMessage,
                        completedChunksCount = completedChunks,
                        isEncrypted = file.isEncrypted
                    )
                }
                if (restoredMap.isNotEmpty()) {
                    _transfers.update { current -> restoredMap + current }
                }
            } catch (e: Exception) {
                android.util.Log.e("TransferManager", "Failed to restore transfers: ${e.message}")
            }
        }
    }

    companion object {
        /**
         * Global chunk size constant: 18MB.
         * Telegram Bot API limits sendDocument (upload) to 50MB, but getFile (download) is strictly
         * capped at 20MB. 18MB leaves comfortable headroom for multipart overhead and network transport.
         */
        const val CHUNK_SIZE_BYTES: Long = 18 * 1024 * 1024L
        const val DEFAULT_CHUNK_SIZE_MB = 18
        const val MAX_SAFE_CHUNK_SIZE_MB = 18

        @Volatile
        private var INSTANCE: TransferManager? = null

        fun getInstance(context: Context): TransferManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                com.example.data.network.NetworkMonitor.register(appCtx)
                val db = AppDatabase.getInstance(appCtx)
                val repo = TelegramRepository()
                val creds = EncryptedCredentialsManager(appCtx)
                val manager = TransferManager(appCtx, db, repo, creds)
                INSTANCE = manager
                manager
            }
        }
    }

    /**
     * Enqueues a batch of files for sequential upload to Telegram.
     * All files are added to the visible upload queue in PENDING ("Queued") state.
     * Uploads execute strictly sequentially (one full file at a time, including chunks,
     * backup-channel copy, and manifest).
     */
    fun enqueueBatchUpload(
        uris: List<Uri>,
        folderId: String?,
        customChunkSizeBytes: Long? = null,
        encryptBeforeUpload: Boolean = false,
        tags: String = ""
    ): List<String> {
        val fileIds = mutableListOf<String>()
        for (uri in uris) {
            val fileId = UUID.randomUUID().toString()
            pauseRequestedFiles.remove(fileId)
            cancelledFiles.remove(fileId)
            fileIds.add(fileId)
            val (fileName, fileSize) = resolveUriMetadata(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val aesKey = if (encryptBeforeUpload) CryptoStreamEngine.generateKeyBase64() else null

            val fileEntity = FileEntity(
                id = fileId,
                name = fileName,
                folderId = folderId,
                size = fileSize,
                mimeType = mimeType,
                status = FileStatus.PENDING,
                checksum = "",
                totalChunks = 1,
                completedChunks = 0,
                localPath = null,
                localUri = uri.toString(),
                isEncrypted = encryptBeforeUpload,
                encryptionKeyBase64 = aesKey,
                tags = tags.trim()
            )
            scope.launch {
                database.fileDao().insert(fileEntity)
            }

            updateProgressState(
                TransferProgress(
                    fileId = fileId,
                    fileName = fileName,
                    isUpload = true,
                    currentChunk = 0,
                    totalChunks = 1,
                    progressFraction = 0f,
                    bytesTransferred = 0L,
                    totalBytes = fileSize,
                    speedBytesPerSec = 0L,
                    status = FileStatus.PENDING,
                    isChunking = false,
                    isEncrypted = encryptBeforeUpload
                )
            )

            uploadQueue.add(fileId)
        }

        notifyService("Upload batch queued (${uris.size} files)")
        processNextUploadInQueue()
        return fileIds
    }

    /**
     * Prepares and starts a chunked upload from a content Uri (single-file convenience method).
     */
    fun enqueueUpload(
        uri: Uri,
        folderId: String?,
        customChunkSizeBytes: Long? = null,
        encryptBeforeUpload: Boolean = false,
        tags: String = ""
    ): String {
        return enqueueBatchUpload(listOf(uri), folderId, customChunkSizeBytes, encryptBeforeUpload, tags).firstOrNull() ?: ""
    }

    /**
     * Executes or resumes a chunked upload by adding it to the sequential upload queue.
     */
    fun startUpload(fileId: String) {
        val existingJob = activeJobs[fileId]
        if (existingJob != null && existingJob.isActive && !pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId)) {
            Log.d("TransferManager", "startUpload: upload already actively running for $fileId")
            return
        }
        scope.launch {
            // If a previous job is still unwinding from pause/cancel, wait for it to finish cleanly
            val unwindingJob = activeJobs[fileId]
            if (unwindingJob != null && !unwindingJob.isCompleted) {
                try {
                    unwindingJob.join()
                } catch (_: Throwable) {}
            }

            pauseRequestedFiles.remove(fileId)
            cancelledFiles.remove(fileId)

            val fileEntity = database.fileDao().getById(fileId)
            if (fileEntity != null) {
                val chunks = database.chunkDao().getChunksForFile(fileId)
                val completedChunks = chunks.count { it.isUploaded }
                val uploadedBytes = chunks.filter { it.isUploaded }.sumOf { it.size }.coerceAtMost(fileEntity.size)
                val fraction = if (fileEntity.size > 0) {
                    (uploadedBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f)
                } else 0f

                database.fileDao().updateStatus(fileId, FileStatus.PENDING, null)
                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = fileEntity.name,
                        isUpload = true,
                        currentChunk = (completedChunks + 1).coerceAtMost(fileEntity.totalChunks.coerceAtLeast(1)),
                        totalChunks = fileEntity.totalChunks,
                        progressFraction = fraction,
                        bytesTransferred = uploadedBytes,
                        totalBytes = fileEntity.size,
                        speedBytesPerSec = 0L,
                        status = FileStatus.PENDING,
                        errorMessage = null,
                        completedChunksCount = completedChunks,
                        isChunking = false,
                        isEncrypted = fileEntity.isEncrypted
                    ),
                    forceStatus = true
                )
                if (!uploadQueue.contains(fileId) && activeUploadFileId.get() != fileId) {
                    uploadQueue.add(fileId)
                }
                notifyService("Processing upload")
                processNextUploadInQueue()
            }
        }
    }

    fun retryUpload(fileId: String) {
        startUpload(fileId)
    }

    /**
     * Sequentially pulls and processes items from the upload queue.
     * Guarantees one file completes fully (all chunks, backup mirroring, manifest)
     * before the next begins, avoiding rate-limiting or data exhaustion.
     * Batches index regeneration to run once when the queue is drained.
     */
    private fun processNextUploadInQueue() {
        scope.launch {
            if (!isUploadingActive.compareAndSet(false, true)) {
                Log.d("TransferManager", "processNextUploadInQueue: upload already active, remaining in queue: ${uploadQueue.size}")
                return@launch
            }

            try {
                while (isActive) {
                    val nextFileId = uploadQueue.poll() ?: break

                    if (cancelledFiles.contains(nextFileId) || pauseRequestedFiles.contains(nextFileId)) {
                        continue
                    }

                    val entity = database.fileDao().getById(nextFileId)
                    if (entity == null || entity.status == FileStatus.PAUSED || entity.status == FileStatus.COMPLETED) {
                        continue
                    }

                    activeUploadFileId.set(nextFileId)
                    var uploadJob: Job? = null
                    try {
                        uploadJob = scope.launch {
                            executeSingleFileUpload(nextFileId)
                        }
                        activeJobs[nextFileId] = uploadJob
                        uploadJob.join()
                    } catch (ce: CancellationException) {
                        Log.i("TransferManager", "Upload queue worker interrupted for $nextFileId")
                    } catch (t: Throwable) {
                        val errMsg = t.message ?: "Upload failed (${t::class.java.simpleName})"
                        Log.e("TransferManager", "Resilience: Per-file upload failure for $nextFileId (${entity.name}): $errMsg", t)
                        database.fileDao().updateStatus(nextFileId, FileStatus.FAILED, errMsg)
                        updateProgressState(
                            TransferProgress(
                                fileId = nextFileId,
                                fileName = entity.name,
                                isUpload = true,
                                currentChunk = entity.completedChunks,
                                totalChunks = entity.totalChunks,
                                progressFraction = 0f,
                                bytesTransferred = 0L,
                                totalBytes = entity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.FAILED,
                                errorMessage = errMsg,
                                isChunking = false
                            )
                        )
                        _transferErrorEvents.tryEmit(errMsg)
                        notifyService("Upload failed: ${entity.name}")
                    } finally {
                        if (uploadJob != null) {
                            activeJobs.remove(nextFileId, uploadJob)
                        }
                        activeUploadFileId.compareAndSet(nextFileId, null)
                    }
                }
            } finally {
                isUploadingActive.set(false)
                val completedCount = completedInCurrentBatch.getAndSet(0)
                if (completedCount > 0) {
                    Log.i("TransferManager", "Upload batch finished ($completedCount file(s) completed). Scheduling single batched VaultIndex auto-publish.")
                    VaultSyncManager.getInstance(context).scheduleAutoPublish(debounceDelayMs = 1500L)
                }
                if (uploadQueue.isNotEmpty()) {
                    processNextUploadInQueue()
                }
            }
        }
    }

    /**
     * Executes the per-file upload flow.
     */
    private suspend fun executeSingleFileUpload(fileId: String) = coroutineScope {
            var fileEntity = database.fileDao().getById(fileId)
            val fileName = fileEntity?.name ?: "File"
            val totalBytes = fileEntity?.size ?: 0L
            val totalChunks = fileEntity?.totalChunks ?: 1

            val token = credentialsManager.getBotToken()
            val chatId = credentialsManager.getChatId()
            if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
                val errorMsg = "Telegram credentials not set. Please connect your bot token and chat ID in Settings."
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, errorMsg)
                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = fileName,
                        isUpload = true,
                        currentChunk = fileEntity?.completedChunks ?: 0,
                        totalChunks = totalChunks,
                        progressFraction = 0f,
                        bytesTransferred = 0L,
                        totalBytes = totalBytes,
                        speedBytesPerSec = 0L,
                        status = FileStatus.FAILED,
                        errorMessage = errorMsg
                    )
                )
                _transferErrorEvents.tryEmit(errorMsg)
                return@coroutineScope
            }

            if (fileEntity == null) return@coroutineScope

            // Check Wi-Fi only restriction if enabled in settings
            if (credentialsManager.isWifiOnly() && !StorageUtil.isConnectedToWifi(context)) {
                val wifiError = "Upload paused: waiting for Wi-Fi network (Wi-Fi only enabled in settings)"
                Log.w("TransferManager", "Upload stopped for $fileId: not connected to Wi-Fi")
                database.fileDao().updateStatus(fileId, FileStatus.PAUSED, wifiError)
                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = fileEntity.name,
                        isUpload = true,
                        currentChunk = fileEntity.completedChunks,
                        totalChunks = fileEntity.totalChunks,
                        progressFraction = if (fileEntity.size > 0) (fileEntity.completedChunks.toFloat() / fileEntity.totalChunks.toFloat()) else 0f,
                        bytesTransferred = 0L,
                        totalBytes = fileEntity.size,
                        speedBytesPerSec = 0L,
                        status = FileStatus.PAUSED,
                        errorMessage = wifiError,
                        isChunking = false
                    )
                )
                _transferErrorEvents.tryEmit(wifiError)
                return@coroutineScope
            }

            var stagingFile = fileEntity.localPath?.let { File(it) } ?: File(context.cacheDir, "upload_staging/$fileId.tmp")
            if (!stagingFile.exists() || stagingFile.length() == 0L) {
                val uriString = fileEntity.localUri
                if (uriString != null) {
                    // Update UI to Chunking state
                    updateProgressState(
                        TransferProgress(
                            fileId = fileId,
                            fileName = fileEntity.name,
                            isUpload = true,
                            currentChunk = 0,
                            totalChunks = fileEntity.totalChunks,
                            progressFraction = 0f,
                            bytesTransferred = 0L,
                            totalBytes = fileEntity.size,
                            speedBytesPerSec = 0L,
                            status = FileStatus.UPLOADING,
                            isChunking = true
                        )
                    )

                    val uri = Uri.parse(uriString)
                    val stagingDir = File(context.cacheDir, "upload_staging").apply { mkdirs() }
                    stagingFile = File(stagingDir, "$fileId.tmp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(stagingFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalStateException("Unable to read selected file stream: ${fileEntity.name}")

                    val actualSize = stagingFile.length()
                    val actualStorageCheck = StorageUtil.checkStorageForUpload(context, actualSize, stagingFileExists = true)
                    if (!actualStorageCheck.isSufficient) {
                        stagingFile.delete()
                        val errMsg = actualStorageCheck.errorMessage ?: "Insufficient storage for chunking"
                        throw IllegalStateException(errMsg)
                    }

                    val overallChecksum = withContext(Dispatchers.Default) {
                        ChecksumUtil.computeSha256(stagingFile)
                    }

                    val rawMaxChunkSize = minOf(credentialsManager.getChunkSizeMb() * 1024 * 1024L, CHUNK_SIZE_BYTES)
                    val maxChunkSize = if (fileEntity.isEncrypted) (rawMaxChunkSize - 4096L).coerceAtLeast(1024L) else rawMaxChunkSize
                    val totalChunks = ((actualSize + maxChunkSize - 1) / maxChunkSize).toInt().coerceAtLeast(1)
                    val targetChunkSize = ((actualSize + totalChunks - 1) / totalChunks).coerceAtLeast(1L)

                    val thumbFile = if (ThumbnailUtil.isMedia(fileEntity.mimeType)) {
                        try {
                            ThumbnailUtil.generateThumbnail(context, stagingFile, fileEntity.mimeType, fileId)
                        } catch (e: Exception) {
                            Log.w("TransferManager", "Thumbnail generation failed for $fileName", e)
                            null
                        }
                    } else null

                    val ensuredKey = if (fileEntity.isEncrypted && fileEntity.encryptionKeyBase64.isNullOrBlank()) {
                        CryptoStreamEngine.generateKeyBase64()
                    } else {
                        fileEntity.encryptionKeyBase64
                    }

                    fileEntity = fileEntity.copy(
                        size = actualSize,
                        checksum = overallChecksum,
                        totalChunks = totalChunks,
                        localPath = stagingFile.absolutePath,
                        thumbnailLocalPath = thumbFile?.absolutePath,
                        encryptionKeyBase64 = ensuredKey
                    )
                    database.fileDao().update(fileEntity)

                    database.chunkDao().deleteForFile(fileId)
                    val chunkEntities = mutableListOf<ChunkEntity>()
                    for (i in 0 until totalChunks) {
                        val offset = i * targetChunkSize
                        val chunkLength = minOf(targetChunkSize, (actualSize - offset).coerceAtLeast(0L))
                        chunkEntities.add(
                            ChunkEntity(
                                fileId = fileId,
                                chunkIndex = i,
                                checksum = "",
                                size = chunkLength,
                                isUploaded = false
                            )
                        )
                    }
                    database.chunkDao().insertAll(chunkEntities)
                } else {
                    val errorMsg = "Source staging file missing"
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, errorMsg)
                    updateProgressState(
                        TransferProgress(
                            fileId = fileId,
                            fileName = fileEntity.name,
                            isUpload = true,
                            currentChunk = fileEntity.completedChunks,
                            totalChunks = fileEntity.totalChunks,
                            progressFraction = 0f,
                            bytesTransferred = 0L,
                            totalBytes = fileEntity.size,
                            speedBytesPerSec = 0L,
                            status = FileStatus.FAILED,
                            errorMessage = errorMsg,
                            isChunking = false
                        )
                    )
                    _transferErrorEvents.tryEmit(errorMsg)
                    return@coroutineScope
                }
            }

            var chunks = database.chunkDao().getChunksForFile(fileId)
            if (chunks.isEmpty()) {
                Log.w("TransferManager", "No chunks found in database for $fileId (${fileEntity.name}). Generating metadata...")
                val actualSize = stagingFile.length()
                val maxChunkSize = minOf(credentialsManager.getChunkSizeMb() * 1024 * 1024L, CHUNK_SIZE_BYTES)
                val totalChunks = ((actualSize + maxChunkSize - 1) / maxChunkSize).toInt().coerceAtLeast(1)
                val targetChunkSize = ((actualSize + totalChunks - 1) / totalChunks).coerceAtLeast(1L)
                val chunkEntities = mutableListOf<ChunkEntity>()
                for (i in 0 until totalChunks) {
                    val offset = i * targetChunkSize
                    val chunkLength = minOf(targetChunkSize, (actualSize - offset).coerceAtLeast(0L))
                    chunkEntities.add(
                        ChunkEntity(
                            fileId = fileId,
                            chunkIndex = i,
                            checksum = "",
                            size = chunkLength,
                            isUploaded = false
                        )
                    )
                }
                database.chunkDao().insertAll(chunkEntities)
                chunks = database.chunkDao().getChunksForFile(fileId)
            }

            val initialCompletedCount = chunks.count { it.isUploaded }
            val initialConfirmedBytes = chunks.filter { it.isUploaded }.sumOf { it.size }

            val confirmedBytes = AtomicLong(initialConfirmedBytes)
            val completedChunksCount = AtomicInteger(initialCompletedCount)
            val maxReportedBytes = AtomicLong(initialConfirmedBytes)
            val inProgressBytesMap = ConcurrentHashMap<Int, Long>()
            val lastUiEmissionMs = AtomicLong(0L)
            val uploadFailedReason = AtomicReference<String?>(null)

            val speedEstimator = RollingSpeedEstimator(windowDurationMs = 2000L)
            speedEstimator.addSample(System.currentTimeMillis(), initialConfirmedBytes)

            fun publishProgress(
                force: Boolean = false,
                status: FileStatus = FileStatus.UPLOADING,
                errorMessage: String? = null,
                isChunking: Boolean = false
            ) {
                if (cancelledFiles.contains(fileId)) {
                    _transfers.update { it - fileId }
                    return
                }
                if (!force && pauseRequestedFiles.contains(fileId) && status == FileStatus.UPLOADING) {
                    return
                }
                val now = SystemClock.elapsedRealtime()
                val last = lastUiEmissionMs.get()
                // Throttle progress state emissions to ~8-10 updates per second (~100-125ms interval) unless forced
                if (!force && (now - last < 120L)) {
                    return
                }
                lastUiEmissionMs.set(now)

                val confirmed = confirmedBytes.get()
                val inProgressSum = inProgressBytesMap.values.sum()
                // Confirmed bytes is the absolute floor. inProgressSum is added on top.
                val rawSum = (confirmed + inProgressSum).coerceIn(confirmed, fileEntity.size)
                // Strictly monotonic: reported bytes never drops backward even during chunk retries!
                val monotonicBytes = maxReportedBytes.updateAndGet { cur -> maxOf(cur, rawSum) }
                val liveSpeed = speedEstimator.addSample(System.currentTimeMillis(), monotonicBytes)

                val fraction = if (fileEntity.size > 0) {
                    (monotonicBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f)
                } else 0f

                val remainingBytes = (fileEntity.size - monotonicBytes).coerceAtLeast(0L)
                val etaSec = if (liveSpeed > 0L && remainingBytes > 0L) remainingBytes / liveSpeed else null
                val activeCount = inProgressBytesMap.size
                val completed = completedChunksCount.get()

                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = fileEntity.name,
                        isUpload = true,
                        currentChunk = (completed + 1).coerceAtMost(fileEntity.totalChunks),
                        totalChunks = fileEntity.totalChunks,
                        progressFraction = fraction,
                        bytesTransferred = monotonicBytes,
                        totalBytes = fileEntity.size,
                        speedBytesPerSec = liveSpeed,
                        status = status,
                        errorMessage = errorMessage,
                        etaSeconds = etaSec,
                        activeConcurrentChunks = activeCount,
                        completedChunksCount = completed,
                        isChunking = isChunking,
                        isEncrypted = fileEntity.isEncrypted,
                        activeBotWorkers = MultiBotLoadBalancer.getInstance(context).botMetrics.value.size.coerceAtLeast(1)
                    ),
                    forceStatus = force
                )
            }

            database.fileDao().updateStatus(fileId, FileStatus.UPLOADING)
            publishProgress(force = true, status = FileStatus.UPLOADING, isChunking = false)
            notifyService("Uploading ${fileEntity.name}")

            val loadBalancer = MultiBotLoadBalancer.getInstance(context)
            val botPool = loadBalancer.getActiveBotPool()
            val workerCount = maxOf(3, minOf(botPool.size * 2, 6))

            val chunksDir = File(context.cacheDir, "upload_chunks/$fileId").apply { mkdirs() }
            val incompleteChunks = chunks.filter { !it.isUploaded }.sortedBy { it.chunkIndex }
            val chunkQueue = ConcurrentLinkedQueue(incompleteChunks)

            // Precompute O(1) byte offsets per chunkIndex
            val offsetsByChunkIndex = HashMap<Int, Long>(chunks.size)
            var runningOffset = 0L
            for (c in chunks.sortedBy { it.chunkIndex }) {
                offsetsByChunkIndex[c.chunkIndex] = runningOffset
                runningOffset += c.size
            }

            try {
                if (incompleteChunks.isNotEmpty()) {
                    coroutineScope {
                        val prepMutex = Mutex()
                        val preparedChunks = ConcurrentHashMap<Int, Deferred<Pair<File, String>>>()

                        fun prepareChunkAsync(targetChunk: ChunkEntity): Deferred<Pair<File, String>> {
                            return preparedChunks.computeIfAbsent(targetChunk.chunkIndex) { idx ->
                                async(Dispatchers.IO) {
                                    val cLen = targetChunk.size
                                    val cOffset = offsetsByChunkIndex[idx] ?: 0L
                                    val rawChunkFile = File(chunksDir, "chunk_$idx.tpart")
                                    val (plainChunkFile, plainSha256) = if (!rawChunkFile.exists() || rawChunkFile.length() != cLen) {
                                        if (rawChunkFile.exists()) rawChunkFile.delete()
                                        writeChunkFileOnDisk(stagingFile, chunksDir, idx, cOffset, cLen)
                                    } else {
                                        val existingSha256 = if (targetChunk.checksum.isNotEmpty() && !fileEntity.isEncrypted) {
                                            targetChunk.checksum
                                        } else {
                                            ChecksumUtil.computeSha256(rawChunkFile)
                                        }
                                        Pair(rawChunkFile, existingSha256)
                                    }

                                    if (fileEntity.isEncrypted && !fileEntity.encryptionKeyBase64.isNullOrBlank()) {
                                        val encFile = File(chunksDir, "chunk_${idx}.enc.tpart")
                                        val encSha256 = CryptoStreamEngine.encryptChunkFile(
                                            plainChunkFile = plainChunkFile,
                                            encryptedOutFile = encFile,
                                            keyBase64 = fileEntity.encryptionKeyBase64!!,
                                            chunkIndex = idx
                                        )
                                        plainChunkFile.delete()
                                        Pair(encFile, encSha256)
                                    } else {
                                        Pair(plainChunkFile, plainSha256)
                                    }
                                }
                            }
                        }

                        // Pre-stage the first 2 chunks immediately so network workers never wait on disk/crypto
                        incompleteChunks.take(2).forEach { prepareChunkAsync(it) }

                        val workers = (0 until workerCount).map { workerId ->
                            launch(Dispatchers.IO) {
                                while (isActive && !chunkQueue.isEmpty() && !pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId) && uploadFailedReason.get() == null) {
                                    // Dynamic load & thermal-aware concurrency: scale with available bot pool
                                    val baseAllowed = getEffectiveMaxConcurrency(context)
                                    val allowedConcurrency = if (botPool.size > 1 && baseAllowed >= 3) workerCount else baseAllowed
                                    if (workerId >= allowedConcurrency) {
                                        delay(300L)
                                        continue
                                    }

                                    val chunk = chunkQueue.poll() ?: break
                                    val chunkIndex = chunk.chunkIndex
                                    val totalChunks = fileEntity.totalChunks
                                    val chunkLength = chunk.size

                                    if (pauseRequestedFiles.contains(fileId) || cancelledFiles.contains(fileId)) {
                                        chunkQueue.add(chunk)
                                        break
                                    }

                                    // Pipeline preparation of the next queued chunk while this chunk uploads
                                    chunkQueue.peek()?.let { nextInQueue ->
                                        prepareChunkAsync(nextInQueue)
                                    }

                                    inProgressBytesMap[chunkIndex] = 0L
                                    publishProgress(force = false)

                                    val (readyChunkFile, localSha256) = try {
                                        prepareChunkAsync(chunk).await()
                                    } finally {
                                        preparedChunks.remove(chunkIndex)
                                    }

                                    val actualFileSizeOnDisk = readyChunkFile.length()
                                    val expectedChunkSize = actualFileSizeOnDisk.coerceAtLeast(1L)

                                    if (actualFileSizeOnDisk > 50 * 1024 * 1024L) {
                                        val errMsg = "Chunk ${chunkIndex + 1} size ($actualFileSizeOnDisk bytes) exceeds Telegram Bot 50MB limit!"
                                        uploadFailedReason.set(errMsg)
                                        inProgressBytesMap.remove(chunkIndex)
                                        break
                                    }

                                    val maxRetries = 5
                                    var attempt = 0
                                    var chunkSuccess = false
                                    var lastError: String? = null
                                    var uploadedMessage: com.example.data.remote.TelegramMessage? = null

                                    while (attempt < maxRetries && !chunkSuccess && isActive && !pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId) && uploadFailedReason.get() == null) {
                                        attempt++
                                        // Reset this chunk's in-progress bytes for retry
                                        inProgressBytesMap[chunkIndex] = 0L
                                        publishProgress(force = false)

                                        if (pauseRequestedFiles.contains(fileId) || cancelledFiles.contains(fileId)) break

                                        val workerBotToken = loadBalancer.acquireBotForChunk(token)
                                        val uploadResult = try {
                                            repository.uploadChunk(
                                                token = workerBotToken,
                                                chatId = chatId,
                                                fileId = fileId,
                                                fileName = fileEntity.name,
                                                chunkIndex = chunkIndex,
                                                totalChunks = fileEntity.totalChunks,
                                                chunkFile = readyChunkFile,
                                                chunkSha256 = localSha256,
                                                expectedChunkSize = expectedChunkSize,
                                                shouldAbort = { !isActive || pauseRequestedFiles.contains(fileId) || cancelledFiles.contains(fileId) }
                                            ) { bytesWritten, _ ->
                                                if (!pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId)) {
                                                    inProgressBytesMap[chunkIndex] = minOf(bytesWritten, chunkLength)
                                                    publishProgress(force = false)
                                                }
                                            }
                                        } finally {
                                            // Released below after checking result
                                        }

                                        if (pauseRequestedFiles.contains(fileId) || cancelledFiles.contains(fileId) || !isActive) {
                                            loadBalancer.releaseBotForChunk(workerBotToken, 0L, false)
                                            break
                                        }

                                        if (uploadResult.isSuccess) {
                                            loadBalancer.releaseBotForChunk(workerBotToken, chunkLength, true)
                                            chunkSuccess = true
                                            uploadedMessage = uploadResult.getOrThrow()
                                        } else {
                                            loadBalancer.releaseBotForChunk(workerBotToken, 0L, false)
                                            val exception = uploadResult.exceptionOrNull()
                                            if (exception?.message == "Transfer aborted by user" || pauseRequestedFiles.contains(fileId) || cancelledFiles.contains(fileId)) {
                                                break
                                            }
                                            if (exception is TelegramApiException && exception.errorCode == 429) {
                                                loadBalancer.markRateLimited(workerBotToken, 15)
                                            }
                                            lastError = exception?.message ?: "Network error"
                                            val isTransient = isTransientNetworkError(exception)
                                            Log.w("TransferManager", "Chunk upload attempt $attempt/$maxRetries for chunk ${chunkIndex + 1}/$totalChunks failed (isTransient=$isTransient): $lastError", exception)

                                            // Evict connection pool so retries use a clean, verified TCP socket
                                            repository.evictConnectionPool("Chunk ${chunkIndex + 1} upload attempt $attempt failed")

                                            if (!isTransient) {
                                                Log.e("TransferManager", "Permanent non-retryable error on chunk ${chunkIndex + 1}: '$lastError'. Aborting retries.")
                                                break
                                            }

                                            if (attempt < maxRetries && !pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId) && uploadFailedReason.get() == null) {
                                                inProgressBytesMap[chunkIndex] = 0L
                                                val backoffMs = when (attempt) {
                                                    1 -> 2000L
                                                    2 -> 4000L
                                                    3 -> 8000L
                                                    4 -> 16000L
                                                    else -> 30000L
                                                }
                                                val retryStatusMsg = "Reconnecting… retrying chunk ${chunkIndex + 1}/$totalChunks (attempt $attempt/$maxRetries)"
                                                publishProgress(force = true, errorMessage = retryStatusMsg)

                                                var waited = 0L
                                                while (waited < backoffMs && !pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId) && uploadFailedReason.get() == null) {
                                                    val step = minOf(500L, backoffMs - waited)
                                                    delay(step)
                                                    waited += step
                                                }
                                            }
                                        }
                                    }

                                    if (pauseRequestedFiles.contains(fileId) || cancelledFiles.contains(fileId) || !isActive) {
                                        inProgressBytesMap.remove(chunkIndex)
                                        chunkQueue.add(chunk)
                                        break
                                    }

                                    if (!chunkSuccess || uploadedMessage == null) {
                                        val failReason = "[Upload Chunk ${chunkIndex + 1}/$totalChunks] ${lastError ?: "Upload failed after $maxRetries attempts"}"
                                        uploadFailedReason.set(failReason)
                                        inProgressBytesMap.remove(chunkIndex)
                                        break
                                    }

                                    val remoteFileId = uploadedMessage.document?.fileId ?: ""

                                    // Requirement 1: Mirror every chunk to backup channel via copyMessage (server-side copy, zero extra mobile data)
                                    var backupMsgId: Long? = null
                                    val backupChatId = credentialsManager.getBackupChatId()?.trim()
                                    if (!backupChatId.isNullOrEmpty() && backupChatId != chatId) {
                                        try {
                                            val copyResult = repository.copyMessage(
                                                token = token,
                                                chatId = backupChatId,
                                                fromChatId = chatId,
                                                messageId = uploadedMessage.messageId
                                            )
                                            if (copyResult.isSuccess) {
                                                backupMsgId = copyResult.getOrThrow().messageId
                                                Log.i("TransferManager", "Chunk $chunkIndex mirrored to backup channel $backupChatId (backupMsgId=$backupMsgId)")
                                            } else {
                                                Log.w("TransferManager", "Failed to mirror chunk $chunkIndex to backup channel: ${copyResult.exceptionOrNull()?.message}")
                                            }
                                        } catch (e: Exception) {
                                            Log.w("TransferManager", "Error mirroring chunk $chunkIndex to backup channel: ${e.message}")
                                        }
                                    }

                                    // Persist chunk upload success in Room immediately
                                    database.chunkDao().markChunkUploaded(
                                        fileId = fileId,
                                        chunkIndex = chunkIndex,
                                        messageId = uploadedMessage.messageId,
                                        fileIdRemote = remoteFileId,
                                        checksum = localSha256,
                                        channelId = chatId,
                                        backupMessageId = backupMsgId
                                    )

                                    inProgressBytesMap.remove(chunkIndex)
                                    confirmedBytes.addAndGet(chunkLength)
                                    val completedNow = completedChunksCount.incrementAndGet()

                                    database.fileDao().updateProgress(fileId, completedNow, FileStatus.UPLOADING)

                                    if (readyChunkFile.exists()) {
                                        readyChunkFile.delete()
                                    }

                                    publishProgress(force = true)
                                }
                            }
                        }
                        workers.forEach { it.join() }
                    }
                }

                // Check cancel condition first
                if (cancelledFiles.contains(fileId)) {
                    _transfers.update { it - fileId }
                    return@coroutineScope
                }

                // Check pause condition
                if (pauseRequestedFiles.contains(fileId)) {
                    database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused")
                    publishProgress(force = true, status = FileStatus.PAUSED)
                    return@coroutineScope
                }

                // Check failure condition
                val failure = uploadFailedReason.get()
                if (failure != null) {
                    Log.e("TransferManager", "Marking upload for $fileId (${fileEntity.name}) as FAILED. Reason: $failure")
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, failure)
                    publishProgress(force = true, status = FileStatus.FAILED, errorMessage = failure)
                    _transferErrorEvents.tryEmit(failure)
                    notifyService("Upload failed: ${fileEntity.name}")
                    return@coroutineScope
                }

                // Verify all chunks are done
                val remainingCount = database.chunkDao().getChunksForFile(fileId).count { !it.isUploaded }
                if (remainingCount > 0) {
                    val errMsg = "Upload incomplete: $remainingCount chunks remaining"
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, errMsg)
                    publishProgress(force = true, status = FileStatus.FAILED, errorMessage = errMsg)
                    _transferErrorEvents.tryEmit(errMsg)
                    notifyService("Upload failed: ${fileEntity.name}")
                    return@coroutineScope
                }

                // All chunks successfully uploaded!
                // Construct and upload the reassembly Manifest
                val updatedChunks = database.chunkDao().getChunksForFile(fileId)
                val manifestChunks = updatedChunks.map { c ->
                    ManifestChunk(
                        index = c.chunkIndex,
                        messageId = c.telegramMessageId ?: 0L,
                        channelId = c.channelId ?: chatId,
                        telegramFileId = c.telegramFileId,
                        sha256 = c.checksum,
                        size = c.size
                    )
                }

                // Check for thumbnail to upload alongside chunks
                var thumbnailMessageId: Long? = fileEntity.thumbnailMessageId
                var thumbnailFileId: String? = fileEntity.thumbnailFileId

                val localThumb = fileEntity.thumbnailLocalPath?.let { File(it) }
                    ?: ThumbnailUtil.getThumbnailFile(context, fileId)

                if (thumbnailFileId == null && localThumb.exists() && localThumb.length() > 0L) {
                    try {
                        Log.i(
                            "TransferManager",
                            "Uploading thumbnail document for $fileId (${fileEntity.name}): " +
                            "size=${localThumb.length()} bytes, caption=THUMBNAIL|$fileId"
                        )
                        val thumbResult = repository.uploadThumbnail(token, chatId, fileId, localThumb)
                        if (thumbResult.isSuccess) {
                            val thumbMsg = thumbResult.getOrThrow()
                            thumbnailMessageId = thumbMsg.messageId
                            thumbnailFileId = thumbMsg.document?.fileId
                            database.fileDao().updateThumbnailInfo(
                                fileId = fileId,
                                fileIdRemote = thumbnailFileId,
                                messageId = thumbnailMessageId,
                                localPath = localThumb.absolutePath
                            )
                            Log.i("TransferManager", "Thumbnail successfully uploaded: msgId=$thumbnailMessageId, fileId=$thumbnailFileId")
                        } else {
                            Log.w("TransferManager", "Thumbnail upload failed: ${thumbResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.w("TransferManager", "Exception uploading thumbnail for $fileId", e)
                    }
                }

                val manifest = FileManifest(
                    fileId = fileId,
                    name = fileEntity.name,
                    size = fileEntity.size,
                    mimeType = fileEntity.mimeType,
                    overallSha256 = fileEntity.checksum,
                    folderId = fileEntity.folderId,
                    channelId = chatId,
                    uploadDate = System.currentTimeMillis(),
                    chunks = manifestChunks,
                    thumbnailMessageId = thumbnailMessageId,
                    thumbnailFileId = thumbnailFileId,
                    isEncrypted = fileEntity.isEncrypted,
                    encryptionKeyBase64 = fileEntity.encryptionKeyBase64,
                    tags = fileEntity.tags
                )

                // Write manifest JSON to a small local file for upload via sendDocument
                val manifestDir = File(context.cacheDir, "upload_manifests").apply { mkdirs() }
                val manifestFile = File(manifestDir, "$fileId.manifest.json")
                val manifestJson = manifestAdapter.toJson(manifest)
                manifestFile.writeText(manifestJson)

                Log.i(
                    "TransferManager",
                    "Uploading manifest document for $fileId (${fileEntity.name}): " +
                    "${manifestChunks.size} chunks, manifest file size: ${manifestFile.length()} bytes"
                )

                val manifestResult = repository.uploadManifest(token, chatId, manifest, manifestFile)
                if (manifestResult.isFailure) {
                    val err = manifestResult.exceptionOrNull()?.message ?: "Manifest upload failed"
                    manifestFile.delete()
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                    publishProgress(force = true, status = FileStatus.FAILED, errorMessage = err, isChunking = false)
                    _transferErrorEvents.tryEmit(err)
                    notifyService("Manifest upload failed: ${fileEntity.name}")
                    return@coroutineScope
                }

                // Clean up temporary manifest file on disk
                manifestFile.delete()

                val manifestMessage = manifestResult.getOrThrow()
                database.fileDao().updateManifestId(fileId, manifestMessage.messageId, chatId)
                database.fileDao().updateStatus(fileId, FileStatus.COMPLETED)

                val completedProgress = TransferProgress(
                    fileId = fileId,
                    fileName = fileEntity.name,
                    isUpload = true,
                    currentChunk = fileEntity.totalChunks,
                    totalChunks = fileEntity.totalChunks,
                    progressFraction = 1f,
                    bytesTransferred = fileEntity.size,
                    totalBytes = fileEntity.size,
                    speedBytesPerSec = 0L,
                    status = FileStatus.COMPLETED,
                    isChunking = false
                )

                // Remove from active transfers immediately upon completion
                _transfers.update { it - fileId }
                // Add to recently completed list (capped at 5)
                _recentlyCompleted.update { current ->
                    (listOf(completedProgress) + current.filter { it.fileId != fileId }).take(5)
                }

                completedInCurrentBatch.incrementAndGet()

                // Safe cleanup of temporary staging file and discrete chunks directory
                stagingFile.delete()
                File(context.cacheDir, "upload_chunks/$fileId").deleteRecursively()

            } catch (e: CancellationException) {
                if (cancelledFiles.contains(fileId)) {
                    Log.i("TransferManager", "Upload cancelled for fileId=$fileId")
                    _transfers.update { it - fileId }
                } else {
                    Log.i("TransferManager", "Upload paused for fileId=$fileId")
                    database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused")
                    publishProgress(force = true, status = FileStatus.PAUSED, isChunking = false)
                }
            } catch (e: Throwable) {
                if (cancelledFiles.contains(fileId)) {
                    _transfers.update { it - fileId }
                } else if (pauseRequestedFiles.contains(fileId)) {
                    database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused")
                    publishProgress(force = true, status = FileStatus.PAUSED, isChunking = false)
                } else {
                    val err = e.message ?: "Upload failed (${e::class.java.simpleName})"
                    Log.e("TransferManager", "Fatal error during upload for $fileId (${fileEntity.name}): $err", e)
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                    publishProgress(force = true, status = FileStatus.FAILED, errorMessage = err, isChunking = false)
                    _transferErrorEvents.tryEmit(err)
                }
            } finally {
                activeJobs.remove(fileId, coroutineContext.job)
            }
    }

    private fun computeTotalDownloadedBytesOnDisk(
        chunks: List<ChunkEntity>,
        downloadTempDir: File,
        totalFileSize: Long
    ): Pair<Long, Int> {
        var bytesOnDisk = 0L
        var completedCount = 0
        for (c in chunks) {
            val partFile = File(downloadTempDir, "chunk_${c.chunkIndex}.part")
            if (partFile.exists()) {
                val len = partFile.length()
                if (len == c.size) {
                    bytesOnDisk += c.size
                    completedCount++
                } else if (len in 1 until c.size) {
                    bytesOnDisk += len
                }
            }
        }
        return Pair(bytesOnDisk.coerceAtMost(totalFileSize), completedCount)
    }

    /**
     * Executes or resumes a chunked download and reassembles the final file.
     * Supports mid-chunk pause and exact byte-offset HTTP Range resumption.
     */
    fun startDownload(fileId: String) {
        val existingJob = activeJobs[fileId]
        if (existingJob != null && existingJob.isActive && !pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId)) {
            Log.d("TransferManager", "startDownload: download already actively running for $fileId")
            return
        }

        scope.launch {
            val thisJob = coroutineContext.job
            val unwindingJob = activeJobs[fileId]
            if (unwindingJob != null && unwindingJob !== thisJob && !unwindingJob.isCompleted) {
                try {
                    unwindingJob.join()
                } catch (_: Throwable) {}
            }

            pauseRequestedFiles.remove(fileId)
            cancelledFiles.remove(fileId)
            activeJobs[fileId] = thisJob

            try {
                var token = credentialsManager.getBotToken()
                if (token.isNullOrBlank()) {
                    val err = "Telegram bot token not configured"
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                    val file = database.fileDao().getById(fileId)
                    updateProgressState(
                        TransferProgress(
                            fileId = fileId,
                            fileName = file?.name ?: "Download",
                            isUpload = false,
                            currentChunk = 0,
                            totalChunks = file?.totalChunks ?: 1,
                            progressFraction = 0f,
                            bytesTransferred = 0L,
                            totalBytes = file?.size ?: 0L,
                            speedBytesPerSec = 0L,
                            status = FileStatus.FAILED,
                            errorMessage = err
                        ),
                        forceStatus = true
                    )
                    _transferErrorEvents.tryEmit(err)
                    return@launch
                }

                val fileEntity = database.fileDao().getById(fileId) ?: return@launch
                val downloadTempDir = File(context.cacheDir, "downloads_temp/$fileId").apply { mkdirs() }
                val tempReassembledFile = File(downloadTempDir, "assembled_${fileEntity.name}")

                var chunks = database.chunkDao().getChunksForFile(fileId)
                val (initialDiskBytes, initialCompletedChunks) = computeTotalDownloadedBytesOnDisk(chunks, downloadTempDir, fileEntity.size)
                val initialFraction = if (fileEntity.size > 0) {
                    (initialDiskBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f)
                } else 0f

                // Check Wi-Fi only restriction if enabled in settings
                if (credentialsManager.isWifiOnly() && !StorageUtil.isConnectedToWifi(context)) {
                    val wifiError = "Download paused: waiting for Wi-Fi network (Wi-Fi only enabled in settings)"
                    Log.w("TransferManager", "Download stopped for $fileId: not connected to Wi-Fi")
                    database.fileDao().updateStatus(fileId, FileStatus.PAUSED, wifiError)
                    updateProgressState(
                        TransferProgress(
                            fileId = fileId,
                            fileName = fileEntity.name,
                            isUpload = false,
                            currentChunk = initialCompletedChunks,
                            totalChunks = fileEntity.totalChunks,
                            progressFraction = initialFraction,
                            bytesTransferred = initialDiskBytes,
                            totalBytes = fileEntity.size,
                            speedBytesPerSec = 0L,
                            status = FileStatus.PAUSED,
                            errorMessage = wifiError,
                            isEncrypted = fileEntity.isEncrypted
                        ),
                        forceStatus = true
                    )
                    _transferErrorEvents.tryEmit(wifiError)
                    return@launch
                }

                // Check available storage space for download (chunks + assembled file + safety buffer)
                val downloadStorageCheck = StorageUtil.checkStorageForDownload(context, fileEntity.size)
                if (!downloadStorageCheck.isSufficient) {
                    val storageError = downloadStorageCheck.errorMessage ?: "Insufficient device storage for download"
                    Log.e("TransferManager", "Download failed: $storageError")
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, storageError)
                    updateProgressState(
                        TransferProgress(
                            fileId = fileId,
                            fileName = fileEntity.name,
                            isUpload = false,
                            currentChunk = initialCompletedChunks,
                            totalChunks = fileEntity.totalChunks,
                            progressFraction = initialFraction,
                            bytesTransferred = initialDiskBytes,
                            totalBytes = fileEntity.size,
                            speedBytesPerSec = 0L,
                            status = FileStatus.FAILED,
                            errorMessage = storageError,
                            isEncrypted = fileEntity.isEncrypted
                        ),
                        forceStatus = true
                    )
                    _transferErrorEvents.tryEmit(storageError)
                    return@launch
                }

                database.fileDao().updateStatus(fileId, FileStatus.DOWNLOADING)
                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = fileEntity.name,
                        isUpload = false,
                        currentChunk = (initialCompletedChunks + 1).coerceAtMost(fileEntity.totalChunks.coerceAtLeast(1)),
                        totalChunks = fileEntity.totalChunks,
                        progressFraction = initialFraction,
                        bytesTransferred = initialDiskBytes,
                        totalBytes = fileEntity.size,
                        speedBytesPerSec = 0L,
                        status = FileStatus.DOWNLOADING,
                        errorMessage = null,
                        completedChunksCount = initialCompletedChunks,
                        isEncrypted = fileEntity.isEncrypted
                    ),
                    forceStatus = true
                )
                notifyService("Downloading ${fileEntity.name}")

                if (chunks.isEmpty()) {
                    // If chunks are not present locally, attempt to recover them from chat manifests
                    Log.w("TransferManager", "Chunks missing locally for $fileId. Attempting to fetch from chat manifests...")
                    val legacyManifests = repository.fetchManifestsFromChat(token)
                    if (legacyManifests.isSuccess) {
                        val matchingManifest = legacyManifests.getOrThrow().find { it.fileId == fileId }
                        if (matchingManifest != null) {
                            val chunkEntities = matchingManifest.chunks.map { mc ->
                                ChunkEntity(
                                    fileId = fileId,
                                    chunkIndex = mc.index,
                                    telegramMessageId = mc.messageId,
                                    telegramFileId = mc.telegramFileId,
                                    checksum = mc.sha256,
                                    size = mc.size,
                                    isUploaded = true,
                                    isDownloaded = false
                                )
                            }
                            database.chunkDao().insertAll(chunkEntities)
                            chunks = database.chunkDao().getChunksForFile(fileId)
                        }
                    }
                }

                if (chunks.isEmpty()) {
                    val err = "File chunk metadata not found. Please tap 'Sync now' in Vault Settings to refresh."
                    Log.e("TransferManager", "Download failed for $fileId: $err")
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                    updateProgressState(
                        TransferProgress(
                            fileId = fileId,
                            fileName = fileEntity.name,
                            isUpload = false,
                            currentChunk = 0,
                            totalChunks = fileEntity.totalChunks,
                            progressFraction = 0f,
                            bytesTransferred = 0L,
                            totalBytes = fileEntity.size,
                            speedBytesPerSec = 0L,
                            status = FileStatus.FAILED,
                            errorMessage = err
                        ),
                        forceStatus = true
                    )
                    _transferErrorEvents.tryEmit(err)
                    return@launch
                }

                val confirmedDownloadBytes = AtomicLong(0L)
                val completedChunksAtomic = AtomicInteger(0)
                val inProgressDownloadBytes = ConcurrentHashMap<Int, Long>()
                val maxReportedDownloadBytes = AtomicLong(initialDiskBytes)
                val lastDownloadUiMs = AtomicLong(0L)
                val downloadFailedReason = AtomicReference<String?>(null)

                val speedEstimator = RollingSpeedEstimator(windowDurationMs = 1800L)
                speedEstimator.addSample(System.currentTimeMillis(), initialDiskBytes)

                fun publishDownloadProgress(force: Boolean = false, errorMessage: String? = null) {
                    if (cancelledFiles.contains(fileId)) return
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val prevElapsed = lastDownloadUiMs.get()
                    if (!force && (nowElapsed - prevElapsed < 100L)) return
                    lastDownloadUiMs.set(nowElapsed)

                    val confirmed = confirmedDownloadBytes.get()
                    val inProgress = inProgressDownloadBytes.values.sum()
                    val rawTotal = (confirmed + inProgress).coerceIn(confirmed, fileEntity.size)
                    val currentTotalDownloaded = maxReportedDownloadBytes.updateAndGet { cur -> maxOf(cur, rawTotal) }
                    val liveSpeed = speedEstimator.addSample(System.currentTimeMillis(), currentTotalDownloaded)
                    val completedNow = completedChunksAtomic.get()

                    val fraction = if (fileEntity.size > 0) {
                        (currentTotalDownloaded.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val remainingBytes = (fileEntity.size - currentTotalDownloaded).coerceAtLeast(0L)
                    val etaSec = if (liveSpeed > 0L && remainingBytes > 0L) remainingBytes / liveSpeed else null

                    updateProgressState(
                        TransferProgress(
                            fileId = fileId,
                            fileName = fileEntity.name,
                            isUpload = false,
                            currentChunk = (completedNow + 1).coerceAtMost(fileEntity.totalChunks.coerceAtLeast(1)),
                            totalChunks = fileEntity.totalChunks,
                            progressFraction = fraction,
                            bytesTransferred = currentTotalDownloaded,
                            totalBytes = fileEntity.size,
                            speedBytesPerSec = liveSpeed,
                            status = FileStatus.DOWNLOADING,
                            errorMessage = errorMessage,
                            etaSeconds = etaSec,
                            activeConcurrentChunks = inProgressDownloadBytes.size,
                            completedChunksCount = completedNow,
                            isEncrypted = fileEntity.isEncrypted
                        ),
                        forceStatus = force
                    )
                }

                try {
                    val orderedChunks = chunks.sortedBy { it.chunkIndex }
                    val pendingDownloadChunks = mutableListOf<ChunkEntity>()

                    // 1. Fast resume check for already-verified .part files on disk
                    for (chunk in orderedChunks) {
                        val chunkTempFile = File(downloadTempDir, "chunk_${chunk.chunkIndex}.part")
                        if (chunkTempFile.exists() && chunkTempFile.length() == chunk.size) {
                            val existingHash = ChecksumUtil.computeSha256(chunkTempFile)
                            if (existingHash == chunk.checksum) {
                                if (!chunk.isDownloaded) {
                                    database.chunkDao().markChunkDownloaded(fileId, chunk.chunkIndex)
                                }
                                confirmedDownloadBytes.addAndGet(chunk.size)
                                completedChunksAtomic.incrementAndGet()
                                continue
                            } else {
                                chunkTempFile.delete()
                            }
                        } else if (chunkTempFile.exists() && chunkTempFile.length() > chunk.size) {
                            chunkTempFile.delete()
                        }
                        pendingDownloadChunks.add(chunk)
                    }

                    // 2. Download remaining chunks using up to 3 concurrent workers + pre-resolved getFile links
                    if (pendingDownloadChunks.isNotEmpty()) {
                        val downloadQueue = ConcurrentLinkedQueue(pendingDownloadChunks)
                        val preResolvedPaths = ConcurrentHashMap<Int, Pair<String, String>>() // chunkIndex -> (token, filePath)
                        val downloadWorkerCount = minOf(
                            3,
                            pendingDownloadChunks.size,
                            getEffectiveMaxConcurrency(context).coerceAtLeast(2)
                        )

                        coroutineScope {
                            // Pre-resolve Telegram getFile URLs in background for the initial batch
                            pendingDownloadChunks.take(4).forEach { preChunk ->
                                val preFileId = preChunk.telegramFileId
                                if (!preFileId.isNullOrBlank()) {
                                    launch(Dispatchers.IO) {
                                        try {
                                            val curTok = token ?: credentialsManager.getBotToken() ?: return@launch
                                            val info = repository.getFileInfo(curTok, preFileId)
                                            val path = info.getOrNull()?.filePath
                                            if (!path.isNullOrBlank()) {
                                                preResolvedPaths[preChunk.chunkIndex] = Pair(curTok, path)
                                            }
                                        } catch (_: Throwable) {}
                                    }
                                }
                            }

                            val workers = (0 until downloadWorkerCount).map {
                                launch(Dispatchers.IO) {
                                    while (isActive && !downloadQueue.isEmpty() && !pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId) && downloadFailedReason.get() == null) {
                                        val chunk = downloadQueue.poll() ?: break
                                        val chunkTempFile = File(downloadTempDir, "chunk_${chunk.chunkIndex}.part")

                                        // Pre-resolve the next chunk in queue while this worker downloads
                                        downloadQueue.peek()?.let { nextChunk ->
                                            val nextFileId = nextChunk.telegramFileId
                                            if (!nextFileId.isNullOrBlank() && !preResolvedPaths.containsKey(nextChunk.chunkIndex)) {
                                                launch(Dispatchers.IO) {
                                                    try {
                                                        val curTok = token ?: credentialsManager.getBotToken() ?: return@launch
                                                        val info = repository.getFileInfo(curTok, nextFileId)
                                                        val path = info.getOrNull()?.filePath
                                                        if (!path.isNullOrBlank()) {
                                                            preResolvedPaths[nextChunk.chunkIndex] = Pair(curTok, path)
                                                        }
                                                    } catch (_: Throwable) {}
                                                }
                                            }
                                        }

                                        if (cancelledFiles.contains(fileId) || pauseRequestedFiles.contains(fileId)) {
                                            downloadQueue.add(chunk)
                                            break
                                        }

                                        var remoteFileId = chunk.telegramFileId
                                        val chunkMessageId = chunk.telegramMessageId
                                        val chunkChannelId = chunk.channelId ?: fileEntity.channelId ?: credentialsManager.getChatId()

                                        val maxRetries = 5
                                        var attempt = 0
                                        var chunkSuccess = false
                                        var lastError: String? = null

                                        while (attempt < maxRetries && !chunkSuccess && isActive && !pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId) && downloadFailedReason.get() == null) {
                                            attempt++
                                            try {
                                                var currentToken = token ?: credentialsManager.getBotToken()
                                                    ?: throw IllegalStateException("No Telegram bot token available")

                                                // Check if we already pre-resolved (token, filePath) on attempt 1
                                                val preResolved = if (attempt == 1) preResolvedPaths.remove(chunk.chunkIndex) else null
                                                val remoteFilePath: String

                                                if (preResolved != null) {
                                                    currentToken = preResolved.first
                                                    remoteFilePath = preResolved.second
                                                } else {
                                                    if (remoteFileId.isNullOrBlank() && !chunkChannelId.isNullOrBlank() && chunkMessageId != null) {
                                                        val regenResult = repository.regenerateFileId(currentToken, chunkChannelId, chunkMessageId)
                                                        if (regenResult.isSuccess) {
                                                            val freshId = regenResult.getOrThrow()
                                                            remoteFileId = freshId
                                                            database.chunkDao().updateRemoteFileId(fileId, chunk.chunkIndex, freshId)
                                                        }
                                                    }

                                                    var activeFileId = remoteFileId
                                                        ?: throw IllegalStateException("Missing Telegram file_id for chunk ${chunk.chunkIndex} (messageId=$chunkMessageId, channelId=$chunkChannelId)")

                                                    val telegramGetFileLimit = 20 * 1024 * 1024L
                                                    if (chunk.size > telegramGetFileLimit) {
                                                        val limitMsg = "Telegram Bot API getFile download limit is 20MB. Chunk size of ${ChecksumUtil.formatBytes(chunk.size)} exceeds Telegram's limit."
                                                        throw IllegalStateException(limitMsg)
                                                    }

                                                    var fileInfoResult = repository.getFileInfo(currentToken, activeFileId)

                                                    if (fileInfoResult.isFailure) {
                                                        val rawEx = fileInfoResult.exceptionOrNull()
                                                        val rawMsg = rawEx?.message ?: "Unknown error"
                                                        val isAuthOrFileIdError = rawMsg.contains("file_id", ignoreCase = true) ||
                                                            rawMsg.contains("FILE_ID_INVALID", ignoreCase = true) ||
                                                            rawMsg.contains("Unauthorized", ignoreCase = true) ||
                                                            (rawEx is TelegramApiException && (rawEx.errorCode == 400 || rawEx.errorCode == 401 || rawEx.errorCode == 403))

                                                        if (isAuthOrFileIdError) {
                                                            if (rawMsg.contains("Unauthorized", ignoreCase = true) || (rawEx is TelegramApiException && (rawEx.errorCode == 401 || rawEx.errorCode == 403))) {
                                                                val nextToken = credentialsManager.rotateToNextToken()
                                                                if (nextToken != null && nextToken != currentToken) {
                                                                    token = nextToken
                                                                    currentToken = nextToken
                                                                }
                                                            }

                                                            if (!chunkChannelId.isNullOrBlank() && chunkMessageId != null) {
                                                                val regenResult = repository.regenerateFileId(currentToken, chunkChannelId, chunkMessageId)
                                                                if (regenResult.isSuccess) {
                                                                    val freshId = regenResult.getOrThrow()
                                                                    remoteFileId = freshId
                                                                    activeFileId = freshId
                                                                    database.chunkDao().updateRemoteFileId(fileId, chunk.chunkIndex, freshId)
                                                                    fileInfoResult = repository.getFileInfo(currentToken, freshId)
                                                                } else {
                                                                    val rotated = credentialsManager.rotateToNextToken()
                                                                    if (rotated != null && rotated != currentToken) {
                                                                        token = rotated
                                                                        currentToken = rotated
                                                                        val retryRegen = repository.regenerateFileId(currentToken, chunkChannelId, chunkMessageId)
                                                                        if (retryRegen.isSuccess) {
                                                                            val freshId = retryRegen.getOrThrow()
                                                                            remoteFileId = freshId
                                                                            activeFileId = freshId
                                                                            database.chunkDao().updateRemoteFileId(fileId, chunk.chunkIndex, freshId)
                                                                            fileInfoResult = repository.getFileInfo(currentToken, freshId)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (fileInfoResult.isFailure) {
                                                        val rawEx = fileInfoResult.exceptionOrNull()
                                                        val rawMsg = rawEx?.message ?: "Unknown error"
                                                        val extraInfo = if (rawMsg.contains("file is too big", ignoreCase = true)) {
                                                            " (Telegram Bot API getFile download limit is 20MB; chunk size is ${ChecksumUtil.formatBytes(chunk.size)})"
                                                        } else ""
                                                        throw IllegalStateException("[Download getFile] $rawMsg$extraInfo", rawEx)
                                                    }
                                                    remoteFilePath = fileInfoResult.getOrThrow().filePath
                                                        ?: throw IllegalStateException("[Download getFile] Telegram returned an empty file_path")
                                                }

                                                val existingPartialBytes = if (chunkTempFile.exists() && chunkTempFile.length() in 1 until chunk.size) {
                                                    chunkTempFile.length()
                                                } else {
                                                    if (chunkTempFile.exists()) chunkTempFile.delete()
                                                    0L
                                                }

                                                inProgressDownloadBytes[chunk.chunkIndex] = existingPartialBytes
                                                publishDownloadProgress(force = false)

                                                val streamResult = repository.downloadFileStreamWithRange(currentToken, remoteFilePath, existingPartialBytes)
                                                if (streamResult.isFailure) {
                                                    val rawEx = streamResult.exceptionOrNull()
                                                    throw IllegalStateException("[Download fileStream] ${rawEx?.message}", rawEx)
                                                }

                                                val downloadStream = streamResult.getOrThrow()
                                                val body = downloadStream.body
                                                val appendToPartial = downloadStream.isPartialContent && existingPartialBytes > 0L
                                                var chunkWritten = if (appendToPartial) existingPartialBytes else 0L

                                                val cancelCloseHandle = coroutineContext.job.invokeOnCompletion { cause ->
                                                    if (cause != null) {
                                                        try {
                                                            body.close()
                                                        } catch (_: Throwable) {}
                                                    }
                                                }

                                                try {
                                                    body.byteStream().use { input ->
                                                        FileOutputStream(chunkTempFile, appendToPartial).use { output ->
                                                            val buffer = ByteArray(64 * 1024)
                                                            var read: Int
                                                            while (input.read(buffer).also { read = it } != -1) {
                                                                if (!isActive || pauseRequestedFiles.contains(fileId) || cancelledFiles.contains(fileId) || downloadFailedReason.get() != null) {
                                                                    output.flush()
                                                                    throw CancellationException("Download paused or cancelled")
                                                                }
                                                                output.write(buffer, 0, read)
                                                                chunkWritten += read
                                                                inProgressDownloadBytes[chunk.chunkIndex] = minOf(chunkWritten, chunk.size)
                                                                publishDownloadProgress(force = false)
                                                            }
                                                            output.flush()
                                                        }
                                                    }
                                                } finally {
                                                    cancelCloseHandle.dispose()
                                                }

                                                val computedChunkSha256 = ChecksumUtil.computeSha256(chunkTempFile)
                                                if (computedChunkSha256 != chunk.checksum) {
                                                    chunkTempFile.delete()
                                                    throw IllegalStateException("Checksum mismatch on chunk ${chunk.chunkIndex}! Expected ${chunk.checksum}, got $computedChunkSha256")
                                                }

                                                chunkSuccess = true
                                            } catch (e: Throwable) {
                                                if (e is CancellationException || !isActive || pauseRequestedFiles.contains(fileId) || cancelledFiles.contains(fileId)) {
                                                    inProgressDownloadBytes.remove(chunk.chunkIndex)
                                                    throw CancellationException("Download paused or cancelled")
                                                }

                                                if (chunkTempFile.exists() && chunkTempFile.length() >= chunk.size) {
                                                    chunkTempFile.delete()
                                                }

                                                lastError = e.localizedMessage ?: "Download error"
                                                val isTransient = isTransientNetworkError(e)
                                                Log.w("TransferManager", "Download attempt $attempt/$maxRetries for chunk ${chunk.chunkIndex + 1}/${fileEntity.totalChunks} failed (isTransient=$isTransient): $lastError", e)

                                                repository.evictConnectionPool("Download chunk ${chunk.chunkIndex + 1} attempt $attempt failed")

                                                if (!isTransient) {
                                                    Log.e("TransferManager", "Permanent non-retryable error on download chunk ${chunk.chunkIndex + 1}: '$lastError'. Aborting retries immediately.")
                                                    break
                                                }

                                                if (attempt < maxRetries && !pauseRequestedFiles.contains(fileId) && !cancelledFiles.contains(fileId) && downloadFailedReason.get() == null) {
                                                    val backoffMs = when (attempt) {
                                                        1 -> 2000L
                                                        2 -> 4000L
                                                        3 -> 8000L
                                                        4 -> 16000L
                                                        else -> 30000L
                                                    }
                                                    val retryStatusMsg = "Reconnecting… retrying chunk ${chunk.chunkIndex + 1}/${fileEntity.totalChunks} (attempt $attempt/$maxRetries)"
                                                    publishDownloadProgress(force = true, errorMessage = retryStatusMsg)

                                                    var waited = 0L
                                                    while (waited < backoffMs) {
                                                        if (pauseRequestedFiles.contains(fileId) || cancelledFiles.contains(fileId) || downloadFailedReason.get() != null) break
                                                        val step = minOf(500L, backoffMs - waited)
                                                        delay(step)
                                                        waited += step
                                                    }
                                                }
                                            }
                                        }

                                        if (cancelledFiles.contains(fileId) || pauseRequestedFiles.contains(fileId) || !isActive) {
                                            inProgressDownloadBytes.remove(chunk.chunkIndex)
                                            throw CancellationException("Download paused or cancelled")
                                        }

                                        if (!chunkSuccess) {
                                            inProgressDownloadBytes.remove(chunk.chunkIndex)
                                            val failReason = "[Download Chunk ${chunk.chunkIndex + 1}/${fileEntity.totalChunks}] $lastError"
                                            downloadFailedReason.compareAndSet(null, failReason)
                                            break
                                        }

                                        inProgressDownloadBytes.remove(chunk.chunkIndex)
                                        database.chunkDao().markChunkDownloaded(fileId, chunk.chunkIndex)
                                        confirmedDownloadBytes.addAndGet(chunk.size)
                                        completedChunksAtomic.incrementAndGet()
                                        publishDownloadProgress(force = true)
                                    }
                                }
                            }
                            workers.forEach { it.join() }
                        }
                    }

                    if (cancelledFiles.contains(fileId) || pauseRequestedFiles.contains(fileId)) {
                        throw CancellationException("Download paused or cancelled")
                    }

                    val failReason = downloadFailedReason.get()
                    if (failReason != null) {
                        val (curDiskBytes, _) = computeTotalDownloadedBytesOnDisk(chunks, downloadTempDir, fileEntity.size)
                        database.fileDao().updateStatus(fileId, FileStatus.FAILED, failReason)
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = false,
                                currentChunk = completedChunksAtomic.get(),
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = if (fileEntity.size > 0) (curDiskBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f) else 0f,
                                bytesTransferred = curDiskBytes,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.FAILED,
                                errorMessage = failReason,
                                isEncrypted = fileEntity.isEncrypted
                            ),
                            forceStatus = true
                        )
                        _transferErrorEvents.tryEmit(failReason)
                        notifyService("Download failed: ${fileEntity.name}")
                        return@launch
                    }

                    // Concatenate all chunks sequentially into temporary reassembly file (decrypting AES-256-GCM if encrypted)
                    FileOutputStream(tempReassembledFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        for (chunk in chunks.sortedBy { it.chunkIndex }) {
                            val chunkTempFile = File(downloadTempDir, "chunk_${chunk.chunkIndex}.part")
                            val sourcePartFile = if (fileEntity.isEncrypted && !fileEntity.encryptionKeyBase64.isNullOrBlank()) {
                                val decFile = File(downloadTempDir, "chunk_${chunk.chunkIndex}.dec.part")
                                CryptoStreamEngine.decryptChunkFile(
                                    encryptedChunkFile = chunkTempFile,
                                    plainOutFile = decFile,
                                    keyBase64 = fileEntity.encryptionKeyBase64!!
                                )
                                chunkTempFile.delete()
                                decFile
                            } else {
                                chunkTempFile
                            }
                            FileInputStream(sourcePartFile).use { input ->
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                }
                            }
                            sourcePartFile.delete()
                        }
                    }

                    // Final end-to-end verification of overall file SHA-256
                    val finalFileChecksum = ChecksumUtil.computeSha256(tempReassembledFile)
                    if (finalFileChecksum != fileEntity.checksum) {
                        tempReassembledFile.delete()
                        throw IllegalStateException("Overall file integrity verification failed! Reassembled checksum did not match manifest.")
                    }

                    // Insert into dedicated Download/TGC folder via MediaStore Scoped Storage
                    val savedMediaUri = DownloadStorageManager.saveToDownloadsTgc(
                        context = context,
                        fileName = fileEntity.name,
                        mimeType = fileEntity.mimeType,
                        sourceFile = tempReassembledFile
                    )

                    // Success! Clean temp chunks and reassembly file
                    downloadTempDir.deleteRecursively()

                    val uriString = savedMediaUri.toString()
                    database.fileDao().markDownloaded(fileId, uriString, uriString)
                    _transferNotificationEvents.tryEmit("Saved to ${DownloadStorageManager.getDisplayPath(fileEntity.name)}")

                    val completedProgress = TransferProgress(
                        fileId = fileId,
                        fileName = fileEntity.name,
                        isUpload = false,
                        currentChunk = fileEntity.totalChunks,
                        totalChunks = fileEntity.totalChunks,
                        progressFraction = 1f,
                        bytesTransferred = fileEntity.size,
                        totalBytes = fileEntity.size,
                        speedBytesPerSec = 0L,
                        status = FileStatus.COMPLETED
                    )

                    // Remove from active transfers so it doesn't linger at 100%
                    _transfers.update { it - fileId }
                    // Add to recently completed list (capped at 5)
                    _recentlyCompleted.update { current ->
                        (listOf(completedProgress) + current.filter { it.fileId != fileId }).take(5)
                    }

                } catch (e: CancellationException) {
                    if (cancelledFiles.contains(fileId)) {
                        database.fileDao().updateStatus(fileId, FileStatus.COMPLETED, null)
                        _transfers.update { it - fileId }
                    } else {
                        val (persistedBytes, persistedCompleted) = computeTotalDownloadedBytesOnDisk(chunks, downloadTempDir, fileEntity.size)
                        database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused")
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = false,
                                currentChunk = (persistedCompleted + 1).coerceAtMost(fileEntity.totalChunks.coerceAtLeast(1)),
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = if (fileEntity.size > 0) (persistedBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f) else 0f,
                                bytesTransferred = persistedBytes,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.PAUSED,
                                completedChunksCount = persistedCompleted,
                                isEncrypted = fileEntity.isEncrypted
                            ),
                            forceStatus = true
                        )
                    }
                } catch (e: Exception) {
                    if (cancelledFiles.contains(fileId)) {
                        database.fileDao().updateStatus(fileId, FileStatus.COMPLETED, null)
                        _transfers.update { it - fileId }
                    } else if (pauseRequestedFiles.contains(fileId)) {
                        val (persistedBytes, persistedCompleted) = computeTotalDownloadedBytesOnDisk(chunks, downloadTempDir, fileEntity.size)
                        database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused")
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = false,
                                currentChunk = (persistedCompleted + 1).coerceAtMost(fileEntity.totalChunks.coerceAtLeast(1)),
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = if (fileEntity.size > 0) (persistedBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f) else 0f,
                                bytesTransferred = persistedBytes,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.PAUSED,
                                completedChunksCount = persistedCompleted,
                                isEncrypted = fileEntity.isEncrypted
                            ),
                            forceStatus = true
                        )
                    } else {
                        val (persistedBytes, persistedCompleted) = computeTotalDownloadedBytesOnDisk(chunks, downloadTempDir, fileEntity.size)
                        val err = e.message ?: "Download failed"
                        database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = false,
                                currentChunk = persistedCompleted,
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = if (fileEntity.size > 0) (persistedBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f) else 0f,
                                bytesTransferred = persistedBytes,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.FAILED,
                                errorMessage = err,
                                completedChunksCount = persistedCompleted,
                                isEncrypted = fileEntity.isEncrypted
                            ),
                            forceStatus = true
                        )
                        _transferErrorEvents.tryEmit(err)
                    }
                }
            } finally {
                activeJobs.remove(fileId, thisJob)
            }
        }
    }

    /**
     * Immediately pauses an active upload or download job and interrupts in-flight streams.
     */
    fun pauseTransfer(fileId: String) {
        cancelledFiles.remove(fileId)
        pauseRequestedFiles.add(fileId)
        uploadQueue.remove(fileId)
        // Immediately reflect Paused state in UI while preserving current byte progress
        _transfers.update { current ->
            val existing = current[fileId] ?: return@update current
            current + (fileId to existing.copy(
                status = FileStatus.PAUSED,
                speedBytesPerSec = 0L,
                etaSeconds = null,
                isChunking = false
            ))
        }
        // Cancel job immediately so in-flight stream closes while keeping reference until finally block unwinds
        activeJobs[fileId]?.cancel()
        scope.launch {
            database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused")
        }
    }

    /**
     * Pauses all active or pending transfers (both in-memory and Room-persisted).
     */
    fun pauseAll() {
        val queuedIds = mutableListOf<String>()
        while (true) {
            val id = uploadQueue.poll() ?: break
            queuedIds.add(id)
        }
        val activeIds = (_transfers.value.filter {
            it.value.status == FileStatus.UPLOADING ||
                    it.value.status == FileStatus.DOWNLOADING ||
                    it.value.status == FileStatus.PENDING
        }.keys + activeJobs.keys + queuedIds).distinct()
        for (id in activeIds) {
            pauseTransfer(id)
        }
        scope.launch {
            val dbActive = database.fileDao().getFilesByStatus(
                listOf(FileStatus.UPLOADING, FileStatus.DOWNLOADING, FileStatus.PENDING)
            )
            for (file in dbActive) {
                if (!cancelledFiles.contains(file.id)) {
                    pauseTransfer(file.id)
                }
            }
        }
    }

    /**
     * Resumes all paused or failed transfers (both in-memory and Room-persisted).
     */
    fun resumeAll() {
        val inMemoryPaused = _transfers.value.filter {
            it.value.status == FileStatus.PAUSED || it.value.status == FileStatus.FAILED
        }.values.toList()
        val resumedIds = mutableSetOf<String>()
        for (transfer in inMemoryPaused) {
            resumedIds.add(transfer.fileId)
            if (transfer.isUpload) {
                startUpload(transfer.fileId)
            } else {
                startDownload(transfer.fileId)
            }
        }
        scope.launch {
            val dbPaused = database.fileDao().getFilesByStatus(
                listOf(FileStatus.PAUSED, FileStatus.FAILED)
            )
            for (file in dbPaused) {
                if (resumedIds.add(file.id) && !cancelledFiles.contains(file.id)) {
                    val isUpload = file.manifestMessageId == null
                    if (isUpload) {
                        startUpload(file.id)
                    } else {
                        startDownload(file.id)
                    }
                }
            }
        }
    }

    /**
     * Cancels a transfer immediately:
     * - For downloads: stops stream, removes from transfer queue, restores cloud file status to COMPLETED in Room,
     *   and preserves already-downloaded .part chunks on disk for future resume.
     * - For uploads: stops workers, removes incomplete file from Room & queue immediately, cleans staging/temp files,
     *   and deletes any uploaded remote chunk messages in the background.
     */
    fun cancelTransfer(fileId: String) {
        val existingTransfer = _transfers.value[fileId]
        cancelledFiles.add(fileId)
        pauseRequestedFiles.remove(fileId)
        uploadQueue.remove(fileId)
        _transfers.update { it - fileId }
        _recentlyCompleted.update { current -> current.filter { it.fileId != fileId } }
        activeJobs[fileId]?.cancel()

        scope.launch {
            try {
                val file = database.fileDao().getById(fileId)
                val chunks = database.chunkDao().getChunksForFile(fileId)
                val isDownload = existingTransfer?.isUpload == false ||
                        (existingTransfer == null && file != null && (file.manifestMessageId != null || chunks.isNotEmpty() && chunks.all { it.isUploaded }))

                if (isDownload) {
                    // Restore cloud file status to COMPLETED so it stays in Vault and exits activeTransfers
                    database.fileDao().updateStatus(fileId, FileStatus.COMPLETED, null)
                    _transfers.update { it - fileId }

                    // Keep completed and partially downloaded .part chunks on disk for future resume; remove only reassembly/corrupted files
                    val downloadTempDir = File(context.cacheDir, "downloads_temp/$fileId")
                    if (downloadTempDir.exists()) {
                        val chunkSizesByIdx = chunks.associate { it.chunkIndex to it.size }
                        downloadTempDir.listFiles()?.forEach { tempFile ->
                            val name = tempFile.name
                            if (name.startsWith("assembled_") || name.endsWith(".dec.part")) {
                                tempFile.delete()
                            } else if (name.startsWith("chunk_") && name.endsWith(".part")) {
                                val idx = name.removePrefix("chunk_").removeSuffix(".part").toIntOrNull()
                                val expectedSize = idx?.let { chunkSizesByIdx[it] }
                                if (expectedSize == null || tempFile.length() == 0L || tempFile.length() > expectedSize) {
                                    tempFile.delete()
                                }
                            }
                        }
                    }
                } else {
                    // Immediately remove incomplete upload from Room so UI never re-emits it
                    database.chunkDao().deleteForFile(fileId)
                    database.fileDao().deleteById(fileId)
                    _transfers.update { it - fileId }

                    // Clean local staging and upload chunk files
                    file?.localPath?.let { path ->
                        if (!path.startsWith("content://")) {
                            val f = File(path)
                            if (f.exists() && f.absolutePath.contains(context.cacheDir.absolutePath)) {
                                f.delete()
                            }
                        }
                    }
                    File(context.cacheDir, "upload_staging/$fileId.tmp").delete()
                    File(context.cacheDir, "upload_chunks/$fileId").deleteRecursively()
                    File(context.cacheDir, "upload_manifests/$fileId.manifest.json").delete()

                    // Clean up any already-uploaded chunk messages on Telegram in the background
                    val token = credentialsManager.getBotToken()
                    val chatId = credentialsManager.getChatId()
                    if (!token.isNullOrBlank() && !chatId.isNullOrBlank()) {
                        file?.manifestMessageId?.let { manifestMsgId ->
                            repository.deleteMessage(token, chatId, manifestMsgId)
                        }
                        for (chunk in chunks) {
                            chunk.telegramMessageId?.let { msgId ->
                                repository.deleteMessage(token, chatId, msgId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TransferManager", "Error cleaning up cancelled transfer: ${e.message}")
            } finally {
                _transfers.update { it - fileId }
                _recentlyCompleted.update { current -> current.filter { it.fileId != fileId } }
            }
        }
    }

    /**
     * Clears the recently completed transfers history list.
     */
    fun clearRecentlyCompleted() {
        _recentlyCompleted.value = emptyList()
        _transfers.update { current -> current.filter { it.value.status != FileStatus.COMPLETED } }
    }

    /**
     * Cancels all active and queued transfers immediately.
     */
    fun cancelAllTransfers() {
        uploadQueue.clear()
        val allIds = (_transfers.value.keys + activeJobs.keys).toSet()
        for (fileId in allIds) {
            cancelTransfer(fileId)
        }
        activeJobs.clear()
        _transfers.value = emptyMap()
        _recentlyCompleted.value = emptyList()
    }

    /**
     * Deletes a file both locally and remotely from Telegram chat.
     */
    suspend fun deleteFile(fileId: String): Result<Unit> {
        return try {
            val token = credentialsManager.getBotToken()
            val chatId = credentialsManager.getChatId()
            val file = database.fileDao().getById(fileId)

            if (!token.isNullOrBlank() && !chatId.isNullOrBlank() && file != null) {
                // 1. Delete manifest message from Telegram chat
                file.manifestMessageId?.let { manifestMsgId ->
                    repository.deleteMessage(token, chatId, manifestMsgId)
                }

                // 2. Delete all chunk messages from Telegram chat
                val chunks = database.chunkDao().getChunksForFile(fileId)
                for (chunk in chunks) {
                    chunk.telegramMessageId?.let { msgId ->
                        repository.deleteMessage(token, chatId, msgId)
                    }
                }
            }

            // 3. Delete local physical files / MediaStore entry
            val targetUriString = file?.localUri ?: file?.localPath
            if (targetUriString != null) {
                if (targetUriString.startsWith("content://")) {
                    try {
                        context.contentResolver.delete(Uri.parse(targetUriString), null, null)
                    } catch (delEx: Exception) {
                        Log.w("TransferManager", "Could not delete MediaStore item: ${delEx.message}")
                    }
                } else {
                    val f = File(targetUriString)
                    if (f.exists()) f.delete()
                }
            }

            // 4. Clean temp staging, discrete chunks, manifest files, and download directories
            File(context.cacheDir, "upload_staging/$fileId.tmp").delete()
            File(context.cacheDir, "upload_chunks/$fileId").deleteRecursively()
            File(context.cacheDir, "upload_manifests/$fileId.manifest.json").delete()
            File(context.cacheDir, "downloads_temp/$fileId").deleteRecursively()

            // 5. Delete Room metadata (cascades to chunks)
            database.fileDao().deleteById(fileId)
            _transfers.update { it - fileId }

            // Debounced auto-publish updated VaultIndex to Telegram
            VaultSyncManager.getInstance(context).scheduleAutoPublish()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Forces a completely clean retry for a file: cancels any ongoing transfer,
     * deletes stale chunk files and stale temp downloads from disk, purges old chunk records
     * from Room, recalculates safe chunk sizing (<= 19MB for Telegram 20MB getFile compatibility),
     * generates fresh discrete chunk files on disk, and starts a fresh upload.
     */
    suspend fun forceFreshUpload(fileId: String) = withContext(Dispatchers.IO) {
        val fileEntity = database.fileDao().getById(fileId)
        if (fileEntity == null) {
            Log.e("TransferManager", "forceFreshUpload: File $fileId not found in database")
            return@withContext
        }

        Log.i("TransferManager", "forceFreshUpload: Starting fresh clean split-and-upload for fileId=$fileId (${fileEntity.name})")

        // 1. Cancel running transfer job
        cancelTransfer(fileId)

        // 2. Delete stale chunk files and download temp files from disk
        val chunksDir = File(context.cacheDir, "upload_chunks/$fileId")
        if (chunksDir.exists()) {
            val deleted = chunksDir.deleteRecursively()
            Log.i("TransferManager", "forceFreshUpload: Purged stale chunks dir: $deleted")
        }
        val downloadTempDir = File(context.cacheDir, "downloads_temp/$fileId")
        if (downloadTempDir.exists()) {
            downloadTempDir.deleteRecursively()
        }

        // 3. Purge old chunk records from Room database
        database.chunkDao().deleteForFile(fileId)

        // 4. Locate source/staging file
        val stagingFile = fileEntity.localPath?.let { File(it) } ?: File(context.cacheDir, "upload_staging/$fileId.tmp")
        if (!stagingFile.exists() || stagingFile.length() == 0L) {
            val errMsg = "Cannot force fresh upload: Staging file not found at ${stagingFile.absolutePath}. Please re-select the file to upload."
            Log.e("TransferManager", errMsg)
            database.fileDao().updateStatus(fileId, FileStatus.FAILED, errMsg)
            _transferErrorEvents.tryEmit(errMsg)
            return@withContext
        }

        val actualSize = stagingFile.length()
        val overallChecksum = ChecksumUtil.computeSha256(stagingFile)

        // 5. Calculate chunk sizing: must be <= 18MB so Telegram can both upload and download via getFile
        val maxChunkSize = minOf(credentialsManager.getChunkSizeMb() * 1024 * 1024L, CHUNK_SIZE_BYTES)
        val totalChunks = ((actualSize + maxChunkSize - 1) / maxChunkSize).toInt().coerceAtLeast(1)
        val targetChunkSize = ((actualSize + totalChunks - 1) / totalChunks).coerceAtLeast(1L)

        Log.i(
            "TransferManager",
            "forceFreshUpload: Re-chunking ${fileEntity.name}: actualSize=$actualSize bytes (${ChecksumUtil.formatBytes(actualSize)}), " +
            "totalChunks=$totalChunks, targetChunkSize=$targetChunkSize bytes (${ChecksumUtil.formatBytes(targetChunkSize)})"
        )

        // 6. Pre-generate fresh chunk entities by computing SHA-256 directly from staging file range
        chunksDir.mkdirs()
        val chunkEntities = mutableListOf<ChunkEntity>()
        for (i in 0 until totalChunks) {
            val offset = i * targetChunkSize
            val chunkLength = minOf(targetChunkSize, actualSize - offset)
            val chunkSha256 = ChecksumUtil.computeSha256Range(stagingFile, offset, chunkLength)

            Log.i(
                "TransferManager",
                "forceFreshUpload: Chunk metadata $i of $totalChunks: length=$chunkLength, sha256=$chunkSha256"
            )

            chunkEntities.add(
                ChunkEntity(
                    fileId = fileId,
                    chunkIndex = i,
                    size = chunkLength,
                    checksum = chunkSha256,
                    telegramMessageId = null,
                    telegramFileId = null,
                    isUploaded = false,
                    isDownloaded = false
                )
            )
        }

        // 7. Insert newly generated chunk entities
        database.chunkDao().insertAll(chunkEntities)

        // 8. Update FileEntity in database
        val updatedFile = fileEntity.copy(
            size = actualSize,
            checksum = overallChecksum,
            totalChunks = totalChunks,
            completedChunks = 0,
            status = FileStatus.PENDING,
            errorMessage = null,
            manifestMessageId = null
        )
        database.fileDao().update(updatedFile)

        // 9. Launch fresh upload
        startUpload(fileId)
    }

    /**
     * Rebuilds local index cache from Telegram chat messages using VaultSyncManager.
     */
    suspend fun resyncFromTelegram(): Result<Int> {
        val syncResult = VaultSyncManager.getInstance(context).syncVault()
        return if (syncResult.isSuccess) {
            val result = syncResult.getOrThrow()
            Result.success(result.newFilesCount)
        } else {
            Result.failure(syncResult.exceptionOrNull() ?: Exception("Sync failed"))
        }
    }

    private fun updateProgressState(progress: TransferProgress, forceStatus: Boolean = false) {
        if (cancelledFiles.contains(progress.fileId)) {
            _transfers.update { it - progress.fileId }
            return
        }
        if (!forceStatus && pauseRequestedFiles.contains(progress.fileId) &&
            (progress.status == FileStatus.UPLOADING || progress.status == FileStatus.DOWNLOADING)
        ) {
            return
        }
        _transfers.update { current ->
            if (cancelledFiles.contains(progress.fileId)) {
                current - progress.fileId
            } else if (!forceStatus && pauseRequestedFiles.contains(progress.fileId) &&
                (progress.status == FileStatus.UPLOADING || progress.status == FileStatus.DOWNLOADING)
            ) {
                current
            } else {
                current + (progress.fileId to progress)
            }
        }
    }

    private fun notifyService(content: String) {
        try {
            val intent = Intent(context, TransferService::class.java).apply {
                action = TransferService.ACTION_UPDATE_STATUS
                putExtra(TransferService.EXTRA_MESSAGE, content)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.w("TransferManager", "Failed to start TransferService foreground intent: ${e.message}")
        }
    }

    private fun resolveUriMetadata(uri: Uri): Pair<String, Long> {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx != -1) {
                    name = cursor.getString(nameIdx) ?: name
                }
                if (sizeIdx != -1) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        return Pair(name, size)
    }

    /**
     * Fast non-blocking check of on-disk partial/completed download bytes for a given fileId.
     */
    fun getSavedDownloadBytesOnDisk(fileId: String, totalFileSize: Long): Long {
        val downloadTempDir = File(context.cacheDir, "downloads_temp/$fileId")
        if (!downloadTempDir.exists()) return 0L
        val sum = downloadTempDir.listFiles()?.sumOf { f ->
            if (f.name.startsWith("chunk_") && f.name.endsWith(".part")) f.length() else 0L
        } ?: 0L
        return sum.coerceAtMost(totalFileSize)
    }

    private fun computeChunkHash(file: File, offset: Long, length: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var remaining = length
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read == -1) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Determines the maximum concurrent chunk uploads allowed based on device thermal status,
     * power save mode, and available system memory. Automatically drops from 3 to 1 under system load.
     */
    fun getEffectiveMaxConcurrency(context: Context): Int {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                if (powerManager.isPowerSaveMode) {
                    return 1
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val thermal = powerManager.currentThermalStatus
                    if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
                        return 1
                    }
                }
            }
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                if (memInfo.lowMemory || memInfo.availMem < 150 * 1024 * 1024L) {
                    return 1
                }
            }
        } catch (_: Exception) {}
        return 3
    }

    /**
     * Writes a discrete chunk file on disk for the given chunk index.
     * Computes the chunk's SHA-256 digest in the exact same pass using a single reusable 64KB buffer,
     * avoiding redundant disk reads and unnecessary object allocation.
     */
    internal fun writeChunkFileOnDisk(
        stagingFile: File,
        chunksDir: File,
        chunkIndex: Int,
        offset: Long,
        chunkLength: Long
    ): Pair<File, String> {
        chunksDir.mkdirs()
        val chunkFile = File(chunksDir, "chunk_$chunkIndex.tpart")
        if (chunkFile.exists()) {
            Log.d("TransferManager", "Deleting stale chunk file before re-writing: ${chunkFile.absolutePath}")
            chunkFile.delete()
        }

        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(stagingFile, "r").use { raf ->
            raf.seek(offset)
            FileOutputStream(chunkFile, false).use { output ->
                var remaining = chunkLength
                val buffer = ByteArray(minOf(64 * 1024, remaining.toInt().coerceAtLeast(1024)))
                while (remaining > 0) {
                    val bytesToRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = raf.read(buffer, 0, bytesToRead)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
                output.flush()
            }
        }

        val actualLengthOnDisk = chunkFile.length()
        if (actualLengthOnDisk != chunkLength) {
            chunkFile.delete()
            throw IllegalStateException(
                "Chunk $chunkIndex on-disk size mismatch! Target: $chunkLength bytes, Actual: $actualLengthOnDisk bytes"
            )
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        return Pair(chunkFile, sha256)
    }

    /**
     * Identifies whether a failure is a transient network/connection error that should be auto-retried with backoff,
     * as opposed to a permanent error (e.g. 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, or file too large)
     * which must fail immediately without wasting retries.
     */
    fun isTransientNetworkError(t: Throwable?): Boolean {
        if (t == null) return false
        if (t is SocketTimeoutException ||
            t is ConnectException ||
            t is UnknownHostException ||
            t is NoRouteToHostException ||
            t is PortUnreachableException ||
            t is InterruptedIOException ||
            t is SSLHandshakeException ||
            t is SSLException) return true

        if (t is TelegramApiException) {
            // Rate limit / Flood control (429) or server errors (5xx) are transient!
            if (t.errorCode == 429 || (t.errorCode != null && t.errorCode in 500..599)) {
                return true
            }
            // 4xx client errors (400, 401, 403, 404) are permanent
            if (t.errorCode != null && t.errorCode in 400..499) {
                return false
            }
        }

        val msg = t.message ?: ""
        val lower = msg.lowercase()
        if (lower.contains("network connection failed") ||
            lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("connection reset") ||
            lower.contains("broken pipe") ||
            lower.contains("unexpected end of stream") ||
            lower.contains("failed to connect") ||
            lower.contains("software caused connection abort") ||
            lower.contains("no route to host") ||
            lower.contains("ssl handshake") ||
            lower.contains("unable to resolve host") ||
            lower.contains("connection closed") ||
            lower.contains("connection abort")) {
            return true
        }

        val cause = t.cause
        if (cause != null && cause != t) {
            return isTransientNetworkError(cause)
        }
        return false
    }
}
