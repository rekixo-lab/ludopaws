# LudoPaws Gems — तकनीकी Production Release Gate (28 अक्टूबर 2026)

## Code status बनाम live स्थिति

- PR #310 और #311 से 2-player **3/0**, 3-player **3/2/0** और 4-player **3/2/1/0** ranked Gems rules `main` में हैं; Team Up winning side को प्रति खिलाड़ी 3 Gems।
- प्रत्येक मैच का payment केवल authenticated server-owned MatchRoom से, account-mapped Durable Object wallet में idempotent transaction द्वारा होता है। Friends में एक rolling 24-hour window में अधिकतम 3 positive rewarded matches हैं।
- Bot/forfeit, Computer और Pass & Play को खर्च करने योग्य wallet currency देने की अनुमति नहीं। एक missing identity दूसरे खिलाड़ियों का pending credit नहीं रोकती; failed credits outbox में retry होते हैं, लेकिन reward न मिलने के लिए manual support अभी भी आवश्यक हो सकता है।
- `productionClaim: false` केवल independent certification को दर्शाता है, न कि runtime capability को।

## Operator द्वारा सुरक्षित deployment क्रम

1. GitHub `main` की **Ludo Paws CI**, **CodeQL**, **LudoProof Android Release Readiness** checks PASS हों।
2. GitHub Actions में **LudoProof Cloudflare Production** चुनें → **Run workflow** → branch **main** → Run। Feature branch से deploy न करें। यह deliberate manual dispatch है और secrets/production environment का उपयोग करता है।
3. उसी run में Wrangler deploy, /health, /ready, store flags, referral health और **production-game-smoke** पास होने चाहिए। Smoke V5 backwards compatibility तथा V6 2/3/4-player entry, server dice/replay और no-early-currency contract जाँचता है; असली wallet credit नहीं करता।
4. `https://api.ludopaws.rekixo.com/ready` में `deploymentRevision` **बिल्कुल उसी** `main` commit SHA के बराबर हो जिसे deploy किया गया है। नया signed release gate mismatched/old backend पर रुक जाएगा।
5. उसके बाद ही **LudoProof Android Signed Release** को `main` से किसी **unused** versionCode और सही versionName के साथ manually dispatch करें। Signed release में server revision match, Google/Ads/Billing flags, CI, unit tests, lint, signatures, SBOM और SHA-256 artifacts verify करें।
6. Play Console में Internal Testing/Production rollout, account/refund/AdMob approvals और वास्तविक payments पर अंतिम निर्णय इंसान लेगा। कोई automatic production rollout नहीं है।

## Owner के physical/manual acceptance tests

- अलग-अलग वास्तविक phones पर Online **2, 3 (यदि UI में उपलब्ध हो), 4** तथा Friends **2/3/4** matches खेलकर rank अनुसार wallet credit; losers को zero, reconnect, duplicate retry, quit/timeout, rematch, failed network।
- Team Up में दोनों winning teammates को +3; bots और local modes को zero spendable Gems।
- Google Play-installed signed build पर purchase → grant → restart → refund **with explicit revoke** → deduction; सिर्फ refund (बिना revoke) को deduction test न मानें।
- AdMob account/store listing review, genuine SSV reward और 10/15-rewarded-ad unlock; **test ads real wallet grant नहीं करते**।
- Google Play Console approval, age/family policy और long low-end handset/performance sessions।

## Restrictions

GitHub connector के वर्तमान उपलब्ध actions में **workflow_dispatch** नहीं है; production deployment / signed release / Google Play Console changes को कोई assistant commit या PR स्वतः live नहीं कर सकता। Manual dispatch steps ऊपर दिए हैं। जब तक live Worker revision, signed package और physical acceptance verify न हो, **public-launch-certified** न लिखें।

यह document 25 अक्टूबर की `RELEASE_STATUS.hi.md` evidence को replace नहीं करता; पुराने rc9 / review status को अकारण approved या live न मानें।
