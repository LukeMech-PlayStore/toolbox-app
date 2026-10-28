#!/usr/bin/env python3
"""
Runs the end-to-end tests (app/src/androidTest) on the connected device and writes their results as JUnit XML,
for the report job's merge.py (run-tests.sh calls it once the app and the tests are installed):

- every test is listed first (a run with -e log true reports each without running it)
- then run class by class, each in a fresh process of the app
- a crash ends its class's run: the test it was in fails with it, and those that hadn't run yet run one by one
- a test that failed is run once more on its own; both tries are in the report (merge.py: one that
  passed the second time counts as flaky, not failed)
- with [shard] of [shards]: its share of the tests alone, dealt by how long each takes (durations.json): the shards
  done at about the same time

    instrument.py <runner> <out dir> <suite name> [<shard> <shards>]

Output: <out>/junit/<Class>.xml, the raw `am instrument -r` output of each run in <out>/raw/, each class's logcat
in <out>/logcat/.
"""
import json
import os
import re
import shlex
import statistics
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

# INSTRUMENTATION_STATUS_CODE values (androidx.test's InstrumentationResultPrinter)
START, OK, ERROR, FAILURE, IGNORED, ASSUMPTION = 1, 0, -1, -2, -3, -4

# A class's whole run: MinecraftServerTest's five servers, each up to TEST_TIMEOUT, the longest
CLASS_TIMEOUT = 300 * 60
TEST_TIMEOUT = 60 * 60
# A run that says nothing for this long is stuck (a test's waits say what they wait for every half a minute)
IDLE_TIMEOUT = 15 * 60
# Each test's seconds on each ABI, from earlier runs (merge.py's, kept in the Actions cache by tests.yml); none yet,
# the tests are dealt by the listing alone
DURATIONS = os.environ.get("E2E_DURATIONS", "durations.json")
# A class's own run besides its tests: the app's process started, its logcat cleared and saved
CLASS_OVERHEAD = 5
# Between tries of a test the app's process died before starting: the system's memory left to settle
START_RETRY_WAIT = 30


def instrument(runner, raw_path, args, timeout):
    """
    `am instrument -w -r` with [args], each line written to [raw_path] with when it came; killed after [timeout] s,
    or once neither it nor the tests' log (E2E: each action, what's waited for every half a minute) has said
    anything for [IDLE_TIMEOUT] s: stuck (e.g. the system's screen capture, on Android 37's emulators).
    """
    command = " ".join(shlex.quote(a) for a in ["am", "instrument", "-w", "-r", *args, runner])
    with open(raw_path, "w", encoding="utf-8") as out:
        proc = subprocess.Popen(["adb", "shell", command], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding="utf-8", errors="replace")
        # The tests' log from now on: each line of it something going on
        log = subprocess.Popen(["adb", "logcat", "-T", "1", "-s", "E2E:I"], stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL, text=True, encoding="utf-8", errors="replace")
        last = [time.time()]
        why = []
        done = threading.Event()

        def follow_log():
            for _ in log.stdout:
                last[0] = time.time()

        def watch():
            start = time.time()
            while not done.wait(30):
                now = time.time()
                if now - start > timeout:
                    why.append(f"Killed after {timeout // 60} min")
                elif now - last[0] > IDLE_TIMEOUT:
                    why.append(f"Killed: nothing said for {IDLE_TIMEOUT // 60} min (stuck)")
                else:
                    continue
                proc.kill()
                return

        threading.Thread(target=follow_log, daemon=True).start()
        threading.Thread(target=watch, daemon=True).start()
        try:
            for line in proc.stdout:
                last[0] = time.time()
                out.write(f"{time.time():.3f}\t{line.rstrip()}\n")
                out.flush()
        finally:
            done.set()
            log.kill()
            proc.wait()
        if why:
            print(f"::warning title=E2E::{os.path.basename(raw_path)}: {why[0]}", flush=True)
            out.write(f"{time.time():.3f}\tINSTRUMENTATION_RESULT: shortMsg={why[0]}\n")


def parse(path):
    """The tests of a run: (class, test, status, stack, seconds), status one of ok, failure, error, skipped, crashed."""
    results, run_lines = [], []
    keys, key, started, when = {}, None, None, 0.0
    with open(path, encoding="utf-8", errors="replace") as f:
        for raw in f:
            raw = raw.rstrip("\n")
            stamp, _, line = raw.partition("\t")
            try:
                when = float(stamp)
            except ValueError:
                # Not one of ours (no time before it): as it is, at the last one's time
                line = raw
            if line.startswith("INSTRUMENTATION_STATUS: "):
                key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
                keys[key] = value
            elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
                code = int(line.split(": ", 1)[1].strip() or 0)
                if code == START:
                    started = (dict(keys), when)
                elif "test" in keys:
                    seconds = when - started[1] if started else 0.0
                    status = {OK: "ok", FAILURE: "failure", ERROR: "error", IGNORED: "skipped", ASSUMPTION: "skipped"}.get(code, "error")
                    results.append((keys.get("class", "?"), keys["test"], status, keys.get("stack", "").strip(), seconds))
                    started = None
                keys, key = {}, None
            elif line.startswith("INSTRUMENTATION_"):
                run_lines.append(line)
                key = None
            elif key is not None:
                # A value going on (a stack trace's lines)
                keys[key] += "\n" + line
            else:
                run_lines.append(line)
    # Started, never ended: the app died with it (or the run was cut off)
    if started is not None:
        k, when = started
        results.append((k.get("class", "?"), k.get("test", "?"), "crashed", "\n".join(run_lines), 0.0))
    return results, "\n".join(run_lines)


def write_junit(path, suite_name, results):
    suite = ET.Element("testsuite", name=suite_name)
    counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for cls, test, status, stack, seconds in results:
        counts["tests"] += 1
        case = ET.SubElement(suite, "testcase", classname=cls, name=test, time=f"{max(seconds, 0):.3f}")
        message = (stack.splitlines() or [""])[0][:500]
        if status == "failure":
            counts["failures"] += 1
            ET.SubElement(case, "failure", message=message).text = stack
        elif status in ("error", "crashed"):
            counts["errors"] += 1
            title = "The app crashed (or the run was cut off) during this test" if status == "crashed" else message
            ET.SubElement(case, "error", message=title).text = stack
        elif status == "skipped":
            counts["skipped"] += 1
            ET.SubElement(case, "skipped", message=message)
    for k, v in counts.items():
        suite.set(k, str(v))
    ET.ElementTree(suite).write(path, encoding="utf-8", xml_declaration=True)
    return counts


def only(cls, test):
    """-e tests_regex for one test: its class and method, a parameterized one's [parameters] too."""
    return any_of(cls, [test])


def any_of(cls, tests):
    """-e tests_regex for some of [cls]'s tests."""
    return ["-e", "tests_regex", "^(" + "|".join(re.escape(f"{cls}#{t}") for t in tests) + ")$"]


def deal(listed, shard, shards):
    """
    This shard's share of [listed] ((class, test) in the listing's order), as {class: [tests]}: the longest first, each
    to the shard with the least time so far, by durations.json's for the device's ABI (else the other's, else the median
    of them); the same deal on every shard of a leg (the same tests listed, the same ABI).
    """
    abi = subprocess.run(["adb", "shell", "getprop", "ro.product.cpu.abi"], capture_output=True, text=True).stdout.strip()
    try:
        with open(DURATIONS, encoding="utf-8") as f:
            table = json.load(f)
    except (OSError, ValueError):
        table = {}
    known = table.get(abi, {})
    others = {test: s for other, tests in table.items() if other != abi for test, s in tests.items()}
    median = statistics.median(list(known.values()) or list(others.values()) or [0])

    def seconds(cls, test):
        key = f"{cls.rsplit('.', 1)[-1]}#{test}"
        return known.get(key, others.get(key, median))

    loads, started, mine = [0.0] * shards, set(), []
    for i in sorted(range(len(listed)), key=lambda i: -seconds(*listed[i])):
        cls, test = listed[i]
        to = min(range(shards), key=lambda s: loads[s])
        loads[to] += seconds(cls, test) + (0 if (to, cls) in started else CLASS_OVERHEAD)
        started.add((to, cls))
        if to == shard:
            mine.append(i)
    print(f"Shard {shard + 1}/{shards} ({abi}): about {loads[shard] / 60:.0f} min of tests, the shards' "
          + " / ".join(f"{load / 60:.0f}" for load in loads), flush=True)
    tests = {}
    for i in sorted(mine):
        tests.setdefault(listed[i][0], []).append(listed[i][1])
    return tests


def main():
    runner, out, suite_name = sys.argv[1:4]
    shard, shards = (int(sys.argv[4]), int(sys.argv[5])) if len(sys.argv) > 5 else (0, 1)
    for d in ("raw", "junit", "logcat"):
        os.makedirs(os.path.join(out, d), exist_ok=True)

    listing = os.path.join(out, "raw", "list.txt")
    # DeviceSetup is run-tests.sh's, before the tests: not one of them. Listed again when the app's process died
    # starting up (the canary's, once, right after the install), what the system said kept
    for attempt in range(3):
        if attempt:
            time.sleep(20)
        subprocess.run(["adb", "logcat", "-c"], check=False)
        instrument(runner, listing, ["-e", "log", "true", "-e", "notClass", "org.lukemech.lmtoolbox.e2e.DeviceSetup"], 10 * 60)
        listed, run_output = parse(listing)
        if listed:
            break
        with open(os.path.join(out, "logcat", f"listTests-{attempt}.txt"), "w", encoding="utf-8") as log:
            subprocess.run(["adb", "logcat", "-d"], stdout=log, stderr=subprocess.DEVNULL, check=False)
    if not listed:
        write_junit(os.path.join(out, "junit", "setup.xml"), suite_name,
                    [("e2e.Setup", "listTests", "error", "No tests listed in 3 tries (logcat/listTests-*.txt):\n" + run_output[-3000:], 0.0)])
        print("::error title=E2E::No tests listed in 3 tries: " + (run_output.strip().splitlines() or ["no output"])[0])
        return

    for cls, names in deal([(cls, test) for cls, test, *_ in listed], shard, shards).items():
        short = cls.rsplit(".", 1)[-1]
        print(f"::group::{short} ({len(names)} tests)", flush=True)
        subprocess.run(["adb", "logcat", "-c"], check=False)
        raw = lambda what: os.path.join(out, "raw", f"{short}{what}.txt")

        # The class whole, or its tests of this shard's
        instrument(runner, raw(""), ["-e", "class", cls] if shards == 1 else any_of(cls, names), CLASS_TIMEOUT)
        results, _ = parse(raw(""))

        # Those a crash kept from running: one by one. The app's process killed before one started (the canary's
        # low-memory killer, the system's own apps still starting up): run again once that's over; never started at
        # all, it fails with what the run said
        done = {test for _, test, *_ in results}
        for i, test in enumerate(t for t in names if t not in done):
            for attempt in range(3):
                if attempt:
                    time.sleep(START_RETRY_WAIT)
                path = raw(f"-rest{i}" + (f"-{attempt}" if attempt else ""))
                instrument(runner, path, only(cls, test), TEST_TIMEOUT)
                ran, run_output = parse(path)
                if ran:
                    results += ran
                    break
            else:
                results.append((cls, test, "crashed", "Never started in 3 tries (logcat: lowmemorykiller?):\n" + run_output, 0.0))

        # Each failed one, once more on its own
        failed = [test for _, test, status, *_ in results if status in ("failure", "error", "crashed")]
        for i, test in enumerate(dict.fromkeys(failed)):
            print(f"Retrying {short}#{test}", flush=True)
            instrument(runner, raw(f"-retry{i}"), only(cls, test), TEST_TIMEOUT)
            results += parse(raw(f"-retry{i}"))[0]

        with open(os.path.join(out, "logcat", f"{short}.txt"), "w", encoding="utf-8") as log:
            subprocess.run(["adb", "logcat", "-d"], stdout=log, stderr=subprocess.DEVNULL, check=False)
        counts = write_junit(os.path.join(out, "junit", f"{short}.xml"), suite_name, results)
        print(f"{short}: {counts}", flush=True)
        print("::endgroup::", flush=True)


if __name__ == "__main__":
    main()
