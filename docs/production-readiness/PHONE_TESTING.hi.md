# Main LudoPaws — device acceptance

29 अक्टूबर 2026 की user requirement: एक user-facing app, package `com.rekixo.ludopaws`। Dev tools, अलग preview/QA app और demo-ad UI हटा दिए गए हैं। सभी चरण पूरे होने तक नए build, lint और test runs नहीं चलाने हैं; बीच में केवल source review, commit/push और user device feedback।

## Source लेना

```bash
git fetch origin
git switch codex/rekixo-launch-branding
git pull --ff-only origin codex/rekixo-launch-branding
```

Source pull से installed APK नहीं बदलता। नया APK तैयार होने तक पुराने APK में हटाए गए buttons फिर भी रह सकते हैं। पुराने build को नया साफ किया हुआ build बताकर install न करें।

## User का Ctrl+Shift+B shortcut

VS Code और terminal अब एक ही Node runner चलाते हैं: `scripts/android-local-testing.mjs run`। यह केवल main package बनाता है; developer UI, preview/QA package या demo ads नहीं लौटे हैं। Ctrl+Shift+B से incremental build → selected connected device पर install → cold launcher start होता है। इसमें tests/lint default से नहीं चलते। `bash scripts/run-ludoproof.sh` उसी runner का compatibility wrapper है।

```bash
node scripts/android-local-testing.mjs configure
node scripts/android-local-testing.mjs run
```

`configure` public GitHub Actions/production variables ही पढ़ता है; release signing secrets नहीं। Existing configured billing, rewarded/interstitial ads, production ad unit IDs, Google linking और Play Games IDs इसी से build में आते हैं। Incomplete config पर features चुपचाप बंद करके APK लगाने के बजाय runner रुकता है और configure करने को कहता है। Cached config में नए flags गायब हों तो वह refresh होती है। SDK discovery Windows/Linux/macOS पर है; local.properties की अन्य settings बचती हैं। Multiple devices पर ANDROID_SERIAL चुनें। Explicit version downgrade, signature conflict, build failure और missing artifact में install/launch रुकते हैं; uninstall/data-clear नहीं होता। Public production flags बदलना, payment करना, ads दिखाना या Play upload करना इस runner का हिस्सा नहीं।

Source pull या configure से APK अपने-आप नहीं बदलता। उसके बाद user को Ctrl+Shift+B/run दबाना है। Actual auth के लिए इस machine का registered signing SHA-1 और valid Play account जरूरी हैं।

## Device install — नया main APK मिलने पर

```bash
adb devices
adb install -r android/app/build/outputs/apk/release/app-release.apk
adb shell am force-stop com.rekixo.ludopaws
adb shell am start -W -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  -n com.rekixo.ludopaws/com.rekixo.ludopaws.HomeActivity
```

Release APK को existing Play signing/update path से compatible होना चाहिए। Missing APK या `INSTALL_FAILED_UPDATE_INCOMPATIBLE` पर uninstall/data-clear न करें। Play App Signing key स्थानीय upload/debug key से अलग हो सकती है; Play-distributed internal update सही install path है। Signing और Play delivery के checks अंतिम acceptance का हिस्सा हैं।

## अभी दर्ज device स्थिति

Connected Samsung SM-A715F के सामान्य user 0 में main app, अलग preview और QA package नहीं मिले। केवल orphan `com.rekixo.ludopaws.test` instrumentation runner था; user के cleanup निर्देश पर वह हटाया गया। Main app/player data को इस cleanup ने uninstall/clear नहीं किया। नया APK build/install नहीं हुआ।

## अंतिम संयुक्त acceptance

सभी दस चरणों के बाद unit/server tests, source gates, lint, signed build और real-device recheck चलेंगे। Google Play Games की privacy eligibility, signing SHA/package configuration और Google-controlled banner अलग verify होंगे। Billing, pending/cancel/refund और server-verified ad rewards अंतिम monetization चरण में जाँचे जाएँगे। License-test transaction को real-money payment का प्रमाण न कहें।

पुराने users के account/profile/wallet/saved-match migration readers production update compatibility का हिस्सा हैं। इन्हें developer interface समझकर हटाना data loss कर सकता है।
