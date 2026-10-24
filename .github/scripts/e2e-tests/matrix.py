#!/usr/bin/env python3
"""
The end-to-end tests' emulator legs (e2e-tests.yml), from what the app supports and what Google has: for x86_64
and x86, its oldest Android (sdkMin), the one it targets (sdkTarget) and the newest there is (canary), on Google's
own system images (sys-img/<tag>/sys-img2-4.xml), each with the channel it's on.

- an ABI whose images end before sdkTarget (x86's at API 30): sdkMin and its newest image instead
- ARM: GitHub's runners can't run an ARM emulator (x86 hosts refuse ARM images, the ARM Linux runners have no
  KVM, the Apple Silicon ones are M1s with no virtualization inside their VM), so the ARM app on x86_64 images,
  which translate ARM apps from API 30 (ARM_FROM): arm64-v8a there, on the target and the canary; armeabi-v7a
  there alone (the newer images are 64-bit only). Allowed to fail: our ARM code under Google's translator
- a job per leg, running every test

    matrix.py <gradle.properties>     prints {"include": [...]} for strategy.matrix (and the legs as a table in the
                                      job's summary, on GitHub)
"""
import json
import os
import re
import sys
import urllib.request

ABIS = ["x86_64", "x86"]
TAGS = ["google_apis", "android"]
# Among the images of a release's newest minor (37.2): Google APIs first (the device a user has), 4 KB pages before 16 KB
# ones (the newest minors and canaries come in 16 KB alone)
TARGET_ORDER = ["google_apis", "default", "google_apis_ps16k"]
CHANNELS = {"channel-0": "stable", "channel-1": "beta", "channel-2": "dev", "channel-3": "canary"}
# The first x86_64 images translating ARM apps (Google's ARM translation)
ARM_FROM = 30


def properties(path):
    props = {}
    for line in open(path, encoding="utf-8"):
        k, sep, v = line.partition("=")
        if sep and not line.lstrip().startswith("#"):
            props[k.strip()] = v.strip()
    return props


def images():
    """Every system image: (version token, e.g. "29", "37.0", "CANARY"; target; ABI; channel)."""
    found = []
    for tag in TAGS:
        url = f"https://dl.google.com/android/repository/sys-img/{tag}/sys-img2-4.xml"
        xml = urllib.request.urlopen(url, timeout=60).read().decode("utf-8")
        for m in re.finditer(r'<remotePackage path="system-images;android-([^;"]+);([^;"]+);([^;"]+)"[^>]*>(.*?)</remotePackage>', xml, re.S):
            token, target, abi, body = m.groups()
            if "obsolete" in m.group(0)[:200]:
                continue
            channel = re.search(r'channelRef ref="([^"]+)"', body)
            found.append((token, target, abi, CHANNELS.get(channel.group(1) if channel else "", "stable")))
    return found


def platforms():
    """The SDK platforms there are (the emulator action installs platforms;android-<api-level>)."""
    xml = urllib.request.urlopen("https://dl.google.com/android/repository/repository2-3.xml", timeout=60).read().decode("utf-8")
    return set(re.findall(r'path="platforms;android-([^"]+)"', xml))


def release(token):
    """A release's API level (29, 37 for 37.0 or 37.2); None for previews, betas, extension levels, canaries."""
    m = re.fullmatch(r"(\d+)(?:\.(\d+))?", token)
    return int(m.group(1)) if m else None


def pick(all_images, abi, level):
    """The image of Android [level] for [abi] (its newest minor release, e.g. 37.2, then by TARGET_ORDER), or None."""
    found = [i for i in all_images if i[2] == abi and i[1] in TARGET_ORDER and release(i[0]) == level]
    found.sort(key=lambda i: (-float(i[0]), TARGET_ORDER.index(i[1])))
    return found[0] if found else None


def canary(all_images, abi):
    found = [i for i in all_images if i[2] == abi and i[0] == "CANARY" and i[1] in TARGET_ORDER]
    found.sort(key=lambda i: TARGET_ORDER.index(i[1]))
    return found[0] if found else None


def main():
    props = properties(sys.argv[1])
    sdk_min, sdk_target = int(props["sdkMin"]), int(props["sdkTarget"])
    all_images, all_platforms = images(), platforms()
    legs = []

    def leg(image, label, force=None):
        token, target, arch, channel = image
        level = "canary" if token == "CANARY" else f"API {release(token)}"
        shown = force or arch
        legs.append({
            "id": f"{shown}-{token}".lower().replace(".", "_"),
            "name": f"{shown} · {level} ({label}{', translated on ' + arch if force else ''})",
            "arch": arch,
            # The SDK platform of the same name if there's one, else its API level's
            "api": token if token in all_platforms else str(release(token)),
            "image": token,
            "target": target,
            "channel": channel,
            # Installed as that ABI alone (run-tests.sh), under Google's translator: tried, never failing the run
            "force_abi": force or "",
            "experimental": bool(force),
        })

    for abi in ABIS:
        minimum = pick(all_images, abi, sdk_min)
        if minimum:
            leg(minimum, "min")
        else:
            print(f"::warning title=E2E matrix::No {abi} image of API {sdk_min}", file=sys.stderr)
        target = pick(all_images, abi, sdk_target)
        newest = canary(all_images, abi)
        if target:
            leg(target, "target")
        if newest:
            leg(newest, "newest")
        if not target and not newest:
            # Its images ended before the app's target (x86's at API 30): its newest one past sdkMin
            levels = sorted({release(i[0]) for i in all_images if i[2] == abi and (release(i[0]) or 0) > sdk_min})
            if levels:
                leg(pick(all_images, abi, levels[-1]), "its newest image")

    # ARM, on x86_64's images: the oldest translating (or sdkMin, past it), the target, the canary; 32-bit on the first
    arm_min = max(sdk_min, ARM_FROM)
    oldest = pick(all_images, "x86_64", arm_min)
    if oldest:
        leg(oldest, "min" if arm_min == sdk_min else "oldest translating", "arm64-v8a")
    for image, label in ((pick(all_images, "x86_64", sdk_target), "target"), (canary(all_images, "x86_64"), "newest")):
        if image and image != oldest:
            leg(image, label, "arm64-v8a")
    if oldest:
        leg(oldest, "min" if arm_min == sdk_min else "oldest translating", "armeabi-v7a")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### Emulator legs\n\n")
            f.write(f"sdkMin {sdk_min}, sdkTarget {sdk_target}\n\n")
            f.write("| Leg | System image | Channel |\n|---|---|---|\n")
            for l in legs:
                f.write(f"| {l['name']} | android-{l['image']};{l['target']};{l['arch']} | {l['channel']} |\n")
    print(json.dumps({"include": legs}))


if __name__ == "__main__":
    main()
