#!/usr/bin/env python3
"""Build the signed update descriptor (version.json) for a MEELANO release.

Reads package + versionCode + versionName from each APK via aapt, hashes the
files, and MACs (versionCode, sha256) with the license secret using the EXACT
scheme of License.updateMac (HMAC-SHA256, first 8 Crockford chars of the
digest). The app re-verifies that MAC before trusting anything.

Usage (from the release workflow):
    MEELANO_UPDATE_SECRET=... python3 tools/make_version.py \
        --tag v24.0.0 \
        --manager app/build/outputs/apk/release/app-release.apk \
        --admin admin/build/outputs/apk/release/admin-release.apk \
        --aapt "$ANDROID_HOME/build-tools/35.0.0/aapt" \
        --out release-out/

Outputs in --out: meelano-manager-v24.0.0.apk, meelano-admin-vX.Y.Z.apk,
version.json. Fails loudly when the tag does not match the manager APK's
own versionName (that mismatch would show users the wrong What's New).
"""

import argparse
import hashlib
import hmac
import json
import os
import re
import shutil
import subprocess
import sys

CROCK = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"


def crock8(digest: bytes) -> str:
    """First 8 Crockford chars of a hash — bit-identical to License.crock(h, 8)."""
    bits = "".join(format(b, "08b") for b in digest)
    return "".join(CROCK[int(bits[i:i + 5], 2)] for i in range(0, 40, 5))


def update_mac(secret: str, code: int, sha: str) -> str:
    """Identical to License.updateMac(code, sha)."""
    msg = ("UPDATE1|%d|%s" % (code, sha.strip().lower())).encode("utf-8")
    return crock8(hmac.new(secret.encode("utf-8"), msg, hashlib.sha256).digest())


def badging(aapt: str, apk: str):
    out = subprocess.run([aapt, "dump", "badging", apk],
                         capture_output=True, text=True, check=True).stdout
    m = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", out)
    if not m:
        raise SystemExit("aapt could not parse %s" % apk)
    return m.group(1), int(m.group(2)), m.group(3)


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def self_test(secret: str) -> None:
    """The MAC must be stable and version-bound, or the app would reject everything."""
    fake = "0123456789abcdef" * 4
    a = update_mac(secret, 17, fake)
    assert len(a) == 8 and update_mac(secret, 17, fake) == a
    assert update_mac(secret, 17, fake.upper()) == a
    assert update_mac(secret, 18, fake) != a
    print("  self-test: MAC scheme OK (%s...)" % a[:4])


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True, help="e.g. v24.0.0")
    ap.add_argument("--manager", required=True)
    ap.add_argument("--admin", required=True)
    ap.add_argument("--aapt", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--msg", default="", help="short Persian line shown in the update dialog")
    args = ap.parse_args()

    secret = os.environ.get("MEELANO_UPDATE_SECRET", "")
    if len(secret) < 16:
        raise SystemExit("MEELANO_UPDATE_SECRET is missing (GitHub Secret, = S1+S2+S3 in License.java)")
    self_test(secret)

    repo = os.environ.get("GITHUB_REPOSITORY", "Companymeelano/Testt")
    tag = args.tag.strip()
    expect_name = tag[1:] if tag.startswith("v") else tag
    os.makedirs(args.out, exist_ok=True)

    desc = {}
    for key, apk, expect_pkg in (("manager", args.manager, "ir.meelano.manager"),
                                 ("admin", args.admin, "ir.meelano.admin")):
        pkg, code, name = badging(args.aapt, apk)
        if pkg != expect_pkg:
            raise SystemExit("%s: package %s != %s (wrong APK?)" % (apk, pkg, expect_pkg))
        if key == "manager" and name != expect_name:
            raise SystemExit("tag %s != manager versionName %s — bump app/build.gradle first"
                             % (tag, name))
        fname = "meelano-%s-v%s.apk" % (key, name)
        shutil.copyfile(apk, os.path.join(args.out, fname))
        sha = sha256_of(apk)
        size = os.path.getsize(apk)
        desc[key] = {
            "code": code,
            "name": name,
            "apk": fname,
            "size": size,
            "sha256": sha,
            "mac": update_mac(secret, code, sha),
            "url": "https://github.com/%s/releases/download/%s/%s" % (repo, tag, fname),
            "msg": args.msg if key == "manager" else "",
        }
        print("  %s: code=%d name=%s size=%d sha=%s... mac=%s"
              % (key, code, name, size, sha[:12], desc[key]["mac"]))

    with open(os.path.join(args.out, "version.json"), "w", encoding="utf-8") as f:
        json.dump(desc, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote %s" % os.path.join(args.out, "version.json"))


if __name__ == "__main__":
    main()
