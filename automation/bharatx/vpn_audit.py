#!/usr/bin/env python3
"""Inspect the supplied SDK's native engines and bundled profile contract, without connecting."""
# SPDX-License-Identifier: MPL-2.0
import hashlib
import io
import json
from pathlib import Path
import re
import shlex
import struct
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MACHINES = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86": (1, 3), "x86_64": (2, 62)}


def verify_elf(data, abi):
    if data[:4] != b"\x7fELF" or data[5] != 1:
        raise ValueError("Not a little-endian ELF engine: " + abi)
    elf_class, machine = MACHINES[abi]
    assert data[4] == elf_class and struct.unpack_from("<H", data, 18)[0] == machine
    assert struct.unpack_from("<H", data, 16)[0] == 3  # ET_DYN: shared engine or PIE launcher
    if elf_class == 2:
        offset = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        alignment_offset, fmt = 48, "<Q"
    else:
        offset = struct.unpack_from("<I", data, 28)[0]
        size, count = struct.unpack_from("<HH", data, 42)
        alignment_offset, fmt = 28, "<I"
    loads = [struct.unpack_from(fmt, data, offset + i * size + alignment_offset)[0]
             for i in range(count) if struct.unpack_from("<I", data, offset + i * size)[0] == 1]
    required = 16384 if elf_class == 2 else 4096
    assert loads and all(alignment >= required for alignment in loads), "Engine LOAD alignment insufficient: " + abi


def profile_directives(text):
    block = None
    directives = []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(("#", ";")):
            continue
        if block:
            if line == "</" + block + ">":
                block = None
            continue
        if line.startswith("<"):
            assert line in ("<ca>", "<cert>", "<key>", "<tls-auth>", "<tls-crypt>", "<tls-crypt-v2>", "<pkcs12>"), "Unsupported inline profile block"
            block = line[1:-1]
            continue
        tokens = shlex.split(line, comments=True)
        if tokens:
            directives.append(tokens)
    assert block is None, "Unclosed inline profile block"
    # No script hooks, plugin loading, management overrides, credential files or external keys.
    permitted = set("client dev proto remote cipher data-ciphers data-ciphers-fallback auth remote-cert-tls nobind persist-key persist-tun resolv-retry verb auth-user-pass key-direction tls-version-min verify-x509-name remote-random connect-timeout connect-retry connect-retry-max reneg-sec ping ping-restart mute explicit-exit-notify auth-nocache pull tun-mtu mssfix fast-io float socket-flags route route-ipv6 redirect-gateway dhcp-option compress comp-lzo".split())
    for tokens in directives:
        assert tokens[0] in permitted, "Unsupported VPN directive: " + tokens[0]
        if tokens[0] == "auth-user-pass":
            assert len(tokens) == 1, "Do not bundle credential files"
    assert any(tokens[0] == "remote" for tokens in directives)
    assert any(tokens == ["remote-cert-tls", "server"] for tokens in directives), "Require server certificate role verification"
    return directives


def audit_vpn():
    lock = json.loads((ROOT / "branding/vpn-sdk-lock.json").read_text())
    artifact = (ROOT / lock["artifact"]).read_bytes()
    assert hashlib.sha256(artifact).hexdigest() == lock["sha256"], "SDK differs from locked supplied AAR"
    with zipfile.ZipFile(io.BytesIO(artifact)) as aar:
        for abi in lock["abisInAar"]:
            for library in lock["engineFiles"]:
                verify_elf(aar.read(f"jni/{abi}/{library}"), abi)
        manifest = ET.fromstring(aar.read("AndroidManifest.xml"))
        service = manifest.find("application/service")
        assert service.get(ANDROID + "permission") == "android.permission.BIND_VPN_SERVICE"
        assert service.get(ANDROID + "exported") == "false"
        assert service.get(ANDROID + "foregroundServiceType") == "systemExempted"
        with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as classes:
            assert "com/nitaistudio/rikivpn/api/RikiVpn.class" in classes.namelist()

    assets = ROOT / "app/src/main/assets"
    catalog = json.loads((assets / "vpn/catalog.json").read_text())
    assert catalog["schemaVersion"] == 1
    ids, paths = set(), set()
    tiers = {"free": 0, "premium": 0}
    for server in catalog["servers"]:
        tier, asset = server["tier"], server["asset"]
        assert tier in tiers
        assert re.fullmatch(r"vpn/" + tier + r"/[a-zA-Z0-9_.-]+\.ovpn", asset)
        assert re.fullmatch(r"[a-z0-9-]{1,64}", server["id"])
        assert server["id"] not in ids and asset not in paths
        ids.add(server["id"]); paths.add(asset)
        data = (assets / asset).read_bytes()
        assert 0 < len(data) <= 262144
        assert hashlib.sha256(data).hexdigest() == server["sha256"]
        directives = profile_directives(data.decode())
        requires_login = any(tokens[0] == "auth-user-pass" for tokens in directives)
        assert requires_login == server["requiresCredentials"]
        assert server["access"] == ("rewarded" if tier == "premium" else "free"), "Premium must use the app reward gate"
        tiers[tier] += 1
    assert set(p.relative_to(assets).as_posix() for p in (assets / "vpn").rglob("*.ovpn")) == paths
    app_manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    assert any(a.get(ANDROID + "name") == "org.mozilla.fenix.bharatx.vpn.BharatXVpnActivity" and a.get(ANDROID + "exported") == "false"
               for a in app_manifest.findall("application/activity"))
    return ["RikiVpn AAR integrity and ELF machine verified for four ABIs; 64-bit engines have 16 KiB LOAD alignment",
            f"Asset catalog validated: {tiers['free']} free, {tiers['premium']} reward-gated premium profiles; no embedded login files"]


if __name__ == "__main__":
    print(json.dumps({"passed": True, "checks": audit_vpn(), "tunnelTested": False}, indent=2))
