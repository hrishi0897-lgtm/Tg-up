package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.local.entity.FolderEntity
import com.example.data.remote.ManifestChunk
import com.example.data.remote.TelegramRepository
import com.example.data.remote.VaultIndex
import com.example.data.remote.VaultIndexFile
import com.example.data.remote.VaultIndexFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncResult(
    val foldersCount: Int,
    val filesCount: Int,
    val newFilesCount: Int,
    val summary: String
)

/**
 * Manages multi-device vault index synchronization.
 * Writes vault hierarchy (folders, files, chunks) to a single Telegram JSON document tagged VAULT_INDEX.
 * Downloads and reconciles the remote index as source of truth on all connected devices.
 */
class VaultSyncManager private constructor(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val repository = TelegramRepository()
    private val credentialsManager = EncryptedCredentialsManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val publishMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncedTime = MutableStateFlow(credentialsManager.getLastSyncedTime())
    val lastSyncedTime: StateFlow<Long> = _lastSyncedTime.asStateFlow()

    private var debouncePublishJob: Job? = null
    private val debounceMutex = Mutex()

    companion object {
        private const val TAG = "VaultSyncManager"

        @Volatile
        private var INSTANCE: VaultSyncManager? = null

        fun getInstance(context: Context): VaultSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VaultSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Debounces auto-publishing the vault index to Telegram.
     * When local vault structure changes (file uploaded, deleted, folder created/renamed/deleted, or file moved),
     * waits until burst of changes settles (default: 7 seconds) before publishing once.
     */
    fun scheduleAutoPublish(debounceDelayMs: Long = 7000L) {
        val token = credentialsManager.getBotToken()
        val chatId = credentialsManager.getChatId()
        if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
            return
        }

        scope.launch {
            debounceMutex.withLock {
                debouncePublishJob?.cancel()
                debouncePublishJob = scope.launch {
                    try {
                        Log.i(TAG, "Auto-publish debounce timer ($debounceDelayMs ms) started...")
                        delay(debounceDelayMs)
                        Log.i(TAG, "Debounce timer expired. Auto-publishing updated VAULT_INDEX to Telegram...")
                        val result = publishVaultIndex()
                        if (result.isSuccess) {
                            val idx = result.getOrThrow()
                            Log.i(TAG, "Auto-published VAULT_INDEX successfully (${idx.files.size} files, ${idx.folders.size} folders).")
                        } else {
                            Log.w(TAG, "Auto-publish VAULT_INDEX failed: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (cancelled: CancellationException) {
                        Log.d(TAG, "Debounce timer reset by new local change.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto-publish exception: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * Publishes the current local vault state as a fresh VAULT_INDEX document in Telegram chat.
     * Overwrites/deletes the previous VAULT_INDEX message reference so endless copies don't accumulate.
     */
    suspend fun publishVaultIndex(): Result<VaultIndex> = publishMutex.withLock {
        val token = credentialsManager.getBotToken()
        val chatId = credentialsManager.getChatId()
        if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Telegram bot token or chat ID is missing"))
        }

        try {
            val localFolders = database.folderDao().getAll()
            val localFiles = database.fileDao().getAll().filter { it.status == FileStatus.COMPLETED }

            Log.i(TAG, "Publishing Vault Index: ${localFiles.size} files, ${localFolders.size} folders to chat $chatId...")

            val indexFolders = localFolders.map { folder ->
                VaultIndexFolder(
                    id = folder.id,
                    name = folder.name,
                    parentFolderId = folder.parentFolderId,
                    createdDate = folder.createdDate
                )
            }

            val indexFiles = localFiles.map { file ->
                val chunks = database.chunkDao().getChunksForFile(file.id).map { chunk ->
                    ManifestChunk(
                        index = chunk.chunkIndex,
                        messageId = chunk.telegramMessageId ?: 0L,
                        channelId = chunk.channelId ?: file.channelId ?: chatId,
                        telegramFileId = chunk.telegramFileId,
                        sha256 = chunk.checksum,
                        size = chunk.size
                    )
                }

                VaultIndexFile(
                    id = file.id,
                    name = file.name,
                    folderId = file.folderId,
                    channelId = file.channelId ?: chatId,
                    size = file.size,
                    mimeType = file.mimeType,
                    uploadDate = file.uploadDate,
                    checksum = file.checksum,
                    totalChunks = file.totalChunks,
                    manifestMessageId = file.manifestMessageId,
                    chunks = chunks
                )
            }

            val now = System.currentTimeMillis()
            val vaultIndex = VaultIndex(
                version = 1,
                timestamp = now,
                deviceId = credentialsManager.getDeviceId(),
                folders = indexFolders,
                files = indexFiles
            )

            val prevMsgId = credentialsManager.getLastVaultIndexMessageId()
            val uploadResult = repository.uploadVaultIndex(
                token = token,
                chatId = chatId,
                vaultIndex = vaultIndex,
                previousIndexMessageId = prevMsgId
            )

            if (uploadResult.isFailure) {
                val err = uploadResult.exceptionOrNull() ?: Exception("Failed to upload vault index")
                Log.e(TAG, "publishVaultIndex failed: ${err.message}", err)
                return Result.failure(err)
            }

            val newMsg = uploadResult.getOrThrow()
            credentialsManager.setLastVaultIndexMessageId(newMsg.messageId)
            credentialsManager.setLastSyncedTime(now)
            _lastSyncedTime.value = now

            Log.i(TAG, "publishVaultIndex succeeded: messageId=${newMsg.messageId}, ${indexFolders.size} folders, ${indexFiles.size} files, pinned in chat")
            Result.success(vaultIndex)
        } catch (e: Exception) {
            Log.e(TAG, "publishVaultIndex exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Synchronizes the vault from Telegram:
     * @param onlyIfNewer If true, skips downloading and rebuilding if remote index timestamp is not newer than local lastSyncedTime.
     * @param isManual If true, was explicitly triggered by user tapping the header refresh button.
     */
    suspend fun syncVault(onlyIfNewer: Boolean = false, isManual: Boolean = false): Result<SyncResult> = syncMutex.withLock {
        val token = credentialsManager.getBotToken()
        val chatId = credentialsManager.getChatId()
        if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
            val err = "Telegram credentials are not configured. Please set Bot Token and Chat ID in Settings."
            Log.e(TAG, "Sync failed: $err")
            return Result.failure(IllegalStateException(err))
        }

        _isSyncing.value = true
        try {
            val localFilesPre = database.fileDao().getAll().filter { it.status == FileStatus.COMPLETED }
            val localFoldersPre = database.folderDao().getAll()
            val localLastSyncedTime = credentialsManager.getLastSyncedTime()
            Log.i(TAG, "Sync started: Device ${credentialsManager.getDeviceId()}, Chat $chatId, localFiles=${localFilesPre.size}, localFolders=${localFoldersPre.size}, lastSynced=$localLastSyncedTime, onlyIfNewer=$onlyIfNewer, isManual=$isManual")

            Log.i(TAG, "Searching for VAULT_INDEX message in Telegram chat $chatId...")
            val remoteIndexResult = repository.fetchLatestVaultIndex(token, chatId)

            if (remoteIndexResult.isFailure) {
                val err = remoteIndexResult.exceptionOrNull() ?: Exception("Network error while searching chat for vault index")
                Log.e(TAG, "Searching for VAULT_INDEX message failed: ${err.message}", err)
                return Result.failure(err)
            }

            val remoteIndexPair = remoteIndexResult.getOrThrow()

            if (remoteIndexPair != null) {
                val (remoteIndex, remoteMsgId) = remoteIndexPair
                Log.i(TAG, "VAULT_INDEX found (messageId=$remoteMsgId, timestamp=${remoteIndex.timestamp}, deviceId=${remoteIndex.deviceId})")

                // Compare timestamp: if onlyIfNewer is true and remoteIndex is not newer, skip download & rebuild
                if (onlyIfNewer && localLastSyncedTime > 0L && remoteIndex.timestamp <= localLastSyncedTime) {
                    Log.i(TAG, "Remote VAULT_INDEX timestamp (${remoteIndex.timestamp}) is not newer than local lastSyncedTime ($localLastSyncedTime). Skipping local rebuild.")
                    val summary = "Vault is up to date (${remoteIndex.files.size} files, ${remoteIndex.folders.size} folders)."
                    return Result.success(
                        SyncResult(
                            foldersCount = remoteIndex.folders.size,
                            filesCount = remoteIndex.files.size,
                            newFilesCount = 0,
                            summary = summary
                        )
                    )
                }

                Log.i(TAG, "Parsing index: ${remoteIndex.files.size} file(s), ${remoteIndex.folders.size} folder(s)")

                Log.i(TAG, "Rebuilding local database...")

                // 1. Reconcile Folders
                val remoteFolderIds = remoteIndex.folders.map { it.id }.toSet()
                val folderEntities = remoteIndex.folders.map { rf ->
                    FolderEntity(
                        id = rf.id,
                        name = rf.name,
                        parentFolderId = rf.parentFolderId,
                        createdDate = rf.createdDate
                    )
                }
                database.folderDao().insertAll(folderEntities)

                // Delete local folders that were removed on another device
                val localFolders = database.folderDao().getAll()
                for (lf in localFolders) {
                    if (!remoteFolderIds.contains(lf.id)) {
                        Log.i(TAG, "Rebuilding local database: Removing local folder removed on another device: ${lf.name} (${lf.id})")
                        database.folderDao().deleteById(lf.id)
                    }
                }

                // 2. Reconcile Files
                val remoteFileIds = remoteIndex.files.map { it.id }.toSet()
                var newDiscoveredFiles = 0

                for (remoteFile in remoteIndex.files) {
                    val existing = database.fileDao().getById(remoteFile.id)
                    if (existing == null) {
                        // Fast metadata-only import: file appears in vault ready to download on demand
                        val fileChannelId = remoteFile.channelId ?: chatId
                        val newEntity = FileEntity(
                            id = remoteFile.id,
                            name = remoteFile.name,
                            folderId = remoteFile.folderId,
                            channelId = fileChannelId,
                            size = remoteFile.size,
                            mimeType = remoteFile.mimeType,
                            uploadDate = remoteFile.uploadDate,
                            status = FileStatus.COMPLETED,
                            checksum = remoteFile.checksum,
                            totalChunks = remoteFile.totalChunks,
                            completedChunks = remoteFile.totalChunks,
                            manifestMessageId = remoteFile.manifestMessageId,
                            localPath = null, // Download on-demand
                            localUri = null
                        )
                        database.fileDao().insert(newEntity)

                        // Insert chunk metadata so file can be downloaded instantly without separate manifest roundtrip
                        val chunkEntities = remoteFile.chunks.map { mc ->
                            ChunkEntity(
                                fileId = remoteFile.id,
                                chunkIndex = mc.index,
                                channelId = mc.channelId ?: fileChannelId,
                                telegramMessageId = mc.messageId,
                                telegramFileId = mc.telegramFileId,
                                checksum = mc.sha256,
                                size = mc.size,
                                isUploaded = true,
                                isDownloaded = false
                            )
                        }
                        if (chunkEntities.isNotEmpty()) {
                            database.chunkDao().insertAll(chunkEntities)
                        }
                        newDiscoveredFiles++
                    } else {
                        // File already exists locally: update name & folderId if changed remotely
                        val fileChannelId = remoteFile.channelId ?: existing.channelId ?: chatId
                        if (existing.name != remoteFile.name || existing.folderId != remoteFile.folderId || existing.channelId != fileChannelId) {
                            Log.i(TAG, "Rebuilding local database: Updating file placement: ${existing.name} -> ${remoteFile.name} (folder: ${remoteFile.folderId})")
                            database.fileDao().update(
                                existing.copy(
                                    name = remoteFile.name,
                                    folderId = remoteFile.folderId,
                                    channelId = fileChannelId
                                )
                            )
                        }

                        // If chunk records were missing, backfill from remote index chunks
                        val currentChunks = database.chunkDao().getChunksForFile(remoteFile.id)
                        if (currentChunks.isEmpty() && remoteFile.chunks.isNotEmpty()) {
                            val chunkEntities = remoteFile.chunks.map { mc ->
                                ChunkEntity(
                                    fileId = remoteFile.id,
                                    chunkIndex = mc.index,
                                    channelId = mc.channelId ?: fileChannelId,
                                    telegramMessageId = mc.messageId,
                                    telegramFileId = mc.telegramFileId,
                                    checksum = mc.sha256,
                                    size = mc.size,
                                    isUploaded = true,
                                    isDownloaded = false
                                )
                            }
                            database.chunkDao().insertAll(chunkEntities)
                        }
                    }
                }

                // 3. Remove local references to files deleted on another device (never delete remote Telegram chunks!)
                val allLocalFiles = database.fileDao().getAll()
                for (localFile in allLocalFiles) {
                    if (localFile.status == FileStatus.COMPLETED && !remoteFileIds.contains(localFile.id)) {
                        Log.i(TAG, "Rebuilding local database: Removing local reference for remotely deleted file: ${localFile.name} (${localFile.id})")
                        database.fileDao().deleteById(localFile.id)
                    }
                }

                val now = System.currentTimeMillis()
                credentialsManager.setLastVaultIndexMessageId(remoteMsgId)
                val updatedSyncedTime = maxOf(now, remoteIndex.timestamp)
                credentialsManager.setLastSyncedTime(updatedSyncedTime)
                _lastSyncedTime.value = updatedSyncedTime

                val summary = if (newDiscoveredFiles > 0) {
                    "Sync complete: discovered $newDiscoveredFiles new file(s), ${remoteIndex.folders.size} folders from Telegram."
                } else {
                    "Sync complete: Vault is up to date (${remoteIndex.files.size} files, ${remoteIndex.folders.size} folders)."
                }

                Log.i(TAG, summary)
                Result.success(
                    SyncResult(
                        foldersCount = remoteIndex.folders.size,
                        filesCount = remoteIndex.files.size,
                        newFilesCount = newDiscoveredFiles,
                        summary = summary
                    )
                )
            } else {
                // VAULT_INDEX not found in chat
                Log.w(TAG, "VAULT_INDEX not found in chat $chatId")

                val currentFolders = database.folderDao().getAll()
                val currentFiles = database.fileDao().getAll().filter { it.status == FileStatus.COMPLETED }

                if (currentFolders.isNotEmpty() || currentFiles.isNotEmpty()) {
                    // This device has local files/folders. Publish them to Telegram now!
                    Log.i(TAG, "Local device has ${currentFiles.size} files and ${currentFolders.size} folders. Publishing initial VAULT_INDEX to Telegram...")
                    val pubResult = publishVaultIndex()
                    if (pubResult.isSuccess) {
                        val summary = "Published Vault Index (${currentFiles.size} files, ${currentFolders.size} folders) to Telegram chat."
                        Log.i(TAG, "Sync complete: $summary")
                        Result.success(
                            SyncResult(
                                foldersCount = currentFolders.size,
                                filesCount = currentFiles.size,
                                newFilesCount = 0,
                                summary = summary
                            )
                        )
                    } else {
                        val pubErr = pubResult.exceptionOrNull()?.localizedMessage ?: "Failed to upload index document"
                        Log.e(TAG, "Sync failed: Could not publish Vault Index to Telegram: $pubErr")
                        Result.failure(Exception("Failed to upload Vault Index to Telegram: $pubErr"))
                    }
                } else {
                    if (isManual) {
                        val errMsg = "No Vault Index found in Telegram chat. Open TeleVault on your other device where files were uploaded so it can publish the vault."
                        Log.e(TAG, "Sync failed: $errMsg")
                        Result.failure(Exception(errMsg))
                    } else {
                        Log.i(TAG, "No Vault Index in chat and no local items. Background check finished silently.")
                        Result.success(
                            SyncResult(
                                foldersCount = 0,
                                filesCount = 0,
                                newFilesCount = 0,
                                summary = "Vault is up to date"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync exception: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
