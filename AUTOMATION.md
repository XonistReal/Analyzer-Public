# Analyzer Automation

Analyzer 0.5.1 includes the local task planner introduced in 0.5.0, scoped safer routing and active Survival protection, using the official [Baritone Fabric API 1.13.1](https://github.com/cabaletta/baritone/releases/tag/v1.13.1), for Minecraft 1.21.4. No AI service, API key or cloud prompt is used. The planner recognizes supported intentions and turns them into explicit stages; it is not an arbitrary-language Minecraft AI.

## Open and start

Press **Right Shift → Automation** in Workspace, Classic or Compact. Choose a preset or type a supported task, adjust the route, then **Review plan → Start task**. The screen shows each stage and its requirements before execution. Open it again to inspect progress, **Pause**, **Resume** or **Stop**. Starting a task temporarily suspends incompatible action modules and restores their earlier toggles when it stops.

Examples:

- `escape spawn 25000 blocks east`: target an X/Z offset from your current position. The default is 10,000 blocks north; direction and distance are editable.
- `go to 1500 64 -2200`: path to a coordinate. `go to 1500 -2200` uses the route Y field.
- `mine 32 diamonds`: collect a total inventory target through reachable ore; bring an iron-tier pickaxe. Already owned matching items count.
- `get diamond armor`: gather wood/stone/iron/coal/diamonds, craft tools and a complete diamond kit, then equip it.
- `build a base`: gather supplies and build the original 7 × 7 × 5 starter shelter at the route location. The footprint starts three blocks east and south of that point, with its floor one block below route Y. It uses 168 cobblestone blocks, a roof and an open two-block entrance.
- `escape spawn, build a base and get fully enchanted diamond armor`: combine the travel, resource, tool, armor, shelter and enchanting stages. After horizontal escape travel, the actual arrival Y is used for returning home and building.

The route fields default to your current location. Explicit coordinates in supported task text override them. Unknown intentions produce a useful error rather than running arbitrary chat commands.

## What runs

Baritone handles normal travel, searching/mining and bounded schematic construction. Analyzer handles ordinary crafting-grid clicks, workstation placement/opening, iron smelting, armor equip, table enchanting and matching-book anvil upgrades. It does not edit client inventories or place blocks directly into the world. Freshly crafted mining tools and shelter supplies are moved to the hotbar through normal clicks because upstream Baritone disables inventory access by default.

The diamond kit consists of helmet, chestplate, leggings, boots, pickaxe and sword. The quality armor goal verifies **Protection IV and Unbreaking III on every diamond armor piece**. Mending and ideal boot/helmet-specific enchantments are not part of that goal. Table outcomes follow vanilla randomness. Available matching books take priority; otherwise unenchanted armor receives the level-30 table offer, and missing upgrade books remain an explicit unmet requirement. Conflicting enchantments or anvil Too Expensive costs are reported.

Table enchanting needs a table, 15 correctly placed/unobstructed bookshelves, lapis and XP levels. Matching-book upgrades need an anvil, relevant books and enough XP; that route can bypass the table. This release does **not** automate finding villagers, book trading, renewable XP farms, hunting leather, constructing the enchanting-library setup or grinding away conflicting enchantments. It pauses at those requirements; it does not claim a fully equipped character before verifying the actual items. Tool durability, food, inventory space, reachable ore and server acceptance can also stop progress.

## Progress and control

Only a reviewed task explicitly started by you runs. Loading a saved module toggle never starts movement. A task checkpoint is saved locally under `config/analyzer/automation-task.json`; unfinished tasks are restored **paused** after restart, and completed tasks retain their completed state. Resume requires the original world and dimension. Disconnect, death and dimension changes stop owned actions and require review. The plan does not reconnect, join public servers or navigate portals automatically.

The Automation module includes safer routing, hostile-mob protection, threat range, retreat health, recovery delay and food controls, alongside minimum health (6 HP), minimum hunger (6), no-progress timeout (90 seconds) and menu pausing. Exact defaults and limits are in [SAFETY.md](SAFETY.md). Protection uses a **PROTECTING** state while the original stage is held; the HUD says **TASK PAUSED FOR SAFETY**. It defends at normal reach/cooldown, retreats along a short checked-ground corridor when appropriate and consumes suitable food through normal inventory/item actions. After the configured quiet delay, health and hunger checks gate automatic resumption. An unrecoverable situation becomes **BLOCKED** with a concrete reason.

Resource-loading overlays always pause execution. Menu pausing is enabled by default and configurable; Analyzer-owned workstation windows are permitted. User Pause and Stop end protection and release its owned forward/sprint/use inputs. Stop preserves task-owned cursor items and avoids moving a cursor item that belongs to a manual interaction. An unavailable return slot is reported for manual recovery.

Manual Baritone movement/mining/build commands take control from the task. Analyzer yields its owned protection/route actions rather than cancelling that separate manual task. Harmless query commands retain ownership. With safer routing enabled, Analyzer temporarily adjusts shared Baritone settings in memory only for its owned task; it restores them on user Pause, Stop, completion, blocking, world/death shutdown or manual takeover. Later user setting edits are preserved, and settings commands are handled without saving the temporary profile to Baritone's settings file.

Protection watches hostile mobs, not players. Its short ground retreat does not find long detours or cover, swim, climb or fly. Missing suitable food while below the configured recovery limits, an unavailable attack/escape or another unmet requirement leaves a visible blocked task for manual recovery. Surface escape remains ordinary pathfinding toward a coordinate offset; neither route preferences nor defense guarantee safety or acceptance on 2b2t, 0b0t or another server.

The [UI overhaul plan](UI-OVERHAUL-PLAN.md) covers a later reference-led menu/task-console redesign. This release keeps the existing menu layouts and adds live protection feedback.

## Dependency and development

The Prism ZIP includes the official API Fabric JAR as a separate mod. Analyzer can still launch without it; the Automation screen permits plan review and explains the missing dependency. Existing instances need `baritone-api-fabric-1.13.1.jar` in their mods folder. The local Analyzer updater updates Analyzer; it does not replace arbitrary other mods.

Pinned SHA-256: `bfb79c3cdb8fe1f7697999b1f9c7f08ea29775f3b69d48ad90c387690ee67b05`. The official checksum, corresponding source archive and licensing notices are included under `third-party/baritone` in the source package and `baritone-source-and-notices` in the Prism instance. Baritone remains separately replaceable under its upstream LGPL license; Analyzer's own code remains MIT.

The API contract is documented by upstream [setup](https://github.com/cabaletta/baritone/blob/v1.13.1/SETUP.md), [public API](https://baritone.leijurv.com/), [builder](https://baritone.leijurv.com/baritone/api/process/IBuilderProcess.html) and [mine process](https://baritone.leijurv.com/baritone/api/process/IMineProcess.html). Runtime evidence and its limits are recorded in [VALIDATION.md](VALIDATION.md).
