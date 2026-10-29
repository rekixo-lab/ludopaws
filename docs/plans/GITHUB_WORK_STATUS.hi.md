# एक branch में काम की स्थिति

29 अक्टूबर 2026। सभी requested changes की working branch: `codex/rekixo-launch-branding`। इसी branch पर आगे commits रखें; main merge, backend deployment या Play release इस काम से नहीं होते।

## Play Games की सही requirement

यूज़र का “guest login” से आशय **Google Play Games का automatic platform connection और Google का welcome banner** है। Backend guest/session creation इसे पूरा नहीं करता। पहले बनाए startup coordinator को Play Games connection का implementation या उसकी सफलता का प्रमाण नहीं मानना है।

चरण 4 में PGS initialization Application/splash से हटाकर Home/privacy flow में रखा गया है। Profile का status check पुराने manual opt-in flag पर निर्भर नहीं है। Banner Google SDK नियंत्रित करता है; game स्वयं उसका नकली banner या बिना authentication connected status नहीं दिखाता। Missing profile, offline और provider failure में Home उपयोग योग्य रहता है। Existing age boundary और नई installation में phase-4 popup की timing सहित पूरा विवरण: [चरण 3](PHASE3_PLAY_GAMES.hi.md)।

आधिकारिक संदर्भ: https://developer.android.com/games/pgs/android/android-signin और https://developer.android.com/games/pgs/platform-authentication । PGS platform identity को game wallet/progress की primary identity न बनाएँ।

## इस consolidation में उपलब्ध काम

- Rekixo launch branding का पहले का commit c01688a1 इसी branch पर है।
- पहले के audit fixes, guest checkout draft, game preparation splash, tests और v11 planning अब इसी branch में समेटे जा रहे हैं। Payment draft की final acceptance और live issue की जाँच अंतिम payment phase में बाकी है।
- वास्तविक automatic PGS startup का implementation चरण 3 में है; physical-device banner acceptance अभी अलग बाकी है। नया age/privacy popup और automatic GOT IT removal चरण 4 में लागू हैं; source-only recheck दर्ज है और नया build/device acceptance बाकी है।

## पहले चलाए गए checks

Startup source के अंतिम build में 513 JVM tests पास (17 startup cases), lint 0 errors / 469 warnings, Android instrumentation sources compile और APK assembly successful। 27 contract gates तथा security gate पास। Instrumentation device पर नहीं चला; उस समय adb devices खाली था। यह Play Games banner या payment का physical-device प्रमाण नहीं है। इस documentation/consolidation में runtime source नहीं बदला गया है।

Raw local audit evidence, screenshots, APKs और account/Console screenshots इस source consolidation में शामिल नहीं हैं। Local checkout code editing/build का माध्यम है; उपलब्ध remote काम का प्रमाण pushed branch commit होगा।

नवीन source-level recheck और चरण 4: [PHASE4_AGE_PRIVACY.hi.md](PHASE4_AGE_PRIVACY.hi.md)।
