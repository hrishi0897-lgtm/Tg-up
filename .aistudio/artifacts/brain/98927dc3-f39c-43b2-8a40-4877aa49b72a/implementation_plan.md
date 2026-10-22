# First-Launch System Permissions & Automatic `Download/TGC` Folder Creation

Update the first-launch experience so the app only requests Android's native **Storage** and **Notification** permissions on first launch, removes the custom `Set Up Download/TGC Folder` confirmation dialog, and automatically creates the `Download/TGC` directory in the background.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Confirmed Decision 1 — Native System Permissions Only on First Launch**: On first open, the app will request the standard Android system permissions (`POST_NOTIFICATIONS` on Android 13+ and `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` where applicable) using `ActivityResultContracts.RequestMultiplePermissions()` instead of showing a custom in-app modal asking to create the folder.
> - **Confirmed Decision 2 — Silent, Automatic `Download/TGC` Creation**: The app will automatically create and register the `Download/TGC` folder in the phone's `Download` directory on startup (and immediately after permission grant on legacy Android versions) without prompting the user for confirmation.

---

## 1. Overview & Core Concept

- **What It Does**: Streamlines the first-time startup flow by requesting only OS-level Notification and Storage permissions while automatically provisioning the `Download/TGC` folder behind the scenes.
- **Target Audience / Persona**: Users opening TeleVault for the first time who expect standard Android permission prompts and automatic folder readiness without extra confirmation dialogs.
- **Key Value**: Eliminates redundant UI friction while guaranteeing that `Download/TGC` exists and is ready for downloads from the moment the app opens.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **First App Launch**:
     - The app immediately triggers `DownloadStorageManager.initializeTgcDirectory(context)` in the background to create `Download/TGC`.
     - Concurrently, if first-launch permissions haven't been requested yet, the app launches the native Android system permission prompt (`RequestMultiplePermissions`) for Notification and Storage permissions.
     - Once the user responds to the system permission dialog, the app marks first-launch permission setup as complete and ensures `Download/TGC` is initialized.
  2. **Zero Custom Folder Dialog**:
     - The custom `FirstLaunchStorageSetupDialog` modal is removed so the user is never asked for permission to create the `TGC` folder.
- **Visual Identity & Theme**:
  - Retains all the newly upgraded visual designs across the app (elevated glassmorphic Bento storage hero with `Download/TGC` status badge and multi-segment bar, refined file/folder cards with `TGC` and `AES-256` badges, glowing progress bars in the Transfer Queue, and the floating glassmorphic bottom navigation pill bar).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified System Permission Request (`RequestMultiplePermissions`)**
  - *Chosen Approach*: Request `POST_NOTIFICATIONS` and applicable storage permissions (`WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE`) together in a single native Android permission flow on first launch, and immediately run `initializeTgcDirectory(context)`.
  - *Why*: Android's native permission dialog is familiar and fast, and creating `Download/TGC` automatically in the background avoids bothering the user with a custom dialog.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       App Startup (MainActivity)                        │
│  1. Automatically runs DownloadStorageManager.initializeTgcDirectory()  │
│  2. Checks if first-launch system permissions have been requested       │
└───────────────────┬─────────────────────────────────────┬───────────────┘
                    │ First Launch                        │ Subsequent Launches
                    ▼                                     ▼
┌───────────────────────────────────────────┐ ┌───────────────────────────┐
│  ActivityResultContracts                  │ │ Silent Background Ensure  │
│  .RequestMultiplePermissions()            │ │ Ensures Download/TGC      │
│  • POST_NOTIFICATIONS (API 33+)           │ │ exists on disk & in       │
│  • READ/WRITE_EXTERNAL_STORAGE            │ │ MediaStore without UI     │
└───────────────────┬───────────────────────┘ └───────────────────────────┘
                    │ Callback
                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               TeleVaultViewModel.onPermissionsResult()                  │
│  • Marks first-launch permission prompt completed in preferences        │
│  • Re-runs DownloadStorageManager.initializeTgcDirectory()              │
└─────────────────────────────────────────────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - Remove `FirstLaunchStorageSetupDialog` from `MainActivity`.
  - In `MainActivity`, when `uiState.showFirstLaunchStoragePrompt` is true on first launch, launch `RequestMultiplePermissions` for the required OS permissions (`POST_NOTIFICATIONS`, `WRITE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`) and call `viewModel.completeFirstLaunchStorageSetup()` automatically.
  - In `TeleVaultViewModel.init`, always call `DownloadStorageManager.initializeTgcDirectory(getApplication())` on a background coroutine so `Download/TGC` is created automatically on startup without any user prompt.
