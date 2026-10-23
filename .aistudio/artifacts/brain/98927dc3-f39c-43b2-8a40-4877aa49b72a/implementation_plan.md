# Phased Implementation Plan: Universal Zero-Download File & Media Viewer

## Overview
We are building an ephemeral, zero-download streaming engine and universal file viewer for **TeleVault** in phased milestones so each layer can be tested and verified independently.

- **Phase 1 (Current Scope — Implement Now & Stop for Testing)**: Core `EphemeralStreamManager` (20–50 MB RAM sliding window with zero-fill wipe on exit, backgrounding, and startup crash recovery), automatic re-requesting of expiring Telegram `getFile` download links, and **Media3 ExoPlayer** with a custom chunk-based `TelegramChunkDataSource` supporting byte-range seeking and basic playback controls for both Video and Audio files.
- **Phase 2 (Deferred — After Phase 1 Testing)**: Pro Video & Audio controls (Subtitles with SRT/VTT loader & styling, audio track switcher, up to 200% LoudnessEnhancer volume boost, swipe gestures for brightness/volume/seek, playback speed, A-B repeat loop, aspect ratio modes, screen lock, frame-by-frame step, rotation, and real-time FFT audio visualizer + 5-band equalizer).
- **Phase 3 (Deferred — After Phase 2 Testing)**: Specialized non-media zero-download viewers (PDF viewer with zoom/thumbnails/night mode, ZIP/TAR archive inspector, CSV table grid, JSON collapsible tree, Markdown/Code viewer, and Hex + ASCII binary inspector).

---

## Phase 1 Architecture & Implementation Details (Active Scope)

### 1. Ephemeral RAM Sliding-Window Engine (`EphemeralStreamManager.kt`)
- **20–50 MB RAM Sliding Window**:
  - Maintains an in-memory LRU cache (`LinkedHashMap<Int, ByteArray>`) capped at **36 MB** (up to 2 decrypted 18 MB chunks in RAM simultaneously: the active playback chunk plus 1 lookahead/adjacent chunk, strictly within the 20–50 MB sliding window budget).
  - Evicts older chunks automatically when advancing or seeking to a new chunk index, zero-filling (`fill(0)`) evicted `ByteArray` buffers before releasing references so sensitive plaintext never lingers on the heap.
  - Exposes a `StateFlow<EphemeralBufferStats>` reporting live RAM usage (MB allocated vs. 36 MB window cap, active chunk index, total chunks, and link refresh status) for the top bar security telemetry pill.
- **On-the-Fly Chunk Decryption**:
  - Supports both unencrypted chunks and AES-256-GCM encrypted chunks (`FileEntity.isEncrypted == true` with `FileEntity.encryptionKey` Base64 key, 12-byte IV prefix, and 16-byte GCM auth tag via `CryptoStreamEngine`).
  - Deduplicates concurrent requests for the same chunk index using a per-chunk `Mutex` / `Deferred<ByteArray>` map so rapid scrubbing does not trigger duplicate downloads.
- **Expiring Telegram File Link Recovery**:
  - Telegram Bot API `getFile` URLs (`https://api.telegram.org/file/bot<token>/<file_path>`) expire after ~1 hour.
  - Caches resolved `file_path` URLs in memory with a timestamp, and automatically re-requests `TelegramRepository.getFile(botToken, telegramFileId)` (using `MultiBotLoadBalancer` / `EncryptedCredentialsManager` tokens) whenever a cached URL is older than 45 minutes or if an HTTP chunk fetch returns `400`, `401`, `403`, or `404`.
- **Strict Lifecycle Wipe & Crash Recovery**:
  - **On Viewer Exit**: `DisposableEffect(Unit)` zeroes out all in-memory chunk byte arrays and clears any temporary cache artifacts immediately.
  - **On App Background (`ON_STOP`)**: `LifecycleEventObserver` pauses playback and flushes the ephemeral RAM buffer when the app is backgrounded.
  - **On App Startup (Crash Recovery)**: `clearOrphanedEphemeralBuffers(context)` runs at initialization to purge any orphaned `.ephemeral_stream_tmp` cache directory if the process was previously killed by the OS.

### 2. Media3 ExoPlayer & Custom Chunk DataSource (`TelegramChunkDataSource.kt`)
- **Dependencies (`gradle/libs.versions.toml` & `app/build.gradle.kts`)**:
  - Add `androidx.media3:media3-exoplayer`, `androidx.media3:media3-ui`, and `androidx.media3:media3-common` (`1.5.1`).
- **Custom `TelegramChunkDataSource` (`BaseDataSource`)**:
  - Implements `androidx.media3.datasource.DataSource` over the ordered list of `ChunkEntity` records (`sequenceNumber`, `telegramFileId`, `sizeBytes`) for a `FileEntity`.
  - Computes total virtual stream length across all chunks and translates ExoPlayer `DataSpec(position, length)` reads into exact `(chunkIndex, offsetInChunk)` slices fetched through `EphemeralStreamManager`.
  - Supports arbitrary random-access byte-range seeking (`C.LENGTH_UNSET` and bounded ranges) so MP4/MKV/WebM/MP3/FLAC/WAV container headers (`moov` atoms at start or end of file) and timeline scrubbing work seamlessly across 18 MB chunk boundaries without downloading the whole file.
  - Triggers asynchronous background prefetching of `chunkIndex + 1` when the read cursor crosses 70% of the current chunk.

### 3. Phase 1 Video & Audio Streaming UI (`FilePreviewScreen.kt`)
- **Zero-Download Status Banner**:
  - Displays a live `RAM Stream • Zero-Download` badge in the top bar showing real-time RAM sliding window usage (e.g., `18.0 / 36.0 MB RAM • Chunk 1/4`).
- **Video Player View & Basic Controls**:
  - Hardware-accelerated Media3 `PlayerView` rendering the custom `TelegramChunkDataSource` stream.
  - Clean overlay controls with:
    - Play / Pause toggle and buffering/chunk-decrypting progress indicator
    - Interactive Seek Bar with current position, buffered position, and total duration (`mm:ss` / `hh:mm:ss`)
    - Quick `-10s` Rewind and `+10s` Fast-Forward buttons
    - Mute / Unmute toggle
    - Fullscreen / Landscape orientation toggle
- **Audio Player View & Basic Controls**:
  - Dedicated audio streaming deck using the same Media3 `ExoPlayer` + `TelegramChunkDataSource` pipeline.
  - Displays file metadata, animated playback state, interactive seek bar with timecodes, `-10s` / Play-Pause / `+10s` transport controls, and mute toggle.
- **Existing Image & Text Preview Preservation**:
  - Keeps existing zero-download in-memory preview for images and text files routed through `EphemeralStreamManager` so all existing previews benefit from link-expiry recovery and automatic RAM wiping.

---

## Verification & Stopping Point
1. Compile the project using `compile_applet` and verify zero build or lint issues.
2. **Stop immediately after Phase 1** so you can test video/audio streaming, seeking across chunks, RAM buffer wiping, and link refresh before we proceed to Phase 2.
