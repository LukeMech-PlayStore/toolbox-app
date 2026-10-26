#!/usr/bin/env python3
"""
Qodana's SARIF as the inspect job's report (tests.yml), as Android Studio's Inspect Code shows its problems:

- the job's summary (the pull request's comment too, comment.py's): how many problems of each severity, a table of the
  inspections that found any (the worst first), then where each found them, every problem linked to its line
- with <code-scanning.sarif.json>: the SARIF with only the problems that fail the job, for code scanning (tests.yml
  uploads it): those on their lines in a pull request's changes, the rest (typos, grammar...) only in the summary
- exits 1 if there's a problem of a [FAILING] severity (the IDE's errors and warnings, an outdated library's among
  them), or no SARIF (Qodana didn't get to write it); the rest (weak warnings, typos, grammar) only reported

    report.py <qodana.sarif.json> [<summary.md> [<code-scanning.sarif.json>]]
"""
import html
import json
import os
import sys

# Qodana's severities (the IDE's: Error, Warning, Weak warning, Typo...), the worst first, and their icons
SEVERITIES = {
    "Critical": "❌",
    "High": "⚠️",
    "Moderate": "🔸",
    "Low": "🔹",
    "Info": "ℹ️",
}
# A result without Qodana's severity: by its SARIF level
LEVELS = {"error": "Critical", "warning": "High", "note": "Moderate", "none": "Info"}
# The severities that fail the job
FAILING = ["Critical", "High"]


def names(run):
    """{rule id: its name as the IDE lists it}, from the driver's rules and every plugin's."""
    tool = run.get("tool", {})
    rules = [rule for component in [tool.get("driver", {})] + tool.get("extensions", []) for rule in component.get("rules", [])]
    return {rule["id"]: (rule.get("shortDescription") or {}).get("text") or rule.get("name") or rule["id"] for rule in rules}


def severity(result):
    """A result's severity: Qodana's, else by its SARIF level (one it doesn't know: High)."""
    found = (result.get("properties") or {}).get("qodanaSeverity") or LEVELS.get(result.get("level", "warning"), "High")
    return found if found in SEVERITIES else "High"


def failing_only(sarif):
    """[sarif] with only the results of a [FAILING] severity (none: none, so code scanning closes what was there)."""
    return {**sarif, "runs": [
        {**run, "results": [r for r in run.get("results", []) if severity(r) in FAILING]} for run in sarif.get("runs", [])
    ]}


def problems(sarif):
    """
    (inspection, severity, path, line, column, message) for each problem, the baseline's own left out; the sanity
    check's too (code Qodana couldn't resolve: a file it skipped the inspections of), kept apart from the results in
    the run's properties.
    """
    found = []
    for run in sarif.get("runs", []):
        rules = names(run)
        sanity = (run.get("properties") or {}).get("qodana.sanity.results") or []
        for result in run.get("results", []) + sanity:
            if result.get("baselineState") == "unchanged":
                continue
            location = ((result.get("locations") or [{}])[0]).get("physicalLocation") or {}
            region = location.get("region") or {}
            message = ((result.get("message") or {}).get("text") or "").strip()
            found.append((
                rules.get(result.get("ruleId"), result.get("ruleId") or "?"),
                severity(result),
                (location.get("artifactLocation") or {}).get("uri") or "",
                region.get("startLine"),
                region.get("startColumn"),
                f"{message} (sanity check: the file wasn't inspected)" if result in sanity else message,
            ))
    return found


def main():
    try:
        with open(sys.argv[1], encoding="utf-8") as f:
            sarif = json.load(f)
        found = problems(sarif)
    except (OSError, ValueError) as e:
        print(f"::error title=Inspections::No report from Qodana ({e}): see the Inspect step's log")
        if len(sys.argv) > 2:
            with open(sys.argv[2], "a", encoding="utf-8") as f:
                f.write("## Inspections\n\n❌ No report from Qodana: see the Inspect step's log\n")
        sys.exit(1)

    if len(sys.argv) > 3:
        with open(sys.argv[3], "w", encoding="utf-8") as f:
            json.dump(failing_only(sarif), f)

    order = list(SEVERITIES)
    found.sort(key=lambda p: (order.index(p[1]), p[0], p[2], p[3] or 0))
    by_inspection = {}
    for problem in found:
        by_inspection.setdefault(problem[0], []).append(problem)

    if len(sys.argv) > 2:
        server, repo, sha = (os.environ.get(k, "") for k in ("GITHUB_SERVER_URL", "GITHUB_REPOSITORY", "GITHUB_SHA"))
        counts = " · ".join(
            f"{SEVERITIES[s]} {n} {s.lower()}" for s in order if (n := sum(1 for p in found if p[1] == s))
        )
        with open(sys.argv[2], "a", encoding="utf-8") as f:
            f.write("## Inspections\n\n")
            if not found:
                f.write("✅ No problems\n")
                return
            failing = " and ".join(s.lower() for s in FAILING)
            f.write(f"{len(found)} problems from {len(by_inspection)} inspections: {counts} (the job fails on {failing})\n\n"
                    "Qodana's HTML report (Inspect Code's table) is in the run's `inspections` artifact.\n\n"
                    "| Inspection | Severity | Problems |\n|---|---|---|\n")
            for inspection, its in by_inspection.items():
                f.write(f"| {html.escape(inspection, quote=False)} | {SEVERITIES[its[0][1]]} {its[0][1]} | {len(its)} |\n")
            f.write("\n### Where\n\n")
            for inspection, its in by_inspection.items():
                f.write(f"<details><summary>{SEVERITIES[its[0][1]]} {html.escape(inspection, quote=False)} · {len(its)}</summary>\n\n")
                for _, _, path, line, _, message in its:
                    where = f"{path}:{line}" if line else path
                    link = f"{server}/{repo}/blob/{sha}/{path}" + (f"#L{line}" if line else "")
                    f.write(f"- [{html.escape(where, quote=False)}]({link}): {html.escape(message, quote=False)}\n")
                f.write("\n</details>\n")
    sys.exit(1 if any(p[1] in FAILING for p in found) else 0)


if __name__ == "__main__":
    main()
