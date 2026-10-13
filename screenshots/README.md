# Native captures

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
