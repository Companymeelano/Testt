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
  diagnostics="$(grep -E 'FAILURE:|Execution failed|Could not |Caused by:|error:|AssertionFailedError|AssertionError|FAILED$|No tests found' "$log_file" | tail -n 8 || true)"
  if [ -z "$diagnostics" ]; then diagnostics="$(tail -n 12 "$log_file")"; fi
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    safe_line="$(printf '%s' "$line" | sed 's/%/%25/g; s/:/%3A/g')"
    echo "::error title=Screenshot instrumentation diagnostics::$safe_line"
  done <<< "$diagnostics"
  exit 1
fi

mkdir -p ../design/screenshots
adb pull /sdcard/Android/data/ir.meelano.manager.debug/files/home-dashboard-screenshots/home-dashboard-light.png ../design/screenshots/home-dashboard-light.png
adb pull /sdcard/Android/data/ir.meelano.manager.debug/files/home-dashboard-screenshots/home-dashboard-dark.png ../design/screenshots/home-dashboard-dark.png
test -s ../design/screenshots/home-dashboard-light.png
test -s ../design/screenshots/home-dashboard-dark.png
