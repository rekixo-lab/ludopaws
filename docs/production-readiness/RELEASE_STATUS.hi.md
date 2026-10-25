# Family V2 — production readiness status

25 अक्टूबर 2026। यह रिपोर्ट 20 अक्टूबर की candidate स्थिति को बदलती है। पुराने `BASELINE_AUDIT.hi.md` और `VALIDATION.family-v1.json` ऐतिहासिक evidence हैं। अभी unconditional public-release GO नहीं है: code checks पास हैं, लेकिन नीचे दिए provider और physical Play acceptance बाकी हैं।

## लागू सुधार

- Account refresh अब persisted current session से मिलता है। Account switch/logout के बाद पुराना session वापस save नहीं होना चाहिए; rotated token वाला cached request current credentials लेता है। Account mutations serialized हैं। दोनों स्थितियों के physical regression checks पास हुए।
- Shop initialization और लंबे समय खुले checkout confirmation पर session refresh UI thread पर नहीं चलता। Refresh के बाद active account फिर जाँचा जाता है; missing billing attribution पर payment sheet नहीं खुलती।
- Profile का explicit Google Save/Restore अब `GetSignInWithGoogleOption` इस्तेमाल करता है। Missing credential और cancellation अलग सँभलते हैं; logs में token/provider payload नहीं जाता।
- Test fixtures को वास्तविक async checkpoint/cache व्यवहार और warm dice-face contrast के अनुसार ठीक किया गया। Realtime rate-limit test में minute-boundary timing स्थिर की गई; production rate limits नहीं बदले।

## सत्यापित जाँच

- 853 tracked files की inventory; 365 Android source files, 50 server source files और 11 main manifest Activities की scope inventory। 127 JS/MJS syntax checks तथा tracked JSON/XML parsing में कोई error नहीं। Inventory coverage हर line की स्वतंत्र certification नहीं है।
- Android: 494 JVM tests पास; final source Test APK assembly पास; release lint में 0 errors, 470 warnings, 1 hint। Warnings अभी समाप्त नहीं हैं।
- Samsung SM-A715F Android 13 पर अलग QA package में 37/37 instrumentation tests पास: session safety, encrypted storage, recreation और पूरे 2/4-player offline games शामिल। यह run final explicit Google picker/Store initialization edits से पहले था; उन edits के बाद JVM, lint और compilation पास हैं। Google provider flow का physical success इससे सिद्ध नहीं होता।
- Deployment-compatible Node 22 पर backend 283/283 और runtime/preflight 20/20 tests पास। 30 source gates और diff whitespace जाँच पास।
- `/health`, `/ready`, `/api/capabilities`, `/api/store/status` में backend healthy और configured Billing/ads/Google capabilities enabled मिलीं। Voided-purchase reconciliation में reported failure नहीं मिला। `productionClaim:false` स्वतंत्र certification claim का संकेत है, disabled purchase flag नहीं।

## Google और AdMob की वास्तविक स्थिति

User की स्पष्ट अनुमति से original package `com.rekixo.ludopaws` के debug certificate का Android OAuth client बनाया और Play Games में जोड़ा गया। Production new-install credential नहीं बदला। OAuth audience **In production** है; Play Games project **314526321614** publish हुआ और console में कोई unpublished change नहीं बचा। वास्तविक phone sign-in/restore तथा Play signing certificate की अंतिम matching acceptance बाकी है।

सही AdMob publisher में rewarded unit `9430436252` और interstitial `5052207546` मिले। Reward पहले से **5 gems** था। Rewarded SSV callback खाली था: `https://api.ludopaws.rekixo.com/api/admob/ssv` को Google ने सफल verify किया, save किया गया और दोबारा खोलकर पुष्टि हुई। Synthetic verification callback wallet grant नहीं करता।

AdMob app approval अभी **Requires review** है। Store details खाली हैं; exact package search में listing नहीं मिली। Store listing उपलब्ध होने पर linking/review पूरा करना है। Callback verification वास्तविक ad fill या wallet credit की पूरी end-to-end स्वीकृति नहीं है।

## Release और शेष acceptance

Backend main deployment run **40174040405**, attempt 2 सफल हुआ था; इस audit batch में production server source नहीं बदला। Signed artifact **1.0.0-rc8 / versionCode 8**, run **40174474992** उपलब्ध था, लेकिन Play में इस audit के दौरान upload नहीं किया गया। वर्तमान audit fixes उस artifact में नहीं हैं; नया signed candidate चाहिए।

Play publishing में पुराना **rc6 Production candidate** review में दिखा और managed publishing **OFF** था। Approval होने पर वह publish हो सकता है। इस audit ने पुराना submission cancel/replace नहीं किया। Internal release production review स्वतः replace नहीं करता।

अंतिम आवश्यक जाँचें:

1. नए audited signed candidate को Internal Play track पर install करके Google save/restore, account switch और Play Games जाँचें।
2. License testing में purchase → server grant → duplicate/restart recovery → refund/restore जाँचें। वास्तविक paid purchase नहीं किया गया है।
3. AdMob store linking/review और वास्तविक rewarded completion → 5 Gems तथा cosmetic progress जाँचें। Demo ads production Gems/unlock नहीं देते। Lumi Goat के 10 और Donkey के 15 verified rewarded ads हैं।
4. दो-device Online/Friends/Team Up/reconnect/rematch और लंबी performance sessions जाँचें। Low-end/scale/soak certification अभी नहीं है।

Family V2 में fixed quick chat और configured integrations enabled हैं। Free-text/voice बंद, Classic board unlocked और अतिरिक्त dice Coming Soon रहना user-approved व्यवहार है। Child-directed/NPA ad treatment और optional Google identity की neutral age-group gating बनी रहती है। इन्हें सिर्फ features enabled दिखाने के लिए हटाना नहीं है।

Temporary audit logs/screenshots repository के बाहर रखे गए हैं। कोई emulator, real-account deletion या real-money transaction नहीं किया गया। Public privacy page को actual Family V2 व्यवहार से मिलाया गया; Google approval का दावा नहीं किया गया है।

## Physical acceptance follow-up

नवीन Test APK original package पर data सुरक्षित रखते हुए install हुआ। Shop में Google Play की INR prices और TEST AD control दिखाई दिए। Google picker खुला, लेकिन चुने account का token backend पर reject हुआ। Verifier में Android authorized presenter (`azp`) को Web audience (`aud`) के बराबर मानने की गलत शर्त मिली। अब अलग explicit Android client allowlist है; केवल अपने verified Play-signing/debug OAuth clients configured हैं। Signature, Web audience, nonce और expiry validation कायम है; unknown presenter reject होता है। इस change के बाद Node 22 backend **284/284** तथा runtime/preflight **21/21** tests पास हैं। Production deployment और successful phone retry का परिणाम अभी दर्ज होना बाकी है।

## Deployed fix और phone retry का परिणाम

Main **aab6ef1d** का backend deploy **40197165242** सफल हुआ; health/readiness/store/live-game verification पास हैं। उसके बाद user ने Google picker में अपना account चुना और फोन पर **Account saved** मिला: मौजूदा guest identity रखते हुए Google linking सफल हुई। Profile में Play Games **CONNECTED** भी दिखा।

Google sample rewarded ad पूरा होकर dismiss हुआ। App ने साफ बताया कि test ad ने real Gems/unlock नहीं बदले; Home wallet 0 रहा। वास्तविक production ad/SSV credit अभी acceptance में बाकी है।

Shop prices Google Play से लोड हुईं, पर native checkout ने **The item that you were attempting to purchase could not be found** दिया। कोई payment नहीं किया गया। Console में सातों products और Starter का backwards-compatible purchase option Active है। License testing में केवल दो configured billing testers हैं; फोन का वास्तविक Play billing account उनसे मिलाना बाकी है। इस error को production purchase success नहीं माना गया है।

इसी main source का signed **rc9 / versionCode 9** build run **40197368881** शुरू है; Play upload और पुराने rc6 production review में कोई बदलाव नहीं किया गया।

## Owner-account verification और rc9 artifact

`finworkar@gmail.com` owner session से Users and permissions पढ़े गए। Invited `ai@rekixo.com` में app Admin/all permissions तथा backend billing service account में View financial data और Manage orders/subscriptions पहले से enabled हैं। कोई permission change नहीं किया गया। User ने फोन Play Store में ai account selected होने की पुष्टि की; यह license tester है और web internal opt-in भी active मिला। Fresh app/Billing restart के बाद भी native checkout में item-not-found आया। इसलिए केवल Console permission की कमी को कारण नहीं माना जा सकता। Play-distributed candidate पर checkout acceptance अगला कदम है।

Signed **rc9 / versionCode 9** run **40197368881** सफल है। Main source `aab6ef1d` के APK/AAB signatures CI में verified हैं; downloaded bytes/hash release evidence से मिले। AAB SHA-256: `fd9eea38afadf3161a53631f28417f83b821a8d4ca5d7ebfcb4c28f2e5631837`। APK/AAB और evidence `/tmp` में हैं, repository में नहीं। Internal upload/release की specific अनुमति माँगी गई है; अभी upload या production-review replacement नहीं किया गया है।
