#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""کنترل سازگاری فراخوانی‌های «لایهٔ لوکس» (MeelanoLuxe).

javalang فقط «نحو» را می‌سنجد؛ این ابزار معنای فراخوانی را می‌سنجد:

  * نام متد/سازندهٔ موجود در MeelanoLuxe باشد،
  * تعداد آرگومان‌ها با امضای همان متد بخواند،
  * ثابت‌های enum آیکون (MeelanoLuxe.I.X) واقعاً تعریف شده باشند.

اجرا:  python3 tools/lux_api_check.py [فایل‌های مصرف‌کننده…]
خروجی: فهرست 🔴‌ها؛ کد بازگشت ۰ (قضاوت نهایی با verify_all).
"""
import glob
import io
import os
import re
import sys

try:
    import javalang
    from javalang import tree
except Exception as e:  # pragma: no cover
    print('javalang missing:', e)
    sys.exit(2)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app', 'src', 'main', 'java', 'ir', 'meelano', 'android')
TARGET = 'MeelanoLuxe'
NESTED_OK = ('IconView', 'Spark', 'Ring', 'MiniBars', 'Corners', 'Shine', 'Medallion')


def arg_count(text, open_index):
    """شمارش آرگومان‌های یک فراخوانی با پیمایش تودرتوی پرانتز/کروشه/گیومه."""
    depth = 0
    count = 0
    empty = True
    i = open_index
    quote = None
    while i < len(text):
        ch = text[i]
        if quote:
            if ch == '\\':
                i += 2
                continue
            if ch == quote:
                quote = None
        elif ch in ('"', "'"):
            quote = ch
            empty = False
        elif ch in '([{':
            if depth == 0 and ch == '(':
                depth = 1
                i += 1
                continue
            depth += 1
            if depth > 1:
                empty = False
        elif ch in ')]}':
            depth -= 1
            if depth == 0:
                return 0 if empty else count + 1
        elif ch == ',' and depth == 1:
            count += 1
        elif depth >= 1 and not ch.isspace():
            empty = False
        i += 1
    return -1


def api_of(lux_path):
    """امضاهای موجود در MeelanoLuxe: متد ثابت، سازندهٔ کلاس تودرتو و ثابت‌های enum."""
    src = io.open(lux_path, encoding='utf-8').read()
    root = javalang.parse.parse(src)
    methods = {}
    ctors = {}
    enums = {}
    for _, cls in root.filter(tree.ClassDeclaration):
        for m in cls.methods:
            if 'static' in (m.modifiers or []) or cls.name == TARGET:
                methods.setdefault(m.name, set()).add(len(m.parameters))
        for c in cls.constructors:
            ctors.setdefault(cls.name, set()).add(len(c.parameters))
    for _, en in root.filter(tree.EnumDeclaration):
        enums[en.name] = set(c.name for c in en.body.constants)
    return methods, ctors, enums


def scan(path, methods, ctors, enums, target=TARGET):
    src = io.open(path, encoding='utf-8').read()
    hits = []
    for m in re.finditer(target + r'\.([A-Za-z_][A-Za-z0-9_]*)\s*\(', src):
        name = m.group(1)
        n = arg_count(src, m.end() - 1)
        if name not in methods:
            if name in enums:
                continue
            if name not in NESTED_OK:
                hits.append('%s: متد/عضو «%s» در %s نیست' % (os.path.basename(path), name, target))
            continue
        if n >= 0 and n not in methods[name]:
            hits.append('%s: فراخوانی %s.%s با %d آرگومان — امضاها: %s' %
                        (os.path.basename(path), target, name, n, sorted(methods[name])))
    for m in re.finditer(r'new\s+' + target + r'\.([A-Za-z_][A-Za-z0-9_]*)\s*\(', src):
        name = m.group(1)
        n = arg_count(src, m.end() - 1)
        if name not in ctors:
            if name in enums:
                continue
            if name not in NESTED_OK:
                hits.append('%s: کلاس تودرتوی «%s» در %s نیست' % (os.path.basename(path), name, target))
            continue
        if n >= 0 and n not in ctors[name]:
            hits.append('%s: new %s.%s با %d آرگومان — امضاها: %s' %
                        (os.path.basename(path), target, name, n, sorted(ctors[name])))
    for m in re.finditer(target + r'\.I\.([A-Z_][A-Z0-9_]*)', src):
        name = m.group(1)
        if 'I' in enums and name not in enums['I']:
            hits.append('%s: آیکون ناموجود I.%s' % (os.path.basename(path), name))
    return hits


def selftest(methods, ctors, enums):
    """خودآزمون: یک فراخوانی با تعداد آرگومان غلط باید گرفته شود."""
    sample = os.path.join(SRC, '_selftest_lux.java')
    code = ('package x;\nclass Z { void f() { %s.section(null, "1", "t", "s", null, 0, "extra");'
            ' new %s.Ring(); } }\n' % (TARGET, TARGET))
    caught = []
    try:
        io.open(sample, 'w', encoding='utf-8').write(code)
        caught = scan(sample, methods, ctors, enums)
    except Exception as e:
        caught = ['SELFTEST-ERROR %s' % e]
    finally:
        if os.path.exists(sample):
            os.remove(sample)
    return caught


def main(argv):
    lux_path = os.path.join(SRC, TARGET + '.java')
    if not os.path.exists(lux_path):
        print('🔴 %s پیدا نشد' % lux_path)
        return 0
    methods, ctors, enums = api_of(lux_path)
    files = argv or [f for f in sorted(glob.glob(os.path.join(SRC, '*.java'))) if not f.endswith(TARGET + '.java')]
    hits = []
    for f in files:
        hits.extend(scan(f, methods, ctors, enums))
    caught = selftest(methods, ctors, enums)
    for h in hits:
        print('🔴', h)
    if len(caught) < 2:
        print('🔴 خودآزمون ابزار ناموفق — انتظار ≥۲ ایراد در نمونهٔ مصنوعی، دریافت: %s' % caught)
    print('lux api check: %d ایراد در %d فایل (خودآزمون: %d ایراد در نمونه)'
          % (len(hits), len(files), len(caught)))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
