#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Aggregate-only check: which purchase-line formula reproduces the invoice total?

Restores the supplied Atiran .bak in the disposable SQL Server container (same restore path as
probe_atiran_backup.py) and prints COUNTS ONLY: how many invoices each candidate formula matches.
No rows, amounts, names or codes are printed or written to disk.

The sales side is a control: subsailfact.LINESUM is known to exist there, so the method is valid
only if the sales control matches almost every invoice.
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


def counts(container, password, executable, database, sql_text):
    out = base.sql(container, password, executable, sql_text, database=database)
    got = base.rows(out, 3)
    return got[0] if got else []


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

    def check(head, line, link_col, total_col, formulas):
        results = {}
        for name, expr in formulas.items():
            q = f"""
SET NOCOUNT ON;
WITH h AS (
  SELECT TRY_CONVERT(nvarchar(100),[{link_col}]) AS k, TRY_CONVERT(decimal(19,2),[{total_col}]) AS tot
  FROM dbo.[{head}]
), l AS (
  SELECT TRY_CONVERT(nvarchar(100),[{link_col}]) AS k, {expr} AS amt
  FROM dbo.[{line}]
), s AS (
  SELECT k, SUM(amt) AS amt FROM l GROUP BY k
)
SELECT COUNT(*) AS invoices,
       SUM(CASE WHEN ABS(s.amt - h.tot) < 1 THEN 1 ELSE 0 END) AS matched,
       SUM(CASE WHEN h.tot IS NULL OR s.amt IS NULL THEN 1 ELSE 0 END) AS unusable
FROM h JOIN s ON s.k = h.k;"""
            results[name] = counts(args.container, password, executable, args.database, q)
        return results

    purchase = check(
        "buyfact", "subbuyfact", "shfackh", "all",
        {
            "TEDVAH*JOZPRICE": "ISNULL(TRY_CONVERT(decimal(19,4),[TEDVAH])*TRY_CONVERT(decimal(19,4),[JOZPRICE]),0)",
            "TEDJOZ*JOZPRICE": "ISNULL(TRY_CONVERT(decimal(19,4),[TEDJOZ])*TRY_CONVERT(decimal(19,4),[JOZPRICE]),0)",
        },
    )
    sales_control = check(
        "sailfact", "subsailfact", "shfacfo", "all",
        {"LINESUM (control)": "ISNULL(TRY_CONVERT(decimal(19,2),[LINESUM]),0)"},
    )
    report = {"purchase_candidates": purchase, "sales_control": sales_control,
              "columns": ["invoices", "matched", "unusable"], "note": "counts only, no business data"}
    Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
