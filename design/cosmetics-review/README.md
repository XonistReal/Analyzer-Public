# Analyzer cosmetics review

Open **index.html** to inspect the eight review candidates. The renderer loads Three.js 0.160.1 from an approved public CDN, so the 3D preview needs an internet connection. If the CDN or WebGL is unavailable, all eight choices and their original source links remain usable with an explicit unavailable notice. Model geometry, textures and selection data are embedded locally; it does not fetch models or contact a cosmetic service. Picks stay in the review view and do not install anything.

The selectable looks are Angel, Dragon, Bat and Parrot wings; a crown; Robin Hood hat; top hat; and camping backpack. All geometry comes from the downloaded source files, with documented coordinate conversion and preview fitting on a neutral block mannequin. The crown's gold preview material is disclosed: its source mesh is untextured. Four wing looks share one supplied articulated model; they are texture variants, not four different meshes.

**Fit revision 2:** all four wing looks mount three Minecraft pixels higher on the upper torso. The top hat's lowest brim surface rests exactly on the head's top at Y=8, lifting it half a pixel from the previous placement. These are attachment transforms; the downloaded geometry, rig, textures and animation are unchanged. The updated portable archive is `Analyzer-Cosmetics-Review-Fit-2.zip`.

## Existing wing rig

Paul Fulham (pau101), **Wings**, MIT, pinned commit `174929878d09490b012dbfa4e8d23d56f02b5add`.

The supplied Minecraft part/pivot rig already has a root, four parented wing bones per side and four feather pivots per side. Its original procedural idle/lift/glide/fall/landing definitions are retained. The review plays the original steady lift/flap formula. This is an existing rigid-part Minecraft rig, not a weighted/skinned FBX/glTF skeleton or imported keyframe clip. No rig was authored for the review.

Original Java geometry, extrusion, animation and utility source bytes are in `sources/wings`; original 64×64 textures and the complete MIT notice are in `models/wings`. The model has 396 quads / 792 triangles before transparent texels are discarded. Retain the MIT copyright and permission notice with copies/substantial portions.

## Other accessories

The crown is by BullHornGames, Robin Hood hat by DefecateRainbows, top hat by FacadeGaikan and backpack by Saraskau. The creator pages identify these as CC0; the original downloads or selected original source files, page snapshots, converted previews and full CC0 text are retained. All four are rigid attachments without an imported rig; the user's rig requirement applies to the wings.

The original small top-hat blend also contains other hats. Those source objects are retained solely to preserve its original downloaded bytes; only the top hat is a review candidate. Large discovery packs, conversion programs, repositories and caches are excluded.

## Contents and integrity

- `index.html`: standalone review view.
- `cosmetics-shortlist.html`: inline fragment; `cosmetics-shortlist-template.html`: editable presentation template.
- `models/wings/avian-review.json`: original constructor geometry, pivots and animation formula metadata.
- `models/accessories/gallery-meshes.json`: four converted review meshes with embedded original texture when applicable.
- `sources/`: downloaded original source bytes, never runtime Analyzer code/resources.
- `provenance/`: inert `.html.txt` snapshots of original creator/license pages.
- `manifest.json`: candidate authors, sources, licenses, transforms and SHA-256 for every other file.
- `SHA256SUMS.txt`: portable integrity list, including the manifest.

Every path in the package manifest is relative to this folder. The review preserves source art. It does not add cosmetics to the Analyzer JAR, mod metadata, Prism instance, server or player inventory. These are candidates awaiting the user's choices.
