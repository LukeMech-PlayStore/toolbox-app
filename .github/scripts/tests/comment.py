#!/usr/bin/env python3
"""
The comment tests.yml keeps on a pull request (its comment job): the E2E tests' and the inspections' results, as the
run's summary has them (merge.py's, report.py's), in one comment updated on every run, not a new one each time.

- on top, a line for each: how its job went and its tally (the first line of its summary under the heading)
- under it, each summary whole, folded away; cut short if they'd take more than a comment can hold (GitHub's 65536
  characters), the rest in the run's summary
- the hidden [MARKER] first: what tells the comment apart from the others (tests.yml finds it by it)

    comment.py <e2e.md> <inspections.md> <comment.md>

A summary missing (its job didn't get to write it): its line says so. The environment's E2E_RESULT and INSPECT_RESULT are
the jobs' results (success, failure, cancelled, skipped), RUN_URL the run's page, COMMIT what was tested.
"""
import os
import sys

MARKER = "<!-- lmtoolbox-tests -->"
# What GitHub holds of a comment, less some room for the rest of it
LIMIT = 65536 - 4000
RESULTS = {"success": "✅", "failure": "❌", "cancelled": "⏹️", "skipped": "⏭️"}
# Why a job has no summary, by its result
WHY_NONE = {"success": "wrote none", "failure": "failed first", "cancelled": "was canceled", "skipped": "didn't run"}


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
    foot = ["", f"[The run]({run_url}): every job's log, screenshots, logcat and Qodana's HTML report in its artifacts. "
            "The inspections' problems are also on the changed lines (code scanning)."]

    # The room left, shared by the summaries; one that needs less leaves the rest to the other
    room = LIMIT - len("\n".join(head + foot))
    shown = [(title, lines) for title, lines, _ in parts if lines]
    texts = {}
    for i, (title, lines) in enumerate(sorted(shown, key=lambda s: len("\n".join(s[1])))):
        texts[title] = cut(lines, room // (len(shown) - i), run_url)
        room -= len(texts[title])
    body = list(head)
    for title, _ in shown:
        body += ["", f"<details><summary><b>{title}</b></summary>", "", texts[title], "", "</details>"]
    with open(sys.argv[3], "w", encoding="utf-8") as f:
        f.write("\n".join(body + foot) + "\n")


if __name__ == "__main__":
    main()
