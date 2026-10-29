# चरण 2 — Rekixo launch branding

Android launcher का system launch अब Rekixo wordmark दिखाता है। Android 9–11 का starting window और Android 12+ का splash icon एक ही native VectorDrawable इस्तेमाल करते हैं। Original website SVG का पूरा wordmark aspect ratio के साथ Android के safe circle में रखा है; launcher/app icon Ludo Paws ही रहता है। कोई extra Activity, minimum delay, network request या custom launch timer नहीं जोड़ा।

Game-logo splash/loading, automatic guest coordinator और age/privacy popup अगले चरणों के काम हैं। इस change से उनका implementation पूरा नहीं माना गया है। Native launch cold/warm start पर OS के अनुसार दिखता है; already-running app के hot resume पर दोबारा दिखना जरूरी नहीं।

Source/provenance: SOURCE.md। Resource generator pinned upstream blob जाँचता है और --check में generated vector consistency verify करता है। SVG की छोटी drop-shadow native vector में omit है; path geometry, colors/gradients और shine opacity रखे हैं।

## Build और real-device preview

Repo root से:

```bash
python3 scripts/generate-rekixo-launch.py --check
./android/gradlew -p android :app:assembleDebug -PLUDOPROOF_BILLING_ENABLED=false -PLUDOPROOF_ADS_ENABLED=false
adb devices
adb install -r -t android/app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.rekixo.ludopaws
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.rekixo.ludopaws/com.rekixo.ludopaws.HomeActivity
```

एक ही package `com.rekixo.ludopaws` है; अलग developer/QA app नहीं है। ऊपर commands अंतिम build/device acceptance के लिए हैं; वर्तमान चरणों में इन्हें बार-बार नहीं चलाना है। Debug और release में user-facing UI एक है, signing अलग हो सकती है। Signature mismatch पर existing app uninstall/data-clear न करें।

भौतिक launch test: app drawer से Ludo Paws icon tap करें; cold start में पूरा Rekixo wordmark, सही proportions, dark background और कोई edge clipping/white flash देखें। Hot resume में forced branding delay नहीं होना चाहिए। Android 9–11 और 12+ पर यह checklist अलग चलाएँ। Stage-2 source/build checks वास्तविक device visual acceptance का विकल्प नहीं हैं।

इस चरण के logs और resource preview chat workspace के `audit-2026-10-29-launch-branding/` में हैं; वे इस source change का runtime payload नहीं हैं।
