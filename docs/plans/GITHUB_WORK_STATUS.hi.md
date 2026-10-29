# एक branch में काम की स्थिति

29 अक्टूबर 2026। सभी requested changes की working branch: `codex/rekixo-launch-branding`। इसी branch पर आगे commits रखें; main merge, backend deployment या Play release इस काम से नहीं होते।

## Play Games की सही requirement

यूज़र का “guest login” से आशय **Google Play Games का automatic platform connection और Google का welcome banner** है। Backend guest/session creation इसे पूरा नहीं करता। पहले बनाए startup coordinator को Play Games connection का implementation या उसकी सफलता का प्रमाण नहीं मानना है।

वर्तमान code में Application family-mode पर PlayGamesSdk initialization रोकता है। Profile का optional Google flow भी अलग age/opt-in gate रखता है। अगला startup काम इन paths को review करके वास्तविक PGS automatic authentication जोड़ना है। Banner Google SDK द्वारा नियंत्रित होगा; game स्वयं उसका नकली banner या बिना authentication connected status नहीं दिखाएगा। Missing profile, cancellation, offline और provider restrictions में Home उपयोग योग्य रहना चाहिए। Mixed-age policy और पहली बार age selection के साथ initialization timing स्पष्ट करनी बाकी है।

आधिकारिक संदर्भ: https://developer.android.com/games/pgs/android/android-signin और https://developer.android.com/games/pgs/platform-authentication । PGS platform identity को game wallet/progress की primary identity न बनाएँ।

## इस consolidation में उपलब्ध काम

- Rekixo launch branding का पहले का commit c01688a1 इसी branch पर है।
- पहले के audit fixes, guest checkout draft, game preparation splash, tests और v11 planning अब इसी branch में समेटे जा रहे हैं। Payment draft की final acceptance और live issue की जाँच अंतिम payment phase में बाकी है।
- नया age/privacy popup, पुराना GOT IT हटाना और वास्तविक automatic PGS startup अभी पूर्ण नहीं हैं। इन्हें पूरे हुए चरण न बताएं।

## पहले चलाए गए checks

Startup source के अंतिम build में 513 JVM tests पास (17 startup cases), lint 0 errors / 469 warnings, Android instrumentation sources compile और APK assembly successful। 27 contract gates तथा security gate पास। Instrumentation device पर नहीं चला; उस समय adb devices खाली था। यह Play Games banner या payment का physical-device प्रमाण नहीं है। इस documentation/consolidation में runtime source नहीं बदला गया है।

Raw local audit evidence, screenshots, APKs और account/Console screenshots इस source consolidation में शामिल नहीं हैं। Local checkout code editing/build का माध्यम है; उपलब्ध remote काम का प्रमाण pushed branch commit होगा।
