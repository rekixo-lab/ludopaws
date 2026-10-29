# Home carousel / Store friendship entry refinement — 29 अक्टूबर 2026

## Confirmed source issues

- HomePromotionCarousel explicitly cleared both promotion.background और bundle.background, exposing galaxy under wood-colored text। Previous referral screen/popup skin fix Home में इस override के कारण नहीं दिखती थी। ये overrides हटे। Each card का opaque themed surface और contrasting text रहता है।
- FeaturedPawsPreview ने सभी live models को fixed .9 zoom और .62 vertical anchor से draw किया। Hop lift/cat/Lumi/taller animal geometry GL viewport से बाहर जा सकती थी; parent clipChildren=false करने से GPU viewport में कटे pixels वापस नहीं आते।
- Home friendship action केवल SEE BUNDLE text था; Store spotlight में EXPLORE text था। दोनों पर अब visible real buttons हैं।

## Final source changes

1. Animated portrait previews को optional verticalAnchor=.69 और silhouette-aware zoom (.6 tall / .72 other) मिला। Model के ऊपर jump headroom है और foot room रखा है। Static portrait cache default .62/1.0 unchanged; gameplay model scale, jump, board geometry और movement state नहीं बदली। Shared preview fix Home/Store/bundle पर लागू है।
2. Friendship compact card का recessed plywood background अब carousel में भी बचता है। Headings/captions dark brown; FREE · 100 Gems white-on-dark-green badge और 12sp condition caption। User ने “300 Gems” कहा, पर campaign reward source में 100 है; reward amount नहीं बदला।
3. पुराने flat Canvas bucket को नया transparent 3D-rendered wooden/gold bucket + glossy overflowing cyan gems local PNG ने replace किया। imagegen skill/built-in tool से asset बना, inspected alpha range 0..255; 1254×1254 RGBA, लगभग 1.98MB। Original copied to drawable-nodpi/referral_gem_bucket_3d.png; no runtime network/media-generation dependency। Local source: /home/rekixo/.codex/generated_images/01a182ac-1974-7182-864e-87a4216120c7/exec-4ca75e46-b33f-4316-943f-b6d8abfecbbf.png। Project asset is canonical runtime copy।
4. Home friendship card का full-width 48dp green Invite a friend button parent के guarded ReferralActivity route को invoke करता है। Qualification से पहले instant Claim now नहीं लिखा। Reward rules और eligibility बने हैं।
5. Home Store spotlight में real 48dp button: Buy in Shop / Watch Ad in Shop / Unlock in Shop; disabled-ad build में View in Shop। यह existing Store category/character route खोलता है; button अपने-आप payment/ad नहीं चलाता। Price/name/category text को wood पर dark contrast मिला। Gameplay छोटे placement का existing explore-after-match behavior बना है। Hidden/unavailable offers के child CTA भी disabled हैं।
6. Store Paws/Board/Dice friendship entry अब single-line banner की जगह shared recessed plywood reward card: relevant item heading, same 3D gem bucket/FREE 100 Gems block और Invite a friend/View progress button। Existing popup path और entitlement/ownership/campaign checks अपरिवर्तित।

## Recheck

Native Button imports/package references, CTA→existing onExplore/performClick handlers, carousel drag interception vs tap, Home fixed 272/302dp area की weight layout, text wrapping, popup/Store reuse, shared preview call signatures और default preservation, resource naming/alpha, paused preview scheduling तथा owned/unavailable guards source में पढ़े। Diff whitespace साफ। Asset generated output visually inspected।

User workflow के अनुसार नया APK build, lint/test suite या connected-device run नहीं किया। Full hop-cycle visual acceptance on device, font-scale/short-window presentation और new CTA touch acceptance अभी बाकी है। चरण 9–10 pending; live prices/backend rewards/payment behavior नहीं बदला।

## Store reward-row arrangement correction

नवीन user requirement केवल Store के तीन friendship entries पर लागू: वही box/header/Invite button रखते हुए row में bucket → Blossom Garden board → Rabbit portrait → Pearl Paw dice → fixed right-side FREE · 100 Gems label। Existing compact reward row में opt-in includeBundleItems flag जोड़ा; Home/page/popup अपनी existing composition रखते हैं। सभी चार artworks समान square slots में available width बाँटते हैं; row की 72dp baseline height और 17sp reward label बने हैं। दाईं text column 126dp है; qualifying caption “After verified play” है। Plus symbols नहीं जोड़े ताकि जगह artworks के लिए बचे। Board और chrome एक ही square में measure होते हैं, जिससे उनका origin/size अलग न हो। Rabbit existing actual-model portrait cache से आता है; नया placeholder/vector/live hop नहीं जोड़ा।

Source recheck: तीनों Store sections का shared entry call, explicit art order, fixed right copy, equal-square child measure, board theme override, rabbit/dice IDs, CTA unchanged और opt-in default preservation जाँचे। Diff whitespace साफ। नया build/lint/tests/device run नहीं किया।
