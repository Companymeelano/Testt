#!/usr/bin/env python3
"""Check manager query mappings and literal dbo object references against the sample schema.

This is a static compatibility check, not a substitute for connecting to the
customer's production SQL Server. The manifest contains catalog metadata only.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "manager/app/src/main/java/ir/meelano/manager"
DEFAULT_MANIFEST = ROOT / "manager/app/src/test/resources/atiran/sample-schema.json"


def split_args(text: str) -> list[str]:
    result: list[str] = []
    start = depth = 0
    quoted = escaped = False
    for i, ch in enumerate(text):
        if quoted:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                quoted = False
        elif ch == '"':
            quoted = True
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            result.append(text[start:i].strip())
            start = i + 1
    result.append(text[start:].strip())
    return result


def calls(source: str, method: str):
    pattern = re.compile(r"\bm\s*\.\s*" + re.escape(method) + r"\s*\(")
    for match in pattern.finditer(source):
        i = match.end()
        depth = 1
        quoted = escaped = False
        while i < len(source) and depth:
            ch = source[i]
            if quoted:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    quoted = False
            elif ch == '"':
                quoted = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            i += 1
        if depth == 0:
            yield match.start(), split_args(source[match.end():i - 1])


def java_string(value: str) -> str | None:
    if not (len(value) >= 2 and value[0] == '"' and value[-1] == '"'):
        return None
    # Column/table names in these contracts are ASCII or Unicode literals; JSON
    # string escaping is compatible with the Java escapes used by the source.
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return value[1:-1]


def norm(value: str) -> str:
    return "".join(ch for ch in value.casefold() if ch.isalnum())


def dbo_references(source: str) -> list[str]:
    # Drop ordinary Java comments so examples in prose are not mistaken for SQL.
    source = re.sub(r'/\*.*?\*/|//[^\r\n]*', '', source, flags=re.S)
    pattern = re.compile(r'\bdbo\s*\.\s*(?:\[([A-Za-z_][A-Za-z0-9_$#]*)\]|([A-Za-z_][A-Za-z0-9_$#]*))', re.I)
    return [match.group(1) or match.group(2) for match in pattern.finditer(source)]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    args = parser.parse_args()
    if not args.manifest.is_file():
        raise SystemExit(f"Schema manifest not found: {args.manifest}")
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    objects = manifest.get("objects", [])
    by_name: dict[str, list[dict]] = {}
    for obj in objects:
        by_name.setdefault(obj["name"].casefold(), []).append(obj)
    functions_by_name: dict[str, list[dict]] = {}
    for function in manifest.get("functions", []):
        functions_by_name.setdefault(function["name"].casefold(), []).append(function)

    errors: list[str] = []
    skipped = 0
    checked = 0
    function_checked = 0
    function_skipped = 0
    direct_dbo_count = 0
    direct_dbo_names: set[str] = set()
    direct_errors: set[str] = set()
    for path in sorted(SOURCE.rglob("*.java")):
        source = path.read_text(encoding="utf-8")
        for name in dbo_references(source):
            direct_dbo_count += 1
            direct_dbo_names.add(name.casefold())
            hits = [obj for obj in by_name.get(name.casefold(), [])
                    if obj["schema"].casefold() == "dbo"]
            if not hits:
                schemas = sorted({obj["schema"] for obj in by_name.get(name.casefold(), [])})
                detail = ", ".join(schemas) if schemas else "absent"
                direct_errors.add(
                    f"{path.relative_to(ROOT)}: literal dbo.{name} is not in dbo in sample (found: {detail})"
                )
        for offset, args_list in calls(source, "must"):
            if len(args_list) < 3:
                skipped += 1
                continue
            table = java_string(args_list[0])
            if table is None:
                skipped += 1
                continue
            candidates = [java_string(arg) for arg in args_list[2:]]
            candidates = [c for c in candidates if c is not None]
            if not candidates:
                skipped += 1
                continue
            hits = by_name.get(table.casefold(), [])
            # Mirror Meta's resolver: prefer dbo, otherwise use the matching
            # object in its actual schema.
            hits.sort(key=lambda obj: (obj["schema"].casefold() != "dbo", obj["schema"].casefold()))
            line = source.count("\n", 0, offset) + 1
            if not hits:
                errors.append(f"{path.relative_to(ROOT)}:{line}: table {table!r} absent in sample")
                continue
            actual = {column["name"] for column in hits[0].get("columns", [])}
            actual_norm = {norm(column) for column in actual}
            checked += 1
            if not any(candidate.casefold() in {c.casefold() for c in actual}
                       or norm(candidate) in actual_norm for candidate in candidates):
                errors.append(
                    f"{path.relative_to(ROOT)}:{line}: {hits[0]['schema']}.{hits[0]['name']} "
                    f"has none of candidates {candidates}; actual columns: {sorted(actual)}"
                )

    for path in sorted(SOURCE.rglob("*.java")):
        source = path.read_text(encoding="utf-8")
        for offset, args_list in calls(source, "qFunction"):
            name = java_string(args_list[0]) if args_list else None
            if name is None:
                function_skipped += 1
                continue
            line = source.count("\n", 0, offset) + 1
            hits = functions_by_name.get(name.casefold(), [])
            if not any(hit.get("kind", "").upper() == "SQL_SCALAR_FUNCTION" for hit in hits):
                errors.append(
                    f"{path.relative_to(ROOT)}:{line}: scalar function {name!r} absent in sample"
                )
            else:
                function_checked += 1

    for check in manifest.get("appProbeChecks", []):
        if check.get("status") != "ok":
            errors.append(f"sample probe {check.get('table')}: {check.get('status')} {check.get('missingColumns', [])}")
    for check in manifest.get("appFunctionChecks", []):
        if check.get("status") != "ok":
            errors.append(f"sample function probe {check.get('function')}: {check.get('status')}")

    errors.extend(sorted(direct_errors))
    print(f"Atiran sample-schema audit: {checked} literal Meta.must mappings checked; "
          f"{skipped} dynamic/unsupported calls skipped; {function_checked} literal scalar-function calls "
          f"checked ({function_skipped} dynamic skipped); {direct_dbo_count} literal dbo references "
          f"across {len(direct_dbo_names)} identifiers checked; {len(errors)} errors.")
    for error in errors:
        print("SCHEMA-ERROR " + error)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
