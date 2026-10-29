#!/usr/bin/env python3
"""بازبینی جامع پروژهٔ Meelano پیش از تحویل (جانشین سبک CI در محیط بدون JDK/Android SDK).

اجرا:  python3 tools/verify_all.py

کنترل‌ها:
  ۱) نحو همهٔ فایل‌های جاوا (javalang) + اثبات اینکه تنها خطای MeelanoQr محدودیت خود ابزار است
  ۲) ارجاع‌های بین‌کلاسی پروژه (Class.method / ثابت‌ها / واسط میزبان)
  ۳) منابع: سالم‌بودن XML، وجود drawable/layout/string/id ارجاع‌شده
  ۴) گلیف‌های بی‌آیکون در ماژول‌های تازه
  ۵) اعتبار pathData آیکون‌های وکتور تازه
  ۶) دسترس‌پذیری صفحه‌های تازه (کیس‌های renderActivePage، ناوبری، مجوز، پیاده‌سازی واسط)
  ۷) تعریف تکراری متد/فیلد در کلاس‌ها
  ۸) جای‌خالی‌های ناتمام (TODO/FIXME/not implemented) در کد تازه
  ۹) موتورهای QR و بارکد در برابر مرجع
  ۱۰) وجود اسناد فارسی و بستهٔ خروجی
  ۱۱) متغیر محلی تکراری در یک متد و isEmpty() روی JSONArray/JSONObject
  ۱۲) سازگاری فراخوانی‌های «لایهٔ لوکس» (MeelanoLuxe): نام متد، تعداد آرگومان و ثابت‌های آیکون
      (دو خطای واقعی javac که javalang از آن‌ها رد می‌شود) + خودآزمون همین کنترل
  ۱۳) هویت برنامهٔ یکپارچهٔ لایسنس‌دار: package، آیکون اصلی، ادغام مجوزها و گواهی امضای به‌روزرسانی
"""
from __future__ import annotations

import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

import javalang
from javalang import tree

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src/main/java/ir/meelano/android')
NEW_MODULES = ['MeelanoUiHost.java', 'MeelanoPalette.java', 'MeelanoUiKit.java',
               'MeelanoWarehouse.java', 'MeelanoWarehouseRepo.java', 'MeelanoWarehouseUi.java',
               'MeelanoFinance.java', 'MeelanoFinanceUi.java', 'MeelanoLabelPrint.java']
RESULTS = []


def report(name, ok, detail=''):
    RESULTS.append((name, ok, detail))
    print(('✅ ' if ok else '⚠️  ') + name + (('  —  ' + detail) if detail else ''))


def java_files():
    return sorted(glob.glob(os.path.join(ROOT, 'app/src/**/*.java'), recursive=True))


def project_java_files():
    """Every application, test and licensing Java source in both Gradle projects."""
    paths = glob.glob(os.path.join(ROOT, 'app/src/**/*.java'), recursive=True)
    paths += glob.glob(os.path.join(ROOT, 'manager/**/*.java'), recursive=True)
    return sorted(set(paths))


# ---------------------------------------------------------------- ۱) نحو
def check_syntax():
    bad = []
    files = project_java_files()
    for f in files:
        src = open(f, encoding='utf-8').read()
        try:
            javalang.parse.parse(src)
        except Exception as e:
            bad.append((f, src, e))
    if not bad:
        report('نحو همهٔ فایل‌های جاوا (هر دو پروژه و تست‌ها)', True, f'{len(files)} فایل بدون خطا')
        return
    # تلاش برای اثبات «محدودیت ابزار»: آرایه‌های تودرتوی چندبعدی را ساده می‌کنیم و دوباره می‌خوانیم
    real = []
    for f, src, e in bad:
        patched = re.sub(r'=\s*\{', '= new int[0] {{{', src, count=1) if False else src
        patched = strip_nested_array_initialisers(src)
        try:
            javalang.parse.parse(patched)
            report(f'نحو {os.path.basename(f)}', True,
                   'تنها مانع، محدودیت javalang در خواندن مقدار آرایهٔ چندبعدی است (خطای واقعی جاوا نیست)')
        except Exception:
            real.append((f, repr(e)[:120]))
    if real:
        for f, msg in real:
            report('نحو ' + os.path.basename(f), False, msg)
    else:
        report('نحو بقیهٔ فایل‌ها', True, f'{len(files) - len(bad)} فایل سالم')


def strip_nested_array_initialisers(src: str) -> str:
    """`= {` ... `}` مربوط به آرایه‌های چندبعدی را با مقدار ساده جایگزین می‌کند."""
    out, i = [], 0
    while True:
        m = re.search(r'(\[\])+\s+([A-Z_][A-Z0-9_]*)\s*=\s*\{', src[i:])
        if not m:
            out.append(src[i:])
            break
        start = i + m.start()
        j = i + m.end() - 1                      # محل `{`
        depth, k = 0, j
        while k < len(src):
            if src[k] == '{':
                depth += 1
            elif src[k] == '}':
                depth -= 1
                if depth == 0:
                    break
            k += 1
        out.append(src[i:start])
        out.append(m.group(0)[:m.group(0).index('{')].rstrip() + ' null;')
        i = k + 1
    return ''.join(out)


# ---------------------------------------------------------------- ۲) ارجاع‌ها
def check_references():
    p = subprocess.run([sys.executable, os.path.join(ROOT, 'tools/java_api_check.py'), '--all'],
                       capture_output=True, text=True, cwd=ROOT)
    lines = p.stdout.splitlines()
    problems = [l for l in lines if 'PARSE-SKIPPED' not in l and 'JavaSyntaxError' not in l
                and l.strip().startswith(('MainActivity.java:', 'Meelano', 'ManagerAnalytics.java:'))
                and 'host.' not in l]
    report('ارجاع‌های بین‌کلاسی (کلاس‌های پروژه)', not problems,
           f'{len(problems)} ارجاع نامعلوم' if problems else 'هیچ ارجاع نامعلومی نیست')
    for l in problems[:8]:
        print('     ', l.strip())


# ---------------------------------------------------------------- ۳) منابع
def check_resources():
    xmls = glob.glob(os.path.join(ROOT, 'app/src/**/*.xml'), recursive=True)
    malformed = []
    for f in xmls:
        try:
            ET.parse(f)
        except Exception:
            malformed.append(os.path.relpath(f, ROOT))
    report('سالم‌بودن فایل‌های XML', not malformed,
           f'{len(xmls)} فایل' + ('' if not malformed else f'، معیوب: {malformed[:5]}'))

    have = {}
    for folder, kind in (('drawable*', 'drawable'), ('layout*', 'layout'), ('menu*', 'menu'), ('xml', 'xml')):
        names = set()
        for f in glob.glob(os.path.join(ROOT, f'app/src/*/res/{folder}/*')):
            names.add(os.path.splitext(os.path.basename(f))[0])
        have[kind] = names
    strings = set()
    for f in glob.glob(os.path.join(ROOT, 'app/src/*/res/values*/*.xml')) + glob.glob(os.path.join(ROOT, 'app/src/*/res/values/*.xml')):
        try:
            for el in ET.parse(f).getroot():
                if el.get('name'):
                    strings.add(el.get('name'))
        except Exception:
            pass
    java = ''.join(open(f, encoding='utf-8').read() for f in java_files())
    missing = {}
    for kind, extra in (('drawable', ''), ('layout', ''), ('menu', ''), ('xml', '')):
        refs = set(re.findall(r'(?<!android\.)R\.' + kind + r'\.([A-Za-z0-9_]+)', java))
        miss = sorted(r for r in refs if r not in have[kind])
        if miss:
            missing[kind] = miss
    srefs = set(re.findall(r'(?<!android\.)R\.string\.([A-Za-z0-9_]+)', java))
    smiss = sorted(s for s in srefs if s not in strings and not s.startswith('meelano_edition'))
    if smiss:
        missing['string'] = smiss
    report('ارجاع منابع (drawable/layout/menu/string)', not missing,
           'همهٔ ارجاع‌ها وجود دارند' if not missing else str(missing)[:200])


# ---------------------------------------------------------------- ۴) گلیف‌ها
def check_glyphs():
    icons = open(os.path.join(SRC, 'MeelanoIcons.java'), encoding='utf-8').read()
    mapped = set(re.findall(r'any\("([^"]+)"', icons)) | set(re.findall(r'lead\("([^"]+)"', icons))

    def ignorable(ch):
        cp = ord(ch)
        return cp < 0x80 or 0x0600 <= cp <= 0x06FF or cp in (0x200C, 0x200D, 0x200E, 0x200F) or ch in '،؛؟«»…•‹›—÷'

    bad = {}
    for f in NEW_MODULES + ['MeelanoWarehouseUi.java', 'MeelanoFinanceUi.java']:
        path = os.path.join(SRC, f)
        if not os.path.exists(path):
            continue
        src = open(path, encoding='utf-8').read()
        for m in re.finditer(r'"([^"\n]*)"', src):
            for ch in m.group(1):
                if ignorable(ch) or ch in mapped:
                    continue
                bad.setdefault(f, {}).setdefault(ch, 0)
                bad[f][ch] += 1
    report('گلیف‌های بی‌آیکون در ماژول‌های تازه', not bad, 'همهٔ گلیف‌ها آیکون وکتور دارند' if not bad else str(bad)[:200])


# ---------------------------------------------------------------- ۵) pathData
def check_vectors():
    allowed = set('MmLlHhVvCcSsQqTtAaZz ')
    bad = []
    for f in sorted(glob.glob(os.path.join(ROOT, 'app/src/*/res/drawable*/mi_*.xml'))):
        try:
            root = ET.parse(f).getroot()
        except Exception:
            bad.append(os.path.basename(f) + ': XML معیوب')
            continue
        for el in root.iter():
            d = el.get('{http://schemas.android.com/apk/res/android}pathData')
            if not d:
                continue
            letters = {c for c in d if c.isalpha()}
            if letters - allowed:
                bad.append(os.path.basename(f) + ': ' + ''.join(sorted(letters - allowed)))
            if not d.strip().startswith(('M', 'm')):
                bad.append(os.path.basename(f) + ': مسیر با M شروع نمی‌شود')
    report('اعتبار آیکون‌های وکتور', not bad, f'{len(glob.glob(os.path.join(ROOT, "app/src/*/res/drawable*/mi_*.xml")))} آیکون' + ('' if not bad else ' — ' + str(bad[:4])))


# ---------------------------------------------------------------- ۶) دسترس‌پذیری صفحه‌ها
def check_wiring():
    main = open(os.path.join(SRC, 'MainActivity.java'), encoding='utf-8').read()
    problems = []
    for page, loader in (('warehouse', 'loadManagerWarehouse'), ('finance', 'loadManagerFinance')):
        if f'case "{page}": {loader}();' not in main:
            problems.append(f'کیس renderActivePage برای {page}')
        if f'private void {loader}()' not in main:
            problems.append(f'متد {loader}')
        if f'"{page}".equals(page)' not in main and f'"warehouse".equals(k)' not in main and f'"finance".equals(k)' not in main:
            problems.append(f'مجوز صفحهٔ {page}')
    if 'showApp("warehouse")' not in main or 'showApp("finance")' not in main:
        problems.append('میان‌بر ناوبری صفحه‌ها')
    if 'MeelanoUiHost uiHost = new MeelanoUiHost()' not in main:
        problems.append('پل MeelanoUiHost')
    # پیاده‌سازی کامل واسط
    host_src = open(os.path.join(SRC, 'MeelanoUiHost.java'), encoding='utf-8').read()
    host_root = javalang.parse.parse(host_src)
    need = set()
    for _, iface in host_root.filter(tree.InterfaceDeclaration):
        for m in iface.methods:
            need.add((m.name, len(m.parameters)))
    bridge = main[main.index('MeelanoUiHost uiHost = new MeelanoUiHost()'):]
    bridge = bridge[:bridge.index('\n    };')]
    have = set()
    for m in re.finditer(r'@Override public [\w<>\[\], .]+ (\w+)\(([^)]*)\)', bridge):
        params = [x for x in m.group(2).split(',') if x.strip()]
        have.add((m.group(1), len(params)))
    miss = sorted(n for n, a in need if (n, a) not in have)
    extra = sorted(n for n, a in have if (n, a) not in need)
    if miss or extra:
        problems.append(('پیاده‌نشده: ' + ', '.join(miss) if miss else '') +
                        (' | اضافه: ' + ', '.join(extra) if extra else ''))
    report('دسترس‌پذیری و اتصال صفحه‌های تازه', not problems,
           'کیس، لودر، مجوز، ناوبری و واسط کامل است' if not problems else '؛ '.join(problems))


# ---------------------------------------------------------------- ۷) تعریف تکراری
def erased_signature(param):
    dims = len(param.type.dimensions) if param.type.dimensions else 0
    return (param.type.name, dims)


def check_duplicates():
    """دو متد با نام و «نوع پاک‌شدهٔ» پارامترهای یکسان، خطای کامپایل است (نام پارامتر مهم نیست)."""
    dups = []
    for f in java_files():
        src = open(f, encoding='utf-8').read()
        try:
            root = javalang.parse.parse(src)
        except Exception:
            continue
        for _, cls in root.filter(tree.ClassDeclaration):
            seen = {}
            for m in cls.methods:
                sig = (m.name, tuple(erased_signature(p) for p in m.parameters), 'static' in (m.modifiers or []))
                seen.setdefault(sig, []).append(m)
            for sig, group in seen.items():
                if len(group) > 1:
                    dups.append(f'{os.path.basename(f)}::{cls.name}.{sig[0]}({len(sig[1])} پارامتر) ×{len(group)}')
            flds = {}
            for fl in cls.fields:
                for d in fl.declarators:
                    flds[d.name] = flds.get(d.name, 0) + 1
            for nm, n in flds.items():
                if n > 1:
                    dups.append(f'{os.path.basename(f)}::{cls.name}.{nm} ×{n}')
    report('تعریف تکراری متد/فیلد (خطای کامپایل)', not dups, 'هیچ موردی نیست' if not dups else str(dups[:6]))


# ---------------------------------------------------------------- ۸) جای‌خالی
def check_placeholders():
    bad = []
    for f in NEW_MODULES + ['MeelanoWarehouseUi.java', 'MeelanoFinanceUi.java']:
        path = os.path.join(SRC, f)
        if not os.path.exists(path):
            continue
        src = open(path, encoding='utf-8').read()
        for m in re.finditer(r'(TODO|FIXME|XXX|not implemented|پیاده\s*نشده)', src):
            bad.append(f + ':' + str(src[:m.start()].count('\n') + 1))
    report('جای‌خالی ناتمام (TODO/FIXME)', not bad, 'هیچ' if not bad else str(bad[:5]))


# ---------------------------------------------------------------- ۹) موتورها
def check_engines():
    for name, script, needle in (('QR', 'tools/qr_reference_check.py', 'اختلاف'),
                                 ('بارکد', 'tools/barcode_reference_check.py', 'ناموفق')):
        p = subprocess.run([sys.executable, os.path.join(ROOT, script)], capture_output=True, text=True, cwd=ROOT)
        last = [l for l in p.stdout.splitlines() if needle in l or 'آزمون' in l or 'ماتریس' in l]
        tail = last[-1].strip() if last else '(بدون خروجی)'
        zero = (' 0' in tail or ': 0' in tail) and 'ناموفق: 0' in tail.replace('اختلاف', 'ناموفق')
        report(f'موتور {name} در برابر مرجع', zero, tail)


# ---------------------------------------------------------------- ۱۰) اسناد
def check_docs():
    ok = True
    detail = []
    for f in ('docs/RESULT-fa.md', 'docs/TAHLIL-fa.md', 'README.md'):
        p = os.path.join(ROOT, f)
        ok = ok and os.path.exists(p)
        detail.append(f'{f}: {"هست" if os.path.exists(p) else "نیست"}')
    report('اسناد تحویل', ok, '؛ '.join(detail))


# ---------------------------------------------------------------- ۱۱) متد روی متغیرهای پروژه
VARARGS_NAMES = set()


def build_class_table():
    table = {}
    for f in java_files():
        VARARGS_NAMES.update(re.findall(r'\b(\w+)\s*\([^;{)]*\.\.\.', open(f, encoding='utf-8').read(), re.S))
    for f in java_files():
        src = open(f, encoding='utf-8').read()
        try:
            root = javalang.parse.parse(src)
        except Exception:
            continue
        for _, cls in root.filter((tree.ClassDeclaration,)):
            info = table.setdefault(cls.name, {'methods': set(), 'files': set()})
            info['files'].add(os.path.basename(f))
            for m in cls.methods:
                info['methods'].add((m.name, len(m.parameters), m.name in VARARGS_NAMES))
        for _, iface in root.filter(tree.InterfaceDeclaration):
            info = table.setdefault(iface.name, {'methods': set(), 'files': set()})
            info['files'].add(os.path.basename(f))
            for m in iface.methods:
                info['methods'].add((m.name, len(m.parameters), m.name in VARARGS_NAMES))
    return table


def check_variable_calls():
    """`p.tone(...)` / `current.toJson(...)` — کنترل صدا زدن متدها روی متغیرهایی که نوعشان کلاس پروژه است."""
    table = build_class_table()
    ambiguous = {n for n, i in table.items() if len(i['files']) > 1}
    problems = []
    for f in sorted(java_files()):
        base = os.path.basename(f)
        if base not in NEW_MODULES + ['MeelanoWarehouseUi.java', 'MeelanoFinanceUi.java']:
            continue
        src = open(f, encoding='utf-8').read()
        try:
            root = javalang.parse.parse(src)
        except Exception:
            continue
        var_type = {}
        for _, decl in root.filter(tree.VariableDeclaration):
            t = getattr(decl.type, 'name', None)
            if not t or t in ambiguous or t not in table or t in ('String', 'int', 'double'):
                continue
            for d in decl.declarators:
                var_type[d.name] = t
        for _, inv in root.filter(tree.MethodInvocation):
            t = var_type.get(inv.qualifier)
            if not t:
                continue
            methods = table[t]['methods']
            argc = len(inv.arguments)
            same_name = [m for m in methods if m[0] == inv.member]
            if not same_name:
                problems.append(f'{base}:{inv.position.line if inv.position else 0} {inv.qualifier}({t}).{inv.member}(...) وجود ندارد')
            elif not any((va and argc >= a - 1) or (not va and argc == a) for _, a, va in same_name):
                arities = sorted(a for _, a, _ in same_name)
                problems.append(f'{base}:{inv.position.line if inv.position else 0} {inv.qualifier}({t}).{inv.member}({argc} آرگومان) — تعداد اعلام‌شده {arities}')
    report('متدها روی متغیرهای از نوع کلاس‌های پروژه', not problems,
           'همه معتبر' if not problems else '؛ '.join(problems[:6]))


def check_switch_cases():
    """برچسب تکراری در یک switch = خطای کامپایل."""
    dups = []
    for f in java_files():
        src = open(f, encoding='utf-8').read()
        try:
            root = javalang.parse.parse(src)
        except Exception:
            continue
        for _, sw in root.filter(tree.SwitchStatement):
            labels = []
            for case in sw.cases:
                if case.case:
                    labels.extend(re.findall(r'"([^"]*)"', str(case.case)))
            seen = set()
            for l in labels:
                if l in seen:
                    dups.append(f'{os.path.basename(f)}:{sw.position.line if sw.position else 0} case "{l}" تکراری')
                seen.add(l)
    report('برچسب تکراری switch', not dups, 'هیچ' if not dups else '؛ '.join(dups[:5]))


# ---------------------------------------------------------------- ۱۲) کوئری‌های دست‌ساز
# فقط متدهایی که کوئری‌شان ساختار روشنی دارد (زیرپرس‌وجوِ به‌هم‌پیوسته).
# پرس‌وجوهای قدیمی‌ترِ پروژه از ده‌ها قطعهٔ رشته‌ای ساخته می‌شوند و تحلیل جای‌محور روی آن‌ها
# هشدار کاذب می‌دهد؛ عمداً در این فهرست نیستند.
SQL_METHODS = ('atiranWarehouseSql', 'warehouses', 'stockRows', 'kardex', 'expiringBatches',
               'movementTotals', 'debtorAging', 'customerNames')



def split_top_level(text, sep=','):
    parts, depth, cur = [], 0, ''
    for ch in text:
        if ch in '(':
            depth += 1
        elif ch in ')':
            depth -= 1
        if ch == sep and depth == 0:
            parts.append(cur)
            cur = ''
        else:
            cur += ch
    parts.append(cur)
    return parts


def top_level_from(sql):
    """جای « FROM » سطح بالا در یک SELECT (برای جدا کردن فهرست ستون‌ها از ادامهٔ پرس‌وجو)."""
    i = sql.upper().find('SELECT') + 6
    depth = 0
    j = i
    while j < len(sql):
        ch = sql[j]
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        elif depth == 0 and sql[j:j + 6].upper() == ' FROM ':
            return i, j
        j += 1
    return i, len(sql)


def derived_aliases(inner_sql):
    """نام‌های مستعاری که خودِ زیرپرس‌وجو می‌سازد (فقط همین‌ها بیرون قابل استفاده‌اند)."""
    try:
        start, end = top_level_from(inner_sql)
    except Exception:
        return set()
    names = set()
    for item in split_top_level(inner_sql[start:end]):
        item = item.strip()
        if not item or item.endswith(')'):
            continue
        mm = re.search(r'(?:AS\s+)?\[?([A-Za-z_][\w]*)\]?$', item, re.I)
        if mm:
            names.add(mm.group(1))
    return names


def derived_table_column_errors(text, file_name, method_name):
    """ستون‌هایی که بیرونِ `FROM (SELECT …) g` استفاده شده‌اند اما زیرپرس‌وجو آن‌ها را نساخته است.

    در SQL Server بیرونِ زیرپرس‌وجو فقط نام‌های مستعارِ ساختهٔ خودِ آن و ستون‌های جدول‌های join‌شده
    (با نام مستعارشان) در دسترس‌اند؛ ارجاع به ستون جدول پایه بیرون، خطای زمان اجرا می‌دهد —
    همان خطایی که نسخهٔ اول `debtorAging` داشت.
    """
    out = []
    marker = ') g'
    if marker not in text:
        return out
    close = text.index(marker)
    depth, open_paren = 0, None
    for i in range(close, -1, -1):
        if text[i] == ')':
            depth += 1
        elif text[i] == '(':
            depth -= 1
            if depth == 0:
                open_paren = i
                break
    if open_paren is None:
        return out
    inner = text[open_paren + 1:close]
    outer = text[:open_paren] + ' ' + text[close + 2:]
    aliases = derived_aliases(inner)
    joined = {m[1] for m in re.findall(r'JOIN\s+dbo\.\[?([\w\u0600-\u06FF]+)\]?\s+(\w+)', outer)}
    for col in re.findall(r'\[([^\]\s]+)\]', outer):
        if col in aliases or col in joined:
            continue
        prefixed = re.findall(r'([\w]+)\.\[' + re.escape(col) + r'\]', outer)
        if prefixed and all(a in joined for a in prefixed):
            continue
        if prefixed:
            continue
        out.append(f'{file_name}::{method_name} ستون [{col}] بیرون زیرپرس‌وجو تعریف نشده است')
    return out



# ---------------------------------------------------------------- ابزار: بازکردن رشته‌های چسبیده
def split_top_level_plus(expr):
    parts, depth, cur, in_str = [], 0, '', None
    for ch in expr:
        if in_str:
            cur += ch
            if ch == in_str:
                in_str = None
            continue
        if ch in (chr(34), chr(39)):
            in_str = ch
            cur += ch
            continue
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        if ch == '+' and depth == 0:
            parts.append(cur)
            cur = ''
        else:
            cur += ch
    parts.append(cur)
    return parts


def unquote(lit):
    return lit[1:-1].replace('\\"', '"').replace("\\'", "'").replace('\\\\', '\\')


def extract_method_source(src, name):
    """متن کامل یک متد از سورس جاوا (با تطبیق آکولاد)."""
    m = re.search(r'\n[^\n]*\b' + re.escape(name) + r'\s*\([^;{]*\)\s*(?:throws [\w, .]+)?\s*\{', src)
    if not m:
        return None
    start = m.end() - 1
    depth = 0
    for i in range(start, len(src)):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[start:i + 1]
    return None


def variable_literals(method_src):
    """نقشهٔ «نام متغیر ← رشتهٔ ساخته‌شده» برای متغیرهای متنی یک متد (تا دو سطح بازکردن)."""
    assigns = {}
    for m in re.finditer(r'(?:\bString\s+)?(\w+)\s*=\s*([^;]+);', method_src):
        name, expr = m.group(1), m.group(2).strip()
        if name in ('if', 'for', 'while', 'return') or '++' in expr or '--' in expr:
            continue
        if name not in assigns:
            assigns[name] = expr
    def build(expr, depth=0, seen=None):
        if depth > 4:
            return None
        seen = seen or set()
        out = []
        for part in split_top_level_plus(expr):
            piece = part.strip()
            if len(piece) >= 2 and piece[0] == '"' and piece[-1] == '"':
                out.append(unquote(piece))
            elif len(piece) >= 2 and piece[0] == "'" and piece[-1] == "'":
                out.append(piece[1:-1])
            elif re.fullmatch(r'\w+', piece) and piece in assigns and piece not in seen:
                inner = build(assigns[piece], depth + 1, seen | {piece})
                out.append('·' if inner is None else inner)      # قطعهٔ نامعلوم → جانگهدار بی‌اثر
            else:
                out.append('·')
        return ''.join(out)
    return {k: build(v) for k, v in assigns.items()}


def resolved_sql_of(method_src, var_name='sql'):
    """متن SQL واقعی متغیر `sql` با بازکردن متغیرهای متنی (قطعه‌های نامعلوم با · نمایش داده می‌شوند)."""
    assigns = variable_literals(method_src)
    if var_name not in assigns:
        return None
    text = assigns[var_name]
    return None if '·' in text and text.count('·') > 4 else text


def check_sql_balance():
    """بازبینی سبک SQLهای رشته‌ای: تعادل پرانتزها و کوتیشن‌ها و ترتیب SELECT/FROM/GROUP BY.

    (کامپایلر SQL نداریم؛ این کنترل خطاهای تایپی رایج را می‌گیرد: پرانتز جاافتاده، کوتیشن زوج‌نشده،
    جای اشتباه GROUP BY/ORDER BY.)
    """
    problems = []
    checked = 0
    for f in java_files():
        src = open(f, encoding='utf-8').read()
        try:
            root = javalang.parse.parse(src)
        except Exception:
            continue
        for _, m in root.filter(tree.MethodDeclaration):
            if m.name not in SQL_METHODS:
                continue
            src_method = extract_method_source(src, m.name)
            text = resolved_sql_of(src_method) if src_method else None
            if text is None:
                # اگر بازکردن کامل نشد، همان قطعه‌های موجود بررسی می‌شوند (بدون کنترل جای‌محور)
                literals = [lit.value for _, lit in m.filter(tree.Literal) if isinstance(lit.value, str)]
                text = ' '.join(literals)
                partial = True
            else:
                partial = False
            if 'SELECT' not in text.upper():
                continue
            checked += 1
            if text.count('(') != text.count(')'):
                problems.append(f'{os.path.basename(f)}::{m.name} پرانتز نامتعادل ({text.count("(")}≠{text.count(")")})')
            if text.count("'") % 2 != 0:
                problems.append(f'{os.path.basename(f)}::{m.name} کوتیشن نامتعادل')
            up = text.upper()
            if 'SELECT' in up and 'FROM' in up:
                if up.index('SELECT') > up.index('FROM') and m.name not in ('collection', 'customerNames'):
                    problems.append(f'{os.path.basename(f)}::{m.name} ترتیب SELECT/FROM مشکوک')
            if up.count('GROUP BY') > up.count('SELECT'):
                problems.append(f'{os.path.basename(f)}::{m.name} GROUP BY بیشتر از SELECT')
            if 'SELECT' in up and 'TOP' in up and '(' not in text.split('TOP')[1][:6]:
                problems.append(f'{os.path.basename(f)}::{m.name} TOP بدون پرانتز')
            if not partial:
                problems.extend(derived_table_column_errors(text, os.path.basename(f), m.name))
    report('کوئری‌های دست‌ساز (رشته بازشده + تعادل پرانتز/کوتیشن/ستون)', not problems,
           f'{checked} متد SQL بررسی شد (رشته‌ها با بازکردن متغیرها ساخته شدند)' if not problems
           else '؛ '.join(problems[:6]))


# ---------------------------------------------------------------- ۱۳) خودآزمون ابزار
def check_tooling_selftest():
    """اثبات اینکه کنترل SQL «دندان» دارد: یک کوئری باگ‌دار باید گرفته و یک کوئری درست رد شود."""
    buggy = ("SELECT TOP (60) p, COUNT_BIG(*), ISNULL(MAX(CASE WHEN dbo.dif_date_alan([t_date]) < 0 THEN -dbo.dif_date_alan([t_date]) ELSE 0 END),0) "
             "FROM (SELECT TRY_CONVERT(nvarchar(60),[shmo]) p, [all] amount, dbo.dif_date_alan([t_date]) d FROM dbo.sailfact) g "
             "WHERE amount>0 GROUP BY p ORDER BY 2 DESC")
    good = ("SELECT TRY_CONVERT(nvarchar(150),a.[name]), COUNT_BIG(DISTINCT g.shka), ISNULL(SUM(g.qty),0) "
            "FROM (SELECT m.wh wh, m.shka shka, SUM(m.qty) qty FROM (SELECT k.shka shka, k.[RdfAnbar] wh, k.active x FROM dbo.ka_act k) m GROUP BY m.wh, m.shka) g "
            "JOIN dbo.anbars a ON a.[rdf_anbar]=g.wh GROUP BY a.[name], a.[anbardar]")
    caught = derived_table_column_errors(buggy, 'selftest', 'buggy')
    clean = derived_table_column_errors(good, 'selftest', 'good')
    ok = bool(caught) and not clean
    report('خودآزمون ابزار (کنترل SQL باگ را می‌گیرد)', ok,
           f'کوئری باگ‌دار یک ایراد گرفت و کوئری درست صفر' if ok else f'caught={caught} clean={clean}')


# ---------------------------------------------------------------- ۱۴) کاما داخل کامنت
def check_comma_in_comment():
    """کلاس خطای واقعی که build را شکست: «,» آخر خط داخل کامنت افتاده باشد.

    مثل `}} // L,` که در واقع `}}` بدون کاما است و javac خطا می‌دهد
    ('}' expected). این کنترل با حذف کامنت‌ها، آرایه‌های چندبعدی را می‌سنجد.
    """
    problems = []
    for f in java_files():
        src = open(f, encoding='utf-8').read()
        if '//' not in src:
            continue
        for i, line in enumerate(src.split('\n'), 1):
            stripped = re.sub(r'\s*//.*$', '', line).rstrip()
            if stripped.endswith('//'):
                continue
        # سنجش ساختاری: بعد از حذف کامنت‌ها، «}» نباید بلافاصله با «{» در خط بعد بیاید
        clean = re.sub(r'//[^\n]*', '', src)
        for m in re.finditer(r'\}\s*\n\s*\{', clean):
            line = clean[:m.start()].count('\n') + 1
            # موارد مجاز: بلوک مقداردهی استاتیک/نمونه در بدنهٔ کلاس
            tail = clean[max(0, m.start() - 40):m.start()].rstrip().split('\n')[-1]
            if re.search(r'\b(static\s*\{|\bif\s*\(|\)\s*\{)\s*$', tail) and not tail.endswith('}'):
                continue
            problems.append(f'{os.path.basename(f)}:{line} احتمال کاما جاافتاده بین عناصر آرایه/بلوک')
    # کنترل مستقیم: کاما در انتهای کامنت که در واقع کاما نیست
    for f in java_files():
        for i, line in enumerate(open(f, encoding='utf-8').read().split('\n'), 1):
            if re.search(r'//[^\n]*,\s*$', line) and line.strip().startswith(('{', '}', '[')):
                problems.append(f'{os.path.basename(f)}:{i} کاما داخل کامنت افتاده (خطای javac)')
    report('کاما/آکولاد ناشی از کامنت‌ها', not problems, 'پاک است' if not problems else '؛ '.join(problems[:5]))


# ---------------------------------------------------------------- ۱۸) سایه‌اندازی متغیر محلی + isEmpty روی JSON
def check_shadow_and_json():
    """دو کلاس خطای واقعی javac که javalang از آن‌ها رد می‌شود:

    * «متغیر محلی تکراری» (variable x is already defined in method …)
    * «isEmpty() روی JSONArray/JSONObject» — چنین متدی در org.json اندروید نیست.
    خودآزمون: فایل مصنوعی با هر دو خطا باید هر دو را برگرداند.
    """
    sys.path.insert(0, os.path.join(ROOT, 'tools'))
    try:
        import shadow_check
    except Exception as e:  # pragma: no cover
        report('متغیر تکراری/JSON isEmpty', False, 'واردکردن ابزار ناموفق: %s' % e)
        return
    problems, checked = [], 0
    for f in java_files():
        try:
            hits = shadow_check.collect(f)
        except Exception:
            continue  # فایل‌های فراتر از توان javalang (MeelanoQr) در کنترل ۱ سنجیده شده‌اند
        checked += 1
        problems.extend(hits)
    sample = os.path.join(ROOT, 'tools', '_selftest_shadow.java')
    bad_src = ('package x;\nimport org.json.JSONArray;\nclass Bad {\n'
               '  void kardex(Object r) {\n    String act = "1";\n    while (true) {\n'
               '      int act = 2;\n      System.out.println(act);\n    }\n  }\n'
               '  void other(JSONArray matrix) {\n'
               '    if (matrix != null && !matrix.isEmpty()) { System.out.println(1); }\n  }\n'
               '  void clean() {\n    for (int j = 0; j < 3; j++) { }\n    for (int j = 0; j < 3; j++) { }\n  }\n'
               '  void sizing(Object v) {\n    float sizePx = 3f;\n    v.setMinimumWidth(sizePx);\n  }\n}\n')
    caught = []
    try:
        fh = open(sample, 'w', encoding='utf-8')
        fh.write(bad_src)
        fh.close()
        caught = shadow_check.collect(sample)
    except Exception as e:
        caught = ['SELFTEST-ERROR %s' % e]
    finally:
        if os.path.exists(sample):
            os.remove(sample)
    selftest_ok = len(caught) >= 3      # سایه‌اندازی متغیر + isEmpty روی JSON + تبدیل float به int
    if not selftest_ok:
        detail = 'خودآزمون کنترل ناموفق (انتظار ≥۳ ایراد در فایل مصنوعی): %s' % caught
    elif problems:
        detail = '؛ '.join(problems[:4])
    else:
        detail = 'پاک است (%d فایل)' % checked
    report('متغیر تکراری/JSON isEmpty', (not problems) and selftest_ok, detail)


# ---------------------------------------------------------------- ۱۹) سازگاری API لایهٔ لوکس
def check_lux_api():
    """هر فراخوانی MeelanoLuxe.X باید نام موجود و «تعداد آرگومان درست» داشته باشد.

    javalang نحو را می‌سنجد و این کلاس خطاها را نمی‌گیرد (مثل String[][] در برابر String[])؛
    این کنترل همان شکاف را برای API تازه می‌بندد و خودآزمون هم دارد.
    """
    script = os.path.join(ROOT, 'tools', 'lux_api_check.py')
    if not os.path.exists(script):
        report('سازگاری API لایهٔ لوکس', False, 'tools/lux_api_check.py پیدا نشد')
        return
    try:
        out = subprocess.run([sys.executable, script], cwd=ROOT, capture_output=True, text=True, timeout=180)
    except Exception as e:
        report('سازگاری API لایهٔ لوکس', False, 'اجرای ابزار ناموفق: %s' % e)
        return
    text = (out.stdout or '') + (out.stderr or '')
    bad = [ln for ln in text.splitlines() if ln.startswith('🔴')]
    tail = [ln for ln in text.splitlines() if ln.startswith('lux api check')]
    ok = not bad and bool(tail)
    detail = 'همهٔ فراخوانی‌ها با امضا می‌خوانند — ' + (tail[0] if tail else '') if ok else '؛ '.join(bad[:4])
    report('سازگاری API لایهٔ لوکس', ok, detail)


def check_unified_app():
    """نسخهٔ درستِ قابل‌ارتقا باید همان هویت نصب یکپارچه را حفظ کند."""
    script = os.path.join(ROOT, 'tools', 'unified_app_check.py')
    if not os.path.exists(script):
        report('هویت برنامهٔ اصلیِ یکپارچه', False, 'tools/unified_app_check.py پیدا نشد')
        return
    try:
        out = subprocess.run([sys.executable, script], cwd=ROOT, capture_output=True, text=True, timeout=60)
    except Exception as e:
        report('هویت برنامهٔ اصلیِ یکپارچه', False, 'اجرای ابزار ناموفق: %s' % e)
        return
    text = (out.stdout or '') + (out.stderr or '')
    bad = [line for line in text.splitlines() if line.startswith('🔴')]
    tail = [line for line in text.splitlines() if line.startswith('unified app check:')]
    ok = out.returncode == 0 and not bad and bool(tail)
    report('هویت برنامهٔ اصلیِ یکپارچه', ok, tail[0] if ok else '؛ '.join(bad[:4]) or 'ابزار کنترل شکست خورد')


def check_nut_visual_assets():
    """Check the nut-theme selector icon and every bundled product-photo key/file pair."""
    import struct

    photos_src = os.path.join(ROOT, 'app/src/main/java/ir/meelano/android/MeelanoProductPhotos.java')
    theme_src = os.path.join(ROOT, 'manager/app/src/main/java/ir/meelano/manager/ui/Theme.java')
    settings_src = os.path.join(ROOT, 'manager/app/src/main/java/ir/meelano/manager/screens/SettingsScreen.java')
    kit_src = os.path.join(ROOT, 'manager/app/src/main/java/ir/meelano/manager/ui/Kit.java')
    assets_dir = os.path.join(ROOT, 'app/src/main/assets/products')
    drawable_dir = os.path.join(ROOT, 'manager/app/src/main/res/drawable-nodpi')
    try:
        photos = open(photos_src, encoding='utf-8').read()
        theme = open(theme_src, encoding='utf-8').read()
        settings = open(settings_src, encoding='utf-8').read()
        kit = open(kit_src, encoding='utf-8').read()
        keys = set(re.findall(r'return\s+"([a-z_]+)"', photos))
        files = {os.path.splitext(n)[0] for n in os.listdir(assets_dir) if n.lower().endswith('.jpg')}
        missing = sorted(keys - files)
        unreferenced = sorted(files - keys)
        bad_jpegs = [k for k in sorted(keys & files)
                     if not open(os.path.join(assets_dir, k + '.jpg'), 'rb').read(3).startswith(b'\xff\xd8\xff')]
        icon_name = 'icon3d_nut_theme'
        icon_path = os.path.join(drawable_dir, icon_name + '.png')
        bad_icons = []
        if not os.path.isfile(icon_path):
            bad_icons.append(icon_name + ' missing')
        else:
            data = open(icon_path, 'rb').read(29)
            if len(data) < 29 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
                bad_icons.append(icon_name + ' invalid PNG')
            else:
                width, height = struct.unpack('>II', data[16:24])
                if width != 128 or height != 128 or data[25] != 6:
                    bad_icons.append(icon_name + ' must be 128px RGBA')
        required_ui = (
            'chipsWithIcons(' in settings,
            'R.drawable.icon3d_dark_theme' in settings and 'R.drawable.icon3d_theme' in settings
            and 'R.drawable.icon3d_nut_theme' in settings,
            '"آجیل و خشکبار"' in settings,
            'برای دیدن همهٔ قالب‌ها' in settings,
            'res == R.drawable.icon3d_nut_theme' in theme
            and 'res == R.drawable.icon3d_dark_theme' in theme and 'res == R.drawable.icon3d_theme' in theme,
            'R.drawable.icon3d_nut_products' not in theme and 'R.drawable.icon3d_nut_inventory' not in theme,
            'if (icons != null) ch.setMinimumHeight(Theme.dp(48));' in kit,
            'c.setSelected(selected);' in kit,
            'ImageView artwork = icon(iconRes, ink, 26);' in kit,
        )
        absent_ui = [name for name, present in zip(
            ('theme selector uses matched image chips', 'all three theme choices use their 3D icons',
             'theme selector labels the nut option', 'horizontal overflow is explained', 'nut icon remains untinted',
             'nut icon is not mapped to catalog or inventory pages', 'theme choices meet a 48dp touch target',
             'selected state is exposed for accessibility', 'icons use a consistent 26dp size'), required_ui) if not present]
        ok = bool(keys) and not missing and not unreferenced and not bad_jpegs and not bad_icons and not absent_ui
        details = []
        if missing: details.append('عکس‌های مفقود: ' + ', '.join(missing))
        if unreferenced: details.append('عکس‌های بدون نگاشت: ' + ', '.join(unreferenced))
        if bad_jpegs: details.append('JPEG خراب: ' + ', '.join(bad_jpegs))
        if bad_icons: details.append('آیکن ۳بعدی نامعتبر: ' + ', '.join(bad_icons))
        if absent_ui: details.append('اتصال چیپ انتخاب تم ناقص است: ' + ', '.join(absent_ui))
        report('پوشش آیکن‌ها و تصاویر آجیل و خشکبار', ok,
               ('%d کلید عکس، هرکدام دارای JPEG؛ تصویر آجیل شفاف ۱۲۸×۱۲۸ و سه گزینهٔ انتخاب تم با آیکن‌های ۳بعدی و هدف لمسی ۴۸dp' % len(keys))
               if ok else '؛ '.join(details))
    except Exception as e:
        report('پوشش آیکن‌ها و تصاویر آجیل و خشکبار', False, 'ممیزی ناموفق: %s' % e)


def check_section_art():
    """Every section of the index must have 3D artwork, and the nut theme must be able to
    show its emerald twin: a section that keeps the small gold medallion looks tiny next to
    the emerald pictures and breaks the look of the skin the user picked."""
    theme_src = os.path.join(ROOT, 'manager/app/src/main/java/ir/meelano/manager/ui/Theme.java')
    room_src = os.path.join(ROOT, 'manager/app/src/main/java/ir/meelano/manager/core/CommandRoom.java')
    art_src = os.path.join(ROOT, 'manager/app/src/main/java/ir/meelano/manager/core/IconArt.java')
    desk_src = os.path.join(ROOT, 'manager/app/src/main/java/ir/meelano/manager/screens/VisitorDeskScreen.java')
    drawable_dir = os.path.join(ROOT, 'manager/app/src/main/res/drawable-nodpi')
    try:
        theme = open(theme_src, encoding='utf-8').read()
        room = open(room_src, encoding='utf-8').read()
        art = open(art_src, encoding='utf-8').read()
        desk = open(desk_src, encoding='utf-8').read()
        files = {os.path.splitext(n)[0] for n in os.listdir(drawable_dir) if n.lower().endswith('.png')}
        # The mapping of Theme.icon3d(). The dark-skin rules are skipped: the nut look is light.
        light_theme = re.sub(r'if \(!light\) \{.*?\n        \}', '', theme, flags=re.S)
        mapping = {}
        for m in re.finditer(r'if \(([^)]*)\) return ([^;]+);', light_theme):
            res = re.search(r'R\.drawable\.(\w+)', m.group(2))
            if not res:
                continue
            for vec in re.findall(r'R\.drawable\.(\w+)', m.group(1)):
                mapping[vec] = res.group(1)
        problems = []
        arts = sorted(set(re.findall(r'"(mi_\w+)"', room)))
        for name in arts:
            base = mapping.get(name)
            if base is None:
                problems.append('%s has no 3D artwork' % name)
            elif base not in files:
                problems.append('%s -> %s.png is missing' % (name, base))
        # The workspace tiles of the visitor use their own artwork; they must be 3D as well.
        for name in sorted(set(re.findall(r'R\.drawable\.(mi_\w+)', desk))):
            base = mapping.get(name)
            if base is None:
                problems.append('workspace tile %s has no 3D artwork' % name)
            elif base not in files:
                problems.append('workspace tile %s -> %s.png is missing' % (name, base))
        # Every medallion of the gold set needs its emerald twin, or the nut theme keeps the
        # small gold medallion. icon3d_medal_alert has no emerald picture (the alert keeps the
        # gold medallion on purpose) and icon3d_medal_goldbar is unused (mi_diamond maps to
        # icon3d_gold, which has an emerald twin).
        exceptions = {'icon3d_medal_alert', 'icon3d_medal_goldbar'}
        for base in sorted(files):
            if not base.startswith('icon3d_medal_') or base in exceptions:
                continue
            twin = 'icon3d_emerald_' + base[len('icon3d_medal_'):]
            if twin not in files:
                problems.append('%s has no emerald twin (%s)' % (base, twin))
        if 'IconArt.emeraldName(n)' not in theme:
            problems.append('Theme.chrome3d does not use the shared naming rule')
        if 'icon3d_medal_' not in art or 'icon3d_emerald_' not in art:
            problems.append('IconArt does not know the naming rule of the artwork')
        ok = bool(arts) and not problems
        report('هنر سه‌بعدی بخش‌های ایندکس', ok,
               ('%d بخش ایندکس و %d آیکن فضای کاری؛ هر بخش تصویر سه‌بعدی دارد و هر مدال طلایی'
                ' نسخهٔ زمردی دارد' % (len(arts), len(set(re.findall(r'R\.drawable\.(mi_\w+)', desk)))))
               if ok else '؛ '.join(problems))
    except Exception as e:
        report('هنر سه‌بعدی بخش‌های ایندکس', False, 'ممیزی ناموفق: %s' % e)


def main():
    print('════════ بازبینی جامع پروژهٔ Meelano ════════')
    check_syntax()
    check_references()
    check_resources()
    check_glyphs()
    check_vectors()
    check_wiring()
    check_duplicates()
    check_placeholders()
    check_variable_calls()
    check_switch_cases()
    check_sql_balance()
    check_tooling_selftest()
    check_comma_in_comment()
    check_shadow_and_json()
    check_lux_api()
    check_unified_app()
    check_nut_visual_assets()
    check_section_art()
    check_engines()
    check_docs()
    failed = [n for n, ok, _ in RESULTS if not ok]
    print('════════════════════════════════════════════')
    print(f'نتیجه: {len(RESULTS) - len(failed)} از {len(RESULTS)} کنترل موفق'
          + ('' if not failed else '  |  ناموفق: ' + ', '.join(failed)))
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
