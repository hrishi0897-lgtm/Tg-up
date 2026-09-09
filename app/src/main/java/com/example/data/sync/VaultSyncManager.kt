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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                        telegramFileId = chunk.telegramFileId,
                        sha256 = chunk.checksum,
                        size = chunk.size
                    )
                }

                VaultIndexFile(
                    id = file.id,
                    name = file.name,
                    folderId = file.folderId,
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

            Log.i(TAG, "publishVaultIndex succeeded: msgId=${newMsg.messageId}, ${indexFolders.size} folders, ${indexFiles.size} files")
            Result.success(vaultIndex)
        } catch (e: Exception) {
            Log.e(TAG, "publishVaultIndex exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Synchronizes the vault from Telegram:
     * 1. Finds the most recent VAULT_INDEX document in the chat.
     * 2. Rebuilds local Room database (folders, file-to-folder mapping, chunk manifests).
     * 3. Handles fast metadata-only sync: files appear in vault ready for on-demand download.
     * 4. Edge-case safety: never silently purges remote Telegram messages during index reconciliation.
     */
    suspend fun syncVault(): Result<SyncResult> = syncMutex.withLock {
        val token = credentialsManager.getBotToken()
        val chatId = credentialsManager.getChatId()
        if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Telegram credentials are not configured"))
        }

        _isSyncing.value = true
        try {
            Log.i(TAG, "Starting vault synchronization from Telegram chat...")
            val remoteIndexResult = repository.fetchLatestVaultIndex(token)

            if (remoteIndexResult.isFailure) {
                val err = remoteIndexResult.exceptionOrNull() ?: Exception("Failed to check chat for vault index")
                Log.e(TAG, "Sync error fetching vault index: ${err.message}", err)
                return Result.failure(err)
            }

            val remoteIndexPair = remoteIndexResult.getOrThrow()

            if (remoteIndexPair != null) {
                val (remoteIndex, remoteMsgId) = remoteIndexPair
                Log.i(TAG, "Discovered remote VAULT_INDEX: msgId=$remoteMsgId, folders=${remoteIndex.folders.size}, files=${remoteIndex.files.size}, ts=${remoteIndex.timestamp}")

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
                        Log.i(TAG, "Removing local folder removed on another device: ${lf.name} (${lf.id})")
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
                        val newEntity = FileEntity(
                            id = remoteFile.id,
                            name = remoteFile.name,
                            folderId = remoteFile.folderId,
                            size = remoteFile.size,
                            mimeType = remoteFile.mimeType,
                            uploadDate = remoteFile.uploadDate,
                            status = FileStatus.COMPLETED,
                            checksum = remoteFile.checksum,
                            totalChunks = remoteFile.totalChunks,
                            completedChunks = remoteFile.totalChunks,
                            manifestMessageId = remoteFile.manifestMessageId,
                            localPath = null, // Not yet downloaded locally
                            localUri = null
                        )
                        database.fileDao().insert(newEntity)

                        // Insert chunk metadata so file can be downloaded instantly without separate manifest roundtrip
                        val chunkEntities = remoteFile.chunks.map { mc ->
                            ChunkEntity(
                                fileId = remoteFile.id,
                                chunkIndex = mc.index,
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
                        if (existing.name != remoteFile.name || existing.folderId != remoteFile.folderId) {
                            Log.i(TAG, "Updating existing file organization: ${existing.name} -> ${remoteFile.name} (folder: ${remoteFile.folderId})")
                            database.fileDao().update(
                                existing.copy(
                                    name = remoteFile.name,
                                    folderId = remoteFile.folderId
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
                        Log.i(TAG, "Removing local reference for file deleted remotely: ${localFile.name} (${localFile.id})")
                        database.fileDao().deleteById(localFile.id)
                    }
                }

                val now = System.currentTimeMillis()
                credentialsManager.setLastVaultIndexMessageId(remoteMsgId)
                credentialsManager.setLastSyncedTime(now)
                _lastSyncedTime.value = now

                val summary = if (newDiscoveredFiles > 0) {
                    "Synced: discovered $newDiscoveredFiles new file(s), ${remoteIndex.folders.size} folders."
                } else {
                    "Vault is in sync (${remoteIndex.files.size} files, ${remoteIndex.folders.size} folders)."
                }

                Result.success(
                    SyncResult(
                        foldersCount = remoteIndex.folders.size,
                        filesCount = remoteIndex.files.size,
                        newFilesCount = newDiscoveredFiles,
                        summary = summary
                    )
                )
            } else {
                // No VAULT_INDEX message in chat yet: check for legacy individual file manifests
                Log.i(TAG, "No VAULT_INDEX found in chat. Checking for legacy manifests...")
                val legacyManifestsResult = repository.fetchManifestsFromChat(token)
                var importedCount = 0
                if (legacyManifestsResult.isSuccess) {
                    val manifests = legacyManifestsResult.getOrThrow()
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
                }

                // Publish initial VAULT_INDEX if files or folders exist locally
                val currentFolders = database.folderDao().getAll()
                val currentFiles = database.fileDao().getAll().filter { it.status == FileStatus.COMPLETED }
                if (currentFolders.isNotEmpty() || currentFiles.isNotEmpty()) {
                    Log.i(TAG, "Publishing initial VAULT_INDEX for newly discovered vault content...")
                    publishVaultIndex()
                }

                val now = System.currentTimeMillis()
                credentialsManager.setLastSyncedTime(now)
                _lastSyncedTime.value = now

                val summary = if (importedCount > 0) {
                    "Discovered $importedCount legacy files and created initial Vault Index."
                } else {
                    "Vault connected and ready. Storage is initialized."
                }

                Result.success(
                    SyncResult(
                        foldersCount = currentFolders.size,
                        filesCount = currentFiles.size,
                        newFilesCount = importedCount,
                        summary = summary
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync exception: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
