#!/usr/bin/env bash
#
# Find out WHY connectedReleaseAndroidTest hangs.
#
# It was previously worked around rather than diagnosed: instrumented tests were
# pointed back at the debug variant and the missing coverage was documented. That is
# a gap with a comment on it, not a fix.
#
# The hang produced no output at all -- not even "Starting N tests" -- so the task
# never got as far as reporting. That narrows it to install or instrumentation
# registration, both of which are directly observable. This asks.
#
set -uo pipefail

PKG=com.verbalogix.conformance
OUT=emulator-out
mkdir -p "$OUT"

echo "=============================================================="
echo " 0. decode the signing key"
echo "=============================================================="
if [ -n "${SIGNING_KEY_BASE64:-}" ]; then
  printf '%s' "$SIGNING_KEY_BASE64" | base64 -d > "$SIGNING_KEYSTORE_PATH"
  echo "keystore decoded ($(stat -c '%s' "$SIGNING_KEYSTORE_PATH") bytes)"
fi

echo
echo "=============================================================="
echo " 1. build BOTH release APKs without running anything"
echo "=============================================================="
# assembleReleaseAndroidTest builds the test APK but does not execute it, so a
# build-side failure is separated from an execution-side one.
./gradlew assembleRelease assembleReleaseAndroidTest --stacktrace 2>&1 \
  | tee "$OUT/build.log" | tail -20

APP_APK=$(find app/build/outputs/apk/release -name '*.apk' ! -name '*unsigned*' ! -name '*androidTest*' | head -1)
TEST_APK=$(find app/build/outputs/apk/androidTest/release -name '*.apk' | head -1)
echo
echo "app  apk: ${APP_APK:-NONE}"
echo "test apk: ${TEST_APK:-NONE}"
[ -z "$APP_APK" ]  && { echo "::error::no app APK"; exit 1; }
[ -z "$TEST_APK" ] && { echo "::error::no androidTest APK -- it never got built"; exit 1; }

echo
echo "=============================================================="
echo " 2. are they signed with the SAME certificate?"
echo "=============================================================="
# If they differ, Android refuses to let the test APK instrument the app, and the
# failure mode is not always a clean error.
for a in "$APP_APK" "$TEST_APK"; do
  d=$(python3 scripts/apk_cert.py "$a" 2>/dev/null | tail -1)
  echo "  $(basename "$a"): ${d:-UNREADABLE}"
done

echo
echo "=============================================================="
echo " 3. what does the test APK declare as its instrumentation runner?"
echo "=============================================================="
AAPT=$(find "${ANDROID_HOME:-/usr/local/lib/android/sdk}/build-tools" -name aapt2 2>/dev/null | sort -V | tail -1)
if [ -n "$AAPT" ]; then
  "$AAPT" dump badging "$TEST_APK" 2>/dev/null | grep -iE "instrumentation|package:" | head -5
fi

echo
echo "=============================================================="
echo " 4. did R8 rename the runner or the test classes?"
echo "=============================================================="
# The manifest names the runner as a STRING. R8 renaming it means `am instrument`
# looks for a class that no longer exists under that name -- and the failure is a
# hang, not an error, because nothing reports back.
TEST_MAP=app/build/outputs/mapping/releaseAndroidTest/mapping.txt
if [ -f "$TEST_MAP" ]; then
  echo "androidTest mapping present ($(wc -l < "$TEST_MAP") lines)"
  echo "--- are annotations retained? (RuntimeVisibleAnnotations) ---"
  if grep -q "RuntimeVisibleAnnotations\|keepattributes" app/proguard-test-rules.pro; then
    echo "  -keepattributes present in proguard-test-rules.pro"
  else
    echo "  NO -keepattributes -- AndroidJUnitRunner scans for @RunWith at runtime and"
    echo "  R8 strips annotations by default. The runner finds the classes, finds no"
    echo "  @RunWith, and reports nothing at all."
  fi
  for cls in androidx.test.runner.AndroidJUnitRunner \
             com.verbalogix.conformance.LaunchTest \
             com.verbalogix.conformance.MigrationTest; do
    line=$(grep -E "^${cls} -> " "$TEST_MAP" 2>/dev/null | head -1)
    if [ -z "$line" ]; then
      echo "  ABSENT  $cls  (removed by R8, or never in this APK)"
    else
      mapped=$(printf '%s' "$line" | sed 's/.* -> //; s/:$//')
      if [ "$mapped" = "$cls" ]; then echo "  kept    $cls"
      else echo "  RENAMED $cls -> $mapped"; fi
    fi
  done
else
  echo "no androidTest mapping at $TEST_MAP"
fi

echo
echo "=============================================================="
echo " 5. install both, then ASK the device what it sees"
echo "=============================================================="
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb uninstall "$PKG.test" >/dev/null 2>&1 || true
adb install -r "$APP_APK"  2>&1 | tail -2
adb install -r "$TEST_APK" 2>&1 | tail -2
echo "--- packages ---"
adb shell pm list packages | grep -i conformance || echo "  NEITHER PACKAGE INSTALLED"
echo "--- registered instrumentation ---"
adb shell pm list instrumentation | grep -i conformance || echo "  NO INSTRUMENTATION REGISTERED (this alone explains a hang)"

echo
echo "=============================================================="
echo " 6. run the instrumentation DIRECTLY, with a hard timeout"
echo "=============================================================="
# Bypasses Gradle entirely. If `am instrument` returns promptly with an error, the
# hang is Gradle waiting on something; if it hangs here too, it is the device.
adb logcat -c
RUNNER=$(adb shell pm list instrumentation | grep -i conformance | sed 's/instrumentation://; s/ .*//' | head -1)
echo "runner: ${RUNNER:-NONE FOUND}"
if [ -n "$RUNNER" ]; then
  timeout 300 adb shell am instrument -w -r "$RUNNER" 2>&1 | tee "$OUT/instrument.log" | tail -30
  echo "am instrument exit: $?  (124 = timed out)"
else
  echo "::error::no instrumentation registered -- am instrument cannot be attempted"
fi

echo
echo "--- logcat, filtered ---"
adb logcat -d 2>/dev/null | grep -iE "AndroidRuntime|TestRunner|instrument|conformance" | tail -40 \
  | tee "$OUT/logcat.log"

rm -f "$SIGNING_KEYSTORE_PATH" 2>/dev/null || true
echo
echo "diagnosis complete -- artifacts in $OUT/"
