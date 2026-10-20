# Production readiness — सुधार और बाकी लॉन्च कार्य

तारीख: 20 अक्टूबर 2026। आधार: GitHub `main` commit `f7013741d9e88a7cf9476619c085375a0542580c`। सुधार शाखा: `codex/production-readiness`। लक्ष्य: बच्चों और बड़ों सहित worldwide launch।

**वर्तमान निर्णय: Family-mode release candidate तैयार किया जा रहा है; worldwide सार्वजनिक launch अभी NO-GO है जब तक नीचे दिए बाहरी release gates पूरे न हों।** User ने Family mode चुना है। इस शाखा में Android online navigation और Worker `FAMILY_V1` gameplay सक्षम हैं; live production अभी इस शाखा पर deploy नहीं हुआ है। पुराने audit में पास हुए CI को इस शाखा की जाँच का प्रमाण न मानें।

## Family edition का वास्तविक व्यवहार

- Multiplayer, Friends invitations, leaderboard, Team Up और fixed emoji quick-chat उपलब्ध हैं। Age/DOB या “adult” client assertion से कोई अतिरिक्त सुविधा नहीं मिलती। Missing/unknown Worker mode में lockdown लागू रहता है।
- Free-text chat के Android screen/menu और Android WebRTC implementation/dependency हटाए गए हैं। पुराने APK के message requests और voice signals भी server पर रोके जाते हैं। Legacy names HTTP, command replies और timeout/forfeit push updates में generated animal names से बदलते हैं।
- Android remote payloads device nickname नहीं भेजते। Offline play पर account bootstrap, Google SDK auto-init या age-sharing prompt नहीं चलता। Online प्रवेश पर safety notice आता है।
- Ads, purchases और new Google linking बंद हैं। Stale operator flags भी family-mode paid endpoints नहीं खोलते। Gem purchase tab/Remove Ads offer छिपाए गए हैं। Existing entitlements और account deletion/recovery APIs रखे गए हैं; नया Google linking/Play Games UI उपलब्ध नहीं है।
- `/api/capabilities` वास्तविक enabled/disabled capabilities बताता है। Family deploy smoke denial checks के बाद वास्तविक roll/move/Friends flows भी चलाता है। CI instrumentation का API host loopback है, production नहीं।

## किए गए बदलाव

| Audit finding | सुधार | पुष्टि की सीमा |
|---|---|---|
| OFF-01 / REM-03 | सामान्य navigation से शुरू हुए offline/online match को Activity recreation पर restore करें | नए instrumentation regression tests; device execution नीचे दर्ज करें |
| SET-01 | Connected selection को authenticated server पर भेजें; offline में केवल मुफ्त, उपलब्ध, level-zero cosmetics चुनें | compilation और मौजूदा catalog/authority checks; paid live equip बाकी |
| ACC-01 | Google restore को खोए हुए guest session की पूर्वशर्त से मुक्त करें | source/compilation; वास्तविक Google recovery बाकी |
| ACC-02 | Profile destroy पर credential picker cancel, executor shutdown और background account work पूरा होने पर presence cleanup | source/compilation; account-switch timing device test बाकी |
| ACC-03 | Google restore के वर्णन में स्पष्ट करें कि XP, statistics और badges device पर रहते हैं | text review |
| ACC-04 | Leaderboard cache को viewer account से बाँधें, पुराने request का account capture करें, deletion/restore पर cache हटाएँ | नया instrumentation regression |
| ACC-05 | Wallet mutation revisions SQLite triggers से बनाएँ; shared client lock से पुराने snapshots अस्वीकार करें | वास्तविक SQLite handler test + नया Android regression |
| REM-01 | नए कमरे/exit/invalid session पर दोनों revision trackers reset करें | source/compilation; multi-room device flow बाकी |
| REM-02 | Family edition से WebRTC implementation और dependency हटाएँ; server voice signaling reject करे | voice rejection/quick-chat integration regression; voice इस release का feature नहीं |
| REM-04 | Friends search में header/editor को attached रखें, केवल rows बदलें | source/compilation; keyboard/device UX बाकी |
| REM-05 | Finished public match से अगली matchmaking player-count selection खोलें | source/compilation; enabled staging flow बाकी |
| REM-06 | Public और Team Up back navigation पर server ticket cancel करें; assignment race होने पर मिले match से reconnect करें | source/compilation; poor-network staging flow बाकी |
| REM-07 | Heartbeat auth failure पर केवल उसी पुराने credential को compare-and-clear करें | shared identity lock; restored credential को stale reply से बचाता है |
| BE-01 | Friend directory requests serialize करें; recent index पहले register करें; deleted owner/opponent के durable tombstones रखें | वास्तविक SQLite deletion/delayed-result regressions |
| BE-02 | Answered request replay पर committed answer लौटाएँ | वास्तविक SQLite handler regression |
| BE-03 | Deleted ranked participant छोड़कर survivors का result रखें; unknown participant अभी भी reject करें | वास्तविक SQLite handler regression |
| BE-04 | Absent leaderboard limit का default 25 रखें | वास्तविक SQLite multi-entry regression |
| GAME-01 | नई Classic/Team V5 contract में protected stacks घोषित करें; पुराने hashes न बदलें; unversioned offline saves V3 रखें | server protected-stack regression; offline migration instrumentation |
| SUP-01 | Release runtime के resolved artifacts, SHA-256 और dependency graph से SBOM बनाएँ | 95 components; embedded native internals और license review शामिल नहीं |
| SUP-02 | APK/AAB की upload signature जाँच के आधार पर evidence लिखें; Google Play distribution signing का दावा न करें | unsigned local candidate पर negative verification; signed CI बाकी |
| SDK-01 | Family package से MobileAds और PlayGames auto-init providers हटाएँ | merged/release package verification |

Guava runtime constraint `33.4.8-android` किया गया है। इसका अर्थ पूरे application या सभी transitive/native dependencies को vulnerability-free घोषित करना नहीं है।

## Publisher की जरूरी manual settings और प्रमाण

1. **Audience/features:** Family edition चुना गया है: free-text chat/voice हटाकर fixed quick-chat और multiplayer रखा गया है। इस edition के लिए adult proof को fake नहीं किया गया है; सभी players पर एक ही capability सीमा लागू होती है। फिर भी publisher को actual child-data processing और देशों के अनुसार लागू consent/parental requirements की समीक्षा करनी है। Current source को worldwide legal certification न मानें।
2. **Play Console:** package `com.rekixo.ludopaws`, target audience/content rating, Families declarations, Data safety, ads declaration, actual privacy/deletion URLs और monitored support address को वास्तविक enabled features के साथ मिलाएँ। Console में इस्तेमाल हो चुके सबसे बड़े versionCode की पुष्टि करें; वर्तमान candidate `2 / 1.0.0-rc2` को बिना जाँच फिर upload न करें।
3. **Policy publication:** `docs/privacy-policy.html`, `docs/pricing.html`, `docs/terms-of-service.html` की publisher identity, contact, child treatment, providers और goods/refund terms की समीक्षा करें। तथ्य सही होने पर ही review markers हटाएँ और Actions variable `LUDOPROOF_POLICY_RELEASE_APPROVED=true` करें। Support-assisted/uninstalled deletion को वास्तव में पूरा करके देखें।
4. **Existing Google accounts:** Web deletion/recovery के लिए सही `GOOGLE_SIGN_IN_WEB_CLIENT_ID` और authorized web origin रखें। New Google linking और Play Games UI इस family release में बंद हैं; project ID भरने से वे चालू नहीं होंगे। Legacy Google-linked installs की migration/recovery को test करें। भविष्य में app sign-in खोलने पर package/certificate fingerprints और child-directed SDK review अलग पूरा करना होगा।
5. **Signing:** production GitHub environment में `LUDOPROOF_ANDROID_KEYSTORE_BASE64`, `LUDOPROOF_ANDROID_STORE_PASSWORD`, `LUDOPROOF_ANDROID_KEY_ALIAS`, `LUDOPROOF_ANDROID_KEY_PASSWORD`। Keys chat/repository में न रखें। Reviewed exact commit का signed workflow, verified AAB, फिर Play internal install और existing-install update test करें।
6. **Worker:** `LUDOPROOF_API_BASE_URL`, OAuth ID, `LUDOPROOF_ACCOUNT_WEB_ORIGIN=https://ludopaws.rekixo.com`; Cloudflare API access और existing Durable Object migrations की पुष्टि करें। `ENTRONEX_API_TOKEN` और `LUDOPROOF_SESSION_HMAC_KEY` server secrets ही रहें। Existing secrets की अंधी rotation न करें। Backup/restore, logs, alerting और rollback rehearsal करें।
7. **Paid goods:** Play catalog/product IDs और service-account permissions, server-only `GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY`, `GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL`; internal purchase, cancellation, duplicate callback, reinstall/restore और refund/revocation जाँचें। मौजूदा family build billing enablement अस्वीकार करता है; ये settings अकेले purchases नहीं खोलतीं। संबंधित checklists: `docs/GOOGLE_PLAY_BILLING_RELEASE_CHECKLIST.md`, `docs/GOOGLE_PLAY_ONE_TIME_BILLING_SETUP.md`।
8. **Ads:** वास्तविक AdMob app/unit IDs, rewarded SSV callback, consent और child/unknown-age request treatment, Families-eligible SDK/provider setup का परीक्षण करें। मौजूदा family build ads enablement अस्वीकार करता है। केवल `LUDOPAWS_AD_SAFETY_REVIEW_APPROVED` या ads flags भरना पर्याप्त नहीं है।
9. **Repository:** `main` protection/ruleset में CI, CodeQL, review, force-push/deletion restrictions लगाएँ; production environment approval और exact commit artifact provenance रखें। यह शाखा उन admin settings को स्वतः नहीं बदलती।
10. **Rollout:** enabled staging में complete 2/4-player games, Team Up, rematch, recreation/process death, reconnect/timeout, account deletion, poor network और relevant store flows; API28–36 तथा physical devices पर जाँच। उसके बाद internal/closed testing और staged production rollout में crash/ANR/server errors देखें।

यह सूची पूरे विश्व के प्रत्येक कानून की कानूनी पर्याप्तता का प्रमाण नहीं है। Publisher को अपनी वास्तविक audience, data और देशों के अनुसार समीक्षा करनी होगी। संबंधित प्राथमिक नीतियाँ: [Google Play Families](https://support.google.com/googleplay/android-developer/answer/9893335?hl=en), [AdMob targeting controls](https://developers.google.com/admob/android/targeting)।

Ads/paid goods की settings भविष्य के monetization release के लिए हैं; current family release में इन्हें off ही रखें। आपातकालीन online shutdown के लिए Worker mode `LOCKDOWN` रखें और आगे के deployments रोकें; reviewed family rollout `FAMILY_V1` उपयोग करता है।

## Validation evidence

वर्तमान source के final results के लिए `VALIDATION.json` देखें। Device पर न चले tests को compilation के आधार पर passed नहीं लिखा गया है। Local emulator run अधूरा रहा और user के निर्देश पर emulator/image/AVD हटा दिए गए हैं (लगभग 6.15 GB)। आगे physical phone के लिए `PHONE_TESTING.hi.md` में अलग QA app का तरीका है। Baseline की coverage और limitations `BASELINE_AUDIT.hi.md` में हैं; वह मूल commit का ऐतिहासिक audit है।
