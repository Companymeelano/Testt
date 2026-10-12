#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مرجع و آزمون موتور بارکد «پخش درخشان» (MeelanoBarcode.java).

همان منطق کد جاوا در این فایل به پایتون نوشته شده و با دو روش آزمون می‌شود:
۱) تطبیق ساختار با کتابخانهٔ مرجع python-barcode (Code39 و EAN-13 ماژول‌به‌ماژول).
۲) کدگشای مستقل: هر خروجی Code128 دوباره خوانده، رقم کنترلی بررسی و متن بازیابی می‌شود.

اجرا:  python3 tools/barcode_reference_check.py
"""
from __future__ import annotations

import random
import string
import sys

try:
    from barcode.charsets import code128 as C128_T, code39 as C39_T, ean as EAN_T
    from barcode import codex
except Exception:  # pragma: no cover
    print('کتابخانهٔ python-barcode لازم است: pip install python-barcode')
    sys.exit(2)

C128 = list(C128_T.CODES)
C128_STOP = C128_T.STOP + '11'  # ۱۱ ماژول STOP + نوار پایانی ۲ ماژولی
C128_START_B = 104
C128_START_C = 105
C128_TO_C = 99
C128_TO_B = 100

C39 = list(C39_T.CODES)
C39_ALPHABET = ''.join(C39_T.REF)
C39_EDGE = C39_T.EDGE
C39_GAP = C39_T.MIDDLE

EAN_A = list(EAN_T.CODES['A'])
EAN_B = list(EAN_T.CODES['B'])
EAN_C = list(EAN_T.CODES['C'])
EAN_PARITY = list(EAN_T.LEFT_PATTERN)
EAN_EDGE = EAN_T.EDGE
EAN_MIDDLE = EAN_T.MIDDLE


# ---------------------------------------------------------------- رمزگذاری (عیناً مثل جاوا)
def code128(data: str) -> str:
    if not data:
        return ''
    values: list[int] = []
    i = 0
    run = 0
    while i + run < len(data) and data[i + run].isdigit():
        run += 1
    if len(data) >= 4 and run == len(data) and len(data) % 2 == 0:
        values.append(C128_START_C)
        mode_c = True
    elif run >= 4 and run % 2 == 0:
        values.append(C128_START_C)
        mode_c = True
    else:
        values.append(C128_START_B)
        mode_c = False
    while i < len(data):
        if mode_c:
            if i + 1 < len(data) and data[i].isdigit() and data[i + 1].isdigit():
                values.append(int(data[i:i + 2]))
                i += 2
            else:
                values.append(C128_TO_B)
                mode_c = False
        else:
            j = i
            while j < len(data) and data[j].isdigit():
                j += 1
            digits = j - i
            if digits >= 6 and digits % 2 == 0:
                values.append(C128_TO_C)
                mode_c = True
            else:
                c = data[i]
                if not (32 <= ord(c) <= 126):
                    raise ValueError('کاراکتر غیرمجاز در Code128')
                values.append(ord(c) - 32)
                i += 1
    check = (values[0] + sum(v * k for k, v in enumerate(values[1:], start=1))) % 103
    return ''.join(C128[v] for v in values) + C128[check] + C128_STOP


def code39(data: str, with_checksum: bool = False) -> str:
    s = data.upper()
    body = ''
    for ch in s:
        if ch not in C39_ALPHABET:
            raise ValueError('کاراکتر غیرمجاز در Code39: ' + ch)
        body += ch
    if with_checksum:
        body += C39_ALPHABET[sum(C39_ALPHABET.index(c) for c in body) % 43]
    out = C39_EDGE + C39_GAP
    for ch in body:
        out += C39[C39_ALPHABET.index(ch)] + C39_GAP
    return out + C39_EDGE


def ean13_check(twelve: str) -> int:
    t = twelve[:12]
    total = 0
    for i, ch in enumerate(t):
        d = ord(ch) - 48
        total += d if i % 2 == 0 else d * 3
    return (10 - (total % 10)) % 10


def ean13(code: str) -> str:
    t = code.strip()
    if len(t) == 12:
        t += str(ean13_check(t))
    if len(t) != 13 or not t.isdigit():
        raise ValueError('EAN-13 باید ۱۳ رقم باشد')
    parity = EAN_PARITY[int(t[0])]
    out = EAN_EDGE
    for i in range(1, 7):
        d = int(t[i])
        out += EAN_A[d] if parity[i - 1] == 'A' else EAN_B[d]
    out += EAN_MIDDLE
    for i in range(7, 13):
        out += EAN_C[int(t[i])]
    return out + EAN_EDGE


# ---------------------------------------------------------------- کدگشاها
def decode_code128(modules: str) -> str:
    """کدگشای مستقل: الگوها را برمی‌گرداند، رقم کنترلی را بررسی و متن را بازیابی می‌کند."""
    if len(modules) < 11 * 3 + 13 or not modules.endswith(C128_STOP):
        raise ValueError('طول/پایان Code128 نامعتبر')
    body = modules[: -len(C128_STOP)]
    if len(body) % 11 != 0:
        raise ValueError('طول بدنهٔ Code128 مضرب ۱۱ نیست')
    values = []
    for i in range(0, len(body), 11):
        chunk = body[i:i + 11]
        if chunk not in C128:
            raise ValueError('الگوی ناشناخته: ' + chunk)
        values.append(C128.index(chunk))
    if len(values) < 3:
        raise ValueError('Code128 خیلی کوتاه')
    expect = (values[0] + sum(v * k for k, v in enumerate(values[1:-1], start=1))) % 103
    if expect != values[-1]:
        raise ValueError('رقم کنترلی Code128 نادرست')
    out = ''
    mode = None
    for v in values[:-1]:
        if v == C128_START_B:
            mode = 'B'
        elif v == C128_START_C:
            mode = 'C'
        elif mode == 'B' and v == 99:          # CODE C
            mode = 'C'
        elif mode == 'C' and v == 100:         # CODE B
            mode = 'B'
        elif mode == 'C':
            # توجه: در مجموعهٔ C مقدار ۹۹ یعنی رقم‌های «۹۹»، نه سوییچ مجموعه
            out += '%02d' % v
        else:
            if v > 95:
                raise ValueError('مقدار ناشناخته در Code128-B')
            out += chr(v + 32)
    return out


def decode_code39(modules: str) -> str:
    """کدگشای Code39 بر پایهٔ پهنای عناصر (۹ عنصر برای هر کاراکتر + یک فاصلهٔ جداکننده)."""
    runs = []
    current = modules[0]
    count = 0
    for ch in modules:
        if ch == current:
            count += 1
        else:
            runs.append((current, count))
            current, count = ch, 1
    runs.append((current, count))
    # ساختار: EDGE + فاصله + (کاراکتر + فاصله)… + EDGE  → تعداد عناصر = ۹ + ۱ + n*۱۰ + ۹
    if len(runs) < 20 or (len(runs) - 19) % 10 != 0:
        raise ValueError('ساختار Code39 نامعتبر: ' + str(len(runs)))
    def pattern_of(group):
        pattern = ''
        for k, (ch, width) in enumerate(group):
            if width not in (1, 3):
                raise ValueError('پهنای غیرمجاز در Code39')
            if (k % 2 == 0) != (ch == '1'):
                raise ValueError('ترتیب میله/فاصله در Code39 خراب است')
            pattern += ch * width
        return pattern
    if pattern_of(runs[:9]) != C39_EDGE:
        raise ValueError('شروع Code39 با ستاره نیست')
    if runs[9] != ('0', 1):
        raise ValueError('فاصلهٔ پس از ستارهٔ شروع نامعتبر')
    runs = runs[10:]
    if len(runs) % 10 != 9:
        raise ValueError('طول بدنهٔ Code39 نامعتبر')
    if pattern_of(runs[-9:]) != C39_EDGE:
        raise ValueError('پایان Code39 با ستاره نیست')
    runs = runs[:-9]
    while runs and runs[-1] == ('0', 1):
        runs.pop()
    if len(runs) % 10 != 9:
        raise ValueError('ساختار کاراکترهای Code39 نامعتبر')
    out = ''
    index = 0
    while index + 9 <= len(runs):
        group = runs[index:index + 9]
        pattern = ''
        for k, (ch, width) in enumerate(group):
            if width not in (1, 3):
                raise ValueError('پهنای غیرمجاز در Code39')
            bar = (k % 2 == 0)
            if bar != (ch == '1'):
                raise ValueError('ترتیب میله/فاصله در Code39 خراب است')
            pattern += ch * width
        if pattern not in C39:
            raise ValueError('الگوی ناشناختهٔ Code39: ' + pattern)
        out += C39_ALPHABET[C39.index(pattern)]
        index += 9
        if index < len(runs):
            gap = runs[index]
            if gap != ('0', 1):
                raise ValueError('فاصلهٔ جداکنندهٔ Code39 نامعتبر')
            index += 1
    return out


def decode_ean13(modules: str) -> str:
    if not modules.startswith(EAN_EDGE) or not modules.endswith(EAN_EDGE):
        raise ValueError('قاب EAN نادرست')
    body = modules[3:-3]
    left = body[:6 * 7]
    mid = body[6 * 7:6 * 7 + len(EAN_MIDDLE)]
    right = body[6 * 7 + len(EAN_MIDDLE):]
    if mid != EAN_MIDDLE:
        raise ValueError('میانهٔ EAN نادرست')
    if len(right) != 6 * 7:
        raise ValueError('طول بخش راست EAN نادرست')
    parity = ''
    first_digit = None
    for i in range(6):
        chunk = left[i * 7:(i + 1) * 7]
        if chunk in EAN_A:
            parity += 'A'
            d = EAN_A.index(chunk)
        elif chunk in EAN_B:
            parity += 'B'
            d = EAN_B.index(chunk)
        else:
            raise ValueError('الگوی چپ EAN ناشناخته')
        if i == 0:
            first_digit = d
    left_digits = ''
    for i in range(6):
        chunk = left[i * 7:(i + 1) * 7]
        if chunk in EAN_A:
            left_digits += str(EAN_A.index(chunk))
        elif chunk in EAN_B:
            left_digits += str(EAN_B.index(chunk))
        else:
            raise ValueError('الگوی چپ EAN ناشناخته')
    right_digits = ''
    for i in range(6):
        chunk = right[i * 7:(i + 1) * 7]
        if chunk not in EAN_C:
            raise ValueError('الگوی راست EAN ناشناخته')
        right_digits += str(EAN_C.index(chunk))
    first = EAN_PARITY.index(parity)
    return f'{first}{left_digits}{right_digits}'


# ---------------------------------------------------------------- آزمون‌ها
def reference_code39(data: str) -> str:
    return codex.Code39(data, add_checksum=False).build()[0]


def reference_ean13(data12: str) -> str:
    from barcode import ean as ean_mod
    obj = ean_mod.EuropeanArticleNumber13(data12)
    return obj.build()[0]


def main() -> int:
    random.seed(1405)
    failures = 0
    checks = 0

    # ۱) Code39 در مقابل کتابخانهٔ مرجع
    samples = ['MEELANO', '1405', 'KA-1002', 'ABC123', 'X']
    for s in samples:
        checks += 1
        if code39(s) != reference_code39(s):
            failures += 1
            print('❌ Code39 با مرجع یکی نیست:', s)

    # ۲) EAN-13 در مقابل کتابخانهٔ مرجع
    for s in ['590123412345', '626010100001', '123456789012', '400638133393']:
        checks += 1
        if ean13(s) != reference_ean13(s):
            failures += 1
            print('❌ EAN13 با مرجع یکی نیست:', s)
        checks += 1
        if decode_ean13(ean13(s)) != s[:12] + str(ean13_check(s)):
            failures += 1
            print('❌ بازخوانی EAN13 اشتباه:', s)

    # ۳) رفت‌وبرگشت Code128 (رمزگذاری ← کدگشایی)
    alphabet = string.ascii_letters + string.digits + '-_. /:+'
    for _ in range(400):
        n = random.randint(1, 28)
        text = ''.join(random.choice(alphabet) for _ in range(n))
        checks += 1
        try:
            if decode_code128(code128(text)) != text:
                failures += 1
                print('❌ Code128 رفت‌وبرگشت:', repr(text))
        except Exception as exc:
            failures += 1
            print('❌ Code128 خطا:', repr(text), exc)

    # ۴) رفت‌وبرگشت عددی (مجموعهٔ C)
    for _ in range(200):
        n = random.randint(2, 24)
        text = ''.join(random.choice(string.digits) for _ in range(n))
        if len(text) >= 4 and len(text) % 2 == 0 or len(text) >= 6:
            checks += 1
            try:
                if decode_code128(code128(text)) != text:
                    failures += 1
                    print('❌ Code128 عددی:', repr(text))
            except Exception as exc:
                failures += 1
                print('❌ Code128 عددی خطا:', repr(text), exc)

    # ۵) رفت‌وبرگشت Code39
    for _ in range(100):
        text = ''.join(random.choice('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-. ') for _ in range(random.randint(1, 14)))
        checks += 1
        try:
            if decode_code39(code39(text)).rstrip() != text.rstrip():
                failures += 1
                print('❌ Code39 رفت‌وبرگشت:', repr(text))
        except Exception as exc:
            failures += 1
            print('❌ Code39 خطا:', repr(text), exc)

    print(f'آزمون‌ها: {checks} — ناموفق: {failures}')
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
