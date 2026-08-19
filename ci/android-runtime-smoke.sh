#!/usr/bin/env bash
set -euo pipefail

cd "${GITHUB_WORKSPACE:-$(pwd)}"

PACKAGE='org.libsdl.opentyrian'
ACTIVITY='org.libsdl.opentyrian/.OpentyrianActivity'
APK="$PWD/apk/app-debug.apk"
ARTIFACT_DIR="$PWD/runtime-artifacts"

mkdir -p "$ARTIFACT_DIR"

collect_diagnostics() {
  adb logcat -d > "$ARTIFACT_DIR/logcat.txt" || true
  adb shell dumpsys activity activities > "$ARTIFACT_DIR/activity.txt" || true
  adb shell dumpsys window > "$ARTIFACT_DIR/window.txt" || true
  adb shell wm size > "$ARTIFACT_DIR/wm-size.txt" || true
  adb shell wm density > "$ARTIFACT_DIR/wm-density.txt" || true
}
trap collect_diagnostics EXIT

assert_running() {
  local phase="$1"
  local pid
  pid="$(adb shell pidof -s "$PACKAGE" | tr -d '\r')"
  if [[ -z "$pid" ]]; then
    echo "OpenTyrian2000 process is not running during phase: $phase" >&2
    return 1
  fi
  adb shell dumpsys activity activities | grep -q "$PACKAGE"
  printf '%s=%s\n' "$phase" "$pid" >> "$ARTIFACT_DIR/processes.txt"
}

capture_screen() {
  local name="$1"
  adb exec-out screencap -p > "$ARTIFACT_DIR/${name}.png"
}

adb logcat -c
adb install -r "$APK"
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" | tee "$ARTIFACT_DIR/am-start.txt"
sleep 5

assert_running initial
capture_screen initial

# Lock the emulator into landscape and verify the app remains alive.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 4
assert_running landscape
capture_screen landscape

# Return to the natural orientation and verify it survives again.
adb shell settings put system user_rotation 0
sleep 4
assert_running returned
capture_screen returned
