# Entity Chams — 0.16.0 beta materials

0.16.0 adds five materials, a separate material for occluded models, a secondary color and an optional edge outline. Everything below the 0.12.0 heading still applies; saved configurations keep their material and colors.

| New material | Appearance |
| --- | --- |
| Shaded | The category color with soft wrapped key light and sky fill, so models read as 3D shapes |
| Hologram | Scanlines, a sweeping light band and iridescent edges, blending toward the secondary color |
| Gradient | Two colors flowing diagonally across the model, lightly shaded |
| Rim light | Clear center with a bright silhouette edge |
| Galaxy | A slowly drifting nebula between the two colors with twinkling stars |

- **Occluded material** chooses the material for the through-walls pass: *Same as visible*, Flat, Glow, Rim light, Wireframe or Hologram. A visible Metallic model with an occluded Rim light, for example, keeps walls readable without a solid blob behind them.
- **Secondary material color** is the second color for Hologram, Gradient and Galaxy. It appears only when one of those materials is in use.
- **Outline model edges** draws the model's real face edges over any material except Wireframe, using the same width as Wireframe.

All ten materials are one GLSL program (`chams.fsh`) with the same depth passes, capture limits and GL-state restoration as before. `ChamsGlChecks` draws every material and each occluded material in the native GL check; that check needs a running client and was not rerun for this beta. See [VALIDATION.md](VALIDATION.md).

# Entity Chams — 0.12.0

Enable **Render → Entity Chams** to apply original GLSL materials to the actual animated living-entity models. Entity ESP and Tracers remain independent: use models, debug boxes, lines, or a combination. Chams starts disabled and does not change an existing profile's other modules.

| Material style | Appearance |
| --- | --- |
| Flat | Solid, unlit category color |
| Metallic | Directional shading, specular highlights and a reflected light band |
| Glass | Translucent centers with reflective Fresnel edges |
| Glow | Emissive color with a brighter, gently animated rim |
| Wireframe | Native model quad edges, without triangle diagonals |

Glass is a translucent model material; it does not refract a captured scene. Glow stays on the model surface; it does not add a post-process bloom halo. The menu's liquid-glass shader remains separate.

**Targets** selects players, hostile mobs and non-hostile mobs, with a range control, optional invisible entities and an optional own player in third person. Spectators, removed/dead entities and the current first-person camera body are excluded. Other living objects such as armor stands are outside these three groups.

**Visible colors** can follow Entity ESP's category colors even while box ESP is disabled. Turn off **Use Entity ESP visible colors** for independent visible palettes and Custom RGB controls. **Occluded models** has its own player, hostile and non-hostile colors and opacity. Turning off **Occluded model pass** retains only fragments that pass the normal scene depth test. Visible and occluded opacity can each be zero independently.

Material strength and animation speed tune the shading; animation speed zero freezes the material's animation. Wire width appears for Wireframe. Mode-specific hidden settings keep their saved values. All settings persist in config files and profiles.

The capture hook runs after vanilla poses a living model, and replays that model into a separate position/normal consumer. Normal textures, equipment, held items, accessories and name tags keep their existing render paths. This iteration changes the base model and skin layers, not equipment feature models. Occlusion uses the final native scene depth, including other entities and equipment as well as terrain. Only models submitted by Minecraft's loaded, in-view entity renderer can be highlighted.

Capture is bounded to 128 targets, 32,768 vertices per model and 262,144 vertices per frame. Meshes expire every world-render frame; disconnect, disabling and reload clear queued geometry. The optional renderer does not write scene depth. Shader failure keeps ordinary Minecraft entities visible and logs once until resource reload; GPU buffers/programs are released at reload and shutdown. Third-party replacement renderers and resource-pack model changes require their own compatibility checks.

Run the reproducible isolated acceptance with Java 21:

```powershell
.\gradlew.bat build moduleCatalog
.\gradlew.bat runChamsSmoke
```

The development fixture uses its own `run-chams-smoke` world and an offline test player. Server commands establish mobs, storage and an occluding wall; a named Scout player is an explicit client-local model fixture. It does not touch the installed Prism game's worlds or preferences. Current results and unedited native framebuffer captures are recorded in [VALIDATION.md](VALIDATION.md) and [the screenshot index](screenshots/README.md).
