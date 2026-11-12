extends RefCounted
## Authored audio families (up to three numbered takes each) and per-character audio
## profiles. Paths are the live repository paths; V2 keeps the files under res://assets/.
## Ports LudoPawsAudioCatalog.kt, LudoPawsCharacterAudioProfiles.kt and
## GeneratedCharacterAudioProfiles.kt.

const ROOT := "res://assets/"

static func _takes(dir: String, stem: String) -> Array:
	return [1, 2, 3].map(func(i): return "audio/sfx/%s/%s_%02d.wav" % [dir, stem, i])

static var MOVE_STEP := _takes("gameplay/movement/step", "lp_sfx_step")
static var MOVE_STEP_DOG := _takes("gameplay/movement/step/dog", "lp_sfx_step_dog")
static var MOVE_STEP_GOAT := _takes("gameplay/movement/step/goat", "lp_sfx_step_goat")
## The surviving Duck take lives in the shared step folder but stays Duck-only.
static var MOVE_STEP_DUCK := _takes("gameplay/movement/step/duck", "lp_sfx_step_duck") \
	+ ["audio/sfx/gameplay/movement/step/lp_sfx_step_duck_01.wav"]
static var MOVE_STEP_CAT := _takes("gameplay/movement/step/cat", "lp_sfx_step_cat")
static var MOVE_JUMP := _takes("gameplay/movement/jump", "lp_sfx_jump")
static var UI_CLICK := _takes("ui/click", "lp_sfx_ui_click")
static var DICE_ROLL := _takes("gameplay/dice", "lp_sfx_dice_roll")
static var DICE_SETTLE := _takes("gameplay/dice/settle", "lp_sfx_dice_settle")
static var SIX := _takes("gameplay/six", "lp_sfx_six")
static var YARD_EXIT := _takes("gameplay/yard_exit", "lp_sfx_yard_exit")
static var CAPTURE := _takes("gameplay/capture", "lp_sfx_capture")
static var SAFE_RELIEF := _takes("gameplay/safe", "lp_sfx_safe_relief")
static var HOME_LANE := _takes("gameplay/home_lane", "lp_sfx_home_lane")
static var HOME := _takes("gameplay/home", "lp_sfx_home")
static var EXACT_HOME_MISS := _takes("gameplay/exact_home_miss", "lp_sfx_exact_home_miss")
static var FAIL := _takes("gameplay/fail", "lp_sfx_fail")
static var THIRD_SIX := _takes("gameplay/third_six", "lp_sfx_third_six")
static var VICTORY := _takes("gameplay/victory", "lp_sfx_victory")
static var DEFEAT := _takes("gameplay/defeat", "lp_sfx_defeat")

## LudoPawsAudioCatalog.takeForEvent: one stable take per shared event index.
static func take_for_event(family: Array, event_index: int) -> Array:
	return [] if family.is_empty() else [family[posmod(event_index, family.size())]]

## takeAvailableForEvent: selects only among takes that actually exist.
static func take_available_for_event(available: Array, event_index: int) -> Variant:
	return null if available.is_empty() else available[posmod(event_index, available.size())]

static func exists(path: String) -> bool:
	return ResourceLoader.exists(ROOT + path)

## Existence is fixed for a build: cached so a sound never pays file checks when it plays.
static var _available := {}

static func available(paths: Array) -> Array:
	var found = _available.get(paths)
	if found == null:
		found = paths.filter(func(p): return exists(p))
		_available[paths.duplicate()] = found
	return found

## Every gameplay family a match can play (warm-up at match start).
static func match_families() -> Array:
	return [MOVE_STEP, MOVE_STEP_DOG, MOVE_STEP_GOAT, MOVE_STEP_DUCK, MOVE_STEP_CAT, MOVE_JUMP, UI_CLICK,
		DICE_ROLL, DICE_SETTLE, SIX, YARD_EXIT, CAPTURE, SAFE_RELIEF, HOME_LANE, HOME, EXACT_HOME_MISS,
		FAIL, THIRD_SIX, VICTORY, DEFEAT]

# ---------------------------------------------------------------- character profiles

## characterId -> [voiceAssetOwnerId or null, footstep style, fallback step family name, step volume]
const PROFILES := {
	"dog": ["dog", "PAW", "DOG", 0.30],
	"goat": ["goat", "HOOF", "GOAT", 0.30],
	"duck": ["duck", "WEB", "DUCK", 0.28],
	"cat": ["cat", "PAW", "CAT", 0.30],
	"donkey": [null, "PAW", "DOG", 0.30],
	"goat_lumi": ["goat", "HOOF", "GOAT", 0.30],
	"cock": ["cock", "PAW", "DOG", 0.30],
	"pig": ["pig", "HOOF", "GOAT", 0.30],
	"elephant": ["elephant", "PAW", "CAT", 0.30],
	"horse": ["horse", "HOOF", "GOAT", 0.30],
	"rabbit": ["rabbit", "PAW", "CAT", 0.30],
	"panda": [null, "PAW", "CAT", 0.28],
	"monkey": [null, "PAW", "CAT", 0.28],
	"giraffe": [null, "HOOF", "GOAT", 0.42],
}

## LudoPawsCharacterAudioProfiles.forCharacter (unknown -> dog).
static func profile(character_id) -> Dictionary:
	var id := str(character_id) if PROFILES.has(str(character_id)) else "dog"
	var row: Array = PROFILES[id]
	return {"characterId": id, "voiceOwner": row[0], "footstep": row[1], "stepVolume": row[3],
		"fallbackSteps": {"DOG": MOVE_STEP_DOG, "GOAT": MOVE_STEP_GOAT, "DUCK": MOVE_STEP_DUCK, "CAT": MOVE_STEP_CAT}[row[2]]}

## LudoPawsCharacterAudioProfile.stepAssetFamilies.
static func step_asset_families(p: Dictionary) -> Array:
	var id: String = p["characterId"]
	if id in ["dog", "goat", "duck", "cat"]:
		return [p["fallbackSteps"]]
	return [_takes("gameplay/movement/step/" + id, "lp_sfx_step_" + id), p["fallbackSteps"]]

## Every voice folder id (own recordings + fallback owners).
static func supported_voice_ids() -> Array:
	var ids := PROFILES.keys()
	for row in PROFILES.values():
		if row[0] != null and not ids.has(row[0]):
			ids.append(row[0])
	return ids

## LudoPawsCharacterAudioProfiles.vocalAssetPaths: lp_vocal_<owner>_<cue>_NN -> voice folder paths.
static func vocal_asset_paths(names: Array) -> Array:
	var out: Array = []
	var ids := supported_voice_ids()
	for name in names:
		var owner := ""
		for id in ids:
			if str(name).begins_with("lp_vocal_%s_" % id) and id.length() > owner.length():
				owner = id
		if not owner.is_empty():
			out.append("audio/sfx/voices/%s/%s.wav" % [owner, name])
			out.append("audio/sfx/voices/%s/%s.ogg" % [owner, name])
	return out
