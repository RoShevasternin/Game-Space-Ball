#!/usr/bin/env python3
"""Складає маркетингові скріншоти для Google Play з сирих кадрів гри.

Вхід:  store/raw/<name>.png — screencap 1080×2400 (гра летербоксована: y 240..2160).
Вихід: store/NN_<name>.png — 1080×1920: заголовок + «телефон» із неоновою рамкою на
       затемненому фоні планети; store/feature_graphic.png — 1024×500.
"""
import sys, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

ROOT   = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))   # корінь проєкту
GAME   = f"{ROOT}/app/src/main/assets"
FONT   = f"{GAME}/font/Inter-Bold.ttf"
RAW    = f"{ROOT}/store/raw"
OUT    = f"{ROOT}/store"
W, H   = 1080, 1920
os.makedirs(OUT, exist_ok=True)

def hexc(s, a=255):
    s = s.lstrip('#'); return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

# (файл, рядки заголовка, фон планети, акцент, акцент2)
SHOTS = [
    ("menu",         ["BOUNCE YOUR WAY", "TO THE STARS"],            1, "FF7BE0", "7FA4FF"),
    ("game_lumina",  ["60 LEVELS", "ACROSS 4 PLANETS"],              1, "FF7BE0", "7FA4FF"),
    ("game_nebulon", ["PORTALS &", "BLACK HOLES"],                   4, "9FB4FF", "E08CFF"),
    ("game_verdis",  ["TRAMPOLINES &", "SLIME PLATFORMS"],        2, "7CFF6B", "2EE6C8"),
    ("game_asteria", ["DODGE LASERS", "& ASTEROIDS"],                3, "FF5CC8", "FF9A5C"),
    ("levels",       ["COLLECT 3 STARS", "ON EVERY LEVEL"],          2, "7CFF6B", "FFD33D"),
    ("shop",         ["UNLOCK NEON", "BALL SKINS"],                  4, "6FE3FF", "B07BFF"),
    ("win",          ["DAILY CHALLENGES", "& LEADERBOARDS"],         2, "FFD33D", "7CFF6B"),
]

def load_raw(name):
    p = f"{RAW}/{name}.png"
    img = Image.open(p).convert("RGBA")
    if img.size == (1080, 2400):
        img = img.crop((0, 240, 1080, 2160))          # прибираємо летербокс
    return img.resize((1080, 1920), Image.LANCZOS)

def cover(img, w, h):
    s = max(w / img.width, h / img.height)
    img = img.resize((int(img.width * s) + 1, int(img.height * s) + 1), Image.LANCZOS)
    x = (img.width - w) // 2; y = (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))

def rounded_mask(w, h, r):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255)
    return m

def glow_text(canvas, lines, y, accent, size=104, fill="FFFFFF"):
    """Заголовок: розмите кольорове сяйво + чіткий текст із темною обводкою."""
    font = ImageFont.truetype(FONT, size)
    # Задовгий рядок — зменшуємо шрифт, доки не влізе з полями по 40 px
    probe = ImageDraw.Draw(canvas)
    while size > 60 and max(probe.textlength(l, font=font) for l in lines) > W - 80:
        size -= 4
        font = ImageFont.truetype(FONT, size)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    line_h = int(size * 1.12)
    for i, line in enumerate(lines):
        bw = d.textlength(line, font=font)
        d.text(((W - bw) / 2, y + i * line_h), line, font=font, fill=hexc(accent))
    glow = layer.filter(ImageFilter.GaussianBlur(22))
    glow = ImageChops.add(glow, glow)                    # яскравіше сяйво
    canvas.alpha_composite(glow)
    d = ImageDraw.Draw(canvas)
    for i, line in enumerate(lines):
        bw = d.textlength(line, font=font)
        d.text(((W - bw) / 2, y + i * line_h), line, font=font, fill=hexc(fill),
               stroke_width=5, stroke_fill=(20, 8, 40, 255))
    return y + len(lines) * line_h

def compose(name, lines, bg_idx, accent, accent2):
    raw = load_raw(name)

    # --- Фон: та сама планета, затемнена й розмита ------------------------------
    bg = cover(Image.open(f"{GAME}/textures/{bg_idx}.png").convert("RGBA"), W, H)
    bg = bg.filter(ImageFilter.GaussianBlur(5))
    bg = Image.blend(bg, Image.new("RGBA", (W, H), (8, 4, 24, 255)), 0.55)
    # м'яке кольорове сяйво позаду телефона
    halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse((W/2 - 520, 560, W/2 + 520, 1900), fill=hexc(accent, 120))
    halo = halo.filter(ImageFilter.GaussianBlur(160))
    bg.alpha_composite(halo)

    # --- Телефон: скріншот у рамці з заокругленням і неоновим обідком -----------
    scale = 0.84
    pw, ph = int(W * scale), int(H * scale)
    px, py = (W - pw) // 2, 400
    r = 64
    frame = raw.resize((pw, ph), Image.LANCZOS)
    mask  = rounded_mask(pw, ph, r)

    # зовнішнє світіння
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle((px - 14, py - 14, px + pw + 14, py + ph + 14), r + 14, fill=hexc(accent, 200))
    glow = glow.filter(ImageFilter.GaussianBlur(34))
    bg.alpha_composite(glow)

    # градієнтний обідок accent → accent2
    rim = Image.new("RGBA", (pw + 16, ph + 16), (0, 0, 0, 0))
    grad = Image.new("RGBA", (pw + 16, ph + 16))
    gd = ImageDraw.Draw(grad)
    c1, c2 = hexc(accent), hexc(accent2)
    for x in range(pw + 16):
        t = x / (pw + 15)
        col = tuple(int(c1[k] + (c2[k] - c1[k]) * t) for k in range(3)) + (255,)
        gd.line((x, 0, x, ph + 16), fill=col)
    rim_mask = rounded_mask(pw + 16, ph + 16, r + 8)
    inner = Image.new("L", (pw + 16, ph + 16), 0)
    ImageDraw.Draw(inner).rounded_rectangle((8, 8, pw + 7, ph + 7), r, fill=255)
    rim_mask = ImageChops.subtract(rim_mask, inner)
    rim.paste(grad, (0, 0), rim_mask)
    bg.alpha_composite(rim, (px - 8, py - 8))

    bg.paste(frame, (px, py), mask)

    # глянцевий відблиск по верхньому краю телефона
    gloss = Image.new("RGBA", (pw, 160), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(gloss)
    for yy in range(160):
        a = int(70 * (1 - yy / 160) ** 2)
        gdraw.line((0, yy, pw, yy), fill=(255, 255, 255, a))
    gloss_mask = rounded_mask(pw, ph, r).crop((0, 0, pw, 160))
    gloss.putalpha(ImageChops.multiply(gloss.getchannel("A"), gloss_mask))
    bg.alpha_composite(gloss, (px, py))

    # --- Заголовок ---------------------------------------------------------------
    glow_text(bg, lines, 96, accent)

    out = bg.crop((0, 0, W, H)).convert("RGB")
    return out

def feature_graphic():
    """1024×500: фон Asteria, логотип, три м'ячі з атласу."""
    FW, FH = 1024, 500
    bg = cover(Image.open(f"{GAME}/textures/3.png").convert("RGBA"), FW, FH)
    bg = Image.blend(bg, Image.new("RGBA", (FW, FH), (10, 4, 28, 255)), 0.25)
    # м'ячі з атласу
    atlas = Image.open(f"{GAME}/atlas/all.png").convert("RGBA")
    balls = {}
    cur = None
    for line in open(f"{GAME}/atlas/all.atlas", encoding="utf-8"):
        s = line.strip()
        if s in ("ball", "ball1", "ball2", "ball3", "ball4"): cur = s; continue
        if cur and s.startswith("xy:"):
            x, y = [int(v) for v in s[3:].split(",")]
        if cur and s.startswith("size:"):
            w, h = [int(v) for v in s[5:].split(",")]
            balls[cur] = atlas.crop((x, y, x + w, y + h)); cur = None
    layout = [("ball1", 700, 250, 190, 0), ("ball3", 880, 120, 130, 15), ("ball2", 905, 330, 150, -10)]
    for key, cx, cy, size, rot in layout:
        b = balls[key].resize((size, size), Image.LANCZOS).rotate(rot, resample=Image.BICUBIC)
        glow = Image.new("RGBA", (size * 2, size * 2), (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse((size * 0.4, size * 0.4, size * 1.6, size * 1.6), fill=(180, 120, 255, 150))
        glow = glow.filter(ImageFilter.GaussianBlur(size * 0.25))
        bg.alpha_composite(glow, (cx - size, cy - size))
        bg.alpha_composite(b, (cx - b.width // 2, cy - b.height // 2))
    # логотип
    font = ImageFont.truetype(FONT, 118)
    layer = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text((70, 120), "SPACE", font=font, fill=hexc("FF5CC8"))
    d.text((70, 250), "BALL", font=font, fill=hexc("FF5CC8"))
    bg.alpha_composite(ImageChops.add(layer.filter(ImageFilter.GaussianBlur(18)), layer.filter(ImageFilter.GaussianBlur(18))))
    d = ImageDraw.Draw(bg)
    d.text((70, 120), "SPACE", font=font, fill=(255, 255, 255, 255), stroke_width=5, stroke_fill=(30, 8, 50, 255))
    d.text((70, 250), "BALL",  font=font, fill=(255, 255, 255, 255), stroke_width=5, stroke_fill=(30, 8, 50, 255))
    small = ImageFont.truetype(FONT, 34)
    d.text((74, 392), "60 LEVELS  •  4 PLANETS  •  NEON SKINS", font=small, fill=hexc("FFD33D"), stroke_width=3, stroke_fill=(20, 8, 40, 255))
    bg.convert("RGB").save(f"{OUT}/feature_graphic.png", optimize=True)

if __name__ == "__main__":
    only = sys.argv[1:]
    for i, (name, lines, bg_idx, a, a2) in enumerate(SHOTS, 1):
        if only and name not in only: continue
        if not os.path.exists(f"{RAW}/{name}.png"):
            print("skip (no raw):", name); continue
        img = compose(name, lines, bg_idx, a, a2)
        path = f"{OUT}/{i:02d}_{name}.png"
        img.save(path, optimize=True)
        print("ok", path, img.size)
    feature_graphic()
    print("ok", f"{OUT}/feature_graphic.png")
