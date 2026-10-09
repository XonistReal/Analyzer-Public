# Survival safety — Analyzer 0.5.2

Protection belongs to an explicitly started or resumed task in **Analyzer → Automation**. Saved checkpoints load paused. Manual Baritone tasks keep their own controls and settings; Analyzer yields when they take over. Safety is a local controller, with no AI service or API key.

## What happens during a threat

The task enters **PROTECTING**, pauses its owned Baritone process and stops block breaking. A readable top-center **TASK PAUSED FOR SAFETY** banner explains the current response. Inventory work is stopped and task-owned crafting items are returned before defense starts.

Active hostile mobs inside the configured range trigger protection. Ordinary mobs need a clear line of sight; urgent creepers are also considered when obscured. Ignited or swelling creepers are prioritized, followed by creepers within eight blocks, or twelve for charged creepers, subject to the configured threat range. This prevents a closer zombie from displacing an imminent blast as the escape priority. Healthy players with a suitable hotbar weapon can defend against a nearby attacker through ordinary attacks at normal reach and cooldown. Creepers, low health and crowded fights favor retreat.

Retreat surveys up to two loaded blocks ahead and rechecks the world each tick. It checks body clearance, hazardous blocks and actual collision-shape support, accepting supported snow/slabs and steps or drops up to one block. Surface water is considered only when the player has a clear nearby route upward to open air; ordinary jump input supplies buoyancy. Land entry requires shallow water. The controller can choose another short corridor if the direct direction is obstructed; it does not reuse an unchecked route after an explosion changes the terrain.

Suitable food can be consumed through ordinary item use from the hotbar, offhand or main inventory. Main-inventory food uses a normal slot swap and restores the original hotbar item afterward. Poisonous or teleporting foods are excluded. Low health can require food and actual regeneration; the controller does not create food or set health.

After the configured quiet interval, health must meet both the retreat and minimum-health limits, and hunger must meet the minimum-hunger limit, before the original running stage resumes. If recovery, cursor restoration, attack or a checked retreat is currently unavailable, the task enters **SAFETY_HOLD** with a specific reason. The owned route stays paused while protection keeps checking threats, terrain and available food. It can react after landing from knockback, after a threat disappears, or after food and health become sufficient. Manual movement or supplying a missing item may still be necessary.

A live stage already **BLOCKED** for a separate requirement can receive protection, but returns to its original blocked reason after danger clears. That stage requires explicit Resume; safety does not silently restart it. User Pause and Stop end monitoring and release guard-owned movement, use, selected-slot and camera control. Saved checkpoints also remain inactive until explicitly resumed. If a menu or game-mode change prevents active protection, the task switches to Paused and requires Resume. After a manual Baritone task finishes, explicit Resume can retry the original Analyzer stage; Analyzer refuses to take control while the manual task is still active.

## Controls

Open the **Automation** module's settings to change these values. Health is measured in HP; 2 HP equals one heart. Hunger uses the vanilla 0–20 scale.

| Setting | Default | Range |
|---|---:|---|
| Use safer Baritone routes | On | On/off |
| Protect against nearby hostile mobs | On | On/off |
| Threat range | 12 blocks | 4–24 |
| Retreat health | 10 HP | 6–20 |
| Resume delay | 3 seconds | 0.5–10 |
| Eat available food | On | On/off |
| Eat below hunger | 14 | 6–20 |
| Minimum health | 6 HP | 1–20 |
| Minimum hunger | 6 | 0–20 |
| No-progress timeout | 90 seconds | 15–600 |
| Pause while menus are open | On | On/off |

## Safer routing and preferences

The unchanged official Baritone API 1.13.1 supplies the pathfinder. Its [settings documentation](https://baritone.leijurv.com/baritone/api/Settings.html) lists mob avoidance as off by default. Avoidance changes path costs around mobs; it does not provide combat defense for a stationary miner. Analyzer's Survival guard supplies the active reaction.

While Analyzer owns a process, its temporary routing profile enables avoidance, raises mob/spawner cost penalties, uses bounded avoidance radii, caps ordinary falls at three blocks or the user's stricter limit, disables bucket-clutch falls and parkour, disables risky diagonal descent/overshoot and liquid-walking assumptions, and enables strict fluid and falling-block checks. This can make some routes unavailable or slower. Baritone's avoidance map includes many passive mobs too and costs extra calculation time.

The bridge snapshots settings before applying the profile. It restores them on user Pause, Stop, completion, ordinary task blocking, world change, disconnect, death and manual takeover. A live safety hold retains the owned paused route and its scoped settings while protection monitors recovery. If another writer edits a setting while the profile is active, restoration preserves that edit. Settings commands temporarily restore the preferences before the upstream command reads or saves them, then reapply the profile only if the same owned process remains active. Analyzer does not write Baritone's settings file itself.

## Practical limits

This is bounded PvE protection. It does not fight players, predict every attack, find a long path to shelter or automatically build cover. Surface-water checks do not provide deep-water or enclosed-underwater navigation. A narrow tunnel, blocked exit, unsupported ledge, ceiling, deep water or lava may leave no checked corridor; climbing, gliding and flying are excluded. Defense needs available equipment, food recovery needs suitable food, and dangerous combat can still kill the player. Unprovoked endermen and passive mobs are excluded; unsupported or modded mob behavior may differ. User-paused tasks do not act, and ordinary blocked stages still require a deliberate retry.

The earlier 0.5.1 Survival fixtures used flat supported ground and did not expose the snowy lakeshore/explosion-knockback failure. That failure revealed two separate limits: unsuitable terrain was rejected immediately, and the resulting blocked state stopped further safety checks. Version 0.5.2 changes both the terrain survey and hold lifecycle. Historical validation remains a record of the narrower scenarios actually tested; it does not establish safety on every terrain.

Validation uses local integrated-server Survival fixtures. Public-server escape, anticheat acceptance, every terrain/mob combination and other mod combinations remain unverified. See [VALIDATION.md](VALIDATION.md) for actual evidence and [UI-OVERHAUL-PLAN.md](UI-OVERHAUL-PLAN.md) for the later UI redesign.
