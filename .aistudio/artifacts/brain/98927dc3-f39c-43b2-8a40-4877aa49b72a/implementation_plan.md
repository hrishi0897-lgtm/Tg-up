# First-Launch `Download/TGC` Storage Setup & Visual Design Overhaul

This update introduces a first-launch storage access prompt that automatically provisions the dedicated `Download/TGC` directory on the device, paired with a comprehensive visual polish pass across the Bento storage header, file and folder cards, Transfer Queue progress cards, and a floating glassmorphic bottom navigation bar.

## User Review & Critical Decisions

> [!IMPORTANT]
> Your preferences from the clarification step have been incorporated into this plan:

- **First-Launch Storage Prompt & Auto-Creation of `Download/TGC`**: On first launch, the app presents a clean, focused storage setup prompt that requests file/storage access (when required by the Android OS level) and immediately creates the `Download/TGC` directory inside the phone's `Download` folder, verifying write readiness.
- **Full Visual Design Upgrade**:
  1. **Elevated Bento Header & Storage Meter**: Multi-stop glassmorphic surface gradients, a segmented multi-category storage bar inside the hero card, and glowing accent borders.
  2. **Refined File & Folder Cards**: Color-coded category icon badges (Documents, Media, Archives, Code/Audio), subtle surface elevation gradients, and crisp `Download/TGC` local availability pills.
  3. **Polished Transfer Queue Cards**: Animated progress glow accents, live throughput/chunk status pills, and tactile control pills for Pause, Resume, and Cancel.
  4. **Redesigned Floating Bottom Navigation Bar & Quick-Action Sheet**: A floating pill-shaped dock with a sliding active capsule indicator and a quick-action creation sheet (`Upload Files`, `New Folder`, `Auto Sync`, `Import Share Link`).

---

## 1. Overview & Core Concept

- **What It Does**:
  1. Ensures the device's `Download/TGC` folder exists from the moment the user first opens the app, with an explicit first-launch storage access prompt and instant directory creation.
  2. Elevates the app's visual craftsmanship with rich depth, glassmorphic gradients, tactile micro-interactions, and a floating navigation architecture.
- **Target Audience / Persona**: Users who want a fast, native-feeling, visually refined cloud vault that stores downloaded files predictably in `Download/TGC`.
- **Key Value**: Immediate confirmation that `Download/TGC` is ready on the device, paired with a high-craft dark/light OLED interface.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **First-Launch Storage Setup & `Download/TGC` Creation**:
     - When the app opens for the first time, a sleek modal card appears prompting for storage access to initialize `Download/TGC`.
     - Tapping **Grant & Create Folder** requests runtime storage permission (on Android 9 and below where `WRITE_EXTERNAL_STORAGE` applies) and immediately initializes `Download/TGC` on disk (and via `MediaStore.Downloads` on Android 10+), displaying a confirmation toast/badge (`Download/TGC ready`).
  2. **Vault Home & Quick-Action Sheet**:
     - The upgraded `StorageBentoGrid` hero card features a radial/linear violet-to-teal atmospheric glow and a unified segmented progress bar visualizing Documents, Media, and Other usage at a glance.
     - Folder and file cards display color-matched category squircles, encryption/local-saved status pills, and smooth press-scale feedback.
     - Tapping the primary action button or bottom dock action opens a streamlined Quick-Action Sheet for uploading files, creating folders, or importing shared links.
  3. **Transfer Queue Experience**:
     - Active uploads and downloads display a glowing progress bar with a subtle top highlight, chunk completion pills, and high-contrast Pause/Resume/Cancel action buttons.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: High-craft dark OLED luxury (`#07080C` background, `#11131C` elevated surface, `#191C28` high surface) with crisp 1dp translucent borders and subtle radial gradient highlights.
  - *Color Palette & Mood*: Electric Violet (`#7C5CFF`) primary accent, Cyber Teal (`#00D2B4`) secondary accent, Amber (`#FFB020`) archive/other accent, and Emerald Mint (`#10B981`) verified/local badge accent.
  - *Typography & Hierarchy*: Bold geometric sans-serif headers paired with tabular monospace (`NumericMonoFont`) for byte counters, speeds, percentages, and paths (`Download/TGC`).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Cross-Version `Download/TGC` Directory Creation on First Launch**
  - *Chosen Approach*: Combine direct directory creation (`Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)/TGC`) with MediaStore `Download/TGC` registration so file managers immediately list the `TGC` folder inside `Download` on both Android 10+ (Scoped Storage) and Android 9 and below.
  - *Why*: Pure MediaStore only materializes folders lazily when a file is downloaded unless explicitly initialized; creating the directory eagerly on first launch ensures the user can see `Download/TGC` right away in their phone's Files app.
- **Decision 2: Floating Pill Navigation Dock**
  - *Chosen Approach*: Upgrade the flat bottom bar into a floating, rounded pill dock with a spring-animated active background capsule and badge counter, while preserving all existing test tags (`tab_nav_vault`, `tab_nav_transfers`).
  - *Why*: Creates a modern, custom-crafted mobile silhouette with proper `navigationBarsPadding()` inset handling.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                             MainActivity                                │
│  • Checks isFirstLaunchStorageSetupDone on startup                      │
│  • Renders FirstLaunchStorageDialog -> requests permission & calls      │
│    DownloadStorageManager.initializeTgcFolder(context)                  │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌───────────────────┐   ┌───────────────────────┐   ┌─────────────────────┐
│ DownloadStorage   │   │   HomeScreen & Bento  │   │  TransfersScreen &  │
│ Manager           │   │   Storage Grid        │   │  Floating Nav Dock  │
│ • Creates         │   │ • Segmented multi-bar │   │ • Glow progress bar │
│   Download/TGC    │   │   hero card + radial  │   │ • Pill status tags  │
│ • MediaStore +    │   │   gradient surface    │   │ • Floating pill     │
│   File mkdirs()   │   │ • Rich file/folder    │   │   bottom nav bar    │
│                   │   │   category badges     │   │                     │
└───────────────────┘   └───────────────────────┘   └─────────────────────┘
```

- **Data Model & State**:
  - `CredentialsManager`: Adds `isStorageSetupCompleted()` and `setStorageSetupCompleted(true)` backed by preferences so the first-launch prompt only appears until completed.
  - `DownloadStorageManager.initializeTgcFolder(context)`: Creates `Download/TGC` directory structure immediately and verifies access.
- **Interactive Component & State Mapping**:
  - `FirstLaunchStorageDialog`: Triggered when `!uiState.isStorageSetupCompleted`; handles runtime permission result and invokes `viewModel.completeFirstLaunchStorageSetup()`.
  - `StorageBentoGrid`, `HomeScreen`, `TransfersScreen`, and `TeleVaultBottomNav`: Upgraded with layered gradient brushes, segmented storage meter, category icon badges, and floating pill navigation while keeping all existing Compose `testTag` identifiers intact.
