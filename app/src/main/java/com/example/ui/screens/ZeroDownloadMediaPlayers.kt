package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
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
import java.util.Locale

/**
 * In-memory session-only preferences for selected audio and subtitle language.
 * Persists across videos during the current app viewing session without writing to disk.
 */
private object SessionMediaTrackPreferences {
    var preferredAudioLanguage: String? = null
    var preferredSubtitleLanguage: String? = null
    var subtitlesDisabled: Boolean = false
}

/**
 * Represents a selectable Audio or Subtitle track parsed from ExoPlayer's [Tracks].
 */
@OptIn(UnstableApi::class)
private data class MediaTrackOption(
    val id: String,
    val group: Tracks.Group,
    val trackIndex: Int,
    val title: String,
    val details: String?,
    val languageCode: String?,
    val isSelected: Boolean,
    val isSupported: Boolean
)

/**
 * Represents a side-loaded `.srt` or `.vtt` subtitle picked via the system document picker.
 */
private data class ExternalSubtitleItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val language: String
)

/**
 * Helper to toggle forced sensor landscape + immersive system bars on fullscreen enter,
 * and return to portrait (or unspecified on screen exit) + restore system bars on fullscreen exit.
 */
internal fun applyPlayerFullscreenSystemUi(
    activity: Activity?,
    fullscreen: Boolean,
    restoreUnspecifiedOrientation: Boolean = false
) {
    if (activity == null) return
    val window = activity.window ?: return
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)

    if (fullscreen) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = if (restoreUnspecifiedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}

/**
 * Phase 1 Zero-Download Media3 ExoPlayer for Video Streaming.
 *
 * Includes:
 * 1. Forced landscape fullscreen (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`) with immersive hidden system bars,
 *    and return to portrait (`SCREEN_ORIENTATION_PORTRAIT`) with system bars restored on exit/back.
 * 2. Audio Track & Subtitle Selection bottom sheets reading live from `player.currentTracks`
 *    (`TRACK_TYPE_AUDIO` & `TRACK_TYPE_TEXT`), live switching via `TrackSelectionOverride`,
 *    readable white-on-black-outline `SubtitleView` styling, session language memory, and
 *    zero-copy external `.srt`/`.vtt` side-loading via `MediaItem.SubtitleConfiguration`.
 * 3. Instant auto-play on chunk 1, real-time 100ms timecode & buffered range seek bar,
 *    3-second auto-hide controls, accurate waiting-for-data buffering card, single-retry link refresh,
 *    and immediate RAM wipe on exit/background.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
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
    onToggleOverlayControls: () -> Unit,
    onSetOverlayControls: (Boolean) -> Unit = {},
    isFullscreen: Boolean = false,
    onFullscreenChange: (Boolean) -> Unit = {}
) {
    val colors = LocalTeleVaultColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isLandscapeOrFullscreen = isLandscape || isFullscreen

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasRenderedFirstFrame by remember(file.id) { mutableStateOf(false) }
    var seekingUncachedChunkIndex by remember(file.id) { mutableStateOf<Int?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }
    var resumePositionOnRetryMs by remember { mutableLongStateOf(0L) }
    var resumePlayWhenReadyOnReload by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }

    // Audio & Subtitle track state
    var audioTracks by remember(file.id) { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var subtitleTracks by remember(file.id) { mutableStateOf<List<MediaTrackOption>>(emptyList()) }
    var isSubtitlesOff by remember(file.id) { mutableStateOf(SessionMediaTrackPreferences.subtitlesDisabled) }
    var externalSubtitles by remember(file.id) { mutableStateOf<List<ExternalSubtitleItem>>(emptyList()) }
    var pendingSelectLatestExternalSub by remember(file.id) { mutableStateOf(false) }
    var hasAppliedInitialSessionPrefs by remember(file.id) { mutableStateOf(false) }

    // Bottom sheet visibility
    var isAudioSheetOpen by remember { mutableStateOf(false) }
    var isSubtitleSheetOpen by remember { mutableStateOf(false) }

    val exoPlayer = remember(file.id, chunks.size, isFullFileDownloaded) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 4_000,
                /* maxBufferMs = */ 20_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setTargetBufferBytes(16 * 1024 * 1024) // Keep ExoPlayer internal ring buffer <= 16 MB
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                // Apply session-level audio/subtitle preferences initially
                val paramsBuilder = trackSelectionParameters.buildUpon()
                SessionMediaTrackPreferences.preferredAudioLanguage?.let {
                    paramsBuilder.setPreferredAudioLanguage(it)
                }
                if (SessionMediaTrackPreferences.subtitlesDisabled) {
                    paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    SessionMediaTrackPreferences.preferredSubtitleLanguage?.let {
                        paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        paramsBuilder.setPreferredTextLanguage(it)
                    }
                }
                trackSelectionParameters = paramsBuilder.build()
            }
    }

    // Helper to refresh audioTracks & subtitleTracks from exoPlayer.currentTracks
    val refreshTracksFromPlayer: (Tracks) -> Unit = { currentTracks ->
        val parsedAudio = parseAudioTracks(currentTracks)
        val parsedSubtitles = parseSubtitleTracks(currentTracks)
        audioTracks = parsedAudio
        subtitleTracks = parsedSubtitles
        val trackTypeDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val anySubSelected = parsedSubtitles.any { it.isSelected }
        isSubtitlesOff = trackTypeDisabled || !anySubSelected
    }

    // External .srt / .vtt document picker (reads directly through content:// URI without copying to disk)
    val subtitleDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri: Uri? ->
        if (pickedUri != null) {
            val displayName = resolveDocumentDisplayName(context, pickedUri) ?: "External Subtitle"
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val resolverMime = context.contentResolver.getType(pickedUri)?.lowercase().orEmpty()
            val isVtt = ext == "vtt" || resolverMime.contains("vtt")
            val isSrt = ext == "srt" || resolverMime.contains("subrip") || resolverMime.contains("srt")

            if (!isVtt && !isSrt && !displayName.endsWith(".srt", true) && !displayName.endsWith(".vtt", true)) {
                Toast.makeText(context, "Please select a .srt or .vtt subtitle file", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            val subtitleMime = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            val newSubItem = ExternalSubtitleItem(
                uri = pickedUri,
                displayName = displayName,
                mimeType = subtitleMime,
                language = displayName.substringBeforeLast('.')
            )

            // Preserve current playback position & play state when attaching side-loaded subtitle
            resumePositionOnRetryMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            resumePlayWhenReadyOnReload = exoPlayer.playWhenReady
            SessionMediaTrackPreferences.subtitlesDisabled = false
            isSubtitlesOff = false
            pendingSelectLatestExternalSub = true
            externalSubtitles = externalSubtitles.filterNot { it.uri == pickedUri } + newSubItem
            interactionTick++
        }
    }

    // Bind media source (Zero-Download TelegramChunkDataSource or local file) + side-loaded SubtitleConfigurations
    LaunchedEffect(exoPlayer, file.id, chunks, isFullFileDownloaded, retryNonce, externalSubtitles) {
        playerError = null
        isBuffering = true
        if (retryNonce == 0 && externalSubtitles.isEmpty()) {
            hasRenderedFirstFrame = false
        }

        val subtitleConfigs = externalSubtitles.mapIndexed { idx, sub ->
            val isLatest = idx == externalSubtitles.lastIndex
            MediaItem.SubtitleConfiguration.Builder(sub.uri)
                .setMimeType(sub.mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.displayName)
                .setSelectionFlags(if (isLatest) C.SELECTION_FLAG_DEFAULT else 0)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()
        }

        if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
            val localUri = if (localTarget.startsWith("/")) Uri.fromFile(File(localTarget)) else Uri.parse(localTarget)
            val itemBuilder = MediaItem.Builder()
                .setUri(localUri)
                .setSubtitleConfigurations(subtitleConfigs)
            resolveMedia3MimeType(file.mimeType, file.name)?.let { itemBuilder.setMimeType(it) }

            val mediaSource = DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
                .createMediaSource(itemBuilder.build())
            if (resumePositionOnRetryMs > 0L) {
                exoPlayer.setMediaSource(mediaSource, resumePositionOnRetryMs)
            } else {
                exoPlayer.setMediaSource(mediaSource)
            }
            exoPlayer.playWhenReady = resumePlayWhenReadyOnReload
            exoPlayer.prepare()
            if (resumePlayWhenReadyOnReload) {
                exoPlayer.play()
            }
        } else if (chunks.isNotEmpty()) {
            val streamUri = Uri.parse("televault://stream/${file.id}/${Uri.encode(file.name)}")
            val itemBuilder = MediaItem.Builder()
                .setUri(streamUri)
                .setSubtitleConfigurations(subtitleConfigs)
            resolveMedia3MimeType(file.mimeType, file.name)?.let { itemBuilder.setMimeType(it) }

            val dataSourceFactory = TelegramChunkDataSource.Factory(
                file = file,
                chunks = chunks,
                streamManager = streamManager,
                context = context
            )
            // Use DefaultMediaSourceFactory when side-loaded subtitles are attached so Media3 merges
            // SubtitleConfigurations automatically; disable internal retry loops so EphemeralStreamManager's
            // single retry (with Telegram link refresh) is authoritative.
            val errorPolicy = DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 0)
            val mediaSource = if (subtitleConfigs.isNotEmpty()) {
                DefaultMediaSourceFactory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(errorPolicy)
                    .createMediaSource(itemBuilder.build())
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(errorPolicy)
                    .createMediaSource(itemBuilder.build())
            }

            if (resumePositionOnRetryMs > 0L) {
                exoPlayer.setMediaSource(mediaSource, resumePositionOnRetryMs)
            } else {
                exoPlayer.setMediaSource(mediaSource)
            }
            exoPlayer.playWhenReady = resumePlayWhenReadyOnReload
            exoPlayer.prepare()
            if (resumePlayWhenReadyOnReload) {
                exoPlayer.play()
            }
        } else {
            isBuffering = false
            playerError = "Waiting for cloud chunk metadata…"
        }
    }

    // Player listener & lifecycle observer (stop & wipe RAM buffer on background / exit)
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    seekingUncachedChunkIndex = null
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        durationMs = dur
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    seekingUncachedChunkIndex = null
                    onSetOverlayControls(true)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                refreshTracksFromPlayer(tracks)

                // If a side-loaded subtitle was just added, immediately select it
                if (pendingSelectLatestExternalSub) {
                    val parsedSubs = parseSubtitleTracks(tracks)
                    val targetSub = parsedSubs.lastOrNull()
                    if (targetSub != null) {
                        pendingSelectLatestExternalSub = false
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(targetSub.group.mediaTrackGroup, targetSub.trackIndex)
                            )
                            .build()
                        isSubtitlesOff = false
                        refreshTracksFromPlayer(exoPlayer.currentTracks)
                    }
                } else if (!hasAppliedInitialSessionPrefs) {
                    hasAppliedInitialSessionPrefs = true
                    var updatedParams = exoPlayer.trackSelectionParameters.buildUpon()
                    var changed = false

                    val prefAudioLang = SessionMediaTrackPreferences.preferredAudioLanguage
                    if (!prefAudioLang.isNullOrBlank()) {
                        val matchingAudio = parseAudioTracks(tracks).firstOrNull {
                            it.languageCode.equals(prefAudioLang, ignoreCase = true)
                        }
                        if (matchingAudio != null) {
                            updatedParams = updatedParams
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(matchingAudio.group.mediaTrackGroup, matchingAudio.trackIndex)
                                )
                            changed = true
                        }
                    }

                    if (SessionMediaTrackPreferences.subtitlesDisabled) {
                        updatedParams = updatedParams
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        changed = true
                    } else {
                        val prefSubLang = SessionMediaTrackPreferences.preferredSubtitleLanguage
                        if (!prefSubLang.isNullOrBlank()) {
                            val matchingSub = parseSubtitleTracks(tracks).firstOrNull {
                                it.languageCode.equals(prefSubLang, ignoreCase = true)
                            }
                            if (matchingSub != null) {
                                updatedParams = updatedParams
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(matchingSub.group.mediaTrackGroup, matchingSub.trackIndex)
                                    )
                                changed = true
                            }
                        }
                    }

                    if (changed) {
                        exoPlayer.trackSelectionParameters = updatedParams.build()
                        refreshTracksFromPlayer(exoPlayer.currentTracks)
                    }
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                seekingUncachedChunkIndex = null
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    seekingUncachedChunkIndex = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isPlaying = false
                seekingUncachedChunkIndex = null
                val rawMessage = error.cause?.cause?.message
                    ?: error.cause?.message
                    ?: error.message
                    ?: "Chunk download or decryption failed."
                playerError = rawMessage
                onSetOverlayControls(true)
            }
        }
        exoPlayer.addListener(listener)
        refreshTracksFromPlayer(exoPlayer.currentTracks)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activity = context as? Activity
                if (activity?.isChangingConfigurations != true) {
                    exoPlayer.pause()
                    exoPlayer.stop()
                    streamManager.wipeAllEphemeralBuffers("App backgrounded during video playback")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
            streamManager.wipeAllEphemeralBuffers("Left video player for ${file.name}")
            applyPlayerFullscreenSystemUi(
                activity = context as? Activity,
                fullscreen = false,
                restoreUnspecifiedOrientation = true
            )
        }
    }

    // Real-time 100ms polling for current time (0:00), duration, and buffered position on seek bar
    LaunchedEffect(exoPlayer) {
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val bufPos = exoPlayer.bufferedPosition.coerceAtLeast(pos)
            val dur = exoPlayer.duration

            if (!isUserScrubbing) {
                currentPositionMs = pos
            }
            bufferedPositionMs = bufPos
            if (dur != C.TIME_UNSET && dur > 0L) {
                durationMs = dur
            }
            if (exoPlayer.isPlaying && pos > 0L) {
                hasRenderedFirstFrame = true
                seekingUncachedChunkIndex = null
            }
            val pendingSeekChunk = seekingUncachedChunkIndex
            if (pendingSeekChunk != null && streamManager.isChunkCachedInRam(file.id, pendingSeekChunk) && exoPlayer.playbackState == Player.STATE_READY) {
                seekingUncachedChunkIndex = null
            }
            delay(100L)
        }
    }

    // Determine whether the player is genuinely waiting for data
    val isWaitingForData = playerError == null && (
        !hasRenderedFirstFrame ||
            isBuffering ||
            seekingUncachedChunkIndex != null
        )

    val isAnySheetOpen = isAudioSheetOpen || isSubtitleSheetOpen

    // Keep controls visible while paused, buffering, scrubbing, on error, or while a bottom sheet is open;
    // auto-hide controls and top bar after 3 seconds of active playback
    LaunchedEffect(
        isPlaying,
        isWaitingForData,
        isUserScrubbing,
        playerError,
        isAnySheetOpen,
        showOverlayControls,
        interactionTick
    ) {
        if (!isPlaying || isWaitingForData || isUserScrubbing || playerError != null || isAnySheetOpen) {
            if (!showOverlayControls) {
                onSetOverlayControls(true)
            }
        } else if (showOverlayControls) {
            delay(3_000L)
            onSetOverlayControls(false)
        }
    }

    // Helper to perform seek and detect if target chunk is not yet in RAM
    val performSeekToMs: (Long) -> Unit = { targetMs ->
        val safeTargetMs = if (durationMs > 0L) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        if (!isFullFileDownloaded && chunks.isNotEmpty() && durationMs > 0L) {
            val totalBytes = streamManager.computeTotalPlaintextSize(file, chunks)
            val approxBytePos = ((safeTargetMs.toDouble() / durationMs.toDouble()) * totalBytes.toDouble())
                .toLong()
                .coerceIn(0L, (totalBytes - 1L).coerceAtLeast(0L))
            val targetChunkIdx = streamManager.getChunkIndexForPosition(file, chunks, approxBytePos)
            if (!streamManager.isChunkCachedInRam(file.id, targetChunkIdx)) {
                seekingUncachedChunkIndex = targetChunkIdx
            } else {
                seekingUncachedChunkIndex = null
            }
        }
        currentPositionMs = safeTargetMs
        exoPlayer.seekTo(safeTargetMs)
        if (!exoPlayer.isPlaying && playerError == null) {
            exoPlayer.play()
        }
        interactionTick++
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
                    // Configure Media3 SubtitleView with readable defaults:
                    // White text, crisp black outline, transparent background, bottom aligned
                    subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                Typeface.DEFAULT_BOLD
                            )
                        )
                        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        setBottomPaddingFraction(0.08f)
                        setApplyEmbeddedStyles(true)
                        setApplyEmbeddedFontSizes(true)
                    }
                    player = exoPlayer
                    setOnClickListener {
                        interactionTick++
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

        // Buffering UI: Show "Buffering Chunk X/Y in RAM..." card ONLY while waiting for data
        if (isWaitingForData) {
            val totalChunks = chunks.size.coerceAtLeast(1)
            val activeChunkOneBased = ((seekingUncachedChunkIndex ?: bufferStats.fetchingChunkIndex ?: 0) + 1)
                .coerceIn(1, totalChunks)
            val bufferingTitle = "Buffering Chunk $activeChunkOneBased/$totalChunks in RAM…"

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.80f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("video_buffering_card")
            ) {
                CircularProgressIndicator(
                    color = colors.teal,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = bufferingTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatRamWindowBadge(bufferStats),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont
                )
            }
        }

        // Stream Error Recovery Overlay (after 1 automatic link-refresh retry fails)
        if (playerError != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.88f))
                    .border(1.dp, colors.violet.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
                    .testTag("video_error_card")
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = colors.violet,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chunk Stream Failed",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = playerError ?: "Unable to download or decrypt chunk after refreshing Telegram link.",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                FilledTonalButton(
                    onClick = {
                        resumePositionOnRetryMs = currentPositionMs
                        resumePlayWhenReadyOnReload = true
                        playerError = null
                        isBuffering = true
                        interactionTick++
                        retryNonce++
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.teal,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("video_retry_stream_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Bottom-Pinned Video Transport & Seek Controls Overlay (never covers the middle of the video)
        AnimatedVisibility(
            visible = showOverlayControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    bottom = if (isLandscapeOrFullscreen) 12.dp else 76.dp,
                    start = if (isLandscapeOrFullscreen) 24.dp else 14.dp,
                    end = if (isLandscapeOrFullscreen) 24.dp else 14.dp
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.80f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
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

                val bufferedFraction = computeBufferedFraction(
                    fileId = file.id,
                    totalChunks = chunks.size,
                    streamManager = streamManager,
                    currentPositionMs = currentPositionMs,
                    bufferedPositionMs = bufferedPositionMs,
                    durationMs = durationMs
                )

                // Dual-layer Seek Bar showing Buffered Position + Active Playback Position
                BufferedStreamSeekBar(
                    value = sliderValue,
                    bufferedFraction = bufferedFraction,
                    activeColor = colors.teal,
                    bufferedColor = colors.teal.copy(alpha = 0.45f),
                    inactiveColor = Color.White.copy(alpha = 0.20f),
                    onValueChange = { frac ->
                        isUserScrubbing = true
                        scrubFraction = frac.coerceIn(0f, 1f)
                        interactionTick++
                    },
                    onValueChangeFinished = {
                        val targetMs = if (durationMs > 0L) {
                            (scrubFraction * durationMs).toLong().coerceIn(0L, durationMs)
                        } else 0L
                        isUserScrubbing = false
                        performSeekToMs(targetMs)
                    },
                    testTag = "preview_video_seek_slider"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatStreamTimecode(activeDisplayMs),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont
                    )
                    Text(
                        text = "${formatRamWindowBadge(bufferStats)} • Buf ${(bufferedFraction * 100).toInt()}%",
                        color = colors.teal,
                        fontSize = 10.sp,
                        fontFamily = NumericMonoFont,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatStreamTimecode(durationMs),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Compact Transport & Track Selection Row:
                // Mute | Audio Tracks | -10s | Play/Pause | +10s | Subtitles (CC) | Fullscreen
                val hasActiveSubtitles = !isSubtitlesOff && subtitleTracks.any { it.isSelected }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left group: Mute + Audio Track Selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                                interactionTick++
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("preview_video_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                interactionTick++
                                refreshTracksFromPlayer(exoPlayer.currentTracks)
                                isAudioSheetOpen = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("preview_video_audio_tracks_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Audio Tracks",
                                tint = if (audioTracks.size > 1) colors.teal else Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    // Center transport group: -10s | Play/Pause | +10s
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                performSeekToMs(target)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("preview_video_rewind_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(colors.teal)
                                .clickable {
                                    interactionTick++
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                            performSeekToMs(0L)
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
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val maxDur = if (durationMs > 0L) durationMs else Long.MAX_VALUE
                                val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                performSeekToMs(target)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("preview_video_ffwd_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Fast-forward 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Right group: Subtitles (CC) + Fullscreen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                interactionTick++
                                refreshTracksFromPlayer(exoPlayer.currentTracks)
                                isSubtitleSheetOpen = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("preview_video_subtitles_button")
                        ) {
                            Icon(
                                imageVector = if (hasActiveSubtitles) {
                                    Icons.Default.ClosedCaption
                                } else {
                                    Icons.Default.Subtitles
                                },
                                contentDescription = "Subtitles",
                                tint = if (hasActiveSubtitles) colors.teal else Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                interactionTick++
                                val targetFullscreen = !isLandscapeOrFullscreen
                                onFullscreenChange(targetFullscreen)
                                applyPlayerFullscreenSystemUi(
                                    activity = context as? Activity,
                                    fullscreen = targetFullscreen,
                                    restoreUnspecifiedOrientation = false
                                )
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("preview_video_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = if (isLandscapeOrFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isLandscapeOrFullscreen) "Exit Fullscreen" else "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Audio Track Selection Bottom Sheet (works in both portrait and fullscreen landscape)
    if (isAudioSheetOpen) {
        val audioSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                isAudioSheetOpen = false
                interactionTick++
            },
            sheetState = audioSheetState,
            containerColor = colors.surface,
            contentColor = colors.text,
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("audio_tracks_bottom_sheet")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Audio Tracks",
                            color = colors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Switch embedded audio streams without restarting playback",
                            color = colors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = colors.line)
                Spacer(modifier = Modifier.height(10.dp))

                if (audioTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceHi)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No embedded audio tracks in this file",
                            color = colors.textDim,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    audioTracks.forEachIndexed { index, track ->
                        TrackSelectionRow(
                            title = track.title,
                            subtitle = track.details,
                            isSelected = track.isSelected,
                            isEnabled = track.isSupported,
                            onClick = {
                                SessionMediaTrackPreferences.preferredAudioLanguage = track.languageCode
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex)
                                    )
                                    .build()
                                refreshTracksFromPlayer(exoPlayer.currentTracks)
                                isAudioSheetOpen = false
                                interactionTick++
                            },
                            testTag = "audio_track_option_$index"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    // Subtitle Track Selection Bottom Sheet (works in both portrait and fullscreen landscape)
    if (isSubtitleSheetOpen) {
        val subtitleSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                isSubtitleSheetOpen = false
                interactionTick++
            },
            sheetState = subtitleSheetState,
            containerColor = colors.surface,
            contentColor = colors.text,
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("subtitle_tracks_bottom_sheet")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Subtitles & Captions",
                            color = colors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Embedded tracks (SRT, ASS/SSA, VTT, PGS) or side-loaded files",
                            color = colors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = colors.line)
                Spacer(modifier = Modifier.height(10.dp))

                // "Off" option
                TrackSelectionRow(
                    title = "Off",
                    subtitle = "Disable subtitle display",
                    isSelected = isSubtitlesOff,
                    isEnabled = true,
                    onClick = {
                        SessionMediaTrackPreferences.subtitlesDisabled = true
                        isSubtitlesOff = true
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .build()
                        refreshTracksFromPlayer(exoPlayer.currentTracks)
                        isSubtitleSheetOpen = false
                        interactionTick++
                    },
                    testTag = "subtitle_track_option_off"
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (subtitleTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceHi)
                            .padding(16.dp)
                            .testTag("no_subtitles_message"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaptionDisabled,
                                contentDescription = null,
                                tint = colors.textDim,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "No subtitles in this file",
                                color = colors.textDim,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    subtitleTracks.forEachIndexed { index, track ->
                        TrackSelectionRow(
                            title = track.title,
                            subtitle = track.details,
                            isSelected = !isSubtitlesOff && track.isSelected,
                            isEnabled = track.isSupported,
                            onClick = {
                                SessionMediaTrackPreferences.subtitlesDisabled = false
                                SessionMediaTrackPreferences.preferredSubtitleLanguage = track.languageCode
                                isSubtitlesOff = false
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex)
                                    )
                                    .build()
                                refreshTracksFromPlayer(exoPlayer.currentTracks)
                                isSubtitleSheetOpen = false
                                interactionTick++
                            },
                            testTag = "subtitle_track_option_$index"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.line)
                Spacer(modifier = Modifier.height(12.dp))

                // "Load subtitle file…" button (.srt or .vtt via system document picker)
                OutlinedButton(
                    onClick = {
                        isSubtitleSheetOpen = false
                        interactionTick++
                        subtitleDocumentLauncher.launch(
                            arrayOf(
                                "application/x-subrip",
                                "text/vtt",
                                "text/plain",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("load_external_subtitle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Load subtitle file… (.srt / .vtt)",
                        color = colors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackSelectionRow(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = LocalTeleVaultColors.current
    val bgColor = if (isSelected) colors.teal.copy(alpha = 0.14f) else colors.surfaceHi
    val borderColor = if (isSelected) colors.teal.copy(alpha = 0.55f) else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isEnabled) colors.text else colors.textDim.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.textDim,
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isSelected) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.teal),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Parses all `TRACK_TYPE_AUDIO` tracks from [Tracks], formatting human-readable labels like
 * `"Japanese – AAC 2.0"` or `"English – AC3 5.1"`.
 */
@OptIn(UnstableApi::class)
private fun parseAudioTracks(tracks: Tracks): List<MediaTrackOption> {
    val result = mutableListOf<MediaTrackOption>()
    var counter = 1
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        val mediaGroup = group.mediaTrackGroup
        for (i in 0 until mediaGroup.length) {
            val format = mediaGroup.getFormat(i)
            val langDisplay = formatLanguageDisplay(format.language)
            val labelText = format.label?.takeIf { it.isNotBlank() && !it.equals(langDisplay, ignoreCase = true) }
            val primaryName = when {
                langDisplay != null && labelText != null -> "$langDisplay ($labelText)"
                langDisplay != null -> langDisplay
                labelText != null -> labelText
                else -> "Audio Track $counter"
            }

            val codecName = formatAudioCodecName(format)
            val channelLayout = formatChannelLayout(format.channelCount)
            val codecAndChannels = listOfNotNull(codecName, channelLayout).joinToString(" ")
            val fullTitle = if (codecAndChannels.isNotBlank()) {
                "$primaryName – $codecAndChannels"
            } else {
                primaryName
            }

            val extraParts = mutableListOf<String>()
            if (format.sampleRate != Format.NO_VALUE && format.sampleRate > 0) {
                extraParts.add("${format.sampleRate / 1000.0} kHz")
            }
            if (format.bitrate != Format.NO_VALUE && format.bitrate > 0) {
                extraParts.add("${format.bitrate / 1000} kbps")
            }
            if (!group.isTrackSupported(i)) {
                extraParts.add("Unsupported codec")
            }

            result.add(
                MediaTrackOption(
                    id = "${mediaGroup.id}_$i",
                    group = group,
                    trackIndex = i,
                    title = fullTitle,
                    details = extraParts.joinToString(" • ").ifBlank { null },
                    languageCode = format.language,
                    isSelected = group.isTrackSelected(i),
                    isSupported = group.isTrackSupported(i)
                )
            )
            counter++
        }
    }
    return result
}

/**
 * Parses all `TRACK_TYPE_TEXT` subtitle tracks from [Tracks], supporting embedded SRT, ASS/SSA,
 * VTT, PGS, TTML, and side-loaded `.srt`/`.vtt` tracks.
 */
@OptIn(UnstableApi::class)
private fun parseSubtitleTracks(tracks: Tracks): List<MediaTrackOption> {
    val result = mutableListOf<MediaTrackOption>()
    var counter = 1
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        val mediaGroup = group.mediaTrackGroup
        for (i in 0 until mediaGroup.length) {
            val format = mediaGroup.getFormat(i)
            val langDisplay = formatLanguageDisplay(format.language)
            val labelText = format.label?.takeIf { it.isNotBlank() && !it.equals(langDisplay, ignoreCase = true) }
            val primaryName = when {
                langDisplay != null && labelText != null -> "$langDisplay – $labelText"
                labelText != null -> labelText
                langDisplay != null -> langDisplay
                else -> "Subtitle Track $counter"
            }

            val subFormatBadge = formatSubtitleCodecBadge(format.sampleMimeType ?: format.codecs)
            val flagsList = mutableListOf<String>()
            if (subFormatBadge != null) flagsList.add(subFormatBadge)
            if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) flagsList.add("Forced")
            if ((format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0) flagsList.add("Default")
            if ((format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) flagsList.add("SDH")

            result.add(
                MediaTrackOption(
                    id = "${mediaGroup.id}_$i",
                    group = group,
                    trackIndex = i,
                    title = primaryName,
                    details = flagsList.joinToString(" • ").ifBlank { null },
                    languageCode = format.language,
                    isSelected = group.isTrackSelected(i),
                    isSupported = group.isTrackSupported(i)
                )
            )
            counter++
        }
    }
    return result
}

private fun formatLanguageDisplay(langCode: String?): String? {
    val clean = langCode?.trim()?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) } ?: return null
    val locale = Locale.forLanguageTag(clean.replace('_', '-'))
    val display = locale.getDisplayLanguage(Locale.ENGLISH)
    return if (display.isNotBlank() && !display.equals(clean, ignoreCase = true)) {
        display.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
    } else {
        clean.uppercase(Locale.ENGLISH)
    }
}

@OptIn(UnstableApi::class)
private fun formatAudioCodecName(format: Format): String? {
    val mime = format.sampleMimeType?.lowercase().orEmpty()
    val codecs = format.codecs?.lowercase().orEmpty()
    return when {
        mime.contains("eac3-joc") || codecs.contains("ec+3") -> "Dolby Atmos"
        mime.contains("eac3") || codecs.startsWith("ec-3") -> "E-AC3"
        mime.contains("ac3") || codecs.startsWith("ac-3") -> "AC3"
        mime.contains("truehd") -> "TrueHD"
        mime.contains("vnd.dts.hd") -> "DTS-HD"
        mime.contains("vnd.dts") -> "DTS"
        mime.contains("mp4a") || mime.contains("aac") || codecs.startsWith("mp4a") -> "AAC"
        mime.contains("opus") || codecs.contains("opus") -> "Opus"
        mime.contains("vorbis") || codecs.contains("vorbis") -> "Vorbis"
        mime.contains("flac") -> "FLAC"
        mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
        mime.contains("raw") || mime.contains("pcm") -> "PCM"
        else -> null
    }
}

@OptIn(UnstableApi::class)
private fun formatChannelLayout(channelCount: Int): String? {
    if (channelCount == Format.NO_VALUE || channelCount <= 0) return null
    return when (channelCount) {
        1 -> "1.0"
        2 -> "2.0"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channelCount}ch"
    }
}

private fun formatSubtitleCodecBadge(mimeOrCodec: String?): String? {
    val lower = mimeOrCodec?.lowercase() ?: return null
    return when {
        lower.contains("subrip") || lower.contains("srt") -> "SRT"
        lower.contains("vtt") -> "VTT"
        lower.contains("ssa") || lower.contains("ass") -> "ASS/SSA"
        lower.contains("pgs") -> "PGS"
        lower.contains("ttml") -> "TTML"
        lower.contains("tx3g") || lower.contains("mp4vtt") -> "TX3G"
        lower.contains("dvbsubs") -> "DVB"
        else -> null
    }
}

private fun resolveDocumentDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    } catch (_: Exception) {
        uri.lastPathSegment?.substringAfterLast('/')
    }
}

/**
 * Phase 1 Zero-Download Media3 ExoPlayer Card for Audio Streaming.
 *
 * Automatically starts playback as soon as Chunk 1 loads, updates timecodes and buffered position
 * in real time, retries once with a fresh Telegram link on chunk error, and wipes RAM on exit/background.
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
    var isBuffering by remember { mutableStateOf(true) }
    var seekingUncachedChunkIndex by remember(file.id) { mutableStateOf<Int?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }
    var resumePositionOnRetryMs by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember(file.id, chunks.size, isFullFileDownloaded) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 4_000,
                /* maxBufferMs = */ 20_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setTargetBufferBytes(12 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(exoPlayer, file.id, chunks, isFullFileDownloaded, retryNonce) {
        playerError = null
        isBuffering = true
        if (isFullFileDownloaded && !localTarget.isNullOrBlank()) {
            val localUri = if (localTarget.startsWith("/")) Uri.fromFile(File(localTarget)) else Uri.parse(localTarget)
            val itemBuilder = MediaItem.Builder().setUri(localUri)
            resolveMedia3MimeType(file.mimeType, file.name)?.let { itemBuilder.setMimeType(it) }
            exoPlayer.setMediaItem(itemBuilder.build())
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
            if (resumePositionOnRetryMs > 0L) {
                exoPlayer.seekTo(resumePositionOnRetryMs)
            }
            exoPlayer.play()
        } else if (chunks.isNotEmpty()) {
            val streamUri = Uri.parse("televault://stream/${file.id}/${Uri.encode(file.name)}")
            val itemBuilder = MediaItem.Builder().setUri(streamUri)
            resolveMedia3MimeType(file.mimeType, file.name)?.let { itemBuilder.setMimeType(it) }

            val dataSourceFactory = TelegramChunkDataSource.Factory(
                file = file,
                chunks = chunks,
                streamManager = streamManager,
                context = context
            )
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 0))
                .createMediaSource(itemBuilder.build())
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
            if (resumePositionOnRetryMs > 0L) {
                exoPlayer.seekTo(resumePositionOnRetryMs)
            }
            exoPlayer.play()
        } else {
            isBuffering = false
            playerError = "Waiting for cloud chunk metadata…"
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    seekingUncachedChunkIndex = null
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        durationMs = dur
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    seekingUncachedChunkIndex = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    seekingUncachedChunkIndex = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isPlaying = false
                seekingUncachedChunkIndex = null
                playerError = error.cause?.cause?.message
                    ?: error.cause?.message
                    ?: error.message
                    ?: "Audio chunk download or decryption failed."
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activity = context as? Activity
                if (activity?.isChangingConfigurations != true) {
                    exoPlayer.pause()
                    exoPlayer.stop()
                    streamManager.wipeAllEphemeralBuffers("App backgrounded during audio playback")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
            streamManager.wipeAllEphemeralBuffers("Left audio player for ${file.name}")
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val bufPos = exoPlayer.bufferedPosition.coerceAtLeast(pos)
            val dur = exoPlayer.duration

            if (!isUserScrubbing) {
                currentPositionMs = pos
            }
            bufferedPositionMs = bufPos
            if (dur != C.TIME_UNSET && dur > 0L) {
                durationMs = dur
            }
            if (exoPlayer.isPlaying && pos > 0L) {
                seekingUncachedChunkIndex = null
            }
            delay(100L)
        }
    }

    val performAudioSeekToMs: (Long) -> Unit = { targetMs ->
        val safeTargetMs = if (durationMs > 0L) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        if (!isFullFileDownloaded && chunks.isNotEmpty() && durationMs > 0L) {
            val totalBytes = streamManager.computeTotalPlaintextSize(file, chunks)
            val approxBytePos = ((safeTargetMs.toDouble() / durationMs.toDouble()) * totalBytes.toDouble())
                .toLong()
                .coerceIn(0L, (totalBytes - 1L).coerceAtLeast(0L))
            val targetChunkIdx = streamManager.getChunkIndexForPosition(file, chunks, approxBytePos)
            if (!streamManager.isChunkCachedInRam(file.id, targetChunkIdx)) {
                seekingUncachedChunkIndex = targetChunkIdx
            } else {
                seekingUncachedChunkIndex = null
            }
        }
        currentPositionMs = safeTargetMs
        exoPlayer.seekTo(safeTargetMs)
        if (!exoPlayer.isPlaying && playerError == null) {
            exoPlayer.play()
        }
    }

    val isWaitingForAudioData = playerError == null && (isBuffering || seekingUncachedChunkIndex != null)

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
                if (isWaitingForAudioData) {
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

            if (isWaitingForAudioData) {
                val totalChunks = chunks.size.coerceAtLeast(1)
                val activeChunkOneBased = ((seekingUncachedChunkIndex ?: bufferStats.fetchingChunkIndex ?: 0) + 1)
                    .coerceIn(1, totalChunks)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Buffering Chunk $activeChunkOneBased/$totalChunks in RAM…",
                    color = colors.teal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (playerError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = playerError ?: "Audio stream interrupted.",
                    color = colors.violet,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        resumePositionOnRetryMs = currentPositionMs
                        playerError = null
                        isBuffering = true
                        retryNonce++
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.teal,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("audio_retry_stream_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

                val bufferedFraction = computeBufferedFraction(
                    fileId = file.id,
                    totalChunks = chunks.size,
                    streamManager = streamManager,
                    currentPositionMs = currentPositionMs,
                    bufferedPositionMs = bufferedPositionMs,
                    durationMs = durationMs
                )

                BufferedStreamSeekBar(
                    value = sliderValue,
                    bufferedFraction = bufferedFraction,
                    activeColor = colors.teal,
                    bufferedColor = colors.teal.copy(alpha = 0.38f),
                    inactiveColor = colors.surfaceHi,
                    onValueChange = { frac ->
                        isUserScrubbing = true
                        scrubFraction = frac.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        val targetMs = if (durationMs > 0L) {
                            (scrubFraction * durationMs).toLong().coerceIn(0L, durationMs)
                        } else 0L
                        isUserScrubbing = false
                        performAudioSeekToMs(targetMs)
                    },
                    testTag = "preview_audio_seek_slider"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatStreamTimecode(activeDisplayMs),
                        color = colors.textDim,
                        fontSize = 12.sp,
                        fontFamily = NumericMonoFont
                    )
                    Text(
                        text = "Buffered ${(bufferedFraction * 100).toInt()}%",
                        color = colors.teal,
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont,
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
                            performAudioSeekToMs(target)
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
                                        performAudioSeekToMs(0L)
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
                            performAudioSeekToMs(target)
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

/**
 * Dual-layer seek bar that renders the buffered position track behind the interactive playback Slider.
 */
@Composable
private fun BufferedStreamSeekBar(
    value: Float,
    bufferedFraction: Float,
    activeColor: Color,
    bufferedColor: Color,
    inactiveColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        // Background track + Buffered progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(inactiveColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(bufferedColor)
            )
        }

        // Foreground interactive Slider
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .testTag(testTag)
        )
    }
}

private fun computeBufferedFraction(
    fileId: String,
    totalChunks: Int,
    streamManager: EphemeralStreamManager,
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long
): Float {
    val exoBufferedFraction = if (durationMs > 0L) {
        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    if (totalChunks <= 0 || durationMs <= 0L) return exoBufferedFraction

    val playFraction = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val currentChunkIdx = (playFraction * totalChunks).toInt().coerceIn(0, totalChunks - 1)
    var contiguousCachedEndChunk = currentChunkIdx
    while (contiguousCachedEndChunk < totalChunks && streamManager.isChunkCachedInRam(fileId, contiguousCachedEndChunk)) {
        contiguousCachedEndChunk++
    }
    val ramChunkFraction = if (contiguousCachedEndChunk > currentChunkIdx) {
        (contiguousCachedEndChunk.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
    } else 0f

    return maxOf(exoBufferedFraction, ramChunkFraction, playFraction).coerceIn(0f, 1f)
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
