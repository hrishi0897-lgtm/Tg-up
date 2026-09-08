package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FolderEntity
import com.example.domain.ChecksumUtil
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.AppBackgroundOuter
import com.example.ui.theme.AppSurface
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.BorderDivider
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.NumericMonoFont
import com.example.ui.theme.StatusError
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextDimmed
import com.example.ui.theme.TextFaint
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VaultAnimatedDialog
import com.example.ui.theme.pressScale

@Composable
private fun AnimatedDialogCard(
    onDismiss: () -> Unit,
    icon: @Composable (() -> Unit)? = null,
    title: String,
    content: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    VaultAnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDivider),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp)
                    ) {
                        icon()
                    }
                }
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont
                )
                Spacer(modifier = Modifier.height(12.dp))
                content()
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AnimatedDialogCard(
        onDismiss = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentViolet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = AccentViolet,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = "New Folder",
        content = {
            Column {
                Text(
                    text = "Enter a name for your new folder:",
                    color = TextDimmed,
                    fontSize = 13.sp,
                    fontFamily = BodySansFont
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("e.g. Work Documents", color = TextFaint, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderDivider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentViolet,
                        focusedContainerColor = AppSurface,
                        unfocusedContainerColor = AppSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = AppBackgroundOuter
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Create", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Cancel", color = TextDimmed)
            }
        }
    )
}

@Composable
fun RenameFolderDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var folderName by remember { mutableStateOf(currentName) }

    AnimatedDialogCard(
        onDismiss = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentViolet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = AccentViolet,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = "Rename Folder",
        content = {
            Column {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderDivider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentViolet,
                        focusedContainerColor = AppSurface,
                        unfocusedContainerColor = AppSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = AppBackgroundOuter
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Cancel", color = TextDimmed)
            }
        }
    )
}

@Composable
fun MoveFileDialog(
    fileName: String,
    folders: List<FolderEntity>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onSelectDestination: (folderId: String?) -> Unit
) {
    AnimatedDialogCard(
        onDismiss = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentViolet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DriveFileMove,
                    contentDescription = null,
                    tint = AccentViolet,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = "Move \"$fileName\"",
        content = {
            Column {
                Text(
                    text = "Select destination:",
                    color = TextDimmed,
                    fontSize = 13.sp,
                    fontFamily = BodySansFont
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    // Root folder option
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (currentFolderId == null) SurfaceCardElevated else Color.Transparent)
                                .clickable { onSelectDestination(null) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Vault Root (No folder)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    items(folders, key = { it.id }) { folder ->
                        val isCurrent = folder.id == currentFolderId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) SurfaceCardElevated else Color.Transparent)
                                .clickable { onSelectDestination(folder.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(folder.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Cancel", color = TextDimmed)
            }
        }
    )
}

@Composable
fun LargeFileConfirmationDialog(
    fileName: String,
    fileSize: Long,
    estimatedChunks: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val formattedSize = ChecksumUtil.formatBytes(fileSize)
    AnimatedDialogCard(
        onDismiss = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x26FFB300)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = "Large Transfer Warning",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "You are about to upload a large file:",
                    color = TextDimmed,
                    fontSize = 13.sp,
                    fontFamily = BodySansFont
                )
                Text(
                    text = fileName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BodySansFont
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCardElevated)
                        .border(1.dp, BorderDivider, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Size", fontSize = 11.sp, color = TextDimmed)
                        Text(formattedSize, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NumericMonoFont, color = AccentViolet)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Telegram Chunks", fontSize = 11.sp, color = TextDimmed)
                        Text("~$estimatedChunks chunks", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NumericMonoFont, color = TextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "This file will be split into $estimatedChunks 18MB chunks and uploaded sequentially. Keep the app active or let the background service complete the transfer.",
                    color = TextFaint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = BodySansFont
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = AppBackgroundOuter
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Start Upload", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Cancel", color = TextDimmed)
            }
        }
    )
}

@Composable
fun RenameFileDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var fileName by remember { mutableStateOf(currentName) }

    AnimatedDialogCard(
        onDismiss = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentViolet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = AccentViolet,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = "Rename File",
        content = {
            Column {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderDivider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentViolet,
                        focusedContainerColor = AppSurface,
                        unfocusedContainerColor = AppSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fileName) },
                enabled = fileName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = AppBackgroundOuter
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Cancel", color = TextDimmed)
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    title: String = "Delete Item",
    itemName: String,
    warningText: String = "This will permanently delete this item from your Telegram vault and remove all chunks. This cannot be undone.",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AnimatedDialogCard(
        onDismiss = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StatusError.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusError,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = title,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Are you sure you want to delete:",
                    color = TextDimmed,
                    fontSize = 13.sp,
                    fontFamily = BodySansFont
                )
                Text(
                    text = itemName,
                    color = TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BodySansFont
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = warningText,
                    color = TextFaint,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = BodySansFont
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusError,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .pressScale(0.93f)
                    .testTag("btn_confirm_delete")
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pressScale(0.93f)
            ) {
                Text("Cancel", color = TextDimmed)
            }
        }
    )
}
