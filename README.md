# Analyzer

An original visual utility client for **Minecraft Java Edition 1.21.4**, built on Fabric and Java 21. Mint and violet, a geometric identity, native controls, and GPU effects. Analyzer 0.1.1 adds Prism integration and a local development updater to the playable visual client.

![Analyzer native interface](screenshots/analyzer-ui.png)

## Install

**Prism Launcher:** import `Analyzer-0.1.1-Prism-1.21.4.zip`. It includes both mods and the Windows local updater. See [PRISM.md](PRISM.md) for drag and drop, automatic updates and the development workflow.

1. Create a **Minecraft 1.21.4 Fabric** instance using the [official Fabric installer](https://fabricmc.net/use/installer/) or your launcher's Fabric instance option. Use Fabric Loader **0.16.10 or newer**; the verified build uses **0.16.14**.
2. Copy **both JARs** from a release bundle into that instance's `mods/` folder: the Analyzer JAR and `fabric-api-0.119.4+1.21.4.jar`. The Prism ZIP keeps them under `minecraft/mods/`; the original loose-mod ZIP used `mods/`.
3. Launch that instance. Click **Analyzer** on the title screen, or press **Right Shift** while playing.

Minecraft 1.21.4 uses Java 21. The official Minecraft Launcher supplies its game runtime. For an alternative launcher, select its Java 21 runtime. Analyzer does not replace the Minecraft launcher and does not require an account of its own.

## What's implemented

| Feature | Controls and behavior |
| --- | --- |
| Native interface | Search, category navigation, module cards, a scrolling settings inspector, animated toggles, hover states, scalable layout, custom Rajdhani typography and a geometric Analyzer mark. |
| Entity ESP | Interpolated boxes or corner outlines with translucent fill and a soft outer stroke; separate filters and colors for players, hostile mobs, passive mobs and dropped items. Adjustable range and line width, optional visibility through walls, name/distance labels and actual health indicators. |
| Storage ESP | Chests, barrels, shulker boxes, ender chests, hoppers and furnaces, with distinct colors and container/distance labels. Uses loaded chunk block entities and a bounded cache. |
| Tracers | View-centered lines to selected entity categories, with range, line width and visibility controls. |
| Atmosphere | Three original built-in GLSL presets: **Aurora**, **Ember**, and **Frost**. Adjustable strength. Highlight bloom, color grading and vignette process the game image before the HUD. |
| Fullbright | Configurable local lightmap brightness. Toggling it off restores ordinary lighting without changing the player's saved gamma option. |
| HUD | Real FPS, position/dimension, active visual modules, and a configurable crosshair. |
| Menu effects | An animated GLSL aurora backdrop, moving radar graphic, ambient particles and bounded glow effects. Particle density can be changed or disabled. |
| Profiles | Save, load and delete local configurations, including keybinds. Curated **aurora**, **ember**, and **minimal** starter profiles are installed once. |
| Persistence | Validated and clamped typed settings, atomic JSON replacement, malformed/future config recovery backups, and safe profile filenames. |

Start with **Profiles → aurora → Load** to try the visual setup. Defaults keep world overlays and grading disabled until you choose them.

## Controls

- **Right Shift** opens/closes the menu; the menu key can be changed in Minecraft's Controls settings.
- **/** focuses module search. **Escape** leaves text entry, then closes the menu.
- Click a card to inspect it; click its switch or press **Space** to toggle it.
- Scroll over cards or the inspector to see additional modules/settings. Arrow keys select cards.
- Click **Keybind**, then press a key. **Backspace/Delete** clears it; **Escape** cancels.
- **Tab** focuses search or profile naming. **Ctrl+A**, **Ctrl+V**, and **Backspace** work in text fields.
- Drag sliders and click preset selectors. Changes save automatically and when the screen closes.

Settings live in the instance's `config/analyzer/config.json`. Profiles live in `config/analyzer/profiles/`. Recovery files use `config-recovery-*.json`. Remove or edit profiles through the menu; first-launch starter profiles are not recreated after deletion.

## Shader and compatibility scope

The built-in shaders are actual GPU screen effects, including bloom and cinematic grading. They do not add shadow maps, water reflections, volumetric clouds or a replacement world-lighting pipeline. Those are separate world shader-pack features. Iris is not bundled, and compatibility with Iris/Sodium or other rendering mods has not been validated in this release.

Entity/storage overlays operate on entities and containers loaded by the game. They do not discover server-side information that has not been sent to the client. This release contains eight implemented visual/world/HUD modules; combat and movement automation are outside its current scope.

The release is pinned to **1.21.4**. Other Minecraft versions require source changes and a new build. Runtime validation details are in [VALIDATION.md](VALIDATION.md). Research and upstream comparisons are in [RESEARCH.md](RESEARCH.md).

## Build from source

Install a Java 21 JDK, set `JAVA_HOME` to it, and run:

```powershell
.\gradlew.bat build
```

On macOS/Linux:

```sh
./gradlew build
```

The Gradle wrapper downloads Gradle 8.12 and checks its SHA-256. Gradle/Loom fetch the pinned official Minecraft, Yarn, Fabric Loader and Fabric API dependencies. The installable remapped JAR appears in `build/libs/analyzer-0.1.1.jar`.

```powershell
.\gradlew.bat coreCheck   # configuration, profile and recovery assertions
.\gradlew.bat runClient   # interactive development instance
.\gradlew.bat runSmoke    # automated UI + isolated singleplayer render checks
```

`runSmoke` uses a hidden development window and the project-local `run/` directory. It creates/updates only `run/saves/Analyzer-Smoke`, exercises controls and shader presets, captures screenshots, reloads resources, and exits. Installed release builds never activate this development harness.

Optional pixel validation after the smoke run: `python tools/verify_render.py` (Python with Pillow).

## Code layout

- `core/`: version-independent module definitions, typed settings and config storage.
- `ui/`: native canvas, drawing primitives, interactive inspector and profiles.
- `render/`: world overlays, container cache, labels and HUD.
- `effects/`: original GLSL compiler, framebuffer copy, rendering and state restoration.
- `mixin/`: narrow, version-specific game hooks for post-processing, fullbright and crosshair replacement.

Analyzer code is MIT licensed. No client source from GPL projects was copied. Bundled Rajdhani fonts use the SIL Open Font License; see [THIRD-PARTY.md](THIRD-PARTY.md), `FONT-LICENSE.txt`, and `GRADLE-LICENSE.txt`.
