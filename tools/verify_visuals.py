"""Pixel checks for the isolated 0.2.0 smoke arena (Python + Pillow)."""
from pathlib import Path
import sys
from PIL import Image, ImageChops, ImageStat

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "run/screenshots"

def capture(name):
    image = Image.open(root / f"analyzer-{name}.png").convert("RGB")
    assert image.size == (1280, 800), f"Wrong capture size: {name}"
    return image

aurora, off = capture("sky-aurora"), capture("sky-off")
# Avoid HUD and terrain. Animated clouds may move, so assert aurora-colored
# pixels as well as difference; cloud movement alone cannot pass this check.
sky = (280, 120, 1000, 490)

def green_lights(image):
    return sum(g > 90 and g > r * 1.7 and g > b * 1.15
               for r, g, b in image.crop(sky).getdata())

lit, baseline = green_lights(aurora), green_lights(off)
assert lit > 3000 and lit > baseline + 2500, f"Sky ribbons absent: {lit} / {baseline}"
sky_difference = sum(ImageStat.Stat(ImageChops.difference(aurora.crop(sky), off.crop(sky))).mean) / 3
assert sky_difference > 12, f"Sky effect too subtle: {sky_difference:.2f}"

# A fixed opaque wall occupies this region in both captures. Its pixels must
# remain stable while the sky changes, proving the sky is behind terrain.
wall = (95, 540, 400, 690)
wall_difference = max(ImageStat.Stat(ImageChops.difference(aurora.crop(wall), off.crop(wall))).mean)
assert wall_difference < 2, f"Sky leaked onto opaque terrain: {wall_difference:.2f}"
for preset in ("nebula", "ember"):
    alternate = capture(f"sky-{preset}")
    difference = sum(ImageStat.Stat(ImageChops.difference(aurora.crop(sky), alternate.crop(sky))).mean) / 3
    assert difference > 8, f"Indistinct sky preset: {preset} / {difference:.2f}"
for mode in ("fast", "fancy", "fabulous"):
    capture(f"screen-{mode}")
print(f"Sky pixels: aurora={lit}, off={baseline}; sky change={sky_difference:.2f}; opaque-wall change={wall_difference:.2f}")
print("Real sky, three distinct presets, terrain occlusion and graphics-mode capture checks passed.")
