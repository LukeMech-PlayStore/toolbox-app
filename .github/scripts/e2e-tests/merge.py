#!/usr/bin/env python3
"""
The legs' JUnit XML (instrument.py's, each shard's in its own artifact) as one report, for the report job of
e2e-tests.yml, whose result is the tests':

- the job's summary: a row per test, its result over every leg, opened to each leg's (and shard's) own; the failed
  first. A retried test's last try counts (passed after failing: flaky), a leg it didn't run in (its shard cut short,
  or without results) not run
- an annotation for each test failed in a leg (GitHub shows 10 of them; the summary has them all); a shard without
  results is a failed e2e.Setup test of its own, its tests not run
- exits 1 if any failed or didn't run

    merge.py <matrix JSON> <artifacts dir> [<summary.md>]

<artifacts dir>/e2e-results-<id>/junit/*.xml, as download-artifact puts them.
"""
import glob
import html
import json
import os
import sys
import xml.etree.ElementTree as ET

# A leg's result of a test, and a test's over them all: the worst first
ICONS = {"failed": "❌", "not run": "⚠️", "flaky": "🔁", "passed": "✅", "skipped": "⏭️"}


def outcome(case):
    """(passed, failed or skipped; its message)."""
    for kind, status in (("failure", "failed"), ("error", "failed"), ("skipped", "skipped")):
        found = case.find(kind)
        if found is not None:
            return status, found.get("message") or ""
    return "passed", ""


def load(legs, artifacts):
    """
    {(class, test): {leg: [(shard, try), ...]}} in the legs' order (matrix.py's) and the order the tries ran; a shard
    without results: a failed e2e.Setup test of its own.
    """
    tests = {}
    for shard in legs:
        leg = shard["name"].rsplit(" · ", 1)[0]
        files = sorted(glob.glob(os.path.join(artifacts, f"e2e-results-{shard['id']}", "junit", "*.xml")))
        cases = [case for path in files for case in ET.parse(path).getroot().iter("testcase")]
        if not files:
            case = ET.Element("testcase", classname="e2e.Setup", name=f"shard {shard['shard'] + 1}/{shard['shards']}", time="0")
            ET.SubElement(case, "error", message="No results: the shard's job failed before writing them, or was cancelled")
            cases = [case]
        for case in cases:
            tests.setdefault((case.get("classname"), case.get("name")), {}).setdefault(leg, []).append((shard["name"], case))
    return tests


def main():
    legs = json.loads(sys.argv[1])["include"]
    order = list(dict.fromkeys(shard["name"].rsplit(" · ", 1)[0] for shard in legs))
    tests = load(legs, sys.argv[2])

    rows, totals, failed = [], dict.fromkeys(ICONS, 0), []
    for (cls, name), ran in tests.items():
        title = f"{'Setup' if cls == 'e2e.Setup' else cls.rsplit('.', 1)[-1]} › {name}"
        lines, results = [], []
        for leg in order:
            tries = ran.get(leg)
            if not tries:
                results.append("not run")
                lines.append(f"| {ICONS['not run']} not run | {leg} | | |")
                continue
            shard = tries[-1][0]
            status, message = outcome(tries[-1][1])
            if status == "passed" and any(outcome(c)[0] == "failed" for _, c in tries[:-1]):
                status, message = "flaky", next(outcome(c)[1] for _, c in tries if outcome(c)[0] == "failed")
            if status == "failed":
                failed.append((f"{title} ({shard})", message))
            results.append(status)
            seconds = sum(float(c.get("time") or 0) for _, c in tries)
            why = html.escape(message.splitlines()[0][:200] if message else "", quote=False).replace("|", "\\|")
            lines.append(f"| {ICONS[status]} {status} | {shard} | {seconds:.0f} s | {why} |")
        overall = next((s for s in ICONS if s in results and s != "skipped"), "skipped")
        totals[overall] += 1
        rows.append((list(ICONS).index(overall), f"<details><summary>{ICONS[overall]} {html.escape(title, quote=False)}</summary>\n\n"
                     "| Result | Leg | Time | Why |\n|---|---|---|---|\n" + "\n".join(lines) + "\n\n</details>\n"))

    if len(sys.argv) > 3:
        rows.sort(key=lambda r: r[0])
        counts = " · ".join(f"{ICONS[s]} {n} {s}" for s, n in totals.items() if n)
        with open(sys.argv[3], "a", encoding="utf-8") as f:
            f.write(f"## E2E\n\n{len(tests)} tests on {len(order)} legs: {counts}\n\n" + "".join(r for _, r in rows))
    for title, message in failed:
        # A workflow command: its message one line, its % and line breaks escaped
        text = (message or "failed").replace("%", "%25").replace("\r", "").replace("\n", "%0A")
        print(f"::error title={title.replace(',', '%2C').replace('::', ':')}::{text}")
    sys.exit(1 if totals["failed"] or totals["not run"] else 0)


if __name__ == "__main__":
    main()
