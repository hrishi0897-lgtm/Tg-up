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
import com.example.domain.RollingSpeedEstimator
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
import kotlinx.coroutines.withContext
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

                // 3. Compute chunk count based on global safe chunk size CHUNK_SIZE_BYTES (18MB)
                // and compute balanced target chunk size (total file size divided by number of chunks)
                val maxChunkSize = minOf(credentialsManager.getChunkSizeMb() * 1024 * 1024L, CHUNK_SIZE_BYTES)
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

                // 5. Clean up any stale chunk files from a previous attempt before chunking
                val chunksDir = File(context.cacheDir, "upload_chunks/$fileId")
                if (chunksDir.exists()) {
                    Log.d("TransferManager", "Cleaning up stale chunk directory for fileId=$fileId: ${chunksDir.absolutePath}")
                    chunksDir.deleteRecursively()
                }
                chunksDir.mkdirs()

                // Pre-generate chunk entities and write fresh, discrete chunk files on disk
                val chunkEntities = mutableListOf<ChunkEntity>()
                for (i in 0 until totalChunks) {
                    val offset = i * targetChunkSize
                    val chunkLength = minOf(targetChunkSize, (actualSize - offset).coerceAtLeast(0L))
                    val chunkFile = writeChunkFileOnDisk(stagingFile, chunksDir, i, offset, chunkLength)
                    val chunkHash = ChecksumUtil.computeSha256(chunkFile)

                    Log.i(
                        "TransferManager",
                        "Created chunk $i on disk: ${chunkFile.name} (${chunkFile.length()} bytes, target=$chunkLength, hash=$chunkHash)"
                    )

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

            val speedEstimator = RollingSpeedEstimator(windowDurationMs = 1800L)
            speedEstimator.addSample(System.currentTimeMillis(), totalBytesSent)

            val chunksDir = File(context.cacheDir, "upload_chunks/$fileId").apply { mkdirs() }

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
                    val totalChunks = fileEntity.totalChunks
                    val offset = chunks.filter { it.chunkIndex < chunkIndex }.sumOf { it.size }
                    val chunkLength = chunk.size

                    val chunkFile = File(chunksDir, "chunk_$chunkIndex.tpart")

                    // Check for stale/corrupted chunk files left over from a previous failed attempt
                    if (!chunkFile.exists() || chunkFile.length() != chunkLength) {
                        if (chunkFile.exists()) {
                            Log.w(
                                "TransferManager",
                                "Stale chunk file found on disk (${chunkFile.length()} vs expected $chunkLength bytes). Deleting and regenerating..."
                            )
                            chunkFile.delete()
                        }
                        writeChunkFileOnDisk(stagingFile, chunksDir, chunkIndex, offset, chunkLength)
                    }

                    // Log the actual file size in bytes of the chunk file on disk immediately before it is attached to the multipart request
                    val actualFileSizeOnDisk = chunkFile.length()
                    val expectedChunkSize = ((fileEntity.size + totalChunks - 1) / totalChunks).coerceAtLeast(1L)
                    val diffFromExpected = actualFileSizeOnDisk - expectedChunkSize

                    Log.i(
                        "TransferManager",
                        ">>> [CHUNK DISK SIZE AUDIT BEFORE MULTIPART]\n" +
                        "  File: ${fileEntity.name} (fileId=$fileId)\n" +
                        "  Chunk: ${chunkIndex + 1} of $totalChunks\n" +
                        "  Path: ${chunkFile.absolutePath}\n" +
                        "  Actual on-disk file size: $actualFileSizeOnDisk bytes (${ChecksumUtil.formatBytes(actualFileSizeOnDisk)})\n" +
                        "  Expected chunk size (total/count): $expectedChunkSize bytes (${ChecksumUtil.formatBytes(expectedChunkSize)})\n" +
                        "  Difference from expected: $diffFromExpected bytes\n" +
                        "  Target chunk length from entity: $chunkLength bytes\n" +
                        "  Telegram 50MB Limit: ${if (actualFileSizeOnDisk <= 50 * 1024 * 1024L) "PASS (<= 50MB)" else "FAIL (EXCEEDS 50MB!)"}"
                    )

                    if (actualFileSizeOnDisk > 50 * 1024 * 1024L) {
                        val errMsg = "Chunk ${chunkIndex + 1} size ($actualFileSizeOnDisk bytes) exceeds Telegram Bot 50MB limit!"
                        database.fileDao().updateStatus(fileId, FileStatus.FAILED, errMsg)
                        updateProgressState(
                            TransferProgress(
                                fileId = fileId,
                                fileName = fileEntity.name,
                                isUpload = true,
                                currentChunk = chunkIndex + 1,
                                totalChunks = totalChunks,
                                progressFraction = (totalBytesSent.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f),
                                bytesTransferred = totalBytesSent,
                                totalBytes = fileEntity.size,
                                speedBytesPerSec = 0L,
                                status = FileStatus.FAILED,
                                errorMessage = errMsg
                            )
                        )
                        _transferErrorEvents.tryEmit(errMsg)
                        return@launch
                    }

                    // Verify chunk checksum before dispatching over network
                    var localChunkSha256 = ChecksumUtil.computeSha256(chunkFile)
                    if (localChunkSha256 != chunk.checksum) {
                        Log.w("TransferManager", "Chunk $chunkIndex hash mismatch on disk. Re-writing fresh chunk file...")
                        chunkFile.delete()
                        writeChunkFileOnDisk(stagingFile, chunksDir, chunkIndex, offset, chunkLength)
                        localChunkSha256 = ChecksumUtil.computeSha256(chunkFile)
                        if (localChunkSha256 != chunk.checksum) {
                            val errMsg = "Chunk ${chunkIndex + 1} corrupted before upload (hash mismatch)"
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
                    }

                    // Upload chunk with retries (up to 3 attempts)
                    val maxRetries = 3
                    var attempt = 0
                    var chunkSuccess = false
                    var lastError: String? = null
                    var uploadedMessage: com.example.data.remote.TelegramMessage? = null

                    while (attempt < maxRetries && !chunkSuccess) {
                        attempt++
                        var lastProgressUiUpdate = 0L

                        val uploadResult = repository.uploadChunk(
                            token = token,
                            chatId = chatId,
                            fileId = fileId,
                            fileName = fileEntity.name,
                            chunkIndex = chunkIndex,
                            totalChunks = fileEntity.totalChunks,
                            chunkFile = chunkFile,
                            chunkSha256 = localChunkSha256,
                            expectedChunkSize = expectedChunkSize
                        ) { bytesWritten, chunkTotal ->
                            val now = System.currentTimeMillis()
                            val currentTotalSent = (totalBytesSent + bytesWritten).coerceAtMost(fileEntity.size)
                            val liveSpeed = speedEstimator.addSample(now, currentTotalSent)

                            val shouldUpdateUi = (now - lastProgressUiUpdate >= 80L) || (bytesWritten >= chunkTotal)
                            if (shouldUpdateUi) {
                                lastProgressUiUpdate = now
                                val fraction = if (fileEntity.size > 0) {
                                    (currentTotalSent.toFloat() / fileEntity.size.toFloat()).coerceIn(0f, 1f)
                                } else 0f

                                val remainingBytes = (fileEntity.size - currentTotalSent).coerceAtLeast(0L)
                                val etaSec = if (liveSpeed > 0L && remainingBytes > 0L) remainingBytes / liveSpeed else null

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
                                        speedBytesPerSec = liveSpeed,
                                        status = FileStatus.UPLOADING,
                                        etaSeconds = etaSec
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
                        val failReason = "[Upload Chunk ${(chunkIndex + 1).coerceAtMost(fileEntity.totalChunks)}/${fileEntity.totalChunks}] ${lastError ?: "Upload failed"}"
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

                    // Clean up uploaded chunk file to reclaim disk space
                    if (chunkFile.exists()) {
                        chunkFile.delete()
                    }

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

                    val maxRetries = 3
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
                                    val buffer = ByteArray(8192)
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        digest.update(buffer, 0, read)
                                        chunkWritten += read

                                        val now = System.currentTimeMillis()
                                        val currentTotalDownloaded = (downloadedBytes + chunkWritten).coerceAtMost(fileEntity.size)
                                        val liveSpeed = speedEstimator.addSample(now, currentTotalDownloaded)

                                        val shouldUpdateUi = (now - lastProgressUiUpdate >= 80L) || (chunkWritten >= chunk.size)
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
                        } catch (e: Exception) {
                            lastError = e.localizedMessage ?: "Download error"
                            if (attempt < maxRetries) {
                                delay(1000L * attempt)
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

            // 4. Clean temp staging, discrete chunks, and download directories
            File(context.cacheDir, "upload_staging/$fileId.tmp").delete()
            File(context.cacheDir, "upload_chunks/$fileId").deleteRecursively()
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

        // 6. Write fresh chunk files to disk and generate ChunkEntity records
        chunksDir.mkdirs()
        val chunkEntities = mutableListOf<ChunkEntity>()
        for (i in 0 until totalChunks) {
            val offset = i * targetChunkSize
            val chunkLength = minOf(targetChunkSize, actualSize - offset)
            val chunkFile = writeChunkFileOnDisk(stagingFile, chunksDir, i, offset, chunkLength)
            val chunkSha256 = ChecksumUtil.computeSha256(chunkFile)

            Log.i(
                "TransferManager",
                "forceFreshUpload: Written chunk $i of $totalChunks: ${chunkFile.length()} bytes, sha256=$chunkSha256"
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

    /**
     * Writes a discrete chunk file on disk for the given chunk index.
     * Strictly stops writing once target chunk length is reached, starts a fresh file,
     * deletes any stale chunk files from previous failed attempts, and verifies size.
     */
    internal fun writeChunkFileOnDisk(
        stagingFile: File,
        chunksDir: File,
        chunkIndex: Int,
        offset: Long,
        chunkLength: Long
    ): File {
        chunksDir.mkdirs()
        val chunkFile = File(chunksDir, "chunk_$chunkIndex.tpart")
        if (chunkFile.exists()) {
            Log.d("TransferManager", "Deleting stale chunk file before re-writing: ${chunkFile.absolutePath}")
            chunkFile.delete()
        }

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
        return chunkFile
    }
}
