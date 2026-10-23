package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import com.example.data.transfer.EphemeralStreamManager
import com.example.data.transfer.ThumbnailManager
import com.example.domain.ChecksumUtil
import com.example.domain.ThumbnailUtil
import com.example.domain.model.TransferProgress
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FilePreviewScreen(
    file: FileEntity,
    folderName: String = "Root Vault",
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val streamManager = remember { EphemeralStreamManager.getInstance(context) }
    val bufferStats by streamManager.bufferStats.collectAsState()
    var fileChunks by remember(file.id) { mutableStateOf<List<ChunkEntity>>(emptyList()) }

    // Load ordered chunk metadata rows from local Room DB (zero network / zero RAM allocation)
    LaunchedEffect(file.id) {
        streamManager.wipeAllEphemeralBuffers("Opened File Details screen for ${file.name}")
        fileChunks = streamManager.getOrderedChunks(file.id)
    }

    // Strict lifecycle RAM wipe on viewer exit and app backgrounding (ON_STOP)
    DisposableEffect(file.id, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activity = context as? Activity
                if (activity?.isChangingConfigurations != true) {
                    streamManager.wipeAllEphemeralBuffers("App backgrounded (ON_STOP)")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            applyPlayerFullscreenSystemUi(context as? Activity, fullscreen = false, restoreUnspecifiedOrientation = true)
            streamManager.wipeAllEphemeralBuffers("Exited FilePreviewScreen for ${file.name}")
        }
    }

    val isImage = remember(file.mimeType) { ThumbnailUtil.isImage(file.mimeType) }
    val isVideo = remember(file.mimeType, file.name) {
        ThumbnailUtil.isVideo(file.mimeType) ||
            file.name.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v")
    }
    val isAudio = remember(file.mimeType, file.name) {
        file.mimeType.startsWith("audio/", ignoreCase = true) ||
            file.name.substringAfterLast('.', "").lowercase() in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus")
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showControls by remember { mutableStateOf(true) }
    // IMPORTANT: For video and audio files, the Details screen opens first with isStreamingMediaActive = false!
    // Nothing is fetched, buffered, or decrypted until the user presses the center Play button.
    var isStreamingMediaActive by remember(file.id) { mutableStateOf(false) }
    var isVideoFullscreen by remember(file.id) { mutableStateOf(false) }
    val isVideoPlayingInLandscapeOrFullscreen = isVideo && isStreamingMediaActive && (isLandscape || isVideoFullscreen)

    // Ensure RAM is strictly at 0.0 MB whenever on the Details screen (before Play is pressed or after returning from Player)
    LaunchedEffect(isStreamingMediaActive) {
        if (!isStreamingMediaActive && (isVideo || isAudio)) {
            streamManager.wipeAllEphemeralBuffers("Details screen active (player closed)")
            showControls = true
        }
    }

    // Synchronize system bars and orientation whenever isVideoFullscreen changes
    LaunchedEffect(isVideoFullscreen) {
        if (isVideo && isVideoFullscreen) {
            applyPlayerFullscreenSystemUi(
                activity = context as? Activity,
                fullscreen = true,
                restoreUnspecifiedOrientation = false
            )
        }
    }

    // Back navigation hierarchy:
    // 1. Fullscreen/Landscape Video -> Return to Portrait Player
    // 2. Active Streaming Player (Video or Audio) -> Wipe RAM to 0.0 MB and return to Details Screen
    // 3. Details Screen -> Return to File List
    val handleNavigateBack: () -> Unit = {
        if (isVideoFullscreen || (isVideo && isStreamingMediaActive && isLandscape)) {
            isVideoFullscreen = false
            applyPlayerFullscreenSystemUi(
                activity = context as? Activity,
                fullscreen = false,
                restoreUnspecifiedOrientation = false
            )
        } else if ((isVideo || isAudio) && isStreamingMediaActive) {
            isVideoFullscreen = false
            isStreamingMediaActive = false
            showControls = true
            applyPlayerFullscreenSystemUi(
                activity = context as? Activity,
                fullscreen = false,
                restoreUnspecifiedOrientation = true
            )
            streamManager.wipeAllEphemeralBuffers("Back from streaming player to Details screen")
        } else {
            applyPlayerFullscreenSystemUi(
                activity = context as? Activity,
                fullscreen = false,
                restoreUnspecifiedOrientation = true
            )
            streamManager.wipeAllEphemeralBuffers("Back navigation from File Details screen")
            onBack()
        }
    }

    BackHandler {
        handleNavigateBack()
    }

    // Zero-download in-RAM decoded full image bitmap
    var ramImageBitmap by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    var isLoadingRamImage by remember(file.id) { mutableStateOf(false) }

    DisposableEffect(file.id) {
        onDispose {
            ramImageBitmap = null
        }
    }

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

    val isDarkVideoPlayerCanvas = isImage || (isVideo && isStreamingMediaActive)
    val bgColor = if (isDarkVideoPlayerCanvas) Color(0xFF000000) else colors.bg
    val effectiveChunkCount = maxOf(file.totalChunks, fileChunks.size, 1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(isImage, isVideo, isStreamingMediaActive) {
                if (isImage || (isVideo && isStreamingMediaActive)) {
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
                // 1. Video or Audio File
                isVideo || isAudio -> {
                    if (isStreamingMediaActive) {
                        if (isVideo) {
                            ZeroDownloadExoVideoPlayer(
                                context = context,
                                file = file,
                                chunks = fileChunks,
                                streamManager = streamManager,
                                bufferStats = bufferStats,
                                localTarget = localTarget,
                                isFullFileDownloaded = isFullFileDownloaded,
                                showOverlayControls = showControls,
                                onToggleOverlayControls = { showControls = !showControls },
                                onSetOverlayControls = { showControls = it },
                                isFullscreen = isVideoFullscreen,
                                onFullscreenChange = { isVideoFullscreen = it }
                            )
                        } else {
                            ZeroDownloadExoAudioPlayerCard(
                                context = context,
                                file = file,
                                chunks = fileChunks,
                                streamManager = streamManager,
                                bufferStats = bufferStats,
                                localTarget = localTarget,
                                isFullFileDownloaded = isFullFileDownloaded
                            )
                        }
                    } else {
                        // FILE DETAILS SCREEN FOR VIDEO & AUDIO (0.0 MB RAM until Play is pressed)
                        MediaFileDetailsOverview(
                            context = context,
                            file = file,
                            folderName = folderName,
                            chunkCount = effectiveChunkCount,
                            isVideo = isVideo,
                            cachedThumbFile = cachedThumbFile,
                            localTarget = localTarget,
                            isFullFileDownloaded = isFullFileDownloaded,
                            onPlayClick = {
                                showControls = true
                                isStreamingMediaActive = true
                            }
                        )
                    }
                }

                // 2. Image File
                isImage -> {
                    val bmp = ramImageBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = file.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
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
                                folderName = folderName,
                                chunkCount = effectiveChunkCount,
                                isVideo = false,
                                isDownloading = isDownloadingFullFile || isLoadingRamImage,
                                activeTransfer = activeTransfer,
                                onDownload = {
                                    if (fileChunks.isNotEmpty()) {
                                        isLoadingRamImage = true
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val bytes = streamManager.getChunkPlaintextBytes(file, fileChunks, 0)
                                                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                withContext(Dispatchers.Main) {
                                                    ramImageBitmap = decoded
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    onLoadFullImage(file.id)
                                                }
                                            } finally {
                                                withContext(Dispatchers.Main) {
                                                    isLoadingRamImage = false
                                                }
                                            }
                                        }
                                    } else {
                                        onLoadFullImage(file.id)
                                    }
                                }
                            )
                        }
                    }
                }

                // 3. PDF File
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
                            folderName = folderName,
                            chunkCount = effectiveChunkCount,
                            isVideo = false,
                            isDownloading = isDownloadingFullFile,
                            activeTransfer = activeTransfer,
                            onDownload = { onLoadFullImage(file.id) }
                        )
                    }
                }

                // 4. Text / Code File
                isTextOrCode -> {
                    InlineTextCodeViewer(
                        context = context,
                        file = file,
                        chunks = fileChunks,
                        streamManager = streamManager,
                        localTarget = localTarget,
                        isFullFileDownloaded = isFullFileDownloaded
                    )
                }

                // 5. Other File Types
                else -> {
                    PreviewPlaceholderCard(
                        file = file,
                        folderName = folderName,
                        chunkCount = effectiveChunkCount,
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
            visible = showControls || !isStreamingMediaActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { handleNavigateBack() },
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
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = ChecksumUtil.formatBytes(file.size),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                fontFamily = NumericMonoFont
                            )
                            Text(
                                text = "•",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                lineHeight = 13.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.teal.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("ram_status_pill")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = formatRamWindowBadge(bufferStats),
                                    color = colors.teal,
                                    fontSize = 8.sp,
                                    lineHeight = 11.sp,
                                    fontFamily = NumericMonoFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (file.isEncrypted) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.violet.copy(alpha = 0.22f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = colors.violet,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "AES-256",
                                        color = colors.violet,
                                        fontSize = 8.sp,
                                        lineHeight = 11.sp,
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
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(activeTransfer.progressFraction * 100).toInt()}%",
                                color = colors.teal,
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
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

        // Bottom Controls & Action Bar (hidden in landscape and fullscreen mode when video player is active)
        AnimatedVisibility(
            visible = (showControls || !isStreamingMediaActive) && !isVideoPlayingInLandscapeOrFullscreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.80f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // For non-video/non-audio files, show a compact metadata summary pill strip above actions
                if (!isVideo && !isAudio) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$folderName • ${ChecksumUtil.formatDate(file.uploadDate)}",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${if (file.isEncrypted) "AES-256" else "Standard"} • $effectiveChunkCount chunk${if (effectiveChunkCount == 1) "" else "s"}",
                            color = colors.teal,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            fontFamily = NumericMonoFont,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isImage && !isFullFileDownloaded && ramImageBitmap == null) {
                    FilledTonalButton(
                        onClick = {
                            if (fileChunks.isNotEmpty()) {
                                isLoadingRamImage = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val bytes = streamManager.getChunkPlaintextBytes(file, fileChunks, 0)
                                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        withContext(Dispatchers.Main) {
                                            ramImageBitmap = decoded
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            onLoadFullImage(file.id)
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isLoadingRamImage = false
                                        }
                                    }
                                }
                            } else {
                                onLoadFullImage(file.id)
                            }
                        },
                        enabled = !isDownloadingFullFile && !isLoadingRamImage,
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
                        if (isDownloadingFullFile || isLoadingRamImage) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Streaming Full Image into RAM…",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stream Full Resolution in RAM (${ChecksumUtil.formatBytes(file.size)})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                } else if (isImage && (isFullFileDownloaded || ramImageBitmap != null)) {
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
                            tint = colors.teal,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (ramImageBitmap != null) {
                                "Viewing Full Resolution in RAM (Zero-Download) • Pinch to zoom"
                            } else {
                                "Viewing Full Resolution Image • Pinch or double-tap to zoom"
                            },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            lineHeight = 13.sp
                        )
                    }
                }

                // Quick Actions Row (Share, Move, Rename, Details, Delete)
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
                        tint = colors.violet,
                        onClick = onDelete,
                        testTag = "preview_delete_action"
                    )
                }
            }
        }
    }
}

/**
 * File Details Screen for Video & Audio files.
 *
 * Displays:
 * - Preview area with video thumbnail or audio type icon and a large Play button in the center.
 * - File name, size, date, folder, encryption status, and number of chunks.
 * - Strictly 0.0 MB RAM usage until the user presses the center Play button.
 */
@Composable
private fun MediaFileDetailsOverview(
    context: Context,
    file: FileEntity,
    folderName: String,
    chunkCount: Int,
    isVideo: Boolean,
    cachedThumbFile: File?,
    localTarget: String?,
    isFullFileDownloaded: Boolean,
    onPlayClick: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 68.dp, bottom = 84.dp, start = 20.dp, end = 20.dp)
            .verticalScroll(scrollState)
            .testTag("file_details_overview"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.line, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Center Preview Area with Thumbnail / Type Icon and Large Center Play Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isVideo) Color(0xFF0B0E14) else colors.surfaceHi)
                        .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                        .clickable { onPlayClick() },
                    contentAlignment = Alignment.Center
                ) {
                    val posterModel: Any? = if (isVideo) {
                        cachedThumbFile ?: localTarget?.takeIf { isFullFileDownloaded }?.let {
                            if (it.startsWith("/")) File(it) else Uri.parse(it)
                        }
                    } else null

                    if (posterModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(posterModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = file.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.42f))
                        )
                    } else {
                        // Type icon backdrop behind the Play button
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = colors.teal.copy(alpha = 0.16f),
                            modifier = Modifier.size(116.dp)
                        )
                    }

                    // Large Play Button in the Center of the Preview Area (preserved at 68.dp)
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(colors.teal)
                            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                            .clickable { onPlayClick() }
                            .testTag("preview_video_play_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Stream in RAM",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Bottom-right Type Pill inside Preview Box
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isVideo) "Tap Play to Stream Video" else "Tap Play to Stream Audio",
                            color = Color.White,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File Name
                Text(
                    text = file.name,
                    color = colors.text,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.line)
                Spacer(modifier = Modifier.height(12.dp))

                // File Metadata Rows: Size, Date, Folder, Encryption Status, Number of Chunks
                DetailsMetadataRow(
                    icon = Icons.Default.Description,
                    label = "Size",
                    value = ChecksumUtil.formatBytes(file.size),
                    isMono = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                DetailsMetadataRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Date",
                    value = ChecksumUtil.formatDate(file.uploadDate),
                    isMono = false
                )
                Spacer(modifier = Modifier.height(8.dp))
                DetailsMetadataRow(
                    icon = Icons.Default.Folder,
                    label = "Folder",
                    value = folderName,
                    isMono = false
                )
                Spacer(modifier = Modifier.height(8.dp))
                DetailsMetadataRow(
                    icon = if (file.isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                    label = "Encryption",
                    value = if (file.isEncrypted) "AES-256-GCM Encrypted" else "Standard (Unencrypted)",
                    valueColor = if (file.isEncrypted) colors.violet else colors.text,
                    isMono = false
                )
                Spacer(modifier = Modifier.height(8.dp))
                DetailsMetadataRow(
                    icon = Icons.Default.Layers,
                    label = "Chunks",
                    value = "$chunkCount chunk${if (chunkCount == 1) "" else "s"}",
                    isMono = true
                )
            }
        }
    }
}

@Composable
private fun DetailsMetadataRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = LocalTeleVaultColors.current.text,
    isMono: Boolean
) {
    val colors = LocalTeleVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceHi)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.teal,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = colors.textDim,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMono) NumericMonoFont else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            .padding(top = 64.dp, bottom = 104.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = colors.violet,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        } else {
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
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
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
    chunks: List<ChunkEntity>,
    streamManager: EphemeralStreamManager,
    localTarget: String?,
    isFullFileDownloaded: Boolean
) {
    val colors = LocalTeleVaultColors.current
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(localTarget, isFullFileDownloaded, chunks) {
        withContext(Dispatchers.IO) {
            try {
                val text = if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
                    if (localTarget.startsWith("/")) {
                        File(localTarget).bufferedReader().use { it.readText().take(200_000) }
                    } else {
                        context.contentResolver.openInputStream(Uri.parse(localTarget))
                            ?.bufferedReader()
                            ?.use { it.readText().take(200_000) } ?: ""
                    }
                } else if (chunks.isNotEmpty()) {
                    val bytes = streamManager.getChunkPlaintextBytes(file, chunks, 0)
                    val sliceLen = minOf(bytes.size, 200_000)
                    String(bytes, 0, sliceLen, Charsets.UTF_8)
                } else {
                    "Waiting for cloud chunk metadata…"
                }
                lines = text.lines()
            } catch (e: Exception) {
                lines = listOf("Error reading document in RAM: ${e.message}")
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
            .padding(top = 64.dp, bottom = 104.dp, start = 14.dp, end = 14.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search in ${file.name} (${lines.size} lines)...",
                    color = colors.textFaint,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
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
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                fontFamily = NumericMonoFont,
                                modifier = Modifier.width(38.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = content,
                                color = colors.text,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
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
    folderName: String,
    chunkCount: Int,
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
            .padding(horizontal = 24.dp)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = file.name,
                color = colors.text,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            DetailsMetadataRow(
                icon = Icons.Default.Description,
                label = "Size",
                value = ChecksumUtil.formatBytes(file.size),
                isMono = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            DetailsMetadataRow(
                icon = Icons.Default.CalendarToday,
                label = "Date",
                value = ChecksumUtil.formatDate(file.uploadDate),
                isMono = false
            )
            Spacer(modifier = Modifier.height(6.dp))
            DetailsMetadataRow(
                icon = Icons.Default.Folder,
                label = "Folder",
                value = folderName,
                isMono = false
            )
            Spacer(modifier = Modifier.height(6.dp))
            DetailsMetadataRow(
                icon = if (file.isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                label = "Encryption",
                value = if (file.isEncrypted) "AES-256-GCM Encrypted" else "Standard (Unencrypted)",
                valueColor = if (file.isEncrypted) colors.violet else colors.text,
                isMono = false
            )
            Spacer(modifier = Modifier.height(6.dp))
            DetailsMetadataRow(
                icon = Icons.Default.Layers,
                label = "Chunks",
                value = "$chunkCount chunk${if (chunkCount == 1) "" else "s"}",
                isMono = true
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                        text = "Loading (${((activeTransfer?.progressFraction ?: 0f) * 100).toInt()}%)...",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
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
                        text = if (file.isEncrypted) "Decrypt & Preview" else "Load Preview",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
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
            fontSize = 9.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
