package com.example.data.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.remote.FileManifest
import com.example.data.remote.ManifestChunk
import com.example.data.remote.TelegramRepository
import com.example.domain.ChecksumUtil
import com.example.domain.model.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _transfers = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val transfers: StateFlow<Map<String, TransferProgress>> = _transfers.asStateFlow()

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

            } catch (e: Exception) {
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, e.localizedMessage)
                updateProgressState(
                    TransferProgress(
                        fileId = fileId,
                        fileName = "Upload",
                        isUpload = true,
                        currentChunk = 0,
                        totalChunks = 1,
                        progressFraction = 0f,
                        bytesTransferred = 0L,
                        totalBytes = 0L,
                        speedBytesPerSec = 0L,
                        status = FileStatus.FAILED,
                        errorMessage = e.localizedMessage
                    )
                )
            }
        }
        return fileId
    }

    /**
     * Executes or resumes a chunked upload.
     */
    fun startUpload(fileId: String) {
        val job = scope.launch {
            val token = credentialsManager.getBotToken()
            val chatId = credentialsManager.getChatId()
            if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, "Telegram credentials not set")
                return@launch
            }

            val fileEntity = database.fileDao().getById(fileId) ?: return@launch
            val stagingFile = fileEntity.localPath?.let { File(it) }
            if (stagingFile == null || !stagingFile.exists()) {
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, "Source staging file missing")
                return@launch
            }

            database.fileDao().updateStatus(fileId, FileStatus.UPLOADING)
            notifyService("Uploading ${fileEntity.name}")

            val chunks = database.chunkDao().getChunksForFile(fileId)
            val chunkSize = credentialsManager.getChunkSizeMb() * 1024 * 1024L
            var completedCount = chunks.count { it.isUploaded }
            var totalBytesSent = chunks.filter { it.isUploaded }.sumOf { it.size }

            try {
                // Upload incomplete chunks in sequential order
                for (chunk in chunks) {
                    if (chunk.isUploaded) continue // Resume: skip completed chunks!

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
                        throw IllegalStateException("Local chunk $chunkIndex corrupted before upload")
                    }

                    var lastTimestamp = System.currentTimeMillis()
                    var lastBytes = 0L

                    // Upload chunk as Telegram document with caption
                    val uploadResult = repository.uploadChunk(
                        token = token,
                        chatId = chatId,
                        fileId = fileId,
                        fileName = fileEntity.name,
                        chunkIndex = chunkIndex,
                        totalChunks = fileEntity.totalChunks,
                        chunkBytes = chunkBytes,
                        chunkSha256 = localChunkSha256
                    ) { bytesWritten, _ ->
                        val now = System.currentTimeMillis()
                        val dt = (now - lastTimestamp).coerceAtLeast(1)
                        val speed = ((bytesWritten - lastBytes) * 1000L) / dt
                        lastTimestamp = now
                        lastBytes = bytesWritten

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

                    if (uploadResult.isFailure) {
                        val err = uploadResult.exceptionOrNull()?.localizedMessage ?: "Chunk upload failed"
                        database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                        return@launch
                    }

                    val message = uploadResult.getOrThrow()
                    val remoteFileId = message.document?.fileId ?: ""

                    // Persist chunk upload success in Room immediately
                    database.chunkDao().markChunkUploaded(
                        fileId = fileId,
                        chunkIndex = chunkIndex,
                        messageId = message.messageId,
                        fileIdRemote = remoteFileId
                    )

                    completedCount++
                    totalBytesSent += chunkLength
                    database.fileDao().updateProgress(fileId, completedCount, FileStatus.UPLOADING)
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
                    val err = manifestResult.exceptionOrNull()?.localizedMessage ?: "Manifest upload failed"
                    database.fileDao().updateStatus(fileId, FileStatus.FAILED, err)
                    return@launch
                }

                val manifestMessage = manifestResult.getOrThrow()
                database.fileDao().updateManifestId(fileId, manifestMessage.messageId)
                database.fileDao().updateStatus(fileId, FileStatus.COMPLETED)

                updateProgressState(
                    TransferProgress(
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
                )

                // Safe cleanup of temporary staging file
                stagingFile.delete()

            } catch (e: CancellationException) {
                database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Upload paused by user")
            } catch (e: Exception) {
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, e.localizedMessage)
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
                        errorMessage = e.localizedMessage
                    )
                )
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
        val job = scope.launch {
            val token = credentialsManager.getBotToken()
            if (token.isNullOrBlank()) {
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, "Telegram bot token not configured")
                return@launch
            }

            val fileEntity = database.fileDao().getById(fileId) ?: return@launch
            database.fileDao().updateStatus(fileId, FileStatus.DOWNLOADING)
            notifyService("Downloading ${fileEntity.name}")

            val chunks = database.chunkDao().getChunksForFile(fileId)
            val downloadTempDir = File(context.cacheDir, "downloads_temp/$fileId").apply { mkdirs() }
            val completedDownloadDir = File(context.filesDir, "vault_storage").apply { mkdirs() }
            val finalTargetFile = File(completedDownloadDir, "${fileEntity.id}_${fileEntity.name}")

            var downloadedBytes = 0L
            var completedChunks = 0

            try {
                // Download each chunk in sequence
                for (chunk in chunks) {
                    val chunkTempFile = File(downloadTempDir, "chunk_${chunk.chunkIndex}.part")

                    // Resume check: if chunk file already exists with valid hash, skip download
                    if (chunkTempFile.exists() && chunkTempFile.length() == chunk.size) {
                        val existingHash = ChecksumUtil.computeSha256(chunkTempFile)
                        if (existingHash == chunk.checksum) {
                            database.chunkDao().markChunkDownloaded(fileId, chunk.chunkIndex)
                            downloadedBytes += chunk.size
                            completedChunks++
                            continue
                        }
                    }

                    // Obtain Telegram file path
                    val remoteFileId = chunk.telegramFileId ?: throw IllegalStateException("Missing Telegram file_id for chunk ${chunk.chunkIndex}")
                    val fileInfoResult = repository.getFileInfo(token, remoteFileId)
                    if (fileInfoResult.isFailure) {
                        throw IllegalStateException("Failed to resolve remote path: ${fileInfoResult.exceptionOrNull()?.localizedMessage}")
                    }
                    val remoteFilePath = fileInfoResult.getOrThrow().filePath
                        ?: throw IllegalStateException("Telegram file_path is empty")

                    // Download chunk binary stream
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

                    database.chunkDao().markChunkDownloaded(fileId, chunk.chunkIndex)
                    downloadedBytes += chunk.size
                    completedChunks++
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

                updateProgressState(
                    TransferProgress(
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
                )

            } catch (e: CancellationException) {
                database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Download paused by user")
            } catch (e: Exception) {
                database.fileDao().updateStatus(fileId, FileStatus.FAILED, e.localizedMessage)
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
                        errorMessage = e.localizedMessage
                    )
                )
            } finally {
                activeJobs.remove(fileId)
            }
        }
        activeJobs[fileId] = job
    }

    /**
     * Pauses an active upload or download job.
     */
    fun pauseTransfer(fileId: String) {
        activeJobs[fileId]?.cancel()
        activeJobs.remove(fileId)
        scope.launch {
            database.fileDao().updateStatus(fileId, FileStatus.PAUSED, "Paused by user")
            _transfers.update { current ->
                val existing = current[fileId] ?: return@update current
                current + (fileId to existing.copy(status = FileStatus.PAUSED))
            }
        }
    }

    /**
     * Cancels an active or queued transfer.
     */
    fun cancelTransfer(fileId: String) {
        activeJobs[fileId]?.cancel()
        activeJobs.remove(fileId)
        scope.launch {
            database.fileDao().updateStatus(fileId, FileStatus.FAILED, "Transfer cancelled")
            _transfers.update { current ->
                current - fileId
            }
        }
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
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_UPDATE_STATUS
            putExtra(TransferService.EXTRA_MESSAGE, content)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
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
