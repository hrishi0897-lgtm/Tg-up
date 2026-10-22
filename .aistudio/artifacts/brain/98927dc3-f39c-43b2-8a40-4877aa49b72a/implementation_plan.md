# TeleVault (TGC) — UI Cleanup, +5px Typography Boost & White Settings Theme

Streamline the Home screen by removing the filter pill row and connection/sync status banners, surface **⚡ Auto Sync** and **🔗 Import Link/QR** in Settings, increase typography size by 5px across the entire app, and style the Settings screen with crisp **White** accents instead of Signature Violet (while keeping Signature Violet on the rest of the app).

## User Review & Critical Decisions

> [!IMPORTANT]
> All four of your requested updates are captured below while preserving the **Signature Violet** single-color theme across the rest of the app.

- **Confirmed Decision 1 — Remove Home Filter Row & Move Actions to Settings**:
  - Remove the horizontal chip bar (`All Files`, `🔒 AES-256`, `📦 >20 MB`, `🕒 Recent 7d`, `⚡ Auto Sync`, `🔗 Import Link/QR`) from the Home screen.
  - Display **⚡ Auto Sync** and **🔗 Import Link/QR** prominently as dedicated action rows in the Settings screen.
- **Confirmed Decision 2 — Remove Connection & Sync Banners from Home Screen**:
  - Remove the `● Backend connected` pill and the `Sync complete: discovered ... from Telegram.` notification banner shown in your screenshot from the Home screen.
- **Confirmed Decision 3 — +5px Text Size Increase Throughout the App**:
  - Increase font sizes across the global typography scale (`Type.kt`), theme density font scaling (`Theme.kt`), and screen text components by **+5px** so all labels, titles, file names, stats, and buttons are significantly larger and easier to read.
- **Confirmed Decision 4 — White Accent Theme in Settings Screen**:
  - Keep **Signature Violet** as the accent color across the main app, but override the Settings screen palette to use **White (`#FFFFFF`)** instead of Signature Violet for all icons, borders, switches, badges, and action buttons (using dark contrast text on solid white buttons so button labels remain crisp and readable).

---

## 1. Overview & Core Concept

- **What It Does**: Cleans up visual clutter above the file list on the Home screen, relocates **⚡ Auto Sync** and **🔗 Import Link/QR** into Settings, boosts app-wide text legibility by +5px, and gives the Settings screen a monochrome **White-on-Dark** aesthetic while keeping **Signature Violet** on the Home, Transfers, and Preview screens.
- **Target Audience / Persona**: Users who want a cleaner, higher-legibility vault interface with larger text and fewer status banners crowding the main file view.
- **Key Value**: More vertical space for files on the Home screen, larger and more readable typography everywhere, and a distinct monochrome White aesthetic inside Settings.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Streamlined Home Screen**: Opens directly to the Top Bar, Storage Bento Grid, Search + Sort/View row, and Vault Files list—without the `Backend connected` pill, `Sync complete` banner, or horizontal filter chip row.
  2. **Settings Screen Actions (`⚡ Auto Sync` & `🔗 Import Link/QR`)**: Inside Settings, users can immediately tap **⚡ Auto Sync** (to configure automatic device folder backup) and **🔗 Import Link/QR** (to import shared links or scan QR codes into `Download/TGC`).
  3. **Monochrome White Settings Screen**: Every section icon, active badge, border highlight, switch thumb/track, and action button inside Settings uses pure **White (`#FFFFFF`)** instead of Signature Violet.
  4. **+5px Larger Text Everywhere**: All headings, file titles, metadata subtitles, storage counters, transfer progress labels, and settings rows render +5px larger across the entire app.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Scoped White Palette Override in `SettingsScreen`**
  - *Chosen Approach*: Provide a Settings-scoped `TeleVaultColors` instance inside `SettingsScreen` where all accent tokens (`violet`, `teal`, `amber`, `mint`, `danger`) map to `Color.White`, and pair solid white buttons with `Color.Black` (`colors.bg`) content text.
  - *Why*: Ensures every card, icon, badge, text field focus border, and button in Settings uses White instead of Signature Violet without affecting the Signature Violet theme on the Home and Transfers screens.
- **Decision 2: App-Wide +5px Typography Scaling**
  - *Chosen Approach*: Increase base `TextStyle` sizes in `Type.kt` and apply a global `fontScale` boost in `TeleVaultTheme` (`Theme.kt`) so both Material typography styles and explicit `.sp` text elements scale up uniformly by +5px across every screen and dialog.
  - *Why*: Guarantees complete, consistent text enlargement across all screens without missing inline `fontSize` declarations.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        TeleVaultTheme (Theme.kt / Type.kt)               │
│  • Main App Accent: Signature Violet (#7C6BFF)                           │
│  • Global Typography Boost: +5px across Type.kt & LocalDensity fontScale │
└───────────────────┬──────────────────────────────────┬───────────────────┘
                    │                                  │
                    ▼                                  ▼
┌──────────────────────────────────────┐  ┌────────────────────────────────┐
│            HomeScreen.kt             │  │       SettingsScreen.kt        │
│ • REMOVED: "Backend connected" pill  │  │ • Accent Override: White       │
│ • REMOVED: "Sync complete" banner    │  │   (#FFFFFF) instead of Violet  │
│ • REMOVED: Horizontal filter &       │  │ • Action Rows:                 │
│   action chip row                    │  │   - ⚡ Auto Sync               │
│ • KEPT: Signature Violet theme       │  │   - 🔗 Import Link/QR          │
└──────────────────────────────────────┘  └────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - **Home Screen Cleanup (`HomeScreen.kt`)**:
    - Remove `item(key = "connection_status_pill")` (`Backend connected` pill).
    - Remove `item(key = "resync_banner")` (`Sync complete: discovered...` banner).
    - Remove the `Row` containing `All Files`, `🔒 AES-256`, `📦 >20 MB`, `🕒 Recent 7d`, `⚡ Auto Sync`, and `🔗 Import Link/QR` under `search_and_filter_row`.
  - **Settings Screen White Theme & Quick Actions (`SettingsScreen.kt`)**:
    - Override `colors` in `SettingsScreen` with `Color.White` for all accent properties (`violet`, `teal`, `amber`, `mint`, `danger`) and `Color(0xFFCCCCCC)` for `violetDim`.
    - Update primary buttons in `SettingsScreen` that use `containerColor = colors.violet` to use `contentColor = colors.bg` (`Color.Black`) so text on white buttons is high-contrast.
    - Display **⚡ Auto Sync** (`settings_open_folder_sync_button`) and **🔗 Import Link/QR** (`settings_import_share_link_button`) with clear labels and icons in the Settings screen.
