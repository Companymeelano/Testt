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
  report_file="$(mktemp)"
  python3 - <<'PY' > "$report_file"
from pathlib import Path
from xml.etree import ElementTree

reports = sorted(Path("app/build").rglob("TEST-*.xml"))
summary = []
for report in reports:
    try:
        root = ElementTree.parse(report).getroot()
    except Exception as error:
        summary.append(f"Could not parse {report}: {error}")
        continue
    suites = [root] + [node for node in root.iter("testsuite") if node is not root]
    for suite in suites:
        summary.append(
            f"{report}: {suite.tag} name={suite.get('name', '')} "
            f"tests={suite.get('tests', '?')} failures={suite.get('failures', '?')} "
            f"errors={suite.get('errors', '?')} skipped={suite.get('skipped', '?')}"
        )
    for node in root.iter():
        if node.tag in ("failure", "error"):
            summary.append(f"{node.tag}: {node.get('message', '')} {(node.text or '').strip()[:1800]}")
        elif node.tag == "system-err" and (node.text or '').strip():
            summary.append("system-err: " + (node.text or '').strip()[:1200])
if not summary:
    summary.append("No JUnit XML report was found under app/build.")
print("\n".join(summary)[:8000])
PY
  report_count=0
  while IFS= read -r line; do
    [ "$report_count" -ge 5 ] && break
    [ -n "$line" ] || continue
    safe_line="$(printf '%s' "$line" | sed 's/%/%25/g; s/:/%3A/g')"
    echo "::error title=Screenshot test report::$safe_line"
    report_count=$((report_count + 1))
  done < "$report_file"

  diagnostics="$(grep -E 'FAILURE:|Execution failed|Could not |Caused by:|error:|AssertionFailedError|AssertionError|FAILED$|No tests found|ErrorName:|ErrorCode:|There were failing tests' "$log_file" | tail -n 3 || true)"
  if [ -z "$diagnostics" ]; then diagnostics="$(tail -n 3 "$log_file")"; fi
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    safe_line="$(printf '%s' "$line" | sed 's/%/%25/g; s/:/%3A/g')"
    echo "::error title=Screenshot Gradle diagnostics::$safe_line"
  done <<< "$diagnostics"

  logcat_file="$(mktemp)"
  if adb logcat -d -t 2000 > "$logcat_file" 2>/dev/null; then
    grep -E 'FATAL EXCEPTION|AndroidRuntime|INSTRUMENTATION_STATUS|INSTRUMENTATION_FAILED|AssertionError' "$logcat_file" | tail -n 2 | while IFS= read -r line; do
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
