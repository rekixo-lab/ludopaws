# Character placeholder audit और fix — 29 अक्टूबर 2026

Branch `codex/rekixo-launch-branding`। User ने Shop, game-start/setup और बाकी character surfaces पहले inspect करके actual characters दिखाने को कहा।

## Current source में क्या मिला

1. `LudoPawsCharacterAvatarView` 25 source references वाला shared portrait surface है: Store/featured cards, profile, local setup, player cards और promotions इसे इस्तेमाल करते हैं। Cache miss पर `CharacterRegistry.resolve(id).avatar.draw(...)` पुरानी hand-drawn 2D art दिखाता था; फिर असली production-geometry portrait आने पर बदलता था। यह placeholder flash का मुख्य source है।
2. `LudoPawsCharacterPortraitBitmapCache` offscreen ES3 से वही renderer/meshes/materials render करता है जो gameplay में हैं, लेकिन सिर्फ memory में bitmap रखता था। Process restart पर images फिर generate होती थीं। Request पहले visible draw पर आती थी।
3. किसी एक render exception पर global rendererUnavailable flag पूरे process के लिए बाकी characters भी रोक देता था। Null result के बाद View उसी requested key को हमेशा done मानता था; retry नहीं थी।
4. FeaturedPawsCard/GamePawsPromotionView live preview startup/visibility gating तक shared avatar दिखाते हैं। इसलिए उनकी अस्थायी art भी उसी shared vector fallback से आती थी। Actual live preview code fake model नहीं बनाता।
5. सभी 11 registered production characters के real renderers हैं। Definition metadata में `lp_3d_pawn_placeholder` string मौजूद है, लेकिन gameplay/Store presentation में उस resource को पढ़कर दिखाने वाला caller नहीं मिला। Metadata नाम अकेला visible issue का कारण नहीं; stable catalog/model contracts इस change में नहीं बदले।

## Implemented fix

- Shared avatar केवल actual production model से बना portrait draw करता है। पुराना avatar.draw runtime call हट गया। Cache pending/failure पर synthetic animal/circle image के बजाय textual Loading/Unavailable status है। Actual image के बिना decorative portrait ring भी नहीं बनता।
- Native launch और game preparation के दौरान selected character first, starters, फिर सभी registered IDs background portrait prewarm में हैं। No UI-thread GL rendering/network/new SDK initialization।
- Production-model 256×256 transparent portrait app-private cache में AtomicFile PNG write से सुरक्षित होता है। Memory hit उसी frame में मिलता है; disk decode अलग serialized portrait worker पर है। App package lastUpdateTime से cache generation बदलती है, reused versionCode वाले updates में भी पुराने model portraits नहीं दिखते। केवल obsolete derived portrait-cache folders cleanup होते हैं; wallet/account/assets नहीं।
- Global all-character failure switch हटाकर per-key 15-second cooldown है। View अधिकतम 3 attempts, 16-second gap के साथ retry करती है। Rebind/reattach पर recovery attempt हो सकता है। Old character callback, detached View और duplicate request guards बने हैं।
- All-model prewarm के दौरान readback के बाद GPU custom meshes release होते हैं; 8MiB bitmap cache सभी 11 natural portraits (लगभग 2.75MiB raw bitmap pixels) समा सकती है। Persist fail पर successful in-memory portrait उपयोगी रहता है। कोई PNG network से नहीं आता।

## Scope और acceptance

Normal live gameplay की actual 3D pawns/animations और geometry नहीं बदली। OpenGL runtime के वास्तव में fail होने पर classic playable board fallback अभी अलग emergency path है; उसे हटाकर game को unplayable नहीं बनाया। यह shared character portrait placeholders से अलग है। पुरानी vector renderer definitions अभी registry metadata में रह सकती हैं, लेकिन shared UI उन्हें display नहीं करती।

पहले install/cache eviction पर portrait generate होने तक brief textual loading रह सकती है। Unsupported/broken GPU पर वास्तविक model image उपलब्ध न हो तो explicit unavailable रहेगा; fake replacement दिखाने का दावा नहीं। Packaged offline static portraits अभी assets में मौजूद नहीं हैं।

Source recheck: सभी active avatar.draw/placeholder resource call scans, 25 shared-view references, 11 registry IDs, startup prewarm ordering, bitmap stale-key callbacks, disk version/corruption/write errors, per-key retry/cooldown, worker-only GL/cache IO और per-render GPU cleanup पढ़े। Whitespace साफ। User के iteration workflow अनुसार APK build, lint, test suites या connected-device run नहीं किया; actual cold/warm Store/setup/profile/live-preview visual acceptance बाकी है। चरण 9–10 pending और live prices अपरिवर्तित।
