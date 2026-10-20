# Original ornate hat designs — selection pending

The user approved integrating the four wing appearances and asked for richer hat designs instead of the basic shortlist hats. Two supplied Temple photographs are visual direction for an elevated, luminous halo and a detailed velvet crown. The new geometry in `design/hats-review` is original procedural concept work; no Temple or Lunar models, textures, jewelry patterns, shaders or art are copied.

These three hats are **review-only**. They are absent from wearable rendering, runtime resources and the installed cosmetic choices until the user selects a design. The earlier downloaded eight-candidate board and its original model/license bytes remain unchanged.

| Draft | Geometry and materials | Preview motion |
| --- | --- | --- |
| Sunforged halo | 24 individually beveled gold segments, inset angular runes, metal stud mounts, beadwork, a thin luminous inner rim | Slow turn and slight float; 16 small drifting sparks |
| Sovereign crown | Woven velvet dome, paired gold cross-arches, separate pearls, faceted colored jewels in pronged bezels, open lattice band, silver piping and small orb finial | Restrained occasional jewel glints |
| Astral diadem | Open rose-gold filigree, seven double-ended faceted crystals, dark silver band, fine partial star arc | Subtle crystal light variation and arc sway |

The shared neutral block mannequin uses an actual 8×8×8 Minecraft-pixel head, with its top at Y=8. Crown and diadem bases begin above that plane; the tilted halo is lifted above the scalp. These are browser concept placements, not proof of attachment during native walking, swimming, sneaking, armor use or elytra flight.

The preview uses actual Three.js 0.160.1 geometry and physically lit materials. Emissive rings and small meshes are real geometry; there is no bloom postprocess, pasted reference photograph or claim of native game effects. The small weave and neutral studio environment are deterministic, locally authored canvas textures. No third-party art files or models are loaded. Only the pinned Three.js module is requested from the approved public CDN; it is a browser review dependency and is not added to Minecraft.

`hats-geometry.js` contains the modular geometry, independent fit coordinates, deterministic animation and finite vertex/mesh caps. `hats-review-template.html` contains the preview controls. Run `python design/hats-review/build_review.py` to assemble `ornate-hat-designs.html`. The final fragment is below 1 MB and supports drag rotation, static geometry thumbnails, effect animation, a wearer toggle and saved review selections. A CDN/WebGL failure leaves the review names and choices available with an explicit notice. Reduced-motion preferences start effects paused.

Selecting a checkbox records a review preference locally; it does not equip or install a hat. Native integration, final effect settings, equipment clipping and production geometry budgets follow selection.

Download `Analyzer-Hat-Designs-0.15.0.zip` from the [current release](https://github.com/XonistReal/Analyzer-Public/releases/latest), extract it and open `index.html` in a browser. The review needs internet access for the pinned Three.js module. Geometry/fit validation samples 600 animation frames; a completed native hat-render acceptance or full browser interaction matrix is not claimed.
