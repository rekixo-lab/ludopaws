# लूडो पॉज़ — प्रोडक्शन तैयारी की जाँच

**निर्णय: पूरे Online/Friends/Team Up/Store गेम की सार्वजनिक प्रोडक्शन रिलीज़ अभी NO-GO है। ऑफलाइन संस्करण भी अभी केवल सुधार और आंतरिक परीक्षण के लिए candidate है।**

जाँच: 20 अक्टूबर 2026, भारत समय। Repository: `rekixo-lab/ludopaws`। जाँचा गया GitHub `main`: `f7013741d9e88a7cf9476619c085375a0542580c`। मूल workspace HEAD `9f9c86b` पुराना था; उसे बदले बिना GitHub के exact commit का अलग snapshot बनाया गया। पुराने `audit-2026-10-20` दस्तावेज़ों को leads की तरह लिया और नीचे दर्ज समस्याओं को वर्तमान source से दोबारा जाँचा। पुराने audit के test counts को वर्तमान परिणाम नहीं माना गया।

## कवरेज और भरोसे की सीमा

Repository की सभी **685 tracked files** की path/size/SHA-256 सूची बनाई गई: 256 production Kotlin files, 103 Kotlin test/instrumentation files, 46 production server JS files, 38 server JS tests, 31 JS tooling scripts, 7 workflows, 18 XML, 5 JSON, 5 HTML और अन्य docs/assets/build files। सभी 115 JS/MJS files पर syntax check तथा सभी JSON/XML पर parse check सफल हुए। Asset/audio/brand/schema scripts भी सफल हुए।

यह व्यापक repository audit है: source control flow, targeted bug reproductions, automated tests, release configuration, GitHub CI/CodeQL और deployment evidence जाँचे गए हैं। **यह प्रत्येक source line की स्वतंत्र manual समीक्षा, सभी binary SDKs का reverse engineering, हर device/network की exhaustive testing, zero-bug guarantee या स्वतंत्र सुरक्षा प्रमाणपत्र नहीं है।** जहाँ device timing, remote credentials, Play Console या hosted behavior की पुष्टि नहीं हुई है, उसे साफ अलग लिखा गया है।

कोई game source fix, production account creation/deletion, purchase, message, PR, merge, deployment या workflow dispatch नहीं किया गया। Reproductions केवल स्थानीय disposable SQLite/fake Durable Object contexts में चले।

## रिलीज़ रोकने वाली स्थितियाँ

| स्थिति | वर्तमान प्रमाण | रिलीज़ के लिए आवश्यकता |
|---|---|---|
| Connected गेम जानबूझकर बंद | `AgeAccessPolicy` का local band server authorization नहीं। Android `LUDOPAWS_CONNECTED_FEATURES_ENABLED=false`; outer Worker age guard restricted routes हमेशा deny करता है, env/header से unlock नहीं होता। | Reviewed, trusted, account/session-bound eligibility तथा moderation/session revocation, फिर end-to-end परीक्षण। Lockdown को केवल flag बदलकर न हटाएँ। |
| Multiplayer smoke का अर्थ सीमित | Latest deploy #84 सफल है, लेकिन smoke अब छह restricted routes पर403 `AGE_ACCESS_NOT_AVAILABLE` जाँचकर exit करता है। | इसे successful match, voice या paid purchase का प्रमाण न मानें; enabled staging में वास्तविक complete flows अलग चलें। |
| Public policy publication blocked | Privacy/Pricing/Terms में release-review markers मौजूद; Pages workflow उन्हें मिलने पर publication रोकती है। | वास्तविक intended release के अनुकूल publisher-approved text, support/deletion route verification और store disclosures। यह कानूनी पर्याप्तता का निर्णय नहीं। |
| Exact current SHA का signed/store release स्थापित नहीं | मिले recent dispatches में latest signed workflow पुराने `e3db776...` पर था; current SHA का successful CI unsigned candidate बनाता है। GitHub releases list खाली। | exact tagged SHA का verified signed AAB, Play internal install/update और device signoff। Console की unpublished state इस जाँच से ज्ञात नहीं। |
| `main` merge safeguards अनुपस्थित | GitHub branch response `protected:false`; repository rulesets list `[]`। | Required CI/CodeQL/review, force-push/deletion restrictions और exact-commit release controls लगाएँ। किसी अन्य org policy की उपलब्धता का अनुमान नहीं किया गया। |
| वास्तविक उपयोग के प्रमाण अधूरे | CI API35 emulator है; job का नाम “real-device” है, physical phone नहीं। | Android API28–36/physical devices, poor network, process death, short screen/font scale, full matches और staged-release metrics। |

संबंधित source: [age guard](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/age-access-guard.js), [smoke script](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/scripts/production-game-smoke.mjs), [repository का prelaunch audit](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/docs/FINAL_PRELAUNCH_AUDIT_20261020.md)।

## अभी तक पूरे हुए परीक्षण

| जाँच | नतीजा | प्रमाण |
|---|---|---|
| Server Node tests | 258 पास;0 fail/skip | `backend-tests.log`; local Node24.22.0, CI Node22 अलग भी पास |
| Android JVM unit tests | 89 suites,420 tests पास;0 fail/error/skip | `android-test-summary.json`; exact current source |
| Source contract/security/asset scripts | 23 scripts पास | `source-gates.json` |
| Runtime/preflight tests | 19 पास;0 fail/skip | `source-gates.log` |
| Current-SHA CI instrumentation | API35 emulator पर3+27=30 tests पास | `github-runtime-evidence.json`; locally दोबारा नहीं चलाया |
| Current-SHA CodeQL | JavaScript और Kotlin दोनों jobs सफल | `github-evidence.json` |
| Local bug reproductions | पाँच actual-backend-handler तथा दो compiled Kotlin traces ने दोष की पुष्टि की | `backend-reproductions.log`, `kotlin-repro/result.txt` |
| File syntax/parsing |115 JS/MJS,5 JSON,18 XML में parse/syntax errors नहीं | `inventory-summary.json` |
| Resolved dependency graph |105 Maven coordinates scan; Guava पर2 advisory matches | `resolved-advisories.json` |

Local Release lint, release APK और AAB build भी सफल हुए। विस्तृत validation appendix नीचे है।

## वर्तमान source में मिली समस्याएँ

नीचे 22 findings हैं। P1 का अर्थ affected feature खोलने से पहले उच्च प्राथमिकता, P2 महत्वपूर्ण correctness/release work, P3 छोटा API defect है। बंद online feature की P1 finding को वर्तमान live exploit या वर्तमान offline crash नहीं कहा गया है। Deterministic source traces और reproduced behavior अलग बताये गए हैं।

| ID | प्राथमिकता | समस्या | लागू क्षेत्र |
|---|---|---|---|
| OFF-01 | P2 | [सामान्य Activity recreation पर ऑफलाइन मैच खुलने की जगह setup खुलता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/OfflineGameActivity.kt#L143) | वर्तमान ऑफलाइन रिलीज़ |
| SET-01 | P2 | [Settings का owned Board/Dice चयन लागू नहीं होता](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/settings/presentation/SettingsDialogUi.kt#L398) | पुराने wallet वाले installs; ऑफलाइन UX |
| ACC-01 | P1 | [Google marker बचा हो और encrypted session खो जाए तो Restore शुरू ही नहीं होता](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt#L455) | account recovery; वर्तमान UI/भविष्य connected |
| ACC-02 | P2 | [Profile छोड़ने पर account switch का global cleanup छूट सकता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt#L277) | connected features दोबारा खोलने से पहले |
| ACC-03 | P2 | [Google Save/Restore की progress portability का वादा लागू नहीं](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt#L254) | account UI/product promise |
| ACC-04 | P2 | [Personalized leaderboard cache खाते बदलने/डिलीट होने के बाद बचता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/leaderboard/data/local/LeaderboardCacheStore.kt#L15) | स्थानीय privacy; migrated installs |
| ACC-05 | P2 | [पुराना wallet response नया balance/selection cache पलट सकता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/local/GemWalletStore.kt#L30) | monetization फिर खोलने से पहले |
| REM-01 | P1 | [दूसरे public मैच में पहले room की revision बचने से state अटकती है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/OnlineMatchActions.kt#L391) | lockdown में unreachable; online blocker |
| REM-02 | P1 | [Friends voice SDP negotiation आगे नहीं बढ़ती](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/data/realtime/FriendsVoiceChatSession.kt#L273) | lockdown में बंद; voice blocker |
| REM-03 | P2 | [सामान्य Online launch के बाद recreation पर session restore छूटता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/MainActivity.kt#L69) | online दोबारा खोलने से पहले |
| REM-04 | P2 | [Friends search हर अक्षर पर focused editor हटा देता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/friends/presentation/FriendsActivity.kt#L576) | social दोबारा खोलने से पहले |
| REM-05 | P2 | [Finished match पर 2P/4P विकल्प enabled दिखते हैं पर बदलते नहीं](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/PublicMatchmakingActions.kt#L11) | online rematch |
| REM-06 | P2 | [Back से search छोड़ने पर server ticket cancel नहीं होता](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/RemoteExitActions.kt#L77) | public/team matchmaking |
| REM-07 | P2 | [पुरानी heartbeat failure नए account की Friend credential साफ कर सकती है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/friends/data/FriendPresenceController.kt#L91) | account switch के साथ social |
| BE-01 | P1 | [डिलीट खिलाड़ी का नाम पुराने finished match से फिर recent list में लिखा जाता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/friend-social.js#L243) | live deletion/data retention; source defect, no hosted mutation |
| BE-02 | P2 | [Friend request replay झूठा ACCEPTED/DECLINED लौटाता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/friend-directory.js#L1004) | friends फिर खोलने से पहले |
| BE-03 | P2 | [एक profile delete होने पर बाकी खिलाड़ियों का ranked result भी reject होता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/leaderboard-room.js#L412) | ranked फिर खोलने से पहले |
| BE-04 | P3 | [limit न भेजने पर leaderboard default 25 की जगह 1](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/leaderboard-room.js#L501) | API contract |
| GAME-01 | P2 | [Stack capture का hashed ruleset और वास्तविक engine अलग हैं](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/rulesets.js#L63) | वर्तमान offline contract और future online |
| SUP-01 | P2 | [SBOM अधूरा है और गलत app version लिखता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/scripts/generate-sbom.mjs#L34) | release/supply chain |
| SUP-02 | P2 | [Signed release metadata upload-signing की सही स्थिति नहीं बताती](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/scripts/hash-android-release.mjs#L67) | release evidence |
| SDK-01 | P2 | [Offline build flag के बावजूद Play Games SDK startup provider से initialize होता है](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/core/app/LudoProofApplication.kt#L30) | वर्तमान offline package/startup privacy boundary |

## Reproduction, प्रभाव और सुधार

### OFF-01 — सामान्य Activity recreation पर ऑफलाइन मैच खुलने की जगह setup खुलता है

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/OfflineGameActivity.kt:143](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/OfflineGameActivity.kt#L143)।

Robot/Pass & Play सामान्यतः resumeSavedMatch=false से खुलते हैं। onCreate savedInstanceState को restoration में नहीं लेता। खेल के बीच process/configuration recreation होने पर saved engine state के बावजूद setup खुलता है; नया Start पुराने मैच को बदल सकता है।

सुधार: savedInstanceState recreation और जानबूझकर नई शुरुआत में अंतर रखें; normal launch → play → recreate regression चलाएँ।

### SET-01 — Settings का owned Board/Dice चयन लागू नहीं होता

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/settings/presentation/SettingsDialogUi.kt:398](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/settings/presentation/SettingsDialogUi.kt#L398)।

Settings acquireOrSelect() का परिणाम अनदेखा करती है। CosmeticInventoryStore में owned item पर यह हमेशा INVALID लौटता है; चयन के लिए server API अपेक्षित है। Offline-only नए खाते में server wallet भी उपलब्ध नहीं।

सुधार: ऑफलाइन cosmetic विकल्पों की स्पष्ट नीति बनाकर वैध चयन लागू करें, या अनुपलब्ध controls को स्पष्ट रूप से बंद करें; connected संस्करण में authoritative select endpoint प्रयोग करें।

### ACC-01 — Google marker बचा हो और encrypted session खो जाए तो Restore शुरू ही नहीं होता

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt:455](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt#L455)।

Restore missing session पर ensureGuestSession() चलाता है। AccountRepository GOOGLE account_mode पर ACCOUNT_SESSION_REQUIRED फेंकता है; unauthenticated Google login तक पहुँच नहीं होती।

सुधार: Restore में सीधे challenge/login करें; linking के लिए ही guest bootstrap रखें। Corrupt session + GOOGLE marker regression आवश्यक।

### ACC-02 — Profile छोड़ने पर account switch का global cleanup छूट सकता है

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt:277](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt#L277)।

Restore process-wide presence pause करता है; देर से लौटे callback में destroyed Activity पर return होने से resume नहीं चलता। executor/flow का lifecycle-safe owner नहीं।

सुधार: application-scoped account coordinator, generation checks और guaranteed cleanup रखें।

### ACC-03 — Google Save/Restore की progress portability का वादा लागू नहीं

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt:254](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt#L254)।

ProfileStore XP/wins/badges स्थानीय SharedPreferences में रखता है; restore account/wallet/social identity लौटाता है, इन stats का cloud restore नहीं।

सुधार: सही local-only wording दें या authoritative progression sync/restore बनाएँ। Paid wallet और local XP को अलग समझाएँ।

### ACC-04 — Personalized leaderboard cache खाते बदलने/डिलीट होने के बाद बचता है

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/leaderboard/data/local/LeaderboardCacheStore.kt:15](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/leaderboard/data/local/LeaderboardCacheStore.kt#L15)।

एक global payload में viewer/isViewer भी हैं। Profile cleanup LeaderboardIdentityStore साफ करता है, अलग LeaderboardCacheStore नहीं। Offline में पुराना YOUR RANK दिख सकता है।

सुधार: viewer cache को account/profile से बाँधें; deletion और restore पर साफ करें।

### ACC-05 — पुराना wallet response नया balance/selection cache पलट सकता है

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/local/GemWalletStore.kt:30](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/store/data/local/GemWalletStore.kt#L30)।

applyServerSnapshot account जाँचता है पर monotonic wallet revision नहीं। catalog/billing/reward requests अलग executors से out of order लौट सकते हैं। यह UI/cache समस्या है; server debit bypass का प्रमाण नहीं।

सुधार: हर wallet mutation पर server revision बढ़ाएँ; एक repository में account generation और revision atomically जाँचें।

### REM-01 — दूसरे public मैच में पहले room की revision बचने से state अटकती है

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/OnlineMatchActions.kt:391](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/OnlineMatchActions.kt#L391)।

captureSession room बदलने पर lastRealtimeRevision reset करता है, lastAppliedAuthoritativeRevision नहीं। applyResponse maxOf(old,new) रखता है; अगली नई-room state stale समझी जाती है। Compiled current-policy repro: first=true, retained=200, second=false।

सुधार: सभी room-scoped revisions/session state साथ reset करें या room-keyed tracker रखें।

### REM-02 — Friends voice SDP negotiation आगे नहीं बढ़ती

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/online/data/realtime/FriendsVoiceChatSession.kt:273](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/data/realtime/FriendsVoiceChatSession.kt#L273)।

setLocalDescription/setRemoteDescription में दिया onSuccess sdpObserver के onCreateSuccess से जुड़ा है; onSetSuccess कुछ नहीं करता। offer भेजना/answer बनाना/candidates drain नहीं चलता। Current extracted callback repro में set-success callbacks=0।

सुधार: create और set के लिए अलग SdpObserver रखें; वास्तविक two-peer offer→answer→audio test करें।

### REM-03 — सामान्य Online launch के बाद recreation पर session restore छूटता है

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/MainActivity.kt:69](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/MainActivity.kt#L69)।

persisted session restore launch extra पर निर्भर है; सामान्य Online navigation false भेजती है। savedInstanceState इस फैसले में नहीं। मौजूदा restore instrumentation पहले से true extra भेजती है।

सुधार: normal-entry recreate/process-restore test और explicit restoration state लागू करें।

### REM-04 — Friends search हर अक्षर पर focused editor हटा देता है

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/friends/presentation/FriendsActivity.kt:576](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/friends/presentation/FriendsActivity.kt#L576)।

afterTextChanged → renderFriends → removeAllViews → नया EditText। focus/IME selection बचाने का रास्ता नहीं। दस-सेकंड refresh भी वही rebuild करता है।

सुधार: search editor स्थिर रखें; केवल filtered rows update करें। IME/device test जोड़ें।

### REM-05 — Finished match पर 2P/4P विकल्प enabled दिखते हैं पर बदलते नहीं

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/PublicMatchmakingActions.kt:11](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/PublicMatchmakingActions.kt#L11)।

selector किसी भी non-null matchId पर return करता है; finished-state UI उन buttons को enable करती है।

सुधार: केवल unfinished match में रोकें या नई search setup से पहले finished identity साफ करें।

### REM-06 — Back से search छोड़ने पर server ticket cancel नहीं होता

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/RemoteExitActions.kt:77](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/online/presentation/actions/RemoteExitActions.kt#L77)।

बिना match ID का exit स्थानीय ticket साफ करता है; cancelPublicMatch नहीं भेजता। Team Up Back finish() करता है। server matching/bot fill चलता रह सकता है।

सुधार: Back और CANCEL को durable cancellation/reconciliation path पर लाएँ; MATCHED race संभालें।

### REM-07 — पुरानी heartbeat failure नए account की Friend credential साफ कर सकती है

Source: [android/app/src/main/java/com/rekixo/ludopaws/feature/friends/data/FriendPresenceController.kt:91](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/feature/friends/data/FriendPresenceController.kt#L91)।

stop() भविष्य runnable रोकता है, in-flight request नहीं। पुराने credential का FRIEND_AUTH_INVALID आने पर store बिना generation/credential तुलना साफ होता है।

सुधार: compare-and-clear exact credential और account generation checks लागू करें।

### BE-01 — डिलीट खिलाड़ी का नाम पुराने finished match से फिर recent list में लिखा जाता है

Source: [server/src/friend-social.js:243](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/friend-social.js#L243)।

वास्तविक handlers + in-memory SQLite reproduction: finished result sync → Alpha delete → surviving recent empty → वही result sync → Alpha वापस। finished-room reads यह sync दोहराती हैं।

सुधार: sink tombstone/deleted-identity validation और idempotent completion outbox रखें।

### BE-02 — Friend request replay झूठा ACCEPTED/DECLINED लौटाता है

Source: [server/src/friend-directory.js:1004](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/friend-directory.js#L1004)।

Decline के बाद accept replay में DB DECLINED रहती है, friendship नहीं बनती, response ACCEPTED कहता है। Current handler reproduction सफल।

सुधार: persisted terminal status लौटाएँ; विरोधाभासी replay पर conflict दें।

### BE-03 — एक profile delete होने पर बाकी खिलाड़ियों का ranked result भी reject होता है

Source: [server/src/leaderboard-room.js:412](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/leaderboard-room.js#L412)।

Current-handler repro: A+B register → A delete → authoritative-shaped terminal result →403 और B को कोई entry नहीं। Match submission deleted profile को फिर शामिल करता है।

सुधार: deleted participant को scoring से safely अलग करें और surviving eligible profiles का idempotent result लिखें।

### BE-04 — limit न भेजने पर leaderboard default 25 की जगह 1

Source: [server/src/leaderboard-room.js:501](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/leaderboard-room.js#L501)।

Number(null)=0 फिर clamp=1। Actual handler repro में तीन entries में केवल एक लौटती है। Android सामान्यतः limit भेजता है।

सुधार: missing query parameter अलग संभालें।

### GAME-01 — Stack capture का hashed ruleset और वास्तविक engine अलग हैं

Source: [server/src/rulesets.js:63](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/server/src/rulesets.js#L63)।

V3/V4 declaration capture-all कहता है; server engine केवल exactly one opponent token पकड़ता है। Actual V4 repro में दो-stack पर captures=0। Offline binding भी capture-all घोषित करती है जबकि OfflineGameEngine size!=1 पर skip करता है।

सुधार: product rule तय करके नया reviewed ruleset/version और server/offline conformance tests बनाएं; पुराने published hash के नीचे semantics चुपचाप न बदलें।

### SUP-01 — SBOM अधूरा है और गलत app version लिखता है

Source: [scripts/generate-sbom.mjs:34](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/scripts/generate-sbom.mjs#L34)।

चार hardcoded components में अधिकांश Android runtime/transitive dependencies नहीं; app version rc2 है पर metadata rc1। इसे vulnerability/license inventory मानना गलत।

सुधार: resolved release runtime/native dependency graph और सही build version से SBOM बनाएँ; schema validate करें।

### SUP-02 — Signed release metadata upload-signing की सही स्थिति नहीं बताती

Source: [scripts/hash-android-release.mjs:67](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/scripts/hash-android-release.mjs#L67)।

signed workflow apksigner/jarsigner verification के बाद यही script चलाती है। उसका note upload signing को भी external कहता है। Hash bytes सही हो सकते हैं, लेकिन verified upload signature की provenance अस्पष्ट/विरोधाभासी है; Play distribution signature अलग है।

सुधार: verified upload signature/certificate और Play distribution signature अलग fields में derive करें।

### SDK-01 — Offline build flag के बावजूद Play Games SDK startup provider से initialize होता है

Source: [android/app/src/main/java/com/rekixo/ludopaws/core/app/LudoProofApplication.kt:30](https://github.com/rekixo-lab/ludopaws/blob/f7013741d9e88a7cf9476619c085375a0542580c/android/app/src/main/java/com/rekixo/ludopaws/core/app/LudoProofApplication.kt#L30)। अतिरिक्त प्रमाण: `merged-release-manifest.xml`, `apk-manifest-tree.txt`, `playsdk-provider-init.txt`, `playsdk-internal-init.txt`, `playsdk-initialization.txt`।

Application का manual PlayGamesSdk.initialize() connected flag से gated है, पर resolved release manifest और final unsigned APK दोनों में PlayGamesInitProvider enabled रहता है। Actual SDK22.1.0 bytecode में provider.onCreate → zzbw.zza(context), वही initializer है जो PlayGamesSdk.initialize() चलाता है। इसलिए manual call रोकना SDK initialization रोकना नहीं है। MobileAdsInitProvider भी merged manifest में मौजूद है; उसके data/network side effects इस जाँच में मापे नहीं गए। यह server lockdown bypass, automatic successful sign-in या data leak का प्रमाण नहीं है।

सुधार: offline variant में unused SDK dependencies/providers हटाएँ या approved SDK-specific startup controls लगाएँ। Packaged APK providers और cold-start network/device traces जाँचें; release source gates को केवल permissions/manual initialize strings तक सीमित न रखें।

## Dependency और native-code जाँच

11 direct dependencies के advisory lookup में कोई match नहीं था। पर **resolved release runtime graph के 105 Maven coordinates** जाँचने पर transitive `com.google.guava:guava:31.1-android` मिला, जिसके लिए OSV ने दो advisories लौटाईं: [CVE-2020-8908](https://github.com/advisories/GHSA-5mg8-w23w-74h3) और [CVE-2023-2976](https://github.com/advisories/GHSA-7g45-4rm6-3mm3)। यह Ads → Privacy Sandbox dependency path से आता है। Maintainer ने संबंधित temporary-file methods के permissions सुधारने वाला [patch](https://github.com/google/guava/commit/feb83a1) प्रकाशित किया था।

**यह dependency match है, इस Android app में साबित exploitable leak नहीं।** App source में affected methods का direct उपयोग नहीं मिला; minSdk28, Android sandbox, SDK usage और R8 reachability के आधार पर applicability की समीक्षा बाकी है। सबसे सरल release विकल्प offline variant से unused Ads/Billing/voice SDKs हटाना हो सकता है; अन्यथा compatible patched dependency चुनकर runtime tests चलाएँ। केवल direct declarations scan करने से यह dependency छूट जाती थी; hardcoded SBOM भी इसे नहीं दिखाता। पूर्ण binary vulnerability/license assessment अभी बाकी है।

WebRTC की merged native libraries के ELF LOAD segment alignment भी जाँचे: arm64-v8a और x86_64 में16KB, 32-bit ABIs में4KB। यह package/device compatibility का एक हिस्सा है; वास्तविक16KB-device run का विकल्प नहीं। Native WebRTC internals की पूर्ण CVE/reachability समीक्षा नहीं की गई।

## जो बातें सही दिशा में हैं

- Backend authoritative move/roll checks और idempotent replay/fallback हैं; malformed command indexes के tests हैं। Source से कोई confirmed arbitrary client dice override नहीं मिला।
- Online dice crypto RNG/rejection sampling उपयोग करता है। Offline derivation/conformance और local save consistency tests मौजूद हैं। Opening guaranteed six explicit gameplay rule है; हर effective roll को IID/independently certified कहना सही नहीं होगा।
- Google token signature/issuer/audience/nonce validation, expiring rotating account sessions और deletion replay boundaries मौजूद हैं। इनसे actual Google Console setup प्रमाणित नहीं होता।
- Wallet/Play/AdMob grants server verification पर निर्भर हैं; local callback को paid authority नहीं बनाया गया। Monetization वर्तमान build/deploy में बंद है।
- Cleartext traffic और Android backup बंद हैं; token storage Keystore/AES-GCM उपयोग करता है। Sensitive audio/ads/billing permissions को actual package से हटाने की CI checks हैं।
- Actions commit SHAs और Gradle distribution digest pinned हैं; JavaScript तथा Android Kotlin CodeQL दोनों current SHA पर वास्तव में चले।
- पुराने audit की “post-win exit network के पूरे timeout तक अटकता है” समस्या current code में1.2s decision budget से संबोधित है; उसे नई unresolved finding नहीं गिना गया। Voice status/buffer और realtime callback refactor सुधरे हैं, पर SDP observer defect अभी बचा है।

## आगे आवश्यक validation और engineering work

ये confirmed outage/exploit की सूची नहीं; production signoff के लिए अनमापे क्षेत्र हैं:

1. **Core offline lifecycle:** normal entry → full 2P/3P/4P match, Robot match, Back confirmation, rematch, background/foreground, font/locale recreation, killed-process restore, corrupt/low-storage save। अभी OfflineGameActivity में onStop work-pause policy नहीं; background bot/animation behavior जाँचना चाहिए।
2. **Game contracts:** exact home, safe cells, stacked pawns, opening-six, third-six, captures, finished-player rotation, Team Up acting seat और every version का server/offline conformance। Hash mismatch GAME-01 का सही product निर्णय चाहिए।
3. **Online lifecycle:** A→B room revision reset, same-request WS/REST timeout race, two failed auto-move attempts के बाद reconnect, cancellation vs MATCHED, slow ACK, stale callbacks और process death। Pure-policy tests अकेले composed flow साबित नहीं करते।
4. **Social/voice:** ordinary registration→request→accept→invite→join→start; स्थिर search editor, accepted-invite authorization, block/report की live room/token revocation। Existing room-code share में authenticated join link नहीं; recipient flow साफ करें। TURN relay/real NAT/firewall audio call, microphone completion after match end और audio module lifetime जाँचें।
5. **Account/privacy:** GOOGLE marker + lost key recovery; A→B restore और old-heartbeat race; Google cancel/Back; session logout/revocation; deletion retries, historical match retention/anonymization और every downstream data sink। Global personalized leaderboard cache और stale wallet क्रम सुधरें।
6. **Store/ads:** Console product IDs/actual localized prices, pending/duplicate purchases, consume/ack failures, refund/revocation, cross-device restoration, SSV signature/reward transaction identity, UMP and Remove Ads behavior—enabled staging/signed Play test build पर। वर्तमान disabled smoke इनका प्रमाण नहीं।
7. **Performance/accessibility:** short-height Home layout, large fonts/TalkBack/touch targets/Hindi localization; low-end phones पर cold start, sustained FPS/jank, memory/OOM/ANR, thermal/battery और long-game behavior। Source में fixed Home spacers/छोटा English text और draw allocations हैं; मात्र lint warnings को runtime failure नहीं माना गया।
8. **Scale/operations:** Friends/global leaderboard/queue Durable Objects पर expected concurrency load; paginated lists और completion-sync write amplification; socket/message budgets; incident alarms, latency/error dashboards, cost caps, backup/restore exercise और tested rollback। Deploy workflow publish के बाद secret checks/smoke करती है; failure पर automatic rollback नहीं है।
9. **Release hygiene:** supported-client/min-version contract (old commit/reveal endpoints410), updated docs (कुछ पुराने EntroNex transport/ruleset descriptions हैं), truthful signing evidence, resolved dependency SBOM/license inventory और retained signed provenance। Artwork/audio और सभी third-party libraries की rights/license provenance भी publisher को verify करनी है।

## सुधार का क्रम

1. वर्तमान lockdown बनाए रखें। तय करें कि अगली release केवल Offline है या पूरा connected product।
2. Offline recreation, SDK startup boundary, rule mismatch और Settings behavior सुधारें; नए-user offline UI में unusable account/store promises हटाएँ या स्पष्ट उपलब्धता दिखाएँ।
3. Google recovery, deleted identity reappearance, room revision और voice SDP जैसे P1 source defects सुधारें; हर दोष के लिए वास्तविक boundary regression जोड़ें।
4. Account/cache/cancellation/social correctness के P2 सुधार पूरे करें; required branch checks, dependency/SBOM/signature evidence सुधरें।
5. Exact signed build पर internal Play/device matrix, actual policies/deletion/support और rollback exercise पूरा करें। फिर छोटा staged rollout और monitored acceptance।

Snapshot credential-pattern screen में केवल private-key parsing regex और generated/fake test-key markers मिले; वास्तविक committed credential इस सीमित screen से पुष्ट नहीं हुआ। Full Git-history/entropy secret scan, Cloudflare/Google secret revocation status और account-console permissions इस audit में प्रमाणित नहीं हैं।

## ऑडिट artifacts

- `FILE_INDEX.md`: सभी files की human-readable सूची और validation coverage।
- `file-inventory.json`: exact SHA की सभी685 files और hashes।
- `inventory-summary.json`: file counts और syntax/parse errors।
- `findings.json`: source line, immutable GitHub link, priority, scope, effect और fix।
- `github-evidence.json`: exact-SHA CI/CodeQL/deploy jobs, recent signed runs, branch/ruleset observations।
- `backend-tests.log`, `source-gates.log/json`, `android-validation.log`, `android-test-summary.json`, `lint-summary.json`: actual local validation।
- `backend-reproductions.mjs/log`: पाँच actual-handler defect reproductions।
- `kotlin-repro/`: current source से extracted/compiled revision and SDP-callback reproductions; fake SDP interfaces हैं, वास्तविक WebRTC call नहीं।
- `release-dependencies.log`, `resolved-advisories.json`, `advisory-details.json`, `native-alignment.json`: resolved supply-chain evidence।
- `live-readonly.json`: इस environment के GET probes। यहाँ custom API और public web origin पर Cloudflare403/error1010 आया। **यह globally down होने का प्रमाण नहीं**: same-SHA deploy logs में health/readiness/store checks सफल हैं। इस network से independent live verification और public policy-content verification नहीं हो सकी।

## अंतिम local release validation

Command: `ANDROID_HOME=/home/rekixo/Android/Sdk ./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease --console=plain`। Result: **BUILD SUCCESSFUL**,13m3s;85 tasks (72 executed,13 cache)। Java21, SDK36, default offline release flags। Production signing key नहीं दी गई।

- Android unit tests: **420/420**,89 suites,0 failures/errors/skips।
- Release lint: **0 errors**,422 warnings और1 hint। इनमें78 DrawAllocation,172 SetTextI18n और अन्य accessibility/API/dependency warnings हैं। Warnings की count defect severity का पर्याय नहीं; runtime/device QA अभी आवश्यक है।
- APK: `app-release-unsigned.apk`,69,416,017 bytes; AAB:39,620,031 bytes। SHA-256 `built-artifacts.json` में हैं। यह production upload-signed build नहीं है।
- `aapt`: package `com.rekixo.ludopaws`, versionCode2, versionName1.0.0-rc2, minSdk28, targetSdk36। Packaged APK में RECORD_AUDIO, AD_ID, AdServices ID/Attribution/Topics और Play Billing permissions नहीं हैं।
- `zipalign -c -P16 -v4`: सफल। ARM64/x86_64 native ELF LOAD alignment16KB। वास्तविक16KB device run अभी नहीं हुआ।
- Packaged manifest में PlayGamesInitProvider और MobileAdsInitProvider मौजूद हैं; SDK-01 केवल merged-source अनुमान नहीं है। Play Games provider का initializer actual resolved SDK bytecode से सत्यापित है। Cold-start network payloads नहीं मापे गए।

इस validation में current game source नहीं बदला। `main` को audit के अंत में पुनः जाँचा: वही `f701374...` SHA और protected=false।
