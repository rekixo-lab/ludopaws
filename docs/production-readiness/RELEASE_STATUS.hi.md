# Family V2 — internal testing release status

20 अक्टूबर 2026। Publisher ने worldwide mixed audience के लिए internal-test release में configured billing, ads और Google account integrations खोलने की अनुमति दी है। पिछली Family V1 lockdown रिपोर्ट ऐतिहासिक है; automated evidence `VALIDATION.json` में है। Public rollout से पहले physical/Play acceptance अभी भी जरूरी है।

## इस candidate का व्यवहार

- Online, Friends, Team Up, leaderboard, invites और fixed quick-chat enabled हैं। Free-text chat और voice हटे रहते हैं; microphone/WebRTC नहीं है। पुराने custom names भी generated public animal names में redact होते हैं।
- App startup, offline/online match और ads के लिए age entry नहीं है। हर ad request पर `AgeRestrictedTreatment.CHILD`, G content limit, non-personalized request और under-age UMP treatment है। AD_ID तथा AdServices permissions हटे हैं। Optional Google identity actions से पहले ही neutral local age-group screen आता है; केवल adult category Google SDK access खोलती है। यह age verification/parental consent नहीं है और gameplay इससे अलग है।
- `FAMILY_V2` में configured Play one-time purchases, cosmetic purchases, rewarded SSV और optional Google linking/restore खोल सकते हैं। `FAMILY_V1` और unknown/missing-mode lockdown अपनी पुरानी restrictions बनाए रखते हैं। कोई client adult header capability नहीं खोलता।
- Signed internal build GitHub variables से billing/rewarded/interstitial flags लेता है; matching backend `/api/store/status` और `/api/capabilities` जरूरी हैं। Keys server/Actions secrets में हैं। QA variant में paid integrations अभी भी निषिद्ध हैं।
- Google Play product prices provider से आते हैं। Account-bound verification के बाद ही wallet grant; pending purchases कोई grant नहीं करते। Duplicate callbacks idempotent हैं। हर पाँच मिनट voided Play purchases reconcile होते हैं; pagination का cursor durable है। Revoked Remove Ads हटता है यदि दूसरा valid entitlement न हो; refunded Gems शेष balance से घटते हैं, balance negative नहीं होता। पहले खर्च हुए cosmetics स्वतः नहीं हटते।
- Offline restore/settings, room revision reset, account-switch cache isolation, deleted-player recent-list tombstones, matchmaking cancel/reconnect और V5 rules के पिछले fixes शामिल हैं। Phone UI/network validation को unit-test pass नहीं माना गया है।

## Console में शेष वास्तविक acceptance

Publisher के बताए अनुसार policy declarations भरे जा चुके हैं। Code/disclosures उसी व्यवहार से align किए गए हैं; उन्हें Google approval का प्रमाण न मानें।

1. Internal Play track पर signed AAB upload करें; versionCode default 3 है, workflow में कोई अधिक unused code चुन सकते हैं। Play signing certificate SHA-1/SHA-256 सही Android OAuth client में दर्ज करें; internal/license testers को app और products का access दें।
2. `remove_ads`, `gems_300`, `gems_1500`, `gems_3500`, `gems_20000`, `gems_40000`, `gems_200000` active purchase options, regional pricing/availability और licenses से जाँचें। SKU suffix को Gem quantity न मानें; exact grants pricing policy में हैं। Test purchase/refund/restore अभी physical Play install पर करना है।
3. Billing service account को purchase management के साथ **View financial reports** access चाहिए, ताकि voided purchases API काम करे। Worker `/ready` में reconciliation status देखें; credential का मौजूद होना permissions का प्रमाण नहीं है।
4. AdMob rewarded SSV callback `https://api.ludopaws.rekixo.com/api/admob/ssv`, configured unit और `gems` reward मिलाएँ। Test device/AdMob test mode में ads देखें; real paid ads पर test clicks न करें। AdMob approval/fill और dismissibility का phone पर परीक्षण बाकी है।
5. **Play Games:** GitHub में `PLAY_GAMES_PROJECT_ID` अभी अनुपस्थित मिला। इसे अनुमान से नहीं भरा गया। Play Console में संबंधित Play Games project/Android credential/tester configuration पूरा करके वास्तविक numeric ID भरें और signed build फिर बनाएँ। Google account linking/recovery का Web OAuth ID अलग है और मौजूद है। Play Games sign-in game-account backup नहीं है।
6. Physical phone पर recreation/process death, Board/Dice equip, दो-device online games/rematch/Team Up/reconnect, Google save/restore/switch/deletion और Play purchase/refund/reinstall flows जाँचें। XP/statistics/badges device-local हैं; सभी guest data का reinstall recovery वादा नहीं किया गया है।

## Machine/deployment evidence

वर्तमान run के परिणाम यहाँ finalize होंगे। Emulator न चलाएँ या डाउनलोड करें। पिछला emulator/image/AVD हटाया जा चुका है; user phone testing बाद में करेगा। `BASELINE_AUDIT.hi.md` और पुराना V1 evidence ऐतिहासिक संदर्भ हैं।
