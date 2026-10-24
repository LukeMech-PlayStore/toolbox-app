#!/usr/bin/env python3
"""
The end-to-end tests' emulator legs (e2e-tests.yml), from what the app supports and what Google has: for x86_64
and x86, its oldest Android (sdkMin), the one it targets (sdkTarget) and the newest there is (canary), on Google's
own system images (sys-img/<tag>/sys-img2-4.xml), each with the channel it's on.

- an ABI whose images end before sdkTarget (x86's at API 30): sdkMin and its newest image instead
- ARM: GitHub's runners can't run an ARM emulator (x86 hosts refuse ARM images, the ARM Linux runners have no
  KVM, the Apple Silicon ones are M1s with no virtualization inside their VM), so the ARM app on the x86_64 images
  that translate ARM apps, as each image's build.prop says (its ABI list, read out of the image's zip without
  downloading it): for each ARM ABI, the oldest translating it, the target and the canary if they do (else the
  newest that does). Allowed to fail: our ARM code under Google's translator
- a job per leg, running every test

    matrix.py <gradle.properties>     prints {"include": [...]} for strategy.matrix (and the legs as a table in the
                                      job's summary, on GitHub)
"""
import json
import os
import re
import struct
import sys
import urllib.request
import zlib

ABIS = ["x86_64", "x86"]
TAGS = ["google_apis", "android"]
# Among the images of a release's newest minor (37.2): Google APIs first (the device a user has), 4 KB pages before 16 KB
# ones (the newest minors and canaries come in 16 KB alone)
TARGET_ORDER = ["google_apis", "default", "google_apis_ps16k"]
CHANNELS = {"channel-0": "stable", "channel-1": "beta", "channel-2": "dev", "channel-3": "canary"}
ARM_ABIS = ["arm64-v8a", "armeabi-v7a"]
REPOSITORY = "https://dl.google.com/android/repository"


def properties(path):
    props = {}
    for line in open(path, encoding="utf-8"):
        k, sep, v = line.partition("=")
        if sep and not line.lstrip().startswith("#"):
            props[k.strip()] = v.strip()
    return props


def images():
    """Every system image: (version token, e.g. "29", "37.0", "CANARY"; target; ABI; channel; its zip's URL)."""
    found = []
    for tag in TAGS:
        xml = urllib.request.urlopen(f"{REPOSITORY}/sys-img/{tag}/sys-img2-4.xml", timeout=60).read().decode("utf-8")
        for m in re.finditer(r'<remotePackage path="system-images;android-([^;"]+);([^;"]+);([^;"]+)"[^>]*>(.*?)</remotePackage>', xml, re.S):
            token, target, abi, body = m.groups()
            if "obsolete" in m.group(0)[:200]:
                continue
            channel = re.search(r'channelRef ref="([^"]+)"', body)
            zip_name = re.search(r"<url>([^<]+)</url>", body)
            found.append((token, target, abi, CHANNELS.get(channel.group(1) if channel else "", "stable"),
                          f"{REPOSITORY}/sys-img/{tag}/{zip_name.group(1)}" if zip_name else None))
    return found


def fetch(url, start, end):
    request = urllib.request.Request(url, headers={"Range": f"bytes={start}-{end}"})
    return urllib.request.urlopen(request, timeout=60).read()


def abi_list(image):
    """The ABIs [image] runs apps of (its build.prop's ro.product.cpu.abilist), read out of its zip (a gigabyte or
    so) by ranges: the zip's directory, then the build.prop alone."""
    url = image[4]
    size = int(urllib.request.urlopen(urllib.request.Request(url, method="HEAD"), timeout=60).headers["Content-Length"])
    tail = fetch(url, max(0, size - 65536), size - 1)
    end = tail.rfind(b"PK\x05\x06")
    dir_size, dir_offset = struct.unpack("<II", tail[end + 12:end + 20])
    if dir_offset == 0xFFFFFFFF:
        end64 = tail.rfind(b"PK\x06\x06")
        dir_size, dir_offset = struct.unpack("<QQ", tail[end64 + 40:end64 + 56])
    directory = fetch(url, dir_offset, dir_offset + dir_size - 1)
    at = 0
    while directory[at:at + 4] == b"PK\x01\x02":
        method, = struct.unpack("<H", directory[at + 10:at + 12])
        packed, unpacked = struct.unpack("<II", directory[at + 20:at + 28])
        name_length, extra_length, comment_length = struct.unpack("<HHH", directory[at + 28:at + 34])
        offset, = struct.unpack("<I", directory[at + 42:at + 46])
        name = directory[at + 46:at + 46 + name_length].decode()
        if name.count("/") <= 1 and name.endswith("/build.prop"):
            extra = directory[at + 46 + name_length:at + 46 + name_length + extra_length]
            if 0xFFFFFFFF in (packed, offset):
                # Zip64: the sizes and the offset that didn't fit, in that order, in the extra field
                big, x = [], 0
                while x < len(extra):
                    kind, length = struct.unpack("<HH", extra[x:x + 4])
                    if kind == 1:
                        big = list(struct.unpack(f"<{length // 8}Q", extra[x + 4:x + 4 + length]))
                    x += 4 + length
                if unpacked == 0xFFFFFFFF:
                    big.pop(0)
                if packed == 0xFFFFFFFF:
                    packed = big.pop(0)
                if offset == 0xFFFFFFFF:
                    offset = big.pop(0)
            local_name, local_extra = struct.unpack("<HH", fetch(url, offset + 26, offset + 29))
            start = offset + 30 + local_name + local_extra
            data = fetch(url, start, start + packed - 1)
            text = (zlib.decompress(data, -15) if method == 8 else data).decode("utf-8", "replace")
            found = re.search(r"^ro\.(?:system\.)?product\.cpu\.abilist=(.*)$", text, re.M)
            return found.group(1).strip().split(",") if found else []
        at += 46 + name_length + extra_length + comment_length
    return []


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
        token, target, arch, channel, _ = image
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

    # ARM, on x86_64's images (one per release from sdkMin, and the canary) whose ABI list has it: the oldest, the
    # target and the canary, or the newest when neither does
    levels = sorted({release(i[0]) for i in all_images if i[2] == "x86_64" and (release(i[0]) or 0) >= sdk_min})
    candidates = [i for i in [pick(all_images, "x86_64", level) for level in levels] + [canary(all_images, "x86_64")] if i]
    lists = {i: abi_list(i) for i in candidates}
    target, newest = pick(all_images, "x86_64", sdk_target), canary(all_images, "x86_64")
    for abi in ARM_ABIS:
        runs = [i for i in candidates if abi in lists[i]]
        if not runs:
            print(f"::warning title=E2E matrix::No x86_64 image runs {abi} apps", file=sys.stderr)
            continue
        chosen = {runs[0]: "min" if release(runs[0][0]) == sdk_min else "oldest translating"}
        for image, label in ((target, "target"), (newest, "newest")):
            if image in runs:
                chosen.setdefault(image, label)
        if target not in runs and newest not in runs:
            chosen.setdefault(runs[-1], "newest translating")
        for image, label in chosen.items():
            leg(image, label, abi)

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### Emulator legs\n\n")
            f.write(f"sdkMin {sdk_min}, sdkTarget {sdk_target}\n\n")
            f.write("| Leg | System image | Channel |\n|---|---|---|\n")
            for l in legs:
                f.write(f"| {l['name']} | android-{l['image']};{l['target']};{l['arch']} | {l['channel']} |\n")
            f.write("\nThe x86_64 images' ABIs: " + "; ".join(f"{i[0]}: {', '.join(v)}" for i, v in lists.items()) + "\n")
    print(json.dumps({"include": legs}))


if __name__ == "__main__":
    main()
