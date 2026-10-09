# Analyzer validation

## Analyzer 0.2.0 visual upgrade

Verified October 8, 2026 with the same Windows / Java 21 / Fabric 1.21.4 / RTX 4060 environment described below. This is one tested hardware environment, not a performance benchmark.

The original installed 0.1.1 log showed successful shader initialization. Its saved configuration had Entity ESP disabled and players-only tracers enabled in a singleplayer world. Those settings can legitimately produce no targets. The old Aurora shader was screen grading and did not implement a spatial sky aurora. This release adds an actual sky pass, stronger visual effects and live explanations of renderer/filter state.

- `build` and `coreCheck` pass **44 core assertions**, including the new sky/interface defaults, preset choices and setting bounds. The release has ten modules and four mixins.
- The isolated `runSmoke` world compiles/links all three original GLSL programs on the actual GPU, exercises UI clicks/search/sliders/booleans/keybinds/profile save, and captures **22 framebuffer screenshots**. Installed builds never activate this development harness.
- The actual sky renders Aurora, Nebula and Ember behind opaque terrain and vanilla clouds. Night-only mode submits no sky frames at noon; Always mode does. The sky/post programs successfully render again after resource reload and a 1600×1000 to 1280×800 resize. All 22 screenshot checkpoints report `glGetError() == 0`.
- Entity and storage Screen ESP project at least six targets each under **Fast, Fancy and Fabulous**. Fixtures include hostile/passive mobs, a local remote-player object, an item and six container types. The nearby radar uses the same entity filters. Removing the player fixture produces a clear `0 other players` tracer status. New dispenser, dropper and brewing-stand recognition was source-reviewed; those extra types were not separately exercised in the arena.
- Glass/Mint with visible particles, Solid/Gold with particles disabled, and Glass/Violet were captured and visually reviewed in game. The interface remains readable, and existing controls continue to work. Tutorial notifications are suppressed only in the development harness for clean captures.
- `tools/verify_visuals.py` checks actual sky-colored pixels, distinct presets and stable opaque-wall pixels. `tools/verify_render.py` retains the earlier 3D outline depth/through-wall and resize check. Run either with an optional screenshot-directory argument; both require Pillow.
- The final pixel check measured **13,359 aurora-colored sky pixels versus 324 with the sky off**, a mean sky-image change of **34.47** on the 0–255 scale, and only **0.13** change on the fixed opaque-wall region. The 3D chest test measured **62 visible outline pixels versus 0 with Fabulous depth testing**. Exact results are in `PIXEL-VALIDATION.log`; the full runtime log is `VALIDATION.log`.
- New `showcase`, `arctic` and `cinder` profiles are seeded once under a new marker. Existing configuration and older edited profiles are preserved. Load `showcase` explicitly to select the new Screen ESP and Bloom combination.

Screen ESP with through-walls disabled uses a line-of-sight check to each target center. The 3D Boxes/Corners modes retain GPU depth testing. This release does not implement model-mask silhouette ESP, shadowed world-lighting shader packs, player aura/trail cosmetics or combat automation. Iris/Sodium and multiplayer behavior remain unverified. Research observations and vendor catalogue claims are distinguished in [RESEARCH-VISUALS.md](RESEARCH-VISUALS.md).

The 0.2.0 release was published to the local feed and installed into the existing Prism Analyzer instance through its updater. The installed JAR matches the feed SHA-256, declares version 0.2.0 and includes the new sky mixin. The prior 0.1.1 JAR was backed up; hashes of all **32 configuration, world and other-mod files** were unchanged after installation. Native Prism and source ZIP integrity are checked during packaging. The unchanged updater's 74-check integration suite is documented below.

## Historical 0.1.x checks

## Analyzer 0.1.1 Prism integration

Added October 8, 2026. The game code and graphics are unchanged from the runtime checks below; this release updates the mod version, distribution tooling, and notices.

- `publishLocal` rebuilt the remapped 0.1.1 JAR, passed all **37 core assertions**, published an immutable hash-named artifact, and atomically replaced the local feed manifest.
- The updater's Windows PowerShell 5.1 integration suite passed **74 checks** in isolated temporary fixtures. It covers initial install, version replacement, same-version rebuilds, duplicate Analyzer JARs, backups, world/config/other-mod preservation, checksums, metadata types and compatibility, unsafe paths and junctions, filename conflicts, disabled updates, locks, caught-exception rollback, and interrupted transaction recovery even without a feed. Renamed JARs with spaces/Unicode are supported. Busy transactions and unsafe recovery stop launch; ordinary feed errors preserve the installed mod and allow launch.
- Native instance ZIP layout, custom-command preservation, quoted instance variable expansion, and Java selection behavior were checked against the installed **Prism Launcher 11.1.1** source. The pack includes Minecraft/Fabric component metadata and both required mods, without game binaries or accounts.
- The directly installed development instance uses the project's verified Java 21 runtime; the portable ZIP inherits the recipient's Prism Java settings. Updates apply before the next launch, not to an already running game.

The updater currently reads a local development feed. An internet release service is not configured. See [PRISM.md](PRISM.md) for setup and publishing.

Verified October 8, 2026 on Windows, Java 21, Minecraft 1.21.4, Fabric Loader 0.16.14, Fabric API 0.119.4+1.21.4 and an NVIDIA GeForce RTX 4060. This records one local hardware/driver environment, not a compatibility or performance benchmark.

## Build and configuration

- `build` succeeded: Java compilation, resources, remapped mod JAR, source JAR and Gradle checks.
- The version-independent core test suite passed **37 assertions**: registry defaults, typed settings, numeric clamping/snapping, invalid values, keybind bounds, JSON round trips, profile save/load/delete, safe names, malformed/future/structurally rejected config recovery, state preservation and temporary-file cleanup.
- Verified release JAR metadata declares client-only Fabric 1.21.4 / Java 21, has remapped Minecraft symbols and mixin refmap, and retains the MIT, font and third-party notices.
- Official portable Java and Gradle downloads were checksum-verified; the Gradle wrapper carries its distribution SHA-256. The unmodified Fabric API JAR was checked against the upstream Maven checksum.

## Native UI and graphics

The `runSmoke` development harness launched Minecraft, loaded all three mixins and compiled/linked both original GLSL programs on the actual GPU. It used a project-local singleplayer test world, then stopped automatically. Nine screenshots were captured from Minecraft's framebuffer; they are runtime output rather than design mockups.

| Check | Observed result |
| --- | --- |
| Module toggle and boolean setting | UI clicks changed the expected typed state. |
| Keyboard bind capture | A captured G bind was stored on Entity ESP. |
| Search and inspector | Searching Atmosphere, selecting it and toggling through the inspector worked. |
| Preset selector | Clicking Aurora advanced to Ember. |
| Slider | Maximum input produced 1.0; the tested intermediate position produced 0.6. |
| Profile save | The UI-created profile appeared in the profile store. |
| Entity ESP | Renderer submitted six targets: two hostile mobs, two passive mobs, a local remote-player fixture and a dropped diamond. Bounds, colors, names, distances and health indicators aligned in the arena. |
| Storage ESP | Renderer submitted all six container types: chest, barrel, shulker box, ender chest, furnace and hopper. Labels and outlines aligned. |
| Tracers | Renderer submitted six targets, anchored to the view. |
| Shader presets | Aurora, Ember and Frost rendered distinct grading; shaders toggled and recompiled after resource reload. |
| Fullbright | Enabled and disabled successfully in the nighttime arena. |
| Fabulous occlusion | The opaque wall hid depth-tested entity/container outlines and labels. Through-wall overlays remained visible when enabled. |
| Resize | Actual framebuffer capture changed from 1600×1000 to 1280×800; GUI/HUD and scene-copy texture adapted. |
| OpenGL checks | `glGetError()` was zero at all nine screenshot checkpoints. |

Pixel verification measured **747 amber outline pixels** for the chest visible through the wall, and **0** in the same region with depth testing under Fabulous graphics. The optional `tools/verify_render.py` script reproduces that check after `runSmoke` (requires Python and Pillow).

The Fabulous test found that its compositor clears the main framebuffer depth. Analyzer therefore draws depth-tested overlays while world depth remains available, and draws through-wall overlays after composition. This was fixed and the full smoke run repeated successfully. HUD scaling was also refined after screenshot review.

## Limits of this validation

The automated arena exercised all four entity filters and six container types. The player fixture used Minecraft's `OtherClientPlayerEntity` in the local client world; it was not a second networked player. No public server was contacted. Multiplayer latency, other utility clients, Iris/Sodium, other GPUs, and Minecraft versions beyond 1.21.4 remain unverified. The nine checkpoint checks are useful graphics evidence, not an assertion that every possible OpenGL state or mod combination was covered.

The release contains eight implemented modules. Shadowed world-lighting shader packs, model-silhouette shader ESP, friend systems, combat/movement automation and anticheat bypass are outside this release.
