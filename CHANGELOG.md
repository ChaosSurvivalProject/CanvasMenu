# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows [Semantic Versioning](https://semver.org/).

## [0.2.0] - 2026-11-07

### Added — 内置贴图指令面板（Command Center）

- New `com.canvasmenu.*` module: second client entrypoint `CommandCenter`, `F7` shortcut and an entry button in the canvas inventory.
- `CommandPanelScreen`: full-screen canvas with a left category `CommandRail` (9 real categories + virtual "All"/"Favorites") and a right flowing `CommandGridLayout` with scroll + scissor clipping.
- `CommandCardWidget`: nine-slice rounded panel, four-corner gradient, continuous hover lift (≤3px) + accent glow stroke, favourite badge; left click fills the chat bar **without auto-sending**, right click toggles favourites, Enter/Space for keyboard access.
- `CommandTooltip` hover overlay showing the raw command and its description.
- JSON-driven `CommandRegistry` reading `assets/canvasmenu/commands.json` (31 commands) with a player override at `config/canvasmenu-commands.json`; per-field fallbacks, duplicate-id guard, hot reload.
- `Favorites` persisted to `config/canvasmenu-favorites.json`.
- `ChatBridge` opens the vanilla `ChatScreen` with the command as initial text (reflection-based, clipboard fallback).
- 41 real 64×64 RGBA PNG icons under `textures/commands/`, flat + micro-gradient + thin outline, grouped by category; `IconTextures` loads them via `ResourceManager` on virtual threads with a run-length paint path; `IconBlitter` falls back to a tinted badge + glyph when a texture is missing.
- Three theme presets: `night_indigo` (暗夜靛蓝), `mist_gray` (雾灰极简), `neon_cyber` (霓虹赛博).
- `AutoUpdater`: complete GitHub Release chain — startup check, SemVer compare, one-click download on virtual threads, SHA-256 verification, `mods/.canvasmenu-update/` staging, atomic `update.lock`, swap on exit, **never hot-swaps a running jar**. Failures degrade silently.
- `GuiCompat`: reflection-based compatibility for 1.21.8+ `Matrix3x2fStack`, 6-arg ARGB `drawString` and `ResourceManager` lookups, with safe fallbacks.
- `.github/workflows/build.yml` (real CI build) and `release.yml` (tag → jar + `.sha256` → GitHub Release).
- Docs: `docs/TEXTURES.md`, rewritten README with build/release/usage instructions.

### Changed

- Upgraded `fabric-loom` 1.7.4 → **1.17.21** and Gradle 8.10.2 → **9.5.0** for Minecraft 1.21.11 (1.18+/1.19+ loom are Java 25 bytecode and incompatible with JDK 21); foojay resolver → 1.0.0; Gradle Wrapper files synced to 9.5.0.
- `CanvasScreen` pose transform now goes through `GuiCompat.withTransform` (no direct `PoseStack` calls).
- Update chain consolidated into `com.canvasmenu.update.AutoUpdater`; the inventory entrypoint no longer performs its own startup check.
- `fabric.mod.json`: two client entrypoints, version 0.2.0, extended `custom.canvasmenu` metadata.

### Fixed — 1.21.11 真实 API 适配（经 GitHub Actions 实际编译验证）

The first CI builds failed with 15 `compileJava` errors because the canvas layer
was written against the pre-1.21.9 GUI API. All were fixed against Mojang's
official 1.21.11 client mappings:

- `Screen.resize(int, int)` no longer takes a `Minecraft` argument.
- Mouse / keyboard callbacks now use immutable records from
  `net.minecraft.client.input`: `mouseClicked(MouseButtonEvent, boolean)`,
  `mouseReleased(MouseButtonEvent)`, `mouseDragged(MouseButtonEvent, double, double)`,
  `keyPressed(KeyEvent)`, `charTyped(CharacterEvent)`.
  `CanvasScreen` unpacks them into the version-independent
  `(double, double, int)` widget API — no widget code had to change.
  A new `onMouseReleased` hook keeps the inventory drag-swap working.
- `KeyMapping`'s 4th constructor argument is now a `KeyMapping.Category`
  (`MISC`), not a translation key.
- `Window.getWindow()` was replaced by `Window.handle()`.
- `ChatScreen` is constructed as `(String initial, boolean isDraft)` since
  1.21.9. `ChatBridge` probes that signature first, so clicking a command card
  really fills the chat bar instead of silently degrading to the clipboard.
- Runtime reflection targets re-verified against the mappings:
  `Minecraft.getResourceManager()`, `ResourceProvider.getResource(Identifier)`
  returning `Optional`, `EditBox.setValue(String)`,
  `MultiPlayerGameMode.handleInventoryMouseClick(int,int,int,ClickType,Player)`.

Verified by a clean GitHub Actions `./gradlew build` on JDK 21:
**BUILD SUCCESSFUL**, producing `canvas-menu-0.2.0.jar` (257 KB) and
`canvas-menu-0.2.0-sources.jar` (188 KB).

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
