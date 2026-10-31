#!/usr/bin/env python3
"""Test app reward/purchase policy using the actual Play Billing model; never request live ads or purchases."""
# SPDX-License-Identifier: MPL-2.0
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def checked_file(path, info, target):
    if path is not None:
        data = path.read_bytes()
    else:
        with urllib.request.urlopen(info["url"], timeout=60) as response:
            data = response.read()
    if hashlib.sha256(data).hexdigest() != info["sha256"]:
        raise ValueError("Policy test dependency differs from the locked official artifact")
    target.write_bytes(data)
    return target


def verify(gradle_home, billing_aar=None, json_jar=None):
    lock = json.loads((ROOT / "branding/monetization-lock.json").read_text())
    stdlib = list((gradle_home / "lib").glob("kotlin-stdlib-*.jar"))
    if len(stdlib) != 1:
        raise ValueError("Expected Gradle 9.8.1's Kotlin standard library")
    with tempfile.TemporaryDirectory(prefix="bharatx-monetization-") as folder:
        temp = Path(folder)
        aar = checked_file(billing_aar, lock["billing"], temp / "billing.aar")
        json_dependency = checked_file(json_jar, lock["testJson"], temp / "json.jar")
        with zipfile.ZipFile(aar) as archive:
            billing = temp / "billing.jar"
            billing.write_bytes(archive.read("classes.jar"))
        classes = temp / "classes"
        classpath = os.pathsep.join(map(str, (billing, json_dependency, stdlib[0])))
        source = ROOT / "app/src/main/java/org/mozilla/fenix/bharatx/monetization"
        subprocess.run(["java", "-cp", str(gradle_home / "lib/*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                        "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath, "-d", str(classes),
                        *[str(source / name) for name in ("PremiumSessionPolicy.kt", "RewardedVideoPolicy.kt", "BharatXPurchasePolicy.kt")]], check=True)
        classpath = str(classes) + os.pathsep + classpath
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-cp", classpath, "-d", str(classes),
                        str(ROOT / "automation/bharatx/MonetizationPolicySmoke.java")], check=True)
        subprocess.run(["java", "-cp", classpath, "MonetizationPolicySmoke"], check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--gradle-home", type=Path)
    parser.add_argument("--billing-aar", type=Path)
    parser.add_argument("--json-jar", type=Path)
    args = parser.parse_args()
    home = args.gradle_home
    if home is None:
        user_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
        candidates = list((user_home / "wrapper/dists/gradle-9.8.1-bin").glob("*/gradle-9.8.1"))
        if len(candidates) != 1:
            raise SystemExit("Run ./gradlew --version first or pass --gradle-home")
        home = candidates[0]
    verify(home.resolve(), args.billing_aar, args.json_jar)
