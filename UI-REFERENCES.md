# Analyzer UI references and comparison

Five screenshots were supplied by the user on October 11, 2026. Their unchanged source copies are saved in [design/ui-references](design/ui-references). The user subsequently chose **Focused workspace**, combined with **reference 3's rounded module cards/switches** and **reference 2's glass appearance**. Keep sidebar browsing and a substantial settings inspector, with menu switching inside HUD Studio. This is the recorded direction; the observations below do not imply endorsement of every detail in the source images. Images 1, 2 and 5 identify their clients in the screenshot; the other names describe their visible layouts.

## Reference board

| Reference | Useful details | Tradeoff to resolve in Analyzer |
|---|---|---|
| [1 — Temple Simple](design/ui-references/reference-1-temple-simple.png) | Persistent search, category sidebar, two-column compact cards, separate switches and restrained selection accents. | Descriptions are truncated. Analyzer needs a full description and a larger inspector for long settings. Small toolbar icons need discoverable labels and keyboard focus. |
| [2 — Temple Glass](design/ui-references/reference-2-temple-glass.png) | Several categories remain visible at once, with enabled counts and compact rows. Familiar category columns support quick scanning across modules. | Long Render/Cosmetics columns run beyond the view. Transparent surfaces over detailed terrain make text harder to read. Columns need independent scrolling and stable controls; full settings belong in a separate inspector. |
| [3 — Rounded cards](design/ui-references/reference-3-rounded-cards.png) | Strong title hierarchy, comfortable card spacing, obvious switches, short descriptions and inline settings. | Expanded cards consume catalogue space and the bottom settings are clipped. Preserve this clarity in the inspector rather than nesting 33 Kill Aura controls into a browsing card. Glow must stay behind the text. |
| [4 — Red workspace](design/ui-references/reference-4-red-workspace.png) | Visible keybinds, detailed colour editors, labeled sliders, category navigation and bounded settings sections. | Dense controls and faint secondary text are difficult to scan. Give controls more space, units and exact numeric entry; do not require a saturated red theme to make selection visible. |
| [5 — Diablo](design/ui-references/reference-5-diablo.png) | Restrained dark surfaces, generous type hierarchy, clear category selection and expandable settings. | Large tiles leave considerable unused space with only a few controls. Keep readable type while fitting useful module content; enabled and inspected states need separate indicators. |

### 1 — Temple Simple

![Temple Simple source reference](design/ui-references/reference-1-temple-simple.png)

### 2 — Temple Glass

![Temple Glass source reference](design/ui-references/reference-2-temple-glass.png)

### 3 — Rounded cards

![Rounded cards source reference](design/ui-references/reference-3-rounded-cards.png)

### 4 — Red workspace

![Red workspace source reference](design/ui-references/reference-4-red-workspace.png)

### 5 — Diablo

![Diablo source reference](design/ui-references/reference-5-diablo.png)

The reference images are comparison material. Analyzer's components should be original; client logos, avatars and other reference assets should not become product assets.

## Historical comparison: two directions with identical content

The October 11 interactive comparison in this conversation presented these directions with shared controls. The user selected Focused workspace. The machine-local browser preview remains historical design evidence; its 168 browser checks covered the simulated comparison, not Minecraft rendering or gameplay. The native implementation and evidence are recorded separately in [UI-OVERHAUL.md](UI-OVERHAUL.md).

| | Focused workspace | Category desk |
|---|---|---|
| Catalogue | Persistent category sidebar and compact module cards with brief descriptions. | Several category columns with compact module rows and enabled counts. |
| Inspection | Substantial side inspector with full labels, grouped controls and its own scroll. | The same inspector beside the columns; opening settings does not expand a row into a long column. |
| Primary benefit | Clear browsing hierarchy and more room for descriptions. | Broad category overview and fast scanning across familiar module names. |
| Main cost | Another category requires a navigation action. | Less space per category; horizontal scrolling or fewer visible columns is necessary at smaller sizes. |
| Shared test content | All 123 modules; Entity ESP's 20 controls and Kill Aura's 33 controls. | Exactly the same catalogue, values and inspector content. |

Both compositions used a **123-module, 1,086-setting** snapshot. They compared layout rather than adding reference-only modules; preview edits remained inside the comparison. The current [module catalogue](MODULES.json) now exposes 1,091 settings, including four HUD Studio aliases for canonical Interface preferences. The snapshot count above describes the historical comparison.

Shared behaviour retained as native requirements:

- Search remains visible while browsing modules. Keep query, category, selected module and inspector position while opening Profiles, HUD or Automation and returning. Switching direction preserves the same context and preview edits.
- Selecting a module opens inspection. Its visible switch changes enabled state independently. Selection, enabled state, keybind and live waiting/error status have different indicators.
- Long settings use groups, full labels, units, meaningful bounds, keyboard numeric entry and independently scrollable content. Conditional controls retain their saved values when hidden.
- Profiles clearly explain replacement of module settings and keybinds before loading. Loading and saving have reachable success/error feedback.
- HUD starts at the existing 60% default and preserves saved widget positions and scale. Menu sizing and ESP label sizing remain independent.
- Automation retains compose → review → start. Show real controller states: draft/reviewed, running, protecting, safety hold, paused, blocked, completed and stopped. Dependency availability is separate. Pause/Stop remain easy to reach; safety monitoring and a resource-blocked stage are distinct. A comparison may show sample states, but native task status must come from the controller.

## Selected native direction and remaining verification

Workspace now implements sidebar browsing, two-column rounded cards, clear switches and a substantial grouped inspector. Glass uses actual scene frost behind its translucent surfaces; Solid is available for an opaque presentation. **Workspace / Classic / Compact** menu switching appears in **HUD Studio** settings. **Glass / Classic / Minimal / Neon** menu themes remain separate from the existing HUD theme, and **Mint / Violet / Gold** accents are implemented native choices. The existing Rajdhani font remains in use; native framebuffer checks determine readability.

The four HUD Studio menu controls share the canonical Interface values instead of creating independent saved copies. Five current bundled profiles have been regenerated without duplicate alias serialization; saved user profiles remain untouched. Keep module and setting IDs, saved values, keybinds, friends, profiles, HUD positions/scale and applicable Classic column preferences. Presentation changes must not alter gameplay or automation ownership semantics.

The detailed [overhaul plan](UI-OVERHAUL-PLAN.md) retains the acceptance matrix: actual native captures at 1280 × 800 and 1920 × 1080, effective GUI scales 2/3/4, contrast over bright/dark worlds, keyboard/focus and hit-region checks, long settings and requirements, real Automation transitions, zero-motion support and GL/performance checks. The final focused native run passes 280 assertions with 19 reviewed captures, and Prism delivery is verified in [VALIDATION.md](VALIDATION.md). The full Automation state/capture matrix, renderer/GPU comparisons and complete acceptance matrix remain broader verification work.
