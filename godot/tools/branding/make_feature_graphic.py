#!/usr/bin/env python3
"""Play Store feature graphic 1024x500 (opaque, < 1 MB): V2 moonlit background, the icon art
(unchanged), the woodland plaque with the game title and the brand tagline (live strings.xml
brand_tagline). Key content inside the centre. Also copies the 512 store icon."""
import shutil
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance
OUT = "assets/branding/playstore"
W, H = 1024, 500
bg = Image.open("assets/ui/backgrounds/moonlit_meadow.webp").convert("RGB")
band = bg.crop((0, 0, bg.width, round(bg.height * 0.215)))   # moon, treeline, cabin, lantern
s = H / band.height
band = band.resize((round(band.width * s), H), Image.LANCZOS)
x0 = (band.width - W) // 2
canvas = band.crop((x0, 0, x0 + W, H))
canvas = ImageEnhance.Brightness(canvas).enhance(0.9).convert("RGBA")
# soft vignette so the centre reads
vig = Image.new("L", (W, H), 0); ImageDraw.Draw(vig).ellipse((-200, -160, W + 200, H + 160), fill=255)
vig = vig.filter(ImageFilter.GaussianBlur(120))
dark = Image.new("RGBA", (W, H), (4, 14, 12, 255))
canvas = Image.composite(canvas, dark, vig)

art = Image.open("assets/branding/source/icon_art_cutout.png").convert("RGBA")
art = art.resize((430, 430), Image.LANCZOS)
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse((150, 70, 520, 440), fill=(255, 196, 90, 90))
canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(50)))
canvas.alpha_composite(art, (120, 40))

plaque = Image.open("assets/ui/brand/woodland_title_plaque.webp").convert("RGBA")
pw = 430
plaque = plaque.resize((pw, round(plaque.height * pw / plaque.width)), Image.LANCZOS)
px, py = 548, 70
canvas.alpha_composite(plaque, (px, py))
d = ImageDraw.Draw(canvas)
title = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf", 50)
tag = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf", 38)
cx = px + pw / 2
cy = py + plaque.height * 0.47
d.text((cx, cy), "LUDO PAWS", font=title, anchor="mm", fill=(255, 241, 214), stroke_width=4, stroke_fill=(74, 38, 14))
d.text((cx, py + plaque.height + 46), "Roll. Race. Roar!", font=tag, anchor="mm", fill=(255, 214, 120), stroke_width=3, stroke_fill=(20, 30, 22))
canvas.convert("RGB").save(f"{OUT}/feature_graphic_1024x500.png", optimize=True)
shutil.copy("assets/branding/icon/playstore_icon_512.png", f"{OUT}/playstore_icon_512.png")
print("feature graphic written")
