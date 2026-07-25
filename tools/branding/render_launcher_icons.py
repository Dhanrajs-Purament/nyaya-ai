#!/usr/bin/env python3
"""
Renders the Nyaya AI launcher icon to legacy density PNGs.

Android 8.0+ (the app's minSdk is 26) always uses the adaptive icon defined in
res/mipmap-anydpi-v26/ic_launcher.xml, which is built from the vector drawables
ic_launcher_background.xml / _foreground.xml / _monochrome.xml. Those vectors are
the source of truth for the brand mark.

The res/mipmap-*/ic_launcher.png files are the pre-Oreo fallback. They are
regenerated here from the same geometry so the APK never carries a stale logo,
and so anything that reads the raster icon (store tooling, some launchers'
caches, docs) shows the current brand.

Run after changing the vector icon:
    pip install pillow && python3 tools/branding/render_launcher_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parents[2] / "app/src/main/res"

# Densities Android expects for a legacy launcher icon, in px.
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Supersampling factor: draw large, downscale with antialiasing.
SS = 8

# Brand palette, matching the vector drawables.
INDIGO_DARK = (9, 23, 51)
INDIGO = (18, 58, 102)
TEAL = (12, 127, 124)
WHITE = (255, 255, 255)
AMBER = (247, 197, 68)

# The vectors use a 108x108 viewport; the legacy icon has no adaptive mask, so
# the art is scaled up to fill more of the canvas than the 72x72 safe zone.
VIEWPORT = 108.0
LEGACY_SCALE = 1.24


def _lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def _diagonal_gradient(size):
    """Indigo to teal along the top-left/bottom-right diagonal."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2.0 * (size - 1))
            if t <= 0.52:
                colour = _lerp(INDIGO_DARK, INDIGO, t / 0.52)
            else:
                colour = _lerp(INDIGO, TEAL, (t - 0.52) / 0.48)
            px[x, y] = colour
    return img


def _rounded_mask(size, radius_ratio=0.22):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)
    return mask


def render(size):
    canvas = size * SS
    img = _diagonal_gradient(canvas)
    draw = ImageDraw.Draw(img, "RGBA")

    # Map the 108-unit vector viewport onto the supersampled canvas.
    unit = canvas / VIEWPORT * LEGACY_SCALE
    offset = (canvas - VIEWPORT * unit) / 2.0

    def p(x, y):
        return (offset + x * unit, offset + y * unit)

    def rect(x0, y0, x1, y1, fill):
        draw.rectangle([p(x0, y0), p(x1, y1)], fill=fill)

    def line(x0, y0, x1, y1, fill, width):
        draw.line([p(x0, y0), p(x1, y1)], fill=fill, width=max(1, int(width * unit)))

    def dot(cx, cy, r, fill):
        draw.ellipse([p(cx - r, cy - r), p(cx + r, cy + r)], fill=fill)

    # Background mesh lattice.
    line(18, 86, 40, 66, (255, 255, 255, 31), 1.4)
    line(40, 66, 62, 78, (255, 255, 255, 31), 1.4)
    line(62, 78, 88, 54, (255, 255, 255, 31), 1.4)
    line(20, 30, 44, 44, (255, 255, 255, 26), 1.4)
    line(44, 44, 70, 26, (255, 255, 255, 26), 1.4)
    line(70, 26, 92, 40, (255, 255, 255, 26), 1.4)

    # Shield.
    shield = [
        (54, 20), (83, 29.5), (83, 52.5), (78, 66), (68, 78), (54, 89),
        (40, 78), (30, 66), (25, 52.5), (25, 29.5),
    ]
    draw.polygon([p(*pt) for pt in shield], fill=(255, 255, 255, 51))
    draw.line([p(*pt) for pt in shield + [shield[0]]],
              fill=(255, 255, 255, 179), width=max(1, int(2.8 * unit)), joint="curve")

    # Mesh links from the AI node down to the beam nodes.
    line(48.5, 33.6, 34.2, 45.4, AMBER + (115,), 1.5)
    line(59.5, 33.6, 73.8, 45.4, AMBER + (115,), 1.5)
    line(54, 37.7, 54, 44, AMBER + (191,), 1.8)

    # Scales: post, base, foot, beam, hangers.
    rect(52.2, 36, 55.8, 74, WHITE)
    rect(45, 74, 63, 77.2, WHITE)
    rect(41.5, 77.2, 66.5, 80.6, WHITE)
    rect(33, 43.6, 75, 47.2, WHITE)
    rect(33.4, 46.8, 35.0, 52.8, WHITE + (217,))
    rect(73.0, 46.8, 74.6, 52.8, WHITE + (217,))

    # Pans as half-ellipses.
    for cx in (34.2, 73.8):
        draw.pieslice([p(cx - 7.7, 52.8 - 11.6), p(cx + 7.7, 52.8 + 11.6)],
                      start=0, end=180, fill=WHITE)

    # AI node (diamond) and mesh nodes on the beam.
    draw.polygon([p(54, 29.5), p(59.5, 33.6), p(54, 37.7), p(48.5, 33.6)], fill=AMBER)
    dot(34.2, 45.4, 4.2, AMBER)
    dot(73.8, 45.4, 4.2, AMBER)
    dot(54, 45.4, 3.2, WHITE)

    img = img.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), _rounded_mask(size))
    return out


def main():
    written = []
    for density, size in DENSITIES.items():
        icon = render(size)
        target_dir = RES / f"mipmap-{density}"
        target_dir.mkdir(parents=True, exist_ok=True)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            path = target_dir / name
            if name == "ic_launcher_round.png":
                circular = Image.new("RGBA", (size, size), (0, 0, 0, 0))
                mask = Image.new("L", (size, size), 0)
                ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
                circular.paste(render(size).convert("RGBA"), (0, 0), mask)
                circular.save(path)
            else:
                icon.save(path)
            written.append(str(path.relative_to(RES.parents[2])))
    print("wrote:")
    for w in written:
        print("  " + w)


if __name__ == "__main__":
    main()
