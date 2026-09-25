package com.example.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FileStatus
import com.example.domain.ChecksumUtil
import com.example.domain.model.TransferProgress
import com.example.ui.components.TeleVaultBottomNav
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.pressScale
import com.example.ui.viewmodel.AppScreen

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

@Composable
fun TransfersScreen(
    transfers: List<TransferProgress>,
    recentlyCompleted: List<TransferProgress>,
    onBack: () -> Unit,
    onPause: (fileId: String) -> Unit,
    onResume: (fileId: String, isUpload: Boolean) -> Unit,
    onCancel: (fileId: String) -> Unit,
    onRetry: (fileId: String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onNavigateToVault: () -> Unit
) {
    val activeCount = transfers.count {
        it.status == FileStatus.UPLOADING || it.status == FileStatus.DOWNLOADING || it.status == FileStatus.PENDING
    }
    val pausedCount = transfers.count { it.status == FileStatus.PAUSED }
    val hasActiveTransfers = activeCount > 0
    val hasPausedTransfers = pausedCount > 0

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack),
        containerColor = OledBlack,
        topBar = {
            TransfersTopBar(
                activeCount = activeCount,
                totalCount = transfers.size,
                hasActiveTransfers = hasActiveTransfers,
                hasPausedTransfers = hasPausedTransfers,
                onBack = onBack,
                onPauseAll = onPauseAll,
                onResumeAll = onResumeAll
            )
        },
        bottomBar = {
            TeleVaultBottomNav(
                currentScreen = AppScreen.TRANSFERS,
                activeTransferCount = transfers.size,
                onVaultSelected = onNavigateToVault,
                onTransfersSelected = { /* Already on Transfers */ }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(OledBlack)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item(key = "header_spacer") {
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (transfers.isEmpty() && recentlyCompleted.isEmpty()) {
                item(key = "empty_transfers") {
                    EmptyTransfersView()
                }
            } else {
                if (transfers.isNotEmpty()) {
                    item(key = "active_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE TRANSFERS (${transfers.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMutedGrey,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Overall Batch Progress (when multiple items in queue):
                    // Slim accent bar, smaller less-dominant percentage/count
                    if (transfers.size > 1) {
                        val completedCount = transfers.count { it.status == FileStatus.COMPLETED }
                        val totalCount = transfers.size
                        item(key = "batch_progress_banner") {
                            BatchProgressHeader(
                                completedCount = completedCount,
                                totalCount = totalCount
                            )
                        }
                    }

                    itemsIndexed(transfers, key = { _, item -> item.fileId }) { index, transfer ->
                        TransferRowCard(
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

                if (recentlyCompleted.isNotEmpty()) {
                    item(key = "completed_header") {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = DividerLowAlpha, thickness = 1.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RECENTLY COMPLETED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StateGreyscale,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Clear",
                                    fontSize = 12.sp,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .pressScale(0.90f)
                                        .clickable(onClick = onClearCompleted)
                                        .padding(4.dp)
                                        .testTag("btn_clear_completed")
                                )
                            }
                        }
                    }

                    itemsIndexed(recentlyCompleted, key = { _, item -> "completed_${item.fileId}" }) { index, item ->
                        CompletedTransferRow(item = item)
                        if (index < recentlyCompleted.size - 1) {
                            HorizontalDivider(color = DividerLowAlpha, thickness = 1.dp)
                        }
                    }
                }

                item(key = "footer_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun TransfersTopBar(
    activeCount: Int,
    totalCount: Int,
    hasActiveTransfers: Boolean,
    hasPausedTransfers: Boolean,
    onBack: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit
) {
    Surface(
        color = OledBlack,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("btn_back_to_vault")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Vault",
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = "Transfer Queue",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = if (totalCount == 0) {
                        "Queue clear"
                    } else if (activeCount > 0) {
                        "$activeCount in progress · $totalCount total"
                    } else {
                        "$totalCount items in queue"
                    },
                    fontSize = 11.sp,
                    color = TextMutedGrey
                )
            }

            if (hasActiveTransfers) {
                IconButton(
                    onClick = onPauseAll,
                    modifier = Modifier
                        .size(36.dp)
                        .pressScale(0.88f)
                        .testTag("btn_pause_all")
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause All",
                        tint = TextMutedGrey,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (hasPausedTransfers) {
                IconButton(
                    onClick = onResumeAll,
                    modifier = Modifier
                        .size(36.dp)
                        .pressScale(0.88f)
                        .testTag("btn_resume_all")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume All",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchProgressHeader(
    completedCount: Int,
    totalCount: Int
) {
    val percent = if (totalCount > 0) (completedCount * 100) / totalCount else 0
    val animatedBatchProgress by animateFloatAsState(
        targetValue = (completedCount.toFloat() / totalCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
        label = "screen_batch_progress_anim"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("batch_progress_banner")
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
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = DividerLowAlpha, thickness = 1.dp)
    }
}

@Composable
private fun TransferRowCard(
    transfer: TransferProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val isDone = transfer.status == FileStatus.COMPLETED
    val isFailed = transfer.status == FileStatus.FAILED
    val isQueued = transfer.status == FileStatus.PENDING
    val isActive = transfer.status == FileStatus.UPLOADING || transfer.status == FileStatus.DOWNLOADING || transfer.status == FileStatus.PAUSED
    val percent = (transfer.progressFraction * 100).toInt().coerceIn(0, 100)

    val animatedProgress by animateFloatAsState(
        targetValue = transfer.progressFraction.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "transfer_progress_${transfer.fileId}"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OledBlack)
            .clickable(enabled = isFailed, onClick = onRetry)
            .padding(vertical = 11.dp)
            .testTag("transfer_item_${transfer.fileId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: small status icon (cloud-up for queued/uploading, checkmark for done, alert for failed)
        Icon(
            imageVector = when {
                isDone -> Icons.Default.Check
                isFailed -> Icons.Default.Error
                !transfer.isUpload -> Icons.Default.CloudDownload
                else -> Icons.Default.CloudUpload
            },
            contentDescription = if (transfer.isUpload) "Upload" else "Download",
            tint = when {
                isDone -> StateMutedGreen
                isFailed -> StateRed
                isQueued -> StateGreyscale
                else -> ElectricCyan
            },
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Center column: filename + subtext + slim progress bar directly under subtext
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transfer.fileName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDone) TextDimmedDone else TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Subtext: size · status · speed (tightly condensed single line)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = ChecksumUtil.formatBytes(transfer.totalBytes),
                    fontSize = 11.sp,
                    color = TextMutedGrey
                )
                Text(text = "·", fontSize = 11.sp, color = StateGreyscale)

                when (transfer.status) {
                    FileStatus.UPLOADING -> {
                        Text(
                            text = "Uploading…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ElectricCyan
                        )
                        Text(text = "·", fontSize = 11.sp, color = StateGreyscale)
                        Text(
                            text = "Chunk ${transfer.currentChunk} of ${transfer.totalChunks} · $percent%",
                            fontSize = 11.sp,
                            color = TextMutedGrey
                        )
                        if (transfer.speedBytesPerSec > 0) {
                            Text(text = "·", fontSize = 11.sp, color = StateGreyscale)
                            Text(
                                text = ChecksumUtil.formatSpeed(transfer.speedBytesPerSec),
                                fontSize = 11.sp,
                                color = TextMutedGrey
                            )
                        }
                    }
                    FileStatus.DOWNLOADING -> {
                        Text(
                            text = "Downloading…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ElectricCyan
                        )
                        Text(text = "·", fontSize = 11.sp, color = StateGreyscale)
                        Text(
                            text = "Chunk ${transfer.currentChunk} of ${transfer.totalChunks} · $percent%",
                            fontSize = 11.sp,
                            color = TextMutedGrey
                        )
                        if (transfer.speedBytesPerSec > 0) {
                            Text(text = "·", fontSize = 11.sp, color = StateGreyscale)
                            Text(
                                text = ChecksumUtil.formatSpeed(transfer.speedBytesPerSec),
                                fontSize = 11.sp,
                                color = TextMutedGrey
                            )
                        }
                    }
                    FileStatus.FAILED -> {
                        Text(
                            text = "Failed — tap to retry",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = StateRed
                        )
                        if (!transfer.errorMessage.isNullOrBlank()) {
                            Text(text = "·", fontSize = 11.sp, color = StateGreyscale)
                            Text(
                                text = transfer.errorMessage,
                                fontSize = 11.sp,
                                color = TextMutedGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    FileStatus.COMPLETED -> {
                        Text(
                            text = "Done",
                            fontSize = 11.sp,
                            color = StateMutedGreen
                        )
                    }
                    FileStatus.PAUSED -> {
                        Text(
                            text = "Paused",
                            fontSize = 11.sp,
                            color = TextMutedGrey
                        )
                    }
                    else -> {
                        Text(
                            text = if (transfer.isChunking) "Chunking" else "Queued",
                            fontSize = 11.sp,
                            color = StateGreyscale
                        )
                    }
                }
            }

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
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_pause_${transfer.fileId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause transfer",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_cancel_${transfer.fileId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel transfer",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                transfer.status == FileStatus.PAUSED -> {
                    IconButton(
                        onClick = onResume,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_resume_${transfer.fileId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume transfer",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_cancel_${transfer.fileId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel transfer",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                isFailed -> {
                    // Red accent icon + retry button inline replacing the pause icon
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_retry_${transfer.fileId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Retry transfer",
                            tint = StateRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_cancel_${transfer.fileId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss error",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                isQueued -> {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_cancel_${transfer.fileId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel queued transfer",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                isDone -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
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

@Composable
private fun CompletedTransferRow(item: TransferProgress) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OledBlack)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Completed",
            tint = StateMutedGreen,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDimmedDone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${ChecksumUtil.formatBytes(item.totalBytes)} · Done",
                fontSize = 11.sp,
                color = TextMutedGrey
            )
        }
    }
}

@Composable
private fun EmptyTransfersView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = StateGreyscale,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No active transfers",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Queue clear",
                fontSize = 12.sp,
                color = TextMutedGrey
            )
        }
    }
}
