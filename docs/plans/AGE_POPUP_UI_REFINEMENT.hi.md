# Age popup reference implementation — 29 अक्टूबर 2026

User के दो reference screenshots के अनुसार Home age/privacy popup की visual refinement।

- Existing shared plywood panel, rounded corners; centered rounded-bold “How old are you?” heading और thin decorative divider। बड़ा recessed plywood age box, initial unset dash, selected age large number।
- Native SeekBar subclass में white 34dp circular knob, 12dp RGB 3D track और दोनों तरफ continuously outward/inward animated chevrons। RGB palette splash से identical: red/yellow/green/blue। Arrow outlines wood पर contrast देते हैं। Edge padding में arrows/knob endpoints समाते हैं; slider का 72dp touch area है। Native thumb bounds से arrows position लेते हैं।
- Same platform progress-layer level से fill बदलता है; native touch/keyboard/accessibility behavior बना है। Max=100; existing deliberate age 1 endpoint handling/clamp और initial unset state वैसे ही हैं। नया custom touch age calculation नहीं बनाया।
- Continue green rounded 3D surface; उम्र चुने बिना disabled state। Privacy-policy link readable blue है; existing one-popup offline policy/back view और draft restoration बने हैं। Age bands/policy version persistence, optional Google gating और exact-age non-persistence में बदलाव नहीं।
- Source recheck: draw bounds/level, endpoint chevrons, RGB palette equality, drawable filter/alpha behavior, animation hidden/detach stopping, native selection callbacks, 0/unset→1, age100, policy/back, required dialog और save/recreation paths पढ़े। No extra SDK/init/ads/payment changes। Whitespace साफ। Build/APK, lint, test suites या device run इस iteration में नहीं किया।
- Existing accepted age के बाद popup automatically repeat नहीं होगा। Profile menu के privacy/age option से existing FamilyAgeScreen.show entry पर नया layout देखा जा सकता है; app data साफ करने की जरूरत नहीं। चरण 9–10 pending, नई prices live नहीं बदलीं।

## Slider thickness adjustment

User के निर्देश पर track की visible height 12dp→24dp (ठीक double)। Background और RGB fill दोनों के bounds/clipping/radius एक ही 24dp capsule पर हैं। 34dp knob, chevrons, 72dp touch area और age-selection logic अपरिवर्तित। Source diff/whitespace recheck किया; build/tests/device run नहीं किया।
