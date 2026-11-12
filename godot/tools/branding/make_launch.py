#!/usr/bin/env python3
"""Single launch screen assets, shared by Android's system splash and the Godot launch screen.
All sizes in dp; bitmaps at xxxhdpi (4 px per dp) so Android scales them exactly like Godot.
  - launch_logo.png   288x288 dp (1152 px): the icon art (unchanged cut-out) fitted inside the
                      192 dp mask-safe circle, with a soft glow that fades out before the edge.
  - launch_branding.png 200x80 dp (800x320 px): "by" + live's Rekixo wordmark (rendered from the
                      unchanged live SVG), centred on one line.
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import math
OUT = "assets/branding/launch"
PX = 4  # px per dp (xxxhdpi)

# Logo: 288 dp canvas, content within a 192 dp circle (radius 96 dp = 384 px).
C = 288 * PX
art = Image.open("assets/branding/source/icon_art_cutout.png").convert("RGBA")
a = art.getchannel("A").point(lambda v: 0 if v < 18 else v); art.putalpha(a)
bb = a.point(lambda v: 255 if v >= 128 else 0).getbbox()
cx, cy = (bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2
px = a.load(); best = 0
for y in range(0, art.height, 2):
    for x in range(0, art.width, 2):
        if px[x, y] >= 128:
            best = max(best, (x - cx) ** 2 + (y - cy) ** 2)
r_art = math.sqrt(best)
target = 96 * PX * 0.95
s = target / r_art
small = art.resize((round(art.width * s), round(art.height * s)), Image.LANCZOS)
logo = Image.new("RGBA", (C, C), (0, 0, 0, 0))
glow = Image.new("RGBA", (C, C), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse((C / 2 - 300, C / 2 - 300, C / 2 + 300, C / 2 + 300), fill=(232, 176, 74, 70))
logo.alpha_composite(glow.filter(ImageFilter.GaussianBlur(70)))
logo.alpha_composite(small, (round(C / 2 - cx * s), round(C / 2 - cy * s)))
# Hard guarantee: nothing outside the 192 dp circle (the system mask would cut it).
mask = Image.new("L", (C, C), 0); ImageDraw.Draw(mask).ellipse((C / 2 - 96 * PX, C / 2 - 96 * PX, C / 2 + 96 * PX, C / 2 + 96 * PX), fill=255)
logo.putalpha(Image.composite(logo.getchannel("A"), Image.new("L", (C, C), 0), mask))
logo.save(f"{OUT}/launch_logo.png", optimize=True)

# Branding: 200x80 dp; "by" small + wordmark on one line, centred.
BW, BH = 200 * PX, 80 * PX
mark = Image.open(f"{OUT}/rekixo_wordmark_480.png").convert("RGBA")
mw = 120 * PX
mark = mark.resize((mw, round(mark.height * mw / mark.width)), Image.LANCZOS)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15 * PX)
by_w = ImageDraw.Draw(Image.new("L", (1, 1))).textlength("by", font=font)
gap = 6 * PX
total = by_w + gap + mark.width
x0 = (BW - total) / 2
brand = Image.new("RGBA", (BW, BH), (0, 0, 0, 0))
d = ImageDraw.Draw(brand)
mid = BH / 2
d.text((x0, mid + mark.height * 0.18), "by", font=font, fill=(214, 226, 236, 230), anchor="ls")
brand.alpha_composite(mark, (round(x0 + by_w + gap), round(mid - mark.height / 2)))
brand.save(f"{OUT}/launch_branding.png", optimize=True)
print("launch assets:", logo.size, brand.size)
