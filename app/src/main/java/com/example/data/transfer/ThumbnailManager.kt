package com.example.data.transfer

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.FileEntity
import com.example.data.remote.TelegramRepository
import com.example.domain.ThumbnailUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ThumbnailManager(
    private val context: Context,
    private val database: AppDatabase,
    private val repository: TelegramRepository = TelegramRepository.getInstance(),
    private val credentialsManager: EncryptedCredentialsManager = EncryptedCredentialsManager(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = ConcurrentHashMap.newKeySet<String>()

    private val _thumbnailUpdatedFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val thumbnailUpdatedFlow: SharedFlow<String> = _thumbnailUpdatedFlow.asSharedFlow()

    companion object {
        private const val TAG = "ThumbnailManager"

        @Volatile
        private var INSTANCE: ThumbnailManager? = null

        fun getInstance(context: Context): ThumbnailManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThumbnailManager(
                    context.applicationContext,
                    AppDatabase.getInstance(context.applicationContext)
                ).also { INSTANCE = it }
            }
        }
    }

    /**
     * Checks if a local thumbnail exists on disk. Returns the File if available, or null.
     */
    fun getLocalThumbnail(file: FileEntity): File? {
        // 1. Check entity's recorded path
        if (!file.thumbnailLocalPath.isNullOrBlank()) {
            val local = File(file.thumbnailLocalPath)
            if (local.exists() && local.length() > 0L) return local
        }

        // 2. Check standard cache location
        val cached = ThumbnailUtil.getThumbnailFile(context, file.id)
        if (cached.exists() && cached.length() > 0L) {
            // Update database path in background if missing
            if (file.thumbnailLocalPath == null) {
                scope.launch {
                    try {
                        database.fileDao().updateThumbnailLocalPath(file.id, cached.absolutePath)
                    } catch (_: Exception) {}
                }
            }
            return cached
        }

        return null
    }

    /**
     * Returns the local thumbnail if already present. If not present and a remote thumbnail exists,
     * enqueues an asynchronous download of the tiny thumbnail chunk (~5-15 KB).
     */
    fun getOrFetchThumbnail(file: FileEntity): File? {
        val local = getLocalThumbnail(file)
        if (local != null) return local

        val remoteFileId = file.thumbnailFileId
        if (!remoteFileId.isNullOrBlank()) {
            enqueueThumbnailDownload(file.id, remoteFileId)
        }

        return null
    }

    /**
     * Asynchronously downloads a thumbnail for the specified file and saves it to local cache.
     */
    fun enqueueThumbnailDownload(fileId: String, remoteFileId: String) {
        if (!activeDownloads.add(fileId)) {
            return // Already in progress
        }

        scope.launch {
            try {
                val token = credentialsManager.getBotToken()
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "Cannot download thumbnail: bot token missing")
                    return@launch
                }

                val destFile = ThumbnailUtil.getThumbnailFile(context, fileId)
                Log.d(TAG, "Downloading thumbnail for fileId=$fileId remoteFileId=$remoteFileId...")
                val result = repository.downloadThumbnail(token, remoteFileId, destFile)

                if (result.isSuccess && destFile.exists() && destFile.length() > 0L) {
                    database.fileDao().updateThumbnailLocalPath(fileId, destFile.absolutePath)
                    _thumbnailUpdatedFlow.tryEmit(fileId)
                    Log.i(TAG, "Thumbnail downloaded successfully for fileId=$fileId (${destFile.length()} bytes)")
                } else {
                    Log.w(TAG, "Failed to download thumbnail for fileId=$fileId: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading thumbnail for fileId=$fileId", e)
            } finally {
                activeDownloads.remove(fileId)
            }
        }
    }

    /**
     * On-demand action: Generates a thumbnail from a local file and uploads it to Telegram (requirement 7).
     */
    suspend fun generateAndUploadThumbnail(file: FileEntity): Result<File> {
        val localSource = file.localUri ?: file.localPath
        if (localSource.isNullOrBlank()) {
            return Result.failure(IllegalStateException("File content must be downloaded before generating a preview."))
        }

        val sourceFile = File(localSource)
        val thumbFile = if (sourceFile.exists()) {
            ThumbnailUtil.generateThumbnail(context, sourceFile, file.mimeType, file.id)
        } else {
            try {
                val uri = android.net.Uri.parse(localSource)
                ThumbnailUtil.generateThumbnailFromUri(context, uri, file.mimeType, file.id)
            } catch (e: Exception) {
                null
            }
        } ?: return Result.failure(Exception("Could not extract frame or thumbnail from source file."))

        // Upload to Telegram
        val token = credentialsManager.getBotToken()
            ?: return Result.failure(Exception("Telegram bot token not configured."))
        val chatId = file.channelId ?: credentialsManager.getChatId()
            ?: return Result.failure(Exception("Chat ID not configured."))

        val uploadResult = repository.uploadThumbnail(token, chatId, file.id, thumbFile)
        if (uploadResult.isFailure) {
            return Result.failure(uploadResult.exceptionOrNull() ?: Exception("Thumbnail upload failed."))
        }

        val msg = uploadResult.getOrThrow()
        val thumbFileId = msg.document?.fileId
        val thumbMsgId = msg.messageId

        database.fileDao().updateThumbnailInfo(
            fileId = file.id,
            fileIdRemote = thumbFileId,
            messageId = thumbMsgId,
            localPath = thumbFile.absolutePath
        )

        _thumbnailUpdatedFlow.tryEmit(file.id)
        Log.i(TAG, "Generated and uploaded thumbnail for ${file.name}: msgId=$thumbMsgId, fileId=$thumbFileId")
        return Result.success(thumbFile)
    }
}
