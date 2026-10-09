#!/usr/bin/env python3
"""Read-only Atiran compatibility probe for MEELANO Manager.

Checks that the key tables/views exist, have rows, and expose the mandatory
columns the app needs (the `Meta.must(...)` set). Only SELECT queries.

Usage:
    pip install pymssql
    python probe_atiran.py --host 192.168.1.50 --db Atiran --user sa2 --password '***'
    python probe_atiran.py --host ... --db ... --user ... --password ... --auth-dump

--auth-dump prints ONLY the login/role metadata the app's user-login needs:
table presence, column names, row counts, role rows (id+name) and the
user_password byte-length distribution. It NEVER prints password bytes.
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
    ap.add_argument("--auth-dump", action="store_true",
                    help="dump login/role metadata for the app login (read-only)")
    a = ap.parse_args()

    try:
        import pymssql
    except ImportError:
        print("pymssql is missing: pip install pymssql")
        return 2

    conn = pymssql.connect(server=a.host, port=int(a.port), database=a.db,
                           user=a.user, password=a.password, timeout=15)
    cur = conn.cursor()
    if a.auth_dump:
        return auth_dump(conn, cur, a.db)
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


AUTH_TABLES = ["sys_users", "Roles", "role", "UserRole", "UserAppAccess",
               "AppAccess", "AppAccessTypes", "LoginDetails", "DeviceUser"]


def auth_dump(conn, cur, db):
    """Read-only login/role recon for MEELANO Manager v26 user login."""
    print("== MEELANO Manager — auth dump (read-only, no secrets) ==\n-- tables --")
    have = {}
    for t in AUTH_TABLES:
        try:
            cur.execute("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        "WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=%s", (t,))
            exists = cur.fetchone()[0] > 0
        except Exception as e:
            print(f"[ERR ] {t}: {e}")
            continue
        have[t] = exists
        if not exists:
            print(f"[MISS] {t}")
            continue
        cur.execute(f"SELECT COUNT_BIG(1) FROM dbo.[{t}]")
        n = cur.fetchone()[0]
        cur.execute("SELECT COLUMN_NAME + ':' + DATA_TYPE "
                    "FROM INFORMATION_SCHEMA.COLUMNS "
                    "WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=%s "
                    "ORDER BY ORDINAL_POSITION", (t,))
        cols = [r[0] for r in cur.fetchall()]
        print(f"[OK  ] {t}: {n} rows\n      cols: {', '.join(cols)}")
    if have.get("sys_users"):
        print("\n-- sys_users sample (id / username / active / role) --")
        try:
            cur.execute("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                        "WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='sys_users'")
            cols = {r[0].lower() for r in cur.fetchall()}
            idc = next((c for c in cols if c in ("user_id", "userid", "id")), None)
            unc = next((c for c in cols if c in ("user_name", "username", "name")), None)
            acc = next((c for c in cols if c in ("active", "isactive")), None)
            rlc = next((c for c in cols if c in ("role_id", "roleid")), None)
            sel = ", ".join(x for x in [idc, unc, acc, rlc] if x) or "TOP 0 *"
            cur.execute(f"SELECT TOP 20 {sel} FROM dbo.sys_users")
            for r in cur.fetchall():
                print("      " + " | ".join("" if v is None else str(v) for v in r))
        except Exception as e:
            print(f"      [ERR] {e}")
        print("\n-- user_password byte-length distribution (lengths only!) --")
        try:
            cur.execute("SELECT DATALENGTH(user_password) AS L, COUNT_BIG(1) AS N "
                        "FROM dbo.sys_users GROUP BY DATALENGTH(user_password)")
            for length, n in cur.fetchall():
                print(f"      len={length}: {n} users")
        except Exception as e:
            print(f"      [ERR] {e}")
    for t in ("Roles", "role"):
        if have.get(t):
            print(f"\n-- {t} rows (id + name auto-detected) --")
            try:
                cur.execute("SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                            "WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=%s "
                            "ORDER BY ORDINAL_POSITION", (t,))
                spec = [(r[0], r[1].lower()) for r in cur.fetchall()]
                idc = next((c for c, d in spec if "int" in d), spec[0][0] if spec else None)
                nmc = next((c for c, d in spec
                            if d in ("nvarchar", "varchar", "nchar", "char", "ntext", "text")),
                           spec[-1][0] if spec else None)
                print(f"      using {idc} / {nmc}")
                cur.execute(f"SELECT [{idc}], [{nmc}] FROM dbo.[{t}] ORDER BY 1")
                for r in cur.fetchall():
                    print(f"      {r[0]} | {r[1]}")
            except Exception as e:
                print(f"      [ERR] {e}")
    if have.get("UserAppAccess"):
        print("\n-- UserAppAccess sample --")
        try:
            cur.execute("SELECT TOP 20 * FROM dbo.UserAppAccess")
            for r in cur.fetchall():
                print("      " + " | ".join("" if v is None else str(v) for v in r))
        except Exception as e:
            print(f"      [ERR] {e}")
    conn.close()
    print(f"\n{db}: auth dump done. Send this output to the developer to tune role mapping.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
