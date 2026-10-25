"""Publishes the newest draft release of each of the app's Play tracks given (TRACKS, comma-separated).

Through the Play Developer API, in one edit: each track's newest draft (by version code) becomes its
release, as "completed" (what Play Console's "Start rollout" does). A track without a draft is left
as it is; with none at all, nothing is committed. Needs PACKAGE_NAME and SERVICE_ACCOUNT_JSON.
"""
import json
import os
import sys

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"


def check(response):
    if not response.ok:
        print(f"::error ::Play API: {response.status_code} {response.text}")
        sys.exit(1)
    return response.json() if response.content else {}


def main():
    package = os.environ["PACKAGE_NAME"]
    tracks = [t.strip() for t in os.environ.get("TRACKS", "").split(",") if t.strip()]
    if not tracks:
        print("::error ::No track to publish")
        sys.exit(1)
    credentials = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["SERVICE_ACCOUNT_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(credentials)
    edits = f"{API}/{package}/edits"
    edit = check(session.post(edits))["id"]

    published = []
    for name in tracks:
        track = check(session.get(f"{edits}/{edit}/tracks/{name}"))
        drafts = [r for r in track.get("releases", []) if r.get("status") == "draft"]
        if not drafts:
            print(f"::warning ::{name}: no draft to publish")
            continue
        release = max(drafts, key=lambda r: max(int(c) for c in r.get("versionCodes", ["0"])))
        release["status"] = "completed"
        # The track's release from now on (Play keeps the one it replaces in its history)
        check(session.put(f"{edits}/{edit}/tracks/{name}", json={"track": name, "releases": [release]}))
        published.append(f"{name}: {release.get('name', '')} ({', '.join(release.get('versionCodes', []))})")

    if not published:
        session.delete(f"{edits}/{edit}")
        print("::error ::No draft on any of the tracks")
        sys.exit(1)
    check(session.post(f"{edits}/{edit}:commit"))
    for line in published:
        print(f"::notice ::Published {line}")


if __name__ == "__main__":
    main()
