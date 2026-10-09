"""Check actual visible stage rows in the 0.5.0 native console capture (Pillow)."""
from pathlib import Path
import sys
from PIL import Image

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / 'run/screenshots'
image = Image.open(root / 'analyzer-automation-expedition-plan.png').convert('RGB')
assert image.size == (1920, 1080), 'Expected the Survival console fixture framebuffer'
# The six queue cards occupy this known 1920x1080 fixture region. Their actual
# labels must reach the framebuffer; counting model rows alone missed a doubly
# transformed scissor that clipped every stage while the rest of the menu drew.
for row in range(6):
    top = 318 + row * 69
    pixels = image.crop((970, top, 1680, top + 57)).getdata()
    glyphs = sum(r > 135 and g > 145 and b > 145 for r, g, b in pixels)
    assert glyphs > 100, f'Queue row {row + 1} is not visibly rendered: {glyphs} text pixels'
    print(f'Queue row {row + 1}: {glyphs} visible text pixels')
print('All six actual Automation queue rows are visible; scissor clipping check passed.')
