# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows [Semantic Versioning](https://semver.org/).

## [0.1.0] - 2026-11-07

### Added
- MVP: `CanvasScreen` four-layer renderer with enter/exit fade + 0.98→1.0 scale, keyboard focus roaming and Esc back-stack.
- `Render2D` primitives: round rect, four-corner gradient, stroke, soft drop shadow, nine-slice, accent glow, safe blur with automatic fallback.
- Layout system: `Anchor` / `Absolute` / `Flex` / `Region` (Hero, Rail, Cards, Dock, Context); all spacing from `Theme.gap`.
- Widgets: `Button`, `Slider`, `Toggle`, `SearchBox` (120 ms debounce), `TabBar`, `Rail`, and the core `ItemCard` with hover expansion, rarity outlines, right-click context layer and vanilla `Slot` drag bridge.
- Theme system: JSON-driven `Theme` + `ThemeLoader` (missing fields fall back to defaults) and three presets: `Neon Cyber`, `Genshin Archive`, `Mono Minimal`.
- Animation: `Animator` global virtual clock, `Tween`, four easing curves, all transitions ≤ 320 ms, respects reduce-motion.
- Client-only `E` inventory takeover via Fabric lifecycle events; underlying `Menu`/`Slot` and vanilla packets untouched.
- GitHub Release update chain: `UpdateChecker` → `SemVer` → `Downloader` → `Updater` (download + restart swap + `update.lock`, never hot-swap).
- `ReleaseUploader` + Gradle `publishRelease` task; token read exclusively from `-PgithubToken` / `GH_TOKEN`, never stored in the repository.
- `SettingsScreen` for theme switching, takeover toggles and update UI.
- MIT license, README, architecture document, `.gitignore` with secret exclusions.

### Security
- Update source is fixed by config and cannot be changed at runtime (anti-poisoning).
- Optional SHA-256 verification of downloaded assets.
- No credentials, absolute local paths or personal data are committed.
