# एक main app — cleanup

29 अक्टूबर 2026। User ने केवल actual user-facing LudoPaws रखने और Dev tools, अलग preview/QA/staging/obsolete flows हटाने को कहा है। Branch `codex/rekixo-launch-branding` है।

## Source से हटाया

- Home और Settings के developer entry points; Home में अब सभी build types पर Rate on Google है।
- DeveloperToolsActivity, उसका debug manifest और standalone CharacterPreviewActivity। Store/game के सामान्य player previews अलग user feature हैं और जारी हैं।
- Separate application IDs/suffixes, developer/QA build switches और QA resources। Main package केवल `com.rekixo.ludopaws` है। Android के standard debug/release signing/build types technical packaging के लिए हैं; उनका player UI अलग नहीं है।
- Developer dice equipment override और debug-only future character roster preview।
- Demo-ad build switch, TEST AD labels, fake demo reward path तथा server availability/verification को bypass करने वाले debug branches। असली ads पर server-side verification और existing consent/privacy treatment लागू रहता है। Monetization flags/payment phase में कोई enable/deploy नहीं किया गया।
- Multiple-mode local testing helper और उसके पुराने operational instructions। Current main-app device instructions update हैं।

## Legacy और production compatibility

अलग staging runtime/config नहीं मिला। Cloudflare production Worker, उसके Durable Objects और migrations existing live backend हैं; उन्हें staging समझकर नहीं हटाया गया। पुराने account/profile/wallet/saved-match readers तथा wire-ID aliases installed users के update/data recovery के लिए हैं। इन्हें हटाने से progress/entitlements या पुराने live clients टूट सकते हैं, इसलिए data compatibility code रखा गया है। Test suites भी अंतिम verification के लिए रखी हैं; उन्हें इस चरण में चलाया नहीं गया।

## Phone और verification स्थिति

Connected Samsung SM-A715F के सामान्य user 0 में main/preview/QA app नहीं मिला। केवल orphan instrumentation runner `com.rekixo.ludopaws.test` मिला; उसी package को user cleanup के अंतर्गत हटाया गया और command ने Success लौटाया। Main app/player data इस कार्रवाई में uninstall/clear नहीं हुआ।

केवल source/manual reference review किया गया है। User के निर्देश के अनुसार tests, lint, APK build, instrumentation और backend deployment नहीं चले। नया cleaned APK install नहीं हुआ। पहले का generated debug APK Dev tools वाला था; stale install से बचने के लिए इस checkout का वह generated APK/output metadata हटाया गया है। नया compatible signed main build अंतिम build/acceptance में बनेगा।


## बाद का feature restoration update

Latest main में मौजूद नए production features इस branch में अलग merge किए गए हैं। Multiple-mode helper की जगह अब `android-local-testing.mjs` का **single main-app runner** है; केवल configure/run, कोई अलग developer/QA app या demo-ad mode नहीं। यह पुराने shortcuts के false-default feature flags की समस्या ठीक करता है और published configuration पढ़ता है। Latest integration report: [FEATURE_RESTORE_AUDIT.hi.md](FEATURE_RESTORE_AUDIT.hi.md)।
