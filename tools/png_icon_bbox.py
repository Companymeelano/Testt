#!/usr/bin/env python3
"""Reports the drawn area of every 3D icon PNG (alpha bounding box).

A 3D icon that only fills the middle third of its canvas looks tiny next to an
icon that fills the whole canvas. This tool finds those icons without any
third-party library, so the artwork can be trimmed to the same visual size.
"""
from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path


def read_png(path: Path):
    """Returns (width, height, rgba_bytes) for an 8-bit RGBA/RGB PNG."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos = 8
    width = height = depth = color = None
    idat = bytearray()
    palette = b""
    trns = b""
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            width, height, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", chunk)
            if depth != 8 or interlace != 0:
                raise ValueError(f"unsupported PNG ({depth} bit, interlace {interlace})")
        elif ctype == b"PLTE":
            palette = chunk
        elif ctype == b"tRNS":
            trns = chunk
        elif ctype == b"IDAT":
            idat += chunk
        elif ctype == b"IEND":
            break
    raw = zlib.decompress(bytes(idat))
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    stride = width * channels
    out = bytearray(height * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    # normalise to RGBA
    rgba = bytearray(width * height * 4)
    for i in range(width * height):
        if color == 6:
            rgba[i * 4:i * 4 + 4] = out[i * 4:i * 4 + 4]
        elif color == 2:
            rgba[i * 4:i * 4 + 3] = out[i * 3:i * 3 + 3]
            rgba[i * 4 + 3] = 255
        elif color == 0:
            v = out[i]
            rgba[i * 4:i * 4 + 4] = bytes((v, v, v, 255))
        elif color == 4:
            v, a = out[i * 2], out[i * 2 + 1]
            rgba[i * 4:i * 4 + 4] = bytes((v, v, v, a))
        elif color == 3:
            idx = out[i]
            rgba[i * 4:i * 4 + 3] = palette[idx * 3:idx * 3 + 3]
            rgba[i * 4 + 3] = trns[idx] if idx < len(trns) else 255
    return width, height, bytes(rgba)


def bbox(path: Path, threshold: int = 8):
    w, h, px = read_png(path)
    minx, miny, maxx, maxy = w, h, -1, -1
    for y in range(h):
        row = y * w * 4
        for x in range(w):
            if px[row + x * 4 + 3] > threshold:
                if x < minx:
                    minx = x
                if x > maxx:
                    maxx = x
                if y < miny:
                    miny = y
                if y > maxy:
                    maxy = y
    if maxx < 0:
        return w, h, None
    return w, h, (minx, miny, maxx, maxy)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    files = sorted(root.glob("*.png"))
    if not files:
        print("no PNGs found")
        return 1
    print(f"{'file':46} {'canvas':>10} {'drawn':>12} {'fill%':>6}")
    small = []
    for f in files:
        try:
            w, h, box = bbox(f)
        except Exception as e:  # noqa: BLE001
            print(f"{f.name:46} unreadable: {e}")
            continue
        if box is None:
            print(f"{f.name:46} {'empty':>10}")
            continue
        x0, y0, x1, y1 = box
        draw_w, draw_h = x1 - x0 + 1, y1 - y0 + 1
        fill = 100.0 * min(draw_w, draw_h) / min(w, h)
        print(f"{f.name:46} {f'{w}x{h}':>10} {f'{draw_w}x{draw_h}':>12} {fill:5.1f}%")
        if fill < 80:
            small.append((f.name, fill))
    print()
    if small:
        print("icons that do not fill their canvas (they look small next to the others):")
        for name, fill in small:
            print(f"  {name}: {fill:.1f}%")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
