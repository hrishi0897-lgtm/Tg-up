# Unified Single-Color Theme (Signature Violet)

Unify the entire application's visual design around a single **Signature Violet** accent palette (`#7C6BFF` / `#8C7CFF` on Dark OLED, `#6B59F5` on Light) using solid flat fills and subtle opacity shades, eliminating all color mixing (no teal, amber, mint, cyan, or multi-color gradients).

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Confirmed Decision 1 — Single Signature Violet Accent Palette**: Every interactive accent, badge, icon container, active indicator, FAB, category bar, and status highlight across the app will use **Signature Violet** and its subtle opacity shades instead of mixing violet, teal, mint, amber, and file-type rainbow colors.
> - **Confirmed Decision 2 — Solid Flat Fills & Opacity Shades Only**: Replace all multi-color linear/radial gradients (`violet -> teal`, `amber -> violet`, `teal -> mint`) with clean, solid flat fills and tonal opacity variations of Signature Violet (`10%`, `16%`, `32%`, `65%`, `100%`).

---

## 1. Overview & Core Concept

- **What It Does**: Establishes a cohesive, distraction-free monochromatic accent system where Signature Violet is the sole accent color throughout every screen, dialog, card, and navigation bar.
- **Target Audience / Persona**: Users who prefer a disciplined, single-accent interface without competing secondary colors or rainbow category indicators.
- **Key Value**: Creates visual harmony across the Vault dashboard, Bento storage meter, file/folder list and grid cards, Transfer Queue, bottom navigation bar, and settings screens.

---

## 2. User Experience & Visual Design

- **Visual Identity & Theme**:
  - **Aesthetic Direction**: Monochromatic Signature Violet on deep OLED Black (`#000000` / `#0A0C12`) and crisp Light surfaces (`#F7F8FC` / `#FFFFFF`).
  - **Color Palette & Tonal Hierarchy**:
    - **Primary Accent (`violet`)**: `#7C6BFF` (Dark) / `#6B59F5` (Light) — used for primary buttons, FAB, active navigation capsule, progress bars, and primary icons.
    - **Medium Violet Shade (`violet` @ 70% opacity)**: Used for secondary category indicators and sub-metrics.
    - **Soft Violet Tint (`violet` @ 12%–16% opacity)**: Used for badge backgrounds, icon containers, and selected card fills.
    - **Subtle Violet Border (`violet` @ 30%–40% opacity)**: Used for active card and badge outlines.
- **Component Styling Updates**:
  1. **Theme & Color Tokens**:
     - Map secondary/tertiary accent tokens (`teal`, `amber`, `mint`, category colors, and file-type icon colors) in the theme palette to cohesive shades of Signature Violet so every screen in the app automatically inherits the single-color theme without color mixing.
     - Update 3D buttons and the primary `+` FAB (`Fab3D`) to use Signature Violet instead of teal.
  2. **Bento Storage Grid**:
     - Replace the multi-color gradient hero background and border with a clean solid surface and a subtle Signature Violet border/tint.
     - Style the segmented storage bar and the `Documents`, `Media`, and `Other` tiles using distinct opacity shades of Signature Violet (`100%`, `70%`, `45%`) rather than mixing violet, teal, and amber.
  3. **Vault File & Folder Cards & Quick-Action Menu**:
     - Style all file-type icons, `FOLDER` badges, `AES-256` badges, `TGC` local badges, and quick-action menu icons in unified Signature Violet opacity shades.
  4. **Transfer Queue Cards**:
     - Replace multi-color gradient progress bars and mixed-color card washes with solid Signature Violet progress fills and subtle violet-tinted icon badges.
  5. **Floating Bottom Navigation Bar**:
     - Replace the violet-to-teal gradient border and capsule with a solid Signature Violet active capsule (`16%` fill, `40%` border) and Signature Violet active icons/badges.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Differentiating Storage Categories Without Mixing Colors**
  - *Chosen Approach*: Use three tonal opacity levels of Signature Violet (`100%`, `72%`, `48%`) for `Documents`, `Media`, and `Other` in the Bento storage breakdown.
  - *Why*: Preserves visual distinction between segments in the storage bar while strictly adhering to the single-color rule.
- **Decision 2: Centralized Token Unification + Component Cleanup**
  - *Chosen Approach*: Align both the centralized `TeleVaultColors` / file-category color constants and the individual screen composables to Signature Violet, and remove all multi-color `Brush.linearGradient` / `Brush.horizontalGradient` mixes.
  - *Why*: Guarantees zero stray teal, amber, or mint accents across all screens, sheets, and dialogs.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   Centralized Theme Palette (Color / Theme)             │
│  • Primary Accent: Signature Violet (#7C6BFF Dark / #6B59F5 Light)      │
│  • Tonal Shades: 100%, 72%, 48% Violet for categories & file icons      │
│  • Solid Fills & Subtle Violet Opacity Containers (12% fill, 32% line)  │
└───────────────┬─────────────────────────┬───────────────────────────────┘
                │                         │
                ▼                         ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────┐
│     HomeScreen & Bento Grid       │ │   Transfers & Floating BottomNav  │
│  • Solid surface Hero + Violet    │ │  • Solid Violet progress fill     │
│    tonal storage segments         │ │  • Solid Violet active tab pill   │
│  • Unified Violet file/folder     │ │  • Violet badge & action icons    │
│    icons, TGC & AES-256 badges    │ │  • Violet 3D FAB & Quick Menu     │
└───────────────────────────────────┘ └───────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - All interactive states (selected file cards, active filter chips, active bottom navigation tab, active Bento category filter, and transfer progress bars) transition cleanly between neutral surface tokens (`surface`, `surfaceHi`, `line`, `textDim`) and Signature Violet tonal states (`violet`, `violet.copy(alpha = 0.14f)`, `violet.copy(alpha = 0.35f)`).
