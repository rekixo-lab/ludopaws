#!/usr/bin/env python3
"""Ludo Paws V2 icons from the owner's art (assets/branding/source). The art is only cropped,
scaled and layered — never redrawn. Run from the project root:  python3 tools/branding/make_icons.py
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter

SRC = "assets/branding/source"
OUT = "assets/branding/icon"
os.makedirs(OUT, exist_ok=True)
cut = Image.open(f"{SRC}/icon_art_cutout.png").convert("RGBA")
teal = Image.open(f"{SRC}/icon_art_teal.png").convert("RGB")
W, H = cut.size

def clean_alpha(img, floor=18):
    """Drop faint halo specks (alpha < floor) so edges stay clean on any background."""
    a = img.getchannel("A").point(lambda v: 0 if v < floor else v)
    img = img.copy(); img.putalpha(a); return img

def max_radius(img, cx, cy, thr=128):
    a = img.getchannel("A"); px = a.load(); w, h = img.size; best = 0.0
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            if px[x, y] >= thr:
                d = (x - cx) ** 2 + (y - cy) ** 2
                if d > best: best = d
    return math.sqrt(best)

def fit_in_circle(img, canvas, radius):
    """Scale art about its bbox centre so its farthest opaque pixel sits at `radius`."""
    bx0, by0, bx1, by1 = img.getchannel("A").point(lambda v: 255 if v >= 128 else 0).getbbox()
    cx, cy = (bx0 + bx1) / 2, (by0 + by1) / 2
    r = max_radius(img, cx, cy)
    s = radius / r
    small = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    out.alpha_composite(small, (round(canvas / 2 - cx * s), round(canvas / 2 - cy * s)))
    return out

art = clean_alpha(cut)

# 1. Master + Play Store (opaque, from the teal artwork).
master = teal.resize((1024, 1024), Image.LANCZOS); master.save(f"{OUT}/icon_master_1024.png", optimize=True)
store = teal.resize((512, 512), Image.LANCZOS); store.save(f"{OUT}/playstore_icon_512.png", optimize=True)

# 2. Adaptive icon, 432 px = 108 dp @ xxxhdpi; safe circle 264 px (66 dp) diameter.
fg = fit_in_circle(art, 432, 138)   # fills the 132-px safe radius; visible mask radius is 144
fg.save(f"{OUT}/ic_launcher_foreground.png", optimize=True)

bg = Image.new("RGB", (432, 432))
d = bg.load()
for y in range(432):
    for x in range(432):
        t = (x + y) / 862.0                                  # diagonal #0B2530 -> #12384A
        base = [0x0B + (0x12 - 0x0B) * t, 0x25 + (0x38 - 0x25) * t, 0x30 + (0x4A - 0x30) * t]
        g = max(0.0, 1.0 - math.hypot(x - 216, y - 200) / 190.0) ** 2 * 0.32   # soft gold glow
        d[x, y] = tuple(round(c + (gc - c) * g) for c, gc in zip(base, (0xE8, 0xB0, 0x4A)))
bg.save(f"{OUT}/ic_launcher_background.png", optimize=True)

# 3. Monochrome (Android 13 themed icon): white silhouette of the dog's head + the paw dice,
#    with the dark details (eyes, nose, mouth, paw prints) cut out so it stays recognisable.
mono = Image.new("RGBA", (W, H), (0, 0, 0, 0))
mp = mono.load(); cp = art.load()
for y in range(H):
    for x in range(W):
        r, g, b, a = cp[x, y]
        if a < 128: continue
        keep = y < 0.575 * H or (x > 0.61 * W and y < 0.76 * H)      # head/upper body + dice
        if not keep: continue
        luma = 0.299 * r + 0.587 * g + 0.114 * b
        if luma < 72: continue                                       # detail holes
        mp[x, y] = (255, 255, 255, 255)
mono = mono.filter(ImageFilter.GaussianBlur(1.2)).point(lambda v: v)  # soften jaggies
a = mono.getchannel("A").point(lambda v: 255 if v >= 110 else 0)
mono = Image.new("RGBA", (W, H), (255, 255, 255, 0)); mono.putalpha(a)
mono = fit_in_circle(mono, 432, 128)
white = Image.new("RGBA", (432, 432), (255, 255, 255, 0)); white.putalpha(mono.getchannel("A"))
white.save(f"{OUT}/ic_launcher_monochrome.png", optimize=True)

# 4. Legacy launchers (square + round) and the Godot 192 icon, from foreground over background.
full = bg.convert("RGBA"); full.alpha_composite(fg)
for name, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    # Legacy icons have no system mask: use the full 108 dp layer cropped to the 72 dp visible area.
    vis = full.crop((72, 72, 360, 360)).resize((px, px), Image.LANCZOS)
    vis.convert("RGB").save(f"{OUT}/ic_launcher_{name}.png", optimize=True)
    m = Image.new("L", (px * 4, px * 4), 0); ImageDraw.Draw(m).ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
    rnd = vis.copy(); rnd.putalpha(m.resize((px, px), Image.LANCZOS)); rnd.save(f"{OUT}/ic_launcher_round_{name}.png", optimize=True)
full.crop((72, 72, 360, 360)).resize((192, 192), Image.LANCZOS).convert("RGB").save(f"{OUT}/icon_godot_192.png", optimize=True)
print("icons written to", OUT)
