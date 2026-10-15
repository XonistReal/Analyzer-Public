# X-ray — native textured world rendering

Open **Render → X-ray** in Studio or Click GUI. X-ray is **disabled by default**. Choose a **World style**, select the ore groups to show, then enable the module. These settings are saved with the normal configuration and profiles.

## Three world styles

| World style | Selected ores | Other terrain |
| --- | --- | --- |
| **Fade terrain** | Original fully opaque ore textures, full lighting and an optional subtle colored edge glow | Original textured block models at approximately **15% opacity** |
| **Terrain outlines** | Original fully opaque ore textures | Filled terrain models are hidden; nearby surface blocks receive small, faint wire outlines |
| **Ores only** | Original fully opaque ore textures | Filled terrain, terrain outlines and ore edge glow are hidden |

Fade terrain uses an alpha multiplier of **38/255**, the nearest lower 8-bit value to 15%. Native texture cutouts, block tint, geometry and texture coordinates remain part of the real block model. The ore edge glow supplements the textured ore; it does not replace the ore with a colored box.

The ten independently saved ore filters are **Diamond, Emerald, Gold, Iron, Copper, Lapis, Redstone, Coal, Nether quartz and Ancient debris**. All are selected by default. A filter includes its normal and deepslate variants where those exist; Gold also includes Nether gold ore. Deselected ores follow the chosen ordinary-terrain style. Manufactured mineral blocks are not classified as ores.

**Detail range** defaults to **24 blocks**, adjustable from **8 to 48**. It controls nearby terrain outlines, ore glow and entity highlights. The native textured chunk treatment applies to the client's loaded, rendered world; this slider does not shorten Minecraft's render distance or turn distant loaded ores into markers. **Subtle ore glow in Fade terrain** affects only that style. Terrain outlines and Ores only do not add the ore glow pass.

## Rendering and restoration

Analyzer wraps the original baked block models using Fabric's public model-loading and quad-emitter APIs. The wrapped model delegates the original geometry and UVs, then adjusts the actual emitted quad's material, alpha and ore lighting. Selected ore faces are retained even when ordinary stone surrounds them. Non-ore Fade geometry uses Minecraft's native translucent world shader. The chunk visibility graph is opened for rendering while X-ray is enabled; collision, server blocks, inventory and ordinary interactions retain their real game state.

The renderer publishes an immutable policy for chunk workers. Enabling, disabling, changing style or changing ore filters rebuilds native chunk geometry. Disabling restores the original model emission and visibility behavior. Resource reloads rebuild against the current native texture atlas and clear cached materials; world changes clear the local detail survey. The wire/glow pass restores its incoming shader, blend, depth, cull, fog and line state. Its survey is incremental and bounded to loaded cells, with at most 4,096 cached ore details and 4,096 terrain details.

**Keep workstations visible** and **Keep storage containers visible** default on in every style. The native fully opaque textured models remain visible for crafting/enchanting/smithing tables, anvils, other vanilla workstations, chests/ender chests, barrels, every shulker color, hoppers, dispensers/droppers, furnaces and other vanilla item-holding blocks. Special container block-entity models retain their ordinary rendering path with full lighting; they are not faded to 15%. A block in both groups remains visible while either group is selected. Other special models, such as signs, remain omitted while X-ray is active.

Entities retain their ordinary native models. **Entity highlights** defaults on and adds thin through-terrain bounds for received entities within Detail range, including mobs, players, vehicles and item drops. This pass is bounded to 256 nearby entities. Turning highlights off removes those bounds; it does not hide ordinary entities. Ore textures and models come from Minecraft and the active resource pack; there is no external X-ray mod or downloaded texture replacement.

X-ray displays block data already present in loaded client chunks. It does not discover unloaded terrain or recover ore data a server has not supplied. The observed renderer configuration is Minecraft **1.21.4**, Fabric API **0.119.4+1.21.4** and its native Indigo renderer. Compatibility with Sodium, other replacement renderers, shader packs and other GPU configurations has not been established by this acceptance run.

## Observed native acceptance

The earlier October 14, 2026 renderer-only focused `xray` development run passed **41 X-ray assertions** and **554 surrounding pipeline checks**. It created nineteen actual ore block states behind an ordinary stone wall, then exercised every style through the real Fabric chunk renderer. Checks verified emitted ore alpha 255, terrain alpha 38, actual model emission, small terrain edges, ore-only geometry, live filtering of both diamond variants, unchanged world block states, disabling and an actual resource reload. GL checks passed before the captures and after reload. No Baritone mod was loaded.

These six base-renderer captures predate the utility/entity additions, whose gameplay checks the user elected to perform manually. Six captures were produced and the three styles plus restored terrain were visually reviewed:

| View | Capture |
| --- | --- |
| Original opaque stone wall | [Before enabling](screenshots/analyzer-native-0.10.2-base-xray-normal-before.png) |
| 15% terrain and textured ores | [Fade terrain](screenshots/analyzer-native-0.10.2-base-xray-fade-terrain.png) |
| Textured ores and faint terrain wires | [Terrain outlines](screenshots/analyzer-native-0.10.2-base-xray-terrain-outlines.png) |
| Selected ore models only | [Ores only](screenshots/analyzer-native-0.10.2-base-xray-ores-only.png) |
| Original world after disabling | [Restored](screenshots/analyzer-native-0.10.2-base-xray-normal-restored.png) |
| Actual refreshed texture atlas | [Resource reload](screenshots/analyzer-native-0.10.2-base-xray-resource-reloaded.png) |

Reproduce the isolated development acceptance with Java 21:

```powershell
.\gradlew.bat runNativeAutomationSmoke -PanalyzerNativeScenario=xray
```

The fixture uses its own development save and no player credentials. These observations describe the tested source; packaging and installation status is recorded separately in [VALIDATION.md](VALIDATION.md).
