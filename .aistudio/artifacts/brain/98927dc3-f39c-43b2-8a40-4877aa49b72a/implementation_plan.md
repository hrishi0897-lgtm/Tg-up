# Phase 1 Polish + Fullscreen Orientation & Audio/Subtitle Selection Plan

This update completes all Phase 1 polish requirements and adds **forced landscape immersive fullscreen** and **audio/subtitle track selection (with external `.srt`/`.vtt` side-loading)** without starting the rest of Phase 2.

---

## 1. Fullscreen Orientation & Immersive Mode (Independent of Phone Posture)
- **Force Sensor Landscape on Fullscreen Enter**:
  - Tapping the Fullscreen button sets `activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, immediately rotating into landscape regardless of how the phone is held or whether system Auto-Rotate is disabled, while allowing flips between left and right landscape.
  - Enters immersive mode using `WindowCompat.getInsetsController(window, window.decorView)` to hide both `WindowInsetsCompat.Type.statusBars()` and `WindowInsetsCompat.Type.navigationBars()` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, letting the video fill the entire screen.
- **Return to Portrait on Fullscreen Exit or Back Press**:
  - Tapping the Fullscreen toggle again or pressing Back while in fullscreen sets `activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT` and restores system status and navigation bars.
  - Leaving the player screen entirely restores `SCREEN_ORIENTATION_UNSPECIFIED` and system bars.
- **Zero-Restart Rotation & RAM Preservation**:
  - Add `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"` to `.MainActivity` in `AndroidManifest.xml` so rotating between portrait and landscape never recreates the Activity, restarts `ExoPlayer`, or wipes the `EphemeralStreamManager` RAM sliding window.
- **Clean Fullscreen Viewport**:
  - Hide the `Share / Move / Rename / Details / Delete` bottom action bar whenever fullscreen or landscape mode is active.

---

## 2. Audio Track & Subtitle Selection (`TRACK_TYPE_AUDIO` & `TRACK_TYPE_TEXT`)
- **Player Control Bar Buttons**:
  - Add dedicated **Audio** (audio track icon) and **Subtitles** (CC icon) action buttons to the bottom player control bar with active-track badges.
- **Audio Track Bottom Sheet**:
  - Inspects `exoPlayer.currentTracks` for `C.TRACK_TYPE_AUDIO`.
  - Lists every embedded audio track with a formatted label combining language, track label, codec, and channel layout (e.g., `"Japanese – AAC 2.0"`, `"English – AC3 5.1"`).
  - Selecting a track applies `TrackSelectionOverride(group.mediaTrackGroup, trackIndex)` on `exoPlayer.trackSelectionParameters` immediately without restarting playback.
- **Subtitles Bottom Sheet & Side-Loaded `.srt` / `.vtt` Picker**:
  - Inspects `exoPlayer.currentTracks` for `C.TRACK_TYPE_TEXT` (supporting SRT, VTT, ASS/SSA, PGS, TTML, etc.).
  - Includes an **"Off"** option (`setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)`).
  - If the container has no embedded subtitle tracks, displays `"No subtitles in this file"`.
  - Includes a **"Load subtitle file…"** action using `ActivityResultContracts.OpenDocument` (`application/x-subrip`, `text/vtt`, `text/plain`, `*/*` filtered for `.srt`/`.vtt`).
  - Reads the external subtitle file directly from the picker `content://` URI via `MediaItem.SubtitleConfiguration` and a delegating `DataSource.Factory` (routing `televault://` chunks through `TelegramChunkDataSource` and `content://` subtitle URIs through `DefaultDataSource`) without copying the file to storage, preserving current playback position and play state.
- **Readable Subtitle Styling**:
  - Configure `PlayerView.subtitleView` with `CaptionStyleCompat` using crisp white foreground text, black outline (`EDGE_TYPE_OUTLINE`), transparent background/window, and bottom alignment.
- **Session Language Memory & Sheet Behavior**:
  - Remember the user's selected audio and subtitle languages in memory for the current viewing session.
  - Both bottom sheets work cleanly in portrait and fullscreen landscape, and keep player controls visible while open.

---

## 3. Phase 1 Polish & Verification Checklist
- **Playback & Seek Bar**: Auto-play starts as soon as chunk 1 is buffered; real-time `0:00` timecode and buffered range bar update every 100ms.
- **Auto-Hide Controls**: Controls and top bar hide after 3 seconds of active playback, reappear on tap, and stay visible when paused, buffering, scrubbing, or while the Audio/Subtitle sheet is open. Center of the video remains unobstructed in landscape.
- **Buffering Card**: `"Buffering Chunk X/Y in RAM..."` appears strictly while waiting for data, hides as soon as the first frame renders, and reappears only when seeking to an unbuffered chunk.
- **Single-Retry Error Handling**: Refresh expired Telegram `file_path` link and retry once per chunk; on second failure, stop immediately and display a clear error card with a **Retry** button (no retry loops).
- **RAM Wipe Check**: RAM pill drops immediately to `0.0 MB` when leaving the player or when the app goes to the background.
