# Custom skyboxes

**World → Skybox** retains the existing `sky` module/category and all saved **Aurora / Nebula / Ember** presets. The default **Sky source → Procedural** keeps that appearance. Custom sources are local presentation and send no sky assets to a server or other players.

Choose a source:

| Source | What it renders |
| --- | --- |
| Procedural | Analyzer's existing original animated Aurora, Nebula and Ember shaders. |
| Custom | One local PNG/JPEG spherical panorama or a folder containing six PNG/JPEG cube faces. |
| Minecraft panorama | The six menu panorama images from the currently loaded Minecraft resource manager, including resource-pack replacements. This is the menu's scene, rather than an ordinary Overworld sky. |
| Minecraft End | Minecraft's actual `textures/environment/end_sky.png` on all six faces, including resource-pack replacements. |

For **Custom**, click **Browse custom skyboxes…**. The shader-styled shelf opens this instance's actual `minecraft/skyboxes` folder. It supports subfolders, search, Refresh and Open folder. Left-click selects an image or cube folder; **Use selected** saves its actual absolute path. Right-click a folder or press Enter to open it. Backspace goes up and Root returns to the library. Dropping one supported image or cube folder also works and may explicitly select a path outside the library. Shelf traversal never follows child symbolic links or resolved paths outside the library.

The selected path is saved in configs/profiles, but its external images are not embedded in the mod or portable ZIP. When moving PCs, copy your skyboxes directory and select its new local path. A missing or invalid asset leaves the native sky usable; an on-screen notice and the shelf's status explain the problem. **Refresh** retries an edited asset without restarting Minecraft. Resource reload also retries/reloads it.

## Panoramic images

Use a **2:1 equirectangular** PNG or JPEG, such as 4096 × 2048. The top of the image is the zenith, the middle is the horizon and the bottom is the nadir. Its left and right edges should join. Ordinary square photos, cube atlases and flat screenshots are rejected with an aspect-ratio explanation instead of being silently stretched around the whole sky. Convert those to a spherical panorama or six faces first.

## Six-face folders

Create a folder containing exactly one supported image for each name; extensions may differ between faces:

```text
skyboxes/
  My sky/
    px.png
    nx.png
    py.png
    ny.png
    pz.png
    nz.png
```

All six must be matching square images, at most 2048 × 2048. Names are lowercase. `px` is +X/east, `nx` is −X/west, `py` is +Y/up, `ny` is −Y/down, `pz` is +Z/south and `nz` is −Z/north. Side images have +Y at their top; the upper face has −Z at its top and the lower face has +Z at its top. This is the renderer's native cube convention. Use the saved yaw control to rotate the whole cube.

For a sky exported from a Source/CS-style tool, convert its faces to PNG/JPEG and rename/orient them to those axes. Different exporters use different `rt/lf/up/dn/ft/bk` conventions, so those labels are not guessed. Raw VTF/VMT/VPK files and packaged OptiFine/FabricSkyBoxes formats are not imported directly. No Valve, Lunar or third-party sky artwork is distributed by Analyzer; choose assets you have permission to use.

## Controls and boundaries

- **Intensity** is the existing overall blend/opacity. At 1, custom image colors replace the native sky background; lower values blend them with it.
- **Custom brightness** adjusts image exposure independently of opacity. **Custom yaw rotation** rotates the sky without rotating the player's camera.
- **Visible when → Always / Night** preserves the existing visibility choice. **Custom sky fades in rain** is independently adjustable.
- Custom sources render in normal sky dimensions. **Custom sky in the End** is opt-in and defaults off. Minecraft omits its entire sky pass while the End dragon's thick-fog bossbar is active; custom skies respect that native condition and become visible outside the fight area. The Nether has no native sky pass and is intentionally left unchanged. Submersion temporarily skips the effect rather than repeatedly reloading its textures.
- Native terrain, sun/moon, clouds, weather and gameplay remain native. This changes sky imagery rather than world lighting or the Atmosphere module. External shader packs/replacement sky renderers may replace this native pass and are not claimed compatible.

## Implementation and resource limits

Original GLSL reconstructs camera direction from the actual native projection and camera rotation and samples a panoramic texture or cube before terrain. It does not change camera, world, dimension, matrix or framebuffer state. All touched GL program/VAO, active texture and unit-zero bindings, unpack/PBO state, blend, depth, scissor/culling and write masks are restored.

Image work runs on one daemon worker with at most one queued replacement request. Only the current request can be uploaded; stale completed loads are discarded. File decoding checks PNG/JPEG reader headers before allocating pixels, caps each image at 32 MiB, 8192 pixels per edge and 32 megapixels, and limits cube faces to 2048 pixels with a 96 MiB combined encoded budget. The GPU's actual 2D/cube texture size limit is checked before upload. The shelf lists at most 4096 supported entries and examines at most 32768 directory entries without decoding them.

The owning texture/program/VAO are released on source/path change, module disable, unsupported dimension/world change, disconnect, resource reload and client shutdown. A load/shader/allocation failure leaves the ordinary native sky, reports its reason, and waits for Refresh or a deliberate source change before retrying. No files are downloaded automatically and no custom libraries are rewritten.

The implementation uses Minecraft/Fabric APIs as documented by the [Yarn 1.21.4 SkyRendering API](https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.8/net/minecraft/client/render/SkyRendering.html). OptiFine's [published custom-sky format](https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/sky.properties) was consulted to distinguish packaged formats from this deliberately small local importer; no upstream implementation or external runtime is bundled.

## Validation

`customSkyboxCheck` exercises real PNG/JPEG decode and pixel order, aspect/format errors, six-face completeness/dimensions/duplicate names, shelf filtering/root boundaries and config persistence of every new source plus all three old procedural presets. Alias text validation remains unchanged. Windows symlink cases explicitly report a skip when OS permission prevents creating the fixture.

`runSkyboxSmoke` is development-only, uses `run-skybox-smoke`, and creates its own isolated integrated world and original colored/grid test textures. It exercises the actual native sky shader and real shelf clicks, four source types, bad-path fallback, resource reload, disable/re-enable, selected GL-state restoration, actual End opt-in outside dragon thick fog, an actual Nether transition with no texture work, and disconnect cleanup. It records native framebuffer PNGs and checks the completed Overworld/End image pixels for the original blue panorama, rather than treating draw counters as visible output. Run completion is reported as `ANALYZER_SKYBOX_COMPLETE`; runtime acceptance and captured images are separate from parser checks and do not demonstrate replacement-renderer compatibility.
