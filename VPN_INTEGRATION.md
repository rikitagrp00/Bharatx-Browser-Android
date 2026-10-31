# BharatX VPN — 1.38.0

Package: `com.nitaistudio.bharatxbrowser` (debug: `.debug`). Open the browser's
three-dot menu, then **BharatX VPN**. The supplied `app/libs/rikivpn-1.0.0.aar`
drives its real OpenVPN engine.

France is Free. Lithuania is Premium access in this app: complete one Appnext
rewarded video or hold an active monthly ad-removal subscription. The uploaded
profile bytes and hashes are unchanged; Lithuania moved to `vpn/premium/`.
Neither supplied profile needs provider credentials. Premium does not promise
a dedicated, private, paid-provider-operated or faster endpoint. Availability
and endpoint operators' privacy practices have not been independently tested.

One completed video gives one credit for the selected server. Starting a
connection reserves it; the first SDK CONNECTED consumes it. Terminal failure
before that first connection restores it. After a successful session ends,
the next connection needs a new video or an active subscription. Auto-reconnect
belongs to the current session. Credits survive activity recreation in memory
but are lost if the app process ends. They are an app gate, not a server entitlement.

Both menu implementations open the same screen. Connection state and traffic
come from the SDK. Separate VPN disclosure, Android VPN consent and Android 13+
notification permission are included. Closing the screen keeps the foreground
session running. Disconnect here, in the notification or Android VPN settings.
Only the running browser package is included in allowedApps. IPv6 blocking is
requested; reconnect is limited to five attempts. A drop can allow ordinary
network browsing; no kill switch is promised. Always-on/cold-start restoration
is disabled. SDK diagnostic logging is off. Credentials are not put in browser
preferences or saved screen state; the SDK manages its own private session files.

## Add assets

Edit `app/src/main/assets/vpn/catalog.json`. Each entry needs id, name, tier,
access, asset, requiresCredentials and the file's SHA-256. Free entries use
`tier: free`, `access: free`, `vpn/free/`. Premium entries use `tier: premium`,
`access: rewarded`, `vpn/premium/`. Example (documentation placeholder only):

```json
{"id":"japan","name":"Japan","tier":"premium","access":"rewarded","asset":"vpn/premium/japan.ovpn","requiresCredentials":true,"sha256":"REPLACE_WITH_FILE_SHA256"}
```

Set requiresCredentials to match a bare auth-user-pass directive. The app then
asks for provider login after the app access gate. A subscription does not bypass
provider authentication. Embed certificates/keys inline; require remote-cert-tls
server. Do not include credential files, scripts or plugins. Calculate the hash
with `sha256sum path/to/profile.ovpn` and run the source audit.

Bundled keys can be extracted. This reward gate cannot stop another VPN client
using an extracted profile. Provider restrictions must be enforced by its server.

## Packaging and notices

`branding/vpn-sdk-lock.json` records the supplied AAR hash. It contains libopenvpn.so
and the PIE launcher libovpnexec.so for four ABIs. The browser supports ARMv7,
ARM64 and x86_64; its GeckoView configuration excludes x86. The 64-bit engines
have 16 KiB LOAD alignment, which is not a substitute for a device test.

The executable must be extracted to applicationInfo.nativeLibraryDir. Keep
useLegacyPackaging and useLegacyPackagingFromBundle enabled for APKs and AABs.
The workflow verifies the actual package assets, identity and native engines.

The uploaded documentation identifies MIT SDK sources and GPLv2 OpenVPN/ics-openvpn
engine code. The AAR lacks the exact original SDK notice and complete corresponding
source/build information. Obtain them before public redistribution. This browser
source alone is not the engine source. See THIRD_PARTY_NOTICES.md.

## Validation

Run audit.py, verify_sdk_parser.py and verify_monetization.py under automation/bharatx
(after ./gradlew --version). No checker loads ads, buys anything or starts a VPN.
Local API compilation uses real SDK/AndroidX artifacts and Android API 36 with
resource-ID stand-ins. The full browser previously stopped before compilation
because this runtime Java installation lacks Gradle JAVA_COMPILER capability.
No APK/AAB, real ad fill, purchase or phone tunnel has been verified here.
See MONETIZATION.md for account setup and device checks.
