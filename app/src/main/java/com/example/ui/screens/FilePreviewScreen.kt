package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.transfer.ThumbnailManager
import com.example.domain.ChecksumUtil
import com.example.domain.ThumbnailUtil
import com.example.domain.model.TransferProgress
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    val isAudio = remember(file.mimeType, file.name) {
        file.mimeType.startsWith("audio/", ignoreCase = true) ||
            file.name.substringAfterLast('.', "").lowercase() in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac")
    }
    val isPdf = remember(file.mimeType, file.name) {
        file.mimeType.equals("application/pdf", ignoreCase = true) ||
            file.name.endsWith(".pdf", ignoreCase = true)
    }
    val isTextOrCode = remember(file.mimeType, file.name) {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        file.mimeType.startsWith("text/", ignoreCase = true) ||
            file.mimeType.contains("json", ignoreCase = true) ||
            file.mimeType.contains("xml", ignoreCase = true) ||
            ext in setOf("txt", "md", "json", "csv", "log", "xml", "yml", "yaml", "kt", "java", "py", "js", "ts", "html", "css", "sh", "sql")
    }

    // Check if full file exists locally
    val localTarget = file.localUri ?: file.localPath
    val isFullFileDownloaded = remember(localTarget, file.status) {
        if (localTarget.isNullOrBlank()) false
        else if (localTarget.startsWith("/")) File(localTarget).exists()
        else {
            try {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(localTarget), "r")?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }
    }

    val cachedThumbFile = remember(file.id, file.thumbnailLocalPath, file.thumbnailFileId) {
        ThumbnailManager.getInstance(context).getOrFetchThumbnail(file)
    }

    var showControls by remember { mutableStateOf(true) }
    var playInlineVideo by remember { mutableStateOf(false) }

    // Pinch-to-zoom state for images
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        offset = if (newScale > 1f) {
            offset + panChange
        } else {
            Offset.Zero
        }
    }

    val isDownloadingFullFile = activeTransfer != null &&
        !activeTransfer.isUpload &&
        (activeTransfer.status == FileStatus.DOWNLOADING || activeTransfer.status == FileStatus.PENDING)

    val bgColor = if (isImage || isVideo) Color(0xFF000000) else colors.bg

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(isImage, isVideo) {
                if (isImage || isVideo) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (isImage) {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.4f
                                }
                            }
                        },
                        onTap = {
                            showControls = !showControls
                        }
                    )
                }
            }
            .testTag("file_preview_screen")
    ) {
        // Main Content Canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isImage) {
                        Modifier
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .transformable(state = transformState)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isImage -> {
                    val imageModel: Any? = when {
                        isFullFileDownloaded && !localTarget.isNullOrBlank() -> {
                            if (localTarget.startsWith("/")) File(localTarget) else Uri.parse(localTarget)
                        }
                        cachedThumbFile != null -> cachedThumbFile
                        else -> null
                    }

                    if (imageModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = file.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        PreviewPlaceholderCard(
                            file = file,
                            isVideo = false,
                            isDownloading = isDownloadingFullFile,
                            activeTransfer = activeTransfer,
                            onDownload = { onLoadFullImage(file.id) }
                        )
                    }
                }

                isVideo -> {
                    if (isFullFileDownloaded && !localTarget.isNullOrBlank() && playInlineVideo) {
                        InlineVideoPlayer(
                            context = context,
                            localTarget = localTarget,
                            onCloseInline = { playInlineVideo = false }
                        )
                    } else if (cachedThumbFile != null || isFullFileDownloaded) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val posterModel: Any? = cachedThumbFile ?: localTarget?.let {
                                if (it.startsWith("/")) File(it) else Uri.parse(it)
                            }
                            if (posterModel != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(posterModel)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(24.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                                    .padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(colors.teal)
                                        .clickable {
                                            if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
                                                playInlineVideo = true
                                            } else if (!isDownloadingFullFile) {
                                                onLoadFullImage(file.id)
                                            }
                                        }
                                        .testTag("preview_video_play_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDownloadingFullFile) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isFullFileDownloaded) Icons.Default.PlayArrow else Icons.Default.CloudDownload,
                                            contentDescription = if (isFullFileDownloaded) "Play Video" else "Download Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = when {
                                        isDownloadingFullFile -> "Downloading Video (${((activeTransfer?.progressFraction ?: 0f) * 100).toInt()}%)..."
                                        isFullFileDownloaded -> "Tap to play inline or open external player"
                                        else -> "Preview Poster Frame • Tap to download full video (${ChecksumUtil.formatBytes(file.size)})"
                                    },
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )

                                if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    FilledTonalButton(
                                        onClick = { openVideoExternally(context, localTarget, file.mimeType) },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.14f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open External Player", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        PreviewPlaceholderCard(
                            file = file,
                            isVideo = true,
                            isDownloading = isDownloadingFullFile,
                            activeTransfer = activeTransfer,
                            onDownload = { onLoadFullImage(file.id) }
                        )
                    }
                }

                isAudio -> {
                    if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
                        InlineAudioPlayerCard(
                            context = context,
                            file = file,
                            localTarget = localTarget
                        )
                    } else {
                        PreviewPlaceholderCard(
                            file = file,
                            isVideo = false,
                            isDownloading = isDownloadingFullFile,
                            activeTransfer = activeTransfer,
                            onDownload = { onLoadFullImage(file.id) }
                        )
                    }
                }

                isPdf -> {
                    if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
                        InlinePdfViewer(
                            context = context,
                            file = file,
                            localTarget = localTarget
                        )
                    } else {
                        PreviewPlaceholderCard(
                            file = file,
                            isVideo = false,
                            isDownloading = isDownloadingFullFile,
                            activeTransfer = activeTransfer,
                            onDownload = { onLoadFullImage(file.id) }
                        )
                    }
                }

                isTextOrCode -> {
                    if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
                        InlineTextCodeViewer(
                            context = context,
                            file = file,
                            localTarget = localTarget
                        )
                    } else {
                        PreviewPlaceholderCard(
                            file = file,
                            isVideo = false,
                            isDownloading = isDownloadingFullFile,
                            activeTransfer = activeTransfer,
                            onDownload = { onLoadFullImage(file.id) }
                        )
                    }
                }

                else -> {
                    PreviewPlaceholderCard(
                        file = file,
                        isVideo = false,
                        isDownloading = isDownloadingFullFile,
                        activeTransfer = activeTransfer,
                        onDownload = { onLoadFullImage(file.id) }
                    )
                }
            }
        }

        // Top Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
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

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = file.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = ChecksumUtil.formatBytes(file.size),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontFamily = NumericMonoFont
                            )
                            Text(
                                text = "•",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (isFullFileDownloaded) "Full Resolution Ready" else "Cloud Vault File",
                                color = if (isFullFileDownloaded) Color(0xFF4ADE80) else Color(0xFF60A5FA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (file.isEncrypted) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.25f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "AES-256",
                                        color = Color(0xFF4ADE80),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onInspectDetails,
                        modifier = Modifier.testTag("preview_inspect_details_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "File Details",
                            tint = Color.White
                        )
                    }
                }

                // Active full-file download progress bar
                if (isDownloadingFullFile && activeTransfer != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (file.isEncrypted) {
                                    "Downloading & decrypting (${activeTransfer.completedChunksCount}/${activeTransfer.totalChunks} chunks)"
                                } else {
                                    "Downloading full file (${activeTransfer.completedChunksCount}/${activeTransfer.totalChunks} chunks)"
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(activeTransfer.progressFraction * 100).toInt()}%",
                                color = colors.teal,
                                fontSize = 11.sp,
                                fontFamily = NumericMonoFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { activeTransfer.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.teal,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        // Bottom Controls & Action Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isImage && !isFullFileDownloaded) {
                    FilledTonalButton(
                        onClick = { onLoadFullImage(file.id) },
                        enabled = !isDownloadingFullFile,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.teal,
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.15f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("load_full_image_button")
                    ) {
                        if (isDownloadingFullFile) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Loading Full Image (${((activeTransfer?.progressFraction ?: 0f) * 100).toInt()}%)...",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Load Full Image (${ChecksumUtil.formatBytes(file.size)})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (isImage && isFullFileDownloaded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Viewing Full Resolution Image • Pinch or double-tap to zoom",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }

                // Quick Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviewActionItem(
                        icon = Icons.Default.Share,
                        label = "Share",
                        onClick = onShare,
                        testTag = "preview_share_action"
                    )
                    PreviewActionItem(
                        icon = Icons.Default.DriveFileMove,
                        label = "Move",
                        onClick = onMove,
                        testTag = "preview_move_action"
                    )
                    PreviewActionItem(
                        icon = Icons.Default.Edit,
                        label = "Rename",
                        onClick = onRename,
                        testTag = "preview_rename_action"
                    )
                    PreviewActionItem(
                        icon = Icons.Default.Info,
                        label = "Details",
                        onClick = onInspectDetails,
                        testTag = "preview_details_action"
                    )
                    PreviewActionItem(
                        icon = Icons.Outlined.Delete,
                        label = "Delete",
                        tint = Color(0xFFF87171),
                        onClick = onDelete,
                        testTag = "preview_delete_action"
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineVideoPlayer(
    context: Context,
    localTarget: String,
    onCloseInline: () -> Unit
) {
    val videoUri = remember(localTarget) {
        resolveLocalFileUri(context, localTarget)
    }

    if (videoUri == null) {
        Text("Unable to resolve local video URI", color = Color.White)
        return
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val mediaController = MediaController(ctx)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun InlineAudioPlayerCard(
    context: Context,
    file: FileEntity,
    localTarget: String
) {
    val colors = LocalTeleVaultColors.current
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var playerError by remember { mutableStateOf<String?>(null) }

    val mediaPlayer = remember(localTarget) {
        MediaPlayer().apply {
            try {
                if (localTarget.startsWith("/")) {
                    setDataSource(localTarget)
                } else {
                    setDataSource(context, Uri.parse(localTarget))
                }
                prepare()
                durationMs = duration.coerceAtLeast(0)
                setOnCompletionListener {
                    isPlaying = false
                    positionMs = 0
                }
            } catch (e: Exception) {
                playerError = e.message ?: "Unable to initialize audio player"
            }
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                positionMs = mediaPlayer.currentPosition.coerceAtLeast(0)
            } catch (_: Exception) {
            }
            delay(250)
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .border(1.dp, colors.line, RoundedCornerShape(24.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(colors.teal.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = file.name,
                color = colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${ChecksumUtil.formatBytes(file.size)} • Built-in Audio Player",
                color = colors.textDim,
                fontSize = 12.sp
            )

            if (playerError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = playerError ?: "",
                    color = Color(0xFFF87171),
                    fontSize = 12.sp
                )
            } else {
                Spacer(modifier = Modifier.height(18.dp))

                Slider(
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { frac ->
                        if (durationMs > 0) {
                            val target = (frac * durationMs).toInt()
                            mediaPlayer.seekTo(target)
                            positionMs = target
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = colors.teal,
                        activeTrackColor = colors.teal,
                        inactiveTrackColor = colors.surfaceHi
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(positionMs),
                        color = colors.textDim,
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont
                    )
                    Text(
                        text = formatDurationMs(durationMs),
                        color = colors.textDim,
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    IconButton(
                        onClick = {
                            val rewind = (mediaPlayer.currentPosition - 10_000).coerceAtLeast(0)
                            mediaPlayer.seekTo(rewind)
                            positionMs = rewind
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = colors.text,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.teal)
                            .clickable {
                                if (mediaPlayer.isPlaying) {
                                    mediaPlayer.pause()
                                    isPlaying = false
                                } else {
                                    mediaPlayer.start()
                                    isPlaying = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlinePdfViewer(
    context: Context,
    file: FileEntity,
    localTarget: String
) {
    val colors = LocalTeleVaultColors.current
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(localTarget, currentPage) {
        withContext(Dispatchers.IO) {
            try {
                val pfd: ParcelFileDescriptor? = if (localTarget.startsWith("/")) {
                    ParcelFileDescriptor.open(File(localTarget), ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(Uri.parse(localTarget), "r")
                }

                if (pfd == null) {
                    errorMessage = "Unable to open PDF descriptor"
                    return@withContext
                }

                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        pageCount = renderer.pageCount
                        if (renderer.pageCount > 0) {
                            val safeIndex = currentPage.coerceIn(0, renderer.pageCount - 1)
                            renderer.openPage(safeIndex).use { page ->
                                val width = (page.width * 2).coerceAtMost(2048)
                                val height = (page.height * 2).coerceAtMost(2800)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                currentBitmap = bmp
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unable to render PDF document"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 64.dp, bottom = 92.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (errorMessage != null) {
            Text(text = errorMessage ?: "", color = Color(0xFFF87171), fontSize = 13.sp)
        } else {
            // Page switcher bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Page ${currentPage + 1} of ${pageCount.coerceAtLeast(1)}",
                        color = colors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = NumericMonoFont
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage -= 1 },
                        enabled = currentPage > 0,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous Page",
                            tint = if (currentPage > 0) colors.text else colors.textFaint
                        )
                    }
                    IconButton(
                        onClick = { if (currentPage < pageCount - 1) currentPage += 1 },
                        enabled = currentPage < pageCount - 1,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next Page",
                            tint = if (currentPage < pageCount - 1) colors.text else colors.textFaint
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = currentBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPage + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = colors.teal)
                }
            }
        }
    }
}

@Composable
private fun InlineTextCodeViewer(
    context: Context,
    file: FileEntity,
    localTarget: String
) {
    val colors = LocalTeleVaultColors.current
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(localTarget) {
        withContext(Dispatchers.IO) {
            try {
                val text = if (localTarget.startsWith("/")) {
                    File(localTarget).bufferedReader().use { it.readText().take(200_000) }
                } else {
                    context.contentResolver.openInputStream(Uri.parse(localTarget))
                        ?.bufferedReader()
                        ?.use { it.readText().take(200_000) } ?: ""
                }
                lines = text.lines()
            } catch (e: Exception) {
                lines = listOf("Error reading document: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    val filteredLines = remember(lines, searchQuery) {
        if (searchQuery.isBlank()) {
            lines.mapIndexed { idx, line -> (idx + 1) to line }
        } else {
            lines.mapIndexedNotNull { idx, line ->
                if (line.contains(searchQuery, ignoreCase = true)) (idx + 1) to line else null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 64.dp, bottom = 92.dp, start = 14.dp, end = 14.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search in ${file.name} (${lines.size} lines)...",
                    color = colors.textFaint,
                    fontSize = 12.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.textDim,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.teal,
                unfocusedBorderColor = colors.line,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = colors.teal,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState()),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(filteredLines) { _, (lineNum, content) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = lineNum.toString().padStart(4, ' '),
                                color = colors.textFaint,
                                fontSize = 11.sp,
                                fontFamily = NumericMonoFont,
                                modifier = Modifier.width(38.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = content,
                                color = colors.text,
                                fontSize = 12.sp,
                                fontFamily = NumericMonoFont
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPlaceholderCard(
    file: FileEntity,
    isVideo: Boolean,
    isDownloading: Boolean,
    activeTransfer: TransferProgress?,
    onDownload: () -> Unit
) {
    val colors = LocalTeleVaultColors.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.teal.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isVideo -> Icons.Default.Movie
                        ThumbnailUtil.isImage(file.mimeType) -> Icons.Default.Image
                        file.name.endsWith(".pdf", true) -> Icons.Default.PictureAsPdf
                        file.mimeType.startsWith("text/") -> Icons.Default.Code
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = file.name,
                color = colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${ChecksumUtil.formatBytes(file.size)} • ${file.totalChunks} chunk${if (file.totalChunks == 1) "" else "s"}",
                color = colors.textDim,
                fontSize = 12.sp,
                fontFamily = NumericMonoFont
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onDownload,
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.teal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloading (${((activeTransfer?.progressFraction ?: 0f) * 100).toInt()}%)...",
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (file.isEncrypted) "Download & Decrypt to Preview" else "Download Full File to Preview",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(testTag)
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
            color = tint.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun resolveLocalFileUri(context: Context, localTarget: String): Uri? {
    return try {
        if (localTarget.startsWith("/")) {
            val f = File(localTarget)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        } else {
            Uri.parse(localTarget)
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatDurationMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

private fun openVideoExternally(context: Context, localTarget: String, mimeType: String) {
    try {
        val uri: Uri = if (localTarget.startsWith("/")) {
            val file = File(localTarget)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.parse(localTarget)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "video/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Play Video"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to launch video player: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
