# Fight Bot — 0.16.0 beta

**Combat → Fight Bot** fights one filtered target for you while your camera stays free. It walks toward a target inside its chase range, circles it at a fighting distance, times jumps so attacks land as falling critical hits, and attacks with server-facing aim. You can keep looking around, read chat or watch something else; with **Silent movement** your screen never turns. Fight Bot starts disabled.

[All Fight Bot settings](MODULES.md#module-fight-bot) · [Silent aim](SILENT-AIM.md) · [Combat and ESP](COMBAT-ESP.md)

## What it does each tick

1. **Pick a target.** The usual combat filters apply (players/mobs, friends, invisible, armor, new entities, field of view, priority, obstructed targets). Targets are searched out to **Chase range**; attacks still need **Attack reach**. The current target stays locked while it remains valid.
2. **Aim.** **Silent movement** sends the aim through movement packets only: the server sees you facing the target, your camera does not move. **Client** turns the real camera instead. Turn speed, smoothing, aim point and the maximum aim error work as they do in Kill Aura.
3. **Move.** Outside fighting range it walks or sprints straight at the target (**Chase only** stops there). In range, **Chase and circle** strafes around the target while holding **Fighting distance**:
   - **Away from their aim** circles toward whichever side keeps you out of the target's look direction, using **Their aim cone to avoid**. Clockwise and Counter-clockwise always circle one way.
   - **Back off while your attack recharges** steps out of range while the weapon recharges, then steps back in for the hit.
   - **Sprint-jump while chasing** hops while closing a long gap. One-block obstacles are jumped, and you swim up in water.
4. **Hit.** It attacks only when the target is in reach, your weapon is charged to **Required attack charge**, the target's hurt time allows damage, and the transmitted aim actually hits the target's hitbox. **Hold the best hotbar weapon** switches to your best weapon first.

## Critical hits

A vanilla critical needs the attacker to be falling, not on the ground, not sprinting, not in water or on a ladder, and not blind or levitating. Fight Bot plans for that:

- It jumps **Jump before full charge (ticks)** before the weapon is ready, so the attack lands just after the top of the jump while you are falling. The default, 6 ticks, matches the rise time of a normal jump.
- It releases sprint in the air before the attack, so the hit counts as a critical rather than a sprint knockback hit.
- With **Only attack when a critical hit is possible**, it waits for the falling window instead of attacking from the ground. Otherwise it still attacks when a critical is impossible (in water, under a low ceiling, or when jumping is unsafe).

## Free camera and movement correction

With **Silent movement**, the server believes you face the target while your screen faces wherever you like. Plain movement keys would then push you in the camera's direction, not toward the target. Fight Bot avoids that two ways:

- It chooses W/A/S/D relative to the direction it wants to move, using the 8-way key sector closest to that direction.
- On ticks it controls, **movement correction** makes your own walking and jumping use the silently transmitted yaw, so your movement agrees with the direction the server sees you facing. Your camera and model still use your real view.

If movement correction is unavailable in a given game version, Fight Bot falls back to camera-relative keys, which still move correctly but less precisely while you turn.

## Safety and taking over

- **Never walk off drops deeper than** refuses steps that would fall further than the limit; it turns the circle the other way or stops instead.
- **Avoid lava, fire, cactus and magma** also avoids soul fire, magma blocks, campfires, sweet berry bushes, powder snow and wither roses.
- **Your movement keys take over:** pressing any movement key gives you movement control at once. Fight Bot keeps aiming and hitting.
- **Stop fighting at or below health** stops at that health (absorption counts). 0 never stops.
- It pauses while you fly, glide, ride or spectate, while you use an item or break a block, and yields to projectile aim and protection actions such as Auto Totem.
- Kill Aura and Fight Bot don't run at the same time: while Fight Bot has a target, it owns the attack.

## Limits

Fight Bot works with the target in its chase range on loaded terrain. It is not a pathfinder: it does not route around walls or plan through mazes, and it won't follow a target across gaps it refuses to walk into. A long-range route to a distant target is outside its scope; use Automation for travel.

Server rules and anti-cheat vary. Silent aim, timed jumps and circling movement are visible to a server's checks; Analyzer makes no claim of passing any server's anti-cheat. Use it where it is allowed.

## Research and originality

The user asked for an automated fight module like those in current clients. These were behavior references only: the [Larp Client module list](https://larpclient.com/docs/modules), the [Wurst FightBot](https://wurst.wiki/fightbot) description, and the [LiquidBounce Kill Aura documentation](https://liquidbounce.net/docs/modules/combat/killaura) on movement correction and critical timing. No code, assets or text from those projects are included. Movement planning, critical timing and safety checks are Analyzer's own, in `FightBotPolicy` and `FightBotController`.

## Verification

`FightBotPolicySelfTest` covers 11,605 deterministic assertions: key mapping in all directions, chase/circle phases, orbit direction and distance correction, critical jump timing and sprint release, back-off timing, drop limits and health stops. It runs in the Gradle `check` task.

The native `runBetaSmoke` fixture runs a real client and integrated server. A 300-health husk stands ten blocks behind a camera facing the other way. Fight Bot walked to it and landed 15 server-side hits in about 15 seconds. Every hit was a falling critical (10.33 damage each, against about 6.9 for a plain hit through the husk's armor), and the real camera yaw never moved. That run also found and fixed a bug: on 1.21.4 the client's fall distance stays 0 in the air, so the bot now measures the drop from the jump's peak. Behavior on public servers and against moving players has not been tested. See [VALIDATION.md](VALIDATION.md).
