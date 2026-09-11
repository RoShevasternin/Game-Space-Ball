#!/usr/bin/env python3
"""Варіанти іконки Space Ball як адаптивної іконки Android.

Полотно 1024 px = 108dp. Лаунчер показує центральні 72dp (683 px) під маскою,
гарантовано видимий — круг 66dp (≈626 px, радіус 313 від центру).
Кожен варіант: bg (RGB), fg (RGBA, прозорий), mono (силует), full (bg+fg).
"""
import math, os, random
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))   # корінь проєкту
HERE = f"{ROOT}/store/icon_variants"
GAME = f"{ROOT}/app/src/main/assets"
os.makedirs(HERE, exist_ok=True)
FONT = f"{GAME}/font/Inter-Bold.ttf"
N = 1024
C = N / 2

PINK, BLUE, CYAN, VIOLET = (255, 92, 200), (92, 140, 255), (46, 230, 255), (176, 123, 255)


def atlas(name):
    img = Image.open(f"{GAME}/atlas/all.png").convert("RGBA")
    cur = None; xy = None
    for line in open(f"{GAME}/atlas/all.atlas", encoding="utf-8"):
        s = line.strip()
        if s == name: cur = s; continue
        if cur and s.startswith("xy:"): xy = [int(v) for v in s[3:].split(",")]
        if cur and s.startswith("size:"):
            w, h = [int(v) for v in s[5:].split(",")]
            return img.crop((xy[0], xy[1], xy[0] + w, xy[1] + h))
    raise KeyError(name)


BALL = atlas("ball1")


# ---------------------------------------------------------------------------
# Примітиви
# ---------------------------------------------------------------------------

def radial(inner, outer, center=(C, C), radius=N * 0.75):
    img = Image.new("RGB", (N, N), outer)
    d = ImageDraw.Draw(img)
    steps = 160
    for i in range(steps, 0, -1):
        t = i / steps
        r = radius * t
        col = tuple(int(inner[k] + (outer[k] - inner[k]) * t) for k in range(3))
        d.ellipse((center[0] - r, center[1] - r, center[0] + r, center[1] + r), fill=col)
    return img.convert("RGBA")


def colored(mask, color, strength=1.0):
    layer = Image.new("RGBA", mask.size, color + (255,))
    if strength != 1.0:
        mask = mask.point(lambda v: int(min(255, v * strength)))
    layer.putalpha(mask)
    return layer


def gradient(c1, c2, angle_deg=0):
    """Лінійний градієнт c1→c2 під кутом."""
    g = Image.new("RGB", (N, 1))
    for x in range(N):
        t = x / (N - 1)
        g.putpixel((x, 0), tuple(int(c1[k] + (c2[k] - c1[k]) * t) for k in range(3)))
    g = g.resize((N, N))
    if angle_deg:
        big = g.resize((int(N * 1.5), int(N * 1.5))).rotate(angle_deg, resample=Image.BICUBIC)
        o = (big.width - N) // 2
        g = big.crop((o, o, o + N, o + N))
    return g


def gradient_layer(mask, c1, c2, angle=0, strength=1.0):
    g = gradient(c1, c2, angle).convert("RGBA")
    if strength != 1.0:
        mask = mask.point(lambda v: int(min(255, v * strength)))
    g.putalpha(mask)
    return g


def blur(mask, r):
    return mask.filter(ImageFilter.GaussianBlur(r))


def nebula(img, blobs, seed=1):
    for (x, y, r, col, a) in blobs:
        m = Image.new("L", (N, N), 0)
        ImageDraw.Draw(m).ellipse((x - r, y - r, x + r, y + r), fill=a)
        img.alpha_composite(colored(blur(m, r * 0.6), col))
    rnd = random.Random(seed)
    stars = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(stars)
    for _ in range(140):
        x, y = rnd.uniform(0, N), rnd.uniform(0, N)
        s = rnd.choice([1.2, 1.6, 2.2, 3.0])
        d.ellipse((x - s, y - s, x + s, y + s), fill=rnd.randint(120, 255))
    img.alpha_composite(colored(stars, (255, 255, 255)))
    img.alpha_composite(colored(blur(stars, 4), (200, 180, 255), 1.5))
    return img


def sparkle(layer, cx, cy, s, color=(255, 255, 255)):
    m = Image.new("L", (N, N), 0)
    k = 0.2
    pts = [(cx, cy - s), (cx + s * k, cy - s * k), (cx + s, cy), (cx + s * k, cy + s * k),
           (cx, cy + s), (cx - s * k, cy + s * k), (cx - s, cy), (cx - s * k, cy - s * k)]
    ImageDraw.Draw(m).polygon(pts, fill=255)
    layer.alpha_composite(colored(blur(m, s * 0.35), color, 1.6))
    layer.alpha_composite(colored(m, (255, 255, 255)))


def ball(layer, mono, cx, cy, d, glow=PINK):
    # сяйво
    m = Image.new("L", (N, N), 0)
    ImageDraw.Draw(m).ellipse((cx - d * 0.62, cy - d * 0.62, cx + d * 0.62, cy + d * 0.62), fill=210)
    layer.alpha_composite(colored(blur(m, d * 0.16), glow))
    # сам м'яч
    b = BALL.resize((d, d), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=2, percent=70, threshold=2))
    layer.alpha_composite(b, (int(cx - d / 2), int(cy - d / 2)))
    # тонкий світлий обідок і відблиск
    rim = Image.new("L", (N, N), 0)
    ImageDraw.Draw(rim).ellipse((cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2), outline=255, width=max(3, d // 60))
    layer.alpha_composite(colored(rim, (255, 230, 255), 0.55))
    hl = Image.new("L", (N, N), 0)
    ImageDraw.Draw(hl).ellipse((cx - d * 0.30, cy - d * 0.36, cx - d * 0.06, cy - d * 0.18), fill=150)
    layer.alpha_composite(colored(blur(hl, d * 0.03), (255, 255, 255)))
    ImageDraw.Draw(mono).ellipse((cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2), fill=255)


def trail(layer, mono, p0, p1, p2, r0, r1, c1=CYAN, c2=PINK, angle=-35):
    """Слід м'яча по квадратичній кривій Безьє p0→p2, радіус r0→r1."""
    m = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(m)
    dm = ImageDraw.Draw(mono)
    steps = 90
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        r = r0 + (r1 - r0) * t
        a = int(40 + 215 * t ** 1.4)
        d.ellipse((x - r, y - r, x + r, y + r), fill=a)
        if t > 0.25:
            dm.ellipse((x - r * 0.8, y - r * 0.8, x + r * 0.8, y + r * 0.8), fill=255)
    layer.alpha_composite(gradient_layer(blur(m, 26), c1, c2, angle, 1.2))
    layer.alpha_composite(gradient_layer(blur(m, 4), c1, c2, angle, 0.9))
    core = m.point(lambda v: max(0, v - 120) * 2)
    layer.alpha_composite(colored(blur(core, 6), (255, 255, 255), 0.7))


def neon_bar(layer, mono, x0, y0, x1, h, c1=PINK, c2=CYAN):
    m = Image.new("L", (N, N), 0)
    ImageDraw.Draw(m).rounded_rectangle((x0, y0, x1, y0 + h), h // 2, fill=255)
    layer.alpha_composite(gradient_layer(blur(m, 30), c1, c2, 0, 1.5))
    layer.alpha_composite(gradient_layer(m, c1, c2))
    core = Image.new("L", (N, N), 0)
    ImageDraw.Draw(core).rounded_rectangle((x0 + 10, y0 + h * 0.3, x1 - 10, y0 + h * 0.55), h // 4, fill=230)
    layer.alpha_composite(colored(blur(core, 3), (255, 255, 255)))
    ImageDraw.Draw(mono).rounded_rectangle((x0, y0, x1, y0 + h), h // 2, fill=255)


def ring_masks(cx, cy, rx, ry, width, angle):
    ring = Image.new("L", (N, N), 0)
    ImageDraw.Draw(ring).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), outline=255, width=width)
    back = ring.copy(); ImageDraw.Draw(back).rectangle((0, cy, N, N), fill=0)
    front = ring.copy(); ImageDraw.Draw(front).rectangle((0, 0, N, cy), fill=0)
    rot = lambda im: im.rotate(angle, center=(cx, cy), resample=Image.BICUBIC)
    return rot(back), rot(front), rot(ring)


def draw_ring(layer, mask, c1=PINK, c2=BLUE):
    layer.alpha_composite(gradient_layer(blur(mask, 34), c1, c2, 0, 1.8))
    layer.alpha_composite(gradient_layer(mask, c1, c2))
    core = mask.point(lambda v: 255 if v > 200 else 0).filter(ImageFilter.MinFilter(9))
    layer.alpha_composite(colored(blur(core, 2), (255, 255, 255), 0.8))


# ---------------------------------------------------------------------------
# Варіанти
# ---------------------------------------------------------------------------

def variant_bounce():
    """A. Неоновий відскок: м'яч злітає з неонової платформи, лишаючи слід."""
    bg = radial((58, 20, 128), (6, 2, 26))
    nebula(bg, [(300, 330, 260, PINK, 120), (760, 720, 300, BLUE, 130), (720, 260, 180, VIOLET, 90)], seed=3)
    fg = Image.new("RGBA", (N, N), (0, 0, 0, 0)); mono = Image.new("L", (N, N), 0)
    neon_bar(fg, mono, 300, 742, 610, 34)
    trail(fg, mono, (400, 736), (440, 520), (585, 430), 10, 118)
    ball(fg, mono, 600, 430, 300)
    sparkle(fg, 330, 350, 46)
    sparkle(fg, 780, 640, 30, CYAN)
    return bg, fg, mono


def variant_gate():
    """B. Варп-ворота: м'яч пролітає крізь неонове кільце — фініш кожного рівня."""
    bg = radial((70, 16, 110), (8, 2, 30))
    nebula(bg, [(512, 520, 330, PINK, 110), (260, 780, 220, BLUE, 110), (800, 260, 200, VIOLET, 100)], seed=7)
    fg = Image.new("RGBA", (N, N), (0, 0, 0, 0)); mono = Image.new("L", (N, N), 0)
    back, front, full = ring_masks(C, 560, 290, 104, 30, 16)
    draw_ring(fg, back)
    trail(fg, mono, (270, 760), (300, 540), (470, 470), 8, 110, CYAN, VIOLET, -20)
    ball(fg, mono, 500, 470, 300, VIOLET)
    draw_ring(fg, front)
    mono.paste(255, mask=full.point(lambda v: 255 if v > 90 else 0))
    sparkle(fg, 760, 300, 44)
    sparkle(fg, 300, 330, 26, PINK)
    return bg, fg, mono


def variant_horizon():
    """C. Над планетою: м'яч високо над світною дугою планети, під ним — удар і пил."""
    bg = radial((40, 18, 110), (4, 2, 22), center=(C, 380))
    nebula(bg, [(260, 280, 230, PINK, 100), (800, 330, 240, BLUE, 120)], seed=11)
    # планета — у фоні, щоб дуга йшла до країв маски
    pm = Image.new("L", (N, N), 0)
    ImageDraw.Draw(pm).ellipse((C - 700, 700, C + 700, 2100), fill=255)
    planet = Image.new("RGBA", (N, N))
    pg = Image.new("RGB", (1, N))
    for y in range(N):
        t = max(0.0, min(1.0, (y - 700) / 300))
        pg.putpixel((0, y), (int(70 - 40 * t), int(30 + 10 * t), int(150 - 60 * t)))
    planet = pg.resize((N, N)).convert("RGBA"); planet.putalpha(pm)
    bg.alpha_composite(planet)
    rim = Image.new("L", (N, N), 0)
    ImageDraw.Draw(rim).ellipse((C - 700, 700, C + 700, 2100), outline=255, width=14)
    bg.alpha_composite(gradient_layer(blur(rim, 28), CYAN, PINK, 0, 1.6))
    bg.alpha_composite(gradient_layer(rim, CYAN, PINK, 0, 0.9))
    fg = Image.new("RGBA", (N, N), (0, 0, 0, 0)); mono = Image.new("L", (N, N), 0)
    # Горизонт планети і в монохромній (Material You) версії — інакше силует не читається
    ImageDraw.Draw(mono).ellipse((C - 700, 712, C + 700, 2112), outline=255, width=34)
    # тінь-відблиск на поверхні і пил
    sh = Image.new("L", (N, N), 0)
    ImageDraw.Draw(sh).ellipse((C - 120, 700, C + 120, 740), fill=200)
    fg.alpha_composite(colored(blur(sh, 18), CYAN))
    trail(fg, mono, (C, 700), (C, 560), (C, 420), 14, 120, CYAN, PINK, -90)
    ball(fg, mono, int(C), 400, 310)
    for (x, y, s) in [(360, 690, 14), (660, 684, 18), (420, 650, 9), (620, 640, 10)]:
        sparkle(fg, x, y, s, CYAN)
    sparkle(fg, 760, 300, 40)
    return bg, fg, mono


VARIANTS = [("A_bounce", variant_bounce), ("B_gate", variant_gate), ("C_horizon", variant_horizon)]


# ---------------------------------------------------------------------------
# Превʼю
# ---------------------------------------------------------------------------

def visible(full, size):
    a = int(N * 18 / 108); b = N - a
    return full.crop((a, a, b, b)).resize((size, size), Image.LANCZOS)


def masked(img, shape):
    s = img.width
    m = Image.new("L", (s * 4, s * 4), 0)
    d = ImageDraw.Draw(m)
    if shape == "circle": d.ellipse((0, 0, s * 4 - 1, s * 4 - 1), fill=255)
    else: d.rounded_rectangle((0, 0, s * 4 - 1, s * 4 - 1), int(s * 4 * 0.30), fill=255)
    m = m.resize((s, s), Image.LANCZOS)
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0)); out.paste(img, (0, 0), m)
    return out


def themed(mono_img, size, shape):
    """Тематична іконка Material You: світлий фон, силует кольором акценту."""
    a = int(N * 18 / 108); b = N - a
    m = mono_img.crop((a, a, b, b)).resize((size, size), Image.LANCZOS)
    tile = Image.new("RGBA", (size, size), (225, 214, 255, 255))
    ink = Image.new("RGBA", (size, size), (60, 40, 120, 255)); ink.putalpha(m)
    tile.alpha_composite(ink)
    return masked(tile, shape)


def main():
    rows = []
    cur_fg = Image.open(f"{ROOT}/app/src/main/res/launcher/mipmap-xxxhdpi/ic_launcher_foreground.webp").convert("RGBA").resize((N, N), Image.LANCZOS)
    cur_bg = Image.new("RGBA", (N, N), (11, 3, 89, 255))
    cur_full = cur_bg.copy(); cur_full.alpha_composite(cur_fg)
    cur_mono = cur_fg.getchannel("A")
    rows.append(("ЗАРАЗ", cur_full, cur_mono))

    for name, fn in VARIANTS:
        bg, fg, mono = fn()
        full = bg.copy(); full.alpha_composite(fg)
        bg.convert("RGB").save(f"{HERE}/{name}_bg.png")
        fg.save(f"{HERE}/{name}_fg.png")
        mono.save(f"{HERE}/{name}_mono.png")
        full.convert("RGB").save(f"{HERE}/{name}_full.png")
        rows.append((name.replace("_", " ").upper(), full, mono))
        print("ok", name)

    cell, small = 240, 110
    W = 40 + 4 * (cell + 40) + 2 * (small + 30) + 40
    H = 40 + len(rows) * (cell + 90)
    sheet = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    # темні шпалери зліва, світлі справа
    dark = Image.new("RGBA", (W // 2, H), (18, 22, 40, 255))
    light = Image.new("RGBA", (W - W // 2, H), (236, 232, 244, 255))
    sheet.paste(dark, (0, 0)); sheet.paste(light, (W // 2, 0))
    font = ImageFont.truetype(FONT, 30)
    d = ImageDraw.Draw(sheet)
    for i, (label, full, mono) in enumerate(rows):
        y = 40 + i * (cell + 90)
        d.text((40, y), label, font=font, fill=(255, 255, 255))
        yy = y + 50
        x = 40
        for shape in ("circle", "squircle"):
            sheet.alpha_composite(masked(visible(full, cell), shape), (x, yy)); x += cell + 40
        for shape in ("circle", "squircle"):
            sheet.alpha_composite(masked(visible(full, cell), shape), (x, yy)); x += cell + 40
        sheet.alpha_composite(masked(visible(full, small), "circle"), (x, yy + 20)); x += small + 30
        sheet.alpha_composite(themed(mono, small, "circle"), (x, yy + 20))
    d.text((W - 360, 10), "themed (Android 13+) →", font=ImageFont.truetype(FONT, 22), fill=(80, 60, 120))
    sheet.convert("RGB").save(f"{HERE}/icon_variants.png")
    print("sheet", sheet.size)


if __name__ == "__main__":
    main()
