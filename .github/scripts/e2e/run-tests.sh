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

adb wait-for-device
abis=$(adb shell getprop ro.product.cpu.abilist | tr -d '\r')
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "Device: API $sdk, ABIs $abis${FORCE_ABI:+, installing as $FORCE_ABI}"
adb logcat -G 16M || true
# Awake, located (the compass), as a user's phone
adb shell svc power stayon true || true
adb shell settings put secure location_mode 3 || true

# 1. The app, as from Play
"${BUNDLETOOL[@]}" get-device-spec --output=spec.json --overwrite || setup_failed "bundletool couldn't read the device"
if [[ -n "${FORCE_ABI:-}" ]]; then
  [[ ",$abis," == *",$FORCE_ABI,"* ]] || setup_failed "This image doesn't run $FORCE_ABI apps (its ABIs: $abis)"
  python3 - spec.json "$FORCE_ABI" <<'EOF'
import json, sys
spec = json.load(open(sys.argv[1]))
spec["supportedAbis"] = [sys.argv[2]]
json.dump(spec, open(sys.argv[1], "w"))
EOF
fi
"${BUNDLETOOL[@]}" build-apks --bundle="$DIST/app-debug.aab" --output=app.apks --overwrite --local-testing --device-spec=spec.json \
  || setup_failed "bundletool couldn't build the APKs"
"${BUNDLETOOL[@]}" install-apks --apks=app.apks --modules=core --allow-test-only || setup_failed "The app couldn't be installed"
adb install -r -t "$DIST/app-debug-androidTest.apk" || setup_failed "The tests couldn't be installed"
echo "Installed: $(adb shell dumpsys package $APP | grep -m1 -E 'primaryCpuAbi' | tr -d '\r' | xargs)"

# 2. The tests: listed, run class by class, those a crash kept from running one by one, each failed one once more
python3 "$HERE/instrument.py" "$RUNNER" "$OUT" "$LEG"

# 3. Screenshots (the app's external files: the instrumentation runs in its process)
adb pull "/sdcard/Android/data/$APP/files/e2e" "$OUT/" > /dev/null 2>&1 || true
exit 0
