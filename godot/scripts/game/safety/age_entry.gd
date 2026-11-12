extends RefCounted
## First-launch age screening. Port of FamilyAgeScreen.kt / AgeAccessPolicy.kt: the age
## (1–100) is mapped to a band and ONLY the band + policy version are stored, never the age.
## Local screening only — not proof of age and not consent.

const PATH := "user://family_privacy_v1.cfg"
const POLICY_VERSION := 1
## Bands: 0 = 1–12, 1 = 13–15, 2 = 16–17, 3 = 18+ (live saveChoice).

## Tests point this elsewhere so they never touch the real choice.
static var default_path := PATH
var path := PATH

func _init(file := "") -> void:
	path = file if not file.is_empty() else default_path

## On Android the choice lives in the live app's own store (SharedPreferences
## family_privacy_v1, through the LudoPawsPlatform plugin), so a player's existing choice
## survives the update and Play Games startup (Java) reads the same value.
func _platform():
	if path == PATH and Engine.has_singleton("LudoPawsPlatform"):
		return Engine.get_singleton("LudoPawsPlatform")
	return null

func has_choice() -> bool:
	var platform = _platform()
	if platform != null:
		return int(platform.ageBand()) >= 0
	var cfg := ConfigFile.new()
	if cfg.load(path) != OK:
		return false
	return int(cfg.get_value("family", "entry_policy_version", 0)) == POLICY_VERSION \
		and int(cfg.get_value("family", "age_band", -1)) in [0, 1, 2, 3, 4]

static func band_for(age: int) -> int:
	if age <= 12:
		return 0
	if age <= 15:
		return 1
	if age <= 17:
		return 2
	return 3

func save_choice(age: int) -> bool:
	if age < 1 or age > 100:
		return false
	var platform = _platform()
	if platform != null:
		return bool(platform.ageBandSave(band_for(age)))
	var cfg := ConfigFile.new()
	cfg.set_value("family", "age_band", band_for(age))
	cfg.set_value("family", "entry_policy_version", POLICY_VERSION)
	return cfg.save(path) == OK

func band() -> int:
	var platform = _platform()
	if platform != null:
		return int(platform.ageBand())
	var cfg := ConfigFile.new()
	cfg.load(path)
	return int(cfg.get_value("family", "age_band", -1))

## Optional Google features need the adult band (optionalGoogleAllowed).
func optional_google_allowed() -> bool:
	return has_choice() and band() == 3
