package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import com.example.ui.theme.LocalReduceMotion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.data.transfer.TransferService
import com.example.data.transfer.TransferWorker
import com.example.ui.screens.CreateFolderDialog
import com.example.ui.screens.FileDetailSheet
import com.example.ui.screens.FolderManagementScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LargeFileConfirmationDialog
import com.example.ui.screens.MoveFileDialog
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.RenameFileDialog
import com.example.ui.screens.RenameFolderDialog
import com.example.ui.screens.SettingsScreen
import androidx.core.content.FileProvider
import java.io.File
import com.example.ui.screens.TransfersScreen
import com.example.ui.screens.TransfersSheet
import com.example.ui.theme.TeleVaultTheme
import com.example.ui.theme.OledBlack
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.TeleVaultViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TeleVaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        // Schedule background WorkManager to resume any paused transfers upon network reconnection
        TransferWorker.scheduleNetworkResume(applicationContext)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            TeleVaultTheme(isDark = isDarkTheme) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !isDarkTheme
                            isAppearanceLightNavigationBars = !isDarkTheme
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TeleVaultApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val isTransfersAction = intent.action == TransferService.ACTION_OPEN_TRANSFERS ||
                intent.getStringExtra(TransferService.EXTRA_NAVIGATE_TO) == TransferService.DESTINATION_TRANSFERS
        if (isTransfersAction) {
            viewModel.navigateToTransfersScreen()
        }
    }
}

@Composable
fun TeleVaultApp(viewModel: TeleVaultViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val folders by viewModel.currentFolders.collectAsState()
    val files by viewModel.currentFiles.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val recentlyCompleted by viewModel.recentlyCompleted.collectAsState()
    val botPool by viewModel.botPool.collectAsState()
    val botHealth by viewModel.botHealth.collectAsState()

    // File upload picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadFile(uri)
        }
    }

    val context = LocalContext.current
    LaunchedEffect(uiState.transferErrorMessage) {
        val error = uiState.transferErrorMessage
        if (!error.isNullOrBlank()) {
            Toast.makeText(context, "Upload Failed: $error", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(uiState.transferNotificationMessage) {
        val notif = uiState.transferNotificationMessage
        if (!notif.isNullOrBlank()) {
            Toast.makeText(context, notif, Toast.LENGTH_SHORT).show()
            viewModel.dismissTransferNotification()
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

    // Handle back button for transfers screen
    BackHandler(enabled = uiState.currentScreen == AppScreen.TRANSFERS) {
        viewModel.navigateToVaultScreen()
    }

    // Handle back button for settings screen
    BackHandler(enabled = uiState.currentScreen == AppScreen.SETTINGS) {
        viewModel.navigateToVaultScreen()
    }

    // Handle back button for folder management screen
    BackHandler(enabled = uiState.currentScreen == AppScreen.FOLDER_MANAGEMENT) {
        viewModel.navigateToVaultScreen()
    }

    // Handle back button for folder hierarchy navigation
    BackHandler(enabled = uiState.currentScreen == AppScreen.VAULT && uiState.breadcrumbs.size > 1) {
        viewModel.navigateUp()
    }

    val reduceMotion = LocalReduceMotion.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = Pair(uiState.isAuthenticated, uiState.currentScreen),
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                    } else {
                        val (initialAuth, initialScreen) = initialState
                        val (targetAuth, targetScreen) = targetState
                        if (initialAuth != targetAuth) {
                            fadeIn(animationSpec = tween(260)) togetherWith fadeOut(animationSpec = tween(200))
                        } else {
                            val initialOrder = when (initialScreen) {
                                AppScreen.VAULT -> 0
                                AppScreen.TRANSFERS -> 1
                                AppScreen.SETTINGS -> 2
                                AppScreen.FOLDER_MANAGEMENT -> 3
                            }
                            val targetOrder = when (targetScreen) {
                                AppScreen.VAULT -> 0
                                AppScreen.TRANSFERS -> 1
                                AppScreen.SETTINGS -> 2
                                AppScreen.FOLDER_MANAGEMENT -> 3
                            }
                            if (targetOrder >= initialOrder) {
                                (slideInHorizontally(
                                    initialOffsetX = { (it * 0.12f).toInt() },
                                    animationSpec = tween(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                                ) + fadeIn(animationSpec = tween(240))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { -(it * 0.12f).toInt() },
                                    animationSpec = tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                                ) + fadeOut(animationSpec = tween(180)))
                            } else {
                                (slideInHorizontally(
                                    initialOffsetX = { -(it * 0.12f).toInt() },
                                    animationSpec = tween(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                                ) + fadeIn(animationSpec = tween(240))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { (it * 0.12f).toInt() },
                                    animationSpec = tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                                ) + fadeOut(animationSpec = tween(180)))
                            }
                        }
                    }
                },
                label = "screen_shared_axis_transition"
            ) { (isAuthenticated, currentScreen) ->
                if (!isAuthenticated) {
                    OnboardingScreen(
                        isValidating = uiState.isValidating,
                        validationError = uiState.validationError,
                        onConnect = { token, chatId ->
                            viewModel.validateAndSaveCredentials(token, chatId)
                        }
                    )
                } else if (currentScreen == AppScreen.TRANSFERS) {
                    TransfersScreen(
                        transfers = activeTransfers,
                        recentlyCompleted = recentlyCompleted,
                        onBack = { viewModel.navigateToVaultScreen() },
                        onPause = { viewModel.pauseTransfer(it) },
                        onResume = { id, isUpload -> viewModel.resumeTransfer(id, isUpload) },
                        onCancel = { viewModel.cancelTransfer(it) },
                        onRetry = { viewModel.retryTransfer(it) },
                        onPauseAll = { viewModel.pauseAllTransfers() },
                        onResumeAll = { viewModel.resumeAllTransfers() },
                        onClearCompleted = { viewModel.clearRecentlyCompleted() },
                        onNavigateToVault = { viewModel.navigateToVaultScreen() }
                    )
                } else if (currentScreen == AppScreen.SETTINGS) {
                    val (token, chatId) = viewModel.getCredentials()
                    SettingsScreen(
                        botTokenMasked = token,
                        chatId = chatId,
                        chunkSizeMb = uiState.chunkSizeMb,
                        isWifiOnly = uiState.isWifiOnly,
                        isDarkTheme = isDarkTheme,
                        testTransferRunning = uiState.testTransferRunning,
                        testTransferStatus = uiState.testTransferStatus,
                        testTransferSuccess = uiState.testTransferSuccess,
                        botTokenPool = botPool,
                        botHealthMap = botHealth,
                        onAddBotToken = { viewModel.addBotToken(it) },
                        onRemoveBotToken = { viewModel.removeBotToken(it) },
                        onSetActiveBotToken = { viewModel.setActiveBotToken(it) },
                        onCheckBotHealth = { viewModel.checkBotHealth(it) },
                        onCheckAllBotsHealth = { viewModel.checkAllBotsHealth() },
                        onToggleTheme = { viewModel.toggleTheme() },
                        onChunkSizeChange = { viewModel.setChunkSizeMb(it) },
                        onWifiOnlyChange = { viewModel.setWifiOnly(it) },
                        onDisconnect = { viewModel.disconnect() },
                        onDismiss = { viewModel.navigateToVaultScreen() },
                        onStartTestTransfer = { viewModel.startSyntheticTestTransfer() },
                        onDismissTestStatus = { viewModel.resetTestTransferStatus() }
                    )
                } else if (currentScreen == AppScreen.FOLDER_MANAGEMENT) {
                    FolderManagementScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateToVaultScreen() }
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
                        lastSyncedTime = uiState.lastSyncedTime,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { viewModel.toggleTheme() },
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
                        onOpenTransfers = { viewModel.navigateToTransfersScreen() },
                        onOpenSettings = { viewModel.navigateToSettingsScreen() },
                        onOpenFolderManagement = { viewModel.navigateToFolderManagementScreen() },
                        onResync = { viewModel.manualSyncFromHeader() },
                        onDismissResyncMsg = { viewModel.clearResyncMessage() },
                        transferErrorMessage = uiState.transferErrorMessage,
                        onDismissTransferError = { viewModel.dismissTransferError() }
                    )
                }
            }

            // File Detail & Clean File View Sheet
            if (uiState.selectedFileForDetail != null) {
                val currentDetailFile = uiState.selectedFileForDetail!!
                FileDetailSheet(
                    file = currentDetailFile,
                    chunks = uiState.selectedFileChunks,
                    onDismiss = { viewModel.dismissFileDetail() },
                    onDownload = { viewModel.downloadFile(currentDetailFile.id) },
                    onShare = {
                        val localTarget = currentDetailFile.localUri ?: currentDetailFile.localPath
                        if (!localTarget.isNullOrBlank()) {
                            val shareUri: android.net.Uri? = if (localTarget.startsWith("content://")) {
                                android.net.Uri.parse(localTarget)
                            } else {
                                val localF = File(localTarget)
                                if (localF.exists()) {
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localF)
                                } else null
                            }
                            if (shareUri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = currentDetailFile.mimeType
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share file"))
                            } else {
                                Toast.makeText(context, "Please download the file first to share", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Please download the file first to share", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRename = {
                        viewModel.setFileToRename(currentDetailFile)
                    },
                    onMove = { viewModel.setItemToMove(currentDetailFile) },
                    onDelete = { viewModel.deleteFile(currentDetailFile.id) }
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
                    isWifiOnly = uiState.isWifiOnly,
                    isDarkTheme = isDarkTheme,
                    testTransferRunning = uiState.testTransferRunning,
                    testTransferStatus = uiState.testTransferStatus,
                    testTransferSuccess = uiState.testTransferSuccess,
                    botTokenPool = botPool,
                    botHealthMap = botHealth,
                    onAddBotToken = { viewModel.addBotToken(it) },
                    onRemoveBotToken = { viewModel.removeBotToken(it) },
                    onSetActiveBotToken = { viewModel.setActiveBotToken(it) },
                    onCheckBotHealth = { viewModel.checkBotHealth(it) },
                    onCheckAllBotsHealth = { viewModel.checkAllBotsHealth() },
                    onToggleTheme = { viewModel.toggleTheme() },
                    onChunkSizeChange = { viewModel.setChunkSizeMb(it) },
                    onWifiOnlyChange = { viewModel.setWifiOnly(it) },
                    onResyncClick = { viewModel.resyncFromTelegram() },
                    onDisconnect = { viewModel.disconnect() },
                    onDismiss = { viewModel.setShowSettingsSheet(false) },
                    onStartTestTransfer = { viewModel.startSyntheticTestTransfer() },
                    onDismissTestStatus = { viewModel.resetTestTransferStatus() }
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

            // Rename File Dialog
            if (uiState.fileToRename != null) {
                val file = uiState.fileToRename!!
                RenameFileDialog(
                    currentName = file.name,
                    onDismiss = { viewModel.setFileToRename(null) },
                    onConfirm = { newName -> viewModel.renameFile(file.id, newName) }
                )
            }

            // Move File Dialog
            if (uiState.itemToMove != null) {
                val file = uiState.itemToMove!!
                MoveFileDialog(
                    fileName = file.name,
                    folders = allFolders,
                    currentFolderId = file.folderId,
                    onDismiss = { viewModel.setItemToMove(null) },
                    onSelectDestination = { targetFolderId -> viewModel.moveFile(file.id, targetFolderId) }
                )
            }

            // Large File Confirmation Dialog
            if (uiState.pendingUploadWarning != null) {
                val warning = uiState.pendingUploadWarning!!
                LargeFileConfirmationDialog(
                    fileName = warning.fileName,
                    fileSize = warning.fileSize,
                    estimatedChunks = warning.estimatedChunks,
                    onConfirm = { viewModel.confirmUploadFile(warning.uri) },
                    onDismiss = { viewModel.dismissUploadWarning() }
                )
            }
        }
    }
}
