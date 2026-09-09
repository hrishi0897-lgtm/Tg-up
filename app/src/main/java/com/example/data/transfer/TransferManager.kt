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
import com.example.domain.DownloadStorageManager
import com.example.domain.RollingSpeedEstimator
import com.example.domain.StorageUtil
import com.example.domain.model.TransferProgress
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
                    val isUpload = chunks.any { !it.isUploaded }
                    val completedChunks = if (isUpload) {
                        chunks.count { it.isUploaded }
                    } else {
                        chunks.count { it.isDownloaded }
                    }
                    val bytesTransferred = if (isUpload) {
                        chunks.filter { it.isUploaded }.sumOf { it.size }
                    } else {
                        chunks.filter { it.isDownloaded }.sumOf { it.size }
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
                        errorMessage = file.errorMessage
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
     * Prepares and starts a chunked upload from a content Uri.
     */
    fun enqueueUpload(uri: Uri, folderId: String?, customChunkSizeBytes: Long? = null): String {
        val fileId = UUID.randomUUID().toString()
        scope.launch {
            try {
                // 1. Resolve file name and size from content provider
                val (fileName, fileSize) = resolveUriMetadata(uri)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                // Check device storage before copying and splitting
                if (fileSize > 0) {
                    val initialStorageCheck = StorageUtil.checkStorageForUpload(context, fileSize, stagingFileExists = false)
                    if (!initialStorageCheck.isSufficient) {
                        val errMsg = initialStorageCheck.errorMessage ?: "Insufficient device storage for upload"
                        Log.e("TransferManager", "Storage check failed before copying: $errMsg")
                        _transferErrorEvents.tryEmit(errMsg)
                        throw IllegalStateException(errMsg)
                    }
                }

                // 2. Cache Uri stream into a local staging file for safe random-access chunking
                val stagingDir = File(context.cacheDir, "upload_staging").apply { mkdirs() }
                val stagingFile = File(stagingDir, "$fileId.tmp")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(stagingFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Unable to read selected file stream")

                val actualSize = stagingFile.length()

                // Re-verify storage capacity with actual size on disk
                val actualStorageCheck = StorageUtil.checkStorageForUpload(context, actualSize, stagingFileExists = true)
                if (!actualStorageCheck.isSufficient) {
                    stagingFile.delete()
                    val errMsg = actualStorageCheck.errorMessage ?: "Insufficient storage for chunking"
                    Log.e("TransferManager", "Storage check failed after caching staging file: $errMsg")
                    _transferErrorEvents.tryEmit(errMsg)
                    throw IllegalStateException(errMsg)
                }

                val overallChecksum = withContext(Dispatchers.Default) {
                    ChecksumUtil.computeSha256(stagingFile)
                }

                // 3. Compute chunk count based on global safe chunk size CHUNK_SIZE_BYTES (18MB) or custom override
                // and compute balanced target chunk size (total file size divided by number of chunks)
                val maxChunkSize = customChunkSizeBytes ?: minOf(credentialsManager.getChunkSizeMb() * 1024 * 1024L, CHUNK_SIZE_BYTES)
                val totalChunks = ((actualSize + maxChunkSize - 1) / maxChunkSize).toInt().coerceAtLeast(1)
                val targetChunkSize = ((actualSize + totalChunks - 1) / totalChunks).coerceAtLeast(1L)

                Log.i(
                    "TransferManager",
                    "Enqueueing upload for $fileName: actualSize=$actualSize bytes (${ChecksumUtil.formatBytes(actualSize)}), " +
                    "totalChunks=$totalChunks, targetChunkSize=$targetChunkSize bytes (${ChecksumUtil.formatBytes(targetChunkSize)})"
                )

                // 4. Register file in Room database
                val fileEntity = FileEntity(
                    id = fileId,
                    name = fileName,
                    folderId = folderId,
                    size = actualSize,
                    mimeType = mimeType,
                    status = FileStatus.PENDING,
                    checksum = overallChecksum,
                    totalChunks = totalChunks,
                    completedChunks = 0,
                    localPath = stagingFile.absolutePath
                )
                database.fileDao().insert(fileEntity)

                // Pre-populate in-memory transfers flow immediately with PENDING state
                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = fileName,
                        isUpload = true,
                        currentChunk = 1,
                        totalChunks = totalChunks,
                        progressFraction = 0f,
                        bytesTransferred = 0L,
                        totalBytes = actualSize,
                        speedBytesPerSec = 0L,
                        status = FileStatus.PENDING
                    )
                )

                // 5. Clean up any stale chunk files from a previous attempt
                val chunksDir = File(context.cacheDir, "upload_chunks/$fileId")
                if (chunksDir.exists()) {
                    Log.d("TransferManager", "Cleaning up stale chunk directory for fileId=$fileId: ${chunksDir.absolutePath}")
                    chunksDir.deleteRecursively()
                }
                chunksDir.mkdirs()

                // Pre-generate chunk entities in database.
                // Discrete chunk files and their individual SHA-256 digests are computed strictly on-demand
                // as each chunk is written to disk for upload to avoid multiple read passes over large files.
                val chunkEntities = mutableListOf<ChunkEntity>()
                for (i in 0 until totalChunks) {
                    val offset = i * targetChunkSize
                    val chunkLength = minOf(targetChunkSize, (actualSize - offset).coerceAtLeast(0L))

                    chunkEntities.add(
                        ChunkEntity(
                            fileId = fileId,
                            chunkIndex = i,
                            checksum = "", // Computed on-demand when writing chunk file
                            size = chunkLength,
                            isUploaded = false
                        )
                    )
                }
                database.chunkDao().insertAll(chunkEntities)

                // 6. Launch the upload
                startUpload(fileId)

            } catch (e: Throwable) {
                val errMsg = e.message ?: "Upload preparation failed (${e::class.java.simpleName})"
                Log.e("TransferManager", "Failed during enqueueUpload: $errMsg", e)
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, errMsg)
                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = "Upload",
                        isUpload = true,
                        currentChunk = 1,
                        totalChunks = 1,
                        progressFraction = 0f,
                        bytesTransferred = 0L,
                        totalBytes = 0L,
                        speedBytesPerSec = 0L,
                        status = FileStatus.FAILED,
                        errorMessage = errMsg
                    )
                )
                _transferErrorEvents.tryEmit(errMsg)
            }
        }
        return fileId
    }

    /**
     * Executes or resumes a chunked upload.
     */
    fun startUpload(fileId: String) {
        pauseRequestedFiles.remove(fileId)
        val job = scope.launch {
            val fileEntity = database.fileDao().getById(fileId)
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
                return@launch
            }

            if (fileEntity == null) return@launch

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
                        errorMessage = wifiError
                    )
                )
                _transferErrorEvents.tryEmit(wifiError)
                return@launch
            }

            val stagingFile = fileEntity.localPath?.let { File(it) } ?: File(context.cacheDir, "upload_staging/$fileId.tmp")
            if (!stagingFile.exists() || stagingFile.length() == 0L) {
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
                        errorMessage = errorMsg
                    )
                )
                _transferErrorEvents.tryEmit(errorMsg)
                return@launch
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
                errorMessage: String? = null
            ) {
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
                        completedChunksCount = completed
                    )
                )
            }

            database.fileDao().updateStatus(fileId, FileStatus.UPLOADING)
            publishProgress(force = true, status = FileStatus.UPLOADING)
            notifyService("Uploading ${fileEntity.name}")

            val chunksDir = File(context.cacheDir, "upload_chunks/$fileId").apply { mkdirs() }
            val incompleteChunks = chunks.filter { !it.isUploaded }.sortedBy { it.chunkIndex }
            val chunkQueue = ConcurrentLinkedQueue(incompleteChunks)

            try {
                if (incompleteChunks.isNotEmpty()) {
                    coroutineScope {
                        val workers = (0 until 3).map { workerId ->
                            launch(Dispatchers.IO) {
                                while (isActive && !chunkQueue.isEmpty() && !pauseRequestedFiles.contains(fileId) && uploadFailedReason.get() == null) {
                                    // Dynamic load & thermal-aware concurrency: drop from 3 to 1 under system load
                                    val allowedConcurrency = getEffectiveMaxConcurrency(context)
                                    if (workerId >= allowedConcurrency) {
                                        delay(300L)
                                        continue
                                    }

                                    val chunk = chunkQueue.poll() ?: break
                                    val chunkIndex = chunk.chunkIndex
                                    val totalChunks = fileEntity.totalChunks
                                    val offset = chunks.filter { it.chunkIndex < chunkIndex }.sumOf { it.size }
                                    val chunkLength = chunk.size

                                    inProgressBytesMap[chunkIndex] = 0L
                                    publishProgress(force = false)

                                    if (pauseRequestedFiles.contains(fileId)) {
                                        inProgressBytesMap.remove(chunkIndex)
                                        chunkQueue.add(chunk)
                                        break
                                    }

                                    val chunkFile = File(chunksDir, "chunk_$chunkIndex.tpart")
                                    val (readyChunkFile, localSha256) = if (!chunkFile.exists() || chunkFile.length() != chunkLength) {
                                        if (chunkFile.exists()) chunkFile.delete()
                                        writeChunkFileOnDisk(stagingFile, chunksDir, chunkIndex, offset, chunkLength)
                                    } else {
                                        val existingSha256 = if (chunk.checksum.isNotEmpty()) chunk.checksum else ChecksumUtil.computeSha256(chunkFile)
                                        Pair(chunkFile, existingSha256)
                                    }

                                    val actualFileSizeOnDisk = readyChunkFile.length()
                                    val expectedChunkSize = ((fileEntity.size + totalChunks - 1) / totalChunks).coerceAtLeast(1L)

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

                                    while (attempt < maxRetries && !chunkSuccess && isActive && !pauseRequestedFiles.contains(fileId) && uploadFailedReason.get() == null) {
                                        attempt++
                                        // Reset this chunk's in-progress bytes for retry
                                        inProgressBytesMap[chunkIndex] = 0L
                                        publishProgress(force = false)

                                        if (pauseRequestedFiles.contains(fileId)) break

                                        val uploadResult = repository.uploadChunk(
                                            token = token,
                                            chatId = chatId,
                                            fileId = fileId,
                                            fileName = fileEntity.name,
                                            chunkIndex = chunkIndex,
                                            totalChunks = fileEntity.totalChunks,
                                            chunkFile = readyChunkFile,
                                            chunkSha256 = localSha256,
                                            expectedChunkSize = expectedChunkSize
                                        ) { bytesWritten, _ ->
                                            inProgressBytesMap[chunkIndex] = bytesWritten
                                            publishProgress(force = false)
                                        }

                                        if (uploadResult.isSuccess) {
                                            chunkSuccess = true
                                            uploadedMessage = uploadResult.getOrThrow()
                                        } else {
                                            val exception = uploadResult.exceptionOrNull()
                                            lastError = exception?.message ?: "Network error"
                                            val isTransient = isTransientNetworkError(exception)
                                            Log.w("TransferManager", "Chunk upload attempt $attempt/$maxRetries for chunk ${chunkIndex + 1}/$totalChunks failed (isTransient=$isTransient): $lastError", exception)

                                            // Evict connection pool so retries use a clean, verified TCP socket
                                            repository.evictConnectionPool("Chunk ${chunkIndex + 1} upload attempt $attempt failed")

                                            if (!isTransient) {
                                                Log.e("TransferManager", "Permanent non-retryable error on chunk ${chunkIndex + 1}: '$lastError'. Aborting retries.")
                                                break
                                            }

                                            if (attempt < maxRetries && !pauseRequestedFiles.contains(fileId) && uploadFailedReason.get() == null) {
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
                                                while (waited < backoffMs && !pauseRequestedFiles.contains(fileId) && uploadFailedReason.get() == null) {
                                                    val step = minOf(500L, backoffMs - waited)
                                                    delay(step)
                                                    waited += step
                                                }
                                            }
                                        }
                                    }

                                    if (pauseRequestedFiles.contains(fileId)) {
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

                                    // Persist chunk upload success in Room immediately
                                    database.chunkDao().markChunkUploaded(
                                        fileId = fileId,
                                        chunkIndex = chunkIndex,
                                        messageId = uploadedMessage.messageId,
                                        fileIdRemote = remoteFileId,
                                        checksum = localSha256
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

                // Check pause condition
                if (pauseRequestedFiles.contains(fileId)) {
                    pauseRequestedFiles.remove(fileId)
                    database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused by user")
                    publishProgress(force = true, status = FileStatus.PAUSED)
                    return@launch
                }

                // Check failure condition
                val failure = uploadFailedReason.get()
                if (failure != null) {
                    Log.e("TransferManager", "Marking upload for $fileId (${fileEntity.name}) as FAILED. Reason: $failure")
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, failure)
                    publishProgress(force = true, status = FileStatus.FAILED, errorMessage = failure)
                    _transferErrorEvents.tryEmit(failure)
                    notifyService("Upload failed: ${fileEntity.name}")
                    return@launch
                }

                // Verify all chunks are done
                val remainingCount = database.chunkDao().getChunksForFile(fileId).count { !it.isUploaded }
                if (remainingCount > 0) {
                    val errMsg = "Upload incomplete: $remainingCount chunks remaining"
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, errMsg)
                    publishProgress(force = true, status = FileStatus.FAILED, errorMessage = errMsg)
                    _transferErrorEvents.tryEmit(errMsg)
                    notifyService("Upload failed: ${fileEntity.name}")
                    return@launch
                }

                // All chunks successfully uploaded!
                // Construct and upload the reassembly Manifest
                val updatedChunks = database.chunkDao().getChunksForFile(fileId)
                val manifestChunks = updatedChunks.map { c ->
                    ManifestChunk(
                        index = c.chunkIndex,
                        messageId = c.telegramMessageId ?: 0L,
                        telegramFileId = c.telegramFileId,
                        sha256 = c.checksum,
                        size = c.size
                    )
                }

                val manifest = FileManifest(
                    fileId = fileId,
                    name = fileEntity.name,
                    size = fileEntity.size,
                    mimeType = fileEntity.mimeType,
                    overallSha256 = fileEntity.checksum,
                    folderId = fileEntity.folderId,
                    uploadDate = System.currentTimeMillis(),
                    chunks = manifestChunks
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
                    publishProgress(force = true, status = FileStatus.FAILED, errorMessage = err)
                    _transferErrorEvents.tryEmit(err)
                    notifyService("Manifest upload failed: ${fileEntity.name}")
                    return@launch
                }

                // Clean up temporary manifest file on disk
                manifestFile.delete()

                val manifestMessage = manifestResult.getOrThrow()
                database.fileDao().updateManifestId(fileId, manifestMessage.messageId)
                database.fileDao().updateStatus(fileId, FileStatus.COMPLETED)

                // Debounced auto-publish fresh VaultIndex to Telegram so other devices stay in sync
                VaultSyncManager.getInstance(context).scheduleAutoPublish()

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
                    status = FileStatus.COMPLETED
                )

                // Remove from active transfers so it doesn't linger at 100%
                _transfers.update { it - fileId }
                // Add to recently completed list (capped at 5)
                _recentlyCompleted.update { current ->
                    (listOf(completedProgress) + current.filter { it.fileId != fileId }).take(5)
                }

                // Safe cleanup of temporary staging file and discrete chunks directory
                stagingFile.delete()
                File(context.cacheDir, "upload_chunks/$fileId").deleteRecursively()

            } catch (e: CancellationException) {
                Log.i("TransferManager", "Upload paused for fileId=$fileId")
                database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Upload paused by user")
            } catch (e: Throwable) {
                val err = e.message ?: "Upload failed (${e::class.java.simpleName})"
                Log.e("TransferManager", "Fatal error during upload for $fileId (${fileEntity.name}): $err", e)
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                publishProgress(force = true, status = FileStatus.FAILED, errorMessage = err)
                _transferErrorEvents.tryEmit(err)
            } finally {
                activeJobs.remove(fileId)
            }
        }
        activeJobs[fileId] = job
    }

    /**
     * Executes or resumes a chunked download and reassembles the final file.
     */
    fun startDownload(fileId: String) {
        pauseRequestedFiles.remove(fileId)
        val job = scope.launch {
            val token = credentialsManager.getBotToken()
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
                    )
                )
                _transferErrorEvents.tryEmit(err)
                return@launch
            }

            val fileEntity = database.fileDao().getById(fileId) ?: return@launch

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
                        currentChunk = fileEntity.completedChunks,
                        totalChunks = fileEntity.totalChunks,
                        progressFraction = if (fileEntity.totalChunks > 0) (fileEntity.completedChunks.toFloat() / fileEntity.totalChunks.toFloat()) else 0f,
                        bytesTransferred = 0L,
                        totalBytes = fileEntity.size,
                        speedBytesPerSec = 0L,
                        status = FileStatus.PAUSED,
                        errorMessage = wifiError
                    )
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
                        currentChunk = 0,
                        totalChunks = fileEntity.totalChunks,
                        progressFraction = 0f,
                        bytesTransferred = 0L,
                        totalBytes = fileEntity.size,
                        speedBytesPerSec = 0L,
                        status = FileStatus.FAILED,
                        errorMessage = storageError
                    )
                )
                _transferErrorEvents.tryEmit(storageError)
                return@launch
            }

            database.fileDao().updateStatus(fileId, FileStatus.DOWNLOADING)
            notifyService("Downloading ${fileEntity.name}")

            var chunks = database.chunkDao().getChunksForFile(fileId)
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
                    )
                )
                _transferErrorEvents.tryEmit(err)
                return@launch
            }

            val downloadTempDir = File(context.cacheDir, "downloads_temp/$fileId").apply { mkdirs() }
            val tempReassembledFile = File(downloadTempDir, "assembled_${fileEntity.name}")

            var downloadedBytes = chunks.filter { it.isDownloaded }.sumOf { it.size }
            var completedChunks = chunks.count { it.isDownloaded }

            val speedEstimator = RollingSpeedEstimator(windowDurationMs = 1800L)
            speedEstimator.addSample(System.currentTimeMillis(), downloadedBytes)

            try {
                // Download each chunk in sequence
                for (chunk in chunks) {
                    val chunkTempFile = File(downloadTempDir, "chunk_${chunk.chunkIndex}.part")

                    // Resume check: if chunk file already exists with valid hash or marked downloaded, skip
                    if (chunk.isDownloaded || (chunkTempFile.exists() && chunkTempFile.length() == chunk.size)) {
                        val existingHash = if (chunkTempFile.exists()) ChecksumUtil.computeSha256(chunkTempFile) else chunk.checksum
                        if (existingHash == chunk.checksum) {
                            if (!chunk.isDownloaded) {
                                database.chunkDao().markChunkDownloaded(fileId, chunk.chunkIndex)
                                downloadedBytes += chunk.size
                                completedChunks++
                            }
                            continue
                        }
                    }

                    // Pause check at chunk boundary before starting chunk download
                    if (pauseRequestedFiles.contains(fileId)) {
                        pauseRequestedFiles.remove(fileId)
                        database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused by user")
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = false,
                                currentChunk = completedChunks,
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = (downloadedBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                                bytesTransferred = downloadedBytes,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.PAUSED
                            )
                        )
                        return@launch
                    }

                    // Obtain Telegram file path and download with up to 3 retries
                    val remoteFileId = chunk.telegramFileId
                        ?: throw IllegalStateException("Missing Telegram file_id for chunk ${chunk.chunkIndex}")

                    val maxRetries = 5
                    var attempt = 0
                    var chunkSuccess = false
                    var lastError: String? = null

                    while (attempt < maxRetries && !chunkSuccess) {
                        attempt++
                        try {
                            // Check chunk size against Telegram Bot API's 20MB getFile download limit
                            val telegramGetFileLimit = 20 * 1024 * 1024L
                            if (chunk.size > telegramGetFileLimit) {
                                val limitMsg = "Telegram Bot API getFile download limit is 20MB. Chunk size of ${ChecksumUtil.formatBytes(chunk.size)} exceeds Telegram's limit. Chunks must be <= 18MB to download with a bot. Please delete and re-upload this file."
                                Log.e("TransferManager", "[Download getFile] $limitMsg")
                                throw IllegalStateException(limitMsg)
                            }

                            val fileInfoResult = repository.getFileInfo(token, remoteFileId)
                            if (fileInfoResult.isFailure) {
                                val rawEx = fileInfoResult.exceptionOrNull()
                                val rawMsg = rawEx?.message ?: "Unknown error"
                                val extraInfo = if (rawMsg.contains("file is too big", ignoreCase = true)) {
                                    " (Telegram Bot API getFile download limit is 20MB; chunk size is ${ChecksumUtil.formatBytes(chunk.size)})"
                                } else ""
                                throw IllegalStateException("[Download getFile] $rawMsg$extraInfo", rawEx)
                            }
                            val remoteFilePath = fileInfoResult.getOrThrow().filePath
                                ?: throw IllegalStateException("[Download getFile] Telegram returned an empty file_path")

                            val streamResult = repository.downloadFileStream(token, remoteFilePath)
                            if (streamResult.isFailure) {
                                val rawEx = streamResult.exceptionOrNull()
                                throw IllegalStateException("[Download fileStream] ${rawEx?.message}", rawEx)
                            }

                            val body = streamResult.getOrThrow()
                            val digest = MessageDigest.getInstance("SHA-256")
                            var chunkWritten = 0L
                            var lastProgressUiUpdate = 0L

                            body.byteStream().use { input ->
                                FileOutputStream(chunkTempFile).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        digest.update(buffer, 0, read)
                                        chunkWritten += read

                                        val now = System.currentTimeMillis()
                                        val currentTotalDownloaded = (downloadedBytes + chunkWritten).coerceAtMost(fileEntity.size)
                                        val liveSpeed = speedEstimator.addSample(now, currentTotalDownloaded)

                                        val shouldUpdateUi = (now - lastProgressUiUpdate >= 100L) || (chunkWritten >= chunk.size)
                                        if (shouldUpdateUi) {
                                            lastProgressUiUpdate = now
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
                                                    currentChunk = chunk.chunkIndex + 1,
                                                    totalChunks = fileEntity.totalChunks,
                                                    progressFraction = fraction,
                                                    bytesTransferred = currentTotalDownloaded,
                                                    totalBytes = fileEntity.size,
                                                    speedBytesPerSec = liveSpeed,
                                                    status = FileStatus.DOWNLOADING,
                                                    etaSeconds = etaSec
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Verify chunk SHA-256 integrity
                            val computedChunkSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                            if (computedChunkSha256 != chunk.checksum) {
                                chunkTempFile.delete()
                                throw IllegalStateException("Checksum mismatch on chunk ${chunk.chunkIndex}! Expected ${chunk.checksum}, got $computedChunkSha256")
                            }

                            chunkSuccess = true
                        } catch (e: Throwable) {
                            lastError = e.localizedMessage ?: "Download error"
                            val isTransient = isTransientNetworkError(e)
                            Log.w("TransferManager", "Download attempt $attempt/$maxRetries for chunk ${chunk.chunkIndex + 1}/${fileEntity.totalChunks} failed (isTransient=$isTransient): $lastError", e)

                            // Evict connection pool so retries use a fresh connection
                            repository.evictConnectionPool("Download chunk ${chunk.chunkIndex + 1} attempt $attempt failed")

                            // Permanent errors abort retries immediately
                            if (!isTransient) {
                                Log.e("TransferManager", "Permanent non-retryable error on download chunk ${chunk.chunkIndex + 1}: '$lastError'. Aborting retries immediately.")
                                break
                            }

                            if (attempt < maxRetries) {
                                val backoffMs = when (attempt) {
                                    1 -> 2000L   // 2s
                                    2 -> 4000L   // 4s
                                    3 -> 8000L   // 8s
                                    4 -> 16000L  // 16s
                                    else -> 30000L // 30s
                                }
                                val retryStatusMsg = "Reconnecting… retrying chunk ${chunk.chunkIndex + 1}/${fileEntity.totalChunks} (attempt $attempt/$maxRetries)"
                                updateProgressState(
                                    TransferProgress(
                                        fileId = fileId,
                                        fileName = fileEntity.name,
                                        isUpload = false,
                                        currentChunk = chunk.chunkIndex + 1,
                                        totalChunks = fileEntity.totalChunks,
                                        progressFraction = (downloadedBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                                        bytesTransferred = downloadedBytes,
                                        totalBytes = fileEntity.size,
                                        speedBytesPerSec = 0L,
                                        status = FileStatus.DOWNLOADING,
                                        errorMessage = retryStatusMsg
                                    )
                                )

                                var waited = 0L
                                while (waited < backoffMs) {
                                    if (pauseRequestedFiles.contains(fileId)) break
                                    val step = minOf(500L, backoffMs - waited)
                                    delay(step)
                                    waited += step
                                }
                            }
                        }
                    }

                    if (!chunkSuccess) {
                        val failReason = "[Download Chunk ${chunk.chunkIndex + 1}/${fileEntity.totalChunks}] $lastError"
                        database.fileDao().updateStatus(fileId, FileStatus.FAILED, failReason)
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = false,
                                currentChunk = completedChunks,
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = (downloadedBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                                bytesTransferred = downloadedBytes,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.FAILED,
                                errorMessage = failReason
                            )
                        )
                        _transferErrorEvents.tryEmit(failReason)
                        notifyService("Download failed: ${fileEntity.name}")
                        return@launch
                    }

                    database.chunkDao().markChunkDownloaded(fileId, chunk.chunkIndex)
                    downloadedBytes += chunk.size
                    completedChunks++

                    // Pause check at chunk boundary after chunk download
                    if (pauseRequestedFiles.contains(fileId)) {
                        pauseRequestedFiles.remove(fileId)
                        database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused by user")
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = false,
                                currentChunk = completedChunks,
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = (downloadedBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                                bytesTransferred = downloadedBytes,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.PAUSED
                            )
                        )
                        return@launch
                    }
                }

                // Concatenate all chunks sequentially into temporary reassembly file
                FileOutputStream(tempReassembledFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    for (chunk in chunks) {
                        val chunkTempFile = File(downloadTempDir, "chunk_${chunk.chunkIndex}.part")
                        FileInputStream(chunkTempFile).use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }

                // Final end-to-end verification of overall file SHA-256
                val finalFileChecksum = ChecksumUtil.computeSha256(tempReassembledFile)
                if (finalFileChecksum != fileEntity.checksum) {
                    tempReassembledFile.delete()
                    throw IllegalStateException("Overall file integrity verification failed! Reassembled checksum did not match manifest.")
                }

                // Insert into dedicated Downloads/TGC folder via MediaStore Scoped Storage
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
                _transferNotificationEvents.tryEmit("Saved to Downloads/TGC")

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
                database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Download paused by user")
            } catch (e: Exception) {
                val err = e.message ?: "Download failed"
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = fileEntity.name,
                        isUpload = false,
                        currentChunk = completedChunks,
                        totalChunks = fileEntity.totalChunks,
                        progressFraction = (downloadedBytes.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                        bytesTransferred = downloadedBytes,
                        totalBytes = fileEntity.size,
                        speedBytesPerSec = 0L,
                        status = FileStatus.FAILED,
                        errorMessage = err
                    )
                )
                _transferErrorEvents.tryEmit(err)
            } finally {
                activeJobs.remove(fileId)
            }
        }
        activeJobs[fileId] = job
    }

    /**
     * Pauses an active upload or download job at the chunk boundary.
     */
    fun pauseTransfer(fileId: String) {
        pauseRequestedFiles.add(fileId)
        // Immediately reflect Paused state in UI
        _transfers.update { current ->
            val existing = current[fileId] ?: return@update current
            current + (fileId to existing.copy(status = FileStatus.PAUSED, speedBytesPerSec = 0L))
        }
        // Also cancel job in case it's in a long wait, while Room will safely retain completed chunks
        activeJobs[fileId]?.cancel()
        activeJobs.remove(fileId)
        scope.launch {
            database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused by user")
        }
    }

    /**
     * Pauses all active or pending transfers.
     */
    fun pauseAll() {
        val activeIds = _transfers.value.filter {
            it.value.status == FileStatus.UPLOADING ||
                    it.value.status == FileStatus.DOWNLOADING ||
                    it.value.status == FileStatus.PENDING
        }.keys
        for (id in activeIds) {
            pauseTransfer(id)
        }
    }

    /**
     * Resumes all paused or failed transfers.
     */
    fun resumeAll() {
        val paused = _transfers.value.filter {
            it.value.status == FileStatus.PAUSED || it.value.status == FileStatus.FAILED
        }.values
        for (transfer in paused) {
            if (transfer.isUpload) {
                startUpload(transfer.fileId)
            } else {
                startDownload(transfer.fileId)
            }
        }
    }

    /**
     * Cancels a transfer: stops the job, cleans up partially uploaded/downloaded chunks,
     * deletes messages if any, removes file record from database and transfer list.
     */
    fun cancelTransfer(fileId: String) {
        pauseRequestedFiles.remove(fileId)
        activeJobs[fileId]?.cancel()
        activeJobs.remove(fileId)
        scope.launch {
            try {
                // Delete physical chunks and manifest, clean up staging and temp dirs, remove from Room
                deleteFile(fileId)
            } catch (e: Exception) {
                android.util.Log.e("TransferManager", "Error cleaning up cancelled transfer: ${e.message}")
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

    private fun updateProgressState(progress: TransferProgress) {
        _transfers.update { current ->
            current + (progress.fileId to progress)
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
