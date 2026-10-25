#!/usr/bin/env python3
"""
The legs' JUnit XML (instrument.py's, each shard's in its own artifact) as one report, for the report job of
e2e-tests.yml: one table in the summary instead of a block per shard, in whatever order the jobs ended.

- the legs in matrix.py's order, each one suite, its shards' tests together
- in a leg: what kept a shard from running first (e2e.Setup), then by class and test (a retried test's tries kept
  in the order they ran, for check_retries)
- a shard without results (its job failed before writing them, or was cancelled): an error in its leg's suite

    merge.py <matrix JSON> <artifacts dir> <out.xml>

<artifacts dir>/e2e-results-<id>/junit/*.xml, as download-artifact puts them.
"""
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET


def main():
    legs = json.loads(sys.argv[1])["include"]
    artifacts, out = sys.argv[2], sys.argv[3]
    suites, cases = {}, {}
    for shard in legs:
        name = shard["name"].rsplit(" · ", 1)[0]
        suites.setdefault(name, [])
        files = sorted(glob.glob(os.path.join(artifacts, f"e2e-results-{shard['id']}", "junit", "*.xml")))
        if not files:
            case = ET.Element("testcase", classname="e2e.Setup", name=f"shard {shard['shard'] + 1}/{shard['shards']}", time="0")
            ET.SubElement(case, "error", message="No results: the shard's job failed before writing them, or was cancelled")
            suites[name].append(case)
        for path in files:
            suites[name] += ET.parse(path).getroot().iter("testcase")

    root = ET.Element("testsuites")
    for name, found in suites.items():
        found.sort(key=lambda c: (c.get("classname") != "e2e.Setup", c.get("classname"), c.get("name")))
        suite = ET.SubElement(root, "testsuite", name=name)
        counts = {"tests": len(found), "failures": 0, "errors": 0, "skipped": 0}
        for case in found:
            for kind, count in (("failure", "failures"), ("error", "errors"), ("skipped", "skipped")):
                counts[count] += case.find(kind) is not None
            suite.append(case)
        suite.set("time", f"{sum(float(c.get('time') or 0) for c in found):.3f}")
        for k, v in counts.items():
            suite.set(k, str(v))
    ET.ElementTree(root).write(out, encoding="utf-8", xml_declaration=True)


if __name__ == "__main__":
    main()
