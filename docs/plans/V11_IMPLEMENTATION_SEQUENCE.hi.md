# V11 — संशोधित requirement और implementation sequence

29 अक्टूबर 2026। यह नवीन user requirement पर आधारित canonical योजना है और पहले के generic दस-चरण क्रम को बदलती है। अभी planning only; live prices/code/deployment नहीं बदल रहे। User-reported baseline: v9 production live, v10 internal। सब तैयार होने पर एक v11 candidate और फिर device/internal acceptance।

## निश्चित startup design

App icon tap → Rekixo launch branding → Ludo Paws splash → Home पर तत्काल एक age/privacy popup → Continue → usable Home। मौजूदा first-run GOT IT/tutorial popup स्वतः दिखना हटाना है; उसे नए popup से पहले/बाद में नहीं दिखाना। Help बाद में Settings में रह सकती है।

Launch: Rekixo brand only। Source repo rekixo-lab/rekixo-site, main tree SHA 5e52ab17d0d6d8b4975e567cf2dc6b43adae096a। उपलब्ध assets public/rekixo-logo-static.svg (blob 75d2e1197f8925061ca5b36cf67bac5d5e1dce9a), public/rekixo-logo-light.svg। Implementation में original artwork inspect करके Android-compatible asset बनाना; web SVG सीधे Android drawable नहीं मानना। Android system launch और custom splash को double-brand screens की अनावश्यक देरी न बनाना।

Splash: बीच में game logo और नीचे real loading/progress bar। Local assets/settings/session restore और आवश्यक startup tasks से readiness जुड़ेगी। Known work में actual progress; unknown network wait में indeterminate bar, कोई fake 0→100 timer या indefinite blocked splash नहीं। Network fail पर offline Home और retry। यूज़र ने device पर तेज़ Rekixo launch → game splash → Home transition देखकर गति स्वीकार की है; उसे जानबूझकर धीमा नहीं करना है।

Guest game identity और Google Play Games identity अलग हैं। Splash में अपना guest/session resume/create background में होगा। नवीन user requirement: **Play Games connection को splash से हटाकर Home के age/privacy popup flow में शुरू करना है।** Unknown age में permitted neutral choice के बाद ही optional SDK शुरू हो; पहले से permitted choice हो तो Home/popup lifecycle में automatic connection हो सके। Account missing, cancelled, supervised/age restrictions या network failure पर fake connected न दिखाएँ। Existing saved account को नया guest बनाकर overwrite नहीं करना। कोई game Google-save payment prerequisite नहीं। यह timing बदलाव चरण 4 में popup के साथ implement करना है; वर्तमान चरण-3 runtime अभी permitted returning player के लिए Application/splash से SDK शुरू करता है।

काम करने का user-approved तरीका: हर चरण में source change और उसी branch पर commit/push; हर बदलाव पर APK build, lint या test suite नहीं चलाना। सभी दस चरणों के बाद combined build/tests/recheck। बीच में user स्वयं device acceptance करेंगे; नया source pull करना installed APK को अपने-आप नहीं बदलता।

## एक age/privacy popup की specification

- Heading: How old are you?
- Number box शुरू में खाली या —; adult/18 default नहीं। Slider thumb बाएँ unset position में।
- Deliberate selection पर 1–100 integer age; tap/drag के साथ keyboard/TalkBack increment/decrement भी। Thumb को पहली position पर चुनने से age 1 भी selectable हो।
- नीचे Continue; age चुने बिना अनजाने में default submit नहीं। Unknown/skip behavior अलग documented decision है; उसे silently adult नहीं मानना।
- नीचे user-requested copy का polished draft: “By continuing, you confirm that you have read and agree to our Privacy Policy.” Privacy Policy clickable। यह advertising/parental consent का blanket प्रमाण नहीं।
- Neutral appearance: adult पर green reward/extra benefits जैसी age बढ़ाने की प्रेरणा नहीं। Local storage में जरूरत जितना age band; exact age/DOB server पर भेजना default नहीं।
- Choice/version persist; हर launch पर फिर popup नहीं। Settings से privacy/age choice देखें/बदलें। Background, rotation, link खोलकर लौटना और double Continue से duplicate transitions न हों।
- Google द्वारा आवश्यक UMP consent/Play approval provider-controlled हैं; अपने एक popup से इन्हें replace करने या हर user के लिए हर sign-in बिना prompt होने का दावा नहीं।

## नया काम का क्रम

| क्रम | काम | पूरा होने का प्रमाण |
|---|---|---|
| 1 | Startup state machine और policy/account separation | first-install/returning/offline/expired/saved-account paths; guest vs Play Games और age-before-sensitive-SDK नियम तय |
| 2 | Rekixo launch branding | repository asset inspect/import, correct system launch theme; unwanted launch flashes नहीं |
| 3 | Game splash + natural progress + guest readiness | centered logo/bottom bar/top truthful status; fake percentage नहीं; bounded wait, offline fallback, session duplication नहीं |
| 4 | Home age-slider popup और GOT IT removal | 1–100 selection, blank initial box, Continue/privacy link; age persisted; accessibility; एक app-owned first-run popup |
| 5 | Navigation/shared UI stability | lag, Back/resume, stacking, empty/loading/retry states, session transitions सुधरें |
| 6 | Store/catalog UX | same-screen availability retry, selected item preserved, real Play price state; Coming Soon/unsupported/owned/insufficient Gems के साफ कारण; unavailable content पर spend नहीं |
| 7 | Ads और reward UX | privacy readiness, explicit Watch, loading/no-fill/retry; receipt-verified credit और durable pending/restart recovery; unexpected ad auto-show नहीं |
| 8 | नया purchase-plan mapping और worldwide price preview | नीचे के targets के अनुसार products/benefits की review; existing buyers/IDs सुरक्षित; client/server/public disclosures mapping |
| 9 | Payment का अंतिम implementation/audit | guest native checkout, provider approval, server token/order verification; v9 free-buy report, pending/cancel/duplicate/refund/restore और wallet grants का वास्तविक validation |
| 10 | Combined final tests → v11 internal candidate → device acceptance | exact-source tests + signed Play-installed build; successful/declined/pending license-test transaction और exact wallet grant; उसके बाद live update का निर्णय |

हर चरण पर सीमित relevant checks; combined भारी suite अंत में। Phase 9 तक payment की नई debugging/Console price edits नहीं। पहले के local payment changes draft हैं, live बदलाव या accepted final design नहीं।

## Price targets और प्रस्तावित गणना

User target: Remove Ads USD 1.00 और Starter USD 0.50। ये targets अभी Play Console में apply नहीं किए गए। Current source की quantities 300, 1,200, 3,000, 7,500, 16,000, 35,000 हैं; legacy product ID में अंक अलग हो सकते हैं, ID rename नहीं करना। निम्न बड़े packs **प्रस्ताव** हैं; current worldwide price audit के बाद final करेंगे।

| Product | मौजूदा grant बनाए रखकर target/proposal USD | Gems प्रति USD |
|---|---:|---:|
| Remove Ads + existing 5 Gems bonus | 1.00 — user target | cosmetic entitlement; rate लागू नहीं |
| Starter — 300 Gems | 0.50 — user target | 600 |
| Value — 1,200 Gems | 1.50 — proposal | 800 |
| Popular — 3,000 Gems | 2.50 — proposal | 1,200 |
| 7,500 Gems | 5.00 — proposal | 1,500 |
| 16,000 Gems | 10.00 — proposal | 1,600 |
| 35,000 Gems | 20.00 — proposal | 1,750 |

यह बढ़ते pack के साथ बेहतर Gems-per-dollar देता है और मौजूदा grant quantities बदलने से बचता है। मौजूदा बड़े-pack USD prices source code में hard-coded नहीं हैं; table उन्हें verified current prices/ठीक 50% कटौती नहीं कहती। नए Gem amounts की जरूरत हो तो phase 8 में अलग decision। Subscription इस request का हिस्सा नहीं। पुराने Remove Ads buyers की entitlement और पहले मिले Gems नहीं घटेंगे।

Worldwide का अर्थ USD reference target के अनुरूप regional Play prices, हर देश में literal $ price या identical exchange-rate amount नहीं। Actual local currencies, taxes, supported minimum/maximum price और rounding का Console preview देखना; valid USD 0.50 tier हर market में मानकर न चलना। UI को Play ProductDetails formattedPrice ही दिखाना है, USD/INR fallback hard-code नहीं।

## अंतिम payment phase में दर्ज issue

यूज़र की v9 रिपोर्ट: बिना payment buy मिल रहा है। P0 issue, root cause unverified। Real-money vs Gems purchase, licensed test card vs real order, pending/cancel state, token replay, cached entitlements और server ledger अलग जाँचें। “Native sheet खुली” successful payment नहीं। इस planning turn में समस्या का patch/deploy या cause का अनुमान लागू नहीं।

## स्रोत और नीति-संगति

- Branding repo: https://github.com/rekixo-lab/rekixo-site/blob/main/public/rekixo-logo-static.svg
- Guest और PGS separation: https://developer.android.com/games/pgs/platform-authentication
- Neutral age/data handling: https://support.google.com/googleplay/android-developer/answer/9893335?hl=en
- Required provider consent: https://developers.google.com/admob/android/privacy
- Worldwide currencies: https://support.google.com/googleplay/android-developer/answer/1169947?hl=en

मौजूदा code review: HomeActivity first-run guide दिखाता है; launch styles में game branding है; Application family mode में PlayGamesSdk initialization रोकता है; FamilyAgeScreen numeric slider के बजाय age bands देता है। अतः सिर्फ popup repaint नहीं, startup/account lifecycle को पहले बदलना होगा।

**कहाँ से शुरू करें:** चरण 1 का startup state diagram और तीन screens की exact UI specification। फिर क्रमशः launch asset/theme, splash/session और age popup। Payment अंत में।


## चरण 1 का परिणाम

Startup flow/state machine और screen contracts पूरे: [V11_STARTUP_FLOW_SPEC.hi.md](V11_STARTUP_FLOW_SPEC.hi.md)। चरण 1 documentation/specification complete; runtime चरण 2–4 अभी pending। अगला implementation चरण 2: Rekixo launch branding।


## चरण 2 का परिणाम

Rekixo native launch branding लागू; generator/source identity और brand checks पास; `.dev` APK assembly तथा lint सफल (0 errors)। Game splash और age popup चरण 3–4 में pending। Source/provenance तथा device commands: [branding README](../../branding/rekixo/README.hi.md)। Physical launch appearance का user acceptance अभी बाकी।


## चरण 3 का परिणाम

Game preparation splash और backend guest/session coordinator के बाद user screenshots के अनुसार वास्तविक Google Play Games automatic platform connection जोड़ा गया है। दोनों identities अलग हैं। Privacy-permitted original-package build में SDK initialization और authentication check चलता है; manual Google-save आवश्यक नहीं। Unknown age में initialization phase-4 choice तक deferred रहता है। नई age choice के बाद automatic refresh hook है। Source और device/build instructions: [चरण 3 Play Games](PHASE3_PLAY_GAMES.hi.md)। Physical-device banner acceptance बाकी है। Age/privacy popup और GOT IT removal चरण 4 में बाकी हैं।
