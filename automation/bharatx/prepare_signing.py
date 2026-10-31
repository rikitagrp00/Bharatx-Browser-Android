#!/usr/bin/env python3
"""Use an existing upload key when configured; never generate a replacement key."""
# SPDX-License-Identifier: MPL-2.0
import base64
import os
from pathlib import Path

encoded = os.environ.get("BHARATX_KEYSTORE_BASE64", "").strip()
state = "unsigned"
if encoded:
    for name in ("BHARATX_KEYSTORE_PASSWORD", "BHARATX_KEY_ALIAS", "BHARATX_KEY_PASSWORD"):
        if not os.environ.get(name):
            raise ValueError("Signing configuration missing: " + name)
    path = Path(os.environ["RUNNER_TEMP"]) / "bharatx-upload-key.jks"
    path.write_bytes(base64.b64decode("".join(encoded.split()), validate=True))
    path.chmod(0o600)
    with open(os.environ["GITHUB_ENV"], "a") as stream:
        stream.write("BHARATX_KEYSTORE_PATH=" + str(path) + "\n")
    state = "signed"
with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
    stream.write("state=" + state + "\n")
print("Release signing:", state)
