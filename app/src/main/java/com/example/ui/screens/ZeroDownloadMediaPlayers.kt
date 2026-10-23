package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.transfer.EphemeralBufferStats
import com.example.data.transfer.EphemeralStreamManager
import com.example.data.transfer.TelegramChunkDataSource
import com.example.domain.ChecksumUtil
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont
import kotlinx.coroutines.delay
import java.io.File

/**
 * Phase 1 Zero-Download Media3 ExoPlayer for Video Streaming.
 *
 * Streams directly from Telegram chunks via [TelegramChunkDataSource] and [EphemeralStreamManager]'s
 * 20–50 MB RAM sliding window (or local file if already downloaded), supporting arbitrary byte-range
 * seeking across chunk boundaries and basic playback controls.
 */
@OptIn(UnstableApi::class)
@Composable
fun ZeroDownloadExoVideoPlayer(
    context: Context,
    file: FileEntity,
    chunks: List<ChunkEntity>,
    streamManager: EphemeralStreamManager,
    bufferStats: EphemeralBufferStats,
    localTarget: String?,
    isFullFileDownloaded: Boolean,
    showOverlayControls: Boolean,
    onToggleOverlayControls: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isLandscapeFullscreen by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableStateOf(0) }

    val exoPlayer = remember(file.id, chunks.size, isFullFileDownloaded, retryNonce) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 8_000,
                /* maxBufferMs = */ 25_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setTargetBufferBytes(16 * 1024 * 1024) // Keep ExoPlayer internal ring buffer <= 16 MB
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Bind media source (Zero-Download TelegramChunkDataSource or local file)
    LaunchedEffect(exoPlayer, file.id, chunks, isFullFileDownloaded, retryNonce) {
        playerError = null
        isBuffering = true

        if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
            val localUri = if (localTarget.startsWith("/")) Uri.fromFile(File(localTarget)) else Uri.parse(localTarget)
            val itemBuilder = MediaItem.Builder().setUri(localUri)
            resolveMedia3MimeType(file.mimeType, file.name)?.let { itemBuilder.setMimeType(it) }
            exoPlayer.setMediaItem(itemBuilder.build())
            exoPlayer.prepare()
        } else if (chunks.isNotEmpty()) {
            // Prefetch chunk 0 immediately in RAM sliding window
            streamManager.prefetchChunkAsync(file, chunks, 0)
            val streamUri = Uri.parse("televault://stream/${file.id}/${Uri.encode(file.name)}")
            val itemBuilder = MediaItem.Builder().setUri(streamUri)
            resolveMedia3MimeType(file.mimeType, file.name)?.let { itemBuilder.setMimeType(it) }

            val dataSourceFactory = TelegramChunkDataSource.Factory(
                file = file,
                chunks = chunks,
                streamManager = streamManager
            )
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(itemBuilder.build())
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
        } else {
            isBuffering = false
            playerError = "Waiting for cloud chunk metadata…"
        }
    }

    // Player listener & lifecycle observer (pause & wipe RAM buffer on ON_STOP)
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        durationMs = dur
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = error.cause?.message ?: error.message ?: "Stream playback error"
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
                streamManager.wipeAllEphemeralBuffers("App backgrounded during video playback")
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
            // Restore unspecified screen orientation if fullscreen was toggled
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Poll playback position for smooth seekbar updates
    LaunchedEffect(exoPlayer, isPlaying, isUserScrubbing) {
        while (true) {
            if (!isUserScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(currentPositionMs)
                val dur = exoPlayer.duration
                if (dur != C.TIME_UNSET && dur > 0L) {
                    durationMs = dur
                }
            }
            delay(200L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("zero_download_video_player"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                    setOnClickListener {
                        onToggleOverlayControls()
                    }
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering / Chunk Decryption Indicator
        if (isBuffering || bufferStats.isFetchingChunk) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                CircularProgressIndicator(
                    color = colors.teal,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = bufferStats.statusLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatRamWindowBadge(bufferStats),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont
                )
            }
        }

        // Stream Error Recovery Overlay
        if (playerError != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, colors.violet.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "Stream Interrupted",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = playerError ?: "",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                FilledTonalButton(
                    onClick = {
                        val resumePos = currentPositionMs
                        retryNonce++
                        exoPlayer.seekTo(resumePos)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.teal,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("video_retry_stream_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh Link & Resume Stream", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Phase 1 Video Transport & Seek Controls Overlay
        AnimatedVisibility(
            visible = showOverlayControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 86.dp, start = 14.dp, end = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Seekbar + Timecodes
                val activeDisplayMs = if (isUserScrubbing && durationMs > 0L) {
                    (scrubFraction * durationMs).toLong()
                } else {
                    currentPositionMs
                }
                val sliderValue = if (isUserScrubbing) {
                    scrubFraction
                } else if (durationMs > 0L) {
                    (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Slider(
                    value = sliderValue,
                    onValueChange = { frac ->
                        isUserScrubbing = true
                        scrubFraction = frac.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0L) {
                            val targetMs = (scrubFraction * durationMs).toLong().coerceIn(0L, durationMs)
                            exoPlayer.seekTo(targetMs)
                            currentPositionMs = targetMs
                        }
                        isUserScrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = colors.teal,
                        activeTrackColor = colors.teal,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preview_video_seek_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatStreamTimecode(activeDisplayMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = NumericMonoFont
                    )
                    Text(
                        text = formatRamWindowBadge(bufferStats),
                        color = colors.teal,
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatStreamTimecode(durationMs),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontFamily = NumericMonoFont
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Transport Buttons Row: Mute | -10s | Play/Pause | +10s | Fullscreen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        },
                        modifier = Modifier.testTag("preview_video_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier.testTag("preview_video_rewind_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(colors.teal)
                            .clickable {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0L)
                                    }
                                    exoPlayer.play()
                                }
                            }
                            .testTag("preview_video_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val maxDur = if (durationMs > 0L) durationMs else Long.MAX_VALUE
                            val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier.testTag("preview_video_ffwd_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Fast-forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            isLandscapeFullscreen = !isLandscapeFullscreen
                            (context as? Activity)?.requestedOrientation = if (isLandscapeFullscreen) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        },
                        modifier = Modifier.testTag("preview_video_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = if (isLandscapeFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isLandscapeFullscreen) "Exit Fullscreen" else "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Phase 1 Zero-Download Media3 ExoPlayer Card for Audio Streaming.
 *
 * Streams audio directly from Telegram chunks via [TelegramChunkDataSource] and [EphemeralStreamManager]'s
 * 20–50 MB RAM sliding window (or local file if already downloaded) with seek support and basic controls.
 */
@OptIn(UnstableApi::class)
@Composable
fun ZeroDownloadExoAudioPlayerCard(
    context: Context,
    file: FileEntity,
    chunks: List<ChunkEntity>,
    streamManager: EphemeralStreamManager,
    bufferStats: EphemeralBufferStats,
    localTarget: String?,
    isFullFileDownloaded: Boolean
) {
    val colors = LocalTeleVaultColors.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableStateOf(0) }

    val exoPlayer = remember(file.id, chunks.size, isFullFileDownloaded, retryNonce) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(6_000, 20_000, 1_000, 2_000)
            .setTargetBufferBytes(12 * 1024 * 1024)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }

    LaunchedEffect(exoPlayer, file.id, chunks, isFullFileDownloaded, retryNonce) {
        playerError = null
        if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
            val localUri = if (localTarget.startsWith("/")) Uri.fromFile(File(localTarget)) else Uri.parse(localTarget)
            val itemBuilder = MediaItem.Builder().setUri(localUri)
            resolveMedia3MimeType(file.mimeType, file.name)?.let { itemBuilder.setMimeType(it) }
            exoPlayer.setMediaItem(itemBuilder.build())
            exoPlayer.prepare()
        } else if (chunks.isNotEmpty()) {
            streamManager.prefetchChunkAsync(file, chunks, 0)
            val streamUri = Uri.parse("televault://stream/${file.id}/${Uri.encode(file.name)}")
            val itemBuilder = MediaItem.Builder().setUri(streamUri)
            resolveMedia3MimeType(file.mimeType, file.name)?.let { itemBuilder.setMimeType(it) }

            val dataSourceFactory = TelegramChunkDataSource.Factory(
                file = file,
                chunks = chunks,
                streamManager = streamManager
            )
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(itemBuilder.build())
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
        } else {
            playerError = "Waiting for cloud chunk metadata…"
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        durationMs = dur
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = error.cause?.message ?: error.message ?: "Audio stream error"
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
                streamManager.wipeAllEphemeralBuffers("App backgrounded during audio playback")
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, isUserScrubbing) {
        while (true) {
            if (!isUserScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur != C.TIME_UNSET && dur > 0L) {
                    durationMs = dur
                }
            }
            delay(200L)
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .border(1.dp, colors.line, RoundedCornerShape(24.dp))
            .testTag("zero_download_audio_player")
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
                if (isBuffering || bufferStats.isFetchingChunk) {
                    CircularProgressIndicator(
                        color = colors.teal,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = file.name,
                color = colors.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Zero-Download RAM Sliding Window Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceHi)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = if (file.isEncrypted) Icons.Default.Lock else Icons.Default.Memory,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${ChecksumUtil.formatBytes(file.size)} • ${formatRamWindowBadge(bufferStats)}",
                    color = colors.textDim,
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont
                )
            }

            if (playerError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = playerError ?: "",
                    color = colors.violet,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        val pos = currentPositionMs
                        retryNonce++
                        exoPlayer.seekTo(pos)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.teal,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh Link & Retry", fontSize = 12.sp)
                }
            } else {
                Spacer(modifier = Modifier.height(18.dp))

                val activeDisplayMs = if (isUserScrubbing && durationMs > 0L) {
                    (scrubFraction * durationMs).toLong()
                } else {
                    currentPositionMs
                }
                val sliderValue = if (isUserScrubbing) {
                    scrubFraction
                } else if (durationMs > 0L) {
                    (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Slider(
                    value = sliderValue,
                    onValueChange = { frac ->
                        isUserScrubbing = true
                        scrubFraction = frac.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0L) {
                            val targetMs = (scrubFraction * durationMs).toLong().coerceIn(0L, durationMs)
                            exoPlayer.seekTo(targetMs)
                            currentPositionMs = targetMs
                        }
                        isUserScrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = colors.teal,
                        activeTrackColor = colors.teal,
                        inactiveTrackColor = colors.surfaceHi
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preview_audio_seek_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatStreamTimecode(activeDisplayMs),
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = NumericMonoFont
                    )
                    Text(
                        text = bufferStats.statusLabel,
                        color = colors.teal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatStreamTimecode(durationMs),
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = NumericMonoFont
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        },
                        modifier = Modifier.testTag("preview_audio_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = colors.textDim
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier.testTag("preview_audio_rewind_button")
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
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0L)
                                    }
                                    exoPlayer.play()
                                }
                            }
                            .testTag("preview_audio_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val maxDur = if (durationMs > 0L) durationMs else Long.MAX_VALUE
                            val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier.testTag("preview_audio_ffwd_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = colors.text,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun formatStreamTimecode(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val min = (totalSec % 3600L) / 60L
    val sec = totalSec % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, min, sec)
    } else {
        "%d:%02d".format(min, sec)
    }
}

internal fun formatRamWindowBadge(stats: EphemeralBufferStats): String {
    val allocMb = stats.allocatedRamBytes.toDouble() / (1024.0 * 1024.0)
    val maxMb = stats.maxWindowBytes.toDouble() / (1024.0 * 1024.0)
    val refreshSuffix = if (stats.refreshedLinksCount > 0) " • ${stats.refreshedLinksCount}↻" else ""
    return "%.1f/%.0f MB RAM$refreshSuffix".format(allocMb, maxMb)
}

private fun resolveMedia3MimeType(mimeType: String, fileName: String): String? {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext == "mp4" || ext == "m4v" -> MimeTypes.VIDEO_MP4
        ext == "mkv" -> MimeTypes.VIDEO_MATROSKA
        ext == "webm" -> MimeTypes.VIDEO_WEBM
        ext == "mp3" -> MimeTypes.AUDIO_MPEG
        ext == "flac" -> MimeTypes.AUDIO_FLAC
        ext == "wav" -> MimeTypes.AUDIO_WAV
        ext == "ogg" || ext == "opus" -> MimeTypes.AUDIO_OGG
        ext == "m4a" || ext == "aac" -> MimeTypes.AUDIO_AAC
        mimeType.isNotBlank() && mimeType != "application/octet-stream" -> mimeType
        else -> null
    }
}
