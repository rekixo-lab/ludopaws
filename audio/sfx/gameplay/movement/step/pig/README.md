# Pig — landing / footstep

यह physical footfall है, animal voice नहीं। इस folder में तीन अलग takes रख सकते हैं:

- `lp_sfx_step_pig_01.wav`
- `lp_sfx_step_pig_02.wav`
- `lp_sfx_step_pig_03.wav`

Firefly prompt:

```text
Pig, a small cloven hoof landing, soft weighty double tap on a wooden game board. One isolated footfall, dry close recording, very short natural decay. No animal vocal, music or background ambience.
```

Final target लगभग 40–75 ms। लंबी clip से एक साफ landing transient trim करें। पहले `_01` काफी है; `_02` और `_03` optional natural variations हैं। WAV PCM 16-bit mono 44.1/48 kHz रखें। Jump take-off इसमें न मिलाएँ।

ये takes missing हों तो existing shared step fallback रहेगा। हर hop के लिए shared jump अलग है: `audio/sfx/gameplay/movement/jump/lp_sfx_jump_01.wav`।
