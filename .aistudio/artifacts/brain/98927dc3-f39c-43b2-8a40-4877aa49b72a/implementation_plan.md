# Implementation Plan: Dedicated `Download/TGC` Path & Reliable Pause/Resume

## Goal
Ensure all downloaded files are saved into the app's dedicated `Download/TGC` folder and visible/accessible with clear location details, and fix all Pause and Resume issues for both uploads and downloads—including byte-offset resumption within active download chunks and race-free coroutine lifecycle handling.

---

## Key Changes

### 1. Dedicated `Download/TGC` Storage Path & Visibility
- **`DownloadStorageManager.kt`**:
  - Ensure all reassembled downloads are saved directly into `Download/TGC/` (using `MediaStore.Downloads` with `RELATIVE_PATH = "Download/TGC/"` on Android 10+ and `/storage/emulated/0/Download/TGC` on older Android versions).
  - Return both the `content://` MediaStore URI (for opening/sharing via intents) and the human-readable display path (`Download/TGC/<filename>`) so the database stores a clean, verifiable path and `content://` URI.
  - Provide helper utilities to check whether a downloaded file still exists in `Download/TGC` and to resolve its openable URI.
- **`SettingsScreen.kt` & `FileDetailSheet.kt` / `TransfersScreen.kt`**:
  - Display the dedicated download folder path (`Download/TGC`) in Settings and on completed download cards / file detail sheets so users can clearly see where files are stored and open them directly.

### 2. Byte-Accurate Download Pause & Resume (HTTP Range Support)
- **`TelegramApi.kt` & `TelegramRepository.kt`**:
  - Add optional `@Header("Range") range: String? = null` support to `downloadFile` in `TelegramApi` and pass `offsetBytes: Long = 0L` in `TelegramRepository.downloadFileStream`.
- **`TransferManager.kt` (Download Engine)**:
  - **Preserve Partial Chunks on Pause**: When a download is paused mid-chunk, keep the `.part` file (`chunk_<index>.part`) on disk instead of deleting it, and keep the exact `downloadedBytes + partialChunkBytes` in `_transfers` so the progress bar freezes at the exact percentage where the user paused.
  - **Byte-Offset Resume**: When resuming a download, check if `chunkTempFile` already has partial bytes (`0 < length < chunk.size`). If so, request `Range: bytes=<existingLength>-` from Telegram's file server:
    - If the server responds with `206 Partial Content`, append the remaining bytes to `chunkTempFile` (`FileOutputStream(chunkTempFile, true)`) starting progress from `existingLength`.
    - If the server responds with `200 OK`, rewrite `chunkTempFile` from byte `0`.
    - Once all bytes for the chunk arrive, compute the SHA-256 over the complete `chunkTempFile` to guarantee end-to-end integrity before marking the chunk downloaded.
  - **Disk Verification for Completed Chunks**: Verify `chunkTempFile.exists() && chunkTempFile.length() == chunk.size` before skipping a chunk marked `isDownloaded`, preventing `FileNotFoundException` during final file reassembly.

### 3. Race-Free Pause & Resume Lifecycle (Uploads & Downloads)
- **`TransferManager.kt`**:
  - **Eliminate Pause/Resume Race Condition**:
    - When `pauseTransfer(fileId)` is called, mark `pauseRequestedFiles.add(fileId)`, persist `FileStatus.PAUSED` immediately in Room and `_transfers` (preserving exact `bytesTransferred` and `completedChunks`), and cancel the active job.
    - In `executeSingleFileUpload` and `startDownload`, ensure `catch (e: CancellationException)` only sets `FileStatus.PAUSED` if `pauseRequestedFiles.contains(fileId)` is still true, and ensure `finally` removes the job using `activeJobs.remove(fileId, currentJob)` so an unwinding job never clobbers a newly resumed job.
    - Before starting a resumed upload or download in `startUpload` / `startDownload`, await cancellation cleanup of any prior job for `fileId`.
  - **Upload Queue Isolation & Progress Preservation**:
    - Isolate each file upload inside its own child `Job` in `processNextUploadInQueue()` so pausing an active upload cancels only that file's job without terminating the queue processor loop.
    - When resuming a paused upload in `startUpload(fileId)`, initialize `bytesTransferred` and `completedChunks` from the already-uploaded chunks in Room instead of resetting to `0`, so the progress bar resumes smoothly from its paused percentage.
- **`TransferWorker.kt`**:
  - Exclude `FileStatus.PAUSED` from automatic background worker restarts so user-paused uploads and downloads remain paused until explicitly resumed by the user.

---

## Verification Plan
- **Compilation & Static Verification**: Run `compile_applet` to verify all Kotlin, Room, Retrofit, and Compose changes compile cleanly.
- **Functional Flows**:
  - Upload a multi-chunk file, pause mid-chunk, verify progress freezes in `PAUSED` state, resume, and verify it continues from completed chunks without resetting to 0% or getting stuck.
  - Download a file, pause mid-chunk, verify partial chunk bytes and progress percentage are retained, resume, and verify it continues from the byte offset, verifies SHA-256, and saves the final file into `Download/TGC`.
