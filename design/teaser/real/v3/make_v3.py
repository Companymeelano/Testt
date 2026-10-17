"""MEELANO teaser v3: ~80 s, real app screenshots, Persian voiceover per scene,
procedural business-lounge score (ducked under the voice)."""
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
FALLBACK_FONT = None
W, H, FPS, SR = 1080, 1920, 30, 24000
GOLD, NAVY, CREAM = (212, 175, 55), (15, 23, 42), (248, 244, 232)
LEAD, TAIL = 0.5, 0.9

# ------------------------------------------------------------------ scenes
# voice file, screenshot, pan (start_y, end_y), caption text
SCENES = [
    ("s1.wav", "home-dashboard.jpg", (0, 470),
     "هر روز فروش، موجودی و بدهی مشتری‌ها را از دفتر و اکسل درمی‌آوری؟ وقتش رسیده که کسب‌وکارت را با یک نگاه ببینی."),
    ("s2.wav", "assistant-card.jpg", (0, 60),
     "این برنامه مستقیم به اطلاعات حسابداری آتیران وصل می‌شود. دیگر لازم نیست چیزی را دوباره وارد کنی؛ همان داده‌های زنده‌ی فروشگاهت، همین حالا."),
    ("s3.wav", "home-dashboard.jpg", (470, 0),
     "فروش امروز، سود، موجودی و بدهی مشتری‌ها، همه در یک داشبورد زنده. عددها را خودت می‌بینی، لحظه به لحظه."),
    ("s4.wav", "assistant-chat.jpg", (0, 120),
     "یا فقط با صدایت بپرس. دستیار فارسی جواب می‌دهد: فروش امروز چطور بوده؟ وضعیت انبار چیست؟ و چک‌های نزدیک سررسید کدام‌اند؟"),
    ("s5.wav", "sections-grid.jpg", (0, 200),
     "از فاکتور و صندوق و چک تا انبار، مشتریان و گزارش‌ها؛ همه بخش‌ها در یک برنامه، با یک لمس."),
    ("s6.wav", "products-stock.jpg", (0, 80),
     "کالایی تمام شد؟ پیش از آنکه مشتری بپرسد، خبردار می‌شوی. فروش را از دست نمی‌دهی و موجودی همیشه زیر کنترل است."),
    (None, None, None,
     "مدیریت فروشگاهت، در جیب تو. امروز امتحان کن و کسب‌وکارت را با یک نگاه ببین."),
]


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
clips = []
durations = []
for voice_file, _, _, _ in SCENES:
    if voice_file is None:
        clips.append(None)
        durations.append(7.0)  # closing card, held for the logo
    else:
        c = read_wav(os.path.join(VOICE_DIR, voice_file))
        clips.append(c)
        durations.append(LEAD + len(c) / SR + TAIL)
TOTAL = sum(durations)
starts = np.cumsum([0] + durations[:-1])


# ------------------------------------------------------------------ voice track
def voice_track():
    v = np.zeros(int(TOTAL * SR) + SR, dtype=np.float32)
    for c, st in zip(clips, starts):
        if c is None:
            continue
        s0 = int((st + LEAD) * SR)
        v[s0:s0 + len(c)] += c
    return v[: int(TOTAL * SR)]


# ------------------------------------------------------------------ score
def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


BPM = 80
BEAT = 60.0 / BPM          # 0.75 s
BAR = BEAT * 4             # 3 s
CHORD_LEN = BAR * 2        # chord changes every 2 bars
# Am9 - Fmaj7 - C6 - G(sus) : bass root, chord tones (MIDI)
CHORDS = [
    (45, [57, 60, 64, 67, 71]),
    (41, [53, 57, 60, 64]),
    (48, [55, 60, 64, 69]),
    (43, [50, 55, 59, 62]),
]


def score(n):
    t = np.arange(n, dtype=np.float32) / SR
    out = np.zeros(n, dtype=np.float32)
    chord_idx = (t // CHORD_LEN).astype(int) % len(CHORDS)

    # warm pad: detuned sine stacks, crossfaded per chord
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
    lfo = 0.85 + 0.15 * np.sin(2 * math.pi * 0.09 * t)
    out += pad * lfo

    # bass: eighth-note pulse on the chord root with an octave push
    eighth = BEAT / 2
    e_idx = (t // eighth).astype(int)
    tm = t - e_idx * eighth
    bar_of_eighth = (e_idx * eighth) // CHORD_LEN
    chord_of_eighth = bar_of_eighth.astype(int) % len(CHORDS)
    base_midi = np.array([CHORDS[k][0] for k in range(len(CHORDS))])[chord_of_eighth]
    octave_push = np.where((e_idx % 4) == 2, 12, 0)
    f_bass = 440.0 * 2 ** ((base_midi + octave_push - 69) / 12.0)
    bass_env = np.exp(-tm * 7.0)
    bass = (np.sin(2 * math.pi * f_bass * t) + 0.25 * np.sin(4 * math.pi * f_bass * t)) * bass_env
    out += bass * 0.22

    # kick on beats 1 and 3 of every bar, with a falling pitch
    kb = t % (BEAT * 2)
    kick_env = np.exp(-kb * 16.0)
    kick_phase = 2 * math.pi * (46 * kb + 60 * (1 - np.exp(-kb * 35.0)) / 35.0)
    kick = np.sin(kick_phase) * kick_env * (kb < 0.5)
    out += kick * 0.45

    # closed hats on the offbeats
    rng = np.random.default_rng(7)
    noise = rng.standard_normal(n).astype(np.float32)
    hat_tm = (t - (t // eighth) * eighth - eighth / 2)
    hat_mask = (t // eighth) % 2 == 1
    hat = noise * np.exp(-np.abs(hat_tm) * 90.0) * hat_mask
    hat = np.diff(hat, prepend=0.0).astype(np.float32)
    out += hat * 0.12

    # gold glitter: a short triangle arpeggio on every chord change
    for k in range(int(n / SR / CHORD_LEN) + 1):
        base_t = k * CHORD_LEN
        tones = CHORDS[k % len(CHORDS)][1]
        for j in range(8):
            st = base_t + j * (BEAT / 2)
            if st >= n / SR:
                break
            m = tones[j % len(tones)] + 12
            s0 = int(st * SR)
            ln = int(0.5 * SR)
            tt = np.arange(min(ln, n - s0)) / SR
            env = np.exp(-tt * 9.0)
            tri = 2 * np.abs(2 * ((mtof(m) * tt) % 1) - 1) - 1
            out[s0:s0 + len(tt)] += (tri * env * 0.035).astype(np.float32)

    # cash-register style "ding" at the dashboard and stock alert scenes
    for when in (starts[2] + 2.0, starts[2] + 4.5, starts[5] + 1.0):
        s0 = int(when * SR)
        if s0 >= n:
            continue
        ln = int(0.6 * SR)
        tt = np.arange(min(ln, n - s0)) / SR
        env = np.exp(-tt * 6.0)
        ding = np.sin(2 * math.pi * 1568 * tt) + 0.5 * np.sin(2 * math.pi * 2093 * tt)
        out[s0:s0 + len(tt)] += (ding * env * 0.07).astype(np.float32)

    # master fades
    fade_in = np.minimum(1.0, t / 1.2)
    fade_out = np.clip((n / SR - t) / 3.0, 0.0, 1.0)
    return out * fade_in * fade_out


def duck(voice, music):
    win = int(0.08 * SR)
    env = np.convolve(np.abs(voice), np.ones(win, dtype=np.float32) / win, mode="same")
    env = np.clip(env / 0.08, 0.0, 1.0)
    smooth = np.convolve(env, np.ones(int(0.05 * SR), dtype=np.float32) / int(0.05 * SR), mode="same")
    return music * (1.0 - 0.7 * smooth)


def build_audio(path):
    voice = voice_track()
    n = len(voice)
    music = score(n) * 0.75
    music = duck(voice, music)
    mix = voice + music
    peak = float(np.max(np.abs(mix))) or 1.0
    mix = mix / max(1.0, peak * 1.08)
    pcm = (np.clip(mix, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


# ------------------------------------------------------------------ video
_fonts = {}


def font(size, bold=True):
    k = (size, bold)
    if k not in _fonts:
        name = "Vazirmatn-Bold.ttf" if bold else "Vazirmatn-Regular.ttf"
        _fonts[k] = ImageFont.truetype(os.path.join(FONT_DIR, name), size)
    return _fonts[k]


def fa(t):
    return get_display(reshape(t))


def wrap(text, size, max_w):
    lines, cur = [], ""
    for w in text.split():
        cand = (cur + " " + w).strip()
        if font(size).getlength(fa(cand)) <= max_w:
            cur = cand
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def caption_layer(text):
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    for y in range(1380, H):
        a = int(235 * min(1.0, (y - 1380) / 220))
        d.line([(0, y), (W, y)], fill=(10, 16, 32, a))
    lines = wrap(text, 44, W - 150)
    for i, ln in enumerate(lines):
        s = fa(ln)
        bb = d.textbbox((0, 0), s, font=font(44))
        d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], 1560 + i * 68), s, font=font(44),
               fill=CREAM + (255,))
    return lay


def ease(x):
    x = max(0.0, min(1.0, x))
    return 1 - (1 - x) ** 3


def end_frame(t, cap_alpha):
    img = Image.new("RGBA", (W, H), NAVY + (255,))
    a = ease(t / 0.9)
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)

    def put(text, y, size, color, bold, alpha):
        s = fa(text)
        bb = d.textbbox((0, 0), s, font=font(size, bold))
        d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], y), s, font=font(size, bold),
               fill=tuple(color) + (int(255 * alpha),))

    put("MEELANO", 640, 150, GOLD, True, a)
    lay2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay2)
    s = "Manager"
    bb = d.textbbox((0, 0), s, font=font(70, False))
    d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], 810), s, font=font(70, False), fill=CREAM + (int(255 * a),))
    img.alpha_composite(lay)
    img.alpha_composite(lay2)
    lay3 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay3)
    b = ease((t - 1.6) / 0.7)
    d.rounded_rectangle((190, 1100, 890, 1230), radius=65, fill=GOLD + (int(255 * b),))
    s = fa("امروز امتحان کن · لینک در بیو")
    bb = d.textbbox((0, 0), s, font=font(40))
    d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], 1140), s, font=font(40), fill=NAVY + (int(255 * b),))
    img.alpha_composite(lay3)
    lay4 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay4)
    s = fa("مدیریت فروشگاهت، در جیب تو")
    bb = d.textbbox((0, 0), s, font=font(48))
    d.text(((W - (bb[2] - bb[0])) / 2 - bb[0], 980), s, font=font(48), fill=CREAM + (int(255 * ease((t - 0.8) / 0.7)),))
    img.alpha_composite(lay4)
    return img.convert("RGB")


def main():
    audio = os.path.join(OUT_DIR, "_mix.wav")
    build_audio(audio)
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    out = os.path.join(OUT_DIR, "meelano-teaser-v3.mp4")
    cmd = [ff, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
           "-r", str(FPS), "-i", "-", "-i", audio, "-c:v", "libx264", "-preset", "medium", "-crf", "21",
           "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart",
           "-t", f"{TOTAL:.2f}", out]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    shots = {}
    captions = []
    for (vf, shot, pan, text), dur in zip(SCENES, durations):
        captions.append(caption_layer(text) if text else None)
        if shot:
            shots[shot] = Image.open(os.path.join(SRC, shot)).convert("RGB")
    for si, ((vf, shot, pan, text), dur, st) in enumerate(zip(SCENES, durations, starts)):
        n = int(round(dur * FPS))
        cap = captions[si]
        for i in range(n):
            t = i / FPS
            if shot is None:
                frame = end_frame(t, 1.0).convert("RGBA")
            else:
                src = shots[shot]
                p = t / dur
                zoom = 1.0 + 0.05 * p
                cw, ch = W / zoom, H / zoom
                y_from, y_to = pan
                y0 = y_from + (y_to - y_from) * ease(p)  # pan in source pixels
                y0 = max(0, min(src.height - ch, y0))
                crop = src.crop((0, int(y0), int(cw), int(y0 + ch)))
                frame = crop.resize((W, H), Image.BICUBIC).convert("RGBA")
                a = min(1.0, (t - 0.0) / 0.4, (dur - t) / 0.4)
                alpha = cap.getchannel("A").point(lambda v, a=a: int(v * max(0.0, a)))
                capf = cap.copy()
                capf.putalpha(alpha)
                frame.alpha_composite(capf)
            # progress hairline
            prog_w = int(W * (st + t) / TOTAL)
            bar = Image.new("RGBA", (max(1, prog_w), 6), GOLD + (255,))
            frame.alpha_composite(bar, (0, H - 6))
            proc.stdin.write(frame.convert("RGB").tobytes())
    proc.stdin.close()
    rc = proc.wait()
    if rc != 0:
        raise SystemExit(f"ffmpeg failed ({rc})")
    print("total seconds:", round(TOTAL, 2))
    print("written", out)


if __name__ == "__main__":
    main()
