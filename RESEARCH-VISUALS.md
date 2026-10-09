# Analyzer visual research

Researched October 8, 2026. This document separates verified documentation/source behavior from footage that could not be viewed. It is a design reference, not a claim that Analyzer implements every item listed here.

## Temple.nz: exact site verified

The user supplied [Temple.nz's official showcase page](https://temple.nz/#showcase). The general web reader could not open the domain, but a direct read of the official page succeeded with HTTP 200. Its page and [module catalogue](https://temple.nz/modules) describe a Minecraft 26.2 client with 163 modules. This establishes the exact reference requested by the user; it does not imply the same version or feature scope for Analyzer's Minecraft 1.21.4 build.

The showcase source is a directly hosted [MP4 combat reel](https://temple.nz/showcase.mp4?v=c9424bb), not an embedded YouTube video. The page calls it ten PvP modules recorded in game and says visuals get their own reel. Only one video is embedded on the current home page. The official [Simple menu screenshot](https://temple.nz/assets/img/menu-simple.webp) and [Glass menu screenshot](https://temple.nz/assets/img/menu-glass.webp) are separate 1920×1080 images. Their alt text describes a single sidebar/modules/settings panel for Simple, and floating category windows over the world for Glass.

The feature findings below are verified against the official [public JSON catalogue](https://temple.nz/assets/data/modules.json?v=be4c7e7b). They describe the vendor's documented features. Browser playback was unavailable to this research subagent, but the main task obtained a working in-app browser and inspected the official menu images and several video frames. Those observations are identified separately below; this is not a claim that the full clip was watched.

Temple Client Legacy is a distinct reference. The verified [TempleDevelopment organization](https://github.com/TempleDevelopment) identifies its website as `templecheats.xyz`; its [Legacy repository](https://github.com/TempleDevelopment/Temple-Client-Legacy) targets Forge 1.12.2. Neither source establishes affiliation with Temple.nz. Its official README links [Normal GUI](https://github.com/TempleDevelopment/Temple-Client-Legacy/blob/main/github/images/gui.png) and [CSGO GUI](https://github.com/TempleDevelopment/Temple-Client-Legacy/blob/main/github/images/gui2.png) screenshots; those pixels were not independently inspected here.

### Actual image and frame observations

The main agent inspected these sources through browser screenshots and reported the following visible details:

- [Simple menu image](https://temple.nz/assets/img/menu-simple.webp): a near-black single panel, eight sidebar categories, compact module rows in two columns, switches, gold selected-state bars and muted descriptive text. This demonstrates information density and hierarchy rather than shader motion.
- [Glass menu image](https://temple.nz/assets/img/menu-glass.webp): tall floating translucent category windows over a gold-and-black swirling sky, beveled active pills and a global search area across the top. The underlying world is visibly present through the panels. A still image establishes transparency and layout, not the precise refraction animation.
- [Combat reel](https://temple.nz/showcase.mp4?v=c9424bb): the video reports a 43.9-second duration. A full-player browser screenshot with a reported playback position of 8.8677 seconds shows floating glass category columns in an outdoor PvP arena, active warm-gold pills and the scene visible through the panels. The caption reads “One click each / Combat and HVH, on the beat.” A screenshot at approximately 42 seconds shows a gold Temple outro. An earlier approximately 20-second view shows a name/health label above an opponent among obsidian blocks. These sampled frames do not establish every rendering module, animation timing or sky effect in the reel.

### Temple.nz menu, sky, and effects

All details in this subsection come from the official catalogue linked above, rather than inferred appearance from a video title.

| Area | Documented behavior | Useful original Analyzer direction |
| --- | --- | --- |
| Glass menu | Independent category windows; blurred, refracted world behind rounded panels. Controls include bend, distortion size/speed, edge stretch, color fringing, edge shine, cursor-following highlight, saturation and tint. Presets: Liquid, Crystal, Frosted, Smoked, Gilded, Custom. | A restrained frosted panel with a crisp rim and a subtle cursor light; keep the text plane calm. Implement an actual background sample if claiming refraction. |
| Menu/HUD | Glass and Simple ClickGUI modes; Simple is a single sidebar panel without animation. Independent GUI scale, screen fades, smooth scrolling, menu sounds. HUD can use glass or compact dark panels; moving accent gradients, daylight-adaptive contrast and a global effect-intensity setting. | One polished layout with consistent search, selected states, sliders and typography. Add independent UI scale and reduced motion. Use adaptive dimming for text over bright terrain. |
| Skybox | Painted night sky; Tendrils, Silk, Void, Plasma, Storm and Nebula animated styles. Optional aurora across the sky, rotation, tilt, star twinkle and a 2:1 equirectangular image override. | Begin with a spatial sky aurora and procedural stars; keep unrelated black-hole and weather styles out of the first iteration. |
| Sky aurora | Theme, Natural green-to-violet, Arctic cyan-to-deep-blue and Crimson palettes; brightness control. Vanilla sun/moon/stars and clouds can be independently hidden. | Analyzer's own mint/cyan/violet curtains with controllable brightness/speed, night fade, and terrain occlusion. |
| Player Aurora | A **separate cosmetic module**: Veil around the player, Crown above the head, or Ribbon Trail along the walking path. Height/speed/brightness, layered realistic folds and movement response. First-person visibility is off by default. | Treat a sky aurora and a local player aura as distinct modules. A local effect needs third-person and close-camera checks. |
| ESP | Box, Corners, 3D Hitbox, Shaded silhouette and Glow outline styles. Separate opacity, line width, padding, entity filters, range and nearest-target cap. Type/theme/health/distance/static coloring, names, distance and health bars. Shaded/Glow are explicitly limited to on-screen entities within vanilla entity render distance. | Crisp corners and translucent box fill first; genuine model-mask glow later. Use honest visibility/range limitations and bounded target counts. |
| Storage ESP | Box/Outline/Fill styles; separate container-type colors, tracers, fill opacity, line width, range and nearest-first draw limit. | Type-aware storage colors, predictable filtering and a fixed draw budget. |
| Target ESP | A band moving vertically around the target, orbiting luminous orbs with trails, or ground ring/head halo/rising sparks. Size, speed, brightness and health/theme/custom colors. Through-wall mode is separate. | A short orbit or ring effect on the last manually hit entity; fade it independently of ordinary entity ESP. |
| Hit Particles | Impact cubes, tumbling shards, bouncing beads or sparks; count, size, lifetime, spread, gravity and block collision. Optional dust/slash arc. Glass, gold, emissive light, matte and glow materials. | Bounded impact sparks with a short lifetime, original geometry and a lightweight material. Do not call a soft stroke a refractive material. |
| Jump Circles | Ripple droplets, glass rings, shockwave dome, crown or an expanding lit-block pattern. Jump/land/both triggers; landing strength depends on fall distance. | A ground-plane ripple on landing, eased expansion, fading alpha and a small fixed particle budget. |
| Trails | Tubes, ribbons, beads, sparkles, wisps, shards, afterimages, elemental and energy types. Length, width, density, drift and movement-only controls; first-person own trails off by default. | Add an original ribbon trail only after the sky, UI and ESP are stable. |
| Hit Markers | World-space ring/box/vortex/glow or a crosshair marker. Neon/glass/matte/metal/frosted finish, duration, hit/kill colors and explicit through-wall option. | A clean crosshair hit flash plus an optional short world ring; make kill and hit states visibly distinct. |

The catalogue also lists Ambience, camera-only Motion Blur, Realistic Water, Wet Ground, Waving Foliage, Air Effects and combinable wings/halos/crowns. These are sizeable rendering systems, not evidence that a small overlay implements a shader pack. A deliberate first build should prioritize a coherent subset and verify it in game.

## Verified visual patterns

### Temple Client Legacy: motion and a focused settings window

The official [CSGO GUI source](https://github.com/TempleDevelopment/Temple-Client-Legacy/blob/main/src/main/java/xyz/templecheats/templeclient/features/gui/clickgui/csgo/CsgoGuiScreen.java) defines a 600 ms InOutCircle blur animation and a 300 ms OutQuint zoom animation. Its render path draws dark rounded layers, optional Gaussian blur, and either particles or snow. These are source-confirmed behaviors rather than observations of video playback.

The [ClickGUI settings module](https://github.com/TempleDevelopment/Temple-Client-Legacy/blob/main/src/main/java/xyz/templecheats/templeclient/features/module/modules/client/ClickGUI.java) exposes background tint, blur radius/compression, scale, theme, gradient start/end colors, rainbow mode, keybind visibility, and descriptions. This supports a design where visual treatment has explicit controls rather than being forced on every user.

Its separate [entity ESP](https://github.com/TempleDevelopment/Temple-Client-Legacy/blob/main/src/main/java/xyz/templecheats/templeclient/features/module/modules/render/esp/sub/Entities.java) and [shader ESP](https://github.com/TempleDevelopment/Temple-Client-Legacy/blob/main/src/main/java/xyz/templecheats/templeclient/features/module/modules/render/esp/sub/Shader.java) implementations filter entity classes and distance. Shader ESP has line-width and opacity settings and renders entity geometry. A glowing model silhouette is a different rendering technique from drawing a bounding box with a soft outer stroke.

**Analyzer direction:** use a brief eased opening, a dark readable panel, an accent gradient, clear keybind feedback, and optional low-motion particles. Keep values readable while controls move. Build model masks separately if adding true silhouette glow later; retain explicit box/corner styles for clarity.

### Future: polish in the interaction details

Future's [official changelog](https://www.futureclient.net/) documents independent scrolling for each GUI panel, retained color-picker alpha during hexadecimal edits, arrow-key slider adjustments, manual numeric input, input-capture fades, configurable shader glow, and moving color gradients. These are documentation-confirmed features; their appearance and animation timing were not viewed.

**Analyzer direction:** ensure hover, focus, active, and binding states each have a visible treatment. Numeric sliders should show units and current values. Color edits should preserve opacity. A strong visual style still needs independent scrolling and predictable input focus.

### RusherHack: ESP is a style system

The [official ESP documentation](https://rusherhack.org/features/modules/ESP.html) lists Shader, Box, Csgo, and Image modes for both entities and storage. It separates target filters and color modes from drawing style. Shader fill can be flat, dots, or lines, with independent fill opacity, outline width, and outline fade. The box and CSGO styles have their own dimensions and options. This was documentation inspection, not a visual playback test.

**Analyzer direction:** provide recognizable Corners, Box, and later Silhouette styles. Expose color, fill, outline width, distance, labels, and through-wall visibility independently. Use a thin crisp inner outline plus a restrained outer glow. Preserve the existing depth-tested/through-wall rendering separation.

### LiquidBounce: search and information hierarchy

The [official ClickGUI documentation](https://liquidbounce.net/docs/usage/clickgui) describes a blurred world behind draggable panels, independent panel scrolling, highlighted enabled modules, searchable results, keyboard result navigation, right-click navigation to settings, and separate tabs for modules, HUD editing, and global settings. Official screenshot references include [search](https://liquidbounce.net/docs/images/get-started/clickgui/clickgui-search.png) and [settings](https://liquidbounce.net/docs/images/get-started/clickgui/clickgui-settings.png). The image-search index described the search overlay and grouped controls; this session did not independently inspect their pixels or motion.

**Analyzer direction:** maintain a stable sidebar and inspector while adding stronger selected-state styling, keyboard navigation, grouped settings, and small descriptions. Preserve search as a functional tool. Use subtle background effects behind the content, keeping the content plane visually calm.

### ThunderHack Recode: local world effects

The [official repository](https://github.com/Pan4ur/ThunderHack-Recode) identifies the client as a Minecraft Crystal/Sword HvH project. Its source exposes configurable GUI blur, gradient direction, typography, dimensions, and closing animation in [ClickGui.java](https://github.com/Pan4ur/ThunderHack-Recode/blob/main/src/main/java/thunder/hack/features/modules/client/ClickGui.java).

[HitParticles.java](https://github.com/Pan4ur/ThunderHack-Recode/blob/main/src/main/java/thunder/hack/features/modules/render/HitParticles.java) exposes particle mode, physics, amount, lifetime, speed, and scale. [JumpCircle.java](https://github.com/Pan4ur/ThunderHack-Recode/blob/main/src/main/java/thunder/hack/features/modules/render/JumpCircle.java) supports ease-out, rotation, scale, and fading textured quads. Its [ESP.java](https://github.com/Pan4ur/ThunderHack-Recode/blob/main/src/main/java/thunder/hack/features/modules/render/ESP.java) includes filtered entity colors and health rendering. These findings come from the source, without runtime testing.

**Analyzer direction:** add short-lived local jump ripples and damage sparks with a fixed cap and fade-out. Use Analyzer's mint/violet palette and original procedural geometry instead of copying assets. Keep the effects cosmetic and configurable.

## A real sky aurora

The author-maintained [Cirrus repository](https://github.com/JevenDev/Cirrus) describes animated northern lights, layered clouds, sky color palettes, stars, and weather-aware effects without a shader pack. It warns that other sky/cloud renderers may override or conflict with those effects. Cirrus is All Rights Reserved, so it is a feature reference only; its code and assets are not suitable for copying into Analyzer without permission.

The publisher's [Auroras description](https://www.curseforge.com/minecraft/mc-mods/auroras/description) explains configurable wave frequency, amplitude, colors, and curtain-like light. This is useful feature documentation rather than an independently watched demonstration.

**Original Analyzer design proposal:** draw three long, irregular curtains in the sky rendering stage. Give each a narrow luminous base, translucent vertical streaks, a broad soft falloff, and a slowly moving wave. Blend mint into icy cyan with a violet fringe. Anchor the shape to world/camera direction so it rotates naturally with the player's view. Terrain must remain opaque in front of it. Night-time fading, an Overworld-only default, rain attenuation, brightness/speed controls, and a reduced-motion setting make the effect usable beyond a showcase.

Scene-wide color grading cannot provide these spatial properties. Avoid adding a bright screen-space ribbon over terrain or tinting the entire world to imitate northern lights. The aurora should be visible against the sky while preserving ordinary block and HUD colors.

## Verified video references and observation status

| Reference | Provenance | Observation status |
| --- | --- | --- |
| [Future Client Showcase](https://www.youtube.com/watch?v=WZEBWOf6ILI) | Embedded by Future's official site | Playback unavailable; no timestamps claimed |
| [Future GUI while a game GUI is open](https://www.youtube.com/watch?v=nWVL5ZRj41g) | Linked in Future's official changelog | Playback unavailable; behavior is described by the changelog |
| [Ai_24 channel](https://www.youtube.com/@Ai_24) | Credited for a showcase by ThunderHack's official README | Channel reference only; no particular footage inspected |
| [KiLAB Gaming channel](https://www.youtube.com/@KiLABGaming) | Credited for an overview by ThunderHack's official README | Channel reference only; no particular footage inspected |
| [Cirrus trailer](https://www.youtube.com/watch?v=juhrI-G-xjo) | Embedded by the author's repository | Playback unavailable; no visual/timing observations claimed |
| [Temple.nz combat reel](https://temple.nz/showcase.mp4?v=c9424bb) | Direct MP4 embedded by the exact official showcase page | Main agent inspected sampled frames described above; full-clip viewing not claimed |

## Implementation and validation priorities

Analyzer 0.2.0 implements the spatial sky (Aurora/Nebula/Ember), scene-blurred Glass menu, accent/motion controls, cursor-reactive menu particles, Screen ESP/radar and target/filter diagnostics. It preserves the single-panel native layout and uses original shaders and geometry. Local jump/hit/trail cosmetics and model-silhouette masks remain future ideas. Actual runtime results and compatibility limits are recorded in [VALIDATION.md](VALIDATION.md).

1. Add the world-sky aurora as an independent rendering effect and compare terrain/sky pixels with it off and on.
2. Strengthen UI depth, selected-state accents, animation pacing, and keyboard/focus behavior without sacrificing legibility.
3. Add configurable local jump/damage effects with bounded lifetimes and object counts.
4. Refine ESP appearance while keeping actual health, labels, range filters, depth visibility, and resize/resource-reload checks.
5. Validate day/night, rain, first/third person, dimensions, Fabulous rendering, menu-open/closed states, and low motion. Do not claim Iris/Sodium compatibility until tested.

Analyzer should retain original code and artwork. Temple Client Legacy, Meteor, LiquidBounce, and ThunderHack have GPL source obligations; Future and RusherHack are proprietary products. Temple.nz is used as a visual/feature reference only, with no source-code licensing assumed. This research does not incorporate any of these clients' code or assets.
