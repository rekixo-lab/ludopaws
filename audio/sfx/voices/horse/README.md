# Horse — voice generation and paste guide

Character ID: `horse`. इस folder में ही generated audio paste करें। नीचे हर sound का exact filename और copy-paste prompt है।

A horse voice combining light whinnies, nickers and snorts; avoid galloping or tack noise in vocal files.

## जल्दी शुरू करें

- पहले `six`, `captured`, `idle` के prompts से voice identity सुनें। फिर बाकी बनाएँ।
- हर situation के लिए पहले `_01.wav` रखें। दूसरी अलग अच्छी take हो तो `_02.wav` रखें। `.ogg` भी supported है।
- नए download का सही नाम नीचे के table से रखें। Existing recordings या उनके `(1)`/`(2)` suffix वाले नाम न बदलें; वे भी supported हैं।
- नीचे की duration final trimmed asset का target है। Firefly ज्यादा लंबी clip दे तो एक साफ gesture trim करें। सिर्फ extension बदलकर format convert नहीं होता।
- Voice में footstep, impact, music और ambience न मिलाएँ। WAV PCM 16-bit mono, 44.1/48 kHz preferred है।

## Files यहाँ रखें

| Situation | First take | Optional second take |
| --- | --- | --- |
| छह आने पर | `lp_vocal_horse_six_01.wav` | `lp_vocal_horse_six_02.wav` |
| गोटी yard से बाहर आने पर | `lp_vocal_horse_yard_exit_01.wav` | `lp_vocal_horse_yard_exit_02.wav` |
| दूसरे की गोटी काटने पर | `lp_vocal_horse_capture_01.wav` | `lp_vocal_horse_capture_02.wav` |
| अपनी गोटी कटने पर | `lp_vocal_horse_captured_01.wav` | `lp_vocal_horse_captured_02.wav` |
| safe cell पर पहुँचने पर | `lp_vocal_horse_safe_01.wav` | `lp_vocal_horse_safe_02.wav` |
| home lane में आने पर | `lp_vocal_horse_home_lane_01.wav` | `lp_vocal_horse_home_lane_02.wav` |
| एक गोटी home पहुँचने पर | `lp_vocal_horse_home_01.wav` | `lp_vocal_horse_home_02.wav` |
| चाल न मिलने / लगातार खराब roll / exact-home miss पर | `lp_vocal_horse_frustrated_01.wav` | `lp_vocal_horse_frustrated_02.wav` |
| लगातार तीसरा six आने पर | `lp_vocal_horse_third_six_01.wav` | `lp_vocal_horse_third_six_02.wav` |
| काफी देर कोई meaningful बदलाव न होने पर | `lp_vocal_horse_idle_01.wav` | `lp_vocal_horse_idle_02.wav` |
| गोटी को capture का खतरा होने पर | `lp_vocal_horse_nervous_01.wav` | `lp_vocal_horse_nervous_02.wav` |
| प्लेयर आगे होने पर | `lp_vocal_horse_proud_01.wav` | `lp_vocal_horse_proud_02.wav` |
| मैच / टीम जीतने पर | `lp_vocal_horse_victory_01.wav` | `lp_vocal_horse_victory_02.wav` |
| मैच / टीम हारने पर | `lp_vocal_horse_defeat_01.wav` | `lp_vocal_horse_defeat_02.wav` |

## Recording नहीं मिले तो क्या होगा

अपनी recording पहले चलेगी; missing cue के लिए मौजूदा fallback सुरक्षित है।

## six — छह आने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_six_01.wav`
Optional second take: `lp_vocal_horse_six_02.wav`. Final target: 0.35–0.65 s.

```text
Horse, short light whinny, bright and surprised, one quick rising phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## yard_exit — गोटी yard से बाहर आने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_yard_exit_01.wav`
Optional second take: `lp_vocal_horse_yard_exit_02.wav`. Final target: 0.25–0.50 s.

```text
Horse, soft nicker, curious and eager, one light rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## capture — दूसरे की गोटी काटने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_capture_01.wav`
Optional second take: `lp_vocal_horse_capture_02.wav`. Final target: 0.30–0.60 s.

```text
Horse, short light whinny, confident and playful, one crisp compact phrase. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## captured — अपनी गोटी कटने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_captured_01.wav`
Optional second take: `lp_vocal_horse_captured_02.wav`. Final target: 0.25–0.55 s.

```text
Horse, brief nasal snort, briefly startled, one soft clipped call, not a pain cry. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## safe — safe cell पर पहुँचने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_safe_01.wav`
Optional second take: `lp_vocal_horse_safe_02.wav`. Final target: 0.30–0.60 s.

```text
Horse, soft nicker, relaxed, one gentle falling call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## home_lane — home lane में आने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_home_lane_01.wav`
Optional second take: `lp_vocal_horse_home_lane_02.wav`. Final target: 0.25–0.50 s.

```text
Horse, soft nicker, light and anticipatory, one quiet rising call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## home — एक गोटी home पहुँचने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_home_01.wav`
Optional second take: `lp_vocal_horse_home_02.wav`. Final target: 0.45–0.80 s.

```text
Horse, short light whinny, warm and pleased, two compact vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## frustrated — चाल न मिलने / लगातार खराब roll / exact-home miss पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_frustrated_01.wav`
Optional second take: `lp_vocal_horse_frustrated_02.wav`. Final target: 0.35–0.70 s.

```text
Horse, low subdued nicker, mildly disappointed, one restrained descending call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## third_six — लगातार तीसरा six आने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_third_six_01.wav`
Optional second take: `lp_vocal_horse_third_six_02.wav`. Final target: 0.35–0.65 s.

```text
Horse, brief nasal snort, mildly annoyed, two clipped vocal pulses, not aggressive. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## idle — काफी देर कोई meaningful बदलाव न होने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_idle_01.wav`
Optional second take: `lp_vocal_horse_idle_02.wav`. Final target: 0.30–0.65 s.

```text
Horse, quiet nasal breath, calm and barely audible, one small natural vocal gesture. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## nervous — गोटी को capture का खतरा होने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_nervous_01.wav`
Optional second take: `lp_vocal_horse_nervous_02.wav`. Final target: 0.35–0.70 s.

```text
Horse, brief nasal snort, hesitant, two soft uneven vocal pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## proud — प्लेयर आगे होने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_proud_01.wav`
Optional second take: `lp_vocal_horse_proud_02.wav`. Final target: 0.30–0.60 s.

```text
Horse, soft nicker, calm and confident, one rounded settled call. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## victory — मैच / टीम जीतने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_victory_01.wav`
Optional second take: `lp_vocal_horse_victory_02.wav`. Final target: 0.80–1.20 s.

```text
Horse, short light whinny, lively and upbeat, one brief phrase of two or three pulses. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

## defeat — मैच / टीम हारने पर

Paste file: `audio/sfx/voices/horse/lp_vocal_horse_defeat_01.wav`
Optional second take: `lp_vocal_horse_defeat_02.wav`. Final target: 0.55–0.90 s.

```text
Horse, low subdued nicker, subdued, one gentle falling phrase, no distress scream. One isolated natural animal vocal gesture, dry close recording, clean ending. No speech, music, background ambience or added impacts.
```

Footstep generation का अलग guide: `audio/sfx/gameplay/movement/step/horse/README.md`। Audio paste करने के बाद नया build/install जरूरी है; running APK अपने-आप नई repository files नहीं पढ़ता।
