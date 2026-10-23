# Fix Light Mode Contrast & Monochrome Accents in Vault Settings

## Goal
Fix the invisible headers, icons, status badges, borders, and action buttons in **Vault Settings** when Light Mode is active. Currently, `SettingsScreen` overrides `violet`, `teal`, `amber`, `mint`, and `danger` with `Color.White` unconditionally, causing white-on-white rendering against Light Mode's white (`#FFFFFF`) and light gray (`#EEF1F8`) card surfaces.

## Key Changes

### 1. Adaptive Monochrome Palette in `SettingsScreen.kt`
- Inspect `baseColors.isDark` when constructing the local `colors` palette for `SettingsScreen`:
  - **Dark Mode (`baseColors.isDark == true`)**: Keep `primaryAccent = Color.White` and `dimAccent = Color(0xFFB0B8C8)`, with `onAccent = Color(0xFF0A0C12)` for text/icons rendered inside solid accent buttons and active switches.
  - **Light Mode (`baseColors.isDark == false`)**: Set `primaryAccent = Color(0xFF0B0D14)` (deep monochrome ink) and `dimAccent = Color(0xFF474E64)`, with `onAccent = Color.White` so solid accent buttons (e.g., *Start 5-Chunk Test Transfer*, selected chunk size pill `18 MB`, *Optimize*, *Save*, dialog confirm buttons, and active `Switch` thumbs) display crisp white text/icons on a dark monochrome fill.
- Apply `primaryAccent` to `violet`, `teal`, `amber`, `mint`, and `danger` in the `SettingsScreen` local color override so all section card headers (`SettingsCard`), icons, badges (`Multi-Bot Parallel Load Balancer`, `Active`, `Primary Bot Token Healthy`, `120 Hz`), action links (`Configure ➔`, `Import ➔`, `Check Now`, `Add Standby Bot`), and bordered action rows (`Disconnect Vault & Clear Credentials`, `Restore from Backup Channel`) have high-contrast visibility in both Light and Dark themes.

### 2. Solid Button & Switch Contrast Consistency
- Update all references to `onWhiteAccent` in `SettingsScreen.kt` (including dialog confirm buttons, chunk-size selector chips, test transfer CTA, and `SwitchDefaults.colors`) to use the theme-aware `onAccent` color (`Color(0xFF0A0C12)` in Dark Mode, `Color.White` in Light Mode).
- Ensure `Recovery Completed Dialog` confirm button uses `onAccent` instead of `colors.bg` for consistent monochrome contrast.
