# Gameplay settings overhaul

This working-tree update expands the settings and runtime behavior of Analyzer's existing **15 Movement, 8 Combat and 7 HVH modules**. The module IDs and old setting IDs are preserved. The client still targets Minecraft **1.21.4**, Fabric and Java 21.

The three categories now contain **442 typed settings**, up from 94; the full 123-module registry contains 1,086 settings. The added controls are connected to runtime behavior.

| Category | Modules | Previous settings | Current settings |
| --- | ---: | ---: | ---: |
| Movement | 15 | 10 | 55 |
| Combat | 8 | 41 | 183 |
| HVH | 7 | 43 | 204 |

## Reference and scope

All module groups and settings in the Movement, Combat and HVH sections of [Temple's live catalogue](https://temple.nz/modules) were inspected on **October 11, 2026**, including the JSON catalogue loaded by that page. That site describes a newer Minecraft release; its packet modes and behavior are not automatically compatible with Analyzer's 1.21.4 runtime.

This update extends existing Analyzer modules. Reference-only modules such as No Fall, Jesus, Spider, Boat Fly, Entity Speed, Noclip, No Slow, Blink, Elytra Autopilot, Fake Lag, Auto Block, Reach, Velocity, Cover Planner, Trap Early Warning, TP Aura and Pearl Trap remain outside this change. Auto Walk is an existing Analyzer module with additional direction/input controls.

The reference determines useful controls rather than implementation code. All controller, policy and UI code is original Analyzer code. Public primary references used to check concepts/API semantics include [Meteor's Kill Aura setting declarations](https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/combat/KillAura.java), [Yarn 1.21.4 ExplosionImpl](https://maven.fabricmc.net/docs/yarn-1.21.4+build.8/net/minecraft/world/explosion/ExplosionImpl.html), and [Yarn 1.21.4 DamageUtil](https://maven.fabricmc.net/docs/yarn-1.21.4+build.8/net/minecraft/entity/DamageUtil.html). GPL implementation code is not copied into Analyzer.

## Using the controls

Open a module's inspector in Workspace, Classic or Compact. Gameplay settings are grouped into targeting, timing, safety, aim/presentation and behavior sections. Fields for inactive movement/target modes are hidden while their saved values are retained.

Hover a setting to read its full label when a narrow panel truncates it.

Left-drag numeric sliders. **Right-click a numeric setting to type an exact value**, then press Enter or Apply. Values pass through the same finite-number, bounds and step normalization as sliders. Escape or Cancel returns without applying the edit. Choice controls cycle through their available modes; Classic and Compact also cycle backwards on right-click.

All gameplay modules remain off in a fresh configuration. Loading a visual profile still leaves gameplay automation off. Loading the explicit HVH profile enables its documented modules. Existing user profile files are not replaced by the installer.

## Movement behavior

- **Speed:** Standard directly controls horizontal motion; Conservative acts only on the ground and caps the requested rate; Sprint hop uses normal sprint/jump input. The preserved base speed and a new multiplier apply to direct profiles.
- **Fly:** independent horizontal and vertical rates, Vanilla/Glide/Conservative glide profiles and an optional periodic descent pulse. Normal-input flight uses an equipped elytra and actual hotbar/offhand rockets. A descent pulse is a motion option, not an anticheat guarantee.
- **Step:** Instant owns the step-height attribute and restores it on pause/disable; Jump uses normal input at a checked one-block ledge.
- **Elytra Boost:** impulse or actual rocket interactions, climb, speed cap, takeoff and rocket conditions.
- **Fast Fall / Reverse Step:** configured fall threshold and loaded-floor distance; the normal-input alternative only sneaks through scaffolding.
- **Glide / Fast Ladder / High Jump / Long Jump / Ground Strafe:** configured motion plus applicable normal-input alternatives. A normal High Jump pillars with actual blocks, and needs stable hotbar blocks. A normal Glide needs a usable elytra.
- **Parkour / Safe Walk:** checked ledges, sprint-jump controls, input-based alternatives and a sneak-only condition.
- **Keep Sprint / Auto Walk:** hunger/use/ground conditions and directional movement, sprint/jump and collision stopping controls.

Target Strafe can supply its current safe orbit direction to Speed/Ground Strafe. It clears that direction when it pauses; ordinary input regains control.

## Combat behavior

Target selection uses eyes-to-hitbox distance, a field of view, category filters and priority by distance, health, angle, armor or hurt time. Optional filters cover friends, invisible/unarmored/newly loaded entities and other living entities. Teammates, spectators, self and tamed companions stay excluded. A wider acquisition range does not increase the actual interaction reach.

Kill Aura supports Single target locking, delayed Switch and bounded Multi actions, aim acquisition beyond attack range, cooldown/CPS timing, randomized delay, charge requirements, weapon/use gates, configurable camera rotation, smoothing, aim point and hit-angle limits. Actual server reach and cooldown rules still apply.

Aim Assist adds strength, held-attack and weapon conditions. Trigger Bot adds crosshair reaction delay. Auto Clicker adds Left/Right/Both, steady/random/paired timing and bounded CPS. Right-click repetition excludes continuous-use items and uses normal item/block interactions.

Auto Weapon adds weapon-type filters, preference and switching delay. Shield Breaker adds reaction/release timing. Bow Aimbot adds selected bow/crossbow/trident inputs and bounded movement/drop prediction. Criticals adds real Jump/Mini jump/Manual fall modes, target/charge/interval conditions, bounded waiting, normal sprint release and an optional local particle cue.

The reference's silent rotation and packet critical modes are not exposed as functioning controls. Camera/None rotation and actual falling attacks are implemented. Weapon scoring remains a material/durability estimate; projectile lead/drop is an approximation.

## HVH behavior

Crystal Aura separates acquisition, placement and break ranges/delays; selects Smart/Own/All crystals; and applies enemy damage, self damage, anti-suicide, face-place and friend-protection gates. Anchor Aura uses matching damage gates, optional existing anchors and normal place/charge/detonate stages. Anchor-safe dimensions remain excluded.

Explosion estimates use loaded-world exposure, difficulty, armor/toughness and known vanilla protection/resistance for enemies. Self estimates deliberately omit protective enchantment/effect reductions. Unknown or unloaded data fails closed. Estimates cannot guarantee survival: moving entities, latency and server rules can change the outcome. Existing minimum-health and self-distance settings continue to apply alongside the new gates.

Surround adds materials, shape, occupied-block extension, floor/centering/self-trap and disable conditions. Auto Trap adds cage shapes and hole/completion conditions. Auto Web adds feet/head, every-enemy and motion conditions. Hole Filler adds enemy relevance, single/double holes, scan timing, self/friend preservation and occupancy checks. Placement budgets are per module and also share an aggregate maximum of eight interactions per tick; inventory clicks retain their separate one-click budget.

The six placement/explosion modules have pending/accepted world previews with RGB color controls. Pending marks expire after 400 ms; locally accepted interactions after 750 ms. Rendering is limited to nearby geometry, 32 pending positions per module and 64 accepted marks, uses normal depth and restores incoming render state. An accepted mark records a local interaction, not a server confirmation.

Target Strafe adds Circle/Vanilla hop/Blind hop/Hug, direction/flip/weave, normal pace, WASD overrides, ground/jump/sprint conditions and checked support/collision. The normal-input path turns the camera and holds actual movement keys.

Placement and attack options use normal interaction packets, real reach, selected visibility limits, optional real camera aiming and swing behavior. None requires the held item; Normal visibly switches and keeps the chosen slot; the reference label Silent means a temporary ordinary hotbar switch/back here. It is not hidden inventory switching or silent rotation.

Authentic support-free air placement, the reference's Target Strafe camera-sharing/look-time/glance/pursuit behavior, and packet ground/sprint spoofing are not implemented. These are explicit reference gaps rather than inert settings.

## Persistence, architecture and validation

The shared GameplayEngine owns input/slot cleanup, aggregate interaction budgets and the unchanged Player automation. CombatController, MovementController and HvhController own their domain logic, backed by typed CombatSettings, MovementSettings and HvhSettings definitions. Domain state resets on world changes, death, menus, unsafe inventory/vehicle states and shutdown.

Old configuration/profile values keep their IDs and units, including cooldown fractions, speed, step height, crystal health/distance and clockwise direction. Missing added fields receive defaults. Configuration migration checks load an old-format snapshot and save/reload the combined old/new values.

Build checks include the existing core, ESP, task-planner and scoped-settings assertions plus combat/movement/HVH policy and configuration-migration tests. `runGameplaySmoke` runs a focused hidden-window development check in the project-local `run/saves/Analyzer-Smoke` world. Its evidence and exact fresh results are recorded below after execution; it is separate from the historical production probe, which joins no worlds.

```powershell
.\gradlew.bat build
.\gradlew.bat moduleCatalog
.\gradlew.bat runGameplaySmoke
```

`MODULES.json` remains the complete authoritative export of setting IDs, labels, types, defaults, bounds and choices. `MODULES.md` remains its module-level summary. This file describes the upgraded behavior and limitations.

## Fresh results — October 11, 2026

`build runGameplaySmoke` completed successfully with Java 21 on Minecraft 1.21.4/Fabric 0.16.14, using the RTX 4060 Ti on this computer.

- **978 build assertions:** 245 core, 38 ESP layout, 46 planner, 10 scoped-settings lease, 102 combat policy, 30 movement policy, 18 HVH policy and 489 old/new configuration assertions.
- **66 focused runtime assertions:** 41 movement/UI and 25 combat/HVH. The fixture completed in 159 client ticks after its setup, with three settings screenshots. It also passed the existing introductory UI toggle/search/slider/keybind/profile checks.
- Actual Survival motion verified Sprint hop without direct velocity writes, Standard's base/multiplier behavior, Conservative's ground cap and airborne no-writes, Step menu restoration, descending Glide and Safe Walk's real mixin gate.
- Rendered numeric controls opened keyboard entry and committed exact values in Workspace, Compact and Classic. The long Kill Aura inspector scrolled from Targeting to Safety.
- A server-spawned hostile verified category filters, camera turn limits, acquisition outside attack reach, charge gating and server-confirmed ordinary melee damage.
- Loaded terrain, armor, resistance, an unloaded chunk and an existing anchor verified damage-estimate behavior without triggering an explosion. The actual pending-preview hook rendered with clean GL state.
- Regression checks verified Criticals releases its sprint ownership after losing melee reach, Target Strafe respects that ownership, and multiple placement candidates share one camera turn per module per tick.

The first runtime attempt exposed a fixture setup race: client checks started before the server had prepared the arena. The harness now waits for server preparation and actual Survival position/ground synchronization. Both subsequent runs completed; the final run includes the coordination regressions.

Screenshots: [Workspace](screenshots/analyzer-gameplay-settings-workspace.png), [Compact](screenshots/analyzer-gameplay-settings-compact.png), [Classic](screenshots/analyzer-gameplay-settings-classic.png). Raw final output is preserved at `build/reports/gameplay-settings-runtime.log`.

The pre-UI-overhaul gameplay artifact has SHA-256 `71586c31a6e842fb81bee801c0164cbc43037bbf5a379ab35e57bc0bd03e29cb`. These build and delivery results are historical; the later native UI build and current Prism/feed/package hashes are recorded at the top of [VALIDATION.md](VALIDATION.md). Future project updates also refresh the existing instance and import ZIP, as recorded in [AGENTS.md](AGENTS.md).

Multiplayer acceptance, every combination of the 442 settings, full projectile simulation and live explosion outcomes remain unverified. This task verified installation and package integrity; it did not launch the new instance through Prism.
