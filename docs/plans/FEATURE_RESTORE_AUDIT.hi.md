# पुराने snapshot से गायब production features की बहाली

29 अक्टूबर 2026। Working branch `codex/rekixo-launch-branding`। User को installed build में पहले किए कई features नहीं दिख रहे थे; उनसे हर feature याद करके बताने के बजाय full Git/source comparison किया गया।

## कारण और प्रमाण

इस branch का starting snapshot `1ee19186` (25 अक्टूबर) था। Latest `origin/main` fetch करने पर `8e666957` मिला, जिसमें उसके बाद के Share/referral, bundle, promotional UI और अन्य काम हैं। पुरानी cached main ref से तुलना पर्याप्त नहीं थी। `1ee19186..8e666957` के tree में 312 files का delta था; यह count सभी source/docs/tests/config changes का है, 312 user-visible features का नहीं। इन commits को अब इसी branch में merge किया गया है। Main पर कोई push/merge/deploy नहीं किया गया।

दूसरा कारण local shortcut की build configuration थी। User के local repo की generated BuildConfig में `LUDOPROOF_BILLING_ENABLED`, `LUDOPROOF_ADS_ENABLED` और `LUDOPROOF_INTERSTITIAL_ADS_ENABLED` false मिले। पुराने Bash shortcut ने shared public build configuration पढ़ी नहीं थी। Store इन्हीं flags से Gems tab और ad surfaces छिपाता है। GitHub Actions की existing public variables में तीनों flags तथा ad-safety review true मिले; उन्हें इस काम में बदला नहीं गया।

## Latest main से जुड़े features

| क्षेत्र | Source और व्यवहार |
|---|---|
| Share / invitation | `HomeModesUi.shareLudoPaws` अब `ReferralActivity` खोलता है; verified invite attribution और invitation details जुड़े हैं। |
| Friendship Bundle | Bundle showcase, scoped popup, reminder/history policy, consent/revision handling और server referral integrity/delivery code। |
| Home promotional UI | `HomePromotionCarousel`, `HomeSpaceBackgroundView`, animated galaxy और Store/featured-character deep links। |
| Store showcase | `GamePawsPromotionView`, नए featured cards, available content filtering और inventory/selection snapshot आधारित rendering। |
| Content / graphics | Latest unified/generated content registry work और main के board/character/gameplay refinements। |
| Audio | Per-character audio profiles, loading/queue hardening, board ambience और foreground visibility lifecycle। |
| Match Gems | Ranked-match reward policy, server-authoritative grants, idempotency, delivery retry और client synchronization source। |
| Server / release hardening | Latest referral/Gems retry, storage/query-plan, deployed-commit/release gates और existing operational tooling। |

यह feature inventory source integration का रिकॉर्ड है। Rewards/referrals/ads की visibility existing campaign, account, privacy eligibility और server state पर भी निर्भर है; केवल source presence से हर account को grant होने का दावा नहीं है।

## हमारे बदलाव सुरक्षित रखे

- Rekixo launch, fast game splash, Home numeric age/privacy popup और offline policy reader। पुराना automatic GOT IT वापस नहीं आया।
- PGS Home/privacy flow में है; Application/splash से initialization वापस नहीं किया गया। PGS host recreation से referral का one-per-session popup opportunity पहले consume होकर cancel न हो, इसलिए referral scheduling उसके बाद है। Child/unknown privacy में restricted referral paths के पुराने eligibility rules बने हैं।
- Dev Tools, standalone preview screens और alternate developer/QA app packages वापस नहीं आए। CI की isolation API URL/emulator boundary से रहती है; user को अलग app नहीं बनती।
- Account-bound recovery/verification, encrypted sessions, frozen offline V6 bounded history और durable offline result recovery बने हैं। New latest-write queue और result reconciliation दोनों समेटे हैं। New main की window callback sound installation और async board/music loading पुराने performance fixes को supersede करते हैं।
- Server में leaderboard, referral और Gems retry deadlines साथ preserve हैं।

## एक main-app runner

VS Code और terminal का एक runner है: `node scripts/android-local-testing.mjs run`। `bash scripts/run-ludoproof.sh` उसी का compatibility wrapper है। Modes केवल configure/run हैं; developer/preview app या test-ad substitution नहीं। Public production configuration पढ़ी जाती है; incomplete flags पर चुपचाप features OFF करके APK install नहीं होती। Config refresh account/service secrets या signing keys नहीं पढ़ता। Feature values log होती हैं ताकि OFF setting दिखाई दे।

Selected device, SDK paths with spaces, retained local.properties entries, version compatibility, signature mismatch और install failure guards बने हैं। Source pull/configure से installed APK नहीं बदलता। User के Ctrl+Shift+B/run पर ही build/install होगा।

```bash
git pull --ff-only origin codex/rekixo-launch-branding
node scripts/android-local-testing.mjs configure
```

उसके बाद Ctrl+Shift+B, या `node scripts/android-local-testing.mjs run`। Default task tests/lint नहीं चलाता। Explicit `--verify` केवल final/user-requested verification के लिए है।

## इस iteration की जाँच और सीमा

Merge conflict markers, obsolete variant references, Home/Share/Store entry points, age-popup interaction, release fragment rule और critical lifecycle/storage paths का source review किया गया। चार edited JS files का non-executing syntax parse (`node --check`) और diff whitespace check किया गया; application/test suite नहीं चली। Imported runner/privacy test fixtures नई single-app requirement के अनुसार update हैं, executed नहीं हैं।

APK build/install, unit/server/instrumentation tests, lint, real payment/ad request, backend deploy और Play release नहीं किए गए। Latest merged Kotlin source का compilation तथा real-device acceptance अभी बाकी है। सभी दस चरणों के बाद combined verification वाला user निर्णय बना है।

## User build log के बाद correction

User के build में चार Kotlin errors मिले: CosmeticInventoryStore में दो पुराने `developerDicePreviewAllowed` references और StoreActivity में duplicate `onResume` declarations। ये merge review में छूटे थे। Deleted preview helper की cleanup branches हटाई गईं; Store showcase/referral resume और billing purchase-refresh को एक ही lifecycle method में रखा गया। Main Activity files में duplicate lifecycle declarations और removed developer symbols का source scan किया; अन्य matches नहीं मिले। Diff whitespace check साफ है। इस correction पर agent ने नया build/lint/test run नहीं किया; user का अगला build compilation का प्रमाण देगा। RECORD_AUDIO remove-marker warning build failure का कारण नहीं था; family permission-removal directive रखा गया है।
