"""30-second teaser built from REAL app screenshots (Persian voiceover, captions, motion)."""
import os
from arabic_reshaper import reshape
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
FONT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(HERE))),
                        "manager/app/src/main/assets/fonts")
SRC = os.path.join(HERE, "source-screens")
VOICE = os.path.join(ROOT, "voiceover-final.wav")
OUT = os.path.join(ROOT, "meelano-teaser-real-30s.mp4")
W, H, FPS, DUR = 1080, 1920, 30, 31.0
GOLD, NAVY, CREAM = (212, 175, 55), (15, 23, 42), (248, 244, 232)
_f = {}

def font(size, bold=False):
    k = (size, bold)
    if k not in _f:
        _f[k] = ImageFont.truetype(os.path.join(FONT_DIR, "Vazirmatn-Bold.ttf" if bold else "Vazirmatn-Regular.ttf"), size)
    return _f[k]

def fa(t):
    return get_display(reshape(t))

def wrap(text, size, max_w, bold=False):
    lines, cur = [], ""
    for w in text.split():
        cand = (cur + " " + w).strip()
        if font(size, bold).getlength(fa(cand)) <= max_w:
            cur = cand
        else:
            lines.append(cur); cur = w
    if cur: lines.append(cur)
    return lines

def ease(x):
    x = max(0.0, min(1.0, x)); return 1 - (1 - x) ** 3

# (start, end, screenshot, caption)
SCENES = [
    (0.0, 4.6, "home-dashboard.jpg", "گزارش فروش، موجودی و بدهی را هنوز از دفتر و اکسل درمی‌آوری؟"),
    (4.6, 9.4, "assistant.jpg", "این برنامه مستقیم به اطلاعات حسابداری آتیران وصل می‌شود."),
    (9.4, 16.5, "home-dashboard.jpg", "فروش، سود، موجودی و بدهی مشتری‌ها، همه در یک داشبورد زنده."),
    (16.5, 22.3, "sections-grid.jpg", "از فاکتور و صندوق و چک تا انبار و مشتریان، همه بخش‌ها در یک برنامه."),
    (22.3, 27.5, "products-stock.jpg", "کالایی تمام شد؟ پیش از آنکه مشتری بپرسد، خبردار می‌شوی."),
    (27.5, 31.0, None, "گزارش‌هایت را امروز در جیبت ببین."),
]
_src = {}

def screenshot(name):
    if name not in _src:
        _src[name] = Image.open(os.path.join(SRC, name)).convert("RGB")
    return _src[name]

def caption_layer(text, alpha):
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    # soft dark band behind the caption
    for y in range(1420, H):
        a = int(230 * min(1.0, (y - 1420) / 200))
        d.line([(0, y), (W, y)], fill=(10, 16, 32, a))
    lines = wrap(text, 46, W - 160, bold=True)
    for i, ln in enumerate(lines):
        s = fa(ln)
        bb = d.textbbox((0, 0), s, font=font(46, True))
        d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], 1640 + i * 72), s, font=font(46, True),
               fill=CREAM + (int(255 * alpha),))
    return lay

def end_card(t):
    img = Image.new("RGBA", (W, H), NAVY + (255,))
    d = ImageDraw.Draw(img)
    a = ease(t / 0.8)
    def center(text, y, size, color, bold=False, alpha=255):
        lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        dd = ImageDraw.Draw(lay)
        s = fa(text) if any("\u0600" <= c <= "\u06FF" for c in text) else text
        bb = dd.textbbox((0, 0), s, font=font(size, bold))
        dd.text(((W - (bb[2] - bb[0])) / 2 - bb[0], y), s, font=font(size, bold),
                fill=tuple(color) + (int(alpha * a),))
        img.alpha_composite(lay)
    center("MEELANO", 700, 150, GOLD, True)
    center("Manager", 870, 70, CREAM)
    center("مدیریت فروشگاهت، در جیب تو", 1000, 48, CREAM)
    b = ease((t - 1.4) / 0.6)
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(lay).rounded_rectangle((190, 1140, 890, 1270), radius=65, fill=GOLD + (int(255 * b),))
    img.alpha_composite(lay)
    s = fa("امروز امتحان کن · لینک در بیو")
    lay2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(lay2)
    bb = dd.textbbox((0, 0), s, font=font(42, True))
    dd.text(((W - (bb[2] - bb[0])) / 2 - bb[0], 1185), s, font=font(42, True), fill=NAVY + (int(255 * b),))
    img.alpha_composite(lay2)
    return img

def frame(i):
    t = i / FPS
    for a, b, shot, text in SCENES:
        if a <= t < b:
            break
    if shot is None:
        return end_card(t - a).convert("RGB")
    st = (t - a) / (b - a)
    src = screenshot(shot)
    zoom = 1.0 + 0.05 * st
    cw, ch = W / zoom, H / zoom
    y0 = (src.height - ch) * ease(st) * 0.9
    crop = src.crop((0, int(y0), int(cw), int(y0 + ch))) if cw <= src.width else src
    base = crop.resize((W, H), Image.BICUBIC).convert("RGBA")
    fade_in = min(1.0, (t - a) / 0.35)
    fade_out = min(1.0, (b - t) / 0.35)
    cap_alpha = max(0.0, min(fade_in, fade_out))
    base.alpha_composite(caption_layer(text, cap_alpha))
    base.alpha_composite(Image.new("RGBA", (int(W * t / DUR), 6), GOLD + (255,)), (0, H - 6))
    return base.convert("RGB")

def audio_ok():
    import wave
    return os.path.exists(VOICE)

def main():
    import subprocess, imageio_ffmpeg
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    cmd = [ff, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
           "-r", str(FPS), "-i", "-", "-i", VOICE, "-c:v", "libx264", "-preset", "medium", "-crf", "21",
           "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart",
           "-t", str(DUR), OUT]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for i in range(int(DUR * FPS)):
        p.stdin.write(frame(i).tobytes())
    p.stdin.close()
    if p.wait() != 0:
        raise SystemExit("ffmpeg failed")
    print("written", OUT)

if __name__ == "__main__":
    main()
