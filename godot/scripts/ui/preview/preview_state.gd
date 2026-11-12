extends RefCounted
## UI-only state shared across routes. Game and service state will have separate owners.

const NAMES := ["Aarav", "Diya", "Kabir", "Myra"]
const SETTINGS_PATH := "user://meadow_ui.cfg"
const Inventory = preload("res://scripts/game/store/cosmetic_inventory.gd")
const StoreService = preload("res://scripts/game/store/store_service.gd")
const PlayBilling = preload("res://scripts/game/store/play_billing.gd")
const Ads = preload("res://scripts/game/ads/ads_service.gd")
const GameSettings = preload("res://scripts/game/settings/game_settings.gd")

var player_count := 4
var online_count := 2
var difficulty := 1
## Owned cosmetics / selections (cache of the server wallet; free Paws always unlocked).
var inventory := Inventory.new()
## The Demo match was started from Settings ("Play tutorial again"): flags/stats unchanged.
var tutorial_replay := false
## Store / billing / ads seams (desktop stubs; the server wallet is the authority).
var store := StoreService.new(inventory)
var billing := PlayBilling.new()
var ads := Ads.new(store)
var player_names := NAMES.duplicate()
var player_colors := [0, 1, 2, 3]
var team_seats := [false, false, false, false]
var room_code := ""
var room_host := false
var match_mode: StringName = &"online"
var saved_preview := false
var tab_indexes := {}
var settings := ConfigFile.new()

func _init() -> void:
	settings.load(SETTINGS_PATH)
	_restore_saved_match()

## Live Continue Match: a saved Robot or Pass & Play match (live checkpoint files) is ready
## to resume from Home; pending match statistics are retried (OfflineResultRecovery).
func _restore_saved_match() -> void:
	load("res://scripts/game/match/result_recovery.gd").retry()
	var SavedMatch = load("res://scripts/game/match/saved_match_store.gd")
	for mode in ["COMPUTER", "PASS_AND_PLAY"]:
		var saved: Dictionary = SavedMatch.load_state(mode)
		if not saved.is_empty():
			local_game = load("res://scripts/game/local_match.gd").from_saved(saved)
			match_mode = &"robot" if mode == "COMPUTER" else &"pass_and_play"
			saved_preview = true
			return

## Live settings in ludoproof_game_settings, V2-only ones in meadow_ui.cfg (game_settings.gd).
func set_setting(key: String, value: Variant) -> void:
	GameSettings.set_value(key, value)

func setting(key: String, fallback: Variant) -> Variant:
	return GameSettings.get_value(key, fallback)

var local_game: RefCounted
var handoff_pending := false
var last_roll := 0
var turn := 0
var events: Array[String] = []
