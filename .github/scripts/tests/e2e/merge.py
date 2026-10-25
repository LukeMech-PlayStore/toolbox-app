#!/usr/bin/env python3
"""
The legs' JUnit XML (instrument.py's, each shard's in its own artifact) as one report, for the report job of
tests.yml, whose result is the tests':

- the job's summary (beside the inspect job's, report.py's): a table of every test's result on each leg, the failed first,
  then why each failed (or was flaky) where, and the legs' system images. A retried test's last try counts (passed
  after failing: flaky), a leg it didn't run in (its shard cut short, or without results) not run
- an annotation for each test failed in a leg (GitHub shows 10 of them; the summary has them all); a shard without
  results is a failed e2e.Setup test of its own, its tests not run
- exits 1 if any failed or didn't run
- with <durations.json>: that file (instrument.py's, how it deals the tests among a leg's shards; kept in the Actions
  cache by tests.yml) updated in place with each test that didn't fail, as long as it took on each ABI (the median of
  its legs), the tests not seen as they were

    merge.py <matrix JSON> <artifacts dir> [<summary.md> [<durations.json>]]

<artifacts dir>/e2e-results-<id>/junit/*.xml, as download-artifact puts them.
"""
import glob
import html
import json
import os
import statistics
import sys
import xml.etree.ElementTree as ET

# A leg's result of a test, and a test's over them all: the worst first
ICONS = {"failed": "❌", "not run": "⚠️", "flaky": "🔁", "passed": "✅", "skipped": "⏭️"}


def outcome(case):
    """(passed, failed or skipped; its message: the whole of it, as its stack has it, not its first line alone)."""
    for kind, status in (("failure", "failed"), ("error", "failed"), ("skipped", "skipped")):
        found = case.find(kind)
        if found is not None:
            title, stack = found.get("message") or "", (found.text or "").strip()
            # A title of its own (a crash's): before the stack
            return status, stack if stack.startswith(title) else "\n".join(filter(None, [title, stack]))
    return "passed", ""


def brief(message, frames=3):
    """[message] up to its stack's first [frames] frames (an annotation's: what failed, and where)."""
    lines, seen = [], 0
    for line in message.splitlines():
        if line.lstrip().startswith("at "):
            seen += 1
            if seen > frames:
                lines.append("\t...")
                break
        lines.append(line)
    return "\n".join(lines)[:3000]


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


def durations(legs, tests, path):
    """[path] (durations.json) updated with how long each test took this time (see the docstring)."""
    abis = {shard["name"].rsplit(" · ", 1)[0]: shard["arch"] for shard in legs}
    try:
        with open(path, encoding="utf-8") as f:
            table = json.load(f)
    except (OSError, ValueError):
        table = {}
    took = {}
    for (cls, name), ran in tests.items():
        if cls == "e2e.Setup":
            continue
        for leg, tries in ran.items():
            status, _ = outcome(tries[-1][1])
            if status != "failed":
                # Skipped: nothing (the ABI's servers on a Java it hasn't)
                seconds = 0.0 if status == "skipped" else float(tries[-1][1].get("time") or 0)
                took.setdefault(abis[leg], {}).setdefault(f"{cls.rsplit('.', 1)[-1]}#{name}", []).append(seconds)
    for abi, its in took.items():
        table.setdefault(abi, {}).update({test: round(statistics.median(s)) for test, s in its.items()})
    with open(path, "w", encoding="utf-8") as f:
        json.dump({abi: dict(sorted(its.items())) for abi, its in sorted(table.items())}, f, indent=1, ensure_ascii=False)
        f.write("\n")


def main():
    legs = json.loads(sys.argv[1])["include"]
    order = list(dict.fromkeys(shard["name"].rsplit(" · ", 1)[0] for shard in legs))
    tests = load(legs, sys.argv[2])

    rows, totals, failed, why = [], dict.fromkeys(ICONS, 0), [], []
    for (cls, name), ran in tests.items():
        title = f"{'Setup' if cls == 'e2e.Setup' else cls.rsplit('.', 1)[-1]} › {name}"
        cells, results = [], []
        for leg in order:
            tries = ran.get(leg)
            if not tries and cls == "e2e.Setup":
                # Only where a shard's setup went wrong: nothing missing elsewhere
                cells.append("")
                continue
            if not tries:
                results.append("not run")
                cells.append(ICONS["not run"])
                continue
            shard = tries[-1][0]
            status, message = outcome(tries[-1][1])
            if status == "passed" and any(outcome(attempt)[0] == "failed" for _, attempt in tries[:-1]):
                status, message = "flaky", next(outcome(attempt)[1] for _, attempt in tries if outcome(attempt)[0] == "failed")
            if status == "failed":
                failed.append((f"{title} ({shard})", message))
            if status in ("failed", "flaky"):
                why.append((list(ICONS).index(status), title, shard, status, message))
            results.append(status)
            seconds = sum(float(attempt.get("time") or 0) for _, attempt in tries)
            cells.append(f"{ICONS[status]} {seconds:.0f} s" if status != "skipped" else ICONS[status])
        overall = next((s for s in ICONS if s in results and s != "skipped"), "skipped")
        totals[overall] += 1
        rows.append((list(ICONS).index(overall), title, f"| {html.escape(title, quote=False)} | " + " | ".join(cells) + " |\n"))

    if len(sys.argv) > 3:
        rows.sort(key=lambda r: r[:2])
        why.sort(key=lambda w: w[:3])
        counts = " · ".join(f"{ICONS[s]} {n} {s}" for s, n in totals.items() if n)
        # A leg's column headed by its ABI over its Android, to keep the table narrow
        head = " | ".join(leg.replace(" · ", "<br>", 1) for leg in order)
        with open(sys.argv[3], "a", encoding="utf-8") as f:
            f.write(f"## E2E\n\n{len(tests)} tests on {len(order)} legs: {counts}\n\n")
            f.write(f"| Test | {head} |\n|---|" + "---|" * len(order) + "\n" + "".join(r for *_, r in rows))
            if why:
                f.write("\n### Why\n\n")
                for _, title, shard, status, message in why:
                    f.write(f"<details><summary>{ICONS[status]} {html.escape(title, quote=False)} · {html.escape(shard, quote=False)}</summary>\n\n"
                            f"```\n{(message or status)[:4000].replace('```', '` ` `')}\n```\n\n</details>\n")
            f.write("\n### Legs\n\n| Leg | System image | Channel |\n|---|---|---|\n")
            for shard in legs[::legs[0]["shards"]] if legs else []:
                f.write(f"| {shard['name'].rsplit(' · ', 1)[0]} | android-{shard['image']};{shard['target']};{shard['arch']} | {shard['channel']} |\n")
    if len(sys.argv) > 4:
        durations(legs, tests, sys.argv[4])
    for title, message in failed:
        # A workflow command: its message one line, its % and line breaks escaped
        text = brief(message or "failed").replace("%", "%25").replace("\r", "").replace("\n", "%0A")
        print(f"::error title={title.replace(',', '%2C').replace('::', ':')}::{text}")
    sys.exit(1 if totals["failed"] or totals["not run"] else 0)


if __name__ == "__main__":
    main()
