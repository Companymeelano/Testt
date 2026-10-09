#!/usr/bin/env python3
"""Find Atiran's user_password recipe from ONE known test vector (offline).

You give it a test password (+ the username it was set for) and the stored
bytes (hex) printed by:
    python probe_atiran.py --host H --db D --user U --password P --pw-dump TESTUSER

It tries every plausible standard transform and prints the matching recipe
so the developer can add it to AtiranAuth.verifyPassword(). Pure stdlib.

Usage:
    python crack_probe.py --password '1234' --user test1 --hex 5D41402ABC...
"""
import argparse
import base64
import hashlib

ALGS = ["md5", "sha1", "sha224", "sha256", "sha384", "sha512"]
CODECS = ["utf-8", "utf-16-le", "utf-16-be", "windows-1256", "latin-1"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--password", required=True)
    ap.add_argument("--user", default="")
    ap.add_argument("--hex", required=True, help="stored bytes as hex (from --pw-dump)")
    a = ap.parse_args()

    try:
        target = bytes.fromhex(a.hex.strip())
    except ValueError:
        print("bad --hex (must be even-length hex)")
        return 1
    print(f"target: {len(target)} bytes")

    texts = [a.password, a.password.strip()]
    users = []
    u = (a.user or "").strip()
    if u:
        users = [u]
        if u.upper() not in users:
            users.append(u.upper())
        if u.lower() not in users:
            users.append(u.lower())

    tried = 0

    def check(label, blob):
        nonlocal tried
        tried += 1
        if blob == target:
            print(f"\n*** MATCH after {tried} tries ***\nrecipe: {label}\n")
            return True
        return False

    # 1. raw bytes + base64/hex wrappings
    for t in texts:
        for cs in CODECS:
            try:
                raw = t.encode(cs)
            except Exception:
                continue
            if check(f"raw bytes ({cs})", raw):
                return 0
            for wraplabel, wrap in [
                ("base64-ascii", base64.b64encode(raw)),
                ("base64-utf16le", base64.b64encode(raw).decode().encode("utf-16-le")),
                ("hex-ascii-upper", raw.hex().upper().encode()),
                ("hex-ascii-lower", raw.hex().encode()),
                ("hex-utf16le", raw.hex().encode("utf-16-le")),
            ]:
                if check(f"{wraplabel} of raw ({cs})", wrap):
                    return 0

    # 2. plain hashes (+ truncated / zero-padded-to-50 forms)
    for t in texts:
        for cs in CODECS:
            try:
                raw = t.encode(cs)
            except Exception:
                continue
            for alg in ALGS:
                d = hashlib.new(alg, raw).digest()
                if check(f"{alg}(pw as {cs})", d):
                    return 0
                if len(d) > len(target) and check(
                        f"{alg}(pw as {cs}) truncated to {len(target)}", d[:len(target)]):
                    return 0
                if len(d) < 50 and check(
                        f"{alg}(pw as {cs}) zero-padded to 50", d + b"\x00" * (50 - len(d))):
                    return 0
                if check(f"hex-ascii of {alg}(pw as {cs})", d.hex().encode()):
                    return 0
                if check(f"hex-ascii-upper of {alg}(pw as {cs})", d.hex().upper().encode()):
                    return 0
                if check(f"double-{alg}(pw as {cs})",
                         hashlib.new(alg, d).digest()):
                    return 0

    # 3. username-salted hashes
    for t in texts:
        for salt in users:
            for combo, how in [(salt + t, "user+pw"), (t + salt, "pw+user"),
                               (salt + ":" + t, "user:pw"), (t + ":" + salt, "pw:user")]:
                for cs in ["utf-8", "utf-16-le"]:
                    raw = combo.encode(cs)
                    for alg in ["md5", "sha1", "sha256"]:
                        if check(f"{alg}({how} as {cs}, user={salt!r})",
                                 hashlib.new(alg, raw).digest()):
                            return 0

    # 4. PBKDF2 (.NET Rfc2898DeriveBytes style) with guessable salts
    for salt in users + [""]:
        for cs in ["utf-8", "utf-16-le"]:
            sb = salt.encode(cs)
            for it in (1000, 10000):
                for kl in (16, 20, 32, len(target)):
                    if kl <= 0 or kl > 64:
                        continue
                    try:
                        d = hashlib.pbkdf2_hmac("sha1", a.password.encode("utf-8"), sb, it, kl)
                    except Exception:
                        continue
                    if check(f"PBKDF2-SHA1(iter={it}, salt=user-as-{cs}, keylen={kl})", d):
                        return 0

    print(f"\nno match after {tried} tries.")
    print("The hash is custom (or server-side pwdencrypt — the app already tries")
    print("pwdcompare() online). Ask the Atiran vendor for the password algorithm.")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
