# BharatX Browser for Android

Version **1.38.0** · Developed by **Nitaistudio** · **Made in India**

- Release application ID: `com.nitaistudio.bharatxbrowser`
- Debug application ID: `com.nitaistudio.bharatxbrowser.debug`
- Support: `nitai.grp00@gmail.com`
- Developer website: https://www.trishabonlineservice.in/?m=1
- Minimum Android version in the pinned source: Android 8.0 / API 26.

## কী পরিবর্তন হয়েছে

BharatX Browser নাম, supplied logo, launcher/private launcher icons, splash and
wordmark resources, localized display text, About developer information,
offline Privacy Policy ও Disclaimer, support ও developer website যোগ করা হয়েছে।
About-এর পুরোনো release navigation সরিয়ে developer website দেওয়া হয়েছে।
Privacy/Disclaimer onboarding এবং About থেকে native offline screen-এ খোলে।

মূল browser architecture, extension implementation, compatibility namespaces,
resource keys ও original copyright/license notices রাখা হয়েছে। Internal source
namespaces এবং dependency/service identifiers product display names নয়।
নতুন `LICENSE` ও `THIRD_PARTY_NOTICES.md` distribution-এর সঙ্গে রাখতে হবে।

## VPN ও বিজ্ঞাপন (version 1.38.0)

তিন-ডট menu → **BharatX VPN**। France Free; Lithuania video দেখে খোলা Premium
তালিকায়। একটি সম্পূর্ণ video একবার connection চালু করার credit দেয়। Connect
হওয়ার আগে ব্যর্থ হলে আবার video লাগে না; সফল session শেষ হলে পরের connection-এ
নতুন video বা active subscription লাগে। Premium label private/faster server-এর
দাবি নয়। Provider credentials প্রয়োজন হলে আলাদা prompt থাকবে।

Appnext placement `dd500dee-43a9-40c7-8b70-afd51662f044` দিয়ে VPN screen-এ banner,
user Disconnect-এর পরে বিরতি রেখে interstitial, ও opt-in rewarded video যুক্ত।
Ad choices মঞ্জুর করার আগে Appnext initialize বা ad load করা হয় না। Browser
webpage, history বা VPN packet ad SDK-তে পাঠানো হয় না।

Product ID: `bharatx_remove_ads_monthly`। Active subscription থাকলে সব ধরনের
ad বন্ধ ও video ছাড়া Premium connection। Store price, purchase, acknowledgement,
restore এবং manage subscription যুক্ত আছে। India-তে ₹15 রাখতে Play Console-এ
monthly auto-renew base plan-এর দাম ₹15.00 দিন; USD ও অন্য currency-র দামও Console
থেকে আসবে। কোডে fake USD conversion বা local paid-unlock flag নেই।

Account-side setup ও পরীক্ষা: [MONETIZATION.md](MONETIZATION.md)। Source/API checks
এবং reward/purchase policy tests হয়েছে; APK/AAB, real ad fill, actual Store purchase
ও phone VPN tunnel এখানে পরীক্ষা হয়নি।

## GitHub build

1. ZIP extract করে `BharatXBrowser-Android` folder-এর ভেতরের সব ফাইল repository
   root-এ upload করুন। `.github` folder-টিও থাকতে হবে।
2. `main` branch-এ push করলে build শুরু হবে। অথবা Actions →
   **Build BharatX Browser Android** → **Run workflow** চালান।
3. Workflow ZIP-এ অনুপস্থিত Android Components source-এর exact locked revision
   download করে fork patches apply করবে। JDK 17, source-এর Gradle wrapper ও SDK
   versions ব্যবহার করবে। প্রথম build-এর জন্য dependency downloads প্রয়োজন।
4. সফল run-এর `BharatXBrowser-Android-1.38.0-signed` অথবা `...-unsigned` artifact
   download করুন। Artifact-এ ARM64 debug APK, ARM64 release APK, release AAB,
   package verification report ও SHA256 checksums থাকবে। AAB-এ source-supported
   ARMv7, ARM64 এবং x86_64 architectures থাকবে। Matching covered-source artifact
   আলাদা দেওয়া হবে। Artifacts 7 দিন রাখা হয়; release distribution-এর জন্য নিজের
   স্থায়ী source ও package copies সংরক্ষণ করুন।

Debug APK signed এবং testing-এর জন্য install করা যায়। Release key ছাড়া release
APK/AAB unsigned থাকবে; সেগুলো সরাসরি install বা Play Console-এ submit করবেন না।
Debug key build runner-এ বদলাতে পারে; পুরোনো debug app-এর signature mismatch হলে
সেই debug app uninstall করে আবার install করুন। Release key পরিবর্তন করবেন না।

## Signed release APK / AAB

Repository secrets-এ নিজের existing upload key দিন:

- `BHARATX_KEYSTORE_BASE64`
- `BHARATX_KEYSTORE_PASSWORD`
- `BHARATX_KEY_ALIAS`
- `BHARATX_KEY_PASSWORD`

Workflow নতুন random release key বানায় না। Existing Play app update-এর জন্য
application ID ও signing/upload key মিলতে হবে, এবং version code পুরোনো release-এর
চেয়ে বড় হতে হবে। এই source-এর package name যদি আপনার app-এর থেকে আলাদা হয়,
`branding/bharatx.properties`-এ প্রয়োজনীয় identity দিন এবং checks মিলিয়ে নিন।
Play Store publication/approval এই workflow স্বয়ংক্রিয়ভাবে করে না। Bundled privacy
page নিজে থেকে public website-এ publish হয় না; Store listing-এর জন্য নিজের
website-এ policy-এর public copy রাখতে হবে।

## Version and local build

Product name/version/base code: `branding/bharatx.properties`। প্রতি release-এ base
`versionCode` বাড়ান। Final code = base × 10 + ABI offset; universal/AAB offset 9।
Engine/dependency version `version.txt`-এ আলাদা আছে; শুধু app branding বদলাতে এটি
বদলাবেন না। SDK/dependency pins আছে `branding/build-lock.json`-এ।

JDK 17, Python 3, Git এবং Android SDK command-line tools install করে:

```bash
python3 -m venv venv
venv/bin/python -m pip install 'glean_parser~=20.1'
venv/bin/python automation/bharatx/prepare_source.py
bash automation/bharatx/install_sdk.sh
bash gradlew :app:assembleForkDebug -PbharatxApkAbi=arm64-v8a
bash gradlew :app:assembleForkRelease -PbharatxApkAbi=arm64-v8a
bash gradlew :app:bundleForkRelease
```

Windows-এ SDK pieces install করে একই Gradle tasks `gradlew.bat` দিয়ে চালান। APK ABI
parameter বাদ দিলে source-supported সব APK architectures build হবে। APK ও AAB
আলাদা Gradle invocations-এ build করুন, কারণ bundle task ABI split configuration
বদলায়। Outputs পাওয়া যায় `gradle/build/app/outputs/`-এ।

## এই পরিবেশে যাচাই

Source identity, policy navigation, original license preservation, icon resources,
resource XML এবং workflow structure যাচাই হয়েছে। Locked component patches পরপর
দুইবার সফলভাবে apply/check হয়েছে। Gradle configuration চালানোর চেষ্টা হয়েছে,
কিন্তু `org.gradle.kotlin.kotlin-dsl:6.7.6` plugin resolve না হওয়ায় APK/AAB compile
ও device test শেষ হয়নি। এই ZIP source project; তৈরি APK বা AAB নয়।
