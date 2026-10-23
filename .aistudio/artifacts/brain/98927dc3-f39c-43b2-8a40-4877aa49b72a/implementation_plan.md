# File Details First Flow & Global -2sp Typography Plan

This update changes how files open from lists/grids so tapping any file opens the **File Details Screen** first (keeping RAM at `0.0 MB` until the user presses the center **Play** button on video/audio files), and reduces all font sizes and line heights across the entire app by `2.sp`.

---

## 1. Tap a File → Details Screen First (Zero Buffering Until Play is Pressed)
- **Unified Tap Behavior (`TeleVaultViewModel.onFileItemClick`)**:
  - Tapping any file in a list or grid always navigates to the File Details screen (`AppScreen.PREVIEW` / `FilePreviewScreen`) instead of auto-starting the media player or opening a separate bottom sheet first.
  - Pass the current folder name (resolved from `allFolders` / `currentFolderId`) so the Details screen displays the file's folder alongside its other metadata.
- **Details Screen Layout & Metadata (`FilePreviewScreen.kt`)**:
  - Shows the **RAM pill** in the top bar (staying at `0.0/36 MB RAM` while on the Details screen).
  - Displays full file details:
    - **File Name**
    - **Type Icon or Thumbnail** in the preview area
    - **Size** & **Upload Date**
    - **Folder Name** (e.g., `Root Vault` or folder name)
    - **Encryption Status** (`AES-256-GCM Encrypted` with key fingerprint or `Standard Cloud Storage`)
    - **Number of Chunks** (`X chunks`)
    - **Existing Actions Bar**: `Share`, `Move`, `Rename`, `Details`, `Delete`.
- **Video & Audio Center Play Button & Deferred Streaming**:
  - For video and audio files, `isStreamingMediaActive` starts as `false` when the Details screen opens.
  - While `isStreamingMediaActive == false`, `ZeroDownloadExoVideoPlayer` and `ZeroDownloadExoAudioPlayerCard` are **not** instantiated—zero network requests, chunk fetches, or decryptions occur, and the RAM pill stays strictly at `0.0 MB`.
  - The center of the preview area displays the video thumbnail or audio artwork card with a **large Play button** (`68.dp` circular button) in the center.
  - For non-video/non-audio file types (images, PDFs, text/code, other files), the existing preview behavior is preserved alongside the metadata details.
- **Play & Two-Stage Back Navigation**:
  - Pressing the center **Play** button transitions `isStreamingMediaActive` to `true`, launching the streaming player and starting playback automatically as soon as Chunk 1 is loaded in RAM.
  - Pressing **Back** (system back or top-bar back button) while in the player (`isStreamingMediaActive == true`):
    - If in fullscreen/landscape, exits fullscreen to portrait first.
    - Otherwise closes the streaming player, immediately wipes the RAM buffer back to `0.0 MB`, and returns to the **Details screen** (`isStreamingMediaActive = false`).
  - Pressing **Back** from the **Details screen** (`isStreamingMediaActive == false`) returns to the file list.
  - Fullscreen orientation (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE` / `SCREEN_ORIENTATION_PORTRAIT`), immersive mode, audio track switching, and subtitle selection (including `.srt`/`.vtt` side-loading) remain unchanged inside the player.

---

## 2. Reduce Text Size & Line Height by `2.sp` Across the Entire App
- **Central Theme Typography (`Type.kt`)**:
  - Lower `fontSize` by `2.sp` and `lineHeight` by `2.sp` for every style in `Typography` (`displayLarge`, `displayMedium`, `headlineLarge`, `headlineMedium`, `titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge`, `labelSmall`) and all signature styles (`WordmarkTextStyle`, `EmptyHeadlineStyle`, `MonoStatValueLarge`, `MonoStatValueMedium`, `MonoStatValueSmall`).
- **Component & Screen Hardcoded `fontSize` / `lineHeight` Values**:
  - Reduce every hardcoded `fontSize = X.sp` and `lineHeight = Y.sp` by `2.sp` across `MainActivity.kt` and all screens, dialogs, bottom sheets, top bars, the RAM pill, and player controls in `app/src/main/java/com/example/ui/screens/` (including `SubtitleView` default caption text size).
- **Preserve Touch Targets & Iconography**:
  - Keep all icon sizes, button dimensions, touch targets (`>= 48.dp`), and the large center Play button unchanged.
  - Verify no text truncation or overlap occurs in portrait or landscape.
