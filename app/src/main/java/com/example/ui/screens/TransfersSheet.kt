package com.example.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

// Pure OLED Palette
private val OledBlack = Color(0xFF000000)
private val ElectricCyan = Color(0xFF00E5FF)
private val TextWhite = Color(0xFFFFFFFF)
private val TextDimmedDone = Color(0xFFA0A0A0)
private val TextMutedGrey = Color(0xFF8E8E93)
private val StateMutedGreen = Color(0xFF4CAF50)
private val StateRed = Color(0xFFFF5252)
private val StateGreyscale = Color(0xFF666666)
private val TrackDark = Color(0xFF141414)
private val DividerLowAlpha = Color(0x1AFFFFFF)

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
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OledBlack)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: "Transfer Queue" left-aligned, count right-aligned in muted grey
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transfer Queue",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = "${transfers.size} items",
                    fontSize = 12.sp,
                    color = TextMutedGrey
                )
            }

            // Overall Batch Progress (when multiple items in queue):
            // Slim accent-colored bar on pure black track, small non-dominant percentage/count
            if (transfers.size > 1) {
                val completedCount = transfers.count { it.status == FileStatus.COMPLETED }
                val totalCount = transfers.size
                val percent = if (totalCount > 0) (completedCount * 100) / totalCount else 0
                val animatedBatchProgress by animateFloatAsState(
                    targetValue = (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
                    label = "batch_progress_anim"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overall Batch Progress",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextMutedGrey
                        )
                        Text(
                            text = "$completedCount of $totalCount · $percent%",
                            fontSize = 11.sp,
                            color = TextMutedGrey
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { animatedBatchProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = ElectricCyan,
                        trackColor = TrackDark
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DividerLowAlpha, thickness = 1.dp)
            } else {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DividerLowAlpha, thickness = 1.dp)
            }

            if (transfers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = StateMutedGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "All Transfers Completed",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Queue clear",
                            fontSize = 12.sp,
                            color = TextMutedGrey
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(transfers, key = { _, item -> item.fileId }) { index, transfer ->
                        TransferRowItem(
                            transfer = transfer,
                            onPause = { onPause(transfer.fileId) },
                            onResume = { onResume(transfer.fileId, transfer.isUpload) },
                            onCancel = { onCancel(transfer.fileId) },
                            onRetry = { onRetry(transfer.fileId) }
                        )
                        if (index < transfers.size - 1) {
                            HorizontalDivider(color = DividerLowAlpha, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferRowItem(
    transfer: TransferProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val isDone = transfer.status == FileStatus.COMPLETED
    val isFailed = transfer.status == FileStatus.FAILED
    val isQueued = transfer.status == FileStatus.PENDING
    val isActive = transfer.status == FileStatus.UPLOADING || transfer.status == FileStatus.DOWNLOADING || transfer.status == FileStatus.PAUSED

    val animatedProgress by animateFloatAsState(
        targetValue = transfer.progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "sheet_progress_${transfer.fileId}"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OledBlack)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: small status icon (cloud-up for queued/uploading, checkmark for done, alert for failed)
        // Icon color reflects state: queued = greyscale, active = electric cyan, done = muted green, failed = red
        Icon(
            imageVector = when {
                isDone -> Icons.Default.Check
                isFailed -> Icons.Default.Error
                !transfer.isUpload -> Icons.Default.CloudDownload
                else -> Icons.Default.CloudUpload
            },
            contentDescription = null,
            tint = when {
                isDone -> StateMutedGreen
                isFailed -> StateRed
                isQueued -> StateGreyscale
                else -> ElectricCyan
            },
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Center column: Filename + Subtext + Slim progress bar directly under subtext
        Column(modifier = Modifier.weight(1f)) {
            // Filename in medium-weight white text, single line, ellipsized if long (dims slightly if done)
            Text(
                text = transfer.fileName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDone) TextDimmedDone else TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Subtext line: size · status · speed, in muted grey, small font — condensed single line
            val subtext = when {
                transfer.isChunking -> "${ChecksumUtil.formatBytes(transfer.totalBytes)} · Chunking"
                isQueued -> "${ChecksumUtil.formatBytes(transfer.totalBytes)} · Queued"
                transfer.status == FileStatus.UPLOADING -> {
                    val speed = if (transfer.speedBytesPerSec > 0) " · ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}" else ""
                    val chunkStr = if (transfer.totalChunks > 1) " (${transfer.completedChunksCount.coerceAtLeast(transfer.currentChunk)}/${transfer.totalChunks})" else ""
                    "${ChecksumUtil.formatBytes(transfer.totalBytes)} · Uploading$chunkStr$speed"
                }
                transfer.status == FileStatus.DOWNLOADING -> {
                    val speed = if (transfer.speedBytesPerSec > 0) " · ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}" else ""
                    val chunkStr = if (transfer.totalChunks > 1) " (${transfer.currentChunk}/${transfer.totalChunks})" else ""
                    "${ChecksumUtil.formatBytes(transfer.totalBytes)} · Downloading$chunkStr$speed"
                }
                transfer.status == FileStatus.PAUSED -> "${ChecksumUtil.formatBytes(transfer.totalBytes)} · Paused"
                isDone -> "${ChecksumUtil.formatBytes(transfer.totalBytes)} · Done"
                isFailed -> "${ChecksumUtil.formatBytes(transfer.totalBytes)} · Failed${if (!transfer.errorMessage.isNullOrBlank()) ": ${transfer.errorMessage}" else ""}"
                else -> "${ChecksumUtil.formatBytes(transfer.totalBytes)} · Queued"
            }

            Text(
                text = subtext,
                fontSize = 11.sp,
                color = if (isFailed) StateRed else TextMutedGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Progress bar: thin (2–3px), accent-colored, directly under the subtext — not a separate thick bar
            if (isActive) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = if (transfer.status == FileStatus.PAUSED) StateGreyscale else ElectricCyan,
                    trackColor = TrackDark
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: minimal ghost/outline icons, small and unobtrusive, not full-size buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            when {
                transfer.status == FileStatus.UPLOADING || transfer.status == FileStatus.DOWNLOADING -> {
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                transfer.status == FileStatus.PAUSED -> {
                    IconButton(
                        onClick = onResume,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                isFailed -> {
                    // Red accent icon + retry button inline replacing the pause icon
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Retry",
                            tint = StateRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                isQueued -> {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                isDone -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = StateMutedGreen,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                }
            }
        }
    }
}
