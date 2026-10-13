#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Counts-only check of the receipt/payment structure in the attached Atiran backup.

Restores the backup with the same path as probe_atiran_backup.py and prints COUNTS ONLY.
No rows, amounts, names, codes or user fields are printed or written.
Answers: which candidate totals reproduce dar.mab; how dar.p lines up with the voucher tables;
how cheques link to dar; the IsHavaleh value categories.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import probe_atiran_backup as base  # noqa: E402

K = "TRY_CONVERT(nvarchar(60),{})"
D = "ISNULL(TRY_CONVERT(decimal(19,2),{}),0)"
FLAG_TRUE = "N'1',N'T',N'TRUE',N'Y',N'YES'"

SQL = {
    "dar_rows": "SELECT COUNT(*) FROM dbo.dar;",
    "dar_p_values": "SELECT CAST(p AS nvarchar(20)) AS pv, COUNT(*) AS n FROM dbo.dar GROUP BY p ORDER BY p;",
    "posdetails_isHavaleh_values": (
        "SELECT CAST(IsHavaleh AS nvarchar(20)) AS fv, COUNT(*) AS n FROM dbo.PosDetails GROUP BY IsHavaleh ORDER BY 1;"),
    # Total reconstruction. Each variant is compared with dar.mab within 1 rial, joining on the full key.
    "mab_reconstruction": f"""
WITH d AS (SELECT {K.format('ghno')} gh, {K.format('p')} pp, {K.format('Rdf_')} rr,
    {D.format('naghd')} n, {D.format('mabcheck')} c, {D.format('mab')} t FROM dbo.dar),
pd AS (SELECT {K.format('ghno')} gh, {K.format('p')} pp, {K.format('Rdf_')} rr,
    SUM({D.format('MabPos')}) pos,
    SUM(CASE WHEN UPPER(LTRIM(RTRIM(COALESCE(CAST(IsHavaleh AS nvarchar(20)),N'')))) IN ({FLAG_TRUE})
             THEN {D.format('MabPos')} ELSE 0 END) posHav
  FROM dbo.PosDetails GROUP BY {K.format('ghno')}, {K.format('p')}, {K.format('Rdf_')}),
hv AS (SELECT {K.format('ghno')} gh, {K.format('p')} pp, {K.format('Rdf_')} rr,
    SUM({D.format('mab')}) hsum FROM dbo.havaleh GROUP BY {K.format('ghno')}, {K.format('p')}, {K.format('Rdf_')})
SELECT COUNT(*) AS docs,
  SUM(CASE WHEN ABS(d.n+d.c-d.t)<1 THEN 1 ELSE 0 END) AS v1_naghd_plus_cheque,
  SUM(CASE WHEN ABS(d.n+d.c+ISNULL(pd.pos,0)-d.t)<1 THEN 1 ELSE 0 END) AS v2_plus_posAll,
  SUM(CASE WHEN ABS(d.n+d.c+ISNULL(pd.posHav,0)-d.t)<1 THEN 1 ELSE 0 END) AS v3_plus_posHavalehFlag,
  SUM(CASE WHEN ABS(d.n+d.c+ISNULL(hv.hsum,0)-d.t)<1 THEN 1 ELSE 0 END) AS v4_plus_havalehTable,
  SUM(CASE WHEN ABS(d.n+d.c+ISNULL(pd.pos,0)+ISNULL(hv.hsum,0)-d.t)<1 THEN 1 ELSE 0 END) AS v5_all_three,
  SUM(CASE WHEN ABS(d.n+d.c+ISNULL(pd.posHav,0)+ISNULL(hv.hsum,0)-d.t)<1 THEN 1 ELSE 0 END) AS v6_flagPos_plus_havaleh
FROM d LEFT JOIN pd ON pd.gh=d.gh AND pd.pp=d.pp AND pd.rr=d.rr
LEFT JOIN hv ON hv.gh=d.gh AND hv.pp=d.pp AND hv.rr=d.rr;""",
    # dar.p against the two voucher tables (ghabz_e_daryaft = receipt, ghabz_e_pardakht = payment).
    "p_vs_voucher_tables": f"""
WITH d AS (SELECT {K.format('ghno')} gh, {K.format('p')} pp, {K.format('Rdf_')} rr, {D.format('mab')} t FROM dbo.dar)
SELECT 'ghabz_e_daryaft' AS tbl, d.pp AS pv, COUNT(*) AS pairs,
  SUM(CASE WHEN ABS({D.format('g.mab')}-d.t)<1 THEN 1 ELSE 0 END) AS mab_equal
FROM d JOIN dbo.ghabz_e_daryaft g ON {K.format('g.ghno')}=d.gh GROUP BY d.pp
UNION ALL
SELECT 'ghabz_e_pardakht', d.pp, COUNT(*),
  SUM(CASE WHEN ABS({D.format('g.mab')}-d.t)<1 THEN 1 ELSE 0 END)
FROM d JOIN dbo.ghabz_e_pardakht g ON {K.format('g.ghno')}=d.gh GROUP BY d.pp;""",
    # Cheques: getchk / putchk against dar.mabcheck, by full key vs ghno-only.
    "cheques_link": f"""
WITH d AS (SELECT {K.format('ghno')} gh, {K.format('p')} pp, {K.format('Rdf_')} rr, {D.format('mabcheck')} c, {D.format('ted_chk')} tc FROM dbo.dar),
gk AS (SELECT {K.format('ghno')} gh, {K.format('p')} pp, {K.format('Rdf_')} rr, SUM({D.format('getchkmab')}) amt, COUNT(*) n
       FROM dbo.getchk GROUP BY {K.format('ghno')}, {K.format('p')}, {K.format('Rdf_')})
SELECT 'getchk_fullkey' AS k, COUNT(*) AS docs_with_rows,
  SUM(CASE WHEN ABS(gk.amt-d.c)<1 THEN 1 ELSE 0 END) AS amount_equal,
  SUM(CASE WHEN gk.n=d.tc THEN 1 ELSE 0 END) AS count_equal
FROM d JOIN gk ON gk.gh=d.gh AND gk.pp=d.pp AND gk.rr=d.rr;""",
    "putchk_link": f"""
WITH d AS (SELECT {K.format('ghno')} gh, {K.format('Rdf_')} rr, {D.format('mabcheck')} c FROM dbo.dar),
pk AS (SELECT {K.format('ghno')} gh, {K.format('Rdf_')} rr, SUM({D.format('putchkmab')}) amt FROM dbo.putchk
       GROUP BY {K.format('ghno')}, {K.format('Rdf_')})
SELECT 'putchk_ghno_rdf' AS k, COUNT(*) AS docs_with_rows,
  SUM(CASE WHEN ABS(pk.amt-d.c)<1 THEN 1 ELSE 0 END) AS amount_equal
FROM d JOIN pk ON pk.gh=d.gh AND pk.rr=d.rr;""",
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--container", default="atiran-sample")
    ap.add_argument("--backup", required=True)
    ap.add_argument("--database", default="AtiranSample")
    ap.add_argument("--output", required=True)
    args = ap.parse_args()
    password = os.environ.get("ATIRAN_SQL_PASSWORD", "")
    if not password:
        print("ATIRAN_SQL_PASSWORD is not set", file=sys.stderr)
        return 2
    executable = base.sqlcmd_path(args.container)
    for attempt in range(90):
        try:
            base.sql(args.container, password, executable, "SELECT 1;")
            break
        except Exception:
            if attempt == 89:
                raise
            time.sleep(2)
    base.restore_sample(args.container, password, executable, args.backup, args.database)
    report = {"note": "counts only, no business data"}
    for name, q in SQL.items():
        out = base.sql(args.container, password, executable, "SET NOCOUNT ON;\n" + q, database=args.database)
        # Field count is inferred from the first row; sqlcmd tab-free delimiter keeps it exact.
        first = [ln for ln in out.splitlines() if ln.strip() and "|" in ln]
        width = first[0].count("|") + 1 if first else 1
        report[name] = [r for r in base.rows(out, width)] if first else []
    Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
