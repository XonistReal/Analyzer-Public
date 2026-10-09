# Analyzer UI overhaul plan

**Status: design plan only.** Finish and verify the current Baritone fixes first. The user will supply Minecraft client UI references afterward; those references will determine the visual direction. This plan does not select a final theme, replace the existing menus, or introduce a new mockup.

The goal is a polished interface that makes a large module catalogue easy to browse, settings easy to understand, and automation progress easy to control. Preserve the visual effects that give Analyzer its identity while making text and actions more prominent than decoration.

## 1. Start with the user's references

For each supplied screenshot, clip or client page, record what the user likes: layout, density, typography, spacing, selected states, settings controls, transitions, and HUD treatment. Record dislikes separately. Look at the same kinds of screens across references—module catalogue, a long settings panel, HUD editor, and any task/status view—rather than choosing a style from a logo or title screen alone.

Produce a short reference board with annotations and a decision sheet. Separate appearance from behaviour: a preferred colour treatment need not require that client's navigation. Create original Analyzer components; use reference assets only for comparison.

Once references arrive, compare two coherent directions using the same module and settings content. Show the relevant tradeoffs together so the user can choose in one review. Leave colour palette, font selection, panel material, and the number of supported layouts open until then.

## 2. Define one visual system

Use a small set of shared tokens for surfaces, text roles, semantic states, spacing, borders, corner treatment, and motion. Apply these to menus, profiles, HUD editing and Automation. Accent colour should mark selection and primary actions; status colours should consistently mean running, paused, blocked, failed or complete. Include labels or icons with those states so colour is never the sole signal.

Typography should have a clear title/body/caption hierarchy, measured text wrapping, stable number widths where helpful, and full labels in the settings inspector. Compare the current Rajdhani font with reference-led alternatives at real Minecraft framebuffer sizes. Judge legibility in game, including bright skies and dark caves. A larger settings panel or a wrapped label is preferable to shrinking important words to fit.

Keep the scale systems separate:

| Surface | Intended behaviour |
|---|---|
| Menus | Reflow or scroll before body text becomes too small. Preserve reliable controls at different Minecraft GUI scales. |
| HUD | Fresh default remains **60%**. Keep readable text floors and let widgets reflow; preserve saved scale and positions. |
| Entity/Storage ESP | Retain independent label scale, block range, distance shrink/fade, overlap handling and label limits. HUD or menu sizing must not enlarge distant ESP text. |

Set explicit project contrast targets during the token pass: at least 4.5:1 for normal text and 3:1 for large text/control boundaries. Measure the composited result of translucent panels, not just the underlying colour values. Keep an opaque, high-contrast presentation available. Avoid making a disabled control so faint that its purpose cannot be read.

## 3. Make navigation and settings predictable

The current layouts serve different workflows: **Workspace** has categories, module cards and an inspector; **Classic** has draggable category columns with inline settings; **Compact** has a denser catalogue and inspector. Reconsider their roles against the references. Options include a unified shell with density choices, a main workspace plus an optional Classic view, or improved versions of all three. Select one approach after comparison; keep the existing layouts available during development.

Whichever approach wins, keep the following flows consistent:

- Find a module by search or category, select it, inspect its settings, then deliberately change its enabled state. Inspection and toggling should have distinct visible controls.
- Organise long settings into understandable groups, such as Targets, Range, Appearance and Behaviour. Show units beside values, distinguish defaults from current values, and support precise numeric editing alongside sliders.
- Keep enabled state, selected state, keybind, and live status visually distinct. A module may be enabled while waiting for a target or missing an item.
- Reach Profiles, Edit HUD and Automation from each supported layout. Preserve search/category context when returning from another screen.
- Explain profile loading before the action: it replaces module settings and keybinds. Provide a visible success or save-failure message without relying on a brief footer flash.

Add presentation metadata only where it improves grouping, units or help text. Module IDs, setting IDs, validation bounds, profiles and saved values remain the source of behaviour; the redesign should not silently rename or reset them.

## 4. Treat Automation as a task console

Keep the existing explicit **compose → review → start** workflow. The screen must distinguish a draft/reviewed plan from the currently running task, and make the current stage and reason for waiting immediately readable. Long requirements and failure details should be expandable or scrollable instead of cut off after a couple of lines.

Represent the actual controller states, rather than inferring success from a switch:

| State | Show | Appropriate action |
|---|---|---|
| Draft / reviewed | Task text, route, stages and prerequisites | Review or Start; Start is enabled only with a reviewed plan and usable dependency. |
| Running | Current stage, verified progress and current detail | Pause and Stop. |
| Protecting | **Task paused for safety**, the active protection action and the condition for recovery | Keep Stop available; show any manual recovery instruction. The HUD banner must distinguish protection from ordinary task progress. |
| Paused | Pause reason and stage that will continue | Resume when applicable; Stop remains available. |
| Blocked | Exact missing resource, path/workstation issue or interruption | Explain what needs to change; allow a deliberate retry/resume. |
| Completed | Verified outcome and remaining limitations, if any | Review a new task. |
| Stopped | Confirmation that owned actions were released; any recovery instruction | Review a new task. |

Keep dependency availability separate from task status. “Baritone available” must not imply that the route is running or that armor is complete. Preserve explicit resumption after restart/world changes, real inventory/world completion checks, conflict handling, and cursor recovery instructions. Opening a plan, loading a profile or restoring a toggle must not start movement.

Do not add generic approval dialogs to routine configuration. Put concrete requirements beside the action they affect, and keep Pause/Stop easy to locate. Use plain language for low health, hunger, missing tools, inaccessible ore, books/XP, occupied workstations and save failures.

## 5. Prototype and implement in phases

| Phase | Deliverable | Gate |
|---|---|---|
| A. Stabilise current work | Verify Baritone fixes and record the current UI baseline. | Runtime behaviour is sound before UI work starts. |
| B. Reference intake | Annotated reference board, preferred behaviours and open decisions. | User references have arrived; no premature style commitment. |
| C. Compare directions | Two low-cost compositions with identical content: catalogue, long ESP settings, HUD editing and a blocked/running task. | Choose a direction from one consolidated comparison. |
| D. Native vertical slice | Implement the chosen shell, search, one module inspector and Automation status/control using actual data. | Readability, input and live-state checks pass at the target sizes. |
| E. Expand and migrate | Apply shared components to remaining layouts/screens; migrate only required presentation preferences. | Existing configurations and profiles load without losing values. |
| F. Verify and package | Real in-game captures, interaction checks, migration evidence and release notes. | Acceptance criteria below pass. |

Prefer a shared native component layer for text, controls, layout, scrolling, focus, hit regions and animation. The existing `GuiDraw`, `Palette`, `CatalogScreen`, `MenuScreens` and HUD measurement helpers provide starting points; the standalone Workspace and Automation screens currently duplicate some composition logic. Unify presentation gradually without changing gameplay or task-controller semantics as a side effect.

Motion should clarify feedback: short selection/toggle transitions, predictable panel entrances and gentle progress changes. Support reduced motion and a true zero-motion mode across all redesigned screens. Keep particles and glow behind content, bound their cost, and verify that moving elements never shift a click target away from the visible control. Use elapsed time rather than frame-dependent animation speed.

## 6. Acceptance criteria

Capture the actual native client at **1280 × 800** and **1920 × 1080**, at Minecraft **GUI scales 2, 3 and 4** wherever the game permits them. Record the effective scale when a requested scale is clamped. Use identical content and world views for comparisons.

The release review should show:

- Catalogue/search, a long Entity ESP inspector, profiles, HUD editor, and Automation draft/running/paused/blocked/completed states. Include empty search, missing Baritone, a long requirement and a save-error state.
- Legible primary labels and numeric values; no clipped controls, inaccessible last settings, overlapping labels, or text concealed by decorative effects. Full requirement/error text is reachable.
- Matching rendered and clickable control bounds after resize and scale changes. Mouse, scroll, keyboard focus, numeric entry, keybind capture, Escape/back and layout switching work predictably.
- Visible keyboard focus, consistent Tab order and accessible control labels. Check Minecraft narration support for the chosen native controls; document any remaining limitation.
- A 60% HUD that preserves usable text and unobstructed gameplay, alongside a dense ESP scene proving independent scale/range and label budgets.
- Automation controls reflecting real state changes, Stop releasing owned actions, and no implicit start after profile/configuration loading.
- Motion disabled without hidden movement; contrast checked against both bright and dark game backgrounds; no new OpenGL errors or meaningful steady-frame regression against the baseline on the same machine.
- Migration evidence preserving module settings, keybinds, friends, profiles, HUD scale/positions, menu preference and applicable Classic column preferences. Take a recoverable configuration backup before any required schema migration and provide a fallback to the old UI during the rollout.

Acceptance evidence should be actual runtime screenshots and interactions. Static prototypes help choose a direction; they are not proof that the finished Minecraft interface behaves correctly.
