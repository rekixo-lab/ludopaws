@tool
extends RefCounted
## Bounded presentation-only ledger: sounds/haptics fire at most once per stable event while
## one match is active. Port of LudoPawsFeedbackLedger.kt.

var max_keys := 512
var _active_match_id = null
var _consumed := {}

func _init(keys := 512) -> void:
	assert(keys >= 16, "feedback ledger must retain at least 16 keys")
	max_keys = keys

func once(match_id: String, key: String) -> bool:
	var safe_match := match_id.strip_edges()
	var safe_key := key.strip_edges()
	if safe_match.is_empty() or safe_key.is_empty():
		return false
	if _active_match_id != safe_match:
		_active_match_id = safe_match
		_consumed.clear()
	var full := "%s:%s" % [safe_match, safe_key]
	if _consumed.has(full):
		return false
	_consumed[full] = true
	while _consumed.size() > max_keys:
		_consumed.erase(_consumed.keys()[0])
	return true

func filter_reactions(reactions: Array) -> Array:
	return reactions.filter(func(r):
		var key: String = r["reactionKey"] if str(r.get("reactionKey", "")).strip_edges() != "" else _fallback_key(r)
		return once(r["matchId"], "REACTION:" + key))

func clear() -> void:
	_active_match_id = null
	_consumed.clear()

func retained_key_count() -> int:
	return _consumed.size()

static func _fallback_key(r: Dictionary) -> String:
	var type = r["momentType"] if r.get("momentType") != null else r["voiceCue"]
	return ":".join([str(r["eventIndex"]), type, r["playerId"], "" if r["tokenIndex"] == null else str(r["tokenIndex"])])
