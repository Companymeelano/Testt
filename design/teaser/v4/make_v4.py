"""MEELANO Manager teaser v4.

Motion graphics that REBUILD the app interface with demo data (no real customer,
store or price data). Persian captions live in fixed safe zones. Nothing here is a
screen recording: every frame is drawn from config.json.

usage: python make_v4.py <variant>
  variants: portrait35 | portrait15 | square35 | landscape35
"""
import json
import math
import os
import subprocess
import sys
import wave

import imageio_ffmpeg
import numpy as np
from arabic_reshaper import reshape
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFilter, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(BASE, "config.json"), encoding="utf-8"))
FONT_DIR = "/home/user/Testt/manager/app/src/main/assets/fonts"
OUT_DIR = os.path.join(BASE, "out")
FPS, SR = 30, 24000

NAVY = (11, 18, 32)
NAVY2 = (22, 33, 58)
NAVY3 = (30, 43, 71)
GOLD = (212, 175, 55)
GOLD_SOFT = (240, 217, 140)
CREAM = (248, 244, 232)
MUTED = (158, 170, 196)
TEAL = (45, 212, 191)
RED = (248, 113, 113)
VIOLET = (154, 123, 255)

FA_DIGITS = str.maketrans("0123456789", "۰۱۲۳۴۵۶۷۸۹")


def fdig(s):
    return str(s).translate(FA_DIGITS)


_fonts = {}


def font(size, bold=True):
    k = (int(size), bold)
    if k not in _fonts:
        name = "Vazirmatn-Bold.ttf" if bold else "Vazirmatn-Regular.ttf"
        _fonts[k] = ImageFont.truetype(os.path.join(FONT_DIR, name), int(size))
    return _fonts[k]


def fa(t):
    return get_display(reshape(t))


def text_w(t, size, bold=True):
    return font(size, bold).getlength(fa(t))


def wrap(text, size, max_w, bold=True):
    lines, cur = [], ""
    for w in text.split():
        cand = (cur + " " + w).strip()
        if text_w(cand, size, bold) <= max_w:
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


def ease_io(x):
    x = max(0.0, min(1.0, x))
    return 0.5 - 0.5 * math.cos(math.pi * x)


def draw_mid(d, text, cx, cy, size, fill, bold=True, anchor="mm"):
    d.text((cx, cy), fa(text), font=font(size, bold), fill=fill, anchor=anchor)


def rounded(img, box, r, fill=None, outline=None, width=2):
    lay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(lay).rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)
    img.alpha_composite(lay)


# ---------------------------------------------------------------- layouts
LAYOUTS = {
    "portrait": dict(W=1080, H=1920, phone=(480, 1050, 300, 520),
                     caption=("center", 300, 470, 940)),
    "square": dict(W=1080, H=1350, phone=(400, 876, 340, 400),
                   caption=("center", 150, 330, 940)),
    "landscape": dict(W=1920, H=1080, phone=(393, 900, 170, 90),
                      caption=("right", 200, 880, 1080)),
}


def background(W, H):
    img = Image.new("RGBA", (W, H), NAVY + (255,))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for r in range(int(W * 0.9), 0, -40):
        gd.ellipse((W * 0.85 - r, -H * 0.05 - r, W * 0.85 + r, -H * 0.05 + r),
                   fill=GOLD + (int(10 * (1 - r / (W * 0.9))),))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(20)))
    return img


# ---------------------------------------------------------------- phone UI
D = CFG["demo"]


def phone_surface(scene, t, s, pw, ph):
    """Draw the rebuilt app screen. Design units are 470 px wide; s = pw / 470."""
    sur = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
    d = ImageDraw.Draw(sur)
    rr = int(44 * s)
    d.rounded_rectangle((0, 0, pw - 1, ph - 1), radius=rr, fill=(7, 11, 22, 255),
                        outline=GOLD + (255,), width=max(2, int(3 * s)))
    inner = (int(16 * s), int(16 * s), pw - int(16 * s), ph - int(16 * s))
    d.rounded_rectangle(inner, radius=int(34 * s), fill=NAVY2 + (255,))

    def X(v):
        return v * s

    # status + header
    draw_mid(d, D["store"], X(370), X(60), int(X(22)), CREAM)
    d.rounded_rectangle((X(40), X(84), X(130), X(116)), radius=X(16), outline=GOLD, width=max(1, int(X(2))))
    draw_mid(d, "مدیر", X(85), X(100), int(X(18)), GOLD_SOFT, bold=False)

    if scene == "chat_type":
        typed = D["hook_question"]
        n = int(len(typed) * min(1.0, t / 2.6))
        d.rounded_rectangle((X(170), X(170), X(440), X(240)), radius=X(26), fill=GOLD + (255,))
        draw_mid(d, typed[:max(0, n)] + ("|" if int(t * 3) % 2 == 0 else ""),
                 X(305), X(205), int(X(26)), NAVY)
        draw_mid(d, D["assistant_idle"], X(230), X(300), int(X(22)), MUTED, bold=False)
        d.rounded_rectangle((X(30), X(900), X(440), X(970)), radius=X(34), fill=NAVY3 + (255,),
                            outline=GOLD + (120,), width=max(1, int(X(2))))
        draw_mid(d, D["input_hint"], X(235), X(935), int(X(20)), MUTED, bold=False)

    elif scene in ("chat_answer",):
        d.rounded_rectangle((X(160), X(160), X(440), X(230)), radius=X(26), fill=GOLD + (255,))
        draw_mid(d, D["hook_question"], X(300), X(195), int(X(22)), NAVY)
        a = ease((t - 0.6) / 0.6)
        if a > 0:
            d.rounded_rectangle((X(30), X(270), X(440), X(470)), radius=X(26),
                                fill=NAVY3 + (int(255 * a),), outline=GOLD + (int(255 * a),), width=max(1, int(X(2))))
            draw_mid(d, D["assistant_label"], X(410), X(300), int(X(18)), GOLD_SOFT, bold=True)
            num = int(D["sales_total_m"] * ease((t - 1.0) / 1.8))
            draw_mid(d, f"فروش امروز {fdig(num)} میلیون تومان", X(235), X(360),
                     int(X(24)), CREAM)
            draw_mid(d, f"{fdig(D['sales_invoices'])} فاکتور", X(235), X(410), int(X(22)), GOLD_SOFT, bold=False)
        b = ease((t - 2.4) / 0.6)
        if b > 0:
            pulse = 0.5 + 0.5 * math.sin(t * 5)
            d.rounded_rectangle((X(40), X(520), X(430), X(600)), radius=X(40),
                                fill=VIOLET + (int(255 * b),), outline=None)
            draw_mid(d, D["live_chat"], X(235), X(560), int(X(24)), NAVY if pulse < 0 else (25, 20, 60))

    elif scene == "dash":
        cards = [
            ("فروش امروز", f"{fdig(D['sales_total_m'])} میلیون", f"{fdig(D['sales_invoices'])} فاکتور", GOLD),
            ("خرید امروز", f"{fdig(D['buy_m'])} میلیون", "", TEAL),
            ("دریافت امروز", f"{fdig(D['receive_m'])} میلیون", "", TEAL),
            ("پرداخت امروز", f"{fdig(D['pay_m'])} میلیون", "", RED),
        ]
        for i, (title, val, sub, col) in enumerate(cards):
            a = ease((t - 0.15 * i) / 0.5)
            if a <= 0:
                continue
            r, c = divmod(i, 2)
            x0 = 30 + c * 210
            y0 = 130 + r * 220
            y0 = y0 + (1 - a) * 40
            d.rounded_rectangle((X(x0), X(y0), X(x0 + 200), X(y0 + 200)), radius=X(26),
                                fill=NAVY3 + (int(255 * a),), outline=col + (int(255 * a),), width=max(1, int(X(2))))
            draw_mid(d, title, X(x0 + 100), X(y0 + 40), int(X(18)), MUTED, bold=False)
            draw_mid(d, val, X(x0 + 100), X(y0 + 100), int(X(25)), CREAM)
            if sub:
                draw_mid(d, sub, X(x0 + 100), X(y0 + 150), int(X(18)), col, bold=False)
        glow = 0.5 + 0.5 * math.sin(t * 4)
        d.rounded_rectangle((X(28), X(128), X(232), X(328)), radius=X(28), outline=GOLD + (int(120 + 100 * glow),),
                            width=max(2, int(X(3))))
        d.rounded_rectangle((X(30), X(600), X(440), X(690)), radius=X(30), fill=NAVY3 + (255,))
        draw_mid(d, D["dash_footer"], X(235), X(645), int(X(20)), MUTED, bold=False)

    elif scene == "stock":
        rows = D["stock"]
        for i, row in enumerate(rows):
            a = ease((t - 0.25 * i) / 0.5)
            if a <= 0:
                continue
            y0 = 140 + i * 165 + (1 - a) * 30
            col = {"gold": GOLD, "red": RED, "teal": TEAL}[row["tone"]]
            d.rounded_rectangle((X(28), X(y0), X(442), X(y0 + 145)), radius=X(26),
                                fill=NAVY3 + (int(255 * a),), outline=col + (int(255 * a),), width=max(1, int(X(2))))
            draw_mid(d, row["name"], X(395), X(y0 + 45), int(X(24)), CREAM)
            draw_mid(d, row["qty"], X(395), X(y0 + 95), int(X(18)), MUTED, bold=False)
            d.rounded_rectangle((X(50), X(y0 + 22), X(190), X(y0 + 62)), radius=X(20), fill=col + (int(255 * a),))
            draw_mid(d, row["status"], X(120), X(y0 + 42), int(X(16)), NAVY)
        press = 0.0
        if t > 3.2:
            press = math.sin(min(1.0, (t - 3.2) / 0.5) * math.pi) * 0.04
        bw = 380 * (1 - press)
        d.rounded_rectangle((X(235 - bw / 2), X(850), X(235 + bw / 2), X(930)), radius=X(40), fill=GOLD + (255,))
        draw_mid(d, D["stock_action"], X(235), X(890), int(X(22)), NAVY)

    elif scene == "grid":
        tiles = D["sections"]
        for i, name in enumerate(tiles):
            a = ease((t - 0.2 * i) / 0.45)
            if a <= 0:
                continue
            r, c = divmod(i, 2)
            x0 = 30 + c * 210
            y0 = 130 + r * 175 + (1 - a) * 30
            d.rounded_rectangle((X(x0), X(y0), X(x0 + 200), X(y0 + 160)), radius=X(26),
                                fill=NAVY3 + (int(255 * a),), outline=GOLD + (int(170 * a),), width=max(1, int(X(2))))
            d.ellipse((X(x0 + 80), X(y0 + 22), X(x0 + 120), X(y0 + 62)), fill=GOLD + (int(255 * a),))
            draw_mid(d, name, X(x0 + 100), X(y0 + 115), int(X(22)), CREAM)

    elif scene == "end":
        a = ease(t / 0.9)
        draw_mid(d, "MEELANO", X(235), X(380), int(X(54)), GOLD + (0,))
        d.text((X(235), X(380)), "MEELANO", font=font(int(X(54)), True), fill=GOLD + (int(255 * a),), anchor="mm")
        d.text((X(235), X(440)), "Manager", font=font(int(X(26)), False), fill=CREAM + (int(255 * a),), anchor="mm")
        b = ease((t - 1.4) / 0.6)
        d.rounded_rectangle((X(60), X(560), X(410), X(640)), radius=X(40), fill=GOLD + (int(255 * b),))
        draw_mid(d, D["cta_button"], X(235), X(600), int(X(22)), NAVY)

    return sur


# ---------------------------------------------------------------- captions
def caption_image(W, H, lay, text, key, cap_w):
    """Caption as an RGBA layer. Key phrase in gold. Returns the layer and text box."""
    kind, y0, y1, box_w = lay["caption"]
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    size = 58 if kind == "center" else 44
    if kind == "center":
        cx = W / 2
        lines = wrap(text, size, box_w)
        total_h = len(lines) * size * 1.45
        top = (y0 + y1) / 2 - total_h / 2
        for i, ln in enumerate(lines):
            cy = top + i * size * 1.45 + size * 0.7
            segs = split_key(ln, key)
            draw_segments(d, segs, cx, cy, size)
    else:
        lines = wrap(text, size, box_w)
        total_h = len(lines) * size * 1.5
        top = (y0 + y1) / 2 - total_h / 2
        rx = 1820
        for i, ln in enumerate(lines):
            cy = top + i * size * 1.5 + size * 0.7
            segs = split_key(ln, key)
            draw_segments(d, segs, rx - 0, cy, size, align="right", right_edge=rx)
    return img


def split_key(line, key):
    if key and key in line:
        a, b = line.split(key, 1)
        segs = []
        if a:
            segs.append((a, CREAM))
        segs.append((key, GOLD_SOFT))
        if b:
            segs.append((b, CREAM))
        return segs
    return [(line, CREAM)]


def draw_segments(d, segs, cx, cy, size, align="center", right_edge=None):
    widths = [text_w(t, size) for t, _ in segs]
    total = sum(widths)
    if align == "center":
        x = cx + total / 2
    else:
        x = right_edge
    for (t, col), w in zip(segs, widths):
        # RTL flow: each segment is drawn with its right edge at x, then x moves left.
        d.text((x, cy), fa(t), font=font(size), fill=col + (255,), anchor="rm")
        x -= w
    return total


# ---------------------------------------------------------------- scenes
def build_timeline(scenes, voice_len):
    tl = []
    t = 0.0
    for sc in scenes:
        dur = max(sc["dur"], voice_len.get(sc["voice"], 0) + 0.65) if sc["voice"] else sc["dur"]
        tl.append(dict(sc, start=t, length=dur))
        t += dur
    return tl, t


def read_wav(path):
    with wave.open(path, "rb") as w:
        sr = w.getframerate()
        raw = w.readframes(w.getnframes())
    x = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    if sr != SR:
        idx = np.linspace(0, len(x) - 1, int(len(x) * SR / sr))
        x = np.interp(idx, np.arange(len(x)), x).astype(np.float32)
    return x


def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


CHORDS = [
    (45, [57, 60, 64, 67, 71]),
    (41, [53, 57, 60, 64]),
    (48, [55, 60, 64, 69]),
    (43, [50, 55, 59, 62]),
]
BPM = 84
BEAT = 60.0 / BPM
CHORD_LEN = BEAT * 8


def score(n, dings, typing_ticks):
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
        pad[mask] = seg * 0.03
    out += pad * (0.85 + 0.15 * np.sin(2 * math.pi * 0.09 * t))

    eighth = BEAT / 2
    e_idx = (t // eighth).astype(int)
    tm = t - e_idx * eighth
    chord_of_e = ((e_idx * eighth) // CHORD_LEN).astype(int) % len(CHORDS)
    base = np.array([c[0] for c in CHORDS])[chord_of_e]
    push = np.where((e_idx % 4) == 2, 12, 0)
    fb = 440.0 * 2 ** ((base + push - 69) / 12.0)
    bass = (np.sin(2 * math.pi * fb * t) + 0.25 * np.sin(4 * math.pi * fb * t)) * np.exp(-tm * 7.0)
    out += bass * 0.2

    kb = t % (BEAT * 2)
    kick = np.sin(2 * math.pi * (46 * kb + 60 * (1 - np.exp(-kb * 35.0)) / 35.0)) * np.exp(-kb * 16.0) * (kb < 0.5)
    out += kick * 0.42

    rng = np.random.default_rng(11)
    noise = rng.standard_normal(n).astype(np.float32)
    hat_tm = t - (t // eighth) * eighth - eighth / 2
    hat = noise * np.exp(-np.abs(hat_tm) * 90.0) * (((t // eighth) % 2) == 1)
    out += np.diff(hat, prepend=0.0).astype(np.float32) * 0.1

    for when in dings:
        s0 = int(when * SR)
        if s0 >= n:
            continue
        ln = int(0.7 * SR)
        tt = np.arange(min(ln, n - s0)) / SR
        ding = np.sin(2 * math.pi * 1568 * tt) + 0.5 * np.sin(2 * math.pi * 2093 * tt)
        out[s0:s0 + len(tt)] += (ding * np.exp(-tt * 6.0) * 0.07).astype(np.float32)

    for when in typing_ticks:
        s0 = int(when * SR)
        ln = int(0.02 * SR)
        if s0 + ln >= n:
            continue
        noise_t = rng.standard_normal(ln).astype(np.float32)
        out[s0:s0 + ln] += noise_t * np.exp(-np.arange(ln) / SR * 300) * 0.05

    fade_in = np.minimum(1.0, t / 1.0)
    fade_out = np.clip((n / SR - t) / 2.5, 0.0, 1.0)
    return out * fade_in * fade_out


def build_audio(path, timeline, total, voice_dir):
    n = int(total * SR) + SR
    voice = np.zeros(n, dtype=np.float32)
    dings, ticks = [], []
    for sc in timeline:
        if sc["voice"]:
            c = read_wav(os.path.join(voice_dir, sc["voice"]))
            s0 = int((sc["start"] + 0.25) * SR)
            voice[s0:s0 + len(c)] += c
        if sc["screen"] == "dash":
            dings.append(sc["start"] + 1.0)
        if sc["screen"] == "stock":
            dings.append(sc["start"] + 1.0)
        if sc["screen"] == "chat_type":
            ticks += [sc["start"] + 0.3 + k * 0.13 for k in range(18)]
    if any(sc["screen"] == "end" for sc in timeline):
        end = [sc for sc in timeline if sc["screen"] == "end"][0]
        dings.append(end["start"] + 1.4)
    voice = voice[:int(total * SR)]
    music = score(len(voice), dings, ticks) * 0.7
    win = int(0.08 * SR)
    env = np.convolve(np.abs(voice), np.ones(win, dtype=np.float32) / win, mode="same")
    env = np.clip(env / 0.08, 0, 1)
    sm = np.convolve(env, np.ones(int(0.05 * SR), dtype=np.float32) / int(0.05 * SR), mode="same")
    music = music * (1 - 0.7 * sm)
    mix = voice + music
    peak = float(np.max(np.abs(mix))) or 1.0
    mix = mix / max(1.0, peak * 1.08)
    pcm = (np.clip(mix, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def srt_time(s):
    h, rem = divmod(s, 3600)
    m, sec = divmod(rem, 60)
    return f"{int(h):02d}:{int(m):02d}:{sec:06.3f}".replace(".", ",")


def render(variant, scenes, name, voice_dir, out_mp4, out_srt, poster=None):
    lay = LAYOUTS["portrait" if variant.startswith("portrait") else variant.replace("35", "").replace("15", "")]
    kind = "portrait" if variant.startswith("portrait") else ("square" if variant.startswith("square") else "landscape")
    lay = LAYOUTS[kind]
    W, H = lay["W"], lay["H"]
    voice_len = {}
    for sc in scenes:
        if sc["voice"]:
            voice_len[sc["voice"]] = len(read_wav(os.path.join(voice_dir, sc["voice"]))) / SR
    timeline, total = build_timeline(scenes, voice_len)
    pw, ph, px, py = lay["phone"]
    s = pw / 470.0
    bg = background(W, H)
    cap_cache = {}
    for sc in timeline:
        cap_cache[sc["id"]] = caption_image(W, H, lay, sc["text"], sc["key"], lay["caption"][3])

    audio = os.path.join(OUT_DIR, f"_{name}.wav")
    build_audio(audio, timeline, total, voice_dir)
    with open(out_srt, "w", encoding="utf-8") as f:
        for i, sc in enumerate(timeline, 1):
            a = sc["start"] + 0.25 if sc["voice"] else sc["start"]
            b = a + max(1.0, min(sc["length"] - 0.4, voice_len.get(sc["voice"], 2.0) + 0.1))
            f.write(f"{i}\n{srt_time(a)} --> {srt_time(b)}\n{sc['text']}\n\n")

    ff = imageio_ffmpeg.get_ffmpeg_exe()
    cmd = [ff, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
           "-r", str(FPS), "-i", "-", "-i", audio, "-c:v", "libx264", "-preset", "medium", "-crf", "22",
           "-pix_fmt", "yuv420p", "-b:v", "9M", "-maxrate", "10M", "-bufsize", "16M",
           "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-movflags", "+faststart", "-t", f"{total:.2f}", out_mp4]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    poster_t = None
    for sc in timeline:
        if sc["screen"] == "dash":
            poster_t = sc["start"] + 2.0
    poster_saved = False
    for sc in timeline:
        n = int(round(sc["length"] * FPS))
        for i in range(n):
            t = i / FPS
            frame = bg.copy()
            # subtle float of the phone
            fy = py + 10 * math.sin(2 * math.pi * (sc["start"] + t) / 6.0) if kind != "landscape" else py
            ph_img = phone_surface(sc["screen"], t, s, pw, ph)
            if kind == "landscape":
                frame.alpha_composite(ph_img, (int(px), int(fy)))
            else:
                frame.alpha_composite(ph_img, (int(px), int(fy)))
            cap = cap_cache[sc["id"]]
            a = min(1.0, t / 0.45, (sc["length"] - t) / 0.45)
            if sc["voice"] and t < 0.4:
                a = min(a, t / 0.4)
            capl = cap.copy()
            capl.putalpha(cap.getchannel("A").point(lambda v, a=max(0.0, a): int(v * a)))
            frame.alpha_composite(capl)
            bar = Image.new("RGBA", (max(1, int(W * (sc["start"] + t) / total)), 6), GOLD + (255,))
            frame.alpha_composite(bar, (0, H - 6))
            if poster_t is not None and not poster_saved and abs((sc["start"] + t) - poster_t) < 0.5 / FPS:
                if poster:
                    frame.convert("RGB").save(poster, quality=95)
                poster_saved = True
            proc.stdin.write(frame.convert("RGB").tobytes())
    proc.stdin.close()
    if proc.wait() != 0:
        raise SystemExit("ffmpeg failed")
    print(variant, "total", round(total, 2), "->", out_mp4)


SCENES_FULL = CFG["scenes_full"]
SCENES_SHORT = CFG["scenes_short"]

if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    vdir = os.path.join(BASE, "voice")
    variant = sys.argv[1]
    if variant == "portrait35":
        render("portrait", SCENES_FULL, "p35", vdir, os.path.join(OUT_DIR, "meelano-teaser-v4-9x16-35s.mp4"),
               os.path.join(OUT_DIR, "meelano-teaser-v4.srt"), poster=os.path.join(OUT_DIR, "poster-9x16.jpg"))
    elif variant == "portrait15":
        render("portrait", SCENES_SHORT, "p15", vdir, os.path.join(OUT_DIR, "meelano-teaser-v4-9x16-15s.mp4"),
               os.path.join(OUT_DIR, "meelano-teaser-v4-15s.srt"))
    elif variant == "square35":
        render("square", SCENES_FULL, "s35", vdir, os.path.join(OUT_DIR, "meelano-teaser-v4-4x5-35s.mp4"),
               os.path.join(OUT_DIR, "meelano-teaser-v4-4x5.srt"))
    elif variant == "landscape35":
        render("landscape", SCENES_FULL, "l35", vdir, os.path.join(OUT_DIR, "meelano-teaser-v4-16x9-35s.mp4"),
               os.path.join(OUT_DIR, "meelano-teaser-v4-16x9.srt"))
    else:
        raise SystemExit("unknown variant")
