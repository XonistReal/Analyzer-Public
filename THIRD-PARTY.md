# Third-party materials and dependencies

Analyzer's original Java code, GLSL shaders, UI resources, and Python/PowerShell distribution tools are licensed under the MIT license in `LICENSE`. The items below retain their own licenses. This document records direct assets/tooling and runtime dependencies; it does not relicense Minecraft or its transitive libraries.

## User-supplied branding

On October 12, 2026 the user supplied `minecraft-text (1).png` (341×392 monogram) and `minecraft-text.png` (1139×392 wordmark) for this client. They are preserved byte-for-byte as `src/main/resources/assets/analyzer/textures/brand/monogram.png` and `wordmark.png`. Both already contain RGBA transparency and a translucent glow. These supplied images replace the generated Home branding; their provenance is separate from Analyzer's original shader artwork and bundled font licenses.

## Bundled fonts

| Local asset | Original upstream file | License |
| --- | --- | --- |
| `src/main/resources/assets/analyzer/font/ui.ttf` | [Rajdhani-Medium.ttf](https://github.com/google/fonts/blob/main/ofl/rajdhani/Rajdhani-Medium.ttf) | SIL Open Font License 1.1 |
| `src/main/resources/assets/analyzer/font/ui_bold.ttf` | [Rajdhani-SemiBold.ttf](https://github.com/google/fonts/blob/main/ofl/rajdhani/Rajdhani-SemiBold.ttf) | SIL Open Font License 1.1 |
| `src/main/resources/assets/analyzer/font/studio.ttf` | [Lato-Regular.ttf](https://github.com/google/fonts/blob/5d3b76120a319730fda218cc7410174a462b32cb/ofl/lato/Lato-Regular.ttf) | SIL Open Font License 1.1 |
| `src/main/resources/assets/analyzer/font/studio_bold.ttf` | [Lato-Bold.ttf](https://github.com/google/fonts/blob/5d3b76120a319730fda218cc7410174a462b32cb/ofl/lato/Lato-Bold.ttf) | SIL Open Font License 1.1 |

Rajdhani copyright: **Copyright (c) 2014, Indian Type Foundry (info@indiantypefoundry.com).** The font binaries were obtained from the [Google Fonts Rajdhani directory](https://github.com/google/fonts/tree/main/ofl/rajdhani); local filenames are shorter resource names. The accompanying upstream [OFL text](https://github.com/google/fonts/blob/main/ofl/rajdhani/OFL.txt) is preserved in `FONT-LICENSE.txt`. Include that copyright and license when redistributing the fonts or Analyzer packages containing them. Analyzer's MIT license does not replace the fonts' license.

The Studio menu uses unmodified Lato Regular and Bold fonts obtained from the official [Google Fonts Lato directory](https://github.com/google/fonts/tree/5d3b76120a319730fda218cc7410174a462b32cb/ofl/lato), pinned to commit `5d3b76120a319730fda218cc7410174a462b32cb`. Their internal font names and contents are unchanged; `studio.ttf` and `studio_bold.ttf` are only Minecraft resource filenames. Copyright belongs to tyPoland Lukasz Dziedzic; the upstream copyright, reserved font name and full license are preserved in [LATO-OFL.txt](LATO-OFL.txt) and must accompany redistributed packages. SHA-256: Regular `d636e4683231f931eda222d588e944d082bfd3bdba02f928bee461c0f185b251`; Bold `8a0aace75d33794eece4b28187bfc1df0bbd2888b5d8a56e01788c8d65d16be1`.

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

The ready-to-install ZIP includes Fabric API as a separate, unmodified mod JAR in `mods/`; the native Prism ZIP uses `minecraft/mods/`. Its upstream `LICENSE-fabric-api` notice is retained inside that JAR and reproduced in `licenses/FABRIC-API-LICENSE.txt` in both ZIP formats. Current native packages also include the matching pinned Fabric API source tree, recorded in [DEPENDENCY-SOURCES.md](DEPENDENCY-SOURCES.md). The source archive includes the upstream Gradle license as `GRADLE-LICENSE.txt`.

## Research references are not bundled dependencies

The 0.9.0 Server Privacy implementation also consulted [OpSec's primary source](https://github.com/aurickk/OpSec) and [Wurst's sign translation vulnerability explanation](https://wurst.wiki/sign_translation_vulnerability) to establish threat and fallback behavior. OpSec is GPL-3.0; no code, mod binary or assets from it are copied or bundled. Analyzer's packet/text/resource-pack defenses are original implementations against the existing Minecraft/Fabric APIs. The Discord-only project called Autism Defender has no verified source or license here and is not bundled. See [SERVER-PRIVACY.md](SERVER-PRIVACY.md).

Future, RusherHack, Temple Client Legacy, Meteor, LiquidBounce, Lambda, Wurst and ThunderHack were consulted through their public documentation/source to compare feature scope and architecture. **No code or assets from these clients were copied into Analyzer.** Their licenses remain with their respective projects; the GPL clients are not relicensed under Analyzer's MIT license. See `RESEARCH.md` for the primary links and version observations.

Iris, Sodium, Satin and third-party world shader packs are not included. Iris may be installed independently to provide world shader packs, but that combination and its version compatibility require separate testing. Analyzer's bundled effects are its own screen post-processing shaders and procedural menu backdrop.

## Historical Baritone dependency and automation research

Releases through 0.9.0 used the separately replaceable Baritone Fabric API 1.13.1 mod. Those historical binaries, corresponding sources and notices remain in [third-party/baritone](third-party/baritone/README.md) for old release provenance and source research. **0.10.0 removes Baritone from the build, mod metadata and Prism distribution.** The native path search, movement, miner, builder and task controllers are original Analyzer code against Minecraft/Fabric APIs. No Baritone implementation source was copied into these controllers and no Baritone classes or native helper are bundled in the current mod or Prism ZIP. Historical validation harnesses are retained as text under `production-validation/legacy-baritone`, outside compiled runtime source.
