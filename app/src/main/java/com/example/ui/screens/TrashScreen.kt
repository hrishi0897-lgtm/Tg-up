package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FileEntity
import com.example.domain.ChecksumUtil
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.DarkTeleVaultColors
import com.example.ui.theme.DisplaySerifFont
import com.example.ui.theme.EmptyHeadlineStyle
import com.example.ui.theme.FileColorArchive
import com.example.ui.theme.FileColorAudio
import com.example.ui.theme.FileColorDoc
import com.example.ui.theme.FileColorGeneric
import com.example.ui.theme.FileColorImage
import com.example.ui.theme.FileColorVideo
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont

@Composable
fun TrashScreen(
    trashFiles: List<FileEntity>,
    onBack: () -> Unit,
    onRestoreFile: (String) -> Unit,
    onPermanentlyDeleteFile: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onBulkRestore: ((List<String>) -> Unit)? = null,
    onBulkPermanentlyDelete: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalTeleVaultColors.current ?: DarkTeleVaultColors

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var fileToDeletePermanently by remember { mutableStateOf<FileEntity?>(null) }
    var selectedTrashIds by remember { mutableStateOf(setOf<String>()) }

    val isSelectionMode = selectedTrashIds.isNotEmpty()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = colors.bg,
        topBar = {
            if (isSelectionMode) {
                TrashSelectionTopBar(
                    selectedCount = selectedTrashIds.size,
                    totalCount = trashFiles.size,
                    onClearSelection = { selectedTrashIds = emptySet() },
                    onSelectAll = {
                        selectedTrashIds = if (selectedTrashIds.size == trashFiles.size) {
                            emptySet()
                        } else {
                            trashFiles.map { it.id }.toSet()
                        }
                    },
                    onBulkRestore = {
                        val ids = selectedTrashIds.toList()
                        selectedTrashIds = emptySet()
                        if (onBulkRestore != null) {
                            onBulkRestore(ids)
                        } else {
                            ids.forEach { onRestoreFile(it) }
                        }
                    },
                    onBulkDeleteForever = { showBulkDeleteDialog = true },
                    colors = colors
                )
            } else {
                TrashTopBar(
                    itemCount = trashFiles.size,
                    onBack = onBack,
                    onEmptyTrashClick = { showEmptyTrashDialog = true },
                    colors = colors
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // 30-day retention notice banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.line)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.violet.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Retention Info",
                            tint = colors.violet,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "30-Day Auto-Purge",
                            fontFamily = BodySansFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = colors.text
                        )
                        Text(
                            text = "Files in the Recycle Bin are permanently deleted after 30 days. Restoring returns them to your vault.",
                            fontFamily = BodySansFont,
                            fontSize = 12.sp,
                            color = colors.textDim,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            if (trashFiles.isEmpty()) {
                TrashEmptyState(colors = colors)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("trash_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(trashFiles, key = { it.id }) { file ->
                        val isSelected = selectedTrashIds.contains(file.id)
                        TrashItemCard(
                            file = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onToggleSelect = {
                                selectedTrashIds = if (isSelected) {
                                    selectedTrashIds - file.id
                                } else {
                                    selectedTrashIds + file.id
                                }
                            },
                            onLongClick = {
                                selectedTrashIds = selectedTrashIds + file.id
                            },
                            onRestore = { onRestoreFile(file.id) },
                            onDeleteForever = { fileToDeletePermanently = file },
                            colors = colors
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Confirmation dialog: Empty Trash
    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            containerColor = colors.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Empty Recycle Bin?",
                    fontFamily = DisplaySerifFont,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Text(
                    text = "All ${trashFiles.size} items in the Recycle Bin will be permanently deleted from Telegram and your device. This cannot be undone.",
                    fontFamily = BodySansFont,
                    fontSize = 14.sp,
                    color = colors.textDim
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashDialog = false
                        selectedTrashIds = emptySet()
                        onEmptyTrash()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_empty_trash_button")
                ) {
                    Text("Empty Everything", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Cancel", color = colors.textDim)
                }
            }
        )
    }

    // Confirmation dialog: Bulk Permanently Delete
    if (showBulkDeleteDialog && selectedTrashIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            containerColor = colors.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Permanently Delete ${selectedTrashIds.size} Item${if (selectedTrashIds.size > 1) "s" else ""}?",
                    fontFamily = DisplaySerifFont,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Text(
                    text = "The selected ${selectedTrashIds.size} file(s) will be permanently erased from Telegram and your local vault. This action cannot be reversed.",
                    fontFamily = BodySansFont,
                    fontSize = 14.sp,
                    color = colors.textDim
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedTrashIds.toList()
                        showBulkDeleteDialog = false
                        selectedTrashIds = emptySet()
                        if (onBulkPermanentlyDelete != null) {
                            onBulkPermanentlyDelete(ids)
                        } else {
                            ids.forEach { onPermanentlyDeleteFile(it) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_bulk_delete_trash_button")
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancel", color = colors.textDim)
                }
            }
        )
    }

    // Confirmation dialog: Permanently delete single file
    fileToDeletePermanently?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDeletePermanently = null },
            containerColor = colors.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Permanently Delete?",
                    fontFamily = DisplaySerifFont,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            },
            text = {
                Text(
                    text = "\"${file.name}\" will be permanently deleted from Telegram and your device. This action cannot be undone.",
                    fontFamily = BodySansFont,
                    fontSize = 14.sp,
                    color = colors.textDim
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = file.id
                        fileToDeletePermanently = null
                        onPermanentlyDeleteFile(id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_forever_button")
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDeletePermanently = null }) {
                    Text("Cancel", color = colors.textDim)
                }
            }
        )
    }
}

@Composable
private fun TrashSelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onBulkRestore: () -> Unit,
    onBulkDeleteForever: () -> Unit,
    colors: com.example.ui.theme.TeleVaultColors
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.line, CircleShape)
                    .testTag("trash_cancel_selection_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Selection",
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "$selectedCount selected",
                fontFamily = BodySansFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.text
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onSelectAll,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.line, CircleShape)
                    .testTag("trash_select_all_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = if (selectedCount == totalCount) "Deselect All" else "Select All",
                    tint = if (selectedCount == totalCount) colors.teal else colors.textDim,
                    modifier = Modifier.size(18.dp)
                )
            }

            Button(
                onClick = onBulkRestore,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.violet.copy(alpha = 0.2f),
                    contentColor = colors.violet
                ),
                shape = RoundedCornerShape(10.dp),
                elevation = null,
                modifier = Modifier.testTag("bulk_restore_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = colors.violet,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onBulkDeleteForever,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.danger.copy(alpha = 0.2f),
                    contentColor = colors.danger
                ),
                shape = RoundedCornerShape(10.dp),
                elevation = null,
                modifier = Modifier.testTag("bulk_delete_forever_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TrashTopBar(
    itemCount: Int,
    onBack: () -> Unit,
    onEmptyTrashClick: () -> Unit,
    colors: com.example.ui.theme.TeleVaultColors
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
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.line, CircleShape)
                    .testTag("trash_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Recycle Bin",
                        fontFamily = DisplaySerifFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.text
                    )
                    if (itemCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(colors.amber.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$itemCount",
                                fontFamily = NumericMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = colors.amber
                            )
                        }
                    }
                }
                Text(
                    text = "Soft-deleted vault items",
                    fontFamily = BodySansFont,
                    fontSize = 12.sp,
                    color = colors.textDim
                )
            }
        }

        if (itemCount > 0) {
            OutlinedButton(
                onClick = onEmptyTrashClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.danger
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.danger.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_trash_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Empty",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TrashEmptyState(colors: com.example.ui.theme.TeleVaultColors) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.line, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = colors.textDim,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = "Recycle Bin is empty",
                style = EmptyHeadlineStyle,
                color = colors.text
            )

            Text(
                text = "Files deleted from your vault will stay here for 30 days before being permanently removed.",
                fontFamily = BodySansFont,
                fontSize = 13.sp,
                color = colors.textDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashItemCard(
    file: FileEntity,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    colors: com.example.ui.theme.TeleVaultColors
) {
    val (icon, tint) = getTrashFileIconAndColor(file)

    val daysRemaining = remember(file.deletedAt) {
        val deletedTime = file.deletedAt ?: System.currentTimeMillis()
        val elapsedMs = System.currentTimeMillis() - deletedTime
        val elapsedDays = (elapsedMs / (1000L * 60L * 60L * 24L)).toInt()
        (30 - elapsedDays).coerceAtLeast(0)
    }

    val cardBorder = if (isSelected) {
        androidx.compose.foundation.BorderStroke(1.5.dp, colors.violet)
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, colors.line)
    }

    val cardBackground = if (isSelected) {
        colors.violet.copy(alpha = 0.08f)
    } else {
        colors.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trash_item_${file.id}")
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect()
                    }
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top row: Checkbox (if selection mode) + Icon + Name + Days left badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) colors.violet else colors.textFaint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        fontFamily = BodySansFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
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
                            text = ChecksumUtil.formatFileSize(file.size),
                            fontFamily = NumericMonoFont,
                            fontSize = 11.5.sp,
                            color = colors.textDim
                        )
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = colors.textFaint
                        )
                        Text(
                            text = if (daysRemaining == 1) "1 day left" else "$daysRemaining days left",
                            fontFamily = BodySansFont,
                            fontSize = 11.5.sp,
                            color = if (daysRemaining <= 3) colors.danger else colors.amber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom action row: Restore & Delete Permanently (only when not in selection mode)
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDeleteForever,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colors.danger
                        ),
                        modifier = Modifier.testTag("delete_forever_${file.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Delete",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onRestore,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.violet.copy(alpha = 0.2f),
                            contentColor = colors.violet
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = null,
                        modifier = Modifier.testTag("restore_file_${file.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = colors.violet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Restore",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun getTrashFileIconAndColor(file: FileEntity): Pair<ImageVector, Color> {
    val mime = file.mimeType.lowercase()
    val name = file.name.lowercase()
    return when {
        mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif") ->
            Icons.Default.Image to FileColorImage
        mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".avi") ->
            Icons.Default.Movie to FileColorVideo
        mime.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".ogg") || name.endsWith(".wav") || name.endsWith(".m4a") ->
            Icons.Default.MusicNote to FileColorAudio
        mime.contains("zip") || mime.contains("tar") || mime.contains("rar") || mime.contains("7z") || name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ->
            Icons.Default.FolderZip to FileColorArchive
        mime.contains("pdf") || mime.contains("document") || mime.contains("text") || name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".txt") ->
            Icons.Default.Description to FileColorDoc
        else ->
            Icons.Default.Description to FileColorGeneric
    }
}
