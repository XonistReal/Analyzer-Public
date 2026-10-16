# Finders and specialized ESP — 0.13.0

Analyzer adds **27 original finder/ESP modules**, under **Render**. Every module starts disabled. They use received blocks, block states, light, equipment and entities; they never request unloaded chunks or read hidden container contents. Enable only the detectors you want. Existing Entity ESP, Storage ESP, Tracers and shader Chams remain independent.

**Advanced Stash Finder** counts selected storage blocks in each loaded chunk. Choose container families, a minimum count, minimum distance from the world origin, and whether one observed spawner qualifies. A double chest consists of two actual blocks. Counts describe storage blocks, not their contents or ownership. A separate bounded container survey gives nearby stash findings priority over full terrain scans.

Enable **Keep local stash history** to remember qualifying observations. **Findings** in Studio or Click GUI opens a shader-based list with dimensions, chunk coordinates, block counts and first/last observation times. History is scoped to the actual world/server using a hash; records store no server address or account credentials. Historical observations are labeled separately from current markers. Clear history is an explicit local action. Region Map displays these records on a configurable coordinate grid with the player's current position and direction.

| Module | Analyzer behavior |
| --- | --- |
| Advanced Stash Finder | Selected storage/spawner counts, thresholds and local observation history |
| Amethyst Finder | Actual budding amethyst, or all amethyst-family blocks, with a height range |
| Bedrock Void ESP | Known air at the world's bottom next to observed bedrock; unknown neighbors do not qualify |
| Beehive ESP | Actual hive/nest honey levels and independent type filters |
| Block Notifier | Selected block groups such as valuable blocks, workstations, storage and portals |
| Chunk Finder | Explainable storage, workbench, redstone, crafted-block, unusual-state and farm evidence |
| Collectible ESP | Received item/glow frames, armor stands and placed banners; optional occupied-frame filter |
| Covered Hole ESP | Bounded covered shafts with observed support; optional building-material cover heuristic |
| Deepslate ESP | Actual raw/building variants within a selected height range |
| Dripstone ESP | Observed contiguous upward/downward pointed-dripstone columns with length filters |
| Drowned Trident ESP | Drowned actually holding a trident in either hand; no drop guarantee |
| Hole / Tunnel / Stairs ESP | Enclosed shafts, supported roofed passages and rising passage geometry |
| Invisible ESP | Received entities with the actual invisible flag; player/hostile/non-hostile groups |
| Kelp ESP | Tall kelp-column counts and the percentage of tops at a selected height |
| Light ESP | Actual emitting sources or received illuminated cells, source families and optional thermal colors |
| Llama ESP | Actual llamas, optionally including trader llamas |
| Pillager ESP | Actual received pillagers |
| Piston ESP | Regular/sticky pistons and optional heads, with an extended-base filter |
| Region Map | Original coordinate sectors, player direction and dimension-filtered local stash history |
| Rotated Deepslate ESP | Actual horizontal axis states on raw deepslate; no invented orientation on axis-less blocks |
| Skeleton ESP | Captured posed biped joints, including arms, legs and optional head direction |
| Spawner Notifier | Received normal spawners and optional trial spawners |
| Sweet Berry ESP | Actual berry-bush growth ages |
| Villager ESP | Actual villagers and optional zombie villagers, with an adult filter |
| Vine ESP | Bounded vine-column lengths and optional observed ground contact |
| Wandering Trader ESP | Actual received wandering traders |
| 1 x 1 Holes | Sealed single-cell air pockets, with optional cover filtering |

Block and entity overlays use thin native shader-expanded debug lines with independent marker colors, Custom RGB, opacity, through-wall control and optional tracers. Common controls that do not apply to a HUD or entity-only module are hidden. Discovery messages stay local, deduplicated and rate limited. No webhook, automatic disconnect, teleport command or remote notification is sent by these modules.

Terrain scans are incremental, nearest-first and bounded to at most 16,384 lookup units per tick. Neighbor probes consume the same budget; interrupted predicates resume instead of bypassing limits. Palette pruning skips irrelevant sections. Expedited refreshes yield to ordinary work so distant chunks cannot be permanently starved. Received block changes, replacement chunks, unloading, settings changes and world changes invalidate affected observations. Active block replacement may still delay a coherent chunk aggregate; scan status is shown in enabled Studio cards rather than implying an instant complete survey. Caches, marker counts and notices have independent caps.

Pattern results are candidates, not proof of a base. Natural caves, geodes, kelp, vines, patrols and terrain can match. Chunk Finder shows reasons and an evidence score rather than a fabricated probability; trial suppression requires an actual trial spawner and enough crafted chamber material. Amethyst Finder deliberately reports exact blocks instead of claiming Glazed's dark-light heuristic proves a farm. Region Map is a coordinate grid, not an unverified table of current DonutSMP region IDs. Block Notifier currently offers block groups, not arbitrary registry-text input. This is a family-level set of original equivalents, not a literal port of every upstream setting or subheuristic.

The behavioral audit used the official [Glazed 1.21.4 source](https://github.com/realnnpg/glazed/tree/1d33d14fdafd26100aa7bf616758786d79ac7b96/src/main/java/com/nnpg/glazed/modules/esp) and checked the later default branch at `6b6cab2a26f7a11985e6129ab51b4e909a2e9460`. The pinned [license](https://github.com/realnnpg/glazed/blob/1d33d14fdafd26100aa7bf616758786d79ac7b96/LICENSE) is restrictive. No Glazed/Meteor code or runtime dependency is incorporated. Runtime code, geometry and UI are original Analyzer implementations against Minecraft/Fabric APIs. Current tests and native acceptance scope are recorded in [VALIDATION.md](VALIDATION.md).
