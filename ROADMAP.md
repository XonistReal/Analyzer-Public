# Analyzer roadmap and reference coverage

Reviewed 2026-10-09 for Analyzer 0.4.0, Minecraft Java 1.21.4.

The actual registry has **122 modules**. The [Temple.nz public catalogue](https://temple.nz/assets/data/modules.json?v=be4c7e7b) contains **163 reference modules**. This comparison identifies **102 areas with a functional counterpart or narrower implementation**: **33 scoped counterparts**, **69 partial implementations**, and **61 planned/unimplemented areas**. These figures are separate from feature parity and runtime test coverage.

HUD Studio and its widgets count as one Temple HUD area. The same evidence is never expanded into seventeen reference features. A partial row states the missing scope; an implementation sharing a name does not imply the reference's styles, bypasses or server behavior.

Evidence status: the matrix is based on the registered source definitions and code review. Selected runtime checks are recorded separately in [VALIDATION.md](VALIDATION.md); not every module or mode has been tested in game. Gameplay automation is opt-in. Server acceptance, normal reach/cooldowns, loaded-world data and renderer compatibility still constrain behavior.

## Next implementation priorities

1. **Combat/HvH correctness:** deterministic explosion exposure/damage prediction, armor/resistance effects, self-damage/friend guards, stable rotation and placement state, target scoring, cooldowns and multi-module coordination. Extend Crystal/Anchor/Trap/Web tests before adding teleport combat.
2. **Movement:** No Slow with precise input hooks and cleanup, then Elytra Autopilot with route/altitude/collision control. Add controlled movement tests for every mode; client motion alone cannot promise server acceptance.
3. **Rendering:** true model Chams; independent player Aurora curtains and wearable aura meshes; world water/foliage/wet-ground shader integration. Keep overlay effects and mesh/material replacements explicitly distinct.
4. **Camera/UI:** Freecam, Freelook, Zoom/No Bob; deepen glass scene sampling and HUD/widget polish, preserving camera/options on exit and resource reload.
5. **Coverage depth:** empty-slot Refill, enchant-aware weapon/armor scoring, crossbow/trident aiming, richer damage/totem events and remote block effects. Expand current partial rows rather than increasing the catalogue with unused switches.

## Every reference module

**Counterpart:** the core behavior has an implemented, scoped counterpart. **Partial:** a narrower implemented behavior exists. **Planned:** no direct implementation is present. All rows are code-reviewed references unless VALIDATION records a specific runtime check.

| Temple category | Temple module | Analyzer ID | Status | Implemented scope / next work |
| --- | --- | --- | --- | --- |
| Client | Glass | `menu` | Partial | Sampled blurred backdrop and translucent panels/rims; no refractive glass material shader. |
| Client | HUD | `hud` | Counterpart | Draggable/scalable editor and 17 client/HUD controls, counted here as one reference area. |
| Client | Menu | `menu` | Partial | Workspace, draggable Classic columns and Compact layouts; no replacement vanilla menu theme. |
| Client | Privacy | — | Planned | No direct implementation in this release; design and validation remain. |
| Client | All Legit | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Fullbright | `fullbright` | Counterpart | Local lightmap boost with restoration. |
| Render | No Block Rotation | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Spoof | — | Planned | No direct implementation in this release; design and validation remain. |
| Cosmetics | Waving Foliage | — | Planned | Vertex animation and material integration with renderer/shader compatibility tests. |
| Render | Motion Blur | — | Planned | No direct implementation in this release; design and validation remain. |
| Cosmetics | Wet Ground | — | Planned | World material/reflection pipeline rather than a dark screen tint. |
| Cosmetics | Realistic Water | — | Planned | Separate water render integration: waves/refraction/reflection/depth/foam; not a grading preset. |
| Render | Ambience | — | Planned | World fog/cloud/weather controls with restoration and a separate lighting pipeline. |
| Render | Chams | — | Planned | True entity model-mask rendering and outlines with correct world depth and resource reload. |
| Cosmetics | Crystal Chams | `crystal-chams` | Partial | Actual tinted vanilla model or original Gem/Prism meshes, size/spin/bounce/base/rings; no through-wall crystal material. Next: Orb model and refractive materials, with dedicated bloom or silhouette rendering. |
| Cosmetics | Hand Chams | — | Planned | First-person model/material pass with HUD and world separation. |
| Cosmetics | Skybox | `sky` | Partial | World-sky Aurora/Nebula/Ember and stars; no imported panoramas or all reference sky styles. |
| Render | Smooth Hotbar | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Chat Animations | — | Planned | No direct implementation in this release; design and validation remain. |
| Cosmetics | Crosshair | `crosshair` | Partial | Arm/gap/width controls; no custom image or all animated shapes. |
| Render | Clean View | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Death Recovery | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | ESP | `entity-esp` | Partial | 3D boxes/corners and projected screen frames; no model-mask silhouettes/chams. |
| Render | Tracers | `tracers` | Counterpart | Loaded-target filters, range, thickness and visibility. |
| Render | Nametags | `nametags` | Partial | Loaded entity names, distance and health; no equipment cards. |
| Render | Logout Spots | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Storage ESP | `storage-esp` | Partial | Loaded common containers including chests, barrels, shulkers and furnaces; no exhaustive storage/spawner support. |
| Render | Ore ESP | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | OreSim | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Chunk Finder | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Item ESP | `item-esp` | Partial | Loaded dropped-item rings, beams and labels; no item allow-list selector. |
| Render | Better Tooltips | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Block Highlight | `block-highlight` | Partial | Pulsing crosshair block outline; no detailed info card. |
| Render | Hole ESP | `hole-esp` | Counterpart | Bounded scan of loaded resistant single-block holes. |
| Render | Zoom | — | Planned | Smooth scoped FOV with input bindings and prior-FOV restoration. |
| Render | No Bob | — | Planned | Restore the prior bob setting when disabled; keep hurt-camera effects separate. |
| Render | Freecam | — | Planned | Independent camera entity/control and reliable body/camera restoration. |
| Render | Freelook | — | Planned | Camera/body rotation separation for every perspective. |
| Cosmetics | Hit Particles | `hit-particles` | Partial | Actual hurt Sparks/Shards/Gems/Arc with filters, size/spread/speed/gravity/colors; no bouncing material system. |
| Render | Target ESP | `target-esp` | Partial | Orbit/Box/Corners with size/linger/depth/colors; no full ghost-orb/halo style set. |
| Cosmetics | Jump Circles | `jump-circles` | Partial | Local Ripple/Rings/Crown/Tile on Jump/Land/Both; no remote-player selector or block mesh lift. |
| Cosmetics | Trails | `trails` | Partial | Local Ribbon/Beads/Sparks with height/width/size/colors; no player afterimages or full elemental styles. |
| Cosmetics | Death Effects | `death-effects` | Partial | Loaded entity death bursts; no coins/souls/ghost models. |
| Cosmetics | Air Effects | `air-effects` | Partial | Bounded world ambient field with seven shapes, density/radius/wind/network/color controls; no full reference shape catalogue. |
| Cosmetics | Block Effects | `block-effects` | Partial | Actual local placements/breaks, map colors, crumble/shatter/frame geometry; no remote-player event coverage. |
| Cosmetics | Place Animation | `place-animation` | Partial | Animated colored ghost overlay; the vanilla block mesh still appears normally. |
| Cosmetics | Place Preview | `place-preview` | Counterpart | Adjacent/replaceable position preview for a held block. |
| Cosmetics | Player Aura | `player-aura` | Partial | Orbit/Gyroscope/Rune geometry with first-person, radius/speed/colors; no wings, ears, horns or wearable mesh collection. Next: Wearable wings/halos/ornament meshes beyond current rotating rings. |
| Cosmetics | Cape | `cape` | Partial | Original segmented moving cloth with three styles/three hems, wearer/color/flow controls; no image/GIF importer. |
| Cosmetics | Damage Effects | `damage-effects`, `health-pulse` | Partial | Confirmed hurt bursts, entity filters and low-health warning; no damage model deformation. |
| Cosmetics | Footsteps | `footsteps` | Partial | Local Rings/Prints/Petals, spacing/size/max-prints/colors; no remote players or puddle materials. |
| Cosmetics | Projectile Trails | `projectile-trails` | Partial | Loaded Line/Comet/Spiral paths with projectile-type filters, range/width/colors; no impact burst style set. |
| Cosmetics | Pickup Streaks | `pickup-streaks` | Partial | Streaks on item pickup packets; no complete experience-orb effect replacement. |
| Cosmetics | Teleport Effects | `teleport-effects` | Partial | Large local position-change Rings/Vortex/Portal with linked ends; no complete remote-player pearl tracking. |
| Render | Damage Numbers | `damage-numbers` | Counterpart | Numbers from actual health decreases of loaded living entities. |
| Cosmetics | Hit Markers | `hit-markers` | Partial | Confirmed recent attacked-target hurt produces Cross/Corners/Dots with size/thickness/spin/colors; no world marker/kill style set. |
| Cosmetics | Hit Sounds | `hit-sounds` | Partial | Seven built-in vanilla sound cues on confirmed attacked-target hurt, pitch/volume/combo/kill controls; no custom file importer. |
| Render | Music | — | Planned | No direct implementation in this release; design and validation remain. |
| Cosmetics | Item Physics | `item-physics` | Partial | Actual dropped-item tumble/ground-rest/water-rock render transforms; no entity physics changes. |
| Cosmetics | Glint Color | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Totem Pops | `totem-pops` | Partial | Real totem-status rings/sparks/label; no persistent per-player pop counts or model replacement. |
| Cosmetics | Contrails | `contrails` | Partial | Paired fading paths from actual motion, glide restriction and speed filters; no aerodynamic fluid simulation. |
| Cosmetics | Aurora | — | Planned | Actual local player veil/crown/ribbon curtains; current Sky Aurora only maps to Skybox. |
| Cosmetics | Pets | `pets` | Partial | Original Rune Orb, Ghost and Dragon meshes in third person; no speech bubbles or Jellyfish. |
| Cosmetics | Walk Style | `walk-style` | Partial | Five local player-model limb poses and strength/movement controls; no server animation. |
| Cosmetics | Emotes | `emotes` | Partial | Five local third-person model poses, loop/length/movement/strength controls; no server animation. |
| Cosmetics | Break Animation | `break-animation` | Partial | Actual mining progress, fading ghost and debris; no replacement block mesh fracture. |
| Cosmetics | Explosion Effects | `explosion-effects` | Partial | Actual explosion packet shockwaves/sparks, added to vanilla effects. |
| Player | Inventory Tweaks | — | Planned | No direct implementation in this release; design and validation remain. |
| Cosmetics | Storm Effects | `storm-effects` | Partial | Actual loaded lightning triggers branching bolt/column/spear, sparks/ripple/flash; no weather replacement. |
| Cosmetics | Vitals | `vitals` | Partial | Real health/fire/underwater-air warning and optional heartbeat; no wearable vitals mesh. |
| Cosmetics | Hand Tweaks | `hand-tweaks` | Partial | First-person transforms, item stretch/size/rotations, seven swing styles and equip/eat/hide controls; no full reference pose set. |
| Render | Spawner Notifier | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Stash Finder | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Trajectories | `trajectories` | Partial | Held bow/trident/snowball/egg/pearl prediction against loaded blocks; no complete other-player future paths. |
| Cosmetics | Spinbot | `spinbot` | Partial | Local player render-state yaw/head pitch only; camera/entity/server rotations remain unchanged. |
| Movement | No Fall | — | Planned | No direct implementation in this release; design and validation remain. |
| Movement | Fly | `fly` | Counterpart | Client velocity flight with collisions; no server flight grant or bypass. |
| Movement | Speed | `speed` | Partial | Directional client speed; no anticheat modes or guaranteed Target Strafe integration. |
| Movement | Step | `step` | Counterpart | Temporary client step-height attribute with restoration. |
| Movement | Jesus | — | Planned | No direct implementation in this release; design and validation remain. |
| Movement | Spider | — | Planned | No direct implementation in this release; design and validation remain. |
| Movement | Boat Fly | — | Planned | No direct implementation in this release; design and validation remain. |
| Movement | Elytra Boost | `elytra-boost` | Partial | Bounded look-direction impulse during existing gliding; no autopilot. |
| Movement | Entity Speed | — | Planned | No direct implementation in this release; design and validation remain. |
| Movement | Fast Fall | `fast-fall` | Counterpart | Accelerates an existing fall; normal fall damage remains. |
| Movement | Fast Ladder | `fast-ladder` | Counterpart | Upward velocity while already climbing. |
| Movement | Glide | `glide` | Counterpart | Limits ordinary downward velocity outside fluids. |
| Movement | Ground Strafe | `ground-strafe` | Partial | Aligns existing grounded momentum; no guaranteed constant server speed. |
| Movement | High Jump | `high-jump` | Counterpart | Higher velocity at the beginning of an ordinary jump. |
| Movement | Long Jump | `long-jump` | Counterpart | Directional impulse at the start of a jump. |
| Movement | Noclip | — | Planned | No direct implementation in this release; design and validation remain. |
| Movement | No Slow | — | Planned | Item-use slowdown hooks with exact input restoration; fluid/web modes require separate verification. |
| Movement | Parkour | `parkour` | Counterpart | Grounded automatic jumps before unsupported edges. |
| Movement | Reverse Step | `reverse-step` | Partial | Downward impulse while a floor is nearby; no complete mode set. |
| Movement | Safe Walk | `safe-walk` | Counterpart | Vanilla ledge clipping without holding sneak. |
| Movement | Blink | — | Planned | No direct implementation in this release; design and validation remain. |
| Combat | Fake Lag | — | Planned | No direct implementation in this release; design and validation remain. |
| Combat | Kill Aura | `kill-aura` | Partial | Visible targets, real view turn, normal reach/cooldown; no silent rotations. |
| HVH | Crystal Aura | `crystal-aura` | Partial | Normal reachable placement/breaking with health/distance guards; no explosion damage prediction. |
| HVH | Anchor Aura | `anchor-aura` | Partial | Normal place/charge/detonate in explosive dimensions; no damage prediction or crosshair-only mode. |
| HVH | Surround | `surround` | Counterpart | Four sides at the feet with optional lower supports, one interaction at a time. |
| HVH | Auto Trap | `auto-trap` | Partial | Bounded sides/roof placement around a target; no advanced adaptive trap planner. |
| HVH | Auto Web | `auto-web` | Counterpart | Normal supported, reachable web placement at target feet. |
| HVH | Hole Filler | `hole-filler` | Partial | Nearby player-area one-block holes; no enemy escape prediction. |
| HVH | Cover Planner | — | Planned | Exposure/damage simulation, self-damage state, mining cost and escape-route scoring. |
| HVH | Trap Early Warning | — | Planned | Track nearby block changes, identify remaining exits and validate any escape action. |
| HVH | TP Aura | — | Planned | No direct implementation in this release; design and validation remain. |
| HVH | Pearl Trap | — | Planned | No direct implementation in this release; design and validation remain. |
| Combat | Aim Assist | `aim-assist` | Counterpart | Smooth target turn while holding attack, with filters/FOV/range. |
| Combat | Bow Aimbot | `bow-aimbot` | Partial | Drawn-bow velocity lead/drop estimate; no crossbow/trident aimbot or full drag simulation. |
| Combat | Auto Block | — | Planned | No direct implementation in this release; design and validation remain. |
| Combat | Auto Clicker | `auto-clicker` | Partial | Held-attack entity clicks capped by CPS and cooldown; no randomized timings or block-click mode. |
| Combat | Auto Weapon | `auto-weapon` | Partial | Hotbar material/damage estimate and owned-slot restoration; no full enchantment score. |
| Combat | Criticals | `criticals` | Partial | Real jump/falling-phase normal attacks; no packet critical spoof. |
| Combat | Shield Breaker | `shield-breaker` | Counterpart | Hotbar axe for a blocking reachable target, with slot ownership/restoration. |
| Movement | Keep Sprint | `keep-sprint` | Partial | Normal sprint input while moving forward; no guaranteed full knockback on every hit. |
| Combat | Reach | — | Planned | No direct implementation in this release; design and validation remain. |
| HVH | Target Strafe | `target-strafe` | Partial | Grounded client orbit; no enemy-view-angle planner, hopping or TP Aura integration. |
| Combat | Trigger Bot | `trigger-bot` | Counterpart | Normal cooldown attacks on a selected crosshair entity. |
| Combat | Velocity | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Auto Eat | `auto-eat` | Counterpart | Hunger-triggered hotbar food with temporary use/slot ownership. |
| Player | Anti AFK | `anti-afk` | Partial | Periodic ordinary turn/jump; no randomized macro-check avoidance claim. |
| Player | Auto Armor | `auto-armor` | Partial | Material-tier armor upgrades/empty slots through guarded quick moves; equipped elytra is preserved. |
| Player | Auto Fish | `auto-fish` | Counterpart | Normal cast, synchronized bite detection, reel and optional recast. |
| Player | Auto Gapple | `auto-gapple` | Counterpart | Health-triggered hotbar golden apple use. |
| Player | Auto Leave | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Auto Reconnect | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Auto Mend | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Auto Respawn | `auto-respawn` | Counterpart | Normal delayed respawn request from the death screen. |
| Player | Auto Tool | `auto-tool` | Counterpart | Fastest usable hotbar tool for a currently mined block. |
| Player | Auto Totem | `auto-totem` | Counterpart | Normal inventory/offhand swap when no totem is equipped. |
| Player | Auto Walk | `auto-walk` | Counterpart | Owned forward input released on pause/disable. |
| Player | Chest Stealer | `chest-stealer` | Counterpart | Bounded quick moves from an open container. |
| Player | Inventory Manager | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Middle Click Friend | `middle-click-friend` | Partial | Persisted UUID friends and combat exclusion; no friend tint in all ESP/chams or chat command set. |
| Player | Notifier | `notifier` | Partial | Loaded player arrivals/departures; no full pop/pearl notification routing. |
| Player | Refill | `refill` | Partial | Restocks low nonempty hotbar stacks with larger matching stacks; no remembered empty-slot refill. |
| Player | Coord Snapper | `coord-snapper` | Counterpart | One-shot coordinates/dimension clipboard copy and optional Nether conversion. |
| Player | Key Pearl | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Pearl Follow | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Player Detection | `player-detection` | Counterpart | Local loaded nonfriend player radius alerts. |
| Player | Durability Alert | `durability-alert` | Counterpart | Periodic low-durability equipment warning. |
| Player | Donut Region | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Stash Disconnect | — | Planned | No direct implementation in this release; design and validation remain. |
| Player | Staff Display | — | Planned | No direct implementation in this release; design and validation remain. |
| World | Nuker | `nuker` | Counterpart | Reachable block mining with normal progress. |
| World | Air Place | `air-place` | Partial | Normal in-range air-placement attempt; server acceptance is required. |
| World | Auto Farm | `auto-farm` | Partial | Nearby mature crop harvest/replant with hotbar seeds; no full crop/route manager. |
| World | Auto Tunnel | `auto-tunnel` | Partial | Two-high forward mining; no base finder, hazard avoidance or steering. |
| Movement | Elytra Autopilot | — | Planned | Coordinate route, pitch/altitude controller, collision checks, landing and explicit start/stop. |
| Player | Auto Relog | — | Planned | No direct implementation in this release; design and validation remain. |
| Client | Streamer Mode | — | Planned | No direct implementation in this release; design and validation remain. |
| Client | Streamproof | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Waypoints | — | Planned | No direct implementation in this release; design and validation remain. |
| Render | Map | — | Planned | No direct implementation in this release; design and validation remain. |
| World | Bed Breaker | `bed-breaker` | Partial | Reachable bed mining; no dragon-egg targeting. |
| World | Flatten | `flatten` | Partial | Mine reachable blocks above standing level, preserving the floor. |
| World | Scaffold | `scaffold` | Partial | Ordinary hotbar placement beneath the player; no god/speed-bridge mode set. |
| World | Speed Mine | `speed-mine` | Partial | Removes client inter-break delay; server progress rules remain authoritative. |
| World | Tower | `tower` | Counterpart | Normal jump and underfoot block placement while holding jump. |
| World | Vein Miner | `vein-miner` | Partial | Bounded reachable connected same-type blocks; no unrestricted whole-vein break. |
| World | No Block Interact | `no-block-interact` | Partial | Block placement on containers without opening them; no full held-item use redirect. |

The exact Temple.nz site and visual observations are documented in [RESEARCH-VISUALS.md](RESEARCH-VISUALS.md). Source data: the official [module JSON](https://temple.nz/assets/data/modules.json?v=be4c7e7b) and Analyzer's generated [MODULES.json](MODULES.json). No Temple client code/assets are copied.

Regenerate after changes: `./gradlew moduleCatalog`, then `python tools/generate_roadmap.py --reference <saved-temple-modules.json>`. The generator validates mapped Analyzer IDs against the exported registry and performs no network requests.
