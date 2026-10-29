# चरण 5–8 — implementation और code-level review

29 अक्टूबर 2026। Branch `codex/rekixo-launch-branding`। हर चरण का अलग commit, फिर संयुक्त source review। APK build, lint और test suites इस iteration में नहीं चलाने हैं। Payment का final audit चरण 9 में है। User ने नई Play prices को **v11 release के साथ** लागू करने को चुना है; अभी live Console prices नहीं बदलनी हैं।

## चरण 5 — Navigation/shared UI

- Home header, Share, Store carousel, recovery entry और game-mode routing में shared navigation guard: transition के दौरान repeated taps से duplicate screens नहीं खुलतीं। Back/foreground return पर guard साफ होता है; failed intent पर भी साफ होकर readable message आता है। कोई नया task flag/singleTop mode जोड़कर existing game/session intent semantics नहीं बदलीं।
- Home के delayed referral/character/audio preparation को नाम वाला एक cancellable runnable बनाया है। Pause पर पुराने jobs cancel होते हैं, हर resume पर नए callbacks का ढेर नहीं लगता।
- Repeated startup/connectivity/resume HUD refresh एक frame में coalesce है; profile/wallet reads हर signal पर अलग-अलग नहीं दोहरतीं।
- Recheck: new helper imports और all affected call sites, failure/retry, Back/resume reset, pause/destroy cleanup और existing privacy/PGS flows पढ़े। Diff whitespace साफ। Runtime timing या device smoothness का measured दावा नहीं; final device checks बाकी हैं।

## चरण 6 — Store/catalog UX

- Catalog refresh coalesces; loading/stale/failure का accessible same-screen status और retry है। Same tab पर tap से failed catalog तथा existing Billing connection फिर query हो सकती है। Late/bootstrap responses stopped Activity या पुराने generation/account पर apply नहीं होते।
- चुना tab, per-tab scroll, featured character और purchase-item draft rotation/process restoration में बचते हैं। Draft restore checkout अपने-आप नहीं खोलता; selected retry action user का नया explicit tap लेता है। Account बदल चुका हो तो पुरानी selection को दोबारा चुनना पड़ता है।
- Play catalog requested SKU न लौटाए तो busy state clear होती है और exact selected item retry मिलता है। Availability request हर attempt पर ताज़ा है। Actual price केवल Play formattedPrice है; missing price को dummy USD/0 price नहीं दिया गया।
- Native pending/completed purchase delivery शुरू हो तो उस owner/product की retry-buy draft हटती है, ताकि recovery के नाम पर दूसरा payment request न बने। Recreated Activity intent से Remove Ads sheet दोबारा स्वतः नहीं खुलती। Existing verification/token logic चरण 9 में रहेगा।
- Cosmetic पर current catalog/content का check entry और spend confirmation के बाद है। Changed/unsupported offer पर nothing-spent message और refresh है। Owned/offline-free paths बने हैं; single Play-product unlock वही mapped product route इस्तेमाल करता है।
- Recheck में missing SKU, stale catalog, repeated tap, changed account, rotation during tab switch, provider pending/delivery, saved intent और stopped callbacks के branches पढ़े। एक ही lifecycle method होने का scan तथा diff whitespace साफ। नया build/tests/lint नहीं चला है।
