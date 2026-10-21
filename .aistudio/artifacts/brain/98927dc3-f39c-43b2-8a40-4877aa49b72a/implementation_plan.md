# TeleVault Pro Upgrade Plan

This upgrade transforms TeleVault into a zero-knowledge, high-throughput cloud vault built on Telegram—featuring a **True OLED Black & Rich White** dual theme system, **Client-Side AES-256-GCM Encryption**, **Multi-Bot Load Balancing**, **Smart Tagging & Filters**, **Automated Folder Sync**, **Rich Media/Document Previews**, **Encrypted QR Code Sharing**, and an interactive **Storage Analytics Dashboard**.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All core upgrade pillars below reflect your confirmed selections and are designed to work seamlessly with your existing Telegram channels and vault index without losing any existing files.

- **Confirmed Visual Identity**: **True OLED Black (`#000000`) & Rich White (`#FFFFFF`) Dual Theme** — Pure `#000000` background in Dark mode for zero-bloom OLED contrast, paired with a crisp, high-craft `#FFFFFF` / `#F7F8FC` Rich White theme and an instant theme toggle.
- **Confirmed Core Capabilities**:
  1. **Client-Side AES-256-GCM Encryption**: Per-file zero-knowledge encryption prior to chunking/upload with automatic decryption on download and preview.
  2. **Multi-Bot Load Balancing**: Parallel chunk dispatch across your pool of verified standby bots to multiply transfer bandwidth and avoid per-bot rate limits.
  3. **Advanced Search, File Tags & Smart Filters**: Color-coded custom tags, multi-criteria filter pills, and instant full-vault search.
  4. **Automated Folder Sync & Camera Backup**: Directory watch and one-tap delta synchronization from local device folders into target TeleVault folders.
  5. **Built-in Media & Document Previewer**: Zoomable image viewer, audio/video playback controls, and syntax-highlighted text/code/markdown/PDF viewer.
- **Confirmed Vault Management & Sharing**:
  1. **Shareable Encrypted Download Links & QR Codes**: Generate scannable QR codes and deep-link payloads (`televault://...`) with embedded decryption keys and expiry controls.
  2. **Storage Analytics Dashboard**: Interactive visual breakdown by file category, encrypted vs. unencrypted ratio, largest files ranking, and duplicate file finder (via SHA-256 checksum matching).

---

## 1. Overview & Core Concept

- **What It Does**: Elevates TeleVault from a basic chunked uploader into a full-featured encrypted cloud workspace. Files can be encrypted on-device before ever touching Telegram, uploaded in parallel across multiple bots, tagged and filtered instantaneously, synced automatically from local device folders, previewed in-app, analyzed for storage optimization, and shared securely via QR codes.
- **Target Audience / Persona**: Privacy-conscious power users, creators, and engineers who rely on Telegram as an unlimited personal cloud drive and demand native high-contrast aesthetics, cryptographic privacy, and desktop-class file organization.
- **Key Value**: Eliminates single-bot speed bottlenecks, guarantees cryptographic confidentiality on Telegram servers, and provides instant visibility into storage usage.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Zero-Knowledge Encrypted Upload & Multi-Bot Dispatch**:
   - When selecting files for upload, a **"Zero-Knowledge AES-256 Encryption"** toggle appears in the upload sheet alongside destination folder and custom tags.
   - Once confirmed, the transfer engine splits the file into chunks, encrypts each chunk with AES-256-GCM, and distributes chunk uploads concurrently across all active/verified bots in your bot pool.
   - An encrypted lock badge (`AES-256`) is displayed on the file card in the Vault and Transfer Queue.

2. **Smart Search, Tags & Filter Bar**:
   - On the Home Vault screen, tapping the search bar or filter icon expands smart filter chips: **All**, **Encrypted**, **Images**, **Videos**, **Documents**, **Audio**, **Archives**, **Large (>100 MB)**, plus any user-created **Custom Tags** (e.g., *Work*, *Receipts*, *Backups*, *Photos*).
   - Users can assign or edit multiple color-coded tags on any file via the File Detail Sheet.

3. **Storage Analytics & Duplicate Finder**:
   - Tapping the Storage Bento Card opens the **Storage Analytics Dashboard**.
   - Displays a multi-segment ring/bar chart breaking down space across categories, a **Top Largest Files** list, **Bot Pool Health & Throughput**, and a **Duplicate Files** detector (identifying files with identical SHA-256 hashes for one-tap cleanup).

4. **Automated Folder Sync**:
   - Accessible from the Vault header or Settings, the **Folder Sync Manager** lets users link local device directories (e.g., `DCIM/Camera`, `Download`, `Documents`) to specific TeleVault folders.
   - Tracks synced file hashes so only new or modified files are queued during a sync run.

5. **Encrypted QR Code & Deep-Link Sharing**:
   - Tapping **Share** on any file generates a shareable payload and renders a high-contrast **QR Code** directly in the Share dialog.
   - Another TeleVault user (or secondary device) can import the shared payload or scan/paste the `televault://share` URI to immediately index and download the file (including its decryption key if shared).

### Visual Identity & Theme (`OLED Black & Rich White`)

- **Aesthetic Direction**: Precision-engineered utilitarian luxury. High-contrast typographic hierarchy, 1dp hairline borders, zero unnecessary gradients, and tactile micro-interactions.
- **Color Palette & Tokens**:
  - **OLED Black Mode (`DarkTeleVaultColors`)**:
    - Background (`bg`): `#000000` (True OLED Black)
    - Card Surface (`surface`): `#0A0C12` (Obsidian Elevated)
    - Elevated Surface (`surfaceHi`): `#121622`
    - Hairline Border (`line`): `#1E2436`
    - Primary Text (`text`): `#F8FAFC`
    - Secondary Text (`textDim`): `#94A3B8`
  - **Rich White Mode (`LightTeleVaultColors`)**:
    - Background (`bg`): `#F7F8FC` (Warm Alabaster Canvas)
    - Card Surface (`surface`): `#FFFFFF` (Pure Rich White)
    - Elevated Surface (`surfaceHi`): `#EEF1F8`
    - Hairline Border (`line`): `#E2E6F0`
    - Primary Text (`text`): `#0B0D14` (Deep Ink)
    - Secondary Text (`textDim`): `#52586E`
  - **Shared Semantic Accents**:
    - Electric Violet (`violet`): `#7C6BFF` (Primary interactive accent)
    - Cyber Teal (`teal`): `#20D6B5` (Encryption & verification accent)
    - Amber Gold (`amber`): `#F5A623` (Folders & sync indicators)
    - Emerald Mint (`mint`): `#10B981` (Completed & verified status)
    - Crimson Danger (`danger`): `#EF4444` (Errors & destructive actions)
- **Typography & Hierarchy**:
  - Clean geometric sans-serif (`BodySansFont`) for titles, filenames, and navigation labels paired with tabular monospace (`NumericMonoFont`) for byte counts, SHA-256 hashes, transfer speeds, and encryption badges.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Per-Chunk AES-256-GCM Encryption vs. Whole-File Encryption**
  - *Chosen Approach*: Encrypt each 18 MB chunk individually with a unique 12-byte IV derived from a per-file 256-bit key stored in the encrypted manifest (and protected by Android Keystore).
  - *Why*: Allows streaming decryption, pause/resume per chunk, and parallel chunk uploads/downloads without requiring double the file size in temporary local storage.
  - *Alternatives Considered*: Encrypting the entire file before splitting into chunks—rejected because encrypting a 2 GB video would require 2 GB of extra disk space and prevent random-access chunk verification.

- **Decision 2: Multi-Bot Load Balancing with Message-ID Resolution**
  - *Chosen Approach*: Distribute chunk uploads across the primary bot and verified standby bots in parallel using a round-robin worker pool, recording the shared channel `telegramMessageId` alongside the uploading bot's `file_id`.
  - *Why*: Telegram scopes `file_id` to individual bots, but `message_id` in the storage channel is universal across all admin bots in that channel. Recording both allows maximum parallel upload speed while keeping every chunk downloadable by any bot in the pool.

- **Decision 3: Self-Contained Encrypted Share URI & QR Code Generation**
  - *Chosen Approach*: Encode file manifest metadata, relay/channel reference, and optional AES key into a compressed Base64 URL-safe `televault://share?...` payload rendered via a native ZXing/Canvas QR matrix.
  - *Why*: Works completely serverless without requiring an external web database, maintaining TeleVault's zero-server privacy guarantee.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        UI LAYER (Jetpack Compose)                       │
│  ┌──────────────┐ ┌──────────────────┐ ┌─────────────┐ ┌─────────────┐  │
│  │  HomeVault   │ │ StorageAnalytics │ │ FolderSync  │ │ Media/Doc   │  │
│  │ (Tags/Filter)│ │   & Duplicates   │ │   Manager   │ │  Previewer  │  │
│  └──────┬───────┘ └────────┬─────────┘ └──────┬──────┘ └──────┬──────┘  │
│         └──────────────────┼──────────────────┼───────────────┘         │
│                            ▼                  ▼                         │
│                   ┌─────────────────────────────────┐                   │
│                   │       TeleVaultViewModel        │                   │
│                   └────────┬───────────────┬────────┘                   │
└────────────────────────────┼───────────────┼────────────────────────────┘
                             ▼               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     DOMAIN & TRANSFER ENGINE LAYER                      │
│  ┌──────────────────────────┐  ┌─────────────────────────────────────┐  │
│  │   CryptoStreamEngine     │  │     MultiBotLoadBalancer            │  │
│  │ (AES-256-GCM Chunk I/O)  │  │ (Round-Robin Bot Pool & Rate Limit) │  │
│  └────────────┬─────────────┘  └──────────────────┬──────────────────┘  │
│               └─────────────────┬─────────────────┘                     │
│                                 ▼                                       │
│                   ┌───────────────────────────┐                         │
│                   │      TransferManager      │                         │
│                   └──────┬─────────────┬──────┘                         │
└──────────────────────────┼─────────────┼────────────────────────────────┘
                           ▼             ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────┐
│     LOCAL PERSISTENCE (Room)      │ │     TELEGRAM CLOUD BACKEND        │
│ ┌───────────┐ ┌─────────────────┐ │ │ ┌───────────────┐ ┌─────────────┐ │
│ │FileEntity │ │SyncFolderEntity │ │ │ │Primary Channel│ │Backup /     │ │
│ │(Tags/AES) │ │ & StandbyBots   │ │ │ │(Chunks/Index) │ │Relay Channel│ │
│ └───────────┘ └─────────────────┘ │ │ └───────────────┘ └─────────────┘ │
└───────────────────────────────────┘ └───────────────────────────────────┘
```

### Data Model & State Evolution

1. **Extended `FileEntity` & Room Migration (`MIGRATION_9_10`)**:
   - `isEncrypted: Boolean` — Indicates if chunks are encrypted with AES-256-GCM.
   - `encryptionKeyBase64: String?` — Per-file AES-256 key (encrypted at rest via Android Keystore before saving or included in encrypted manifest).
   - `tags: String` — Comma-separated custom tag labels (e.g., `"Work,Encrypted,Important"`) indexed for fast filtering.
2. **New `SyncFolderEntity` Table**:
   - `id: String`, `localTreeUri: String`, `localDisplayName: String`, `targetVaultFolderId: String?`, `lastSyncedAt: Long`, `autoSyncEnabled: Boolean`, `encryptOnSync: Boolean`, `syncedFilesCount: Int`.
3. **Multi-Bot Load Balancer State**:
   - Reads active primary token + verified `StandbyBotEntity` tokens from `StandbyBotDao`.
   - Tracks per-bot active concurrency, bytes transferred, and automatic cooldown backoff if Telegram returns HTTP 429 (`retry_after`).

### Interactive Component & State Mapping

- **Theme Switcher (`HomeScreen` & `SettingsScreen`)**: Toggles between True OLED Black (`#000000`) and Rich White (`#FFFFFF`) immediately via `TeleVaultViewModel.toggleTheme()`, persisting preference in `EncryptedCredentialsManager`.
- **Smart Filter & Tag Bar (`HomeScreen` & `FileDetailSheet`)**: Selecting filter chips or tag pills updates `activeCategoryFilter` and `selectedTagFilter` in `UiState`, reactively filtering the observed `Flow<List<FileEntity>>`. Adding/removing tags on a file updates Room and triggers a debounced vault index sync.
- **Storage Analytics Modal/Screen (`StorageAnalyticsScreen`)**: Computes category byte totals, encrypted file ratio, and groups files by `checksum` where `count > 1` to surface duplicate files with a one-tap **"Clean Up Duplicates"** action.
- **Folder Sync Screen (`FolderSyncScreen`)**: Uses Android `ActivityResultContracts.OpenDocumentTree` to grant persistent SAF directory access, compares local children against existing Vault filenames/sizes, and enqueues new files into `TransferManager.enqueueBatchUpload()`.
- **Enhanced Previewer (`FilePreviewScreen`)**: Automatically decrypts encrypted chunks into a secure in-memory/temp buffer on view, supporting interactive image pan/zoom, native ExoPlayer/MediaPlayer audio-video controls, and scrollable code/text/PDF rendering.
- **QR & Deep-Link Share Dialog (`ShareFileDialog`)**: Renders a scannable QR code bitmap on-device for the generated share link and provides an **"Import Shared Link / Code"** dialog on the Home screen to ingest shared files directly.
