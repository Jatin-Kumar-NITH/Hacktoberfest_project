"""Draws the app icons (the gold "power bolt" Z on black) used by the web manifest and the Android app.

The same mark lives in static/icons/logo.svg for the in-app logo. Keep BOLT in sync with it.
Run once after changing the design:  python tools/build_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "static" / "icons"
ANDROID_RES = ROOT / "android" / "app" / "src" / "main" / "res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
BG, GOLD = "#000000", "#ffd60a"

# The bolt Z as a filled outline in a 100x100 box (a 10-wide stroke along
# 22,21 > 78,21 > 45,50 > 60,50 > 26,79 > 78,79 with mitred corners).
BOLT = [
    (22, 16), (91.26, 16), (58.26, 45), (73.57, 45), (39.57, 74), (78, 74),
    (78, 84), (12.43, 84), (46.43, 55), (31.74, 55), (64.74, 26), (22, 26),
]


def tile(size, rounded, glyph_scale, circle=False):
    scale = 4  # draw big, then downsample for smooth edges
    s = size * scale
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if circle:
        d.ellipse([0, 0, s - 1, s - 1], fill=BG)
    elif rounded:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=BG)
    else:
        d.rectangle([0, 0, s, s], fill=BG)
    k = s * glyph_scale / 100
    off = (s - 100 * k) / 2
    d.polygon([(off + x * k, off + y * k) for x, y in BOLT], fill=GOLD)
    return img.resize((size, size), Image.LANCZOS)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for size in (192, 512):
        tile(size, True, 0.82).save(OUT / f"icon-{size}.png")
        # Maskable: full bleed, bolt kept inside the 80% safe zone.
        tile(size, False, 0.62).save(OUT / f"maskable-{size}.png")
    tile(180, False, 0.78).save(OUT / "apple-touch-icon.png")
    tile(32, True, 0.9).save(OUT / "favicon-32.png")
    for name, size in DENSITIES.items():
        folder = ANDROID_RES / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        tile(size, True, 0.82).save(folder / "ic_launcher.png")
        tile(size, False, 0.72, circle=True).save(folder / "ic_launcher_round.png")
    print("wrote", sorted(p.name for p in OUT.iterdir()), "and Android launcher icons")


if __name__ == "__main__":
    main()
