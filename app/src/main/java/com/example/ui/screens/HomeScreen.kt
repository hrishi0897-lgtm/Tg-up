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
import com.example.ui.components.TeleVaultBottomNav
import com.example.ui.theme.LocalReduceMotion
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
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.AppBackgroundOuter
import com.example.ui.theme.AppSurface
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.BorderDivider
import com.example.ui.theme.BorderSubtle
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
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusMint
import com.example.ui.theme.StatusMintBg
import com.example.ui.theme.StatusMintBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TealFabGradient
import com.example.ui.theme.TextDimmed
import com.example.ui.theme.TextFaint
import com.example.ui.theme.TextPrimary
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
    activeTransfers: List<TransferProgress>,
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
    transferErrorMessage: String? = null,
    onDismissTransferError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showFabMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val activeCount = activeTransfers.count {
        it.status == FileStatus.UPLOADING || it.status == FileStatus.DOWNLOADING
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackgroundOuter),
        containerColor = AppSurface,
        topBar = {
            HomeTopBar(
                isResyncing = isResyncing,
                lastSyncedTime = lastSyncedTime,
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
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // New Folder Action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCardElevated)
                                .border(1.dp, BorderDivider, RoundedCornerShape(14.dp))
                                .clickable {
                                    showFabMenu = false
                                    onCreateFolderClick()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "New Folder",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = BodySansFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Upload File Action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCardElevated)
                                .border(1.dp, BorderDivider, RoundedCornerShape(14.dp))
                                .clickable {
                                    showFabMenu = false
                                    onUploadFileClick()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Upload File",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = BodySansFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Primary Teal Gradient FAB with tactile spring scale and rotation
                val fabInteractionSource = remember { MutableInteractionSource() }
                val isFabPressed by fabInteractionSource.collectIsPressedAsState()
                val fabScale by animateFloatAsState(
                    targetValue = if (isFabPressed) 0.90f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f),
                    label = "fab_scale"
                )
                val fabRotation by animateFloatAsState(
                    targetValue = if (showFabMenu) 45f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "fab_rotation"
                )

                Box(
                    modifier = Modifier
                        .scale(fabScale)
                        .drawBehind {
                            drawRoundRect(
                                color = Color(0x5935E0C2),
                                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                                topLeft = Offset(0f, 6.dp.toPx()),
                                size = Size(size.width, size.height)
                            )
                        }
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(TealFabGradient)
                        .clickable(
                            interactionSource = fabInteractionSource,
                            indication = null
                        ) { showFabMenu = !showFabMenu }
                        .testTag("fab_add"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add options",
                        tint = AppBackgroundOuter,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(fabRotation)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 96.dp)
        ) {
            // 1. Status Badge: "Backend connected" with glowing mint dot
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(StatusMintBg)
                        .border(1.dp, StatusMintBorder, RoundedCornerShape(100.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StatusMint)
                    )
                    Text(
                        text = "Backend connected",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = BodySansFont,
                        color = StatusMint
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Transfer error banner if present
            if (transferErrorMessage != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FF5252))
                            .border(1.dp, Color(0x55FF5252), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Transfer Error",
                            tint = StatusError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Transfer Failed",
                                color = StatusError,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodySansFont
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = transferErrorMessage,
                                color = TextPrimary,
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
                                color = AccentViolet,
                                fontSize = 12.sp,
                                fontFamily = BodySansFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismissTransferError, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = TextDimmed, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Resync notification banner if present
            if (resyncMessage != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1A8C7CFF))
                            .border(1.dp, Color(0x408C7CFF), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = AccentViolet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = resyncMessage,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = BodySansFont,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissResyncMsg, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = TextDimmed, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 2. Hero: 120dp Circular Progress Ring Card with subtle radial gradient
            item {
                StorageMeterCard(
                    stats = storageStats,
                    onOpenTransfers = onOpenTransfers,
                    activeTransfersCount = activeCount
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. Search & Filter Row: Search Box + Sort Square Button + View Toggle Square Button
            item {
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
                            .background(SurfaceCard)
                            .border(1.dp, BorderDivider, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextFaint,
                            modifier = Modifier.size(16.dp)
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            textStyle = TextStyle(
                                fontFamily = BodySansFont,
                                fontSize = 14.sp,
                                color = TextPrimary
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(AccentViolet),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search the vault",
                                        fontFamily = BodySansFont,
                                        fontSize = 14.sp,
                                        color = TextFaint
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
                                color = TextDimmed,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable { onSearchChange("") }
                                    .padding(4.dp)
                            )
                        }
                    }

                    // Sort Square Button
                    Box {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCard)
                                .border(1.dp, BorderDivider, RoundedCornerShape(14.dp))
                                .pressScale(0.88f)
                                .clickable { showSortMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                                tint = TextDimmed,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier
                                .background(SurfaceCardElevated)
                                .border(1.dp, BorderDivider, RoundedCornerShape(12.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name", color = TextPrimary, fontSize = 13.sp, fontFamily = BodySansFont) },
                                onClick = {
                                    onSortChange(SortBy.NAME)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date Uploaded", color = TextPrimary, fontSize = 13.sp, fontFamily = BodySansFont) },
                                onClick = {
                                    onSortChange(SortBy.DATE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("File Size", color = TextPrimary, fontSize = 13.sp, fontFamily = BodySansFont) },
                                onClick = {
                                    onSortChange(SortBy.SIZE)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    // View Toggle Square Button
                    val viewToggleRot by animateFloatAsState(
                        targetValue = if (isGridView) 180f else 0f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
                        label = "view_toggle_rot"
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderDivider, RoundedCornerShape(14.dp))
                            .pressScale(0.88f)
                            .clickable(onClick = onToggleViewMode),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle view",
                            tint = TextDimmed,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(viewToggleRot)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 4. Breadcrumbs (if inside a subfolder)
            if (breadcrumbs.size > 1) {
                item {
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
                    Box(modifier = Modifier.animateItem()) {
                        Column {
                            Text(
                                text = "Folders",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = BodySansFont,
                                color = TextDimmed
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                items(folders, key = { "folder_${it.id}" }) { folder ->
                    Box(modifier = Modifier.animateItem()) {
                        FolderItemRow(
                            folder = folder,
                            onClick = { onFolderClick(folder) },
                            onRename = { onRenameFolder(folder) },
                            onDelete = { onDeleteFolder(folder) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item(key = "folders_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 6. Section Head: "Vault files" + Item Count in Numeric Monospace Font
            item(key = "vault_files_header") {
                Box(modifier = Modifier.animateItem()) {
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
                            color = TextPrimary
                        )
                        Text(
                            text = "${files.size} items",
                            fontSize = 12.5.sp,
                            fontFamily = NumericMonoFont,
                            color = TextFaint
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 7. Content: Empty State or File List/Grid
            if (files.isEmpty() && folders.isEmpty()) {
                item(key = "empty_state") {
                    Box(modifier = Modifier.animateItem()) {
                        EmptyFolderState(
                            isSearch = searchQuery.isNotBlank(),
                            onUploadClick = onUploadFileClick
                        )
                    }
                }
            } else if (isGridView) {
                item(key = "grid_view_container") {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.height((((files.size + 1) / 2) * 144).dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        userScrollEnabled = false
                    ) {
                        items(files, key = { "grid_${it.id}" }) { file ->
                            Box(modifier = Modifier.animateItem()) {
                                FileGridCard(file = file, onClick = { onFileClick(file) })
                            }
                        }
                    }
                }
            } else {
                items(files, key = { "file_${it.id}" }) { file ->
                    Box(modifier = Modifier.animateItem()) {
                        FileListItem(file = file, onClick = { onFileClick(file) })
                    }
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
    onOpenSettings: () -> Unit,
    onResync: () -> Unit
) {
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
                            color = Color(0x598C7CFF),
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
                Canvas(modifier = Modifier.size(22.dp)) {
                    val w = size.width
                    val h = size.height
                    val outerPath = Path().apply {
                        moveTo(w * 0.5f, h * 0.125f)
                        lineTo(w * 0.833f, h * 0.333f)
                        lineTo(w * 0.833f, h * 0.667f)
                        lineTo(w * 0.5f, h * 0.875f)
                        lineTo(w * 0.167f, h * 0.667f)
                        lineTo(w * 0.167f, h * 0.333f)
                        close()
                    }
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
                    style = WordmarkTextStyle
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val dotColor = when {
                        isResyncing -> AccentTeal
                        lastSyncedTime > 0 -> StatusMint
                        else -> TextFaint
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
                        color = if (isResyncing) AccentTeal else TextFaint
                    )
                }
            }
        }

        // Header Action Buttons (38dp square buttons)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "resync_infinite")
            val spinAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(850, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "resync_angle"
            )

            // Resync icon button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderDivider, RoundedCornerShape(12.dp))
                    .pressScale(0.88f)
                    .clickable(enabled = !isResyncing, onClick = onResync),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync",
                    tint = if (isResyncing) AccentViolet else TextDimmed,
                    modifier = Modifier
                        .size(17.dp)
                        .rotate(if (isResyncing) spinAngle else 0f)
                )
            }

            // Settings icon button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderDivider, RoundedCornerShape(12.dp))
                    .pressScale(0.88f)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextDimmed,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

// ==========================================
// Storage Meter Hero Card (120dp Circular Ring + Radial Gradient)
// ==========================================

@Composable
private fun StorageMeterCard(
    stats: StorageStats,
    onOpenTransfers: () -> Unit,
    activeTransfersCount: Int
) {
    val reduceMotion = LocalReduceMotion.current
    val (storageValue, storageUnit) = remember(stats.totalBytesStored) {
        splitStorageValueAndUnit(stats.totalBytesStored)
    }

    val targetAngle = remember(stats.totalBytesStored) {
        if (stats.totalBytesStored <= 0L) {
            28f
        } else {
            val gbStored = stats.totalBytesStored.toFloat() / (1024f * 1024f * 1024f)
            (28f + (gbStored * 30f).coerceIn(0f, 310f)).coerceAtMost(340f)
        }
    }

    var ringAnimationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ringAnimationStarted = true
    }

    val animatedAngle by animateFloatAsState(
        targetValue = if (reduceMotion) targetAngle else if (ringAnimationStarted) targetAngle else 0f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            spring(
                dampingRatio = 0.8f,
                stiffness = 220f
            )
        },
        label = "ring_fill_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceCard)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF191F35), SurfaceCard),
                        center = Offset(size.width * 0.15f, 0f),
                        radius = size.width * 1.3f
                    )
                )
            }
            .border(1.dp, BorderDivider, RoundedCornerShape(28.dp))
            .padding(top = 24.dp, start = 22.dp, end = 22.dp, bottom = 22.dp)
            .testTag("storage_card")
    ) {
        Column {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vault storage",
                    fontSize = 13.sp,
                    fontFamily = BodySansFont,
                    color = TextDimmed
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(
                            color = AccentTeal,
                            radius = size.minDimension / 2f - 1.dp.toPx(),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }
                    Text(
                        text = "No archive limit",
                        fontSize = 12.sp,
                        fontFamily = BodySansFont,
                        fontWeight = FontWeight.Normal,
                        color = AccentTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Ring + Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // Circular Ring (120dp)
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 9.dp.toPx()
                        val diameter = size.minDimension - strokePx
                        val topLeft = Offset(strokePx / 2f, strokePx / 2f)
                        val arcSize = Size(diameter, diameter)

                        // Background track
                        drawArc(
                            color = SurfaceCardElevated,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx)
                        )

                        // Progress gradient arc
                        if (animatedAngle > 0f) {
                            drawArc(
                                brush = Brush.linearGradient(
                                    colors = listOf(AccentViolet, AccentTeal),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, size.height)
                                ),
                                startAngle = -90f,
                                sweepAngle = animatedAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Numeric Monospace Storage Value & Unit
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedContent(
                            targetState = storageValue,
                            transitionSpec = {
                                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                            },
                            label = "storage_val_crossfade"
                        ) { valText ->
                            Text(
                                text = valText,
                                style = MonoStatValueLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = storageUnit,
                            fontSize = 10.5.sp,
                            fontFamily = BodySansFont,
                            color = TextFaint,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Vertical Stat List beside ring
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Files
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "Files",
                            fontSize = 13.sp,
                            fontFamily = BodySansFont,
                            color = TextDimmed
                        )
                        AnimatedContent(
                            targetState = stats.fileCount,
                            transitionSpec = {
                                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                            },
                            label = "files_count_crossfade"
                        ) { count ->
                            Text(
                                text = "$count",
                                style = MonoStatValueMedium
                            )
                        }
                    }

                    // Folders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "Folders",
                            fontSize = 13.sp,
                            fontFamily = BodySansFont,
                            color = TextDimmed
                        )
                        Text(
                            text = "${stats.folderCount}",
                            style = MonoStatValueMedium
                        )
                    }

                    // Transfers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenTransfers),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "Transfers",
                            fontSize = 13.sp,
                            fontFamily = BodySansFont,
                            color = TextDimmed
                        )
                        if (activeTransfersCount > 0) {
                            Text(
                                text = "$activeTransfersCount active",
                                style = MonoStatValueMedium,
                                color = AccentViolet
                            )
                        } else {
                            Text(
                                text = "Idle",
                                fontSize = 14.sp,
                                fontFamily = BodySansFont,
                                color = TextFaint
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun splitStorageValueAndUnit(bytes: Long): Pair<String, String> {
    if (bytes <= 0L) return Pair("0", "bytes used")
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val tb = gb * 1024.0
    return when {
        bytes >= tb -> Pair(String.format(java.util.Locale.US, "%.1f", bytes / tb), "TB used")
        bytes >= gb -> Pair(String.format(java.util.Locale.US, "%.1f", bytes / gb), "GB used")
        bytes >= mb -> Pair(String.format(java.util.Locale.US, "%.1f", bytes / mb), "MB used")
        bytes >= kb -> Pair(String.format(java.util.Locale.US, "%.1f", bytes / kb), "KB used")
        else -> Pair("$bytes", "bytes used")
    }
}

// ==========================================
// Empty State: Outlined Vault Canvas + Serif Headline + Violet Gradient Button
// ==========================================

@Composable
private fun EmptyFolderState(
    isSearch: Boolean,
    onUploadClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Outlined vault icon illustration
        Canvas(modifier = Modifier.size(72.dp)) {
            val scale = size.width / 72f
            val strokeWidth = 2.dp.toPx()
            val baseColor = Color(0xFF3A4160)
            val accentColor = AccentViolet

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

            // Handle arch at top: x=22, y=10, up to y=6, then curve to y=2 at x=26, across to x=46, curve to (50, 6) then (50, 10)
            val handlePath = Path().apply {
                moveTo(22f * scale, 10f * scale)
                lineTo(22f * scale, 6f * scale)
                quadraticTo(22f * scale, 2f * scale, 26f * scale, 2f * scale)
                lineTo(46f * scale, 2f * scale)
                quadraticTo(50f * scale, 2f * scale, 50f * scale, 6f * scale)
                lineTo(50f * scale, 10f * scale)
            }
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
            style = EmptyHeadlineStyle,
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
            color = TextDimmed,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 270.dp)
        )

        if (!isSearch) {
            Spacer(modifier = Modifier.height(22.dp))

            // Gradient violet upload button with shadow glow
            Box(
                modifier = Modifier
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0x4D8C7CFF),
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                            topLeft = Offset(0f, 6.dp.toPx()),
                            size = Size(size.width, size.height)
                        )
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(VioletButtonGradient)
                    .pressScale(0.94f)
                    .clickable(onClick = onUploadClick)
                    .padding(horizontal = 22.dp, vertical = 13.dp)
                    .testTag("empty_upload_button"),
                contentAlignment = Alignment.Center
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
    Surface(
        color = Color(0xEB0B0D14),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .border(
                width = 1.dp,
                color = BorderDivider,
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
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Vault",
                    fontSize = 11.5.sp,
                    fontFamily = BodySansFont,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                // Active Underline Indicator in Violet
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentViolet)
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
                                containerColor = AccentViolet,
                                contentColor = AppBackgroundOuter
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
                            tint = TextFaint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Transfers",
                        tint = TextFaint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Transfers",
                    fontSize = 11.5.sp,
                    fontFamily = BodySansFont,
                    fontWeight = FontWeight.Normal,
                    color = TextFaint
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
                color = if (isLast) TextPrimary else TextDimmed,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBreadcrumbClick(index) }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
            )
            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = TextFaint,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderItemRow(
    folder: FolderEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderDivider, RoundedCornerShape(14.dp))
            .pressScale(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCardElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
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
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Folder · ${ChecksumUtil.formatDate(folder.createdDate)}",
                fontSize = 11.sp,
                fontFamily = NumericMonoFont,
                color = TextFaint
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
                    tint = TextDimmed,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(SurfaceCardElevated)
                    .border(1.dp, BorderDivider, RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Rename Folder", color = TextPrimary, fontSize = 13.sp, fontFamily = BodySansFont) },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = StatusError, fontSize = 13.sp, fontFamily = BodySansFont, fontWeight = FontWeight.SemiBold) },
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderDivider, RoundedCornerShape(14.dp))
            .pressScale(0.98f)
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
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ChecksumUtil.formatFileSize(file.size),
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    color = TextDimmed
                )
                Text(
                    text = " · ",
                    fontSize = 11.sp,
                    color = TextFaint
                )
                Text(
                    text = ChecksumUtil.formatDate(file.uploadDate),
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    color = TextFaint
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderDivider, RoundedCornerShape(14.dp))
            .pressScale(0.98f)
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
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = ChecksumUtil.formatFileSize(file.size),
                    fontSize = 10.5.sp,
                    fontFamily = NumericMonoFont,
                    color = TextDimmed
                )
            }
        }
    }
}

@Composable
fun FileIcon(mimeType: String, size: androidx.compose.ui.unit.Dp) {
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

    Box(
        modifier = Modifier
            .size(size + 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
private fun FileStatusIndicator(status: FileStatus) {
    when (status) {
        FileStatus.COMPLETED -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Stored",
                tint = StatusMint,
                modifier = Modifier.size(16.dp)
            )
        }
        FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Transferring",
                tint = AccentViolet,
                modifier = Modifier.size(16.dp)
            )
        }
        FileStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Failed",
                tint = StatusError,
                modifier = Modifier.size(16.dp)
            )
        }
        FileStatus.PAUSED -> {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Paused",
                tint = Color(0xFFFFCA28),
                modifier = Modifier.size(16.dp)
            )
        }
        FileStatus.PENDING -> {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Pending",
                tint = TextFaint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
