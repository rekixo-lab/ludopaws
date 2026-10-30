# Firefly prompts — 9 characters, every supported vocal situation

प्राइस और पब्लिश PENDING हैं। यह prompt/design pack है; कोई audio अभी generate या app में activate नहीं किया गया है।

## इस्तेमाल कैसे करें

1. नीचे अपना character चुनें। हर situation के लिए दिया गया पूरा English prompt Firefly Generate Sound Effects में paste करें। उसमें character और सही vocal शब्द पहले से हैं।
2. एक बार में केवल एक vocal gesture generate करें। सभी 14 situations एक ही prompt में न डालें।
3. Duration slider में उपलब्ध सबसे छोटी उपयोगी अवधि चुनें। नीचे दी हुई durations final trimmed asset के design targets हैं, Firefly की minimum-duration guarantees नहीं। यदि 8-second clip निकलती है तो उसमें से सिर्फ एक साफ gesture trim करें।
4. पहले six, captured और idle के samples सुनकर एक consistent animal identity चुनें। उसके बाद बाकी situations उसी timbre में बनाएँ।
5. पहले _01 take बनाएँ। अच्छा होने पर उसी prompt से एक अलग natural take चुनकर _02 रखें; केवल pitch बदलकर duplicate बनाने की जरूरत नहीं।
6. Vocal में music, background ambience, footstep या capture-impact न मिलाएँ। Game इन layers को अलग समय पर बजाता है।
7. WAV PCM 16-bit mono, 44.1 या 48 kHz preferred है; OGG भी voice loader स्वीकार करता है। Leading silence trim करें, छोटी fade लगाएँ, clipping और लंबी reverb हटाएँ। Download format अलग हो तो सही conversion करें; extension बदलना conversion नहीं है।

केवल animal name बदलना हर बार पर्याप्त नहीं: rooster के लिए crow/cluck, elephant के लिए trumpet/rumble और rabbit के लिए snuffle/grunt चाहिए। नीचे ready prompts में यह फर्क शामिल है। Emotion descriptions game performance की creative direction हैं; वे wildlife behavior के वैज्ञानिक दावे नहीं हैं।

## Current code में कहाँ आवाज़ बजती है

| Cue | Situation | Final clip target | Current vocal eligibility |
| --- | --- | --- | --- |
| `six` | छह आने पर | 0.35–0.65 s | 68% |
| `yard_exit` | गोटी yard से बाहर आने पर | 0.25–0.50 s | 48% |
| `capture` | दूसरे की गोटी काटने पर | 0.30–0.60 s | 100% |
| `captured` | अपनी गोटी कटने पर | 0.25–0.55 s | 100% |
| `safe` | safe cell पर पहुँचने पर | 0.30–0.60 s | 36% |
| `home_lane` | home lane में आने पर | 0.25–0.50 s | 24% |
| `home` | एक गोटी home पहुँचने पर | 0.45–0.80 s | 100% |
| `frustrated` | चाल न मिलने / लगातार खराब roll / exact-home miss पर | 0.35–0.70 s | 34% / 100% / 48% |
| `third_six` | लगातार तीसरा six आने पर | 0.35–0.65 s | 100% |
| `idle` | काफी देर कोई meaningful बदलाव न होने पर | 0.30–0.65 s | 28%, timing/cooldown के बाद |
| `nervous` | गोटी को capture का खतरा होने पर | 0.35–0.70 s | 45% |
| `proud` | प्लेयर आगे होने पर | 0.30–0.60 s | 18% |
| `victory` | मैच / टीम जीतने पर | 0.80–1.20 s | 100% |
| `defeat` | मैच / टीम हारने पर | 0.55–0.90 s | 100% |

Eligibility का मतलब हर बार सुनाई देने की guarantee नहीं; director cooldown, priority, dedupe और audio focus भी लागू होते हैं। सामान्य turn start, roll start, low roll, normal move और only-legal-move पर अतिरिक्त animal vocal नहीं है। Idle vocal eligibility लगभग 24 seconds के meaningful inactivity के बाद शुरू होती है; हर idle animation पर आवाज़ नहीं आती।

## Universal reusable prompt

```text
{ANIMAL}, {CALL}, {DELIVERY}. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

नीचे सभी 126 combinations पहले से भर दिए हैं। सभी paste folders अब तैयार और wired हैं। हर character का local README `audio/sfx/voices/<id>/README.md` देखें; पूरी entry guide `audio/START_HERE.md` है।

## Voice loading की वर्तमान स्थिति

- सभी 13 characters के अपने voice folders recordings स्वीकार करते हैं।
- Lumi Goat की अपनी `goat_lumi` recording पहले चलेगी; missing cue पर मौजूदा Goat fallback रहेगा।
- Donkey, Panda और Monkey अपनी recording मिलने पर बोलेंगे; missing recording पर शांत रहेंगे। गलत animal fallback नहीं बजेगा।
- हर voice folder के README में filenames और prompts हैं। Empty WAV placeholders नहीं बनाए गए हैं। Audio डालने के बाद rebuild/install जरूरी है।

## Donkey — `donkey`

A rounded nasal hee-haw identity, trimmed to a short phrase; avoid a long farmyard braying sequence.

Voice source folder: `audio/sfx/voices/donkey/`

### six — छह आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_donkey_six_01.wav`.

```text
Donkey, short nasal bray, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### yard_exit — गोटी yard से बाहर आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_donkey_yard_exit_01.wav`.

```text
Donkey, soft breathy bray, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### capture — दूसरे की गोटी काटने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_donkey_capture_01.wav`.

```text
Donkey, short nasal bray, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### captured — अपनी गोटी कटने पर

Final target: 0.25–0.55 s. Suggested first take: `lp_vocal_donkey_captured_01.wav`.

```text
Donkey, brief nasal snort, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### safe — safe cell पर पहुँचने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_donkey_safe_01.wav`.

```text
Donkey, soft breathy bray, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home_lane — home lane में आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_donkey_home_lane_01.wav`.

```text
Donkey, soft breathy bray, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home — एक गोटी home पहुँचने पर

Final target: 0.45–0.80 s. Suggested first take: `lp_vocal_donkey_home_01.wav`.

```text
Donkey, short nasal bray, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_donkey_frustrated_01.wav`.

```text
Donkey, low subdued bray, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### third_six — लगातार तीसरा six आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_donkey_third_six_01.wav`.

```text
Donkey, brief nasal snort, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### idle — काफी देर कोई meaningful बदलाव न होने पर

Final target: 0.30–0.65 s. Suggested first take: `lp_vocal_donkey_idle_01.wav`.

```text
Donkey, quiet nasal breath, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### nervous — गोटी को capture का खतरा होने पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_donkey_nervous_01.wav`.

```text
Donkey, brief nasal snort, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### proud — प्लेयर आगे होने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_donkey_proud_01.wav`.

```text
Donkey, soft breathy bray, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### victory — मैच / टीम जीतने पर

Final target: 0.80–1.20 s. Suggested first take: `lp_vocal_donkey_victory_01.wav`.

```text
Donkey, short nasal bray, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### defeat — मैच / टीम हारने पर

Final target: 0.55–0.90 s. Suggested first take: `lp_vocal_donkey_defeat_01.wav`.

```text
Donkey, low subdued bray, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## Lumi Goat — `goat_lumi`

A light, curious young-goat voice; keep its timbre consistent across all takes.

Voice source folder: `audio/sfx/voices/goat_lumi/`

### six — छह आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_goat_lumi_six_01.wav`.

```text
Young goat, light wavering bleat, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### yard_exit — गोटी yard से बाहर आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_goat_lumi_yard_exit_01.wav`.

```text
Young goat, soft gentle bleat, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### capture — दूसरे की गोटी काटने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_goat_lumi_capture_01.wav`.

```text
Young goat, light wavering bleat, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### captured — अपनी गोटी कटने पर

Final target: 0.25–0.55 s. Suggested first take: `lp_vocal_goat_lumi_captured_01.wav`.

```text
Young goat, brief higher bleat, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### safe — safe cell पर पहुँचने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_goat_lumi_safe_01.wav`.

```text
Young goat, soft gentle bleat, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home_lane — home lane में आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_goat_lumi_home_lane_01.wav`.

```text
Young goat, soft gentle bleat, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home — एक गोटी home पहुँचने पर

Final target: 0.45–0.80 s. Suggested first take: `lp_vocal_goat_lumi_home_01.wav`.

```text
Young goat, light wavering bleat, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_goat_lumi_frustrated_01.wav`.

```text
Young goat, low breathy bleat, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### third_six — लगातार तीसरा six आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_goat_lumi_third_six_01.wav`.

```text
Young goat, brief higher bleat, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### idle — काफी देर कोई meaningful बदलाव न होने पर

Final target: 0.30–0.65 s. Suggested first take: `lp_vocal_goat_lumi_idle_01.wav`.

```text
Young goat, quiet nasal breath, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### nervous — गोटी को capture का खतरा होने पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_goat_lumi_nervous_01.wav`.

```text
Young goat, brief higher bleat, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### proud — प्लेयर आगे होने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_goat_lumi_proud_01.wav`.

```text
Young goat, soft gentle bleat, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### victory — मैच / टीम जीतने पर

Final target: 0.80–1.20 s. Suggested first take: `lp_vocal_goat_lumi_victory_01.wav`.

```text
Young goat, light wavering bleat, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### defeat — मैच / टीम हारने पर

Final target: 0.55–0.90 s. Suggested first take: `lp_vocal_goat_lumi_defeat_01.wav`.

```text
Young goat, low breathy bleat, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## Cock / Rooster — `cock`

Short rooster calls; reserve the crow for stronger positive beats, rather than crowing on every action.

Voice source folder: `audio/sfx/voices/cock/`

### six — छह आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_cock_six_01.wav`.

```text
Rooster, brief rising crow, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### yard_exit — गोटी yard से बाहर आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_cock_yard_exit_01.wav`.

```text
Rooster, soft rounded cluck, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### capture — दूसरे की गोटी काटने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_cock_capture_01.wav`.

```text
Rooster, brief rising crow, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### captured — अपनी गोटी कटने पर

Final target: 0.25–0.55 s. Suggested first take: `lp_vocal_cock_captured_01.wav`.

```text
Rooster, brief alert cluck, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### safe — safe cell पर पहुँचने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_cock_safe_01.wav`.

```text
Rooster, soft rounded cluck, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home_lane — home lane में आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_cock_home_lane_01.wav`.

```text
Rooster, soft rounded cluck, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home — एक गोटी home पहुँचने पर

Final target: 0.45–0.80 s. Suggested first take: `lp_vocal_cock_home_01.wav`.

```text
Rooster, brief rising crow, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_cock_frustrated_01.wav`.

```text
Rooster, low subdued cluck, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### third_six — लगातार तीसरा six आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_cock_third_six_01.wav`.

```text
Rooster, brief alert cluck, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### idle — काफी देर कोई meaningful बदलाव न होने पर

Final target: 0.30–0.65 s. Suggested first take: `lp_vocal_cock_idle_01.wav`.

```text
Rooster, quiet low cluck, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### nervous — गोटी को capture का खतरा होने पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_cock_nervous_01.wav`.

```text
Rooster, brief alert cluck, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### proud — प्लेयर आगे होने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_cock_proud_01.wav`.

```text
Rooster, soft rounded cluck, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### victory — मैच / टीम जीतने पर

Final target: 0.80–1.20 s. Suggested first take: `lp_vocal_cock_victory_01.wav`.

```text
Rooster, brief rising crow, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### defeat — मैच / टीम हारने पर

Final target: 0.55–0.90 s. Suggested first take: `lp_vocal_cock_defeat_01.wav`.

```text
Rooster, low subdued cluck, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## Pig — `pig`

Round oinks and nasal grunts; avoid piercing squeals.

Voice source folder: `audio/sfx/voices/pig/`

### six — छह आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_pig_six_01.wav`.

```text
Pig, short bright oink, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### yard_exit — गोटी yard से बाहर आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_pig_yard_exit_01.wav`.

```text
Pig, soft rounded oink, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### capture — दूसरे की गोटी काटने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_pig_capture_01.wav`.

```text
Pig, short bright oink, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### captured — अपनी गोटी कटने पर

Final target: 0.25–0.55 s. Suggested first take: `lp_vocal_pig_captured_01.wav`.

```text
Pig, brief nasal grunt, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### safe — safe cell पर पहुँचने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_pig_safe_01.wav`.

```text
Pig, soft rounded oink, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home_lane — home lane में आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_pig_home_lane_01.wav`.

```text
Pig, soft rounded oink, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home — एक गोटी home पहुँचने पर

Final target: 0.45–0.80 s. Suggested first take: `lp_vocal_pig_home_01.wav`.

```text
Pig, short bright oink, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_pig_frustrated_01.wav`.

```text
Pig, low gentle grunt, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### third_six — लगातार तीसरा six आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_pig_third_six_01.wav`.

```text
Pig, brief nasal grunt, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### idle — काफी देर कोई meaningful बदलाव न होने पर

Final target: 0.30–0.65 s. Suggested first take: `lp_vocal_pig_idle_01.wav`.

```text
Pig, quiet snuffling breath, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### nervous — गोटी को capture का खतरा होने पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_pig_nervous_01.wav`.

```text
Pig, brief nasal grunt, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### proud — प्लेयर आगे होने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_pig_proud_01.wav`.

```text
Pig, soft rounded oink, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### victory — मैच / टीम जीतने पर

Final target: 0.80–1.20 s. Suggested first take: `lp_vocal_pig_victory_01.wav`.

```text
Pig, short bright oink, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### defeat — मैच / टीम हारने पर

Final target: 0.55–0.90 s. Suggested first take: `lp_vocal_pig_defeat_01.wav`.

```text
Pig, low gentle grunt, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## Elephant — `elephant`

Recognizable trunk resonance and warm rumbles, restrained enough for a mobile game; avoid long explosive trumpeting.

Voice source folder: `audio/sfx/voices/elephant/`

### six — छह आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_elephant_six_01.wav`.

```text
Elephant, brief rounded trumpet, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### yard_exit — गोटी yard से बाहर आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_elephant_yard_exit_01.wav`.

```text
Elephant, soft mid-low rumbling vocal, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### capture — दूसरे की गोटी काटने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_elephant_capture_01.wav`.

```text
Elephant, brief rounded trumpet, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### captured — अपनी गोटी कटने पर

Final target: 0.25–0.55 s. Suggested first take: `lp_vocal_elephant_captured_01.wav`.

```text
Elephant, short breathy trumpet, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### safe — safe cell पर पहुँचने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_elephant_safe_01.wav`.

```text
Elephant, soft mid-low rumbling vocal, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home_lane — home lane में आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_elephant_home_lane_01.wav`.

```text
Elephant, soft mid-low rumbling vocal, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home — एक गोटी home पहुँचने पर

Final target: 0.45–0.80 s. Suggested first take: `lp_vocal_elephant_home_01.wav`.

```text
Elephant, brief rounded trumpet, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_elephant_frustrated_01.wav`.

```text
Elephant, low subdued rumble, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### third_six — लगातार तीसरा six आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_elephant_third_six_01.wav`.

```text
Elephant, short breathy trumpet, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### idle — काफी देर कोई meaningful बदलाव न होने पर

Final target: 0.30–0.65 s. Suggested first take: `lp_vocal_elephant_idle_01.wav`.

```text
Elephant, quiet rumbling breath, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### nervous — गोटी को capture का खतरा होने पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_elephant_nervous_01.wav`.

```text
Elephant, short breathy trumpet, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### proud — प्लेयर आगे होने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_elephant_proud_01.wav`.

```text
Elephant, soft mid-low rumbling vocal, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### victory — मैच / टीम जीतने पर

Final target: 0.80–1.20 s. Suggested first take: `lp_vocal_elephant_victory_01.wav`.

```text
Elephant, brief rounded trumpet, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### defeat — मैच / टीम हारने पर

Final target: 0.55–0.90 s. Suggested first take: `lp_vocal_elephant_defeat_01.wav`.

```text
Elephant, low subdued rumble, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## Horse — `horse`

A horse voice combining light whinnies, nickers and snorts; avoid galloping or tack noise in vocal files.

Voice source folder: `audio/sfx/voices/horse/`

### six — छह आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_horse_six_01.wav`.

```text
Horse, short light whinny, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### yard_exit — गोटी yard से बाहर आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_horse_yard_exit_01.wav`.

```text
Horse, soft nicker, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### capture — दूसरे की गोटी काटने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_horse_capture_01.wav`.

```text
Horse, short light whinny, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### captured — अपनी गोटी कटने पर

Final target: 0.25–0.55 s. Suggested first take: `lp_vocal_horse_captured_01.wav`.

```text
Horse, brief nasal snort, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### safe — safe cell पर पहुँचने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_horse_safe_01.wav`.

```text
Horse, soft nicker, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home_lane — home lane में आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_horse_home_lane_01.wav`.

```text
Horse, soft nicker, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home — एक गोटी home पहुँचने पर

Final target: 0.45–0.80 s. Suggested first take: `lp_vocal_horse_home_01.wav`.

```text
Horse, short light whinny, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_horse_frustrated_01.wav`.

```text
Horse, low subdued nicker, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### third_six — लगातार तीसरा six आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_horse_third_six_01.wav`.

```text
Horse, brief nasal snort, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### idle — काफी देर कोई meaningful बदलाव न होने पर

Final target: 0.30–0.65 s. Suggested first take: `lp_vocal_horse_idle_01.wav`.

```text
Horse, quiet nasal breath, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### nervous — गोटी को capture का खतरा होने पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_horse_nervous_01.wav`.

```text
Horse, brief nasal snort, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### proud — प्लेयर आगे होने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_horse_proud_01.wav`.

```text
Horse, soft nicker, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### victory — मैच / टीम जीतने पर

Final target: 0.80–1.20 s. Suggested first take: `lp_vocal_horse_victory_01.wav`.

```text
Horse, short light whinny, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### defeat — मैच / टीम हारने पर

Final target: 0.55–0.90 s. Suggested first take: `lp_vocal_horse_defeat_01.wav`.

```text
Horse, low subdued nicker, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## Rabbit — `rabbit`

Very restrained vocal design: breath/snuffles and tiny grunts. Do not turn the rabbit into a squeaking toy or add screams.

Voice source folder: `audio/sfx/voices/rabbit/`

### six — छह आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_rabbit_six_01.wav`.

```text
Rabbit, tiny breathy nasal grunt, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### yard_exit — गोटी yard से बाहर आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_rabbit_yard_exit_01.wav`.

```text
Rabbit, soft close snuffle, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### capture — दूसरे की गोटी काटने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_rabbit_capture_01.wav`.

```text
Rabbit, tiny breathy nasal grunt, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### captured — अपनी गोटी कटने पर

Final target: 0.25–0.55 s. Suggested first take: `lp_vocal_rabbit_captured_01.wav`.

```text
Rabbit, brief restrained nasal grunt, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### safe — safe cell पर पहुँचने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_rabbit_safe_01.wav`.

```text
Rabbit, soft close snuffle, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home_lane — home lane में आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_rabbit_home_lane_01.wav`.

```text
Rabbit, soft close snuffle, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home — एक गोटी home पहुँचने पर

Final target: 0.45–0.80 s. Suggested first take: `lp_vocal_rabbit_home_01.wav`.

```text
Rabbit, tiny breathy nasal grunt, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_rabbit_frustrated_01.wav`.

```text
Rabbit, low soft nasal grunt, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### third_six — लगातार तीसरा six आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_rabbit_third_six_01.wav`.

```text
Rabbit, brief restrained nasal grunt, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### idle — काफी देर कोई meaningful बदलाव न होने पर

Final target: 0.30–0.65 s. Suggested first take: `lp_vocal_rabbit_idle_01.wav`.

```text
Rabbit, very quiet sniffing breath, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### nervous — गोटी को capture का खतरा होने पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_rabbit_nervous_01.wav`.

```text
Rabbit, brief restrained nasal grunt, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### proud — प्लेयर आगे होने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_rabbit_proud_01.wav`.

```text
Rabbit, soft close snuffle, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### victory — मैच / टीम जीतने पर

Final target: 0.80–1.20 s. Suggested first take: `lp_vocal_rabbit_victory_01.wav`.

```text
Rabbit, tiny breathy nasal grunt, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### defeat — मैच / टीम हारने पर

Final target: 0.55–0.90 s. Suggested first take: `lp_vocal_rabbit_defeat_01.wav`.

```text
Rabbit, low soft nasal grunt, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## Panda — `panda`

A giant-panda vocal identity, not a generic bear roar. Use nasal, wavering bleats; avoid defaulting to a loud goat or roaring bear.

Voice source folder: `audio/sfx/voices/panda/`

### six — छह आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_panda_six_01.wav`.

```text
Giant panda, short wavering nasal bleat, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### yard_exit — गोटी yard से बाहर आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_panda_yard_exit_01.wav`.

```text
Giant panda, soft nasal panda bleat, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### capture — दूसरे की गोटी काटने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_panda_capture_01.wav`.

```text
Giant panda, short wavering nasal bleat, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### captured — अपनी गोटी कटने पर

Final target: 0.25–0.55 s. Suggested first take: `lp_vocal_panda_captured_01.wav`.

```text
Giant panda, brief gentle nasal huff, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### safe — safe cell पर पहुँचने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_panda_safe_01.wav`.

```text
Giant panda, soft nasal panda bleat, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home_lane — home lane में आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_panda_home_lane_01.wav`.

```text
Giant panda, soft nasal panda bleat, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home — एक गोटी home पहुँचने पर

Final target: 0.45–0.80 s. Suggested first take: `lp_vocal_panda_home_01.wav`.

```text
Giant panda, short wavering nasal bleat, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_panda_frustrated_01.wav`.

```text
Giant panda, low breathy nasal call, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### third_six — लगातार तीसरा six आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_panda_third_six_01.wav`.

```text
Giant panda, brief gentle nasal huff, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### idle — काफी देर कोई meaningful बदलाव न होने पर

Final target: 0.30–0.65 s. Suggested first take: `lp_vocal_panda_idle_01.wav`.

```text
Giant panda, quiet close sniffing breath, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### nervous — गोटी को capture का खतरा होने पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_panda_nervous_01.wav`.

```text
Giant panda, brief gentle nasal huff, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### proud — प्लेयर आगे होने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_panda_proud_01.wav`.

```text
Giant panda, soft nasal panda bleat, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### victory — मैच / टीम जीतने पर

Final target: 0.80–1.20 s. Suggested first take: `lp_vocal_panda_victory_01.wav`.

```text
Giant panda, short wavering nasal bleat, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### defeat — मैच / टीम हारने पर

Final target: 0.55–0.90 s. Suggested first take: `lp_vocal_panda_defeat_01.wav`.

```text
Giant panda, low breathy nasal call, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## Monkey — `monkey`

A small-monkey character sound, not human laughter, speech or an ape scream.

Voice source folder: `audio/sfx/voices/monkey/`

### six — छह आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_monkey_six_01.wav`.

```text
Small monkey, short lively chitter, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### yard_exit — गोटी yard से बाहर आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_monkey_yard_exit_01.wav`.

```text
Small monkey, soft rounded chatter, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### capture — दूसरे की गोटी काटने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_monkey_capture_01.wav`.

```text
Small monkey, short lively chitter, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### captured — अपनी गोटी कटने पर

Final target: 0.25–0.55 s. Suggested first take: `lp_vocal_monkey_captured_01.wav`.

```text
Small monkey, brief restrained squeaky call, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### safe — safe cell पर पहुँचने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_monkey_safe_01.wav`.

```text
Small monkey, soft rounded chatter, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home_lane — home lane में आने पर

Final target: 0.25–0.50 s. Suggested first take: `lp_vocal_monkey_home_lane_01.wav`.

```text
Small monkey, soft rounded chatter, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### home — एक गोटी home पहुँचने पर

Final target: 0.45–0.80 s. Suggested first take: `lp_vocal_monkey_home_01.wav`.

```text
Small monkey, short lively chitter, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_monkey_frustrated_01.wav`.

```text
Small monkey, low subdued chatter, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### third_six — लगातार तीसरा six आने पर

Final target: 0.35–0.65 s. Suggested first take: `lp_vocal_monkey_third_six_01.wav`.

```text
Small monkey, brief restrained squeaky call, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### idle — काफी देर कोई meaningful बदलाव न होने पर

Final target: 0.30–0.65 s. Suggested first take: `lp_vocal_monkey_idle_01.wav`.

```text
Small monkey, quiet soft chitter, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### nervous — गोटी को capture का खतरा होने पर

Final target: 0.35–0.70 s. Suggested first take: `lp_vocal_monkey_nervous_01.wav`.

```text
Small monkey, brief restrained squeaky call, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### proud — प्लेयर आगे होने पर

Final target: 0.30–0.60 s. Suggested first take: `lp_vocal_monkey_proud_01.wav`.

```text
Small monkey, soft rounded chatter, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### victory — मैच / टीम जीतने पर

Final target: 0.80–1.20 s. Suggested first take: `lp_vocal_monkey_victory_01.wav`.

```text
Small monkey, short lively chitter, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

### defeat — मैच / टीम हारने पर

Final target: 0.55–0.90 s. Suggested first take: `lp_vocal_monkey_defeat_01.wav`.

```text
Small monkey, low subdued chatter, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## Movement / landing sounds — अलग physical layer

हर move पर vocal मत बनाएँ। नीचे प्रत्येक prompt से एक छोटी landing sound लें और तीन slightly different takes `_01`, `_02`, `_03` रखें। Final footfall target लगभग 40–75 ms है; generated clip लंबी हो तो छोटा landing transient trim करें। Board movement stylized है, इसलिए ये wood-surface Foley directions हैं, wildlife recordings के दावे नहीं।

### Donkey footfall

Path: `audio/sfx/gameplay/movement/step/donkey/lp_sfx_step_donkey_01.wav`

```text
Donkey, a small cloven hoof landing, light dry double tick on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

### Lumi Goat footfall

Path: `audio/sfx/gameplay/movement/step/goat_lumi/lp_sfx_step_goat_lumi_01.wav`

```text
Young goat, a small cloven hoof landing, delicate dry double tick on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

### Cock / Rooster footfall

Path: `audio/sfx/gameplay/movement/step/cock/lp_sfx_step_cock_01.wav`

```text
Rooster, small chicken feet landing, delicate claw taps on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

### Pig footfall

Path: `audio/sfx/gameplay/movement/step/pig/lp_sfx_step_pig_01.wav`

```text
Pig, a small cloven hoof landing, soft weighty double tap on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

### Elephant footfall

Path: `audio/sfx/gameplay/movement/step/elephant/lp_sfx_step_elephant_01.wav`

```text
Elephant, one heavy padded foot landing, short soft low thud on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

### Horse footfall

Path: `audio/sfx/gameplay/movement/step/horse/lp_sfx_step_horse_01.wav`

```text
Horse, one horse hoof landing, clean light wooden clop on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

### Rabbit footfall

Path: `audio/sfx/gameplay/movement/step/rabbit/lp_sfx_step_rabbit_01.wav`

```text
Rabbit, small furry hind paws landing, very soft short pat on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

### Panda footfall

Path: `audio/sfx/gameplay/movement/step/panda/lp_sfx_step_panda_01.wav`

```text
Giant panda, one soft broad furry paw landing, rounded muted thud on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

### Monkey footfall

Path: `audio/sfx/gameplay/movement/step/monkey/lp_sfx_step_monkey_01.wav`

```text
Small monkey, small bare monkey hands and feet landing, light soft taps on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

## Jump और character movements का फर्क

- Shared jump take-off पहले से है: `audio/sfx/gameplay/movement/jump/lp_sfx_jump_01.wav`। अलग animal jump-vocal या per-character jump folders अभी wired नहीं हैं।
- Shared jump को replace करना हो तभी prompt: `A tiny soft springy hop take-off, short airy lift, dry close recording, clean ending. No voice, music or landing impact.` Final target 90–160 ms.
- Ear twitch, tail sway, head look, blink, Panda standing/four-paw transition और Monkey head-scratch के लिए अलग SFX triggers अभी नहीं हैं। हर gesture में आवाज़ जोड़ने से अनावश्यक chatter होगा; इन्हें फिलहाल शांत रखें।
- Dice roll, settle, six sparkle, capture impact, home sparkle और UI clicks मौजूदा shared physical effects हैं; हर animal के लिए इनकी नौ copies की जरूरत नहीं।

## पहले क्या generate करें

- Voice pack: 9 × 14 = **126 first takes**। हर slot का दूसरा approved take लेने पर **252 voice files**।
- Optional landing pack: 9 × 3 = **27 footfall takes**।
- शुरुआत में 9 × 3 (six, captured, idle) = **27 voice audition takes** बनाना identity चुनने के लिए काफी है। सुनकर पसंद आने के बाद पूरा pack बनाएँ।
- Firefly का output सुनकर स्वीकार करें: exact species identity, duration और emotion prompt से guaranteed नहीं होते। Rabbit को बहुत quiet रखें और अनावश्यक screaming, human laughter या spoken words वाले takes हटाएँ।

## References और code source

- Adobe: https://helpx.adobe.com/firefly/web/work-with-audio-and-video/work-with-audio/writing-effective-text-prompts-for-sound-effects-generation.html — concise auditory descriptions; one sound at a time.
- Smithsonian panda FAQ: https://nationalzoo.si.edu/animals/giant-panda-faqs — panda bleats, chirps and honks; the friendly-contact bleat informs the proposed Panda timbre.
- RSPCA rabbit behavior: https://www.rspca.org.uk/adviceandwelfare/pets/rabbits/behaviour — subtle rabbit communication; supports restrained sound direction, not a claim that rabbits vocally celebrate game events.
- Runtime mappings: `LudoPawsReactionEngine.kt`, `LudoPawsReactionAudioProfile.kt`, `LudoPawsReactionDirector.kt`, `LudoPawsCharacterAudioProfiles.kt`, `content/content-manifest.json`.
- Existing project charts: `docs/LUDO_PAWS_REACTION_AUDIO_SYSTEM.md`, `docs/LUDO_PAWS_AUDIO_REPLACEMENT_GUIDE.md`, `audio/README.md`. Older documents contain historical four-character lists; runtime manifest is the current voice-owner source of truth.
