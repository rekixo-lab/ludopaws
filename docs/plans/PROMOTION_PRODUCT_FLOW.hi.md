# Promotion product presentation / exact-item flow — 29 अक्टूबर 2026

User ने screenshot/code review के सुझाव लागू करने को कहा। Same branch `codex/rekixo-launch-branding`।

## Home

- GamePawsPromotionView का Home variant अब vertical: centered category + rounded-bold “Unlock <item>” title, weighted central actual artwork stage, bottom offer/progress + 48dp-minimum button। पुराने left/title–tiny-model–right/button layout की जगह स्पष्ट hierarchy है। Fixed Home carousel height और नीचे के game-mode buttons नहीं बदले।
- Artwork host में non-Texture children center-aligned square हैं; board और chrome एक shared square में हैं। Live renderer wide stage के center में काम करता है। Prominent Home preview के लिए framing zoom .68 tall / .78 others और preview-only hop lift 40% scale (smooth trajectory, कोई hard peak clamp नहीं) है; gameplay hops/geometry untouched। Default other preview framing unchanged।
- Offer label में existing account-scoped verified ad count: N / required ads completed। Mixed Gems/ads offers में दोनों readable lines। Ad-progress preference updates को listener refresh करता है। Value-bearing actions अभी भी Store पर explicit click के बाद हैं।

## Exact selected item in Shop

- Home callback अब cosmetic ID भेजता है। Store का नया promoted_cosmetic_id input id validate करके सही PAWS/BOARD/DICE tab चुनता है। Selected item card catalog/bundle entry से ऊपर आता है; generic category में item ढूँढना नहीं पड़ता।
- New PromotedStoreItemCard: title, actual character portrait/board/dice, verified ad progress, Gems और केवल applicable Play channel पर current Play-formatted price/unavailable label; correct existing handleCosmeticTap route वाला action। Unknown/unrenderable preview पर substitute character नहीं दिखता। Show/read से ad या payment auto-start नहीं होता।
- Focused character पर duplicate FeaturedPawsCard suppress है। Back to catalog से focus clear; rotation उसे intent से फिर resurrect नहीं करती। Character grid से नया focused character चुना जा सकता है। Focus ID/signature/state save तथा category switching paths reviewed। Regular FeaturedPawsCard के wood पर white/light text को dark readable colors मिले।
- At action time latest catalog item फिर resolve है; content unavailable/owned/level/Gems/ad/Play checks existing Store handlers पर रहते हैं। Ads-only disabled build में unavailable action है। Refund/token/payment verification architecture नहीं बदला।

## Match

- छोटे match promotion की existing protected-area sizing रहती है; View after match action अब item को app-private per-account interest में रखता है। Latest explicit selection, 24-hour expiry; no purchase/ad/wallet mutation। Compact CTA short placements में भी दिखाई देता है।
- Offline और online result renderer finished/presentation-complete state के बाद saved item देखने का prompt offer करते हैं। Active match से Shop नहीं खुलता। User View in Shop चुनें तभी exact item open होता है। Account switch, already owned/unavailable item, expiry, repeat result callbacks और bad-window lifecycle guards हैं। Prompt दिखाई देने पर saved entry consume होती है ताकि हर result repaint पर popup न आए।

## Recheck / remaining acceptance

Source checks: callback→Intent extra→Store validation/tab mapping, current product lookup, default non-promoted catalog, focus clear/recreation, native Button callbacks, actual ad-progress source, shared preview signature/defaults, matched package imports/registry methods, online/offline finish guard, per-account interest and hidden child CTA guards पढ़े। Diff whitespace साफ।

User iteration instructions के अनुसार APK build, compiler/lint/test suite या connected-device run नहीं किया। Home prominent animation framing, large-font layout, exact-item card touch flow, successful verified-ad progress update और end-of-match saved offer का device acceptance बाकी है। इसलिए measured smoothness या real ad/purchase completion का दावा नहीं। Phase 9–10 pending, live prices और referral 100 Gems unchanged।
