# अपने Android मोबाइल पर सुरक्षित QA जाँच

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
