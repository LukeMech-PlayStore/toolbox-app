#!/usr/bin/env python3
"""
The end-to-end tests' emulator legs (e2e-tests.yml), from what the app supports and what Google has: for every ABI
the app ships, its oldest Android (sdkMin), the one it targets (sdkTarget) and the newest there is (canary), on
Google's own system images (sys-img/<tag>/sys-img2-4.xml), each with the channel it's on.

- an ABI whose images end before sdkTarget (x86's at API 30): sdkMin and its newest image instead
- armeabi-v7a: on its own images where they reach sdkMin, else installed as 32-bit on the arm64-v8a legs
- ARM legs run under emulation on the x86_64 runners: allowed to fail
- each leg's tests in shards (shards()), each a job of its own: the tools, and the Minecraft servers of each Java

    matrix.py <gradle.properties>     prints {"include": [...]} for strategy.matrix (and the legs as a table in the
                                      job's summary, on GitHub)
"""
import json
import os
import re
import sys
import urllib.request

ABIS = ["x86_64", "x86", "arm64-v8a", "armeabi-v7a"]
TAGS = ["google_apis", "android"]
# Most wanted first: Google APIs (the device a user has), 4 KB pages before 16 KB ones (canary's are 16 KB anyway)
TARGET_ORDER = ["google_apis", "default", "google_apis_ps16k"]
CHANNELS = {"channel-0": "stable", "channel-1": "beta", "channel-2": "dev", "channel-3": "canary"}
# The Java versions the subsystem has (the app's Java.JDKS): a shard of the Minecraft servers each
JAVA_KT = "app/src/main/java/org/lukemech/lmtoolbox/linux/parts/Java.kt"


def shards():
    """Each leg's tests, run side by side: (id, name, which tests, a regex instrument.py matches on Class#test[params])."""
    jdks = re.findall(r'Jdk\("(jdk\d+)", (\d+)\)', open(JAVA_KT, encoding="utf-8").read())
    return [("tools", "tools & pages", r"^(?!.*\.mc_server\.)")] + [
        (f"mc-{part}", f"Minecraft · Java {version}", rf"\.mc_server\.MinecraftServerTest#.*-{part}\]$")
        for part, version in jdks
    ]


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
    """The image of Android [level] for [abi] (by TARGET_ORDER, then the newest minor release), or None."""
    found = [i for i in all_images if i[2] == abi and i[1] in TARGET_ORDER and release(i[0]) == level]
    found.sort(key=lambda i: (TARGET_ORDER.index(i[1]), -float(i[0])))
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

    def leg(image, label, abi=None, force=None):
        token, target, arch, channel = image
        shown = abi or arch
        level = "canary" if token == "CANARY" else f"API {release(token)}"
        on = f", on {arch}" if force else ""
        for shard, shard_name, classes in shards():
            legs.append({
                "id": f"{shown}-{token}-{shard}".lower().replace(".", "_"),
                "name": f"{shown} · {level} ({label}{on}) · {shard_name}",
                "classes": classes,
                "arch": arch,
                # The SDK platform of the same name if there's one, else its API level's
                "api": token if token in all_platforms else str(release(token)),
                "image": token,
                "target": target,
                "channel": channel,
                # ARM under emulation on an x86_64 runner: tried, never failing the run
                "experimental": arch.startswith("arm"),
                "force_abi": force or "",
            })

    def min_target_canary(abi, force=None, on=None):
        image_abi = on or abi
        shown = abi if force else None
        minimum = pick(all_images, image_abi, sdk_min)
        if minimum:
            leg(minimum, "min", shown, force)
        target = pick(all_images, image_abi, sdk_target)
        newest = canary(all_images, image_abi)
        if target:
            leg(target, "target", shown, force)
        if newest:
            leg(newest, "newest", shown, force)
        if not target and not newest:
            # Its images ended before the app's target (x86's at API 30): its newest one past sdkMin
            levels = sorted({release(i[0]) for i in all_images if i[2] == image_abi and (release(i[0]) or 0) > sdk_min})
            if levels:
                leg(pick(all_images, image_abi, levels[-1]), "its newest image", shown, force)
        if not minimum:
            print(f"::warning title=E2E matrix::No {image_abi} image of API {sdk_min}", file=sys.stderr)

    min_target_canary("x86_64")
    min_target_canary("x86")
    min_target_canary("arm64-v8a")
    # armeabi-v7a: its own images if they reach sdkMin (none have since API 25), else as 32-bit on arm64-v8a ones
    if any(i[2] == "armeabi-v7a" and (release(i[0]) or 0) >= sdk_min for i in all_images):
        min_target_canary("armeabi-v7a")
    else:
        min_target_canary("armeabi-v7a", force="armeabi-v7a", on="arm64-v8a")

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
