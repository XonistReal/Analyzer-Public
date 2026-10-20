# Analyzer

A Minecraft Java **1.21.4** utility client built with **Fabric and Java 21**. Analyzer combines shader menus and HUDs, world visuals, ESP, combat and movement tools, local task automation, and player cosmetics.

**[Download the latest release](https://github.com/XonistReal/Analyzer-Public/releases/latest)** · **[Source ZIP (main)](https://github.com/XonistReal/Analyzer-Public/archive/refs/heads/main.zip)** · **[All modules and settings](MODULES.md)** · **[Installation](docs/INSTALLATION.md)** · **[Build guide](docs/DEVELOPING.md)**

![Analyzer Studio with the Glass theme](screenshots/analyzer-liquid-studio-glass.png)

## Download

Open the [latest release](https://github.com/XonistReal/Analyzer-Public/releases/latest) and choose an asset:

| You want to… | Download |
| --- | --- |
| Import a ready-to-use Prism Launcher instance | `Analyzer-<version>-Prism-1.21.4.zip` |
| Add Analyzer to an existing Fabric instance | `analyzer-<version>.jar`, plus the required Fabric API |
| Read or modify the complete project | The release's source ZIP, or clone this repository |
| Verify a download | Compare it with the release's `SHA256SUMS.txt` |

The current release is **0.15.0**. The Prism package includes Analyzer and Fabric API; use your own Minecraft account and a Java 21 installation. Manual installs target Fabric Loader **0.16.14** and Fabric API **0.119.4+1.21.4**. Other Minecraft versions require a different build. [Installation and updating →](docs/INSTALLATION.md)

## Modules and settings

The [module catalogue](MODULES.md) is generated from the same definitions as the in-game menu. It lists every registered module, its behavior, whether it starts enabled, and its settings with defaults, numeric bounds and available choices. [MODULES.json](MODULES.json) provides the same registry as structured data.

| Area | Examples | Details |
| --- | --- | --- |
| Menus and HUD | Studio and Click GUI, editable widgets, Glass/Classic/Minimal/Neon themes, custom Home screen | [Using the menu](docs/MODULE-GUIDE.md), [Home](HOME-SCREEN.md), [liquid glass](LIQUID-GLASS.md) |
| ESP and finders | Entity and storage debug boxes, screen-locked tracers, Advanced Stash Finder, Amethyst Finder, local Findings history | [ESP](COMBAT-ESP.md), [finders](FINDERS.md), [tracers](TRACERS-AND-NAME.md) |
| World rendering | Five posed-model chams materials, three X-ray styles, procedural and imported skyboxes | [Chams](ENTITY-CHAMS.md), [X-ray](XRAY.md), [skyboxes](CUSTOM-SKYBOX.md) |
| Combat and movement | Kill Aura and opt-in silent rotations, target filters, defensive inventory tools, movement and placement helpers | [Combat](COMBAT-ESP.md), [silent aim](SILENT-AIM.md), [settings](GAMEPLAY-SETTINGS.md) |
| Automation | Local material requests, native pathfinding and mining, tool recovery, schematic ghosts and material-first building | [Automation](AUTOMATION.md), [construction](SCHEMATIC-CONSTRUCTION.md), [protection](SAFETY.md) |
| Privacy | Local name masking and configurable server-information protections | [Name Changer](TRACERS-AND-NAME.md), [Server Privacy](SERVER-PRIVACY.md) |
| Cosmetics | Angel, Dragon, Bat and Parrot wings on a shared articulated rig; cape, pets and visual effects | [Wings and settings](WINGS.md), [Cosmetics catalogue](MODULES.md#cosmetics) |

**New in 0.15.0:** the four approved wing looks are wearable on the local player's third-person model, with animation, scale and placement controls. [Ornate hat concepts](HAT-DESIGNS.md) remain review assets. [Release history →](VERSION-HISTORY.md)

ESP and finders use received, loaded game data. Automation uses Analyzer's own local controllers, requires explicit plan review and Start, and reports unsupported requirements. Wing cosmetics are local to your client. Server Privacy reduces specific information leaks; it does not hide your account, IP or gameplay behavior. The feature documents describe these boundaries and [VALIDATION.md](VALIDATION.md) separates automated checks from observed gameplay. Renderer compatibility, including Iris/Sodium, has not been established for every feature.

## First launch

1. Press **Right Shift** in game, or choose **Modules** on Analyzer's Home screen.
2. Search for a module and expand its settings. Its switch controls whether it is enabled.
3. Open **HUD Studio** to choose **Studio / Click GUI**, menu appearance and HUD placement. Menu and HUD themes are independent.
4. For tasks, open **Automation**, enter a goal or choose a schematic, then review the plan before starting it.

Gameplay automation starts disabled on a fresh install. Profiles can replace active module settings and keybinds; inspect a profile before using it. Settings, profiles and friends are stored under the instance's `config/analyzer/` directory. [Menu, settings and profiles →](docs/MODULE-GUIDE.md)

## Source and contributions

```sh
git clone https://github.com/XonistReal/Analyzer-Public.git
cd Analyzer-Public
```

With a **Java 21 JDK**, run `./gradlew build moduleCatalog` on Linux/macOS or `.\gradlew.bat build moduleCatalog` in Windows PowerShell. The installable mod is `build/libs/analyzer-0.15.0.jar`; the `-sources.jar` is for reading source, not playing.

Start with the [source map and build guide](docs/DEVELOPING.md), then read [CONTRIBUTING.md](CONTRIBUTING.md). Module definitions live in `core`, native screens in `ui`, game actions in `gameplay` and `automation`, and rendering in `render`, `effects`, `skybox` and `cosmetic`. The [CI workflow](.github/workflows/build.yml) builds with Java 21 and checks that the generated catalogue stays current.

Analyzer is [MIT licensed](LICENSE). Included wing assets/code ports, fonts and dependencies retain their own notices in [THIRD-PARTY.md](THIRD-PARTY.md), [WINGS-MIT-LICENSE.txt](WINGS-MIT-LICENSE.txt) and [DEPENDENCY-SOURCES.md](DEPENDENCY-SOURCES.md). Research references and historical archives are documented separately from current runtime dependencies.

[Public history and release scope](PUBLICATION.md) explains the retained version tags, archival omissions and historical checksum records.
