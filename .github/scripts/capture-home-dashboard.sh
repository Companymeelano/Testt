#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root/manager"
emulator_abi="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
emulator_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "::notice title=Dashboard emulator::API ${emulator_api}, ABI ${emulator_abi}"

wait_for_package_service() {
  local seconds="${1:-90}"
  local elapsed=0
  while [ "$elapsed" -lt "$seconds" ]; do
    local booted
    booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$booted" = "1" ]; then
      local service_status
      service_status="$(adb shell service check package 2>/dev/null | tr -d '\r' || true)"
      if printf '%s' "$service_status" | grep -Eqi 'service package: found'; then
        return 0
      fi
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

if ! wait_for_package_service 120; then
  service_status="$(adb shell service check package 2>&1 | tr '\r\n' ' ' || true)"
  echo "::error title=Dashboard emulator not ready::Android package service did not become available within 120 seconds. $service_status"
  exit 1
fi

build_log="$(mktemp)"
attempt=1
result=1
while [ "$attempt" -le 3 ]; do
  set +e
  gradle :app:assembleDebug :app:assembleDebugAndroidTest -PhomeScreenshotAbi=true -Pandroid.useAndroidX=true --console=plain --stacktrace --no-daemon 2>&1 | tee "$build_log"
  result=${PIPESTATUS[0]}
  set -e
  [ "$result" -eq 0 ] && break
  if [ "$attempt" -ge 3 ] || ! grep -Eq 'Could not resolve|Could not GET|Could not download|Could not find' "$build_log"; then
    break
  fi
  attempt=$((attempt + 1))
  echo "::warning::Screenshot APK dependency resolution failed; retrying (${attempt}/3)."
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
    echo "::error title=Screenshot APK build report::$safe_line"
    report_count=$((report_count + 1))
  done < "$report_file"

  diagnostics="$(grep -E 'FAILURE:|Execution failed|Could not |Caused by:|error:|FAILED$|ErrorName:|ErrorCode:' "$build_log" | tail -n 4 || true)"
  if [ -z "$diagnostics" ]; then diagnostics="$(tail -n 4 "$build_log")"; fi
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    safe_line="$(printf '%s' "$line" | sed 's/%/%25/g; s/:/%3A/g')"
    echo "::error title=Screenshot APK build diagnostics::$safe_line"
  done <<< "$diagnostics"
  exit 1
fi

app_apk="app/build/outputs/apk/debug/app-debug.apk"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
for apk in "$app_apk" "$test_apk"; do
  if [ ! -s "$apk" ]; then
    echo "::error title=Screenshot APK missing::$apk was not produced by the Android build."
    exit 1
  fi
done

adb wait-for-device
adb uninstall ir.meelano.manager.debug.test >/dev/null 2>&1 || true
adb uninstall ir.meelano.manager.debug >/dev/null 2>&1 || true
for apk in "$app_apk" "$test_apk"; do
  install_log="$(mktemp)"
  installed=0
  for install_attempt in 1 2 3; do
    if wait_for_package_service 60 && adb install -r "$apk" > "$install_log" 2>&1; then
      installed=1
      break
    fi
    if [ "$install_attempt" -lt 3 ]; then
      echo "::warning::Android package service was temporarily unavailable while installing $apk; retry $((install_attempt + 1))/3."
      adb wait-for-device
      sleep 5
    fi
  done
  if [ "$installed" -ne 1 ]; then
    install_output="$(tail -n 8 "$install_log" | tr '\r\n' ' ' | sed 's/%/%25/g; s/:/%3A/g')"
    echo "::error title=Screenshot APK install failed::$apk: $install_output"
    exit 1
  fi
  cat "$install_log"
done

instrumentation_log="$(mktemp)"
set +e
adb shell am instrument -w -r ir.meelano.manager.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee "$instrumentation_log"
instrumentation_result=${PIPESTATUS[0]}
set -e
if [ "$instrumentation_result" -ne 0 ] \
  || ! grep -Eq 'OK \([1-9][0-9]* tests?\)|INSTRUMENTATION_STATUS: numtests=[1-9]' "$instrumentation_log" \
  || grep -Eq 'INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -2|FAILURES!!!|Tests run: [0-9]+, Failures: [1-9]' "$instrumentation_log"; then
  grep -E 'INSTRUMENTATION_STATUS|INSTRUMENTATION_RESULT|INSTRUMENTATION_CODE|INSTRUMENTATION_FAILED|FAILURES!!!|Tests run:|AssertionError|Exception|Error:' "$instrumentation_log" | tail -n 10 | while IFS= read -r line; do
    safe_line="$(printf '%s' "$line" | sed 's/%/%25/g; s/:/%3A/g')"
    echo "::error title=Home screenshot instrumentation::$safe_line"
  done || true
  exit 1
fi
echo "::notice title=Home screenshot instrumentation::Instrumented Light and Dark rendering test passed."

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
