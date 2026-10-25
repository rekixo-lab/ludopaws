# अपने Android मोबाइल पर सुरक्षित QA जाँच

## रोजमर्रा की feature testing

Repo root से Node.js, Java/Android SDK और authenticated GitHub CLI (`gh auth login`) के साथ:

```bash
node scripts/android-local-testing.mjs configure
node scripts/android-local-testing.mjs dev
```

पहला command केवल public Actions variables पढ़कर `~/.config/ludopaws/android-testing.json`
में रखता है (या `XDG_CONFIG_HOME` के अंदर)। Repo में कोई config, secret या APK copy नहीं होता।
अगली बार सिर्फ `dev` command पर्याप्त है; configuration बदलने पर `configure` फिर चलाएँ।
`dev` अलग `com.rekixo.ludopaws.dev` / **LudoPaws Dev** app है; Play app का data अलग रहता है।
इसमें Home → **Dev tools** (या Settings → **Developer tools**) से live build flags, server availability, सभी character
previews, yard, road stacks, legal tap hints और चारों winner/door/rank animations देखें।
Board fixtures केवल memory में हैं; saved match, inventory, wallet या leaderboard नहीं बदलते।
Demo ads Google test units हैं; reward callback का मतलब वास्तविक gems मिलना नहीं है।
Dev में production Billing/Store Ads और Google/Play Games login बंद हैं।

**All content / lock reasons / dice previews** में हर item का actual gate दिखता है।
सभी आठ ready dice बिना gems खरीदे preview कर सकते हैं। **Try in Dev games** केवल `.dev`
app में visual dice selection बदलता है; server inventory, ownership या wallet नहीं बदलता।
**Reset Dev dice to normal selection** से override हटाएँ। अधूरे boards/Paws packs के लिए
Coming Soon लिखा रहेगा; उनका actual content implement किए बिना वे उपलब्ध नहीं हो सकते।
Developer panel का **Open full testing app (LudoPaws)** original-package app खोलता है।

वास्तविक Google linking और Play purchases के लिए:

```bash
node scripts/android-local-testing.mjs integration
```

यह original package `com.rekixo.ludopaws` का debug APK है। Billing build flag चालू होता है;
Store का server availability check और receipt verification लागू रहते हैं। फोन में Play
Console का **license tester** account रखें और purchase dialog में test payment/account
सत्यापित करें। सिर्फ internal-track membership पर्याप्त नहीं है। हर code change पर Play
upload जरूरी नहीं: https://developer.android.com/google/play/billing/test

Google Cloud में original Android package + इस computer के debug signing SHA-1 की OAuth
credential होनी चाहिए। SHA-1 देखने के लिए `bash android/gradlew -p android :app:signingReport`।
Play Games project ID न होने पर वह feature उपलब्ध नहीं होगा; Developer tools कारण दिखाता है।
सही ID उपलब्ध होने पर `configure` refresh करें। अलग debug signing key होने पर उस computer
का SHA-1 भी register करना होगा। Public values cache में edit या `ORG_GRADLE_PROJECT_<NAME>`
environment variables से override कर सकते हैं। Cache में release keystore/password कभी न रखें।

पहले से Play-signed app होने पर debug signature अलग होने से installation reject हो सकता है;
ऊपर का command uninstall/data-clear/downgrade नहीं करता। ऐसी स्थिति में अलग test device
इस्तेमाल करें; उसी installed package को update करने के लिए compatible signature जरूरी है।
कई devices connected हों तो command से पहले `export ANDROID_SERIAL=<device-serial>` करें।
केवल APK बनाना हो तो `dev --assemble` या `integration --assemble` इस्तेमाल करें।
Output standard ignored `android/app/build/outputs/apk/debug/app-debug.apk` में रहता है।

Developer activity केवल `src/debug` में है, exported नहीं है और release APK में शामिल नहीं होती।
Family restrictions/consent को unlock नहीं किया गया है। Store के live ad/reward verification
की अंतिम जाँच configured test device/internal release पर अलग करें।

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
