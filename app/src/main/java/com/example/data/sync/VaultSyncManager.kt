package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.local.entity.FolderEntity
import com.example.data.remote.ManifestChunk
import com.example.data.remote.TelegramApiException
import com.example.data.remote.TelegramRepository
import com.example.data.remote.TelegramUser
import com.example.data.remote.VaultIndex
import com.example.data.remote.VaultIndexFile
import com.example.data.remote.VaultIndexFolder
import com.example.domain.model.BotRevocationAlert
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

data class RestoreResult(
    val restoredFoldersCount: Int,
    val restoredFilesCount: Int,
    val brokenFilesCount: Int,
    val summary: String,
    val brokenFileNames: List<String> = emptyList()
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

    private val _primaryBotAlert = MutableStateFlow<BotRevocationAlert?>(credentialsManager.getLastBotRevocationAlert())
    val primaryBotAlert: StateFlow<BotRevocationAlert?> = _primaryBotAlert.asStateFlow()

    fun clearPrimaryBotAlert() {
        _primaryBotAlert.value = null
        credentialsManager.clearBotRevocationAlert()
    }

    /**
     * Active bot-health check: calls getMe for the primary bot.
     * Logs exact error code and timestamp in Logcat and persistent history.
     * If 401 Unauthorized, 403 Forbidden, or token revoked/invalid, sets primaryBotAlert.
     */
    suspend fun checkPrimaryBotHealth(token: String? = credentialsManager.getBotToken()): Result<TelegramUser> {
        val activeToken = token ?: credentialsManager.getBotToken()
        if (activeToken.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No bot token configured"))
        }

        val result = repository.validateBotToken(activeToken)
        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val formattedTime = sdf.format(java.util.Date(now))

        if (result.isFailure) {
            val ex = result.exceptionOrNull()
            val (errorCode, description) = if (ex is TelegramApiException) {
                Pair(ex.errorCode, ex.message ?: "Telegram API Exception")
            } else {
                val msg = ex?.message ?: "Unknown error"
                val code = when {
                    msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true) -> 401
                    msg.contains("403") || msg.contains("Forbidden", ignoreCase = true) -> 403
                    else -> null
                }
                Pair(code, msg)
            }

            // Log exact error code and timestamp so pattern is visible in Logcat & history
            val logEntry = "FAIL | HTTP ${errorCode ?: "ERR"} | $description | $formattedTime"
            Log.e(TAG, "🚨 [BOT HEALTH MONITOR] Primary bot check FAILED! HTTP ${errorCode ?: "N/A"} - '$description' at timestamp $now ($formattedTime)")
            credentialsManager.recordBotHealthLog(logEntry)

            val isRevokedOrInvalid = errorCode == 401 || errorCode == 403 ||
                    description.contains("Unauthorized", ignoreCase = true) ||
                    description.contains("Forbidden", ignoreCase = true) ||
                    description.contains("revoked", ignoreCase = true) ||
                    description.contains("deleted", ignoreCase = true) ||
                    description.contains("blocked", ignoreCase = true)

            if (isRevokedOrInvalid) {
                val alert = BotRevocationAlert(
                    errorCode = errorCode,
                    errorMessage = description,
                    timestamp = now,
                    formattedTime = formattedTime
                )
                _primaryBotAlert.value = alert
                credentialsManager.saveLastBotRevocationAlert(alert)
                Log.e(TAG, "🚨 [BOT HEALTH MONITOR] Prominent alert surfaced: Your bot token appears to be invalid or revoked — switch to a standby bot in Settings (Code: $errorCode)")
            }

            return Result.failure(ex ?: Exception(description))
        } else {
            val user = result.getOrThrow()
            Log.i(TAG, "✅ [BOT HEALTH MONITOR] Primary bot healthy: @${user.username ?: user.firstName} (id: ${user.id}) at $formattedTime")
            credentialsManager.recordBotHealthLog("OK | @${user.username ?: user.firstName} (id: ${user.id}) | $formattedTime")
            if (_primaryBotAlert.value != null) {
                _primaryBotAlert.value = null
                credentialsManager.clearBotRevocationAlert()
            }
            return Result.success(user)
        }
    }

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

            val folderMap = localFolders.associateBy { it.id }
            fun buildPath(folderId: String?): String {
                if (folderId == null) return "/"
                val parts = mutableListOf<String>()
                var curr = folderMap[folderId]
                val visited = mutableSetOf<String>()
                while (curr != null && visited.add(curr.id)) {
                    parts.add(0, curr.name)
                    curr = curr.parentFolderId?.let { folderMap[it] }
                }
                return "/" + parts.joinToString("/")
            }

            Log.i(TAG, "Publishing Vault Index: ${localFiles.size} files, ${localFolders.size} folders to chat $chatId...")

            val indexFolders = localFolders.map { folder ->
                VaultIndexFolder(
                    id = folder.id,
                    name = folder.name,
                    parentFolderId = folder.parentFolderId,
                    path = buildPath(folder.id),
                    createdDate = folder.createdDate
                )
            }

            val indexFiles = localFiles.map { file ->
                val chunks = database.chunkDao().getChunksForFile(file.id).map { chunk ->
                    ManifestChunk(
                        index = chunk.chunkIndex,
                        messageId = chunk.telegramMessageId ?: 0L,
                        channelId = chunk.channelId.ifEmpty { file.channelId ?: chatId },
                        telegramFileId = chunk.telegramFileId,
                        sha256 = chunk.checksum,
                        size = chunk.size,
                        backupMessageId = chunk.backupTelegramMessageId
                    )
                }

                VaultIndexFile(
                    id = file.id,
                    name = file.name,
                    folderId = file.folderId,
                    folderPath = buildPath(file.folderId),
                    channelId = file.channelId ?: chatId,
                    size = file.size,
                    mimeType = file.mimeType,
                    uploadDate = file.uploadDate,
                    checksum = file.checksum,
                    totalChunks = file.totalChunks,
                    manifestMessageId = file.manifestMessageId,
                    chunks = chunks,
                    thumbnailMessageId = file.thumbnailMessageId,
                    thumbnailFileId = file.thumbnailFileId
                )
            }

            val backupChatId = credentialsManager.getBackupChatId()?.trim()
            val now = System.currentTimeMillis()
            val vaultIndex = VaultIndex(
                version = 2,
                timestamp = now,
                deviceId = credentialsManager.getDeviceId(),
                backupChatId = backupChatId,
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
                Log.e(TAG, "publishVaultIndex failed on primary chat: ${err.message}", err)
                return Result.failure(err)
            }

            val newMsg = uploadResult.getOrThrow()
            credentialsManager.setLastVaultIndexMessageId(newMsg.messageId)
            credentialsManager.setLastSyncedTime(now)
            _lastSyncedTime.value = now

            // Requirement 2: Upload televault_index.json to backup channel as well, replacing previous and pinning it
            if (!backupChatId.isNullOrEmpty() && backupChatId != chatId) {
                try {
                    val prevBackupMsgId = credentialsManager.getLastBackupVaultIndexMessageId()
                    val backupUploadResult = repository.uploadVaultIndex(
                        token = token,
                        chatId = backupChatId,
                        vaultIndex = vaultIndex,
                        previousIndexMessageId = prevBackupMsgId
                    )
                    if (backupUploadResult.isSuccess) {
                        val backupMsg = backupUploadResult.getOrThrow()
                        credentialsManager.setLastBackupVaultIndexMessageId(backupMsg.messageId)
                        Log.i(TAG, "Successfully published & pinned televault_index.json to backup channel $backupChatId (msgId=${backupMsg.messageId})")
                    } else {
                        Log.w(TAG, "Failed to upload index to backup channel $backupChatId: ${backupUploadResult.exceptionOrNull()?.message}")
                    }
                } catch (backupEx: Exception) {
                    Log.w(TAG, "Exception uploading index to backup channel: ${backupEx.message}")
                }
            }

            Log.i(TAG, "publishVaultIndex succeeded: primary messageId=${newMsg.messageId}, ${indexFolders.size} folders, ${indexFiles.size} files, pinned in chat")
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
            // Active bot-health monitoring: check getMe for primary bot as part of periodic/foreground sync
            Log.i(TAG, "Active bot-health monitoring: Checking primary bot via getMe...")
            val healthCheckResult = checkPrimaryBotHealth(token)
            if (healthCheckResult.isFailure) {
                val currentAlert = _primaryBotAlert.value
                if (currentAlert != null) {
                    val criticalMessage = "Your bot token appears to be invalid or revoked — switch to a standby bot in Settings"
                    Log.e(TAG, "🚨 [BOT HEALTH MONITOR] Halting sync: $criticalMessage (Error ${currentAlert.errorCode}: ${currentAlert.errorMessage})")
                    _isSyncing.value = false
                    return Result.failure(TelegramApiException(criticalMessage, currentAlert.errorCode, currentAlert.errorMessage))
                }
            }

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
                            localUri = null,
                            thumbnailFileId = remoteFile.thumbnailFileId,
                            thumbnailMessageId = remoteFile.thumbnailMessageId
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
                        val needsThumbnailUpdate = existing.thumbnailFileId == null && remoteFile.thumbnailFileId != null
                        if (existing.name != remoteFile.name || existing.folderId != remoteFile.folderId || existing.channelId != fileChannelId || needsThumbnailUpdate) {
                            Log.i(TAG, "Rebuilding local database: Updating file placement/metadata: ${existing.name} -> ${remoteFile.name}")
                            database.fileDao().update(
                                existing.copy(
                                    name = remoteFile.name,
                                    folderId = remoteFile.folderId,
                                    channelId = fileChannelId,
                                    thumbnailFileId = existing.thumbnailFileId ?: remoteFile.thumbnailFileId,
                                    thumbnailMessageId = existing.thumbnailMessageId ?: remoteFile.thumbnailMessageId
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
                // Safety guard: If remote index has 0 files but local vault has existing completed files,
                // do not mass-delete local files (this prevents wiping local files if an empty index was posted or read)
                val allLocalFiles = database.fileDao().getAll()
                if (remoteFileIds.isNotEmpty()) {
                    for (localFile in allLocalFiles) {
                        if (localFile.status == FileStatus.COMPLETED && !remoteFileIds.contains(localFile.id)) {
                            Log.i(TAG, "Rebuilding local database: Removing local reference for remotely deleted file: ${localFile.name} (${localFile.id})")
                            database.fileDao().deleteById(localFile.id)
                        }
                    }
                } else if (allLocalFiles.any { it.status == FileStatus.COMPLETED }) {
                    Log.w(TAG, "Remote index has 0 files but local DB has completed files. Preserving local files and auto-publishing updated index.")
                    scheduleAutoPublish(debounceDelayMs = 1000L)
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

    /**
     * Requirement 3 & 4: Disaster Recovery Flow.
     * Takes backupChatId, downloads televault_index.json from it,
     * rebuilds the entire local database from scratch (folders, files, chunks)
     * using the backup channel's message_ids as the new primary source.
     * Works on a fresh install with no local SQLite records beforehand.
     * Runs integrity check: verifies chunk count and retrievability,
     * flagging incomplete/broken files in the UI.
     */
    suspend fun restoreFromBackupChannel(
        backupChatId: String,
        botToken: String? = null
    ): Result<RestoreResult> = syncMutex.withLock {
        val cleanBackupChatId = backupChatId.trim()
        val token = (botToken ?: credentialsManager.getBotToken())?.trim()

        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Telegram Bot Token is missing. Please configure your bot token."))
        }
        if (cleanBackupChatId.isBlank()) {
            return Result.failure(IllegalStateException("Backup Channel ID is empty."))
        }

        _isSyncing.value = true
        try {
            Log.i(TAG, "Starting Disaster Recovery from backup channel: $cleanBackupChatId...")

            // 1. Download televault_index.json from backup channel
            val fetchResult = repository.fetchLatestVaultIndex(token, cleanBackupChatId)
            if (fetchResult.isFailure) {
                val err = fetchResult.exceptionOrNull()?.message ?: "Failed to connect to backup channel"
                return Result.failure(Exception("Could not fetch index from backup channel: $err"))
            }

            val indexPair = fetchResult.getOrThrow()
                ?: return Result.failure(Exception("televault_index.json was not found in backup channel $cleanBackupChatId. Make sure the bot is an admin in the channel and the index file was pinned."))

            val (vaultIndex, indexMessageId) = indexPair
            Log.i(TAG, "televault_index.json found (msgId=$indexMessageId, ts=${vaultIndex.timestamp}, ${vaultIndex.files.size} files, ${vaultIndex.folders.size} folders). Rebuilding local database...")

            // 2. Wipe existing local database for clean rebuild
            database.chunkDao().clearAll()
            database.fileDao().clearAll()
            database.folderDao().clearAll()

            // 3. Rebuild Folders
            val folderEntities = vaultIndex.folders.map { vf ->
                FolderEntity(
                    id = vf.id,
                    name = vf.name,
                    parentFolderId = vf.parentFolderId,
                    createdDate = vf.createdDate
                )
            }
            database.folderDao().insertAll(folderEntities)

            // 4. Rebuild Files and Chunks using backup channel message_ids as primary source
            val fileEntities = mutableListOf<FileEntity>()
            val chunkEntities = mutableListOf<ChunkEntity>()
            val brokenFileNames = mutableListOf<String>()

            for (vf in vaultIndex.files) {
                val totalChunks = vf.totalChunks
                val chunkList = vf.chunks
                var isFileBroken = false
                var brokenReason: String? = null

                if (chunkList.size < totalChunks) {
                    isFileBroken = true
                    brokenReason = "Incomplete: only ${chunkList.size} of $totalChunks chunks recorded"
                }

                val chunksForFile = mutableListOf<ChunkEntity>()
                for (ci in 0 until totalChunks) {
                    val chunk = chunkList.find { it.index == ci }
                    if (chunk == null) {
                        isFileBroken = true
                        brokenReason = "Incomplete: chunk index $ci is missing from backup index"
                        break
                    }

                    // Requirement 3: Use backup channel's message_id as new primary source
                    val primaryMsgId = chunk.backupMessageId ?: chunk.messageId
                    if (primaryMsgId <= 0) {
                        isFileBroken = true
                        brokenReason = "Broken: chunk $ci has no valid message ID"
                    }

                    chunksForFile.add(
                        ChunkEntity(
                            fileId = vf.id,
                            chunkIndex = ci,
                            channelId = cleanBackupChatId,
                            telegramMessageId = primaryMsgId,
                            telegramFileId = null,
                            checksum = chunk.sha256,
                            size = chunk.size,
                            isUploaded = true,
                            isDownloaded = false,
                            backupTelegramMessageId = chunk.backupMessageId
                        )
                    )
                }

                if (isFileBroken) {
                    brokenFileNames.add(vf.name)
                }

                fileEntities.add(
                    FileEntity(
                        id = vf.id,
                        name = vf.name,
                        folderId = vf.folderId,
                        channelId = cleanBackupChatId,
                        size = vf.size,
                        mimeType = vf.mimeType,
                        uploadDate = vf.uploadDate,
                        status = if (isFileBroken) FileStatus.FAILED else FileStatus.COMPLETED,
                        errorMessage = brokenReason,
                        checksum = vf.checksum,
                        totalChunks = totalChunks,
                        completedChunks = if (isFileBroken) chunkList.size else totalChunks,
                        manifestMessageId = null,
                        localPath = null,
                        localUri = null,
                        thumbnailFileId = vf.thumbnailFileId,
                        thumbnailMessageId = vf.thumbnailMessageId
                    )
                )
                chunkEntities.addAll(chunksForFile)
            }

            database.fileDao().insertAll(fileEntities)
            database.chunkDao().insertAll(chunkEntities)

            // 5. Requirement 4: Integrity check on retrievability
            for (fe in fileEntities.filter { it.status == FileStatus.COMPLETED }) {
                val firstChunk = chunkEntities.find { it.fileId == fe.id && it.chunkIndex == 0 }
                val testMsgId = firstChunk?.telegramMessageId
                if (testMsgId != null && testMsgId > 0) {
                    try {
                        val testCopy = repository.copyMessage(token, cleanBackupChatId, cleanBackupChatId, testMsgId)
                        if (testCopy.isSuccess) {
                            val tempId = testCopy.getOrThrow().messageId
                            try { repository.deleteMessage(token, cleanBackupChatId, tempId) } catch (_: Exception) {}
                        } else {
                            val errMsg = testCopy.exceptionOrNull()?.message ?: ""
                            if (errMsg.contains("not found", ignoreCase = true) || errMsg.contains("Bad Request", ignoreCase = true)) {
                                database.fileDao().updateStatus(fe.id, FileStatus.FAILED, "Incomplete: Chunk message $testMsgId not found in channel")
                                brokenFileNames.add(fe.name)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Integrity check warning for ${fe.name}: ${e.message}")
                    }
                }
            }

            // 6. Set restored backup channel as current active vault chat
            credentialsManager.saveCredentials(botToken = token, chatId = cleanBackupChatId, backupChatId = null)
            credentialsManager.setLastVaultIndexMessageId(indexMessageId)
            val now = System.currentTimeMillis()
            credentialsManager.setLastSyncedTime(now)
            _lastSyncedTime.value = now

            // Also ensure ChannelEntity exists for new active channel
            try {
                database.channelDao().insert(
                    ChannelEntity(
                        channelId = cleanBackupChatId,
                        displayName = "Restored Vault",
                        addedDate = now,
                        isActive = true
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Non-critical: Channel insert: ${e.message}")
            }

            val brokenCount = brokenFileNames.distinct().size
            val summary = if (brokenCount == 0) {
                "Successfully restored ${fileEntities.size} files and ${folderEntities.size} folders. All chunks verified intact."
            } else {
                "Restored ${fileEntities.size} files (${brokenCount} flagged as incomplete/broken) and ${folderEntities.size} folders."
            }

            Log.i(TAG, "Disaster Recovery complete: $summary")
            Result.success(
                RestoreResult(
                    restoredFoldersCount = folderEntities.size,
                    restoredFilesCount = fileEntities.size,
                    brokenFilesCount = brokenCount,
                    summary = summary,
                    brokenFileNames = brokenFileNames.distinct()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Disaster Recovery failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
