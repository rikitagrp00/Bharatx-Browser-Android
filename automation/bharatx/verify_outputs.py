#!/usr/bin/env python3
"""Inspect actual packaged APK identity, policy assets and signature where required."""
# SPDX-License-Identifier: MPL-2.0
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def verify(directory, build_tools, *, signed=False):
    apks = sorted(directory.rglob("*.apk"))
    bundles = sorted(directory.rglob("*.aab"))
    if len(apks) < 2 or not bundles:
        raise ValueError("Expected debug/release APKs and an AAB; no release output is silently omitted")
    properties = dict(line.split("=", 1) for line in (ROOT / "branding/bharatx.properties").read_text().splitlines() if line and not line.startswith("#"))
    expected_codes = {int(properties["versionCode"]) * 10 + offset for offset in (1, 2, 3, 9)}
    records = []
    for artifact in apks + bundles:
        with zipfile.ZipFile(artifact) as archive:
            if archive.testzip() is not None:
                raise ValueError("Corrupt archive: " + artifact.name)
            prefix = "base/" if artifact.suffix == ".aab" else ""
            for name in ("about", "privacy", "disclaimer"):
                packaged = archive.read(prefix + "assets/bharatx/" + name + ".html")
                expected = (ROOT / "app/src/main/assets/bharatx" / (name + ".html")).read_bytes()
                if packaged != expected:
                    raise ValueError("Policy assets differ: " + artifact.name)
            if not any(name.endswith("libxul.so") for name in archive.namelist()):
                raise ValueError("Missing GeckoView native runtime: " + artifact.name)
            catalog = json.loads((ROOT / "app/src/main/assets/vpn/catalog.json").read_text())
            for asset in ["vpn/catalog.json"] + [entry["asset"] for entry in catalog["servers"]]:
                if archive.read(prefix + "assets/" + asset) != (ROOT / "app/src/main/assets" / asset).read_bytes():
                    raise ValueError("VPN asset differs: " + artifact.name + ":" + asset)
            abis = {name.split("/")[-2] for name in archive.namelist() if name.endswith("/libxul.so")}
            expected_abis = {"armeabi-v7a", "arm64-v8a", "x86_64"}
            if not abis <= expected_abis or (artifact.suffix == ".aab" and abis != expected_abis):
                raise ValueError("Unexpected browser/VPN ABIs: " + artifact.name)
            from vpn_audit import verify_elf
            for abi in abis:
                for library in ("libopenvpn.so", "libovpnexec.so"):
                    entry = prefix + "lib/" + abi + "/" + library
                    verify_elf(archive.read(entry), abi)
                    if artifact.suffix == ".apk" and archive.getinfo(entry).compress_type != zipfile.ZIP_DEFLATED:
                        raise ValueError("VPN engine must use legacy native extraction: " + artifact.name)
        record = {"file": artifact.name, "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest()}
        if artifact.suffix == ".apk":
            badging = subprocess.check_output([str(build_tools / "aapt2"), "dump", "badging", str(artifact)], text=True)
            package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
            if package is None or package[1] not in ("com.nitaistudio.bharatxbrowser", "com.nitaistudio.bharatxbrowser.debug"):
                raise ValueError("Unexpected compiled package identity: " + artifact.name)
            if "application-label:'BharatX Browser'" not in badging:
                raise ValueError("Unexpected compiled launcher label: " + artifact.name)
            if package[3] != properties["versionName"] or int(package[2]) not in expected_codes:
                raise ValueError("Compiled package version differs from branding/bharatx.properties")
            tree = subprocess.check_output([str(build_tools / "aapt2"), "dump", "xmltree", str(artifact), "--file", "AndroidManifest.xml"], text=True)
            if not re.search(r"extractNativeLibs[^\n]*0xffffffff", tree):
                raise ValueError("VPN executable extraction is disabled: " + artifact.name)
            if "com.nitaistudio.rikivpn.vpnservice.RikiVpnService" not in tree or "android.permission.BIND_VPN_SERVICE" not in tree:
                raise ValueError("SDK VpnService manifest was not merged: " + artifact.name)
            record.update({"package": package[1], "versionCode": int(package[2]), "versionName": package[3]})
            if package[1].endswith(".debug") or signed:
                subprocess.run([str(build_tools / "apksigner"), "verify", str(artifact)], check=True)
        elif signed:
            verification = subprocess.check_output(["jarsigner", "-verify", str(artifact)], text=True)
            if "jar verified." not in verification:
                raise ValueError("App bundle signature was not verified: " + artifact.name)
        records.append(record)
    (directory / "package-verification.json").write_text(json.dumps({"passed": True, "releaseSigned": signed, "artifacts": records}, indent=2) + "\n")
    (directory / "SHA256SUMS.txt").write_text("".join(x["sha256"] + "  " + x["file"] + "\n" for x in records))
    print("Verified actual Android packages:", len(records))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--signed", action="store_true")
    args = parser.parse_args()
    verify(args.directory.resolve(), args.build_tools.resolve(), signed=args.signed)
