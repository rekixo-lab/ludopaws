extends RefCounted
## Copyright-safe fallback synthesizer, used only when no authored clip exists for a sound
## (e.g. footsteps, which have no recordings). Stylised non-verbal game sounds.
## Port of LudoPawsProceduralAudio.kt: same presets, same PCM (24 kHz mono 16-bit), same
## int32 noise generator; proven sample-for-sample against the live synth.

const SAMPLE_RATE := 24000

## Sfx preset -> [durationMs, startHz, endHz, harmonic, noise, {options}]
const SFX := {
	"CLICK": [70, 1050.0, 620.0, 0.08, 0.03, {}],
	"DICE_ROLL": [330, 190.0, 125.0, 0.12, 0.50, {"tremolo": 24.0}],
	"DICE_SETTLE": [72, 880.0, 490.0, 0.18, 0.08, {}],
	"MOVE_PAW": [75, 170.0, 105.0, 0.10, 0.22, {}],
	"MOVE_HOOF": [82, 520.0, 270.0, 0.18, 0.11, {}],
	"MOVE_WEB": [86, 310.0, 165.0, 0.10, 0.32, {}],
	"JUMP": [105, 240.0, 650.0, 0.18, 0.16, {"tremolo": 9.0}],
	"SIX_SPARK": [230, 720.0, 1180.0, 0.28, 0.015, {"double": true}],
	"YARD_EXIT": [150, 360.0, 690.0, 0.24, 0.05, {}],
	"CAPTURE_IMPACT": [210, 180.0, 72.0, 0.55, 0.32, {"tremolo": 6.0}],
	"SAFE_SHIMMER": [300, 620.0, 920.0, 0.36, 0.01, {"vibrato": 4.0, "depth": 0.02}],
	"HOME_LANE": [330, 460.0, 760.0, 0.24, 0.01, {"double": true}],
	"HOME_SPARKLE": [520, 540.0, 1080.0, 0.34, 0.01, {"tremolo": 5.0, "double": true}],
	"FAIL_SOFT": [220, 330.0, 165.0, 0.18, 0.04, {}],
	"EXACT_HOME_MISS": [260, 460.0, 170.0, 0.23, 0.07, {"tremolo": 5.0}],
	"THIRD_SIX": [320, 360.0, 92.0, 0.38, 0.16, {"tremolo": 13.0}],
	"VICTORY": [720, 480.0, 1040.0, 0.36, 0.01, {"vibrato": 5.5, "double": true}],
	"DEFEAT": [560, 360.0, 145.0, 0.30, 0.03, {"vibrato": 3.2}],
}

## LudoPawsProceduralVocal preset -> same layout.
const VOCAL := {
	"DOG_YIP": [210, 470.0, 710.0, 0.30, 0.05, {"tremolo": 5.0, "double": true}],
	"DOG_WHINE": [370, 520.0, 760.0, 0.18, 0.02, {"vibrato": 6.5, "depth": 0.035}],
	"DOG_RUFF": [230, 190.0, 145.0, 0.55, 0.20, {"tremolo": 12.0}],
	"GOAT_BLEAT": [390, 290.0, 320.0, 0.42, 0.04, {"vibrato": 12.5, "depth": 0.075, "tremolo": 8.5}],
	"GOAT_SOFT_BLEAT": [360, 250.0, 225.0, 0.34, 0.035, {"vibrato": 8.0, "depth": 0.05}],
	"RABBIT_SNUFFLE": [180, 220.0, 160.0, 0.10, 0.30, {"tremolo": 16.0, "double": true}],
	"HORSE_NEIGH": [550, 680.0, 310.0, 0.58, 0.06, {"vibrato": 13.0, "depth": 0.12, "tremolo": 9.0}],
	"HORSE_SNORT": [240, 165.0, 105.0, 0.24, 0.38, {"tremolo": 24.0, "double": true}],
	"ELEPHANT_TRUMPET": [560, 340.0, 610.0, 0.72, 0.12, {"vibrato": 9.0, "depth": 0.09, "tremolo": 5.0}],
	"ELEPHANT_RUMBLE": [430, 105.0, 80.0, 0.55, 0.10, {"vibrato": 6.0, "depth": 0.05, "tremolo": 7.0}],
	"PIG_OINK": [270, 185.0, 125.0, 0.65, 0.15, {"vibrato": 17.0, "depth": 0.08, "tremolo": 11.0, "double": true}],
	"PIG_SQUEAL": [330, 760.0, 1100.0, 0.40, 0.06, {"vibrato": 15.0, "depth": 0.065, "tremolo": 8.0}],
	"COCK_CROW": [560, 620.0, 980.0, 0.55, 0.07, {"vibrato": 7.0, "depth": 0.10, "tremolo": 6.0, "double": true}],
	"COCK_CLUCK": [180, 580.0, 270.0, 0.50, 0.09, {"tremolo": 13.0, "double": true}],
	"DUCK_QUACK": [185, 720.0, 330.0, 0.62, 0.18, {"tremolo": 16.0}],
	"DUCK_SOFT_QUACK": [205, 480.0, 285.0, 0.48, 0.11, {"tremolo": 11.0}],
	"CAT_CHIRP": [205, 690.0, 980.0, 0.22, 0.015, {"vibrato": 8.0, "depth": 0.025, "double": true}],
	"CAT_MEW": [360, 510.0, 430.0, 0.25, 0.015, {"vibrato": 5.0, "depth": 0.045, "peak": 790.0}],
	"CAT_PURR": [430, 82.0, 86.0, 0.65, 0.055, {"tremolo": 25.0}],
	"CAT_HUFF": [155, 175.0, 135.0, 0.10, 0.45, {"tremolo": 7.0}],
}

static var _cache := {}

## Cached AudioStreamWAV for an Sfx preset (playSfx).
static func sfx_stream(preset: String, rate := 1.0) -> AudioStreamWAV:
	return _stream("sfx", SFX, preset, rate)

## Cached AudioStreamWAV for a vocal preset (playVocal).
static func vocal_stream(preset: String, rate := 1.0) -> AudioStreamWAV:
	return _stream("vocal", VOCAL, preset, rate)

static func _stream(kind: String, table: Dictionary, preset: String, rate: float) -> AudioStreamWAV:
	var r := _f32(clampf(rate, 0.65, 1.45))
	var key := "%s:%s:%s" % [kind, preset, r]
	if not _cache.has(key):
		var pcm := samples(table[preset], r)
		var bytes := PackedByteArray()
		bytes.resize(pcm.size() * 2)
		for i in pcm.size():
			bytes.encode_s16(i * 2, pcm[i])
		var stream := AudioStreamWAV.new()
		stream.format = AudioStreamWAV.FORMAT_16_BITS
		stream.mix_rate = SAMPLE_RATE
		stream.stereo = false
		stream.data = bytes
		_cache[key] = stream
	return _cache[key]

## LudoPawsProceduralAudio.synth for one preset row; returns 16-bit samples.
static func samples(row: Array, rate: float) -> PackedInt32Array:
	var probe_t := Time.get_ticks_usec()
	var probe_r = _probed_samples(row, rate)
	preload("res://scripts/game/perf.gd").span("synth_samples", Time.get_ticks_usec() - probe_t)
	return probe_r

static func _probed_samples(row: Array, rate: float) -> PackedInt32Array:
	var duration_ms: int = row[0]
	var start_hz: float = row[1]
	var end_hz: float = row[2]
	var harmonic: float = row[3]
	var noise: float = row[4]
	var o: Dictionary = row[5]
	var vibrato_hz: float = o.get("vibrato", 0.0)
	var vibrato_depth: float = o.get("depth", 0.0)
	var tremolo_hz: float = o.get("tremolo", 0.0)
	var double_pulse: bool = o.get("double", false)
	var peak = o.get("peak")
	var adjusted := maxi(int(_f32(float(duration_ms) / _f32(rate))), 55)
	var count := maxi(SAMPLE_RATE * adjusted / 1000, 1)
	var phase := 0.0
	var noise_state := _i32(_i32(duration_ms * 1103515245) + int(start_hz)) ^ int(end_hz)
	noise_state = _i32(noise_state)
	var out := PackedInt32Array()
	out.resize(count)
	for index in count:
		var t := float(index) / SAMPLE_RATE
		var p := float(index) / float(count)
		var base_hz: float
		if peak != null:
			base_hz = _lerp(start_hz, peak, p * 2.0) if p < 0.5 else _lerp(peak, end_hz, (p - 0.5) * 2.0)
		else:
			base_hz = _lerp(start_hz, end_hz, p)
		var vibrato := 1.0 + (sin(TAU * vibrato_hz * t) * vibrato_depth if vibrato_hz > 0.0 else 0.0)
		phase += TAU * base_hz * vibrato / SAMPLE_RATE
		noise_state = _i32(noise_state * 1664525 + 1013904223)
		var white := float(((noise_state & 0xFFFFFFFF) >> 9) & 0x7fffff) / 4194303.5 - 1.0
		var tone := sin(phase) + harmonic * sin(phase * 2.0 + 0.35)
		var tremolo := 0.72 + 0.28 * sin(TAU * tremolo_hz * t) if tremolo_hz > 0.0 else 1.0
		var pulse := minf(_bell(p, 0.23, 0.17) + _bell(p, 0.70, 0.19), 1.0) if double_pulse else 1.0
		var envelope := clampf(p / 0.09, 0.0, 1.0) * clampf((1.0 - p) / 0.20, 0.0, 1.0) * pulse
		var sample := clampf((tone * (1.0 - noise) + white * noise) * tremolo * envelope * 0.58, -1.0, 1.0)
		out[index] = int(sample * 32767.0)
	return out

static func _bell(value: float, center: float, radius: float) -> float:
	var x := clampf((value - center) / radius, -1.0, 1.0)
	return 0.0 if absf(x) >= 1.0 else 0.5 + 0.5 * cos(PI * x)

static func _lerp(a: float, b: float, p: float) -> float:
	return a + (b - a) * clampf(p, 0.0, 1.0)

static func _i32(v: int) -> int:
	v = v & 0xFFFFFFFF
	return v - 0x100000000 if v >= 0x80000000 else v

static func _f32(v: float) -> float:
	var a := PackedFloat32Array([v])
	return a[0]
