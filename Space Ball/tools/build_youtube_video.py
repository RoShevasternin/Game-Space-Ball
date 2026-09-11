#!/usr/bin/env python3
"""Промо-ролик 16:9 (1920×1080, 30 fps) для YouTube і хедера сторінки в Google Play.

Портретний геймплей (записи з телефона, ті самі, що для TikTok — store/video/raw) вписаний
у «телефон» з неоновою рамкою праворуч; зліва — великий підпис із сяйвом і м'яч-скін планети;
позаду — розмитий фон планети, що повільно наближається. Інтро та фінал — панорама всіх
чотирьох планет із логотипом. Звук — оригінальні звуки гри (той самий механізм, що в
build_tiktok_video.py). Вихід: store/video/spaceball_youtube_16x9.mp4.

Для сторінки Play: завантажити на YouTube як public, монетизацію вимкнути, посилання —
Play Console → Store listing → Video.
"""
import os, subprocess, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_tiktok_video as tt          # сегменти, аудіо, ffmpeg
import make_event_image as ev            # панорама, м'ячі, сліди, іскри

ROOT   = tt.ROOT
ASSETS = tt.ASSETS
RAW    = tt.RAW
OUT    = tt.OUT
TMP    = "/tmp/spaceball-yt"
FF     = tt.FF
FONT   = f"{ASSETS}/font/Inter-Bold.ttf"

W, H, FPS = 1920, 1080, 30
XF = tt.XF

# Телефон: 562×1000, праворуч
PH_W, PH_H, PH_X, PH_Y, PH_R = 562, 1000, 1140, 40, 48

# (кліп, start, end, підпис, акцент, accent2, фон планети, м'яч)  — start/end з build_tiktok_video
SEGS = [
    ("intro",   0.0, 3.2,  None,                                   "FF7BE0", "7FA4FF", None, None),
    ("lumina", 10.5, 14.5, ["60 LEVELS", "ACROSS 4 PLANETS"],      "FF7BE0", "7FA4FF", 1, "ball1"),
    ("verdis", 10.0, 13.5, ["TRAMPOLINES", "& SLIME"],             "7CFF6B", "2EE6C8", 2, "ball2"),
    ("asteria", 8.8, 12.3, ["DODGE LASERS", "& ASTEROIDS"],        "FF5CC8", "FF9A5C", 3, "ball3"),
    ("nebulon", 8.8, 12.3, ["PORTALS &", "BLACK HOLES"],           "9FB4FF", "E08CFF", 4, "ball4"),
    ("asteria", 15.2, 17.4, ["LOST?", "CONTINUE!"],                "7BE58A", "2EE6C8", 3, None),
    ("levels",  7.4, 10.6, ["COLLECT 3 STARS", "ON EVERY LEVEL"],  "FFD33D", "FF9A5C", 2, None),
    ("shop",    7.6, 10.2, ["UNLOCK NEON", "BALL SKINS"],          "6FE3FF", "B07BFF", 4, "ball3"),
    ("win",    11.4, 14.6, ["3 STARS", "PERFECT RUN"],             "FFD33D", "7CFF6B", 2, None),
    ("outro",   0.0, 4.0,  None,                                   "FF7BE0", "7FA4FF", None, None),
]


def hexc(s, a=255):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def cover(img, w, h):
    s = max(w / img.width, h / img.height)
    img = img.resize((int(img.width * s) + 1, int(img.height * s) + 1), Image.LANCZOS)
    x = (img.width - w) // 2; y = (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))


def glow_text(canvas, lines, x, y, size, color, align="left", border=6, line_gap=1.12):
    font = ImageFont.truetype(FONT, size)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    lh = int(size * line_gap)
    def xpos(line):
        w = d.textlength(line, font=font)
        return x - w / 2 if align == "center" else x
    for i, line in enumerate(lines):
        d.text((xpos(line), y + i * lh), line, font=font, fill=hexc(color))
    glow = layer.filter(ImageFilter.GaussianBlur(size * 0.22))
    canvas.alpha_composite(ImageChops.add(glow, glow))
    d = ImageDraw.Draw(canvas)
    for i, line in enumerate(lines):
        d.text((xpos(line), y + i * lh), line, font=font, fill=(255, 255, 255, 255),
               stroke_width=border, stroke_fill=(20, 8, 40, 255))


# ---------------------------------------------------------------------------
# Статичні шари (Pillow)
# ---------------------------------------------------------------------------

def make_bg(idx, path):
    """Розмитий і затемнений фон планети 1920×1080 (ffmpeg потім повільно наближає)."""
    bg = cover(Image.open(f"{ASSETS}/textures/{idx}.png").convert("RGBA"), W, H)
    bg = bg.filter(ImageFilter.GaussianBlur(14))
    bg = Image.blend(bg, Image.new("RGBA", (W, H), (8, 4, 24, 255)), 0.45)
    bg.convert("RGB").save(path)


def make_mask(path):
    m = Image.new("L", (PH_W * 2, PH_H * 2), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, PH_W * 2 - 1, PH_H * 2 - 1), PH_R * 2, fill=255)
    m.resize((PH_W, PH_H), Image.LANCZOS).save(path)


def make_decor(lines, accent, accent2, ball_key, reg, path):
    """Прозорий шар поверх: тінь + неонова рамка телефона, підпис і м'яч зліва, іскри."""
    ev.set_canvas(W, H)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    a1, a2 = hexc(accent)[:3], hexc(accent2)[:3]

    # Світіння позаду телефона (над фоном, під самим телефоном — але шар іде поверх, тож
    # малюємо лише ЗОВНІ прямокутника телефона: вирізаємо його маскою наприкінці)
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).rounded_rectangle((PH_X - 16, PH_Y - 16, PH_X + PH_W + 16, PH_Y + PH_H + 16), PH_R + 16, fill=190)
    img.alpha_composite(ev.grad_layer(glow.filter(ImageFilter.GaussianBlur(40)), a1, a2, 1.0))
    # Обідок
    rim = Image.new("L", (W, H), 0)
    ImageDraw.Draw(rim).rounded_rectangle((PH_X - 8, PH_Y - 8, PH_X + PH_W + 8, PH_Y + PH_H + 8), PH_R + 8, fill=255)
    hole = Image.new("L", (W, H), 0)
    ImageDraw.Draw(hole).rounded_rectangle((PH_X, PH_Y, PH_X + PH_W, PH_Y + PH_H), PH_R, fill=255)
    img.alpha_composite(ev.grad_layer(ImageChops.subtract(rim, hole), a1, a2))

    # Ліва частина: підпис і м'яч
    if lines:
        size = 112
        font = ImageFont.truetype(FONT, size)
        d = ImageDraw.Draw(img)
        while size > 64 and max(d.textlength(l, font=font) for l in lines) > 960:
            size -= 4; font = ImageFont.truetype(FONT, size)
        glow_text(img, lines, 120, 300, size, accent)
    if ball_key:
        ev.trail(img, (380, 900), (430, 720), (560, 690), 8, 84, ev.CYAN, a1)
        ev.ball(img, reg[ball_key], 580, 680, 200, a1)
        ev.sparkle(img, 760, 620, 30)
        ev.sparkle(img, 330, 780, 22, ev.CYAN)
    ev.sparkle(img, 980, 180, 26, a1)
    ev.sparkle(img, 200, 920, 18)

    # Вирізаємо область телефона, щоб шар не закривав відео
    alpha = img.getchannel("A")
    alpha = ImageChops.subtract(alpha, hole)
    img.putalpha(alpha)
    img.save(path)


def make_card(kind, reg, path):
    """Інтро/фінал: панорама 4 планет + логотип."""
    ev.set_canvas(W, H)
    img = ev.panorama()
    img = Image.blend(img, Image.new("RGBA", (W, H), (6, 2, 20, 255)), 0.25)
    # М'ячі по кутах, нижче за текст
    ev.trail(img, (160, 1000), (200, 800), (330, 750), 8, 90, ev.CYAN, ev.PINK)
    ev.ball(img, reg["ball1"], 360, 740, 210, ev.VIOLET)
    ev.trail(img, (1760, 1000), (1720, 800), (1590, 750), 8, 90, ev.GOLD, ev.GREEN)
    ev.ball(img, reg["ball2"], 1560, 740, 200, ev.GREEN)
    ev.sparkles(img, 12, seed=3)
    if kind == "intro":
        glow_text(img, ["SPACE BALL"], W / 2, 300, 190, "FF5CC8", align="center", border=9)
        glow_text(img, ["BOUNCE YOUR WAY TO THE STARS"], W / 2, 545, 58, "9FC5FF", align="center", border=4)
    else:
        glow_text(img, ["SPACE BALL"], W / 2, 250, 170, "FF5CC8", align="center", border=9)
        glow_text(img, ["60 LEVELS  •  4 PLANETS  •  NEON SKINS"], W / 2, 480, 54, "FFD33D", align="center", border=4)
        glow_text(img, ["AVAILABLE ON GOOGLE PLAY"], W / 2, 780, 62, "7CFF6B", align="center", border=5)
    img.convert("RGB").save(path)


# ---------------------------------------------------------------------------
# ffmpeg
# ---------------------------------------------------------------------------

def zoompan(dur):
    frames = int(dur * FPS) + 2
    return (f"scale={W * 1.12:.0f}:-1,zoompan=z='1+0.0005*on':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
            f":d=1:s={W}x{H}:fps={FPS},trim=duration={dur},setpts=PTS-STARTPTS")


def main(out="spaceball_youtube_16x9.mp4"):
    os.makedirs(TMP, exist_ok=True)
    reg = ev.atlas_regions({"ball1", "ball2", "ball3", "ball4", "a1", "a2", "a3", "a4"})
    make_mask(f"{TMP}/mask.png")
    for i in (1, 2, 3, 4):
        make_bg(i, f"{TMP}/bg{i}.png")
    make_card("intro", reg, f"{TMP}/intro.png")
    make_card("outro", reg, f"{TMP}/outro.png")

    inputs, filters, tt_segs = [], [], []
    n = 0  # індекс вхідного файлу
    for i, (name, s, e, lines, acc, acc2, bg, ball_key) in enumerate(SEGS):
        dur = e - s
        tt_segs.append((name, s, e, lines if name != "outro" else None, acc, 1.0))
        if name in ("intro", "outro"):
            inputs += ["-loop", "1", "-framerate", str(FPS), "-t", f"{dur + 0.2}", "-i", f"{TMP}/{name}.png"]
            filters.append(f"[{n}:v]{zoompan(dur)},fps={FPS},settb=AVTB,format=yuv420p[v{i}]"); n += 1
            continue

        decor = f"{TMP}/decor{i}.png"
        make_decor(lines, acc, acc2, ball_key, reg, decor)
        inputs += ["-loop", "1", "-framerate", str(FPS), "-t", f"{dur + 0.2}", "-i", f"{TMP}/bg{bg}.png"]; ib = n; n += 1
        inputs += ["-i", f"{RAW}/{name}.mp4"]; ic = n; n += 1
        inputs += ["-loop", "1", "-framerate", str(FPS), "-t", f"{dur + 0.2}", "-i", decor]; idc = n; n += 1
        inputs += ["-loop", "1", "-framerate", str(FPS), "-t", f"{dur + 0.2}", "-i", f"{TMP}/mask.png"]; im = n; n += 1

        filters.append(f"[{ib}:v]{zoompan(dur)}[b{i}]")
        filters.append(f"[{ic}:v]trim=start={s}:end={e},setpts=PTS-STARTPTS,crop=1080:1920:0:240,"
                       f"scale={PH_W}:{PH_H}:flags=lanczos,fps={FPS},format=rgba[c{i}]")
        filters.append(f"[{im}:v]format=gray,trim=duration={dur},setpts=PTS-STARTPTS[m{i}]")
        filters.append(f"[c{i}][m{i}]alphamerge[cm{i}]")
        filters.append(f"[b{i}][cm{i}]overlay={PH_X}:{PH_Y}:shortest=1[p{i}]")
        # Підпис і рамка «прилітають» зліва з появою
        filters.append(f"[{idc}:v]format=rgba,trim=duration={dur},setpts=PTS-STARTPTS,"
                       f"fade=in:st=0:d=0.35:alpha=1,fade=out:st={dur - 0.3:.2f}:d=0.3:alpha=1[d{i}]")
        filters.append(f"[p{i}][d{i}]overlay=x='-60*max(0,1-t/0.35)':y=0:shortest=1,fps={FPS},settb=AVTB,format=yuv420p[v{i}]")

    durs, offs, total = tt.segment_offsets(tt_segs)
    prev = "v0"
    for i in range(1, len(SEGS)):
        outl = f"x{i}" if i < len(SEGS) - 1 else "vout"
        filters.append(f"[{prev}][v{i}]xfade=transition=fade:duration={XF}:offset={offs[i]:.3f}[{outl}]")
        prev = outl

    wav = f"{TMP}/audio.wav"
    tt.build_audio(tt_segs, wav)
    inputs += ["-i", wav]

    cmd = [FF, "-hide_banner", "-loglevel", "error", "-y", *inputs,
           "-filter_complex", ";".join(filters), "-map", "[vout]", "-map", f"{n}:a",
           "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-r", str(FPS),
           "-c:a", "aac", "-b:a", "192k", "-ac", "2", "-shortest", "-movflags", "+faststart", f"{OUT}/{out}"]
    subprocess.run(cmd, check=True)
    print(f"ok {out}  {total:.1f}s")


if __name__ == "__main__":
    main()
