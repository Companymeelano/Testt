#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""کشف خطاهای واقعی javac که javalang از آن‌ها رد نمی‌شود:

۱) «متغیر محلی تکراری»: variable x is already defined in method m(...)
۲) isEmpty() روی JSONArray/JSONObject — در org.json اندروید چنین متدی نیست.

اجرا:  python3 tools/shadow_check.py [مسیر…]
خروجی: فهرست HIT‌ها. کد بازگشت همیشه ۰ (قضاوت با verify_all).
"""
import io, os, sys

try:
    import javalang
except Exception as e:  # pragma: no cover
    print("javalang missing:", e)
    sys.exit(2)

SCOPE_TYPES = ("BlockStatement", "ForStatement", "ForControl", "WhileStatement", "DoStatement",
               "TryStatement", "TryResource", "IfStatement", "SwitchStatement", "SwitchStatementCase",
               "SynchronizedStatement", "CatchClause", "LambdaExpression")
METHOD_TYPES = ("MethodDeclaration", "ConstructorDeclaration", "InitializerDeclaration")
CLASS_TYPES = ("ClassDeclaration", "InterfaceDeclaration", "EnumDeclaration",
               "EnumConstantDeclaration", "AnnotationDeclaration")


def walk(node, parent=None, out=None):
    if out is None:
        out = []
    out.append((node, parent))
    if isinstance(node, (list, tuple, set)):
        for c in node:
            if hasattr(c, "attrs"):
                walk(c, node, out)
    else:
        for name in getattr(node, "attrs", []):
            v = getattr(node, name, None)
            if isinstance(v, (list, tuple, set)):
                for c in v:
                    if hasattr(c, "attrs"):
                        walk(c, node, out)
            elif hasattr(v, "attrs"):
                walk(v, node, out)
    return out


def tnode(n):
    return type(n).__name__


def type_name(t):
    """نام نوع از گرهٔ javalang (Type/ReferenceType/BasicType)."""
    if t is None:
        return None
    tname = getattr(t, "name", None)
    if tname:
        return tname
    tname = getattr(t, "sub_type", None)
    return tname


def collect(path):
    errs = []
    src = io.open(path, encoding="utf-8").read()
    tree = javalang.parse.parse(src)
    pairs = walk(tree)
    parent_of = {}
    for n, p in pairs:
        parent_of[id(n)] = p
    nodes = [n for n, _ in pairs]

    def ancestors(n):
        ids = set()
        cur = parent_of.get(id(n))
        while cur is not None:
            ids.add(id(cur))
            cur = parent_of.get(id(cur))
        return ids

    method_cache = {}

    def method_of(n):
        key = id(n)
        if key in method_cache:
            return method_cache[key]
        chain = []
        cur = parent_of.get(key)
        res = None
        while cur is not None:
            if tnode(cur) in METHOD_TYPES:
                res = cur
                break
            chain.append(id(cur))
            cur = parent_of.get(id(cur))
        for cid in chain:
            method_cache[cid] = res
        method_cache[key] = res
        return res

    def scope_of(n):
        cur = parent_of.get(id(n))
        while cur is not None:
            if tnode(cur) in SCOPE_TYPES or tnode(cur) in METHOD_TYPES:
                return cur
            cur = parent_of.get(id(cur))
        return None


    def pos_of(n, owner=None):
        return getattr(n, "position", None) or (getattr(owner, "position", None) if owner is not None else None)

    # ------------------------------------------------ ۱) متغیر محلی تکراری
    # قاعدهٔ javac: «variable x is already defined» هر جا دو اعلان هم‌نام با دامنهٔ تودرتو
    # (یا یکی) باشند رخ می‌دهد؛ جدا بودن بلوک‌های خواهر مجاز است.
    groups = {}
    for n in nodes:
        decl_node, owner = None, None
        if tnode(n) == "VariableDeclarator":
            par = parent_of.get(id(n))
            if par is None or tnode(par) not in ("LocalVariableDeclaration", "VariableDeclaration"):
                continue
            if tnode(par) == "VariableDeclaration" and tnode(parent_of.get(id(par))) != "ForControl":
                continue
            decl_node, owner = n, par
        elif tnode(n) == "FormalParameter":
            decl_node, owner = n, n
        elif tnode(n) == "CatchClauseParameter":
            decl_node, owner = n, n
        if decl_node is None:
            continue
        m = method_of(n)
        if m is None or pos_of(decl_node, owner) is None:
            continue
        groups.setdefault((id(m), decl_node.name), []).append((decl_node, owner))

    scope_cache = {}

    def scope_chain(n):
        key = id(n)
        if key in scope_cache:
            return scope_cache[key]
        chain = []
        cur = scope_of(n)
        while cur is not None:
            chain.append(cur)
            cur = scope_of(cur)
        res = tuple(id(c) for c in chain)
        scope_cache[key] = res
        return res

    # قاعدهٔ دقیق javac: خطا وقتی رخ می‌دهد که اعلان دیرتر، داخل «دامنهٔ» اعلان پیشین بیفتد
    # (اعلان داخلی که پیش از اعلان بیرونی نوشته شده خطا نیست).
    for (mid, name), items in groups.items():
        if len(items) < 2:
            continue
        items = sorted(items, key=lambda it: (pos_of(it[0], it[1]).line, pos_of(it[0], it[1]).column))
        for i in range(len(items)):
            s1 = scope_of(items[i][0])
            c1 = set(scope_chain(items[i][0]))
            for j in range(i + 1, len(items)):
                s2 = scope_of(items[j][0])
                c2 = set(scope_chain(items[j][0]))
                conflict = (s1 is s2) or (s1 is not None and id(s1) in c2)
                if conflict:
                    p1 = pos_of(items[i][0], items[i][1])
                    p2 = pos_of(items[j][0], items[j][1])
                    errs.append("%s:%d «%s» دوباره تعریف شده (نیز خط %d)" %
                                (os.path.basename(path), p1.line, name, p2.line))

    # ------------------------------------------------ ۲) isEmpty() روی JSON
    field_types = {}
    for n in nodes:
        if tnode(n) == "FieldDeclaration":
            for d in n.declarators:
                field_types[d.name] = type_name(n.type)
    per_method = {}
    all_decls = []
    for n in nodes:
        if tnode(n) == "VariableDeclarator":
            par = parent_of.get(id(n))
            if par is not None and tnode(par) in ("LocalVariableDeclaration", "VariableDeclaration") and n.position:
                all_decls.append((n.position.line, n.name, type_name(par.type), n))
        elif tnode(n) == "FormalParameter" and n.position:
            all_decls.append((n.position.line, n.name, type_name(n.type), n))
        elif tnode(n) == "CatchClauseParameter" and n.position:
            all_decls.append((n.position.line, n.name, None, n))
    for n in nodes:
        mm = method_of(n)
        if mm is not None:
            per_method.setdefault(id(mm), ([], mm))[0].append(n)
    for m in [n for n in nodes if tnode(n) in METHOD_TYPES]:
        mnodes = list(per_method.get(id(m), ([], m))[0]) + [m]
        mset = set(id(x) for x in mnodes)
        decls = [(ln, nm, t) for ln, nm, t, n in all_decls if id(n) in mset]
        for n in mnodes:
            if tnode(n) != "MethodInvocation":
                continue
            if n.member != "isEmpty" or n.arguments or not n.qualifier or n.position is None:
                continue
            q = n.qualifier.split(".")[-1].strip("()")
            cands = [t for ln, nm, t in decls if nm == q and ln <= n.position.line]
            if cands:
                t = cands[-1]
            elif q in field_types:
                t = field_types[q]
            else:
                t = None
            if t in ("JSONArray", "JSONObject"):
                errs.append("%s:%d %s.isEmpty() — org.json اندروید isEmpty ندارد (length()/has() استفاده کن)" %
                            (os.path.basename(path), n.position.line, q))
    return errs


def main(argv):
    roots = argv or [os.path.join("app", "src", "main", "java", "ir", "meelano", "android")]
    files = []
    for r in roots:
        if os.path.isfile(r):
            files.append(r)
        else:
            for d, _, fs in os.walk(r):
                for f in fs:
                    if f.endswith(".java"):
                        files.append(os.path.join(d, f))
    hits = 0
    skipped = []
    for f in sorted(files):
        try:
            for line in collect(f):
                print("HIT", line)
                hits += 1
        except Exception as ex:
            skipped.append("%s (%s)" % (os.path.basename(f), ex))
    if skipped:
        print("PARSE-SKIPPED:", "; ".join(skipped))
    print("shadow/json check: %d hit(s) / %d file(s)" % (hits, len(files)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
