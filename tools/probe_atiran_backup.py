#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Restore the supplied Atiran .bak in a disposable SQL Server and export schema only.

The dump intentionally reads no business rows. It records object/column/type/PK/FK
metadata so query mappings can be checked against the supplied sample without
publishing customer, invoice, payment, or stock data.

Run in CI after a SQL Server container named ``atiran-sample`` is ready:
  python3 tools/probe_atiran_backup.py --container atiran-sample \
      --backup /var/opt/mssql/backup/AtiranSample.bak \
      --output manager/app/src/test/resources/atiran/sample-schema.json
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

SQLCMD_CANDIDATES = (
    "/opt/mssql-tools18/bin/sqlcmd",
    "/opt/mssql-tools/bin/sqlcmd",
)
DELIM = "|"


def run(args: list[str], *, check: bool = True, timeout: int = 180,
        env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    p = subprocess.run(args, text=True, capture_output=True, timeout=timeout, env=env)
    if check and p.returncode:
        details = (p.stderr or p.stdout or "(no SQL Server output)").strip()
        # Do not surface a connection URL or password even if a lower layer does.
        details = re.sub(r"(?i)(password|pwd)\s*=\s*[^;\s]+", r"\1=***", details)
        raise RuntimeError(f"command failed ({p.returncode}): {details[-6000:]}")
    return p


def sqlcmd_path(container: str) -> str:
    for candidate in SQLCMD_CANDIDATES:
        p = run(["docker", "exec", container, "test", "-x", candidate], check=False)
        if p.returncode == 0:
            return candidate
    p = run(["docker", "exec", container, "bash", "-lc",
             "find /opt -type f -name sqlcmd 2>/dev/null | head -5"], check=False)
    choices = [x.strip() for x in p.stdout.splitlines() if x.strip()]
    if choices:
        return choices[0]
    raise RuntimeError("sqlcmd was not found in the SQL Server container")


def sql(container: str, password: str, executable: str, statement: str,
        *, timeout: int = 300, database: str | None = None) -> str:
    env = os.environ.copy()
    env["SQLCMDPASSWORD"] = password
    args = [
        "docker", "exec", "-e", "SQLCMDPASSWORD", container, executable,
        "-S", "localhost", "-U", "sa", "-C", "-b",
        "-h", "-1", "-W", "-w", "65535", "-s", DELIM,
    ]
    if database:
        args.extend(["-d", database])
    args.extend(["-Q", statement])
    return run(args, timeout=timeout, env=env).stdout


def rows(stdout: str, expected_fields: int) -> list[list[str]]:
    parsed: list[list[str]] = []
    for line in stdout.splitlines():
        line = line.rstrip("\r\n")
        if not line.strip():
            continue
        if re.fullmatch(r"\(\d+ rows? affected\)", line.strip(), flags=re.IGNORECASE):
            continue
        fields = [part.strip() for part in line.split(DELIM)]
        if len(fields) != expected_fields:
            # sqlcmd can include harmless blank/separator lines; anything else is
            # a format error rather than a silently-corrupted schema manifest.
            if set(line.strip()) <= {"-", " "}:
                continue
            raise RuntimeError(f"unexpected sqlcmd row shape ({len(fields)} fields): {line[:300]}")
        parsed.append(fields)
    return parsed


def sql_lit(value: str) -> str:
    return "N'" + value.replace("'", "''") + "'"


def normalize_identifier(value: str) -> str:
    return "".join(ch.casefold() for ch in value if ch.isalnum())


def restore_sample(container: str, password: str, executable: str, backup: str,
                   database: str) -> None:
    filelist_sql = f"SET NOCOUNT ON; RESTORE FILELISTONLY FROM DISK = {sql_lit(backup)};"
    file_rows = rows(sql(container, password, executable, filelist_sql), 22)
    files = [(r[0], r[2], r[6]) for r in file_rows if r[2] in {"D", "L", "F"}]
    if not files:
        raise RuntimeError("RESTORE FILELISTONLY returned no database files")

    moves: list[str] = []
    data_no = 0
    log_no = 0
    other_no = 0
    for logical, kind, file_id in files:
        if kind == "L":
            path = f"/var/opt/mssql/data/{database}_log_{log_no}.ldf"
            log_no += 1
        elif kind == "D":
            ext = "mdf" if data_no == 0 else "ndf"
            path = f"/var/opt/mssql/data/{database}_data_{data_no}.{ext}"
            data_no += 1
        else:
            path = f"/var/opt/mssql/data/{database}_other_{other_no}.ndf"
            other_no += 1
        moves.append(f"MOVE {sql_lit(logical)} TO {sql_lit(path)}")

    db_ident = "[" + database.replace("]", "]]" ) + "]"
    statement = (
        f"IF DB_ID({sql_lit(database)}) IS NOT NULL BEGIN "
        f"ALTER DATABASE {db_ident} SET SINGLE_USER WITH ROLLBACK IMMEDIATE; "
        f"DROP DATABASE {db_ident}; END; "
        f"RESTORE DATABASE {db_ident} FROM DISK = {sql_lit(backup)} "
        f"WITH REPLACE, RECOVERY, STATS=10, " + ", ".join(moves) + ";"
    )
    sql(container, password, executable, statement, timeout=900)
    print(f"Restored metadata-only probe database from backup ({len(files)} data/log files).")


def load_required_checks() -> tuple[dict[str, list[str]], list[str]]:
    path = Path("manager/tools/probe_atiran.py")
    spec = importlib.util.spec_from_file_location("atiran_probe", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"could not load compatibility checks: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.TABLES, list(module.VIEWS)


def dump_schema(container: str, password: str, executable: str, database: str,
                output: Path, source_backup: str) -> dict:
    column_sql = """
SET NOCOUNT ON;
SELECT
  s.name,
  o.name,
  CASE o.type WHEN 'U' THEN 'TABLE' WHEN 'V' THEN 'VIEW' ELSE o.type_desc END,
  c.column_id,
  c.name,
  TYPE_NAME(c.user_type_id),
  c.max_length,
  c.precision,
  c.scale,
  c.is_nullable,
  c.is_identity,
  CASE WHEN EXISTS (
      SELECT 1 FROM sys.indexes i
      JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
      WHERE i.object_id=o.object_id AND i.is_primary_key=1 AND ic.column_id=c.column_id
  ) THEN 1 ELSE 0 END
FROM sys.objects o
JOIN sys.schemas s ON s.schema_id=o.schema_id
JOIN sys.columns c ON c.object_id=o.object_id
WHERE o.type IN ('U','V') AND o.is_ms_shipped=0
ORDER BY s.name,o.name,c.column_id;
"""
    column_rows = rows(sql(container, password, executable, column_sql, database=database), 12)
    if not column_rows:
        raise RuntimeError(f"database {database!r} returned no user-table/view columns; refusing an empty manifest")
    objects: dict[tuple[str, str], dict] = {}
    for r in column_rows:
        schema, name, kind = r[0], r[1], r[2]
        key = (schema, name)
        obj = objects.setdefault(key, {"schema": schema, "name": name,
                                       "kind": kind, "columns": []})
        obj["columns"].append({
            "ordinal": int(r[3]),
            "name": r[4],
            "type": r[5],
            "maxLengthBytes": int(r[6]),
            "precision": int(r[7]),
            "scale": int(r[8]),
            "nullable": r[9] == "1",
            "identity": r[10] == "1",
            "primaryKey": r[11] == "1",
        })

    fk_sql = """
SET NOCOUNT ON;
SELECT ps.name,pt.name,pc.name,rs.name,rt.name,rc.name,fk.name
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
JOIN sys.tables pt ON pt.object_id=fkc.parent_object_id
JOIN sys.schemas ps ON ps.schema_id=pt.schema_id
JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id
JOIN sys.tables rt ON rt.object_id=fkc.referenced_object_id
JOIN sys.schemas rs ON rs.schema_id=rt.schema_id
JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id
WHERE fk.is_ms_shipped=0
ORDER BY ps.name,pt.name,fk.name,fkc.constraint_column_id;
"""
    foreign_keys = []
    for r in rows(sql(container, password, executable, fk_sql, database=database), 7):
        foreign_keys.append({
            "from": {"schema": r[0], "table": r[1], "column": r[2]},
            "to": {"schema": r[3], "table": r[4], "column": r[5]},
            "name": r[6],
        })

    function_sql = """
SET NOCOUNT ON;
SELECT s.name,o.name,o.type_desc,
       COALESCE(p.parameter_id,-1),COALESCE(p.name,N''),
       COALESCE(TYPE_NAME(p.user_type_id),N''),COALESCE(p.max_length,0),
       COALESCE(p.precision,0),COALESCE(p.scale,0),COALESCE(p.is_output,0)
FROM sys.objects o
JOIN sys.schemas s ON s.schema_id=o.schema_id
LEFT JOIN sys.parameters p ON p.object_id=o.object_id
WHERE o.type IN ('FN','IF','TF','FS','FT') AND o.is_ms_shipped=0
ORDER BY s.name,o.name,p.parameter_id;
"""
    functions: dict[tuple[str, str], dict] = {}
    for r in rows(sql(container, password, executable, function_sql, database=database), 10):
        key = (r[0], r[1])
        fn = functions.setdefault(key, {"schema": r[0], "name": r[1],
                                        "kind": r[2], "parameters": []})
        if r[3] != "-1":
            fn["parameters"].append({
                "ordinal": int(r[3]),
                "name": r[4],
                "type": r[5],
                "maxLengthBytes": int(r[6]),
                "precision": int(r[7]),
                "scale": int(r[8]),
                "output": r[9] == "1",
            })

    by_name = {(schema.lower(), name.lower()): obj for (schema, name), obj in objects.items()}

    def resolve_object(name: str) -> dict | None:
        obj = by_name.get(("dbo", name.lower()))
        if obj:
            return obj
        matches = [value for (schema, object_name), value in by_name.items()
                   if object_name == name.lower()]
        return sorted(matches, key=lambda value: value["schema"].lower())[0] if matches else None

    required, views = load_required_checks()
    # dif_date_alan is a scalar function, not a table; older probe versions listed it as one.
    required = {name: columns for name, columns in required.items()
                if name.lower() != "dif_date_alan"}
    known_checks = []
    for table, wanted in required.items():
        obj = resolve_object(table)
        if not obj:
            known_checks.append({"table": table, "status": "missing-table",
                                 "missingColumns": list(wanted)})
            continue
        actual = {normalize_identifier(c["name"]) for c in obj["columns"]}
        missing = [c for c in wanted if normalize_identifier(c) not in actual]
        known_checks.append({"table": table, "schema": obj["schema"],
                             "status": "ok" if not missing else "missing-columns",
                             "missingColumns": missing})
    for view in views:
        obj = resolve_object(view)
        known_checks.append({"view": view, "schema": obj["schema"] if obj else None,
                             "status": "ok" if obj and obj["kind"] == "VIEW" else "missing-view"})

    function_by_name = {(schema.lower(), name.lower()): fn for (schema, name), fn in functions.items()}
    app_function_checks = []
    for function in ("dif_date_alan", "UDF_Persian_To_Julian"):
        found = next((function_by_name[(schema.lower(), function.lower())]
                      for schema in ("dbo", "")
                      if (schema.lower(), function.lower()) in function_by_name), None)
        if found is None:
            found = next((fn for (schema, name), fn in functions.items()
                          if name.lower() == function.lower()), None)
        app_function_checks.append({
            "function": function,
            "status": "ok" if found else "missing-function",
            "schema": found["schema"] if found else None,
            "kind": found["kind"] if found else None,
        })

    manifest = {
        "formatVersion": 1,
        "source": {
            "backupArchive": source_backup,
            "restoredDatabase": database,
            "scope": "schema metadata only; no business rows exported",
            "schemaResolution": "dbo preferred when ambiguous; actual schema recorded for every object",
        },
        "objectCount": len(objects),
        "columnCount": sum(len(obj["columns"]) for obj in objects.values()),
        "foreignKeyCount": len(foreign_keys),
        "functionCount": len(functions),
        "objects": sorted(objects.values(), key=lambda x: (x["schema"].lower(), x["name"].lower())),
        "foreignKeys": foreign_keys,
        "functions": sorted(functions.values(), key=lambda x: (x["schema"].lower(), x["name"].lower())),
        "appProbeChecks": known_checks,
        "appFunctionChecks": app_function_checks,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    good = sum(1 for row in known_checks if row["status"] == "ok")
    bad = len(known_checks) - good
    print(f"Schema export: {manifest['objectCount']} objects, {manifest['columnCount']} columns, "
          f"{manifest['foreignKeyCount']} foreign keys, {manifest['functionCount']} functions; "
          f"app probe checks {good}/{len(known_checks)} OK, {bad} warnings.")
    for row in app_function_checks:
        if row["status"] != "ok":
            print(f"SCHEMA-WARN function {row['function']}: {row['status']}")
    for row in known_checks:
        if row["status"] != "ok":
            name = row.get("table", row.get("view", "?"))
            print(f"SCHEMA-WARN {name}: {row['status']} {row.get('missingColumns', [])}")
    return manifest


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--container", default="atiran-sample")
    ap.add_argument("--backup", required=True, help="backup file path inside SQL Server container")
    ap.add_argument("--database", default="AtiranSample")
    ap.add_argument("--output", required=True)
    ap.add_argument("--source-archive", default="Atiran2-1405-7-14-15-21-49.zip")
    args = ap.parse_args()
    password = os.environ.get("ATIRAN_SQL_PASSWORD", "")
    if not password:
        print("ATIRAN_SQL_PASSWORD is not set", file=sys.stderr)
        return 2
    try:
        executable = sqlcmd_path(args.container)
        # A few container images take a minute or more to initialize SQL Server.
        for attempt in range(90):
            try:
                sql(args.container, password, executable, "SELECT 1;")
                break
            except Exception:
                if attempt == 89:
                    raise
                time.sleep(2)
        restore_sample(args.container, password, executable, args.backup, args.database)
        dump_schema(args.container, password, executable, args.database,
                    Path(args.output), args.source_archive)
        return 0
    except Exception as exc:
        print(f"Atiran schema probe failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
