# Analyzer modules and settings

158 registered modules and 1839 exposed settings. Generated from the actual in-game registry; defaults describe a fresh configuration. HUD Studio appearance/Home aliases share their canonical Interface values.

[Using modules and profiles](docs/MODULE-GUIDE.md) · [Install](docs/INSTALLATION.md) · [README](README.md)

Choose a category, then click a module for its complete settings. Descriptions state implemented scope; reference names do not imply feature parity. Gameplay automation is opt-in. Some controls appear in game only for their relevant mode.

[Render](#render) · [World](#world) · [HUD](#hud) · [Combat](#combat) · [Movement](#movement) · [HVH](#hvh) · [Player](#player) · [Cosmetics](#cosmetics) · [Client](#client)

## Render

| Module | Behavior | Default |
| --- | --- | --- |
| [Entity ESP](#module-entity-esp) | Clean debug boxes for players, hostile mobs and non-hostile mobs. | Off |
| [Tracers](#module-tracers) | Single clean lines to selected entities, using the same colors as Entity ESP. | Off |
| [Atmosphere](#module-shader) | Cinematic bloom and color grading. Skybox controls the sky. | Off |
| [Entity Chams](#module-chams) | Shader materials on actual living models, with separate visible and occluded colors. | Off |
| [Amethyst Finder](#module-amethyst-finder) | Marks received budding amethyst or geode blocks; chunk evidence is local to loaded data. | Off |
| [Bedrock Void ESP](#module-bedrock-void-esp) | Finds known air openings in the world's lowest layer beside received bedrock. | Off |
| [Beehive ESP](#module-beehive-esp) | Shows received beehives and bee nests at the selected honey level. | Off |
| [Block Notifier](#module-block-notifier) | Highlights selected valuable blocks, workstations or storage in received chunks. | Off |
| [Covered Hole ESP](#module-covered-hole-esp) | Finds a solid cover above a bounded, enclosed vertical shaft; natural formations can match. | Off |
| [Deepslate ESP](#module-deepslate-esp) | Highlights deepslate by type and height; high blocks are a clue rather than placement proof. | Off |
| [Dripstone ESP](#module-dripstone-esp) | Marks supported pointed-dripstone columns up to 32 blocks long. | Off |
| [Hole / Tunnel / Stairs ESP](#module-hole-tunnel-stairs-esp) | Recognizes bounded shafts, enclosed corridors and rising passage patterns in loaded terrain. | Off |
| [Light ESP](#module-light-esp) | Highlights received emitted block light; sunlight is excluded and source mode avoids propagated-light clutter. | Off |
| [1 x 1 Holes](#module-one-by-one-holes) | Finds one-cell pockets with a solid floor and four walls; matching terrain may be natural. | Off |
| [Piston ESP](#module-piston-esp) | Highlights received pistons and piston heads, optionally only extended mechanisms. | Off |
| [Rotated Deepslate ESP](#module-rotated-deepslate-esp) | Shows raw deepslate with an actual horizontal axis; axisless decorative blocks are excluded. | Off |
| [Spawner Notifier](#module-spawner-notifier) | Marks received spawner blocks without claiming hidden contents, activation or server data access. | Off |
| [Sweet Berry ESP](#module-sweet-berry-esp) | Shows berry bushes from their actual received growth age. | Off |
| [Vine ESP](#module-vine-esp) | Shows bounded vine columns, optionally requiring an observed solid block below their base. | Off |
| [Collectible ESP](#module-collectible-esp) | Mark loaded item frames, armor stands and placed banners; contents stay server-controlled. | Off |
| [Drowned Trident ESP](#module-drowned-trident-esp) | Mark received drowned actually holding a trident in either hand; drops are not guaranteed. | Off |
| [Invisible ESP](#module-invisible-esp) | Mark received invisible players and mobs without altering their visibility or inventory. | Off |
| [Llama ESP](#module-llama-esp) | Mark loaded llamas, optionally including trader llamas. | Off |
| [Pillager ESP](#module-pillager-esp) | Mark loaded pillagers with independent debug outlines. | Off |
| [Skeleton ESP](#module-skeleton-esp) | Draw animated bones from the real posed biped model for selected players and mobs. | Off |
| [Villager ESP](#module-villager-esp) | Mark loaded villagers, optionally including zombie villagers. | Off |
| [Wandering Trader ESP](#module-wandering-trader-esp) | Mark received wandering traders; no unreceived entities or trade data are inferred. | Off |
| [Advanced Stash Finder](#module-advanced-stash-finder) | Records received storage clusters by chunk, with actual counts and local history. | Off |
| [Chunk Finder](#module-chunk-finder) | Shows explainable building and farm evidence in received chunks; candidates can be natural. | Off |
| [Kelp ESP](#module-kelp-esp) | Marks tall kelp distributions near a selected top height; this is a pattern, not proof of a farm. | Off |
| [Region Map](#module-region-map) | Original coordinate grid of your position and locally recorded stashes; no assumed server region IDs. | Off |
| [Block Highlight](#module-block-highlight) | A pulsing outline of the block under your crosshair. | Off |
| [Target ESP](#module-target-esp) | A clean debug box on your crosshair target or recently attacked entity, with optional orbit rings. | Off |
| [Hole ESP](#module-hole-esp) | Outline loaded single-block holes with resistant floors and walls. | Off |
| [Trajectories](#module-trajectories) | Predict a held bow, trident, snowball, egg or pearl path against loaded blocks. | Off |
| [Item ESP](#module-item-esp) | Clean debug boxes around loaded dropped items, with optional labels and ring markers. | Off |
| [Nametags](#module-nametags) | Independent readable names, health and distances for loaded players and mobs. | Off |
| [X-ray](#module-xray) | Real ore textures in loaded terrain: 15% terrain, fine terrain outlines, or ores only. Disabling restores ordinary chunks. | Off |

## World

| Module | Behavior | Default |
| --- | --- | --- |
| [Storage ESP](#module-storage-esp) | Debug boxes around loaded containers, with colors for each storage group. | Off |
| [Fullbright](#module-fullbright) | Lift world lighting for clearer exploration. | Off |
| [Skybox](#module-sky) | Procedural skies, local panoramic images and six-face skyboxes. | On |
| [Scaffold](#module-scaffold) | Place hotbar blocks underneath your feet using ordinary block interactions. | Off |
| [Tower](#module-tower) | While holding jump, rise onto automatically placed hotbar blocks. | Off |
| [Air Place](#module-air-place) | While holding use, attempt to place a block at your crosshair in empty air. | Off |
| [Nuker](#module-nuker) | Mine nearby reachable blocks one at a time with normal mining progress. | Off |
| [Auto Farm](#module-auto-farm) | Harvest mature crops and replant empty farmland using hotbar seeds. | Off |
| [Auto Tunnel](#module-auto-tunnel) | Mine a two-block-tall path in the direction you face. | Off |
| [Bed Breaker](#module-bed-breaker) | Find and mine reachable beds using normal mining progress. | Off |
| [Flatten](#module-flatten) | Mine reachable blocks above your standing level; preserve the floor. | Off |
| [Speed Mine](#module-speed-mine) | Remove the client delay between ordinary block breaks; server mining rules still apply. | Off |
| [Vein Miner](#module-vein-miner) | After a manual break, mine connected blocks of the same type within reach. | Off |
| [No Block Interact](#module-no-block-interact) | Use block placement against containers without opening them. | Off |

## HUD

| Module | Behavior | Default |
| --- | --- | --- |
| [Coordinates](#module-coordinates) | Show position and dimension in your HUD. | On |
| [Crosshair](#module-crosshair) | A crisp customizable crosshair. | Off |
| [HUD Studio](#module-hud) | Choose your menu, appearance and HUD widget layout. | On |
| [Watermark](#module-watermark) | Your selected logo and version badge. | On |
| [Active Modules](#module-active-modules) | A bounded list of enabled gameplay modules. | On |
| [FPS](#module-fps) | Current client frames per second. | On |
| [Ping](#module-ping) | Actual player latency reported by the server. | Off |
| [Speed HUD](#module-speed-hud) | Horizontal movement speed in blocks per second. | Off |
| [Armor](#module-armor) | Equipped armor icons with remaining durability. | Off |
| [Potion Effects](#module-potions) | Active effects, levels and remaining durations. | Off |
| [Target HUD](#module-target-hud) | Health and armor of your acquired combat or crosshair target. | Off |
| [Keybinds](#module-keybinds) | Assigned module binds with their enabled state. | Off |
| [Inventory Preview](#module-inventory) | Your actual inventory in a compact three-row panel. | Off |
| [Compass](#module-compass) | Yaw-based cardinal direction and heading. | Off |
| [Biome](#module-biome) | Registry name of the biome at your current position. | Off |
| [World Clock](#module-world-clock) | World time of day and current day number. | Off |
| [Item Counter](#module-item-counter) | Count a selected resource in your inventory and offhand. | Off |
| [Keystrokes](#module-keystrokes) | Live movement, jump and mouse-button states. | Off |
| [Target Distance](#module-reach-display) | Measured camera distance to the current raycast hit. | Off |

## Combat

| Module | Behavior | Default |
| --- | --- | --- |
| [Kill Aura](#module-kill-aura) | Choose filtered targets and attack with normal reach, bounded charged actions and camera or selectable server-only rotations. | Off |
| [Aim Assist](#module-aim-assist) | Pull your actual camera toward a filtered target with configurable aim point, strength and smoothing. | Off |
| [Trigger Bot](#module-trigger-bot) | Attack a filtered crosshair target after its reaction delay, normal reach and selected charge timing. | Off |
| [Auto Clicker](#module-auto-clicker) | Repeat held entity attacks or ordinary item/block use with bounded steady, random or paired timing. | Off |
| [Auto Weapon](#module-auto-weapon) | Temporarily choose allowed hotbar melee weapons by material estimate or durability after a reaction delay. | Off |
| [Shield Breaker](#module-shield-breaker) | Hold a usable hotbar axe for a blocking crosshair target, with reaction and release delays. | Off |
| [Bow Aimbot](#module-bow-aimbot) | Aim the actual camera for selected drawn bows, charged crossbows or tridents using bounded lead/drop estimates. | Off |
| [Criticals](#module-criticals) | Use real jumps or your own falling motion to time ordinary charged attacks; no packet critical spoofing. | Off |
| [Auto Block](#module-auto-block) | Raise an equipped offhand shield toward a visible threat between attacks; release it before ordinary melee attacks. | Off |
| [Double Hand](#module-double-hand) | Temporarily hold a hotbar totem after a real server-reported pop or at low health near a visible threat; yields to Auto Totem once the offhand is protected. | Off |
| [Hover Totem](#module-hover-totem) | In your own inventory, hold the selected key over a totem to move it into the offhand through a normal guarded slot swap. | Off |

## Movement

| Module | Behavior | Default |
| --- | --- | --- |
| [Keep Sprint](#module-keep-sprint) | Keep sprinting while vanilla movement and hunger conditions permit. | Off |
| [Auto Walk](#module-auto-walk) | Hold a chosen movement key while the world is focused; input is released on pause or disable. | Off |
| [Speed](#module-speed) | Choose direct motion, a grounded conservative profile, or normal sprint jumps. | Off |
| [Fly](#module-fly) | Client flight or descending glide; Legit uses an equipped elytra and hotbar rockets. Servers may correct direct motion. | Off |
| [Step](#module-step) | Raise the client step height, or use a normal jump at a checked one-block ledge. | Off |
| [Parkour](#module-parkour) | Jump near a loaded unsupported edge while moving on the ground. | Off |
| [Safe Walk](#module-safe-walk) | Clip movement at ledges, or hold normal sneak near an edge. | Off |
| [Fast Ladder](#module-fast-ladder) | Increase climbing velocity; Legit holds normal forward and jump on climbable blocks. | Off |
| [Reverse Step](#module-reverse-step) | Accelerate an existing descent only toward a loaded floor within the chosen drop. Legit sneaks through scaffolding. | Off |
| [Fast Fall](#module-fast-fall) | Accelerate an existing fall after a distance threshold. Legit only drops through scaffolding. | Off |
| [Glide](#module-glide) | Limit falling velocity outside fluids; Legit opens an equipped usable elytra. | Off |
| [High Jump](#module-high-jump) | Increase an ordinary jump's upward velocity; Legit pillars with normal hotbar block use while jump is held. | Off |
| [Long Jump](#module-long-jump) | Boost the start of a directional jump; Legit sprint-jumps near an edge without modifying velocity. | Off |
| [Elytra Boost](#module-elytra-boost) | Add a bounded gliding impulse and optional climb, or use real hotbar rockets when flight slows. | Off |
| [Ground Strafe](#module-ground-strafe) | Align existing ground momentum, set a steady ground speed, or use ordinary sprint hops. | Off |

## HVH

| Module | Behavior | Default |
| --- | --- | --- |
| [Crystal Aura](#module-crystal-aura) | Score reachable crystals with loaded-world explosion exposure and conservative self/friend damage guards. | Off |
| [Anchor Aura](#module-anchor-aura) | Place, charge and detonate reachable anchors outside anchor-safe dimensions, with exposure damage guards. | Off |
| [Surround](#module-surround) | Build a supported configurable shell around your occupied blocks using normal placement. | Off |
| [Auto Trap](#module-auto-trap) | Build a supported roof, head shell or full cage around a selected target. | Off |
| [Auto Web](#module-auto-web) | Place supported feet/head webs on selected enemies within reach and a per-tick budget. | Off |
| [Hole Filler](#module-hole-filler) | Scan loaded nearby supported single/double holes and fill relevant unoccupied holes. | Off |
| [Target Strafe](#module-target-strafe) | Orbit a selected target with checked support, collision, ordinary hops and optional camera/input steering. | Off |

## Player

| Module | Behavior | Default |
| --- | --- | --- |
| [Auto Eat](#module-auto-eat) | Begin eating below the hunger threshold and continue until full; temporarily owns and restores use input/slot. | Off |
| [Auto Gapple](#module-auto-gapple) | Use a hotbar golden apple below a health threshold. | Off |
| [Auto Totem](#module-auto-totem) | Swap an inventory totem into the offhand through the normal player inventory handler. | Off |
| [Auto Armor](#module-auto-armor) | Equip stronger material-tier armor through guarded normal inventory quick moves. | Off |
| [Auto Tool](#module-auto-tool) | Choose the fastest hotbar tool for the block you are actively mining. | Off |
| [Refill](#module-refill) | Swap larger matching inventory stacks into low hotbar stacks. | Off |
| [Anti AFK](#module-anti-afk) | Periodically turn and jump using ordinary input, while no menu is open. | Off |
| [Auto Respawn](#module-auto-respawn) | Request an ordinary respawn after the death screen delay. | Off |
| [Durability Alert](#module-durability-alert) | Show a periodic warning for low-durability equipped items. | Off |
| [Auto Fish](#module-auto-fish) | Reel on the server-synchronized caught-fish flag, then optionally cast again. | Off |
| [Chest Stealer](#module-chest-stealer) | Quick-move items from an open container at a bounded rate. | Off |
| [Middle Click Friend](#module-middle-click-friend) | Middle-click a player to add or remove a locally saved friend. | Off |
| [Player Detection](#module-player-detection) | Notify locally when a non-friend enters your selected radius. | Off |
| [Notifier](#module-notifier) | Local messages for loaded players arriving or departing. | Off |
| [Coord Snapper](#module-coord-snapper) | Copy your coordinates to the clipboard, then turn off automatically. | Off |

## Cosmetics

| Module | Behavior | Default |
| --- | --- | --- |
| [Particles](#module-particles) | Animated ambient particles in the Analyzer menu. | On |
| [Place Animation](#module-place-animation) | Ease a colored block ghost into each successful placement. | On |
| [Break Animation](#module-break-animation) | Mining progress, dissolving block ghosts and debris on successful breaks. | On |
| [Place Preview](#module-place-preview) | Show the actual adjacent or replaceable block position for your held block. | Off |
| [Hit Particles](#module-hit-particles) | Colored sparks when loaded living entities are actually hurt. | On |
| [Jump Circles](#module-jump-circles) | Expanding floor ripples when you leave the ground with upward velocity. | Off |
| [Trails](#module-trails) | A fading ribbon following your actual movement. | Off |
| [Damage Numbers](#module-damage-numbers) | Floating numbers from actual health decreases of loaded living entities. | Off |
| [Hit Markers](#module-hit-markers) | A brief crosshair flash when your recently attacked target is hurt. | Off |
| [Footsteps](#module-footsteps) | Small fading floor rings spaced along grounded movement. | Off |
| [Death Effects](#module-death-effects) | A rising particle burst when a loaded living entity dies. | Off |
| [Projectile Trails](#module-projectile-trails) | Fading paths behind actual loaded projectiles. | Off |
| [Pickup Streaks](#module-pickup-streaks) | A curved streak from an item to the collector on real pickup packets. | Off |
| [Teleport Effects](#module-teleport-effects) | Paired energy rings on large local position changes. | Off |
| [Player Aura](#module-player-aura) | Rotating rings around your actual player position. | Off |
| [Explosion Effects](#module-explosion-effects) | Shockwave rings and sparks from actual nearby explosion packets. | Off |
| [Totem Pops](#module-totem-pops) | Golden rings, sparks and a floating pop label on real totem-use statuses. | Off |
| [Landing Impact](#module-landing-impact) | A floor shockwave when a falling player lands. | Off |
| [Health Pulse](#module-health-pulse) | A fading edge vignette when your local health decreases. | Off |
| [Air Effects](#module-air-effects) | A bounded animated particle field around the camera, drawn in world space. | Off |
| [Block Effects](#module-block-effects) | Independent colored fragments or frames from actual placements and breaks. | Off |
| [Damage Effects](#module-damage-effects) | Confirmed hurt bursts and an optional persistent low-health warning. | Off |
| [Contrails](#module-contrails) | Paired fading paths from real player motion, optionally restricted to elytra flight. | Off |
| [Storm Effects](#module-storm-effects) | Branching bolts, ground ripples and a flash on actual loaded lightning strikes. | Off |
| [Vitals](#module-vitals) | A local warning driven by real health, fire and remaining underwater air. | Off |
| [Hit Sounds](#module-hit-sounds) | Local vanilla sound cues on confirmed hurts of a recently attacked target. | Off |
| [Item Physics](#module-item-physics) | Dropped-item models tumble in the air and rest on the ground; entity physics is unchanged. | Off |
| [Hand Tweaks](#module-hand-tweaks) | Actual first-person hand/item transforms and swing/equip controls; does not alter attacks. | Off |
| [Crystal Chams](#module-crystal-chams) | Tint the actual crystal model or replace it with an original Gem/Prism mesh. Normal depth remains. | Off |
| [Cape](#module-cape) | An original segmented cloth mesh with moving hems and colors, visible in third person. No image/GIF importer. | Off |
| [Wings](#module-wings) | Approved Angel, Dragon, Bat and Parrot textures on an existing articulated wing rig. Local third-person cosmetic; no flight or server broadcast. | Off |
| [Pets](#module-pets) | Original animated Rune Orb, Ghost or Dragon geometry following your rendered body in third person. | Off |
| [Emotes](#module-emotes) | Pose your local player model in third person. No camera change or server-side animation. | Off |
| [Walk Style](#module-walk-style) | Alternative local third-person arm, leg and body poses using the existing player model. | Off |
| [Spinbot](#module-spinbot) | Spin only your rendered player model. Camera, entity rotation and server packets are unchanged. | Off |

## Client

| Module | Behavior | Default |
| --- | --- | --- |
| [Interface](#module-menu) | Glass or solid panels, accent colors and motion control. | On |
| [Server Privacy](#module-server-privacy) | Reduce mod-identifying metadata and text probes. Vanilla-only channels can disable server-connected mods; gameplay remains observable. | On |
| [Name Changer](#module-name-changer) | Show a local alias for your own name in rendered labels and chat. Your account and outgoing text stay unchanged; editable input shows its actual text. | Off |
| [Automation](#module-expedition) | Review a local task plan. Safer routing and active Survival protection guard its normal actions. | Off |

## Settings reference

Numeric values are inclusive and use the listed step. Text trims outer whitespace. File selections use their dedicated browser. Per-module keybinds and the module's enable switch are separate controls.

<a id="module-entity-esp"></a>

### Entity ESP

**Render** · ID: `entity-esp` · Default: **Off**

Clean debug boxes for players, hostile mobs and non-hostile mobs.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | On | On / Off |
| Non-hostile mobs (`passives`) | Boolean | Off | On / Off |
| Dropped items (`items`) | Boolean | Off | On / Off |
| Name and distance (`labels`) | Boolean | Off | On / Off |
| Health (`health`) | Boolean | Off | On / Off |
| Nearby target radar (`radar`) | Boolean | Off | On / Off |
| Range (blocks) (`range`) | Number | 128 | 16 to 256; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Line width (`line-width`) | Number | 1 | 0.5 to 4; step 0.25 |
| Line opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Box style (`style`) | Choice | Boxes | Boxes / Corners / Screen |
| Minimum marker size (pixels) (`min-marker-pixels`) | Number | 0 | 0 to 12; step 1 |
| Players color (`players-color`) | Choice | Cyan | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Players red (`players-red`) | Number | 102 | 0 to 255; step 1 |
| Players green (`players-green`) | Number | 223 | 0 to 255; step 1 |
| Players blue (`players-blue`) | Number | 247 | 0 to 255; step 1 |
| Hostile mobs color (`hostiles-color`) | Choice | Red | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Hostile mobs red (`hostiles-red`) | Number | 255 | 0 to 255; step 1 |
| Hostile mobs green (`hostiles-green`) | Number | 113 | 0 to 255; step 1 |
| Hostile mobs blue (`hostiles-blue`) | Number | 136 | 0 to 255; step 1 |
| Non-hostile mobs color (`passives-color`) | Choice | Mint | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Non-hostile mobs red (`passives-red`) | Number | 121 | 0 to 255; step 1 |
| Non-hostile mobs green (`passives-green`) | Number | 248 | 0 to 255; step 1 |
| Non-hostile mobs blue (`passives-blue`) | Number | 204 | 0 to 255; step 1 |
| Dropped items color (`items-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Dropped items red (`items-red`) | Number | 255 | 0 to 255; step 1 |
| Dropped items green (`items-green`) | Number | 207 | 0 to 255; step 1 |
| Dropped items blue (`items-blue`) | Number | 120 | 0 to 255; step 1 |
| Label range (blocks) (`label-range`) | Number | 48 | 8 to 256; step 4 |
| Label scale (`label-scale`) | Number | 1 | 0.5 to 2; step 0.05 |
| Shrink distant labels (`distance-scale`) | Boolean | On | On / Off |
| Full-size distance (blocks) (`label-near`) | Number | 8 | 2 to 64; step 2 |
| Distant label size (`label-far-scale`) | Number | 0.45 | 0.2 to 1; step 0.05 |
| Fade distant labels (`fade-labels`) | Boolean | On | On / Off |
| Maximum labels (`max-labels`) | Number | 16 | 0 to 128; step 1 |
| Hide overlapping labels (`declutter`) | Boolean | On | On / Off |

[Back to Render](#render)

<a id="module-storage-esp"></a>

### Storage ESP

**World** · ID: `storage-esp` · Default: **Off**

Debug boxes around loaded containers, with colors for each storage group.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Container labels (`labels`) | Boolean | Off | On / Off |
| Storage tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Tracer width (`tracer-width`) | Number | 1 | 0.5 to 4; step 0.25 |
| Range (blocks) (`range`) | Number | 64 | 16 to 128; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Line width (`line-width`) | Number | 1 | 0.5 to 4; step 0.25 |
| Line opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Box style (`style`) | Choice | Boxes | Boxes / Corners / Screen |
| Minimum marker size (pixels) (`min-marker-pixels`) | Number | 0 | 0 to 12; step 1 |
| Chests (`chests`) | Boolean | On | On / Off |
| Chests color (`chests-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Chests red (`chests-red`) | Number | 255 | 0 to 255; step 1 |
| Chests green (`chests-green`) | Number | 207 | 0 to 255; step 1 |
| Chests blue (`chests-blue`) | Number | 120 | 0 to 255; step 1 |
| Trapped chests (`trapped-chests`) | Boolean | On | On / Off |
| Trapped chests color (`trapped-chests-color`) | Choice | Red | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Trapped chests red (`trapped-chests-red`) | Number | 255 | 0 to 255; step 1 |
| Trapped chests green (`trapped-chests-green`) | Number | 113 | 0 to 255; step 1 |
| Trapped chests blue (`trapped-chests-blue`) | Number | 136 | 0 to 255; step 1 |
| Ender chests (`ender-chests`) | Boolean | On | On / Off |
| Ender chests color (`ender-chests-color`) | Choice | Violet | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Ender chests red (`ender-chests-red`) | Number | 181 | 0 to 255; step 1 |
| Ender chests green (`ender-chests-green`) | Number | 155 | 0 to 255; step 1 |
| Ender chests blue (`ender-chests-blue`) | Number | 250 | 0 to 255; step 1 |
| Barrels (`barrels`) | Boolean | On | On / Off |
| Barrels color (`barrels-color`) | Choice | Orange | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Barrels red (`barrels-red`) | Number | 255 | 0 to 255; step 1 |
| Barrels green (`barrels-green`) | Number | 165 | 0 to 255; step 1 |
| Barrels blue (`barrels-blue`) | Number | 99 | 0 to 255; step 1 |
| Shulker boxes (`shulkers`) | Boolean | On | On / Off |
| Shulker boxes color (`shulkers-color`) | Choice | Pink | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Shulker boxes red (`shulkers-red`) | Number | 244 | 0 to 255; step 1 |
| Shulker boxes green (`shulkers-green`) | Number | 140 | 0 to 255; step 1 |
| Shulker boxes blue (`shulkers-blue`) | Number | 220 | 0 to 255; step 1 |
| Hoppers (`hoppers`) | Boolean | On | On / Off |
| Hoppers color (`hoppers-color`) | Choice | Cyan | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Hoppers red (`hoppers-red`) | Number | 102 | 0 to 255; step 1 |
| Hoppers green (`hoppers-green`) | Number | 223 | 0 to 255; step 1 |
| Hoppers blue (`hoppers-blue`) | Number | 247 | 0 to 255; step 1 |
| Furnaces and smokers (`furnaces`) | Boolean | On | On / Off |
| Furnaces and smokers color (`furnaces-color`) | Choice | Orange | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Furnaces and smokers red (`furnaces-red`) | Number | 255 | 0 to 255; step 1 |
| Furnaces and smokers green (`furnaces-green`) | Number | 165 | 0 to 255; step 1 |
| Furnaces and smokers blue (`furnaces-blue`) | Number | 99 | 0 to 255; step 1 |
| Dispensers (`dispensers`) | Boolean | On | On / Off |
| Dispensers color (`dispensers-color`) | Choice | Gray | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Dispensers red (`dispensers-red`) | Number | 190 | 0 to 255; step 1 |
| Dispensers green (`dispensers-green`) | Number | 197 | 0 to 255; step 1 |
| Dispensers blue (`dispensers-blue`) | Number | 206 | 0 to 255; step 1 |
| Droppers (`droppers`) | Boolean | On | On / Off |
| Droppers color (`droppers-color`) | Choice | White | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Droppers red (`droppers-red`) | Number | 241 | 0 to 255; step 1 |
| Droppers green (`droppers-green`) | Number | 243 | 0 to 255; step 1 |
| Droppers blue (`droppers-blue`) | Number | 246 | 0 to 255; step 1 |
| Brewing stands (`brewing`) | Boolean | On | On / Off |
| Brewing stands color (`brewing-color`) | Choice | Blue | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Brewing stands red (`brewing-red`) | Number | 131 | 0 to 255; step 1 |
| Brewing stands green (`brewing-green`) | Number | 180 | 0 to 255; step 1 |
| Brewing stands blue (`brewing-blue`) | Number | 255 | 0 to 255; step 1 |
| Crafters (`crafters`) | Boolean | On | On / Off |
| Crafters color (`crafters-color`) | Choice | Mint | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Crafters red (`crafters-red`) | Number | 121 | 0 to 255; step 1 |
| Crafters green (`crafters-green`) | Number | 248 | 0 to 255; step 1 |
| Crafters blue (`crafters-blue`) | Number | 204 | 0 to 255; step 1 |
| Chiseled bookshelves (`bookshelves`) | Boolean | On | On / Off |
| Chiseled bookshelves color (`bookshelves-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Chiseled bookshelves red (`bookshelves-red`) | Number | 255 | 0 to 255; step 1 |
| Chiseled bookshelves green (`bookshelves-green`) | Number | 207 | 0 to 255; step 1 |
| Chiseled bookshelves blue (`bookshelves-blue`) | Number | 120 | 0 to 255; step 1 |
| Decorated pots (`pots`) | Boolean | On | On / Off |
| Decorated pots color (`pots-color`) | Choice | Orange | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Decorated pots red (`pots-red`) | Number | 255 | 0 to 255; step 1 |
| Decorated pots green (`pots-green`) | Number | 165 | 0 to 255; step 1 |
| Decorated pots blue (`pots-blue`) | Number | 99 | 0 to 255; step 1 |
| Lecterns (`lecterns`) | Boolean | On | On / Off |
| Lecterns color (`lecterns-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Lecterns red (`lecterns-red`) | Number | 255 | 0 to 255; step 1 |
| Lecterns green (`lecterns-green`) | Number | 207 | 0 to 255; step 1 |
| Lecterns blue (`lecterns-blue`) | Number | 120 | 0 to 255; step 1 |
| Beehives and nests (`hives`) | Boolean | On | On / Off |
| Beehives and nests color (`hives-color`) | Choice | Lime | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Beehives and nests red (`hives-red`) | Number | 185 | 0 to 255; step 1 |
| Beehives and nests green (`hives-green`) | Number | 235 | 0 to 255; step 1 |
| Beehives and nests blue (`hives-blue`) | Number | 114 | 0 to 255; step 1 |
| Campfires (`campfires`) | Boolean | On | On / Off |
| Campfires color (`campfires-color`) | Choice | Orange | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Campfires red (`campfires-red`) | Number | 255 | 0 to 255; step 1 |
| Campfires green (`campfires-green`) | Number | 165 | 0 to 255; step 1 |
| Campfires blue (`campfires-blue`) | Number | 99 | 0 to 255; step 1 |
| Jukeboxes (`jukeboxes`) | Boolean | On | On / Off |
| Jukeboxes color (`jukeboxes-color`) | Choice | Pink | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Jukeboxes red (`jukeboxes-red`) | Number | 244 | 0 to 255; step 1 |
| Jukeboxes green (`jukeboxes-green`) | Number | 140 | 0 to 255; step 1 |
| Jukeboxes blue (`jukeboxes-blue`) | Number | 220 | 0 to 255; step 1 |
| Storage minecarts and boats (`vehicles`) | Boolean | On | On / Off |
| Storage minecarts and boats color (`vehicles-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Storage minecarts and boats red (`vehicles-red`) | Number | 255 | 0 to 255; step 1 |
| Storage minecarts and boats green (`vehicles-green`) | Number | 207 | 0 to 255; step 1 |
| Storage minecarts and boats blue (`vehicles-blue`) | Number | 120 | 0 to 255; step 1 |
| Label range (blocks) (`label-range`) | Number | 40 | 8 to 128; step 4 |
| Label scale (`label-scale`) | Number | 1 | 0.5 to 2; step 0.05 |
| Shrink distant labels (`distance-scale`) | Boolean | On | On / Off |
| Full-size distance (blocks) (`label-near`) | Number | 8 | 2 to 64; step 2 |
| Distant label size (`label-far-scale`) | Number | 0.45 | 0.2 to 1; step 0.05 |
| Fade distant labels (`fade-labels`) | Boolean | On | On / Off |
| Maximum labels (`max-labels`) | Number | 12 | 0 to 128; step 1 |
| Hide overlapping labels (`declutter`) | Boolean | On | On / Off |

[Back to World](#world)

<a id="module-tracers"></a>

### Tracers

**Render** · ID: `tracers` · Default: **Off**

Single clean lines to selected entities, using the same colors as Entity ESP.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Non-hostile mobs (`passives`) | Boolean | Off | On / Off |
| Dropped items (`items`) | Boolean | Off | On / Off |
| Range (blocks) (`range`) | Number | 128 | 16 to 256; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Line width (`line-width`) | Number | 1 | 0.5 to 4; step 0.25 |
| Line opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Target anchor (`anchor`) | Choice | Center | Center / Feet / Head |
| Use Entity ESP colors (`match-esp-colors`) | Boolean | On | On / Off |
| Players color (`players-color`) | Choice | Cyan | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Players red (`players-red`) | Number | 102 | 0 to 255; step 1 |
| Players green (`players-green`) | Number | 223 | 0 to 255; step 1 |
| Players blue (`players-blue`) | Number | 247 | 0 to 255; step 1 |
| Hostile mobs color (`hostiles-color`) | Choice | Red | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Hostile mobs red (`hostiles-red`) | Number | 255 | 0 to 255; step 1 |
| Hostile mobs green (`hostiles-green`) | Number | 113 | 0 to 255; step 1 |
| Hostile mobs blue (`hostiles-blue`) | Number | 136 | 0 to 255; step 1 |
| Non-hostile mobs color (`passives-color`) | Choice | Mint | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Non-hostile mobs red (`passives-red`) | Number | 121 | 0 to 255; step 1 |
| Non-hostile mobs green (`passives-green`) | Number | 248 | 0 to 255; step 1 |
| Non-hostile mobs blue (`passives-blue`) | Number | 204 | 0 to 255; step 1 |
| Dropped items color (`items-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Dropped items red (`items-red`) | Number | 255 | 0 to 255; step 1 |
| Dropped items green (`items-green`) | Number | 207 | 0 to 255; step 1 |
| Dropped items blue (`items-blue`) | Number | 120 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-fullbright"></a>

### Fullbright

**World** · ID: `fullbright` · Default: **Off**

Lift world lighting for clearer exploration.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Brightness (`gamma`) | Number | 16 | 1 to 16; step 0.5 |

[Back to World](#world)

<a id="module-coordinates"></a>

### Coordinates

**HUD** · ID: `coordinates` · Default: **On**

Show position and dimension in your HUD.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Show dimension (`show-dimension`) | Boolean | On | On / Off |

[Back to HUD](#hud)

<a id="module-crosshair"></a>

### Crosshair

**HUD** · ID: `crosshair` · Default: **Off**

A crisp customizable crosshair.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Arm length (`size`) | Number | 6 | 2 to 20; step 0.5 |
| Center gap (`gap`) | Number | 3 | 0 to 12; step 0.5 |
| Line width (`line-width`) | Number | 1.5 | 0.5 to 4; step 0.25 |

[Back to HUD](#hud)

<a id="module-particles"></a>

### Particles

**Cosmetics** · ID: `particles` · Default: **On**

Animated ambient particles in the Analyzer menu.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Density (`density`) | Number | 0.75 | 0 to 1; step 0.05 |

[Back to Cosmetics](#cosmetics)

<a id="module-menu"></a>

### Interface

**Client** · ID: `menu` · Default: **On**

Glass or solid panels, accent colors and motion control.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Panel material (`material`) | Choice | Glass | Glass / Solid |
| Menu theme (`theme`) | Choice | Glass | Glass / Classic / Minimal / Neon |
| Accent color (`accent`) | Choice | Rose | Rose / Mint / Violet / Gold |
| Motion amount (`motion`) | Number | 0.8 | 0 to 1; step 0.05 |
| Custom home screen (`home-enabled`) | Boolean | On | On / Off |
| Home background (`home-background`) | Choice | Panorama | Panorama / Orbit |
| Home animation (`home-motion`) | Number | 0.65 | 0 to 1; step 0.05 |
| Blur home submenus (`home-blur`) | Boolean | On | On / Off |
| Menu layout (`layout`) | Choice | Studio | Studio / Click GUI |
| Render column X (`classic-visual-x`) | Number | 2 | 0 to 100; step 0.1 |
| Render column Y (`classic-visual-y`) | Number | 25 | 0 to 100; step 0.1 |
| Render collapsed (`classic-visual-collapsed`) | Boolean | Off | On / Off |
| World column X (`classic-world-x`) | Number | 26 | 0 to 100; step 0.1 |
| World column Y (`classic-world-y`) | Number | 25 | 0 to 100; step 0.1 |
| World collapsed (`classic-world-collapsed`) | Boolean | Off | On / Off |
| HUD column X (`classic-hud-x`) | Number | 50 | 0 to 100; step 0.1 |
| HUD column Y (`classic-hud-y`) | Number | 25 | 0 to 100; step 0.1 |
| HUD collapsed (`classic-hud-collapsed`) | Boolean | Off | On / Off |
| Combat column X (`classic-combat-x`) | Number | 74 | 0 to 100; step 0.1 |
| Combat column Y (`classic-combat-y`) | Number | 25 | 0 to 100; step 0.1 |
| Combat collapsed (`classic-combat-collapsed`) | Boolean | Off | On / Off |
| Movement column X (`classic-movement-x`) | Number | 98 | 0 to 100; step 0.1 |
| Movement column Y (`classic-movement-y`) | Number | 25 | 0 to 100; step 0.1 |
| Movement collapsed (`classic-movement-collapsed`) | Boolean | Off | On / Off |
| HVH column X (`classic-hvh-x`) | Number | 2 | 0 to 100; step 0.1 |
| HVH column Y (`classic-hvh-y`) | Number | 95 | 0 to 100; step 0.1 |
| HVH collapsed (`classic-hvh-collapsed`) | Boolean | Off | On / Off |
| Player column X (`classic-player-x`) | Number | 26 | 0 to 100; step 0.1 |
| Player column Y (`classic-player-y`) | Number | 95 | 0 to 100; step 0.1 |
| Player collapsed (`classic-player-collapsed`) | Boolean | Off | On / Off |
| Cosmetics column X (`classic-cosmetics-x`) | Number | 50 | 0 to 100; step 0.1 |
| Cosmetics column Y (`classic-cosmetics-y`) | Number | 95 | 0 to 100; step 0.1 |
| Cosmetics collapsed (`classic-cosmetics-collapsed`) | Boolean | Off | On / Off |
| Client column X (`classic-client-x`) | Number | 74 | 0 to 100; step 0.1 |
| Client column Y (`classic-client-y`) | Number | 95 | 0 to 100; step 0.1 |
| Client collapsed (`classic-client-collapsed`) | Boolean | Off | On / Off |

[Back to Client](#client)

<a id="module-sky"></a>

### Skybox

**World** · ID: `sky` · Default: **On**

Procedural skies, local panoramic images and six-face skyboxes.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Sky source (`source`) | Choice | Procedural | Procedural / Custom / Minecraft panorama / Minecraft End |
| Browse custom skyboxes… (`custom-path`) | Text | Empty | Local path, 0–4096 characters; no control characters or § |
| Sky preset (`preset`) | Choice | Aurora | Aurora / Nebula / Ember |
| Intensity (`intensity`) | Number | 0.8 | 0 to 1; step 0.05 |
| Twinkling stars (`stars`) | Boolean | On | On / Off |
| Animation speed (`speed`) | Number | 0.6 | 0 to 2; step 0.05 |
| Visible when (`day-night`) | Choice | Always | Always / Night |
| Custom yaw rotation (degrees) (`rotation`) | Number | 0 | -180 to 180; step 1 |
| Custom brightness (`exposure`) | Number | 1 | 0.1 to 3; step 0.05 |
| Custom sky fades in rain (`weather-fade`) | Boolean | On | On / Off |
| Custom sky in the End (`end-sky`) | Boolean | Off | On / Off |

[Back to World](#world)

<a id="module-shader"></a>

### Atmosphere

**Render** · ID: `shader` · Default: **Off**

Cinematic bloom and color grading. Skybox controls the sky.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Preset (`preset`) | Choice | Aurora | Off / Aurora / Ember / Frost / Bloom |
| Strength (`strength`) | Number | 0.75 | 0 to 1; step 0.01 |

[Back to Render](#render)

<a id="module-server-privacy"></a>

### Server Privacy

**Client** · ID: `server-privacy` · Default: **On**

Reduce mod-identifying metadata and text probes. Vanilla-only channels can disable server-connected mods; gameplay remains observable.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Report vanilla client brand (`vanilla-brand`) | Boolean | On | On / Off |
| Block mod channel announcements (`block-mod-channels`) | Boolean | On | On / Off |
| Vanilla-only channels (strict) (`vanilla-only`) | Boolean | On | On / Off |
| Hide non-vanilla known packs (`hide-known-packs`) | Boolean | On | On / Off |
| Decline mod login queries (`block-login-queries`) | Boolean | On | On / Off |
| Protect remote translation/key probes (`block-key-probes`) | Boolean | On | On / Off |
| Block local/private pack URLs (`block-local-pack-urls`) | Boolean | On | On / Off |
| Hide identifying pack download headers (`scrub-pack-headers`) | Boolean | On | On / Off |

[Back to Client](#client)

<a id="module-hud"></a>

### HUD Studio

**HUD** · ID: `hud` · Default: **On**

Choose your menu, appearance and HUD widget layout.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Menu layout (`menu-layout`) | Choice | Studio | Studio / Click GUI |
| Menu material (`menu-material`) | Choice | Glass | Glass / Solid |
| Menu theme (`menu-theme`) | Choice | Glass | Glass / Classic / Minimal / Neon |
| Accent color (`menu-accent`) | Choice | Rose | Rose / Mint / Violet / Gold |
| Custom home screen (`home-enabled`) | Boolean | On | On / Off |
| Home background (`home-background`) | Choice | Panorama | Panorama / Orbit |
| Home animation (`home-motion`) | Number | 0.65 | 0 to 1; step 0.05 |
| Blur home submenus (`home-blur`) | Boolean | On | On / Off |
| HUD theme (`theme`) | Choice | Glass | Glass / Classic / Minimal / Neon |
| HUD scale (`scale`) | Number | 0.6 | 0.5 to 1.75; step 0.05 |
| Maximum list rows (`max-modules`) | Number | 10 | 3 to 16; step 1 |
| Editor grid snapping (`snap`) | Boolean | On | On / Off |
| Editor show disabled widgets (`show-disabled`) | Boolean | Off | On / Off |
| Coordinates X (`coordinates-x`) | Number | 2 | 0 to 100; step 0.1 |
| Coordinates Y (`coordinates-y`) | Number | 94 | 0 to 100; step 0.1 |
| Radar X (`radar-x`) | Number | 2 | 0 to 100; step 0.1 |
| Radar Y (`radar-y`) | Number | 26 | 0 to 100; step 0.1 |

[Back to HUD](#hud)

<a id="module-watermark"></a>

### Watermark

**HUD** · ID: `watermark` · Default: **On**

Your selected logo and version badge.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 2 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 2 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |
| Logo (`logo`) | Choice | Analyzer logo | Analyzer logo / A logo |

[Back to HUD](#hud)

<a id="module-active-modules"></a>

### Active Modules

**HUD** · ID: `active-modules` · Default: **On**

A bounded list of enabled gameplay modules.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 98 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 2 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |
| ESP target details (`details`) | Boolean | On | On / Off |
| Include HUD modules (`hud-modules`) | Boolean | Off | On / Off |

[Back to HUD](#hud)

<a id="module-fps"></a>

### FPS

**HUD** · ID: `fps` · Default: **On**

Current client frames per second.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 2 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 12 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |

[Back to HUD](#hud)

<a id="module-ping"></a>

### Ping

**HUD** · ID: `ping` · Default: **Off**

Actual player latency reported by the server.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 17 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 12 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |

[Back to HUD](#hud)

<a id="module-speed-hud"></a>

### Speed HUD

**HUD** · ID: `speed-hud` · Default: **Off**

Horizontal movement speed in blocks per second.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 32 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 12 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |
| Speed units (`units`) | Choice | Blocks/s | Blocks/s / km/h |

[Back to HUD](#hud)

<a id="module-armor"></a>

### Armor

**HUD** · ID: `armor` · Default: **Off**

Equipped armor icons with remaining durability.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 50 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 86 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |
| Durability percentages (`durability`) | Boolean | On | On / Off |

[Back to HUD](#hud)

<a id="module-potions"></a>

### Potion Effects

**HUD** · ID: `potions` · Default: **Off**

Active effects, levels and remaining durations.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 98 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 55 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |

[Back to HUD](#hud)

<a id="module-target-hud"></a>

### Target HUD

**HUD** · ID: `target-hud` · Default: **Off**

Health and armor of your acquired combat or crosshair target.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 68 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 74 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |

[Back to HUD](#hud)

<a id="module-keybinds"></a>

### Keybinds

**HUD** · ID: `keybinds` · Default: **Off**

Assigned module binds with their enabled state.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 2 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 48 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |
| Only active binds (`active-only`) | Boolean | Off | On / Off |

[Back to HUD](#hud)

<a id="module-inventory"></a>

### Inventory Preview

**HUD** · ID: `inventory` · Default: **Off**

Your actual inventory in a compact three-row panel.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 50 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 68 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |
| Stack counts (`counts`) | Boolean | On | On / Off |

[Back to HUD](#hud)

<a id="module-compass"></a>

### Compass

**HUD** · ID: `compass` · Default: **Off**

Yaw-based cardinal direction and heading.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 50 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 2 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |

[Back to HUD](#hud)

<a id="module-biome"></a>

### Biome

**HUD** · ID: `biome` · Default: **Off**

Registry name of the biome at your current position.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 2 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 84 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |

[Back to HUD](#hud)

<a id="module-world-clock"></a>

### World Clock

**HUD** · ID: `world-clock` · Default: **Off**

World time of day and current day number.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 2 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 76 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |
| Clock format (`format`) | Choice | 24 hour | 24 hour / 12 hour |

[Back to HUD](#hud)

<a id="module-item-counter"></a>

### Item Counter

**HUD** · ID: `item-counter` · Default: **Off**

Count a selected resource in your inventory and offhand.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 81 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 86 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |
| Count item (`item`) | Choice | Totems | Totems / Pearls / Crystals / Gapples / Arrows |

[Back to HUD](#hud)

<a id="module-keystrokes"></a>

### Keystrokes

**HUD** · ID: `keystrokes` · Default: **Off**

Live movement, jump and mouse-button states.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 2 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 61 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |
| Show mouse buttons (`mouse`) | Boolean | On | On / Off |

[Back to HUD](#hud)

<a id="module-reach-display"></a>

### Target Distance

**HUD** · ID: `reach-display` · Default: **Off**

Measured camera distance to the current raycast hit.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| HUD position X (`x`) | Number | 68 | 0 to 100; step 0.1 |
| HUD position Y (`y`) | Number | 12 | 0 to 100; step 0.1 |
| Panel opacity (`opacity`) | Number | 0.8 | 0.1 to 1; step 0.05 |

[Back to HUD](#hud)

<a id="module-chams"></a>

### Entity Chams

**Render** · ID: `chams` · Default: **Off**

Shader materials on actual living models, with separate visible and occluded colors.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Material style (`style`) | Choice | Flat | Flat / Metallic / Glass / Glow / Wireframe |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | On | On / Off |
| Non-hostile mobs (`passives`) | Boolean | Off | On / Off |
| Own player in third person (`self`) | Boolean | Off | On / Off |
| Invisible entities (`invisibles`) | Boolean | Off | On / Off |
| Range (blocks) (`range`) | Number | 128 | 8 to 256; step 8 |
| Occluded model pass (`through-walls`) | Boolean | On | On / Off |
| Visible opacity (%) (`visible-opacity`) | Number | 85 | 0 to 100; step 5 |
| Occluded opacity (%) (`hidden-opacity`) | Number | 45 | 0 to 100; step 5 |
| Material strength (`effect-strength`) | Number | 1 | 0 to 2; step 0.05 |
| Material animation speed (`animation-speed`) | Number | 1 | 0 to 3; step 0.05 |
| Wire width (`wire-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Use Entity ESP visible colors (`match-esp-colors`) | Boolean | On | On / Off |
| Visible players color (`players-color`) | Choice | Cyan | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Visible players red (`players-red`) | Number | 102 | 0 to 255; step 1 |
| Visible players green (`players-green`) | Number | 223 | 0 to 255; step 1 |
| Visible players blue (`players-blue`) | Number | 247 | 0 to 255; step 1 |
| Visible hostile mobs color (`hostiles-color`) | Choice | Red | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Visible hostile mobs red (`hostiles-red`) | Number | 255 | 0 to 255; step 1 |
| Visible hostile mobs green (`hostiles-green`) | Number | 113 | 0 to 255; step 1 |
| Visible hostile mobs blue (`hostiles-blue`) | Number | 136 | 0 to 255; step 1 |
| Visible non-hostile mobs color (`passives-color`) | Choice | Mint | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Visible non-hostile mobs red (`passives-red`) | Number | 121 | 0 to 255; step 1 |
| Visible non-hostile mobs green (`passives-green`) | Number | 248 | 0 to 255; step 1 |
| Visible non-hostile mobs blue (`passives-blue`) | Number | 204 | 0 to 255; step 1 |
| Occluded players color (`players-hidden-color`) | Choice | Violet | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Occluded players red (`players-hidden-red`) | Number | 181 | 0 to 255; step 1 |
| Occluded players green (`players-hidden-green`) | Number | 155 | 0 to 255; step 1 |
| Occluded players blue (`players-hidden-blue`) | Number | 250 | 0 to 255; step 1 |
| Occluded hostile mobs color (`hostiles-hidden-color`) | Choice | Orange | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Occluded hostile mobs red (`hostiles-hidden-red`) | Number | 255 | 0 to 255; step 1 |
| Occluded hostile mobs green (`hostiles-hidden-green`) | Number | 165 | 0 to 255; step 1 |
| Occluded hostile mobs blue (`hostiles-hidden-blue`) | Number | 99 | 0 to 255; step 1 |
| Occluded non-hostile mobs color (`passives-hidden-color`) | Choice | Blue | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Occluded non-hostile mobs red (`passives-hidden-red`) | Number | 131 | 0 to 255; step 1 |
| Occluded non-hostile mobs green (`passives-hidden-green`) | Number | 180 | 0 to 255; step 1 |
| Occluded non-hostile mobs blue (`passives-hidden-blue`) | Number | 255 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-amethyst-finder"></a>

### Amethyst Finder

**Render** · ID: `amethyst-finder` · Default: **Off**

Marks received budding amethyst or geode blocks; chunk evidence is local to loaded data.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Amethyst blocks (`target`) | Choice | Budding amethyst | Budding amethyst / All amethyst |
| Minimum Y (`min-y`) | Number | -64 | -64 to 320; step 1 |
| Maximum Y (`max-y`) | Number | 64 | -64 to 320; step 1 |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Violet | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 181 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 155 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 250 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-bedrock-void-esp"></a>

### Bedrock Void ESP

**Render** · ID: `bedrock-void-esp` · Default: **Off**

Finds known air openings in the world's lowest layer beside received bedrock.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Minimum connected opening cells (`minimum-size`) | Number | 2 | 1 to 16; step 1 |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Red | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 113 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 136 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-beehive-esp"></a>

### Beehive ESP

**Render** · ID: `beehive-esp` · Default: **Off**

Shows received beehives and bee nests at the selected honey level.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Minimum honey level (`minimum-honey`) | Number | 5 | 0 to 5; step 1 |
| Crafted beehives (`beehives`) | Boolean | On | On / Off |
| Natural bee nests (`bee-nests`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 207 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 120 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-block-notifier"></a>

### Block Notifier

**Render** · ID: `block-notifier` · Default: **Off**

Highlights selected valuable blocks, workstations or storage in received chunks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Block group (`target`) | Choice | Valuable blocks | Valuable blocks / Spawners / Workstations / Storage / Diamond ore / Ancient debris / Beacons / Nether portals |
| Minimum matching cells including target (`minimum-neighbors`) | Number | 1 | 1 to 7; step 1 |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Cyan | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 102 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 223 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 247 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-covered-hole-esp"></a>

### Covered Hole ESP

**Render** · ID: `covered-hole-esp` · Default: **Off**

Finds a solid cover above a bounded, enclosed vertical shaft; natural formations can match.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Minimum air depth (`minimum-depth`) | Number | 3 | 2 to 16; step 1 |
| Maximum checked depth (`maximum-depth`) | Number | 16 | 2 to 32; step 1 |
| Only building-block covers (`manufactured-cover-only`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Orange | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 165 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 99 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-deepslate-esp"></a>

### Deepslate ESP

**Render** · ID: `deepslate-esp` · Default: **Off**

Highlights deepslate by type and height; high blocks are a clue rather than placement proof.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Deepslate blocks (`target`) | Choice | Raw deepslate | Raw deepslate / Building variants / Both |
| Minimum Y (`min-y`) | Number | 8 | -64 to 320; step 1 |
| Maximum Y (`max-y`) | Number | 320 | -64 to 320; step 1 |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Blue | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 131 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 180 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 255 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-dripstone-esp"></a>

### Dripstone ESP

**Render** · ID: `dripstone-esp` · Default: **Off**

Marks supported pointed-dripstone columns up to 32 blocks long.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Hanging stalactites (`stalactites`) | Boolean | On | On / Off |
| Rising stalagmites (`stalagmites`) | Boolean | On | On / Off |
| Minimum column length (`minimum-length`) | Number | 4 | 1 to 32; step 1 |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Orange | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 165 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 99 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-hole-tunnel-stairs-esp"></a>

### Hole / Tunnel / Stairs ESP

**Render** · ID: `hole-tunnel-stairs-esp` · Default: **Off**

Recognizes bounded shafts, enclosed corridors and rising passage patterns in loaded terrain.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Vertical shafts (`holes`) | Boolean | On | On / Off |
| Enclosed tunnels (`tunnels`) | Boolean | On | On / Off |
| Rising passages (`stairs`) | Boolean | On | On / Off |
| Minimum shaft depth (`minimum-depth`) | Number | 3 | 2 to 16; step 1 |
| Maximum checked shaft depth (`maximum-depth`) | Number | 16 | 2 to 32; step 1 |
| Minimum tunnel / stair length (`minimum-length`) | Number | 4 | 3 to 16; step 1 |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Violet | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 181 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 155 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 250 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-light-esp"></a>

### Light ESP

**Render** · ID: `light-esp` · Default: **Off**

Highlights received emitted block light; sunlight is excluded and source mode avoids propagated-light clutter.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Minimum block light (`minimum-light`) | Number | 8 | 1 to 15; step 1 |
| Only emitting blocks (`source-only`) | Boolean | On | On / Off |
| Include natural light-source types (`include-natural`) | Boolean | Off | On / Off |
| Torches and lanterns (`torches`) | Boolean | On | On / Off |
| Glowstone and sea lanterns (`glowstone`) | Boolean | On | On / Off |
| Redstone light sources (`redstone`) | Boolean | On | On / Off |
| Beacons and conduits (`beacons`) | Boolean | On | On / Off |
| Color by light strength (`thermal-colors`) | Boolean | Off | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 207 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 120 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-one-by-one-holes"></a>

### 1 x 1 Holes

**Render** · ID: `one-by-one-holes` · Default: **Off**

Finds one-cell pockets with a solid floor and four walls; matching terrain may be natural.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Require a solid ceiling (`covered-only`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Cyan | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 102 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 223 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 247 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-piston-esp"></a>

### Piston ESP

**Render** · ID: `piston-esp` · Default: **Off**

Highlights received pistons and piston heads, optionally only extended mechanisms.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Regular pistons (`regular`) | Boolean | On | On / Off |
| Sticky pistons (`sticky`) | Boolean | On | On / Off |
| Extended piston heads (`heads`) | Boolean | On | On / Off |
| Only extended pistons (`extended-only`) | Boolean | Off | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Mint | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 121 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 248 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 204 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-rotated-deepslate-esp"></a>

### Rotated Deepslate ESP

**Render** · ID: `rotated-deepslate-esp` · Default: **Off**

Shows raw deepslate with an actual horizontal axis; axisless decorative blocks are excluded.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Block axis (`orientation`) | Choice | Horizontal | Horizontal / X axis / Z axis |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Violet | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 181 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 155 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 250 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-spawner-notifier"></a>

### Spawner Notifier

**Render** · ID: `spawner-notifier` · Default: **Off**

Marks received spawner blocks without claiming hidden contents, activation or server data access.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Include trial spawners (`trial-spawners`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Red | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 113 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 136 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-sweet-berry-esp"></a>

### Sweet Berry ESP

**Render** · ID: `sweet-berry-esp` · Default: **Off**

Shows berry bushes from their actual received growth age.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Minimum bush age (`minimum-age`) | Number | 2 | 0 to 3; step 1 |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Pink | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 244 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 140 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 220 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-vine-esp"></a>

### Vine ESP

**Render** · ID: `vine-esp` · Default: **Off**

Shows bounded vine columns, optionally requiring an observed solid block below their base.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Minimum column length (`minimum-length`) | Number | 3 | 1 to 32; step 1 |
| Require ground contact (`grounded-only`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Mint | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 121 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 248 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 204 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-collectible-esp"></a>

### Collectible ESP

**Render** · ID: `collectible-esp` · Default: **Off**

Mark loaded item frames, armor stands and placed banners; contents stay server-controlled.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Item frames (`item-frames`) | Boolean | On | On / Off |
| Glow item frames (`glow-frames`) | Boolean | On | On / Off |
| Armor stands (`armor-stands`) | Boolean | On | On / Off |
| Placed banners (`banners`) | Boolean | On | On / Off |
| Only frames displaying an item (`filled-frames-only`) | Boolean | Off | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Pink | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 244 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 140 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 220 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-drowned-trident-esp"></a>

### Drowned Trident ESP

**Render** · ID: `drowned-trident-esp` · Default: **Off**

Mark received drowned actually holding a trident in either hand; drops are not guaranteed.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Cyan | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 102 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 223 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 247 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-invisible-esp"></a>

### Invisible ESP

**Render** · ID: `invisible-esp` · Default: **Off**

Mark received invisible players and mobs without altering their visibility or inventory.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | On | On / Off |
| Non-hostile mobs (`passives`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Violet | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 181 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 155 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 250 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-llama-esp"></a>

### Llama ESP

**Render** · ID: `llama-esp` · Default: **Off**

Mark loaded llamas, optionally including trader llamas.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Trader llamas (`trader-llamas`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 207 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 120 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-pillager-esp"></a>

### Pillager ESP

**Render** · ID: `pillager-esp` · Default: **Off**

Mark loaded pillagers with independent debug outlines.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Red | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 113 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 136 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-skeleton-esp"></a>

### Skeleton ESP

**Render** · ID: `skeleton-esp` · Default: **Off**

Draw animated bones from the real posed biped model for selected players and mobs.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs with biped models (`hostiles`) | Boolean | On | On / Off |
| Non-hostile mobs with biped models (`passives`) | Boolean | Off | On / Off |
| Invisible entities (`invisibles`) | Boolean | Off | On / Off |
| Own player in third person (`self`) | Boolean | Off | On / Off |
| Head direction bone (`head`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | White | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 241 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 243 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 246 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-villager-esp"></a>

### Villager ESP

**Render** · ID: `villager-esp` · Default: **Off**

Mark loaded villagers, optionally including zombie villagers.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Zombie villagers (`zombie-villagers`) | Boolean | Off | On / Off |
| Adults only (`adults-only`) | Boolean | Off | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Mint | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 121 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 248 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 204 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-wandering-trader-esp"></a>

### Wandering Trader ESP

**Render** · ID: `wandering-trader-esp` · Default: **Off**

Mark received wandering traders; no unreceived entities or trade data are inferred.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Blue | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 131 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 180 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 255 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-advanced-stash-finder"></a>

### Advanced Stash Finder

**Render** · ID: `advanced-stash-finder` · Default: **Off**

Records received storage clusters by chunk, with actual counts and local history.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Minimum storage blocks (`min-storage`) | Number | 4 | 1 to 128; step 1 |
| Minimum distance from world origin (`min-origin-distance`) | Number | 0 | 0 to 30000000; step 100 |
| Any spawner qualifies (`critical-spawners`) | Boolean | On | On / Off |
| Chests and trapped chests (`chests`) | Boolean | On | On / Off |
| Barrels (`barrels`) | Boolean | On | On / Off |
| Shulker boxes (`shulkers`) | Boolean | On | On / Off |
| Ender chests (`ender-chests`) | Boolean | On | On / Off |
| Furnaces, smokers and blast furnaces (`furnaces`) | Boolean | On | On / Off |
| Dispensers and droppers (`dispensers`) | Boolean | On | On / Off |
| Hoppers (`hoppers`) | Boolean | On | On / Off |
| Other storage containers (`other-storage`) | Boolean | On | On / Off |
| Include spawners in total (`spawners`) | Boolean | On | On / Off |
| Keep local stash history (`remember-stashes`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Gold | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 207 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 120 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-chunk-finder"></a>

### Chunk Finder

**Render** · ID: `chunk-finder` · Default: **Off**

Shows explainable building and farm evidence in received chunks; candidates can be natural.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Minimum evidence score (`min-evidence`) | Number | 4 | 1 to 128; step 1 |
| Storage clusters (`storage`) | Boolean | On | On / Off |
| Workstations (`workstations`) | Boolean | On | On / Off |
| Pistons and redstone components (`redstone`) | Boolean | On | On / Off |
| Crafted building blocks (`building-blocks`) | Boolean | On | On / Off |
| Unusual deepslate and end stone (`unusual-blocks`) | Boolean | On | On / Off |
| Grouped crops and hives (`farm-patterns`) | Boolean | On | On / Off |
| Suppress tuff/copper chamber clusters (`ignore-trial-chambers`) | Boolean | On | On / Off |
| Chamber block threshold (`trial-chamber-threshold`) | Number | 50 | 8 to 256; step 1 |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Orange | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 255 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 165 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 99 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-kelp-esp"></a>

### Kelp ESP

**Render** · ID: `kelp-esp` · Default: **Off**

Marks tall kelp distributions near a selected top height; this is a pattern, not proof of a farm.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Minimum kelp columns (`min-columns`) | Number | 10 | 1 to 256; step 1 |
| Minimum tall columns (`min-tall-columns`) | Number | 10 | 1 to 256; step 1 |
| Minimum column length (`min-length`) | Number | 8 | 1 to 32; step 1 |
| Matching top height (`top-y`) | Number | 62 | -64 to 320; step 1 |
| Matching tall tops (%) (`sea-level-ratio`) | Number | 60 | 0 to 100; step 5 |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Mint | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 121 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 248 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 204 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-region-map"></a>

### Region Map

**Render** · ID: `region-map` · Default: **Off**

Original coordinate grid of your position and locally recorded stashes; no assumed server region IDs.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Blocks per grid cell (`grid-size`) | Number | 512 | 16 to 65536; step 16 |
| Horizontal position (%) (`position-x`) | Number | 98 | 0 to 100; step 1 |
| Vertical position (%) (`position-y`) | Number | 70 | 0 to 100; step 1 |
| Visible cells (`cells`) | Choice | 5 | 3 / 5 / 7 / 9 |
| Coordinates (`show-coordinates`) | Boolean | On | On / Off |
| Grid coordinates (`show-labels`) | Boolean | On | On / Off |
| Player indicator (`show-player`) | Boolean | On | On / Off |
| Local stash markers (`show-stashes`) | Boolean | On | On / Off |
| Range (blocks) (`range`) | Number | 96 | 16 to 256; step 8 |
| Scan work per tick (`scan-budget`) | Number | 4096 | 512 to 16384; step 512 |
| Maximum markers (`max-markers`) | Number | 128 | 8 to 512; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Tracers (`tracers`) | Boolean | Off | On / Off |
| Tracer origin (`tracer-origin`) | Choice | Crosshair | Crosshair / Bottom / Top |
| Line width (`line-width`) | Number | 1 | 0.5 to 3; step 0.25 |
| Opacity (%) (`opacity`) | Number | 100 | 10 to 100; step 5 |
| Local discovery messages (`notify`) | Boolean | Off | On / Off |
| Marker color (`marker-color`) | Choice | Cyan | White / Red / Orange / Gold / Lime / Mint / Cyan / Blue / Violet / Pink / Gray / Custom |
| Marker red (`marker-red`) | Number | 102 | 0 to 255; step 1 |
| Marker green (`marker-green`) | Number | 223 | 0 to 255; step 1 |
| Marker blue (`marker-blue`) | Number | 247 | 0 to 255; step 1 |

[Back to Render](#render)

<a id="module-place-animation"></a>

### Place Animation

**Cosmetics** · ID: `place-animation` · Default: **On**

Ease a colored block ghost into each successful placement.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 0.7 | 0.2 to 3; step 0.1 |
| Animation (`style`) | Choice | Bloom | Bloom / Rise / Pop / Drop / Spin |
| Range (`range`) | Number | 48 | 8 to 128; step 8 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-break-animation"></a>

### Break Animation

**Cosmetics** · ID: `break-animation` · Default: **On**

Mining progress, dissolving block ghosts and debris on successful breaks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 0.8 | 0.2 to 3; step 0.1 |
| Debris count (`debris`) | Number | 20 | 4 to 48; step 4 |
| Mining progress (`progress`) | Boolean | On | On / Off |
| Ghost style (`style`) | Choice | Dissolve | Dissolve / Chip / Brackets |
| Range (`range`) | Number | 48 | 8 to 128; step 8 |
| Debris size (`size`) | Number | 0.06 | 0.02 to 0.3; step 0.01 |
| Debris spread (`spread`) | Number | 1 | 0.2 to 3; step 0.1 |
| Debris gravity (`gravity`) | Number | 2.4 | 0 to 6; step 0.2 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-place-preview"></a>

### Place Preview

**Cosmetics** · ID: `place-preview` · Default: **Off**

Show the actual adjacent or replaceable block position for your held block.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Through walls (`through-walls`) | Boolean | Off | On / Off |
| Opacity (`opacity`) | Number | 0.35 | 0.05 to 0.7; step 0.05 |
| Draw edges (`edges`) | Boolean | On | On / Off |
| Draw fill (`fill`) | Boolean | On | On / Off |
| Show blocked placement (`show-blocked`) | Boolean | On | On / Off |
| Ease position changes (`glide`) | Boolean | On | On / Off |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-block-highlight"></a>

### Block Highlight

**Render** · ID: `block-highlight` · Default: **Off**

A pulsing outline of the block under your crosshair.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Line width (`line-width`) | Number | 2 | 0.5 to 4; step 0.5 |
| Pulse (`pulse`) | Boolean | On | On / Off |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Render](#render)

<a id="module-hit-particles"></a>

### Hit Particles

**Cosmetics** · ID: `hit-particles` · Default: **On**

Colored sparks when loaded living entities are actually hurt.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 0.7 | 0.2 to 3; step 0.1 |
| Spark count (`count`) | Number | 18 | 4 to 48; step 2 |
| Particle shape (`style`) | Choice | Sparks | Sparks / Shards / Gems / Arc |
| Particle size (`size`) | Number | 0.06 | 0.02 to 0.3; step 0.01 |
| Spread (`spread`) | Number | 1 | 0.2 to 3; step 0.1 |
| Particle speed (`speed`) | Number | 1.3 | 0.2 to 3; step 0.1 |
| Gravity (`gravity`) | Number | 2.4 | 0 to 6; step 0.2 |
| Hurt entities (`who`) | Choice | Everything | Everything / Players / Self |
| Range (`range`) | Number | 64 | 8 to 128; step 8 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-jump-circles"></a>

### Jump Circles

**Cosmetics** · ID: `jump-circles` · Default: **Off**

Expanding floor ripples when you leave the ground with upward velocity.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 1 | 0.2 to 3; step 0.1 |
| Radius (`size`) | Number | 1.3 | 0.4 to 3; step 0.1 |
| Ripple shape (`style`) | Choice | Ripple | Ripple / Rings / Crown / Tile |
| Trigger (`trigger`) | Choice | Jump | Jump / Land / Both |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-trails"></a>

### Trails

**Cosmetics** · ID: `trails` · Default: **Off**

A fading ribbon following your actual movement.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 1.2 | 0.2 to 3; step 0.1 |
| Line width (`line-width`) | Number | 2 | 0.5 to 5; step 0.5 |
| Path shape (`style`) | Choice | Ribbon | Ribbon / Beads / Sparks |
| Trail height (`height`) | Number | 0.15 | 0 to 1.8; step 0.05 |
| Bead size (`size`) | Number | 0.06 | 0.02 to 0.3; step 0.01 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-damage-numbers"></a>

### Damage Numbers

**Cosmetics** · ID: `damage-numbers` · Default: **Off**

Floating numbers from actual health decreases of loaded living entities.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 1.4 | 0.2 to 3; step 0.1 |
| Text size (`scale`) | Number | 1 | 0.5 to 2; step 0.1 |
| Rise distance (`rise`) | Number | 0.8 | 0.1 to 2.5; step 0.1 |
| Include distance (`distance`) | Boolean | Off | On / Off |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-hit-markers"></a>

### Hit Markers

**Cosmetics** · ID: `hit-markers` · Default: **Off**

A brief crosshair flash when your recently attacked target is hurt.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 0.4 | 0.2 to 3; step 0.1 |
| Marker size (`size`) | Number | 7 | 3 to 16; step 1 |
| Marker shape (`style`) | Choice | Cross | Cross / Corners / Dots |
| Thickness (`thickness`) | Number | 1 | 1 to 3; step 1 |
| Rotation speed (`spin`) | Number | 0 | 0 to 5; step 0.25 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-footsteps"></a>

### Footsteps

**Cosmetics** · ID: `footsteps` · Default: **Off**

Small fading floor rings spaced along grounded movement.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 1.3 | 0.2 to 3; step 0.1 |
| Step spacing (`spacing`) | Number | 0.7 | 0.3 to 1.5; step 0.1 |
| Footprint shape (`style`) | Choice | Rings | Rings / Prints / Petals |
| Print radius (`size`) | Number | 0.32 | 0.1 to 1; step 0.02 |
| Maximum prints (`max-prints`) | Number | 24 | 4 to 64; step 2 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-death-effects"></a>

### Death Effects

**Cosmetics** · ID: `death-effects` · Default: **Off**

A rising particle burst when a loaded living entity dies.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 1.6 | 0.2 to 3; step 0.1 |
| Particle count (`count`) | Number | 28 | 8 to 48; step 4 |
| Death burst (`style`) | Choice | Soul | Soul / Shatter / Shockwave |
| Entities (`who`) | Choice | Everything | Everything / Players / Hostiles |
| Own death (`self`) | Boolean | On | On / Off |
| Range (`range`) | Number | 64 | 8 to 128; step 8 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-projectile-trails"></a>

### Projectile Trails

**Cosmetics** · ID: `projectile-trails` · Default: **Off**

Fading paths behind actual loaded projectiles.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 1.5 | 0.2 to 3; step 0.1 |
| Line width (`line-width`) | Number | 1.5 | 0.5 to 4; step 0.5 |
| Trail shape (`style`) | Choice | Line | Line / Comet / Spiral |
| Arrows and tridents (`arrows`) | Boolean | On | On / Off |
| Ender pearls (`pearls`) | Boolean | On | On / Off |
| Other thrown items (`thrown`) | Boolean | On | On / Off |
| Other projectiles (`other`) | Boolean | On | On / Off |
| Range (`range`) | Number | 64 | 8 to 128; step 8 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-pickup-streaks"></a>

### Pickup Streaks

**Cosmetics** · ID: `pickup-streaks` · Default: **Off**

A curved streak from an item to the collector on real pickup packets.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 0.6 | 0.2 to 3; step 0.1 |
| Line width (`line-width`) | Number | 2 | 0.5 to 4; step 0.5 |
| Pickup bead size (`size`) | Number | 0.06 | 0.02 to 0.3; step 0.01 |
| Streak length (`segments`) | Number | 12 | 4 to 24; step 2 |
| Collectors (`who`) | Choice | Both | Self / Players / Both |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-teleport-effects"></a>

### Teleport Effects

**Cosmetics** · ID: `teleport-effects` · Default: **Off**

Paired energy rings on large local position changes.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 1.2 | 0.2 to 3; step 0.1 |
| Minimum distance (`threshold`) | Number | 8 | 4 to 32; step 1 |
| Arrival shape (`style`) | Choice | Rings | Rings / Vortex / Portal |
| Radius (`size`) | Number | 1.5 | 0.5 to 4; step 0.1 |
| Connect departure and arrival (`link`) | Boolean | Off | On / Off |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-player-aura"></a>

### Player Aura

**Cosmetics** · ID: `player-aura` · Default: **Off**

Rotating rings around your actual player position.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Radius (`size`) | Number | 0.9 | 0.4 to 2; step 0.1 |
| Orbit speed (`speed`) | Number | 1 | 0.2 to 3; step 0.1 |
| Ring arrangement (`style`) | Choice | Orbit | Orbit / Gyroscope / Rune |
| Show own aura in first person (`first-person`) | Boolean | On | On / Off |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-target-esp"></a>

### Target ESP

**Render** · ID: `target-esp` · Default: **Off**

A clean debug box on your crosshair target or recently attacked entity, with optional orbit rings.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Attack highlight (seconds) (`linger`) | Number | 2 | 0 to 5; step 0.5 |
| Through walls (`through-walls`) | Boolean | Off | On / Off |
| Target shape (`style`) | Choice | Box | Orbit / Box / Corners |
| Size multiplier (`size`) | Number | 1 | 0.5 to 2; step 0.1 |
| Line width (`line-width`) | Number | 1 | 0.5 to 4; step 0.25 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Render](#render)

<a id="module-hole-esp"></a>

### Hole ESP

**Render** · ID: `hole-esp` · Default: **Off**

Outline loaded single-block holes with resistant floors and walls.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Scan radius (`radius`) | Number | 8 | 3 to 12; step 1 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Hole shape (`style`) | Choice | Box | Box / Floor outline |
| Line width (`line-width`) | Number | 1 | 0.5 to 4; step 0.25 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Render](#render)

<a id="module-trajectories"></a>

### Trajectories

**Render** · ID: `trajectories` · Default: **Off**

Predict a held bow, trident, snowball, egg or pearl path against loaded blocks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Prediction steps (`steps`) | Number | 80 | 20 to 160; step 10 |
| Line width (`line-width`) | Number | 1.5 | 0.5 to 4; step 0.5 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Render](#render)

<a id="module-item-esp"></a>

### Item ESP

**Render** · ID: `item-esp` · Default: **Off**

Clean debug boxes around loaded dropped items, with optional labels and ring markers.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Range (`range`) | Number | 64 | 8 to 128; step 8 |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Item labels (`labels`) | Boolean | Off | On / Off |
| Item shape (`style`) | Choice | Box | Box / Corners / Rings |
| Line width (`line-width`) | Number | 1 | 0.5 to 4; step 0.25 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Render](#render)

<a id="module-nametags"></a>

### Nametags

**Render** · ID: `nametags` · Default: **Off**

Independent readable names, health and distances for loaded players and mobs.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Range (`range`) | Number | 64 | 8 to 128; step 8 |
| Players (`players`) | Boolean | On | On / Off |
| Mobs (`mobs`) | Boolean | On | On / Off |
| Through walls (`through-walls`) | Boolean | On | On / Off |
| Text size (`scale`) | Number | 1 | 0.5 to 1.5; step 0.1 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Render](#render)

<a id="module-explosion-effects"></a>

### Explosion Effects

**Cosmetics** · ID: `explosion-effects` · Default: **Off**

Shockwave rings and sparks from actual nearby explosion packets.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 1.2 | 0.2 to 3; step 0.1 |
| Shockwave radius (`size`) | Number | 4 | 1 to 8; step 0.5 |
| Blast shape (`style`) | Choice | Nova | Nova / Implosion / Rings |
| Spark count (`count`) | Number | 32 | 0 to 64; step 4 |
| Shockwave (`shockwave`) | Boolean | On | On / Off |
| Range (`range`) | Number | 96 | 8 to 128; step 8 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-totem-pops"></a>

### Totem Pops

**Cosmetics** · ID: `totem-pops` · Default: **Off**

Golden rings, sparks and a floating pop label on real totem-use statuses.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 1.5 | 0.2 to 3; step 0.1 |
| Spark count (`count`) | Number | 30 | 8 to 48; step 2 |
| Pop shape (`style`) | Choice | Rings | Rings / Spiral / Crown |
| Radius (`size`) | Number | 1.8 | 0.5 to 4; step 0.1 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-landing-impact"></a>

### Landing Impact

**Cosmetics** · ID: `landing-impact` · Default: **Off**

A floor shockwave when a falling player lands.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 0.8 | 0.2 to 3; step 0.1 |
| Radius (`size`) | Number | 1.5 | 0.5 to 3; step 0.1 |
| Impact shape (`style`) | Choice | Ripple | Ripple / Crown |
| Debris count (`count`) | Number | 12 | 0 to 40; step 2 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-health-pulse"></a>

### Health Pulse

**Cosmetics** · ID: `health-pulse` · Default: **Off**

A fading edge vignette when your local health decreases.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Duration (seconds) (`duration`) | Number | 0.7 | 0.2 to 3; step 0.1 |
| Intensity (`intensity`) | Number | 0.6 | 0.1 to 1; step 0.1 |
| Edge shape (`style`) | Choice | Edges | Edges / Corners / Screen |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-air-effects"></a>

### Air Effects

**Cosmetics** · ID: `air-effects` · Default: **Off**

A bounded animated particle field around the camera, drawn in world space.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Ambient shape (`style`) | Choice | Fireflies | Fireflies / Embers / Snow / Stardust / Cubes / Network / Rings |
| Particle count (`density`) | Number | 48 | 12 to 160; step 4 |
| Field radius (`radius`) | Number | 12 | 4 to 24; step 1 |
| Clear camera radius (`clear-radius`) | Number | 1.5 | 0.5 to 6; step 0.25 |
| Particle size (`size`) | Number | 0.06 | 0.02 to 0.4; step 0.01 |
| Drift speed (`speed`) | Number | 0.6 | 0.1 to 3; step 0.1 |
| Horizontal wind (`wind`) | Number | 0.3 | -2 to 2; step 0.1 |
| Network link distance (`link-range`) | Number | 3 | 1 to 6; step 0.25 |
| Through walls (`through-walls`) | Boolean | Off | On / Off |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-block-effects"></a>

### Block Effects

**Cosmetics** · ID: `block-effects` · Default: **Off**

Independent colored fragments or frames from actual placements and breaks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| On placement (`on-place`) | Boolean | On | On / Off |
| On break (`on-break`) | Boolean | On | On / Off |
| Fragment shape (`style`) | Choice | Crumble | Crumble / Shatter / Frame |
| Use block map colors (`block-colors`) | Boolean | On | On / Off |
| Fragment count (`count`) | Number | 24 | 4 to 64; step 4 |
| Fragment size (`size`) | Number | 0.07 | 0.02 to 0.3; step 0.01 |
| Duration (seconds) (`duration`) | Number | 0.9 | 0.2 to 3; step 0.1 |
| Spread (`spread`) | Number | 1 | 0.2 to 3; step 0.1 |
| Gravity (`gravity`) | Number | 2.4 | 0 to 6; step 0.2 |
| Range (`range`) | Number | 48 | 8 to 128; step 8 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-damage-effects"></a>

### Damage Effects

**Cosmetics** · ID: `damage-effects` · Default: **Off**

Confirmed hurt bursts and an optional persistent low-health warning.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Hurt entities (`who`) | Choice | Everything | Everything / Players / Self |
| Hurt burst (`style`) | Choice | Shatter | Bleed / Shatter / Shockwave |
| Particle count (`count`) | Number | 20 | 4 to 48; step 2 |
| Particle size (`size`) | Number | 0.07 | 0.02 to 0.3; step 0.01 |
| Duration (seconds) (`duration`) | Number | 0.8 | 0.2 to 3; step 0.1 |
| Spread (`spread`) | Number | 1 | 0.2 to 3; step 0.1 |
| Gravity (`gravity`) | Number | 2.4 | 0 to 6; step 0.2 |
| Natural damage red (`damage-red`) | Boolean | On | On / Off |
| Low health edge pulse (`low-health`) | Boolean | On | On / Off |
| Low health (HP) (`threshold`) | Number | 6 | 2 to 20; step 1 |
| Range (`range`) | Number | 64 | 8 to 128; step 8 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-contrails"></a>

### Contrails

**Cosmetics** · ID: `contrails` · Default: **Off**

Paired fading paths from real player motion, optionally restricted to elytra flight.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Trail shape (`style`) | Choice | Streamers | Vapour / Streamers / Embers |
| Players (`who`) | Choice | Self | Self / Players / Both |
| Only while gliding (`elytra-only`) | Boolean | On | On / Off |
| Minimum speed (blocks/tick) (`min-speed`) | Number | 0.25 | 0.05 to 2; step 0.05 |
| Duration (seconds) (`duration`) | Number | 2 | 0.2 to 3; step 0.1 |
| Trail width (`line-width`) | Number | 2 | 0.5 to 5; step 0.5 |
| Wing offset (`wing-spread`) | Number | 0.7 | 0.2 to 2; step 0.1 |
| Natural speed color (`speed-color`) | Boolean | On | On / Off |
| Show own in first person (`first-person`) | Boolean | Off | On / Off |
| Range (`range`) | Number | 96 | 8 to 128; step 8 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-storm-effects"></a>

### Storm Effects

**Cosmetics** · ID: `storm-effects` · Default: **Off**

Branching bolts, ground ripples and a flash on actual loaded lightning strikes.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Strike shape (`style`) | Choice | Bolt | Bolt / Column / Spear |
| Strike height (`size`) | Number | 32 | 8 to 64; step 4 |
| Branches (`branches`) | Number | 4 | 0 to 8; step 1 |
| Duration (seconds) (`duration`) | Number | 0.8 | 0.2 to 3; step 0.1 |
| Ground sparks (`sparks`) | Boolean | On | On / Off |
| Ground ripple (`scorch`) | Boolean | On | On / Off |
| Ripple duration (`scorch-time`) | Number | 2 | 0.5 to 5; step 0.25 |
| Screen flash (`flash`) | Boolean | On | On / Off |
| Flash strength (`flash-strength`) | Number | 0.25 | 0.05 to 0.6; step 0.05 |
| Range (`range`) | Number | 128 | 8 to 128; step 8 |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-vitals"></a>

### Vitals

**Cosmetics** · ID: `vitals` · Default: **Off**

A local warning driven by real health, fire and remaining underwater air.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Low health (HP) (`threshold`) | Number | 8 | 2 to 20; step 1 |
| Warning style (`style`) | Choice | Pulse | Pulse / Steady / Corners |
| Edge intensity (`strength`) | Number | 0.6 | 0.1 to 1; step 0.1 |
| Heartbeat sound (`heartbeat`) | Boolean | Off | On / Off |
| Warn while burning (`burning`) | Boolean | On | On / Off |
| Warn on low underwater air (`drowning`) | Boolean | On | On / Off |
| Color mode (`color-mode`) | Choice | Natural | Natural / Theme / Custom / Rainbow |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Color brightness (`brightness`) | Number | 1 | 0.25 to 1.75; step 0.05 |
| Color cycle speed (`color-speed`) | Number | 1 | 0.1 to 4; step 0.1 |
| Pulse brightness (`color-pulse`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-hit-sounds"></a>

### Hit Sounds

**Cosmetics** · ID: `hit-sounds` · Default: **Off**

Local vanilla sound cues on confirmed hurts of a recently attacked target.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Hit sound (`sound`) | Choice | Ding | Pop / Punch / Crit / Tick / Ding / Orb / Amethyst |
| Volume (`volume`) | Number | 0.65 | 0 to 1; step 0.05 |
| Pitch (`pitch`) | Number | 1 | 0.4 to 2; step 0.05 |
| Pitch variation (`variation`) | Number | 0.05 | 0 to 0.4; step 0.01 |
| Raise pitch on a hit combo (`combo`) | Boolean | On | On / Off |
| Kill cue (`kill-sound`) | Choice | Lower | Off / Same / Lower / Ding |

[Back to Cosmetics](#cosmetics)

<a id="module-xray"></a>

### X-ray

**Render** · ID: `xray` · Default: **Off**

Real ore textures in loaded terrain: 15% terrain, fine terrain outlines, or ores only. Disabling restores ordinary chunks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| World style (`style`) | Choice | Fade terrain | Fade terrain / Terrain outlines / Ores only |
| Detail range (`range`) | Number | 24 | 8 to 48; step 4 |
| Subtle ore glow in Fade terrain (`glow`) | Boolean | On | On / Off |
| Keep workstations visible (`workstations`) | Boolean | On | On / Off |
| Keep storage containers visible (`storage`) | Boolean | On | On / Off |
| Entity highlights (`entities`) | Boolean | On | On / Off |
| Diamond ores (`diamond`) | Boolean | On | On / Off |
| Emerald ores (`emerald`) | Boolean | On | On / Off |
| Gold ores (`gold`) | Boolean | On | On / Off |
| Iron ores (`iron`) | Boolean | On | On / Off |
| Copper ores (`copper`) | Boolean | On | On / Off |
| Lapis ores (`lapis`) | Boolean | On | On / Off |
| Redstone ores (`redstone`) | Boolean | On | On / Off |
| Coal ores (`coal`) | Boolean | On | On / Off |
| Nether quartz ores (`quartz`) | Boolean | On | On / Off |
| Ancient debris ores (`debris`) | Boolean | On | On / Off |

[Back to Render](#render)

<a id="module-item-physics"></a>

### Item Physics

**Cosmetics** · ID: `item-physics` · Default: **Off**

Dropped-item models tumble in the air and rest on the ground; entity physics is unchanged.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Tumble speed (`tumble`) | Number | 1 | 0 to 3; step 0.05 |
| Rock in water (`water-rock`) | Boolean | On | On / Off |
| Water rocking (`water-strength`) | Number | 0.2 | 0 to 0.6; step 0.02 |
| Ground clearance (`ground-lift`) | Number | 0.035 | 0.01 to 0.15; step 0.005 |

[Back to Cosmetics](#cosmetics)

<a id="module-hand-tweaks"></a>

### Hand Tweaks

**Cosmetics** · ID: `hand-tweaks` · Default: **Off**

Actual first-person hand/item transforms and swing/equip controls; does not alter attacks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Swing style (`swing`) | Choice | Low | Vanilla / Low / Smooth / Stab / Slide / Spin / Tap |
| Swing strength (`swing-strength`) | Number | 0.4 | 0.05 to 1.5; step 0.05 |
| Cancel swing (`cancel-swing`) | Boolean | Off | On / Off |
| Hold swing pose (`hold-pose`) | Boolean | Off | On / Off |
| Held swing progress (`swing-pose`) | Number | 0.35 | 0 to 1; step 0.05 |
| Equip dip amount (`equip-dip`) | Number | 1 | 0 to 1; step 0.05 |
| Cancel eating animation (`cancel-eat`) | Boolean | Off | On / Off |
| Hide hands and items (`hide-hands`) | Boolean | Off | On / Off |
| Hide offhand item (`hide-offhand`) | Boolean | Off | On / Off |
| Hands and items scale (`hand-scale`) | Number | 1 | 0.3 to 1.5; step 0.05 |
| Main hand X (`main-x`) | Number | 0 | -1 to 1; step 0.05 |
| Main hand Y (`main-y`) | Number | 0 | -1 to 1; step 0.05 |
| Main hand Z (`main-z`) | Number | 0 | -1 to 1; step 0.05 |
| Main item size (`main-size`) | Number | 1 | 0.2 to 2.5; step 0.05 |
| Main item tilt (`main-tilt`) | Number | 0 | -180 to 180; step 5 |
| Main item turn (`main-turn`) | Number | 0 | -180 to 180; step 5 |
| Main item roll (`main-roll`) | Number | 0 | -180 to 180; step 5 |
| Offhand hand X (`off-x`) | Number | 0 | -1 to 1; step 0.05 |
| Offhand hand Y (`off-y`) | Number | 0 | -1 to 1; step 0.05 |
| Offhand hand Z (`off-z`) | Number | 0 | -1 to 1; step 0.05 |
| Offhand item size (`off-size`) | Number | 1 | 0.2 to 2.5; step 0.05 |
| Offhand item tilt (`off-tilt`) | Number | 0 | -180 to 180; step 5 |
| Offhand item turn (`off-turn`) | Number | 0 | -180 to 180; step 5 |
| Offhand item roll (`off-roll`) | Number | 0 | -180 to 180; step 5 |
| Item width (`stretch-x`) | Number | 1 | 0.2 to 3; step 0.05 |
| Item height (`stretch-y`) | Number | 1 | 0.2 to 3; step 0.05 |
| Item depth (`stretch-z`) | Number | 1 | 0.2 to 3; step 0.05 |

[Back to Cosmetics](#cosmetics)

<a id="module-crystal-chams"></a>

### Crystal Chams

**Cosmetics** · ID: `crystal-chams` · Default: **Off**

Tint the actual crystal model or replace it with an original Gem/Prism mesh. Normal depth remains.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Color mode (`color`) | Choice | Theme | Theme / Mint / Violet / Gold / Rainbow / Custom / Original |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Crystal model (`model`) | Choice | Vanilla | Vanilla / Gem / Prism |
| Opacity (`opacity`) | Number | 0.85 | 0.1 to 1; step 0.05 |
| Vanilla model fullbright (`glow`) | Boolean | On | On / Off |
| Orbit bands (`rings`) | Boolean | On | On / Off |
| Model size (`size`) | Number | 1 | 0.25 to 2.5; step 0.05 |
| Spin rate (`spin`) | Number | 1 | -3 to 3; step 0.05 |
| Bounce height (`bounce`) | Number | 1 | 0 to 2; step 0.05 |
| Crystal base (`base`) | Choice | Default | Default / Always / Never |

[Back to Cosmetics](#cosmetics)

<a id="module-cape"></a>

### Cape

**Cosmetics** · ID: `cape` · Default: **Off**

An original segmented cloth mesh with moving hems and colors, visible in third person. No image/GIF importer.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Color mode (`color`) | Choice | Theme | Theme / Mint / Violet / Gold / Rainbow / Custom |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Cape style (`style`) | Choice | Trimmed | Trimmed / Stripes / Gradient |
| Cape hem (`hem`) | Choice | Straight | Straight / Pointed / Swallowtail |
| Wearers (`who`) | Choice | Self | Self / Players / Both |
| Hide with Elytra (`hide-elytra`) | Boolean | On | On / Off |
| Hide Minecraft cape (`hide-vanilla`) | Boolean | On | On / Off |
| Cape length (`length`) | Number | 1 | 0.5 to 1.6; step 0.05 |
| Cape width (`width`) | Number | 1 | 0.5 to 1.6; step 0.05 |
| Flow (`flow`) | Number | 1 | 0 to 2; step 0.05 |
| Ripple (`ripple`) | Number | 0.5 | 0 to 2; step 0.05 |
| Wind (`wind`) | Number | 0.4 | 0 to 2; step 0.05 |
| Opacity (`opacity`) | Number | 0.9 | 0.2 to 1; step 0.05 |

[Back to Cosmetics](#cosmetics)

<a id="module-wings"></a>

### Wings

**Cosmetics** · ID: `wings` · Default: **Off**

Approved Angel, Dragon, Bat and Parrot textures on an existing articulated wing rig. Local third-person cosmetic; no flight or server broadcast.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Wing style (`style`) | Choice | Angel | Angel / Dragon / Bat / Parrot |
| Animation (`animation`) | Choice | Auto | Auto / Flap / Idle / Glide / Still |
| Animation speed (`speed`) | Number | 1 | 0 to 3; step 0.05 |
| Animation strength (`strength`) | Number | 1 | 0 to 1.5; step 0.05 |
| Wing scale (`scale`) | Number | 1 | 0.5 to 1.5; step 0.05 |
| Extra height (pixels) (`height`) | Number | 0 | -4 to 8; step 0.25 |
| Extra back offset (pixels) (`back-offset`) | Number | 0 | -2 to 6; step 0.25 |
| Hide with Elytra (`hide-elytra`) | Boolean | On | On / Off |
| Extra chest armor clearance (`armor-clearance`) | Boolean | On | On / Off |
| Fullbright texture (no bloom) (`fullbright`) | Boolean | Off | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-pets"></a>

### Pets

**Cosmetics** · ID: `pets` · Default: **Off**

Original animated Rune Orb, Ghost or Dragon geometry following your rendered body in third person.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Color mode (`color`) | Choice | Theme | Theme / Mint / Violet / Gold / Rainbow / Custom |
| Custom red (`red`) | Number | 121 | 0 to 255; step 1 |
| Custom green (`green`) | Number | 248 | 0 to 255; step 1 |
| Custom blue (`blue`) | Number | 204 | 0 to 255; step 1 |
| Companion (`pet`) | Choice | Rune Orb | Rune Orb / Ghost / Dragon |
| Pet position (`position`) | Choice | Shoulder | Shoulder / Orbit / Behind |
| Pet size (`size`) | Number | 1 | 0.5 to 2.5; step 0.05 |
| Pet animation speed (`speed`) | Number | 1 | 0 to 2; step 0.05 |
| Pet opacity (`opacity`) | Number | 0.85 | 0.2 to 1; step 0.05 |

[Back to Cosmetics](#cosmetics)

<a id="module-emotes"></a>

### Emotes

**Cosmetics** · ID: `emotes` · Default: **Off**

Pose your local player model in third person. No camera change or server-side animation.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Emote (`emote`) | Choice | Wave | Wave / Dance / Bow / Sit / Cheer |
| Loop (`loop`) | Boolean | Off | On / Off |
| Duration (seconds) (`length`) | Number | 4 | 1 to 20; step 0.5 |
| Stop when moving (`stop-moving`) | Boolean | On | On / Off |
| Pose strength (`strength`) | Number | 1 | 0.3 to 1.5; step 0.05 |

[Back to Cosmetics](#cosmetics)

<a id="module-walk-style"></a>

### Walk Style

**Cosmetics** · ID: `walk-style` · Default: **Off**

Alternative local third-person arm, leg and body poses using the existing player model.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Walking style (`style`) | Choice | Swagger | Swagger / Robot / Ninja / Zombie / Bouncy |
| Pose strength (`strength`) | Number | 1 | 0.3 to 1.5; step 0.05 |
| Only while walking (`only-moving`) | Boolean | On | On / Off |

[Back to Cosmetics](#cosmetics)

<a id="module-spinbot"></a>

### Spinbot

**Cosmetics** · ID: `spinbot` · Default: **Off**

Spin only your rendered player model. Camera, entity rotation and server packets are unchanged.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Yaw speed (degrees/tick) (`speed`) | Number | 15 | -180 to 180; step 5 |
| Rendered head pitch (`pitch`) | Number | 0 | -90 to 90; step 5 |

[Back to Cosmetics](#cosmetics)

<a id="module-kill-aura"></a>

### Kill Aura

**Combat** · ID: `kill-aura` · Default: **Off**

Choose filtered targets and attack with normal reach, bounded charged actions and camera or selectable server-only rotations.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Other living entities (`other-living`) | Boolean | Off | On / Off |
| Ignore saved friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible entities (`ignore-invisible`) | Boolean | Off | On / Off |
| Ignore players without armor (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore newly loaded entities (ticks) (`ignore-new`) | Number | 0 | 0 to 100; step 1 |
| Target range from eyes to hitbox (`range`) | Number | 3 | 1 to 8; step 0.25 |
| Target field of view (degrees) (`fov`) | Number | 180 | 10 to 360; step 10 |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Angle / Armor / Hurt time |
| Allow obstructed targets (`through-walls`) | Boolean | Off | On / Off |
| Obstructed target range (`wall-range`) | Number | 0 | 0 to 8; step 0.25 |
| Require visible crosshair / 120 degree cone (`legit`) | Boolean | Off | On / Off |
| Target mode (`mode`) | Choice | Single | Single / Switch / Multi |
| Switch delay (ms) (`switch-delay`) | Number | 250 | 50 to 1000; step 50 |
| Maximum targets per charged action (`max-targets`) | Number | 3 | 1 to 8; step 1 |
| Extra acquisition range (`aim-range`) | Number | 0 | 0 to 4; step 0.25 |
| Only acquire while holding attack (`only-click`) | Boolean | Off | On / Off |
| Prefer targets inside actual melee reach (`prefer-reachable`) | Boolean | On | On / Off |
| Rotation (`rotation`) | Choice | Client | Client / None / Silent movement / Silent packet / Silent pulse |
| Maximum turn (degrees/tick) (`turn-speed`) | Number | 12 | 1 to 180; step 1 |
| Slow down near aim point (%) (`smoothing`) | Number | 0 | 0 to 100; step 1 |
| Aim point (`aim-point`) | Choice | Head | Nearest / Chest / Head |
| Maximum attack aim error (degrees) (`hit-angle`) | Number | 90 | 1 to 180; step 1 |
| Attack timing (`timing`) | Choice | Cooldown | Cooldown / CPS |
| Required attack charge (`cooldown`) | Number | 0.95 | 0.2 to 1; step 0.05 |
| Random delay after charge (ms) (`attack-jitter`) | Number | 0 | 0 to 300; step 25 |
| Minimum CPS (`min-cps`) | Number | 9 | 1 to 20; step 1 |
| Maximum CPS (`max-cps`) | Number | 12 | 1 to 20; step 1 |
| Required charge in CPS mode (`hit-charge`) | Number | 0.95 | 0 to 1; step 0.05 |
| Wait for falling motion while airborne (`wait-for-crit`) | Boolean | Off | On / Off |
| Only attack with a melee weapon (`weapon-only`) | Boolean | Off | On / Off |
| Pause attacks while using an item (`pause-using`) | Boolean | On | On / Off |
| Pause while breaking a block (`pause-mining`) | Boolean | On | On / Off |
| Require active aim ray through target hitbox (`require-ray`) | Boolean | On | On / Off |
| Maximum target hurt time (ticks) (`hurt-time`) | Number | 10 | 0 to 10; step 1 |
| Wait after changing held weapon (ticks) (`settle-ticks`) | Number | 1 | 0 to 10; step 1 |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Wait after first silent look (ticks) (`silent-settle`) | Number | 1 | 0 to 10; step 1 |
| Explicit look refresh (ticks) (`silent-refresh`) | Number | 1 | 1 to 20; step 1 |

[Back to Combat](#combat)

<a id="module-aim-assist"></a>

### Aim Assist

**Combat** · ID: `aim-assist` · Default: **Off**

Pull your actual camera toward a filtered target with configurable aim point, strength and smoothing.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Other living entities (`other-living`) | Boolean | Off | On / Off |
| Ignore saved friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible entities (`ignore-invisible`) | Boolean | Off | On / Off |
| Ignore players without armor (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore newly loaded entities (ticks) (`ignore-new`) | Number | 0 | 0 to 100; step 1 |
| Target range from eyes to hitbox (`range`) | Number | 6 | 1 to 8; step 0.25 |
| Target field of view (degrees) (`fov`) | Number | 180 | 10 to 360; step 10 |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Angle / Armor / Hurt time |
| Allow obstructed targets (`through-walls`) | Boolean | Off | On / Off |
| Obstructed target range (`wall-range`) | Number | 0 | 0 to 8; step 0.25 |
| Rotation (`rotation`) | Choice | Client | Client / None |
| Maximum turn (degrees/tick) (`turn-speed`) | Number | 12 | 1 to 180; step 1 |
| Slow down near aim point (%) (`smoothing`) | Number | 0 | 0 to 100; step 1 |
| Aim point (`aim-point`) | Choice | Head | Nearest / Chest / Head |
| Visible gradual assistance while clicking (`legit`) | Boolean | Off | On / Off |
| Only while holding attack (`hold-attack`) | Boolean | On | On / Off |
| Only while holding a melee weapon (`weapon-only`) | Boolean | Off | On / Off |
| Pause while using an item (`pause-using`) | Boolean | On | On / Off |
| Pause while breaking a block (`pause-mining`) | Boolean | On | On / Off |
| Aim strength (%) (`strength`) | Number | 100 | 1 to 100; step 1 |

[Back to Combat](#combat)

<a id="module-trigger-bot"></a>

### Trigger Bot

**Combat** · ID: `trigger-bot` · Default: **Off**

Attack a filtered crosshair target after its reaction delay, normal reach and selected charge timing.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Other living entities (`other-living`) | Boolean | Off | On / Off |
| Ignore saved friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible entities (`ignore-invisible`) | Boolean | Off | On / Off |
| Ignore players without armor (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore newly loaded entities (ticks) (`ignore-new`) | Number | 0 | 0 to 100; step 1 |
| Target range from eyes to hitbox (`range`) | Number | 3 | 1 to 8; step 0.25 |
| Target field of view (degrees) (`fov`) | Number | 180 | 10 to 360; step 10 |
| Rotation (`rotation`) | Choice | Client | Client / None |
| Maximum turn (degrees/tick) (`turn-speed`) | Number | 12 | 1 to 180; step 1 |
| Slow down near aim point (%) (`smoothing`) | Number | 0 | 0 to 100; step 1 |
| Aim point (`aim-point`) | Choice | Head | Nearest / Chest / Head |
| Maximum attack aim error (degrees) (`hit-angle`) | Number | 90 | 1 to 180; step 1 |
| Attack timing (`timing`) | Choice | Cooldown | Cooldown / CPS |
| Required attack charge (`cooldown`) | Number | 0.95 | 0.2 to 1; step 0.05 |
| Random delay after charge (ms) (`attack-jitter`) | Number | 0 | 0 to 300; step 25 |
| Minimum CPS (`min-cps`) | Number | 9 | 1 to 20; step 1 |
| Maximum CPS (`max-cps`) | Number | 12 | 1 to 20; step 1 |
| Required charge in CPS mode (`hit-charge`) | Number | 0.95 | 0 to 1; step 0.05 |
| Wait for falling motion while airborne (`wait-for-crit`) | Boolean | Off | On / Off |
| Only attack with a melee weapon (`weapon-only`) | Boolean | Off | On / Off |
| Pause attacks while using an item (`pause-using`) | Boolean | On | On / Off |
| Pause while breaking a block (`pause-mining`) | Boolean | On | On / Off |
| Require actual camera ray through target hitbox (`require-ray`) | Boolean | On | On / Off |
| Maximum target hurt time (ticks) (`hurt-time`) | Number | 10 | 0 to 10; step 1 |
| Wait after changing held weapon (ticks) (`settle-ticks`) | Number | 1 | 0 to 10; step 1 |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Reaction delay and varied charged attacks (`legit`) | Boolean | Off | On / Off |
| Crosshair reaction delay (ms) (`reaction-delay`) | Number | 0 | 0 to 1000; step 25 |

[Back to Combat](#combat)

<a id="module-auto-clicker"></a>

### Auto Clicker

**Combat** · ID: `auto-clicker` · Default: **Off**

Repeat held entity attacks or ordinary item/block use with bounded steady, random or paired timing.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Other living entities (`other-living`) | Boolean | Off | On / Off |
| Ignore saved friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible entities (`ignore-invisible`) | Boolean | Off | On / Off |
| Ignore players without armor (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore newly loaded entities (ticks) (`ignore-new`) | Number | 0 | 0 to 100; step 1 |
| Target range from eyes to hitbox (`range`) | Number | 3 | 1 to 8; step 0.25 |
| Target field of view (degrees) (`fov`) | Number | 180 | 10 to 360; step 10 |
| Rotation (`rotation`) | Choice | Client | Client / None |
| Maximum turn (degrees/tick) (`turn-speed`) | Number | 12 | 1 to 180; step 1 |
| Slow down near aim point (%) (`smoothing`) | Number | 0 | 0 to 100; step 1 |
| Aim point (`aim-point`) | Choice | Head | Nearest / Chest / Head |
| Maximum attack aim error (degrees) (`hit-angle`) | Number | 90 | 1 to 180; step 1 |
| Attack timing (`timing`) | Choice | CPS | Cooldown / CPS |
| Required attack charge (`cooldown`) | Number | 0.95 | 0.2 to 1; step 0.05 |
| Random delay after charge (ms) (`attack-jitter`) | Number | 0 | 0 to 300; step 25 |
| Minimum CPS (`min-cps`) | Number | 8 | 1 to 20; step 1 |
| Maximum CPS (`max-cps`) | Number | 8 | 1 to 20; step 1 |
| Required charge in CPS mode (`hit-charge`) | Number | 0.95 | 0 to 1; step 0.05 |
| Wait for falling motion while airborne (`wait-for-crit`) | Boolean | Off | On / Off |
| Only attack with a melee weapon (`weapon-only`) | Boolean | Off | On / Off |
| Pause attacks while using an item (`pause-using`) | Boolean | On | On / Off |
| Pause while breaking a block (`pause-mining`) | Boolean | On | On / Off |
| Require actual camera ray through target hitbox (`require-ray`) | Boolean | On | On / Off |
| Maximum target hurt time (ticks) (`hurt-time`) | Number | 10 | 0 to 10; step 1 |
| Wait after changing held weapon (ticks) (`settle-ticks`) | Number | 1 | 0 to 10; step 1 |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Vary clicks, cap at 12 CPS and occasionally skip (`legit`) | Boolean | Off | On / Off |
| Repeated button (`button`) | Choice | Left | Left / Right / Both |
| Maximum CPS cap (`cps`) | Number | 8 | 1 to 20; step 1 |
| Left-attack click pattern (`pattern`) | Choice | Steady | Steady / Random / Butterfly |
| Wait for the selected attack charge (`respect-cooldown`) | Boolean | On | On / Off |

[Back to Combat](#combat)

<a id="module-auto-weapon"></a>

### Auto Weapon

**Combat** · ID: `auto-weapon` · Default: **Off**

Temporarily choose allowed hotbar melee weapons by material estimate or durability after a reaction delay.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Crosshair-only delayed switch; no same-tick attack (`legit`) | Boolean | Off | On / Off |
| Allow swords (`swords`) | Boolean | On | On / Off |
| Allow axes (`axes`) | Boolean | On | On / Off |
| Allow maces (`maces`) | Boolean | On | On / Off |
| Allow tridents (`tridents`) | Boolean | On | On / Off |
| Weapon preference (`preference`) | Choice | Damage estimate | Damage estimate / Sword first / Axe first / Durability |
| Weapon switch delay (ms) (`reaction-delay`) | Number | 0 | 0 to 1000; step 25 |
| Wait after switching weapon (ticks) (`settle-ticks`) | Number | 1 | 0 to 10; step 1 |

[Back to Combat](#combat)

<a id="module-shield-breaker"></a>

### Shield Breaker

**Combat** · ID: `shield-breaker` · Default: **Off**

Hold a usable hotbar axe for a blocking crosshair target, with reaction and release delays.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Delayed axe switch before attacking (`legit`) | Boolean | Off | On / Off |
| Ignore saved friends (`ignore-friends`) | Boolean | On | On / Off |
| Blocking-target reaction delay (ms) (`reaction-delay`) | Number | 0 | 0 to 1000; step 25 |
| Keep axe after blocking ends (ms) (`release-delay`) | Number | 0 | 0 to 1000; step 25 |
| Wait after switching axe (ticks) (`settle-ticks`) | Number | 1 | 0 to 10; step 1 |
| Only break shields while holding attack (`only-click`) | Boolean | Off | On / Off |
| Pause while using an item (`pause-using`) | Boolean | On | On / Off |
| Pause while breaking a block (`pause-mining`) | Boolean | On | On / Off |
| Required attack charge (`cooldown`) | Number | 0.95 | 0.2 to 1; step 0.05 |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |

[Back to Combat](#combat)

<a id="module-bow-aimbot"></a>

### Bow Aimbot

**Combat** · ID: `bow-aimbot` · Default: **Off**

Aim the actual camera for selected drawn bows, charged crossbows or tridents using bounded lead/drop estimates.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Other living entities (`other-living`) | Boolean | Off | On / Off |
| Ignore saved friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible entities (`ignore-invisible`) | Boolean | Off | On / Off |
| Ignore players without armor (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore newly loaded entities (ticks) (`ignore-new`) | Number | 0 | 0 to 100; step 1 |
| Target range from eyes to hitbox (`range`) | Number | 48 | 1 to 120; step 0.25 |
| Target field of view (degrees) (`fov`) | Number | 180 | 10 to 360; step 10 |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Angle / Armor / Hurt time |
| Allow obstructed targets (`through-walls`) | Boolean | Off | On / Off |
| Obstructed target range (`wall-range`) | Number | 0 | 0 to 120; step 0.25 |
| Rotation (`rotation`) | Choice | Client | Client / None |
| Maximum turn (degrees/tick) (`turn-speed`) | Number | 12 | 1 to 180; step 1 |
| Slow down near aim point (%) (`smoothing`) | Number | 0 | 0 to 100; step 1 |
| Aim point (`aim-point`) | Choice | Head | Nearest / Chest / Head |
| Aim drawn bows (`bows`) | Boolean | On | On / Off |
| Aim held charged crossbows before firing (`crossbows`) | Boolean | Off | On / Off |
| Aim drawn tridents (`tridents`) | Boolean | Off | On / Off |
| Lead target velocity (`predict-movement`) | Boolean | On | On / Off |
| Projectile compensation (`prediction`) | Choice | Lead and drop | Direct / Lead / Lead and drop |
| Target velocity lead multiplier (`lead-factor`) | Number | 1 | 0 to 2; step 0.05 |
| Minimum bow / trident draw fraction (`min-charge`) | Number | 0 | 0 to 1; step 0.05 |

[Back to Combat](#combat)

<a id="module-criticals"></a>

### Criticals

**Combat** · ID: `criticals` · Default: **Off**

Use real jumps or your own falling motion to time ordinary charged attacks; no packet critical spoofing.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Other living entities (`other-living`) | Boolean | Off | On / Off |
| Ignore saved friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible entities (`ignore-invisible`) | Boolean | Off | On / Off |
| Ignore players without armor (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore newly loaded entities (ticks) (`ignore-new`) | Number | 0 | 0 to 100; step 1 |
| Target range from eyes to hitbox (`range`) | Number | 3 | 1 to 8; step 0.25 |
| Target field of view (degrees) (`fov`) | Number | 180 | 10 to 360; step 10 |
| Rotation (`rotation`) | Choice | Client | Client / None |
| Maximum turn (degrees/tick) (`turn-speed`) | Number | 12 | 1 to 180; step 1 |
| Slow down near aim point (%) (`smoothing`) | Number | 0 | 0 to 100; step 1 |
| Aim point (`aim-point`) | Choice | Head | Nearest / Chest / Head |
| Maximum attack aim error (degrees) (`hit-angle`) | Number | 90 | 1 to 180; step 1 |
| Attack timing (`timing`) | Choice | Cooldown | Cooldown / CPS |
| Required attack charge (`cooldown`) | Number | 0.95 | 0.2 to 1; step 0.05 |
| Random delay after charge (ms) (`attack-jitter`) | Number | 0 | 0 to 300; step 25 |
| Minimum CPS (`min-cps`) | Number | 9 | 1 to 20; step 1 |
| Maximum CPS (`max-cps`) | Number | 12 | 1 to 20; step 1 |
| Required charge in CPS mode (`hit-charge`) | Number | 0.95 | 0 to 1; step 0.05 |
| Only attack with a melee weapon (`weapon-only`) | Boolean | Off | On / Off |
| Pause attacks while using an item (`pause-using`) | Boolean | On | On / Off |
| Pause while breaking a block (`pause-mining`) | Boolean | On | On / Off |
| Require actual camera ray through target hitbox (`require-ray`) | Boolean | On | On / Off |
| Maximum target hurt time (ticks) (`hurt-time`) | Number | 10 | 0 to 10; step 1 |
| Wait after changing held weapon (ticks) (`settle-ticks`) | Number | 1 | 0 to 10; step 1 |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Wait for your own normal jump (`legit`) | Boolean | Off | On / Off |
| Critical movement (`mode`) | Choice | Jump | Jump / Mini jump / Manual fall |
| Apply critical timing to (`crit-targets`) | Choice | Living | Living / Players / Hostiles |
| Minimum charge for critical timing (`min-charge`) | Number | 0.9 | 0 to 1; step 0.05 |
| Only apply to Kill Aura attacks (`only-kill-aura`) | Boolean | Off | On / Off |
| Mini jump upward velocity (`hop-height`) | Number | 0.2 | 0.08 to 0.42; step 0.01 |
| Minimum time between automatic hops (ms) (`min-interval`) | Number | 0 | 0 to 1000; step 50 |
| Maximum waiting time for a falling hit (ticks) (`hold-time`) | Number | 14 | 4 to 30; step 1 |
| Allow automatic hops while moving (`hop-while-moving`) | Boolean | On | On / Off |
| Release sprint normally while waiting for a crit (`sprint-reset`) | Boolean | On | On / Off |
| Show local critical cue on the falling attack (`crit-particles`) | Boolean | Off | On / Off |

[Back to Combat](#combat)

<a id="module-auto-block"></a>

### Auto Block

**Combat** · ID: `auto-block` · Default: **Off**

Raise an equipped offhand shield toward a visible threat between attacks; release it before ordinary melee attacks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Ignore saved friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible entities (`ignore-invisible`) | Boolean | Off | On / Off |
| Ignore players without armor (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore newly loaded entities (ticks) (`ignore-new`) | Number | 0 | 0 to 100; step 1 |
| Target range from eyes to hitbox (`range`) | Number | 4 | 1 to 8; step 0.25 |
| Target field of view (degrees) (`fov`) | Number | 180 | 10 to 360; step 10 |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Angle / Armor / Hurt time |
| Rotation (`rotation`) | Choice | Client | Client / None |
| Maximum turn (degrees/tick) (`turn-speed`) | Number | 18 | 1 to 180; step 1 |
| Slow down near aim point (%) (`smoothing`) | Number | 0 | 0 to 100; step 1 |
| Raise shield at or below health (`health`) | Number | 20 | 1 to 36; step 1 |
| Keep shield after threat leaves (ticks) (`release-delay`) | Number | 4 | 0 to 20; step 1 |
| Face the selected visible threat (`face-threat`) | Boolean | On | On / Off |
| Yield while combat placement modules are enabled (`yield-hvh`) | Boolean | On | On / Off |
| Pause while breaking a block (`pause-mining`) | Boolean | On | On / Off |

[Back to Combat](#combat)

<a id="module-double-hand"></a>

### Double Hand

**Combat** · ID: `double-hand` · Default: **Off**

Temporarily hold a hotbar totem after a real server-reported pop or at low health near a visible threat; yields to Auto Totem once the offhand is protected.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Ignore saved friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible entities (`ignore-invisible`) | Boolean | Off | On / Off |
| Ignore players without armor (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore newly loaded entities (ticks) (`ignore-new`) | Number | 0 | 0 to 100; step 1 |
| Target range from eyes to hitbox (`range`) | Number | 6 | 1 to 12; step 0.25 |
| Target field of view (degrees) (`fov`) | Number | 180 | 10 to 360; step 10 |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Angle / Armor / Hurt time |
| Hold main-hand totem at or below health (`health`) | Number | 10 | 1 to 36; step 1 |
| Protect when (`mode`) | Choice | Either | Low health / After totem pop / Either |
| Only when offhand has no totem (`without-offhand`) | Boolean | On | On / Off |
| Keep totem after danger ends (ticks) (`hold-ticks`) | Number | 10 | 0 to 60; step 1 |

[Back to Combat](#combat)

<a id="module-hover-totem"></a>

### Hover Totem

**Combat** · ID: `hover-totem` · Default: **Off**

In your own inventory, hold the selected key over a totem to move it into the offhand through a normal guarded slot swap.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Hold key while hovering (`hold-key`) | Choice | Sneak | Sneak / Use / None |
| Hover before swapping (ticks) (`hover-ticks`) | Number | 3 | 0 to 20; step 1 |
| Minimum interval between swaps (ticks) (`delay`) | Number | 6 | 1 to 40; step 1 |

[Back to Combat](#combat)

<a id="module-crystal-aura"></a>

### Crystal Aura

**HVH** · ID: `crystal-aura` · Default: **Off**

Score reachable crystals with loaded-world explosion exposure and conservative self/friend damage guards.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Target range (blocks) (`range`) | Number | 6 | 1 to 8; step 0.25 |
| Field of view (degrees) (`fov`) | Number | 180 | 20 to 360; step 10 |
| Ignore unarmored players (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible targets (`ignore-invisible`) | Boolean | Off | On / Off |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Armor |
| Target through walls (`through-walls`) | Boolean | Off | On / Off |
| Minimum action interval (ticks) (`delay`) | Number | 4 | 1 to 20; step 1 |
| Place crystals (`place`) | Boolean | On | On / Off |
| Break crystals (`break`) | Boolean | On | On / Off |
| Break cooldown threshold (`cooldown`) | Number | 0.95 | 0.2 to 1; step 0.05 |
| Minimum self distance (blocks) (`self-distance`) | Number | 3 | 2 to 6; step 0.25 |
| Minimum health (HP) (`health`) | Number | 12 | 1 to 20; step 1 |
| Placement interval floor (ticks) (`place-delay`) | Number | 1 | 0 to 20; step 1 |
| Break interval floor (ticks) (`break-delay`) | Number | 1 | 0 to 20; step 1 |
| Crystal break range (blocks) (`break-range`) | Number | 4.5 | 1 to 6; step 0.25 |
| Break which crystals (`break-which`) | Choice | Smart | Smart / Own / All |
| Face place below enemy health (HP; 0 off) (`face-place-health`) | Number | 8 | 0 to 36; step 1 |
| Face place enemies in holes (`face-place-hole`) | Boolean | Off | On / Off |
| Placement reach cap (blocks) (`place-range`) | Number | 4.5 | 1 to 6; step 0.25 |
| Require real crosshair + vanilla reach (`legit`) | Boolean | Off | On / Off |
| Turn real camera to interaction (`rotate`) | Boolean | Off | On / Off |
| Camera turn speed (degrees/tick) (`aim-speed`) | Number | 30 | 5 to 90; step 1 |
| Aim smoothing (%) (`aim-smoothing`) | Number | 40 | 0 to 100; step 5 |
| Hotbar switch (Silent = visible switch/back) (`switch`) | Choice | Silent | None / Normal / Silent |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Pause while using an item (`pause-eating`) | Boolean | On | On / Off |
| Pause at health (HP; 0 off) (`pause-health`) | Number | 0 | 0 to 20; step 1 |
| Show pending / accepted placement preview (`render`) | Boolean | On | On / Off |
| Preview red (`red`) | Number | 232 | 0 to 255; step 1 |
| Preview green (`green`) | Number | 200 | 0 to 255; step 1 |
| Preview blue (`blue`) | Number | 114 | 0 to 255; step 1 |
| Minimum predicted enemy damage (HP) (`min-damage`) | Number | 6 | 0 to 36; step 0.5 |
| Maximum conservative self damage (HP) (`max-self-damage`) | Number | 8 | 0 to 36; step 0.5 |
| Require health above self damage (`anti-suicide`) | Boolean | On | On / Off |
| Reject blasts near friends (`protect-friends`) | Boolean | On | On / Off |

[Back to HVH](#hvh)

<a id="module-anchor-aura"></a>

### Anchor Aura

**HVH** · ID: `anchor-aura` · Default: **Off**

Place, charge and detonate reachable anchors outside anchor-safe dimensions, with exposure damage guards.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Target range (blocks) (`range`) | Number | 6 | 1 to 8; step 0.25 |
| Field of view (degrees) (`fov`) | Number | 180 | 20 to 360; step 10 |
| Ignore unarmored players (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible targets (`ignore-invisible`) | Boolean | Off | On / Off |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Armor |
| Target through walls (`through-walls`) | Boolean | Off | On / Off |
| Action interval (ticks) (`delay`) | Number | 4 | 1 to 20; step 1 |
| Minimum self distance (blocks) (`self-distance`) | Number | 3.5 | 2 to 6; step 0.25 |
| Minimum health (HP) (`health`) | Number | 12 | 1 to 20; step 1 |
| Use existing anchors (`use-existing`) | Boolean | On | On / Off |
| Placement reach cap (blocks) (`place-range`) | Number | 4.5 | 1 to 6; step 0.25 |
| Require real crosshair + vanilla reach (`legit`) | Boolean | Off | On / Off |
| Turn real camera to interaction (`rotate`) | Boolean | Off | On / Off |
| Camera turn speed (degrees/tick) (`aim-speed`) | Number | 30 | 5 to 90; step 1 |
| Aim smoothing (%) (`aim-smoothing`) | Number | 40 | 0 to 100; step 5 |
| Hotbar switch (Silent = visible switch/back) (`switch`) | Choice | Silent | None / Normal / Silent |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Pause while using an item (`pause-eating`) | Boolean | On | On / Off |
| Pause at health (HP; 0 off) (`pause-health`) | Number | 0 | 0 to 20; step 1 |
| Show pending / accepted placement preview (`render`) | Boolean | On | On / Off |
| Preview red (`red`) | Number | 232 | 0 to 255; step 1 |
| Preview green (`green`) | Number | 200 | 0 to 255; step 1 |
| Preview blue (`blue`) | Number | 114 | 0 to 255; step 1 |
| Minimum predicted enemy damage (HP) (`min-damage`) | Number | 7 | 0 to 36; step 0.5 |
| Maximum conservative self damage (HP) (`max-self-damage`) | Number | 7 | 0 to 36; step 0.5 |
| Require health above self damage (`anti-suicide`) | Boolean | On | On / Off |
| Reject blasts near friends (`protect-friends`) | Boolean | On | On / Off |

[Back to HVH](#hvh)

<a id="module-surround"></a>

### Surround

**HVH** · ID: `surround` · Default: **Off**

Build a supported configurable shell around your occupied blocks using normal placement.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Placement interval (ticks) (`delay`) | Number | 3 | 1 to 20; step 1 |
| Build lower supports (`support`) | Boolean | On | On / Off |
| Surround shape (`shape`) | Choice | Normal | Normal / Double / Anti-city |
| Ease toward block center (`center`) | Boolean | On | On / Off |
| Fill missing floor (`floor`) | Boolean | On | On / Off |
| Surround every occupied block (`extend`) | Boolean | On | On / Off |
| Cover above your head (`self-trap`) | Boolean | Off | On / Off |
| Disable on upward jump (`off-jump`) | Boolean | On | On / Off |
| Disable after leaving start block (`off-moved`) | Boolean | On | On / Off |
| Disable when complete (`off-done`) | Boolean | Off | On / Off |
| Placement reach cap (blocks) (`place-range`) | Number | 4.5 | 1 to 6; step 0.25 |
| Require real crosshair + vanilla reach (`legit`) | Boolean | Off | On / Off |
| Turn real camera to interaction (`rotate`) | Boolean | Off | On / Off |
| Camera turn speed (degrees/tick) (`aim-speed`) | Number | 35 | 5 to 90; step 1 |
| Aim smoothing (%) (`aim-smoothing`) | Number | 40 | 0 to 100; step 5 |
| Hotbar switch (Silent = visible switch/back) (`switch`) | Choice | Silent | None / Normal / Silent |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Pause while using an item (`pause-eating`) | Boolean | On | On / Off |
| Pause at health (HP; 0 off) (`pause-health`) | Number | 0 | 0 to 20; step 1 |
| Show pending / accepted placement preview (`render`) | Boolean | On | On / Off |
| Preview red (`red`) | Number | 232 | 0 to 255; step 1 |
| Preview green (`green`) | Number | 200 | 0 to 255; step 1 |
| Preview blue (`blue`) | Number | 114 | 0 to 255; step 1 |
| Placements per tick (shared cap 8) (`blocks-per-tick`) | Number | 1 | 1 to 8; step 1 |
| Building material (`block`) | Choice | Obsidian | Obsidian / Ender chest / Any hard / Any |

[Back to HVH](#hvh)

<a id="module-auto-trap"></a>

### Auto Trap

**HVH** · ID: `auto-trap` · Default: **Off**

Build a supported roof, head shell or full cage around a selected target.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Target range (blocks) (`range`) | Number | 6 | 1 to 8; step 0.25 |
| Field of view (degrees) (`fov`) | Number | 180 | 20 to 360; step 10 |
| Ignore unarmored players (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible targets (`ignore-invisible`) | Boolean | Off | On / Off |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Armor |
| Target through walls (`through-walls`) | Boolean | Off | On / Off |
| Action interval (ticks) (`delay`) | Number | 4 | 1 to 20; step 1 |
| Trap shape (`shape`) | Choice | Full + roof | Top / Head / Full / Full + roof |
| Only targets in holes (`only-hole`) | Boolean | Off | On / Off |
| Disable when cage complete (`off-done`) | Boolean | Off | On / Off |
| Placement reach cap (blocks) (`place-range`) | Number | 4.5 | 1 to 6; step 0.25 |
| Require real crosshair + vanilla reach (`legit`) | Boolean | Off | On / Off |
| Turn real camera to interaction (`rotate`) | Boolean | Off | On / Off |
| Camera turn speed (degrees/tick) (`aim-speed`) | Number | 35 | 5 to 90; step 1 |
| Aim smoothing (%) (`aim-smoothing`) | Number | 40 | 0 to 100; step 5 |
| Hotbar switch (Silent = visible switch/back) (`switch`) | Choice | Silent | None / Normal / Silent |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Pause while using an item (`pause-eating`) | Boolean | On | On / Off |
| Pause at health (HP; 0 off) (`pause-health`) | Number | 0 | 0 to 20; step 1 |
| Show pending / accepted placement preview (`render`) | Boolean | On | On / Off |
| Preview red (`red`) | Number | 232 | 0 to 255; step 1 |
| Preview green (`green`) | Number | 200 | 0 to 255; step 1 |
| Preview blue (`blue`) | Number | 114 | 0 to 255; step 1 |
| Placements per tick (shared cap 8) (`blocks-per-tick`) | Number | 1 | 1 to 8; step 1 |
| Building material (`block`) | Choice | Obsidian | Obsidian / Ender chest / Any hard / Any |

[Back to HVH](#hvh)

<a id="module-auto-web"></a>

### Auto Web

**HVH** · ID: `auto-web` · Default: **Off**

Place supported feet/head webs on selected enemies within reach and a per-tick budget.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Target range (blocks) (`range`) | Number | 6 | 1 to 8; step 0.25 |
| Field of view (degrees) (`fov`) | Number | 180 | 20 to 360; step 10 |
| Ignore unarmored players (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible targets (`ignore-invisible`) | Boolean | Off | On / Off |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Armor |
| Target through walls (`through-walls`) | Boolean | Off | On / Off |
| Action interval (ticks) (`delay`) | Number | 4 | 1 to 20; step 1 |
| Web target feet (`feet`) | Boolean | On | On / Off |
| Web target head (`head`) | Boolean | Off | On / Off |
| Web every selected enemy (`every-enemy`) | Boolean | Off | On / Off |
| Only moving targets (`moving-only`) | Boolean | Off | On / Off |
| Moving threshold (blocks/tick) (`motion-threshold`) | Number | 0.03 | 0.01 to 0.3; step 0.01 |
| Placement reach cap (blocks) (`place-range`) | Number | 4.5 | 1 to 6; step 0.25 |
| Require real crosshair + vanilla reach (`legit`) | Boolean | Off | On / Off |
| Turn real camera to interaction (`rotate`) | Boolean | Off | On / Off |
| Camera turn speed (degrees/tick) (`aim-speed`) | Number | 35 | 5 to 90; step 1 |
| Aim smoothing (%) (`aim-smoothing`) | Number | 40 | 0 to 100; step 5 |
| Hotbar switch (Silent = visible switch/back) (`switch`) | Choice | Silent | None / Normal / Silent |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Pause while using an item (`pause-eating`) | Boolean | On | On / Off |
| Pause at health (HP; 0 off) (`pause-health`) | Number | 0 | 0 to 20; step 1 |
| Show pending / accepted placement preview (`render`) | Boolean | On | On / Off |
| Preview red (`red`) | Number | 232 | 0 to 255; step 1 |
| Preview green (`green`) | Number | 200 | 0 to 255; step 1 |
| Preview blue (`blue`) | Number | 114 | 0 to 255; step 1 |
| Placements per tick (shared cap 8) (`blocks-per-tick`) | Number | 1 | 1 to 8; step 1 |

[Back to HVH](#hvh)

<a id="module-hole-filler"></a>

### Hole Filler

**HVH** · ID: `hole-filler` · Default: **Off**

Scan loaded nearby supported single/double holes and fill relevant unoccupied holes.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Search range (blocks) (`range`) | Number | 4 | 1 to 5; step 0.5 |
| Placement interval (ticks) (`delay`) | Number | 4 | 1 to 20; step 1 |
| Only holes near enemies (`near-enemies`) | Boolean | On | On / Off |
| Enemy distance to hole (blocks) (`enemy-distance`) | Number | 3 | 1 to 8; step 0.25 |
| Keep holes beside you (`keep-yours`) | Boolean | On | On / Off |
| Include two-block holes (`double-holes`) | Boolean | On | On / Off |
| Scan interval (ticks) (`scan-delay`) | Number | 4 | 1 to 20; step 1 |
| Keep holes beside friends (`protect-friends`) | Boolean | On | On / Off |
| Placement reach cap (blocks) (`place-range`) | Number | 4.5 | 1 to 6; step 0.25 |
| Require real crosshair + vanilla reach (`legit`) | Boolean | Off | On / Off |
| Turn real camera to interaction (`rotate`) | Boolean | Off | On / Off |
| Camera turn speed (degrees/tick) (`aim-speed`) | Number | 35 | 5 to 90; step 1 |
| Aim smoothing (%) (`aim-smoothing`) | Number | 40 | 0 to 100; step 5 |
| Hotbar switch (Silent = visible switch/back) (`switch`) | Choice | Silent | None / Normal / Silent |
| Swing animation (`swing`) | Choice | Client | Client / Packet / None |
| Pause while using an item (`pause-eating`) | Boolean | On | On / Off |
| Pause at health (HP; 0 off) (`pause-health`) | Number | 0 | 0 to 20; step 1 |
| Show pending / accepted placement preview (`render`) | Boolean | On | On / Off |
| Preview red (`red`) | Number | 232 | 0 to 255; step 1 |
| Preview green (`green`) | Number | 200 | 0 to 255; step 1 |
| Preview blue (`blue`) | Number | 114 | 0 to 255; step 1 |
| Placements per tick (shared cap 8) (`blocks-per-tick`) | Number | 1 | 1 to 8; step 1 |
| Building material (`block`) | Choice | Obsidian | Obsidian / Ender chest / Any hard / Any |

[Back to HVH](#hvh)

<a id="module-target-strafe"></a>

### Target Strafe

**HVH** · ID: `target-strafe` · Default: **Off**

Orbit a selected target with checked support, collision, ordinary hops and optional camera/input steering.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Players (`players`) | Boolean | On | On / Off |
| Hostile mobs (`hostiles`) | Boolean | Off | On / Off |
| Passive mobs (`passives`) | Boolean | Off | On / Off |
| Target range (blocks) (`range`) | Number | 6 | 1 to 8; step 0.25 |
| Field of view (degrees) (`fov`) | Number | 180 | 20 to 360; step 10 |
| Ignore unarmored players (`ignore-naked`) | Boolean | Off | On / Off |
| Ignore friends (`ignore-friends`) | Boolean | On | On / Off |
| Ignore invisible targets (`ignore-invisible`) | Boolean | Off | On / Off |
| Target priority (`priority`) | Choice | Distance | Distance / Health / Armor |
| Target through walls (`through-walls`) | Boolean | Off | On / Off |
| Orbit radius (blocks) (`radius`) | Number | 2.5 | 1 to 5; step 0.25 |
| Horizontal speed cap (blocks/tick) (`speed`) | Number | 0.22 | 0.05 to 0.6; step 0.01 |
| Clockwise (`clockwise`) | Boolean | On | On / Off |
| Strafe style (`style`) | Choice | Circle | Circle / Vanilla hop / Blind hop / Hug |
| Direction override (`direction`) | Choice | Saved clockwise | Saved clockwise / Right / Left / Flip |
| A/D direction and S pause (`respect-wasd`) | Boolean | On | On / Off |
| Steer only on ground (`ground-only`) | Boolean | On | On / Off |
| Jump while orbiting (`auto-jump`) | Boolean | Off | On / Off |
| Ease at normal movement pace (`natural-pace`) | Boolean | On | On / Off |
| Use sprint pace (`sprint`) | Boolean | Off | On / Off |
| Hug distance (blocks) (`hug-distance`) | Number | 0.4 | 0.2 to 1.5; step 0.05 |
| Reverse direction after landing (`weave`) | Boolean | On | On / Off |
| Blind hop arc (degrees) (`hop-arc`) | Number | 55 | 20 to 120; step 5 |
| Avoid target front cone (degrees) (`view-cone`) | Number | 120 | 30 to 180; step 5 |
| Direction stability (%) (`direction-stability`) | Number | 20 | 0 to 100; step 5 |
| Camera + ordinary forward input (`legit`) | Boolean | Off | On / Off |
| Camera turn speed (degrees/tick) (`aim-speed`) | Number | 30 | 5 to 90; step 1 |

[Back to HVH](#hvh)

<a id="module-keep-sprint"></a>

### Keep Sprint

**Movement** · ID: `keep-sprint` · Default: **Off**

Keep sprinting while vanilla movement and hunger conditions permit.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Use the normal sprint key (`legit`) | Boolean | On | On / Off |
| Sprint above hunger (`min-hunger`) | Number | 6 | 0 to 20; step 1 |
| Pause while using items (`pause-on-use`) | Boolean | On | On / Off |
| Only sprint on the ground (`ground-only`) | Boolean | Off | On / Off |

[Back to Movement](#movement)

<a id="module-auto-walk"></a>

### Auto Walk

**Movement** · ID: `auto-walk` · Default: **Off**

Hold a chosen movement key while the world is focused; input is released on pause or disable.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Direction (`direction`) | Choice | Forward | Forward / Backward / Left / Right |
| Sprint when walking forward (`sprint`) | Boolean | Off | On / Off |
| Hold jump (`jump`) | Boolean | Off | On / Off |
| Stop at a wall (`stop-on-collision`) | Boolean | Off | On / Off |

[Back to Movement](#movement)

<a id="module-speed"></a>

### Speed

**Movement** · ID: `speed` · Default: **Off**

Choose direct motion, a grounded conservative profile, or normal sprint jumps.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Use normal sprint and jump keys (`legit`) | Boolean | Off | On / Off |
| Profile (`profile`) | Choice | Standard | Standard / Conservative / Sprint hop |
| Base speed (blocks/tick) (`speed`) | Number | 0.35 | 0.1 to 1; step 0.01 |
| Multiplier (direct profiles) (`multiplier`) | Number | 1 | 1 to 4; step 0.1 |

[Back to Movement](#movement)

<a id="module-fly"></a>

### Fly

**Movement** · ID: `fly` · Default: **Off**

Client flight or descending glide; Legit uses an equipped elytra and hotbar rockets. Servers may correct direct motion.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Use elytra and normal rockets (`legit`) | Boolean | Off | On / Off |
| Mode (`mode`) | Choice | Vanilla | Vanilla / Glide / Conservative glide |
| Horizontal speed (blocks/tick) (`speed`) | Number | 0.35 | 0.05 to 1; step 0.01 |
| Vertical speed (blocks/tick) (`vertical-speed`) | Number | 0.35 | 0.05 to 1; step 0.01 |
| Periodic downward hover pulse (`anti-hover`) | Boolean | Off | On / Off |
| Hover pulse interval (ticks) (`pulse-interval`) | Number | 40 | 10 to 100; step 1 |
| Legit rocket interval (ticks) (`rocket-delay`) | Number | 30 | 10 to 100; step 1 |

[Back to Movement](#movement)

<a id="module-step"></a>

### Step

**Movement** · ID: `step` · Default: **Off**

Raise the client step height, or use a normal jump at a checked one-block ledge.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Use the normal jump key (`legit`) | Boolean | Off | On / Off |
| Mode (`mode`) | Choice | Instant | Instant / Jump |
| Maximum step height (blocks) (`height`) | Number | 1 | 0.6 to 2.5; step 0.01 |

[Back to Movement](#movement)

<a id="module-parkour"></a>

### Parkour

**Movement** · ID: `parkour` · Default: **Off**

Jump near a loaded unsupported edge while moving on the ground.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Use the normal jump key (`legit`) | Boolean | Off | On / Off |
| Sprint jumps (`sprint`) | Boolean | On | On / Off |
| Edge look-ahead (blocks) (`look-ahead`) | Number | 0.65 | 0.2 to 1; step 0.01 |

[Back to Movement](#movement)

<a id="module-safe-walk"></a>

### Safe Walk

**Movement** · ID: `safe-walk` · Default: **Off**

Clip movement at ledges, or hold normal sneak near an edge.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Sneak at edges using normal input (`legit`) | Boolean | Off | On / Off |
| Only while the sneak key is held (`only-sneaking`) | Boolean | Off | On / Off |

[Back to Movement](#movement)

<a id="module-fast-ladder"></a>

### Fast Ladder

**Movement** · ID: `fast-ladder` · Default: **Off**

Increase climbing velocity; Legit holds normal forward and jump on climbable blocks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Use normal forward and jump keys (`legit`) | Boolean | Off | On / Off |
| Climb speed (blocks/tick) (`speed`) | Number | 0.28 | 0.1 to 0.6; step 0.01 |
| Speed while holding jump (`jump-multiplier`) | Number | 1 | 1 to 2; step 0.1 |

[Back to Movement](#movement)

<a id="module-reverse-step"></a>

### Reverse Step

**Movement** · ID: `reverse-step` · Default: **Off**

Accelerate an existing descent only toward a loaded floor within the chosen drop. Legit sneaks through scaffolding.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Drop through scaffolding with sneak (`legit`) | Boolean | Off | On / Off |
| Downward speed (blocks/tick) (`speed`) | Number | 0.8 | 0.1 to 2; step 0.01 |
| Maximum nearby drop (blocks) (`max-drop`) | Number | 1.5 | 1 to 6; step 0.01 |

[Back to Movement](#movement)

<a id="module-fast-fall"></a>

### Fast Fall

**Movement** · ID: `fast-fall` · Default: **Off**

Accelerate an existing fall after a distance threshold. Legit only drops through scaffolding.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Drop through scaffolding with sneak (`legit`) | Boolean | Off | On / Off |
| Downward speed (blocks/tick) (`speed`) | Number | 1 | 0.2 to 3; step 0.01 |
| Trigger fall distance (blocks) (`trigger-fall`) | Number | 1 | 0.25 to 10; step 0.01 |

[Back to Movement](#movement)

<a id="module-glide"></a>

### Glide

**Movement** · ID: `glide` · Default: **Off**

Limit falling velocity outside fluids; Legit opens an equipped usable elytra.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Open an equipped elytra normally (`legit`) | Boolean | Off | On / Off |
| Fall speed (blocks/tick) (`speed`) | Number | 0.1 | 0.02 to 0.5; step 0.01 |

[Back to Movement](#movement)

<a id="module-high-jump"></a>

### High Jump

**Movement** · ID: `high-jump` · Default: **Off**

Increase an ordinary jump's upward velocity; Legit pillars with normal hotbar block use while jump is held.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Pillar with normal jumps and blocks (`legit`) | Boolean | Off | On / Off |
| Upward velocity (blocks/tick) (`height`) | Number | 0.7 | 0.42 to 1.5; step 0.01 |

[Back to Movement](#movement)

<a id="module-long-jump"></a>

### Long Jump

**Movement** · ID: `long-jump` · Default: **Off**

Boost the start of a directional jump; Legit sprint-jumps near an edge without modifying velocity.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Normal sprint jump at a ledge (`legit`) | Boolean | Off | On / Off |
| Base jump speed (blocks/tick) (`speed`) | Number | 0.65 | 0.2 to 1.5; step 0.01 |
| Horizontal boost multiplier (`boost`) | Number | 1 | 0.5 to 3; step 0.1 |
| Minimum jump velocity (`height`) | Number | 0.42 | 0.2 to 1; step 0.01 |

[Back to Movement](#movement)

<a id="module-elytra-boost"></a>

### Elytra Boost

**Movement** · ID: `elytra-boost` · Default: **Off**

Add a bounded gliding impulse and optional climb, or use real hotbar rockets when flight slows.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Use normal hotbar rocket interactions (`legit`) | Boolean | Off | On / Off |
| Mode (`mode`) | Choice | Impulse | Impulse / Rockets |
| Impulse per tick (`thrust`) | Number | 0.035 | 0.005 to 0.3; step 0.01 |
| Space climb velocity (`climb`) | Number | 0 | 0 to 1.5; step 0.01 |
| Maximum impulse speed (`max-speed`) | Number | 3 | 0.5 to 5; step 0.01 |
| Jump and open equipped elytra (`take-off`) | Boolean | Off | On / Off |
| Use rocket below speed (`rocket-below`) | Number | 1 | 0.1 to 3; step 0.01 |
| Rocket interval (ticks) (`rocket-delay`) | Number | 30 | 10 to 100; step 1 |

[Back to Movement](#movement)

<a id="module-ground-strafe"></a>

### Ground Strafe

**Movement** · ID: `ground-strafe` · Default: **Off**

Align existing ground momentum, set a steady ground speed, or use ordinary sprint hops.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Use normal sprint and jump keys (`legit`) | Boolean | Off | On / Off |
| Mode (`mode`) | Choice | Momentum | Momentum / Steady |
| Steady ground speed (blocks/tick) (`speed`) | Number | 0.32 | 0.1 to 0.8; step 0.01 |

[Back to Movement](#movement)

<a id="module-name-changer"></a>

### Name Changer

**Client** · ID: `name-changer` · Default: **Off**

Show a local alias for your own name in rendered labels and chat. Your account and outgoing text stay unchanged; editable input shows its actual text.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Alias (letters, numbers, _) (`alias`) | Text | Player | 1–32 ASCII letters, numbers or underscore |

[Back to Client](#client)

<a id="module-auto-eat"></a>

### Auto Eat

**Player** · ID: `auto-eat` · Default: **Off**

Begin eating below the hunger threshold and continue until full; temporarily owns and restores use input/slot.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Eat below hunger (`hunger`) | Number | 16 | 1 to 20; step 1 |

[Back to Player](#player)

<a id="module-auto-gapple"></a>

### Auto Gapple

**Player** · ID: `auto-gapple` · Default: **Off**

Use a hotbar golden apple below a health threshold.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Eat below health (`health`) | Number | 12 | 1 to 20; step 1 |

[Back to Player](#player)

<a id="module-auto-totem"></a>

### Auto Totem

**Player** · ID: `auto-totem` · Default: **Off**

Swap an inventory totem into the offhand through the normal player inventory handler.

No additional module settings.

[Back to Player](#player)

<a id="module-auto-armor"></a>

### Auto Armor

**Player** · ID: `auto-armor` · Default: **Off**

Equip stronger material-tier armor through guarded normal inventory quick moves.

No additional module settings.

[Back to Player](#player)

<a id="module-auto-tool"></a>

### Auto Tool

**Player** · ID: `auto-tool` · Default: **Off**

Choose the fastest hotbar tool for the block you are actively mining.

No additional module settings.

[Back to Player](#player)

<a id="module-refill"></a>

### Refill

**Player** · ID: `refill` · Default: **Off**

Swap larger matching inventory stacks into low hotbar stacks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Refill below count (`threshold`) | Number | 16 | 1 to 32; step 1 |

[Back to Player](#player)

<a id="module-anti-afk"></a>

### Anti AFK

**Player** · ID: `anti-afk` · Default: **Off**

Periodically turn and jump using ordinary input, while no menu is open.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Interval (seconds) (`interval`) | Number | 30 | 5 to 120; step 1 |

[Back to Player](#player)

<a id="module-auto-respawn"></a>

### Auto Respawn

**Player** · ID: `auto-respawn` · Default: **Off**

Request an ordinary respawn after the death screen delay.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Delay (ticks) (`delay`) | Number | 20 | 1 to 100; step 1 |

[Back to Player](#player)

<a id="module-durability-alert"></a>

### Durability Alert

**Player** · ID: `durability-alert` · Default: **Off**

Show a periodic warning for low-durability equipped items.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Remaining durability (%) (`threshold`) | Number | 15 | 1 to 50; step 1 |

[Back to Player](#player)

<a id="module-auto-fish"></a>

### Auto Fish

**Player** · ID: `auto-fish` · Default: **Off**

Reel on the server-synchronized caught-fish flag, then optionally cast again.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Automatically cast (`cast`) | Boolean | On | On / Off |
| Cast/reel interval (ticks) (`delay`) | Number | 20 | 5 to 100; step 1 |

[Back to Player](#player)

<a id="module-scaffold"></a>

### Scaffold

**World** · ID: `scaffold` · Default: **Off**

Place hotbar blocks underneath your feet using ordinary block interactions.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Placement delay (ticks) (`delay`) | Number | 3 | 2 to 20; step 1 |

[Back to World](#world)

<a id="module-tower"></a>

### Tower

**World** · ID: `tower` · Default: **Off**

While holding jump, rise onto automatically placed hotbar blocks.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Jump velocity (`height`) | Number | 0.42 | 0.42 to 0.7; step 0.02 |

[Back to World](#world)

<a id="module-air-place"></a>

### Air Place

**World** · ID: `air-place` · Default: **Off**

While holding use, attempt to place a block at your crosshair in empty air.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Placement distance (`distance`) | Number | 3 | 1 to 4; step 0.25 |

[Back to World](#world)

<a id="module-nuker"></a>

### Nuker

**World** · ID: `nuker` · Default: **Off**

Mine nearby reachable blocks one at a time with normal mining progress.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Interaction range (`range`) | Number | 4 | 1 to 4.5; step 0.25 |

[Back to World](#world)

<a id="module-auto-farm"></a>

### Auto Farm

**World** · ID: `auto-farm` · Default: **Off**

Harvest mature crops and replant empty farmland using hotbar seeds.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Interaction range (`range`) | Number | 4 | 1 to 4.5; step 0.25 |

[Back to World](#world)

<a id="module-auto-tunnel"></a>

### Auto Tunnel

**World** · ID: `auto-tunnel` · Default: **Off**

Mine a two-block-tall path in the direction you face.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Interaction range (`range`) | Number | 4 | 1 to 4.5; step 0.25 |

[Back to World](#world)

<a id="module-bed-breaker"></a>

### Bed Breaker

**World** · ID: `bed-breaker` · Default: **Off**

Find and mine reachable beds using normal mining progress.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Interaction range (`range`) | Number | 4 | 1 to 4.5; step 0.25 |

[Back to World](#world)

<a id="module-flatten"></a>

### Flatten

**World** · ID: `flatten` · Default: **Off**

Mine reachable blocks above your standing level; preserve the floor.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Interaction range (`range`) | Number | 4 | 1 to 4.5; step 0.25 |

[Back to World](#world)

<a id="module-speed-mine"></a>

### Speed Mine

**World** · ID: `speed-mine` · Default: **Off**

Remove the client delay between ordinary block breaks; server mining rules still apply.

No additional module settings.

[Back to World](#world)

<a id="module-vein-miner"></a>

### Vein Miner

**World** · ID: `vein-miner` · Default: **Off**

After a manual break, mine connected blocks of the same type within reach.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Interaction range (`range`) | Number | 4 | 1 to 4.5; step 0.25 |
| Connected block limit (`limit`) | Number | 32 | 4 to 64; step 1 |

[Back to World](#world)

<a id="module-no-block-interact"></a>

### No Block Interact

**World** · ID: `no-block-interact` · Default: **Off**

Use block placement against containers without opening them.

No additional module settings.

[Back to World](#world)

<a id="module-chest-stealer"></a>

### Chest Stealer

**Player** · ID: `chest-stealer` · Default: **Off**

Quick-move items from an open container at a bounded rate.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Transfer delay (ticks) (`delay`) | Number | 4 | 2 to 20; step 1 |

[Back to Player](#player)

<a id="module-middle-click-friend"></a>

### Middle Click Friend

**Player** · ID: `middle-click-friend` · Default: **Off**

Middle-click a player to add or remove a locally saved friend.

No additional module settings.

[Back to Player](#player)

<a id="module-player-detection"></a>

### Player Detection

**Player** · ID: `player-detection` · Default: **Off**

Notify locally when a non-friend enters your selected radius.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Detection range (`range`) | Number | 48 | 8 to 128; step 4 |

[Back to Player](#player)

<a id="module-notifier"></a>

### Notifier

**Player** · ID: `notifier` · Default: **Off**

Local messages for loaded players arriving or departing.

No additional module settings.

[Back to Player](#player)

<a id="module-coord-snapper"></a>

### Coord Snapper

**Player** · ID: `coord-snapper` · Default: **Off**

Copy your coordinates to the clipboard, then turn off automatically.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Include Nether conversion (`nether`) | Boolean | On | On / Off |

[Back to Player](#player)

<a id="module-expedition"></a>

### Automation

**Client** · ID: `expedition` · Default: **Off**

Review a local task plan. Safer routing and active Survival protection guard its normal actions.

| Setting | Type | Default | Allowed values |
| --- | --- | --- | --- |
| Use cautious native routes (`safe-routing`) | Boolean | On | On / Off |
| Path search nodes per tick (`path-budget`) | Number | 256 | 64 to 512; step 64 |
| Path segment radius (blocks) (`path-radius`) | Number | 24 | 8 to 48; step 4 |
| Maximum planned drop (blocks) (`max-drop`) | Number | 1 | 1 to 3; step 1 |
| Smooth visible turning (`smooth-turns`) | Boolean | On | On / Off |
| Turning speed (degrees / second) (`turn-speed`) | Number | 150 | 30 to 720; step 10 |
| Turning acceleration (degrees / second²) (`turn-acceleration`) | Number | 600 | 60 to 3600; step 60 |
| Move within heading angle (degrees) (`movement-angle`) | Number | 35 | 5 to 60; step 5 |
| Delay between broken blocks (ticks) (`break-delay`) | Number | 5 | 1 to 20; step 1 |
| Delay between block placements (ticks) (`place-delay`) | Number | 4 | 1 to 20; step 1 |
| Find a suitable nearby build site (`auto-site`) | Boolean | On | On / Off |
| Build site search radius (blocks) (`site-radius`) | Number | 24 | 8 to 64; step 4 |
| Show selected schematic ghost (`schematic-preview`) | Boolean | On | On / Off |
| Schematic ghost opacity (`preview-opacity`) | Number | 0.3 | 0.1 to 0.8; step 0.05 |
| Schematic preview style (`preview-style`) | Choice | Ghost | Ghost / Outline |
| Protect against nearby hostile mobs (`defense`) | Boolean | On | On / Off |
| Avoid hostile patrols before contact (`proactive-avoidance`) | Boolean | On | On / Off |
| Proactive threat range (blocks) (`avoid-range`) | Number | 16 | 8 to 32; step 1 |
| Escape route search radius (blocks) (`escape-radius`) | Number | 12 | 4 to 24; step 1 |
| Prefer cover from ranged mobs (`prefer-cover`) | Boolean | On | On / Off |
| Threat detection range (blocks) (`threat-range`) | Number | 12 | 4 to 24; step 1 |
| Retreat below health (`retreat-health`) | Number | 10 | 6 to 20; step 1 |
| Safe delay before resuming (seconds) (`resume-delay`) | Number | 3 | 0.5 to 10; step 0.5 |
| Eat available food during tasks (`auto-food`) | Boolean | On | On / Off |
| Eat below hunger (`eat-below`) | Number | 14 | 6 to 20; step 1 |
| Pause below health (`min-health`) | Number | 6 | 1 to 20; step 1 |
| Pause below hunger (`min-hunger`) | Number | 6 | 0 to 20; step 1 |
| No-progress timeout (seconds) (`stuck-timeout`) | Number | 90 | 15 to 600; step 5 |
| Pause while other menus are open (`pause-on-menu`) | Boolean | On | On / Off |
| Yield to manual movement or action input (`pause-on-input`) | Boolean | On | On / Off |

[Back to Client](#client)

---

Generated by `./gradlew moduleCatalog` from `BuiltinModules.definitions()`. Edit the typed definitions and regenerate both catalogue files when controls change.
