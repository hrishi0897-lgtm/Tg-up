package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.dao.FileDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FolderEntity
import com.example.domain.ChecksumUtil
import com.example.domain.model.BreadcrumbItem
import com.example.domain.model.classifyFileCategory
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.FileColorArchive
import com.example.ui.theme.FileColorAudio
import com.example.ui.theme.FileColorDoc
import com.example.ui.theme.FileColorFolder
import com.example.ui.theme.FileColorGeneric
import com.example.ui.theme.FileColorImage
import com.example.ui.theme.FileColorVideo
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont
import com.example.ui.theme.pressScale
import com.example.ui.viewmodel.TeleVaultViewModel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Stateful FolderManagementScreen composable powered directly by [FolderDao] and optional [FileDao].
 * Displays current directory contents (subfolders and files) and enables intuitive hierarchical
 * navigation into subfolders, parent folders, breadcrumb jumping, and folder management actions.
 */
@Composable
fun FolderManagementScreen(
    folderDao: FolderDao,
    fileDao: FileDao? = null,
    initialFolderId: String? = null,
    onBack: () -> Unit,
    onFileClick: ((FileEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var currentFolderId by remember { mutableStateOf(initialFolderId) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }

    // Dialog states
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToMove by remember { mutableStateOf<FolderEntity?>(null) }
    var fileToMove by remember { mutableStateOf<FileEntity?>(null) }

    // Observe all folders to build breadcrumbs and move destinations
    val allFolders by folderDao.observeAll().collectAsState(initial = emptyList())

    // Observe subfolders of the current directory
    val subfolders by folderDao.observeSubfolders(currentFolderId).collectAsState(initial = emptyList())

    // Observe files of the current directory
    val files by folderDao.observeFilesInFolder(currentFolderId).collectAsState(initial = emptyList())

    // Current folder entity
    val currentFolder = remember(currentFolderId, allFolders) {
        currentFolderId?.let { id -> allFolders.find { it.id == id } }
    }

    // Build breadcrumb trail dynamically from parent relationships
    val breadcrumbs by remember(currentFolderId, allFolders) {
        derivedStateOf {
            val list = mutableListOf<BreadcrumbItem>()
            var currId = currentFolderId
            while (currId != null) {
                val folder = allFolders.find { it.id == currId }
                if (folder != null) {
                    list.add(0, BreadcrumbItem(folder.id, folder.name))
                    currId = folder.parentFolderId
                } else {
                    break
                }
            }
            list.add(0, BreadcrumbItem(null, "Vault Root"))
            list.toList()
        }
    }

    // Real-time filtering for subfolders and files based on search query
    val filteredSubfolders = remember(subfolders, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) subfolders
        else subfolders.filter { it.name.contains(query, ignoreCase = true) }
    }

    val filteredFiles = remember(files, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) files
        else files.filter { it.name.contains(query, ignoreCase = true) }
    }

    FolderManagementContent(
        currentFolder = currentFolder,
        subfolders = filteredSubfolders,
        files = filteredFiles,
        breadcrumbs = breadcrumbs,
        allFolders = allFolders,
        searchQuery = searchQuery,
        isGridView = isGridView,
        onSearchQueryChange = { searchQuery = it },
        onToggleViewMode = { isGridView = !isGridView },
        onNavigateToSubfolder = { folder ->
            currentFolderId = folder.id
            searchQuery = ""
        },
        onNavigateToBreadcrumb = { targetFolderId ->
            currentFolderId = targetFolderId
            searchQuery = ""
        },
        onNavigateUp = {
            currentFolderId = currentFolder?.parentFolderId
            searchQuery = ""
        },
        onCreateFolderClick = { showCreateFolderDialog = true },
        onRenameFolderClick = { folderToRename = it },
        onDeleteFolderClick = { folderToDelete = it },
        onMoveFolderClick = { folderToMove = it },
        onMoveFileClick = { fileToMove = it },
        onFileClick = { onFileClick?.invoke(it) },
        onBack = onBack,
        modifier = modifier
    )

    // Create New Folder Dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                coroutineScope.launch {
                    val newFolder = FolderEntity(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        parentFolderId = currentFolderId,
                        createdDate = System.currentTimeMillis()
                    )
                    folderDao.insert(newFolder)
                    showCreateFolderDialog = false
                }
            }
        )
    }

    // Rename Folder Dialog
    folderToRename?.let { folder ->
        RenameFolderDialog(
            currentName = folder.name,
            onDismiss = { folderToRename = null },
            onConfirm = { newName ->
                coroutineScope.launch {
                    folderDao.renameFolder(folder.id, newName.trim())
                    folderToRename = null
                }
            }
        )
    }

    // Delete Folder Confirmation Dialog
    folderToDelete?.let { folder ->
        DeleteFolderConfirmationDialog(
            folderName = folder.name,
            onDismiss = { folderToDelete = null },
            onConfirm = {
                coroutineScope.launch {
                    folderDao.deleteById(folder.id)
                    folderToDelete = null
                }
            }
        )
    }

    // Move Folder Dialog
    folderToMove?.let { folder ->
        val candidateDestinations = remember(allFolders, folder) {
            // Cannot move folder into itself or its direct/indirect children
            val descendantIds = mutableSetOf(folder.id)
            var added: Boolean
            do {
                added = false
                allFolders.forEach { f ->
                    if (f.parentFolderId in descendantIds && f.id !in descendantIds) {
                        descendantIds.add(f.id)
                        added = true
                    }
                }
            } while (added)

            allFolders.filter { it.id !in descendantIds }
        }

        MoveFolderDialog(
            folder = folder,
            candidateFolders = candidateDestinations,
            onDismiss = { folderToMove = null },
            onSelectDestination = { targetParentId ->
                coroutineScope.launch {
                    folderDao.moveFolder(folder.id, targetParentId)
                    folderToMove = null
                }
            }
        )
    }

    // Move File Dialog
    fileToMove?.let { file ->
        MoveFileDialog(
            fileName = file.name,
            folders = allFolders,
            currentFolderId = currentFolderId,
            onDismiss = { fileToMove = null },
            onSelectDestination = { targetFolderId ->
                coroutineScope.launch {
                    folderDao.moveFile(file.id, targetFolderId)
                    fileDao?.moveFile(file.id, targetFolderId)
                    fileToMove = null
                }
            }
        )
    }
}

/**
 * ViewModel overload for FolderManagementScreen.
 */
@Composable
fun FolderManagementScreen(
    viewModel: TeleVaultViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember(context) { com.example.data.local.AppDatabase.getInstance(context) }
    val uiState by viewModel.uiState.collectAsState()

    FolderManagementScreen(
        folderDao = database.folderDao(),
        fileDao = database.fileDao(),
        initialFolderId = uiState.currentFolderId,
        onBack = onBack,
        onFileClick = { viewModel.inspectFile(it) },
        modifier = modifier
    )
}

/**
 * FileManagementScreen alias composables to support both File and Folder management naming conventions.
 */
@Composable
fun FileManagementScreen(
    folderDao: FolderDao,
    fileDao: FileDao? = null,
    initialFolderId: String? = null,
    onBack: () -> Unit,
    onFileClick: ((FileEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    FolderManagementScreen(
        folderDao = folderDao,
        fileDao = fileDao,
        initialFolderId = initialFolderId,
        onBack = onBack,
        onFileClick = onFileClick,
        modifier = modifier
    )
}

@Composable
fun FileManagementScreen(
    viewModel: TeleVaultViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    FolderManagementScreen(
        viewModel = viewModel,
        onBack = onBack,
        modifier = modifier
    )
}

/**
 * FileManagementContent alias for FolderManagementContent.
 */
@Composable
fun FileManagementContent(
    currentFolder: FolderEntity?,
    subfolders: List<FolderEntity>,
    files: List<FileEntity>,
    breadcrumbs: List<BreadcrumbItem>,
    allFolders: List<FolderEntity>,
    searchQuery: String,
    isGridView: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onNavigateToSubfolder: (FolderEntity) -> Unit,
    onNavigateToBreadcrumb: (String?) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onRenameFolderClick: (FolderEntity) -> Unit,
    onDeleteFolderClick: (FolderEntity) -> Unit,
    onMoveFolderClick: (FolderEntity) -> Unit,
    onMoveFileClick: (FileEntity) -> Unit,
    onFileClick: (FileEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    FolderManagementContent(
        currentFolder = currentFolder,
        subfolders = subfolders,
        files = files,
        breadcrumbs = breadcrumbs,
        allFolders = allFolders,
        searchQuery = searchQuery,
        isGridView = isGridView,
        onSearchQueryChange = onSearchQueryChange,
        onToggleViewMode = onToggleViewMode,
        onNavigateToSubfolder = onNavigateToSubfolder,
        onNavigateToBreadcrumb = onNavigateToBreadcrumb,
        onNavigateUp = onNavigateUp,
        onCreateFolderClick = onCreateFolderClick,
        onRenameFolderClick = onRenameFolderClick,
        onDeleteFolderClick = onDeleteFolderClick,
        onMoveFolderClick = onMoveFolderClick,
        onMoveFileClick = onMoveFileClick,
        onFileClick = onFileClick,
        onBack = onBack,
        modifier = modifier
    )
}

/**
 * Core UI rendering for FolderManagementScreen and FileManagementScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagementContent(
    currentFolder: FolderEntity?,
    subfolders: List<FolderEntity>,
    files: List<FileEntity>,
    breadcrumbs: List<BreadcrumbItem>,
    allFolders: List<FolderEntity>,
    searchQuery: String,
    isGridView: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onNavigateToSubfolder: (FolderEntity) -> Unit,
    onNavigateToBreadcrumb: (String?) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onRenameFolderClick: (FolderEntity) -> Unit,
    onDeleteFolderClick: (FolderEntity) -> Unit,
    onMoveFolderClick: (FolderEntity) -> Unit,
    onMoveFileClick: (FileEntity) -> Unit,
    onFileClick: (FileEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentFolder?.name ?: "Folder Management",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BodySansFont,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentFolder == null) "Root Directory" else "Subfolder Directory",
                            fontSize = 11.sp,
                            fontFamily = NumericMonoFont,
                            color = colors.textDim
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("folder_mgmt_back_button")
                            .pressScale(0.92f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.text
                        )
                    }
                },
                actions = {
                    // Search toggle / clear button
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) {
                                onSearchQueryChange("")
                            }
                        },
                        modifier = Modifier
                            .testTag("folder_mgmt_search_toggle")
                            .pressScale(0.92f)
                    ) {
                        Icon(
                            imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = if (searchQuery.isNotEmpty()) "Clear search" else "Search in Directory",
                            tint = if (searchQuery.isNotEmpty()) colors.teal else colors.textDim
                        )
                    }

                    // Grid / List view toggle
                    IconButton(
                        onClick = onToggleViewMode,
                        modifier = Modifier
                            .testTag("folder_mgmt_view_toggle")
                            .pressScale(0.92f)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List",
                            tint = colors.textDim
                        )
                    }

                    // New Folder Button
                    IconButton(
                        onClick = onCreateFolderClick,
                        modifier = Modifier
                            .testTag("folder_mgmt_create_folder")
                            .pressScale(0.92f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Create Subfolder",
                            tint = colors.teal
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.text
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Prominent Real-time Search Bar at top of Screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("file_management_search_bar")
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search files & folders by name…",
                            color = colors.textFaint,
                            fontSize = 13.sp,
                            fontFamily = BodySansFont
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = if (searchQuery.isNotBlank()) colors.teal else colors.textDim,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.testTag("search_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = colors.textDim,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.line,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        cursorColor = colors.teal,
                        focusedContainerColor = colors.surfaceHi,
                        unfocusedContainerColor = colors.surfaceHi
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("folder_mgmt_search_input")
                )
            }

            // Real-time filter result indicator
            if (searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .border(1.dp, colors.line.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtered by \"$searchQuery\": ${subfolders.size} folder(s), ${files.size} file(s) found",
                        fontSize = 11.5.sp,
                        fontFamily = NumericMonoFont,
                        color = colors.teal
                    )
                    Text(
                        text = "Clear filter",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.danger,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onSearchQueryChange("") }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Breadcrumb navigation bar
            DirectoryBreadcrumbBar(
                breadcrumbs = breadcrumbs,
                onBreadcrumbClick = onNavigateToBreadcrumb,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .border(1.dp, colors.line.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // Current Directory Status Banner & Navigate Up Bar
            CurrentDirectoryInfoBar(
                currentFolder = currentFolder,
                subfolderCount = subfolders.size,
                fileCount = files.size,
                totalSizeBytes = files.sumOf { it.size },
                onNavigateUp = if (currentFolder != null) onNavigateUp else null,
                onCreateFolder = onCreateFolderClick
            )

            // Content Area: Empty State OR Items (List / Grid)
            if (subfolders.isEmpty() && files.isEmpty()) {
                EmptyDirectoryState(
                    isSearchActive = searchQuery.isNotBlank(),
                    onCreateFolder = onCreateFolderClick,
                    onClearSearch = { onSearchQueryChange("") },
                    modifier = Modifier.weight(1f)
                )
            } else if (isGridView) {
                DirectoryGridView(
                    subfolders = subfolders,
                    files = files,
                    onFolderClick = onNavigateToSubfolder,
                    onRenameFolder = onRenameFolderClick,
                    onDeleteFolder = onDeleteFolderClick,
                    onMoveFolder = onMoveFolderClick,
                    onFileClick = onFileClick,
                    onMoveFile = onMoveFileClick,
                    modifier = Modifier.weight(1f)
                )
            } else {
                DirectoryListView(
                    subfolders = subfolders,
                    files = files,
                    onFolderClick = onNavigateToSubfolder,
                    onRenameFolder = onRenameFolderClick,
                    onDeleteFolder = onDeleteFolderClick,
                    onMoveFolder = onMoveFolderClick,
                    onFileClick = onFileClick,
                    onMoveFile = onMoveFileClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Interactive Breadcrumb bar showing the path hierarchy.
 */
@Composable
private fun DirectoryBreadcrumbBar(
    breadcrumbs: List<BreadcrumbItem>,
    onBreadcrumbClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        breadcrumbs.forEachIndexed { index, item ->
            val isCurrent = index == breadcrumbs.lastIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrent) colors.surfaceHi else Color.Transparent)
                    .clickable { onBreadcrumbClick(item.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("breadcrumb_${item.title}")
            ) {
                if (index == 0) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (isCurrent) colors.teal else colors.textDim,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontFamily = BodySansFont,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) colors.text else colors.textDim
                )
            }

            if (!isCurrent) {
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

/**
 * Directory stats & Up navigation banner.
 */
@Composable
private fun CurrentDirectoryInfoBar(
    currentFolder: FolderEntity?,
    subfolderCount: Int,
    fileCount: Int,
    totalSizeBytes: Long,
    onNavigateUp: (() -> Unit)?,
    onCreateFolder: () -> Unit
) {
    val colors = LocalTeleVaultColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentFolder?.name ?: "Vault Root",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$subfolderCount folders",
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont,
                        color = colors.teal
                    )
                    Text("•", fontSize = 11.sp, color = colors.textFaint)
                    Text(
                        text = "$fileCount files",
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont,
                        color = colors.textDim
                    )
                    Text("•", fontSize = 11.sp, color = colors.textFaint)
                    Text(
                        text = ChecksumUtil.formatBytes(totalSizeBytes),
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont,
                        color = colors.textDim
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onNavigateUp != null) {
                    Button(
                        onClick = onNavigateUp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceHi,
                            contentColor = colors.text
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .testTag("folder_mgmt_up_button")
                            .pressScale(0.94f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Navigate Up",
                            tint = colors.teal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Up", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onCreateFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.teal.copy(alpha = 0.15f),
                        contentColor = colors.teal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .testTag("folder_mgmt_new_subfolder_btn")
                        .pressScale(0.94f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Add",
                        tint = colors.teal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * List presentation for directory contents.
 */
@Composable
private fun DirectoryListView(
    subfolders: List<FolderEntity>,
    files: List<FileEntity>,
    onFolderClick: (FolderEntity) -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onMoveFolder: (FolderEntity) -> Unit,
    onFileClick: (FileEntity) -> Unit,
    onMoveFile: (FileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Subfolders Section
        if (subfolders.isNotEmpty()) {
            item(key = "subfolders_heading") {
                Text(
                    text = "Subfolders (${subfolders.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    color = colors.textDim,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            items(subfolders, key = { it.id }) { folder ->
                FolderRowItem(
                    folder = folder,
                    onClick = { onFolderClick(folder) },
                    onRename = { onRenameFolder(folder) },
                    onDelete = { onDeleteFolder(folder) },
                    onMove = { onMoveFolder(folder) }
                )
            }
        }

        // Files Section
        if (files.isNotEmpty()) {
            item(key = "files_heading") {
                Text(
                    text = "Files in Directory (${files.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    color = colors.textDim,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            items(files, key = { it.id }) { file ->
                FileRowItem(
                    file = file,
                    onClick = { onFileClick(file) },
                    onMove = { onMoveFile(file) }
                )
            }
        }
    }
}

/**
 * Grid presentation for directory contents.
 */
@Composable
private fun DirectoryGridView(
    subfolders: List<FolderEntity>,
    files: List<FileEntity>,
    onFolderClick: (FolderEntity) -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onMoveFolder: (FolderEntity) -> Unit,
    onFileClick: (FileEntity) -> Unit,
    onMoveFile: (FileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Subfolders
        items(subfolders, key = { it.id }) { folder ->
            FolderGridItem(
                folder = folder,
                onClick = { onFolderClick(folder) },
                onRename = { onRenameFolder(folder) },
                onDelete = { onDeleteFolder(folder) },
                onMove = { onMoveFolder(folder) }
            )
        }

        // Files
        items(files, key = { it.id }) { file ->
            FileGridItem(
                file = file,
                onClick = { onFileClick(file) },
                onMove = { onMoveFile(file) }
            )
        }
    }
}

@Composable
private fun FolderRowItem(
    folder: FolderEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    var showMenu by remember { mutableStateOf(false) }
    val formattedDate = remember(folder.createdDate) { ChecksumUtil.formatDate(folder.createdDate) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("folder_item_${folder.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(FileColorFolder.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = FileColorFolder,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
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
                    .testTag("folder_menu_${folder.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Folder options",
                    tint = colors.textDim,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open Subfolder", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move to Directory", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onMove()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp)) },
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
private fun FileRowItem(
    file: FileEntity,
    onClick: () -> Unit,
    onMove: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    var showMenu by remember { mutableStateOf(false) }
    val formattedSize = remember(file.size) { ChecksumUtil.formatBytes(file.size) }
    val formattedDate = remember(file.uploadDate) { ChecksumUtil.formatDate(file.uploadDate) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("file_item_${file.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val categoryColor = remember(file.mimeType, file.name) { resolveCategoryColor(file.mimeType, file.name) }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(categoryColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = "File",
                tint = categoryColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
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
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedSize,
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    color = colors.textDim
                )
                Text(" • ", fontSize = 11.sp, color = colors.textFaint)
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    color = colors.textFaint
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("file_menu_${file.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File options",
                    tint = colors.textDim,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Inspect Details", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move to Directory", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onMove()
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderGridItem(
    folder: FolderEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FileColorFolder.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = FileColorFolder,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Open", fontSize = 13.sp) }, onClick = { showMenu = false; onClick() })
                        DropdownMenuItem(text = { Text("Rename", fontSize = 13.sp) }, onClick = { showMenu = false; onRename() })
                        DropdownMenuItem(text = { Text("Move", fontSize = 13.sp) }, onClick = { showMenu = false; onMove() })
                        DropdownMenuItem(text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 13.sp) }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = folder.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BodySansFont,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Directory",
                fontSize = 10.sp,
                fontFamily = NumericMonoFont,
                color = colors.textFaint
            )
        }
    }
}

@Composable
private fun FileGridItem(
    file: FileEntity,
    onClick: () -> Unit,
    onMove: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val categoryColor = remember(file.mimeType, file.name) { resolveCategoryColor(file.mimeType, file.name) }
    val formattedSize = remember(file.size) { ChecksumUtil.formatBytes(file.size) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Details", fontSize = 13.sp) }, onClick = { showMenu = false; onClick() })
                        DropdownMenuItem(text = { Text("Move", fontSize = 13.sp) }, onClick = { showMenu = false; onMove() })
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = file.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BodySansFont,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formattedSize,
                fontSize = 10.sp,
                fontFamily = NumericMonoFont,
                color = colors.textDim
            )
        }
    }
}

/**
 * Empty directory illustration and action.
 */
@Composable
private fun EmptyDirectoryState(
    isSearchActive: Boolean,
    onCreateFolder: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceHi)
                    .border(1.dp, colors.line, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Search else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isSearchActive) "No items match your filter" else "This directory is empty",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BodySansFont,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSearchActive) "Try searching with a different term" else "Organize your Telegram storage by creating subfolders or moving files here.",
                fontSize = 12.sp,
                color = colors.textDim,
                fontFamily = BodySansFont,
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (isSearchActive) {
                Button(
                    onClick = onClearSearch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceHi,
                        contentColor = colors.text
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear Filter", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onCreateFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.teal,
                        contentColor = Color(0xFF05060A)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Subfolder", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Move Folder Dialog.
 */
@Composable
private fun MoveFolderDialog(
    folder: FolderEntity,
    candidateFolders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onSelectDestination: (newParentId: String?) -> Unit
) {
    val colors = LocalTeleVaultColors.current

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = colors.teal, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Move \"${folder.name}\"",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    color = colors.text
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select a destination directory for this folder:",
                fontSize = 12.sp,
                color = colors.textDim,
                fontFamily = BodySansFont
            )
            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                // Root option
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (folder.parentFolderId == null) colors.surfaceHi else Color.Transparent)
                            .clickable { onSelectDestination(null) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = colors.teal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vault Root (No Parent)", fontSize = 13.sp, color = colors.text, fontWeight = FontWeight.SemiBold)
                    }
                }

                items(candidateFolders) { destFolder ->
                    val isCurrentParent = folder.parentFolderId == destFolder.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrentParent) colors.surfaceHi else Color.Transparent)
                            .clickable { onSelectDestination(destFolder.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(destFolder.name, fontSize = 13.sp, color = colors.text, fontWeight = FontWeight.Medium)
                        if (isCurrentParent) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text("Current", fontSize = 10.sp, color = colors.textFaint, fontFamily = NumericMonoFont)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textDim)
                }
            }
        }
    }
}

/**
 * Delete Folder confirmation dialog.
 */
@Composable
private fun DeleteFolderConfirmationDialog(
    folderName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = LocalTeleVaultColors.current

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Delete \"$folderName\"?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BodySansFont,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This will remove the folder record from your vault. Subfolders will be deleted.",
                fontSize = 12.sp,
                color = colors.textDim,
                fontFamily = BodySansFont
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textDim)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun resolveCategoryColor(mimeType: String?, fileName: String): Color {
    return when (classifyFileCategory(mimeType, fileName)) {
        com.example.domain.model.StorageCategory.DOCUMENTS -> FileColorDoc
        com.example.domain.model.StorageCategory.MEDIA -> {
            val name = fileName.lowercase()
            if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov")) FileColorVideo
            else if (name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav")) FileColorAudio
            else FileColorImage
        }
        com.example.domain.model.StorageCategory.OTHER -> {
            val name = fileName.lowercase()
            if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".7z")) {
                FileColorArchive
            } else {
                FileColorGeneric
            }
        }
    }
}
