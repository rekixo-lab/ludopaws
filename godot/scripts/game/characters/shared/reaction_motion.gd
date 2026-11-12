@tool
extends RefCounted
## Additive body-language layer: cue durations and frames, per-pawn variation and situational
## accents. Port of LudoPaws3DReactionMotion.kt, LudoPawsPawnReactionVariation.kt and
## LudoPawsSituationalActing.kt.

const P = preload("res://scripts/game/characters/shared/pose.gd")
const Catalog = preload("res://scripts/game/characters/catalog.gd")

const BASE_DURATION_MS := {
	"IDLE": 520, "EXCITED": 620, "HAPPY": 680, "SAD": 760, "ANGRY": 650, "NERVOUS": 720,
	"CAPTURE": 700, "CAPTURED": 620, "SAFE": 640, "HOME": 900, "VICTORY": 1250, "DEFEAT": 950,
}

static func _profile(character_id: String):
	var def = Catalog.definition(character_id)
	return def if def != null else Catalog.definition(Catalog.DEFAULT_CHARACTER_ID)

static func duration_ms(character_id: String, cue: String) -> int:
	return int(BASE_DURATION_MS[cue] * _profile(character_id).reaction_duration_scale())

static func sample(character_id: String, cue: String, progress: float) -> Dictionary:
	var raw := clampf(progress, 0.0, 1.0)
	var p := raw
	if cue == "CAPTURED" or cue == "NERVOUS":
		p = 0.5 * (raw / 0.18) if raw < 0.18 else 0.5 + 0.5 * (raw - 0.18) / 0.82
	var frame := {
		"p": p, "arc": maxf(sin(p * PI), 0.0), "wave": sin(p * PI * 2.0),
		"fast": sin(p * PI * 5.0), "bounce": absf(sin(p * PI * 2.0)) * (1.0 - p * 0.20),
	}
	return _profile(character_id).reaction(cue, frame)

## LudoPawsPawnReactionVariation.choose: [cue, delay_ms, variant, duration_scale]
static func choose(cue: String, token: int, seed: int, group: bool) -> Array:
	var rank := posmod(token - posmod(seed, 4), 4)
	var varied := cue
	if group:
		if cue == "VICTORY":
			varied = "HAPPY" if rank == 1 else ("EXCITED" if rank == 2 else "VICTORY")
		elif cue == "DEFEAT":
			varied = "DEFEAT" if rank % 2 == 0 else "SAD"
	var scale: float = ([0.94, 1.06, 1.13, 1.0][rank]) if group else 1.0
	return [varied, rank * 90 if group else 0, rank, scale]

static func vary(base: Dictionary, progress: float, variant: int) -> Dictionary:
	var v := posmod(variant, 4)
	var envelope := maxf(sin(clampf(progress, 0.0, 1.0) * PI), 0.0)
	var strength: float = [0.88, 1.04, 0.96, 1.12][v]
	var direction := 1.0 if v % 2 == 0 else -1.0
	return P.copy(base, {
		"liftY": base["liftY"] * strength,
		"headTiltDegrees": base["headTiltDegrees"] * strength + direction * (3.0 + v) * envelope,
		"primaryAppendageDegrees": base["primaryAppendageDegrees"] * strength,
		"secondaryAppendageDegrees": base["secondaryAppendageDegrees"] * (1.12 - v * 0.07),
		"tertiaryAppendageDegrees": base["tertiaryAppendageDegrees"] * strength,
		"neckPitchDegrees": base["neckPitchDegrees"] + direction * 3.0 * envelope,
	})

## LudoPawsSituationalActing.pose
static func situational(base: Dictionary, moment, progress: float) -> Dictionary:
	var p := clampf(progress, 0.0, 1.0)
	var arc := maxf(sin(p * PI), 0.0)
	var wave := sin(p * PI * 2.0)
	var neck := 0.0
	match moment:
		"TOKEN_LEFT_YARD": neck = -6.0 * arc
		"SAFE_REACHED": neck = 4.0 * arc
		"EXACT_HOME_MISS": neck = 10.0 * arc
		"POOR_ROLL_STREAK", "NO_LEGAL_MOVE": neck = 6.0 * arc
		"HOME_REACHED": neck = -7.0 * arc
	var tilt := 0.0
	match moment:
		"EXACT_HOME_MISS": tilt = 7.0 * arc
		"THIRD_SIX_FORFEIT": tilt = 5.0 * wave * arc
		"CAPTURE_MADE": tilt = -4.0 * arc
	return P.copy(base, {"neckPitchDegrees": base["neckPitchDegrees"] + neck, "headTiltDegrees": base["headTiltDegrees"] + tilt})

static func observer(progress: float) -> Dictionary:
	var p := clampf(progress, 0.0, 1.0)
	var arc := maxf(sin(p * PI), 0.0)
	return P.reaction({"headTiltDegrees": 4.0 * sin(p * PI * 2.0) * arc, "neckPitchDegrees": -3.0 * arc})
