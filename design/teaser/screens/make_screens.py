"""Illustrative app screens (design mockups with sample figures, not device captures)."""
import math, os
from arabic_reshaper import reshape
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFont

FONT_DIR = "/home/user/Testt/manager/app/src/main/assets/fonts"
NAVY = (15, 23, 42); NAVY2 = (22, 33, 58); NAVY3 = (30, 43, 71)
GOLD = (212, 175, 55); GOLD_SOFT = (240, 217, 140)
CREAM = (248, 244, 232); MUTED = (158, 170, 196)
W, H = 1080, 1920
_fonts = {}

def font(size, bold=False):
    k = (size, bold)
    if k not in _fonts:
        name = "Vazirmatn-Bold.ttf" if bold else "Vazirmatn-Regular.ttf"
        _fonts[k] = ImageFont.truetype(os.path.join(FONT_DIR, name), size)
    return _fonts[k]

def fa(text):
    return get_display(reshape(text))

def text_w(text, size, bold=False):
    bb = font(size, bold).getbbox(fa(text))
    return bb[2] - bb[0]

def layer():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))

def draw_text(base, text, cx, cy, size, color, alpha=1.0, bold=False):
    if alpha <= 0:
        return
    lay = layer(); d = ImageDraw.Draw(lay); f = font(size, bold); s = fa(text)
    bb = d.textbbox((0, 0), s, font=f)
    d.text((cx - (bb[2] + bb[0]) / 2, cy - (bb[3] + bb[1]) / 2), s, font=f,
           fill=tuple(color) + (int(255 * alpha),))
    base.alpha_composite(lay)

def wrap(text, size, max_w, bold=False):
    lines, cur = [], ""
    for w in text.split():
        cand = (cur + " " + w).strip()
        if text_w(cand, size, bold) <= max_w:
            cur = cand
        else:
            lines.append(cur); cur = w
    if cur:
        lines.append(cur)
    return lines

def rounded(base, box, radius, fill=None, outline=None, width=2, alpha=1.0):
    lay = layer(); d = ImageDraw.Draw(lay)
    a = int(255 * max(0.0, min(1.0, alpha)))
    d.rounded_rectangle(box, radius=radius,
                        fill=(tuple(fill) + (a,)) if fill else None,
                        outline=(tuple(outline) + (a,)) if outline else None, width=width)
    base.alpha_composite(lay)

def background(t=0):
    img = Image.new("RGBA", (W, H), NAVY + (255,))
    glow = layer(); gd = ImageDraw.Draw(glow)
    for r in range(900, 0, -30):
        gd.ellipse((W - 200 - r, -220 - r, W - 200 + r, -220 + r), fill=GOLD + (int(9 * (1 - r / 900)),))
    img.alpha_composite(glow)
    return img


OUT = os.path.dirname(os.path.abspath(__file__))
W, H = W, H

def chip(img, text, cx, cy, w, gold=False, alpha=1.0):
    rounded(img, (cx - w / 2, cy - 34, cx + w / 2, cy + 34), 34,
              fill=GOLD if gold else NAVY3, outline=None if gold else GOLD, width=2, alpha=alpha)
    draw_text(img, text, cx, cy, 34, NAVY if gold else CREAM, alpha, bold=gold)

def bubble(img, text, x0, x1, y, mine, size=38):
    lines = wrap(text, size, x1 - x0 - 60)
    h = 40 + len(lines) * 56 + 20
    fill = GOLD if mine else NAVY3
    rounded(img, (x0, y, x1, y + h), 34, fill=fill, outline=None if mine else GOLD, width=2)
    for i, ln in enumerate(lines):
        draw_text(img, ln, (x0 + x1) / 2, y + 40 + i * 56 + 28, size, NAVY if mine else CREAM, 1, bold=mine)
    return y + h

def voice_screen():
    img = background(0)
    draw_text(img, "دستیار صوتی", W / 2, 150, 60, GOLD, 1, bold=True)
    draw_text(img, "آتیران · همیشه آماده", W / 2, 225, 32, MUTED)
    y = bubble(img, "فروش امروز چقدر بود؟", 430, 1000, 380, mine=True)
    y = bubble(img, "فروش امروز ۴۸۲ میلیون ریال است. نسبت به دیروز ۱۲ درصد رشد داشته است.",
               80, 960, y + 40, mine=False)
    # waveform (voice reply being spoken)
    cx, cy = W / 2, 1020
    for i in range(38):
        amp = 30 + 90 * abs(math.sin(i * 0.55)) * (0.6 + 0.4 * math.sin(i * 0.17))
        x = 130 + i * 22
        rounded(img, (x, cy - amp, x + 12, cy + amp), 6, fill=GOLD, alpha=0.9)
    draw_text(img, "در حال پخش پاسخ", W / 2, 1180, 34, MUTED)
    # suggestion chips, centered per row so nothing overlaps
    rows = [["بدهکارها کی‌اند؟", "چک‌های نزدیک سررسید"], ["موجودی انبار", "دریافت امروز"]]
    y = 1290
    for row in rows:
        widths = [text_w(t, 34) + 70 for t in row]
        total = sum(widths) + 24 * (len(row) - 1)
        x = W / 2 - total / 2
        for t, w in zip(row, widths):
            chip(img, t, x + w / 2, y, w)
            x += w + 24
        y += 96
    rounded(img, (W / 2 - 110, 1520, W / 2 + 110, 1600), 40, fill=GOLD)
    draw_text(img, "بپرسید…", W / 2, 1560, 40, NAVY, 1, bold=True)
    chip(img, "پخش دوباره", W / 2, 1700, 300)
    return img.convert("RGB")

def intro_screen():
    img = background(0)
    draw_text(img, "MEELANO", W / 2, 620, 170, GOLD, 1, bold=True)
    draw_text(img, "Manager", W / 2, 760, 74, CREAM, 1)
    draw_text(img, "مدیریت فروشگاه، در جیب شما", W / 2, 860, 50, CREAM, 1)
    feats = [("اتصال مستقیم به آتیران", 1010), ("داشبورد مدیریتی زنده", 1110),
             ("دستیار صوتی فارسی", 1210), ("هشدار هوشمند موجودی", 1310)]
    for text, y in feats:
        rounded(img, (120, y - 45, 960, y + 45), 40, fill=NAVY3, outline=GOLD, width=2)
        draw_text(img, text, W / 2, y, 44, CREAM, 1, bold=True)
    rounded(img, (190, 1490, 890, 1620), 65, fill=GOLD)
    draw_text(img, "شروع با نسخه نمایشی", W / 2, 1555, 44, NAVY, 1, bold=True)
    return img.convert("RGB")

def reports_screen():
    img = background(0)
    draw_text(img, "گزارش‌ها", W / 2, 180, 60, GOLD, 1, bold=True)
    draw_text(img, "فروش، خرید، دریافت و پرداخت", W / 2, 255, 32, MUTED)
    items = [("فروش ماهانه", "۴٬۱۲۰ میلیون ریال", [0.3, 0.45, 0.4, 0.62, 0.58, 0.8, 0.9]),
             ("بدهکاران", "۳۶۵ میلیون ریال", [0.8, 0.7, 0.72, 0.6, 0.55, 0.5, 0.42]),
             ("موجودی انبار", "۱٬۲۴۰ قلم", [0.5, 0.55, 0.5, 0.48, 0.5, 0.45, 0.46])]
    for i, (title, val, spark) in enumerate(items):
        y = 340 + i * 440
        rounded(img, (90, y, 990, y + 380), 36, fill=NAVY3, outline=GOLD, width=2)
        draw_text(img, title, W / 2, y + 70, 42, MUTED, 1)
        draw_text(img, val, W / 2, y + 150, 66, CREAM, 1, bold=True)
        lay = layer(); d = ImageDraw.Draw(lay)
        pts = [(150 + k * 780 / (len(spark) - 1), y + 350 - v * 130) for k, v in enumerate(spark)]
        d.line(pts, fill=GOLD + (255,), width=8, joint="curve")
        img.alpha_composite(lay)
    return img.convert("RGB")

if __name__ == "__main__":
    voice_screen().save(os.path.join(OUT, "screen-1-voice-assistant.png"))
    intro_screen().save(os.path.join(OUT, "screen-2-intro.png"))
    reports_screen().save(os.path.join(OUT, "screen-3-reports.png"))
    print("ok")
