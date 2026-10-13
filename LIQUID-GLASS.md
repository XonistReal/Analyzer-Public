# Liquid glass and current layouts — Analyzer 0.8.0

Only **Studio / Click GUI** are module layouts. Workspace, Classic and Compact screen implementations are removed from the runtime. Opening a saved config or profile containing one of those old layout values selects Studio, including loading an old HVH preset and pressing **Back to modules**. The migration happens while decoding settings; it does not rewrite the saved profile file or discard its other values. Valid canonical Interface fields take precedence over HUD aliases; malformed canonical fields can recover from a valid alias. Editing a setting to a removed choice is rejected.

All five bundled full starter profiles now select Studio. Their names, gameplay settings, themes and keybinds retain their separate meaning. **Classic is still a theme**, and the starter profile named Classic remains available; neither restores the removed Classic screen.

## Controls

Open **Appearance → HUD Studio → Menu layout** and choose **Studio** or **Click GUI**. Fresh defaults remain Studio/Rose. Studio uses top category tabs, three columns of cards and inline settings. Click GUI is a new native shader layout with horizontally paged category columns, Lato text and the same typed settings.

- Use Click GUI's category strip to bring any of the nine categories into view, or use its board scrollbar/arrows and Shift+wheel.
- Drag a category header to offset its column. Use its small collapse control or right-click the header to collapse; Reset columns restores the initial arrangement.
- Wheel-scroll individual columns. Select a module to inspect it; use its separate switch to toggle it.
- Profiles, HUD editing, Automation, exact numeric input and key capture retain native callbacks and parent return. Opening a menu or loading a profile does not start a task.

Existing stored category positions are retained under their prior storage IDs and adapted to bounded offsets in the new board. No removed screen can be reopened through those fields.

## Theme and material

**Menu theme → Glass** gives the menu's surfaces actual scene refraction, slight frost, smoked body transmission, clearer curved edges, subtle colour separation and directional glints. Glass cards work with **Panel material → Solid** too. **Panel material → Glass** controls the existing softer background treatment behind the whole menu; selecting Solid keeps the scene behind it sharp. Theme and material remain separate preferences.

**HUD theme → Glass** applies the same optical surface to HUD panels over the world. HUD and menu themes are independent. Home panes/buttons, Profiles, numeric entry, Automation and the HUD editor share the current shader presentation. Classic, Minimal and Neon retain their other surface styles, and Rose, Mint, Violet and Gold remain the accents. Foreground text and controls remain crisp native elements.

**Watermark → Logo → Analyzer logo / A logo** still renders exactly one supplied asset. Both original transparent PNGs and their glow remain unchanged; placement, scale and saved logo choice are preserved.

## Native rendering

Analyzer's original GLSL samples one immutable copy of the actual incoming draw framebuffer per owned Glass scope, before custom UI/text is painted. All surfaces in that scope reuse the copy. The curved edge normal is evaluated in physical framebuffer pixels, so requested GUI scale does not multiply the lens distortion. Samples never read the texture being drawn into. The renderer respects native GUI matrices/clipping and restores incoming GL bindings, viewport, active texture, read/draw framebuffer state, shader, blend and depth state. Resource reload and shutdown release the shader and scene texture.

This is an original shader inspired by the supplied visual reference, not Apple's implementation or code copied from another client. It uses native Minecraft screens and OpenGL; there is no ImGui.

## Actual Minecraft captures

| View | Capture |
| --- | --- |
| Studio with Glass theme | [Studio](screenshots/analyzer-liquid-studio-glass.png) |
| Rebuilt category columns | [Click GUI](screenshots/analyzer-liquid-click-gui.png), [Solid backdrop](screenshots/analyzer-liquid-click-gui-solid-backdrop.png) |
| Old HVH profile, Load and Back | [Studio return](screenshots/analyzer-liquid-legacy-hvh-return.png) |
| HUD with one selected logo | [Analyzer](screenshots/analyzer-liquid-hud-analyzer.png), [A](screenshots/analyzer-liquid-hud-a.png) |
| Identical stripe scene, refraction toggled by test fixture | [Refraction](screenshots/analyzer-liquid-optics-refraction.png), [No refraction](screenshots/analyzer-liquid-optics-no-refraction.png) |
| Home and Options | [Panorama](screenshots/analyzer-liquid-home-panorama.png), [Options](screenshots/analyzer-liquid-home-options.png) |

The stripe fixture holds opacity, frost and highlights constant. The final run measures a **23 physical-pixel edge displacement**, mean interior channel difference **0.0171059**, and **zero outside/corner difference**. This distinguishes refraction from a translucent overlay. The fixture also binds a distinct read framebuffer and texture sentinels to verify the actual draw-source capture and GL restoration. Refraction switching is a diagnostic only, not a new user setting.

Java 21 build checks pass **2,364 assertions**. Actual Minecraft 1.21.4 checks pass **959 layout/optics assertions** and **491 Home assertions**, with **42 final PNGs**. Six window/GUI cases cover 1280×800 and 1920×1080 at requested scales 2/3/4; Minecraft clamps 1280 GUI4 to effective scale3. The final runtime logs have no ERROR/FATAL or GL/shader failures. Primary layout, optics, profile return, HUD and high-scale Options captures were visually reviewed. Performance benchmarks and other GPU/shader-pack combinations are outside this run.

[VALIDATION.md](VALIDATION.md) records final build, runtime and Prism preservation evidence. Older screenshot prefixes and validation sections describe their historical releases.
