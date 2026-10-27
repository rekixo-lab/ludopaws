# अपने Android मोबाइल पर सुरक्षित QA जाँच

## Google Play वाले build पर वास्तविक integration acceptance

25 अक्टूबर 2026 को **rc9 / versionCode 9** Internal testing में release हुआ।
अपने अधिकृत tester account से यह link खोलें:
https://play.google.com/apps/internaltest/4701696451906351095

फोन में Play Store से installed rc9 (`installer=com.android.vending`) पर Google account
Restore तथा zero-charge **Test card, always approves** purchase सफल हुई। Backend ने
300 Gems दिए; restart के बाद 300 रहे। Test card केवल license tester को मिलता है।
Real card/UPI दिखे तो test के नाम पर वास्तविक भुगतान न करें।

इस फोन पर side-loaded debug build prices दिखाता था, लेकिन checkout में item-not-found था;
Play-distributed rc9 में यह error नहीं आया। इससे हर debug build/phone पर खरीदारी की
गारंटी नहीं मिलती। कई Google accounts वाले फोन में Play billing का account app download
history से भी तय हो सकता है; केवल foreground Play account या game sign-in समान होना पर्याप्त
प्रमाण नहीं है। Invited developer-account permissions और buyer license testing अलग हैं।

Debug/Test और Google Play signing keys अलग हैं। इनके बीच सीधे update अस्वीकार हो सकता है।
Uninstall local XP/statistics/badges मिटा सकता है; पहले Google account save करें और data-loss
समझकर ही बदलाव करें। इस audit में user की स्पष्ट अनुमति से replacement किया गया था।
Play builds में Developer tools और demo-ad labels नहीं होते; production units/flags लागू होते हैं।
AdMob approval/store linking और real SSV reward acceptance अलग हैं।

## रोजमर्रा की local feature testing

Repo root से Node.js, Java/Android SDK और authenticated GitHub CLI (`gh auth login`) के साथ:

```bash
node scripts/android-local-testing.mjs configure
node scripts/android-local-testing.mjs dev
```

`configure` केवल public GitHub Actions variables पढ़कर `~/.config/ludopaws/android-testing.json`
(या `XDG_CONFIG_HOME` के अंदर) में रखता है। Repo में config, keystore या APK copy नहीं होता।
अगली बार सिर्फ `dev` चलाएँ; public configuration बदलने पर `configure` refresh करें।

**एक ही runner:** VS Code में `Ctrl+Shift+B` भी यही `dev` command चलाता है। पुराना
`run-ludoproof.ps1` और उसका अलग Gradle downloader हटा दिया गया है। Windows, Linux और
macOS पर checked-in Gradle wrapper इस्तेमाल होता है। JDK और Android SDK पहले उपलब्ध हों।
SDK का रास्ता `ANDROID_HOME` से दे सकते हैं; `local.properties` की दूसरी settings सुरक्षित रहती हैं।
Device selection build से पहले होती है; install और launch दोनों उसी serial पर होते हैं।
Unit tests और debug lint भी चाहिए तो `dev --verify` चलाएँ। केवल build के लिए
`dev --assemble` है; यह फोन से संपर्क नहीं करता। पुराने `integration` alias की जगह `dev` इस्तेमाल करें।

`dev` **LudoPaws Test** बनाता है: original package
`com.rekixo.ludopaws`, Billing ON, Google linking/Play Games IDs configured और rewarded/
interstitial **Google TEST ADS ON**। Build सफल होने पर सही ऐप अपने-आप खुलता है।
Google IDs अधूरी हों तो helper disabled build लगाने के बजाय साफ error देता है।

Store में GEMS/payment section और TEST AD button मिलेगा। Test ads production SSV/account
जानकारी नहीं भेजते और असली wallet, gems या character unlock progress नहीं बढ़ाते।
Consent/family treatment लागू रहता है। Test-ad configuration release tasks में स्वीकार नहीं है।

Google purchases के लिए फोन का Google account Play Console में **license tester** होना चाहिए।
Purchase dialog में account/test payment जाँचें; internal-track membership अकेली पर्याप्त नहीं।
हर code change पर Play upload जरूरी नहीं: https://developer.android.com/google/play/billing/test
Google login के लिए original package + इस machine के debug signing SHA-1 की Android OAuth
credential सही होनी चाहिए। SHA-1 Home → **Dev tools** में या इस command से देखें:

```bash
bash android/gradlew -p android :app:signingReport
```

Age/privacy, Google account selection और account-linking की जरूरी choices स्वयं करें।
Real ad rewards/10-ad Lumi Goat और 15-ad Donkey unlock के लिए production ad units वाला
Ads-enabled build, नया PlayerAccount backend deploy और verified SSV callbacks जरूरी हैं।
Test ad देखकर genuine server entitlement नहीं दिया जाता।

अलग local visual previews के लिए ही `node scripts/android-local-testing.mjs preview` चलाएँ।
यह `com.rekixo.ludopaws.dev` / **LudoPaws Preview** है; इसमें Billing/Google/Store Ads OFF हैं।
इसे full testing app न समझें। Home → **Dev tools** (या Settings → **Developer tools**) में
board scenarios, character previews, server/build status और हर content item का lock कारण देखें।
केवल Classic dice उपलब्ध है; बाकी Coming Soon हैं। Classic board का Store preview gameplay
के साझा 3D renderer से आता है और Classic मुफ्त है। Preview fixture saved match/wallet नहीं बदलता।

पहले से Play-signed app होने पर debug signature अलग होने से installation reject हो सकती है।
Helper uninstall/data-clear/downgrade नहीं करता। ऐसे मामले में अलग test device या compatible
signature जरूरी है। कई phones जुड़े हों तो `export ANDROID_SERIAL=<serial>` करें।
केवल APK बनाना हो तो command में `--assemble` जोड़ें। Output standard ignored
`android/app/build/outputs/apk/debug/app-debug.apk` में रहता है। Developer activity केवल debug
source set में है; release में शामिल नहीं होती।

इस काम में डाउनलोड किया गया local emulator, API35 system image और `ludopaws_family_api35` AVD user के निर्देश पर हटा दिए गए हैं। इन्हें दोबारा डाउनलोड/चलाना नहीं है। Local emulator run अधूरा था; उसे device pass का प्रमाण नहीं माना गया है।

## Test app

- Debug-only Gradle property: `LUDOPAWS_QA_BUILD=true`।
- अलग application ID: `com.rekixo.ludopaws.qa`; app label: **Ludo Paws QA**। Production app का storage/account इससे अलग रहता है।
- API host: `https://127.0.0.1`। QA requests production accounts/matches को नहीं छूते। Backend integration tests अलग local disposable contexts में चलते हैं।
- QA mode में restore instrumentation Wi-Fi/mobile-data/airplane-mode commands नहीं चलाता। यह mode lifecycle restore की जाँच करता है; actual network-loss behavior के लिए dedicated approved test environment अलग चाहिए।
- QA flag के साथ release tasks अस्वीकार होते हैं। यह APK production/store upload के लिए नहीं है।

मोबाइल USB से कनेक्ट करें, Developer options में USB debugging चालू करें और फोन पर इसी कंप्यूटर की debugging अनुमति दें। `adb devices` में device authorized दिखने के बाद केवल उसी physical device का serial चुनकर APK/test APK install करें। मौजूद production app को uninstall/clear न करें।

## कम संसाधनों में build

```bash
ANDROID_HOME=/home/rekixo/Android/Sdk ./android/gradlew -p android \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
  -PLUDOPAWS_QA_BUILD=true --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8'
```

Install से पहले APK application ID और instrumentation target को `aapt dump badging` से सत्यापित करें। Target `com.rekixo.ludopaws.qa` ही होना चाहिए। Test runner package `com.rekixo.ludopaws.qa.test` है। पहले wallet/cache और saved-state regressions, फिर UI और offline full-match tests छोटे groups में चलाएँ। Raw results में failed/aborted tests को अलग रखें।

Release APK/AAB/lint जैसे भारी checks remote CI में emulator instrumentation बंद करके चलाए जा सकते हैं। किसी physical-device pass को API28–36 के हर फोन, सभी network conditions या live multiplayer deployment का प्रमाण न मानें।
