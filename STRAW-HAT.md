# Straw Hat — 0.16.0 beta

**Cosmetics → Straw Hat** puts a wide conical straw hat on the player's head in third person. It follows the posed head, so it turns, nods and crouches with the model. It is drawn only on your screen; other players don't see it. Straw Hat starts disabled.

[All Straw Hat settings](MODULES.md#module-straw-hat) · [Other cosmetics](MODULES.md#cosmetics)

| Hat style | Look |
| --- | --- |
| Straw | Woven straw texture with a dark binding at the brim and a chin strap, lit like the player model |
| Lacquered | The same weave, tinted a deep red-brown lacquer |
| Glass | Tinted glass in the chosen color: brighter at the tip, clearer toward the brim, with a solid rim |
| Rainbow glass | Glass that cycles through the rainbow |

- **Wearers:** Self, other Players, or Both.
- **Brim size**, **Cone height**, **Extra height** and **Tilt** shape and seat the hat. Tilt pivots at the top of the head, so the brim dips forward or back without lifting off.
- **Chin strap** applies to Straw and Lacquered. **Glass opacity** applies to the glass styles. **Color mode** applies to Glass; Custom shows red/green/blue.
- With a helmet on, the hat sits higher so the cone clears the helmet. **Hide while wearing a helmet** hides it instead.

## Design and assets

The cone, brim, inner surface and strap are generated in code (`StrawHatMesh`, 32 segments). The woven texture `textures/cosmetics/hats/straw_hat.png` is original and generated for Analyzer; no other client's model or texture is used. The hat rests on the corners of the head's outer layer so the head doesn't poke through the cone at any supported size.

The three ornate concepts in [HAT-DESIGNS.md](HAT-DESIGNS.md) remain review-only; Straw Hat is a separate wearable design the user requested.

## Verification

`StrawHatSelfTest` checks the mesh across the full size, height, lift and helmet ranges: 690,207 assertions covering head clearance, brim extent, quad counts with and without the strap, finite vertices, texture coordinates, outward unit normals, the texture resource and the module's defaults. It runs in Gradle `check`. The native `runBetaSmoke` fixture confirms that both Straw and Glass draw on the posed third-person player, and it saves a capture of each. See [VALIDATION.md](VALIDATION.md).
