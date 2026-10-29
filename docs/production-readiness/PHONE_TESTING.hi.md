# Main LudoPaws — device acceptance

29 अक्टूबर 2026 की user requirement: एक user-facing app, package `com.rekixo.ludopaws`। Dev tools, अलग preview/QA app और demo-ad UI हटा दिए गए हैं। सभी चरण पूरे होने तक नए build, lint और test runs नहीं चलाने हैं; बीच में केवल source review, commit/push और user device feedback।

## Source लेना

```bash
git fetch origin
git switch codex/rekixo-launch-branding
git pull --ff-only origin codex/rekixo-launch-branding
```

Source pull से installed APK नहीं बदलता। नया APK तैयार होने तक पुराने APK में हटाए गए buttons फिर भी रह सकते हैं। पुराने build को नया साफ किया हुआ build बताकर install न करें।

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
