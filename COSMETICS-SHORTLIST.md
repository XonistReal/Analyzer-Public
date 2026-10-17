# Cosmetics shortlist — review before integration

The user requested free commercial-use 3D cosmetics to inspect before choosing which enter Analyzer. Eight looks are downloaded and previewable in `design/cosmetics-review/index.html`; the portable copy is `Analyzer-Cosmetics-Review` on the Desktop, with `Analyzer-Cosmetics-Review.zip` beside it. Nothing in this review adds a cosmetic to the game, JAR, Prism mods, or player inventory.

**Fit revision 2:** the current preview and Desktop folder raise all four wing looks three Minecraft pixels toward the shoulders. The top hat's brim now rests on head-top Y=8 instead of penetrating it by half a pixel. `Analyzer-Cosmetics-Review-Fit-2.zip` contains these corrected placements; the original release archive remains unchanged. This review adjustment does not change the installed Minecraft client.

| Candidate | Creator | License | Existing articulation |
| --- | --- | --- | --- |
| Angel wings | Paul Fulham / pau101 | MIT | Existing Minecraft part/pivot rig |
| Dragon wings | Paul Fulham / pau101 | MIT | Same existing rig; different original texture |
| Bat wings | Paul Fulham / pau101 | MIT | Same existing rig; different original texture |
| Parrot wings | Paul Fulham / pau101 | MIT | Same existing rig; different original texture |
| Low-poly gold crown | BullHornGames | CC0 | Rigid head attachment |
| Robin Hood hat | DefecateRainbows | CC0 | Rigid head attachment |
| Classic top hat | FacadeGaikan | CC0 | Rigid head attachment |
| Camping backpack | Saraskau | CC0 | Rigid back attachment |

The wing source is [Wings at pinned commit `1749298`](https://github.com/pau101/Wings/tree/174929878d09490b012dbfa4e8d23d56f02b5add). Its [MIT license](https://github.com/pau101/Wings/blob/174929878d09490b012dbfa4e8d23d56f02b5add/LICENSE.md) permits commercial reuse with its copyright and permission notice retained. All eight original paired wing/feather pivots and their parent hierarchy are preserved: 17 parts including the root, 396 quads / 792 triangles before transparent texels are discarded. The review plays the supplied steady lift/flap formula. This is a downloaded rigid-part Minecraft rig, not a weighted FBX/glTF skeleton or imported keyframe clip. No new rig was authored. Four wing looks share one mesh.

The original creator pages list CC0 for the [crown](https://opengameart.org/content/low-poly-crown), [Robin Hood hat](https://opengameart.org/content/robin-hood-hat), [top hat](https://opengameart.org/content/hats), and [backpack](https://opengameart.org/content/low-poly-camping-assets). The review retains the full CC0 text, creator/page provenance, and original source bytes. Only the top-hat object is previewed from its original small blend; other source objects remain archival. The crown source has no material, so its gold preview color is explicitly a review choice.

The view renders geometry from these source files on an original neutral block mannequin. Coordinate conversion, triangulation, material copying, and cosmetic fitting are recorded alongside the mesh. Controls inspect each model, rotate the view, toggle the mannequin, play the existing wing animation, and save review picks locally. A CDN/WebGL failure leaves all eight choice controls and their original source links usable, with an explicit preview-unavailable notice. The normal renderer requires Three.js 0.160.1 from the documented public CDN; models and textures are embedded, with no cosmetic service or model fetch.

`design/cosmetics-review/manifest.json` records sources, authors, original/derived paths, license identifiers, byte counts and SHA-256 values. `SHA256SUMS.txt` also includes the manifest. The Desktop folder, archive, inline fragment and tracked review folder are checked against these bytes. The package excludes discovery packs, tool executables/caches, nested repositories, accounts and runtime mod files.

Future integration waits for the user's selected models. Rendering, attachment offsets, clipping against equipment/elytra, animation pacing and any visibility/sharing design remain implementation work after that choice; this shortlist is not native in-game acceptance.
