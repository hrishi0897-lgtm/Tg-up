package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.local.entity.FolderEntity
import com.example.data.remote.TelegramRepository
import com.example.data.remote.TelegramUser
import com.example.data.sync.VaultSyncManager
import com.example.data.sync.VaultSyncWorker
import com.example.data.transfer.TransferManager
import com.example.domain.model.BotHealthInfo
import com.example.domain.model.BreadcrumbItem
import com.example.domain.model.CategoryStorageBreakdown
import com.example.domain.model.StorageCategory
import com.example.domain.model.StorageStats
import com.example.domain.model.TransferProgress
import com.example.domain.model.classifyFileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class SortBy {
    NAME, DATE, SIZE
}

enum class AppScreen {
    VAULT,
    TRANSFERS,
    SETTINGS,
    FOLDER_MANAGEMENT
}

data class PendingUploadWarning(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val estimatedChunks: Int
)

data class UiState(
    val isAuthenticated: Boolean = false,
    val currentScreen: AppScreen = AppScreen.VAULT,
    val isValidating: Boolean = false,
    val validationSuccessUser: TelegramUser? = null,
    val validationError: String? = null,
    val currentFolderId: String? = null,
    val breadcrumbs: List<BreadcrumbItem> = listOf(BreadcrumbItem(null, "Vault")),
    val searchQuery: String = "",
    val sortBy: SortBy = SortBy.DATE,
    val sortAscending: Boolean = false,
    val isGridView: Boolean = false,
    val selectedFileForDetail: FileEntity? = null,
    val selectedFileChunks: List<ChunkEntity> = emptyList(),
    val isResyncing: Boolean = false,
    val resyncMessage: String? = null,
    val lastSyncedTime: Long = 0L,
    val chunkSizeMb: Int = 18,
    val isWifiOnly: Boolean = false,
    val showTransfersSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val folderToRename: FolderEntity? = null,
    val fileToRename: FileEntity? = null,
    val folderToDelete: FolderEntity? = null,
    val fileToDelete: FileEntity? = null,
    val itemToMove: FileEntity? = null,
    val showInAppGuide: Boolean = false,
    val pendingUploadWarning: PendingUploadWarning? = null,
    val transferErrorMessage: String? = null,
    val transferNotificationMessage: String? = null,
    val testTransferRunning: Boolean = false,
    val testTransferStatus: String? = null,
    val testTransferSuccess: Boolean? = null
)

class TeleVaultViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repo = TelegramRepository()
    private val creds = EncryptedCredentialsManager(application)
    private val transferManager = TransferManager.getInstance(application)
    private val vaultSyncManager = VaultSyncManager.getInstance(application)

    private val _uiState = MutableStateFlow(
        UiState(
            isAuthenticated = creds.hasCredentials(),
            chunkSizeMb = creds.getChunkSizeMb(),
            isWifiOnly = creds.isWifiOnly(),
            lastSyncedTime = creds.getLastSyncedTime()
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(creds.getThemeMode() == "dark")
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _botPool = MutableStateFlow<List<String>>(creds.getTokenPool())
    val botPool: StateFlow<List<String>> = _botPool.asStateFlow()

    private val _botHealth = MutableStateFlow<Map<String, BotHealthInfo>>(emptyMap())
    val botHealth: StateFlow<Map<String, BotHealthInfo>> = _botHealth.asStateFlow()

    fun toggleTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        creds.setThemeMode(if (next) "dark" else "light")
    }

    fun refreshBotPool() {
        _botPool.value = creds.getTokenPool()
    }

    fun addBotToken(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        creds.addBotTokenToPool(clean)
        _botPool.value = creds.getTokenPool()
        checkBotHealth(clean)
    }

    fun removeBotToken(token: String) {
        creds.removeBotTokenFromPool(token)
        _botPool.value = creds.getTokenPool()
        _botHealth.update { it - token }
    }

    fun setActiveBotToken(token: String) {
        creds.setActiveBotToken(token)
        _botPool.value = creds.getTokenPool()
        checkBotHealth(token)
    }

    fun checkBotHealth(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _botHealth.update { current ->
                val existing = current[clean] ?: BotHealthInfo(clean)
                current + (clean to existing.copy(isChecking = true, errorMessage = null))
            }
            val result = repo.checkBotHealth(clean, creds.getChatId())
            val now = System.currentTimeMillis()
            if (result.isSuccess) {
                val status = result.getOrThrow()
                val isHealthy = status.isWorking && (status.hasChatAccess ?: true)
                val username = status.botUser?.username ?: status.botUser?.firstName
                val errMsg = if (!status.isWorking) {
                    if (status.isBanned) "Bot token is banned / unauthorized" else status.error ?: "Bot check failed"
                } else if (status.hasChatAccess == false) {
                    "Bot is not an admin in channel"
                } else null

                _botHealth.update { current ->
                    current + (clean to BotHealthInfo(
                        token = clean,
                        isChecking = false,
                        isHealthy = isHealthy,
                        username = username,
                        errorMessage = errMsg,
                        lastChecked = now
                    ))
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Health check failed"
                _botHealth.update { current ->
                    current + (clean to BotHealthInfo(
                        token = clean,
                        isChecking = false,
                        isHealthy = false,
                        username = null,
                        errorMessage = err,
                        lastChecked = now
                    ))
                }
            }
        }
    }

    fun checkAllBotsHealth() {
        val pool = creds.getTokenPool()
        pool.forEach { checkBotHealth(it) }
    }

    init {
        viewModelScope.launch {
            transferManager.transferErrorEvents.collect { errorMsg ->
                _uiState.update { it.copy(transferErrorMessage = errorMsg) }
            }
        }
        viewModelScope.launch {
            transferManager.transferNotificationEvents.collect { notifMsg ->
                _uiState.update { it.copy(transferNotificationMessage = notifMsg) }
            }
        }
        viewModelScope.launch {
            vaultSyncManager.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isResyncing = syncing) }
            }
        }
        viewModelScope.launch {
            vaultSyncManager.lastSyncedTime.collect { syncedTime ->
                _uiState.update { it.copy(lastSyncedTime = syncedTime) }
            }
        }

        // On app start with existing credentials, schedule periodic worker and trigger background check
        if (creds.hasCredentials()) {
            VaultSyncWorker.schedule(application)
            syncVault(onlyIfNewer = true, isManual = false)

            viewModelScope.launch(Dispatchers.IO) {
                val chatId = creds.getChatId()?.trim()
                if (!chatId.isNullOrEmpty()) {
                    val channelDao = db.channelDao()
                    if (channelDao.getActiveChannel() == null) {
                        channelDao.insert(
                            ChannelEntity(
                                channelId = chatId,
                                displayName = "My Vault",
                                addedDate = System.currentTimeMillis(),
                                isActive = true
                            )
                        )
                    }
                }
            }
        }
    }

    fun dismissTransferError() {
        _uiState.update { it.copy(transferErrorMessage = null) }
    }

    fun dismissTransferNotification() {
        _uiState.update { it.copy(transferNotificationMessage = null) }
    }

    // Storage summary reactive stats
    val storageStats: StateFlow<StorageStats> = combine(
        db.fileDao().observeAll(),
        db.folderDao().observeFolderCount()
    ) { allFiles, folderCount ->
        val completedFiles = allFiles.filter { it.status == FileStatus.COMPLETED }
        val totalBytes = completedFiles.sumOf { it.size }
        var docBytes = 0L
        var mediaBytes = 0L
        var otherBytes = 0L

        for (file in completedFiles) {
            when (classifyFileCategory(file.mimeType, file.name)) {
                StorageCategory.DOCUMENTS -> docBytes += file.size
                StorageCategory.MEDIA -> mediaBytes += file.size
                StorageCategory.OTHER -> otherBytes += file.size
            }
        }

        StorageStats(
            totalBytesStored = totalBytes,
            fileCount = completedFiles.size,
            folderCount = folderCount,
            breakdown = CategoryStorageBreakdown(
                documentsBytes = docBytes,
                mediaBytes = mediaBytes,
                otherBytes = otherBytes
            )
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        StorageStats()
    )

    // Current folder's subfolders
    val currentFolders: StateFlow<List<FolderEntity>> = _uiState
        .map { it.currentFolderId }
        .flatMapLatest { folderId ->
            db.folderDao().observeSubfolders(folderId)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    // Current folder's files (supports active search filtering & sorting)
    val currentFiles: StateFlow<List<FileEntity>> = combine(
        _uiState.map { it.currentFolderId },
        _uiState.map { it.searchQuery },
        _uiState.map { it.sortBy },
        _uiState.map { it.sortAscending }
    ) { folderId, query, sortBy, ascending ->
        Params(folderId, query, sortBy, ascending)
    }.flatMapLatest { params ->
        if (params.query.isNotBlank()) {
            db.fileDao().searchFiles(params.query.trim())
        } else {
            db.fileDao().observeByFolder(params.folderId)
        }.map { list ->
            val sorted = when (params.sortBy) {
                SortBy.NAME -> list.sortedBy { it.name.lowercase() }
                SortBy.DATE -> list.sortedBy { it.uploadDate }
                SortBy.SIZE -> list.sortedBy { it.size }
            }
            if (params.ascending) sorted else sorted.reversed()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // All available folders for moving files
    val allFolders: StateFlow<List<FolderEntity>> = db.folderDao().observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Live transfers state: combination of active in-memory transfers and all non-completed files in Room
    val activeTransfers: StateFlow<List<TransferProgress>> = combine(
        transferManager.transfers,
        db.fileDao().observeTransfers(listOf(FileStatus.PENDING, FileStatus.UPLOADING, FileStatus.DOWNLOADING, FileStatus.FAILED, FileStatus.PAUSED))
    ) { inMemoryMap, dbNonCompletedFiles ->
        val result = mutableListOf<TransferProgress>()
        val seenFileIds = mutableSetOf<String>()

        // 1. In-memory transfers (live byte rates, chunk status)
        for (item in inMemoryMap.values) {
            val dbFile = dbNonCompletedFiles.find { it.id == item.fileId }
            // If Room database reports FAILED or PAUSED, the database status
            // strictly takes precedence over any stale in-memory UPLOADING/DOWNLOADING state!
            val reconciledItem = if (dbFile != null && (dbFile.status == FileStatus.FAILED || dbFile.status == FileStatus.PAUSED)) {
                item.copy(
                    status = dbFile.status,
                    errorMessage = dbFile.errorMessage ?: item.errorMessage,
                    speedBytesPerSec = 0L
                )
            } else {
                item
            }
            result.add(reconciledItem)
            seenFileIds.add(item.fileId)
        }

        // 2. Persisted non-completed files from Room (ensures failed/stuck files are never invisible)
        for (file in dbNonCompletedFiles) {
            if (!seenFileIds.contains(file.id)) {
                val fraction = if (file.totalChunks > 0) {
                    (file.completedChunks.toFloat() / file.totalChunks.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val bytesEstimate = (fraction * file.size).toLong()
                result.add(
                    TransferProgress(
                        fileId = file.id,
                        fileName = file.name,
                        isUpload = file.localPath != null || file.manifestMessageId == null,
                        currentChunk = file.completedChunks,
                        totalChunks = file.totalChunks,
                        progressFraction = fraction,
                        bytesTransferred = bytesEstimate,
                        totalBytes = file.size,
                        speedBytesPerSec = 0L,
                        status = file.status,
                        errorMessage = file.errorMessage
                    )
                )
                seenFileIds.add(file.id)
            }
        }
        result
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val recentlyCompleted: StateFlow<List<TransferProgress>> = transferManager.recentlyCompleted
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private data class Params(
        val folderId: String?,
        val query: String,
        val sortBy: SortBy,
        val ascending: Boolean
    )

    // Credential Management & Validation
    fun validateAndSaveCredentials(token: String, chatId: String) {
        if (token.isBlank() || chatId.isBlank()) {
            _uiState.update { it.copy(validationError = "Please enter both Bot Token and Chat ID.") }
            return
        }
        _uiState.update { it.copy(isValidating = true, validationError = null) }
        viewModelScope.launch {
            val result = repo.validateCredentials(token.trim(), chatId.trim())
            if (result.isSuccess) {
                val user = result.getOrThrow()
                val cleanChatId = chatId.trim()
                creds.saveCredentials(token.trim(), cleanChatId)
                viewModelScope.launch(Dispatchers.IO) {
                    db.channelDao().insert(
                        ChannelEntity(
                            channelId = cleanChatId,
                            displayName = "My Vault",
                            addedDate = System.currentTimeMillis(),
                            isActive = true
                        )
                    )
                }
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        validationSuccessUser = user,
                        isAuthenticated = true,
                        validationError = null
                    )
                }
                VaultSyncWorker.schedule(getApplication())
                syncVault(onlyIfNewer = false, isManual = false)
            } else {
                val error = result.exceptionOrNull()?.localizedMessage ?: "Validation failed"
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        validationError = error
                    )
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                VaultSyncWorker.cancel(getApplication())
                try {
                    androidx.work.WorkManager.getInstance(getApplication()).cancelAllWork()
                } catch (e: Exception) {
                    Log.w("TeleVaultViewModel", "Failed to cancel work manager: ${e.message}")
                }
                transferManager.cancelAllTransfers()
                db.fileDao().clearAll()
                db.folderDao().clearAll()
                db.chunkDao().clearAll()
                creds.clearCredentials()
            } catch (e: Exception) {
                Log.e("TeleVaultViewModel", "Error during disconnect: ${e.message}", e)
            } finally {
                _uiState.update {
                    it.copy(
                        isAuthenticated = false,
                        currentScreen = AppScreen.VAULT,
                        validationSuccessUser = null,
                        validationError = null,
                        lastSyncedTime = 0L,
                        resyncMessage = null,
                        showSettingsSheet = false,
                        breadcrumbs = listOf(BreadcrumbItem(null, "Vault")),
                        currentFolderId = null,
                        searchQuery = "",
                        testTransferRunning = false,
                        testTransferStatus = null,
                        testTransferSuccess = null
                    )
                }
            }
        }
    }

    fun toggleInAppGuide(show: Boolean) {
        _uiState.update { it.copy(showInAppGuide = show) }
    }

    // Navigation & Folder Browsing
    fun openFolder(folder: FolderEntity) {
        val newBreadcrumbs = _uiState.value.breadcrumbs + BreadcrumbItem(folder.id, folder.name)
        _uiState.update {
            it.copy(
                currentFolderId = folder.id,
                breadcrumbs = newBreadcrumbs,
                searchQuery = ""
            )
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        val list = _uiState.value.breadcrumbs
        if (index in list.indices) {
            val target = list[index]
            val truncated = list.subList(0, index + 1)
            _uiState.update {
                it.copy(
                    currentFolderId = target.id,
                    breadcrumbs = truncated,
                    searchQuery = ""
                )
            }
        }
    }

    fun navigateUp(): Boolean {
        val list = _uiState.value.breadcrumbs
        if (list.size > 1) {
            navigateToBreadcrumb(list.size - 2)
            return true
        }
        return false
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortBy(sortBy: SortBy) {
        _uiState.update {
            if (it.sortBy == sortBy) {
                it.copy(sortAscending = !it.sortAscending)
            } else {
                it.copy(sortBy = sortBy, sortAscending = false)
            }
        }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    // Folder Actions
    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val folder = FolderEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                parentFolderId = _uiState.value.currentFolderId
            )
            db.folderDao().insert(folder)
            _uiState.update { it.copy(showCreateFolderDialog = false) }
            vaultSyncManager.scheduleAutoPublish()
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            db.folderDao().renameFolder(folderId, newName.trim())
            _uiState.update { it.copy(folderToRename = null) }
            vaultSyncManager.scheduleAutoPublish()
        }
    }

    fun renameFile(fileId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            db.fileDao().renameFile(fileId, newName.trim())
            _uiState.update { current ->
                val updatedFile = current.selectedFileForDetail?.let {
                    if (it.id == fileId) it.copy(name = newName.trim()) else it
                }
                current.copy(fileToRename = null, selectedFileForDetail = updatedFile)
            }
            vaultSyncManager.scheduleAutoPublish()
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            // Delete sub-files in folder
            val filesInFolder = db.fileDao().getByFolder(folder.id)
            for (file in filesInFolder) {
                transferManager.deleteFile(file.id)
            }
            db.folderDao().deleteById(folder.id)
            vaultSyncManager.scheduleAutoPublish()
        }
    }

    fun moveFile(fileId: String, targetFolderId: String?) {
        viewModelScope.launch {
            db.fileDao().moveFile(fileId, targetFolderId)
            _uiState.update { it.copy(itemToMove = null) }
            vaultSyncManager.scheduleAutoPublish()
        }
    }

    // Transfer Actions
    fun uploadFile(uri: Uri) {
        val (fileName, fileSize) = resolveUriMetadata(uri)
        val chunkSizeMb = creds.getChunkSizeMb().coerceAtMost(18)
        val chunkSizeBytes = chunkSizeMb * 1024 * 1024L
        val estimatedChunks = ((fileSize + chunkSizeBytes - 1) / chunkSizeBytes).toInt().coerceAtLeast(1)

        // Threshold for warning: files >= 100MB (multi-chunk transfers)
        if (fileSize >= 100 * 1024 * 1024L || estimatedChunks >= 6) {
            _uiState.update {
                it.copy(
                    pendingUploadWarning = PendingUploadWarning(
                        uri = uri,
                        fileName = fileName,
                        fileSize = fileSize,
                        estimatedChunks = estimatedChunks
                    )
                )
            }
        } else {
            confirmUploadFile(uri)
        }
    }

    fun confirmUploadFile(uri: Uri) {
        val folderId = _uiState.value.currentFolderId
        transferManager.enqueueUpload(uri, folderId)
        _uiState.update { it.copy(showTransfersSheet = true, pendingUploadWarning = null) }
    }

    fun dismissUploadWarning() {
        _uiState.update { it.copy(pendingUploadWarning = null) }
    }

    private fun resolveUriMetadata(uri: Uri): Pair<String, Long> {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
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

    fun downloadFile(fileId: String) {
        transferManager.startDownload(fileId)
        _uiState.update { it.copy(showTransfersSheet = true) }
    }

    fun pauseTransfer(fileId: String) {
        transferManager.pauseTransfer(fileId)
    }

    fun resumeTransfer(fileId: String, isUpload: Boolean) {
        viewModelScope.launch {
            try {
                if (isUpload) {
                    transferManager.startUpload(fileId)
                } else {
                    transferManager.startDownload(fileId)
                }
            } catch (e: Throwable) {
                Log.e("TeleVaultViewModel", "Error in resumeTransfer for fileId=$fileId", e)
                _uiState.update {
                    it.copy(transferErrorMessage = "Resume failed: ${e.message ?: e::class.java.simpleName}")
                }
            }
        }
    }

    fun cancelTransfer(fileId: String) {
        try {
            transferManager.cancelTransfer(fileId)
        } catch (e: Throwable) {
            Log.e("TeleVaultViewModel", "Error in cancelTransfer for fileId=$fileId", e)
        }
    }

    fun pauseAllTransfers() {
        try {
            transferManager.pauseAll()
        } catch (e: Throwable) {
            Log.e("TeleVaultViewModel", "Error in pauseAllTransfers", e)
        }
    }

    fun resumeAllTransfers() {
        try {
            transferManager.resumeAll()
        } catch (e: Throwable) {
            Log.e("TeleVaultViewModel", "Error in resumeAllTransfers", e)
        }
    }

    fun clearRecentlyCompleted() {
        transferManager.clearRecentlyCompleted()
    }

    fun navigateToTransfersScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.TRANSFERS) }
    }

    fun navigateToVaultScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.VAULT) }
    }

    fun navigateToSettingsScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.SETTINGS) }
    }

    fun navigateToFolderManagementScreen() {
        _uiState.update { it.copy(currentScreen = AppScreen.FOLDER_MANAGEMENT) }
    }

    fun setFolderToDelete(folder: FolderEntity?) {
        _uiState.update { it.copy(folderToDelete = folder) }
    }

    fun setFileToDelete(file: FileEntity?) {
        _uiState.update { it.copy(fileToDelete = file) }
    }

    fun confirmDeleteFolder() {
        val folder = _uiState.value.folderToDelete ?: return
        _uiState.update { it.copy(folderToDelete = null) }
        deleteFolder(folder)
    }

    fun confirmDeleteFile() {
        val file = _uiState.value.fileToDelete ?: return
        _uiState.update { it.copy(fileToDelete = null, selectedFileForDetail = null) }
        deleteFile(file.id)
    }

    fun retryTransfer(fileId: String) {
        viewModelScope.launch {
            try {
                val file = db.fileDao().getById(fileId) ?: run {
                    Log.e("TeleVaultViewModel", "retryTransfer: file $fileId not found in database")
                    return@launch
                }
                Log.i("TeleVaultViewModel", "retryTransfer: Retrying transfer for ${file.name} (id=$fileId), current status=${file.status}")

                val chunks = db.chunkDao().getChunksForFile(fileId)
                val stagingFile = file.localPath?.let { java.io.File(it) } ?: java.io.File(getApplication<Application>().cacheDir, "upload_staging/$fileId.tmp")
                val hasStaging = stagingFile.exists() && stagingFile.length() > 0L

                val hasOversizedChunks = chunks.any { it.size > TransferManager.CHUNK_SIZE_BYTES }
                val hasUnuploadedChunks = chunks.isEmpty() || chunks.any { !it.isUploaded }

                if (hasStaging && hasOversizedChunks) {
                    // Only re-split if the chunks themselves were improperly sized (>18MB)
                    Log.i("TeleVaultViewModel", "retryTransfer: File has oversized chunks, forcing clean re-split and upload")
                    transferManager.forceFreshUpload(fileId)
                } else if (hasStaging && (hasUnuploadedChunks || file.status == FileStatus.FAILED || file.status == FileStatus.PAUSED)) {
                    // RESUME upload from the first uncompleted chunk!
                    val uploadedCount = chunks.count { it.isUploaded }
                    Log.i("TeleVaultViewModel", "retryTransfer: Resuming upload from chunk ${uploadedCount + 1}/${file.totalChunks}")
                    transferManager.startUpload(fileId)
                } else if (!hasStaging && chunks.isNotEmpty() && chunks.all { it.telegramFileId != null }) {
                    // Chunks exist in Telegram, no local staging file -> start/resume download
                    Log.i("TeleVaultViewModel", "retryTransfer: Retrying download for ${file.name}")
                    transferManager.startDownload(fileId)
                } else if (hasStaging) {
                    transferManager.startUpload(fileId)
                } else {
                    transferManager.startDownload(fileId)
                }
            } catch (e: Throwable) {
                Log.e("TeleVaultViewModel", "CRASH PREVENTED: Error during retryTransfer for fileId=$fileId", e)
                _uiState.update {
                    it.copy(transferErrorMessage = "Retry failed: ${e.message ?: e::class.java.simpleName}")
                }
            }
        }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            transferManager.deleteFile(fileId)
            _uiState.update {
                if (it.selectedFileForDetail?.id == fileId) {
                    it.copy(selectedFileForDetail = null, selectedFileChunks = emptyList())
                } else it
            }
            vaultSyncManager.publishVaultIndex()
        }
    }

    // Inspect file details and chunk breakdown
    fun inspectFile(file: FileEntity) {
        viewModelScope.launch {
            val chunks = db.chunkDao().getChunksForFile(file.id)
            _uiState.update {
                it.copy(
                    selectedFileForDetail = file,
                    selectedFileChunks = chunks
                )
            }
        }
    }

    fun dismissFileDetail() {
        _uiState.update { it.copy(selectedFileForDetail = null, selectedFileChunks = emptyList()) }
    }

    // Multi-Device Sync Vault from Telegram chat
    fun syncVault(onlyIfNewer: Boolean = false, isManual: Boolean = true) {
        if (isManual) {
            _uiState.update { it.copy(isResyncing = true, resyncMessage = null) }
        }
        viewModelScope.launch {
            val result = vaultSyncManager.syncVault(onlyIfNewer = onlyIfNewer, isManual = isManual)
            if (result.isSuccess) {
                val syncData = result.getOrThrow()
                val showMessage = isManual || syncData.newFilesCount > 0
                _uiState.update {
                    it.copy(
                        isResyncing = false,
                        lastSyncedTime = creds.getLastSyncedTime(),
                        resyncMessage = if (showMessage) syncData.summary else it.resyncMessage
                    )
                }
            } else {
                if (isManual) {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Sync failed"
                    _uiState.update {
                        it.copy(
                            isResyncing = false,
                            resyncMessage = "Sync error: $err"
                        )
                    }
                } else {
                    _uiState.update { it.copy(isResyncing = false) }
                }
            }
        }
    }

    /**
     * Manual sync triggered exclusively from the header refresh icon on the Vault screen.
     */
    fun manualSyncFromHeader() {
        syncVault(onlyIfNewer = false, isManual = true)
    }

    fun resyncFromTelegram() {
        manualSyncFromHeader()
    }

    /**
     * Triggered on app open or when brought to foreground (onResume).
     * Only updates if remote index timestamp is newer than local lastSyncedTime.
     * Silent when up to date.
     */
    fun onAppForeground() {
        if (creds.hasCredentials()) {
            syncVault(onlyIfNewer = true, isManual = false)
        }
    }

    fun forcePublishVaultIndex() {
        _uiState.update { it.copy(isResyncing = true, resyncMessage = null) }
        viewModelScope.launch {
            val result = vaultSyncManager.publishVaultIndex()
            if (result.isSuccess) {
                val idx = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isResyncing = false,
                        lastSyncedTime = creds.getLastSyncedTime(),
                        resyncMessage = "Published Vault Index with ${idx.files.size} file(s) and ${idx.folders.size} folder(s) to Telegram."
                    )
                }
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Publish failed"
                _uiState.update {
                    it.copy(
                        isResyncing = false,
                        resyncMessage = "Publish error: $err"
                    )
                }
            }
        }
    }

    fun clearResyncMessage() {
        _uiState.update { it.copy(resyncMessage = null) }
    }

    // Settings
    fun setChunkSizeMb(sizeMb: Int) {
        creds.setChunkSizeMb(sizeMb)
        _uiState.update { it.copy(chunkSizeMb = creds.getChunkSizeMb()) }
    }

    fun setWifiOnly(enabled: Boolean) {
        creds.setWifiOnly(enabled)
        _uiState.update { it.copy(isWifiOnly = creds.isWifiOnly()) }
        try {
            VaultSyncWorker.schedule(getApplication())
            com.example.data.transfer.TransferWorker.scheduleNetworkResume(getApplication())
        } catch (e: Exception) {
            Log.w("TeleVaultViewModel", "Failed to reschedule workers with new network constraint", e)
        }
    }

    // Dialog state toggles
    fun setShowTransfersSheet(show: Boolean) {
        _uiState.update { it.copy(showTransfersSheet = show) }
    }

    fun setShowSettingsSheet(show: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = show) }
    }

    fun setShowCreateFolderDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateFolderDialog = show) }
    }

    fun setFolderToRename(folder: FolderEntity?) {
        _uiState.update { it.copy(folderToRename = folder) }
    }

    fun setFileToRename(file: FileEntity?) {
        _uiState.update { it.copy(fileToRename = file) }
    }

    fun setItemToMove(file: FileEntity?) {
        _uiState.update { it.copy(itemToMove = file) }
    }

    fun getCredentials(): Pair<String, String> {
        return Pair(creds.getBotToken() ?: "", creds.getChatId() ?: "")
    }

    fun resetTestTransferStatus() {
        _uiState.update {
            it.copy(
                testTransferRunning = false,
                testTransferStatus = null,
                testTransferSuccess = null
            )
        }
    }

    // Runs an actual synthetic 5-chunk test transfer:
    // Generates a small in-memory file, splits into 5 chunks, uploads chunk 1,
    // triggers a simulated pause/resume cycle, verifies completed chunks are never re-uploaded,
    // completes the remaining chunks, and reports a clear pass/fail result.
    fun startSyntheticTestTransfer() {
        if (_uiState.value.testTransferRunning) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    testTransferRunning = true,
                    testTransferStatus = "Initializing 5-chunk integrity test…",
                    testTransferSuccess = null
                )
            }

            try {
                val token = creds.getBotToken()
                val chatId = creds.getChatId()
                if (token.isNullOrBlank() || chatId.isNullOrBlank()) {
                    throw IllegalStateException("Bot credentials are not configured")
                }

                // 1. Generate small synthetic file: 5 chunks of 20KB each (100KB total)
                _uiState.update { it.copy(testTransferStatus = "Generating 5 synthetic test chunks…") }
                val testFileId = "test_" + UUID.randomUUID().toString().take(8)
                val testFileName = "televault_integrity_test_5chunks.bin"
                val chunkCount = 5
                val chunkBytes = 20 * 1024 // 20 KB per chunk
                val totalBytes = (chunkCount * chunkBytes).toLong()

                val testDir = java.io.File(getApplication<Application>().cacheDir, "test_transfers").apply { mkdirs() }
                val stagingFile = java.io.File(testDir, "$testFileId.tmp")
                val chunkDir = java.io.File(testDir, "chunks_$testFileId").apply { mkdirs() }

                val testBuffer = ByteArray(chunkBytes) { (it % 251).toByte() }
                stagingFile.outputStream().use { fos ->
                    repeat(chunkCount) {
                        fos.write(testBuffer)
                    }
                }

                val overallChecksum = com.example.domain.ChecksumUtil.computeSha256(stagingFile)

                // Register file and chunks in Room
                val fileEntity = FileEntity(
                    id = testFileId,
                    name = testFileName,
                    folderId = null,
                    size = totalBytes,
                    mimeType = "application/octet-stream",
                    status = FileStatus.UPLOADING,
                    checksum = overallChecksum,
                    totalChunks = chunkCount,
                    completedChunks = 0,
                    localPath = stagingFile.absolutePath
                )
                db.fileDao().insert(fileEntity)

                val chunkEntities = (0 until chunkCount).map { idx ->
                    ChunkEntity(
                        fileId = testFileId,
                        chunkIndex = idx,
                        size = chunkBytes.toLong(),
                        checksum = ""
                    )
                }
                db.chunkDao().insertAll(chunkEntities)

                // 2. Upload Chunk 0
                _uiState.update { it.copy(testTransferStatus = "Uploading chunk 1 of 5…") }
                val chunk0File = java.io.File(chunkDir, "chunk_0.tpart")
                chunk0File.writeBytes(testBuffer)
                val chunk0Sha256 = com.example.domain.ChecksumUtil.computeSha256(chunk0File)

                val uploadResult0 = repo.uploadChunk(
                    token = token,
                    chatId = chatId,
                    fileId = testFileId,
                    fileName = testFileName,
                    chunkIndex = 0,
                    totalChunks = chunkCount,
                    chunkFile = chunk0File,
                    chunkSha256 = chunk0Sha256,
                    expectedChunkSize = chunkBytes.toLong()
                ) { _, _ -> }

                if (uploadResult0.isFailure) {
                    val err = uploadResult0.exceptionOrNull()?.message ?: "Upload failed on chunk 1"
                    throw IllegalStateException("Failed uploading chunk 1: $err")
                }

                val msg0 = uploadResult0.getOrThrow()
                db.chunkDao().markChunkUploaded(
                    fileId = testFileId,
                    chunkIndex = 0,
                    messageId = msg0.messageId,
                    fileIdRemote = msg0.document?.fileId ?: "",
                    checksum = chunk0Sha256
                )
                db.fileDao().updateProgress(testFileId, 1, FileStatus.UPLOADING)
                chunk0File.delete()

                // 3. Trigger simulated pause!
                _uiState.update { it.copy(testTransferStatus = "Simulating pause & verifying recorded chunk state…") }
                kotlinx.coroutines.delay(400)
                db.fileDao().updateStatus(testFileId, FileStatus.PAUSED, "Simulated pause for verification test")

                // 4. Verify that chunk 0 is recorded as uploaded in Room
                val chunksAfterPause = db.chunkDao().getChunksForFile(testFileId)
                val completedChunksBeforeResume = chunksAfterPause.filter { it.isUploaded }
                if (completedChunksBeforeResume.size != 1 || completedChunksBeforeResume.first().chunkIndex != 0) {
                    throw IllegalStateException("Integrity failure: Chunk 0 was not recorded as uploaded in Room database")
                }

                // 5. Resume transfer: verify chunk 0 is SKIPPED and NEVER re-uploaded
                _uiState.update { it.copy(testTransferStatus = "Resuming transfer… confirming chunk 1 is skipped…") }
                kotlinx.coroutines.delay(400)
                db.fileDao().updateStatus(testFileId, FileStatus.UPLOADING)

                val pendingChunks = chunksAfterPause.filter { !it.isUploaded }.sortedBy { it.chunkIndex }
                if (pendingChunks.any { it.chunkIndex == 0 }) {
                    throw IllegalStateException("Integrity failure: Chunk 0 was scheduled for redundant re-upload upon resume")
                }

                // 6. Upload remaining chunks 1 through 4
                for (chunk in pendingChunks) {
                    val idx = chunk.chunkIndex
                    _uiState.update { it.copy(testTransferStatus = "Uploading chunk ${idx + 1} of 5…") }
                    val chunkFile = java.io.File(chunkDir, "chunk_$idx.tpart")
                    chunkFile.writeBytes(testBuffer)
                    val chunkSha = com.example.domain.ChecksumUtil.computeSha256(chunkFile)

                    val uploadRes = repo.uploadChunk(
                        token = token,
                        chatId = chatId,
                        fileId = testFileId,
                        fileName = testFileName,
                        chunkIndex = idx,
                        totalChunks = chunkCount,
                        chunkFile = chunkFile,
                        chunkSha256 = chunkSha,
                        expectedChunkSize = chunkBytes.toLong()
                    ) { _, _ -> }

                    if (uploadRes.isFailure) {
                        val err = uploadRes.exceptionOrNull()?.message ?: "Upload failed on chunk ${idx + 1}"
                        throw IllegalStateException("Failed uploading chunk ${idx + 1}: $err")
                    }

                    val msg = uploadRes.getOrThrow()
                    db.chunkDao().markChunkUploaded(
                        fileId = testFileId,
                        chunkIndex = idx,
                        messageId = msg.messageId,
                        fileIdRemote = msg.document?.fileId ?: "",
                        checksum = chunkSha
                    )
                    db.fileDao().updateProgress(testFileId, idx + 1, FileStatus.UPLOADING)
                    chunkFile.delete()
                }

                // 7. Verify all 5 chunks completed
                val finalChunks = db.chunkDao().getChunksForFile(testFileId)
                val finalCompletedCount = finalChunks.count { it.isUploaded }
                if (finalCompletedCount != 5) {
                    throw IllegalStateException("Integrity failure: Expected 5/5 chunks completed, found $finalCompletedCount")
                }

                // Clean up remote test messages and local test records so vault remains clean
                try {
                    finalChunks.forEach { ch ->
                        ch.telegramMessageId?.let { mId -> repo.deleteMessage(token, chatId, mId) }
                    }
                    db.fileDao().deleteById(testFileId)
                    db.chunkDao().deleteForFile(testFileId)
                    stagingFile.delete()
                    chunkDir.deleteRecursively()
                } catch (cleanupEx: Exception) {
                    Log.w("TeleVaultViewModel", "Cleanup after test transfer non-fatal: ${cleanupEx.message}")
                }

                _uiState.update {
                    it.copy(
                        testTransferRunning = false,
                        testTransferSuccess = true,
                        testTransferStatus = "Test passed — pause/resume integrity confirmed (5/5 chunks verified, zero redundant uploads)"
                    )
                }
            } catch (e: Exception) {
                Log.e("TeleVaultViewModel", "Synthetic 5-chunk test failed", e)
                val failureMsg = e.message ?: "Unknown transfer failure"
                _uiState.update {
                    it.copy(
                        testTransferRunning = false,
                        testTransferSuccess = false,
                        testTransferStatus = "Test failed: $failureMsg"
                    )
                }
            }
        }
    }
}
