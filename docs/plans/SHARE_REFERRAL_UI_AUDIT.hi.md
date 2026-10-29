# Share / Friendship Bundle audit और refinement — 29 अक्टूबर 2026

User report: Home Share → page → “Connect Google & invite” → generic Settings; unclear sharing/rewards; mismatched BG; overflowing gem bucket + Free 100 Gems presentation requested।

## Findings और final behavior

- Confirmed: ReferralActivity.act में unlinked snapshot पर showSettingsDialog सीधे खुलता था। Main action server disabled/privacy/rewarded/loading states में disabled या Retry बन जाता था; normal game sharing secondary link में दबा था। Popup भी connect action कहकर सिर्फ referral page खोलता था।
- अब main action हमेशा meaningful share path देता है। Guest, unknown/restricted privacy, unavailable/loading/error rewards, already rewarded और delivery VERIFYING states में plain official Play Store URL का Android share chooser खुलता है। Google save, campaign API success या reward consent plain sharing की prerequisite नहीं हैं। Offline में भी chooser/link बन सकता है; recipient को Play install के लिए connectivity चाहिए।
- Eligible linked account में enabled campaign और unqualified invitation state पर personal server-issued referral URL का existing authenticated path बना है। Terms/optional-verification consent पहले जैसा है। URL scheme/host/token validation, account binding और server reward verification नहीं हटे। Main invite failure पर clear error और separate Share Play Store link रहता है।
- Optional “Set up invitation rewards” अलग action है। Privacy-permitted user को existing Profile account actions में सीधे भेजता है, generic Settings में नहीं। वहाँ existing Save/Restore Google flow चलता है। Return पर fresh server status load होता है। Google linking को bypass करके referral reward नहीं दिया गया।
- Server /invite award already qualified होने पर BUNDLE_ALREADY_QUALIFIED लौटाता है; इसलिए VERIFYING/REWARDED states में main action new referral बनाने के बजाय plain sharing करता है। Progress refresh/header से देखा जा सकता है। No automatic extra award, wallet write या local 100-Gems credit। Existing status sync CosmeticInventoryStore के जरिए wallet भी apply करता है; duplicate grant नहीं जोड़ा।
- In-flight invite, status load, account switch/late callback, chooser repeat tap और pause/resume generation paths rechecked। Final invite UI callback पर current account फिर match होता है। Rapid repeated external chooser opens 750ms guard से coalesce; popup→page और optional account navigation existing shared navigation guard से हैं।

## UI

- Referral page और modal bundle popup shared plywood skin में हैं। Popup की animated galaxy layer पूरी तरह हटाई; shared rounded panel/scroll clipping रहे। Bundle card में recessed plywood, dark readable typography, wood secondary controls और warm-gold primary share action है।
- New ReferralGemRewardView: existing Store GemClusterView के faceted gems से भरी wooden bucket और बाहर गिरते gems; बगल में “FREE 100 Gems”, ठीक नीचे qualifying-match condition। Compact Home bundle और full page/popup में shared implementation; large text पर row wrap-content है। No remote/generated bitmap dependency और no new animation loop।
- “Free” का मतलब no purchase/ad-view requirement है; share/install alone reward नहीं है। Only inviter, new eligible install, verified real Online/Friends match, once-per-campaign conditions text/rules में बने हैं। Referral rewards का Google/age eligibility पहले जैसा है; plain game sharing सभी users कर सकते हैं।

## Recheck और pending acceptance

Source recheck: Home Share→ReferralActivity, all primary action states, popup CTA/navigation, current-account guards, existing /invite server contract, consent/withdraw/verification preservation, plain offline chooser, artwork small/large layout और shared BG factories पढ़े। Old showSettingsDialog route और popup HomeSpaceBackgroundView references हटे; diff whitespace साफ।

User workflow के अनुसार इस iteration में build/APK, lint/tests, device/share-app या actual two-account qualifying-install reward test नहीं किया। Chooser खुलना किसी recipient को message send करने का प्रमाण नहीं; real referral grant acceptance अभी बाकी है। कोई message/contact access या live backend/Console deploy नहीं किया। चरण 9–10 pending और live prices अपरिवर्तित।
