# Analyzer 0.6.0 module sweep

Final result: PASS. The bounded Java 21 native run completed successfully in 2 minutes 15 seconds, including launcher startup and shutdown. The fixture itself took 115.86 seconds / 2,319 ticks.

- 123 modules, each with default, low/false/first-choice, and high/true/last-choice settings: 369 live variants.
- 1,091 exposed settings: 11,886 accepted writes covering numeric min/mid/max and clamps, both boolean states, and every choice name. All 1,091 invalid typed writes were rejected without changing state.
- Seven active-config and seven named-profile roundtrips restored every module state, keybind and setting without warnings. HUD menu aliases were compared through their canonical stored values.
- 13,051 matrix/native/cleanup assertions and 25 additional focused Combat/HVH assertions passed. The final runtime log contains no ERROR/FATAL messages, guarded gameplay failures or OpenGL errors.
- Every variant rendered its real Studio module card and native shader surfaces; each then disabled with owned movement inputs, Step height and empty-inventory slot ownership released. HUD widget variants enabled HUD Studio and verified real render bounds.
- Nine category screenshots were copied to screenshots/analyzer-module-sweep-*.png and visually checked.

The focused action fixture checked actual Step attribute and Auto Walk input ownership/release, hostile target filters, bounded camera turn, vanilla melee reach and weapon charge gates, server-confirmed damage, loaded-terrain/armor/resistance/anchor explosion estimates, preview rendering and bounded placement turns.

## Scope limits

- Config admission covers every choice, both booleans and numeric endpoints/midpoint; live execution covers three combinations per module, not every Cartesian combination.
- Creative mode and an empty inventory deliberately gate equipment-, container-, keypress-, death- and task-dependent actions. A nonzero counter records an actual local action; a successful enable/render test alone does not prove that module's behavior.
- Coord Snapper's open-menu pause gate is verified instead of copying. The fixture never reads or replaces the system clipboard.
- Automation's module toggle is tested without an explicit Start; it must remain idle. The separate focused combat fixture verifies real hostile filters, bounded camera rotation, normal reach/charge gates and server-confirmed melee, plus loaded-terrain HVH estimates and previews.
- No public multiplayer server, launcher world, saved user profile or user configuration is used. No disconnect/relog modules exist in this 123-module catalog.

The initial attempts were stopped or failed due to fixture issues: a transient Windows clipboard preservation failure, then a Survival transition over floor mined by the sweep. The final fixture performs no clipboard reads or writes and rebuilds supported ground, clears fall distance, and waits for the actual Survival transition. No production module fixes were required.

## Per-module result

Every row passed all three variants. Action counters record accepted local controller actions; they do not alone establish server-confirmed effects. Cosmetic frames and HUD bounds establish actual render-path participation.

| Module | Category | Settings | Variants | Action / utility counters | Cosmetic frames | HUD render bounds |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Entity ESP | Render | 20 | 3/3 | 0 / 0 | 0 | n/a |
| Storage ESP | World | 14 | 3/3 | 0 / 0 | 0 | n/a |
| Tracers | Render | 7 | 3/3 | 0 / 0 | 0 | n/a |
| Fullbright | World | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Coordinates | HUD | 1 | 3/3 | 0 / 0 | 0 | 3/3 |
| Crosshair | HUD | 3 | 3/3 | 0 / 0 | 0 | n/a |
| Particles | Cosmetics | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Interface | Client | 32 | 3/3 | 0 / 0 | 0 | n/a |
| Sky Aurora | World | 5 | 3/3 | 0 / 0 | 0 | n/a |
| Atmosphere | Render | 2 | 3/3 | 0 / 0 | 0 | n/a |
| HUD Studio | HUD | 13 | 3/3 | 0 / 0 | 0 | n/a |
| Watermark | HUD | 3 | 3/3 | 0 / 0 | 0 | 3/3 |
| Active Modules | HUD | 5 | 3/3 | 0 / 0 | 0 | 3/3 |
| FPS | HUD | 3 | 3/3 | 0 / 0 | 0 | 3/3 |
| Ping | HUD | 3 | 3/3 | 0 / 0 | 0 | 3/3 |
| Speed HUD | HUD | 4 | 3/3 | 0 / 0 | 0 | 3/3 |
| Armor | HUD | 4 | 3/3 | 0 / 0 | 0 | 3/3 |
| Potion Effects | HUD | 3 | 3/3 | 0 / 0 | 0 | 3/3 |
| Target HUD | HUD | 3 | 3/3 | 0 / 0 | 0 | 3/3 |
| Keybinds | HUD | 4 | 3/3 | 0 / 0 | 0 | 3/3 |
| Inventory Preview | HUD | 4 | 3/3 | 0 / 0 | 0 | 3/3 |
| Compass | HUD | 3 | 3/3 | 0 / 0 | 0 | 3/3 |
| Biome | HUD | 3 | 3/3 | 0 / 0 | 0 | 3/3 |
| World Clock | HUD | 4 | 3/3 | 0 / 0 | 0 | 3/3 |
| Item Counter | HUD | 4 | 3/3 | 0 / 0 | 0 | 3/3 |
| Keystrokes | HUD | 4 | 3/3 | 0 / 0 | 0 | 3/3 |
| Target Distance | HUD | 3 | 3/3 | 0 / 0 | 0 | 3/3 |
| Place Animation | Cosmetics | 10 | 3/3 | 0 / 0 | 0 | n/a |
| Break Animation | Cosmetics | 15 | 3/3 | 0 / 0 | 0 | n/a |
| Place Preview | Cosmetics | 13 | 3/3 | 0 / 0 | 0 | n/a |
| Block Highlight | Render | 9 | 3/3 | 0 / 0 | 0 | n/a |
| Hit Particles | Cosmetics | 16 | 3/3 | 0 / 0 | 0 | n/a |
| Jump Circles | Cosmetics | 11 | 3/3 | 0 / 0 | 0 | n/a |
| Trails | Cosmetics | 12 | 3/3 | 0 / 0 | 0 | n/a |
| Damage Numbers | Cosmetics | 11 | 3/3 | 0 / 0 | 0 | n/a |
| Hit Markers | Cosmetics | 12 | 3/3 | 0 / 0 | 0 | n/a |
| Footsteps | Cosmetics | 12 | 3/3 | 0 / 0 | 0 | n/a |
| Death Effects | Cosmetics | 13 | 3/3 | 0 / 0 | 0 | n/a |
| Projectile Trails | Cosmetics | 15 | 3/3 | 0 / 0 | 0 | n/a |
| Pickup Streaks | Cosmetics | 12 | 3/3 | 0 / 0 | 0 | n/a |
| Teleport Effects | Cosmetics | 12 | 3/3 | 0 / 0 | 0 | n/a |
| Player Aura | Cosmetics | 11 | 3/3 | 0 / 0 | 0 | n/a |
| Target ESP | Render | 11 | 3/3 | 0 / 0 | 0 | n/a |
| Hole ESP | Render | 9 | 3/3 | 0 / 0 | 0 | n/a |
| Trajectories | Render | 9 | 3/3 | 0 / 0 | 0 | n/a |
| Item ESP | Render | 10 | 3/3 | 0 / 0 | 0 | n/a |
| Nametags | Render | 12 | 3/3 | 0 / 0 | 0 | n/a |
| Explosion Effects | Cosmetics | 13 | 3/3 | 0 / 0 | 0 | n/a |
| Totem Pops | Cosmetics | 11 | 3/3 | 0 / 0 | 0 | n/a |
| Landing Impact | Cosmetics | 11 | 3/3 | 0 / 0 | 0 | n/a |
| Health Pulse | Cosmetics | 10 | 3/3 | 0 / 0 | 0 | n/a |
| Air Effects | Cosmetics | 16 | 3/3 | 0 / 0 | 0 | n/a |
| Block Effects | Cosmetics | 17 | 3/3 | 0 / 0 | 0 | n/a |
| Damage Effects | Cosmetics | 18 | 3/3 | 0 / 0 | 0 | n/a |
| Contrails | Cosmetics | 17 | 3/3 | 0 / 0 | 0 | n/a |
| Storm Effects | Cosmetics | 17 | 3/3 | 0 / 0 | 0 | n/a |
| Vitals | Cosmetics | 13 | 3/3 | 0 / 0 | 0 | n/a |
| Hit Sounds | Cosmetics | 6 | 3/3 | 0 / 0 | 0 | n/a |
| Item Physics | Cosmetics | 4 | 3/3 | 0 / 0 | 9 | n/a |
| Hand Tweaks | Cosmetics | 27 | 3/3 | 0 / 0 | 12 | n/a |
| Crystal Chams | Cosmetics | 12 | 3/3 | 0 / 0 | 0 | n/a |
| Cape | Cosmetics | 15 | 3/3 | 0 / 0 | 9 | n/a |
| Pets | Cosmetics | 9 | 3/3 | 0 / 0 | 9 | n/a |
| Emotes | Cosmetics | 5 | 3/3 | 0 / 0 | 1 | n/a |
| Walk Style | Cosmetics | 3 | 3/3 | 0 / 0 | 3 | n/a |
| Spinbot | Cosmetics | 2 | 3/3 | 0 / 0 | 9 | n/a |
| Kill Aura | Combat | 33 | 3/3 | 0 / 0 | 0 | n/a |
| Aim Assist | Combat | 21 | 3/3 | 0 / 0 | 0 | n/a |
| Trigger Bot | Combat | 27 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Clicker | Combat | 30 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Weapon | Combat | 7 | 3/3 | 0 / 0 | 0 | n/a |
| Shield Breaker | Combat | 6 | 3/3 | 0 / 0 | 0 | n/a |
| Bow Aimbot | Combat | 24 | 3/3 | 0 / 0 | 0 | n/a |
| Criticals | Combat | 35 | 3/3 | 0 / 0 | 0 | n/a |
| Crystal Aura | HVH | 39 | 3/3 | 0 / 0 | 0 | n/a |
| Anchor Aura | HVH | 31 | 3/3 | 0 / 0 | 0 | n/a |
| Surround | HVH | 25 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Trap | HVH | 29 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Web | HVH | 30 | 3/3 | 0 / 0 | 0 | n/a |
| Hole Filler | HVH | 23 | 3/3 | 0 / 0 | 0 | n/a |
| Target Strafe | HVH | 27 | 3/3 | 0 / 0 | 0 | n/a |
| Keep Sprint | Movement | 4 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Walk | Movement | 4 | 3/3 | 6 / 0 | 0 | n/a |
| Speed | Movement | 4 | 3/3 | 0 / 0 | 0 | n/a |
| Fly | Movement | 7 | 3/3 | 4 / 0 | 0 | n/a |
| Step | Movement | 3 | 3/3 | 4 / 0 | 0 | n/a |
| Parkour | Movement | 3 | 3/3 | 0 / 0 | 0 | n/a |
| Safe Walk | Movement | 2 | 3/3 | 0 / 0 | 0 | n/a |
| Fast Ladder | Movement | 3 | 3/3 | 0 / 0 | 0 | n/a |
| Reverse Step | Movement | 3 | 3/3 | 2 / 0 | 0 | n/a |
| Fast Fall | Movement | 3 | 3/3 | 0 / 0 | 0 | n/a |
| Glide | Movement | 2 | 3/3 | 2 / 0 | 0 | n/a |
| High Jump | Movement | 2 | 3/3 | 0 / 0 | 0 | n/a |
| Long Jump | Movement | 4 | 3/3 | 0 / 0 | 0 | n/a |
| Elytra Boost | Movement | 8 | 3/3 | 0 / 0 | 0 | n/a |
| Ground Strafe | Movement | 3 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Eat | Player | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Gapple | Player | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Totem | Player | 0 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Armor | Player | 0 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Tool | Player | 0 | 3/3 | 0 / 0 | 0 | n/a |
| Refill | Player | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Anti AFK | Player | 1 | 3/3 | 1 / 0 | 0 | n/a |
| Auto Respawn | Player | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Durability Alert | Player | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Fish | Player | 2 | 3/3 | 0 / 0 | 0 | n/a |
| Scaffold | World | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Tower | World | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Air Place | World | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Nuker | World | 1 | 3/3 | 0 / 4 | 0 | n/a |
| Auto Farm | World | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Auto Tunnel | World | 1 | 3/3 | 0 / 1 | 0 | n/a |
| Bed Breaker | World | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Flatten | World | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Speed Mine | World | 0 | 3/3 | 0 / 0 | 0 | n/a |
| Vein Miner | World | 2 | 3/3 | 0 / 0 | 0 | n/a |
| No Block Interact | World | 0 | 3/3 | 0 / 0 | 0 | n/a |
| Chest Stealer | Player | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Middle Click Friend | Player | 0 | 3/3 | 0 / 0 | 0 | n/a |
| Player Detection | Player | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Notifier | Player | 0 | 3/3 | 0 / 0 | 0 | n/a |
| Coord Snapper | Player | 1 | 3/3 | 0 / 0 | 0 | n/a |
| Automation | Client | 11 | 3/3 | 0 / 0 | 0 | n/a |

Detailed input values, normalized stored values, live statuses, action counters and rendering diagnostics are in all-module-sweep-coverage.json. The exact native checks are in all-module-sweep-runtime.log.
