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
import androidx.compose.runtime.remember
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FolderEntity
import com.example.ui.viewmodel.SortBy
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
import com.example.ui.theme.LocalTeleVaultColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.TeleVaultViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TeleVaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unlockMaximumRefreshRate()
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
        unlockMaximumRefreshRate()
        viewModel.onAppForeground()
    }

    /**
     * Unlocks the maximum display refresh rate (e.g., 90Hz, 120Hz, 144Hz) supported
     * by the device hardware, enabling ultra-smooth fluid scrolling and animations.
     */
    private fun unlockMaximumRefreshRate() {
        try {
            val win = window ?: return

            // 1. Query display and find the highest available refresh rate mode
            val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display ?: (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay
            }

            val modes = currentDisplay?.supportedModes ?: emptyArray()
            val maxMode = modes.maxByOrNull { it.refreshRate }
            val highestRefreshRate = maxMode?.refreshRate ?: currentDisplay?.refreshRate ?: 60f

            val layoutParams = win.attributes
            var updated = false

            if (maxMode != null && maxMode.modeId > 0) {
                layoutParams.preferredDisplayModeId = maxMode.modeId
                updated = true
            }

            if (highestRefreshRate > 60f) {
                @Suppress("DEPRECATION")
                layoutParams.preferredRefreshRate = highestRefreshRate
                updated = true
            }

            if (updated) {
                win.attributes = layoutParams
            }

            // 2. Reduce post-processing latency if available (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                win.setPreferMinimalPostProcessing(true)
            }

            // 3. Request high frame rate category on the decor view if supported (Android 14+)
            if (Build.VERSION.SDK_INT >= 34) {
                win.decorView.post {
                    try {
                        val setCategoryMethod = win.decorView.javaClass.getMethod("setFrameRateCategory", Int::class.javaPrimitiveType)
                        val highCategory = try {
                            val surfaceClass = Class.forName("android.view.Surface")
                            surfaceClass.getField("FRAME_RATE_CATEGORY_HIGH").getInt(null)
                        } catch (_: Throwable) {
                            1 // FRAME_RATE_CATEGORY_HIGH constant value
                        }
                        setCategoryMethod.invoke(win.decorView, highCategory)
                    } catch (_: Throwable) {}

                    try {
                        if (highestRefreshRate > 60f) {
                            val setFrameRateMethod = win.decorView.javaClass.getMethod(
                                "setFrameRate",
                                Float::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType
                            )
                            setFrameRateMethod.invoke(win.decorView, highestRefreshRate, 0)
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {
            // Graceful fallback on devices that do not expose custom display modes
        }
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
    val activeTransfersCount by viewModel.activeTransfersCount.collectAsState()
    val standbyBots by viewModel.standbyBots.collectAsState()
    val recoveryState by viewModel.recoveryState.collectAsState()

    // File upload picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadFile(uri)
        }
    }

    // Memoized callbacks for HomeScreen to avoid unnecessary recompositions
    val onToggleTheme = remember(viewModel) { { viewModel.toggleTheme() } }
    val onSearchChange = remember(viewModel) { { query: String -> viewModel.setSearchQuery(query) } }
    val onSortChange = remember(viewModel) { { sort: SortBy -> viewModel.setSortBy(sort) } }
    val onToggleViewMode = remember(viewModel) { { viewModel.toggleViewMode() } }
    val onFolderClick = remember(viewModel) { { folder: FolderEntity -> viewModel.openFolder(folder) } }
    val onBreadcrumbClick = remember(viewModel) { { index: Int -> viewModel.navigateToBreadcrumb(index) } }
    val onFileClick = remember(viewModel) { { file: FileEntity -> viewModel.inspectFile(file) } }
    val onRenameFolder = remember(viewModel) { { folder: FolderEntity -> viewModel.setFolderToRename(folder) } }
    val onDeleteFolder = remember(viewModel) { { folder: FolderEntity -> viewModel.deleteFolder(folder) } }
    val onCreateFolderClick = remember(viewModel) { { viewModel.setShowCreateFolderDialog(true) } }
    val onUploadFileClick = remember(filePickerLauncher) { { filePickerLauncher.launch("*/*") } }
    val onOpenTransfers = remember(viewModel) { { viewModel.navigateToTransfersScreen() } }
    val onOpenSettings = remember(viewModel) { { viewModel.navigateToSettingsScreen() } }
    val onOpenFolderManagement = remember(viewModel) { { viewModel.navigateToFolderManagementScreen() } }
    val onResync = remember(viewModel) { { viewModel.manualSyncFromHeader() } }
    val onDismissResyncMsg = remember(viewModel) { { viewModel.clearResyncMessage() } }
    val onDismissTransferError = remember(viewModel) { { viewModel.dismissTransferError() } }

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
                    TransfersScreenContainer(viewModel = viewModel)
                } else if (currentScreen == AppScreen.SETTINGS) {
                    SettingsScreenContainer(
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme,
                        onDismiss = { viewModel.navigateToVaultScreen() }
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
                        activeTransfersCount = activeTransfersCount,
                        isResyncing = uiState.isResyncing,
                        resyncMessage = uiState.resyncMessage,
                        lastSyncedTime = uiState.lastSyncedTime,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        onSearchChange = onSearchChange,
                        onSortChange = onSortChange,
                        onToggleViewMode = onToggleViewMode,
                        onFolderClick = onFolderClick,
                        onBreadcrumbClick = onBreadcrumbClick,
                        onFileClick = onFileClick,
                        onRenameFolder = onRenameFolder,
                        onDeleteFolder = onDeleteFolder,
                        onCreateFolderClick = onCreateFolderClick,
                        onUploadFileClick = onUploadFileClick,
                        onOpenTransfers = onOpenTransfers,
                        onOpenSettings = onOpenSettings,
                        onOpenFolderManagement = onOpenFolderManagement,
                        onResync = onResync,
                        onDismissResyncMsg = onDismissResyncMsg,
                        transferErrorMessage = uiState.transferErrorMessage,
                        onDismissTransferError = onDismissTransferError,
                        botRevocationAlert = uiState.primaryBotAlert,
                        onDismissBotRevocationAlert = { viewModel.dismissBotRevocationAlert() },
                        onTriggerRecoveryFromAlert = { viewModel.triggerRecoveryFromAlert() },
                        hasStandbyBots = standbyBots.isNotEmpty()
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
                TransfersSheetContainer(viewModel = viewModel)
            }

            // Settings Sheet
            if (uiState.showSettingsSheet) {
                SettingsScreenContainer(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
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
                MoveFileDialogContainer(
                    viewModel = viewModel,
                    file = file
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

            // Standby Bot Recovery Confirmation Dialog (from alert or home screen)
            if (uiState.standbyBotToRecover != null) {
                val bot = uiState.standbyBotToRecover!!
                val colors = LocalTeleVaultColors.current
                AlertDialog(
                    onDismissRequest = { viewModel.setStandbyBotToRecover(null) },
                    containerColor = colors.surface,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.violet.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = colors.violet,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Switch Vault to ${bot.label}?",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "Your primary bot was revoked or restricted. This recovery flow safely migrates all file chunks to ${bot.label} using server-side copyMessage.",
                                fontSize = 13.sp,
                                color = colors.text,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceHi.copy(alpha = 0.6f))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "• 0 MB data: Telegram server-side copyMessage",
                                        fontSize = 11.sp,
                                        color = colors.textDim
                                    )
                                    Text(
                                        text = "• Generates fresh file_ids and binds chunks to ${bot.label}",
                                        fontSize = 11.sp,
                                        color = colors.textDim
                                    )
                                    Text(
                                        text = "• Sets ${bot.label} as active bot credentials",
                                        fontSize = 11.sp,
                                        color = colors.textDim
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetBot = bot
                                viewModel.setStandbyBotToRecover(null)
                                viewModel.startStandbyRecovery(targetBot)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.violet),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Start Recovery", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.setStandbyBotToRecover(null) }) {
                            Text("Cancel", color = colors.textDim)
                        }
                    }
                )
            }

            // Standby Recovery Global Progress Dialog
            if (recoveryState.isRecovering) {
                val colors = LocalTeleVaultColors.current
                AlertDialog(
                    onDismissRequest = { /* Non-dismissible while recovering */ },
                    containerColor = colors.surface,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.violet.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 3.dp,
                                color = colors.violet
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Recovering Vault Chunks...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = recoveryState.statusMessage,
                                fontSize = 13.sp,
                                color = colors.textDim
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { recoveryState.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = colors.violet,
                                trackColor = colors.surfaceHi
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${recoveryState.currentChunk} / ${recoveryState.totalChunks} chunks",
                                    fontSize = 11.sp,
                                    color = colors.textDim,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${(recoveryState.progressFraction * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = colors.violet,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.cancelStandbyRecovery() }
                        ) {
                            Text("Cancel", color = colors.danger)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TransfersScreenContainer(viewModel: TeleVaultViewModel) {
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val recentlyCompleted by viewModel.recentlyCompleted.collectAsState()
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
}

@Composable
private fun TransfersSheetContainer(viewModel: TeleVaultViewModel) {
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    TransfersSheet(
        transfers = activeTransfers,
        onDismiss = { viewModel.setShowTransfersSheet(false) },
        onPause = { viewModel.pauseTransfer(it) },
        onResume = { id, isUpload -> viewModel.resumeTransfer(id, isUpload) },
        onCancel = { viewModel.cancelTransfer(it) },
        onRetry = { viewModel.retryTransfer(it) }
    )
}

@Composable
private fun SettingsScreenContainer(
    viewModel: TeleVaultViewModel,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val botPool by viewModel.botPool.collectAsState()
    val botHealth by viewModel.botHealth.collectAsState()
    val standbyBots by viewModel.standbyBots.collectAsState()
    val verifiedStandbyBotsCount by viewModel.verifiedStandbyBotsCount.collectAsState()
    val recoveryState by viewModel.recoveryState.collectAsState()
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
        standbyBots = standbyBots,
        verifiedStandbyBotsCount = verifiedStandbyBotsCount,
        recoveryState = recoveryState,
        onAddStandbyBot = { tokenInput, labelInput, onResult ->
            viewModel.addStandbyBot(tokenInput, labelInput, onResult)
        },
        onVerifyStandbyBot = { bot, onResult ->
            viewModel.verifyStandbyBot(bot, onResult)
        },
        onVerifyAllStandbyBots = { viewModel.verifyAllStandbyBots() },
        onDeleteStandbyBot = { viewModel.deleteStandbyBot(it) },
        onStartStandbyRecovery = { viewModel.startStandbyRecovery(it) },
        onCancelStandbyRecovery = { viewModel.cancelStandbyRecovery() },
        onResetStandbyRecoveryState = { viewModel.resetStandbyRecoveryState() },
        onAddBotToken = { viewModel.addBotToken(it) },
        onRemoveBotToken = { viewModel.removeBotToken(it) },
        onSetActiveBotToken = { viewModel.setActiveBotToken(it) },
        onCheckBotHealth = { viewModel.checkBotHealth(it) },
        onCheckAllBotsHealth = { viewModel.checkAllBotsHealth() },
        onToggleTheme = { viewModel.toggleTheme() },
        onChunkSizeChange = { viewModel.setChunkSizeMb(it) },
        onWifiOnlyChange = { viewModel.setWifiOnly(it) },
        onResyncClick = { viewModel.resyncFromTelegram() },
        primaryBotAlert = uiState.primaryBotAlert,
        onDismissPrimaryBotAlert = { viewModel.dismissBotRevocationAlert() },
        botHealthLogs = remember(uiState.primaryBotAlert) { viewModel.getBotHealthHistoryLogs() },
        onCheckPrimaryBotHealth = { viewModel.checkPrimaryBotHealth() },
        onDisconnect = { viewModel.disconnect() },
        onDismiss = onDismiss,
        onStartTestTransfer = { viewModel.startSyntheticTestTransfer() },
        onDismissTestStatus = { viewModel.resetTestTransferStatus() }
    )
}

@Composable
private fun MoveFileDialogContainer(
    viewModel: TeleVaultViewModel,
    file: FileEntity
) {
    val allFolders by viewModel.allFolders.collectAsState()
    MoveFileDialog(
        fileName = file.name,
        folders = allFolders,
        currentFolderId = file.folderId,
        onDismiss = { viewModel.setItemToMove(null) },
        onSelectDestination = { targetFolderId -> viewModel.moveFile(file.id, targetFolderId) }
    )
}
