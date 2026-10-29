# चरण 1–3 recheck और चरण 4 — Home age/privacy

29 अक्टूबर 2026। Branch `codex/rekixo-launch-branding`। यह implementation और code-level review report है; नए source के build/lint/unit/device tests इस iteration में नहीं चलाए गए हैं। User ने सभी दस चरणों के बाद संयुक्त verification तय किया है। पहले की 523-tests/build evidence इस source की verification नहीं है।

## पहले तीन चरणों में review और fixes

- Rekixo launch के source/provenance, native themes, manifest launcher और fast splash route देखे। Branding files नहीं बदलीं; user ने पहले दोनों screens दिखने और गति स्वीकार करने की पुष्टि की थी। कोई artificial minimum delay नहीं जोड़ा गया।
- Application तथा splash से PGS initialization/status wait हटे। Splash अब केवल game preparation/session readiness पर है। PGS failure या provider timeout Home को नहीं रोकते।
- Restored Activity की पुरानी elapsed timestamp तुरंत watchdog error दे सकती थी। नए artwork काम का नया foreground budget है; background समय preparation failure नहीं बनता।
- Home artwork एक bounded process cache से reuse होता है; PGS के internal Home recreation पर दोबारा bitmap decode नहीं चाहिए। Optional board/yard prewarming भी प्रत्येक Home recreation पर दोहराने के बजाय process में पहली Home पर चलता है।
- पुराने remote match का mode अपठनीय हो तो अब save-owner step fail होता है; credentials silently clear करके account bootstrap नहीं चलता। Existing pending-owner guard बना है। Recovery error Home के privacy popup को नहीं रोकता।
- SDK 22.1.0 की cached AAR classes का read-only bytecode review किया: automatic authenticator अगले `onActivityCreated` पर शुरू होता है, जबकि `isAuthenticated()` status task लौटाता है। Home बनने के बाद सिर्फ initialize + status query से पहला attempt pending रह सकता था।
- Public APIs से SDK initialize होने के बाद Home का एक guarded internal recreation जोड़ा है। Application-owned flags और Home-ready saved/process state दूसरा splash या age popup बनने से रोकते हैं। Coalesced request के बीच background/foreground वापसी पर deferred bootstrap retry हो सकता है। Explicit Profile sign-in स्वयं authentication शुरू करता है और उसे recreation नहीं चाहिए। SDK internals runtime में call/reflection से इस्तेमाल नहीं होते। Physical-device flicker/banner acceptance फिर भी अंतिम जाँच में बाकी है।

आधिकारिक API संदर्भ: [PlayGamesSdk.initialize](https://developers.google.com/android/reference/com/google/android/gms/games/PlayGamesSdk), [PGS Android authentication](https://developer.android.com/games/pgs/android/android-signin)। SDK version बदलने पर lifecycle assumption फिर देखें।

## लागू popup

Home तैयार होते ही एक restorable dialog: **How old are you?**, शुरू में `—`, neutral slider (1–100), Continue और Privacy Policy footer। कोई default adult age नहीं। पहली position पर deliberate tap age 1 चुनता है; unset को 0 उम्र दिखाकर submit नहीं किया जाता। Native slider keyboard/TalkBack actions रखता है; content labels/live number और 48dp controls हैं। Number box large font पर बढ़ सकती है और dialog scrollable/bounded है।

Continue valid selection तक disabled है। केवल band और policy version (`entry_policy_version=1`) SharedPreferences में atomically commit होते हैं। Exact number केवल temporary dialog draft/saved-instance state है, profile/account/API में नहीं भेजा जाता। Save fail पर popup खुला रहता है, retry दिया जाता है, in-memory preference mutation rollback होती है और Google access बंद रहता है। यह independent age verification या advertising/parental consent नहीं है।

Privacy Policy link उसी dialog में bundled full policy दिखाता है, offline भी। Back से number draft बना रहता है। Online policy अलग browser में वैकल्पिक रूप से खुलती है; browser absent/blocked हो तो local policy उपलब्ध है। External page खोलना onboarding complete नहीं करता।

पुराना automatic Got it हटा है। Settings में user द्वारा खोली जाने वाली Help रहती है, उसका closing action अब Close है। Home के underlying touch/keyboard/accessibility controls choice pending रहने तक बंद रहते हैं; dialog का अलग window accessible है।

नई installation और पुराने band-only installation दोनों नया versioned popup देखते हैं। Save के बाद दोबारा हर launch पर popup नहीं आता। Profile → Age and privacy से उसी neutral UI में choice बदली जा सकती है। Native FragmentManager draft और policy view restore करता है; एक tag और deduplicated callbacks duplicate dialogs रोकते हैं। Dialog close से पहले callbacks capture होते हैं; destroy/detach पर पुराने Activity callbacks साफ होते हैं। Release में reflected no-arg fragment constructor का keep rule है।

## Play Games timing

Unknown/younger privacy में कोई नया Google SDK initialization नहीं। Existing conservative adult-band permission सफलतापूर्वक save होने पर Home flow PGS शुरू करता है; लौटते permitted user के लिए Home पर automatic check है। Current age gate Google की universal under-18 prohibition का दावा नहीं। Missing profile/Play services, offline और provider failure में fake connected या forced Google-save नहीं है। Profile के late PGS callbacks privacy change के बाद पुराना connected/name status वापस नहीं लगाते। PGS identity को wallet account में नहीं बदला गया।

Google native welcome banner Google नियंत्रित करता है। उसे custom banner बनाकर simulate नहीं किया गया। Code-level lifecycle handling को वास्तविक banner success का प्रमाण नहीं कहा जा रहा है।

## दोबारा देखे गए source paths

पहले pass में flow/call sites, दूसरे pass में save/dismiss/restore और SDK lifecycle, फिर अंतिम pass में references, accessibility gate, small-screen scroll, shrinking constructor और policy/spec alignment देखे गए। Age unset/1/100, child/adult, existing choice, corrupted preference, save false, repeated Continue, browser return, rotation/process restoration, stopped/destroyed host, PGS exception/timeout और pending-match-owner failure के branches पढ़े गए। ये **manual code review** cases हैं; executed runtime tests नहीं। Future source gates की obsolete list-dialog/startup expectations नई requirement से अपडेट हैं; gates अभी नहीं चलाए गए।

## बाकी acceptance

अंतिम combined build में compilation/lint/tests और signed-release restoration verify करनी है। Real phone पर slider/TalkBack/font scaling, popup alignment, Home recreation appearance, true Google banner, offline/reconnect और policy browser return जाँचना बाकी है। इस iteration ने APK build/install, account switch, payment, ad request, age entry on phone, backend deploy या Play release नहीं किया। Online privacy HTML केवल branch source में update है; live website publish नहीं किया गया। अगला मुख्य implementation चरण 5 है।

Local repository में:

```bash
git pull --ff-only origin codex/rekixo-launch-branding
```

User device build/install के लिए existing Ctrl+Shift+B task है; agent ने इसे इस iteration में नहीं चलाया है।
