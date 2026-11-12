extends RefCounted
## Teaching hints for the Demo match (NEW V2 FEATURE). Hints come from the real game
## moments (reaction moment pipeline) of the player's own seat, in Hindi + English, and each
## hint is shown at most once. Pure presentation: never touches the match.

const Reactions = preload("res://scripts/game/reactions/reaction_engine.gd")

const HINTS := {
	"roll": "Dice पर tap करके roll करो · Tap the dice to roll",
	"tap": "चमकती गोटी पर tap करो · Tap the glowing paw",
	"six": "6 आया! एक और बारी · A six! Roll again",
	"safe": "Shield वाली जगह safe है, यहाँ capture नहीं होता · Shield cells are safe from capture",
	"capture": "Capture! सामने वाली गोटी वापस yard में गई, और तुम्हें extra turn मिला · Capture! Their paw went back to the yard and you roll again",
	"captured": "तुम्हारी गोटी पकड़ी गई, वापस yard में. 6 लाकर फिर निकालो · Your paw was captured. Roll a 6 to bring it out",
	"home_lane": "घर में जाने के लिए exact नंबर चाहिए · You need the exact number to reach home",
	"win": "तुम जीत गए! · You won!",
	"loss": "इस बार Robot जीत गया · The robot won this time",
}
## The player's moments that teach something, strongest first.
const MOMENT_HINTS := [["MATCH_WIN", "win"], ["MATCH_LOSS", "loss"], ["CAPTURE_MADE", "capture"],
	["TOKEN_CAPTURED", "captured"], ["SIX_ROLLED", "six"], ["SAFE_REACHED", "safe"],
	["HOME_LANE_ENTERED", "home_lane"], ["EXACT_HOME_MISS", "home_lane"]]

var player_id := "local-0"
var shown := {}
var current := ""

func _init(user_player_id := "local-0") -> void:
	player_id = user_player_id

## After every committed roll / move: show the strongest new lesson for the player.
func on_commit(previous: Dictionary, current_snapshot: Dictionary) -> void:
	current = ""
	var mine := Reactions.derive(previous, current_snapshot).filter(func(r): return r["playerId"] == player_id)
	var types := mine.map(func(r): return r.get("momentType"))
	for pair in MOMENT_HINTS:
		if types.has(pair[0]) and _show(pair[1]):
			return

## Before acting: the first roll and the first choice get their own hint.
func on_turn(my_turn: bool, can_roll: bool, can_move: bool) -> void:
	if not my_turn:
		return
	if can_roll:
		_show("roll")
	elif can_move:
		_show("tap")

func _show(key: String) -> bool:
	if shown.has(key):
		return false
	shown[key] = true
	current = HINTS[key]
	return true

func text() -> String:
	return current
