package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.data.transfer.TransferWorker
import com.example.ui.screens.CreateFolderDialog
import com.example.ui.screens.FileDetailSheet
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MoveFileDialog
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.RenameFolderDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransfersSheet
import com.example.ui.theme.TeleVaultTheme
import com.example.ui.theme.OledBlack
import com.example.ui.viewmodel.TeleVaultViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TeleVaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Schedule background WorkManager to resume any paused transfers upon network reconnection
        TransferWorker.scheduleNetworkResume(applicationContext)

        setContent {
            TeleVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OledBlack
                ) {
                    TeleVaultApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun TeleVaultApp(viewModel: TeleVaultViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val folders by viewModel.currentFolders.collectAsState()
    val files by viewModel.currentFiles.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()

    // File upload picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadFile(uri)
        }
    }

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Handle back button for folder hierarchy navigation
    BackHandler(enabled = uiState.breadcrumbs.size > 1) {
        viewModel.navigateUp()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack),
        color = OledBlack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!uiState.isAuthenticated) {
                OnboardingScreen(
                    isValidating = uiState.isValidating,
                    validationError = uiState.validationError,
                    onConnect = { token, chatId ->
                        viewModel.validateAndSaveCredentials(token, chatId)
                    }
                )
            } else {
                HomeScreen(
                    storageStats = storageStats,
                    folders = folders,
                    files = files,
                    breadcrumbs = uiState.breadcrumbs,
                    searchQuery = uiState.searchQuery,
                    sortBy = uiState.sortBy,
                    sortAscending = uiState.sortAscending,
                    isGridView = uiState.isGridView,
                    activeTransfers = activeTransfers,
                    isResyncing = uiState.isResyncing,
                    resyncMessage = uiState.resyncMessage,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onSortChange = { viewModel.setSortBy(it) },
                    onToggleViewMode = { viewModel.toggleViewMode() },
                    onFolderClick = { viewModel.openFolder(it) },
                    onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) },
                    onFileClick = { viewModel.inspectFile(it) },
                    onRenameFolder = { viewModel.setFolderToRename(it) },
                    onDeleteFolder = { viewModel.deleteFolder(it) },
                    onCreateFolderClick = { viewModel.setShowCreateFolderDialog(true) },
                    onUploadFileClick = { filePickerLauncher.launch("*/*") },
                    onOpenTransfers = { viewModel.setShowTransfersSheet(true) },
                    onOpenSettings = { viewModel.setShowSettingsSheet(true) },
                    onResync = { viewModel.resyncFromTelegram() },
                    onDismissResyncMsg = { viewModel.clearResyncMessage() }
                )
            }

            // File Detail & Chunk Inspector Sheet
            if (uiState.selectedFileForDetail != null) {
                FileDetailSheet(
                    file = uiState.selectedFileForDetail!!,
                    chunks = uiState.selectedFileChunks,
                    onDismiss = { viewModel.dismissFileDetail() },
                    onDownload = { viewModel.downloadFile(uiState.selectedFileForDetail!!.id) },
                    onMove = { viewModel.setItemToMove(uiState.selectedFileForDetail) },
                    onDelete = { viewModel.deleteFile(uiState.selectedFileForDetail!!.id) }
                )
            }

            // Active Transfers Sheet
            if (uiState.showTransfersSheet) {
                TransfersSheet(
                    transfers = activeTransfers,
                    onDismiss = { viewModel.setShowTransfersSheet(false) },
                    onPause = { viewModel.pauseTransfer(it) },
                    onResume = { id, isUpload -> viewModel.resumeTransfer(id, isUpload) },
                    onCancel = { viewModel.cancelTransfer(it) },
                    onRetry = { viewModel.retryTransfer(it) }
                )
            }

            // Settings Sheet
            if (uiState.showSettingsSheet) {
                val (token, chatId) = viewModel.getCredentials()
                SettingsScreen(
                    botTokenMasked = token,
                    chatId = chatId,
                    chunkSizeMb = uiState.chunkSizeMb,
                    onChunkSizeChange = { viewModel.setChunkSizeMb(it) },
                    onResyncClick = { viewModel.resyncFromTelegram() },
                    onDisconnect = { viewModel.disconnect() },
                    onDismiss = { viewModel.setShowSettingsSheet(false) }
                )
            }

            // Create Folder Dialog
            if (uiState.showCreateFolderDialog) {
                CreateFolderDialog(
                    onDismiss = { viewModel.setShowCreateFolderDialog(false) },
                    onConfirm = { folderName -> viewModel.createFolder(folderName) }
                )
            }

            // Rename Folder Dialog
            if (uiState.folderToRename != null) {
                val folder = uiState.folderToRename!!
                RenameFolderDialog(
                    currentName = folder.name,
                    onDismiss = { viewModel.setFolderToRename(null) },
                    onConfirm = { newName -> viewModel.renameFolder(folder.id, newName) }
                )
            }

            // Move File Dialog
            if (uiState.itemToMove != null) {
                val file = uiState.itemToMove!!
                MoveFileDialog(
                    fileName = file.name,
                    allFolders = allFolders,
                    onDismiss = { viewModel.setItemToMove(null) },
                    onSelectFolder = { targetFolderId -> viewModel.moveFile(file.id, targetFolderId) }
                )
            }
        }
    }
}
