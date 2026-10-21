"""Draws the app icons (the black "Z" tile) used by the web manifest and the Android app.

Run once after changing the design:  python tools/build_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "static" / "icons"
ANDROID_RES = ROOT / "android" / "app" / "src" / "main" / "res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
INK, CARD = "#1d1d1f", "#ffffff"
FONT = "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf"


def tile(size, rounded, glyph_scale, circle=False):
    scale = 4  # draw big, then downsample for smooth edges
    s = size * scale
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if circle:
        d.ellipse([0, 0, s - 1, s - 1], fill=INK)
    elif rounded:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=INK)
    else:
        d.rectangle([0, 0, s, s], fill=INK)
    font = ImageFont.truetype(FONT, int(s * glyph_scale))
    d.text((s / 2, s / 2), "Z", font=font, fill=CARD, anchor="mm")
    return img.resize((size, size), Image.LANCZOS)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for size in (192, 512):
        tile(size, True, 0.56).save(OUT / f"icon-{size}.png")
        # Maskable: full bleed, letter kept inside the 80% safe zone.
        tile(size, False, 0.42).save(OUT / f"maskable-{size}.png")
    tile(180, False, 0.56).save(OUT / "apple-touch-icon.png")
    tile(32, True, 0.6).save(OUT / "favicon-32.png")
    for name, size in DENSITIES.items():
        folder = ANDROID_RES / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        tile(size, True, 0.56).save(folder / "ic_launcher.png")
        tile(size, False, 0.5, circle=True).save(folder / "ic_launcher_round.png")
    print("wrote", sorted(p.name for p in OUT.iterdir()), "and Android launcher icons")


if __name__ == "__main__":
    main()
