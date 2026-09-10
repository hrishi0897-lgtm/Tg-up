package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.domain.ChecksumUtil
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailSheet(
    file: FileEntity,
    chunks: List<ChunkEntity> = emptyList(),
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Hidden debug mode unlocked by tapping the top-bar title/icon 7 times
    var debugTapCount by remember { mutableIntStateOf(0) }
    val isDebugMode = debugTapCount >= 7

    val localTarget = file.localUri ?: file.localPath
    val isDownloadedLocally = remember(localTarget) {
        if (localTarget.isNullOrBlank()) false
        else if (localTarget.startsWith("content://")) {
            try {
                context.contentResolver.openInputStream(Uri.parse(localTarget))?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        } else {
            File(localTarget).exists()
        }
    }

    val shareableUri: Uri? = remember(localTarget, isDownloadedLocally) {
        if (!isDownloadedLocally || localTarget.isNullOrBlank()) null
        else if (localTarget.startsWith("content://")) {
            Uri.parse(localTarget)
        } else {
            val localFile = File(localTarget)
            if (localFile.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localFile
                )
            } else null
        }
    }

    val isMedia = remember(file.mimeType) {
        file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.line)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .padding(bottom = 36.dp)
        ) {
            // Header: Preview / Thumbnail / Icon + File details
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.bg),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Preview or Icon: If it's an image/video with local content, render real thumbnail
                    if (isMedia && localTarget != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceHi)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(shareableUri ?: localTarget)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = file.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    debugTapCount++
                                    if (debugTapCount == 7) {
                                        Toast.makeText(context, "Debug inspector enabled", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            FileIcon(mimeType = file.mimeType, size = 48.dp)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // File Name
                    Text(
                        text = file.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                debugTapCount++
                                if (debugTapCount == 7) {
                                    Toast.makeText(context, "Debug inspector enabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Meta Row: Size and Upload Date (strictly NO chunk count or message IDs)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = ChecksumUtil.formatFileSize(file.size),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textDim
                        )
                        Text(
                            text = " · ",
                            fontSize = 13.sp,
                            color = colors.textFaint
                        )
                        Text(
                            text = ChecksumUtil.formatDate(file.uploadDate),
                            fontSize = 13.sp,
                            color = colors.textDim
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Verification Status: Clean badge with "Verified" checkmark when checksum passed
                    if (file.checksum.isNotBlank() && file.status == FileStatus.COMPLETED) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(StatusSuccess.copy(alpha = 0.12f))
                                .border(1.dp, StatusSuccess.copy(alpha = 0.35f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = StatusSuccess,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Verified",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusSuccess
                            )
                        }
                    } else if (file.status == FileStatus.COMPLETED) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.violet.copy(alpha = 0.12f))
                                .border(1.dp, colors.violet.copy(alpha = 0.35f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Archived",
                                tint = colors.violet,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Archived in Vault",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.violet
                            )
                        }
                    }
                }
            }

            // Failure Banner (if failed)
            if (file.status == FileStatus.FAILED && !file.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusError.copy(alpha = 0.12f))
                        .border(1.dp, StatusError.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = file.errorMessage,
                        color = StatusError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Local Availability Badge (if on device)
            if (isDownloadedLocally) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusSuccess.copy(alpha = 0.12f))
                        .border(1.dp, StatusSuccess.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = StatusSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloaded & ready on this device",
                        color = StatusSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Actions: Download, Share, Rename, Move to folder, Delete
            Text(
                text = "Actions",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textDim,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.bg),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Action 1: Download / Open File
                    if (isDownloadedLocally) {
                        ActionItem(
                            icon = Icons.Default.OpenInNew,
                            label = "Open File",
                            iconTint = colors.violet,
                            labelColor = colors.text,
                            onClick = {
                                if (shareableUri != null) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(shareableUri, file.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Open file"))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "File is not available on device", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        ActionItem(
                            icon = Icons.Default.Download,
                            label = "Download",
                            iconTint = colors.violet,
                            labelColor = colors.text,
                            onClick = {
                                onDownload()
                                onDismiss()
                            }
                        )
                    }

                    HorizontalDivider(color = colors.line, thickness = 0.5.dp)

                    // Action 2: Share
                    ActionItem(
                        icon = Icons.Default.Share,
                        label = "Share",
                        iconTint = colors.violet,
                        labelColor = colors.text,
                        onClick = {
                            onShare()
                        }
                    )

                    HorizontalDivider(color = colors.line, thickness = 0.5.dp)

                    // Action 3: Rename
                    ActionItem(
                        icon = Icons.Default.Edit,
                        label = "Rename",
                        iconTint = colors.text,
                        labelColor = colors.text,
                        onClick = {
                            onDismiss()
                            onRename()
                        }
                    )

                    HorizontalDivider(color = colors.line, thickness = 0.5.dp)

                    // Action 4: Move to folder
                    ActionItem(
                        icon = Icons.Default.DriveFileMove,
                        label = "Move to folder",
                        iconTint = colors.text,
                        labelColor = colors.text,
                        onClick = {
                            onDismiss()
                            onMove()
                        }
                    )

                    HorizontalDivider(color = colors.line, thickness = 0.5.dp)

                    // Action 5: Delete
                    ActionItem(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        iconTint = StatusError,
                        labelColor = StatusError,
                        onClick = {
                            onDismiss()
                            onDelete()
                        }
                    )
                }
            }

            // Gated Developer / Debug Mode (Only shown if unlocked by tapping file icon/name 7 times)
            AnimatedVisibility(visible = isDebugMode) {
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceHi),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.violet.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = colors.violet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "DEBUG: INTERNAL CHUNKS (${chunks.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.violet,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Text(
                                    text = if (file.manifestMessageId != null) "Manifest Msg #${file.manifestMessageId}" else "Manifest Pending",
                                    fontSize = 10.sp,
                                    color = colors.textFaint
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Checksum: ${file.checksum}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = colors.textDim,
                                lineHeight = 14.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (chunks.isEmpty()) {
                                Text(
                                    text = "No chunk records stored in local database.",
                                    fontSize = 11.sp,
                                    color = colors.textFaint
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    chunks.take(8).forEach { chunk ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.bg)
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Part ${chunk.chunkIndex + 1} · Msg #${chunk.telegramMessageId ?: "-"}",
                                                fontSize = 11.sp,
                                                color = colors.text
                                            )
                                            Text(
                                                text = ChecksumUtil.formatFileSize(chunk.size),
                                                fontSize = 11.sp,
                                                color = colors.textFaint
                                            )
                                        }
                                    }
                                    if (chunks.size > 8) {
                                        Text(
                                            text = "...and ${chunks.size - 8} more chunks",
                                            fontSize = 10.sp,
                                            color = colors.textFaint
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    labelColor: Color = LocalTeleVaultColors.current.text,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
    }
}
