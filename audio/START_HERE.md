# START HERE — Character audio paste folders

सभी 13 characters के folders तैयार हैं। हर voice folder का README खोलें: उसमें 14 situations के copy-paste Firefly prompts, exact filenames और trim targets हैं। आपको audio generate/download करना, बताये नाम से save करना और सही folder में रखना है।

## Folder list

| Character | Voice prompts और paste folder | Footstep prompts और paste folder |
| --- | --- | --- |
| Dog | [voices/dog](sfx/voices/dog/README.md) | [step/dog](sfx/gameplay/movement/step/dog/README.md) |
| Goat | [voices/goat](sfx/voices/goat/README.md) | [step/goat](sfx/gameplay/movement/step/goat/README.md) |
| Duck | [voices/duck](sfx/voices/duck/README.md) | [step/duck](sfx/gameplay/movement/step/duck/README.md) |
| Cat | [voices/cat](sfx/voices/cat/README.md) | [step/cat](sfx/gameplay/movement/step/cat/README.md) |
| Donkey | [voices/donkey](sfx/voices/donkey/README.md) | [step/donkey](sfx/gameplay/movement/step/donkey/README.md) |
| Lumi Goat | [voices/goat_lumi](sfx/voices/goat_lumi/README.md) | [step/goat_lumi](sfx/gameplay/movement/step/goat_lumi/README.md) |
| Cock / Rooster | [voices/cock](sfx/voices/cock/README.md) | [step/cock](sfx/gameplay/movement/step/cock/README.md) |
| Pig | [voices/pig](sfx/voices/pig/README.md) | [step/pig](sfx/gameplay/movement/step/pig/README.md) |
| Elephant | [voices/elephant](sfx/voices/elephant/README.md) | [step/elephant](sfx/gameplay/movement/step/elephant/README.md) |
| Horse | [voices/horse](sfx/voices/horse/README.md) | [step/horse](sfx/gameplay/movement/step/horse/README.md) |
| Rabbit | [voices/rabbit](sfx/voices/rabbit/README.md) | [step/rabbit](sfx/gameplay/movement/step/rabbit/README.md) |
| Panda | [voices/panda](sfx/voices/panda/README.md) | [step/panda](sfx/gameplay/movement/step/panda/README.md) |
| Monkey | [voices/monkey](sfx/voices/monkey/README.md) | [step/monkey](sfx/gameplay/movement/step/monkey/README.md) |

## उदाहरण: Monkey के six की आवाज़

1. `audio/sfx/voices/monkey/README.md` में `six` section का prompt copy करें।
2. Firefly Generate Sound Effects में paste करके Generate करें। एक साफ short vocal gesture चुनें।
3. सही format में export/convert करके नाम रखें: `lp_vocal_monkey_six_01.wav`।
4. उसे `audio/sfx/voices/monkey/` में paste करें।
5. दूसरी अलग take हो तो `lp_vocal_monkey_six_02.wav` रखें। सिर्फ एक take भी चलती है।
6. Ctrl+Shift+B से rebuild/install करें। नया audio उसी situation में existing voice probability/settings/cooldown के अनुसार चलेगा।

## जरूरी बातें

- Empty या dummy `.wav` files नहीं बनाई गई हैं। वास्तविक audio आप generate करके रखेंगे; README files folders को Git में सुरक्षित रखती हैं।
- सभी existing audio files और filenames सुरक्षित हैं। Downloads के numeric suffix भी supported हैं।
- प्रत्येक character के लिए 14 vocal cues × 2 optional takes और 3 optional footsteps के filenames तैयार हैं।
- Normal move/turn/roll पर vocal नहीं है। Footsteps और jump अलग physical layers हैं। Head look, blink, ear/tail gestures के लिए नई आवाज़ जरूरी नहीं।
- Lumi अपनी recording को प्राथमिकता देगा और missing cue पर Goat fallback रखेगा। Donkey/Panda/Monkey अपनी recording के बिना शांत रहेंगे।
- Voice या Sound setting off, cooldown, dedupe, probability, app background/audio focus की वजह से हर event पर voice सुनना जरूरी नहीं।
- 13 × 14 = 182 first vocal takes; second takes optional हैं। पुराने चार characters की अच्छी recordings फिर से बनाने की जरूरत नहीं।
- प्राइस और पब्लिश PENDING हैं।

## नई character acting के लिए

Paw wash, ear movement, tummy gesture, clap और bystander glance के लिए अलग voice files नहीं चाहिए। इन्हें जानबूझकर silent रखा है। ऊपर वाले 14 situation prompts और footsteps ही generate करें; sound हर छोटे movement पर नहीं बजेगा। Settings में Animal voices अलग से चुन सकते हैं; Sound master mute रहेगा।
