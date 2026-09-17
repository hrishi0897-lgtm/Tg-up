package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import com.example.domain.model.StorageCategory
import com.example.domain.model.classifyFileCategory
import com.example.ui.components.Fab3D
import com.example.ui.components.IconButton3D
import com.example.ui.components.SquareButton3D
import com.example.ui.components.StorageBentoGrid
import com.example.ui.components.TeleVaultBottomNav
import com.example.ui.components.UploadButton3D
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.MotionSpecs
import com.example.ui.theme.pressScale
import com.example.ui.viewmodel.AppScreen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.local.entity.FolderEntity
import com.example.domain.ChecksumUtil
import com.example.domain.model.BotRevocationAlert
import com.example.domain.model.BreadcrumbItem
import com.example.domain.model.StorageStats
import com.example.domain.model.TransferProgress
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.BrandMarkGradient
import com.example.ui.theme.DisplaySerifFont
import com.example.ui.theme.EmptyHeadlineStyle
import com.example.ui.theme.FileColorArchive
import com.example.ui.theme.FileColorAudio
import com.example.ui.theme.FileColorDoc
import com.example.ui.theme.FileColorFolder
import com.example.ui.theme.FileColorGeneric
import com.example.ui.theme.FileColorImage
import com.example.ui.theme.FileColorVideo
import com.example.ui.theme.MonoStatValueLarge
import com.example.ui.theme.MonoStatValueMedium
import com.example.ui.theme.NumericMonoFont
import com.example.ui.theme.TealFabGradient
import com.example.ui.theme.VioletButtonGradient
import com.example.ui.theme.WordmarkTextStyle
import com.example.ui.viewmodel.SortBy

@Composable
fun HomeScreen(
    storageStats: StorageStats,
    folders: List<FolderEntity>,
    files: List<FileEntity>,
    breadcrumbs: List<BreadcrumbItem>,
    searchQuery: String,
    sortBy: SortBy,
    sortAscending: Boolean,
    isGridView: Boolean,
    activeTransfers: List<TransferProgress> = emptyList(),
    activeTransfersCount: Int = activeTransfers.count {
        it.status == FileStatus.UPLOADING || it.status == FileStatus.DOWNLOADING
    },
    isResyncing: Boolean,
    resyncMessage: String?,
    lastSyncedTime: Long = 0L,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortBy) -> Unit,
    onToggleViewMode: () -> Unit,
    onFolderClick: (FolderEntity) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onFileClick: (FileEntity) -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onCreateFolderClick: () -> Unit,
    onUploadFileClick: () -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenSettings: () -> Unit,
    onResync: () -> Unit,
    onDismissResyncMsg: () -> Unit,
    onOpenFolderManagement: () -> Unit = {},
    transferErrorMessage: String? = null,
    onDismissTransferError: () -> Unit = {},
    botRevocationAlert: BotRevocationAlert? = null,
    onDismissBotRevocationAlert: () -> Unit = {},
    onTriggerRecoveryFromAlert: () -> Unit = {},
    hasStandbyBots: Boolean = false,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    selectedFileIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onToggleFileSelection: (String) -> Unit = {},
    onSelectAllFiles: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onBulkDownload: () -> Unit = {},
    onBulkMove: () -> Unit = {},
    onBulkDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    var showFabMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<StorageCategory?>(null) }

    val activeCount = activeTransfersCount
    val displayedFiles = remember(files, selectedCategory) {
        if (selectedCategory == null) files
        else files.filter { classifyFileCategory(it.mimeType, it.name) == selectedCategory }
    }
    val filePairs = remember(displayedFiles) { displayedFiles.chunked(2) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bodyBg),
        containerColor = colors.bg,
        topBar = {
            if (isSelectionMode) {
                BulkSelectionTopBar(
                    selectedCount = selectedFileIds.size,
                    totalCount = displayedFiles.size,
                    allSelected = displayedFiles.isNotEmpty() && selectedFileIds.size == displayedFiles.size,
                    onSelectAll = onSelectAllFiles,
                    onClearSelection = onClearSelection
                )
            } else {
                HomeTopBar(
                    isResyncing = isResyncing,
                    lastSyncedTime = lastSyncedTime,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onOpenSettings = onOpenSettings,
                    onResync = onResync
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Expanded FAB menu items
                    AnimatedVisibility(
                    visible = showFabMenu,
                    enter = fadeIn(animationSpec = tween(150)) + expandVertically(
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f)
                    ),
                    exit = fadeOut(animationSpec = tween(100)) + shrinkVertically(animationSpec = tween(150))
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // New Folder Action
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceHi)
                                    .clickable {
                                        showFabMenu = false
                                        onCreateFolderClick()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "New Folder",
                                    color = colors.text,
                                    fontSize = 13.sp,
                                    fontFamily = BodySansFont,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Upload File Action
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceHi)
                                    .clickable {
                                        showFabMenu = false
                                        onUploadFileClick()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Upload File",
                                    color = colors.text,
                                    fontSize = 13.sp,
                                    fontFamily = BodySansFont,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Primary 3D Teal FAB with rotation on open
                val fabRotation by animateFloatAsState(
                    targetValue = if (showFabMenu) 45f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "fab_rotation"
                )

                Fab3D(
                    onClick = { showFabMenu = !showFabMenu },
                    modifier = Modifier.testTag("fab_add")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add options",
                        tint = Color(0xFF05060A),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = fabRotation }
                    )
                }
            }
        }
    },
        bottomBar = {
            if (isSelectionMode) {
                BulkActionsBottomBar(
                    selectedCount = selectedFileIds.size,
                    onDownload = onBulkDownload,
                    onMove = onBulkMove,
                    onDelete = onBulkDelete
                )
            } else {
                TeleVaultBottomNav(
                    currentScreen = AppScreen.VAULT,
                    activeTransferCount = activeCount,
                    onVaultSelected = { /* Already in Vault */ },
                    onTransfersSelected = onOpenTransfers
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 0. Bot Revocation Alert Banner (High priority, hard-to-miss alert)
            if (botRevocationAlert != null) {
                item(key = "bot_revocation_alert") {
                    BotRevocationAlertBanner(
                        alert = botRevocationAlert,
                        hasStandbyBots = hasStandbyBots,
                        onTriggerRecovery = onTriggerRecoveryFromAlert,
                        onOpenSettings = onOpenSettings,
                        onDismiss = onDismissBotRevocationAlert,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            // 1. Status Badge: "Backend connected" or "Bot token revoked" (Static indicator, zero animation idle overhead)
            item(key = "status_badge") {
                if (botRevocationAlert != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .background(colors.danger.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
                            .border(1.dp, colors.danger.copy(alpha = 0.45f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(colors.danger, CircleShape)
                        )
                        Text(
                            text = "Bot token revoked / invalid",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodySansFont,
                            color = colors.danger
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .background(colors.mint.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
                            .border(1.dp, colors.mint.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(colors.mint, CircleShape)
                        )
                        Text(
                            text = "Backend connected",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = BodySansFont,
                            color = colors.mint
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Transfer error banner if present
            if (transferErrorMessage != null) {
                item(key = "transfer_error_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.danger.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Transfer Error",
                            tint = colors.danger,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Transfer Failed",
                                color = colors.danger,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodySansFont
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = transferErrorMessage,
                                color = colors.text,
                                fontSize = 12.sp,
                                fontFamily = BodySansFont,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onOpenTransfers,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "View",
                                color = colors.violet,
                                fontSize = 12.sp,
                                fontFamily = BodySansFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismissTransferError, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = colors.textDim, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Resync notification banner if present
            if (resyncMessage != null) {
                item(key = "resync_banner") {
                    val isError = resyncMessage.contains("error", ignoreCase = true) ||
                            resyncMessage.contains("failed", ignoreCase = true) ||
                            resyncMessage.contains("No Vault Index", ignoreCase = true)

                    val bannerBg = if (isError) colors.danger.copy(alpha = 0.15f) else colors.violet.copy(alpha = 0.12f)
                    val bannerBorder = if (isError) colors.danger.copy(alpha = 0.4f) else colors.violet.copy(alpha = 0.35f)
                    val bannerTint = if (isError) colors.danger else colors.violet
                    val bannerIcon = if (isError) Icons.Default.Warning else Icons.Default.CloudDone

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bannerBg, RoundedCornerShape(12.dp))
                            .border(1.dp, bannerBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .testTag("sync_banner"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = bannerIcon,
                            contentDescription = if (isError) "Sync Error" else "Sync Status",
                            tint = bannerTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = resyncMessage,
                            color = colors.text,
                            fontSize = 12.sp,
                            fontFamily = BodySansFont,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissResyncMsg,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("dismiss_sync_banner")
                        ) {
                            Text("✕", color = colors.textDim, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // 2. Hero: Bento-Grid Storage Breakdown & Consolidated Stats
            item(key = "hero_bento_grid") {
                StorageBentoGrid(
                    stats = storageStats,
                    selectedCategory = selectedCategory,
                    onCategoryClick = { clickedCat ->
                        selectedCategory = if (selectedCategory == clickedCat) null else clickedCat
                    },
                    activeTransfersCount = activeCount,
                    onTransfersClick = onOpenTransfers
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 3. Search & Filter Row: Search Box + 3D Sort Button + 3D View Toggle Button
            item(key = "search_and_filter_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search Box
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.searchBg)
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.textFaint,
                            modifier = Modifier.size(16.dp)
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            textStyle = TextStyle(
                                fontFamily = BodySansFont,
                                fontSize = 14.sp,
                                color = colors.text
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.violet),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search the vault",
                                        fontFamily = BodySansFont,
                                        fontSize = 14.sp,
                                        color = colors.textFaint
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_vault_input")
                        )
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "✕",
                                color = colors.textDim,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable { onSearchChange("") }
                                    .padding(4.dp)
                            )
                        }
                    }

                    // Sort 3D Button
                    Box {
                        SquareButton3D(
                            onClick = { showSortMenu = true }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                                tint = colors.textDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier
                                .background(colors.surfaceHi)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name", color = colors.text, fontSize = 13.sp, fontFamily = BodySansFont) },
                                onClick = {
                                    onSortChange(SortBy.NAME)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date Uploaded", color = colors.text, fontSize = 13.sp, fontFamily = BodySansFont) },
                                onClick = {
                                    onSortChange(SortBy.DATE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("File Size", color = colors.text, fontSize = 13.sp, fontFamily = BodySansFont) },
                                onClick = {
                                    onSortChange(SortBy.SIZE)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    // View Toggle 3D Button
                    val viewToggleRot by animateFloatAsState(
                        targetValue = if (isGridView) 180f else 0f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
                        label = "view_toggle_rot"
                    )
                    SquareButton3D(
                        onClick = onToggleViewMode
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle view",
                            tint = colors.textDim,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = viewToggleRot }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 4. Breadcrumbs (if inside a subfolder)
            if (breadcrumbs.size > 1) {
                item(key = "breadcrumbs_bar") {
                    BreadcrumbBar(
                        breadcrumbs = breadcrumbs,
                        onBreadcrumbClick = onBreadcrumbClick
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // 5. Folders Section (if any and not searching)
            if (folders.isNotEmpty() && searchQuery.isBlank()) {
                item(key = "folders_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Folders",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodySansFont,
                            color = colors.textDim
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenFolderManagement)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("btn_manage_folders")
                        ) {
                            Text(
                                text = "Manage Folders",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = BodySansFont,
                                color = colors.teal
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                items(folders, key = { it.id }, contentType = { "folder_row" }) { folder ->
                    FolderItemRow(
                        folder = folder,
                        onClick = { onFolderClick(folder) },
                        onRename = { onRenameFolder(folder) },
                        onDelete = { onDeleteFolder(folder) }
                    )
                }

                item(key = "folders_spacer") {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // 6. Section Head: "Vault files" + Item Count in Numeric Monospace Font
            item(key = "vault_files_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "Search results"
                                selectedCategory == StorageCategory.DOCUMENTS -> "Documents"
                                selectedCategory == StorageCategory.MEDIA -> "Media"
                                selectedCategory == StorageCategory.OTHER -> "Other files"
                                else -> "Vault files"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodySansFont,
                            color = colors.text
                        )

                        if (selectedCategory != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(colors.surfaceHi, RoundedCornerShape(100.dp))
                                    .clickable { selectedCategory = null }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Filtered • Clear ✕",
                                    fontSize = 11.sp,
                                    fontFamily = BodySansFont,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textDim
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (displayedFiles.isNotEmpty()) {
                            Text(
                                text = if (isSelectionMode) "Done" else "Select",
                                fontSize = 12.5.sp,
                                fontFamily = BodySansFont,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.teal,
                                modifier = Modifier
                                    .clickable {
                                        if (isSelectionMode) {
                                            onClearSelection()
                                        } else {
                                            displayedFiles.firstOrNull()?.let { onToggleFileSelection(it.id) }
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "${displayedFiles.size} items",
                            fontSize = 12.5.sp,
                            fontFamily = NumericMonoFont,
                            color = colors.textFaint
                        )
                    }
                }
            }

            // 7. Content: Empty State or File List/Grid
            if (displayedFiles.isEmpty() && folders.isEmpty()) {
                item(key = "empty_state") {
                    if (selectedCategory != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "No ${selectedCategory?.name?.lowercase()} in this folder",
                                fontSize = 14.sp,
                                fontFamily = BodySansFont,
                                color = colors.textDim
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(colors.surface, RoundedCornerShape(10.dp))
                                    .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                                    .clickable { selectedCategory = null }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Show All Files",
                                    fontSize = 12.5.sp,
                                    fontFamily = BodySansFont,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.teal
                                )
                            }
                        }
                    } else {
                        EmptyFolderState(
                            isSearch = searchQuery.isNotBlank(),
                            onUploadClick = onUploadFileClick
                        )
                    }
                }
            } else if (isGridView) {
                items(filePairs, key = { pair -> "grid_pair_${pair.first().id}" }, contentType = { "grid_pair" }) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val file1 = pair[0]
                        val isSelected1 = selectedFileIds.contains(file1.id)
                        Box(modifier = Modifier.weight(1f)) {
                            FileGridCard(
                                file = file1,
                                isSelected = isSelected1,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        onToggleFileSelection(file1.id)
                                    } else {
                                        onFileClick(file1)
                                    }
                                },
                                onLongClick = {
                                    onToggleFileSelection(file1.id)
                                }
                            )
                        }
                        if (pair.size > 1) {
                            val file2 = pair[1]
                            val isSelected2 = selectedFileIds.contains(file2.id)
                            Box(modifier = Modifier.weight(1f)) {
                                FileGridCard(
                                    file = file2,
                                    isSelected = isSelected2,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleFileSelection(file2.id)
                                        } else {
                                            onFileClick(file2)
                                        }
                                    },
                                    onLongClick = {
                                        onToggleFileSelection(file2.id)
                                    }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(displayedFiles, key = { it.id }, contentType = { "file_item" }) { file ->
                    val isSelected = selectedFileIds.contains(file.id)
                    FileListItem(
                        file = file,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleFileSelection(file.id)
                            } else {
                                onFileClick(file)
                            }
                        },
                        onLongClick = {
                            onToggleFileSelection(file.id)
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// Top Bar with Brand, Wordmark, and Header Actions
// ==========================================

@Composable
private fun HomeTopBar(
    isResyncing: Boolean,
    lastSyncedTime: Long = 0L,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onResync: () -> Unit
) {
    val colors = LocalTeleVaultColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Brand: 44dp Icon Mark + Serif Wordmark + Tagline
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Gradient app icon mark (14dp corner radius, 44x44dp, shadow glow)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = colors.violet.copy(alpha = 0.35f),
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                            topLeft = Offset(0f, 4.dp.toPx()),
                            size = Size(size.width, size.height)
                        )
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandMarkGradient),
                contentAlignment = Alignment.Center
            ) {
                // Geometric vault icon
                val outerPath = remember { Path() }
                Canvas(modifier = Modifier.size(22.dp)) {
                    val w = size.width
                    val h = size.height
                    outerPath.reset()
                    outerPath.moveTo(w * 0.5f, h * 0.125f)
                    outerPath.lineTo(w * 0.833f, h * 0.333f)
                    outerPath.lineTo(w * 0.833f, h * 0.667f)
                    outerPath.lineTo(w * 0.5f, h * 0.875f)
                    outerPath.lineTo(w * 0.167f, h * 0.667f)
                    outerPath.lineTo(w * 0.167f, h * 0.333f)
                    outerPath.close()
                    drawPath(
                        path = outerPath,
                        color = Color.White,
                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(w * 0.5f, h * 0.125f),
                        end = Offset(w * 0.5f, h * 0.875f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(w * 0.167f, h * 0.333f),
                        end = Offset(w * 0.833f, h * 0.667f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(w * 0.833f, h * 0.333f),
                        end = Offset(w * 0.167f, h * 0.667f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Column {
                Text(
                    text = "TeleVault",
                    style = WordmarkTextStyle.copy(color = colors.text)
                )
                Spacer(modifier = Modifier.height(2.dp))
                SyncedStatusText(
                    isResyncing = isResyncing,
                    lastSyncedTime = lastSyncedTime
                )
            }
        }

        // Header Action Buttons with 3D pressed edge shadow (38dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Theme toggle 3D icon button (sun when dark, moon when light)
            IconButton3D(
                onClick = onToggleTheme,
                modifier = Modifier.testTag("theme_toggle_btn")
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme",
                    tint = colors.textDim,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Resync 3D icon button (animation instantiated strictly while isResyncing == true)
            ResyncButton(
                isResyncing = isResyncing,
                onResync = onResync
            )

            // Settings 3D icon button
            IconButton3D(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = colors.textDim,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/**
 * Isolated sync status text with 60-second polling cadence to prevent recomposing
 * sibling header elements (wordmark, canvas, action buttons).
 */
@Composable
private fun SyncedStatusText(
    isResyncing: Boolean,
    lastSyncedTime: Long,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current

    // Ticks every 60 seconds to keep relative time text fresh without high-frequency recomposition churn
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastSyncedTime) {
        while (true) {
            delay(60_000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val statusText = remember(isResyncing, lastSyncedTime, currentTime) {
        when {
            isResyncing -> "Syncing vault..."
            lastSyncedTime > 0 -> {
                val diff = currentTime - lastSyncedTime
                when {
                    diff < 60_000L -> "Synced just now"
                    diff < 3_600_000L -> "Synced ${diff / 60_000L}m ago"
                    diff < 86_400_000L -> "Synced ${diff / 3_600_000L}h ago"
                    else -> "Synced on ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(lastSyncedTime))}"
                }
            }
            else -> "Telegram cloud storage"
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        val dotColor = when {
            isResyncing -> colors.teal
            lastSyncedTime > 0 -> colors.mint
            else -> colors.textFaint
        }
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = statusText,
            fontSize = 11.sp,
            fontFamily = BodySansFont,
            color = if (isResyncing) colors.teal else colors.textFaint
        )
    }
}

/**
 * Isolated resync button. Only enters active animation composition when isResyncing == true,
 * ensuring zero frame clock scheduling or animation callbacks when idle.
 */
@Composable
private fun ResyncButton(
    isResyncing: Boolean,
    onResync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    IconButton3D(
        onClick = onResync,
        enabled = !isResyncing,
        modifier = modifier.testTag("resync_btn")
    ) {
        if (isResyncing) {
            ResyncSpinningIcon(tint = colors.violet)
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Sync",
                tint = colors.textDim,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun ResyncSpinningIcon(tint: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "resync_infinite")
    val spinAngleState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "resync_angle"
    )
    Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = "Syncing",
        tint = tint,
        modifier = Modifier
            .size(17.dp)
            .graphicsLayer { rotationZ = spinAngleState.value }
    )
}

// ==========================================



// ==========================================
// Empty State: Outlined Vault Canvas + Serif Headline + 3D Violet Button
// ==========================================

@Composable
private fun EmptyFolderState(
    isSearch: Boolean,
    onUploadClick: () -> Unit
) {
    val colors = LocalTeleVaultColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Outlined vault icon illustration
        val handlePath = remember { Path() }
        Canvas(modifier = Modifier.size(72.dp)) {
            val scale = size.width / 72f
            val strokeWidth = 2.dp.toPx()
            val baseColor = colors.line
            val accentColor = colors.violet

            // Body rectangle: x=14, y=10, width=44, height=52, rx=10
            drawRoundRect(
                color = baseColor,
                topLeft = Offset(14f * scale, 10f * scale),
                size = Size(44f * scale, 52f * scale),
                cornerRadius = CornerRadius(10f * scale, 10f * scale),
                style = Stroke(width = strokeWidth)
            )

            // Center dial circle: cx=36, cy=34, r=9
            drawCircle(
                color = accentColor,
                radius = 9f * scale,
                center = Offset(36f * scale, 34f * scale),
                style = Stroke(width = strokeWidth)
            )

            // Keyhole vertical slit: from (36, 40) to (36, 48)
            drawLine(
                color = accentColor,
                start = Offset(36f * scale, 40f * scale),
                end = Offset(36f * scale, 48f * scale),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Handle arch at top
            handlePath.reset()
            handlePath.moveTo(22f * scale, 10f * scale)
            handlePath.lineTo(22f * scale, 6f * scale)
            handlePath.quadraticTo(22f * scale, 2f * scale, 26f * scale, 2f * scale)
            handlePath.lineTo(46f * scale, 2f * scale)
            handlePath.quadraticTo(50f * scale, 2f * scale, 50f * scale, 6f * scale)
            handlePath.lineTo(50f * scale, 10f * scale)
            drawPath(
                path = handlePath,
                color = baseColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Serif display headline
        Text(
            text = if (isSearch) "No matching files" else "Nothing archived yet",
            style = EmptyHeadlineStyle.copy(color = colors.text),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Dimmed body copy
        Text(
            text = if (isSearch)
                "Try searching for a different keyword or file name in the vault."
            else
                "Upload a file of any size — TeleVault splits it into encrypted chunks and stores them across Telegram, with no limit on how much you keep.",
            fontSize = 13.5.sp,
            lineHeight = 21.sp,
            fontFamily = BodySansFont,
            color = colors.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 270.dp)
        )

        if (!isSearch) {
            Spacer(modifier = Modifier.height(22.dp))

            // 3D Violet Upload Button
            UploadButton3D(
                onClick = onUploadClick,
                modifier = Modifier.testTag("empty_upload_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Upload a file",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BodySansFont,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ==========================================
// Bottom Navigation with Underline Indicator & Semi-Transparent Background
// ==========================================

@Composable
private fun VaultBottomNav(
    activeTransferCount: Int,
    onTransfersSelected: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    Surface(
        color = colors.navBg,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .border(
                width = 1.dp,
                color = colors.line,
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vault Nav Item (Active)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { /* Already in vault */ }
                    .padding(vertical = 6.dp)
                    .testTag("tab_nav_vault"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Vault",
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Vault",
                    fontSize = 11.5.sp,
                    fontFamily = BodySansFont,
                    fontWeight = FontWeight.Medium,
                    color = colors.text
                )
                // Active Underline Indicator in Violet
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.violet)
                )
            }

            // Transfers Nav Item (Inactive or Clickable)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onTransfersSelected)
                    .padding(vertical = 6.dp)
                    .testTag("tab_nav_transfers"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (activeTransferCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = colors.violet,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = activeTransferCount.toString(),
                                    fontSize = 10.sp,
                                    fontFamily = NumericMonoFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Transfers",
                            tint = colors.textFaint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Transfers",
                        tint = colors.textFaint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Transfers",
                    fontSize = 11.5.sp,
                    fontFamily = BodySansFont,
                    fontWeight = FontWeight.Normal,
                    color = colors.textFaint
                )
                Spacer(modifier = Modifier.height(2.5.dp))
            }
        }
    }
}

// ==========================================
// Folder and File Components with New Card Surface & Typography
// ==========================================

@Composable
private fun BreadcrumbBar(
    breadcrumbs: List<BreadcrumbItem>,
    onBreadcrumbClick: (Int) -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        breadcrumbs.forEachIndexed { index, item ->
            val isLast = index == breadcrumbs.lastIndex
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontFamily = BodySansFont,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isLast) colors.text else colors.textDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBreadcrumbClick(index) }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
            )
            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = colors.textFaint,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

private val StaticItemCornerShape = RoundedCornerShape(14.dp)
private val StaticIconCornerShape = RoundedCornerShape(10.dp)

@Immutable
private data class FileIconTheme(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val border: Color
)

private val iconThemeCache = java.util.concurrent.ConcurrentHashMap<String, FileIconTheme>()

private fun resolveFileIconTheme(mimeType: String): FileIconTheme {
    return iconThemeCache.getOrPut(mimeType) {
        val (icon, tint) = when {
            mimeType.startsWith("image/") -> Pair(Icons.Default.Image, FileColorImage)
            mimeType.startsWith("video/") -> Pair(Icons.Default.Movie, FileColorVideo)
            mimeType.startsWith("audio/") -> Pair(Icons.Default.AudioFile, FileColorAudio)
            mimeType.contains("pdf") || mimeType.contains("document") || mimeType.contains("text") ->
                Pair(Icons.Default.Description, FileColorDoc)
            mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("rar") ->
                Pair(Icons.Default.FolderZip, FileColorArchive)
            else -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, FileColorGeneric)
        }
        FileIconTheme(
            icon = icon,
            tint = tint,
            bg = tint.copy(alpha = 0.14f),
            border = tint.copy(alpha = 0.28f)
        )
    }
}

@Composable
private fun FolderItemRow(
    folder: FolderEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    var showMenu by remember { mutableStateOf(false) }
    val formattedDate = remember(folder.createdDate) { ChecksumUtil.formatDate(folder.createdDate) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, StaticItemCornerShape)
            .border(1.dp, colors.line, StaticItemCornerShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(colors.surfaceHi, StaticIconCornerShape)
                .border(1.dp, colors.line, StaticIconCornerShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = FileColorFolder,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BodySansFont,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Folder · $formattedDate",
                fontSize = 11.sp,
                fontFamily = NumericMonoFont,
                color = colors.textFaint
            )
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Folder Options",
                    tint = colors.textDim,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(colors.surfaceHi, RoundedCornerShape(12.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Rename Folder", color = colors.text, fontSize = 13.sp, fontFamily = BodySansFont) },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = colors.danger, fontSize = 13.sp, fontFamily = BodySansFont, fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: FileEntity,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val colors = LocalTeleVaultColors.current
    val formattedSize = remember(file.size) { ChecksumUtil.formatFileSize(file.size) }
    val formattedDate = remember(file.uploadDate) { ChecksumUtil.formatDate(file.uploadDate) }

    val borderColor = if (isSelected) colors.teal else colors.line
    val backgroundColor = if (isSelected) colors.teal.copy(alpha = 0.08f) else colors.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, StaticItemCornerShape)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, StaticItemCornerShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) colors.teal else colors.textDim,
                modifier = Modifier
                    .size(22.dp)
                    .testTag("checkbox_${file.id}")
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        FileIcon(mimeType = file.mimeType, size = 22.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BodySansFont,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedSize,
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    color = colors.textDim
                )
                Text(
                    text = " · ",
                    fontSize = 11.sp,
                    color = colors.textFaint
                )
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    color = colors.textFaint
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))
        FileStatusIndicator(status = file.status)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCard(
    file: FileEntity,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val colors = LocalTeleVaultColors.current
    val formattedSize = remember(file.size) { ChecksumUtil.formatFileSize(file.size) }

    val borderColor = if (isSelected) colors.teal else colors.line
    val backgroundColor = if (isSelected) colors.teal.copy(alpha = 0.08f) else colors.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(backgroundColor, StaticItemCornerShape)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, StaticItemCornerShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FileIcon(mimeType = file.mimeType, size = 22.dp)
                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) colors.teal else colors.textDim,
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("grid_checkbox_${file.id}")
                    )
                } else {
                    FileStatusIndicator(status = file.status)
                }
            }

            Column {
                Text(
                    text = file.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BodySansFont,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = formattedSize,
                    fontSize = 10.5.sp,
                    fontFamily = NumericMonoFont,
                    color = colors.textDim
                )
            }
        }
    }
}

@Composable
fun FileIcon(mimeType: String, size: androidx.compose.ui.unit.Dp) {
    val theme = remember(mimeType) { resolveFileIconTheme(mimeType) }

    Box(
        modifier = Modifier
            .size(size + 14.dp)
            .background(theme.bg, StaticIconCornerShape)
            .border(1.dp, theme.border, StaticIconCornerShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = theme.icon,
            contentDescription = null,
            tint = theme.tint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
private fun FileStatusIndicator(status: FileStatus) {
    val colors = LocalTeleVaultColors.current
    when (status) {
        FileStatus.COMPLETED -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Stored",
                tint = colors.mint,
                modifier = Modifier.size(16.dp)
            )
        }
        FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Transferring",
                tint = colors.violet,
                modifier = Modifier.size(16.dp)
            )
        }
        FileStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Failed",
                tint = colors.danger,
                modifier = Modifier.size(16.dp)
            )
        }
        FileStatus.PAUSED -> {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Paused",
                tint = colors.amber,
                modifier = Modifier.size(16.dp)
            )
        }
        FileStatus.PENDING -> {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Pending",
                tint = colors.textFaint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun BotRevocationAlertBanner(
    alert: BotRevocationAlert,
    hasStandbyBots: Boolean,
    onTriggerRecovery: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    val reduceMotion = LocalReduceMotion.current
    val infiniteTransition = rememberInfiniteTransition(label = "alert_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_pulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = colors.danger.copy(alpha = if (reduceMotion) 0.85f else borderAlpha),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("bot_revocation_alert_banner"),
        color = colors.danger.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.danger.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Bot Token Alert",
                        tint = colors.danger,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BOT TOKEN INVALID OR REVOKED",
                        color = colors.danger,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodySansFont,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "HTTP ${alert.errorCode ?: "401/403"} • Checked at ${alert.formattedTime}",
                        color = colors.textDim,
                        fontSize = 11.5.sp,
                        fontFamily = NumericMonoFont
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("dismiss_bot_alert_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Alert",
                        tint = colors.textDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Exact Alert Message requested by user
            Text(
                text = "Your bot token appears to be invalid or revoked — switch to a standby bot in Settings",
                color = colors.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BodySansFont,
                lineHeight = 19.sp
            )

            if (alert.errorMessage.isNotBlank() && !alert.errorMessage.contains("Your bot token appears")) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Details: ${alert.errorMessage}",
                    color = colors.textDim,
                    fontSize = 12.sp,
                    fontFamily = BodySansFont,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasStandbyBots) {
                    Button(
                        onClick = onTriggerRecovery,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.violet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("alert_switch_to_standby_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Switch to Standby Bot",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                        modifier = Modifier.testTag("alert_settings_button")
                    ) {
                        Text(
                            text = "Settings",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.danger,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alert_open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configure Standby Bot in Settings",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Bulk Selection Top Bar
// ==========================================
@Composable
fun BulkSelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit
) {
    val colors = LocalTeleVaultColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = colors.surfaceHi,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("bulk_cancel_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Selection",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = if (selectedCount == 0) "Select items" else "$selectedCount selected",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodySansFont,
                        color = colors.text
                    )
                    Text(
                        text = "$totalCount total available",
                        fontSize = 11.5.sp,
                        fontFamily = NumericMonoFont,
                        color = colors.textDim
                    )
                }
            }

            TextButton(
                onClick = onSelectAll,
                modifier = Modifier.testTag("bulk_select_all_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    fontSize = 13.sp,
                    fontFamily = BodySansFont,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.teal
                )
            }
        }
    }
}

// ==========================================
// Bulk Actions Bottom Bar
// ==========================================
@Composable
fun BulkActionsBottomBar(
    selectedCount: Int,
    onDownload: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val hasSelection = selectedCount > 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = colors.surfaceHi,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Download Action
            BulkActionButton(
                icon = Icons.Default.Download,
                label = "Download",
                tint = if (hasSelection) colors.teal else colors.textDim.copy(alpha = 0.5f),
                enabled = hasSelection,
                onClick = onDownload,
                testTag = "bulk_download_button"
            )

            // Move Action
            BulkActionButton(
                icon = Icons.Default.DriveFileMove,
                label = "Move",
                tint = if (hasSelection) colors.violet else colors.textDim.copy(alpha = 0.5f),
                enabled = hasSelection,
                onClick = onMove,
                testTag = "bulk_move_button"
            )

            // Delete Action
            BulkActionButton(
                icon = Icons.Default.Delete,
                label = "Delete",
                tint = if (hasSelection) colors.danger else colors.textDim.copy(alpha = 0.5f),
                enabled = hasSelection,
                onClick = onDelete,
                testTag = "bulk_delete_button"
            )
        }
    }
}

@Composable
private fun BulkActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = LocalTeleVaultColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = BodySansFont,
            fontWeight = FontWeight.Medium,
            color = if (enabled) colors.text else colors.textDim.copy(alpha = 0.5f)
        )
    }
}

