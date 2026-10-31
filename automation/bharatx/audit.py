#!/usr/bin/env python3
"""Offline product checks. These checks do not claim that an APK was compiled."""
# SPDX-License-Identifier: MPL-2.0
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.scripts = False

    def handle_starttag(self, tag, attrs):
        self.scripts |= tag == "script"
        for key, value in attrs:
            if key in ("src", "href"):
                self.links.append(value)


def audit():
    checks = []
    properties = dict(line.split("=", 1) for line in (ROOT / "branding/bharatx.properties").read_text().splitlines() if line and not line.startswith("#"))
    assert properties["applicationId"] == "com.nitaistudio.bharatxbrowser"
    assert re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", properties["versionName"])
    assert 1 <= int(properties["versionCode"]) <= 210000000
    gradle = (ROOT / "app/build.gradle").read_text()
    assert 'applicationId bharatxBrand.getProperty("applicationId")' in gradle
    assert 'applicationIdSuffix ".iceraven"' not in gradle
    assert "output.versionCode.set(bharatxVersionBase * 10 + abiOffset)" in gradle
    checks.append("BharatX app ID and independent APK/AAB version codes")

    android = "{http://schemas.android.com/apk/res/android}"
    count = 0
    for xml in (ROOT / "app/src").glob("*/res/**/*.xml"):
        root = ET.parse(xml).getroot()
        count += 1
        if root.tag == "resources":
            for string in root.findall("string"):
                name = string.get("name", "")
                value = "".join(string.itertext())
                if name == "app_name":
                    assert value == properties["appName"], str(xml)
                if re.search(r"license|licensing|rights|copyright|trademark|open_source|pref_key", name, re.I):
                    continue
                assert not re.search(r"\bIceraven(?: Fenix)?\b|\bFloop\b|\bFloorp\b", value), str(xml) + ":" + name
    checks.append(f"{count} resource XML files parsed; launcher names and localized branding checked")

    main = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    info = main.find("application").findall("activity")
    assert any(node.get(android + "name") == "org.mozilla.fenix.bharatx.BharatXInfoActivity" and node.get(android + "exported") == "false" for node in info)
    for manifest in (ROOT / "app/src").rglob("AndroidManifest.xml"):
        root = ET.parse(manifest).getroot()
        assert android + "sharedUserId" not in root.attrib
    checks.append("Internal policy activity registered; no inherited upstream shared UID")

    for name in ("about", "privacy", "disclaimer"):
        text = (ROOT / "app/src/main/assets/bharatx" / (name + ".html")).read_text()
        parser = Page(); parser.feed(text)
        assert not parser.scripts
        assert "nitai.grp00@gmail.com" in text and "Made in India" in text
        assert set(parser.links) <= {"mailto:nitai.grp00@gmail.com", "https://www.trishabonlineservice.in/?m=1", "https://appnext.com/privacy-policy"}
    checks.append("Offline About/Privacy/Disclaimer contain supplied contacts and the advertising provider privacy reference")

    support = (ROOT / "app/src/main/java/org/mozilla/fenix/settings/SupportUtils.kt").read_text()
    assert 'const val WHATS_NEW_URL = BharatXBrand.WEBSITE' in support
    assert 'BharatXPages.intentFor(context, url)' in support
    assert '"https://github.com' not in support
    assert "BharatXPages.openIfLocal" in (ROOT / "app/src/main/java/org/mozilla/fenix/settings/about/AboutFragment.kt").read_text()
    checks.append("About/onboarding legal links route to native offline pages; old release navigation removed")

    logo = (ROOT / "branding/BharatXBrowser.png").read_bytes()
    assert logo == (ROOT / "app/src/main/res/drawable-nodpi/bharatx_logo.png").read_bytes()
    for path in (ROOT / "app/src").glob("*/res/mipmap*/*.png"):
        if path.name.startswith("ic_launcher"):
            assert path.read_bytes() == logo, str(path)
    checks.append("Launcher, private launcher and product logo resources use the supplied logo")

    assert hashlib.sha256((ROOT / "LICENSE").read_bytes()).hexdigest() == (ROOT / "branding/source-license.sha256").read_text().strip()
    assert "Mozilla Public License Version 2.0" in (ROOT / "LICENSE").read_text()
    assert "Iceraven" in (ROOT / "THIRD_PARTY_NOTICES.md").read_text()
    workflows = sorted(path.name for path in (ROOT / ".github/workflows").glob("*.yml"))
    assert workflows == ["build-bharatx-android.yml"]
    assert "forkRelease" in (ROOT / ".github/workflows/build-bharatx-android.yml").read_text()
    checks.append("MPL and original attribution retained; only the BharatX Android workflow is active")
    from vpn_audit import audit_vpn
    checks.extend(audit_vpn())
    monetization = json.loads((ROOT / "branding/monetization-lock.json").read_text())
    billing = (ROOT / "app/src/main/java/org/mozilla/fenix/bharatx/monetization/BharatXBilling.kt").read_text()
    ads = (ROOT / "app/src/main/java/org/mozilla/fenix/bharatx/monetization/BharatXAds.kt").read_text()
    assert monetization["subscriptionProductId"] == "bharatx_remove_ads_monthly"
    assert monetization["subscriptionProductId"] in billing
    assert monetization["appnextPlacementId"] == "dd500dee-43a9-40c7-8b70-afd51662f044"
    assert monetization["appnextPlacementId"] in ads
    assert monetization["appnext"]["coordinate"] in gradle and monetization["billing"]["coordinate"] in gradle
    assert "formattedPrice" in billing and "setOfferToken" in billing
    assert "BharatXPremiumAccess.reserve" in (ROOT / "app/src/main/java/org/mozilla/fenix/bharatx/vpn/BharatXVpn.kt").read_text()
    checks.append("Appnext placement and Play monthly product/SDK pins match; Store pricing and VPN reward gate wired")
    return {"passed": True, "checks": checks, "applicationId": properties["applicationId"], "versionName": properties["versionName"], "apkBuilt": False}


if __name__ == "__main__":
    result = audit()
    print(json.dumps(result, indent=2))
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            stream.write("version=" + result["versionName"] + "\n")
