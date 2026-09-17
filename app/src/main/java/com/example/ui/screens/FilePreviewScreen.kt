package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.transfer.ThumbnailManager
import com.example.domain.ChecksumUtil
import com.example.domain.ThumbnailUtil
import com.example.domain.model.TransferProgress
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont
import java.io.File

@Composable
fun FilePreviewScreen(
    file: FileEntity,
    activeTransfer: TransferProgress? = null,
    onBack: () -> Unit,
    onLoadFullImage: (String) -> Unit,
    onInspectDetails: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val context = LocalContext.current

    val isImage = remember(file.mimeType) { ThumbnailUtil.isImage(file.mimeType) }
    val isVideo = remember(file.mimeType) { ThumbnailUtil.isVideo(file.mimeType) }

    // Check if full file exists locally
    val localTarget = file.localUri ?: file.localPath
    val isFullFileDownloaded = remember(localTarget, file.status) {
        if (localTarget.isNullOrBlank()) false
        else if (localTarget.startsWith("content://")) {
            try {
                context.contentResolver.openInputStream(Uri.parse(localTarget))?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        } else {
            val f = File(localTarget)
            f.exists() && f.length() > 0L
        }
    }

    // Resolve thumbnail file
    val thumbnailManager = remember { ThumbnailManager.getInstance(context) }
    val thumbFile = remember(file.thumbnailLocalPath, file.id) {
        thumbnailManager.getOrFetchThumbnail(file)
    }

    val isTransferring = activeTransfer != null &&
            (activeTransfer.status == FileStatus.DOWNLOADING || activeTransfer.status == FileStatus.PENDING)

    val formattedSize = remember(file.size) { ChecksumUtil.formatFileSize(file.size) }
    val formattedDate = remember(file.uploadDate) { ChecksumUtil.formatDate(file.uploadDate) }

    // Zoom and pan state for full images
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        if (newScale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F141A)) // Dark theater background for previewing media
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("preview_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BodySansFont,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedSize,
                            fontSize = 12.sp,
                            fontFamily = NumericMonoFont,
                            color = Color(0xFFA0AEC0)
                        )
                        Text(
                            text = " · ",
                            fontSize = 12.sp,
                            color = Color(0xFF718096)
                        )
                        Text(
                            text = if (isFullFileDownloaded) "Full File Ready" else "Thumbnail Preview",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isFullFileDownloaded) colors.mint else colors.teal
                        )
                    }
                }

                IconButton(
                    onClick = onInspectDetails,
                    modifier = Modifier.testTag("preview_inspect_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "File Details",
                        tint = Color(0xFFA0AEC0)
                    )
                }

                IconButton(
                    onClick = {
                        if (isFullFileDownloaded && localTarget != null) {
                            shareLocalFile(context, file, localTarget)
                        } else {
                            onShare()
                        }
                    },
                    modifier = Modifier.testTag("preview_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color(0xFFA0AEC0)
                    )
                }
            }

            // Main Preview Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(0.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isImage -> {
                        if (isFullFileDownloaded && localTarget != null) {
                            // High-resolution full image
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(localTarget))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = file.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                                    .transformable(state = transformableState)
                            )
                        } else {
                            // Downscaled Thumbnail Preview (200px)
                            Box(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbFile != null && thumbFile.exists()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbFile)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Thumbnail for ${file.name}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(240.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(200.dp)
                                            .background(Color(0xFF1A202C), RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = colors.teal,
                                            modifier = Modifier.size(64.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    isVideo -> {
                        // Video Frame Preview
                        Box(
                            modifier = Modifier
                                .padding(24.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbFile != null && thumbFile.exists()) {
                                Box(contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbFile)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Video Frame for ${file.name}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(240.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                    // Play icon overlay
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color(0x99000000), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(200.dp)
                                        .background(Color(0xFF1A202C), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // Generic fallback icon
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .background(Color(0xFF1A202C), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            FileIcon(mimeType = file.mimeType, size = 56.dp)
                        }
                    }
                }
            }

            // Bottom Action Area & Load Full Image / Video Controls
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A202C)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    if (isTransferring) {
                        // Download in-place progress indicator
                        val progress = activeTransfer?.progressFraction ?: 0f
                        val percent = (progress * 100).toInt()
                        val speed = ChecksumUtil.formatBytes(activeTransfer?.speedBytesPerSec ?: 0L)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading full resolution...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "$percent% · $speed/s",
                                    fontSize = 12.sp,
                                    fontFamily = NumericMonoFont,
                                    color = colors.teal
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = colors.teal,
                                trackColor = Color(0xFF2D3748)
                            )
                        }
                    } else if (!isFullFileDownloaded) {
                        // Demand-based "Load full image" / "Download Video" CTA
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isImage) "Full Image on Demand" else "Full Video on Demand",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Showing lightweight thumbnail ($formattedSize full size)",
                                    fontSize = 12.sp,
                                    color = Color(0xFFA0AEC0)
                                )
                            }

                            Button(
                                onClick = { onLoadFullImage(file.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("load_full_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isImage) "Load full image" else "Download to Play",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        // File is fully downloaded
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(colors.mint.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colors.mint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Full Resolution Loaded",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Saved locally on this device",
                                        fontSize = 11.sp,
                                        color = Color(0xFFA0AEC0)
                                    )
                                }
                            }

                            if (isVideo && localTarget != null) {
                                Button(
                                    onClick = { playVideo(context, file, localTarget) },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.mint),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play Video", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Secondary Fast Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ActionPill(
                            icon = Icons.Default.Edit,
                            label = "Rename",
                            onClick = onRename
                        )
                        ActionPill(
                            icon = Icons.Default.DriveFileMove,
                            label = "Move",
                            onClick = onMove
                        )
                        ActionPill(
                            icon = Icons.Default.Info,
                            label = "Details",
                            onClick = onInspectDetails
                        )
                        ActionPill(
                            icon = Icons.Outlined.Delete,
                            label = "Trash",
                            tint = Color(0xFFFC8181),
                            onClick = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color(0xFFA0AEC0),
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun shareLocalFile(context: Context, file: FileEntity, localPath: String) {
    try {
        val uri = if (localPath.startsWith("content://")) {
            Uri.parse(localPath)
        } else {
            val localFile = File(localPath)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun playVideo(context: Context, file: FileEntity, localPath: String) {
    try {
        val uri = if (localPath.startsWith("content://")) {
            Uri.parse(localPath)
        } else {
            val localFile = File(localPath)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType.ifBlank { "video/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No video player available: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
