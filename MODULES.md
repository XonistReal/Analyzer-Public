# Analyzer module catalogue

123 registered modules. Descriptions state the implemented scope; matching reference names do not imply feature parity. Gameplay automation is opt-in.

## Render

| Module | Behavior | Default |
| --- | --- | --- |
| Entity ESP | Highlight nearby players, creatures and items. | Off |
| Tracers | Draw lines from your view to nearby entities. | Off |
| Atmosphere | Cinematic bloom and color grading. Sky Aurora controls the sky. | Off |
| Block Highlight | A pulsing outline of the block under your crosshair. | Off |
| Target ESP | Orbiting target rings on your crosshair target or recently attacked entity. | Off |
| Hole ESP | Outline loaded single-block holes with resistant floors and walls. | Off |
| Trajectories | Predict a held bow, trident, snowball, egg or pearl path against loaded blocks. | Off |
| Item ESP | Independent glowing rings and beams on loaded dropped items. | Off |
| Nametags | Independent readable names, health and distances for loaded players and mobs. | Off |

## World

| Module | Behavior | Default |
| --- | --- | --- |
| Storage ESP | Highlight nearby containers. | Off |
| Fullbright | Lift world lighting for clearer exploration. | Off |
| Sky Aurora | Animated sky curtains, stars and cosmic color above your world. | On |
| Scaffold | Place hotbar blocks underneath your feet using ordinary block interactions. | Off |
| Tower | While holding jump, rise onto automatically placed hotbar blocks. | Off |
| Air Place | While holding use, attempt to place a block at your crosshair in empty air. | Off |
| Nuker | Mine nearby reachable blocks one at a time with normal mining progress. | Off |
| Auto Farm | Harvest mature crops and replant empty farmland using hotbar seeds. | Off |
| Auto Tunnel | Mine a two-block-tall path in the direction you face. | Off |
| Bed Breaker | Find and mine reachable beds using normal mining progress. | Off |
| Flatten | Mine reachable blocks above your standing level; preserve the floor. | Off |
| Speed Mine | Remove the client delay between ordinary block breaks; server mining rules still apply. | Off |
| Vein Miner | After a manual break, mine connected blocks of the same type within reach. | Off |
| No Block Interact | Use block placement against containers without opening them. | Off |

## HUD

| Module | Behavior | Default |
| --- | --- | --- |
| Coordinates | Show position and dimension in your HUD. | On |
| Crosshair | A crisp customizable crosshair. | Off |
| HUD Studio | Choose your menu, appearance and HUD widget layout. | On |
| Watermark | Your selected logo and version badge. | On |
| Active Modules | A bounded list of enabled gameplay modules. | On |
| FPS | Current client frames per second. | On |
| Ping | Actual player latency reported by the server. | Off |
| Speed HUD | Horizontal movement speed in blocks per second. | Off |
| Armor | Equipped armor icons with remaining durability. | Off |
| Potion Effects | Active effects, levels and remaining durations. | Off |
| Target HUD | Health and armor of your acquired combat or crosshair target. | Off |
| Keybinds | Assigned module binds with their enabled state. | Off |
| Inventory Preview | Your actual inventory in a compact three-row panel. | Off |
| Compass | Yaw-based cardinal direction and heading. | Off |
| Biome | Registry name of the biome at your current position. | Off |
| World Clock | World time of day and current day number. | Off |
| Item Counter | Count a selected resource in your inventory and offhand. | Off |
| Keystrokes | Live movement, jump and mouse-button states. | Off |
| Target Distance | Measured camera distance to the current raycast hit. | Off |

## Combat

| Module | Behavior | Default |
| --- | --- | --- |
| Kill Aura | Choose, rotate toward and attack filtered targets with normal reach and bounded charged actions. | Off |
| Aim Assist | Pull your actual camera toward a filtered target with configurable aim point, strength and smoothing. | Off |
| Trigger Bot | Attack a filtered crosshair target after its reaction delay, normal reach and selected charge timing. | Off |
| Auto Clicker | Repeat held entity attacks or ordinary item/block use with bounded steady, random or paired timing. | Off |
| Auto Weapon | Temporarily choose allowed hotbar melee weapons by material estimate or durability after a reaction delay. | Off |
| Shield Breaker | Hold a usable hotbar axe for a blocking crosshair target, with reaction and release delays. | Off |
| Bow Aimbot | Aim the actual camera for selected drawn bows, charged crossbows or tridents using bounded lead/drop estimates. | Off |
| Criticals | Use real jumps or your own falling motion to time ordinary charged attacks; no packet critical spoofing. | Off |

## Movement

| Module | Behavior | Default |
| --- | --- | --- |
| Keep Sprint | Keep sprinting while vanilla movement and hunger conditions permit. | Off |
| Auto Walk | Hold a chosen movement key while the world is focused; input is released on pause or disable. | Off |
| Speed | Choose direct motion, a grounded conservative profile, or normal sprint jumps. | Off |
| Fly | Client flight or descending glide; Legit uses an equipped elytra and hotbar rockets. Servers may correct direct motion. | Off |
| Step | Raise the client step height, or use a normal jump at a checked one-block ledge. | Off |
| Parkour | Jump near a loaded unsupported edge while moving on the ground. | Off |
| Safe Walk | Clip movement at ledges, or hold normal sneak near an edge. | Off |
| Fast Ladder | Increase climbing velocity; Legit holds normal forward and jump on climbable blocks. | Off |
| Reverse Step | Accelerate an existing descent only toward a loaded floor within the chosen drop. Legit sneaks through scaffolding. | Off |
| Fast Fall | Accelerate an existing fall after a distance threshold. Legit only drops through scaffolding. | Off |
| Glide | Limit falling velocity outside fluids; Legit opens an equipped usable elytra. | Off |
| High Jump | Increase an ordinary jump's upward velocity; Legit pillars with normal hotbar block use while jump is held. | Off |
| Long Jump | Boost the start of a directional jump; Legit sprint-jumps near an edge without modifying velocity. | Off |
| Elytra Boost | Add a bounded gliding impulse and optional climb, or use real hotbar rockets when flight slows. | Off |
| Ground Strafe | Align existing ground momentum, set a steady ground speed, or use ordinary sprint hops. | Off |

## HVH

| Module | Behavior | Default |
| --- | --- | --- |
| Crystal Aura | Score reachable crystals with loaded-world explosion exposure and conservative self/friend damage guards. | Off |
| Anchor Aura | Place, charge and detonate reachable anchors outside anchor-safe dimensions, with exposure damage guards. | Off |
| Surround | Build a supported configurable shell around your occupied blocks using normal placement. | Off |
| Auto Trap | Build a supported roof, head shell or full cage around a selected target. | Off |
| Auto Web | Place supported feet/head webs on selected enemies within reach and a per-tick budget. | Off |
| Hole Filler | Scan loaded nearby supported single/double holes and fill relevant unoccupied holes. | Off |
| Target Strafe | Orbit a selected target with checked support, collision, ordinary hops and optional camera/input steering. | Off |

## Player

| Module | Behavior | Default |
| --- | --- | --- |
| Auto Eat | Eat hotbar food below a hunger threshold; temporarily owns and restores use input/slot. | Off |
| Auto Gapple | Use a hotbar golden apple below a health threshold. | Off |
| Auto Totem | Swap an inventory totem into the offhand through the normal player inventory handler. | Off |
| Auto Armor | Equip stronger material-tier armor through guarded normal inventory quick moves. | Off |
| Auto Tool | Choose the fastest hotbar tool for the block you are actively mining. | Off |
| Refill | Swap larger matching inventory stacks into low hotbar stacks. | Off |
| Anti AFK | Periodically turn and jump using ordinary input, while no menu is open. | Off |
| Auto Respawn | Request an ordinary respawn after the death screen delay. | Off |
| Durability Alert | Show a periodic warning for low-durability equipped items. | Off |
| Auto Fish | Reel on the server-synchronized caught-fish flag, then optionally cast again. | Off |
| Chest Stealer | Quick-move items from an open container at a bounded rate. | Off |
| Middle Click Friend | Middle-click a player to add or remove a locally saved friend. | Off |
| Player Detection | Notify locally when a non-friend enters your selected radius. | Off |
| Notifier | Local messages for loaded players arriving or departing. | Off |
| Coord Snapper | Copy your coordinates to the clipboard, then turn off automatically. | Off |

## Cosmetics

| Module | Behavior | Default |
| --- | --- | --- |
| Particles | Animated ambient particles in the Analyzer menu. | On |
| Place Animation | Ease a colored block ghost into each successful placement. | On |
| Break Animation | Mining progress, dissolving block ghosts and debris on successful breaks. | On |
| Place Preview | Show the actual adjacent or replaceable block position for your held block. | Off |
| Hit Particles | Colored sparks when loaded living entities are actually hurt. | On |
| Jump Circles | Expanding floor ripples when you leave the ground with upward velocity. | Off |
| Trails | A fading ribbon following your actual movement. | Off |
| Damage Numbers | Floating numbers from actual health decreases of loaded living entities. | Off |
| Hit Markers | A brief crosshair flash when your recently attacked target is hurt. | Off |
| Footsteps | Small fading floor rings spaced along grounded movement. | Off |
| Death Effects | A rising particle burst when a loaded living entity dies. | Off |
| Projectile Trails | Fading paths behind actual loaded projectiles. | Off |
| Pickup Streaks | A curved streak from an item to the collector on real pickup packets. | Off |
| Teleport Effects | Paired energy rings on large local position changes. | Off |
| Player Aura | Rotating rings around your actual player position. | Off |
| Explosion Effects | Shockwave rings and sparks from actual nearby explosion packets. | Off |
| Totem Pops | Golden rings, sparks and a floating pop label on real totem-use statuses. | Off |
| Landing Impact | A floor shockwave when a falling player lands. | Off |
| Health Pulse | A fading edge vignette when your local health decreases. | Off |
| Air Effects | A bounded animated particle field around the camera, drawn in world space. | Off |
| Block Effects | Independent colored fragments or frames from actual placements and breaks. | Off |
| Damage Effects | Confirmed hurt bursts and an optional persistent low-health warning. | Off |
| Contrails | Paired fading paths from real player motion, optionally restricted to elytra flight. | Off |
| Storm Effects | Branching bolts, ground ripples and a flash on actual loaded lightning strikes. | Off |
| Vitals | A local warning driven by real health, fire and remaining underwater air. | Off |
| Hit Sounds | Local vanilla sound cues on confirmed hurts of a recently attacked target. | Off |
| Item Physics | Dropped-item models tumble in the air and rest on the ground; entity physics is unchanged. | Off |
| Hand Tweaks | Actual first-person hand/item transforms and swing/equip controls; does not alter attacks. | Off |
| Crystal Chams | Tint the actual crystal model or replace it with an original Gem/Prism mesh. Normal depth remains. | Off |
| Cape | An original segmented cloth mesh with moving hems and colors, visible in third person. No image/GIF importer. | Off |
| Pets | Original animated Rune Orb, Ghost or Dragon geometry following your rendered body in third person. | Off |
| Emotes | Pose your local player model in third person. No camera change or server-side animation. | Off |
| Walk Style | Alternative local third-person arm, leg and body poses using the existing player model. | Off |
| Spinbot | Spin only your rendered player model. Camera, entity rotation and server packets are unchanged. | Off |

## Client

| Module | Behavior | Default |
| --- | --- | --- |
| Interface | Glass or solid panels, accent colors and motion control. | On |
| Automation | Review a local task plan. Safer routing and active Survival protection guard its normal actions. | Off |

