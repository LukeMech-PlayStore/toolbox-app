#!/usr/bin/env bash
# The end-to-end tests on the running emulator (e2e-tests.yml, android-emulator-runner's script):
#  1. the debug app installed as from Play: bundletool's local testing, the core module for real (PRoot is
#     executed from nativeLibraryDir, see buildSrc's LocalTesting.kt), the other modules downloaded when a tool asks;
#     FORCE_ABI: as if the device had that ABI alone (armeabi-v7a on a 64-bit image)
#  2. the tests (app/src/androidTest) run by instrument.py: class by class, carrying on after a crash, failed ones retried
#  3. their results as JUnit XML (e2e-results/junit), screenshots and each class's logcat beside them
# Always exits 0 once results are written: the report step fails the job on failed tests.
#
# Env: E2E_LEG (the matrix leg's name), FORCE_ABI, E2E_DIST (the build job's files)
set -uo pipefail

DIST=${E2E_DIST:-dist}
OUT=${E2E_OUT:-e2e-results}
LEG=${E2E_LEG:-local}
APP=org.lukemech.lmtoolbox.dbg
RUNNER=$APP.test/androidx.test.runner.AndroidJUnitRunner
HERE=$(cd "$(dirname "$0")" && pwd)
BUNDLETOOL=(java -jar "$DIST/bundletool.jar")
mkdir -p "$OUT/raw" "$OUT/junit" "$OUT/logcat"

# A single failed test case standing for what kept the tests from running at all
setup_failed() {
  python3 - "$OUT/junit/setup.xml" "$LEG" "$1" <<'EOF'
import sys, xml.etree.ElementTree as ET
s = ET.Element("testsuite", name=sys.argv[2], tests="1", failures="0", errors="1", skipped="0")
c = ET.SubElement(s, "testcase", classname="e2e.Setup", name="install", time="0")
ET.SubElement(c, "error", message=sys.argv[3]).text = sys.argv[3]
ET.ElementTree(s).write(sys.argv[1], encoding="utf-8", xml_declaration=True)
EOF
  echo "::error title=E2E setup ($LEG)::$1"
  exit 0
}

# Booted isn't ready: the package manager answers a while later (bundletool asks it first), and the system may
# restart once more after that (or during an install); ready once it has answered for a while
wait_ready() {
  adb wait-for-device
  local ok=0
  for _ in $(seq 1 120); do
    if adb shell pm list features 2>/dev/null | grep -q '^feature:' && adb shell cmd activity get-current-user > /dev/null 2>&1; then
      ok=$((ok + 1)); [[ $ok -ge 5 ]] && return 0
    else
      ok=0
    fi
    sleep 2
  done
}
wait_ready
abis=$(adb shell getprop ro.product.cpu.abilist | tr -d '\r')
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "Device: API $sdk, ABIs $abis${FORCE_ABI:+, installing as $FORCE_ABI}"
adb logcat -G 16M || true
# Awake, located (the compass), as a user's phone
adb shell svc power stayon true || true
adb shell settings put secure location_mode 3 || true

# 1. The app, as from Play
for try in 1 2 3; do
  "${BUNDLETOOL[@]}" get-device-spec --output=spec.json --overwrite && break
  [[ $try == 3 ]] && setup_failed "bundletool couldn't read the device"
  sleep 20
done
if [[ -n "${FORCE_ABI:-}" ]]; then
  [[ ",$abis," == *",$FORCE_ABI,"* ]] || setup_failed "This image doesn't run $FORCE_ABI apps (its ABIs: $abis)"
  python3 - spec.json "$FORCE_ABI" <<'EOF'
import json, sys
spec = json.load(open(sys.argv[1]))
spec["supportedAbis"] = [sys.argv[2]]
json.dump(spec, open(sys.argv[1], "w"))
EOF
fi
# One key for the app's APKs and the tests' (they can only instrument an app signed as they are): made here, whatever
# key Gradle signed the build with
KEY=$(mktemp -d)/e2e.keystore
keytool -genkeypair -keystore "$KEY" -storepass android -keypass android -alias e2e -keyalg RSA -keysize 2048 \
  -validity 365 -dname "CN=LMToolbox e2e" > /dev/null 2>&1 || setup_failed "keytool couldn't make a key"
APKSIGNER=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)apksigner
"${BUNDLETOOL[@]}" build-apks --bundle="$DIST/app-debug.aab" --output=app.apks --overwrite --local-testing --device-spec=spec.json \
  --ks="$KEY" --ks-pass=pass:android --ks-key-alias=e2e --key-pass=pass:android \
  || setup_failed "bundletool couldn't build the APKs"
"$APKSIGNER" sign --ks "$KEY" --ks-pass pass:android --ks-key-alias e2e --key-pass pass:android \
  --out tests.apk "$DIST/app-debug-androidTest.apk" || setup_failed "apksigner couldn't sign the tests"
# Installed again once the system is back, when it went away in the middle (the package service restarting)
install_app() {
  for try in 1 2 3; do
    "${BUNDLETOOL[@]}" install-apks --apks=app.apks --modules=core --allow-test-only && return 0
    adb shell pm path "$APP" 2>/dev/null | grep -q package: && return 1
    sleep 10; wait_ready
  done
  return 1
}
installed() { adb shell pm path "$APP" 2>/dev/null | grep -q package:; }
adb install -r -t tests.apk || { wait_ready; adb install -r -t tests.apk; } || setup_failed "The tests couldn't be installed"
if ! install_app; then
  installed || setup_failed "The app couldn't be installed"
  # From Android 11 bundletool can't make the app's folder it puts the Play modules in (Android/data/<app>): the
  # app makes it (DeviceSetup, in its process), then they're put there again
  adb shell am instrument -w -e class org.lukemech.lmtoolbox.e2e.DeviceSetup "$RUNNER"
  if ! install_app; then
    # Android 11 keeps the shell out of it even then: as root (Google APIs images allow it), the files given to the
    # app after (they'd be root's, unreadable to it: Play's error -13)
    adb root > /dev/null 2>&1; sleep 3; wait_ready
    install_app || setup_failed "The app couldn't be installed (its Play modules couldn't be put on the device)"
    dir=/data/media/0/Android/data/$APP/files
    owner=$(adb shell stat -c %u:%g "$dir" | tr -d '\r')
    adb shell "chown -R $owner $dir/local_testing && chmod -R u+rwX,g+rwX $dir/local_testing && restorecon -R $dir/local_testing" \
      || setup_failed "The Play modules couldn't be given to the app"
  fi
fi
echo "Installed: $(adb shell dumpsys package $APP | grep -m1 -E 'primaryCpuAbi' | tr -d '\r' | xargs)"

# 2. The tests: listed, run class by class, those a crash kept from running one by one, each failed one once more.
# What they say they're doing (each test, each element tried, what's waited for) shown as they go
adb logcat -v time -s E2E:I &
LOGCAT=$!
python3 "$HERE/instrument.py" "$RUNNER" "$OUT" "$LEG"
kill $LOGCAT 2>/dev/null

# 3. Screenshots (the app's external files: the instrumentation runs in its process)
adb pull "/sdcard/Android/data/$APP/files/e2e" "$OUT/" > /dev/null 2>&1 || true
exit 0
