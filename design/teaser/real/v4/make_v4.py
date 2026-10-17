"""MEELANO teaser v4: longer cut, real screenshots inside a phone frame,
clean title / phone / caption layout (no text over app content),
Persian voiceover per scene, original business lounge score ducked under the voice."""
import math
import os
import subprocess
import wave

import imageio_ffmpeg
import numpy as np
from arabic_reshaper import reshape
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, "src")
VOICE_DIR = os.path.join(BASE, "voice")
OUT_DIR = os.path.join(BASE, "out")
FONT_DIR = "/home/user/Testt/manager/app/src/main/assets/fonts"
W, H, FPS, SR = 1080, 1920, 30, 24000
GOLD, NAVY, CREAM = (212, 175, 55), (15, 23, 42), (248, 244, 232)
MUTED = (158, 170, 196)
LEAD, TAIL = 0.5, 0.9
WIN_H = 1980                 # window height in source pixels (aspect = screen 600x1100)
SCREEN_W, SCREEN_H = 600, 1100
PHONE_X, PHONE_Y = (W - SCREEN_W) // 2, 300

# ------------------------------------------------------------------ scenes
# voice file, screenshot, pan (window top y from -> to, source px), title, caption
SCENES = [
    ("s1.wav", "home-dashboard.jpg", (0, 428), "چالش هر روز",
     "هر روز فروش، موجودی و بدهی مشتری‌ها را از دفتر و اکسل درمی‌آوری؟ وقتش رسیده که کسب‌وکارت را با یک نگاه ببینی."),
    ("s2.wav", "assistant-card.jpg", (0, 200), "اتصال مستقیم",
     "این برنامه مستقیم به اطلاعات حسابداری آتیران وصل می‌شود. دیگر لازم نیست چیزی را دوباره وارد کنی؛ همان داده‌های زنده‌ی فروشگاهت، همین حالا."),
    ("s3.wav", "home-dashboard.jpg", (428, 0), "داشبورد زنده",
     "فروش امروز، سود، موجودی و بدهی مشتری‌ها، همه در یک داشبورد زنده. عددها را خودت می‌بینی، لحظه به لحظه."),
    ("s4.wav", "assistant-chat.jpg", (0, 428), "دستیار صوتی",
     "یا فقط با صدایت بپرس. دستیار فارسی جواب می‌دهد: فروش امروز چطور بوده؟ وضعیت انبار چیست؟ و چک‌های نزدیک سررسید کدام‌اند؟"),
    ("s5.wav", "hub-smart.jpg", (0, 428), "گزارش‌های هوشمند",
     "گزارش‌های هوشمند، آماده‌ی دیدن سریع. مشتریانی که مدتی خرید نکرده‌اند، سبدهای رها‌شده و خوش‌قولی چک، همه در یک جا."),
    ("s6.wav", "hub-purchase.jpg", (0, 428), "گزارش‌های خرید",
     "گزارش‌های خرید: خرید روزانه، پرخریدترین کالاها و روند قیمت خرید، با یک نگاه. شکاف قیمت تأمین‌کنندگان هم روشن می‌شود."),
    ("s7.wav", "hub-returns.jpg", (0, 428), "برگشتی و ابطالی",
     "برگشتی و ابطالی: فاکتورهای تأیید‌نشده را پیدا کن و سند برگشتی هر نوع را جدا و دقیق ببین."),
    ("s8.wav", "hub-visitors-a.jpg", (0, 428), "تحلیل ویزیتورها",
     "تحلیل ویزیتورها: بدهکاران، مشتریان بدون خرید، سود و وصول هر ویزیتور را، با جزئیات کامل ببین."),
    ("s11.wav", "hub-visitors-b.jpg", (0, 428), "مسیرها و سن بدهی",
     "بازده هر ویزیتور و فروش هر مسیر، همراه با سن بدهی مشتریان. تا بدانی کدام راه‌ها بیشترین سود را دارند."),
    ("s9.wav", "hub-receipts.jpg", (0, 428), "دریافت و پرداخت",
     "دریافت و پرداخت: قبض‌های روزانه، گردش پوز بانک‌ها و جمع هر روز، همه مرتب و قابل اعتماد."),
    (None, None, None, None,
     "مدیریت فروشگاهت، در جیب تو. امروز امتحان کن و کسب‌وکارت را با یک نگاه ببین."),
]
CLOSING_VOICE = "s10.wav"


def read_wav(path):
    with wave.open(path, "rb") as w:
        sr = w.getframerate()
        raw = w.readframes(w.getnframes())
    x = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    if sr != SR:
        idx = np.linspace(0, len(x) - 1, int(len(x) * SR / sr))
        x = np.interp(idx, np.arange(len(x)), x).astype(np.float32)
    return x


# ------------------------------------------------------------------ timing
clips, durations = [], []
for voice_file, _, _, _, _ in SCENES:
    if voice_file is None:
        c = read_wav(os.path.join(VOICE_DIR, CLOSING_VOICE))
        clips.append(c)
        durations.append(LEAD + len(c) / SR + 1.6)
    else:
        c = read_wav(os.path.join(VOICE_DIR, voice_file))
        clips.append(c)
        durations.append(LEAD + len(c) / SR + TAIL)
TOTAL = sum(durations)
starts = np.cumsum([0] + durations[:-1])


def voice_track():
    v = np.zeros(int(TOTAL * SR) + SR, dtype=np.float32)
    for c, st in zip(clips, starts):
        s0 = int((st + LEAD) * SR)
        v[s0:s0 + len(c)] += c
    return v[: int(TOTAL * SR)]


# ------------------------------------------------------------------ score
def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


BPM = 80
BEAT = 60.0 / BPM
CHORD_LEN = BEAT * 8
CHORDS = [
    (45, [57, 60, 64, 67, 71]),   # Am9
    (41, [53, 57, 60, 64]),       # Fmaj7
    (48, [55, 60, 64, 69]),       # C6
    (43, [50, 55, 59, 62]),       # G
]
DING_TIMES = [starts[2] + 2.0, starts[2] + 4.5, starts[7] + 1.0, starts[9] + 1.0]


def score(n):
    t = np.arange(n, dtype=np.float32) / SR
    out = np.zeros(n, dtype=np.float32)
    chord_idx = (t // CHORD_LEN).astype(int) % len(CHORDS)
    pad = np.zeros(n, dtype=np.float32)
    for k, (_, tones) in enumerate(CHORDS):
        mask = chord_idx == k
        if not mask.any():
            continue
        tk = t[mask]
        seg = np.zeros(tk.shape, dtype=np.float32)
        for m in tones:
            f = mtof(m)
            seg += np.sin(2 * math.pi * f * tk) * 0.5
            seg += np.sin(2 * math.pi * f * 1.004 * tk) * 0.25
            seg += np.sin(2 * math.pi * 2 * f * tk) * 0.08
        pad[mask] = seg * 0.035
    out += pad * (0.85 + 0.15 * np.sin(2 * math.pi * 0.09 * t))

    eighth = BEAT / 2
    e_idx = (t // eighth).astype(int)
    tm = t - e_idx * eighth
    chord_of_eighth = (e_idx * eighth // CHORD_LEN).astype(int) % len(CHORDS)
    base_midi = np.array([c[0] for c in CHORDS])[chord_of_eighth]
    octave_push = np.where((e_idx % 4) == 2, 12, 0)
    f_bass = 440.0 * 2 ** ((base_midi + octave_push - 69) / 12.0)
    bass = (np.sin(2 * math.pi * f_bass * t) + 0.25 * np.sin(4 * math.pi * f_bass * t)) * np.exp(-tm * 7.0)
    out += bass * 0.22

    kb = t % (BEAT * 2)
    kick_phase = 2 * math.pi * (46 * kb + 60 * (1 - np.exp(-kb * 35.0)) / 35.0)
    out += np.sin(kick_phase) * np.exp(-kb * 16.0) * (kb < 0.5) * 0.45

    rng = np.random.default_rng(7)
    noise = rng.standard_normal(n).astype(np.float32)
    hat_tm = t - (t // eighth) * eighth - eighth / 2
    hat = noise * np.exp(-np.abs(hat_tm) * 90.0) * ((t // eighth) % 2 == 1)
    out += np.diff(hat, prepend=0.0).astype(np.float32) * 0.12

    for k in range(int(n / SR / CHORD_LEN) + 1):
        tones = CHORDS[k % len(CHORDS)][1]
        for j in range(8):
            st = k * CHORD_LEN + j * (BEAT / 2)
            if st >= n / SR:
                break
            s0 = int(st * SR)
            ln = int(0.5 * SR)
            tt = np.arange(min(ln, n - s0)) / SR
            tri = 2 * np.abs(2 * ((mtof(tones[j % len(tones)] + 12) * tt) % 1) - 1) - 1
            out[s0:s0 + len(tt)] += (tri * np.exp(-tt * 9.0) * 0.035).astype(np.float32)

    for when in DING_TIMES:
        s0 = int(when * SR)
        if s0 >= n:
            continue
        tt = np.arange(min(int(0.6 * SR), n - s0)) / SR
        ding = np.sin(2 * math.pi * 1568 * tt) + 0.5 * np.sin(2 * math.pi * 2093 * tt)
        out[s0:s0 + len(tt)] += (ding * np.exp(-tt * 6.0) * 0.07).astype(np.float32)

    fade_in = np.minimum(1.0, t / 1.2)
    fade_out = np.clip((n / SR - t) / 3.0, 0.0, 1.0)
    return out * fade_in * fade_out


def duck(voice, music):
    win = int(0.08 * SR)
    env = np.convolve(np.abs(voice), np.ones(win, dtype=np.float32) / win, mode="same")
    env = np.clip(env / 0.08, 0.0, 1.0)
    sm = int(0.05 * SR)
    smooth = np.convolve(env, np.ones(sm, dtype=np.float32) / sm, mode="same")
    return music * (1.0 - 0.7 * smooth)


def build_audio(path):
    voice = voice_track()
    music = duck(voice, score(len(voice)) * 0.75)
    mix = voice + music
    peak = float(np.max(np.abs(mix))) or 1.0
    mix = mix / max(1.0, peak * 1.08)
    pcm = (np.clip(mix, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


# ------------------------------------------------------------------ visuals
_fonts = {}


def font(size, bold=True):
    k = (size, bold)
    if k not in _fonts:
        name = "Vazirmatn-Bold.ttf" if bold else "Vazirmatn-Regular.ttf"
        _fonts[k] = ImageFont.truetype(os.path.join(FONT_DIR, name), size)
    return _fonts[k]


def fa(t):
    return get_display(reshape(t))


def wrap(text, size, max_w, bold=False):
    lines, cur = [], ""
    for w in text.split():
        cand = (cur + " " + w).strip()
        if font(size, bold).getlength(fa(cand)) <= max_w:
            cur = cand
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def ease(x):
    x = max(0.0, min(1.0, x))
    return 1 - (1 - x) ** 3


def background():
    img = Image.new("RGBA", (W, H), NAVY + (255,))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for r in range(900, 0, -30):
        gd.ellipse((W / 2 - r, 220 - r, W / 2 + r, 220 + r), fill=GOLD + (int(10 * (1 - r / 900)),))
    img.alpha_composite(glow)
    return img


BG = background()


def centered_text(d, text, y, size, color, bold, alpha=255):
    s = fa(text)
    bb = d.textbbox((0, 0), s, font=font(size, bold))
    d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], y), s, font=font(size, bold),
           fill=tuple(color) + (int(alpha),))


def phone(img, shot_img, pan, p, dy):
    """Phone frame with a scrolling screenshot inside."""
    x0, y0 = PHONE_X, PHONE_Y + dy
    bz = 26
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.rounded_rectangle((x0 - bz, y0 - bz, x0 + SCREEN_W + bz, y0 + SCREEN_H + bz),
                        radius=74, fill=(8, 13, 28, 255), outline=GOLD + (255,), width=4)
    img.alpha_composite(lay)
    y_from, y_to = pan
    top = y_from + (y_to - y_from) * ease(p)
    top = max(0, min(shot_img.height - WIN_H, top))
    crop = shot_img.crop((0, int(top), shot_img.width, int(top) + WIN_H))
    scr = crop.resize((SCREEN_W, SCREEN_H), Image.BILINEAR).convert("RGBA")
    mask = Image.new("L", (SCREEN_W, SCREEN_H), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, SCREEN_W - 1, SCREEN_H - 1), radius=52, fill=255)
    scr.putalpha(mask)
    img.alpha_composite(scr, (x0, y0))


def scene_frame(shot_img, pan, title, caption, t, dur):
    p = t / dur
    frame = BG.copy()
    tfade = ease(t / 0.5)
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    centered_text(d, title, 120, 66, GOLD, True, 255 * tfade)
    d.rectangle((W / 2 - 60, 215, W / 2 + 60, 218), fill=GOLD + (int(255 * tfade),))
    frame.alpha_composite(lay)
    dy = int((1 - ease(t / 0.6)) * 90)
    phone(frame, shot_img, pan, p, dy)
    cfade = ease((t - 0.35) / 0.5) * min(1.0, (dur - t) / 0.35)
    lay2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d2 = ImageDraw.Draw(lay2)
    y = 1475
    for line in wrap(caption, 40, 940):
        centered_text(d2, line, y, 40, CREAM, False, 255 * max(0.0, cfade))
        y += 60
    frame.alpha_composite(lay2)
    return frame


def closing_frame(t, dur):
    frame = BG.copy()
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    a = ease(t / 0.9)
    centered_text(d, "MEELANO", 620, 150, GOLD, True, 255 * a)
    centered_text(d, "Manager", 800, 72, CREAM, False, 255 * a)
    centered_text(d, "مدیریت فروشگاهت، در جیب تو", 960, 48, CREAM, False, 255 * ease((t - 0.8) / 0.7))
    b = ease((t - 1.6) / 0.7)
    d.rounded_rectangle((190, 1120, 890, 1250), radius=65, fill=GOLD + (int(255 * b),))
    centered_text(d, "امروز امتحان کن · لینک در بیو", 1160, 40, NAVY, True, 255 * b)
    frame.alpha_composite(lay)
    return frame


def main():
    audio = os.path.join(OUT_DIR, "_mix_v4.wav")
    build_audio(audio)
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    out = os.path.join(OUT_DIR, "meelano-teaser-v4.mp4")
    cmd = [ff, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
           "-r", str(FPS), "-i", "-", "-i", audio, "-c:v", "libx264", "-preset", "medium", "-crf", "24",
           "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart",
           "-t", f"{TOTAL:.2f}", out]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    shots = {}
    for voice_file, shot, pan, title, caption in SCENES:
        if shot and shot not in shots:
            shots[shot] = Image.open(os.path.join(SRC, shot)).convert("RGB")
    for si, ((voice_file, shot, pan, title, caption), dur) in enumerate(zip(SCENES, durations)):
        n = int(round(dur * FPS))
        for i in range(n):
            t = i / FPS
            if shot is None:
                frame = closing_frame(t, dur)
            else:
                frame = scene_frame(shots[shot], pan, title, caption, t, dur)
            bar = Image.new("RGBA", (max(1, int(W * (starts[si] + t) / TOTAL)), 6), GOLD + (255,))
            frame.alpha_composite(bar, (0, H - 6))
            proc.stdin.write(frame.convert("RGB").tobytes())
    proc.stdin.close()
    if proc.wait() != 0:
        raise SystemExit("ffmpeg failed")
    print("total seconds:", round(TOTAL, 2))
    print("written", out)


if __name__ == "__main__":
    main()
