# Native Analyzer Automation — 0.10.0

Automation now runs entirely in Analyzer against Minecraft/Fabric APIs. Baritone and its native helper are removed from the current build, mod metadata and Prism package. Historical sources/notices remain for earlier releases and research. The new navigation, motion, mining, building and safety code is original; no external bot or AI service is required.

## Start and control

Open **Right Shift → Automation**, type a supported goal or select a preset, then **Review plan → Start reviewed plan**. Review exposes actual stages, materials and unmet requirements. Start and Resume are explicit. Saved checkpoints restore paused. Pause/Stop release owned movement, breaking and item use; unrelated modules and manual cursor items remain preserved. Loading profiles never starts a task.

Examples: `go to 1500 64 -2200`, `escape spawn 25000 blocks east`, `mine 32 diamonds`, `mine 16 ancient debris`, `get diamond armor`, `get fully enchanted diamond armor`, `get netherite armor`, `go to the nether`, `return to the overworld`, `go to the end`, or combined escape/base/gear goals. These are supported local intents, not arbitrary language or executable commands.

## Original navigation and pacing

An incremental A* search surveys loaded collision shapes, support, one-block jumps, configured drops and surface water. Longer routes are divided into observed segments as chunks load. Costs prefer cautious ground and penalize nearby hostiles. Every movement segment is checked again against current terrain. Unsupported gaps, blocked exits, unloaded terrain and repeated lack of movement are reported. This is ground navigation; it does not fly, teleport or navigate arbitrary vehicles/deep-water routes.

**Client → Automation** includes turn speed/acceleration, smooth turning, heading tolerance, between-block breaking and placement delays, search budget/radius, maximum drop, automatic site selection, mob avoidance and recovery settings. Turning changes the visible player rotation used by normal Minecraft interactions. Actions wait for actual aim and server state. Breaking delay controls the pause between blocks; block hardness, tools, reach and server acceptance still determine the actual breaking time. Manual movement/action input pauses the task by default.

## Mining and tool recovery

The native miner searches loaded ore/log sources, approaches usable interaction positions, selects a suitable durable tool, breaks through ordinary attack/progress calls and verifies actual item drops. Ore goals can dig checked stair/branch corridors at a resource-specific height. Lapis and flint count the requested drops, not merely broken ore/gravel. Flint can retry owned gravel through normal placement and breaking with a bounded retry budget.

A missing or nearly broken required pickaxe starts a local prerequisite queue: gather wood, make/place a crafting table, craft wooden/stone/iron tools, smelt real iron and craft a diamond pickaxe when its tier is needed. Owned materials and usable tools are credited. Recovery does not invent resources: inaccessible wood/ore, no building space or loss of every needed resource in a dimension can still block it. Meaningful inventory, durability and movement changes drive the progress watchdog.

## Schematics and material-first construction

Choose **Import schematic**, paste a local path, or drag one file onto the Automation screen. Supported imports are Litematica `.litematic` versions 4–7, mapped MCEdit `.schematic`, and Sponge `.schem` versions 1–3. Review shows dimensions, material totals, selected site and importer warnings. Automatic site selection surveys nearby loaded level ground, full support, existing blocks/containers, fluids and nearby hostiles; it does not select a place by pretending unloaded chunks are empty.

Every remaining material is acquired before the construction stage. A curated material catalog gathers and crafts ordinary wood/stone/glass and related building blocks; unsupported recipes require the actual owned item. Construction checks the reviewed file hash and supplies again, then uses the original native builder. World state, orientations, support, player clearance and material consumption are observed. Exact selected block states are verified before completion. Existing structures and block-entity contents are preserved. Only replaceable foliage or recorded task-owned mistaken placements can be cleared.

Imports are bounded to 64 MiB files, 128 MiB decoded NBT, 32 nesting levels, 256 blocks per axis and 262,144 total cells. A plan has at most 64 stages and each material target at most 4,096 items. Supplies must fit in player inventory; automatic depot/chest logistics are not implemented. Tall/floating structures need reachable support or supplied stairs/scaffolding. Entities, inventories and command-bearing block-entity NBT are ignored with a warning; unknown or non-Survival states are rejected. Ambiguous old connection states require re-export in a modern format. The builder does not promise arbitrary redstone simulation or every schematic.

## Gear, enchantment and dimensions

Diamond gearing uses normal crafting, smelting and equip clicks. Enchanting budgets **12 lapis for four level-30 table offers** (3 each), credits owned lapis and verifies item enchantment data. Quality armor means Protection IV + Unbreaking III on all four pieces. Actual table outcomes remain random; a usable table/bookshelf layout, XP and suitable books/anvil upgrades are real requirements. Villager trading, renewable XP farming and automatic acquisition of every enchantment book are not implemented.

Netherite planning gathers 16 ancient debris, 16 gold and the diamond/template-duplication budget, smelts scrap, crafts four ingots, performs real smithing upgrades and equips verified netherite armor. The first Netherite Upgrade template must already be owned; autonomous bastion discovery/looting is not implemented. Duplicating the other three costs 21 diamonds and three netherrack. Smithing verifies that existing armor enchantments survive.

Nether goals can prepare tools and portal supplies, collect actual flint, craft an igniter, construct a normal 10-obsidian frame with 13 cobblestone corner/scaffold supports, ignite it and enter. Existing reachable loaded portals can be used. Expected transport is accepted only for the same server/save and authorized dimension pair. End travel can use a loaded complete portal or fill a complete oriented frame with owned eyes. Unknown stronghold discovery and arbitrary custom dimensions are not implemented. Reaching portal coordinates alone never counts as transport.

## Evidence and limits

Pure policy tests cover planner/material budgets, import parsing, motion limits, graph routes, safety geometry and configuration. Native acceptance runs in an isolated Java 21 Survival client without Baritone, using real player movement, block actions, inventory and dimension transport. The current observed results and any unresolved cases are recorded in [VALIDATION.md](VALIDATION.md). Historical Baritone tests do not establish behavior of this replacement. Public-server acceptance, every terrain/mob combination and performance superiority over another bot remain unverified.
