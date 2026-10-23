# Fast First-Frame Streaming Engine & Minimal Spinner Player UI

This update accelerates video and audio playback start-up so the first frame renders as quickly as possible while cleaning up the player UI to show only a minimal circular spinner during buffering and removing RAM/buffer text from the player screen. All internal 36 MB RAM sliding-window accounting, zero-disk guarantees, Details-first navigation, fullscreen, audio track switching, and subtitle features remain intact.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Spinner-Only Buffering & Clean Player Chrome**:
>   - Replaces the `"Buffering Chunk X/Y in RAM..."` card and `"X/36 MB RAM"` text over the video with a clean centered circular loading spinner (`CircularProgressIndicator`) shown **only** while the player is waiting for data (before the first frame renders or when seeking to unbuffered data).
>   - Removes the RAM status pill from the top bar and removes the `"X/36 MB RAM • Buf X%"` text from the player controls while keeping internal 36 MB RAM accounting and wipe guarantees active.
> - **Sub-Second First Frame Architecture**:
>   - **Parallel Start (`Chunk 0` + `Last Chunk`)**: Immediately upon opening the player, resolves `getFile` links and begins streaming both the **first chunk** (header/initial frames) and the **last chunk** (MP4 `moov` atom / MKV `Cues` index) in parallel, while warming `getFile` links for the next 2 chunks (`chunk 1` and `chunk 2`) in the background.
>   - **Progressive Partial Reads & HTTP Range (`2–4 MB` Window)**: Satisfies `ExoPlayer` reads as soon as the requested bytes arrive rather than waiting for an entire 18 MB chunk to finish. Uses HTTP `Range` requests (`2–4 MB` read-ahead window) against the Telegram file URL, and if the server returns `200 OK` (ignoring `Range`), streams the chunk progressively in RAM and unblocks `ExoPlayer` reads immediately as bytes arrive.
>   - **Random-Access Progressive AES-CTR Decryption (GCM Counter Offset)**: Because AES-256-GCM encrypts payload bytes using AES-CTR starting at counter block `J_0 + 2` (for a standard 12-byte IV where `J_0 = IV || 0x00000001`), any arbitrary byte range inside an encrypted chunk can be decrypted on the fly using `AES/CTR/NoPadding` by reading the 12-byte IV header and computing the 16-byte counter block for `blockIndex = 2 + (plaintextOffset / 16)`. When a full encrypted chunk completes in RAM, non-blocking AES-GCM tag verification is performed without delaying the first frame.
>   - **Shared HTTP/2 + Keep-Alive `OkHttpClient`**: Reuses a single shared `OkHttpClient` configured with `Protocol.HTTP_2`, `Protocol.HTTP_1_1`, and a persistent `ConnectionPool` across all Bot API and file stream requests.
>   - **Fast `LoadControl` Thresholds**: Configures `bufferForPlaybackMs = 500`, `bufferForPlaybackAfterRebufferMs = 1500`, `minBufferMs = 2000`, and `maxBufferMs = 8000`.
>   - **`StreamTiming` Logcat Instrumentation**: Logs `Log.d("StreamTiming", ...)` measurements for link resolution latency, time to first byte of chunk 1, progressive decryption time, and time to first rendered frame.

---

## 1. Overview & Core Concept

- **What It Does**: Eliminates the multi-second wait for full 18 MB chunk downloads and sequential MP4/MKV index fetches before playback begins, while decluttering the player UI.
- **Key Value**: Delivers near-instant first-frame video rendering and responsive seeking while strictly preserving the 36 MB RAM-only zero-download security model.

---

## 2. User Experience & Visual Design

- **Clean Player Overlay**:
  - **Buffering State**: Displays only a centered `CircularProgressIndicator` over the video canvas when waiting for initial frames or unbuffered seek data; hides immediately once playback continues.
  - **Top Bar**: Displays the Back button, file name, file size, encryption badge, and Details info button (no RAM pill on the player/preview top bar).
  - **Bottom Player Controls**: Displays the dual-layer seek bar (showing active and buffered position visually), current time (`0:00`) on the left, total duration on the right, and the transport/track buttons below (no `"X/36 MB RAM • Buf X%"` label).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Progressive Chunk Buffer + Range Read-Ahead (`3 MB` Window)**
  - *Chosen Approach*: Maintain per-chunk progressive buffers in the 36 MB RAM window. When `ExoPlayer` requests bytes at `offsetInChunk`, if those bytes are already present in the progressive buffer, return them immediately. Otherwise, issue an HTTP `Range` request covering the needed range plus a `3 MB` read-ahead window (or stream progressively from byte 0 if `Range` is not honored), notifying waiting reader coroutines as each `32 KB` network slice arrives.
  - *Why*: `ExoPlayer` typically needs only the first `256 KB – 1 MB` of Chunk 0 plus the tail index of the last chunk (`moov` / `Cues`) to render the first video frame. Returning partial reads immediately cuts startup latency by 80–90%.
- **Decision 2: On-the-Fly AES-CTR Decryption with GCM Counter Derivation**
  - *Chosen Approach*: For AES-256-GCM encrypted chunks (`[12-byte IV][Ciphertext][16-byte Tag]`), cache the 12-byte IV as soon as the first 12 bytes of the chunk are fetched. Decrypt any arbitrary plaintext slice `[offset, offset + length)` directly in RAM using `AES/CTR/NoPadding` initialized with counter block `IV (12 bytes) || bigEndianInt32(2 + (offset / 16))`, discarding the initial `offset % 16` keystream bytes. Run full `AES/GCM/NoPadding` tag verification asynchronously once a full chunk finishes downloading without blocking frame rendering.
  - *Why*: Standard `AES/GCM/NoPadding` (`cipher.doFinal`) requires the entire 18 MB chunk and trailing 16-byte tag before outputting a single byte of plaintext. Mathematical equivalence of GCM's CTR mode (NIST SP 800-38D, counter starting at `2` for 96-bit IVs) enables instant random-access range decryption in RAM.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        ZeroDownloadExoVideoPlayer                            │
│  • LoadControl: min=2000ms, max=8000ms, playback=500ms, rebuffer=1500ms      │
│  • UI: Centered CircularProgressIndicator only while waiting for data        │
│  • Logs: Log.d("StreamTiming", "firstRenderedFrameMs=...")                   │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                          EphemeralStreamManager                              │
│                                                                              │
│  1. warmStartSession(file, chunks):                                          │
│     ├── Parallel: resolve link + start progressive stream for FIRST chunk    │
│     ├── Parallel: resolve link + start progressive stream for LAST chunk     │
│     └── Background: pre-resolve getFile links for chunk 1 and chunk 2        │
│                                                                              │
│  2. Progressive RAM Chunk Buffer (36 MB Sliding Window Cap):                 │
│     ├── HTTP Range request (3 MB read-ahead) via shared HTTP/2 OkHttpClient  │
│     ├── Progressive fallback if server responds 200 OK instead of 206        │
│     ├── AES-CTR range decryption (counter = 2 + offset/16 for 12-byte IV)    │
│     ├── Satisfies ExoPlayer DataSource.read() as soon as partial bytes exist │
│     └── Background prefetch of next chunk (chunkIndex + 1) during playback   │
└──────────────────────────────────────────────────────────────────────────────┘
```
