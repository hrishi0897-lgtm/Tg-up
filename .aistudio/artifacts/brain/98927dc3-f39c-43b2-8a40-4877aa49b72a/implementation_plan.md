# TeleVault Transfer Lifecycle Fixes, Human-Crafted UI Polish & Release APK Plan

This plan resolves the active upload and download pause/cancel bugs in the transfer engine, strips verbose explanatory text and helper banners across all screens for a crisp human-crafted experience, and configures automated Release APK generation on every build.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Download Cancellation & Partial Chunks**
> Per your selection, canceling an active or paused download will immediately abort the network stream and remove the item from the transfer queue, while **preserving already-verified chunk files on disk** and keeping the cloud file entry intact in your Vault so future downloads of that file resume instantly from the saved chunks.
>
> **Zero-Explanation UI Density**
> All instructional banners, multi-sentence helper descriptions, and verbose onboarding/settings explanations will be removed across the app in favor of concise labels, scannable metadata badges, and direct controls.

---

## 1. Overview & Core Concept

TeleVault's transfer engine currently suffers from stream-level race conditions where pausing or canceling an active transfer does not immediately interrupt the underlying HTTP byte stream, and in-flight progress callbacks overwrite the paused/canceled state. Additionally, canceling a download previously invoked the same destructive deletion path as canceling an incomplete upload.

This update overhauls the transfer state machine for deterministic pause and cancel control, refines every screen to look 100% human-designed without AI-style explanatory text walls, and ensures a signed Release APK is built alongside compilation.

---

## 2. User Experience & Visual Design

* **Instant Pause & Cancel Feedback:** Tapping **Pause** immediately freezes the progress bar and speed counter without creeping forward or flipping back to active status. Tapping **Cancel** immediately removes the transfer card from the queue.
* **Zero Helper Banners or Text Walls:**
  * Remove explanatory paragraphs from the Vault Home header, Folder Sync cards, Storage Analytics cards, Settings sections, Trash header, and Onboarding setup steps.
  * Replace multi-line descriptions in dialogs (Share Link, Import Link, Pair Device, Batch Upload, File Details) with clean form controls, compact status chips, and direct action buttons.
* **Refined OLED Black & Rich White Craftsmanship:** Tighter, intentional typography hierarchy where every label is purposeful (1–3 words), paired with crisp 1dp borders, tabular numeric metrics, and balanced spacing.

---

## 3. Key Product Decisions & Trade-Offs

* **Stream-Level Interrupts on Pause & Cancel:** Rather than waiting for a 19 MB chunk upload or download to finish before honoring a pause or cancel request, the byte-streaming loops will check cancellation and pause flags on every 8 KB buffer read/write and abort immediately.
* **Non-Destructive Download Cancellation vs. Clean Upload Cancellation:**
  * **Cancel Download:** Stops the active stream, deletes only the incomplete in-flight `.part` file, keeps completed `.part` chunks in cache for future resume, removes the item from the Transfer Queue, and restores the Vault file status to `COMPLETED`.
  * **Cancel Upload:** Stops all parallel bot workers, prevents the `CancellationException` handler from re-inserting the transfer as `PAUSED`, cleans up uploaded Telegram chunk messages and temporary staging files, and removes the incomplete record.
* **Release APK Generation:** Preserves the project's existing signing configuration so compatibility is never broken, while wiring Release APK assembly into the verification workflow so a ready-to-install Release APK is always produced.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        UI Layer (Jetpack Compose)                       │
│  TransfersScreen / TransfersSheet / HomeScreen / Dialogs / Settings     │
│  • Concise human-crafted labels (zero instructional banners)            │
│  • Pause / Resume / Cancel actions dispatch to TransferManager          │
└───────────────────┬─────────────────────────────────────┬───────────────┘
                    │                                     │
         Pause / Cancel Signal                   StateFlow<Map<Id, Progress>>
                    ▼                                     ▲
┌─────────────────────────────────────────────────────────┴───────────────┐
│                      TransferManager State Machine                      │
│  • pauseRequestedFiles: ConcurrentSet  • cancelledFiles: ConcurrentSet  │
│  • State Guard: blocks in-flight callbacks from overwriting PAUSED      │
│    or re-inserting CANCELLED transfers into _transfers                  │
└─────────┬───────────────────────────────────────────────┬───────────────┘
          │                                               │
   Upload Worker Pool                              Download Stream Loop
          ▼                                               ▼
┌───────────────────────────────────┐   ┌─────────────────────────────────┐
│ CountingRequestBody (8 KB chunks) │   │ ResponseBody.byteStream (8 KB)  │
│ • Checks coroutine isActive,      │   │ • Checks pauseRequestedFiles &  │
│   pauseRequestedFiles &           │   │   cancelledFiles every read     │
│   cancelledFiles every write      │   │ • Rethrows CancellationException│
│ • Cancel cleans staging & remote  │   │ • Cancel keeps verified .part   │
│   chunks; never falls back PAUSED │   │   chunks & restores COMPLETED   │
└───────────────────────────────────┘   └─────────────────────────────────┘
```

* **State Guard in Progress Publishers:** Both `updateProgressState` and `publishProgress` will check `cancelledFiles` and `pauseRequestedFiles` before mutating `_transfers`. If a file is marked canceled, all late worker emissions are dropped; if marked paused, emissions with `UPLOADING` or `DOWNLOADING` status are ignored unless `force = true` is explicitly passed by the pause handler.
* **Exception Handling Fix:** All chunk upload and download `catch (e: Throwable)` blocks will explicitly check for `CancellationException` and check `pauseRequestedFiles`/`cancelledFiles` first, exiting the retry loop immediately instead of treating user pause/cancel as a retriable network error.
