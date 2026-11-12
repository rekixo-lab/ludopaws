#!/usr/bin/env python3
"""Adaptive-icon mask previews (circle, squircle, rounded square, teardrop) at 48 and 96 px on
light and dark wallpapers, plus the themed monochrome icon. Writes .godot/preview/branding/."""
import os
from PIL import Image, ImageDraw
OUT = ".godot/preview/branding"; os.makedirs(OUT, exist_ok=True)
I = "assets/branding/icon"
fg = Image.open(f"{I}/ic_launcher_foreground.png").convert("RGBA")
bg = Image.open(f"{I}/ic_launcher_background.png").convert("RGBA")
mono = Image.open(f"{I}/ic_launcher_monochrome.png").convert("RGBA")
full = bg.copy(); full.alpha_composite(fg)
S = 288 * 4  # visible 72 dp of the 108 dp layer, supersampled

def mask(kind):
    m = Image.new("L", (S, S), 0); d = ImageDraw.Draw(m)
    if kind == "circle": d.ellipse((0, 0, S - 1, S - 1), fill=255)
    elif kind == "rounded": d.rounded_rectangle((0, 0, S - 1, S - 1), radius=S * 0.18, fill=255)
    elif kind == "squircle":
        px = m.load(); n = 4.0
        for y in range(S):
            for x in range(S):
                u, v = abs(2 * x / S - 1), abs(2 * y / S - 1)
                if u ** n + v ** n <= 1: px[x, y] = 255
    elif kind == "teardrop":
        d.ellipse((0, 0, S - 1, S - 1), fill=255); d.rectangle((S // 2, S // 2, S - 1, S - 1), fill=255)
    return m

def icon(layer, kind, px):
    vis = layer.crop((72, 72, 360, 360)).resize((S, S), Image.LANCZOS)
    vis.putalpha(Image.composite(vis.getchannel("A"), Image.new("L", (S, S), 0), mask(kind)))
    return vis.resize((px, px), Image.LANCZOS)

kinds = ["circle", "squircle", "rounded", "teardrop"]
themed_fg = Image.new("RGBA", mono.size, (0x1D, 0x3B, 0x2A, 0)); themed_fg.putalpha(mono.getchannel("A"))
themed = Image.new("RGBA", mono.size, (0xC8, 0xE6, 0xC9, 255)); themed.alpha_composite(themed_fg)
for wall_name, wall in [("light", (236, 236, 228)), ("dark", (24, 26, 30))]:
    sheet = Image.new("RGB", (40 + 5 * 230, 40 + 2 * 150), wall)
    for row, px in enumerate([48, 96]):
        for col, kind in enumerate(kinds + ["themed"]):
            src = themed if kind == "themed" else full
            ic = icon(src, "circle" if kind == "themed" else kind, px)
            sheet.paste(ic, (40 + col * 230, 40 + row * 150), ic)
    sheet.save(f"{OUT}/icon_masks_{wall_name}.png")
big = Image.new("RGB", (4 * 300, 300), (30, 30, 30))
for i, kind in enumerate(kinds):
    ic = icon(full, kind, 280); big.paste(ic, (10 + i * 300, 10), ic)
big.save(f"{OUT}/icon_masks_large.png")
print("previews in", OUT)
