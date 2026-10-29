# Splash UI refinement — 29 अक्टूबर 2026

- Splash में Home hero bitmap लगाने के सभी call sites हटे। Existing `ic_ludo_paws` game-logo asset तुरंत center में दिखता है। Home hero की existing background preparation/cache अब भी Home readiness के लिए है; Home artwork या timing contract नहीं बदला।
- Title में rounded bold font, warm gold text, spacing/shadow; caption medium font। Gradient navy background existing game palette से मेल खाता है।
- Stock horizontal ProgressBar के बजाय gold-rimmed blue/cyan/mint track, gloss और चार Ludo-color markers। Moving highlight indeterminate work दर्शाता है; completion केवल existing localReady/account.settled से है। कोई invented percentage या minimum delay नहीं। Animator attach/visible lifecycle पर चलता है, detach/background/error/completion पर cancel होता है।
- Progress track के नीचे approx 1.5 inch (`COMPLEX_UNIT_IN`, device-reported display metrics) का spacer। Landscape/split-screen में available height के 35% तक cap है, ताकि logo/retry/exit का स्थान बचा रहे। System bar/cutout insets अलग सुरक्षित हैं; OEM physical DPI accuracy पर exact measured inch निर्भर है।
- Rekixo wordmark Android <=30 में 180→216dp (20%)। Android 12+ में whole-dp maximum mask-safe 187dp override। वहाँ full 20% नहीं: native circular mask wide wordmark काटेगा। Generator दोनों resources को same source से बनाता है। Official dimensions: https://developer.android.com/develop/ui/views/launch/splash-screen ।
- Recheck: removed bitmap API के callers, actual readiness/error/retry branches, cached Home preparation, center FIT_CENTER sizing, short-window gap, view/animator lifecycle, vector XML और generated resource consistency पढ़े/जाँचे। Whitespace साफ। User instruction अनुसार Gradle/APK build, lint, tests या device execution नहीं चले। Final visual acceptance user के device पर बाकी है।
- चरण 9 और 10 pending; नई prices live नहीं बदलीं। इस change में AdMob/payment configuration नहीं बदला।

## Device feedback fix और physical-device verification

User ने square corners और bottom-touching track report किया और इस बार explicitly connected device पर जाँचने को कहा।

- Gap को `onSizeChanged` से निकालकर `onMeasure` में child measurement से पहले तय किया। Weighted column के initial zero-height spacer पर निर्भरता हटती है।
- RoundedSplashLogo actual FIT_CENTER drawable bounds को imageMatrix से map करके 26dp rounded clip देता है; पूरे letterboxed ImageView को round करके artwork square नहीं छोड़ा जाता।
- इसी source का normal main-app debug build सफल; connected Samsung SM-A715F (1080×2400, density 420) पर `install -r` सफल और cold launch चला। App uninstall/data-clear नहीं किया। Existing account/age choices इस visual check के लिए नहीं बदलीं।
- Real-device 8-second cold-launch recording से splash frames inspect किए। Logo के चारों rounded corners स्पष्ट हैं। Frame 14 में gold track bounds x=66..1013, y=1754..1809; visible track के नीचे screen edge तक 590px खाली जगह है। Home खुलना भी recording में दिखा। यह reported physical DPI से approx inch placement है, ruler-based measurement नहीं।
- Temporary evidence: `/tmp/ludopaws-splash-device-review/launch.mp4`, `frame-14.png`। Recording/build visual acceptance के लिए हैं; full unit/instrumentation/lint suite नहीं चलाई गई। चरण 9–10 और live-price decisions अपरिवर्तित हैं।

## Relaxed timing — नवीन user requirement

यूज़र ने तेज़ launch/splash को अब जानबूझकर readable बनाने को कहा: लगभग एक-एक सेकंड। यह पहले के no-minimum-delay निर्णय को बदलता है।

- पहली startup flow में Activity decor का bounded pre-draw gate native Rekixo launch को लगभग 1000ms का minimum देता है। कोई Thread.sleep/main-thread blocking नहीं; वास्तविक preparation इस दौरान जारी है। OS/process startup overhead अलग होने से native screen का exact total time device पर निर्भर है।
- Game splash का minimum 1000ms उसके वास्तविक first dispatchDraw के बाद शुरू होता है। Account/artwork पहले ready हों तब भी Home इस interval से पहले नहीं खुलता। Unknown work का existing 3000ms account grace और local watchdog/error/retry contract बरकरार है। Ready state का interval fake progress नहीं भरता।
- Home पहले दिख चुका हो तो saved-state/process session branch native gate/custom splash delay bypass करती है। सामान्य foreground resume और दूसरे page से Back पर startup sequence नहीं दोहरती। Timer stop/destroy cleanup और resume re-evaluation हैं; retry को नया splash interval मिलता है।
- Source recheck: first-frame gate, cached artwork, settled/slow/offline account, grace/watchdog, retry, Home restoration और lifecycle paths पढ़े; whitespace साफ। इस timing-only iteration में नया build/device recording या test suite नहीं चला है। पिछली device recording rounded-logo/spacing fix की है, इस नए timing की acceptance नहीं।

## Logo size refinement

User के नवीन निर्देश पर game-splash logo width और horizontal padding दोनों आधे किए (300dp container →150dp; 12dp horizontal inset →6dp)। Portrait में actual visible artwork 276dp→138dp है। Actual artwork clipping radius 26dp→36dp बढ़ा; bounds clamp बना है। Rekixo native branding, progress placement और one-second timing इस change में नहीं बदले। Source diff/whitespace recheck किया; नया build/device run/tests नहीं चलाए।
