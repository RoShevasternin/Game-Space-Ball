#!/usr/bin/env python3
"""Картинка для Google Play Promotional content (Major update / Event).

Вимоги Play: 1920×1080 (16:9), JPG або 24-bit PNG, ≤ 1 МБ, БЕЗ тексту й логотипів,
важливі елементи в безпечній зоні: 15 % зверху, 20 % знизу, 10 % з боків.

Кадр: панорама з фонів усіх 4 планет, що плавно перетікають одна в одну; над кожною —
свій м'яч-скін у стрибку з неоновим слідом; праворуч м'яч влітає у варп-ворота.
    python tools/make_event_image.py   →  store/event/major_update_1920x1080.jpg + major_update_square_1080.jpg
"""
import math, os, random
from PIL import Image, ImageDraw, ImageFilter

ROOT   = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = f"{ROOT}/app/src/main/assets"
OUT    = f"{ROOT}/store/event"
W, H   = 1920, 1080

PINK, BLUE, CYAN, VIOLET, GREEN, GOLD = (255, 92, 200), (92, 140, 255), (46, 230, 255), (176, 123, 255), (124, 255, 107), (255, 211, 61)
SAFE = (int(W * 0.10), int(H * 0.15), int(W * 0.90), int(H * 0.80))


def atlas_regions(names):
    img = Image.open(f"{ASSETS}/atlas/all.png").convert("RGBA")
    out, cur, xy = {}, None, None
    for line in open(f"{ASSETS}/atlas/all.atlas", encoding="utf-8"):
        s = line.strip()
        if s in names: cur = s; continue
        if cur and s.startswith("xy:"): xy = [int(v) for v in s[3:].split(",")]
        if cur and s.startswith("size:"):
            w, h = [int(v) for v in s[5:].split(",")]
            out[cur] = img.crop((xy[0], xy[1], xy[0] + w, xy[1] + h)); cur = None
    return out


def layer():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))


def mask():
    return Image.new("L", (W, H), 0)


def colored(m, color, strength=1.0):
    l = Image.new("RGBA", (W, H), color + (255,))
    if strength != 1.0:
        m = m.point(lambda v: int(min(255, v * strength)))
    l.putalpha(m)
    return l


def hgradient(c1, c2):
    g = Image.new("RGB", (W, 1))
    for x in range(W):
        t = x / (W - 1)
        g.putpixel((x, 0), tuple(int(c1[k] + (c2[k] - c1[k]) * t) for k in range(3)))
    return g.resize((W, H)).convert("RGBA")


def grad_layer(m, c1, c2, strength=1.0):
    g = hgradient(c1, c2)
    if strength != 1.0:
        m = m.point(lambda v: int(min(255, v * strength)))
    g.putalpha(m)
    return g


# ---------------------------------------------------------------------------

def panorama(textures=(1, 2, 3, 4)):
    """Портретні фони планет (1080×1920) → смуги на всю висоту кадру з м'якими переходами."""
    slice_w = round(1080 * H / 1920)           # увесь арт планети по висоті (608 px для H=1080)
    step = (W - slice_w) / max(1, len(textures) - 1)
    base = Image.new("RGBA", (W, H), (6, 2, 20, 255))
    for i, tex_id in enumerate(textures):
        tex = Image.open(f"{ASSETS}/textures/{tex_id}.png").convert("RGBA").resize((slice_w, H), Image.LANCZOS)
        x = round(i * step)
        if i > 0:
            fade = slice_w - round(step)        # ширина перекриття
            a = Image.new("L", (slice_w, H), 255)
            d = ImageDraw.Draw(a)
            for k in range(fade):
                d.line((k, 0, k, H), fill=int(255 * (k / fade) ** 1.2))
            tex.putalpha(a)
        base.alpha_composite(tex, (x, 0))
    # легке затемнення й віньєтка, щоб неон читався
    base = Image.blend(base, Image.new("RGBA", (W, H), (8, 4, 26, 255)), 0.28)
    vig = mask()
    ImageDraw.Draw(vig).ellipse((-W * 0.25, -H * 0.45, W * 1.25, H * 1.45), fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(220))
    dark = Image.new("RGBA", (W, H), (4, 2, 14, 255))
    dark.putalpha(vig.point(lambda v: 200 - int(v * 0.78)))
    base.alpha_composite(dark)
    return base


def glow_dot(img, cx, cy, r, color, alpha=200, blur=None):
    m = mask()
    ImageDraw.Draw(m).ellipse((cx - r, cy - r, cx + r, cy + r), fill=alpha)
    img.alpha_composite(colored(m.filter(ImageFilter.GaussianBlur(blur or r * 0.45)), color))


def neon_bar(img, x0, x1, y, h, c1, c2):
    m = mask()
    ImageDraw.Draw(m).rounded_rectangle((x0, y, x1, y + h), h // 2, fill=255)
    img.alpha_composite(grad_layer(m.filter(ImageFilter.GaussianBlur(26)), c1, c2, 1.6))
    img.alpha_composite(grad_layer(m, c1, c2))
    core = mask()
    ImageDraw.Draw(core).rounded_rectangle((x0 + 8, y + h * 0.3, x1 - 8, y + h * 0.55), h // 4, fill=230)
    img.alpha_composite(colored(core.filter(ImageFilter.GaussianBlur(2)), (255, 255, 255)))


def trail(img, p0, p1, p2, r0, r1, c1, c2):
    m = mask()
    d = ImageDraw.Draw(m)
    steps = 110
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        r = r0 + (r1 - r0) * t
        d.ellipse((x - r, y - r, x + r, y + r), fill=int(30 + 225 * t ** 1.5))
    img.alpha_composite(grad_layer(m.filter(ImageFilter.GaussianBlur(24)), c1, c2, 1.25))
    img.alpha_composite(grad_layer(m.filter(ImageFilter.GaussianBlur(4)), c1, c2, 0.85))
    core = m.point(lambda v: max(0, v - 130) * 2)
    img.alpha_composite(colored(core.filter(ImageFilter.GaussianBlur(5)), (255, 255, 255), 0.6))


def ball(img, sprite, cx, cy, d, glow):
    glow_dot(img, cx, cy, d * 0.66, glow, 190, d * 0.2)
    b = sprite.resize((d, d), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))
    img.alpha_composite(b, (int(cx - d / 2), int(cy - d / 2)))
    hl = mask()
    ImageDraw.Draw(hl).ellipse((cx - d * 0.30, cy - d * 0.36, cx - d * 0.06, cy - d * 0.18), fill=140)
    img.alpha_composite(colored(hl.filter(ImageFilter.GaussianBlur(d * 0.03)), (255, 255, 255)))


def sparkle(img, cx, cy, s, color=(255, 255, 255)):
    m = mask()
    k = 0.2
    pts = [(cx, cy - s), (cx + s * k, cy - s * k), (cx + s, cy), (cx + s * k, cy + s * k),
           (cx, cy + s), (cx - s * k, cy + s * k), (cx - s, cy), (cx - s * k, cy - s * k)]
    ImageDraw.Draw(m).polygon(pts, fill=255)
    img.alpha_composite(colored(m.filter(ImageFilter.GaussianBlur(s * 0.4)), color, 1.7))
    img.alpha_composite(colored(m, (255, 255, 255)))


def gate(img, cx, cy, rx, ry, angle, c1, c2, front):
    """Кільце варп-воріт; front=False — задня половина (під м'ячем), True — передня."""
    ring = mask()
    ImageDraw.Draw(ring).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), outline=255, width=22)
    half = ring.copy()
    if front: ImageDraw.Draw(half).rectangle((0, 0, W, cy), fill=0)
    else:     ImageDraw.Draw(half).rectangle((0, cy, W, H), fill=0)
    half = half.rotate(angle, center=(cx, cy), resample=Image.BICUBIC)
    img.alpha_composite(grad_layer(half.filter(ImageFilter.GaussianBlur(30)), c1, c2, 1.9))
    img.alpha_composite(grad_layer(half, c1, c2))
    core = half.point(lambda v: 255 if v > 200 else 0).filter(ImageFilter.MinFilter(7))
    img.alpha_composite(colored(core.filter(ImageFilter.GaussianBlur(2)), (255, 255, 255), 0.8))
    if not front:
        inner = mask()
        ImageDraw.Draw(inner).ellipse((cx - rx * 0.9, cy - ry * 0.9, cx + rx * 0.9, cy + ry * 0.9), fill=110)
        inner = inner.rotate(angle, center=(cx, cy), resample=Image.BICUBIC).filter(ImageFilter.GaussianBlur(30))
        img.alpha_composite(grad_layer(inner, c2, c1))


def set_canvas(w, h):
    global W, H, SAFE
    W, H = w, h
    SAFE = (int(W * 0.10), int(H * 0.15), int(W * 0.90), int(H * 0.80))


def save_jpeg(img, name):
    rgb = img.convert("RGB")
    path = f"{OUT}/{name}"
    for q in (92, 88, 84, 80, 76):
        rgb.save(path, quality=q, optimize=True, progressive=True)
        if os.path.getsize(path) <= 1_000_000:
            break
    print("ok", path, os.path.getsize(path) // 1024, "KB, quality", q)
    # Превʼю з безпечною зоною (лише для перевірки, не завантажувати)
    prev = rgb.copy()
    ImageDraw.Draw(prev).rectangle(SAFE, outline=(255, 255, 0), width=4)
    prev.resize((W // 2, H // 2)).save(f"{OUT}/_preview_{name}", quality=85)


def render_wide(reg):
    set_canvas(1920, 1080)
    img = panorama()

    # Платформи, з яких стартують м'ячі (низ кадру, над безпечною межею 864)
    neon_bar(img, 250, 480, 818, 22, PINK, VIOLET)            # Lumina
    neon_bar(img, 700, 930, 790, 22, GREEN, CYAN)             # Verdis
    neon_bar(img, 1130, 1360, 818, 22, PINK, (255, 154, 92))  # Asteria

    # Сліди й м'ячі: кожен скін над своєю планетою
    trail(img, (360, 812), (330, 640), (430, 540), 8, 78, CYAN, PINK)
    ball(img, reg["ball1"], 440, 530, 176, VIOLET)
    trail(img, (815, 784), (800, 560), (900, 430), 8, 80, CYAN, GREEN)
    ball(img, reg["ball2"], 910, 420, 180, GREEN)
    trail(img, (1245, 812), (1230, 650), (1320, 560), 8, 76, GOLD, PINK)
    ball(img, reg["ball3"], 1330, 550, 172, PINK)

    # Варп-ворота праворуч, м'яч Nebulon влітає в кільце
    gcx, gcy = 1590, 360
    gate(img, gcx, gcy, 150, 58, 12, (159, 180, 255), (224, 140, 255), front=False)
    trail(img, (1400, 700), (1470, 420), (1580, 350), 8, 70, CYAN, VIOLET)
    ball(img, reg["ball4"], 1590, 345, 150, BLUE)
    gate(img, gcx, gcy, 150, 58, 12, (159, 180, 255), (224, 140, 255), front=True)

    stars(img, reg, [(560, 380, 96), (1040, 300, 104), (1450, 470, 88), (300, 300, 80)])
    sparkles(img, 14, seed=5)
    sparkle(img, 1700, 250, 34)
    sparkle(img, 700, 250, 26, CYAN)
    save_jpeg(img, "major_update_1920x1080.jpg")


def render_square(reg):
    set_canvas(1080, 1080)
    img = panorama((1, 4))                     # Lumina → Nebulon

    # Чотири м'ячі по висхідній діагоналі до воріт у правому верхньому куті
    neon_bar(img, 130, 330, 830, 20, PINK, VIOLET)
    trail(img, (230, 824), (190, 690), (270, 640), 7, 62, CYAN, PINK)
    ball(img, reg["ball1"], 280, 630, 140, VIOLET)
    trail(img, (330, 600), (380, 470), (460, 470), 6, 62, CYAN, GREEN)
    ball(img, reg["ball2"], 470, 470, 142, GREEN)
    trail(img, (520, 440), (580, 330), (650, 360), 6, 58, GOLD, PINK)
    ball(img, reg["ball3"], 660, 362, 134, PINK)

    gcx, gcy = 840, 290
    gate(img, gcx, gcy, 118, 46, 12, (159, 180, 255), (224, 140, 255), front=False)
    trail(img, (700, 330), (760, 250), (832, 282), 6, 52, CYAN, VIOLET)
    ball(img, reg["ball4"], 840, 282, 118, BLUE)
    gate(img, gcx, gcy, 118, 46, 12, (159, 180, 255), (224, 140, 255), front=True)

    stars(img, reg, [(700, 620, 84), (300, 330, 76), (880, 520, 70), (520, 760, 66)])
    sparkles(img, 10, seed=9)
    save_jpeg(img, "major_update_square_1080.jpg")


def stars(img, reg, spots):
    for key, (x, y, s) in zip(["a1", "a2", "a3", "a4"], spots):
        st = reg[key].resize((s, int(s * 67 / 70)), Image.LANCZOS)
        glow_dot(img, x, y, s * 0.55, GOLD, 120, s * 0.35)
        img.alpha_composite(st, (x - st.width // 2, y - st.height // 2))


def sparkles(img, count, seed):
    rnd = random.Random(seed)
    for _ in range(count):
        x = rnd.uniform(SAFE[0], SAFE[2]); y = rnd.uniform(SAFE[1], SAFE[3])
        sparkle(img, x, y, rnd.uniform(8, 20), rnd.choice([(255, 255, 255), CYAN, PINK, GOLD]))


def main():
    os.makedirs(OUT, exist_ok=True)
    reg = atlas_regions({"ball1", "ball2", "ball3", "ball4", "a1", "a2", "a3", "a4"})
    render_wide(reg)
    render_square(reg)


if __name__ == "__main__":
    main()
