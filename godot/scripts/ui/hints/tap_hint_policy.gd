extends RefCounted
## Tap hand guidance policy (pure, testable): idle times, which offer wins, back-off and
## caps. See docs/TAP_HINT_MAP.md.

const IDLE_DEMO_MS := 1500
const IDLE_ONBOARDING_MS := 3000
const IDLE_MATCH_MS := 3000
const IDLE_SCREEN_MS := 5000
## Each ignored show makes the next wait for that target 50 % longer.
const BACKOFF := 0.5
## A non-essential target shows at most this many times per session.
const MAX_SHOWS := 6

var stats := {}   # key -> {shows, ignored}

func _stat(key: String) -> Dictionary:
	return stats.get_or_add(key, {"shows": 0, "ignored": 0})

func idle_needed(offer: Dictionary) -> int:
	return int(offer["idle_ms"] * (1.0 + BACKOFF * _stat(offer["key"])["ignored"]))

func eligible(offer: Dictionary, hints_on: bool) -> bool:
	if offer.get("essential", false):
		return true
	return hints_on and _stat(offer["key"])["shows"] < MAX_SHOWS

## available: offers whose target is on screen and not behind a dialog. Returns the offer to
## show now, or null (nothing eligible, or not idle long enough for the best one).
func pick(available: Array, hints_on: bool, idle_ms: int) -> Variant:
	var best = null
	for offer in available:
		if eligible(offer, hints_on) and (best == null or offer["priority"] > best["priority"]):
			best = offer
	if best == null or idle_ms < idle_needed(best):
		return null
	return best

func shown(key: String) -> void:
	_stat(key)["shows"] += 1

## The user tapped elsewhere while the hand pointed here.
func ignored(key: String) -> void:
	_stat(key)["ignored"] += 1

## The user tapped the target: back to the normal wait.
func acted(key: String) -> void:
	_stat(key)["ignored"] = 0
