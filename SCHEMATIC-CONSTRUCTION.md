# Schematic ghosts, construction and fluids — 0.11.0

Select a real file in **Automation → Schematics → Browse**, then Review. **View ghost in world** closes the menu without starting a task. Clear ghost removes it. **Automation settings → Show selected schematic ghost / Schematic ghost opacity / Schematic preview style** control textured Ghost or Outline mode. Source palettes/hashes stay unchanged, and an import/site/build eligibility error does not discard a successfully loaded preview.

The ghost uses native block model quads and actual outline shapes for special models/fluids. Missing geometry is pink, conflicts red and matching construction green. It is read-only, limited to the nearest 4,096 selected cells within 96 blocks, and cleared when worlds change. Native translucent/line layers test world depth without writing it. It does not create client blocks, spoof world state or send placement packets.

## Construction properties

Captured farms contain live states which cannot be placed as independent blocks: piston heads, power levels, compost/growth/light and currently moving parts. Review explicitly lists those fields. The original snapshot remains the preview; construction excludes generated piston-head/moving-piston cells and leaves live simulation to the actual server.

Facing, axis, rotation, slab/stair half, structural connections, paired doors/beds and waterlogging remain real completion requirements. Hypothetical placements must predict a compatible state and destination; immediately before use the actual camera hit, face and placement cell are checked again. Ordinary block use can configure repeaters, comparators, notes and clickable open states. Power-controlled iron/open states defer until remaining geometry/fluid work is attempted, then need the actual circuit to settle; they are not forced.

A multi-material palette reuses its working hotbar slot through ordinary inventory swaps instead of requiring an empty hotbar slot for every block type. Existing solid blocks and saved block entities are preserved; source hashes, loaded build bounds and actual remaining materials are checked again before execution.

## One reusable bucket

Source water/lava and waterlogged cells require **one reusable bucket capacity**, credited whether empty or already holding water/lava. The material planner can craft the bucket from three real iron ingots. Five source cells do not mean five physical buckets.

Dry containment is built before filling. The builder uses ordinary filled-bucket actions, waits for observed block states, and refills the empty bucket from an actual reachable source outside the blueprint, within a bounded 24-block search. Refill approaches remain committed while valid and brake before aiming. Water needs a real replenishable supply; lava needs enough actual source blocks to consume. Nothing creates an infinite lava supply. Carrying the opposite fluid defers the next fill while a matching selected source can use that bucket.

Flowing cells must form from actual sources through Minecraft simulation; they are never placed as invented source blocks. Fresh bounded containment checks use vanilla flow/collision predicates, reject unknown boundaries and spills into unselected dry cells, and protect current/projected player space. Unexpected fluids still block ordinary dry placement. Correctly placed blueprint fluids remain valid on checkpoint resume.

Primary API references: [BucketItem](https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.8/net/minecraft/item/BucketItem.html), [ItemPlacementContext](https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.8/net/minecraft/item/ItemPlacementContext.html).

## Actual files and remaining limits

Both supplied files resolve against the Minecraft 1.21.4 registry:

| File | Bounds | Snapshot / construction blocks | Fluids |
| --- | --- | --- | --- |
| Bonemeal Farm | 13 × 7 × 12 | 353 / 346; seven generated piston-head cells | One lava source, seven waterlogged trapdoors, six flowing cells |
| Kelp Farm | 4 × 7 × 5 | 64 / 64 | Three water sources, two flowing cells |

The check used bounded parsing plus a headless registry bootstrap with a test-only access shim. It did not launch Minecraft, edit the cached runtime JAR or build either farm in a world. Gameplay placement remains a manual check.

Source block-entity payloads are explicitly warned about: **container contents, crafter configuration, sign text and scheduled ticks are not restored**. These construction plans do not claim to recreate the instantaneous state of a running farm or finish all machine configuration. Real supplies, reachable supports/scaffolding, safe loaded terrain and actual fluid sources are required. Missing acquisition jobs such as kelp, slime balls, string or blaze rods need real owned inputs; review identifies them rather than pretending they can be collected automatically.

Current validation and artifact hashes are in [VALIDATION.md](VALIDATION.md).
