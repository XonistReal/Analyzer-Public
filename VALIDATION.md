# Analyzer 0.1.0 validation

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
