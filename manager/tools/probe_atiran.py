#!/usr/bin/env python3
"""Read-only Atiran compatibility probe for MEELANO Manager.

Checks that the key tables/views exist, have rows, and expose the mandatory
columns the app needs (the `Meta.must(...)` set). Only SELECT queries.

Usage:
    pip install pymssql
    python probe_atiran.py --host 192.168.1.50 --db Atiran --user sa2 --password '***'
"""
import argparse
import sys

# table -> [mandatory columns (any-case match)]
TABLES = {
    "sailfact": ["date", "shfacfo", "all", "shmo", "t_date", "tasvieh", "vis_rdf"],
    "buyfact": ["date"],
    "subsailfact": ["shfacfo", "SHKA", "TEDVAH", "LINESUM"],
    "dar": ["ghno", "date", "shmo"],
    "PosDetails": ["ghno", "MabPos", "PosBankRdf"],
    "darDescriptionType": [],
    "getchk": [],
    "putchk": [],
    "CheckTypes": [],
    "CUSTOMERS": ["SHMO", "man"],
    "cust_act": ["shmo", "date", "act_bed", "act_bes"],
    "custgroup": ["group_rdf"],
    "masir": ["rdf_masir"],
    "inventory": ["shka", "naka", "mojkavah"],
    "ka_act": ["shka"],
    "kagroup": ["group_rdf"],
    "anbars": ["rdf_anbar"],
    "inventory_anbars": [],
    "visitors": ["vis_rdf"],
    "vis_goals": [],
    "sys_users": ["user_id"],
    "LoginDetails": ["user_id"],
    "BANK": ["RDF"],
    "ban_act": ["bank_rdf"],
    "ActNames": [],
    "COW": [],
    "dif_date_alan": [],
    "TableChanges": [],
    "Log": [],
}
VIEWS = ["VW_GainDetails", "VW_CustomersGain", "VW_InventoryAnbars", "VW_getchk", "VW_Putchk"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", required=True)
    ap.add_argument("--port", default="1433")
    ap.add_argument("--db", required=True)
    ap.add_argument("--user", required=True)
    ap.add_argument("--password", required=True)
    a = ap.parse_args()

    try:
        import pymssql
    except ImportError:
        print("pymssql is missing: pip install pymssql")
        return 2

    conn = pymssql.connect(server=a.host, port=int(a.port), database=a.db,
                           user=a.user, password=a.password, timeout=15)
    cur = conn.cursor()
    ok_tables = 0
    print("== MEELANO Manager — Atiran probe ==\n-- tables --")
    for t, musts in TABLES.items():
        cur.execute("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                    "WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=%s", (t,))
        exists = cur.fetchone()[0] > 0
        if not exists:
            print(f"[MISS] {t}: not found")
            continue
        cur.execute(f"SELECT COUNT_BIG(1) FROM dbo.[{t}]")
        n = cur.fetchone()[0]
        cur.execute("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                    "WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=%s", (t,))
        cols = {r[0] for r in cur.fetchall()}
        missing = [c for c in musts if c.lower() not in cols]
        ok_tables += 1
        flag = "OK " if not missing else "WARN"
        extra = "" if not missing else f"  MISSING COLS: {', '.join(missing)}"
        print(f"[{flag}] {t}: {n} rows, {len(cols)} cols{extra}")
    print("\n-- views --")
    for v in VIEWS:
        cur.execute("SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS "
                    "WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=%s", (v,))
        print(f"[{'OK ' if cur.fetchone()[0] else 'MISS'}] {v}")
    conn.close()
    print(f"\n{a.db}: {ok_tables}/{len(TABLES)} tables present.")
    print("MISS = that app card will show a Persian 'unavailable' note; app keeps working.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
