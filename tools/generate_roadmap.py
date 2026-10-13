"""Generate an honest Temple.nz comparison from the actual Analyzer registry.

Run after `gradlew moduleCatalog`; no network calls or code changes are made.
"""
from __future__ import annotations
import argparse
from collections import Counter
import json
from pathlib import Path

# A reference area is counted once, even when several Analyzer widgets support it.
# 'Counterpart' means a functional core counterpart, never complete feature parity.
MATCHES: dict[str, tuple[str, str, str]] = {
    "Glass": ("menu", "Partial", "Sampled blurred backdrop and translucent panels/rims; no refractive glass material shader."),
    "HUD": ("hud", "Counterpart", "Draggable/scalable editor and 17 client/HUD controls, counted here as one reference area."),
    "Menu": ("menu", "Partial", "Workspace, draggable Classic columns and Compact layouts; no replacement vanilla menu theme."),
    "Fullbright": ("fullbright", "Counterpart", "Local lightmap boost with restoration."),
    "Skybox": ("sky", "Partial", "World-sky Aurora/Nebula/Ember and stars; no imported panoramas or all reference sky styles."),
    "Crosshair": ("crosshair", "Partial", "Arm/gap/width controls; no custom image or all animated shapes."),
    "ESP": ("entity-esp", "Partial", "3D boxes/corners and projected screen frames; no model-mask silhouettes/chams."),
    "Tracers": ("tracers", "Counterpart", "Loaded-target filters, range, thickness and visibility."),
    "Nametags": ("nametags", "Partial", "Loaded entity names, distance and health; no equipment cards."),
    "Storage ESP": ("storage-esp", "Partial", "Loaded common containers including chests, barrels, shulkers and furnaces; no exhaustive storage/spawner support."),
    "Item ESP": ("item-esp", "Partial", "Loaded dropped-item rings, beams and labels; no item allow-list selector."),
    "Block Highlight": ("block-highlight", "Partial", "Pulsing crosshair block outline; no detailed info card."),
    "Hole ESP": ("hole-esp", "Counterpart", "Bounded scan of loaded resistant single-block holes."),
    "Hit Particles": ("hit-particles", "Partial", "Actual hurt Sparks/Shards/Gems/Arc with filters, size/spread/speed/gravity/colors; no bouncing material system."),
    "Target ESP": ("target-esp", "Partial", "Orbit/Box/Corners with size/linger/depth/colors; no full ghost-orb/halo style set."),
    "Jump Circles": ("jump-circles", "Partial", "Local Ripple/Rings/Crown/Tile on Jump/Land/Both; no remote-player selector or block mesh lift."),
    "Trails": ("trails", "Partial", "Local Ribbon/Beads/Sparks with height/width/size/colors; no player afterimages or full elemental styles."),
    "Death Effects": ("death-effects", "Partial", "Loaded entity death bursts; no coins/souls/ghost models."),
    "Block Effects": ("block-effects", "Partial", "Actual local placements/breaks, map colors, crumble/shatter/frame geometry; no remote-player event coverage."),
    "Place Animation": ("place-animation", "Partial", "Animated colored ghost overlay; the vanilla block mesh still appears normally."),
    "Place Preview": ("place-preview", "Counterpart", "Adjacent/replaceable position preview for a held block."),
    "Player Aura": ("player-aura", "Partial", "Orbit/Gyroscope/Rune geometry with first-person, radius/speed/colors; no wings, ears, horns or wearable mesh collection."),
    "Damage Effects": ("damage-effects,health-pulse", "Partial", "Confirmed hurt bursts, entity filters and low-health warning; no damage model deformation."),
    "Air Effects": ("air-effects", "Partial", "Bounded world ambient field with seven shapes, density/radius/wind/network/color controls; no full reference shape catalogue."),
    "Contrails": ("contrails", "Partial", "Paired fading paths from actual motion, glide restriction and speed filters; no aerodynamic fluid simulation."),
    "Storm Effects": ("storm-effects", "Partial", "Actual loaded lightning triggers branching bolt/column/spear, sparks/ripple/flash; no weather replacement."),
    "Vitals": ("vitals", "Partial", "Real health/fire/underwater-air warning and optional heartbeat; no wearable vitals mesh."),
    "Hit Sounds": ("hit-sounds", "Partial", "Seven built-in vanilla sound cues on confirmed attacked-target hurt, pitch/volume/combo/kill controls; no custom file importer."),
    "Item Physics": ("item-physics", "Partial", "Actual dropped-item tumble/ground-rest/water-rock render transforms; no entity physics changes."),
    "Hand Tweaks": ("hand-tweaks", "Partial", "First-person transforms, item stretch/size/rotations, seven swing styles and equip/eat/hide controls; no full reference pose set."),
    "Crystal Chams": ("crystal-chams", "Partial", "Actual tinted vanilla model or original Gem/Prism meshes, size/spin/bounce/base/rings; no through-wall crystal material."),
    "Cape": ("cape", "Partial", "Original segmented moving cloth with three styles/three hems, wearer/color/flow controls; no image/GIF importer."),
    "Pets": ("pets", "Partial", "Original Rune Orb, Ghost and Dragon meshes in third person; no speech bubbles or Jellyfish."),
    "Emotes": ("emotes", "Partial", "Five local third-person model poses, loop/length/movement/strength controls; no server animation."),
    "Walk Style": ("walk-style", "Partial", "Five local player-model limb poses and strength/movement controls; no server animation."),
    "Spinbot": ("spinbot", "Partial", "Local player render-state yaw/head pitch only; camera/entity/server rotations remain unchanged."),
    "Footsteps": ("footsteps", "Partial", "Local Rings/Prints/Petals, spacing/size/max-prints/colors; no remote players or puddle materials."),
    "Projectile Trails": ("projectile-trails", "Partial", "Loaded Line/Comet/Spiral paths with projectile-type filters, range/width/colors; no impact burst style set."),
    "Pickup Streaks": ("pickup-streaks", "Partial", "Streaks on item pickup packets; no complete experience-orb effect replacement."),
    "Teleport Effects": ("teleport-effects", "Partial", "Large local position-change Rings/Vortex/Portal with linked ends; no complete remote-player pearl tracking."),
    "Damage Numbers": ("damage-numbers", "Counterpart", "Numbers from actual health decreases of loaded living entities."),
    "Hit Markers": ("hit-markers", "Partial", "Confirmed recent attacked-target hurt produces Cross/Corners/Dots with size/thickness/spin/colors; no world marker/kill style set."),
    "Totem Pops": ("totem-pops", "Partial", "Real totem-status rings/sparks/label; no persistent per-player pop counts or model replacement."),
    "Break Animation": ("break-animation", "Partial", "Actual mining progress, fading ghost and debris; no replacement block mesh fracture."),
    "Explosion Effects": ("explosion-effects", "Partial", "Actual explosion packet shockwaves/sparks, added to vanilla effects."),
    "Trajectories": ("trajectories", "Partial", "Held bow/trident/snowball/egg/pearl prediction against loaded blocks; no complete other-player future paths."),
    "Fly": ("fly", "Counterpart", "Client velocity flight with collisions; no server flight grant or bypass."),
    "Speed": ("speed", "Partial", "Standard/Conservative direct motion or ordinary sprint hops, with multiplier and accepted Target Strafe direction; no anticheat guarantee."),
    "Step": ("step", "Counterpart", "Temporary client step-height attribute with restoration."),
    "Elytra Boost": ("elytra-boost", "Partial", "Bounded impulse/climb/cap or actual rockets with optional takeoff; no autopilot."),
    "Fast Fall": ("fast-fall", "Counterpart", "Accelerates an existing fall; normal fall damage remains."),
    "Fast Ladder": ("fast-ladder", "Counterpart", "Upward velocity while already climbing."),
    "Glide": ("glide", "Counterpart", "Limits ordinary downward velocity outside fluids."),
    "Ground Strafe": ("ground-strafe", "Partial", "Momentum/Steady ground motion or normal sprint hops, following accepted orbit direction; no guaranteed constant server speed."),
    "High Jump": ("high-jump", "Counterpart", "Higher velocity at the beginning of an ordinary jump."),
    "Long Jump": ("long-jump", "Counterpart", "Directional impulse at the start of a jump."),
    "Parkour": ("parkour", "Counterpart", "Grounded automatic jumps before unsupported edges."),
    "Reverse Step": ("reverse-step", "Partial", "Downward impulse while a floor is nearby; no complete mode set."),
    "Safe Walk": ("safe-walk", "Counterpart", "Vanilla ledge clipping without holding sneak."),
    "Kill Aura": ("kill-aura", "Partial", "Single/Switch/Multi target modes, filters/priorities, real camera turn and cooldown/CPS timing; no silent rotations."),
    "Crystal Aura": ("crystal-aura", "Partial", "Smart/Own/All crystals, bounded place/break timing, exposure damage and conservative self/friend guards; no silent rotation or server outcome guarantee."),
    "Anchor Aura": ("anchor-aura", "Partial", "Normal place/charge/detonate with exposure damage, self/friend guards and optional actual crosshair; no silent rotation or server outcome guarantee."),
    "Surround": ("surround", "Counterpart", "Materials, shell shapes, occupied-block extension, centering, supports/floor and disable conditions under bounded placement."),
    "Auto Trap": ("auto-trap", "Partial", "Bounded sides/roof placement around a target; no advanced adaptive trap planner."),
    "Auto Web": ("auto-web", "Counterpart", "Supported reachable feet/head webs, selected enemies and motion conditions under a placement budget."),
    "Hole Filler": ("hole-filler", "Partial", "Loaded single/double holes, enemy relevance and self/friend preservation; no enemy escape prediction."),
    "Aim Assist": ("aim-assist", "Counterpart", "Smooth target turn while holding attack, with filters/FOV/range."),
    "Bow Aimbot": ("bow-aimbot", "Partial", "Bow/crossbow/trident lead and drop estimates with filters and camera controls; no full drag/collision simulation or silent shot redirection."),
    "Auto Clicker": ("auto-clicker", "Partial", "Left/Right/Both normal interactions, held input, steady/random/paired timings and bounded CPS; no continuous-use item repetition."),
    "Auto Weapon": ("auto-weapon", "Partial", "Hotbar material/damage estimate and owned-slot restoration; no full enchantment score."),
    "Criticals": ("criticals", "Partial", "Real jump/falling-phase normal attacks; no packet critical spoof."),
    "Shield Breaker": ("shield-breaker", "Counterpart", "Hotbar axe for a blocking reachable target, with slot ownership/restoration."),
    "Keep Sprint": ("keep-sprint", "Partial", "Normal sprint input while moving forward; no guaranteed full knockback on every hit."),
    "Target Strafe": ("target-strafe", "Partial", "Circle/Vanilla hop/Blind hop/Hug with WASD, flip/weave, normal pace and checked support; no camera-sharing/glance/pursuit or TP Aura integration."),
    "Trigger Bot": ("trigger-bot", "Counterpart", "Normal cooldown attacks on a selected crosshair entity."),
    "Auto Eat": ("auto-eat", "Counterpart", "Hunger-triggered hotbar food with temporary use/slot ownership."),
    "Anti AFK": ("anti-afk", "Partial", "Periodic ordinary turn/jump; no randomized macro-check avoidance claim."),
    "Auto Armor": ("auto-armor", "Partial", "Material-tier armor upgrades/empty slots through guarded quick moves; equipped elytra is preserved."),
    "Auto Fish": ("auto-fish", "Counterpart", "Normal cast, synchronized bite detection, reel and optional recast."),
    "Auto Gapple": ("auto-gapple", "Counterpart", "Health-triggered hotbar golden apple use."),
    "Auto Respawn": ("auto-respawn", "Counterpart", "Normal delayed respawn request from the death screen."),
    "Auto Tool": ("auto-tool", "Counterpart", "Fastest usable hotbar tool for a currently mined block."),
    "Auto Totem": ("auto-totem", "Counterpart", "Normal inventory/offhand swap when no totem is equipped."),
    "Auto Walk": ("auto-walk", "Counterpart", "Owned forward input released on pause/disable."),
    "Chest Stealer": ("chest-stealer", "Counterpart", "Bounded quick moves from an open container."),
    "Middle Click Friend": ("middle-click-friend", "Partial", "Persisted UUID friends and combat exclusion; no friend tint in all ESP/chams or chat command set."),
    "Notifier": ("notifier", "Partial", "Loaded player arrivals/departures; no full pop/pearl notification routing."),
    "Refill": ("refill", "Partial", "Restocks low nonempty hotbar stacks with larger matching stacks; no remembered empty-slot refill."),
    "Coord Snapper": ("coord-snapper", "Counterpart", "One-shot coordinates/dimension clipboard copy and optional Nether conversion."),
    "Player Detection": ("player-detection", "Counterpart", "Local loaded nonfriend player radius alerts."),
    "Durability Alert": ("durability-alert", "Counterpart", "Periodic low-durability equipment warning."),
    "Nuker": ("nuker", "Counterpart", "Reachable block mining with normal progress."),
    "Air Place": ("air-place", "Partial", "Normal in-range air-placement attempt; server acceptance is required."),
    "Auto Farm": ("auto-farm", "Partial", "Nearby mature crop harvest/replant with hotbar seeds; no full crop/route manager."),
    "Auto Tunnel": ("auto-tunnel", "Partial", "Two-high forward mining; no base finder, hazard avoidance or steering."),
    "Bed Breaker": ("bed-breaker", "Partial", "Reachable bed mining; no dragon-egg targeting."),
    "Flatten": ("flatten", "Partial", "Mine reachable blocks above standing level, preserving the floor."),
    "Scaffold": ("scaffold", "Partial", "Ordinary hotbar placement beneath the player; no god/speed-bridge mode set."),
    "Speed Mine": ("speed-mine", "Partial", "Removes client inter-break delay; server progress rules remain authoritative."),
    "Tower": ("tower", "Counterpart", "Normal jump and underfoot block placement while holding jump."),
    "Vein Miner": ("vein-miner", "Partial", "Bounded reachable connected same-type blocks; no unrestricted whole-vein break."),
    "No Block Interact": ("no-block-interact", "Partial", "Block placement on containers without opening them; no full held-item use redirect."),
}

PRIORITIES = {
    "No Slow": "Item-use slowdown hooks with exact input restoration; fluid/web modes require separate verification.",
    "Elytra Autopilot": "Coordinate route, pitch/altitude controller, collision checks, landing and explicit start/stop.",
    "Chams": "True entity model-mask rendering and outlines with correct world depth and resource reload.",
    "Crystal Chams": "Orb model and refractive materials, with dedicated bloom or silhouette rendering.",
    "Hand Chams": "First-person model/material pass with HUD and world separation.",
    "Realistic Water": "Separate water render integration: waves/refraction/reflection/depth/foam; not a grading preset.",
    "Waving Foliage": "Vertex animation and material integration with renderer/shader compatibility tests.",
    "Wet Ground": "World material/reflection pipeline rather than a dark screen tint.",
    "Freecam": "Independent camera entity/control and reliable body/camera restoration.",
    "Freelook": "Camera/body rotation separation for every perspective.",
    "No Bob": "Restore the prior bob setting when disabled; keep hurt-camera effects separate.",
    "Zoom": "Smooth scoped FOV with input bindings and prior-FOV restoration.",
    "Cover Planner": "Exposure/damage simulation, self-damage state, mining cost and escape-route scoring.",
    "Trap Early Warning": "Track nearby block changes, identify remaining exits and validate any escape action.",
    "Aurora": "Actual local player veil/crown/ribbon curtains; current Sky Aurora only maps to Skybox.",
    "Player Aura": "Wearable wings/halos/ornament meshes beyond current rotating rings.",
    "Ambience": "World fog/cloud/weather controls with restoration and a separate lighting pipeline.",
}

def generate(reference: list[dict], catalog: list[dict], version: str, date: str) -> str:
    by_id = {entry["id"]: entry for entry in catalog}
    reference_names = {entry["name"] for entry in reference}
    unknown = set(MATCHES) - reference_names
    if unknown:
        raise ValueError(f"Mapping names missing from reference catalogue: {sorted(unknown)}")
    for ids, _, _ in MATCHES.values():
        for module_id in ids.split(","):
            if module_id not in by_id:
                raise ValueError(f"Mapping points to absent Analyzer module: {module_id}")
    counts = Counter(MATCHES.get(entry["name"], ("", "Planned", ""))[1] for entry in reference)
    covered = counts["Counterpart"] + counts["Partial"]
    lines = ["# Analyzer roadmap and reference coverage", "", f"Reviewed {date} for Analyzer {version}, Minecraft Java 1.21.4.", "",
        f"The actual registry has **{len(catalog)} modules**. The [Temple.nz public catalogue](https://temple.nz/assets/data/modules.json?v=be4c7e7b) contains **{len(reference)} reference modules**. This comparison identifies **{covered} areas with a functional counterpart or narrower implementation**: **{counts['Counterpart']} scoped counterparts**, **{counts['Partial']} partial implementations**, and **{counts['Planned']} planned/unimplemented areas**. These figures are separate from feature parity and runtime test coverage.", "",
        "HUD Studio and its widgets count as one Temple HUD area. The same evidence is never expanded into seventeen reference features. A partial row states the missing scope; an implementation sharing a name does not imply the reference's styles, bypasses or server behavior.", "",
        "Evidence status: the matrix is based on the registered source definitions and code review. Selected runtime checks are recorded separately in [VALIDATION.md](VALIDATION.md); not every module or mode has been tested in game. Gameplay automation is opt-in. Server acceptance, normal reach/cooldowns, loaded-world data and renderer compatibility still constrain behavior.", "",
        "## Next implementation priorities", "",
        "1. **Combat/HvH correctness:** broaden real-world Crystal/Anchor/Trap/Web evidence for the implemented exposure damage, conservative self/friend guards, target scoring and timing. Improve multi-module slot/rotation coordination and server reconciliation before adding teleport combat.",
        "2. **Movement:** No Slow with precise input hooks and cleanup, then Elytra Autopilot with route/altitude/collision control. Add controlled movement tests for every mode; client motion alone cannot promise server acceptance.",
        "3. **Rendering:** true model Chams; independent player Aurora curtains and wearable aura meshes; world water/foliage/wet-ground shader integration. Keep overlay effects and mesh/material replacements explicitly distinct.",
        "4. **Camera/UI:** Freecam, Freelook, Zoom/No Bob; deepen glass scene sampling and HUD/widget polish, preserving camera/options on exit and resource reload.",
        "5. **Coverage depth:** empty-slot Refill, enchant-aware weapon/armor scoring, complete projectile drag/collision, richer damage/totem events and remote block effects. Expand current partial rows rather than increasing the catalogue with unused switches.", "",
        "## Every reference module", "",
        "**Counterpart:** the core behavior has an implemented, scoped counterpart. **Partial:** a narrower implemented behavior exists. **Planned:** no direct implementation is present. All rows are code-reviewed references unless VALIDATION records a specific runtime check.", "",
        "| Temple category | Temple module | Analyzer ID | Status | Implemented scope / next work |",
        "| --- | --- | --- | --- | --- |"]
    for entry in reference:
        name = entry["name"]
        if name in MATCHES:
            ids, status, note = MATCHES[name]
            if name in PRIORITIES:
                note += " Next: " + PRIORITIES[name]
        else:
            ids, status = "—", "Planned"
            note = PRIORITIES.get(name, "No direct implementation in this release; design and validation remain.")
        ids_text = ", ".join(f"`{value}`" for value in ids.split(",")) if ids != "—" else ids
        lines.append(f"| {entry['category']} | {name} | {ids_text} | {status} | {note.replace('|', '/') } |")
    lines += ["", "The exact Temple.nz site and visual observations are documented in [RESEARCH-VISUALS.md](RESEARCH-VISUALS.md). Source data: the official [module JSON](https://temple.nz/assets/data/modules.json?v=be4c7e7b) and Analyzer's generated [MODULES.json](MODULES.json). No Temple client code/assets are copied.", "",
        "Regenerate after changes: `./gradlew moduleCatalog`, then `python tools/generate_roadmap.py --reference <saved-temple-modules.json>`. The generator validates mapped Analyzer IDs against the exported registry and performs no network requests.", ""]
    return "\n".join(lines)

def main() -> None:
    repo = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, default=repo.parents[1] / "work/temple-modules.json")
    parser.add_argument("--catalog", type=Path, default=repo / "MODULES.json")
    parser.add_argument("--output", type=Path, default=repo / "ROADMAP.md")
    parser.add_argument("--date", default="2026-10-09")
    parser.add_argument("--version", default="0.5.0")
    args = parser.parse_args()
    text = generate(json.loads(args.reference.read_text(encoding="utf-8-sig")),
                    json.loads(args.catalog.read_text(encoding="utf-8-sig")), args.version, args.date)
    args.output.write_text(text, encoding="utf-8")
    print(f"Wrote {args.output}")

if __name__ == "__main__":
    main()
