# v11 pricing — release के साथ लागू होने वाला plan

स्थिति: **तैयार draft, Play Console में लागू नहीं**। User का निर्णय: final v11 release के साथ prices बदलें। Console product prices बदलने से पुराने live clients को भी नया price मिल सकता है; इसे केवल v11 APK तक सीमित न मानें।

| Stable product ID | मौजूदा benefit | Target USD |
|---|---|---:|
| remove_ads | Remove Ads + 5 bonus Gems | 1.00 |
| gems_300 | 300 Gems | 0.50 |
| gems_1500 | 1,200 Gems | 1.50 |
| gems_3500 | 3,000 Gems | 2.50 |
| gems_20000 | 7,500 Gems | 5.00 |
| gems_40000 | 16,000 Gems | 10.00 |
| gems_200000 | 35,000 Gems | 20.00 |

बड़े packs में क्रमशः 800, 1,200, 1,500, 1,600, 1,750 Gems/USD; Starter में 600 Gems/USD। IDs में पुराने अंक quantity का प्रमाण नहीं हैं। Android/server के grants समान रखे हैं; पुराने buyers की entitlement/Gems कम नहीं होते। Cosmetic direct-purchase products का pricing इस plan में नहीं बदलता।

Canonical machine-readable targets: `pricing-plan.json`। Client में future prices hard-code नहीं हैं; current Play ProductDetails.formattedPrice ही ग्राहक को दिखता है। Missing catalog में Price unavailable/retry रहेगा।

Repository root से offline preparation:

```bash
node scripts/prepare-v11-pricing.mjs > /tmp/ludopaws-v11-pricing-draft.json
```

यह command client/server grant mapping और complete core-product coverage जाँचकर Google conversion request bodies बनाती है। कोई build, test suite या Console edit नहीं चलाती।

Release operator के private environment में Android Publisher scope वाला access token उपलब्ध हो तो:

```bash
node scripts/prepare-v11-pricing.mjs --preview > /tmp/ludopaws-v11-regional-preview.json
```

Token `ANDROID_PUBLISHER_ACCESS_TOKEN` environment variable से पढ़ा जाता है; token file/repository/output में नहीं लिखा जाता। Script में केवल pricing conversion utility है; product price update/activation API नहीं है। Live preview इस iteration में नहीं चला है।

Worldwide final review में हर supported region के converted currency, taxes, rounding, minimum/maximum permitted price और उपलब्ध purchase option जाँचें। USD 0.50 हर देश में उसी numeric amount में नहीं दिखेगा। Google conversion का input tax-exclusive reference है; जहाँ लागू हो Console का tax treatment और regional final totals review करें। Conversion preview किसी product की existing availability या minimum price acceptance का प्रमाण नहीं है। गलत/unsupported region को चुपचाप USD fallback से न भरें। Release के समय fresh preview लें; archived conversion rates को current न मानें।

Final v11 gate: चरण 9 में payment/grant audit → चरण 10 में combined checks और signed Play/internal device acceptance → current Console products/prices export सुरक्षित करें → fresh regional preview और review → release के साथ Console pricing apply/verify → Play catalog में actual localized prices और exact grants जाँचें। यह document Console application को अभी completed नहीं कहता।

Official API: [convertRegionPrices](https://developers.google.com/android-publisher/api-ref/rest/v3/monetization/convertRegionPrices), [one-time product regional configuration](https://developers.google.com/android-publisher/api-ref/rest/v3/monetization.onetimeproducts)।
