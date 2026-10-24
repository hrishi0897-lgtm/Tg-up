# End-to-End Performance, Streaming & UX Polish Plan

This upgrade accelerates streaming startup and mid-stream seeking, adds minimal high-utility media controls (playback speed, double-tap 10s seek gestures, and per-file resume memory), speeds up multi-chunk file uploads and downloads, and makes vault browsing and thumbnail loading instantaneous.

## User Review & Critical Decisions

> [!IMPORTANT]
> All four improvement tracks and media player enhancements selected during clarification are included below while strictly preserving your Phase 1 invariants:
> - **Zero-Download RAM-Only Rule**: Media streaming remains strictly capped within the 36 MB RAM sliding window (`ByteArray.fill(0)` on eviction, player exit, and `ON_STOP`).
> - **Details-First Flow with 0.0 MB RAM**: Opening the Details screen never downloads or decrypts chunk bytes (`allocatedRamBytes == 0`). Only lightweight `getFile` URL metadata strings are pre-resolved in the background so tapping **Play** skips the initial Bot API round-trip.
> - **Minimal Spinner-Only Player UI**: The video surface keeps the clean centered `CircularProgressIndicator` without any RAM pill or chunk text overlays.

- **Confirmed Decision 1 (Pre-Resolve Links on Details Screen)**: When a video or audio file's Details screen opens, the app resolves the Telegram `getFile` download URLs for Chunk 0, the Last Chunk, and Chunk 1 in the background without downloading any media payload bytes (`0.0 MB` RAM used). When the user taps **Play**, HTTP byte-range streaming starts immediately.
- **Confirmed Decision 2 (Minimal Speed Selector, Double-Tap 10s Seek & Resume Position)**: Add a compact `1.0x` playback speed selector (`0.5x` – `2.0x`), left/right double-tap `±10s` seek gestures on the video surface, and automatic per-file playback position memory—while keeping the player chrome minimal and uncluttered.
- **Confirmed Decision 3 (Transfer Pipeline Acceleration)**: Overlap next-chunk preparation/encryption during uploads and use bounded concurrent chunk workers + pre-resolved `getFile` URLs during full-file downloads.
- **Confirmed Decision 4 (Vault Browser & Thumbnail Responsiveness)**: Add an in-memory thumbnail lookup cache (`ConcurrentHashMap`) to eliminate `File.exists()` disk I/O on the main thread during scroll, and wire `ThumbnailManager.thumbnailUpdatedFlow` into the Vault list/grid and Details screen so thumbnails appear live as soon as they finish fetching.

---

## 1. Overview & Core Concept

- **What It Does**: Eliminates network round-trip dead time before the first video frame, makes rapid scrubbing and seeking responsive, remembers where you left off in every video/audio file, speeds up large multi-chunk transfers, and ensures smooth 60fps scrolling in the vault browser.
- **Target Audience / Persona**: Privacy-conscious power users streaming large encrypted MKV/MP4 videos and managing multi-gigabyte cloud vaults backed by Telegram.
- **Key Value**: Sub-second time-to-first-frame after tapping Play, effortless one-handed playback navigation, faster bulk transfers, and zero UI jank when browsing large folders.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Zero-Latency Play from Details Screen**:
     - User taps a video or audio item in the Vault -> Details screen opens immediately (`0.0 MB` RAM allocated).
     - In the background, lightweight `getFile` path strings for the first, last, and second chunks are resolved and cached in `resolvedLinkCache`.
     - User taps the center **Play** button -> `ExoPlayer` immediately opens the pre-resolved HTTP/2 URLs without waiting for `getFile` RPC calls, rendering the first frame significantly faster.
  2. **Minimal Player Controls & Gestures**:
     - **Double-Tap Edge Seek**: Double-tapping the left third of the video seeks back 10 seconds (`-10s`); double-tapping the right third seeks forward 10 seconds (`+10s`) with a subtle, brief visual badge fade, while single-tapping anywhere toggles the control overlay as before.
     - **Playback Speed Chip**: A compact speed button (`1x`, `1.25x`, `1.5x`, `2x`, etc.) in the player controls opens a clean bottom sheet (matching the Audio/Subtitle sheet style) to switch playback speed immediately on both video and audio players.
     - **Automatic Per-File Resume**: Leaving a video or audio stream saves the current timestamp (`positionMs`) for that `fileId`. Re-pressing Play automatically seeks to the saved timestamp (unless the file was already completed past 97%, in which case it starts fresh from `0:00`).
  3. **Live Vault Thumbnails & Smooth Scrolling**:
     - Scrolling through hundreds of files in Grid or List view queries an O(1) in-memory thumbnail path cache instead of hitting the filesystem on every frame.
     - When cloud thumbnails finish downloading in the background, the corresponding Vault card and Details preview crossfade from the category icon to the real thumbnail automatically.

- **Visual Identity & Theme**:
  - Preserves the **Obsidian Vault** dark/light aesthetic and reduced `-2sp` typography hierarchy across all screens and bottom sheets.
  - Double-tap seek feedback uses a translucent pill indicator (`Color.Black.copy(alpha = 0.55f)`) with a 450ms fade animation so it never clutters the video frame.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Metadata-Only Link Warm-Up on Details Screen**
  - *Chosen Approach*: Add `preResolveLinksOnly(file, chunks)` to `EphemeralStreamManager`. Call it when `FilePreviewScreen` loads chunk metadata on the Details screen.
  - *Why*: Calling Telegram's `getFile` endpoint only returns a JSON object with `file_path` (~100 bytes) and allocates zero media buffer RAM (`allocatedRamBytes` stays strictly `0L`), honoring the Details-screen zero-buffer requirement while shaving 300–600 ms off Play startup.
  - *Alternatives Considered*: Pre-fetching Chunk 0 bytes on the Details screen—rejected because it violates the strict rule that no media bytes may be fetched or decrypted before Play is pressed.

- **Decision 2: Superseded Seek Cancellation & Adaptive Boundary Prefetch**
  - *Chosen Approach*: When `ExoPlayer` issues a new mid-chunk HTTP Range read outside the active range window, immediately cancel any in-flight `rangeJob` on that chunk and cancel stale background chunk prefetches outside `[activeChunkIndex, activeChunkIndex + 1]`. Trigger next-chunk prefetch when playback or read offset passes 60% of the active chunk.
  - *Why*: Prevents competing HTTP streams from saturating bandwidth when the user scrubs across the timeline.

- **Decision 3: Bounded Concurrent Download Workers & Pipelined Upload Preparation**
  - *Chosen Approach*: In `TransferManager`, pre-resolve `getFile` URLs in parallel batches and download up to 3 chunks concurrently during full-file downloads (writing each to its dedicated `chunk_N.part` file before sequential SHA-256 verification and reassembly). For uploads, prepare/encrypt chunk `N+1` in a background coroutine while chunk `N` is uploading.
  - *Why*: Maximizes throughput on fast connections while respecting Telegram Bot API rate limits and keeping disk cache bounded.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         FilePreviewScreen (Details)                        │
│  • Displays metadata + actions (0.0 MB RAM)                                │
│  • Calls streamManager.preResolveLinksOnly(file, chunks) in background     │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ User taps center Play button
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│            ZeroDownloadExoVideoPlayer / ZeroDownloadAudioPlayer            │
│  • Restores saved resume position (PlaybackPositionStore)                  │
│  • Left/Right Double-Tap ±10s Gesture + Center Single-Tap Controls Toggle  │
│  • Compact Speed Selector (0.5x – 2.0x) + Audio & Subtitle Bottom Sheets   │
│  • Centered Spinner-Only Buffering Indicator                               │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ Media3 DataSource reads
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│               EphemeralStreamManager (36 MB RAM Sliding Window)            │
│  • Instant HTTP/2 Range requests using pre-resolved Telegram file_path     │
│  • Progressive AES-CTR range decryption (counter = 2 + plainOffset / 16)   │
│  • Immediate cancellation of superseded range jobs on rapid seek           │
└────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│                    TransferManager & ThumbnailManager                      │
│  • Downloads: 3x concurrent chunk workers + batch getFile link warm-up     │
│  • Uploads: Pipelined next-chunk encryption/hashing during network upload  │
│  • Thumbnails: O(1) in-memory path cache + SharedFlow live UI updates      │
└────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

1. **Pre-Resolve Links & Fast Seek (`EphemeralStreamManager`)**:
   - `preResolveLinksOnly(file, chunks)` resolves `getFile` links for indices `0`, `lastIndex`, `1`, and `2` in parallel and caches them in `resolvedLinkCache` without creating any `ProgressiveChunkState` or allocating any byte arrays.
   - `readChunkBytes(...)` cancels any prior `rangeJob` immediately when a new range offset is requested and starts `prefetchNextChunkInBackground` once `chunkOffset >= expectedSize * 3 / 5` (60%).
2. **Player Controls, Gestures & Resume Memory (`ZeroDownloadMediaPlayers`)**:
   - `PlaybackPositionStore` persists per-file playback timestamps (`positionMs` and `durationMs`) in lightweight local preferences and restores them when `ExoPlayer` prepares the media source.
   - Video surface gesture detector distinguishes left-third double-tap (`seekBack 10s`), right-third double-tap (`seekForward 10s`), and single-tap (`onToggleOverlayControls()`).
   - Speed selector updates `exoPlayer.setPlaybackSpeed(speed)` and `MediaPlayer` `PlaybackParams` live without restarting playback.
3. **Transfer & Thumbnail Acceleration (`TransferManager`, `ThumbnailManager`, `HomeScreen`, `FilePreviewScreen`)**:
   - `ThumbnailManager` maintains a thread-safe `resolvedPathCache: ConcurrentHashMap<String, File>` and bounds concurrent thumbnail downloads via a `Semaphore(4)` so visible items load first.
   - `HomeScreen` and `FilePreviewScreen` collect `thumbnailManager.thumbnailUpdatedFlow` to trigger instant recomposition when a thumbnail arrives.
   - `TransferManager` pipelines chunk preparation during uploads and uses bounded parallel chunk workers (`Semaphore(3)`) for full-file downloads.
