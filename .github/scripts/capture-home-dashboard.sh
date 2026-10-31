#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root/manager"

log_file="$(mktemp)"
attempt=1
result=1
while [ "$attempt" -le 3 ]; do
  set +e
  gradle :app:connectedDebugAndroidTest -PhomeScreenshotAbi=true -Pandroid.useAndroidX=true --console=plain --stacktrace --no-daemon 2>&1 | tee "$log_file"
  result=${PIPESTATUS[0]}
  set -e
  [ "$result" -eq 0 ] && break
  if [ "$attempt" -ge 3 ] || ! grep -Eq 'Could not resolve|Could not GET|Could not download|Could not find' "$log_file"; then
    break
  fi
  attempt=$((attempt + 1))
  echo "::warning::Screenshot test dependency resolution failed; retrying (${attempt}/3)."
  sleep 15
done

if [ "$result" -ne 0 ]; then
  diagnostics="$(grep -A3 -E 'FAILURE:|Execution failed|Could not |Caused by:|error:|AssertionFailedError|AssertionError|FAILED$|No tests found' "$log_file" | tail -n 24 || true)"
  if [ -z "$diagnostics" ]; then diagnostics="$(tail -n 12 "$log_file")"; fi
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    safe_line="$(printf '%s' "$line" | sed 's/%/%25/g; s/:/%3A/g')"
    echo "::error title=Screenshot instrumentation diagnostics::$safe_line"
  done <<< "$diagnostics"

  report_file="$(mktemp)"
  python3 - <<'PY' > "$report_file"
from pathlib import Path
from xml.etree import ElementTree

reports = sorted(Path("app/build").rglob("TEST-*.xml"))
failures = []
for report in reports:
    try:
        root = ElementTree.parse(report).getroot()
    except Exception as error:
        failures.append(f"Could not parse {report}: {error}")
        continue
    for case in root.iter("testcase"):
        for kind in ("failure", "error"):
            result = case.find(kind)
            if result is None:
                continue
            name = f"{case.get('classname', '')}.{case.get('name', '')}"
            failures.append(f"{report}: {name}: {result.get('message', '')}")
            detail = (result.text or "").strip()
            if detail:
                failures.append(detail[:2500])
            break
if not failures:
    failures.append("No failed JUnit XML case was found. XML reports: "
                    + (", ".join(str(p) for p in reports) or "none"))
print("\n".join(failures)[:8000])
PY
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    safe_line="$(printf '%s' "$line" | sed 's/%/%25/g; s/:/%3A/g')"
    echo "::error title=Screenshot test report::$safe_line"
  done < "$report_file"

  logcat_file="$(mktemp)"
  if adb logcat -d -t 2000 > "$logcat_file" 2>/dev/null; then
    grep -E 'FATAL EXCEPTION|AndroidRuntime|INSTRUMENTATION_STATUS|INSTRUMENTATION_FAILED|AssertionError' "$logcat_file" | tail -n 12 | while IFS= read -r line; do
      safe_line="$(printf '%s' "$line" | sed 's/%/%25/g; s/:/%3A/g')"
      echo "::error title=Screenshot emulator log::$safe_line"
    done || true
  fi
  exit 1
fi

mkdir -p ../design/screenshots
adb pull /sdcard/Android/data/ir.meelano.manager.debug/files/home-dashboard-screenshots/home-dashboard-light.png ../design/screenshots/home-dashboard-light.png
adb pull /sdcard/Android/data/ir.meelano.manager.debug/files/home-dashboard-screenshots/home-dashboard-dark.png ../design/screenshots/home-dashboard-dark.png
test -s ../design/screenshots/home-dashboard-light.png
test -s ../design/screenshots/home-dashboard-dark.png
