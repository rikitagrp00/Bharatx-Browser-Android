#!/usr/bin/env python3
"""Run parser contract checks against the exact bundled AAR, using Gradle's Kotlin stdlib."""
# SPDX-License-Identifier: MPL-2.0
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile
from vpn_audit import ROOT, audit_vpn, profile_directives


def run(stdlib):
    audit_vpn()
    catalog = json.loads((ROOT / "app/src/main/assets/vpn/catalog.json").read_text())
    # Reject unsupported hooks and external credential files in the app's asset contract.
    for bad in ("auth-user-pass account.txt", "up script.sh", "plugin addon.so", "ca ca.crt"):
        try:
            profile_directives("client\nremote example.invalid 1194\nremote-cert-tls server\n" + bad + "\n")
        except AssertionError:
            pass
        else:
            raise AssertionError("Asset policy accepted an external file or script directive")
    with tempfile.TemporaryDirectory(prefix="bharatx-vpn-parser-") as directory:
        temp = Path(directory)
        with zipfile.ZipFile(ROOT / "app/libs/rikivpn-1.0.0.aar") as aar:
            jar = temp / "classes.jar"
            jar.write_bytes(aar.read("classes.jar"))
        classpath = os.pathsep.join((str(jar), str(stdlib)))
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-cp", classpath,
                        "-d", str(temp), str(ROOT / "automation/bharatx/VpnProfileSmoke.java")], check=True)
        paths = [str(ROOT / "app/src/main/assets" / entry["asset"]) for entry in catalog["servers"] if not entry["requiresCredentials"]]
        if paths:
            subprocess.run(["java", "-cp", str(temp) + os.pathsep + classpath, "VpnProfileSmoke", *paths], check=True)
    print("VPN asset policy negative checks passed: 4; no tunnel attempted")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--kotlin-stdlib", type=Path)
    args = parser.parse_args()
    stdlib = args.kotlin_stdlib
    if stdlib is None:
        home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
        matches = sorted((home / "wrapper/dists/gradle-9.8.1-bin").glob("*/gradle-9.8.1/lib/kotlin-stdlib-*.jar"))
        if len(matches) != 1:
            raise SystemExit("Run ./gradlew --version first, or pass --kotlin-stdlib /path/to/kotlin-stdlib.jar")
        stdlib = matches[0]
    run(stdlib.resolve())
