#!/usr/bin/env python3
"""Збирає рекламний ролик 9:16 (1080×1920, 30 fps) із записів екрана телефона — зі звуком гри.

Вхід (store/video/raw, у .gitignore):
  <name>.mp4  — adb screenrecord 1080×2400 (гра летербоксована: y 240..2160)
  <name>.t    — epoch ms, коли стартував screenrecord (date на телефоні)
  <name>.sfx  — події звуку з логу гри в режимі shots: `<шлях> <epoch ms> <гучність> <висота>`
Вихід: store/video/spaceball_tiktok_27s.mp4 і _15s.mp4 (H.264 + AAC).

Відео: обрізка летербокса, підписи з появою/зникненням, xfade між сегментами, фінальна заставка.
Аудіо: оригінальні звуки гри в тих самих місцях, що й у записі, «вшух» на переходах, варп на
фіналі. Музику додають у TikTok з Commercial Music Library — гучність оригінального звуку
в редакторі TikTok варто лишити ~30–50 %.
"""
import array, math, os, shutil, subprocess, sys, wave
import imageio_ffmpeg

ROOT   = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))   # корінь проєкту
ASSETS = f"{ROOT}/app/src/main/assets"
RAW    = f"{ROOT}/store/video/raw"
OUT    = f"{ROOT}/store/video"
FF     = imageio_ffmpeg.get_ffmpeg_exe()
# drawtext не любить пробілів у шляху до шрифту — копіюємо в /tmp
FONT = "/tmp/spaceball-Inter-Bold.ttf"
shutil.copy(f"{ASSETS}/font/Inter-Bold.ttf", FONT)

FPS = 30
XF  = 0.3        # тривалість переходу, с
SR  = 44100      # частота дискретизації аудіо
# Затримка між `date` і першим кадром screenrecord (виміряно по кадрах стрибка)
LATENCY = 0.35

# (файл, start, end, [рядки підпису], колір підпису, коефіцієнт швидкості)
# Вікна підібрані по контактному листу кадрів: без інтро-банера «LEVEL N» і до смерті м'яча
SEGMENTS = [
    ("nebulon", 8.8, 12.3, ["PORTALS &", "BLACK HOLES"],          "9FB4FF", 1.0),
    ("asteria", 8.8, 12.3, ["DODGE LASERS", "& ASTEROIDS"],        "FF5CC8", 1.0),
    ("verdis", 10.0, 13.5, ["TRAMPOLINES", "& SLIME"],             "7CFF6B", 1.0),
    ("lumina", 10.5, 14.5, ["60 LEVELS", "4 PLANETS"],             "FF7BE0", 1.0),
    ("asteria", 15.2, 17.4, ["LOST? CONTINUE!"],                   "7BE58A", 1.0),
    ("levels",  7.4, 10.6, ["COLLECT 3 STARS", "ON EVERY LEVEL"],  "FFD33D", 1.0),
    ("shop",    7.6, 10.2, ["UNLOCK NEON", "BALL SKINS"],          "6FE3FF", 1.0),
    ("win",    11.4, 14.6, ["3 STARS = PERFECT RUN"],              "FFD33D", 1.0),
    ("menu",    8.0, 11.5, None,                                   "FFFFFF", 1.0),   # фінальна заставка
]
SHORT = [SEGMENTS[0], SEGMENTS[1], SEGMENTS[2], SEGMENTS[5], SEGMENTS[6], SEGMENTS[8]]


# ---------------------------------------------------------------------------
# Відео
# ---------------------------------------------------------------------------

def esc(s):
    return s.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'").replace(",", "\\,")


def drawtext(text, y, size, color, dur, t_in=0.25, t_out=0.3, border=6, delay=0.0):
    alpha = (f"if(lt(t,{delay}),0,if(lt(t,{delay + t_in}),(t-{delay})/{t_in},"
             f"if(gt(t,{dur - t_out}),({dur}-t)/{t_out},1)))")
    return (f"drawtext=fontfile='{FONT}':text='{esc(text)}':fontsize={size}:fontcolor=#{color}"
            f":borderw={border}:bordercolor=#140828:x=(w-text_w)/2:y={y}:alpha='{alpha}'")


def segment_filter(idx, name, start, end, lines, color, speed):
    dur = (end - start) / speed
    f = (f"[{idx}:v]trim=start={start}:end={end},setpts=(PTS-STARTPTS)/{speed},"
         f"crop=1080:1920:0:240,fps={FPS},format=yuv420p")
    if lines:
        y0 = 1440 if name == "win" else 150   # на екрані перемоги вгорі вже є YOU WIN
        for i, line in enumerate(lines):
            f += "," + drawtext(line, y0 + i * 118, 96 if name != "win" else 72, color, dur)
    else:
        # фінал: затемнене меню, логотип, заклик, «безкоштовно»
        f += ",drawbox=x=0:y=0:w=1080:h=1920:color=#08041a@0.72:t=fill"
        f += "," + drawtext("SPACE BALL", 560, 150, "FFFFFF", dur, border=8)
        f += "," + drawtext("FREE ON GOOGLE PLAY", 740, 64, "FFD33D", dur, delay=0.5)
        f += "," + drawtext("DOWNLOAD NOW", 1560, 80, "7CFF6B", dur, delay=0.9)
    return f + f"[v{idx}]", dur


def segment_offsets(segs):
    durs = [(e - s) / sp for (_, s, e, _, _, sp) in segs]
    offs, acc = [], 0.0
    for i, d in enumerate(durs):
        offs.append(acc)
        acc += d - XF
    return durs, offs, acc + XF


# ---------------------------------------------------------------------------
# Аудіо
# ---------------------------------------------------------------------------

_cache = {}


def load_sound(rel_path):
    """Декодує звук гри в mono float [-1..1] через ffmpeg."""
    if rel_path in _cache:
        return _cache[rel_path]
    raw = subprocess.run([FF, "-hide_banner", "-loglevel", "error", "-i", f"{ASSETS}/{rel_path}",
                          "-f", "s16le", "-ac", "1", "-ar", str(SR), "-"],
                         check=True, capture_output=True).stdout
    pcm = array.array("h"); pcm.frombytes(raw)
    samples = [v / 32768.0 for v in pcm]
    _cache[rel_path] = samples
    return samples


def read_events(name):
    path = f"{RAW}/{name}.sfx"
    if not os.path.exists(path):
        return []
    start_ms = int(open(f"{RAW}/{name}.t").read().strip())
    events = []
    for line in open(path):
        parts = line.split()
        if len(parts) != 4:
            continue
        snd, ms, vol, pitch = parts[0], int(parts[1]), float(parts[2]), float(parts[3])
        events.append(((ms - start_ms) / 1000.0 - LATENCY, snd, vol, pitch))
    return events


def mix_into(buf, samples, at, gain, pitch=1.0):
    """Додає звук у буфер з позиції at (с); висота — зміна швидкості відтворення, як у libGDX."""
    i0 = int(at * SR)
    n = int(len(samples) / pitch)
    for k in range(n):
        j = i0 + k
        if j < 0:
            continue
        if j >= len(buf):
            break
        src = k * pitch
        a = int(src); b = min(a + 1, len(samples) - 1); fr = src - a
        buf[j] += (samples[a] * (1 - fr) + samples[b] * fr) * gain


def build_audio(segs, wav_path):
    durs, offs, total = segment_offsets(segs)
    buf = [0.0] * (int(total * SR) + SR)

    for (name, start, end, lines, _, speed), off, dur in zip(segs, offs, durs):
        for (t, snd, vol, pitch) in read_events(name):
            if start - 0.05 <= t < end:
                # у libGDX гучність > 1 обрізається до 1; «down» (приземлення) тихіше, щоб не заглушував
                gain = min(1.0, vol) * (0.45 if snd.endswith("down.mp3") else 0.8)
                mix_into(buf, load_sound(snd), off + (t - start) / speed, gain, pitch)
        # «вшух» на вході кожного сегмента, крім першого
        if off > 0:
            mix_into(buf, load_sound("sound/portal.wav"), off - 0.15, 0.22, 1.35)
        if lines is None:
            mix_into(buf, load_sound("sound/warp.wav"), off + 0.05, 0.9, 1.0)
            mix_into(buf, load_sound("sound/sparkle.wav"), off + 0.55, 0.6, 1.2)
            mix_into(buf, load_sound("sound/purchase.wav"), off + 0.95, 0.55, 1.0)

    # нормалізація до -1 dBFS і м'яке обмеження піків
    peak = max(1e-6, max(abs(v) for v in buf))
    k = 0.89 / peak
    out = array.array("h", (int(32767 * math.tanh(v * k * 1.1) / math.tanh(1.1)) for v in buf))
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(out.tobytes())
    return total


# ---------------------------------------------------------------------------
# Збірка
# ---------------------------------------------------------------------------

def main(out="spaceball_tiktok.mp4", segs=None):
    segs = segs or SEGMENTS
    inputs, filters = [], []
    for i, (name, s, e, lines, color, speed) in enumerate(segs):
        inputs += ["-i", f"{RAW}/{name}.mp4"]
        f, _ = segment_filter(i, name, s, e, lines, color, speed)
        filters.append(f)

    durs, offs, total = segment_offsets(segs)
    prev = "v0"
    for i in range(1, len(segs)):
        outl = f"x{i}" if i < len(segs) - 1 else "vout"
        filters.append(f"[{prev}][v{i}]xfade=transition=fade:duration={XF}:offset={offs[i]:.3f}[{outl}]")
        prev = outl

    wav = f"/tmp/spaceball-{os.path.splitext(out)[0]}.wav"
    build_audio(segs, wav)
    inputs += ["-i", wav]

    cmd = [FF, "-hide_banner", "-loglevel", "error", "-y", *inputs,
           "-filter_complex", ";".join(filters), "-map", "[vout]", "-map", f"{len(segs)}:a",
           "-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", "-r", str(FPS),
           "-c:a", "aac", "-b:a", "192k", "-ar", str(SR), "-shortest",
           "-movflags", "+faststart", f"{OUT}/{out}"]
    subprocess.run(cmd, check=True)
    print(f"ok {out}  {total:.1f}s")


if __name__ == "__main__":
    main("spaceball_tiktok_27s.mp4")
    main("spaceball_tiktok_15s.mp4", [(n, a, b, l, c, 1.15) for (n, a, b, l, c, _) in SHORT])
