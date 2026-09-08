package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
            TransfersBottomNav(
                activeTransferCount = transfers.size,
                onVaultSelected = onNavigateToVault
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (transfers.isEmpty() && recentlyCompleted.isEmpty()) {
                item {
                    EmptyTransfersView()
                }
            } else {
                if (transfers.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE TRANSFERS (${transfers.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextTertiary,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    items(transfers, key = { it.fileId }) { transfer ->
                        TransferRowCard(
                            transfer = transfer,
                            onPause = { onPause(transfer.fileId) },
                            onResume = { onResume(transfer.fileId, transfer.isUpload) },
                            onCancel = { onCancel(transfer.fileId) },
                            onRetry = { onRetry(transfer.fileId) }
                        )
                    }
                }

                if (recentlyCompleted.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECENTLY COMPLETED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextTertiary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Clear",
                                fontSize = 12.sp,
                                color = TelegramBlue,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable(onClick = onClearCompleted)
                                    .padding(4.dp)
                                    .testTag("btn_clear_completed")
                            )
                        }
                    }

                    items(recentlyCompleted, key = { "completed_${it.fileId}" }) { item ->
                        CompletedTransferCard(item = item)
                    }
                }

                item {
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_back_to_vault")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Vault",
                    tint = TextPrimary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = "Active Transfers",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (totalCount == 0) {
                        "Queue clear"
                    } else if (activeCount > 0) {
                        "$activeCount in progress · $totalCount total"
                    } else {
                        "$totalCount items in queue"
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            if (hasActiveTransfers) {
                IconButton(
                    onClick = onPauseAll,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_pause_all")
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause All",
                        tint = TextSecondary
                    )
                }
            } else if (hasPausedTransfers) {
                IconButton(
                    onClick = onResumeAll,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_resume_all")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume All",
                        tint = TelegramBlue
                    )
                }
            }
        }
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
    val isFailed = transfer.status == FileStatus.FAILED
    val percent = (transfer.progressFraction * 100).toInt().coerceIn(0, 100)

    val animatedProgress by animateFloatAsState(
        targetValue = transfer.progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "transfer_progress_${transfer.fileId}"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = OledCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isFailed) StatusError.copy(alpha = 0.5f) else OledBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isFailed, onClick = onRetry)
            .testTag("transfer_item_${transfer.fileId}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Icon, File Name, and Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFailed) StatusError.copy(alpha = 0.15f)
                            else TelegramBlue.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (transfer.isUpload) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                        contentDescription = if (transfer.isUpload) "Upload" else "Download",
                        tint = if (isFailed) StatusError else TelegramBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (transfer.status) {
                            FileStatus.UPLOADING -> if (transfer.speedBytesPerSec > 0) "Uploading…" else "Connecting to Telegram…"
                            FileStatus.DOWNLOADING -> if (transfer.speedBytesPerSec > 0) "Downloading…" else "Connecting to Telegram…"
                            FileStatus.PAUSED -> "Paused"
                            FileStatus.PENDING -> "Queued"
                            FileStatus.FAILED -> "Failed — tap to retry"
                            FileStatus.COMPLETED -> "Completed"
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isFailed) FontWeight.Medium else FontWeight.Normal,
                        color = when (transfer.status) {
                            FileStatus.FAILED -> StatusError
                            FileStatus.UPLOADING, FileStatus.DOWNLOADING -> TelegramBlue
                            FileStatus.PAUSED -> TextSecondary
                            else -> TextTertiary
                        }
                    )
                }

                // Action Controls with guaranteed 48dp minimum touch targets
                when (transfer.status) {
                    FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_pause_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Pause transfer",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_cancel_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel transfer",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    FileStatus.PAUSED -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_resume_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Resume transfer",
                                tint = TelegramBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_cancel_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel transfer",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    FileStatus.FAILED -> {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_retry_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Replay,
                                contentDescription = "Retry transfer",
                                tint = TelegramBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_cancel_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    FileStatus.PENDING -> {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_cancel_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel queued transfer",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    FileStatus.COMPLETED -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Smooth Animated Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when {
                    isFailed -> StatusError
                    transfer.status == FileStatus.COMPLETED -> StatusSuccess
                    else -> TelegramBlue
                },
                trackColor = OledSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Metrics Row: Chunk progress, overall percent, transferred / total bytes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val chunkProgressLabel = when {
                    transfer.isUpload && transfer.status == FileStatus.UPLOADING && transfer.activeConcurrentChunks > 0 -> {
                        "${transfer.activeConcurrentChunks} of ${transfer.totalChunks} chunks uploading, ${transfer.completedChunksCount} complete · $percent%"
                    }
                    transfer.isUpload && transfer.completedChunksCount > 0 -> {
                        "Chunk ${transfer.completedChunksCount} of ${transfer.totalChunks} complete · $percent%"
                    }
                    else -> "Chunk ${transfer.currentChunk} of ${transfer.totalChunks} · $percent%"
                }

                Text(
                    text = chunkProgressLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Text(
                    text = "${ChecksumUtil.formatBytes(transfer.bytesTransferred)} / ${ChecksumUtil.formatBytes(transfer.totalBytes)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Secondary Metrics Row: Live Transfer Speed & Estimated Time Remaining (ETA)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val speedText = when (transfer.status) {
                    FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
                        if (transfer.speedBytesPerSec > 0) {
                            "${if (transfer.isUpload) "↑" else "↓"} ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}"
                        } else {
                            "Connecting…"
                        }
                    }
                    FileStatus.PAUSED -> "Paused"
                    FileStatus.COMPLETED -> "Finished"
                    FileStatus.FAILED -> "Failed"
                    else -> "Queued"
                }

                Text(
                    text = speedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (transfer.status == FileStatus.UPLOADING || transfer.status == FileStatus.DOWNLOADING) TelegramBlue else TextTertiary
                )

                val etaText = when (transfer.status) {
                    FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
                        if (transfer.speedBytesPerSec > 0 && transfer.etaSeconds != null) {
                            ChecksumUtil.formatEta(transfer.etaSeconds)
                        } else if (transfer.bytesTransferred >= transfer.totalBytes && transfer.totalBytes > 0) {
                            "Finalizing…"
                        } else {
                            "Estimating…"
                        }
                    }
                    FileStatus.PAUSED -> {
                        val remaining = (transfer.totalBytes - transfer.bytesTransferred).coerceAtLeast(0L)
                        "${ChecksumUtil.formatBytes(remaining)} left"
                    }
                    FileStatus.COMPLETED -> "Verified SHA-256"
                    else -> ""
                }

                if (etaText.isNotEmpty()) {
                    Text(
                        text = etaText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextTertiary
                    )
                }
            }

            // Visible Failure Reason
            if (isFailed && !transfer.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(StatusError.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = StatusError,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = transfer.errorMessage,
                        fontSize = 11.sp,
                        color = StatusError,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedTransferCard(item: TransferProgress) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OledCard),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OledBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = StatusSuccess,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${if (item.isUpload) "Uploaded" else "Downloaded"} · ${ChecksumUtil.formatBytes(item.totalBytes)} · Verified",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
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
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(OledSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No active transfers",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Files you upload or download will appear here\nwith live chunk progress and resumption controls.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun TransfersBottomNav(
    activeTransferCount: Int,
    onVaultSelected: () -> Unit
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
                color = OledBorder,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onVaultSelected,
            icon = {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Vault",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    text = "Vault",
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
            modifier = Modifier.testTag("tab_nav_vault")
        )

        NavigationBarItem(
            selected = true,
            onClick = { /* Already on Transfers */ },
            icon = {
                if (activeTransferCount > 0) {
                    BadgedBox(badge = {
                        Badge(
                            containerColor = TelegramBlue,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = activeTransferCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Transfers",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Transfers",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            label = {
                Text(
                    text = "Transfers",
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
            modifier = Modifier.testTag("tab_nav_transfers")
        )
    }
}
