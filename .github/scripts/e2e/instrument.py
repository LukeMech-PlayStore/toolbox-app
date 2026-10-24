#!/usr/bin/env python3
"""
Runs the end-to-end tests (app/src/androidTest) on the connected device and writes their results as JUnit XML,
for mikepenz/action-junit-report (run-tests.sh calls it once the app and the tests are installed):

- every test is listed first (a run with -e log true reports each without running it)
- then run class by class, each in a fresh process of the app
- a crash ends its class's run: the test it was in fails with it, and those that hadn't run yet run one by one
- a test that failed is run once more on its own; both tries are in the report (check_retries: one that
  passed the second time counts as flaky, not failed)

    instrument.py <runner> <out dir> <suite name>

Output: <out>/junit/<Class>.xml, the raw `am instrument -r` output of each run in <out>/raw/, each class's logcat
in <out>/logcat/.
"""
import os
import re
import shlex
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

# INSTRUMENTATION_STATUS_CODE values (androidx.test's InstrumentationResultPrinter)
START, OK, ERROR, FAILURE, IGNORED, ASSUMPTION = 1, 0, -1, -2, -3, -4

CLASS_TIMEOUT = 150 * 60
TEST_TIMEOUT = 60 * 60


def instrument(runner, raw_path, args, timeout):
    """`am instrument -w -r` with [args], each line written to [raw_path] with when it came; killed after [timeout] s."""
    command = " ".join(shlex.quote(a) for a in ["am", "instrument", "-w", "-r", *args, runner])
    with open(raw_path, "w", encoding="utf-8") as out:
        proc = subprocess.Popen(["adb", "shell", command], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding="utf-8", errors="replace")
        killed = threading.Event()
        timer = threading.Timer(timeout, lambda: (killed.set(), proc.kill()))
        timer.start()
        try:
            for line in proc.stdout:
                out.write(f"{time.time():.3f}\t{line.rstrip()}\n")
                out.flush()
        finally:
            timer.cancel()
            proc.wait()
        if killed.is_set():
            out.write(f"{time.time():.3f}\tINSTRUMENTATION_RESULT: shortMsg=Killed after {timeout // 60} min\n")


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
    return ["-e", "tests_regex", "^" + re.escape(f"{cls}#{test}") + "$"]


def main():
    runner, out, suite_name = sys.argv[1:4]
    for d in ("raw", "junit", "logcat"):
        os.makedirs(os.path.join(out, d), exist_ok=True)

    listing = os.path.join(out, "raw", "list.txt")
    instrument(runner, listing, ["-e", "log", "true"], 10 * 60)
    listed, run_output = parse(listing)
    tests = {}
    for cls, test, *_ in listed:
        tests.setdefault(cls, []).append(test)
    if not tests:
        write_junit(os.path.join(out, "junit", "setup.xml"), suite_name,
                    [("e2e.Setup", "listTests", "error", "No tests found:\n" + run_output[-3000:], 0.0)])
        print("::error title=E2E::No tests found")
        return

    for cls, names in tests.items():
        short = cls.rsplit(".", 1)[-1]
        print(f"::group::{short} ({len(names)} tests)", flush=True)
        subprocess.run(["adb", "logcat", "-c"], check=False)
        raw = lambda what: os.path.join(out, "raw", f"{short}{what}.txt")

        instrument(runner, raw(""), ["-e", "class", cls], CLASS_TIMEOUT)
        results, _ = parse(raw(""))

        # Those a crash kept from running: one by one
        done = {test for _, test, *_ in results}
        for i, test in enumerate(t for t in names if t not in done):
            instrument(runner, raw(f"-rest{i}"), only(cls, test), TEST_TIMEOUT)
            results += parse(raw(f"-rest{i}"))[0]

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
