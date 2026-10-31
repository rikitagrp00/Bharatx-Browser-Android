#!/usr/bin/env python3
"""Fetch the exact missing Android Components revision and apply the fork patches."""
# SPDX-License-Identifier: MPL-2.0
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def run(*args, cwd=ROOT):
    subprocess.run(list(map(str, args)), cwd=cwd, check=True)


def replace(path, old, new):
    text = path.read_text()
    if old in text:
        path.write_text(text.replace(old, new))


def apply_patch(components, patch):
    reverse = subprocess.run(["git", "apply", "--reverse", "--check", str(patch)], cwd=components, capture_output=True)
    if reverse.returncode == 0:
        return
    run("git", "apply", "--check", patch, cwd=components)
    run("git", "apply", patch, cwd=components)


def prepare(components, *, fetch=True):
    lock = json.loads((ROOT / "branding/build-lock.json").read_text())
    pinned = lock["androidComponents"]
    if not (components / ".git").exists():
        if components.exists() and any(components.iterdir()):
            raise ValueError("Android Components must be the locked Git checkout; existing files were left intact")
        if not fetch:
            raise ValueError("Missing locked Android Components checkout")
        components.mkdir(parents=True, exist_ok=True)
        run("git", "init", components)
        run("git", "remote", "add", "origin", pinned["repository"], cwd=components)
        run("git", "fetch", "--depth", "1", "origin", pinned["commit"], cwd=components)
        run("git", "checkout", "--detach", "FETCH_HEAD", cwd=components)
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=components, text=True).strip()
    if revision != pinned["commit"]:
        raise ValueError("Android Components revision differs from branding/build-lock.json")

    assets = components / "components/feature/search/src/main/assets"
    for xml in (ROOT / "automation/iceraven/assets").glob("*.xml"):
        shutil.copy2(xml, assets / "searchplugins" / xml.name)
    shutil.copy2(ROOT / "automation/iceraven/assets/list.json", assets / "search/list.json")
    reader = components / "components/feature/search/src/main/java/mozilla/components/feature/search/storage/SearchEngineReader.kt"
    text = reader.read_text()
    start = text.index("internal val GENERAL_SEARCH_ENGINE_IDS")
    position = text.index("setOf(", start) + len("setOf(")
    end = text.index("\n    )", position)
    missing = [name for name in ("startpage", "brave") if f'"{name}"' not in text[position:end]]
    reader.write_text(text[:position] + "".join(f'\n        "{name}",' for name in missing) + text[position:])

    for pattern in ("*.gradle", "*.kts"):
        for file in components.rglob(pattern):
            replace(file, "../../../../../gradle/libs.versions.toml", "../../../gradle/libs.versions.toml")
    replace(ROOT / "gradle/libs.versions.toml", ', version.ref = "python-envs-plugin"', "")
    integrity = components / "components/lib/integrity-googleplay/build.gradle"
    text = integrity.read_text()
    plugin = "alias(libs.plugins.python.envs.plugin)"
    if plugin not in text:
        integrity.write_text(text.replace("plugins {", "plugins {\n    " + plugin, 1))
    replace(components / "plugins/config/src/main/java/ConfigPlugin.kt", "mobile/android/version.txt", "version.txt")
    replace(components / "components/lib/crash/build.gradle", "mobile/android/", "")
    for name in ("top_sites_no_most_visted_sites.patch", "toolbar.patch"):
        apply_patch(components, ROOT / "automation/iceraven/patches" / name)

    run(sys.executable, ROOT / "automation/iceraven/toolkit/crashreporter/generate_crash_reporter_sources.py")
    suffix = lock["publicSuffixList"]
    target = ROOT / "netwerk/dns/effective_tld_names.dat"
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != suffix["sha256"]:
        request = urllib.request.Request(suffix["url"], headers={"User-Agent": "BharatXBrowser-Build/1.36.0"})
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != suffix["sha256"]:
            raise ValueError("Public suffix list checksum differs from the lock")
        target.write_bytes(data)
    print("Prepared locked Android Components and fork patches:", revision, flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--components", type=Path, default=ROOT / "android-components")
    parser.add_argument("--no-fetch", action="store_true")
    args = parser.parse_args()
    prepare(args.components.resolve(), fetch=not args.no_fetch)
