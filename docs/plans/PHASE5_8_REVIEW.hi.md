# चरण 5–8 — implementation और code-level review

29 अक्टूबर 2026। Branch `codex/rekixo-launch-branding`। हर चरण का अलग commit, फिर संयुक्त source review। APK build, lint और test suites इस iteration में नहीं चलाने हैं। Payment का final audit चरण 9 में है। User ने नई Play prices को **v11 release के साथ** लागू करने को चुना है; अभी live Console prices नहीं बदलनी हैं।

## चरण 5 — Navigation/shared UI

- Home header, Share, Store carousel, recovery entry और game-mode routing में shared navigation guard: transition के दौरान repeated taps से duplicate screens नहीं खुलतीं। Back/foreground return पर guard साफ होता है; failed intent पर भी साफ होकर readable message आता है। कोई नया task flag/singleTop mode जोड़कर existing game/session intent semantics नहीं बदलीं।
- Home के delayed referral/character/audio preparation को नाम वाला एक cancellable runnable बनाया है। Pause पर पुराने jobs cancel होते हैं, हर resume पर नए callbacks का ढेर नहीं लगता।
- Repeated startup/connectivity/resume HUD refresh एक frame में coalesce है; profile/wallet reads हर signal पर अलग-अलग नहीं दोहरतीं।
- Recheck: new helper imports और all affected call sites, failure/retry, Back/resume reset, pause/destroy cleanup और existing privacy/PGS flows पढ़े। Diff whitespace साफ। Runtime timing या device smoothness का measured दावा नहीं; final device checks बाकी हैं।
