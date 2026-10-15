> **Current 0.11.0:** [Debug ESP](COMBAT-ESP.md) replaces forced fills/glow/panels with configurable debug boxes, storage colors and matching tracers. The tests/captures below document the earlier label-readability update.

# ESP readability and distance controls

Analyzer's Entity ESP and Storage ESP now give world overlays their own distance and label controls. The HUD stays readable at 60%; ESP text can shrink and fade as targets move away. Distances use Minecraft blocks. The `b` suffix on a tag means blocks.

## What changed

The earlier world renderer multiplied billboard scale by distance, up to eight times its starting size. This kept names around 80–120 blocks away large. Screen ESP also expanded small projected boxes to at least three GUI pixels, surrounded them with several glow layers, and drew labels through the menu/HUD minimum text scale. Those choices caused the dense scene in the user's screenshot.

World and Screen labels now use the same separate distance curve. At the default label scale, a nearby name has a nominal 14-pixel glyph size. It stays full size through eight blocks, then shrinks smoothly toward 6.3 pixels at the label range. Default far opacity is 35%. Names outside label range disappear while boxes remain available up to the separately configured geometry range. Text that would fall below six nominal physical pixels is omitted instead of enlarged.

Nearest labels claim space first. Overlapping labels are omitted; Screen tags can use four nearby positions around the actual box. They no longer spread through six rows far from their target. Entity and container label budgets are separate. Screen tags also avoid the existing HUD areas, hotbar, crosshair, and world billboard labels.

The renderer preserves real projected box dimensions. Screen lines are drawn in physical framebuffer pixels, so changing Minecraft's GUI scale cannot enlarge a tiny faraway entity into a large marker. Subpixel markers are omitted; tiny boxes have no surrounding glow. Nearby 3D boxes retain the entity's actual pose and dimensions. ESP changes never alter entities or server state.

## Actual settings and fresh defaults

Existing saved geometry range values are preserved. New settings get their defaults when loading an older configuration or profile.

| Setting | Entity ESP | Storage ESP | Effect |
|---|---:|---:|---|
| Range (blocks) | 128 | 64 | Box, category selection, and radar range; existing `range` setting retained. |
| Label range (blocks) | 48 | 40 | Names and world billboard health panels stop beyond this distance, bounded by geometry range. |
| Label scale | 1.0 | 1.0 | Multiplies ESP glyph size independently of HUD scale; range 0.5–2.0. |
| Shrink distant labels | On | On | Applies the smooth distance size curve. Turn off for fixed label size within label range. |
| Full-size distance (blocks) | 8 | 8 | Labels retain selected size up to this distance. |
| Distant label size | 0.45 | 0.45 | Size multiplier at label range; 0.45 means 45% of the near size. |
| Fade distant labels | On | On | Opacity decreases with the same distance curve. |
| Maximum labels | 16 | 12 | Nearest submitted tag-panel limit per module; zero hides tags. |
| Hide overlapping labels | On | On | Rejects colliding tags. Turn off to show dense tag stacks. |
| Minimum marker size (pixels) | 3 | 3 | Rejects a projected box if its largest dimension is smaller than this physical pixel threshold. Zero disables this filter. |
| Line width | 1.5 | 1.5 | Existing setting retained; Screen outlines round to whole physical pixels and cap thickness for tiny boxes. |

Players/mob/item filters, through-wall mode, Boxes/Corners/Screen style, names, health, and radar remain available. Screen health bars are shown only for meaningful projected boxes (at least twelve pixels tall) inside label range. Their `Health` switch remains separate from name tags.

## Verified evidence and captures

Implementation: `core/BuiltinModules.java`, `render/AnalyzerRenderer.java`, and the Minecraft-independent `render/EspLayout.java`.

The standalone `EspLayoutSelfTest` has passed **38 assertions** with Java 21. It checks distance endpoints, monotonic shrink/fade, range cutoffs, malformed values, disabling attenuation, projected tiny/narrow boxes, viewport bounds, and rejecting a simulated 71-tag overlap stack.

The `EspSmoke` integration run passed **31 assertions and five actual 1920 × 1080 captures** in the isolated development world. Its real fixture contains 71 selected hostile entities plus a small dropped item. The checks verified world and Screen label budgets, overlap rejection, different near/middle sizes, GUI scales 2 and 4, changing HUD scale, independent label and geometry ranges, half label scale, storage tags, disabled-module cleanup, and OpenGL state. Every capture passed its OpenGL check.

The five captures were visually inspected. Nearby tags are larger than 29-block tags, distant unnamed boxes keep their projected dimensions, the tag stack collapses when overlap hiding is enabled, and the storage capture contains two names for multiple container boxes. GUI scale 4 enlarges the vanilla hotbar/chat while ESP text remains the same physical size. Images are copied to [the 0.5.0 capture folder](../screenshots-0.5.0/) and this source tree's `screenshots/` folder.

Actual in-game captures:

| Capture name | Scene |
|---|---|
| [analyzer-esp-dense-unfiltered.png](../screenshots-0.5.0/analyzer-esp-dense-unfiltered.png) | Dense fixture with overlap hiding disabled, for a same-scene crowd comparison. This is a diagnostic opt-out, not an old-release screenshot. |
| [analyzer-esp-distance-world.png](../screenshots-0.5.0/analyzer-esp-distance-world.png) | Same fixture with nearest-first overlap rejection and default 16-tag budget, using 3D Corners mode. |
| [analyzer-esp-distance-screen-gui2.png](../screenshots-0.5.0/analyzer-esp-distance-screen-gui2.png) | Same fixture with projected Screen boxes and distance-sized text at GUI scale 2. |
| [analyzer-esp-distance-screen-gui4.png](../screenshots-0.5.0/analyzer-esp-distance-screen-gui4.png) | Same fixture at GUI scale 4 to compare physical box and label size. |
| [analyzer-esp-storage-declutter.png](../screenshots-0.5.0/analyzer-esp-storage-declutter.png) | Actual chest/barrel fixture with a two-container label budget. |

The earlier 0.4.0 captures remain in `../screenshots-0.4.0/`. These new scenes exercise the actual client render path, not a static interface mockup.

The crowd stress fixture places sixty entities at almost the same coordinates. Their individual translucent fills accumulate into a bright solid central box even though tags are decluttered. This release reduces tag overlap and distant-marker clutter; it does not merge overlapping entity geometry. Ordinary entities spread across a scene do not produce that exact stacked fill. The observed effect is kept in the evidence rather than hidden by image editing.
