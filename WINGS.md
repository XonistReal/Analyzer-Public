# Approved articulated wings

**Cosmetics → Wings** is an opt-in local-player cosmetic. Angel, Dragon, Bat and Parrot use the four previously reviewed original textures on Paul Fulham's existing Wings avian model. They share the same 17-part hierarchical Minecraft pivot rig: root, eight structural joints and eight separately posed, one-pixel-thick texture extrusions. They are not four different meshes or weighted/skinned models. Original atlas pixels, model pivots, cuboids and UV orientation are retained. The attachment uses the approved Fit-2 upper-back position: root raised three Minecraft model pixels, with the source coracoid pivots still at Y 5.5.

## Controls

- **Wing style:** Angel / Dragon / Bat / Parrot.
- **Animation:** Auto selects folded Idle on the ground, Flap in the air and Glide while swimming/gliding. Flap, Idle, Glide and Still can also be selected directly. These are visual poses only.
- **Animation speed / strength:** pace the cycle and blend from the original spread rest pose. Zero speed freezes the cycle; zero strength and Still retain the original rest pose. Flap/Idle formulas are adapted from the downloaded source; deterministic low-amplitude flutter replaces its randomly seeded glide noise.
- **Wing scale:** 0.5–1.5, default 1.
- **Extra height / back offset:** measured in Minecraft model pixels. Defaults retain the approved raised mount; positive height moves upward. The source rig itself is not edited.
- **Hide with Elytra:** enabled by default. Disabling it permits ordinary visual overlap with equipped Elytra.
- **Extra chest armor clearance:** adds 0.8 model pixel behind the body when a chest item is equipped; enabled by default.
- **Fullbright texture (no bloom):** disabled by default. Uses the native full-bright lightmap value, with no extra postprocess, bloom or emissive scene lighting.

## Rendering and scope

Only the local player's third-person rendered model wears the wings. Invisible players, spectators and sleeping poses are excluded. Other players do not receive a cosmetic message or see the wings through this client-side feature. There are no changes to flight, collisions, movement, camera, player angles, equipment, inventory, identity or outgoing networking. First-person ordinary gameplay does not render the local body; Minecraft's own player-preview surfaces may render it.

The feature follows the already posed native body transform, so crouching, bending, swimming and gliding use the same body attachment. It never edits the player's model parts or entity. Every wing part resets before and after each draw. Cape and Pets remain independent. Wing textures are ordinary native entity cutout buffers with depth, diffuse lighting and alpha-tested feather edges; equipment and shader chams remain separate feature passes. There are 396 quads / 1,584 submitted vertices at fixed detail. The renderer owns CPU model geometry only; textures and buffers are managed by Minecraft, including resource reload and shutdown. There is no custom OpenGL state, retained world/entity reference or per-player animation cache.

The old hat/accessory review models are not included in runtime resources or player rendering. New ornate headwear remains subject to visual selection before integration.

## License and provenance

Source project: [pau101/Wings](https://github.com/pau101/Wings/tree/174929878d09490b012dbfa4e8d23d56f02b5add), commit `174929878d09490b012dbfa4e8d23d56f02b5add`, Copyright (c) 2018 Paul Fulham, MIT. The original PNG bytes are copied from `design/cosmetics-review/models/wings` into `assets/analyzer/textures/cosmetics/wings`. The pure `WingRig`, `WingMesh` and `WingAnimation` ports retain source attribution. Full MIT text accompanies copies as [WINGS-MIT-LICENSE.txt](WINGS-MIT-LICENSE.txt). No Lunar/Temple assets or flight system are used.

Deterministic checks and actual native-render acceptance are recorded separately in `VALIDATION.md`.
