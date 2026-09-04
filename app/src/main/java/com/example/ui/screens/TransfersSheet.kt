package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FileStatus
import com.example.domain.ChecksumUtil
import com.example.domain.model.TransferProgress
import com.example.ui.theme.OledBlack
import com.example.ui.theme.OledBorder
import com.example.ui.theme.OledCard
import com.example.ui.theme.OledSurface
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersSheet(
    transfers: List<TransferProgress>,
    onDismiss: () -> Unit,
    onPause: (fileId: String) -> Unit,
    onResume: (fileId: String, isUpload: Boolean) -> Unit,
    onCancel: (fileId: String) -> Unit,
    onRetry: (fileId: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledBlack,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(OledBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transfer Queue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${transfers.size} items",
                    fontSize = 12.sp,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (transfers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "All Transfers Completed",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No active uploads or downloads.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(transfers, key = { it.fileId }) { transfer ->
                        TransferItemCard(
                            transfer = transfer,
                            onPause = { onPause(transfer.fileId) },
                            onResume = { onResume(transfer.fileId, transfer.isUpload) },
                            onCancel = { onCancel(transfer.fileId) },
                            onRetry = { onRetry(transfer.fileId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferItemCard(
    transfer: TransferProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OledCard),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Icon(
                    imageVector = if (transfer.isUpload) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = if (transfer.status == FileStatus.FAILED) StatusError else TelegramBlue,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (transfer.status) {
                            FileStatus.UPLOADING -> "Uploading chunk ${transfer.currentChunk}/${transfer.totalChunks} · ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}"
                            FileStatus.DOWNLOADING -> "Downloading chunk ${transfer.currentChunk}/${transfer.totalChunks} · ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}"
                            FileStatus.PAUSED -> "Paused (Chunk ${transfer.currentChunk}/${transfer.totalChunks})"
                            FileStatus.FAILED -> "Failed: ${transfer.errorMessage ?: "Transfer error"}"
                            FileStatus.COMPLETED -> "Verified & Stored"
                            FileStatus.PENDING -> "Queued"
                        },
                        fontSize = 11.sp,
                        color = if (transfer.status == FileStatus.FAILED) StatusError else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                when (transfer.status) {
                    FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    FileStatus.PAUSED -> {
                        IconButton(onClick = onResume, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = TelegramBlue, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    FileStatus.FAILED -> {
                        IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Replay, contentDescription = "Retry", tint = TelegramBlue, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    FileStatus.COMPLETED -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(18.dp))
                    }
                    FileStatus.PENDING -> {
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Progress bar
            if (transfer.status == FileStatus.UPLOADING || transfer.status == FileStatus.DOWNLOADING || transfer.status == FileStatus.PAUSED) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { transfer.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = TelegramBlue,
                    trackColor = OledSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(transfer.progressFraction * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = TelegramBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${ChecksumUtil.formatFileSize(transfer.bytesTransferred)} / ${ChecksumUtil.formatFileSize(transfer.totalBytes)}",
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}
