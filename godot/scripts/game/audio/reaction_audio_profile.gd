extends RefCounted
## Non-verbal animal vocal plan for a reaction: which clip names, procedural fallback,
## volume, playback rate, play probability and delay. Port of LudoPawsReactionAudioProfile.kt.

const VARIANT_COUNT := 2

## plan(characterId, species, reaction) -> Dictionary or null.
static func plan(character_id: String, species: String, reaction: Dictionary) -> Variant:
	var cue: String = reaction["voiceCue"]
	if cue == "SILENT":
		return null
	var probability := probability_percent(cue, reaction.get("momentType"))
	if probability <= 0:
		return null
	return {"names": resource_names(character_id, cue), "fallback": fallback_for(species, cue),
		"volume": _f32(_f32(volume_for(cue)) * (0.55 if species == "RABBIT" else 1.0)),
		"rate": playback_rate_for(species, cue), "probability": probability,
		"delay": delay_for(reaction.get("momentType"))}

static func should_play(p: Dictionary, reaction_key: String) -> bool:
	if p["probability"] >= 100:
		return true
	if p["probability"] <= 0:
		return false
	return stable_positive_hash(reaction_key) % 100 < p["probability"]

static func resource_names(character_id: String, cue: String) -> Array:
	return range(1, VARIANT_COUNT + 1).map(func(v): return "lp_vocal_%s_%s_%02d" % [character_id, cue.to_lower(), v])

static func deterministic_variant_index(reaction_key: String, variant_count: int) -> int:
	if variant_count <= 1:
		return 0
	return stable_positive_hash("variant:" + reaction_key) % variant_count

static func probability_percent(cue: String, moment) -> int:
	match cue:
		"SIX": return 68
		"YARD_EXIT": return 48
		"CAPTURE", "CAPTURED", "HOME", "THIRD_SIX", "VICTORY", "DEFEAT": return 100
		"SAFE": return 36
		"HOME_LANE": return 24
		"FRUSTRATED":
			match moment:
				"POOR_ROLL_STREAK": return 100
				"EXACT_HOME_MISS": return 48
				"NO_LEGAL_MOVE": return 34
			return 45
		"IDLE": return 28
		"NERVOUS": return 45
		"PROUD": return 18
	return 0

static func fallback_for(species: String, cue: String) -> String:
	match species:
		"DOG":
			if cue in ["CAPTURED", "FRUSTRATED", "DEFEAT"]: return "DOG_WHINE"
			if cue in ["THIRD_SIX", "NERVOUS"]: return "DOG_RUFF"
			return "DOG_YIP"
		"GOAT", "SHEEP":
			return "GOAT_SOFT_BLEAT" if cue in ["CAPTURED", "FRUSTRATED", "DEFEAT", "NERVOUS"] else "GOAT_BLEAT"
		"DUCK", "CHICK":
			return "DUCK_SOFT_QUACK" if cue in ["CAPTURED", "FRUSTRATED", "DEFEAT", "NERVOUS"] else "DUCK_QUACK"
		"RABBIT":
			return "RABBIT_SNUFFLE"
		"HORSE":
			return "HORSE_NEIGH" if cue in ["SIX", "YARD_EXIT", "CAPTURED", "HOME", "VICTORY"] else "HORSE_SNORT"
		"ELEPHANT":
			return "ELEPHANT_TRUMPET" if cue in ["SIX", "YARD_EXIT", "CAPTURE", "CAPTURED", "HOME", "VICTORY"] else "ELEPHANT_RUMBLE"
		"PIG":
			return "PIG_SQUEAL" if cue in ["CAPTURED", "THIRD_SIX", "VICTORY"] else "PIG_OINK"
		"COCK":
			return "COCK_CROW" if cue in ["SIX", "YARD_EXIT", "HOME", "PROUD", "VICTORY"] else "COCK_CLUCK"
		"CAT":
			if cue in ["CAPTURED", "FRUSTRATED", "DEFEAT"]: return "CAT_MEW"
			if cue in ["SAFE", "HOME", "VICTORY", "PROUD"]: return "CAT_PURR"
			if cue in ["THIRD_SIX", "NERVOUS"]: return "CAT_HUFF"
			return "CAT_CHIRP"
	return "DOG_YIP"

static func volume_for(cue: String) -> float:
	match cue:
		"IDLE", "PROUD": return 0.34
		"SAFE", "HOME_LANE", "NERVOUS": return 0.42
		"DEFEAT", "FRUSTRATED": return 0.46
		"SIX", "YARD_EXIT", "HOME", "VICTORY": return 0.55
		"CAPTURE", "CAPTURED", "THIRD_SIX": return 0.58
	return 0.0

static func playback_rate_for(species: String, cue: String) -> float:
	var base := 1.0
	match species:
		"DUCK", "CHICK": base = 1.06
		"CAT": base = 1.03
		"GOAT", "SHEEP": base = 0.97
	if cue in ["VICTORY", "SIX"]:
		return _f32(minf(_f32(_f32(base) + _f32(0.05)), 1.45))
	if cue in ["DEFEAT", "FRUSTRATED"]:
		return _f32(maxf(_f32(_f32(base) - _f32(0.05)), 0.65))
	return _f32(base)

static func delay_for(moment) -> int:
	match moment:
		"CAPTURE_MADE": return 80
		"TOKEN_CAPTURED": return 155
	return 0

## 17, *31 + char code, int32 wrap, & Int.MAX_VALUE.
static func stable_positive_hash(value: String) -> int:
	var h := 17
	for i in value.length():
		h = (h * 31 + value.unicode_at(i)) & 0xFFFFFFFF
	return h & 0x7FFFFFFF

static func _f32(v: float) -> float:
	var a := PackedFloat32Array([v])
	return a[0]
