# First beta — October 21, 2026

Analyzer **0.16.0-beta.1** adds:

- **Combat → Fight Bot**
- **Cosmetics → Straw Hat**
- **Studio / Click GUI / Compact** menu layouts
- Five chams materials, with a separate occluded material, a secondary color and edges
- **Filled** and **Glow** ESP styles

[Fight Bot](FIGHT-BOT.md) · [Straw Hat](STRAW-HAT.md) · [Chams](ENTITY-CHAMS.md) · [ESP](COMBAT-ESP.md).

Fabric and Mojang hosts were unreachable from the authoring environment, so every build and native check below ran on GitHub Actions (ubuntu-latest, Temurin 21). Java 21 `build moduleCatalog` passes every Gradle `check` suite, and the committed catalogue matches **160 modules / 1,931 settings**. Two suites are new: `FightBotPolicySelfTest` passes **11,605** assertions and `StrawHatSelfTest` **690,207**. Updated suites pass as well: `CoreSelfTest` 2,301, `ChamsPolicySelfTest` 1,065, `SilentAimPolicySelfTest` 1,414 and `GameplayConfigSelfTest` 597.

The [native smoke workflow](.github/workflows/native-smoke.yml) runs real development clients and integrated servers under Xvfb with Mesa software OpenGL:

- **`runBetaSmoke` — 40 checks, 10 captures.**
  - **Fight Bot:** a 300-health husk stands ten blocks behind a camera facing away. Fight Bot chased it and landed **15 server-side hits** in about 15 seconds, taking it to 145 health. Each hit did **10.33**, a falling critical; a plain hit through the husk's armor does about 6.9. The real camera yaw never moved.
  - **Straw Hat:** Straw and Glass draw on the posed player.
  - **ESP:** Filled ESP leaves clean GL state. With Glow ESP, `MinecraftClient.hasOutline` and `Entity.getTeamColorValue` answer for the husk in its ESP color and return to native behavior when disabled.
  - **Chams:** Hologram and Galaxy draw on the husk with a Rim light occluded material and edges.
  - **Menus:** In the Click GUI, left-click toggles a Movement module on and off, right-click opens the settings flyout, Escape closes only the flyout, and a header drag saves the panel position. Compact and Studio open with live controls.
- **`runChamsSmoke` passes.** It includes `ChamsGlChecks`, which compiles and draws all ten materials, plus five occluded-material variants with edges, without GL errors and with every state restored.
- **`runSilentAimSmoke` passes 425 checks**, including real server attacks and damage through the changed silent-rotation ownership.
- **`runWingsSmoke` passes 39 checks** through the updated cosmetic renderer.
- **Mixins:** every client launch applied all mixins, including the two Glow ESP hooks and the two movement-correction hooks.

The first `runBetaSmoke` run showed Fight Bot reaching the target without ever attacking. On 1.21.4 the client player's `fallDistance` stays 0 in the air, so the falling-critical window never opened. This was fixed before release: the bot now measures the drop from the jump's peak. The next run measured the critical hits above.

**Not covered:**

- `runUiSmoke`, `runGameplaySmoke` and `runLiquidLayoutSmoke` fail on the CI runner at tick-timed UI steps for unmodified 0.15.0 as well, so they provide no evidence for this release. A baseline run on the same runner confirmed this.
- Hardware GPU drivers, Windows/macOS, Iris/Sodium, public servers or anti-cheat, and moving player targets are untested.
- The Criticals module's own falling check uses the same client `fallDistance` and was not changed or retested.
- No Prism ZIP is published for this beta. It needs native acceptance on the Windows machine first.

## Earlier 0.15.0 evidence

# Articulated wings and public delivery — October 20, 2026

Analyzer **0.15.0** adds opt-in **Cosmetics → Wings** with Angel / Dragon / Bat / Parrot, Auto / Flap / Idle / Glide / Still, animation speed/strength and scale/height/back-offset controls. The approved attachment is raised three Minecraft pixels and follows the native posed body. Four original textures share Paul Fulham's supplied 17-part MIT rig. [Controls and attribution](WINGS.md).

Java 21 `build moduleCatalog` and `publishLocal` pass **79,277 assertions across 34 suites**, including **103 wing policy/config assertions**. The generated catalogue documents **158 modules / 1,839 settings**, with every default, numeric bound/step and choice readable in Markdown. The source JAR and runtime JAR both retain full wing/font notices. Final build/feed SHA-256: **ac80dfdd12f8b5cae220ed241afdaf246fa3e0a9e41a31350792fb73e98d8de1**.

The completed isolated `runWingsSmoke` passes **39 native checks** and writes seven inspected **1920 × 1080** framebuffer captures. Actual feature draws confirm unchanged texture resources, 1,584 submitted vertices, the raised mount, crouched body attachment and armor spacing. The fixture observes Elytra hiding/override, first-person suppression, resource reload, disable/re-enable and loaded disconnect cleanup. All screenshot callbacks finish; GL checks pass and the completed log contains no ERROR/FATAL entries. [Native evidence](production-validation/native-0.15.0/wings.json) · [Combined acceptance](production-validation/native-0.15.0/acceptance.json).

Native screenshots exercise Flap; Auto/Idle/Glide/Still, finite mirrored geometry, speed/strength bounds and profile/config compatibility have deterministic coverage. No full native pose/renderer/server matrix is claimed. The three original ornate hat concepts remain review-only and outside runtime, with 600 animation-frame geometry samples rather than claimed native wearable acceptance. Other modules' deterministic checks pass; unrelated native gameplay was not rerun.

Public source preserves tag names/metadata in a filtered copy, omitting the two archival files with unresolved helper provenance while leaving the original private history unchanged. [Publication scope](PUBLICATION.md). Public Prism packages disable the optional local feed and contain no author-specific manifest path or pre-launch command. The existing user's installed instance retains its deliberate local feed, launcher preferences and unrelated game data; delivery checks compare build/feed/package/installed hashes and preserve a recoverable prior JAR. Optional Windows symlink fixtures remain explicitly skipped where OS link permission is unavailable.

## Earlier 0.14.0 evidence

# Custom skyboxes and cosmetic selection - October 16, 2026

Analyzer **0.14.0** extends **World → Skybox** with local 2:1 PNG/JPEG panoramas, six matching square cube faces, a shader folder browser and native Minecraft panorama/End texture sources. Existing Aurora/Nebula/Ember settings retain Procedural as their default. [Controls and format limits](CUSTOM-SKYBOX.md).

Java 21 `build moduleCatalog` and final `publishLocal` pass **79,162 assertions across 33 suites**, including **27 skybox import/library/config assertions**. The exported catalogue has **157 modules / 1,829 controls**. Optional Windows skybox/schematic symlink fixtures explicitly skip because operating-system link permission is unavailable. Final build/feed SHA-256: **b11c46345e3a28acd489c1c763070f241ec87d3183c68cc5718d05e97abe7c15**.

The completed isolated native run passes **55 checks** and writes six inspected **1920 × 1080** framebuffer captures. Actual library row/Use selected clicks, PNG and cube imports, both native texture choices, bad-path/native-sky fallback, resource reload, disable/re-enable, selected GL-state restoration, real End/Nether transitions and loaded disconnect cleanup are observed. The custom source uses the native End pass outside dragon boss fog; Minecraft suppresses its entire sky pass inside that fog, which is preserved. Completed Overworld/End PNGs independently contain **987/1340** and **1200/1340** sampled blue fixture pixels, respectively. Native Minecraft panorama cube mapping was corrected against its actual vertex/UV geometry and matching source edges.

Earlier draw-count-only fixture output did not prove visible End rendering and is superseded; the subsequent timeout exposed the native boss-fog gate. Only `skybox-0.14.0-native-final-verified.log` and its completed, pixel-checked captures are current acceptance. [Native evidence](production-validation/native-0.14.0/skybox.json) retains those distinctions. Native sun/moon/clouds remain native; replacement sky renderers/shader packs, arbitrary resource-pack formats and raw VTF/VPK import are not established. The Nether remains unchanged.

Eight downloaded cosmetic candidates are preserved separately for user selection. Four wing looks share one supplied 17-part Minecraft pivot rig and its original flap definitions; crown, Robin Hood hat, top hat and backpack use actual downloaded geometry. MIT/CC0 creator sources, exact original files, conversion disclosures, manifests and portable ZIP integrity are verified. Prior browser inspection shows actual wing animation, accessory thumbnails, full-size crown rendering and saved selection changes at 736-pixel width. The later source-linked CDN/WebGL fallback is syntax-checked; a completed 320-pixel or synthetic outage browser run is not claimed. No candidate is integrated into the runtime JAR or equipped in game. [Review scope](COSMETICS-SHORTLIST.md) · [Combined acceptance](production-validation/native-0.14.0/acceptance.json). Prism delivery separately verifies preserved user data and matching build/feed/package/install hashes.

## Earlier 0.13.2 evidence

# Kill Aura silent rotation transports - October 16, 2026

Analyzer **0.13.2** adds **Silent movement / Silent packet / Silent pulse** under Kill Aura Rotation while retaining Client/None preferences and normal combat gates. [Controls and mode differences](SILENT-AIM.md).

Java 21 `build moduleCatalog publishLocal` passes **79,135 assertions across 32 suites**, including **1,413 silent-aim policy/config assertions**. The catalogue has **157 modules / 1,823 controls**. Optional Windows schematic-library symlink cases remain explicitly skipped. Final build/feed SHA-256: **77280aad2a78dd21aa9187bd00f51e3ddd5f17f3d42a4d961d5dd0bfcdbfa25a**.

The completed isolated `runSilentAimSmoke` passes **427 native checks**, including **319 camera-stability tick samples**, with three inspected **1920 x 1080** framebuffer captures. Actual outbound look/attack ordering is observed; a separate integrated-server attack callback records the look at the real ordinary melee interaction, followed by actual server health loss. All three modes hit while the local camera remains at **90 / 7.5** and the target is north. Movement uses normal cadence; Packet emits explicit looks; Pulse sends aim/attack/return in order. The fresh server ray intersects the actual target hitbox. A continuously moving server target receives further real damage over 100 ticks without starving the movement transport.

Real weapon charge, normal reach and a received stone wall prevent invalid actions. Disable, changed mode, an actual inventory menu, removal of the sole server target and disconnect from an active lease clear ownership and restore ordinary look where applicable. Cross-controller handoff and correction/respawn code paths were reviewed, with no claim of a full native combat/task or active-correction matrix. Native GL error checks pass, all three screenshot callbacks finish and the completed log has no ERROR/FATAL entries.

The isolated fixture explicitly discards old entities and binds server/client observations to one authoritative damageable 200-health NoAI Husk. It waits for actual teleport/wall replication before range/visibility cases. Earlier attempts exposed replication-sensitive point tolerance, a cached view vector within the pulse packet batch and an old saved entity resurrected by setup; only the completed fourth run is acceptance. Moving-target checks derive the ray from exact server attack-time yaw/pitch and a fresh server AABB; they do not promise zero-latency point accuracy. Local packet transmission is not a server acknowledgement. Remote anti-cheat/tweak acceptance, unrelated module behavior and replacement renderers remain unverified. [Evidence JSON](production-validation/native-0.13.2/acceptance.json) and [captures](screenshots/README.md) retain hashes. Prism delivery verifies matching build/feed/package/install hashes and preserves user worlds, configs, profiles, schematics, other mods and launcher preferences.

## Earlier 0.13.1 evidence

# Screen-locked tracers and local name alias - October 16, 2026

Analyzer **0.13.1** adds independent **Tracer origin → Crosshair / Bottom / Top** throughout entity/storage/finder/skeleton tracers, retaining saved target anchors, and opt-in **Client → Name Changer → Alias**. [Controls and scope](TRACERS-AND-NAME.md).

Java 21 `build moduleCatalog publishLocal` passes **77,720 assertions across 31 suites**, including **4,682 tracer-transform assertions** and **46 name policy/config assertions**. The catalogue exports **157 modules / 1,821 controls**. Optional Windows schematic symlink cases remain explicitly skipped. Final build/feed SHA-256: **8f654976f8c2e4add5234cbfea67846a6781341400d5180488c216c801911b6e**.

The completed isolated `runTracerPrivacySmoke` passes **162 native checks**, including **30 producer/origin/visibility-pass combinations** and **10 submitted-origin samples during actual walking bob**, with first/back-third-person views, FOV 70/90/100 and moved/rotated cameras. All five representative producers emit vertices that project to the chosen screen position; all 29 source schemas and varied transforms have deterministic coverage. Three unedited **1920 x 1080** captures were visually inspected: the lines meet the crosshair, bottom and top, with the own name replaced in actual chat, tab, sidebar and native/Lato labels. There are no ERROR/FATAL entries in the completed log; native GL error checks pass.

Native String/Text/OrderedText drawing and widths agree with the alias; styled names, trimming/wrapping, live edits/disable and cached chat/helper presentation pass. Original message components, session/profile names and UUIDs remain unchanged. Actual TextField and multiline EditBox cursor/line geometry and SelectionManager validators retain raw input. The real shader alias editor accepts typed characters, rejects an invalid value and exercises Apply/Cancel. All nine mixin targets load. Book/sign-specific injection scopes were independently reviewed against pinned bytecode; this run does not type through complete book/sign screens. Actual damage, remote servers, custom third-party font paths and unrelated combat/automation were not rerun.

Earlier incomplete fixture runs encountered population timing/target visibility and a protected nested fixture type; they are not acceptance. The final command-built arena contains actual received blocks/mobs and an own-scoreboard entry. Native input handling remains development-only in the isolated launch. [Screenshots](screenshots/README.md) and [acceptance JSON](production-validation/native-0.13.1/acceptance.json) retain evidence hashes. Editable contents intentionally stay real, while the account and server identity are never renamed. Prism delivery verifies matching build/feed/package/install hashes and preserves user game data and launcher preferences.

## Earlier 0.13.0 evidence

# Loaded finders and specialized ESP - October 16, 2026

Analyzer **0.13.0** adds **27 original module-family equivalents**, including Advanced Stash Finder, Amethyst Finder, exact entity/equipment/state detectors, posed Skeleton ESP, local Findings history and a coordinate Region Map. [Controls, source references and differences](FINDERS.md).

Java 21 `build moduleCatalog publishLocal` passes **72,928 assertions across 29 suites**, with **156 modules / 1,791 controls**. Focused policy results include **657 block, 313 entity, 203 chunk/history, 159 update and 159 coordinate-grid assertions**. Optional Windows schematic-library symlink cases remain skipped where unavailable. Final build/feed SHA-256: **48367376e21d2f801a30aee1e54a85dfbe9fef4515e1491c0dbfef0ac9540fbd**.

The isolated `runFindersSmoke` client completes **99 native checks**, with positive results from **25 detectors**, five unedited **1920 x 1080** framebuffer captures, native Findings UI and a Region Map HUD. Actual received equipment distinguishes trident-bearing/empty-handed drowned; entity family toggles and posed limbs work. Full chunk/kelp scans complete during **20 actual observer update requests / 36 received state transitions**. Removing real received budding amethyst removes its marker; disabling clears all overlays, and the normal connection-close/save/disconnect sequence clears world observations and pose segments. Native GL checks pass and the completed log has no ERROR/FATAL entries.

Server commands build the isolated arena and remove only natural kelp beneath its test chunk before creating two controlled four-high columns. Scout is an explicit client-local invisible player model fixture; all other mobs, gear, blocks and state transitions come from the integrated server. **Bedrock Void has pure policy coverage, not native bottom-layer acceptance in this run.** Region Map is visually observed separately from the 25 detector positives. All five final captures are inspected. Earlier incomplete runs exposed fairness, stale-cache, natural-fixture and quit-sequence issues; only the completed final run establishes current native acceptance.

Exact loaded-data predicates do not establish ownership or a confirmed base. Natural terrain and vegetation can match patterns; no base probability or server-data bypass is claimed. Current coverage deliberately differs from upstream settings/subheuristics, and the coordinate grid does not claim verified DonutSMP IDs. Other combat/automation outcomes and replacement renderers were not rerun. [Screenshots](screenshots/README.md) and [acceptance JSON](production-validation/native-0.13.0/acceptance.json) preserve hashes. Prism packaging/update verifies the same build/feed/package/installed JAR while preserving user worlds, settings, profiles, schematics, other mods and launcher Java selection.

## Earlier 0.12.0 evidence

# Shader entity chams - October 15, 2026

Analyzer **0.12.0** adds opt-in **Render > Entity Chams** with **Flat / Metallic / Glass / Glow / Wireframe** on actual posed living models. Visible/occluded group colors and opacity are independent, with optional matching to Entity ESP. Debug boxes remain separate. [Material settings and scope](ENTITY-CHAMS.md).

Java 21 `build moduleCatalog publishLocal` passes **70,965 assertions across 24 suites**, including **1,043 chams policy assertions**, and exports **129 modules / 1,373 controls**. Optional Windows schematic-library symlink cases remain skipped where unavailable. Build/feed SHA-256: **88946eb4a8eb7ceb45e8954629b689d5c2ae59d26fe68ff3e2c35a541900db4d**.

The isolated `runChamsSmoke` native client passes **32 focused scene checks plus 535 live GL assertions**, produces nine unedited **1920 x 1080** framebuffer captures, and completes without ERROR/FATAL entries. Actual integrated-server mobs, storage and an occluding stone-brick wall are fixture preconditions; Scout is a client-local player rendering fixture. Each scene submits five posed models / 840 vertices. All five styles, separate occluded colors, disabling the hidden pass, module disable cleanup, actual resource reload and independent debug boxes are observed. Live checks compare incoming/outgoing program, VAO/VBO/EBO, depth, blend, cull, write masks, polygon state, line width, bindings and viewport/scissor state. All nine images were visually inspected. The first development pass exposed unsupported driver wide lines; final Wireframe uses shader pixel edges, and a small shared depth tolerance removes coplanar color striping. The completed second run is the current evidence.

Scope remains base living/skin models: normal armor/equipment/accessories remain. Glass uses translucent Fresnel shading and Glow an emissive rim; no scene refraction or postprocess bloom is claimed. Replacement renderers/resource-pack models and unrelated combat/automation acceptance are outside this focused run. [Capture index](screenshots/README.md) and [machine-readable evidence](production-validation/native-0.12.0/acceptance.json) retain hashes. Prism delivery verifies the same JAR across build/feed/package/install and preserves existing worlds, configs, profiles, schematics, unrelated mods and launcher Java settings.

## Earlier 0.11.0 evidence

# Later focused ESP photo pass — October 15, 2026

At the user's request, Analyzer **0.11.0** subsequently ran in an isolated Minecraft 1.21.4 development world and produced **four original 1920 × 1080 framebuffer photos**: entity groups, storage colors, matching tracers and through-wall boxes. **Nine focused checks**, including clean OpenGL errors before all four captures, passed. The images were visually inspected. Mobs/storage/cart/wall are real integrated-server objects; the cyan Scout player is an explicit client-local rendering fixture.

The [photo index](screenshots/README.md) and [separate photo evidence](production-validation/native-0.11.0/esp-photos.json) record scope/hashes. Production renderer sources and the installed JAR are unchanged; the temporary development capture hook was restored byte-for-byte. This establishes the photographed ESP behavior, not combat acceptance or completion of either farm. The original delivery evidence below remains historical to that delivery.

## Original 0.11.0 delivery

# Combat, debug ESP and schematic construction — October 15, 2026

Analyzer **0.11.0** coordinates combat camera/hotbar/action ownership and adds opt-in **Auto Block / Double Hand / Hover Totem**. Entity/storage/tracer ESP uses clean debug geometry and broad category colors; remaining target/item/hole ESP paths follow the same presentation. Local requests recognize running vanilla item names and stack sizes, including **Could you get me 512 ice?**, and distinguish real acquisition requirements. Schematic review retains a read-only ghost and uses ordinary reusable buckets for source fluids/waterlogging. Details: [Combat and ESP](COMBAT-ESP.md), [construction and limits](SCHEMATIC-CONSTRUCTION.md).

Java 21 build/moduleCatalog/publishLocal pass **69,882 assertions across 23 suites**, with **128 modules / 1,335 exposed controls**. Focused results include **157 combat / 155 ESP / 102 intent / 133 schematic** checks and **425 recipe assertions comparing 141 real layouts/batch yields against pinned Minecraft data**. Windows optional symlink cases remain explicitly skipped where unavailable. Build/feed SHA-256: **91a47440ea57575f17d6af45bb4a9cb468742e963feb4994220c6334f6b935c4**.

Both actual user files pass bounded parsing and resolution against Minecraft 1.21.4's real block registry: **Bonemeal Farm** resolves 353 snapshot blocks to 346 construction blocks (seven generated piston-head cells excluded), 22 material types and one reusable bucket; **Kelp Farm** resolves 64/64 blocks, 16 material types and one bucket. Their hashes/palettes are unchanged. This is a **headless registry check with an isolated test-only access shim**, not a game world, placement run or alteration of the runtime JAR.

**No new native gameplay client or capture run was performed; remaining in-game checks are left to the user.** Combat outcomes, visual appearance, ghost alignment and complete farm placement/refills remain manual acceptance. Construction completion verifies persistent properties, not the instantaneous power/growth state of a running machine. **Container contents, crafter configuration, sign text and scheduled ticks are not restored**; actual supports, supplies and external water/lava sources remain necessary. Current evidence is in [the 0.11.0 record](production-validation/native-0.11.0/acceptance.json). Earlier native screenshots/tests below describe their recorded versions.

Prism delivery verifies archive integrity and matching build/feed/package/install hashes while preserving existing user data, profiles, worlds, schematics, unrelated mods and launcher Java settings.

## Earlier 0.10.3 evidence

# Camera braking and faster local planning — October 15, 2026

Analyzer **0.10.3** addresses camera hunting with acceleration-aware braking, keeps valid mining approaches, avoids turning around to recenter while stopping, and uses view-preserving local corrections for descending/final route steps. Nearest-first resource observations, checked adjacent routes, useful planning-window frontiers and same-tick waypoint advancement remove redundant work. Configured turn/break/place pacing and existing safety checks remain. [AUTOMATION-MOTION.md](AUTOMATION-MOTION.md) describes the implementation and limits.

Java 21 `build moduleCatalog` and `publishLocal` pass **69,006 assertions across 22 suites**, including **65,165 motion**, **75 pathfinding**, **70 route-following** and **50 mining-policy** assertions. The catalogue remains **125 modules / 1,140 controls**. Optional Windows schematic-library symlink cases remain skipped where creation is unavailable. Build and feed match SHA-256 **`3a58543049ab2fb58bb3c63165af0f280d93f8c145e5b846d5e38a1aca0ab798`**. A deterministic out-of-window frontier fixture uses **5 expansions instead of 81** with the same node budget; this is a synthetic search result, not observed Minecraft FPS or travel speed.

**The user elected to perform the remaining in-game checks.** No native Minecraft client, new gameplay acceptance run or new capture was produced for 0.10.3. Camera feel, mining/descents and pathfinding in the user's terrain remain manual checks. The earlier evidence below applies to its recorded versions and does not establish native acceptance of these motion changes. The [0.10.3 acceptance record](production-validation/native-0.10.3/acceptance.json) records deterministic results, artifact/log hashes and pending gameplay checks. Delivery verifies archive integrity and matching build/feed/package/install hashes while preserving the existing instance's user data, other mods and Java selection.

## Earlier 0.10.2 evidence

# Local requests, recovery, X-ray and Auto Eat — October 14, 2026

Analyzer **0.10.2** accepts local requests such as **get me 600 sand** and **get just 37 diamonds**, preserving the material and final inventory target. Selected/named schematic requests resolve an actual local file before material/site review and explicit Start. Mining clears checked natural cover, retries visible faces, refreshes obstructed resource batches and waits for real walking/drop pickup. Tree recovery uses verified walking/vines/ladders or a rooted-trunk descent. Workstation placement checks real nearby faces/heights and recovers acknowledged worker-placed tables while preserving existing/observed replacements. Site selection verifies actual loaded support instead of trusting unavailable client heightmaps.

**Render → X-ray** adds Fade terrain (15% opaque), Terrain outlines and Ores only, retaining real selected ore textures. Workstation/storage models and nearby entity highlights are enabled by default through separate controls. **Player → Auto Eat** now treats its hunger setting as a trigger and continues until full hunger, restoring input/slot afterward. See [Automation](AUTOMATION.md) and [X-ray](XRAY.md).

Java 21 `build moduleCatalog publishLocal` passes **4,455 assertions across 21 suites**. The catalogue has **125 modules / 1,140 controls**. Optional schematic-library symlink cases are skipped where Windows denies them. Build and published feed match SHA-256 **`4e8e6b8fb34260d99653fa666b52d5583941bb7c5a3d57e2aaf9922f984cb00b`**.

The completed native mining field test passes **28,195 sustained controller assertions plus 16,541 surrounding checks**. It actually collects **96 cobblestone through grass/dirt cover**, returns through preserved access and collects **600 sand** through normal top-down breaking and pickup. Server/client inventories agree; protected bounds, hazard invariants, health and input cleanup pass.

The subsequent combined run completed actual travel, mining, native construction, material-first imported schematic construction, Nether/Overworld transport, **51 progression assertions** and **60 inventory assertions**, including real smithing, crafting, enchanting and equip. **The user explicitly requested stopping the remaining in-game checks and delivering now.** It was stopped during tool recovery and is not recorded as a completed regression. Remaining tool/safety/input/tree/workstation integration, Auto Eat gameplay and the latest X-ray utility/entity controls are left for the user's manual checks.

Earlier focused component runs observed **1,724 descent assertions**, **15 workstation assertions** and **41 renderer-only X-ray assertions**. The latter verifies actual native ore textures, alpha38/255 terrain, thin terrain edges, filtering, disable/resource reload and GL state; it predates the new workstation/storage/entity additions. Those earlier component observations are separated from final-build verification. No complete final gameplay or replacement-renderer/GPU/shader-pack compatibility claim is made.

The isolated fixtures use their own Analyzer-Smoke save, offline test player and documented initial terrain/tools/materials. Production controllers use ordinary interactions and authoritative state. No personal world is used. **24 captures** use `screenshots/analyzer-native-0.10.2-*`; `base-*` identifies earlier focused component images. Historical image bytes are preserved. The [acceptance record](production-validation/native-0.10.2/acceptance.json) records exact scope, pending checks and capture/log hashes. Prism packaging verifies the remapped JAR, archive and matching dependency source/notices; installation preserves the existing instance's worlds, profiles, settings, schematics, unrelated mods and Java selection.

## Earlier 0.10.1 evidence

# Task startup and schematic browser — October 14, 2026

Analyzer **0.10.1** fixes the menu click being treated as gameplay input on the first native task dispatch. Explicit Start/Resume enter **WAITING_INPUT**, perform no task actions while the menu, focus or held controls prevent handoff, then automatically start after release. The wait has no timeout that grants unchecked input. Fresh manual input pauses immediately and requires explicit Resume; Stop cancels a pending start. Checkpoints still restore paused. HUD notices distinguish waiting, manual pause, unmet requirements and actual protection, naming the bound controls when held.

**Automation → Schematics → Browse** lists the current Minecraft instance's `schematics` folder. It supports subfolders, filename search, Refresh, Open folder, file metadata and explicit Use selected. Choosing a file resolves its real path and returns to material/site review before Start. Listing is bounded and does not parse schematic contents; existing import formats, file limits and source hashes remain enforced. Drag and drop remains available.

Java 21 `build moduleCatalog` and `publishLocal` pass **3,982 assertions across 17 policy/configuration suites**, including **111 input-handoff assertions** and **24 real temporary-folder library assertions**. Windows denied symlink creation, so those optional platform cases were explicitly skipped. The module export remains 124 modules and 1,124 controls.

- `runNativeAutomationSmoke -PanalyzerNativeScenario=task-ui` passes **508 pipeline checks plus 495 sustained UI/controller assertions**, with four captures. Real rendered browser controls select a local Litematica file and review its material-first plan. Held Start and Resume remain inert beyond the old ten-tick grace, release starts actual server-backed travel, fresh movement pauses without automatic resumption, and Stop prevents a cancelled pending task from starting. Final movement/action cleanup, shaders and GL state pass.
- `runNativeAutomationSmoke -PanalyzerNativeScenario=guard` passes **721 pipeline checks plus 53 guard assertions**, with four captures. Real AI zombie defense, resumed mining, available-food recovery, critical-health creeper retreat, neutral-mob filtering, Pause/Resume and cleanup remain operational after the input changes.

The startup regression supplies physical-control snapshots through a strictly development-only fixture; it does **not** inject operating-system hardware events. Screens, production task controllers, normal interactions and authoritative Survival player movement are real. Both isolated native logs complete without ERROR/FATAL entries. No personal world or installed instance is used for testing. The complete mining/building/portal/gear pipeline was last observed in **0.10.0** below and was not rerun in full for this focused update.

Eight current captures use `screenshots/analyzer-native-ui-*`; the [acceptance record](production-validation/native-0.10.1/acceptance.json) preserves their hashes and the focused log hashes. The current remapped JAR and local feed match SHA-256 **`a92b7edfe73951b8838b1dd38c930b3e6ef93b0ecf3a1536767a48b9014dffbe`**. Prism packaging checks its embedded version/hash, dependencies, archive integrity and exclusion of accounts/config/saves. Updates target the located **Analyzer-0.10.0-Prism-1.21.4** instance and preserve its settings, worlds, profiles, unrelated mods and selected Java runtime. Its folder name does not identify the embedded installed version.

## Earlier 0.10.0 complete automation evidence

# Native automation replacement — October 14, 2026

Analyzer **0.10.0** removes Baritone and its native helper from the current Gradle dependencies, mod metadata and Prism package. The original native controllers use Minecraft/Fabric APIs. Planning stays local, reviewed goals require explicit Start/Resume, checkpoints restore paused, and only observed inventory/world state completes a stage. Historical Baritone tests below are evidence for those earlier releases only.

The isolated Java 21 native Survival acceptance uses `run-native-automation/`, its own `Analyzer-Smoke` save and the offline `AnalyzerDev` test player. It asserts that no Baritone mod loads. Server commands establish documented test preconditions; production controllers then perform normal movement, breaking, placement, slot clicks and portal interactions. No personal world or installed instance is used for these fixtures.

The final Java 21 `build moduleCatalog` passes **3,847 assertions** across 15 policy/configuration suites and exports 124 modules with 1,124 controls. The complete `runNativeAutomationSmoke` sequence passes **13,057 sustained checks** without Baritone, plus the separately reported controller assertions below. The full native log has no ERROR/FATAL entries; final input and GL-state checks pass.

- Original A* navigation walks around a real wall, respects the configured visible turn limit, pauses without continuing movement, and resumes to the actual goal. Mining removes real stone and receives actual cobblestone.
- Native construction places and consumes ten cobblestone plus an exact upper slab state. A real two-palette Litematica file is decoded and reviewed through the file-drop UI. Its missing cobblestone is mined and planks are crafted; both materials are verified before the first schematic block is placed, and both final states are observed.
- The portal stage consumes **10 obsidian and 13 corner/stair supports**, ignites the frame normally, enters the Nether and returns to the Overworld. Expected loading screens and the actual portal cooldown are handled without changing game state. This fixture supplies the portal kit; it does not establish a complete empty-inventory diamond/obsidian expedition.
- **51 progression assertions** verify three real template duplications, four normal smithing upgrades, preservation of Protection IV/Unbreaking III, and actual server-equipped netherite armor. The fixture supplies the first template, diamonds, ingots and enchanted diamond gear; autonomous bastion discovery is not implied.
- **60 inventory assertions** verify mixed-species wood recipes, cursor-safe cancellation, table/tool/furnace crafting, smelting, diamond equip, real enchanting/anvil upgrades and normal console Review input. XP, table/bookshelves and upgrade books are fixture prerequisites.
- **26 tool-recovery assertions** start with no tools, wood or crafting table in inventory. The controller harvests actual nearby logs/stone and crafts its prerequisites, then recovers after two normal pickaxe breakages using the same controller. Only terrain setup and accelerated one-durability wear-out preconditions are fixtures; replacement items are crafted through production actions.
- **53 Survival-guard assertions** verify real AI zombie defense while mining with ordinary iron armor, resumed drop collection, main-inventory food consumption and Pause/Resume recovery, critical-health creeper retreat around real lava, neutral-mob exclusion and owned-input cleanup. Armor is removed before the critical-health test.
- The Windows PowerShell 5.1 updater passes **100 isolated integration checks**, including exact legacy-JAR retirement, recoverable backups, unrelated/modified-mod preservation, read-only checks, rollback and locked-file behavior.

All **14 current captures** use `screenshots/analyzer-native-automation-*`. The machine-readable [acceptance record](production-validation/native-0.10.0/acceptance.json) records counters, capture hashes and local log hashes. Historical capture bytes and Baritone evidence retain their old names and remain separate. Commands and evidence logs are `build/reports/native-automation-build-checks.log`, `native-automation-runtime.log` and `updater-native-final-tests.log`.

The verified release is published to the local feed and installed in the existing **Analyzer-0.9.0-Prism-1.21.4** folder; the installed embedded version is **0.10.0**. Build, feed, imported ZIP and the single installed Analyzer JAR match SHA-256 **`d68ea74143a7301e421ee7f7dcd2fc09a7e5f53d4c40a734d141bc123b561dbf`**. Minecraft was closed during installation. The exact old official Baritone JAR was moved to a recoverable `.analyzer-updater/retired-mods` backup; the previous Analyzer JAR has its own updater backup. Fabric API remains byte-identical and the active mods folder now contains only Analyzer and Fabric API.

Prism had been launched since the earlier empty-instance snapshot, so delivery took a fresh baseline before touching mods. All **22 current configuration/launcher files**, including 17 saved config/profile files, remain byte-identical. The user's Prism-managed Java 21.0.7 selection, updater configuration and pre-launch command are preserved. No world files were present in this instance's delivery snapshot. The rebuilt Desktop Prism ZIP passes archive integrity, current-JAR hash, branding, dependency, documentation and account/config-exclusion checks; its portable Java preference still inherits Prism settings.

Current scope and unmet requirements are described in [AUTOMATION.md](AUTOMATION.md). Public-server behavior, every terrain/schematic combination, autonomous XP/book/template discovery and performance superiority over another bot are not established by these tests.

## Earlier 0.9.0 evidence

# Server privacy and fresh Prism instance — October 12, 2026

Analyzer **0.9.0** adds **Client → Server Privacy**, enabled by default with eight independent saved protections. Original packet filtering reports the vanilla wire brand, suppresses mod channel announcements and unknown custom channels in strict mode, filters known packs to Minecraft's actual namespace/id/version tuple, and declines plugin login queries using the native unsupported reply. Source-scoped remote translation/keybinding protection includes packet text and sign-editor NBT lines before editable strings are flattened. The native server resource-pack Downloader strips identifying HTTP headers and checks URL destinations, every DNS answer and redirects within an exception-safe request scope.

These defenses are informed by the verified primary [OpSec source](https://github.com/aurickk/OpSec), without copying or bundling its GPL implementation. The user could not locate the Discord-only Autism Defender project, so its implementation is not claimed. [SERVER-PRIVACY.md](SERVER-PRIVACY.md) records every setting, compatibility tradeoff and limit. Explicit saved opt-outs remain valid; missing fields in older config/profile files use the new defaults. Normal gameplay packets, stock text/bindings, accepted server-pack translations and integrated-server traffic remain usable.

## Final verification

- Java 21 **build / publishLocal** pass **2,596 assertions**: 1,641 core, 38 ESP layout, 46 planner, 10 settings lease, 102 Combat, 30 Movement, 18 HVH, 489 gameplay configuration, 71 privacy configuration, 60 network policy and 91 resource-pack policy checks. The actual registry contains **124 modules / 1,108 exposed settings / 1,100 canonical settings**, with eight HUD aliases. Five new full starter snapshots include all eight privacy protections and Studio layout; older installed files receive missing defaults without being rewritten.
- Final native privacy acceptance passes **250 assertions**, **four captures**, **25 phases** and **165 ticks**. Its network suite passes **86 checks** with actual brand/login/known-pack codecs, transformed ClientConnection pipeline installation, queued writes, direct writes, callbacks, flushes, live toggles and actual in-process LocalChannel transport. Normal keepalive and resource-pack status packet objects retain identity. The required empty known-pack response and login transaction IDs are preserved.
- The native text suite passes **57 checks**, covering all five packet text codecs, generic disk decoding remaining unmarked, explicit fallback formatting, shared-content copies/cache epochs, nested arguments/siblings/entity/item hover content, exact vanilla versus mod bindings, real loaded resource-language provenance, renamed vanilla keys, local menu text and opt-outs. Real SignText NBT is initially unmarked; the actual SignEditScreen constructor snapshots protected strings after the sign hook, without opening/sending the editor or creating a world.
- Resource tests invoke the transformed pack-loader factory, its actual anonymous header builder, marked Downloader and NetworkUtils HTTP boundary. Injected DNS and stub HTTP prevent external requests while native hashing, body/result, content-length and finish callbacks pass. Master opt-out and unrelated Downloader bypass are tested. Pure tests cover initial/redirect destinations, mixed public/private DNS answers, selected reserved ranges, malformed/credential URLs, failures, connection cleanup and thread-local restoration.
- Visual QA found title-only glass sampling the previous menu frame, producing duplicate branding. Catalog and auxiliary title screens now paint a fresh procedural scene before optical capture. In-world capture-before-material order is retained. The native regression contaminates incoming frames with bright green before rendering both **Studio / Click GUI × Glass / Solid** materials at zero motion. Actual surface pixels match the clean baseline with **maximum mean channel difference 0.0**. Final four title PNGs replace the initial 0.9.0 QA captures and were visually reviewed.
- Final in-world layout/optics regression passes **919 assertions**, **11 captures**, **98 phases** and **356 ticks**. Legacy HVH Load/Back, both layouts, category paging/drag/collapse, settings, parent returns, HUD logo choices, theme/material independence and reload pass. The stripe lens still produces **23 physical-pixel movement**, interior mean difference **0.01710591761317748**, outside/corner difference **0** and correct GL state restoration.
- Final Home regression passes **491 assertions**, **31 captures**, **66 phases** and **422 ticks**, including actual world/server/options controls, native FOV changes/restoration, both layout parent returns, Panorama/Orbit, Vanilla fallback, six window/GUI cases, zero motion and resource reload. Realms/Quit controls are checked without invoking services or joining a world/server. The layout/Home matrix covers 1280×800 and 1920×1080 at requested GUI scales 2/3/4; 1280 GUI4 is clamped to effective scale3.

All **46 current PNGs** are copied unchanged under `screenshots/analyzer-privacy-*`: four title, eleven in-world layout/optics and thirty-one Home captures. Historical 0.8.0 repo captures retain their original prefixes and bytes. The final three native logs contain no ERROR/FATAL entries or GL/shader failures. Evidence: `build/reports/privacy-runtime.log`, `privacy-liquid-runtime.log`, `privacy-home-runtime.log` and `privacy-publish.log`.

This is specific information-leak reduction, not a guarantee of undetectability. Other mod HTTP/sockets, ordinary actions/chat, client settings, account identity and IP remain outside concealment. Strict custom channels can make modded servers or network-dependent mods incompatible. DNS validation leaves a lookup/connect race; it is not address-pinned transport or complete SSRF isolation. No public-server acceptance test, authenticated Realms request, full gameplay/task rerun, performance benchmark or other GPU/shader-pack validation is claimed.

## Fresh Prism delivery

During this update the user deliberately removed the previous Analyzer instance in preparation for a new one. The verified native instance is created at **`C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.9.0-Prism-1.21.4`**, with the matching import ZIP **`C:\Users\sould\Desktop\Analyzer-0.9.0-Prism-1.21.4.zip`**. This fresh delivery contains no user worlds, config, profiles or launcher credentials; it does not restore the data removed by the user. Other Prism instances and launcher preferences remain untouched.

The final remapped build, feed, ZIP and single installed **analyzer-0.9.0-e66440e91184.jar** share SHA-256 **`e66440e91184bd6a0a298c4f8591035d035d95dff72c0d2a132079bda443be62`**. Minecraft was closed for installation. The strict updater verifies **up-to-date**; the local manifest connection and Prism pre-launch command are enabled. The new instance inherits Prism's Java choice rather than replacing it.

Archive CRC, matching dependencies, fonts/logos, 34 registered mixins including eleven privacy hooks, removed legacy screen classes, current catalog/defaults and five full Studio snapshots are audited. Corresponding Baritone sources/notices, Fabric API license, updater and all current documentation/captures are included. The fresh stage is empty before packaging; its files match the installed instance. A before/after hash audit preserves **21 launcher/other-instance files** and the **five unrelated instances**. No backup of a replaced Analyzer JAR is required because the former instance was already removed. Final evidence is `build/reports/prism-privacy-delivery.json`.

Historical sections below describe their recorded releases and then-current instances.

---

# Liquid glass and retired layouts — October 12, 2026

Analyzer **0.8.0** removes the Workspace, Classic and Compact screen implementations. The only module layout choices are **Studio / Click GUI**. The new Click GUI has native shader category columns, shared typed controls, nine-category paging, drag/collapse and independent scrolling. Old saved layout strings migrate to Studio before validation and canonical/alias precedence; opening an old premade HVH profile and selecting **Back to modules** returns to Studio. Profile files and other saved values are preserved. All five new bundled full starter snapshots select Studio. Classic remains a separate theme and profile name, not a layout.

The original **LiquidGlass** renderer and GLSL shader copy the actual incoming draw framebuffer once per owned scope, then reuse that immutable scene for lens distortion, subtle frost/dispersion, smoked body transmission and curved-edge glints. Glass theme cards work with Solid menu material; menu material still controls the surrounding background treatment. HUD Glass, Home panes/buttons and auxiliary screens share optical surfaces. Existing themes/accents and exclusive **Analyzer logo / A logo** watermark selection remain independent. [LIQUID-GLASS.md](LIQUID-GLASS.md) documents controls, migration and final captures.

## Final checks

- Java 21 **build / publishLocal** pass **2,364 assertions**: 1,631 core, 38 ESP layout, 46 planner, 10 scoped-settings lease, 102 Combat, 30 Movement, 18 HVH and 489 configuration checks. The expanded core regression exercises legacy canonical/alias values, malformed precedence, config/profile loading, complete HVH state preservation and unchanged profile bytes. The registry remains **123 modules / 1,100 exposed settings / 1,092 canonical settings**, including eight aliases.
- Actual Minecraft **Liquid/layout** acceptance passes **959 assertions**, **11 screenshots**, **98 phases** and **356 fixture ticks**. It uses the real old-profile Load and Back controls, all five current bundled profiles, the HUD layout setting, nine category jumps, board paging, collapse/drag, typed settings/config reload, numeric entry, Profiles/HUD parent return and independent HUD/menu theme/material combinations. No navigation operation starts automation.
- The optical stripe fixture renders the same scene with refraction off/on while holding opacity, frost and glints fixed. Mean interior channel difference is **0.01710591761317748**, mean outside difference **0**, curved-edge stripe movement **23 physical pixels**, and clipped-corner difference **0**. The fixture deliberately binds a different read framebuffer, nonzero active texture unit and texture sentinels; actual draw-source capture and restored GL state pass. Multiple surfaces share exactly one copy per completed owned scope. These pixel checks demonstrate scene displacement, not just alpha blending.
- The Home regression passes **491 assertions**, **31 screenshots**, **66 phases** and **405 ticks**. It covers actual world/server/options pages and callbacks, FOV changes/restoration, both current module layouts returning to the exact Home parent, saved Vanilla fallback, Panorama/Orbit switching, blur pixels, zero motion and reload. Realms/Quit controls are checked without invoking online services, joining a world/server or exiting early.
- Both runs cover **1280×800 / 1920×1080** at requested GUI scales **2/3/4**; Minecraft clamps 1280 GUI4 to effective scale3. Both exclusive watermark logos are checked across all four HUD themes. Shader programs recompile after resource reload, zero motion preserves control geometry, and both final logs have no ERROR/FATAL entries or GL/shader failures. All **42 final PNGs** are copied unchanged to `screenshots/analyzer-liquid-*`; primary layouts, optics, HVH return, HUD logo choices and high-scale Options captures were visually reviewed.

Evidence: `build/reports/liquid-layout-runtime.log`, `build/reports/liquid-home-runtime.log` and `build/reports/liquid-publish.log`. The renderer is original code inspired by the supplied reference, not Apple's implementation. This acceptance does not repeat the historical full gameplay/task matrix or claim a frame-performance benchmark, authenticated Realms validation, or other GPU/shader-pack testing.

## Prism delivery

The user imported the preceding 0.7.2 ZIP and removed the older instance directory. The actual existing instance is now `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.7.2-Prism-1.21.4`; that folder records the import, not the installed runtime version. Minecraft was closed for the strict update. Exactly one **analyzer-0.8.0-0ea233a09a0f.jar** is installed, and the subsequent strict check reports **up-to-date**. The previous 0.7.2 JAR is backed up.

The final build, feed, installed JAR and import archive share Analyzer SHA-256 **`0ea233a09a0f7ee33a391368095a1ebcb9309620ceb9b410e90e5c13a6244543`**. All **109 protected game/dependency/launcher files** and **11 saved profiles** remain byte-identical, including the active configuration, worlds, friends, unrelated mods, feed connection, pre-launch command and Prism-managed Java selection. Legacy layout migration operates in memory rather than replacing saved user files.

`C:\Users\sould\Desktop\Analyzer-0.8.0-Prism-1.21.4.zip` contains the verified runtime, matching Fabric API/Baritone, dependency sources/notices, updater, current documentation and 42 new native captures. The distribution audit checks ZIP CRC, matching hashes, 23 mixins, current layout metadata, absence of all three removed screen classes, original shader resources, unchanged supplied logo bytes and five full Studio starter snapshots. The package inherits launcher Java settings and contains no account credentials, saved worlds or user configuration. Final integrity/preservation evidence is `build/reports/prism-liquid-delivery.json`.

Historical sections below describe their recorded releases, including former layouts and counts.

---

# Exclusive watermark logo choice — October 12, 2026

Analyzer **0.7.2** adds **Watermark → Logo → Analyzer logo / A logo**. The full Analyzer logo is the default for missing fields. Gameplay and the HUD editor draw only the selected supplied asset; there is no combined-logo choice. Selection saves with the active config and named profiles. The existing panel footprint, position, opacity, scale and version/theme caption are preserved. Home and Studio branding are unchanged by this update.

Java 21 **build / publishLocal** pass **1,054 assertions**. The final actual-Minecraft Studio/HUD fixture passes **574 assertions**, **88 phases**, **344 ticks** and **22 run captures**. The native settings control cycles Analyzer → A → Analyzer. Both choices survive config reload; saved-profile Load restores Analyzer, and a temporary A-logo profile roundtrip preserves every existing development profile hash. Real asset-frame counters advance only for the selected logo in closed-menu gameplay and in the HUD editor, while the other counter remains unchanged. Disabling Watermark submits neither image or widget panel. Counter checks exclude Home/Studio rendering, where both resources are independently valid.

All 17 HUD widgets still submit native shader surfaces across four themes; numeric title pixel clarity, FPS framebuffer differences, six window/GUI cases and reload also pass. The final log has no ERROR/FATAL entries. Five new PNGs are copied unchanged to `screenshots/analyzer-watermark-*.png` and visually reviewed. Historical 0.7.1 captures/logs remain intact. No full Home regression or gameplay action sweep is repeated for this watermark-only change. Evidence: `build/reports/watermark-runtime.log` and `build/reports/watermark-coverage.json`.

The registry contains **123 modules / 1,100 exposed settings / 1,092 canonical settings**, including eight HUD appearance aliases. Five regenerated canonical starter snapshots contain the new default; existing installed profiles are not overwritten.

The final build/feed/install/archive Analyzer SHA-256 is **`4501ff58cc39b377642a4c14bbe42f5b8db6b7f8bd94efc85d49f65b95fc1913`**. Minecraft was closed for the strict update of `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.7.0-Prism-1.21.4`. Exactly one **analyzer-0.7.2-4501ff58cc39.jar** is installed and the final updater check reports **up-to-date**. The previous 0.7.1 JAR is backed up. All **109 protected game/dependency/launcher files** and **11 saved profiles** remain byte-identical, including active configuration, worlds, friends, unrelated mods, updater connection and Java preferences.

`C:\Users\sould\Desktop\Analyzer-0.7.2-Prism-1.21.4.zip` includes the matching runtime/dependencies, updater, sources/notices, current docs and five watermark captures. ZIP CRC, exact Analyzer hashes, unchanged logo resources and five canonical snapshots are verified. The package inherits Prism's Java settings and contains no accounts, saved worlds or user configuration. Final delivery evidence is `build/reports/prism-watermark-delivery.json`.

---

# Complete Studio, shader HUD and supplied branding — October 12, 2026

Analyzer **0.7.1** extends Studio's native GLSL surfaces and Lato to Profiles, Automation, HUD editing and numeric entry. Forced Workspace shortcuts are removed from the current Studio workflow. All 17 HUD widgets, Radar and protection notices use native shader panels; keycaps, item slots and target health bars use rounded shader geometry. Projected ESP label panels are styled without changing world-space target/depth rules. The user-supplied transparent monogram and wordmark replace the generated Home branding and appear in Studio and the HUD watermark. [Current controls and captures](STUDIO-COMPLETE.md) documents the update.

## Final checks

- Java 21 **build / publishLocal / moduleCatalog** passed **1,054 build assertions**. The catalog remains **123 modules / 1,099 exposed settings / 1,091 canonical settings**, with eight appearance aliases; there are no new settings or gameplay policies in this presentation update.
- The final native Studio/HUD fixture passed **542 assertions**, **17 screenshots**, **78 phases** and **303 ticks**. It checks real Profiles save/load/delete with every pre-existing development profile hash preserved, numeric invalid/apply/cancel behavior, actual Automation plan review without starting a task, exact parent navigation, editor dragging/persistence, all 17 HUD shader render paths in four themes, Radar, supplied logo rendering, six window/GUI cases, zero motion and resource reload.
- Actual framebuffer differences confirm the new FPS panel is drawn (mean channel difference **0.037656**). Pixel inspection verifies the numeric title remains crisp (sharp-edge fraction **0.143414**). Visual QA caught the initial numeric modal double-blur: Minecraft's default background was running inside the modal's scaled canvas. The final override draws its backdrop once before the modal; the final capture and pixel regression pass.
- The separate title-only Home regression passes **499 assertions**, **31 captures**, **70 phases** and **433 ticks**, covering native world/server/options pages, real FOV changes/restoration, all four module layouts returning to Home, saved vanilla fallback, background switching, visible blur pixels, six window/GUI cases, zero motion and reload. Realms/Quit controls are checked without invoking online services or an early exit.
- Captures cover **1280×800 / 1920×1080**, requested GUI scales **2/3/4**. Minecraft clamps requested GUI4 at 1280 to effective scale3. All **48 current captures** are copied unchanged under `screenshots/analyzer-refresh-*`; historical Studio/Home prefixes remain intact. Primary Home, HUD, auxiliary, numeric and editor captures were visually reviewed. Both final runtime logs contain no ERROR/FATAL entries or shader/GL failures.
- Safety notice presentation uses a labeled fixture snapshot and actual HUD callback without running a task or creating danger. The real protection controller and gameplay outcomes are unchanged; this presentation fixture does not repeat the full historical Survival/task matrix. Inventory/equipment/target widget data uses the isolated fixture's actual state. HUD Glass is translucent over the live world, rather than applying full-screen blur; explicit older module layouts remain optional. No live Prism launch, authenticated Realms request, public server join, performance benchmark or other renderer/GPU validation is claimed.

Evidence: `build/reports/studio-refresh-runtime.log`, `build/reports/home-refresh-runtime.log` and `build/reports/studio-refresh-coverage.json`. Both supplied PNGs remain byte-identical RGBA images with alpha extrema **0–255**, including their original partial-alpha glow. Resource reload and native frame counters verify actual texture rendering.

## Prism delivery

The user imported the preceding 0.7.0 ZIP and removed the old 0.5.2 instance directory. The actual existing instance is now `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.7.0-Prism-1.21.4`; it is reused rather than duplicated. The updater remains connected to the local feed through its configuration and pre-launch command.

The final build, feed, installed JAR and import archive share Analyzer SHA-256 **`f9f5bbb21096643a42ac998f2270fdf87a1d103fd560d030bb1088f4453481c8`**. Minecraft was closed for the strict update; **analyzer-0.7.1-f9f5bbb21096.jar** is the single installed Analyzer JAR and the updater's final check reports **up-to-date**. The replaced 0.7.0 release is backed up.

All **109 protected game/dependency/launcher files** remain byte-identical, including the active Analyzer config and **11 saved profiles**. Worlds, friends, keybinds, appearance, widget placement/scale, unrelated mods and Prism-managed Java preferences are preserved. The Desktop `Analyzer-0.7.1-Prism-1.21.4.zip` contains the current runtime, matching dependencies, sources/notices, updater, current docs, 48 new native captures and the supplied logo resources. Archive CRC, metadata, 23 mixins, five canonical snapshots, shader/font resources and exact source PNG bytes/transparency are audited. It inherits launcher Java settings and contains no accounts, user configuration or saved worlds.

Final delivery evidence is `build/reports/prism-studio-refresh-delivery.json`. Historical validation below applies to its recorded builds.

---

# Home screen and all-module sweep — October 12, 2026

Analyzer **0.7.0** opens the custom Home by default over **Minecraft panorama**, as selected by the user. Optional **Orbit** is original procedural GLSL. Singleplayer, Multiplayer and Options retain real Minecraft data, controls and callbacks inside translucent panels. HUD Studio exposes the four canonical Home preferences through four aliases; all four module-menu layouts and existing themes remain available. [Home controls/captures](HOME-SCREEN.md) and the [comparison photos](HOME-REFERENCES.md) document the direction.

## Every-module quick sweep, preceding the Home implementation

The isolated Minecraft fixture passed **123 modules × three contrasting settings combinations = 369 live variants**, with **13,051 sweep assertions** plus **25 focused Combat/HVH assertions**. It accepted **11,886 setting writes**, covering every choice, both booleans and numeric min/mid/max/clamps, rejected **1,091 invalid writes**, and completed **seven active-config plus seven profile roundtrips**. Nine category captures accompany the [per-module results and limits](MODULE-TESTS.md).

That sweep used the 0.6.0 catalog before adding Home: **1,091 exposed / 1,087 canonical settings**. Empty inventory and creative-mode prerequisites gate equipment/container/task/death dependent actions; enabling and rendering alone does not prove their effects. Live combinations are three per module, not every combination. Coord Snapper remains behind its pause gate and the system clipboard is untouched. No user world, launcher configuration or public server was used. Final evidence is `build/reports/all-module-sweep-runtime.log` and `build/reports/all-module-sweep-coverage.json`.

## Final Home acceptance

- Java 21 **build / publishLocal / moduleCatalog** passed **1,054 assertions**: 321 core, 38 ESP layout, 46 planner, 10 scoped-settings lease, 102 Combat, 30 Movement, 18 HVH and 489 configuration checks. The 24 new core checks cover defaults for older configs, shared Home aliases, independent appearance, typed persistence, invalid values and reset.
- Actual Minecraft 1.21.4/Fabric native acceptance passed **499 assertions**, **31 screenshots**, **70 phases** and **419 fixture ticks**. It checks actual custom-title startup, native world/server/options controls, Language/Accessibility/Credits parent return, all four Analyzer layouts, the registered menu shortcut returning to the exact Home parent, saved vanilla fallback and re-enabling Home.
- The native FOV slider changes Minecraft's actual option and restores it. All ten Options actions, FOV and native world/server controls have real visible hit centers at **1280×800 / 1920×1080**, requested GUI scales **2/3/4**. Minecraft clamps 1280 GUI4 to effective scale3. Chrome is independent of GUI scale; real list rows retain Minecraft's font. Very short panes fall back to native pages with the same Home parent; the dedicated acceptance matrix does not cover every ultrawide aspect ratio.
- Pixel readback verifies actual content in Panorama and Orbit, retained backgrounds outside translucent panels and amber disk pixels through the blurred Orbit pane. The first counter-only run missed a black blur source; visual review found it, and the final shader copies the actual incoming draw framebuffer. Native hover/narration/tooltip lifecycles are retained. A later capture review found native LAN status centered on the whole screen; the scoped renderer now centers that label inside the server panel, without changing discovery or narration.
- Motion zero freezes native panorama yaw and submits zero Orbit time across actual frames. Blur on/off and resource reload pass with healthy Home/UI shaders and clean GL state. The final log has no ERROR/FATAL entries. Final PNGs were copied unchanged to `screenshots/analyzer-home-*.png`; the six-size/page capture matrix and primary captures were visually reviewed.
- Realms and Quit controls are checked without online service calls or an early exit. The fixture never loads a world or joins a server; its saved-server list must be empty before opening Multiplayer. No Prism launch or authenticated Realms validation is claimed.

The current catalog contains **123 modules / 1,099 exposed controls / 1,091 canonical settings**, including **eight HUD Studio presentation aliases**. This change adds Home preferences and presentation; gameplay policies remain those exercised in the preceding sweep. Final runtime evidence is `build/reports/home-runtime.log`. Original shaders and separate-copy blur do not simulate physical lensing/refraction. Iris/Sodium and other GPUs/renderers remain unverified.

## Home Prism delivery

The final remapped JAR, local feed, installed JAR and import archive share SHA-256 **`b0ec5109cef3f095a25dd6f67a5c878ef1d3d173d018f6ba0497ce2831f05bfc`**.

- The existing instance is `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.5.2-Prism-1.21.4`; its old folder name records the original import. Minecraft was closed for the strict update. The updater installed **analyzer-0.7.0-b0ec5109cef3.jar** and its final check reports **up-to-date**; exactly one Analyzer JAR is installed and the replaced 0.6.0 build is backed up.
- **All 78 protected game/dependency/launcher files** remain byte-identical, including active Analyzer configuration and all **11 saved profiles**. Missing Home preferences receive custom-Home/Panorama defaults. Current Studio/Rose/Solid preferences, worlds, friends, keybinds, unrelated mods, the feed connection, pre-launch command and Prism-managed Java selection are preserved.
- `C:\Users\sould\Desktop\Analyzer-0.7.0-Prism-1.21.4.zip` includes the current JAR, matching Fabric API and official Baritone, dependency sources/notices, updater, current docs, original comparison photos, 31 Home captures and nine module-sweep captures. The portable package inherits Prism's Java settings and contains no accounts, saved worlds or user configuration. Archive CRC, embedded shader/font resources, 23 registered mixins, five canonical snapshots and matching build/feed/install/archive hashes are audited.

Final integrity/preservation evidence is `build/reports/prism-home-delivery.json`. Historical validation below applies to its recorded versions.

---

# Analyzer validation

## Analyzer 0.6.0 shader Studio — October 12, 2026

Studio is a new native menu built around the third supplied reference: a smaller centered black shell, top category tabs, three columns of gray cards, settings inside expanded cards, Lato typography and pink Rose controls. Original GLSL signed-distance shaders draw the rounded surfaces, soft shadows and control glow. Minecraft supplies native text/input; ImGui and browser rendering are not used. The existing Workspace, Classic and Compact remain selectable through HUD Studio.

- Final `build runStudioSmoke` succeeds with **1,030 build assertions**: 297 core, 38 ESP layout, 46 planner, 10 scoped-settings lease, 102 combat, 30 movement, 18 HVH and 489 configuration assertions. Thirteen added core checks cover Studio/Rose defaults, matching HUD aliases, persistence and valid older preferences. `publishLocal moduleCatalog` also succeeds.
- **91 native runtime assertions** pass in 245 fixture ticks with **16 actual framebuffer captures**. The renderer's successful shader-surface counter increases; its GLSL programs and GL state remain healthy. Tests exercise actual module inspection/switches, search/category contents, slider dragging and exact entry, long settings/key capture, HUD layout/theme/accent aliases, canonical save/reload, parent return, resource reload and zero motion. UI interactions do not create gameplay attacks, placement, inventory transactions or owned movement.
- Inspecting the last Cosmetic module reveals its actual card. Wheel input at an expanded card's scroll boundary continues through the surrounding catalogue. Those two production fixes were compiled into the final successful run.
- Captures cover Glass and Solid material, all four menu themes, Rose appearance settings, retained Classic/Compact and **1280 × 800 / 1920 × 1080** at requested GUI scales 2/3/4. Minecraft clamps requested 1280 GUI 4 to effective scale 3; other combinations reach the requested scale. Solid leaves the world crisp and does not increase the scene-copy counter. All 16 captures were visually reviewed and copied unchanged to `screenshots/analyzer-studio-*.png`.
- The final native log contains no ERROR/FATAL entries, shader failures or GL errors. An initial fixture-only failure involved a newly switched screen that had not rendered its scroll diagnostic yet; the fixture waits for the live control before interacting. The final proof is `build/reports/shader-studio-runtime.log`. Source/class timestamps confirm the final scrolling fixes were included.

The registry still has **123 modules / 1,091 exposed settings / 1,087 canonical settings**. Gameplay/controller code is unchanged by this menu renderer; historical gameplay/production checks below apply to their recorded builds. Narrator audio, a full Automation state/capture matrix, steady-frame benchmarks, exhaustive contrast measurements and other renderer/GPU combinations remain separate from this focused acceptance run. [Studio controls and captures](UI-OVERHAUL.md) records the delivered UI.

## Studio Prism delivery — October 12, 2026

The verified remapped Analyzer 0.6.0 JAR, local feed, installed JAR and import archive share SHA-256 **`785e9902b7b0908d6b5b41c84baf6d36ea448bd4d551ac455ab07f7598a71784`**.

- The active imported instance is `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.5.2-Prism-1.21.4`; its folder retains the original import name. The old `instances\Analyzer` directory no longer exists. Audit of the installed 0.5.2 JAR confirmed it already matched the previous feed, while its saved layout was Classic. The instance was therefore opening the retained column menu rather than Workspace.
- With Minecraft closed, the strict updater installs 0.6.0 in that existing instance and keeps a recoverable 0.5.2 JAR backup. A subsequent strict check reports **up-to-date**. Exactly one Analyzer JAR is installed, matching the feed and build hashes.
- The requested presentation is deliberately selected by changing only canonical `menu.layout` to **Studio**, `menu.accent` to **Rose**, and `menu.material` to **Solid**. The existing Glass theme remains. The original configuration is backed up under `.analyzer-updater/backups/studio-appearance-before-0.6.0.json`. A semantic comparison confirms every other configuration value is preserved; a hash audit verifies all **77 other protected game/dependency/instance files** remain byte-identical, including all 11 saved profiles. Worlds, friends, keybinds, Classic positions and the user's Prism Java settings are preserved.
- The Desktop `Analyzer-0.6.0-Prism-1.21.4.zip` includes the matching runtime JAR, Fabric API, official Baritone 1.13.1, separate corresponding sources/notices, updater, current docs, reference images and all **16 Studio captures**. ZIP CRC, embedded shader/font resources, Lato OFL notice, canonical profiles and matching archive/feed/build/install hashes are verified. The portable instance inherits Prism's Java settings and contains no launcher accounts.

Final installation/archive evidence is `build/reports/prism-studio-delivery.json`. The standing instruction and current instance path are saved in [AGENTS.md](AGENTS.md). This distribution audit does not claim a new launch through Prism; native rendering is verified in the isolated actual Minecraft fixture.

## Native UI overhaul — October 11, 2026

The user-selected Focused workspace is implemented with rounded cards/switches, real scene frost, sidebar browsing and a grouped inspector. Workspace, Classic and Compact share native controls. HUD Studio exposes layout, material, menu theme and accent choices; menu and HUD themes remain independent. The catalogue contains **123 modules and 1,091 exposed settings: 1,087 canonical settings plus four presentation aliases**.

Verified with Java 21, Minecraft 1.21.4, Fabric Loader 0.16.14 and Fabric API 0.119.4+1.21.4 on this computer:

- Final `build runUiSmoke` and `publishLocal moduleCatalog` succeed with **1,017 build assertions**: 284 core, 38 ESP layout, 46 planner, 10 scoped-settings lease, 102 combat, 30 movement, 18 HVH and 489 configuration assertions. The new core checks cover aliases, independent themes, migration, canonical import precedence, persistence and reset behaviour.
- The actual-world native UI fixture passes **280 assertions**, producing **19 framebuffer captures** in 290 fixture ticks. It checks catalogue/search/category contents, separate inspection and enable actions, long Combat/ESP inspectors, exact numeric input, Page Up/Down, visible hit regions, key capture/cancel/restore in all layouts, HUD menu switching and real save/reload, all four menu themes, Glass/Solid sampling, zero motion, resource reload and clean GL state.
- Keyboard checks cover Tab/Shift+Tab, initial backwards focus, Enter, arrows and reopening a nonempty search with `/`. The actual narration hook produces focused labels, values, module toggle states and keybind descriptions. HUD, Automation and Profiles return to the original parent; Profiles also retains query/selection across layouts. Menu interaction does not trigger gameplay actions or retain movement inputs.
- Captures cover **1280 × 800** and **1920 × 1080**, requested GUI scales 2/3/4. Minecraft clamps 1280 GUI 4 to effective scale 3; all other requested combinations reach their intended effective scale. Actual screenshot/hit-region dimensions are checked. The captures, including menu appearance settings and the visible Profiles Back control, were visually reviewed.
- The focused gameplay regression also passes **66 runtime assertions** (41 movement/shared numeric input and 25 Combat/HVH), plus **23 introductory UI checks** for toggles, search, numeric editing, keybinds and profile save/load. It completes in 159 fixture ticks and produces three gameplay settings captures. Both final runtime logs contain no ERROR/FATAL entries.

Final logs are `build/reports/native-ui-runtime.log` and `build/reports/native-ui-gameplay-runtime.log`. [UI controls and captures](UI-OVERHAUL.md) links the native images. Surface tokens were tuned using conservative bright/dark scene compositing; this is not a complete per-pixel contrast measurement. Spoken narrator output, the full Automation state/capture matrix, steady-frame performance comparison, Iris/Sodium and other GPU configurations remain outside this focused verification. Historical gameplay/production evidence below applies to its recorded artifacts.

## Native UI Prism delivery — October 11, 2026

The final remapped Analyzer 0.5.2 JAR, local updater manifest, import ZIP and installed Analyzer JAR share SHA-256 **`ec2747a16a997516b24b477601870049fe8139e49f5f61d9438324dc3dc9b592`**.

- With Minecraft closed, the strict Windows PowerShell updater installed the new same-version build in the existing **Analyzer** instance at `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer`, retaining a recoverable backup of the previous Analyzer JAR. A subsequent strict check reports **up-to-date**.
- A before/after hash audit verifies that all **five protected game/dependency/instance-setting files** remain byte-identical. Exactly one Analyzer JAR is installed. No worlds, configuration or saved user profiles are replaced; this instance had not yet created those game files. The local feed and Prism pre-launch updater remain connected.
- `C:\Users\sould\Desktop\Analyzer-0.5.2-Prism-1.21.4.zip` is rebuilt with the matching Analyzer JAR, Fabric API, unchanged official Baritone API 1.13.1, corresponding dependency sources/notices, updater, current documentation, five original references and all **19 native UI captures**. ZIP CRC, dependency identities, embedded canonical profiles and matching build/feed/install/archive hashes are verified. The portable archive inherits Prism's Java settings; the existing installed instance keeps its explicit Java 21 path.
- The updater code is unchanged from its earlier **74-check** integration run. Current installation/archive evidence is preserved in `build/reports/prism-native-ui-delivery.json`. The standing future-update instruction is recorded in [AGENTS.md](AGENTS.md). No Minecraft launch through Prism was needed for this distribution audit.

## UI reference comparison before native implementation — October 11, 2026

The five user-supplied screenshots are recorded in [UI-REFERENCES.md](UI-REFERENCES.md), with unchanged source copies under `design/ui-references`. Two interactive browser compositions compare **Focused workspace** and **Category desk** using the same complete catalogue: 123 modules and 1,086 settings. Entity ESP has 20 controls and Kill Aura has 33; conditional visibility follows the native settings rules. ESP's prototype grouping is a presentation proposal.

The comparison passes **168 browser checks**, with zero failures or uncaught browser errors, at outer widths of 1024, 736 and 320 CSS pixels. Checks cover both carousel directions, catalogue equality, inspection versus enable switches, numeric entry/clamping, conditional controls, empty search, retained navigation/inspector context, narrow-screen Back, HUD scale/position, Pause/Stop, blocked requirements and missing Baritone. Screenshots were visually reviewed. An initial inspector/footer overlap and catalogue button-style regression were repaired before final verification.

The browser comparison source SHA-256 is `de7b8c61bf532ec6f5c3e6cda92b49aef370eb6de3466146ffc415980bec211e`. Local verification output is in `build/ui-preview/QA.md`, `qa-results.json` and its captures. Task/HUD values are illustrative and edits remain local to the preview. These checks do not establish native Minecraft UI acceptance, performance or framebuffer readability. The existing native menus and game configuration are preserved while the user compares directions.

The standing Prism delivery workflow also applies to this documentation update: `publishLocal` passes the 978 build assertions, the installed JAR matches the unchanged verified release, and the refreshed import ZIP includes the reference board and all five comparison images. Instance documentation is refreshed without modifying worlds, configuration, profiles or other game files.

## Working-tree gameplay settings overhaul

Verified October 11, 2026 on this Windows computer with Java 21, Minecraft 1.21.4, Fabric Loader 0.16.14, Fabric API 0.119.4+1.21.4 and NVIDIA RTX 4060 Ti. This update retains version 0.5.2 and is separate from the published October 9 artifact documented below.

- `build runGameplaySmoke` succeeds with **978 build assertions**, including **489 old/new configuration migration assertions**. The catalogue still contains 123 modules; the existing 30 Movement/Combat/HVH modules now have **442 settings**, up from 94.
- **66 focused runtime assertions** pass: 41 movement/UI and 25 combat/HVH, completing in 159 fixture ticks with three screenshots. Actual Survival profiles, Step cleanup, descending Fly and Safe Walk gates work. Rendered numeric controls open and commit keyboard entry in all three layouts; a long Combat inspector scrolls to its final group.
- A real server-spawned target verifies filtering, camera speed, acquisition versus melee reach, charge gating and server-confirmed ordinary damage. Loaded terrain/armor/resistance/unloaded chunks and an existing anchor verify damage estimates without causing an explosion. Pending placement previews render with clean GL state.
- Integration regressions cover critical-wait cleanup after losing melee reach, Target Strafe respecting sprint ownership, and placement retries staying within one turn per module/tick. An initial fixture race was repaired by waiting for authoritative arena setup and actual client synchronization; the two subsequent runs pass.
- [Gameplay settings](GAMEPLAY-SETTINGS.md) records the implemented scope, explicit reference gaps and test details. Screenshots are [Workspace](screenshots/analyzer-gameplay-settings-workspace.png), [Compact](screenshots/analyzer-gameplay-settings-compact.png) and [Classic](screenshots/analyzer-gameplay-settings-classic.png). The final runtime log is preserved at `build/reports/gameplay-settings-runtime.log`.

Multiplayer, every mode combination and actual explosion outcomes remain unverified; historical runtime and production checks below apply to their recorded artifacts.

## Prism delivery — October 11, 2026

- `publishLocal` completed with the 978 build assertions passing. The final remapped JAR, local manifest, import ZIP and installed JAR share SHA-256 `71586c31a6e842fb81bee801c0164cbc43037bbf5a379ab35e57bc0bd03e29cb`.
- This computer had five Prism instances and no Analyzer instance. A new **Analyzer** instance was created at `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer`, with Minecraft 1.21.4, Fabric Loader 0.16.14, Fabric API 0.119.4+1.21.4 and the verified official Baritone API 1.13.1. Its explicit Java path uses the installed Java 21 runtime. All ten original instance-definition files remain byte-identical.
- With Minecraft closed, the updater verified the feed, installed Analyzer, and subsequently reported **up-to-date**. The unchanged Windows PowerShell 5.1 updater suite passed all **74 isolated integration checks**.
- The native import ZIP on the Desktop has root instance definitions, three mod JARs, updater configuration, current documentation/screenshots, Fabric API license and corresponding Baritone sources/notices. ZIP CRC, dependency metadata and matching Analyzer hashes were checked. The portable ZIP inherits Prism's Java settings rather than embedding this computer's Java path.
- The packager now preflights required inputs and includes the gameplay settings guide and all three menu screenshots. Historical `VALIDATION.log` and `PIXEL-VALIDATION.log` are absent from this source archive; packaging reports their omission instead of failing or substituting fresh output for historical evidence.
- The user's instruction to update Prism after every project update is saved in [AGENTS.md](AGENTS.md), with the verified instance/feed paths and delivery workflow. No Minecraft launch through Prism was performed during this distribution step.

The installation and archive verification record is `build/reports/prism-delivery.json`.

## Analyzer 0.5.2 Creeper and terrain recovery

Verified October 9, 2026 on Windows, Java 21, Minecraft 1.21.4, Fabric Loader 0.16.14, Fabric API 0.119.4+1.21.4, unchanged official Baritone API 1.13.1 and NVIDIA RTX 4060. These selected local scenarios cover the reported failure more closely; they do not establish universal survival.

- The complete `build moduleCatalog runSmoke` passes 245 core, 38 ESP-layout, 46 planner and 10 setting-lease assertions. It passes **669 runtime assertions**, including all 602 earlier checks and **67 new terrain/recovery checks**. All **108 framebuffer/GL checkpoints** and the final GL check pass with zero GL errors. The final log contains no ERROR entries. [VALIDATION.log](VALIDATION.log) records the actual output.
- A real unarmored Survival player at night has six HP, logs and no combat weapon. A normal-AI creeper causes actual swimming retreat from shallow water onto partial collision support of layered snow. An authoritative server impulse then produces actual airborne motion and landing while the original task remains live. User Pause releases swimming/jump movement after inertia settles.
- A player-sized bedrock chamber with a one-cell-high eye window has no checked escape. The controller enters **SAFETY_HOLD**, keeps the original owned route paused, and remains active. Removing the creeper and opening a passage does not resume at six HP without food. Adding actual cooked beef and enabling vanilla regeneration lets the player eat, recover and finish the original route without pressing Resume.
- An actual ignited creeper with a normal 30-tick fuse explodes near an unarmored player in a small contained shallow-water depression. This fixture sets the existing retreat-health option to 18 HP to exercise recovery through normal regeneration. The fixture verifies real nonlethal explosion damage, a live original stage after the blast, actual food consumption, normal regeneration and completion of the same route. No healing command is issued after the explosion. This particular distance, terrain and starting health do not prove immunity to lethal blasts.
- A narrow loaded corridor contains a real normal-AI creeper and zombie on opposite sides. The navigator and live guard choose movement away from the creeper before ordinary zombie combat. An additional actual-world survey verifies a required jump over a one-block stone rise.
- A stage genuinely blocked by a missing mining tool remains blocked for that original requirement after intervening protection clears; monitoring danger does not silently retry resource-blocked work. Restored checkpoints and explicit user Pause remain inactive.
- The retained suite covers armed zombie defense and mining resumption, inventory food use and restoration, neutral-mob filtering, supported retreat beside lava, temporary routing preferences, manual Baritone takeover, ESP layout, all 123 registered modules, rendering, crafting, enchanting and checkpoints.
- Current sky/depth and visible Automation queue-row pixel checks pass in [PIXEL-VALIDATION.log](PIXEL-VALIDATION.log). The [UI overhaul](UI-OVERHAUL-PLAN.md) remains a design plan awaiting the user's references.
- Two actual production Fabric startups, with and without optional Baritone, pass against the final remapped JAR SHA-256 `8688b52782c70773f65f8c8553f11417ec0254a0977520de9711ce416ccdb027`. Both use development=false, render the actual Automation screen, report GL=0 and stop normally; no saved world or public server is joined. [Production evidence](production-validation/README.md) records the logs, captures and checksums.
- The existing closed Prism instance was upgraded to 0.5.2 with a recoverable 0.5.1 backup. A before/after SHA-256 audit confirms **all 187 other game files** remain byte-identical, including worlds, configuration, options, logs, Baritone data and other mods. The local updater feed contains the same verified JAR.
- Actual engine-level checks also verify manual Baritone takeover, explicit Resume after the manual route finishes, and release of guard inputs when a manual inventory screen interrupts protection with normal menu pausing disabled.

Protection checks short loaded corridors and uses normal movement, reach, cooldown and inventory resources. It does not find a long path to shelter, build cover or fight players. No checked corridor produces a visible live hold with continuous re-evaluation; manual movement or resources can still be needed. Public-server escape, anticheat acceptance, every terrain/mob combination, Iris/Sodium and other GPUs remain unverified. See [SAFETY.md](SAFETY.md) for controls and limits.

## Historical release evidence

## Analyzer 0.5.1 Survival safety

Verified October 9, 2026 on Windows, Java 21, Minecraft 1.21.4, Fabric Loader 0.16.14, Fabric API 0.119.4+1.21.4, unchanged official Baritone API 1.13.1 and NVIDIA RTX 4060. These are selected local scenarios on one hardware environment.

- The complete `build moduleCatalog runSmoke` passes 245 core, 38 ESP-layout, 46 planner and 10 setting-lease assertions. It passes **602 runtime assertions**, including all 492 retained checks, **70 safer-profile** and **40 active-protection** checks. All **100 framebuffer/GL checkpoints** and the final GL check pass with zero GL errors. The final runtime log has no ERROR entries. [VALIDATION-0.5.1.log](VALIDATION-0.5.1.log) records the actual output.
- A real Survival player starts a reviewed stone-mining stage at night on Normal difficulty. A zombie with normal AI approaches. The owned miner pauses, ordinary cooldown-limited sword attacks damage and kill the actual server zombie, the player survives, and the same mining stage resumes and obtains eight cobblestone drops. This armed, isolated fixture does not prove survival against every group or equipment level.
- Available food starts outside a full hotbar. Normal slot swapping and item use pause an actual owned travel route. User Pause cancels use and restores the original slot/items; explicit Resume consumes actual server food, raises hunger and leaves no cursor item.
- A normal-AI creeper triggers checked-ground retreat. After real damage lowers health beneath the configured task limit, the controller remains PROTECTING and increases actual separation while staying on supported ground beside a lava-floor hazard. User Pause releases movement after inertia settles. An unprovoked enderman and passive cow cause no defense attacks. Stop releases protection, routing and path ownership.
- Runtime checks verify temporary public Baritone settings, idle no-mutation, settings-query disk serialization, explicit setting-edit preservation, scoped movement while paused, completion restoration, command-registry restoration and manual takeover reaching its own destination. Pure lease tests cover rollback and edits by another writer. The test restores its isolated preferences; production integration never writes Baritone's settings file itself.
- The protection banner reaches the actual framebuffer with nominal 14-pixel text and bounded physical coordinates, independent of the 60% HUD. The UI overhaul remains [a plan](UI-OVERHAUL-PLAN.md), waiting for the user's reference images. [SAFETY.md](SAFETY.md) describes controls and limits.
- Two production Fabric launches pass against the exact remapped Analyzer SHA recorded in [production-validation/production-validation.json](production-validation/production-validation.json). They use the actual official game client with development=false, with and without optional Baritone. Both render the real Automation console, report GL=0 and stop normally. No saved world or public game server is joined. Production evidence is in [production-validation](production-validation/README.md).
- Current sky/depth and six visible Automation queue-row pixel checks pass in [PIXEL-VALIDATION-0.5.1.log](PIXEL-VALIDATION-0.5.1.log). Existing ESP behavior, 123 registered modules, fonts, game actions, crafting/enchanting and explicit checkpoint resumption pass the retained suite.

Protection is bounded PvE behavior: a short dry ground corridor, normal reach/cooldown and existing inventory resources. It does not find long escape paths to shelter or cover, swim/climb/fly, or fight players. Missing food, obstructed ground or unresolved recovery produces a visible blocked reason requiring manual attention. Public-server spawn escape, anticheat acceptance, all mob/terrain combinations, Iris/Sodium and other GPUs remain unverified. The unchanged LGPL Baritone artifact and corresponding sources/notices remain separately distributed.

- Distribution integrity checks verify matching JAR/feed checksums, 123 typed profile states, 17 mixins/refmap entries, optional Baritone handling, unchanged upstream artifacts/sources/notices, production evidence and both native Prism/source archives. The existing closed Prism Analyzer instance was upgraded to 0.5.1 with a recoverable 0.5.0 backup. A before/after SHA-256 audit confirms **all 185 other game files** remained byte-identical, including worlds, configuration, options, logs, Baritone data and other mods.

## Historical release evidence

## Analyzer 0.5.0 ESP and local Automation

Verified October 9, 2026 on Windows, Java 21, Minecraft 1.21.4, Fabric Loader 0.16.14, Fabric API 0.119.4+1.21.4, official Baritone API 1.13.1 and NVIDIA RTX 4060. The registry contains **123 modules**. These checks describe selected local scenarios on one hardware environment.

- The final `build moduleCatalog runSmoke` succeeds with **245 core**, **38 ESP-layout** and **46 task-planner assertions**. The complete native suite passes **492 runtime assertions**: 44 retained visual/UI, 96 gameplay/menu/editor, 222 cosmetic/readability, 31 ESP, 47 Baritone/ownership and 52 inventory/task-console checks. All **94 framebuffer/OpenGL checkpoints** and the final GL check pass with zero GL errors. The final log has no ERROR entries. [VALIDATION-0.5.0.log](VALIDATION-0.5.0.log) contains the actual output.
- ESP's dense fixture contains 71 selected hostile entities plus a small dropped item. Runtime checks cover distinct near/middle label sizes, nearest-first limits, overlap rejection, independent geometry/label ranges, half label scale, storage budgets, GUI scales 2/4, HUD scale independence and disabled-module cleanup. Five actual captures are linked in [ESP-READABILITY.md](ESP-READABILITY.md). The synthetic stack's transparent box fills still accumulate; label filtering does not merge overlapping geometry.
- Official Baritone runs real Survival travel, pauses owned movement and resumes it; it breaks actual stone/iron ore and receives the resulting cobblestone/raw iron. A nearly complete starter-shelter fixture verifies two actual missing blocks are placed through the builder, material consumption and all 245 schematic cells. This exercises shelter repair, not a full 168-material construction from empty terrain. Manual movement takes ownership, is preserved when Analyzer cancels, and reaches its destination. Actual low-health damage blocks the central task and pauses its owned route.
- Ordinary crafting clicks produce a wooden pickaxe, crafting table, furnace, stone pickaxe, smelted iron pickaxe and all six diamond-kit items from 29 diamonds. The server inventory verifies each stage. Stopping mid-craft returns the task-owned cursor stack without dropped items. Armor is equipped through normal slot actions.
- An actual level-30 table offer consumes three levels and three lapis. Two matching books upgrade a partially enchanted helmet through an anvil; authoritative server item components verify Protection IV and Unbreaking III on all four pieces, both books are consumed, XP is spent and armor is re-equipped. The other three armor pieces start enchanted in this quality fixture. The helper reads enchantment identifiers instead of comparing client/server registry objects; a failed strict check caught that issue before release.
- The native Automation console reviews the Expedition preset into a multi-stage plan through actual controls. Review leaves execution stopped. Screenshot review caught a doubly transformed scissor that hid stage rows; the corrected capture passes six independent visible-text row checks in `tools/verify_automation.py`. Primary controls use dark text on a solid accent fill for contrast. Checkpoints restore unfinished work paused, and action modules are temporarily suspended during tasks. Disabling Automation while blocked releases its owned route and restores suspended Auto Weapon; cleanup also applies while paused. Supported intentions are deterministic local parsing; no AI service or API key is used. [AUTOMATION.md](AUTOMATION.md) records controls and requirements.
- Five fresh profile snapshots include all 123 typed states with 60% HUD scale; Automation defaults off. Existing user settings/profiles are preserved. Retained rendering checks cover reload, resize and Fast/Fancy/Fabulous. [PIXEL-VALIDATION-0.5.0.log](PIXEL-VALIDATION-0.5.0.log) contains the sky/terrain/depth-occlusion pixel measurements for this run.

Enchanting still needs suitable workstations, lapis/XP or matching books; obtaining villagers, trading books, constructing an XP farm or enchantment library and repairing tools are not automated. The verified shelter is a fixed original 7 × 7 × 5 starter design. Arbitrary building, hostile public-server escape, every crafting/terrain/material combination, Iris/Sodium and other GPUs remain unverified. No public server was contacted.

The actual remapped release JAR also passes two isolated production Fabric launches (`fabric.development=false`): with official Baritone and its correctly loaded nested native helper, and without Baritone. Both reach the title screen, load 123 modules, render the actual Automation screen and stop normally with GL=0. The absent-dependency screen explains installation/restart and disables execution. Both 1280×800 screenshots were inspected. [Production evidence](production-validation/README.md) includes portable logs, the checked JAR hash, captures and the original probe source.

`publishLocal` rebuilt/checked the release and atomically published the local feed. Its SHA-256 is `a2b3187680df6a9edd16d857ebbdd6a769819316433b188eb5682cb8b7a3d316`, matching both production probes. Baritone remains an unmodified separate, replaceable dependency with official checksums, corresponding sources and licensing notices.

The existing Prism **Analyzer** instance was upgraded with Minecraft closed; 0.4.0 was backed up through the updater. The installed 0.5.0 JAR matches the feed and Baritone matches its pinned official hash. All **74 pre-existing configuration, world and other-mod files** remain byte-identical. The only added game file is the separate Baritone JAR. Native Prism/source archive checks cover CRC integrity, exact mod/source/notice identities, 123 module states, all 17 mixins/refmap entries, the optional-adapter guard, five 60% profiles and a source archive free of build/run/cache files. The updater's implementation is unchanged from its historical 74-check integration suite.

## Historical release checks

## Analyzer 0.4.0 cosmetics and typography

Verified October 9, 2026 on Windows, Java 21, Minecraft 1.21.4, Fabric Loader 0.16.14, Fabric API 0.119.4+1.21.4 and NVIDIA RTX 4060. The registry contains **122 modules**, including fifteen new default-off cosmetic modules and existing HUD widgets. This is one tested hardware environment, not a performance benchmark or full reference parity.

- The final `build moduleCatalog runSmoke` succeeds with **242 core assertions** and **362 runtime assertions** (44 retained visual/UI, 96 gameplay/menu/editor, 222 cosmetic/readability checks). All **81 framebuffer/OpenGL checkpoints** and the final GL checks pass with zero GL errors. The final run has no ERROR log entries. [VALIDATION-0.4.0.log](VALIDATION-0.4.0.log) records that release's actual final output; development scenarios never run in an installed release.
- Fresh HUD scale is **60%**. Eleven native HUD captures cover Glass, Classic, Minimal and Neon at **1920x1080 and 1280x800**, including Minecraft GUI scales 2/3/4 where supported. Actual render-callback bounds stay within the viewport, and the same HUD retains its physical size across GUI scale changes. TTF body/caption sizing has nominal physical floors of 14/12 pixels; icons/panels retain the requested smaller size. Armor captions include 100% and 1%, and inventory counts use the readable font.
- The bundled SemiBold font now uses two-times glyph rasterization and linear sampling on **Analyzer's own font atlases**. Actual GPU min/mag filters are checked after resource reload. The atlas subclass retains filtering across render-phase cache calls and normal texture disposal; Minecraft's ordinary bitmap-font storage is unaffected. Labels use physical pixel alignment, brighter contrast and matching text measurement/truncation. All three native menu layouts have final captures at the corrected typography.
- Air Effects renders a bounded camera-area Network field: changing density from 48 to 12 changes the actual submitted field; disabling clears it. Independent Block Effects observes ordinary accepted stone placement and breaking. Damage Effects and Hit Sounds respond to actual attacked-zombie hurt; Storm Effects observes a real summoned LightningEntity. Vitals responds to actual Survival health loss and stops when disabled.
- Actual dropped-item rendering exercises Item Physics, and actual end crystals render the original Gem and Prism meshes. Hand Tweaks changes first-person hand/item transforms. Third-person feature renderers submit a procedural cape and Dragon pet; actual player models receive Dance and Zombie walk poses. Cosmetic Spinbot changes rendered state while preserving camera/entity and server yaw. Disabling Cape stops mesh submission. Contrails follows ordinary walking with the glide restriction off; disabling releases its paths. Elytra-only filtering, water rocking, every visual style and every swing/pose combination were source-reviewed rather than all exercised in game.
- Screenshot review caught and fixed disconnected cape/pet/crystal triangles: these meshes now submit independent quad faces through the matching QUADS layer. Cape trim retains configured alpha; ground clearance affects both flat and 3D item models. Vanilla-model fullbright is labelled explicitly; Gem/Prism use unlit vertex-color meshes.
- The retained normal combat, crystal/anchor explosions, four-side Surround, inventory swap, input/attribute cleanup, Survival mining and fishing-rod retention checks pass. Repeated arena runs disable mob loot and purge incidental dropped entities from the crystal placement fixture; a live target and actual obsidian are checked before enabling the module. This makes the test fixture deterministic without bypassing placement rules.
- Five fresh full snapshots (`cinematic`, `classic`, `neon`, `studio`, `hvh`) contain all 122 typed states at 60% HUD scale. The four visual profiles keep gameplay automation off. New `studio` is seeded under its own marker without overwriting old profile files or active preferences.
- Retained sky/ESP checks pass across Fast/Fancy/Fabulous, reload and resize. [PIXEL-VALIDATION-0.4.0.log](PIXEL-VALIDATION-0.4.0.log) records distinct sky effects, stable opaque terrain and through-wall versus depth-tested chest pixels. The gold chest mask includes both fill and edge at a blue/green ratio below .75; grading had made the previous .70 boundary reject most gold pixels. The required visible pixel count and the occlusion comparison remain in place.

The [cosmetics comparison](RESEARCH-COSMETICS.md) covers all 37 published cosmetic entries, and the [roadmap](ROADMAP.md) covers 163 reference areas: 33 scoped counterparts, 69 partial implementations and 61 planned areas. These are source-reviewed scope counts, not runtime or feature-parity counts. Reflective water, waving foliage, wet-ground materials, Hand Chams and Glint Color remain planned. No public server was contacted; Iris/Sodium, other GPUs, imported cape/media files and multiplayer performance remain unverified.

`publishLocal` rebuilt and checked the release, then atomically published the local feed. The existing Prism **Analyzer** instance was upgraded through its updater with Minecraft closed; 0.3.0 was backed up. The installed 0.4.0 JAR matches SHA-256 `0f99e5fcce4c158f62a654a4d3153d5d4d3de33cc5ccadb7db0e2d6d3a4cfc91`. Hashes of all **47 configuration, world and other-mod files** were unchanged after installation. The updater itself is unchanged from its historical 74-check integration suite. Release archive integrity is checked during packaging, including all 17 mixin classes and remap entries, five complete 122-state profiles, the native Prism metadata and a clean source archive.

## Historical release checks

## Analyzer 0.3.0 expansion

Verified October 9, 2026 on the same Windows / Java 21 / Minecraft 1.21.4 / Fabric / RTX 4060 environment. The registry contains **107 modules**, including HUD widgets; this is separate from reference coverage and runtime coverage.

- The final `build moduleCatalog runSmoke` succeeds. **218 core assertions** pass: typed settings/configuration recovery plus registry consistency, gameplay opt-in defaults and all four new profile snapshots. `cinematic`, `classic` and `neon` keep gameplay automation off; `hvh` explicitly enables its documented combat/inventory/building setup.
- The isolated native client passes **139 runtime assertions**, including **54 screenshot/OpenGL checkpoints** (22 retained visual checks plus 32 expansion frames). Every checkpoint reports zero GL errors; a final GL check also passes. The final run has no ERROR log entries. `VALIDATION-0.3.0.log` contains that release's actual final build/runtime output. Installed builds do not activate this development harness.
- Actual normal stone placement and breaking change the world and emit bounded ghost/ring/debris geometry. Disabling the respective animation prevents new events. Seventeen successive framebuffer captures are encoded into the release's block-effect GIF; it is a runtime recording. These effects overlay vanilla meshes rather than replacing chunk geometry.
- Kill Aura submits normal attacks and reduces a nearby test zombie's health; disabling it stops attacks. Crystal Aura performs real interaction and receives an actual explosion packet. Anchor Aura places, charges and detonates an anchor in the Overworld, with a real explosion packet. Surround builds all four actual obsidian sides around the player. These are selected local scenarios, not multiplayer combat benchmarks.
- Auto Totem performs a real offhand inventory swap. Step changes then restores the original step attribute. Auto Walk presses then releases its owned forward input. Coord Snapper copies coordinates once and turns off; the harness restores the previous clipboard contents without logging them.
- Nuker breaks actual blocks in Creative and completes ordinary **Survival mining without held attack**. Long obsidian mining retains progress; disabling the module and opening a menu cancel it. This caught and fixed vanilla idle-input cancellation resetting utility mining every tick. Item `canMine()` is checked; a creative sword is correctly excluded.
- Auto Fish performs a real rod cast, retains the rod and server bobber across subsequent waiting ticks, then restores the original hotbar slot on disable. This caught and fixed premature rod release. A naturally occurring fish bite/reel cycle was not exercised.
- Workspace, Classic and Compact menus open through real controls. Classic toggles and inline typed settings work; Compact search/toggle works. All four HUD themes render actual game data. The editor's actual widget bounds drive a drag, persisted normalized position, theme change and scale change. The editor owns its widget pass to avoid drawing widgets twice; fresh editor defaults show active widgets.
- The retained sky/shader/ESP checks still pass across Fast/Fancy/Fabulous, reload and resize. Pixel verification measures **13,728 aurora-colored sky pixels versus 324 with sky off**, mean sky change **34.14**, opaque-wall change **0.24**, and **62 through-wall chest outline pixels versus 0 with Fabulous depth testing**. Exact results are in `PIXEL-VALIDATION.log`.
- Source review checks bounded visual buffers, loaded-world scans, normal reach/cooldown/action budgets, target filters/friends, guarded inventory transactions and cleanup of temporary owned input/slots/attributes. Not all 107 modules, visual styles, item combinations or simultaneous-module combinations were tested in game.

Remaining scope: Crystal/Anchor use distance/health guards without explosion exposure/damage prediction. Movement may be corrected by servers. Full model-mask Chams, reflective water/foliage shader integration and the remaining reference features are planned in [ROADMAP.md](ROADMAP.md). Iris/Sodium, other GPUs and public-server behavior remain unverified. No public server was contacted for these checks. The 89 scoped/partial reference areas in the roadmap describe source-reviewed implementation coverage, not feature parity.

`publishLocal` rebuilt/checked the release and atomically published the local feed. The existing Prism **Analyzer** instance was upgraded through its updater with Minecraft closed; 0.2.0 was backed up. The installed 0.3.0 JAR matches SHA-256 `90485cb5c37c197d068bba69983f14e9d14be795bf0ab4d29fdc3a59ab0d9a5b`. Hashes of all **32 configuration, world and other-mod files** were unchanged after installation. The updater itself is unchanged from its historical 74-check integration suite. The native Prism ZIP and clean source ZIP are checked for CRC/metadata/catalogue integrity during packaging.

## Historical release validation

## Analyzer 0.2.0 visual upgrade

Verified October 8, 2026 with the same Windows / Java 21 / Fabric 1.21.4 / RTX 4060 environment described below. This is one tested hardware environment, not a performance benchmark.

The original installed 0.1.1 log showed successful shader initialization. Its saved configuration had Entity ESP disabled and players-only tracers enabled in a singleplayer world. Those settings can legitimately produce no targets. The old Aurora shader was screen grading and did not implement a spatial sky aurora. This release adds an actual sky pass, stronger visual effects and live explanations of renderer/filter state.

- `build` and `coreCheck` pass **44 core assertions**, including the new sky/interface defaults, preset choices and setting bounds. The release has ten modules and four mixins.
- The isolated `runSmoke` world compiles/links all three original GLSL programs on the actual GPU, exercises UI clicks/search/sliders/booleans/keybinds/profile save, and captures **22 framebuffer screenshots**. Installed builds never activate this development harness.
- The actual sky renders Aurora, Nebula and Ember behind opaque terrain and vanilla clouds. Night-only mode submits no sky frames at noon; Always mode does. The sky/post programs successfully render again after resource reload and a 1600×1000 to 1280×800 resize. All 22 screenshot checkpoints report `glGetError() == 0`.
- Entity and storage Screen ESP project at least six targets each under **Fast, Fancy and Fabulous**. Fixtures include hostile/passive mobs, a local remote-player object, an item and six container types. The nearby radar uses the same entity filters. Removing the player fixture produces a clear `0 other players` tracer status. New dispenser, dropper and brewing-stand recognition was source-reviewed; those extra types were not separately exercised in the arena.
- Glass/Mint with visible particles, Solid/Gold with particles disabled, and Glass/Violet were captured and visually reviewed in game. The interface remains readable, and existing controls continue to work. Tutorial notifications are suppressed only in the development harness for clean captures.
- `tools/verify_visuals.py` checks actual sky-colored pixels, distinct presets and stable opaque-wall pixels. `tools/verify_render.py` retains the earlier 3D outline depth/through-wall and resize check. Run either with an optional screenshot-directory argument; both require Pillow.
- The final pixel check measured **13,359 aurora-colored sky pixels versus 324 with the sky off**, a mean sky-image change of **34.47** on the 0–255 scale, and only **0.13** change on the fixed opaque-wall region. The 3D chest test measured **62 visible outline pixels versus 0 with Fabulous depth testing**. Exact results are in `PIXEL-VALIDATION.log`; the full runtime log is `VALIDATION.log`.
- New `showcase`, `arctic` and `cinder` profiles are seeded once under a new marker. Existing configuration and older edited profiles are preserved. Load `showcase` explicitly to select the new Screen ESP and Bloom combination.

Screen ESP with through-walls disabled uses a line-of-sight check to each target center. The 3D Boxes/Corners modes retain GPU depth testing. This release does not implement model-mask silhouette ESP, shadowed world-lighting shader packs, player aura/trail cosmetics or combat automation. Iris/Sodium and multiplayer behavior remain unverified. Research observations and vendor catalogue claims are distinguished in [RESEARCH-VISUALS.md](RESEARCH-VISUALS.md).

The 0.2.0 release was published to the local feed and installed into the existing Prism Analyzer instance through its updater. The installed JAR matches the feed SHA-256, declares version 0.2.0 and includes the new sky mixin. The prior 0.1.1 JAR was backed up; hashes of all **32 configuration, world and other-mod files** were unchanged after installation. Native Prism and source ZIP integrity are checked during packaging. The unchanged updater's 74-check integration suite is documented below.

## Historical 0.1.x checks

## Analyzer 0.1.1 Prism integration

Added October 8, 2026. The game code and graphics are unchanged from the runtime checks below; this release updates the mod version, distribution tooling, and notices.

- `publishLocal` rebuilt the remapped 0.1.1 JAR, passed all **37 core assertions**, published an immutable hash-named artifact, and atomically replaced the local feed manifest.
- The updater's Windows PowerShell 5.1 integration suite passed **74 checks** in isolated temporary fixtures. It covers initial install, version replacement, same-version rebuilds, duplicate Analyzer JARs, backups, world/config/other-mod preservation, checksums, metadata types and compatibility, unsafe paths and junctions, filename conflicts, disabled updates, locks, caught-exception rollback, and interrupted transaction recovery even without a feed. Renamed JARs with spaces/Unicode are supported. Busy transactions and unsafe recovery stop launch; ordinary feed errors preserve the installed mod and allow launch.
- Native instance ZIP layout, custom-command preservation, quoted instance variable expansion, and Java selection behavior were checked against the installed **Prism Launcher 11.1.1** source. The pack includes Minecraft/Fabric component metadata and both required mods, without game binaries or accounts.
- The directly installed development instance uses the project's verified Java 21 runtime; the portable ZIP inherits the recipient's Prism Java settings. Updates apply before the next launch, not to an already running game.

The updater currently reads a local development feed. An internet release service is not configured. See [PRISM.md](PRISM.md) for setup and publishing.

Verified October 8, 2026 on Windows, Java 21, Minecraft 1.21.4, Fabric Loader 0.16.14, Fabric API 0.119.4+1.21.4 and an NVIDIA GeForce RTX 4060. This records one local hardware/driver environment, not a compatibility or performance benchmark.

## Build and configuration

- `build` succeeded: Java compilation, resources, remapped mod JAR, source JAR and Gradle checks.
- The version-independent core test suite passed **37 assertions**: registry defaults, typed settings, numeric clamping/snapping, invalid values, keybind bounds, JSON round trips, profile save/load/delete, safe names, malformed/future/structurally rejected config recovery, state preservation and temporary-file cleanup.
- Verified release JAR metadata declares client-only Fabric 1.21.4 / Java 21, has remapped Minecraft symbols and mixin refmap, and retains the MIT, font and third-party notices.
- Official portable Java and Gradle downloads were checksum-verified; the Gradle wrapper carries its distribution SHA-256. The unmodified Fabric API JAR was checked against the upstream Maven checksum.

## Native UI and graphics

The `runSmoke` development harness launched Minecraft, loaded all three mixins and compiled/linked both original GLSL programs on the actual GPU. It used a project-local singleplayer test world, then stopped automatically. Nine screenshots were captured from Minecraft's framebuffer; they are runtime output rather than design mockups.

| Check | Observed result |
| --- | --- |
| Module toggle and boolean setting | UI clicks changed the expected typed state. |
| Keyboard bind capture | A captured G bind was stored on Entity ESP. |
| Search and inspector | Searching Atmosphere, selecting it and toggling through the inspector worked. |
| Preset selector | Clicking Aurora advanced to Ember. |
| Slider | Maximum input produced 1.0; the tested intermediate position produced 0.6. |
| Profile save | The UI-created profile appeared in the profile store. |
| Entity ESP | Renderer submitted six targets: two hostile mobs, two passive mobs, a local remote-player fixture and a dropped diamond. Bounds, colors, names, distances and health indicators aligned in the arena. |
| Storage ESP | Renderer submitted all six container types: chest, barrel, shulker box, ender chest, furnace and hopper. Labels and outlines aligned. |
| Tracers | Renderer submitted six targets, anchored to the view. |
| Shader presets | Aurora, Ember and Frost rendered distinct grading; shaders toggled and recompiled after resource reload. |
| Fullbright | Enabled and disabled successfully in the nighttime arena. |
| Fabulous occlusion | The opaque wall hid depth-tested entity/container outlines and labels. Through-wall overlays remained visible when enabled. |
| Resize | Actual framebuffer capture changed from 1600×1000 to 1280×800; GUI/HUD and scene-copy texture adapted. |
| OpenGL checks | `glGetError()` was zero at all nine screenshot checkpoints. |

Pixel verification measured **747 amber outline pixels** for the chest visible through the wall, and **0** in the same region with depth testing under Fabulous graphics. The optional `tools/verify_render.py` script reproduces that check after `runSmoke` (requires Python and Pillow).

The Fabulous test found that its compositor clears the main framebuffer depth. Analyzer therefore draws depth-tested overlays while world depth remains available, and draws through-wall overlays after composition. This was fixed and the full smoke run repeated successfully. HUD scaling was also refined after screenshot review.

## Limits of this validation

The automated arena exercised all four entity filters and six container types. The player fixture used Minecraft's `OtherClientPlayerEntity` in the local client world; it was not a second networked player. No public server was contacted. Multiplayer latency, other utility clients, Iris/Sodium, other GPUs, and Minecraft versions beyond 1.21.4 remain unverified. The nine checkpoint checks are useful graphics evidence, not an assertion that every possible OpenGL state or mod combination was covered.

The release contains eight implemented modules. Shadowed world-lighting shader packs, model-silhouette shader ESP, friend systems, combat/movement automation and anticheat bypass are outside this release.
