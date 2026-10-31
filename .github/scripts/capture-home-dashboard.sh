#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root/manager"
emulator_abi="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
emulator_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "::notice title=Dashboard emulator::API ${emulator_api}, ABI ${emulator_abi}"

log_file="$(mktemp)"
attempt=1
result=1
while [ "$attempt" -le 3 ]; do
  set +e
  gradle :app:connectedDebugAndroidTest -PhomeScreenshotAbi=true -Pandroid.useAndroidX=true --console=plain --info --stacktrace --no-daemon 2>&1 | tee "$log_file"
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

  device_diagnostics="$(grep -Ei 'Skipping device|Unknown API|No compatible devices|Found [0-9]+ connected|incompatible|ABI|API Level|ShellCommandUnresponsive|properties for device' "$log_file" | tail -n 4 || true)"
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    safe_line="$(printf '%s' "$line" | sed 's/%/%25/g; s/:/%3A/g')"
    echo "::error title=Screenshot device compatibility::$safe_line"
  done <<< "$device_diagnostics"

  diagnostics="$(grep -E 'FAILURE:|Execution failed|Could not |Caused by:|error:|AssertionFailedError|AssertionError|FAILED$|No tests found|ErrorName:|ErrorCode:|There were failing tests' "$log_file" | tail -n 2 || true)"
  if [ -z "$diagnostics" ]; then diagnostics="$(tail -n 2 "$log_file")"; fi
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
screenshot_dir="files/home-dashboard-screenshots"
for theme in light dark; do
  if ! adb exec-out run-as ir.meelano.manager.debug cat "$screenshot_dir/home-dashboard-$theme.png" > "../design/screenshots/home-dashboard-$theme.png"; then
    app_files="$(adb shell run-as ir.meelano.manager.debug ls -laR "$screenshot_dir" 2>&1 | tr '\r\n' ' ' || true)"
    echo "::error title=$theme screenshot extraction failed::Could not read the app-private PNG. run-as output: $app_files"
    exit 1
  fi
  if [ ! -s "../design/screenshots/home-dashboard-$theme.png" ]; then
    echo "::error title=$theme screenshot is empty::App-private screenshot output was empty."
    exit 1
  fi
done
