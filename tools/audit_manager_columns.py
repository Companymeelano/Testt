#!/usr/bin/env python3
# Read-only: every literal m.col / m.colFlex column candidate vs the attached Atiran sample schema.
# Mismatches = columns that silently become null at runtime. Run: python3 tools/audit_manager_columns.py
# Requires tools/audit_manager_schema.py (imported for its call parser).
import json,re,sys
from pathlib import Path
sys.path.insert(0,'tools')
import sys; sys.path.insert(0, str(Path(__file__).resolve().parent)); import audit_manager_schema as A
d=json.load(open(A.ROOT/'manager/app/src/test/resources/atiran/sample-schema.json',encoding='utf8'))
objs={}
for o in d['objects']:
    objs.setdefault(o['name'].casefold(),[]).append(o)
def cols_of(t):
    hits=objs.get(t.casefold(),[])
    if not hits: return None
    hits.sort(key=lambda o:(o['schema'].casefold()!='dbo',o['schema']))
    return {c['name'] for c in hits[0].get('columns',[])}
rows=[]
dynamic_skips=0
for path in sorted(A.SOURCE.rglob('*.java')):
    src=path.read_text(encoding='utf-8')
    for method in ('col','colFlex'):
        for off,args in A.calls(src,method):
            if len(args)<2: continue
            t=A.java_string(args[0])
            if t is None: continue
            cands=[A.java_string(a) for a in args[1:]]
            cands=[c for c in cands if c is not None]
            if not cands:
                # Calls with an attribute/loop variable/array expression cannot be
                # checked as literal candidates; report the skip, not a false mismatch.
                dynamic_skips += 1
                continue
            line=src.count('\n',0,off)+1
            actual=cols_of(t)
            if actual is None:
                rows.append((str(path.relative_to(A.ROOT)),line,method,t,'TABLE ABSENT',cands))
                continue
            ok=any(c in actual or any(c.casefold()==a.casefold() for a in actual) for c in cands)
            if not ok: rows.append((str(path.relative_to(A.ROOT)).split('/')[-1],line,method,t,'NO CANDIDATE',cands))
for r in rows: print(r)
print('dynamic calls skipped:',dynamic_skips)
print('total mismatches:',len(rows))
