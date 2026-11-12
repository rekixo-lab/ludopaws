extends RefCounted
## First-time Demo match (NEW V2 FEATURE, not in live): a short real Robot match that only
## changes the STARTING POSITION. Every rule stays the real engine's: unbiased local dice,
## captures, safe cells, extra turns, third six, exact roll to home. Nothing here touches the
## engine; the start state is written into the engine's own state fields after new_match.

const LocalMatch = preload("res://scripts/game/local_match.gd")
const Rules = preload("res://scripts/game/rules/ludo_engine.gd")
const Starter = preload("res://scripts/game/characters/starter_assignment.gd")

## Relative cell of each player's one track paw (not a safe cell, never shared globally).
const DEMO_START_POSITION := 30
const HOME := 57
## Robot difficulty for the demo (0 Easy, 1 Normal, 2 Hard).
const ROBOT_DIFFICULTY := 0
const RULESET := "CLASSIC_V8"
## User bottom-left Red, Robot diagonally opposite Yellow (live 2-player layout).
const SEATS := [0, 2]
const FLAG_PATH := "user://tutorial.cfg"
## Tests point this elsewhere so they never touch the real flag.
static var flag_path := FLAG_PATH

## user_character: the player's selected / starter free Paw; the robot gets another free Paw.
static func create(user_character := "dog", unlocked: Array = ["dog", "goat", "duck", "cat"]) -> LocalMatch:
	var free := ["dog", "goat", "duck", "cat"]
	var paws := Starter.normalize(2, [], user_character if free.has(user_character) else "dog", true, free)
	var seats: Array[int] = []
	seats.assign(SEATS)
	var game := LocalMatch.new(seats, "ROBOT", [
		{"displayName": "You", "characterId": paws[0], "playerType": "HUMAN"},
		{"displayName": "Robot", "characterId": paws[1], "playerType": "BOT"}], RULESET)
	var state: Dictionary = game.state
	for player in state["players"]:
		player["tokens"] = [HOME, HOME, HOME, DEMO_START_POSITION]
	state["openingRollConsumed"] = [true, true]
	state["comebackWaits"] = [-1, -1]
	game._sync()
	return game

## The demo start is sound: a non-safe cell and distinct global cells for both players.
static func start_is_valid(game) -> bool:
	var cells: Array = []
	for player in game.state["players"]:
		var cell = Rules.global_cell(player["color"], DEMO_START_POSITION)
		if cell == null or Rules.is_safe_global(cell):
			return false
		cells.append(cell)
	return cells[0] != cells[1]

# ---------------------------------------------------------------- first-launch flag

static func completed(path := "") -> bool:
	path = path if not path.is_empty() else flag_path
	var cfg := ConfigFile.new()
	cfg.load(path)
	return bool(cfg.get_value("tutorial", "tutorial_completed", false))

## Saved only when the demo is finished or skipped; quitting mid-demo leaves it unset.
static func mark_completed(path := "") -> void:
	path = path if not path.is_empty() else flag_path
	var cfg := ConfigFile.new()
	cfg.load(path)
	cfg.set_value("tutorial", "tutorial_completed", true)
	cfg.save(path)

## For testing: delete the flag so the demo runs on the next launch.
static func reset(path := "") -> void:
	path = path if not path.is_empty() else flag_path
	DirAccess.remove_absolute(ProjectSettings.globalize_path(path))
