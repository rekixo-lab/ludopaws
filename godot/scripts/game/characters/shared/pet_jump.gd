@tool
extends RefCounted
## Port of feature/characters/shared/Pet3DJump.kt and CharacterMaterials.kt.

const WHITE := Color(1, 1, 1, 1)
const EYE_DARK := Color(0.025, 0.030, 0.035, 1)
const NOSE_DARK := Color(0.05, 0.04, 0.04, 1)
const NOSE_HIGHLIGHT := Color(0.48, 0.43, 0.40, 1)

static func height(progress: float) -> float:
	var p := clampf(progress, 0.0, 1.0)
	if p < 0.13:
		return -0.065 * sin(p / 0.13 * PI)
	if p < 0.82:
		var t := (p - 0.13) / 0.69
		return 4.0 * t * (1.0 - t)
	var t := (p - 0.82) / 0.18
	return -0.075 * sin(t * PI * 2.0) * (1.0 - t)
