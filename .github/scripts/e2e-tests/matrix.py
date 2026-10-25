#!/usr/bin/env python3
"""
The end-to-end tests' emulator legs (e2e-tests.yml), from what the app supports and what Google has: for x86_64 and
x86, the oldest Android the app runs on (sdkMin), the one it targets (sdkTarget) and the newest there is (canary), on
Google's own system images (sys-img/<tag>/sys-img2-4.xml), each with the channel it's on.

- an ABI whose images end before sdkTarget (x86's at API 30): sdkMin and its newest image instead
- no ARM: GitHub's runners can't run an ARM emulator (x86 hosts refuse ARM images, the ARM Linux runners have no
  KVM), and the ARM app under the x86_64 images' translator can't run the Linux subsystem (PRoot traces what it
  runs, and reads the host's registers, not the ARM ones it knows)
- each leg in [SHARDS] jobs, each running its share of the tests (instrument.py): one emulator would take hours

    matrix.py <gradle.properties>     prints {"include": [...]} for strategy.matrix (merge.py's summary lists them)
"""
import json
import re
import sys
import urllib.request

ABIS = ["x86_64", "x86"]
TAGS = ["google_apis", "android"]
# Among the images of a release: plain Android first (the app needs no Google APIs, and Android 37's Google APIs image
# takes its system down right after it boots on the runners), else Google APIs (x86's API 30 and the canary have nothing
# else); 4 KB pages before 16 KB ones (the newest minors and canaries come in 16 KB alone)
TARGET_ORDER = ["default", "google_apis", "google_apis_ps16k"]
CHANNELS = {"channel-0": "stable", "channel-1": "beta", "channel-2": "dev", "channel-3": "canary"}
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
    """Every system image of [ABIS]: (version token, e.g. "29", "37.0", "CANARY"; target; ABI; channel)."""
    found = []
    for tag in TAGS:
        xml = urllib.request.urlopen(f"{REPOSITORY}/sys-img/{tag}/sys-img2-4.xml", timeout=60).read().decode("utf-8")
        for m in re.finditer(r'<remotePackage path="system-images;android-([^;"]+);([^;"]+);([^;"]+)"[^>]*>(.*?)</remotePackage>', xml, re.S):
            token, target, abi, body = m.groups()
            if abi not in ABIS or "obsolete" in m.group(0)[:200]:
                continue
            channel = re.search('channelRef ref="([^"]+)"', body)
            found.append((token, target, abi, CHANNELS.get(channel.group(1) if channel else "", "stable")))
    return found


def platforms():
    """The SDK platforms there are (the emulator action installs platforms;android-<api-level>)."""
    xml = urllib.request.urlopen("https://dl.google.com/android/repository/repository2-3.xml", timeout=60).read().decode("utf-8")
    return set(re.findall('path="platforms;android-([^"]+)"', xml))


def release(token):
    """The API level of a release (29, 37 for 37.0 or 37.2); None for previews, betas, extension levels, canaries."""
    m = re.fullmatch(r"(\d+)(?:\.(\d+))?", token)
    return int(m.group(1)) if m else None


def pick(all_images, abi, level):
    """
    The image of Android [level] for [abi], or None: 4 KB pages over a newer minor (37.2 comes in 16 KB alone, and
    on the runners it either takes SurfaceFlinger down (SwiftShader) or doesn't boot in 10 minutes (ANGLE); 37.0's
    4 KB one is the same Android), then its newest minor, then by TARGET_ORDER.
    """
    found = [i for i in all_images if i[2] == abi and i[1] in TARGET_ORDER and release(i[0]) == level]
    found.sort(key=lambda i: ("ps16k" in i[1], -float(i[0]), TARGET_ORDER.index(i[1])))
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

    def leg(image, label):
        token, target, abi, channel = image
        level = "canary" if token == "CANARY" else f"API {release(token)}"
        for shard in range(SHARDS):
            legs.append({
                "id": f"{abi}-{token}-{shard + 1}".lower().replace(".", "_"),
                "name": f"{abi} · {level} ({label}) · {shard + 1}/{SHARDS}",
                "arch": abi,
                # The SDK platform of the same name if there's one, else its API level's
                "api": token if token in all_platforms else str(release(token)),
                "image": token,
                "target": target,
                "channel": channel,
                "shard": shard,
                "shards": SHARDS,
            })

    for abi in ABIS:
        minimum, target, newest = pick(all_images, abi, sdk_min), pick(all_images, abi, sdk_target), canary(all_images, abi)
        if minimum:
            leg(minimum, "min")
        else:
            print(f"::warning title=E2E matrix::No {abi} image of API {sdk_min}", file=sys.stderr)
        if target:
            leg(target, "target")
        if newest:
            leg(newest, "newest")
        if not target and not newest:
            # Its images ended before the app's target (x86's at API 30): its newest one past sdkMin
            levels = sorted({release(i[0]) for i in all_images if i[2] == abi and (release(i[0]) or 0) > sdk_min})
            if levels:
                leg(pick(all_images, abi, levels[-1]), "its newest image")

    print(json.dumps({"include": legs}))


if __name__ == "__main__":
    main()
