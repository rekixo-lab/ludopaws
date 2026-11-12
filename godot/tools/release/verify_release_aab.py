#!/usr/bin/env python3
"""Verifies a Ludo Paws release bundle before anyone uploads it (CI and local).

  verify_release_aab.py BUNDLE.aab --bundletool bundletool.jar --version-code 17 \
      --version-name 2.0.0 [--expected-cert AA:BB:...]

Checks: package / versionCode / versionName, no restricted permissions, the real (not test)
AdMob App ID and ad units, https backend, arm64-v8a native libraries with 16 KB page alignment,
no tests / tools / debug / preview files in the pack, and (when given) the signing certificate.
Prints only public values. Exit 1 on any failure.
"""
import argparse, configparser, re, struct, subprocess, sys, zipfile

TEST_PUBLISHER = "ca-app-pub-3940256099942544"
BANNED_PATHS = re.compile(r"/(tests|tools|scripts/debug|scripts/previews|scenes/previews)/")
RESTRICTED = re.compile(r"RECORD_AUDIO|permission\.AD_ID|ACCESS_ADSERVICES_(AD_ID|ATTRIBUTION|TOPICS)")
failures = []

def check(ok, label):
    print(("ok   " if ok else "FAIL ") + label)
    if not ok:
        failures.append(label)

def elf_min_load_align(data):
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return None
    end = "<" if data[5] == 1 else ">"
    phoff = struct.unpack_from(end + "Q", data, 0x20)[0]
    phentsize, phnum = struct.unpack_from(end + "HH", data, 0x36)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        if struct.unpack_from(end + "I", data, off)[0] == 1:  # PT_LOAD
            aligns.append(struct.unpack_from(end + "Q", data, off + 0x30)[0])
    return min(aligns) if aligns else None

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("bundle")
    ap.add_argument("--bundletool", required=True)
    ap.add_argument("--version-code", required=True)
    ap.add_argument("--version-name", required=True)
    ap.add_argument("--package", default="com.rekixo.ludopaws")
    ap.add_argument("--expected-cert")
    a = ap.parse_args()

    manifest = subprocess.run(["java", "-jar", a.bundletool, "dump", "manifest", "--bundle", a.bundle],
                              check=True, capture_output=True, text=True).stdout
    check('package="%s"' % a.package in manifest, "package = " + a.package)
    check('android:versionCode="%s"' % a.version_code in manifest, "versionCode = " + a.version_code)
    check('android:versionName="%s"' % a.version_name in manifest, "versionName = " + a.version_name)
    check(not RESTRICTED.search(manifest), "no AD_ID / AdServices / RECORD_AUDIO permission")
    app_id = re.search(r'android:name="com\.google\.android\.gms\.ads\.APPLICATION_ID"\s+android:value="([^"]+)"', manifest)
    app_id = app_id.group(1) if app_id else ""
    check(re.fullmatch(r"ca-app-pub-\d{16}~\d{10}", app_id) is not None and not app_id.startswith(TEST_PUBLISHER),
          "real AdMob App ID (%s~…)" % app_id.split("~")[0])

    with zipfile.ZipFile(a.bundle) as z:
        names = z.namelist()
        arm64 = [n for n in names if n.startswith("base/lib/arm64-v8a/") and n.endswith(".so")]
        check(bool(arm64), "arm64-v8a native libraries present (%d)" % len(arm64))
        for n in arm64:
            align = elf_min_load_align(z.read(n))
            check(align is not None and align >= 16384, "16 KB page alignment: %s (%s)" % (n.rsplit("/", 1)[1], align))
        banned = [n for n in names if BANNED_PATHS.search("/" + n.split("/assets/", 1)[-1] if "/assets/" in n else "/" + n)]
        check(not banned, "no tests / tools / debug / preview files (%s)" % banned[:3])
        cfgs = [n for n in names if n.endswith("/config/release.local.cfg")]
        check(len(cfgs) == 1, "release config packed once")
        if cfgs:
            text = z.read(cfgs[0]).decode()
            c = configparser.ConfigParser()
            c.read_string(text)
            get = lambda s, k: c.get(s, k, fallback="").strip().strip('"')
            check(TEST_PUBLISHER not in text, "no Google test ad IDs in the release config")
            for key in ("rewarded_unit_id", "interstitial_unit_id"):
                check(re.fullmatch(r"ca-app-pub-\d{16}/\d{10}", get("admob", key)) is not None, "real AdMob %s" % key)
            check(get("backend", "base_url").startswith("https://"), "https backend URL")
            check(not re.search(r"(?i)password|secret|keystore|token|private", text), "config holds only public values")

    if a.expected_cert:
        out = subprocess.run(["keytool", "-printcert", "-jarfile", a.bundle], capture_output=True, text=True).stdout
        m = re.search(r"SHA256:\s*([0-9A-F:]{95})", out)
        cert = m.group(1) if m else ""
        print("certificate SHA-256: " + (cert or "(unsigned)"))
        check(cert == a.expected_cert.upper(), "signing certificate matches v16")

    if failures:
        print("%d check(s) failed" % len(failures), file=sys.stderr)
        sys.exit(1)
    print("Release bundle verified.")

if __name__ == "__main__":
    main()
