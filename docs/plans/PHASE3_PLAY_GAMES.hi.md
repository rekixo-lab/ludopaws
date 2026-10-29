# चरण 3 — Splash और Google Play Games

**Cleanup के बाद:** अब source में Dev tools, CharacterPreviewActivity, अलग developer/QA application IDs और demo-ad mode नहीं हैं। नीचे के APK hash/tests पहले के phase-3 build की historical evidence हैं; cleanup source का नया APK या test run नहीं हुआ है। वर्तमान install/process निर्देश: [main app device acceptance](../production-readiness/PHONE_TESTING.hi.md)।

**नवीन device feedback:** यूज़र ने Rekixo launch और game splash दोनों देखकर तेज़ Home transition स्वीकार किया है। अब Play Games को Home के age/privacy popup flow में शुरू करने की requirement है। यह Application/splash initialization timing हटाने वाला runtime बदलाव चरण 4 में popup के साथ होगा; नीचे चरण 3 के implemented behavior और उसके build की historical evidence है। आगे हर edit पर build/lint/tests नहीं; अंतिम संयुक्त verification सभी दस चरण पूरे होने पर होगा।

यूज़र के screenshots में ऊपर avatar, नाम और level के साथ दिखा “Hi there” Google Play Games का native welcome banner है। यह backend guest, Google-save और Play Billing से अलग है। इस implementation में PGS v2 का automatic platform authentication इस्तेमाल है; custom welcome banner, fake success या backend wallet identity change नहीं है।

## लागू व्यवहार

- Rekixo system launch के बाद बीच में game artwork और नीचे वास्तविक preparation indicator। Backend account और PGS की readiness अलग हैं। ऊपर PGS status है; backend guest को Play Games connected नहीं कहा जाता।
- Returning eligible player के लिए Application startup पर SDK initialization और Home/splash lifecycle से `isAuthenticated()` check। पुराने manual `play_games_opt_in` को startup prerequisite नहीं बनाया गया है। Profile का status check भी इस पुराने flag पर निर्भर नहीं है।
- Startup से `signIn()` या server auth code request नहीं। Existing Play Games profile का native welcome Google SDK दिखाता है। Missing profile पर automatic profile-creation popup suppressed है; Profile का explicit Play Games action उपलब्ध रहता है। Banner का समय/दिखना Google नियंत्रित करता है, game उसे guaranteed नहीं मानता।
- PGS check offline में भी SDK को दिया जाता है। Cached state Google के पास है; offline banner को fresh server authentication का प्रमाण नहीं कहा जाता। Failure, missing Play services और SDK exception पर Home usable है। Local artwork ready होने पर network/PGS का अधिकतम startup wait 3 seconds है।
- Process-owned state repeated lifecycle refresh को coalesce करती है; destroyed/stopped Home observer detach होता है। Timeout के बाद late result अगला attempt overwrite नहीं करता। Privacy choice बदलने पर पुराने result को reject किया जाता है। Foreground/reconnect पर status फिर check हो सकता है।
- Original package और positive Play Games project ID जरूरी हैं। `.dev` / `.qa` preview को production OAuth project में authenticate कराने का प्रयास नहीं होता। Release workflow का configured project ID जारी रहता है।

## Age/privacy और अगले चरण की सीमा

Mixed-age app में PGS optional है। Existing conservative privacy setting अभी केवल local “18 or older” choice पर optional Google SDK अनुमति देती है; यह हमारी implementation boundary है, Google की universal under-18 prohibition का दावा नहीं। Unknown, younger या “Prefer not to say” में SDK शुरू नहीं होता, gameplay/store को PGS sign-in से gate नहीं किया जाता। Corrupt preference भी permission नहीं बनती।

नई installation में age choice अभी unset रहती है। इसलिए **पहली installation के splash पर सबके लिए banner आने का दावा नहीं है**। Existing age selection के बाद PGS auto-refresh hook जोड़ा गया है। चरण 4 में नया numeric age/privacy popup बनेगा; उस choice के बाद यही hook connection शुरू कर सकेगा। Returning permitted users के अगले cold launch पर यह splash में चलेगा। Age पूछने से पहले सभी नए users पर SDK शुरू करके इस privacy boundary को bypass नहीं किया गया है। पुराना GOT IT और numeric age popup चरण 4 के काम हैं।

आधिकारिक संदर्भ:
- [PGS Android initialization और automatic authentication](https://developer.android.com/games/pgs/android/android-signin)
- [Platform identity और in-game identity](https://developer.android.com/games/pgs/platform-authentication)
- [Google API Services mixed-audience policy](https://developers.google.com/terms/api-services-user-data-policy#child-directed_apps)

## Local repository में लेना

अपने Ludopaws checkout में, अपनी uncommitted changes पहले commit/stash करके:

```bash
git fetch origin
git switch codex/rekixo-launch-branding
git pull --ff-only origin codex/rekixo-launch-branding
```

पहली बार branch न हो तो `git switch --track origin/codex/rekixo-launch-branding` करें। अपनी current changes हटाने के लिए reset/clean न चलाएँ।

## वास्तविक Play Games test APK

PGS test के लिए original package और उस build की signing SHA-1 से मेल खाता registered Android OAuth client चाहिए। Project 314526321614 source के existing release-status evidence में published है। Local machine की debug key अलग हो सकती है; केवल project ID डालने से हर machine की key authorize नहीं होती।

Repo के root में:

```bash
./android/gradlew -p android :app:assembleDebug \
  -PPLAY_GAMES_PROJECT_ID=314526321614 \
  -PLUDOPAWS_VERSION_CODE=10 \
  -PLUDOPAWS_VERSION_NAME=1.0.0-phase3-play-games \
  -PLUDOPROOF_BILLING_ENABLED=false \
  -PLUDOPROOF_ADS_ENABLED=false

adb devices
adb install -r -t android/app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.rekixo.ludopaws
adb shell am start -W -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  -n com.rekixo.ludopaws/com.rekixo.ludopaws.HomeActivity
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` का अर्थ installed Play app और debug APK signatures अलग हैं। Data बचाने के लिए app uninstall न करें। उसी signing key का approved build/internal Play update चाहिए। `.dev` preview अलग install हो सकती है, लेकिन उससे actual production PGS authentication validate नहीं होगी। यह चरण billing/ads test या v11 production release नहीं है।

इस पुराने APK का package original `com.rekixo.ludopaws` था; debug build में Dev tools दिखते थे। Cleanup के बाद वे source से हटे हैं; उस पुराने APK में बदलाव अपने-आप नहीं होगा। Debug signing के कारण Play-installed release से signature mismatch हो सकता है। इस पुराने build command में billing और ads बंद थे, इसलिए वह production purchase/ads acceptance build नहीं था।

Launch screens देखने के लिए पहले force-stop और फिर launcher intent जरूरी diagnostic path है; सिर्फ `am start -n` या पहले से खुले Home को resume करना बराबर जाँच नहीं है। विकल्प: force-stop के बाद फोन की Home screen से game icon दबाएँ। Fast preparation में custom splash बहुत कम समय रह सकती है; वह visibility समस्या अलग device observation है, केवल सही APK installed होने से उसे passed न मानें। पुराने GOT IT popup का दिखना चरण 4 से पहले अपेक्षित है।

## Device acceptance

Existing permitted age + existing Play Games profile पर cold launch में Google welcome/status देखें। Offline launch में Home खुले; banner आए या न आए, false success न बने। Unknown/younger privacy में SDK न चले। Existing Profile → Age and privacy की permitted choice के बाद automatic check हो; Google-save आवश्यक न हो। Reconnect/background/rotation में crash, duplicate manual prompts या permanently blocked splash न हो। Missing profile/Play services और unregistered signing key में Home काम करे।

अंतिम verification: **BUILD SUCCESSFUL in 5m 35s**। 523 JVM tests, 0 failures/errors/skips; इनमें 10 नए PGS state cases हैं। Lint: 0 errors/fatal, 469 warnings। Instrumentation sources compile और original-package APK assembly सफल। 27/27 project contract gates, security gate (448 files), merged manifest/project-ID checks और diff whitespace check पास। Runtime code बदलने के बाद यही अंतिम combined run था।

Physical device: `adb devices -l` खाली। इसलिए actual Google welcome banner, offline SDK cache और device startup appearance का acceptance **अभी नहीं चला**। APK SHA-256: `0a2fdd2aeff3dc39b5af4f72cabf6fcd23e1d5e55733a8156bf9177ed43214fb`। Machine-readable evidence: [validation](PHASE3_PLAY_GAMES_VALIDATION.json)। Payment/ads का transaction test या Play release नहीं किया गया।
