package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.OledBlack
import com.example.ui.theme.OledBorder
import com.example.ui.theme.OledCard
import com.example.ui.theme.OledCardElevated
import com.example.ui.theme.OledSurface
import com.example.ui.theme.OledSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TelegramBlue
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
                // Expanded FAB options
                AnimatedVisibility(visible = showFabMenu) {
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
                                .border(1.dp, OledBorder, RoundedCornerShape(24.dp))
                                .clickable {
                                    showFabMenu = false
                                    onCreateFolderClick()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "New Folder",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Upload File action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(OledCardElevated)
                                .border(1.dp, OledBorder, RoundedCornerShape(24.dp))
                                .clickable {
                                    showFabMenu = false
                                    onUploadFileClick()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Upload File",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Primary FAB
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = TelegramBlue,
                    contentColor = OledBlack,
                    shape = CircleShape,
                    modifier = Modifier.testTag("fab_add")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add options",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
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

            // 2. Search & Filter Bar
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
                                tint = TextSecondary,
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
                            unfocusedBorderColor = OledBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = TelegramBlue,
                            focusedContainerColor = OledSurface,
                            unfocusedContainerColor = OledSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("search_bar")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sort menu button
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OledSurface)
                                .border(1.dp, OledBorder, RoundedCornerShape(12.dp))
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
                            modifier = Modifier.background(OledCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name", color = TextPrimary) },
                                onClick = {
                                    onSortChange(SortBy.NAME)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date Uploaded", color = TextPrimary) },
                                onClick = {
                                    onSortChange(SortBy.DATE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("File Size", color = TextPrimary) },
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
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(OledSurface)
                            .border(1.dp, OledBorder, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            tint = TextSecondary,
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
                        fontWeight = FontWeight.SemiBold,
                        color = TextTertiary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                    Spacer(modifier = Modifier.height(18.dp))
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
                        text = if (searchQuery.isNotBlank()) "SEARCH RESULTS" else "FILES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextTertiary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${files.size} items",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(TelegramBlue.copy(alpha = 0.15f))
                    .border(1.dp, TelegramBlue.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = TelegramBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "TeleVault",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Resync from Telegram button
            IconButton(
                onClick = onResync,
                enabled = !isResyncing,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isResyncing) Icons.Default.Sync else Icons.Default.Refresh,
                    contentDescription = "Resync from Telegram",
                    tint = if (isResyncing) TelegramBlue else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Transfers Sheet button with badge
            IconButton(
                onClick = onOpenTransfers,
                modifier = Modifier.size(36.dp)
            ) {
                BadgedBox(
                    badge = {
                        if (activeTransferCount > 0) {
                            Badge(
                                containerColor = TelegramBlue,
                                contentColor = OledBlack
                            ) {
                                Text("$activeTransferCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Transfers",
                        tint = if (activeTransferCount > 0) TelegramBlue else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Settings button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = OledCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storage_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(StatusSuccess)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TELEGRAM BACKEND ACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusSuccess,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "Unlimited Storage",
                    fontSize = 12.sp,
                    color = TelegramBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = ChecksumUtil.formatFileSize(stats.totalBytesStored),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "stored securely",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Unlimited Rising Sparkline Visualizer
            UsageSparkline()

            Spacer(modifier = Modifier.height(14.dp))

            // Quick stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatPill(title = "Files", value = "${stats.fileCount}")
                StatPill(title = "Folders", value = "${stats.folderCount}")
                StatPill(
                    title = "Active Transfers",
                    value = if (activeTransfersCount > 0) "$activeTransfersCount running" else "Idle",
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
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(OledSurface)
            .border(1.dp, OledBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Rising data points reflecting unlimited growth
            val points = listOf(
                Offset(0f, height * 0.85f),
                Offset(width * 0.15f, height * 0.70f),
                Offset(width * 0.35f, height * 0.75f),
                Offset(width * 0.55f, height * 0.45f),
                Offset(width * 0.75f, height * 0.50f),
                Offset(width * 0.90f, height * 0.25f),
                Offset(width, height * 0.15f)
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
                    colors = listOf(TelegramBlue.copy(alpha = 0.3f), TelegramBlue)
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Current end point indicator dot
            drawCircle(
                color = TelegramBlue,
                radius = 3.dp.toPx(),
                center = points.last()
            )
        }
    }
}

@Composable
private fun StatPill(
    title: String,
    value: String,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Text(text = title, fontSize = 11.sp, color = TextTertiary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
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
            .clip(RoundedCornerShape(12.dp))
            .background(OledCard)
            .border(1.dp, OledBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Folder",
            tint = TelegramBlue,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = ChecksumUtil.formatDate(folder.createdDate),
                fontSize = 11.sp,
                color = TextTertiary
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Folder Options",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(OledCard)
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = StatusError) },
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
            .clip(RoundedCornerShape(12.dp))
            .background(OledCard)
            .border(1.dp, OledBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIcon(mimeType = file.mimeType, size = 26.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ChecksumUtil.formatFileSize(file.size),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = " · ",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
                Text(
                    text = if (file.totalChunks > 1) "${file.totalChunks} chunks" else "1 chunk",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
                Text(
                    text = " · ",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
                Text(
                    text = ChecksumUtil.formatDate(file.uploadDate),
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        FileStatusIndicator(status = file.status)
    }
}

@Composable
private fun FileGridCard(
    file: FileEntity,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OledCard),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FileIcon(mimeType = file.mimeType, size = 28.dp)
                FileStatusIndicator(status = file.status)
            }

            Column {
                Text(
                    text = file.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = ChecksumUtil.formatFileSize(file.size),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun FileIcon(mimeType: String, size: androidx.compose.ui.unit.Dp) {
    val (icon, tint) = when {
        mimeType.startsWith("image/") -> Pair(Icons.Default.Image, Color(0xFF29B6F6))
        mimeType.startsWith("video/") -> Pair(Icons.Default.Movie, Color(0xFFAB47BC))
        mimeType.startsWith("audio/") -> Pair(Icons.Default.AudioFile, Color(0xFFFFA726))
        mimeType.contains("pdf") || mimeType.contains("document") || mimeType.contains("text") ->
            Pair(Icons.Default.Description, Color(0xFF26A69A))
        mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("rar") ->
            Pair(Icons.Default.FolderZip, Color(0xFFFF7043))
        else -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF78909C))
    }

    Box(
        modifier = Modifier
            .size(size + 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f)),
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
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(OledCard)
                .border(1.dp, OledBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearch) Icons.Default.Search else Icons.Default.Cloud,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSearch) "No Matching Files Found" else "No Files in This Folder",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isSearch) "Try searching for a different keyword or check your spelling."
            else "Upload files of any size. TeleVault will chunk and securely archive them to Telegram.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        if (!isSearch) {
            Spacer(modifier = Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = onUploadClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = TelegramBlue,
                    contentColor = OledBlack
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload to Vault", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
