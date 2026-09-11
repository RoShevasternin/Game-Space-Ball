#!/usr/bin/env python3
"""Ставить обраний варіант іконки з make_icon_variants.py у ресурси застосунку.

    python tools/install_icon.py C_horizon

Пише в app/src/main/res/launcher:
  mipmap-*/ic_launcher_background|foreground|monochrome.webp — шари адаптивної іконки (108dp)
  mipmap-*/ic_launcher.webp, ic_launcher_round.webp          — для Android < 8 (48dp)
  mipmap-anydpi-v26/ic_launcher(_round).xml                   — адаптивна іконка з трьома шарами
і store/play_icon_512.png — іконка для Google Play Console.
"""
import importlib.util, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES  = f"{ROOT}/app/src/main/res/launcher"

spec = importlib.util.spec_from_file_location("icons", f"{ROOT}/tools/make_icon_variants.py")
icons = importlib.util.module_from_spec(spec); spec.loader.exec_module(icons)
N = icons.N

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
</adaptive-icon>
"""


def visible(full):
    """Центральні 72dp зі 108dp — те, що лаунчер показує під маскою."""
    a = round(N * 18 / 108)
    return full.crop((a, a, N - a, N - a))


def shaped(img, size, shape):
    s4 = size * 4
    m = Image.new("L", (s4, s4), 0)
    d = ImageDraw.Draw(m)
    if shape == "circle":
        d.ellipse((0, 0, s4 - 1, s4 - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, s4 - 1, s4 - 1), round(s4 * 0.2), fill=255)
    m = m.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img.resize((size, size), Image.LANCZOS), (0, 0), m)
    return out


def main(name):
    fn = dict(icons.VARIANTS)[name]
    bg, fg, mono = fn()
    full = bg.copy(); full.alpha_composite(fg)
    mono_rgba = Image.new("RGBA", (N, N), (255, 255, 255, 0)); mono_rgba.putalpha(mono)

    for dens, k in DENSITIES.items():
        d = f"{RES}/mipmap-{dens}"
        os.makedirs(d, exist_ok=True)
        layer = round(108 * k)
        legacy = round(48 * k)
        bg.convert("RGB").resize((layer, layer), Image.LANCZOS).save(f"{d}/ic_launcher_background.webp", quality=95)
        fg.resize((layer, layer), Image.LANCZOS).save(f"{d}/ic_launcher_foreground.webp", quality=95)
        mono_rgba.resize((layer, layer), Image.LANCZOS).save(f"{d}/ic_launcher_monochrome.webp", quality=95)
        shaped(visible(full), legacy, "square").save(f"{d}/ic_launcher.webp", quality=95)
        shaped(visible(full), legacy, "circle").save(f"{d}/ic_launcher_round.webp", quality=95)

    xml_dir = f"{RES}/mipmap-anydpi-v26"
    os.makedirs(xml_dir, exist_ok=True)
    for f in ("ic_launcher.xml", "ic_launcher_round.xml"):
        open(f"{xml_dir}/{f}", "w", encoding="utf-8").write(ADAPTIVE_XML)

    # Google Play: 512×512 PNG без прозорості, маску Play накладає сам
    os.makedirs(f"{ROOT}/store", exist_ok=True)
    visible(full).convert("RGB").resize((512, 512), Image.LANCZOS).save(f"{ROOT}/store/play_icon_512.png")
    print("installed", name)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "C_horizon")
