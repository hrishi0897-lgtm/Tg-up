package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.FabGradient
import com.example.ui.theme.FileColorArchive
import com.example.ui.theme.FileColorAudio
import com.example.ui.theme.FileColorDoc
import com.example.ui.theme.FileColorFolder
import com.example.ui.theme.FileColorGeneric
import com.example.ui.theme.FileColorImage
import com.example.ui.theme.FileColorVideo
import com.example.ui.theme.OledBlack
import com.example.ui.theme.OledBorder
import com.example.ui.theme.OledBorderGlow
import com.example.ui.theme.OledBorderSubtle
import com.example.ui.theme.OledCard
import com.example.ui.theme.OledCardElevated
import com.example.ui.theme.OledCardGlass
import com.example.ui.theme.OledSurface
import com.example.ui.theme.OledSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StorageGlowGradient
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramBlueContainer
import com.example.ui.theme.TelegramBlueDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
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
        containerColor = OledBlack,
        topBar = {
            HomeTopBar(
                activeTransferCount = activeCount,
                isResyncing = isResyncing,
                onOpenTransfers = onOpenTransfers,
                onOpenSettings = onOpenSettings,
                onResync = onResync
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Expanded FAB options with spring enter/exit
                AnimatedVisibility(
                    visible = showFabMenu,
                    enter = fadeIn(animationSpec = tween(150)) + expandVertically(
                        animationSpec = spring(
                            dampingRatio = 0.65f,
                            stiffness = 500f
                        )
                    ),
                    exit = fadeOut(animationSpec = tween(100)) + shrinkVertically(
                        animationSpec = tween(150)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Create Folder action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(OledCardElevated)
                                .border(1.dp, OledBorderSubtle, RoundedCornerShape(24.dp))
                                .clickable {
                                    showFabMenu = false
                                    onCreateFolderClick()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "New Folder",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Upload File action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(OledCardElevated)
                                .border(1.dp, OledBorderSubtle, RoundedCornerShape(24.dp))
                                .clickable {
                                    showFabMenu = false
                                    onUploadFileClick()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Upload File",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Primary FAB with tactile spring scale micro-interaction and 45-degree rotation
                val fabInteractionSource = remember { MutableInteractionSource() }
                val isFabPressed by fabInteractionSource.collectIsPressedAsState()
                val fabScale by animateFloatAsState(
                    targetValue = if (isFabPressed) 0.88f else 1f,
                    animationSpec = spring(
                        dampingRatio = 0.45f,
                        stiffness = 400f
                    ),
                    label = "fab_spring_scale"
                )
                val fabRotation by animateFloatAsState(
                    targetValue = if (showFabMenu) 45f else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = 500f
                    ),
                    label = "fab_rotation"
                )

                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = TelegramBlue,
                    contentColor = OledBlack,
                    shape = CircleShape,
                    interactionSource = fabInteractionSource,
                    modifier = Modifier
                        .scale(fabScale)
                        .testTag("fab_add")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add options",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(fabRotation)
                    )
                }
            }
        },
        bottomBar = {
            VaultBottomNav(
                activeTransferCount = activeCount,
                onTransfersSelected = onOpenTransfers
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // 1. Storage Summary Card
            item {
                Spacer(modifier = Modifier.height(8.dp))
                StorageMeterCard(
                    stats = storageStats,
                    onOpenTransfers = onOpenTransfers,
                    activeTransfersCount = activeCount
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Transfer error banner if present
            if (transferErrorMessage != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StatusError.copy(alpha = 0.15f))
                            .border(1.dp, StatusError.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
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
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = transferErrorMessage,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onOpenTransfers,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("View", color = TelegramBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDismissTransferError, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = TextSecondary, fontSize = 12.sp)
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(TelegramBlue.copy(alpha = 0.12f))
                            .border(1.dp, TelegramBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = resyncMessage,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissResyncMsg, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 2. Search & Filter Bar with high-contrast surfaces
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search files in Vault...", color = TextTertiary, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TelegramBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Text("✕", color = TextSecondary, fontSize = 14.sp)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TelegramBlue,
                            unfocusedBorderColor = OledBorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = TelegramBlue,
                            focusedContainerColor = OledSurface,
                            unfocusedContainerColor = OledSurface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("search_bar")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sort menu button
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(OledSurface)
                                .border(1.dp, OledBorderSubtle, RoundedCornerShape(14.dp))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort Options",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier
                                .background(OledCardElevated)
                                .border(1.dp, OledBorderSubtle, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name", color = TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    onSortChange(SortBy.NAME)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date Uploaded", color = TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    onSortChange(SortBy.DATE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("File Size", color = TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    onSortChange(SortBy.SIZE)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Grid / List toggle
                    IconButton(
                        onClick = onToggleViewMode,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(OledSurface)
                            .border(1.dp, OledBorderSubtle, RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            tint = if (isGridView) TelegramBlue else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 3. Breadcrumbs
            item {
                BreadcrumbBar(
                    breadcrumbs = breadcrumbs,
                    onBreadcrumbClick = onBreadcrumbClick
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 4. Folders section (if any)
            if (folders.isNotEmpty() && searchQuery.isBlank()) {
                item {
                    Text(
                        text = "FOLDERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        folders.forEach { folder ->
                            FolderItemRow(
                                folder = folder,
                                onClick = { onFolderClick(folder) },
                                onRename = { onRenameFolder(folder) },
                                onDelete = { onDeleteFolder(folder) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // 5. Files Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "SEARCH RESULTS" else "VAULT FILES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "${files.size} items",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextTertiary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Empty state if no files
            if (files.isEmpty() && folders.isEmpty()) {
                item {
                    EmptyFolderState(
                        isSearch = searchQuery.isNotBlank(),
                        onUploadClick = onUploadFileClick
                    )
                }
            } else if (isGridView) {
                // Grid layout for files
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.height((((files.size + 1) / 2) * 140).dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        userScrollEnabled = false
                    ) {
                        items(files, key = { it.id }) { file ->
                            FileGridCard(file = file, onClick = { onFileClick(file) })
                        }
                    }
                }
            } else {
                // List layout for files
                items(files, key = { it.id }) { file ->
                    FileListItem(file = file, onClick = { onFileClick(file) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    activeTransferCount: Int,
    isResyncing: Boolean,
    onOpenTransfers: () -> Unit,
    onOpenSettings: () -> Unit,
    onResync: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Confident Brand Heading with high-contrast badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OledCardElevated)
                    .border(1.dp, OledBorderSubtle, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = TelegramBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "TeleVault",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    color = TextPrimary
                )
                Text(
                    text = "TELEGRAM CLOUD STORAGE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = TextTertiary
                )
            }
        }

        // Top Actions with refined OLED container pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Resync from Telegram button
            IconButton(
                onClick = onResync,
                enabled = !isResyncing,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OledSurfaceVariant)
                    .border(1.dp, OledBorderSubtle, CircleShape)
            ) {
                Icon(
                    imageVector = if (isResyncing) Icons.Default.Sync else Icons.Default.Refresh,
                    contentDescription = "Resync from Telegram",
                    tint = if (isResyncing) TelegramBlue else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Transfers button with badge (accessible 48dp touch target)
            IconButton(
                onClick = onOpenTransfers,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (activeTransferCount > 0) TelegramBlueContainer else OledSurfaceVariant)
                    .border(
                        1.dp,
                        if (activeTransferCount > 0) TelegramBlue.copy(alpha = 0.5f) else OledBorderSubtle,
                        CircleShape
                    )
                    .testTag("btn_topbar_transfers")
            ) {
                BadgedBox(
                    badge = {
                        if (activeTransferCount > 0) {
                            Badge(
                                containerColor = TelegramBlue,
                                contentColor = OledBlack
                            ) {
                                Text("$activeTransferCount", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Transfers",
                        tint = if (activeTransferCount > 0) TelegramBlue else TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Settings button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OledSurfaceVariant)
                    .border(1.dp, OledBorderSubtle, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StorageMeterCard(
    stats: StorageStats,
    onOpenTransfers: () -> Unit,
    activeTransfersCount: Int
) {
    // Subtle rhythmic pulse on the storage meter glow
    val infiniteTransition = rememberInfiniteTransition(label = "meter_pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(OledCard)
            .border(1.dp, OledBorder.copy(alpha = pulseAlpha), RoundedCornerShape(20.dp))
            .testTag("storage_card")
    ) {
        // Subtle top gradient sheen for depth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(StorageGlowGradient)
        )

        Column(modifier = Modifier.padding(20.dp)) {
            // Live status badge + Unlimited pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusSuccess.copy(alpha = 0.12f))
                        .border(1.dp, StatusSuccess.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(StatusSuccess)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BACKEND CONNECTED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusSuccess,
                        letterSpacing = 0.8.sp
                    )
                }

                Text(
                    text = "∞ UNLIMITED ARCHIVE",
                    fontSize = 10.sp,
                    color = TelegramBlue,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Typographic Showpiece: 40sp Black headline
            Column {
                Text(
                    text = "VAULT STORAGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = TextTertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ChecksumUtil.formatFileSize(stats.totalBytesStored),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.2).sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "in cloud",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dynamic Unlimited Growth Sparkline
            UsageSparkline()

            Spacer(modifier = Modifier.height(16.dp))

            // Stat capsules: 3 well-differentiated compartments
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCapsule(
                    modifier = Modifier.weight(1f),
                    title = "FILES",
                    value = "${stats.fileCount}",
                    highlight = false
                )
                StatCapsule(
                    modifier = Modifier.weight(1f),
                    title = "FOLDERS",
                    value = "${stats.folderCount}",
                    highlight = false
                )
                StatCapsule(
                    modifier = Modifier.weight(1.2f),
                    title = "TRANSFERS",
                    value = if (activeTransfersCount > 0) "$activeTransfersCount active" else "Idle",
                    highlight = activeTransfersCount > 0,
                    onClick = onOpenTransfers
                )
            }
        }
    }
}

@Composable
private fun UsageSparkline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OledSurface)
            .border(1.dp, OledBorderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Rising data points reflecting unlimited growth
            val points = listOf(
                Offset(0f, height * 0.88f),
                Offset(width * 0.16f, height * 0.72f),
                Offset(width * 0.36f, height * 0.74f),
                Offset(width * 0.54f, height * 0.46f),
                Offset(width * 0.72f, height * 0.52f),
                Offset(width * 0.88f, height * 0.22f),
                Offset(width, height * 0.12f)
            )

            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    val midX = (p0.x + p1.x) / 2
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
            }

            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(TelegramBlue.copy(alpha = 0.2f), TelegramBlue)
                ),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Current end point glowing indicator dot
            drawCircle(
                color = TelegramBlue.copy(alpha = 0.3f),
                radius = 6.dp.toPx(),
                center = points.last()
            )
            drawCircle(
                color = TelegramBlue,
                radius = 3.5.dp.toPx(),
                center = points.last()
            )
        }
    }
}

@Composable
private fun StatCapsule(
    title: String,
    value: String,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) TelegramBlueContainer else OledSurface)
            .border(
                1.dp,
                if (highlight) TelegramBlue.copy(alpha = 0.4f) else OledBorderSubtle,
                RoundedCornerShape(10.dp)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = if (highlight) TelegramBlue else TextTertiary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) TelegramBlue else TextPrimary
        )
    }
}

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
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast) TextPrimary else TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBreadcrumbClick(index) }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
            )
            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = TextTertiary,
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
            .background(OledCard)
            .border(1.dp, OledBorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TelegramBlueContainer)
                .border(1.dp, TelegramBlue.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = TelegramBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Folder · ${ChecksumUtil.formatDate(folder.createdDate)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextTertiary
            )
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(OledSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Folder Options",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(OledCardElevated)
                    .border(1.dp, OledBorderSubtle, RoundedCornerShape(8.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Rename Folder", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = StatusError, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
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
            .background(OledCard)
            .border(1.dp, OledBorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIcon(mimeType = file.mimeType, size = 24.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ChecksumUtil.formatFileSize(file.size),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Text(
                    text = " · ",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
                Text(
                    text = ChecksumUtil.formatDate(file.uploadDate),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextTertiary
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
            .height(138.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OledCard)
            .border(1.dp, OledBorderSubtle, RoundedCornerShape(14.dp))
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
                FileIcon(mimeType = file.mimeType, size = 24.dp)
                FileStatusIndicator(status = file.status)
            }

            Column {
                Text(
                    text = file.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ChecksumUtil.formatFileSize(file.size),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
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
                tint = StatusSuccess,
                modifier = Modifier.size(16.dp)
            )
        }
        FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Transferring",
                tint = TelegramBlue,
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
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyFolderState(
    isSearch: Boolean,
    onUploadClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(OledCardElevated)
                .border(1.dp, OledBorderSubtle, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearch) Icons.Default.Search else Icons.Default.Cloud,
                contentDescription = null,
                tint = TelegramBlue,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isSearch) "No Matching Files" else "Vault is Clean",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isSearch) "Try searching for a different name or file extension."
            else "Upload files of any size. TeleVault will chunk and securely archive them to Telegram with zero storage limits.",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (!isSearch) {
            Spacer(modifier = Modifier.height(22.dp))
            androidx.compose.material3.Button(
                onClick = onUploadClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = TelegramBlue,
                    contentColor = OledBlack
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Upload First File",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun VaultBottomNav(
    activeTransferCount: Int,
    onTransfersSelected: () -> Unit
) {
    NavigationBar(
        containerColor = OledBlack,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .border(
                width = 1.dp,
                color = OledBorderSubtle,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
    ) {
        NavigationBarItem(
            selected = true,
            onClick = { /* Already in Vault */ },
            icon = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(TelegramBlueContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Vault",
                        tint = TelegramBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            label = {
                Text(
                    text = "Vault",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TelegramBlue,
                selectedTextColor = TelegramBlue,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = Color.Transparent
            ),
            modifier = Modifier.testTag("tab_nav_vault")
        )

        NavigationBarItem(
            selected = false,
            onClick = onTransfersSelected,
            icon = {
                if (activeTransferCount > 0) {
                    BadgedBox(badge = {
                        Badge(
                            containerColor = TelegramBlue,
                            contentColor = OledBlack
                        ) {
                            Text(
                                text = activeTransferCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Transfers",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Transfers",
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            label = {
                Text(
                    text = "Transfers",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TelegramBlue,
                selectedTextColor = TelegramBlue,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = Color.Transparent
            ),
            modifier = Modifier.testTag("tab_nav_transfers")
        )
    }
}

