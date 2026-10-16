> **0.12.0 addition:** [Entity Chams](ENTITY-CHAMS.md) provides five GLSL materials on actual posed models, with independent visible/occluded colors. It can be used alongside the debug ESP below.

# Combat and debug ESP — 0.11.0

Combat modules share a per-tick camera/action owner instead of rotating and switching independently. Active protection and projectile use take priority; melee waits while an accepted explosive/placement action owns the tick. Auto Weapon and Shield Breaker coordinate their slot changes. Aura prefers reachable targets, preserves its lock where appropriate, and can require the actual camera ray before attacking. Mining, held-item use, target hurt time and weapon-settle gates are exposed in settings. Fractional action deadlines preserve configured average CPS. Projectile aim uses a lower ballistic arc estimate; it is not a complete drag/collision simulation or a guarantee of a hit.

New modules are disabled by default:

| Module | Actual behavior |
| --- | --- |
| Auto Block | Uses an equipped offhand shield against configured nearby threats, releases it before attacks/food, and yields to enabled HVH placement modules by default. |
| Double Hand | Selects an actual hotbar totem after a real server-reported local totem pop or a configured low-health threat; returns the previous slot when safe, respecting manual selection. |
| Hover Totem | In the player's own inventory, swaps an actually hovered totem to the offhand after the configured dwell and optional held key. Cursor, slot, menu and interval guards apply. |

Rockets, pillar aiming and Anti AFK yield to active combat controls. Native Automation suspends conflicting modules, including the three additions, and restores its temporary setting lease on exit. Ordinary server reach, cooldowns, packets, equipped items and friend/team exclusions still apply.

## Debug ESP

Entity and storage boxes use one line pass around actual bounds; forced filled faces, glow stacks and floating label panels are removed. Entity colors have three main groups: **players / hostile mobs / non-hostile mobs**, each with a palette or custom RGB. The previous dropped-item filter remains optional. Tracers can match those colors and expose their own range, width, opacity and anchor.

Storage filters/colors cover chests, trapped/ender chests, barrels, all shulkers, hoppers, furnaces/smokers, dispensers, droppers, brewing stands, crafters, chiseled bookshelves, pots, lecterns, hives, campfires, jukeboxes and storage vehicles. Double chests share a union box. Loaded storage carts and chest boats use their actual interpolated bounds.

Fresh defaults use complete boxes with labels, health panels and radar off. Existing explicit filters, styles, colors and labels remain saved. Optional labels are plain and retain distance/overlap limits. Target ESP defaults to Box; Item ESP uses actual bounds with an optional Rings mode; Hole ESP is outline-only. Existing explicit alternative styles stay selectable. Geometry range, width, depth/through-wall behavior and HUD/menu themes remain independent.

## Primary references and module coverage

The implementations are original Analyzer code against Minecraft/Fabric APIs. No Meteor or Glazed code or runtime dependency is bundled.

- [Meteor KillAura](https://github.com/MeteorDevelopment/meteor-client/blob/2385f7b4bb9de1a698b4503254bd32f30fd7a87d/src/main/java/meteordevelopment/meteorclient/systems/modules/combat/KillAura.java): targeting, pause conditions and coordination concepts.
- [Meteor ESP](https://github.com/MeteorDevelopment/meteor-client/blob/2385f7b4bb9de1a698b4503254bd32f30fd7a87d/src/main/java/meteordevelopment/meteorclient/systems/modules/render/ESP.java), [StorageESP](https://github.com/MeteorDevelopment/meteor-client/blob/2385f7b4bb9de1a698b4503254bd32f30fd7a87d/src/main/java/meteordevelopment/meteorclient/systems/modules/render/StorageESP.java), [Tracers](https://github.com/MeteorDevelopment/meteor-client/blob/2385f7b4bb9de1a698b4503254bd32f30fd7a87d/src/main/java/meteordevelopment/meteorclient/systems/modules/render/Tracers.java): native box and broad filter concepts.
- [Glazed's 1.21.4 source](https://github.com/realnnpg/glazed/tree/1d33d14fdafd26100aa7bf616758786d79ac7b96): inspected Double Hand, Hover Totem and Shield Breaker behavior. Its [licence](https://github.com/realnnpg/glazed/blob/1d33d14fdafd26100aa7bf616758786d79ac7b96/LICENSE) is restrictive; the additions are independently implemented feature equivalents.

| Glazed feature family | Analyzer coverage |
| --- | --- |
| Double Hand / Hover Totem | Added here. |
| Aim Assist / Shield Breaker / Key Pearl / totems | Existing counterparts; combat coordination and shield switching improved here. |
| Crystal/Anchor macros / Anti Trap | Analyzer has its own auras, trap and surround controllers; these are different modes, not claimed identical ports. |
| Storage/player/entity visuals | Debug ESP, Tracers and existing storage/stash/detection tools; not every specialized Glazed detector. |
| Server shops, orders, economy macros, RTP methods and other specialized tools | Not added in this update; server commands and assumptions require separate implementation and validation. |

The public [LARP repository located during research](https://github.com/optimizemaxxing-eng/larpaddon) contained only a README, with no implementation to verify. No LARP source or binary was imported.

Build and policy results are in [VALIDATION.md](VALIDATION.md). Remaining in-game checks are left to the user; public-server outcomes and renderer compatibility are not established by deterministic checks.
