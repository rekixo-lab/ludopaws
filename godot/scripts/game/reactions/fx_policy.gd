@tool
extends RefCounted
## Pure presentation policy for board reaction effects. Port of LudoPawsFxPolicy.kt.
## Plan = {durationMs, particleCount, allowTranslation, allowRotation, allowShake, allowConfetti}

static func plan(cue: String) -> Dictionary:
	match cue:
		"IDLE": return _plan(900, 0, false, false, false, false)
		"EXCITED", "HAPPY": return _plan(760, 8, true, true, false, false)
		"CAPTURE": return _plan(880, 12, true, true, false, false)
		"CAPTURED": return _plan(1050, 8, true, true, true, false)
		"SAFE": return _plan(820, 6, false, false, false, false)
		"HOME": return _plan(1050, 14, true, true, false, false)
		"ANGRY": return _plan(760, 6, false, true, true, false)
		"NERVOUS": return _plan(900, 4, false, true, true, false)
		"SAD", "DEFEAT": return _plan(980, 7, true, true, false, false)
		"VICTORY": return _plan(1500, 32, true, true, false, true)
	push_error("Unknown animation cue %s" % cue)
	return _plan(900, 0, false, false, false, false)

static func _plan(duration: int, particles: int, translate: bool, rotate: bool, shake: bool, confetti: bool) -> Dictionary:
	return {"durationMs": duration, "particleCount": particles, "allowTranslation": translate,
		"allowRotation": rotate, "allowShake": shake, "allowConfetti": confetti}
