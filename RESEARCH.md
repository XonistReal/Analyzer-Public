# Analyzer research and implementation scope

Research date: October 8, 2026. Sources below are upstream repositories and official client documentation. Version statements describe the inspected branch or dated release, not every release a project has published. Repository activity and published feature lists are useful maintenance signals; they do not constitute a security audit or an independent performance benchmark.

## Client comparison

| Reference | Version and source status | Useful design lessons |
| --- | --- | --- |
| [Future](https://www.futureclient.net/) | Official v3.0 changelog, March 15, 2026: Minecraft 1.21.11. Commercial proprietary software; its [terms](https://futureclient.net/legal) retain its intellectual property. | Configurable visual overlays and a GUI usable alongside game screens. Feature inspiration only. |
| [Temple Client Legacy](https://github.com/TempleDevelopment/Temple-Client-Legacy) | Forge 1.12.2, GPL-3.0; archived August 4, 2026. | Separate normal/CSGO GUI and HUD editing, optional Baritone integration. Its old Forge/rendering stack makes it a historical reference rather than a modern foundation. |
| [Meteor](https://github.com/MeteorDevelopment/meteor-client) | GPL-3.0. Current [version catalog](https://raw.githubusercontent.com/MeteorDevelopment/meteor-client/master/gradle/libs.versions.toml) targets Minecraft 26.2, JDK 25 and Fabric. | Typed settings, module events, separate themes/widgets, and extensive visual configuration. Its [ESP source](https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/render/ESP.java) includes box, wireframe, 2D, shader and glow modes, with distance/health/type colors. |
| [RusherHack](https://rusherhack.org/) | Commercial proprietary software. [v2.0.8 changelog](https://www.rusherhack.com/changelog.html), June 2, 2026: 1.21.11 supported; 1.20.1–1.21.4 builds cease receiving new features. | Its [ESP documentation](https://rusherhack.org/features/modules/ESP.html) provides a strong settings benchmark: entity/storage filters, range, box/shader/CSGO modes, fill, outline width and fading. Its rendering rewrite favors vanilla APIs, a useful long-term maintenance direction. |
| [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) | GPL-3.0. Current [properties](https://raw.githubusercontent.com/CCBlueX/LiquidBounce/nextgen/gradle.properties) declare mod 0.41.1 and a Minecraft range above 26.2 and below 26.4. | Its [Svelte theme system](https://github.com/CCBlueX/LiquidBounce/tree/nextgen/src-theme) separates UI presentation from game integration. Analyzer uses a native screen to keep deployment simpler. |
| [Lambda](https://github.com/lambda-client/lambda) | GPL-3.0; current [branch properties](https://raw.githubusercontent.com/lambda-client/lambda/1.21.11/gradle.properties) target 1.21.11 / Java 21. Explicitly alpha. | Search, context menus, keybind editing and linkable configurations; centralized managers prevent concurrent modules from conflicting. Published extreme ESP performance claims remain developer claims. |
| [Wurst 7](https://github.com/Wurst-Imperium/Wurst7) | GPL-3.0. Current [properties](https://raw.githubusercontent.com/Wurst-Imperium/Wurst7/master/gradle.properties) target 26.2, v7.55.1; README requires JDK 25. | Plain configuration and documented compatibility checks with rendering-related mods. |
| [ThunderHack Recode](https://github.com/Pan4ur/ThunderHack-Recode) | GPL-3.0, Fabric 1.21 / Java 21; archived November 6, 2024. Development is explicitly stopped. | Historical UI/effects reference, including Satin usage. Age and discontinued maintenance increase porting cost. |

**Temple.nz remains unverified.** The domain could not be retrieved, and no primary source established that it identifies this Minecraft project. The [official Temple Development organization](https://github.com/TempleDevelopment) points to `templecheats.xyz` and Temple Client Legacy. Those are the verified references; the domain names are not assumed to represent the same project.

## Analyzer's version choice

Analyzer 0.1.0 deliberately targets one concrete game version: **Minecraft Java Edition 1.21.4**, **Java 21**, **Fabric Loader 0.16.14**, **Fabric API 0.119.4+1.21.4**, **Yarn 1.21.4+build.8**, **Loom 1.9.2** and **Gradle 8.12**. The loader metadata permits Loader 0.16.10 or newer; 0.16.14 is the build pin.

This is a focused initial target with established mappings, rather than a claim to support every version used by the references. The current Meteor/Wurst branches now target newer game and Java versions, while Temple uses an older Forge stack. Their source cannot be dropped into this build unchanged. Porting Analyzer to 1.21.11 or 26.x requires revisiting rendering APIs, mixins, dependencies and runtime behavior. Protocol translation to other server versions is outside this release.

## Original implementation

Analyzer's Java code, UI drawing and GLSL effects are original MIT-licensed work. No GPL client source, proprietary client code, logos or shader-pack assets were copied. Rajdhani fonts are the separate OFL-licensed exception documented in `THIRD-PARTY.md`.

The core owns typed module definitions, validated values, enabled states and keyboard binds. Both the native screen and game adapter consume that same state. JSON configuration includes a schema version, bounded reads, safe profile names and atomic replacement when supported. The game-specific adapter connects Fabric callbacks and a small set of mixins to world/HUD drawing. GPU effects manage their own programs and scene-copy texture, restore OpenGL state, handle resize/reload disposal and disable themselves after a shader failure.

## Implemented scope versus reference features

| Area | Analyzer 0.1.0 implementation | Wider reference features outside this release |
| --- | --- | --- |
| Interface | Native module/category/search interface, settings, keyboard binds, local configuration/profiles, Rajdhani UI fonts and animated menu effects. | Embedded browser theme marketplaces and other clients' GUI/HUD systems. |
| Entity visuals | Entity ESP and tracers with player/hostile/passive/item filters, interpolated positions, range, line width and through-wall control; ESP uses translucent boxes or corner outlines with a soft outer stroke, name/distance labels and health indicators. | Rendered-model silhouette masks, shader/glow ESP, skeletal/wireframe model ESP and friend color systems. |
| Storage visuals | Nearby loaded-container ESP with range, labels and through-wall control. | Tunnel scanning, stash automation and generalized block search. |
| HUD/world utilities | Coordinates with optional dimension, custom crosshair and Fullbright. | Combat automation, movement exploits, Baritone pathfinding and packet manipulation. |
| Built-in shaders | Original Aurora/Ember/Frost screen post-processing: bright-area bloom sampling, color grading and vignette; a procedural aurora menu background. | Shadow maps, global illumination, reflective water, world material replacement and full Iris/OptiFine shader packs. |

ESP visualizes information already available in the client's loaded world. It does not discover entities or containers withheld by the server or outside loaded chunks. Storage indexing is bounded to the configured range and 4,096 containers, refreshes every 10 client ticks while enabled, and skips unloaded chunks. Entity ESP and tracer lists are each capped at 4,096 targets. These bounds limit work; they are not a measured FPS guarantee. Shader preset names describe Analyzer's own screen effects; they are not third-party world-lighting packs.

[Iris](https://github.com/IrisShaders/Iris) is a separate optional path for full world shader packs. It is not bundled or required by Analyzer, and compatibility with particular Iris/Sodium versions or shader packs needs a separate runtime check. [Satin](https://github.com/Ladysnake/Satin) was researched as a shader-management reference but is not a dependency.

## Compatibility and issue scope

The supported target is the pinned Fabric 1.21.4 environment. Builds on other Minecraft versions, simultaneous utility clients, replacement renderers and shader packs are unverified unless explicitly recorded in the validation notes. Research pages and source inspection do not establish that Analyzer's visual output was tested on every GPU or against these clients.

For a reproducible issue, record Analyzer/Minecraft/Loader/API versions, other installed mods, GPU/driver, preset and module settings, and the relevant exception from `latest.log`. Reproduce first in a separate 1.21.4 instance with only Analyzer and Fabric API; then reintroduce additional rendering mods to identify a conflict. Fullbright and ESP visibility may differ with external renderers. This release makes no anticheat-bypass or server-compatibility guarantee.
