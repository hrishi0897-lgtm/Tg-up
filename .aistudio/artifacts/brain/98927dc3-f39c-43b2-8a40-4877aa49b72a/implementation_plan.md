# Streaming Player Bottom Bar & Portrait Controls Layout Fix

This update hides the file action bar (**Share / Move / Rename / Details / Delete**) whenever the streaming media player is active (`isStreamingMediaActive == true`) in both portrait and landscape modes, and anchors the portrait player controls cleanly to the bottom of the screen with system navigation bar inset padding. All streaming, buffering, fullscreen, audio track, and subtitle logic remain untouched.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Zero Streaming Logic Changes**: `EphemeralStreamManager`, `TelegramChunkDataSource`, fullscreen orientation switching, and audio/subtitle track selection remain completely unchanged.
> - **Action Bar Visibility**: The bottom action bar (`Share / Move / Rename / Details / Delete`) is now shown **only** when `isStreamingMediaActive == false` (on the File Details screen and non-streaming previews) and is completely hidden during active video or audio streaming in both portrait and landscape.
> - **Bottom-Anchored Portrait Controls**: Removing the bottom action bar during playback frees the bottom edge of the screen. The portrait player control card now sits directly at the bottom of the screen using `.navigationBarsPadding()` plus a clean `12.dp` bottom margin so no transport or track buttons are ever clipped by the system navigation bar.

---

## 1. Overview & Core Concept

- **What It Does**: Eliminates the overlapping/stacked bottom action bar during media playback so the player canvas utilizes the full height below the top bar, while positioning the player controls cleanly at the bottom edge in portrait mode.
- **Key Value**: Prevents cramped vertical space and clipped bottom controls in portrait playback while keeping all file management actions accessible on the File Details screen before and after playback.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **File Details Screen (`isStreamingMediaActive == false`)**: The bottom action bar (**Share**, **Move**, **Rename**, **Details**, **Delete**) is visible at the bottom of the screen with `navigationBarsPadding()`.
  2. **Active Streaming Player (`isStreamingMediaActive == true`)**:
     - Tapping the center **Play** button opens the player and immediately hides the bottom action bar in both portrait and landscape.
     - In portrait mode, the player controls card (seek bar, current/total time, RAM badge, volume mute, audio tracks, `-10s`, Play/Pause, `+10s`, subtitles, fullscreen) sits at the bottom of the screen with `.navigationBarsPadding()` and `12.dp` bottom spacing so all buttons are fully visible and unobstructed.
  3. **Closing the Player (Back to Details)**:
     - Pressing **Back** from the player sets `isStreamingMediaActive = false`, wipes the RAM buffer to `0.0 MB`, and immediately restores the bottom action bar on the Details screen.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Gate Bottom Action Bar on `!isStreamingMediaActive`**
  - *Chosen Approach*: Update the bottom bar `AnimatedVisibility` condition in the preview screen to `visible = !isStreamingMediaActive && showControls`.
  - *Why*: Ensures the Share / Move / Rename / Details / Delete bar is never rendered while video or audio streaming is active (regardless of portrait or landscape orientation), and automatically reappears as soon as the player closes.
- **Decision 2: Replace Portrait `76.dp` Offset with `navigationBarsPadding()` + `12.dp` Bottom Padding**
  - *Chosen Approach*: Previously, the video player controls used `bottom = 76.dp` in portrait to float above the bottom action bar. With the action bar hidden during playback, the controls overlay now applies `.navigationBarsPadding()` and `bottom = 12.dp`.
  - *Why*: Anchors the player controls to the true bottom of the screen while respecting gesture/3-button system navigation insets so buttons are never cut off.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌────────────────────────────────────────────────────────────────────────────┐
│                             FilePreviewScreen                              │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Top Bar Overlay (Back, Filename, Size, RAM Pill, Info)               │  │
│  │ • Visible on Details screen; auto-hides after 3s during playback     │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Full-Height Content Area                                             │  │
│  │                                                                      │  │
│  │  isStreamingMediaActive == false:                                    │  │
│  │   └── MediaFileDetailsOverview (Metadata + Center 68.dp Play Button) │  │
│  │                                                                      │  │
│  │  isStreamingMediaActive == true:                                     │  │
│  │   └── ZeroDownloadExoVideoPlayer / ZeroDownloadExoAudioPlayerCard    │  │
│  │        └── Bottom-Pinned Controls Overlay                            │  │
│  │             • .align(Alignment.BottomCenter)                         │  │
│  │             • .navigationBarsPadding()                               │  │
│  │             • .padding(bottom = 12.dp, start/end = 14.dp / 24.dp)    │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Bottom Action Bar (Share / Move / Rename / Details / Delete)         │  │
│  │ • visible = !isStreamingMediaActive && showControls                  │  │
│  │ • Hidden in both portrait & landscape when player is active          │  │
│  │ • Restored automatically when closing player back to Details screen  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```
