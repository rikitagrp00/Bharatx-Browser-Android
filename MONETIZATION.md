# BharatX Android monetization — 1.38.0

| Configuration | Value |
| --- | --- |
| Package | `com.nitaistudio.bharatxbrowser` |
| Appnext placement | `dd500dee-43a9-40c7-8b70-afd51662f044` |
| Appnext SDK | `com.appnext.sdk:appnext-sdk:3.0.0.17` |
| Play Billing SDK | `com.android.billingclient:billing:9.1.0` |
| Subscription | `bharatx_remove_ads_monthly` |
| Intended India price | ₹15.00/month, set in Play Console |
| Display price/currency | Actual Google Play `formattedPrice` |
| France | Free |
| Lithuania | Rewarded Premium app access |

The placement ID is used for all three ad formats. Enable them for the correct
app in Appnext. If the account needs separate IDs, provide the respective
placements and update `BharatXAds.kt`. Ownership, inventory and live fill have
not been tested here.

## Behavior

Ad choices precedes the first Appnext initialization. Decline keeps Free VPN
available. The dedicated banner slot is at the bottom of the native VPN screen,
outside its scrolling controls. Banners are suppressed while the app VPN is
active. No ads are injected into websites or drawn over browsing content.

An interstitial can show only after a user-requested disconnect finishes, with
the VPN screen resumed and the ad already loaded. Initial delay: 60 seconds;
subsequent impressions: at least 180 seconds apart. None is forced at launch,
on Back, during a connection or while browsing. Vendor-controlled background
tasks of an already-initialized SDK are not guaranteed to stop with ad display.

Premium Connect requests an optional rewarded video. Only `onVideoEnded` after
an actual `onAdShown` creates a credit. A click, early close, error, timeout or
duplicate event cannot award it. Connecting waits for ad close and screen resume.
One video unlocks one connection to that server; failure before the first SDK
CONNECTED retains the credit. Once a successful session ends, a new session
needs a new video or active subscription. Credit is memory-only across activity
recreation and is lost on process death. Premium is an app access tier, not a
promise of a private/faster endpoint. See `VPN_INTEGRATION.md`.

An active monthly subscription disables banner, interstitial and rewarded ads
and bypasses the Premium video gate. A profile's own provider authentication is
independent. No browsing URL/history, DNS requests or VPN packet data is passed
by this integration to the ad SDK. Appnext's own data practices are disclosed
before consent and linked in the bundled Privacy Policy.

## Activate ₹15/month and localized prices

Adding a product ID in source does not create a Play product or change its price.
No Play/Appnext dashboard setting or live purchase was changed by this work.

1. In Play Console for this package, create/confirm the **subscription**
   `bharatx_remove_ads_monthly` under Monetize with Play → Products → Subscriptions.
2. Add and activate an **auto-renewing monthly** base plan (`P1M`), for example
   ID `monthly`, with its selling countries. The app selects an eligible ordinary
   monthly base plan with one recurring phase; it does not select trial/intro offers.
3. Set India to **INR 15.00**. Configure USD/other country prices there, or use
   Play Console's currency conversion. The app shows the Store's formatted price
   and sends the selected offer token to checkout. It does not invent a dollar
   conversion or show ₹15 when the Store charges another amount.
4. Upload a signed AAB to an internal test track and install from Google Play
   with a licensed tester account. The `.debug` package is a different app and
   cannot buy the release app's product. Test before release.
5. **Restore purchases** rechecks the current account; **Manage subscription**
   opens Google Play's renewal/cancellation page.

## Purchase handling

Current subscriptions are queried on resume and active entitlement is rechecked
periodically. Eligibility requires the correct package/product, purchased state,
non-empty token and no suspension. New purchases must be acknowledged before
access is granted. Pending payments do not unlock it. Cancelled renewal keeps
access for the paid period while Play reports it as current; removal from the
current-purchases query ends the benefit.

There is no saved paid flag or fake 30-day timer. An in-memory entitlement must
be rechecked after 15 minutes. Unknown entitlement suppresses ordinary ad
requests during checking. A billing error cannot create a new paid entitlement.

This zero-backend version relies on the live Play Billing client. It has no
independent RSA receipt verification, backend, RTDN or server-side reward
verification. A modified client or extracted VPN profile cannot be secured by
this local gate; server-grade entitlement protection is not claimed.

## Validation

The existing Android workflow includes source audit, actual RikiVpn parser
checks and **30 monetization policy checks**, then full APK/AAB tasks. SDKs come
from their documented repositories; inspected hashes are recorded in
`branding/monetization-lock.json`.

```bash
./gradlew --version
python3 automation/bharatx/audit.py
python3 automation/bharatx/verify_sdk_parser.py
python3 automation/bharatx/verify_monetization.py
```

Policy tests use real Billing `Purchase` models and the app's reward/session
logic. They make no ad, billing or VPN requests. The JSON library is a JVM test
dependency only. Local API compilation uses real vendor/AndroidX artifacts and
Android API 36 with resource-ID stand-ins. This does not prove real inventory,
checkout or a tunnel. Full browser compilation previously stopped because this
runtime Java installation lacks Gradle's compiler capability. No APK/AAB, live
ad, payment or device connection has been verified. CI is not automatic Store
publication or approval.

Device tests: consent allow/decline/revoke; banner layout; interstitial timing;
rewarded early close/no-fill/completion; failed VPN retries; reconnect after a
successful session; pending/cancelled/successful purchase; restore; expiry; and
absence of all three ad formats for a subscriber.

API references:

- https://developers.appnext.com/docs/sdk-getting-started
- https://developers.appnext.com/docs/sdk-banner-integration-guide
- https://developers.appnext.com/docs/Interstitial-Integration-Guide
- https://developers.appnext.com/docs/Rewarded-Integration-Guide
- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/release-notes
