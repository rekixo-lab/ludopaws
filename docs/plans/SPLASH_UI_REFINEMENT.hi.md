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
