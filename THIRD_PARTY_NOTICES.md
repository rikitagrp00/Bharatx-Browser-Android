# Third-party notices

BharatX Browser Android branding and customizations: Nitai Studio (Nitaistudio), 2026.

This project is derived from Iceraven and Mozilla Firefox for Android (Fenix),
with GeckoView and Android Components dependencies. Those projects and their
contributors retain their original copyrights. Their names in this notice,
source namespaces and license headers identify the original software; they do
not identify the publisher of BharatX Browser or imply endorsement.

The original Mozilla Public License 2.0 is retained in LICENSE. Keep original
notices and provide the corresponding modified MPL covered source when
redistributing the app. Dependency-specific license information remains in the
original source files and the app's open-source library information.

The app's developer website and support contact do not replace third-party
license terms. Extension, search and optional service providers retain their own
terms and privacy policies.

## RikiVpn SDK and native OpenVPN engine

The user supplied `app/libs/rikivpn-1.0.0.aar`. Its SHA-256 and packaged ABIs
are recorded in `branding/vpn-sdk-lock.json`. It contains SDK classes under
`com.nitaistudio.rikivpn` and `libopenvpn.so` / `libovpnexec.so` native files.
The supplied SDK documentation identifies its own source license as MIT and the
OpenVPN / ics-openvpn engine as GPLv2. That documentation also describes the
combined AAR as a combined work. The uploaded AAR contains no original MIT
license, NOTICE, complete engine build description or corresponding source.
Do not represent the root MPL license as covering those binary components.

Obtain and preserve the SDK author's exact MIT notice, all engine/dependency
notices and the complete corresponding source/build information for this exact
AAR before redistributing it in a public release. A generic upstream repository
link or this browser source archive alone does not supply those missing items.
The OpenVPN engine is not rebuilt by this browser workflow. This integration
does not certify license compliance or Store approval. See `VPN_INTEGRATION.md`.

The two VPN profiles were supplied by the user. They contain inline shared
certificates/keys and do not require a username/password. Their endpoint
operators, redistribution permission and logging policies have not been
independently verified. France is Free; Lithuania is in the app's rewarded Premium access tier. That label does not change the endpoint operator, authentication or privacy properties.

## Appnext and Google Play Billing

Version 1.38.0 declares Appnext SDK `com.appnext.sdk:appnext-sdk:3.0.0.17` and
Google Play Billing `com.android.billingclient:billing:9.1.0`. They are downloaded
from their vendors' repositories, not relicensed under this project's MPL.
Their vendor terms and notices remain applicable. Official artifact hashes used
for local API inspection are recorded in `branding/monetization-lock.json`.
The JSON library in that lock is a CI test dependency only, not an app dependency.
Appnext's data practices are linked from the bundled Privacy Policy and disclosed
before the app initializes that advertising SDK. No browsing data is explicitly
provided by this integration. Subscription checks use Google Play's client SDK;
this source does not include a server-side receipt or reward verifier.
