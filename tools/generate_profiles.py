"""Regenerate starter snapshots from the exported, typed module catalogue."""
from copy import deepcopy
import json
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
catalogue = json.loads((repo / "MODULES.json").read_text(encoding="utf-8"))
definitions = {m["id"]: m for m in catalogue}
base = {"schemaVersion": 1, "modules": {
    m["id"]: {"enabled": m["defaultEnabled"], "keybind": -1,
              "settings": {s["id"]: s["default"] for s in m["settings"]}}
    for m in catalogue}}

def change(document, module_id, enabled=None, **settings):
    entry = document["modules"][module_id]
    if enabled is not None:
        entry["enabled"] = enabled
    registered = {s["id"]: s for s in definitions[module_id]["settings"]}
    for name, value in settings.items():
        setting = registered[name]
        if setting["kind"] == "CHOICE":
            assert value in setting["choices"], (module_id, name, value)
        elif setting["kind"] == "BOOLEAN":
            assert isinstance(value, bool), (module_id, name, value)
        else:
            assert setting["min"] <= value <= setting["max"], (module_id, name, value)
        entry["settings"][name] = value

def visuals(document):
    for module_id in ("sky", "shader", "hit-particles", "jump-circles", "trails",
                      "damage-numbers", "hit-markers", "explosion-effects", "totem-pops",
                      "landing-impact", "target-esp", "armor", "speed-hud", "compass"):
        change(document, module_id, True)
    change(document, "entity-esp", True, style="Screen", passives=True)
    change(document, "shader", True, preset="Bloom", strength=0.55)

cinematic = deepcopy(base)
visuals(cinematic)
change(cinematic, "place-animation", True, duration=1.0, style="Bloom")
change(cinematic, "break-animation", True, duration=1.0, debris=28)
change(cinematic, "sky", True, preset="Aurora", intensity=0.85)
change(cinematic, "menu", layout="Workspace", material="Glass", accent="Mint", motion=1.0)
change(cinematic, "hud", theme="Glass", **{"max-modules": 8})

classic = deepcopy(base)
change(classic, "menu", layout="Classic", material="Solid", accent="Gold", motion=0.5)
change(classic, "hud", theme="Classic")
change(classic, "entity-esp", True, style="Corners")
change(classic, "armor", True)
change(classic, "ping", True)
change(classic, "particles", False)

neon = deepcopy(base)
visuals(neon)
change(neon, "menu", layout="Compact", material="Glass", accent="Violet", motion=1.0)
change(neon, "hud", theme="Neon", **{"max-modules": 8})
change(neon, "sky", True, preset="Nebula")
change(neon, "shader", True, preset="Frost", strength=0.4)

hvh = deepcopy(base)
change(hvh, "menu", layout="Classic", material="Glass", accent="Violet")
change(hvh, "hud", theme="Neon", **{"max-modules": 10})
for module_id in ("kill-aura", "crystal-aura", "surround", "auto-totem", "auto-armor",
                  "auto-weapon", "keep-sprint", "entity-esp", "target-esp", "target-hud",
                  "armor", "ping", "potions", "item-counter", "hit-particles",
                  "explosion-effects", "totem-pops", "hit-markers"):
    change(hvh, module_id, True)
change(hvh, "entity-esp", style="Screen")

studio = deepcopy(cinematic)
for module_id in ("air-effects", "block-effects", "damage-effects", "hit-sounds", "vitals",
                  "item-physics", "hand-tweaks", "crystal-chams", "cape", "pets"):
    change(studio, module_id, True)
change(studio, "air-effects", density=36, style="Stardust")
change(studio, "block-effects", **{"on-place": False})
change(studio, "cape", style="Gradient", hem="Pointed")
change(studio, "pets", pet="Rune Orb", position="Shoulder")
profiles = {"cinematic": cinematic, "classic": classic, "neon": neon, "studio": studio, "hvh": hvh}
directory = repo / "src/main/resources/assets/analyzer/profiles"
for name, document in profiles.items():
    change(document, "hud", scale=0.6, **{"show-disabled": False})
    assert len(document["modules"]) == len(catalogue)
    if name != "hvh":
        assert all(not document["modules"][m["id"]]["enabled"] for m in catalogue
                   if m["category"] in ("Combat", "HVH", "Movement", "Player", "World")
                   and m["id"] != "sky")
    (directory / f"{name}.json").write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
print(f"Generated five snapshots with {len(catalogue)} typed module states each at 60% HUD scale.")
