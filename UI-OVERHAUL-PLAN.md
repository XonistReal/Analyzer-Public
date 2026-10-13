# Analyzer UI overhaul plan

**Current status: Analyzer 0.8.0 implements and verifies only Studio / Click GUI module layouts, with original liquid-glass GLSL for the Glass theme.** Workspace, Classic and Compact are removed. Saved retired layout names migrate to Studio on config/profile load, preserving other values and source profile bytes. The build passes 2,364 assertions; native layout/optics and Home acceptance pass 959 and 491 assertions with 11 and 31 captures. [LIQUID-GLASS.md](LIQUID-GLASS.md) explains the renderer; [STUDIO-COMPLETE.md](STUDIO-COMPLETE.md) describes current screens; [VALIDATION.md](VALIDATION.md) records verification and delivery separately. Earlier Workspace/Studio comparisons remain historical evidence. The full acceptance matrix below is broader than one release fixture.

The goal is a polished interface that makes a large module catalogue easy to browse, settings easy to understand, and automation progress easy to control. Preserve the visual effects that give Analyzer its identity while making text and actions more prominent than decoration.

## 1. Start with the user's references

For each supplied screenshot, clip or client page, record useful details and tradeoffs in layout, density, typography, spacing, selected states, settings controls, transitions, and HUD treatment. Record the user's likes and dislikes when stated; supplying an image does not establish agreement with every detail in it. Look at the same kinds of screens across references—module catalogue, a long settings panel, HUD editor, and any task/status view—rather than choosing a style from a logo or title screen alone.

Produce a short reference board with annotations and a decision sheet. Separate appearance from behaviour: a preferred colour treatment need not require that client's navigation. Create original Analyzer components; use reference assets only for comparison.

The earlier comparison used a **123-module, 1,086-setting** snapshot. Its selected sidebar/catalogue/inspector direction was implemented in 0.5.2, but the user rejected its visual result as too similar to the baseline. The current direction changes that composition: **Studio** has a centered black/gray shell, top category tabs, three columns of cards, inline expanded settings, wide Lato type and **Rose** controls. The 0.6.0 catalogue exposed **1,091 controls: 1,087 canonical settings plus four HUD Studio aliases**; its retained layouts were a historical rollout choice. The current 0.8.0 catalogue exposes **1,100 controls: 1,092 canonical settings plus eight HUD Studio appearance/Home aliases**. Only Studio / Click GUI layouts remain, with independent Glass/Classic/Minimal/Neon themes and Rose/Mint/Violet/Gold accents; Studio/Rose are fresh defaults.

## 2. Define one visual system

Use a small set of shared tokens for surfaces, text roles, semantic states, spacing, borders, corner treatment, and motion. Apply these to menus, profiles, HUD editing and Automation. Accent colour should mark selection and primary actions; status colours should consistently mean running, protecting, safety hold, paused, blocked, failed or complete. Include labels or icons with those states so colour is never the sole signal.

Studio, Click GUI, auxiliary menus and HUD use a clear title/body/caption hierarchy with Lato Regular/Bold. Measure wrapping with the same native font used for drawing. Judge text in actual Minecraft framebuffers, including bright skies and dark caves. An expanded card's scroll keeps full controls reachable instead of shrinking important words to fit. Shader surfaces do not substitute for typography or input behavior.

Keep the scale systems separate:

| Surface | Intended behaviour |
|---|---|
| Menus | Reflow or scroll before body text becomes too small. Preserve reliable controls at different Minecraft GUI scales. |
| HUD | Fresh default remains **60%**. Keep readable text floors and let widgets reflow; preserve saved scale and positions. |
| Entity/Storage ESP | Retain independent label scale, block range, distance shrink/fade, overlap handling and label limits. HUD or menu sizing must not enlarge distant ESP text. |

Set explicit project contrast targets during the token pass: at least 4.5:1 for normal text and 3:1 for large text/control boundaries. Measure the composited result of translucent panels, not just the underlying colour values. Keep an opaque, high-contrast presentation available. Avoid making a disabled control so faint that its purpose cannot be read.

## 3. Make navigation and settings predictable

The selected **Studio** has top categories, three columns of rounded cards and settings inside each card. Long card settings scroll independently; scrolling at their boundary continues through the catalogue. The new **Click GUI** uses shader category columns with inline settings, per-column scrolling, header dragging/collapse and a horizontal board rail. Workspace, Classic and Compact are removed layouts. Classic remains an independent theme. **HUD Studio** exposes persisted layout, material, menu theme and accent controls aliasing canonical Interface settings. Studio's Appearance action, Click GUI's Style action and HUD-editor settings make them reachable.

Keep the following flows consistent across the two supported shader layouts:

- Find a module by search or category, select it, inspect its settings, then deliberately change its enabled state. Inspection and toggling should have distinct visible controls.
- Organise long settings into understandable groups, such as Targets, Range, Appearance and Behaviour. Show units beside values, distinguish defaults from current values, and support precise numeric editing alongside sliders.
- Keep enabled state, selected state, keybind, and live status visually distinct. A module may be enabled while waiting for a target or missing an item.
- Reach Profiles, Edit HUD and Automation from each supported layout. Preserve search/category context when returning from another screen.
- Explain profile loading before the action: it replaces module settings and keybinds. Provide a visible success or save-failure message without relying on a brief footer flash.

Studio cards and Click GUI settings wrap labels, separate inspection from the module switch, and support numeric-value clicks or a right-click on the slider row for exact entry. Tab/Shift+Tab moves visible focus, Enter/Space activates a focused control, arrow keys adjust supported values, and Page Up/Down scrolls settings. The catalogue supplies labels through Minecraft's native narration hook. Returning from Profiles, HUD or Automation restores the parent screen's browsing context.

Add presentation metadata only where it improves grouping, units or help text. Module IDs, setting IDs, validation bounds, profiles and saved values remain the source of behaviour; the redesign should not silently rename or reset them. HUD Studio's four menu and four Home aliases serialize through their canonical Interface fields, avoiding conflicting copies. Retired layout strings migrate before typed validation and canonical/alias precedence resolution; unsupported live choices reject. The five current bundled profiles have been regenerated using canonical values and all choose Studio; saved user profiles are not overwritten. Loading an older HVH profile and returning from Profiles must resolve Studio without altering its gameplay preset.

## 4. Treat Automation as a task console

Keep the existing explicit **compose → review → start** workflow. The screen must distinguish a draft/reviewed plan from the currently running task, and make the current stage and reason for waiting immediately readable. Long requirements and failure details should be expandable or scrollable instead of cut off after a couple of lines.

Represent the actual controller states, rather than inferring success from a switch:

| State | Show | Appropriate action |
|---|---|---|
| Draft / reviewed | Task text, route, stages and prerequisites | Review or Start; Start is enabled only with a reviewed plan and usable dependency. |
| Running | Current stage, verified progress and current detail | Pause and Stop. |
| Protecting | **Task paused for safety**, the active protection action and the condition for recovery | Pause and Stop remain available; distinguish protection from ordinary task progress. |
| Safety hold | **Task paused for safety**, the current unavailable escape/recovery condition and live rechecking | Keep Pause and Stop available; show manual assistance when needed. A changing terrain/threat condition must update the detail. |
| Paused | Pause reason and stage that will continue | Resume when applicable; Stop remains available. |
| Blocked | Exact missing resource, path/workstation issue or interruption | Explain what needs to change; allow a deliberate retry/resume. |
| Completed | Verified outcome and remaining limitations, if any | Review a new task. |
| Stopped | Confirmation that owned actions were released; any recovery instruction | Review a new task. |

A safety hold is active monitoring with the original route paused, not a terminal task failure. If protection intervenes while a stage is already blocked for another requirement, return to that blocked reason afterward without automatically dispatching the stage. Distinguish that behaviour from automatically continuing a formerly running stage after threats clear and recovery checks pass. User Pause/Stop must end protection immediately.

Keep dependency availability separate from task status. “Baritone available” must not imply that the route is running or that armor is complete. Preserve explicit resumption after restart/world changes, real inventory/world completion checks, conflict handling, and cursor recovery instructions. Opening a plan, loading a profile or restoring a toggle must not start movement.

Do not add generic approval dialogs to routine configuration. Put concrete requirements beside the action they affect, and keep Pause/Stop easy to locate. Use plain language for low health, hunger, missing tools, inaccessible ore, books/XP, occupied workstations and save failures.

## 5. Prototype and implement in phases

| Phase | Status | Deliverable | Gate |
|---|---|---|---|
| A. Stabilise current work | Existing native baseline recorded in [VALIDATION.md](VALIDATION.md). | Verify Baritone fixes and record the current UI baseline. | Runtime behaviour is sound before native UI work starts. |
| B. Reference intake | Five references received; [reference board](UI-REFERENCES.md) prepared. | Annotated reference board, useful behaviours and open decisions. | User references have arrived; no premature style commitment. |
| C. Correct the direction | Earlier comparison is historical; latest user request selects reference 3's native shader card menu. | Centered black/gray Studio, top navigation, inline settings and Rose/Lato presentation. | The latest direction is recorded in [AGENTS.md](AGENTS.md). |
| D. Native Studio | Implemented: original GLSL surfaces, three-column cards, inline settings, search, appearance controls and parent-screen return. | Render actual settings with a visibly different composition from the old Workspace. | Real framebuffer review and native interactions determine the result. |
| E. Preserve settings | Retired layout names migrate to Studio; all five current starter profiles choose Studio. Canonical HUD aliases retain one authority. | Preserve unrelated settings, keybinds, source profile bytes and independent HUD/menu preferences. | Prove legacy HVH Load/Back and canonical/alias precedence in actual regression fixtures. |
| F. Verify and package | The 0.8.0 build, layout/optics and Home fixtures pass; [VALIDATION.md](VALIDATION.md) records packaging/install evidence separately. | Actual Studio/Click GUI/Glass/Home captures, preservation audit and the 0.8.0 Prism ZIP. | The full matrix remains scoped below; do not infer every item from fixture counts. |

Use the native component layer for text, controls, scrolling, focus, hit regions and animation. Studio and Click GUI extend CatalogScreen and use original GLSL signed-distance surfaces for rounded shapes, shadows and glow. Glass theme adds optical refraction from one immutable scene capture, curved edge displacement, frost, subtle dispersion and directional highlights. Minecraft GUI matrices, clipping, native TTF text and Screen input remain the integration layer; no ImGui/browser is involved. Keep GL state restoration, resource reload, resize and a readable failure fallback. Menu material Glass softens the scene backdrop; Solid keeps it crisp and presents the outer shell as solid. Glass-theme cards and controls remain optical with either material. HUD Glass uses its own scoped scene capture and remains independent of the menu theme. Gameplay and task-controller semantics remain unchanged.

Motion should clarify feedback: short selection/toggle transitions, predictable panel entrances and gentle progress changes. Support reduced motion and a true zero-motion mode across all redesigned screens. Keep particles and glow behind content, bound their cost, and verify that moving elements never shift a click target away from the visible control. Use elapsed time rather than frame-dependent animation speed.

## 6. Acceptance criteria

Capture the actual native client at **1280 × 800** and **1920 × 1080**, at Minecraft **GUI scales 2, 3 and 4** wherever the game permits them. Record the effective scale when a requested scale is clamped. Use identical content and world views for comparisons.

The release review should show:

- Studio catalogue/search, a long expanded Combat/Entity ESP card, profiles, HUD editor, and Automation draft/running/protecting/safety-hold/paused/blocked/completed states. Include empty search, missing Baritone, a long requirement and a save-error state.
- Legible primary labels and numeric values; no clipped controls, inaccessible last settings, overlapping labels, or text concealed by decorative effects. Full requirement/error text is reachable.
- Matching rendered and clickable control bounds after resize and scale changes. Mouse, scroll, keyboard focus, numeric entry, keybind capture, Escape/back and layout switching work predictably.
- Visible keyboard focus, consistent Tab order and accessible control labels. Check Minecraft narration support for the chosen native controls; document any remaining limitation.
- A 60% HUD that preserves usable text and unobstructed gameplay, alongside a dense ESP scene proving independent scale/range and label budgets.
- Automation controls reflecting real state changes, Stop releasing owned actions, and no implicit start after profile/configuration loading.
- Motion disabled without hidden movement; contrast checked against both bright and dark game backgrounds; no new OpenGL errors or meaningful steady-frame regression against the baseline on the same machine.
- Migration evidence preserving unrelated module settings, keybinds, friends, source profile bytes, HUD scale/positions and stored category-column fields. Only Studio / Click GUI remain valid current choices; retired disk values migrate to Studio. Preserve current user appearance preferences. Keep the schema unchanged and retain the vanilla Home fallback and readable shader-failure fallback.

Historical captures compare the old Workspace with the first Studio release. Current review must show Studio and the new shader Click GUI, actual curved-edge refraction, and legacy HVH profile Load/Back resolving Studio. Judge the rendered result and native interactions rather than technical status text or a changed JAR name alone.

Acceptance evidence should be actual runtime screenshots and interactions. Static prototypes help choose a direction; they are not proof that the finished Minecraft interface behaves correctly.
