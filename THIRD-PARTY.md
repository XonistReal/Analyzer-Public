# Third-party materials and dependencies

Analyzer's original Java code, GLSL shaders, UI resources, and Python/PowerShell distribution tools are licensed under the MIT license in `LICENSE`. The items below retain their own licenses. This document records direct assets/tooling and runtime dependencies; it does not relicense Minecraft or its transitive libraries.

## Bundled fonts

| Local asset | Original upstream file | License |
| --- | --- | --- |
| `src/main/resources/assets/analyzer/font/ui.ttf` | [Rajdhani-Medium.ttf](https://github.com/google/fonts/blob/main/ofl/rajdhani/Rajdhani-Medium.ttf) | SIL Open Font License 1.1 |
| `src/main/resources/assets/analyzer/font/ui_bold.ttf` | [Rajdhani-SemiBold.ttf](https://github.com/google/fonts/blob/main/ofl/rajdhani/Rajdhani-SemiBold.ttf) | SIL Open Font License 1.1 |

Rajdhani copyright: **Copyright (c) 2014, Indian Type Foundry (info@indiantypefoundry.com).** The font binaries were obtained from the [Google Fonts Rajdhani directory](https://github.com/google/fonts/tree/main/ofl/rajdhani); local filenames are shorter resource names. The accompanying upstream [OFL text](https://github.com/google/fonts/blob/main/ofl/rajdhani/OFL.txt) is preserved in `FONT-LICENSE.txt`. Include that copyright and license when redistributing the fonts or Analyzer packages containing them. Analyzer's MIT license does not replace the fonts' license.

## Runtime and build dependencies

| Component | Analyzer build version | Role and upstream license |
| --- | --- | --- |
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) | 0.16.14; metadata minimum 0.16.10 | Installed mod loader; [Apache-2.0](https://github.com/FabricMC/fabric-loader/blob/master/LICENSE). |
| [Fabric API](https://github.com/FabricMC/fabric-api) | 0.119.4+1.21.4 | Separate required mod providing game callbacks; Apache-2.0. |
| [Yarn](https://github.com/FabricMC/yarn) | 1.21.4+build.8 | Build-time names/mappings; CC0-1.0. |
| [Fabric Loom](https://github.com/FabricMC/fabric-loom) | 1.9.2 | Gradle build plugin; MIT. |
| [Gradle](https://github.com/gradle/gradle) | 8.12 | Build system and included wrapper tooling; Apache-2.0. |
| Minecraft Java Edition | 1.21.4 | Game installed separately under Mojang/Microsoft terms; no game JAR is included in Analyzer's release mod. |

The release mod requires the game's existing runtime libraries, including Gson for JSON and LWJGL for OpenGL calls. Analyzer does not shade copies of those libraries or bundle Fabric Loader/API into its mod JAR. A source/build archive includes Gradle wrapper tooling; Gradle retrieves the build distribution and development dependencies separately.

The ready-to-install ZIP includes Fabric API as a separate, unmodified mod JAR in `mods/`; the native Prism ZIP uses `minecraft/mods/`. Its upstream `LICENSE-fabric-api` notice is retained inside that JAR and reproduced in `licenses/FABRIC-API-LICENSE.txt` in both ZIP formats. The source archive includes the upstream Gradle license as `GRADLE-LICENSE.txt`.

## Research references are not bundled dependencies

Future, RusherHack, Temple Client Legacy, Meteor, LiquidBounce, Lambda, Wurst and ThunderHack were consulted through their public documentation/source to compare feature scope and architecture. **No code or assets from these clients were copied into Analyzer.** Their licenses remain with their respective projects; the GPL clients are not relicensed under Analyzer's MIT license. See `RESEARCH.md` for the primary links and version observations.

Iris, Sodium, Satin and third-party world shader packs are not included. Iris may be installed independently to provide world shader packs, but that combination and its version compatibility require separate testing. Analyzer's bundled effects are its own screen post-processing shaders and procedural menu backdrop.

## Baritone optional runtime

Official Fabric API 1.13.1 for Minecraft 1.21.4 is shipped as a separate, replaceable mod under LGPL-3.0-or-later. Original Analyzer integration uses the public API. The verified artifact, upstream corresponding sources, license/notices and provenance are retained in [third-party/baritone](third-party/baritone/README.md); Prism imports include those notices and sources. No Baritone source is copied into Analyzer.
