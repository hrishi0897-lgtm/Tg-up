package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FolderEntity
import com.example.domain.ChecksumUtil
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont
import com.example.ui.theme.pressScale

data class BatchUploadItem(
    val uri: Uri,
    val name: String,
    val size: Long
)

data class PendingBatchUpload(
    val items: List<BatchUploadItem>,
    val defaultFolderId: String? = null
)

@Composable
fun BatchUploadFolderDialog(
    items: List<BatchUploadItem>,
    folders: List<FolderEntity>,
    initialFolderId: String? = null,
    onDismiss: () -> Unit,
    onConfirmUpload: (targetFolderId: String?) -> Unit
) {
    val colors = LocalTeleVaultColors.current
    var selectedFolderId by remember { mutableStateOf<String?>(initialFolderId) }

    val totalSize = remember(items) { items.sumOf { it.size } }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("batch_upload_dialog"),
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.teal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Upload ${items.size} File${if (items.size > 1) "s" else ""}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodySansFont,
                        color = colors.text
                    )
                    Text(
                        text = "${items.size} items • ${ChecksumUtil.formatBytes(totalSize)}",
                        fontSize = 12.sp,
                        fontFamily = NumericMonoFont,
                        color = colors.textDim
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Section 1: Selected files preview list
                Text(
                    text = "FILES IN QUEUE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    color = colors.textFaint,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceHi)
                        .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(items) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.name,
                                    fontSize = 12.sp,
                                    fontFamily = BodySansFont,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ChecksumUtil.formatBytes(item.size),
                                    fontSize = 11.sp,
                                    fontFamily = NumericMonoFont,
                                    color = colors.textDim
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Destination Folder Selection
                Text(
                    text = "DESTINATION FOLDER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    color = colors.textFaint,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceHi)
                        .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                ) {
                    LazyColumn {
                        // Root folder option
                        item {
                            val isSelected = selectedFolderId == null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolderId = null }
                                    .background(if (isSelected) colors.teal.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("folder_option_root"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.teal else colors.textDim,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Vault Root",
                                        fontSize = 13.sp,
                                        fontFamily = BodySansFont,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) colors.teal else colors.text
                                    )
                                    Text(
                                        text = "Primary vault directory",
                                        fontSize = 11.sp,
                                        color = colors.textFaint
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = colors.teal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Subfolders
                        items(folders, key = { it.id }) { folder ->
                            HorizontalDivider(color = colors.line, thickness = 0.5.dp)
                            val isSelected = selectedFolderId == folder.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolderId = folder.id }
                                    .background(if (isSelected) colors.teal.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("folder_option_${folder.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.teal else colors.textDim,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = folder.name,
                                    fontSize = 13.sp,
                                    fontFamily = BodySansFont,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) colors.teal else colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = colors.teal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmUpload(selectedFolderId) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.teal,
                    contentColor = Color(0xFF05060A)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .pressScale(0.95f)
                    .testTag("btn_confirm_batch_upload")
            ) {
                Text(
                    text = "Start Upload",
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_batch_upload")
            ) {
                Text(
                    text = "Cancel",
                    color = colors.textDim,
                    fontFamily = BodySansFont,
                    fontSize = 13.sp
                )
            }
        }
    )
}
