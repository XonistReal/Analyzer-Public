# Analyzer module catalogue

122 registered modules. Descriptions state the implemented scope; matching reference names do not imply feature parity. Gameplay automation is opt-in.

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
| HUD Studio | Theme, scale and position controls for your entire HUD. | On |
| Watermark | Analyzer identity and version badge. | On |
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
| Kill Aura | Attack the nearest selected visible target within vanilla reach and cooldown. | Off |
| Aim Assist | Smoothly turn toward a selected target while holding attack. | Off |
| Trigger Bot | Attack a selected entity under your crosshair at the configured cooldown. | Off |
| Auto Clicker | Repeat entity attacks while you hold attack; respects reach and cooldown. | Off |
| Auto Weapon | Select a hotbar melee weapon by its material/damage estimate while attacking. | Off |
| Shield Breaker | Select a hotbar axe and attack a visible blocking target at normal reach. | Off |
| Bow Aimbot | Aim a drawn bow at a visible target with velocity lead and gravity compensation. | Off |
| Criticals | Jump before melee attacks, then attack in the ordinary falling phase. Hold attack or enable an aura. | Off |

## Movement

| Module | Behavior | Default |
| --- | --- | --- |
| Keep Sprint | Hold the normal sprint input while moving forward and able to sprint. | Off |
| Auto Walk | Hold forward while the world is focused; releases the input when disabled. | Off |
| Speed | Set horizontal client velocity while directional input is held; servers may correct it. | Off |
| Fly | Client velocity flight with jump/sneak vertical control; does not grant server flight permission. | Off |
| Step | Temporarily raise the client step-height attribute; servers may correct movement. | Off |
| Parkour | Jump when approaching an unsupported edge while moving on the ground. | Off |
| Safe Walk | Use vanilla ledge clipping without requiring the sneak key. | Off |
| Fast Ladder | Increase upward velocity while climbing with forward or jump input. | Off |
| Reverse Step | Accelerate downward when falling toward a nearby floor. | Off |
| Fast Fall | Accelerate an existing fall after the configured distance; no fall-damage spoof. | Off |
| Glide | Limit ordinary falling velocity outside fluids; servers may correct it. | Off |
| High Jump | Increase upward velocity at the start of a normal jump. | Off |
| Long Jump | Add horizontal velocity at the start of a normal directional jump. | Off |
| Elytra Boost | Add a bounded impulse in the view direction while already gliding. | Off |
| Ground Strafe | Align existing horizontal momentum with your ground movement input. | Off |

## HVH

| Module | Behavior | Default |
| --- | --- | --- |
| Crystal Aura | Place crystals on real obsidian/bedrock and break reachable enemy-near crystals. No damage prediction. | Off |
| Anchor Aura | Place, charge and use anchors only in dimensions where they explode. No damage prediction. | Off |
| Surround | Place hotbar obsidian around your feet, one normal interaction at a time. | Off |
| Auto Trap | Place obsidian around a selected target and above its head with ordinary block use. | Off |
| Auto Web | Place hotbar cobweb at a selected target's feet when support and reach permit. | Off |
| Hole Filler | Fill nearby one-block holes with a solid floor and four solid walls using obsidian. | Off |
| Target Strafe | Move tangentially around the nearest selected visible target; collision remains active. | Off |

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

