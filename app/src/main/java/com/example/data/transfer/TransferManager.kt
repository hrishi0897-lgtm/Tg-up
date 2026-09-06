package com.example.data.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.remote.FileManifest
import com.example.data.remote.ManifestChunk
import com.example.data.remote.TelegramApiException
import com.example.data.remote.TelegramRepository
import com.example.domain.ChecksumUtil
import com.example.domain.model.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

    private val _transfers = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val transfers: StateFlow<Map<String, TransferProgress>> = _transfers.asStateFlow()

    private val _recentlyCompleted = MutableStateFlow<List<TransferProgress>>(emptyList())
    val recentlyCompleted: StateFlow<List<TransferProgress>> = _recentlyCompleted.asStateFlow()

    private val _transferErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val transferErrorEvents: SharedFlow<String> = _transferErrorEvents.asSharedFlow()

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
        @Volatile
        private var INSTANCE: TransferManager? = null

        fun getInstance(context: Context): TransferManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
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
    fun enqueueUpload(uri: Uri, folderId: String?): String {
        val fileId = UUID.randomUUID().toString()
        scope.launch {
            try {
                // 1. Resolve file name and size from content provider
                val (fileName, fileSize) = resolveUriMetadata(uri)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                // 2. Cache Uri stream into a local staging file for safe random-access chunking
                val stagingDir = File(context.cacheDir, "upload_staging").apply { mkdirs() }
                val stagingFile = File(stagingDir, "$fileId.tmp")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(stagingFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Unable to read selected file stream")

                val actualSize = stagingFile.length()
                val overallChecksum = ChecksumUtil.computeSha256(stagingFile)

                // 3. Compute chunk count based on user-configured safe chunk size
                val chunkSize = credentialsManager.getChunkSizeMb() * 1024 * 1024L
                val totalChunks = ((actualSize + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)

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

                // 5. Pre-generate chunk entities
                val chunkEntities = mutableListOf<ChunkEntity>()
                for (i in 0 until totalChunks) {
                    val offset = i * chunkSize
                    val chunkLength = minOf(chunkSize, actualSize - offset)
                    // Compute individual chunk hash
                    val chunkHash = computeChunkHash(stagingFile, offset, chunkLength)
                    chunkEntities.add(
                        ChunkEntity(
                            fileId = fileId,
                            chunkIndex = i,
                            checksum = chunkHash,
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
            val stagingFile = fileEntity.localPath?.let { File(it) }
            if (stagingFile == null || !stagingFile.exists()) {
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

            val chunks = database.chunkDao().getChunksForFile(fileId)
            val chunkSize = credentialsManager.getChunkSizeMb() * 1024 * 1024L
            var completedCount = chunks.count { it.isUploaded }
            var totalBytesSent = chunks.filter { it.isUploaded }.sumOf { it.size }
            val initialFraction = if (fileEntity.size > 0) (totalBytesSent.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f) else 0f

            val initialCurrentChunk = (completedCount + 1).coerceAtMost(fileEntity.totalChunks)
            database.fileDao().updateStatus(fileId, FileStatus.UPLOADING)
            updateProgressState(
                TransferProgress(
                    fileId = fileId,
                    fileName = fileEntity.name,
                    isUpload = true,
                    currentChunk = initialCurrentChunk,
                    totalChunks = fileEntity.totalChunks,
                    progressFraction = initialFraction,
                    bytesTransferred = totalBytesSent,
                    totalBytes = fileEntity.size,
                    speedBytesPerSec = 0L,
                    status = FileStatus.UPLOADING
                )
            )
            notifyService("Uploading ${fileEntity.name}")

            try {
                // Upload incomplete chunks in sequential order
                for (chunk in chunks) {
                    if (chunk.isUploaded) continue // Resume: skip already completed chunks!

                    // Pause check at chunk boundary before starting chunk
                    if (pauseRequestedFiles.contains(fileId)) {
                        pauseRequestedFiles.remove(fileId)
                        database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused by user")
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = true,
                                currentChunk = completedCount,
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = (totalBytesSent.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                                bytesTransferred = totalBytesSent,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.PAUSED
                            )
                        )
                        return@launch
                    }

                    val chunkIndex = chunk.chunkIndex
                    val offset = chunkIndex * chunkSize
                    val chunkLength = chunk.size

                    // Read chunk slice into memory buffer
                    val chunkBytes = ByteArray(chunkLength.toInt())
                    RandomAccessFile(stagingFile, "r").use { raf ->
                        raf.seek(offset)
                        raf.readFully(chunkBytes)
                    }

                    // Verify chunk checksum before dispatching over network
                    val localChunkSha256 = ChecksumUtil.computeSha256(chunkBytes)
                    if (localChunkSha256 != chunk.checksum) {
                        val errMsg = "Chunk ${chunkIndex + 1} corrupted before upload"
                        database.fileDao().updateStatus(fileId, FileStatus.FAILED, errMsg)
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = true,
                                currentChunk = completedCount,
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = (totalBytesSent.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                                bytesTransferred = totalBytesSent,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.FAILED,
                                errorMessage = errMsg
                            )
                        )
                        return@launch
                    }

                    // Upload chunk with retries (up to 3 attempts)
                    val maxRetries = 3
                    var attempt = 0
                    var chunkSuccess = false
                    var lastError: String? = null
                    var uploadedMessage: com.example.data.remote.TelegramMessage? = null

                    while (attempt < maxRetries && !chunkSuccess) {
                        attempt++
                        var lastTimestamp = System.currentTimeMillis()
                        var lastBytes = 0L
                        var lastProgressUiUpdate = 0L

                        val uploadResult = repository.uploadChunk(
                            token = token,
                            chatId = chatId,
                            fileId = fileId,
                            fileName = fileEntity.name,
                            chunkIndex = chunkIndex,
                            totalChunks = fileEntity.totalChunks,
                            chunkBytes = chunkBytes,
                            chunkSha256 = localChunkSha256
                        ) { bytesWritten, chunkTotal ->
                            val now = System.currentTimeMillis()
                            val shouldUpdateUi = (now - lastProgressUiUpdate >= 100L) || (bytesWritten >= chunkTotal)
                            if (shouldUpdateUi) {
                                val dt = (now - lastTimestamp).coerceAtLeast(1)
                                val speed = ((bytesWritten - lastBytes) * 1000L) / dt
                                lastTimestamp = now
                                lastBytes = bytesWritten
                                lastProgressUiUpdate = now

                                val currentTotalSent = totalBytesSent + bytesWritten
                                val fraction = (currentTotalSent.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f)

                                updateProgressState(
                                    TransferProgress(
                                        fileId = fileId,
                                        fileName = fileEntity.name,
                                        isUpload = true,
                                        currentChunk = chunkIndex + 1,
                                        totalChunks = fileEntity.totalChunks,
                                        progressFraction = fraction,
                                        bytesTransferred = currentTotalSent,
                                        totalBytes = fileEntity.size,
                                        speedBytesPerSec = speed,
                                        status = FileStatus.UPLOADING
                                    )
                                )
                            }
                        }

                        if (uploadResult.isSuccess) {
                            chunkSuccess = true
                            uploadedMessage = uploadResult.getOrThrow()
                        } else {
                            val exception = uploadResult.exceptionOrNull()
                            lastError = exception?.message ?: "Network error"
                            Log.e("TransferManager", "Chunk upload failed (chunk ${chunkIndex + 1}/${fileEntity.totalChunks}, attempt $attempt/$maxRetries): $lastError", exception)
                            // If it's a permanent 4xx error (e.g. Forbidden, Bad Request), break immediately without retrying
                            if (exception is TelegramApiException && exception.errorCode != null && exception.errorCode in 400..499 && exception.errorCode != 429) {
                                Log.e("TransferManager", "Permanent 4xx Telegram API error (${exception.errorCode}): '$lastError'. Aborting upload retries.")
                                break
                            }
                            if (attempt < maxRetries) {
                                delay(1000L * attempt)
                            }
                        }
                    }

                    if (!chunkSuccess || uploadedMessage == null) {
                        val failReason = lastError ?: "Upload failed"
                        Log.e("TransferManager", "Marking upload for $fileId (${fileEntity.name}) as FAILED. Reason: $failReason")
                        database.fileDao().updateStatus(fileId, FileStatus.FAILED, failReason)
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = true,
                                currentChunk = (chunkIndex + 1).coerceAtMost(fileEntity.totalChunks),
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = (totalBytesSent.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                                bytesTransferred = totalBytesSent,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.FAILED,
                                errorMessage = failReason
                            )
                        )
                        _transferErrorEvents.tryEmit(failReason)
                        notifyService("Upload failed: ${fileEntity.name}")
                        return@launch
                    }

                    val remoteFileId = uploadedMessage.document?.fileId ?: ""

                    // Persist chunk upload success in Room immediately
                    database.chunkDao().markChunkUploaded(
                        fileId = fileId,
                        chunkIndex = chunkIndex,
                        messageId = uploadedMessage.messageId,
                        fileIdRemote = remoteFileId
                    )

                    completedCount++
                    totalBytesSent += chunkLength
                    database.fileDao().updateProgress(fileId, completedCount, FileStatus.UPLOADING)

                    // Pause check at chunk boundary after chunk completion
                    if (pauseRequestedFiles.contains(fileId)) {
                        pauseRequestedFiles.remove(fileId)
                        database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused by user")
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = true,
                                currentChunk = completedCount,
                                totalChunks = fileEntity.totalChunks,
                                progressFraction = (totalBytesSent.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                                bytesTransferred = totalBytesSent,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.PAUSED
                            )
                        )
                        return@launch
                    }
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

                val manifestResult = repository.uploadManifest(token, chatId, manifest)
                if (manifestResult.isFailure) {
                    val err = manifestResult.exceptionOrNull()?.message ?: "Manifest upload failed"
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                    updateProgressState(
                        TransferProgress(
                            fileId = fileId,
                            fileName = fileEntity.name,
                            isUpload = true,
                            currentChunk = completedCount,
                            totalChunks = fileEntity.totalChunks,
                            progressFraction = 1f,
                            bytesTransferred = fileEntity.size,
                            totalBytes = fileEntity.size,
                            speedBytesPerSec = 0L,
                            status = FileStatus.FAILED,
                            errorMessage = err
                        )
                    )
                    _transferErrorEvents.tryEmit(err)
                    notifyService("Manifest upload failed: ${fileEntity.name}")
                    return@launch
                }

                val manifestMessage = manifestResult.getOrThrow()
                database.fileDao().updateManifestId(fileId, manifestMessage.messageId)
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
                    status = FileStatus.COMPLETED
                )

                // Remove from active transfers so it doesn't linger at 100%
                _transfers.update { it - fileId }
                // Add to recently completed list (capped at 5)
                _recentlyCompleted.update { current ->
                    (listOf(completedProgress) + current.filter { it.fileId != fileId }).take(5)
                }

                // Safe cleanup of temporary staging file
                stagingFile.delete()

            } catch (e: CancellationException) {
                Log.i("TransferManager", "Upload paused for fileId=$fileId")
                database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Upload paused by user")
            } catch (e: Throwable) {
                val err = e.message ?: "Upload failed (${e::class.java.simpleName})"
                Log.e("TransferManager", "Fatal error during upload for $fileId (${fileEntity.name}): $err", e)
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = fileEntity.name,
                        isUpload = true,
                        currentChunk = (completedCount + 1).coerceAtMost(fileEntity.totalChunks),
                        totalChunks = fileEntity.totalChunks,
                        progressFraction = (totalBytesSent.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                        bytesTransferred = totalBytesSent,
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
            database.fileDao().updateStatus(fileId, FileStatus.DOWNLOADING)
            notifyService("Downloading ${fileEntity.name}")

            val chunks = database.chunkDao().getChunksForFile(fileId)
            val downloadTempDir = File(context.cacheDir, "downloads_temp/$fileId").apply { mkdirs() }
            val completedDownloadDir = File(context.filesDir, "vault_storage").apply { mkdirs() }
            val finalTargetFile = File(completedDownloadDir, "${fileEntity.id}_${fileEntity.name}")

            var downloadedBytes = chunks.filter { it.isDownloaded }.sumOf { it.size }
            var completedChunks = chunks.count { it.isDownloaded }

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

                    val maxRetries = 3
                    var attempt = 0
                    var chunkSuccess = false
                    var lastError: String? = null

                    while (attempt < maxRetries && !chunkSuccess) {
                        attempt++
                        try {
                            val fileInfoResult = repository.getFileInfo(token, remoteFileId)
                            if (fileInfoResult.isFailure) {
                                throw IllegalStateException("Failed to resolve remote path: ${fileInfoResult.exceptionOrNull()?.localizedMessage}")
                            }
                            val remoteFilePath = fileInfoResult.getOrThrow().filePath
                                ?: throw IllegalStateException("Telegram file_path is empty")

                            val streamResult = repository.downloadFileStream(token, remoteFilePath)
                            if (streamResult.isFailure) {
                                throw IllegalStateException("Stream download failed: ${streamResult.exceptionOrNull()?.localizedMessage}")
                            }

                            val body = streamResult.getOrThrow()
                            val digest = MessageDigest.getInstance("SHA-256")
                            var lastTimestamp = System.currentTimeMillis()
                            var chunkWritten = 0L

                            body.byteStream().use { input ->
                                FileOutputStream(chunkTempFile).use { output ->
                                    val buffer = ByteArray(8192)
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        digest.update(buffer, 0, read)
                                        chunkWritten += read

                                        val now = System.currentTimeMillis()
                                        val dt = (now - lastTimestamp).coerceAtLeast(1)
                                        val speed = (chunkWritten * 1000L) / dt

                                        val overallProgress = downloadedBytes + chunkWritten
                                        val fraction = (overallProgress.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f)

                                        updateProgressState(
                                            TransferProgress(
                                                fileId = fileId,
                                                fileName = fileEntity.name,
                                                isUpload = false,
                                                currentChunk = chunk.chunkIndex + 1,
                                                totalChunks = fileEntity.totalChunks,
                                                progressFraction = fraction,
                                                bytesTransferred = overallProgress,
                                                totalBytes = fileEntity.size,
                                                speedBytesPerSec = speed,
                                                status = FileStatus.DOWNLOADING
                                            )
                                        )
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
                        } catch (e: Exception) {
                            lastError = e.localizedMessage ?: "Download error"
                            if (attempt < maxRetries) {
                                delay(1000L * attempt)
                            }
                        }
                    }

                    if (!chunkSuccess) {
                        val failReason = "Chunk ${chunk.chunkIndex + 1} of ${fileEntity.totalChunks} failed: $lastError"
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

                // Concatenate all chunks sequentially into final destination file
                FileOutputStream(finalTargetFile).use { output ->
                    for (chunk in chunks) {
                        val chunkTempFile = File(downloadTempDir, "chunk_${chunk.chunkIndex}.part")
                        FileInputStream(chunkTempFile).use { input ->
                            input.copyTo(output)
                        }
                    }
                }

                // Final end-to-end verification of overall file SHA-256
                val finalFileChecksum = ChecksumUtil.computeSha256(finalTargetFile)
                if (finalFileChecksum != fileEntity.checksum) {
                    finalTargetFile.delete()
                    throw IllegalStateException("Overall file integrity verification failed! Reassembled checksum did not match manifest.")
                }

                // Success! Clean temp chunks and mark as completed
                downloadTempDir.deleteRecursively()
                database.fileDao().markDownloaded(fileId, finalTargetFile.absolutePath)

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

            // 3. Delete local physical files
            file?.localPath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }

            // 4. Clean temp staging and download directories
            File(context.cacheDir, "upload_staging/$fileId.tmp").delete()
            File(context.cacheDir, "downloads_temp/$fileId").deleteRecursively()

            // 5. Delete Room metadata (cascades to chunks)
            database.fileDao().deleteById(fileId)
            _transfers.update { it - fileId }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rebuilds local index cache from Telegram chat messages (Resync feature).
     */
    suspend fun resyncFromTelegram(): Result<Int> {
        val token = credentialsManager.getBotToken() ?: return Result.failure(Exception("Bot token missing"))
        return try {
            val result = repository.fetchManifestsFromChat(token)
            if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: Exception("Sync failed"))
            val manifests = result.getOrThrow()
            var importedCount = 0

            for (manifest in manifests) {
                val existing = database.fileDao().getById(manifest.fileId)
                if (existing == null) {
                    val fileEntity = FileEntity(
                        id = manifest.fileId,
                        name = manifest.name,
                        folderId = manifest.folderId,
                        size = manifest.size,
                        mimeType = manifest.mimeType,
                        uploadDate = manifest.uploadDate,
                        status = FileStatus.COMPLETED,
                        checksum = manifest.overallSha256,
                        totalChunks = manifest.chunks.size,
                        completedChunks = manifest.chunks.size
                    )
                    database.fileDao().insert(fileEntity)

                    val chunkEntities = manifest.chunks.map { mc ->
                        ChunkEntity(
                            fileId = manifest.fileId,
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
                    importedCount++
                }
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
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
}
