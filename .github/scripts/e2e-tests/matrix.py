#!/usr/bin/env python3
"""
The end-to-end tests' emulator legs (e2e-tests.yml), from what the app supports and what Google has: x86_64's
oldest Android the app runs on (sdkMin), the one it targets (sdkTarget) and the newest there is (canary), on Google's
own system images (sys-img/<tag>/sys-img2-4.xml), each with the channel it's on.

- x86_64 alone: GitHub's runners can't run an ARM emulator (x86 hosts refuse ARM images, the ARM Linux runners have
  no KVM), and the ARM app under the x86_64 images' translator can't run the Linux subsystem (PRoot traces what it
  runs, and reads the host's registers, not the ARM ones it knows)
- each leg in [SHARDS] jobs, each running its share of the tests (instrument.py): one emulator would take hours

    matrix.py <gradle.properties>     prints {"include": [...]} for strategy.matrix (and the legs as a table in the
                                      job's summary, on GitHub)
"""
import json
import os
import re
import sys
import urllib.request

ABI = "x86_64"
TAGS = ["google_apis", "android"]
# Among the images of a release's newest minor (37.2): Google APIs first (the device a user has), 4 KB pages before 16 KB
# ones (the newest minors and canaries come in 16 KB alone)
TARGET_ORDER = ["google_apis", "default", "google_apis_ps16k"]
CHANNELS = {"channel-0": "stable", "channel-1": "beta", "channel-2": "dev", "channel-3": "canary"}
# The emulator's renderer: SwiftShader, as the emulator action's default; Android 37's releases' SurfaceFlinger aborts
# on it ("Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma"), taking the system down every 20 s or so
# (the package manager gone in the middle of an install): SwiftShader under ANGLE for them. The canary's runs on it
GPU, GPU_FROM_37 = "swiftshader_indirect", "swangle_indirect"
# Jobs per leg, the tests dealt among them (the Minecraft servers' alone take over an hour on one)
SHARDS = 4
REPOSITORY = "https://dl.google.com/android/repository"


def properties(path):
    props = {}
    for line in open(path, encoding="utf-8"):
        k, sep, v = line.partition("=")
        if sep and not line.lstrip().startswith("#"):
            props[k.strip()] = v.strip()
    return props


def images():
    """Every x86_64 system image: (version token, e.g. "29", "37.0", "CANARY"; target; channel)."""
    found = []
    for tag in TAGS:
        xml = urllib.request.urlopen(f"{REPOSITORY}/sys-img/{tag}/sys-img2-4.xml", timeout=60).read().decode("utf-8")
        for m in re.finditer(r'<remotePackage path="system-images;android-([^;"]+);([^;"]+);([^;"]+)"[^>]*>(.*?)</remotePackage>', xml, re.S):
            token, target, abi, body = m.groups()
            if abi != ABI or "obsolete" in m.group(0)[:200]:
                continue
            channel = re.search(r'channelRef ref="([^"]+)"', body)
            found.append((token, target, CHANNELS.get(channel.group(1) if channel else "", "stable")))
    return found


def platforms():
    """The SDK platforms there are (the emulator action installs platforms;android-<api-level>)."""
    xml = urllib.request.urlopen("https://dl.google.com/android/repository/repository2-3.xml", timeout=60).read().decode("utf-8")
    return set(re.findall(r'path="platforms;android-([^"]+)"', xml))


def release(token):
    """A release's API level (29, 37 for 37.0 or 37.2); None for previews, betas, extension levels, canaries."""
    m = re.fullmatch(r"(\d+)(?:\.(\d+))?", token)
    return int(m.group(1)) if m else None


def pick(all_images, level):
    """The image of Android [level] (its newest minor release, e.g. 37.2, then by TARGET_ORDER), or None."""
    found = [i for i in all_images if i[1] in TARGET_ORDER and release(i[0]) == level]
    found.sort(key=lambda i: (-float(i[0]), TARGET_ORDER.index(i[1])))
    return found[0] if found else None


def canary(all_images):
    found = [i for i in all_images if i[0] == "CANARY" and i[1] in TARGET_ORDER]
    found.sort(key=lambda i: TARGET_ORDER.index(i[1]))
    return found[0] if found else None


def main():
    props = properties(sys.argv[1])
    sdk_min, sdk_target = int(props["sdkMin"]), int(props["sdkTarget"])
    all_images, all_platforms = images(), platforms()
    legs = []

    for image, label in ((pick(all_images, sdk_min), "min"), (pick(all_images, sdk_target), "target"), (canary(all_images), "newest")):
        if not image:
            print(f"::warning title=E2E matrix::No {ABI} image for the {label} leg", file=sys.stderr)
            continue
        token, target, channel = image
        level = "canary" if token == "CANARY" else f"API {release(token)}"
        for shard in range(SHARDS):
            legs.append({
                "id": f"{ABI}-{token}-{shard + 1}".lower().replace(".", "_"),
                "name": f"{ABI} · {level} ({label}) · {shard + 1}/{SHARDS}",
                "arch": ABI,
                # The SDK platform of the same name if there's one, else its API level's
                "api": token if token in all_platforms else str(release(token)),
                "image": token,
                "target": target,
                "channel": channel,
                "gpu": GPU_FROM_37 if (release(token) or 0) >= 37 else GPU,
                "shard": shard,
                "shards": SHARDS,
            })

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### Emulator legs\n\n")
            f.write(f"sdkMin {sdk_min}, sdkTarget {sdk_target}, each in {SHARDS} shards\n\n")
            f.write("| Leg | System image | Channel |\n|---|---|---|\n")
            for l in legs[::SHARDS]:
                f.write(f"| {l['name'].rsplit(' · ', 1)[0]} | android-{l['image']};{l['target']};{l['arch']} | {l['channel']} |\n")
    print(json.dumps({"include": legs}))


if __name__ == "__main__":
    main()
