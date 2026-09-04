package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.local.entity.FolderEntity
import com.example.data.remote.TelegramRepository
import com.example.data.remote.TelegramUser
import com.example.data.transfer.TransferManager
import com.example.domain.model.BreadcrumbItem
import com.example.domain.model.StorageStats
import com.example.domain.model.TransferProgress
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

data class UiState(
    val isAuthenticated: Boolean = false,
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
    val chunkSizeMb: Int = 45,
    val showTransfersSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val folderToRename: FolderEntity? = null,
    val itemToMove: FileEntity? = null,
    val showInAppGuide: Boolean = false
)

class TeleVaultViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repo = TelegramRepository()
    private val creds = EncryptedCredentialsManager(application)
    private val transferManager = TransferManager.getInstance(application)

    private val _uiState = MutableStateFlow(
        UiState(
            isAuthenticated = creds.hasCredentials(),
            chunkSizeMb = creds.getChunkSizeMb()
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Storage summary reactive stats
    val storageStats: StateFlow<StorageStats> = combine(
        db.fileDao().observeTotalStorageUsed(),
        db.fileDao().observeCompletedFileCount(),
        db.folderDao().observeFolderCount()
    ) { totalBytes, fileCount, folderCount ->
        StorageStats(
            totalBytesStored = totalBytes,
            fileCount = fileCount,
            folderCount = folderCount
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

    // Live transfers state
    val activeTransfers: StateFlow<List<TransferProgress>> = transferManager.transfers
        .map { it.values.toList() }
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
                creds.saveCredentials(token.trim(), chatId.trim())
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        validationSuccessUser = user,
                        isAuthenticated = true,
                        validationError = null
                    )
                }
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
        creds.clearCredentials()
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                validationSuccessUser = null,
                validationError = null
            )
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
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            db.folderDao().renameFolder(folderId, newName.trim())
            _uiState.update { it.copy(folderToRename = null) }
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
        }
    }

    fun moveFile(fileId: String, targetFolderId: String?) {
        viewModelScope.launch {
            db.fileDao().moveFile(fileId, targetFolderId)
            _uiState.update { it.copy(itemToMove = null) }
        }
    }

    // Transfer Actions
    fun uploadFile(uri: Uri) {
        val folderId = _uiState.value.currentFolderId
        transferManager.enqueueUpload(uri, folderId)
        _uiState.update { it.copy(showTransfersSheet = true) }
    }

    fun downloadFile(fileId: String) {
        transferManager.startDownload(fileId)
        _uiState.update { it.copy(showTransfersSheet = true) }
    }

    fun pauseTransfer(fileId: String) {
        transferManager.pauseTransfer(fileId)
    }

    fun resumeTransfer(fileId: String, isUpload: Boolean) {
        if (isUpload) {
            transferManager.startUpload(fileId)
        } else {
            transferManager.startDownload(fileId)
        }
    }

    fun cancelTransfer(fileId: String) {
        transferManager.cancelTransfer(fileId)
    }

    fun retryTransfer(fileId: String) {
        viewModelScope.launch {
            val file = db.fileDao().getById(fileId) ?: return@launch
            val chunks = db.chunkDao().getChunksForFile(fileId)
            val needsUpload = chunks.any { !it.isUploaded }
            if (needsUpload) {
                transferManager.startUpload(fileId)
            } else {
                transferManager.startDownload(fileId)
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

    // Resync from Telegram chat
    fun resyncFromTelegram() {
        _uiState.update { it.copy(isResyncing = true, resyncMessage = null) }
        viewModelScope.launch {
            val result = transferManager.resyncFromTelegram()
            if (result.isSuccess) {
                val count = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isResyncing = false,
                        resyncMessage = "Sync complete. Discovered $count files from Telegram chat."
                    )
                }
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Sync failed"
                _uiState.update {
                    it.copy(
                        isResyncing = false,
                        resyncMessage = "Sync error: $err"
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

    fun setItemToMove(file: FileEntity?) {
        _uiState.update { it.copy(itemToMove = file) }
    }

    fun getCredentials(): Pair<String, String> {
        return Pair(creds.getBotToken() ?: "", creds.getChatId() ?: "")
    }
}
