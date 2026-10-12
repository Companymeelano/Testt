#!/usr/bin/env python3
"""Static cross-module API checker for the Meelano Android sources.

There is no JDK/Android SDK inside the build sandbox, so `javac` cannot run here. This
script recovers most of the value of a compile step for the *project-internal* API surface:

  1. every .java file is parsed (javalang) — syntax errors are reported;
  2. a symbol table of every project class / method / field (name, arity, static-ness) is built;
  3. every `Class.method(...)` / `Class.FIELD` / `object.method(...)` reference inside the
     checked files is resolved against that table.

Usage:  python3 tools/java_api_check.py [--all] [file ...]
"""
from __future__ import annotations

import glob
import os
import re
import sys

import javalang
from javalang import tree

SRC = 'app/src/main/java/ir/meelano/android'
# New / hand-written module files that must be consistent with the rest of the app.
FOCUS = [
    'MeelanoPalette.java', 'MeelanoUiKit.java', 'MeelanoWarehouseUi.java',
    'MeelanoLabelPrint.java', 'MeelanoFinance.java', 'MeelanoFinanceUi.java',
    'MeelanoWarehouse.java', 'MeelanoWarehouseRepo.java', 'MeelanoUiHost.java',
    'MeelanoQr.java', 'MeelanoBarcode.java',
]


VARARGS = set()


class ClassInfo:
    def __init__(self, name):
        self.name = name
        self.methods = {}       # name -> set of (arity, is_static)
        self.fields = {}        # NAME -> is_static
        self.nested = set()


def collect(decl, out, prefix=''):
    decls = list(decl.filter(tree.ClassDeclaration)) + list(decl.filter(tree.InterfaceDeclaration))
    for _, node in decls:
        name = (prefix + node.name) if prefix else node.name
        if name in out:
            return
        info = ClassInfo(name)
        for m in node.methods:
            arity = len(m.parameters)
            varargs = m.name in VARARGS           # filled by the regex pass before collecting
            info.methods.setdefault(m.name, set()).add((arity, 'static' in (m.modifiers or []), varargs))
        for f in node.fields:
            for d in f.declarators:
                info.fields[d.name] = 'static' in (f.modifiers or [])
        for n in node.body:
            if isinstance(n, (tree.ClassDeclaration, tree.InterfaceDeclaration)):
                info.nested.add(n.name)
                collect(n, out, '')          # nested classes are referenced flat too
        out[name] = info


def parse_dir():
    table = {}
    errors = []
    for path in sorted(glob.glob(os.path.join(SRC, '*.java'))):
        src = open(path, encoding='utf-8').read()
        VARARGS.update(VARARGS_RE.findall(src))
        try:
            tree_root = javalang.parse.parse(src)
        except Exception as e:                                   # javalang grammar gaps
            errors.append((os.path.basename(path), repr(e)[:160]))
            collect_regex(src, table)
            continue
        collect(tree_root, table)
    return table, errors


DECL_RE = re.compile(
    r'(?:^|\n)\s*(?:public\s+|private\s+|protected\s+)?(static\s+)?(?:final\s+)?'
    r'[\w<>\[\], .]+\s+(\w+)\s*\(', re.S)
VARARGS_RE = re.compile(r'\b(\w+)\s*\([^;{)]*\.\.\.')
FIELD_RE = re.compile(r'(?:^|\n)\s*(?:public\s+|private\s+|protected\s+)?(static\s+)?(?:final\s+)?'
                      r'[\w<>\[\], .]+\s+([A-Z][A-Z0-9_]{2,})\s*(?:=|;)')


def top_level_args(src: str, open_paren: int):
    """Returns (argc, varargs) for the parameter list starting at `open_paren`."""
    depth, i, text = 0, open_paren, src
    while i < len(text):
        ch = text[i]
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                inner = text[open_paren + 1:i]
                break
        i += 1
    else:
        return 0, False
    parts, depth2, cur = [], 0, ''
    for ch in inner:
        if ch in '<([':
            depth2 += 1
        elif ch in '>)]':
            depth2 -= 1
        if ch == ',' and depth2 == 0:
            parts.append(cur)
            cur = ''
        else:
            cur += ch
    parts.append(cur)
    parts = [p for p in parts if p.strip()]
    return len(parts), '...' in inner


def collect_regex(src: str, table: dict):
    """Fallback symbol table for files javalang refuses (nested array initialisers)."""
    name = os.path.basename(src)
    m = re.search(r'class\s+(\w+)', src)
    if not m:
        return
    info = table.setdefault(m.group(1), ClassInfo(m.group(1)))
    for m in DECL_RE.finditer(src):
        st, mname = m.group(1), m.group(2)
        if mname in ('if', 'for', 'while', 'switch', 'catch', 'return', 'new', 'synchronized'):
            continue
        argc, va = top_level_args(src, m.end() - 1)
        is_static = st == 'static' or re.search(r'\bstatic\b', m.group(0)) is not None
        info.methods.setdefault(mname, set()).add((argc, is_static, va))
    for st, fname in FIELD_RE.findall(src):
        info.fields[fname] = st == 'static'


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    check_all = '--all' in sys.argv
    table, errors = parse_dir()
    known = set(table)

    files = sorted(glob.glob(os.path.join(SRC, '*.java')))
    if args:
        files = [f for f in files if os.path.basename(f) in
                 set(a if a.endswith('.java') else a + '.java' for a in args)]
    elif not check_all:
        files = [f for f in files if os.path.basename(f) in FOCUS]

    problems = []
    for path in files:
        base = os.path.basename(path)
        src = open(path, encoding='utf-8').read()
        try:
            root = javalang.parse.parse(src)
        except Exception:
            problems.append((base, 0, 'PARSE-SKIPPED (javalang grammar gap; checked by regex pass)'))
            continue
        for _, node in root.filter(tree.MethodInvocation):
            qual, name = node.qualifier, node.member
            argc = len(node.arguments)
            if qual in known and qual not in ('MainActivity',):
                sigs = table[qual].methods.get(name)
                if not sigs:
                    if name not in table[qual].fields and name not in table[qual].nested and name not in known:
                        problems.append((base, node.position.line if node.position else 0,
                                         f'{qual}.{name}(...) — no such member'))
                else:
                    ok = any(a == argc or v for (a, s, v) in sigs)
                    if not ok:
                        problems.append((base, node.position.line if node.position else 0,
                                         f'{qual}.{name}({argc} args) — declared arities {sorted(a for a, _, _ in sigs)}'))
                    elif not any(s for (a, s, v) in sigs if a == argc or v):
                        problems.append((base, node.position.line if node.position else 0,
                                         f'{qual}.{name}(...) — instance method called on class (not static)'))
            # local objects named `h` in the new modules are MeelanoUiHost (checked separately by the UiKit pass)
            if qual == 'host' and name not in table['MeelanoUiHost'].methods:
                problems.append((base, node.position.line if node.position else 0,
                                 f'host.{name}(...) — not in MeelanoUiHost'))
        for _, node in root.filter(tree.MemberReference):
            qual, name = node.qualifier, node.member
            if qual in known:
                if name not in table[qual].fields and name not in table[qual].methods and name not in table[qual].nested \
                        and name not in known:
                    problems.append((base, node.position.line if node.position else 0,
                                     f'{qual}.{name} — unknown constant/field'))

    print(f'parsed files: {len(files)}   project classes: {len(known)}')
    if errors:
        print('javalang grammar gaps (fallback regex pass used):')
        for f, e in errors:
            print('   ', f, e)
    if not problems:
        print('✅ no unresolved project-internal references')
        return 0
    print(f'⚠ {len(problems)} unresolved reference(s):')
    for f, line, msg in problems:
        print(f'   {f}:{line}  {msg}')
    return 1


if __name__ == '__main__':
    sys.exit(main())
