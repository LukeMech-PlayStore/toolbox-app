#!/usr/bin/env python3
"""
The comment tests.yml keeps on a pull request (its comment job): the E2E tests' and the inspections' results, as the
run's summary has them (merge.py's, report.py's), in one comment updated on every run, not a new one each time.

- on top, a line for each: how its job went and its tally (the first line of its summary under the heading)
- under it, each summary whole, folded away; cut short if they'd take more than a comment can hold (GitHub's 65536
  characters), the rest in the run's summary
- with <failures dir> (merge.py's) and where tests.yml published it: each failed (or flaky) test on its leg, unfolded,
  with the screen when it failed, its logcat's errors and the whole logcat linked
- the hidden [MARKER] first: what tells the comment apart from the others (tests.yml finds it by it)

    comment.py <e2e.md> <inspections.md> <comment.md> [<failures dir> <its URL>]

A summary missing (its job didn't get to write it): its line says so. The environment's E2E_RESULT and INSPECT_RESULT are
the jobs' results (success, failure, cancelled, skipped), RUN_URL the run's page, COMMIT what was tested.
"""
import html
import json
import os
import re
import sys

MARKER = "<!-- lmtoolbox-tests -->"
# What GitHub holds of a comment, less some room for the rest of it
LIMIT = 65536 - 4000
RESULTS = {"success": "✅", "failure": "❌", "cancelled": "⏹️", "skipped": "⏭️"}
# Why a job has no summary, by its result
WHY_NONE = {"success": "wrote none", "failure": "failed first", "cancelled": "was canceled", "skipped": "didn't run"}
FAILURE_ICONS = {"failed": "❌", "flaky": "🔁"}
# A logcat's line (adb logcat -d's threadtime): date, time, pid, tid, level, tag
LOGCAT_LINE = re.compile(r"^\S+ \S+\s+(\d+)\s+\d+ ([VDIWEF]) (.*?)\s*:")
# A failure's logcat errors shown: its last ones
LOGCAT_ERRORS = 30
# A screenshot's width in the comment: a phone's screen, small enough for a few side by side
SCREENSHOT_WIDTH = 270


def read(path):
    """A summary's lines, its own heading (## E2E, ## Inspections) left out; none without one."""
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.read().strip().splitlines()
    except OSError:
        return None
    return lines[1:] if lines and lines[0].startswith("## ") else lines


def tally(lines):
    """The summary's first line of text: its tally (e.g. "120 tests on 6 legs: ✅ 120 passed")."""
    return next((line for line in lines if line.strip() and not line.startswith(("#", "|", "<"))), "") if lines else ""


def app_errors(lines):
    """
    [lines]' errors (and worse) of the app's processes, those the tests logged from (E2E: the instrumentation runs
    in the app's), the system's crash reports (AndroidRuntime) too; none logged from: every process's.
    """
    parsed = [(m, line) for line in lines if (m := LOGCAT_LINE.match(line))]
    pids = {m[1] for m, _ in parsed if m[3] == "E2E"}
    return [line for m, line in parsed
            if m[2] in "EF" and (not pids or m[1] in pids or m[3] == "AndroidRuntime")]


def failures(folder, url):
    """
    The failures section's lines, from [folder]'s failures.json (merge.py's), its files at [url]: each test that failed
    on a leg (or was flaky there), its screenshot and its logcat's last errors; none without failures.json.
    """
    try:
        with open(os.path.join(folder, "failures.json"), encoding="utf-8") as f:
            listed = json.load(f)
    except (OSError, ValueError):
        return None
    lines = []
    for failure in listed:
        title = html.escape(f"{failure['title']} · {failure['shard']}", quote=False)
        lines += [f"<details><summary>{FAILURE_ICONS.get(failure['status'], '❔')} {title}</summary>", ""]
        if failure.get("screenshot"):
            lines += [f'<img src="{url}/{failure["screenshot"]}" width="{SCREENSHOT_WIDTH}" alt="The screen when it failed">', ""]
        if failure.get("logcat"):
            try:
                with open(os.path.join(folder, failure["logcat"]), encoding="utf-8", errors="replace") as f:
                    errors = app_errors(f.read().splitlines())[-LOGCAT_ERRORS:]
            except OSError:
                errors = []
            if errors:
                lines += [f"The app's last errors in logcat ([the whole of it]({url}/{failure['logcat']})):", "",
                          "```", *(e.replace("```", "` ` `") for e in errors), "```", ""]
            else:
                lines += [f"[Logcat]({url}/{failure['logcat']}): no errors in it", ""]
        if not failure.get("screenshot") and not failure.get("logcat"):
            lines += ["No screenshot or logcat: see the run's artifacts", ""]
        lines.append("</details>")
    return lines


def cut(lines, room, run_url):
    """[lines] up to [room] characters, at a line's end, what's left open (a code block, a fold) closed; the rest linked."""
    text = "\n".join(lines)
    if len(text) <= room:
        return text
    kept, used = [], 0
    for line in lines:
        if used + len(line) + 1 > room:
            break
        kept.append(line)
        used += len(line) + 1
    if sum(line.startswith("```") for line in kept) % 2:
        kept.append("```")
    kept += ["</details>"] * (sum("<details>" in line for line in kept) - sum("</details>" in line for line in kept))
    return "\n".join(kept) + f"\n\n✂️ Cut short: the rest is in [the run's summary]({run_url})."


def main():
    run_url = os.environ.get("RUN_URL", "")
    failed = failures(sys.argv[4], sys.argv[5]) if len(sys.argv) > 5 else None
    commit = os.environ.get("COMMIT", "")[:7]
    parts = [
        ("E2E tests", read(sys.argv[1]), os.environ.get("E2E_RESULT", "")),
        ("Inspections", read(sys.argv[2]), os.environ.get("INSPECT_RESULT", "")),
    ]

    head = [MARKER, f"## Tests · `{commit}`" if commit else "## Tests", "", "| | Result |", "|---|---|"]
    for title, lines, result in parts:
        icon = RESULTS.get(result, "❔")
        what = tally(lines) or f"No report: the job {WHY_NONE.get(result, WHY_NONE['skipped'])}"
        head.append(f"| {icon} **{title}** | {what} |")
    foot = ["", f"[The run]({run_url}): every job's log, screenshots, logcat and Qodana's HTML report in its artifacts."]

    # The room left, shared by the summaries; one that needs less leaves the rest to the other
    room = LIMIT - len("\n".join(head + foot))
    shown = [(title, lines) for title, lines, _ in parts if lines] + ([("Failures", failed)] if failed else [])
    texts = {}
    for i, (title, lines) in enumerate(sorted(shown, key=lambda s: len("\n".join(s[1])))):
        texts[title] = cut(lines, room // (len(shown) - i), run_url)
        room -= len(texts[title])
    body = list(head)
    for title, _ in shown:
        # The failures unfolded: what's to be looked at first
        fold = "<details open>" if title == "Failures" else "<details>"
        body += ["", f"{fold}<summary><b>{title}</b></summary>", "", texts[title], "", "</details>"]
    with open(sys.argv[3], "w", encoding="utf-8") as f:
        f.write("\n".join(body + foot) + "\n")


if __name__ == "__main__":
    main()
