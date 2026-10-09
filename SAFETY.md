# Survival safety — Analyzer 0.5.1

Protection belongs to an explicitly started or resumed task in **Analyzer → Automation**. Saved checkpoints load paused. Manual Baritone tasks keep their own controls and settings; Analyzer yields when they take over. Safety is a local controller, with no AI service or API key.

## What happens during a threat

The task enters **PROTECTING**, pauses its owned Baritone process and stops block breaking. A readable top-center **TASK PAUSED FOR SAFETY** banner explains the current response. Inventory work is stopped and task-owned crafting items are returned before defense starts.

Visible, active hostile mobs inside the configured range trigger protection. Healthy players with a suitable hotbar weapon can defend against a nearby attacker through ordinary attacks at normal reach and cooldown. Creepers, low health and crowded fights favor retreat. Retreat checks a short corridor for loaded chunks, body clearance, full floor support and hazardous blocks or fluids. It can choose another ground direction when the direct escape is obstructed.

Suitable food can be consumed through ordinary item use from the hotbar, offhand or main inventory. Main-inventory food uses a normal slot swap and restores the original hotbar item afterward. Poisonous or teleporting foods are excluded. Low health can require food and actual regeneration; the controller does not create food or set health.

After the configured quiet interval, health must meet both the retreat and minimum-health limits, and hunger must meet the minimum-hunger limit, before the original stage resumes. If recovery, cursor restoration, attack or a safe retreat is unavailable, the task becomes **BLOCKED** with a reason. Move to safety or supply the missing requirement, then explicitly Resume. User Pause/Stop release guard-owned movement, use, selected-slot and camera control.

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

The bridge snapshots settings before applying the profile. It restores them on user Pause, Stop, completion, blocking, world change, disconnect, death and manual takeover. If another writer edits a setting while the profile is active, restoration preserves that edit. Settings commands temporarily restore the preferences before the upstream command reads or saves them, then reapply the profile only if the same owned process remains active. Analyzer does not write Baritone's settings file itself.

## Practical limits

This is bounded PvE protection, not guaranteed survival. It does not fight players, predict every attack, find a long path to shelter or automatically build cover. Ground retreat does not swim, climb, glide or fly; a narrow tunnel, blocked exit, ledge, deep water or lava may leave no checked corridor. Defense needs available equipment, food recovery needs suitable food, and dangerous combat can still kill the player. Unprovoked endermen and passive mobs are excluded; unsupported or modded mob behavior may differ. Manual pauses and blocked tasks require manual attention.

Validation uses local integrated-server Survival fixtures. Public-server escape, anticheat acceptance, every terrain/mob combination and other mod combinations remain unverified. See [VALIDATION.md](VALIDATION.md) for actual evidence and [UI-OVERHAUL-PLAN.md](UI-OVERHAUL-PLAN.md) for the later UI redesign.
