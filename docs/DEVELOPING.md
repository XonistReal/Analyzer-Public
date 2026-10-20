# Building and finding your way through the source

[README](../README.md) · [Contributing](../CONTRIBUTING.md) · [Generated module reference](../MODULES.md)

## Build

Clone the repository and use a **Java 21 JDK**. Set `JAVA_HOME` to that JDK if your system has multiple versions.

```sh
git clone https://github.com/XonistReal/Analyzer-Public.git
cd Analyzer-Public
./gradlew build moduleCatalog
```

In Windows PowerShell, use `.\gradlew.bat build moduleCatalog`. If the Unix wrapper lacks executable permission in an extracted source archive, use `bash ./gradlew build moduleCatalog`.

The wrapper selects the pinned Gradle distribution and verifies its distribution checksum. Fabric Loom resolves the Minecraft, Yarn, loader and Fabric API versions in `gradle.properties`.

| Output | Purpose |
| --- | --- |
| `build/libs/analyzer-<version>.jar` | Remapped runtime mod for a Fabric instance |
| `build/libs/analyzer-<version>-sources.jar` | Main source/resource archive |
| `MODULES.md` and `MODULES.json` | Generated registry documentation |

Run `./gradlew runClient` for an interactive development client. Its data is in the checkout's `run` directory rather than your installed Prism instance.

## Source map

All Java packages below are under [`src/main/java/dev/analyzer`](../src/main/java/dev/analyzer).

| Area | Read first | Responsibility |
| --- | --- | --- |
| Entry point | [`AnalyzerClient`](../src/main/java/dev/analyzer/AnalyzerClient.java) | Registers events, renderer/controllers, keybinds, reload and shutdown handling |
| Definitions and saved state | [`BuiltinModules`](../src/main/java/dev/analyzer/core/BuiltinModules.java), [`Setting`](../src/main/java/dev/analyzer/core/Setting.java), [`AnalyzerCore`](../src/main/java/dev/analyzer/core/AnalyzerCore.java) | Typed metadata, defaults, normalized values, aliases and configuration |
| Native menus and HUD | [`MenuScreens`](../src/main/java/dev/analyzer/ui/MenuScreens.java), [`StudioScreen`](../src/main/java/dev/analyzer/ui/StudioScreen.java), [`ClickGuiScreen`](../src/main/java/dev/analyzer/ui/ClickGuiScreen.java), [`HudWidgets`](../src/main/java/dev/analyzer/ui/HudWidgets.java) | Minecraft screens, text/input, settings inspectors and widgets |
| Shader surfaces and overlays | [`effects`](../src/main/java/dev/analyzer/effects), [`render`](../src/main/java/dev/analyzer/render) | GLSL resources, scene capture, state restoration, debug geometry and model passes |
| Gameplay actions | [`GameplayEngine`](../src/main/java/dev/analyzer/gameplay/GameplayEngine.java), [`UtilityEngine`](../src/main/java/dev/analyzer/gameplay/UtilityEngine.java) | Bounded combat, movement, inventory and block actions |
| Tasks and construction | [`TaskPlanner`](../src/main/java/dev/analyzer/automation/TaskPlanner.java), [`AutomationEngine`](../src/main/java/dev/analyzer/automation/AutomationEngine.java), [`NativePathfinder`](../src/main/java/dev/analyzer/automation/NativePathfinder.java), [`NativeBuilder`](../src/main/java/dev/analyzer/automation/NativeBuilder.java) | Local plan review, execution stages, navigation, material recovery and observed completion |
| Loaded-data detectors | [`finder`](../src/main/java/dev/analyzer/finder) | Bounded block/entity/chunk observations, invalidation and scoped history |
| Privacy | [`privacy`](../src/main/java/dev/analyzer/privacy) | Local presentation masking and networking/resource probe protections |
| Skyboxes | [`skybox`](../src/main/java/dev/analyzer/skybox) | Bounded image import, async decode, resource ownership and native sky integration |
| Wings | [`cosmetic`](../src/main/java/dev/analyzer/cosmetic) | Attributed rig, geometry, animation and native model attachment |
| Minecraft hooks | [`mixin`](../src/main/java/dev/analyzer/mixin), [`analyzer.mixins.json`](../src/main/resources/analyzer.mixins.json) | Narrow version-specific hooks and accessors |

Textures, fonts, GLSL and starter profiles are under [`src/main/resources`](../src/main/resources). Tests are under [`src/test/java`](../src/test/java). [`tools/prism`](../tools/prism) contains the packaging and local updater tools. `design` contains research and review artifacts; review files are not automatically runtime assets.

## Follow a module

1. Find its stable ID in [MODULES.md](../MODULES.md) or [MODULES.json](../MODULES.json).
2. Search that ID in `core` to find its definition, defaults and settings. `BuiltinModules` assembles the registry from the definition families.
3. Search the same ID in its engine or renderer to follow the behavior. `AnalyzerCore` is the shared settings facade; UI controls do not own separate gameplay values.
4. Find the corresponding `*SelfTest` and feature document for rules, limitations and observed native evidence.

For example, Wings is defined in `core/CosmeticModules.java`, rendered through `render/CosmeticRenderer.java` and `cosmetic/WingsRenderer.java`, and checked by `cosmetic/WingPolicySelfTest.java`. [WINGS.md](../WINGS.md) describes the mount, animation and license.

## Checks and catalogue generation

`build` includes compilation, remapping and the policy/configuration checks wired into `check`. These are executable `*SelfTest` classes with focused Gradle tasks such as `coreCheck`, `plannerCheck`, `chamsCheck` and `wingPolicyCheck`. The ordinary `test` task alone is not the complete project validation command.

After changing module definitions, run:

```sh
./gradlew build moduleCatalog
git diff -- MODULES.md MODULES.json
```

Review and commit both generated files. [`ModuleCatalog`](../src/test/java/dev/analyzer/core/ModuleCatalog.java) exports the actual registry; do not hand-edit generated settings tables. The [GitHub workflow](../.github/workflows/build.yml) builds and fails if regenerating the catalogue changes committed output.

Native smoke tasks such as `runStudioSmoke`, `runSkyboxSmoke` and `runWingsSmoke` launch actual development clients and may create fixture worlds/screenshots. They require a graphical environment and use the run directories configured in `build.gradle`. They are separate from headless CI. Installed release builds do not activate the development harness. [VALIDATION.md](../VALIDATION.md) records what was actually observed for each delivery; a policy test is not evidence of every terrain, renderer or public server working.

## Local development updates

`./gradlew publishLocal` builds and verifies the remapped JAR, then writes a checksum-addressed artifact and `release.json` into `analyzer-updates` beside the checkout. Use `-PanalyzerUpdateDir=<directory>` to select another local feed. This task does not publish to GitHub or change a running game.

The optional Windows updater in `tools/prism/Update-Analyzer.ps1` connects a closed Prism instance to that local manifest. Public release imports leave it disconnected; local developers can configure it explicitly. See [PRISM.md](../PRISM.md) for feed connection and packager options. Never commit local manifests, instance account files, worlds or user settings.

## Code and asset changes

Keep stable IDs and saved choices compatible, and preserve comments that explain ownership, geometry, protocol or rendering invariants. Prefer a focused change over a broad formatting pass. Release versions are pinned to Minecraft 1.21.4; mixins and native API changes need targeted verification.

Retain the full notices and provenance for included assets/code ports. Research access alone does not grant redistribution rights. [THIRD-PARTY.md](../THIRD-PARTY.md) and [DEPENDENCY-SOURCES.md](../DEPENDENCY-SOURCES.md) distinguish current dependencies from historical reference material.
