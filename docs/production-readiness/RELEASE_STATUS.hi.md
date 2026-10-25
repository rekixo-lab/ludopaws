# Family V2 — release and acceptance status

25 अक्टूबर 2026। Functional source **aab6ef1d**, signed **1.0.0-rc9 / versionCode 9**। यह रिपोर्ट वर्तमान स्थिति है; `BASELINE_AUDIT.hi.md` और `VALIDATION.family-v1.json` पुराने evidence हैं। कोई unconditional public-readiness certification नहीं: नीचे verified और शेष acceptance अलग हैं।

## Release की वर्तमान स्थिति

- Backend deployment **40197165242** सफल: health, readiness, Store flags तथा live game flow verification पास।
- Signed Android workflow **40197368881** सफल; unit tests, release lint, upload signatures और APK/AAB evidence checks पास। Downloaded artifacts के hashes भी evidence से मिले। AAB SHA-256: `fd9eea38afadf3161a53631f28417f83b821a8d4ca5d7ebfcb4c28f2e5631837`। Artifacts/logs/screenshots repository के बाहर हैं।
- User की specific अनुमति से owner `finworkar@gmail.com` से rc9 Internal release publish हुआ: track `4701696451906351095`, release `4`, **Available to internal testers**, 25 अक्टूबर **19:53 IST**। Google ने एक download-size warning दी; blocking error नहीं। New-install estimate 24.8 MB, update 13.3 MB।
- बाद में user ने स्पष्ट रूप से पुराने rc6 production review में नया AAB भेजने को कहा। इसी verified rc9 को Production track `4697316581238009464`, release `2` में promote किया; release name **1.0.0-rc9 - Production candidate**। **Submit change → Restart review** पूरा हुआ। Publishing overview में अब rc9 **Changes in review** में है; quick checks चल रहे हैं और सफल होने पर review आगे बढ़ेगी। पुराने rc6 review को restart किया गया है। Managed publishing **OFF** बना है; approval पर rollout आगे बढ़ सकता है। इसे अभी public-live न मानें।

## Physical phone पर सत्यापित

Samsung SM-A715F, Android 13 पर:

1. नवीन original-package Test APK install हुआ। पहले Google picker खुला लेकिन backend ने token reject किया। Android `azp` को Web `aud` के बराबर मानने की गलत शर्त मिली और सुधारी गई। Exact verified Android presenter allowlist है; signature, Web audience, nonce और expiry checks कायम हैं। Production deploy के बाद Google linking **Account saved** से सफल हुई; Play Games **CONNECTED** भी दिखा।
2. Google sample rewarded ad पूरा/dismiss हुआ। App ने test completion बताया और real wallet 0 रहा; test ads से production Gems/unlock नहीं दिए गए।
3. Debug side-load में prices लोड होती थीं पर native checkout item-not-found देता था। Owner Console में invited ai account के Admin/all permissions तथा billing service account के financial/orders permissions पहले से सही मिले। ai account license tester और internal opt-in active है। Permissions नहीं बदली गईं।
4. User ने local XP/statistics/badges loss समझकर Test app uninstall/Play replacement की स्पष्ट अनुमति दी। Official Play Store ने **rc9 / code 9**, installer **com.android.vending**, install किया। User ने अपना age group स्वयं चुना। पुराने Google-saved account का **Account restored** मिला। Local statistics reset अपेक्षित था; उसकी cloud recovery का दावा नहीं है।
5. Play-installed rc9 पर native checkout में **Test card, always approves / you will not be charged** दिखा। User ने स्वयं test purchase/payment-verification choices पूरी कीं। App ने **Purchase verified** बताया; server wallet **0 → 300 Gems** हुआ। Restart के बाद 300 रहे; duplicate credit नहीं हुआ। Console में **Test: 300 Gems** Processed था। वास्तविक पैसा नहीं लिया गया।
6. User ने इसी test order को स्वयं refund किया; Console में Refunded हुआ। Revoke selection पुष्ट नहीं हुई। Refund के बाद scheduled reconciliation बिना failure पूरा हुआ, लेकिन refreshed wallet अभी 300 था। **Refund/revocation deduction acceptance पास नहीं है।** Google के अनुसार developer refund बिना revoke के Voided Purchases API में नहीं आता। Wallet manually नहीं बदला गया।

## Code/config में पूरे किए सुधार

- Current persisted session से refresh मिलता है; account switch/logout के बाद cached पुराना session वापस नहीं रखा जाता। Rotated token वाला request current credentials लेता है। Account mutations serialized हैं और stale-session regressions पास हुए।
- Shop initialization और लंबे समय खुले checkout confirmation पर network/session refresh UI thread पर नहीं होता। Payment sheet से पहले active account तथा billing attribution फिर जाँचे जाते हैं।
- Explicit Google Save/Restore में `GetSignInWithGoogleOption`; missing credential/cancellation अलग सँभलते हैं। Tokens/provider payload logs में नहीं जाते।
- Async checkpoint/cache वाले instrumentation fixtures, warm dice-face contrast check और minute-boundary rate-limit fixture सही किए गए; production rate limit या gameplay सरल करके test पास नहीं किया।
- User-approved debug Android OAuth client बना और Play Games से linked है; production new-install default credential नहीं बदला। OAuth audience **In production**, Play Games project **314526321614** published है।

## Ads की वास्तविक स्थिति

सही publisher `pub-9987799703505828`: rewarded unit `9430436252`, interstitial `5052207546`, reward **5 gems**। खाली rewarded SSV callback `https://api.ludopaws.rekixo.com/api/admob/ssv` को user की अनुमति से Google verification के बाद save किया और reopen से पुष्टि की। Verification fixture wallet grant नहीं करता।

AdMob app status अभी **Requires review** है; Store details linked नहीं मिले और exact package search में listing नहीं मिली। Production listing उपलब्ध होने पर linking/review पूरा करना है। Signed rc9 में actual production IDs/flags enabled हैं; demo mode release नहीं हुआ। इससे Google approval या ad fill की guarantee नहीं निकलती। वास्तविक rewarded SSV → 5 Gems/cosmetic progress अभी पूरी तरह सत्यापित नहीं है। Lumi Goat 10 और Donkey 15 verified rewarded ads के बाद unlock होते हैं।

## Automated evidence और सीमाएँ

- 853 tracked-file inventory, 365 Android source files, 50 server source files, 11 main manifest Activities; 127 JS/MJS syntax checks और tracked JSON/XML parsing में 0 errors। Inventory हर line की स्वतंत्र certification नहीं है।
- Android 494 JVM tests; final source Test APK assembly और signed release CI पास। Release lint: 0 errors, 470 warnings, 1 hint; warnings समाप्त होने का दावा नहीं।
- अलग QA package पर physical instrumentation **37/37** पास, जिसमें session safety, encrypted storage, recreation और पूरे 2/4-player offline matches हैं। यह suite final explicit Google picker/Store initialization edits से पहले थी; बाद की exact-source CI और वास्तविक Play flows ऊपर अलग दर्ज हैं।
- Node 22 backend **284/284**, runtime/preflight **21/21**, 30 source gates और diff whitespace checks पास।
- Live Billing/ads/Google capability flags ON; readiness healthy। `productionClaim:false` स्वतंत्र certification claim का संकेत है, integration OFF होने का नहीं।

अभी शेष: Play quick checks/production approval; AdMob linking/review/real reward acceptance; confirmed revoke/refund deduction; दो-device Online/Friends/Team Up/reconnect/rematch और लंबे low-end/scale/performance sessions। कोई emulator नहीं चलाया/डाउनलोड किया गया। कोई real account deletion नहीं हुई। Refund/payment/security/age choices user ने स्वयं कीं।

Family V2 में fixed quick chat enabled है; free-text/voice बंद हैं। Classic board unlocked और अतिरिक्त dice Coming Soon रहना user-approved व्यवहार है। Child-directed/NPA ad treatment और optional Google identity की age-group gating सुरक्षित रहती है। Public privacy page को इस व्यवहार से मिलाया गया; Google approval का दावा नहीं।

Google refund semantics: https://developers.google.com/android-publisher/voided-purchases
Internal tester link: https://play.google.com/apps/internaltest/4701696451906351095
