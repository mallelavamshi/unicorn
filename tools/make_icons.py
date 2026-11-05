"""Builds Unicorn launcher icons from the line-drawn logo (branding/unicorn-logo-source.png).

Extracts the purple stroke from the left (circle) variant as an alpha mask, recolors it to the brand purple and writes
adaptive-icon foregrounds, legacy icons and a Play Store icon.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(sys.argv[1])
BRAND = (116, 48, 164)
src = Image.open(ROOT / "branding" / "unicorn-logo-source.png").convert("RGB")
w, h = src.size
left = src.crop((0, 0, w // 2, h))

# Alpha from "how far from white" on the green channel (purple stroke g~48, white bg g~255)
px = left.load()
lw, lh = left.size
mask = Image.new("L", left.size, 0)
mp = mask.load()
for y in range(lh):
    for x in range(lw):
        r, g, b = px[x, y]
        if b - g > 40:  # purple only, ignores the grey drop shadow
            mp[x, y] = max(0, min(255, int((255 - g) * 255 / (255 - 48))))
bbox = mask.getbbox()
glyph_mask = mask.crop(bbox)
glyph = Image.new("RGBA", glyph_mask.size, BRAND + (0,))
glyph.putalpha(glyph_mask)
print("glyph bbox", bbox)


def fit(img, box):
    scale = min(box / img.width, box / img.height)
    return img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)


def centered(canvas_size, img, bg=None):
    canvas = Image.new("RGBA", (canvas_size, canvas_size), bg or (0, 0, 0, 0))
    canvas.alpha_composite(img, ((canvas_size - img.width) // 2, (canvas_size - img.height) // 2))
    return canvas


res = ROOT / "app" / "src" / "main" / "res"
densities = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
for name, d in densities.items():
    out = res / f"mipmap-{name}"
    out.mkdir(parents=True, exist_ok=True)
    # Adaptive foreground: 108dp canvas, glyph inside the 66dp safe zone (kept at ~58dp for breathing room)
    size = round(108 * d)
    centered(size, fit(glyph, round(58 * d))).save(out / "ic_unicorn_foreground.png")
    # Legacy icon: 48dp white rounded square with the glyph
    size = round(48 * d)
    legacy = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(legacy).rounded_rectangle((0, 0, size - 1, size - 1), radius=round(11 * d), fill=(255, 255, 255, 255))
    legacy.alpha_composite(fit(glyph, round(32 * d)), ((size - fit(glyph, round(32 * d)).width) // 2,
                                                         (size - fit(glyph, round(32 * d)).height) // 2))
    legacy.save(out / "ic_unicorn.png")

store = Image.new("RGBA", (512, 512), (255, 255, 255, 255))
store.alpha_composite(fit(glyph, 340), ((512 - fit(glyph, 340).width) // 2, (512 - fit(glyph, 340).height) // 2))
store.convert("RGB").save(ROOT / "branding" / "play-store-icon-512.png")
glyph.save(ROOT / "branding" / "unicorn-glyph.png")
print("icons written")
