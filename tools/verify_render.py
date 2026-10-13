"""Optional pixel verification after gradlew runSmoke; requires Pillow."""
from pathlib import Path
import sys
from PIL import Image

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / 'run' / 'screenshots'
aurora = Image.open(root / 'analyzer-world-aurora.png').convert('RGB')
frost = Image.open(root / 'analyzer-world-frost.png').convert('RGB')
assert aurora.size == frost.size == (1600, 1000), 'Expected initial smoke window dimensions'

def chest_pixels(image):
    # The isolated test chest is behind an opaque wall. The visible outline
    # must disappear under depth testing even though the target stays loaded.
    # Include the gold overlay's fill as well as its bright edge. Animated color
    # grading shifts its blue/green ratio around .70; .75 retains the gold mask
    # while the opaque/depth-tested comparison has no matching pixels.
    return sum(r > 115 and g > 85 and r > g * 1.08 and b < g * 0.75
               for r, g, b in image.crop((315, 387, 408, 447)).getdata())

visible, occluded = chest_pixels(aurora), chest_pixels(frost)
print(f'Chest overlay pixels: through walls={visible}; depth tested Fabulous={occluded}')
assert visible > 60, 'The visible chest outline was not captured'
assert occluded < visible * 0.25, 'Fabulous depth testing failed to occlude the chest'
assert Image.open(root / 'analyzer-world-resized.png').size == (1280, 800), 'Window resize was not captured'
print('Pixel-level occlusion and 1280x800 resize checks passed.')
