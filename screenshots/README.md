# Screen-locked tracers and local name alias - 0.13.1

Three unedited 1920 x 1080 native captures from the completed isolated `runTracerPrivacySmoke`. All five representative tracer producers use their selected screen origin. Actual native chat, tab, sidebar and String/Text/OrderedText/Lato labels display the local alias `PrivatePilot`. Blocks, mobs and scoreboard are integrated-server fixture preconditions; no user world or account was altered. These frames show through-wall mode; the complete 162-check run also verifies depth-tested origins and live walking bob.

- [Crosshair origin and local name alias](analyzer-native-tracer-privacy-crosshair.png)
- [Bottom origin](analyzer-native-tracer-privacy-bottom.png)
- [Top origin in third person](analyzer-native-tracer-privacy-top.png)

[Controls and limits](../TRACERS-AND-NAME.md) · [Acceptance](../production-validation/native-0.13.1/acceptance.json). Historical screenshots and their scopes follow.

# Finder and specialized ESP screenshots - 0.13.0

Five unedited 1920 x 1080 native Minecraft framebuffer captures from the completed isolated `runFindersSmoke`. Actual integrated-server mobs, equipment, blocks and chunk patterns are fixture preconditions; Scout is an explicitly client-local invisible player model. All five images were visually inspected. The run completes 99 checks and 25 detector positives, plus the actual map HUD/Findings screen; Bedrock Void is covered by pure policy fixtures only.

- [Entity families, invisible player and actual equipment](analyzer-native-finders-entities.png)
- [Actual posed skeleton limbs](analyzer-native-finders-skeleton.png)
- [Block state and column detectors](analyzer-native-finders-blocks.png)
- [Stash geometry and original coordinate map](analyzer-native-finders-stash-map.png)
- [Native shader Findings history browser](analyzer-native-finders-history.png)

[Controls and limits](../FINDERS.md) - [Current acceptance](../production-validation/native-0.13.0/acceptance.json). Historical screenshots and their scopes follow.

# Shader entity materials - 0.12.0

Nine unedited 1920 x 1080 native Minecraft framebuffer captures from `runChamsSmoke`. Actual integrated-server mobs demonstrate hostile/non-hostile groups; Scout is an explicit client-local player model fixture. Five posed meshes submit 840 vertices in the focused arena. A real received stone-brick wall tests occluded palettes and the depth-only opt-out. GL-state restoration and resource reload pass; the final capture combines independent debug boxes and metallic chams.

- [Flat](analyzer-native-chams-flat.png)
- [Metallic](analyzer-native-chams-metallic.png)
- [Glass](analyzer-native-chams-glass.png)
- [Glow](analyzer-native-chams-glow.png)
- [Wireframe](analyzer-native-chams-wireframe.png)
- [Occluded colors through a real wall](analyzer-native-chams-occluded.png)
- [Occluded pass disabled](analyzer-native-chams-depth-only.png)
- [Metallic after resource reload](analyzer-native-chams-reloaded.png)
- [Metallic with independent debug boxes](analyzer-native-chams-with-boxes.png)

[Settings and limits](../ENTITY-CHAMS.md). Current evidence: [0.12.0 acceptance](../production-validation/native-0.12.0/acceptance.json). These focused material tests do not establish unrelated combat/automation behavior or third-party renderer compatibility. Older screenshots and their version scopes follow.

# Native ESP photos — October 15, 2026

Four original 1920 × 1080 in-game screenshots from Analyzer 0.11.0 in an isolated development world:

- [Entity boxes](analyzer-0.11.0-esp-entities.png): red hostile mobs, mint non-hostile mobs and cyan local player demo.
- [Storage colors](analyzer-0.11.0-esp-storage.png): double/trapped/ender chests, barrel, shulker, furnace, hopper, brewing stand, dispenser, crafter, pot and storage cart.
- [Matching tracers](analyzer-0.11.0-esp-tracers.png): entity/storage colors with their configured lines.
- [Through-wall boxes](analyzer-0.11.0-esp-through-walls.png): actual stone wall hiding the selected scene.

Mobs, containers and wall are integrated-server objects; Scout is an explicit client-local player rendering fixture. Images are unedited native framebuffer captures. All four passed their OpenGL error checks; nine focused checks completed. [Evidence](../production-validation/native-0.11.0/esp-photos.json). No personal world or settings were changed.

## Earlier captures

# Current native captures

The earlier captures below document their original releases. The original delivery left gameplay checks to the user; this later focused photo session is recorded separately.

Analyzer **0.10.2** adds 24 recorded images under `analyzer-native-0.10.2-*`. [Validation](../VALIDATION.md) distinguishes completed mining/core stages, earlier `base-*` component runs and remaining manual gameplay checks.

- [600 sand](analyzer-native-0.10.2-automation-600-sand.png), [96 cobblestone](analyzer-native-0.10.2-automation-grass-covered-96-stone.png)
- [Earlier X-ray fade](analyzer-native-0.10.2-base-xray-fade-terrain.png), [outlines](analyzer-native-0.10.2-base-xray-terrain-outlines.png), [ores only](analyzer-native-0.10.2-base-xray-ores-only.png)
- [Vine descent](analyzer-native-0.10.2-base-recovery-vine-descent.png), [recovered workstation](analyzer-native-0.10.2-base-workstations-owned-workstation-recovered.png)

## Historical captures

# Native captures

Analyzer **0.10.1** adds eight current `analyzer-native-ui-*` captures from focused native startup/browser and protection regressions. The [validation record](../VALIDATION.md) distinguishes simulated physical-input snapshots from real rendered screen input, authoritative player travel and normal Survival interactions.

- [Schematic folder browser](analyzer-native-ui-schematic-browser.png), [selected file and material review](analyzer-native-ui-schematic-selected-review.png)
- [Held Start waits for release](analyzer-native-ui-start-waits-for-release.png), [resumed task reaches its destination](analyzer-native-ui-task-start-resume-complete.png)
- [Zombie defense](analyzer-native-ui-safety-zombie-defense.png), [mining resumes](analyzer-native-ui-safety-mining-resumed.png), [food recovery](analyzer-native-ui-safety-eating.png), [creeper retreat](analyzer-native-ui-safety-creeper-retreat.png)

## Earlier 0.10.0 captures

Analyzer **0.10.0** adds **14** native automation captures under `analyzer-native-automation-*`. They come from the complete isolated Survival sequence without Baritone: route/pause/resume, actual mining, exact construction, schematic material review, Nether travel, real smithing, crafting/enchanting, repeated broken-tool recovery and mob/food protection. The full run and its limits are recorded in [Validation](../VALIDATION.md).

- [Material-first schematic review](analyzer-native-automation-console.png), [exact construction](analyzer-native-automation-building.png)
- [Native Nether travel](analyzer-native-automation-nether.png), [real smithing upgrades](analyzer-native-automation-smithing.png)
- [Repeated tool recovery](analyzer-native-automation-tool-recovery.png), [enchanted armor](analyzer-native-automation-enchanted-kit.png)
- [Zombie defense](analyzer-native-automation-safety-zombie-defense.png), [food recovery](analyzer-native-automation-safety-eating.png), [creeper retreat](analyzer-native-automation-safety-creeper-retreat.png)

## Earlier 0.9.0 captures

Analyzer **0.9.0** adds Server Privacy. Its final native acceptance has **250 checks and four title-menu captures**; a fresh-frame poison test verifies zero differences after preceding framebuffer content is erased. Separate current runs pass **919 layout/optics checks with 11 captures** and **491 Home checks with 31 captures**. All **46 new images** use `analyzer-privacy-`; the first 0.9.0 QA captures are replaced by the final corrected title render.

- [Privacy in Studio](analyzer-privacy-studio.png), [Solid material](analyzer-privacy-studio-solid.png)
- [Privacy in Click GUI](analyzer-privacy-click-gui.png), [Solid material](analyzer-privacy-click-gui-solid.png)
- [Current in-world Studio glass](analyzer-privacy-liquid-studio-glass.png)
- [Current old-HVH Back return](analyzer-privacy-liquid-legacy-hvh-return.png)
- [Current Panorama Home](analyzer-privacy-home-panorama.png), [Options](analyzer-privacy-home-options.png)

[Server Privacy](../SERVER-PRIVACY.md) describes tested protections and limits. [Validation](../VALIDATION.md) records the 0.9.0 build and fresh Prism delivery. Older prefixes below retain their original historical PNGs and counts.

## 0.8.0 liquid-glass captures

Analyzer **0.8.0** uses `analyzer-liquid-` for the two current shader layouts and optical Glass theme. The current layout/optics run has **11 captures and 959 assertions**; the title-only Home regression has **31 captures and 491 assertions**. These are actual Minecraft framebuffer captures from the verified build, which passes **2,364 assertions**. Release packaging/install hashes are recorded separately in [Validation](../VALIDATION.md).

- [Studio Glass](analyzer-liquid-studio-glass.png)
- [New shader Click GUI](analyzer-liquid-click-gui.png)
- [Click GUI with a crisp Solid background](analyzer-liquid-click-gui-solid-backdrop.png)
- [Legacy HVH profile Load/Back returns to Studio](analyzer-liquid-legacy-hvh-return.png)
- [Refraction enabled](analyzer-liquid-optics-refraction.png) and [diagnostic refraction disabled](analyzer-liquid-optics-no-refraction.png)
- [HUD with Analyzer logo](analyzer-liquid-hud-analyzer.png) and [HUD with A logo](analyzer-liquid-hud-a.png)
- [Panorama Home](analyzer-liquid-home-panorama.png) and [Orbit](analyzer-liquid-home-orbit.png)
- [Worlds](analyzer-liquid-home-worlds.png), [servers](analyzer-liquid-home-servers.png) and [Options](analyzer-liquid-home-options.png)

[Liquid glass](../LIQUID-GLASS.md), [Studio controls](../STUDIO-COMPLETE.md) and [Home controls](../HOME-SCREEN.md) describe the current screens. Workspace, Classic and Compact layouts are removed; pictures of them below are historical, not selectable alternatives.

## Historical releases


Analyzer 0.7.2 uses `analyzer-watermark-` for the two exclusive logo choices, their setting and HUD editor rendering. Earlier prefixes retain their recorded release evidence.

Analyzer 0.7.1 uses `analyzer-refresh-` for 17 Studio/HUD/auxiliary screenshots and 31 title-only Home regressions (48 new captures). [Complete Studio refresh](../STUDIO-COMPLETE.md) describes the screens and supplied transparent branding; the captures retain their historical release prefix. Older prefixes below retain historical evidence.

Actual Minecraft framebuffer captures. Analyzer 0.7.0 uses `analyzer-home-` for the custom panorama/Orbit Home, native world/server/options pages, blur/reload and framebuffer/GUI-scale cases. [Home controls](../HOME-SCREEN.md) links them. The preceding `analyzer-module-sweep-` captures cover all nine module categories; [Module test report](../MODULE-TESTS.md) records the bounded 123-module sweep. Analyzer 0.6.0's native Studio menu uses the `analyzer-studio-` prefix for three-column cards, inline settings, Solid material, themes, HUD appearance controls, retained layouts and framebuffer/GUI-scale combinations. [UI controls and captures](../UI-OVERHAUL.md) links these images; [Validation](../VALIDATION.md) records final checks and artifact hashes.

The `analyzer-ui-overhaul-` images belong to the earlier 0.5.2 Workspace overhaul and remain historical comparison evidence. `analyzer-readable-workspace.png` shows the older sidebar/inspector baseline; `analyzer-studio-solid.png` shows the new centered black/gray menu. Their dates and corresponding build records matter when comparing outcomes.

Earlier Analyzer 0.5.2 water, snow, live-hold and creeper-blast scenes remain historical reference. The `analyzer-gameplay-settings-` captures record the movement, combat and HVH settings overhaul.
