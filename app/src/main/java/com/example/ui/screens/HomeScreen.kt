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
import com.example.ui.components.Fab3D
import com.example.ui.components.IconButton3D
import com.example.ui.components.QuickStatsBar
import com.example.ui.components.SquareButton3D
import com.example.ui.components.StorageDonutRingCard
import com.example.ui.components.TeleVaultBottomNav
import com.example.ui.components.UploadButton3D
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.MotionSpecs
import com.example.ui.theme.pressScale
import com.example.ui.viewmodel.AppScreen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    var showFabMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val activeCount = activeTransfersCount

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bodyBg),
        containerColor = colors.bg,
        topBar = {
            HomeTopBar(
                isResyncing = isResyncing,
                lastSyncedTime = lastSyncedTime,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onOpenSettings = onOpenSettings,
                onResync = onResync
            )
        },
        floatingActionButton = {
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
        },
        bottomBar = {
            TeleVaultBottomNav(
                currentScreen = AppScreen.VAULT,
                activeTransferCount = activeCount,
                onVaultSelected = { /* Already in Vault */ },
                onTransfersSelected = onOpenTransfers
            )
        }
    ) { innerPadding ->
        val filePairs = remember(files) { files.chunked(2) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 96.dp)
        ) {
            // 1. Status Badge: "Backend connected" with pulsing mint dot
            item(key = "status_badge") {
                val reduceMotion = LocalReduceMotion.current
                val infinitePulse = rememberInfiniteTransition(label = "badge_pulse")
                val pulseAlphaState = infinitePulse.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_dot_alpha"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(colors.mint.copy(alpha = 0.12f))
                        .border(1.dp, colors.mint.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .graphicsLayer { this.alpha = if (reduceMotion) 1f else pulseAlphaState.value }
                            .background(colors.mint)
                    )
                    Text(
                        text = "Backend connected",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = BodySansFont,
                        color = colors.mint
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Transfer error banner if present
            if (transferErrorMessage != null) {
                item(key = "transfer_error_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.danger.copy(alpha = 0.15f))
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
                    Spacer(modifier = Modifier.height(12.dp))
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(bannerBg)
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
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 2. Hero: 3-segment Storage Ring Donut Card + Quick Stats Bar
            item(key = "hero_storage_card") {
                StorageDonutRingCard(stats = storageStats)
                Spacer(modifier = Modifier.height(12.dp))
                QuickStatsBar(
                    filesCount = storageStats.fileCount,
                    foldersCount = storageStats.folderCount,
                    activeTransfersCount = activeCount,
                    onTransfersClick = onOpenTransfers
                )
                Spacer(modifier = Modifier.height(16.dp))
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

                items(folders, key = { it.id }) { folder ->
                    FolderItemRow(
                        folder = folder,
                        onClick = { onFolderClick(folder) },
                        onRename = { onRenameFolder(folder) },
                        onDelete = { onDeleteFolder(folder) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item(key = "folders_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 6. Section Head: "Vault files" + Item Count in Numeric Monospace Font
            item(key = "vault_files_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "Search results" else "Vault files",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BodySansFont,
                        color = colors.text
                    )
                    Text(
                        text = "${files.size} items",
                        fontSize = 12.5.sp,
                        fontFamily = NumericMonoFont,
                        color = colors.textFaint
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 7. Content: Empty State or File List/Grid
            if (files.isEmpty() && folders.isEmpty()) {
                item(key = "empty_state") {
                    EmptyFolderState(
                        isSearch = searchQuery.isNotBlank(),
                        onUploadClick = onUploadFileClick
                    )
                }
            } else if (isGridView) {
                items(filePairs, key = { pair -> "grid_pair_${pair.first().id}" }) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            FileGridCard(file = pair[0], onClick = { onFileClick(pair[0]) })
                        }
                        if (pair.size > 1) {
                            Box(modifier = Modifier.weight(1f)) {
                                FileGridCard(file = pair[1], onClick = { onFileClick(pair[1]) })
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else {
                items(files, key = { it.id }) { file ->
                    FileListItem(file = file, onClick = { onFileClick(file) })
                    Spacer(modifier = Modifier.height(8.dp))
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
                Row(
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
                    val statusText = when {
                        isResyncing -> "Syncing vault..."
                        lastSyncedTime > 0 -> {
                            val diff = System.currentTimeMillis() - lastSyncedTime
                            when {
                                diff < 60_000L -> "Synced just now"
                                diff < 3_600_000L -> "Synced ${diff / 60_000L}m ago"
                                diff < 86_400_000L -> "Synced ${diff / 3_600_000L}h ago"
                                else -> "Synced on ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(lastSyncedTime))}"
                            }
                        }
                        else -> "Telegram cloud storage"
                    }
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontFamily = BodySansFont,
                        color = if (isResyncing) colors.teal else colors.textFaint
                    )
                }
            }
        }

        // Header Action Buttons with 3D pressed edge shadow (38dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            // Resync 3D icon button
            IconButton3D(
                onClick = onResync,
                enabled = !isResyncing,
                modifier = Modifier.testTag("resync_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync",
                    tint = if (isResyncing) colors.violet else colors.textDim,
                    modifier = Modifier
                        .size(17.dp)
                        .graphicsLayer { rotationZ = if (isResyncing) spinAngleState.value else 0f }
                )
            }

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
            .clip(StaticItemCornerShape)
            .background(colors.surface)
            .border(1.dp, colors.line, StaticItemCornerShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(StaticIconCornerShape)
                .background(colors.surfaceHi)
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
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
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
                    .background(colors.surfaceHi)
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

@Composable
private fun FileListItem(
    file: FileEntity,
    onClick: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val formattedSize = remember(file.size) { ChecksumUtil.formatFileSize(file.size) }
    val formattedDate = remember(file.uploadDate) { ChecksumUtil.formatDate(file.uploadDate) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(StaticItemCornerShape)
            .background(colors.surface)
            .border(1.dp, colors.line, StaticItemCornerShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

@Composable
private fun FileGridCard(
    file: FileEntity,
    onClick: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val formattedSize = remember(file.size) { ChecksumUtil.formatFileSize(file.size) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(StaticItemCornerShape)
            .background(colors.surface)
            .border(1.dp, colors.line, StaticItemCornerShape)
            .clickable(onClick = onClick)
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
                FileStatusIndicator(status = file.status)
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
            .clip(StaticIconCornerShape)
            .background(theme.bg)
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
