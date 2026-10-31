#!/usr/bin/env python3
"""SDK packages required by the locked source, rather than guessed SDK versions."""
# SPDX-License-Identifier: MPL-2.0
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
sdk = json.loads((ROOT / "branding/build-lock.json").read_text())["androidSdk"]
config = (ROOT / "android-components/.config.yml").read_text()
for key, expected in (("compileSdkMajorVersion", sdk["compileSdkMajor"]), ("compileSdkMinorVersion", sdk["compileSdkMinor"]), ("targetSdkVersion", sdk["targetSdk"]), ("minSdkVersion", sdk["minSdk"])):
    actual = re.search(r"^" + key + r":\s*(\d+)\s*$", config, re.M)
    if actual is None or int(actual[1]) != expected:
        raise ValueError("Locked SDK configuration changed: " + key)
platform = str(sdk["compileSdkMajor"]) + ("." + str(sdk["compileSdkMinor"]) if sdk["compileSdkMinor"] else "")
for package in ("platform-tools", "platforms;android-" + platform, "build-tools;" + sdk["buildTools"], "ndk;" + sdk["ndk"]):
    print(package)
