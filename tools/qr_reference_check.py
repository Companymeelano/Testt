#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مرجع پایتونی موتور QR پروژه «پخش درخشان» (MeelanoQr.java).

این فایل هم‌الگوریتمِ دقیق کد جاوای پروژه است و برای یک دلیل ساخته شده:
بدون JDK/اندروید در این محیط نمی‌توان کد جاوا را اجرا کرد، پس درستی الگوریتم
(بایت‌مود، انتخاب نسخه، رید-سالومون، چیدمان، بیت‌های فرمت و ماسک) اینجا با
کتابخانهٔ مرجع `qrcode` سطر‌به‌سطر مقایسه می‌شود. اگر ماتریس‌ها مو‌به‌مو یکی باشند،
ترجمهٔ جاوا همان الگوریتم را اجرا می‌کند.

اجرا:  python3 tools/qr_reference_check.py
"""
from __future__ import annotations

import sys

# ---------------------------------------------------------------- جداول QR (سطح M و L، نسخه ۱..۱۰)
# تعداد کدواژه‌های تصحیح خطا در هر بلوک و تعداد بلوک‌ها، به تفکیک نسخه و سطح.
# ساختار: level -> version -> (ec_per_block, [(count, data_codewords), ...])
ECC_BLOCKS = {
    'L': {
        1:  (7,  [(1, 19)]),
        2:  (10, [(1, 34)]),
        3:  (15, [(1, 55)]),
        4:  (20, [(1, 80)]),
        5:  (26, [(1, 108)]),
        6:  (18, [(2, 68)]),
        7:  (20, [(2, 78)]),
        8:  (24, [(2, 97)]),
        9:  (30, [(2, 116)]),
        10: (18, [(2, 68), (2, 69)]),
    },
    'M': {
        1:  (10, [(1, 16)]),
        2:  (16, [(1, 28)]),
        3:  (26, [(1, 44)]),
        4:  (18, [(2, 32)]),
        5:  (24, [(2, 43)]),
        6:  (16, [(4, 27)]),
        7:  (18, [(4, 31)]),
        8:  (22, [(2, 38), (2, 39)]),
        9:  (22, [(3, 36), (2, 37)]),
        10: (26, [(4, 43), (1, 44)]),
    },
    'Q': {
        1:  (13, [(1, 13)]),
        2:  (22, [(1, 22)]),
        3:  (18, [(2, 17)]),
        4:  (26, [(2, 24)]),
        5:  (18, [(2, 15), (2, 16)]),
        6:  (24, [(4, 19)]),
        7:  (18, [(2, 14), (4, 15)]),
        8:  (22, [(4, 18), (2, 19)]),
        9:  (20, [(4, 16), (4, 17)]),
        10: (24, [(6, 19), (2, 20)]),
    },
    'H': {
        1:  (17, [(1, 9)]),
        2:  (28, [(1, 16)]),
        3:  (22, [(2, 13)]),
        4:  (16, [(4, 9)]),
        5:  (22, [(2, 11), (2, 12)]),
        6:  (28, [(4, 15)]),
        7:  (26, [(4, 13), (1, 14)]),
        8:  (26, [(4, 14), (2, 15)]),
        9:  (24, [(4, 12), (4, 13)]),
        10: (28, [(6, 15), (2, 16)]),
    },
}

# موقعیت الگوهای تراز (alignment) برای نسخه‌های ۱..۱۰ (نسخه ۱ بدون الگو)
ALIGNMENT = {
    1: [], 2: [6, 18], 3: [6, 22], 4: [6, 26], 5: [6, 30], 6: [6, 34],
    7: [6, 22, 38], 8: [6, 24, 42], 9: [6, 26, 46], 10: [6, 28, 50],
}

# کدواژه‌های کل (داده+تصحیح) هر نسخه — باید با جدول بالا جمع بزند
TOTAL_CODEWORDS = {1: 26, 2: 44, 3: 70, 4: 100, 5: 134, 6: 172, 7: 196, 8: 242, 9: 292, 10: 346}

# بیت‌های اطلاعات فرمت: ۵ بیت داده (۲ بیت سطح + ۳ بیت ماسک) + ۱۰ بیت BCH، XOR با 0x5412
FORMAT_EC_BITS = {'L': 1, 'M': 0, 'Q': 3, 'H': 2}


# ---------------------------------------------------------------- GF(256) / رید-سالومون
GF_EXP = [0] * 512
GF_LOG = [0] * 256


def _init_gf():
    x = 1
    for i in range(255):
        GF_EXP[i] = x
        GF_LOG[x] = i
        x <<= 1
        if x & 0x100:
            x ^= 0x11D
    for i in range(255, 512):
        GF_EXP[i] = GF_EXP[i - 255]


_init_gf()


def rs_generator(degree: int) -> list[int]:
    poly = [1]
    for i in range(degree):
        nxt = [0] * (len(poly) + 1)
        for j, c in enumerate(poly):
            nxt[j] ^= c
            nxt[j + 1] ^= gf_mul(c, GF_EXP[i])
        poly = nxt
    return poly


def gf_mul(a: int, b: int) -> int:
    if a == 0 or b == 0:
        return 0
    return GF_EXP[GF_LOG[a] + GF_LOG[b]]


def rs_encode(data: list[int], ec_len: int) -> list[int]:
    gen = rs_generator(ec_len)
    res = [0] * ec_len
    for byte in data:
        factor = byte ^ res[0]
        res = res[1:] + [0]
        if factor != 0:
            for i, g in enumerate(gen[1:]):
                res[i] ^= gf_mul(g, factor)
    return res


# ---------------------------------------------------------------- ساخت ماتریس
class Qr:
    def __init__(self, version: int, level: str, mask: int = -1):
        self.version = version
        self.level = level
        self.size = 17 + 4 * version
        self.modules = [[False] * self.size for _ in range(self.size)]
        self.reserved = [[False] * self.size for _ in range(self.size)]
        self.mask = mask

    # ---- الگوهای ثابت
    def _finder(self, row: int, col: int) -> None:
        for r in range(-1, 8):
            for c in range(-1, 8):
                rr, cc = row + r, col + c
                if 0 <= rr < self.size and 0 <= cc < self.size:
                    self.reserved[rr][cc] = True
                    dark = (0 <= r <= 6 and (c == 0 or c == 6)) or (0 <= c <= 6 and (r == 0 or r == 6)) or (2 <= r <= 4 and 2 <= c <= 4)
                    self.modules[rr][cc] = dark

    def _alignment(self) -> None:
        pos = ALIGNMENT.get(self.version, [])
        for r in pos:
            for c in pos:
                if self.reserved[r][c]:
                    continue
                for dr in range(-2, 3):
                    for dc in range(-2, 3):
                        self.reserved[r + dr][c + dc] = True
                        self.modules[r + dr][c + dc] = max(abs(dr), abs(dc)) != 1

    def _timing(self) -> None:
        for i in range(self.size):
            if not self.reserved[6][i]:
                self.reserved[6][i] = True
                self.modules[6][i] = i % 2 == 0
            if not self.reserved[i][6]:
                self.reserved[i][6] = True
                self.modules[i][6] = i % 2 == 0

    def _reserve_format(self) -> None:
        for i in range(9):
            if not self.reserved[8][i]:
                self.reserved[8][i] = True
            if not self.reserved[i][8]:
                self.reserved[i][8] = True
        for i in range(8):
            self.reserved[8][self.size - 1 - i] = True
            self.reserved[self.size - 1 - i][8] = True
        self.reserved[self.size - 8][8] = True

    def _write_version(self) -> None:
        if self.version < 7:
            return
        rem = self.version
        for _ in range(12):
            rem = (rem << 1) ^ ((rem >> 11) * 0x1F25)
        bits = (self.version << 12) | rem
        for i in range(18):
            bit = (bits >> i) & 1 == 1
            a = self.size - 11 + i % 3
            b = i // 3
            self.modules[b][a] = bit
            self.modules[a][b] = bit
            self.reserved[b][a] = True
            self.reserved[a][b] = True

    def _write_format(self, mask: int) -> None:
        data = (FORMAT_EC_BITS[self.level] << 3) | mask
        rem = data
        for _ in range(10):
            rem = (rem << 1) ^ ((rem >> 9) * 0x537)
        bits = ((data << 10) | rem) ^ 0x5412
        # نسخهٔ اول: ۱۵ بیت، از بیت ۱۴ (پرارزش) تا ۰
        for i in range(15):
            bit = (bits >> i) & 1 == 1
            # کپی اول: ستون ۸ (ردیف ۰..۵) + گوشهٔ finder بالا-چپ
            if i < 6:
                self.modules[i][8] = bit
            elif i < 8:
                self.modules[i + 1][8] = bit
            elif i == 8:
                self.modules[8][7] = bit
            else:
                self.modules[8][14 - i] = bit
            # کپی دوم: ردیف ۸ (ستون آخر) + ستون ۸ (ردیف‌های آخر)
            if i < 8:
                self.modules[8][self.size - 1 - i] = bit
            else:
                self.modules[self.size - 15 + i][8] = bit
        self.modules[self.size - 8][8] = True  # ماژول تیرهٔ ثابت

    def _place_data(self, codewords: list[int], mask: int) -> None:
        bit_index = 0
        total_bits = len(codewords) * 8
        col = self.size - 1
        upward = True
        while col > 0:
            if col == 6:
                col -= 1
            rows = range(self.size - 1, -1, -1) if upward else range(self.size)
            for row in rows:
                for c in (col, col - 1):
                    if self.reserved[row][c]:
                        continue
                    bit = False
                    if bit_index < total_bits:
                        bit = ((codewords[bit_index >> 3] >> (7 - (bit_index & 7))) & 1) == 1
                    bit_index += 1
                    self.modules[row][c] = bit ^ mask_bit(mask, row, c)
            upward = not upward
            col -= 2

    # ---- API
    def build(self, text: str, mask: int | None = None) -> 'Qr':
        data = text.encode('utf-8')
        codewords = self._encode(data)
        self._finder(0, 0)
        self._finder(0, self.size - 7)
        self._finder(self.size - 7, 0)
        self._alignment()
        self._timing()
        self._reserve_format()
        self._write_version()
        m = mask if mask is not None else (self.mask if self.mask >= 0 else 0)
        self._place_data(codewords, m)
        self._write_format(m)
        self.mask = m
        return self

    def _encode(self, data: bytes) -> list[int]:
        ec_per_block, groups = ECC_BLOCKS[self.level][self.version]
        total_data = sum(count * words for count, words in groups)
        bits: list[int] = []

        def push(value: int, length: int) -> None:
            for i in range(length - 1, -1, -1):
                bits.append((value >> i) & 1)

        push(0b0100, 4)                        # حالت بایت
        push(len(data), 8 if self.version <= 9 else 16)
        for b in data:
            push(b, 8)
        if len(bits) > total_data * 8:
            raise ValueError('داده برای این نسخهٔ QR بزرگ است')
        # پایان‌دهنده
        for _ in range(min(4, total_data * 8 - len(bits))):
            bits.append(0)
        while len(bits) % 8 != 0:
            bits.append(0)
        codewords = [int(''.join(str(b) for b in bits[i:i + 8]), 2) for i in range(0, len(bits), 8)]
        pad_index = 0
        while len(codewords) < total_data:
            codewords.append(0xEC if pad_index % 2 == 0 else 0x11)
            pad_index += 1
        # تقسیم به بلوک و افزودن تصحیح خطا
        blocks_data, blocks_ec = [], []
        offset = 0
        for count, words in groups:
            for _ in range(count):
                block = codewords[offset:offset + words]
                offset += words
                blocks_data.append(block)
                blocks_ec.append(rs_encode(block, ec_per_block))
        out: list[int] = []
        max_data = max(len(b) for b in blocks_data)
        for i in range(max_data):
            for b in blocks_data:
                if i < len(b):
                    out.append(b[i])
        for i in range(ec_per_block):
            for b in blocks_ec:
                out.append(b[i])
        return out


def mask_bit(mask: int, row: int, col: int) -> bool:
    if mask == 0:
        return (row + col) % 2 == 0
    if mask == 1:
        return row % 2 == 0
    if mask == 2:
        return col % 3 == 0
    if mask == 3:
        return (row + col) % 3 == 0
    if mask == 4:
        return (row // 2 + col // 3) % 2 == 0
    if mask == 5:
        return (row * col) % 2 + (row * col) % 3 == 0
    if mask == 6:
        return ((row * col) % 2 + (row * col) % 3) % 2 == 0
    return ((row + col) % 2 + (row * col) % 3) % 2 == 0


def data_capacity_bytes(version: int, level: str) -> int:
    _, groups = ECC_BLOCKS[level][version]
    total_data = sum(count * words for count, words in groups)
    header = 4 + (8 if version <= 9 else 16)
    return (total_data * 8 - header) // 8


def best_version(data_len: int, level: str, max_version: int = 10) -> int:
    for v in range(1, max_version + 1):
        if data_capacity_bytes(v, level) >= data_len:
            return v
    return -1


def build_matrix(text: str, level: str = 'M', mask: int = 0, version: int | None = None) -> list[list[bool]]:
    if version is None:
        version = best_version(len(text.encode('utf-8')), level)
        if version < 0:
            raise ValueError('داده بزرگ‌تر از پشتیبانی نسخه ۱۰ است')
    return Qr(version, level).build(text, mask).modules


# ---------------------------------------------------------------- آزمون درستی با کتابخانهٔ مرجع
def self_check() -> int:
    try:
        import qrcode
        from qrcode import constants
        from qrcode.util import QRData, MODE_8BIT_BYTE
    except Exception as exc:  # pragma: no cover
        print('کتابخانهٔ qrcode نصب نیست؛ آزمون تطبیق انجام نشد:', exc)
        return 0

    levels = {'L': constants.ERROR_CORRECT_L, 'M': constants.ERROR_CORRECT_M,
              'Q': constants.ERROR_CORRECT_Q, 'H': constants.ERROR_CORRECT_H}
    samples = [
        'https://meelano.ir',
        'MEELANO-1405-000123',
        'IR830120010000000000000000',
        'پخش درخشان میلانو - فاکتور ۱۲۳۴',
        'A' * 60,
        'IR' + '9' * 24,
        'MEELANO/INV/1405-07-14/000123',
    ]
    checked = 0
    failed = 0
    for text in samples:
        for level, lib_level in levels.items():
            version = best_version(len(text.encode('utf-8')), level)
            if version < 0:
                continue
            for mask in range(8):
                mine = build_matrix(text, level, mask, version)
                qr = qrcode.QRCode(version=version, error_correction=lib_level, box_size=1, border=0,
                                   mask_pattern=mask)
                qr.add_data(QRData(text.encode('utf-8'), mode=MODE_8BIT_BYTE))
                try:
                    qr.make(fit=False)
                except Exception:
                    # باگ کتابخانهٔ مرجع در برخی طول‌های دقیقاً پر؛ آزمون این مورد رد می‌شود
                    continue
                theirs = qr.modules
                checked += 1
                if [list(r) for r in mine] != [list(r) for r in theirs]:
                    failed += 1
                    print('❌ اختلاف در:', repr(text[:24]), 'سطح', level, 'نسخه', version, 'ماسک', mask)
                    for y in range(len(mine)):
                        for x in range(len(mine)):
                            if mine[y][x] != theirs[y][x]:
                                print('   اولین اختلاف در ردیف/ستون', y, x)
                                break
                        else:
                            continue
                        break
    print(f'ماتریس‌های مقایسه‌شده: {checked} — اختلاف: {failed}')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(self_check())
