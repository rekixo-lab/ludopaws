@tool
extends RefCounted
## Panda built from scene parts, with a smooth stand -> four paws -> stand idle posture.
## Port of PandaRenderer.kt and PandaPosture.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const Mat = preload("res://scripts/game/characters/shared/pet_jump.gd")

const MODEL_SIZE_MULTIPLIER := 1.20
const COAT := Color(0.94, 0.93, 0.89, 1)
const DARK := Color(0.045, 0.052, 0.065, 1)
const PAD := Color(0.16, 0.17, 0.19, 1)

static func step_travel_progress(progress: float) -> float:
	return Hop.travel(progress)

static func _ease(value: float) -> float:
	var t := clampf(value, 0.0, 1.0)
	return clampf(t * t * t * (t * (t * 6.0 - 15.0) + 10.0), 0.0, 1.0)

## PandaPosture.allFours
static func all_fours(time_seconds: float, idle_weight: float) -> float:
	var p := fposmod(time_seconds, 12.0)
	var lower := _ease((p - 1.2) / 1.8)
	var rise := _ease((p - 6.0) / 1.8)
	return lower * (1.0 - rise) * clampf(idle_weight, 0.0, 1.0)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var p := clampf(progress, 0.0, 1.0)
	var wave := sin(p * PI * 2.0)
	var anatomy := 1.0 if playback == P.SETTLE else p
	var arc := maxf(sin(step_travel_progress(anatomy) * PI), 0.0)
	var pose := {}
	match motion:
		P.IDLE:
			pose = P.pose({"headTiltDegrees": 7.0 * wave, "bodyRollDegrees": 2.5 * wave,
				"primaryAppendageDegrees": 17.0 * wave, "secondaryAppendageDegrees": 8.0 * wave})
		P.HOP:
			pose = P.pose({"legTuckDegrees": 24.0 * arc, "legSwingDegrees": -14.0 * arc,
				"bodyPitchDegrees": -5.0 * arc, "neckPitchDegrees": 4.0 * arc, "primaryAppendageDegrees": 18.0 * arc})
		_:
			pose = P.pose({"liftY": 0.15 * absf(sin(p * PI * 3.0)) * (1.0 - p),
				"bodyYawDegrees": 360.0 * p, "headTiltDegrees": 6.0 * wave, "primaryAppendageDegrees": 26.0 * wave})
	return Hop.apply(pose, motion, progress, 0.52, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var life = ctx.life
	ctx.surface = "SOFT_FUR"
	# The life clock continues across idle loops; idle weight fades out so the Panda stands to respond.
	var q: float = life.follow_stance(all_fours(life.time_seconds, life.idle_weight)) if life != null else 0.0
	var sit: float = pose["gestureWeight"] * (1.0 - q)
	var model := G.scale(parent, MODEL_SIZE_MULTIPLIER, MODEL_SIZE_MULTIPLIER, MODEL_SIZE_MULTIPLIER)
	var root := G.translate(model, 0, -pose["bodyCrouch"] - 0.18 * sit, 0)
	root = G.rotate(root, pose["bodyPitchDegrees"], 1, 0, 0)
	root = G.rotate(root, pose["bodyRollDegrees"] * (1.0 - q), 0, 0, 1)
	var torso := G.translate(root, 0, -0.16 * q, -0.08 * q)
	torso = G.rotate(torso, 65.0 * q, 1, 0, 0)
	var breath: float = 1.0 + (life.breath if life != null else 0.0)
	ctx.draw_part(torso, Vector3(0, -0.02, 0), Vector3(0.39 * breath, 0.46, 0.31 * breath), COAT)
	ctx.draw_part(torso, Vector3(0, 0.26, -0.015), Vector3(0.40, 0.19, 0.29), DARK)
	ctx.draw_part(torso, Vector3(0, -0.15, -0.32), Vector3(0.12, 0.12, 0.12), COAT)
	for side in [-1.0, 1.0]:
		var arm := G.translate(root, side * 0.35, 0.25 - 0.40 * q, 0.32 * q)
		var wave_arm: float = pose["primaryAppendageDegrees"] * (1.0 if side == 1.0 else 0.45) + sit * (24.0 + 9.0 * sin(pose["gesturePhase"] * PI * 6.0))
		arm = G.rotate(arm, side * (10.0 + wave_arm) * (1.0 - q), 0, 0, 1)
		arm = G.rotate(arm, pose["secondaryAppendageDegrees"] * (1.0 - q), 1, 0, 0)
		ctx.draw_part(arm, Vector3(side * 0.045 * (1.0 - q), -0.21 - 0.02 * q, 0.035), Vector3(0.14, 0.29, 0.15), DARK)
		ctx.draw_part(arm, Vector3(0, -0.43 - 0.04 * q, 0.065 + 0.065 * q), Vector3(0.16, 0.10, 0.19), DARK)
		var leg := G.translate(model, side * 0.23, -0.35, -0.025 - 0.24 * q + 0.14 * sit)
		leg = G.rotate(leg, pose["legSwingDegrees"], 1, 0, 0)
		var tuck := clampf(pose["legTuckDegrees"] / 38.0, 0.0, 1.0)
		ctx.draw_part(leg, Vector3(0, -0.13 + tuck * 0.10, 0.02), Vector3(0.18, 0.23, 0.18), DARK)
		ctx.draw_part(leg, Vector3(0, -0.27 + tuck * 0.16, 0.12), Vector3(0.19, 0.10, 0.23), DARK)
		ctx.draw_part(leg, Vector3(0, -0.27 + tuck * 0.16, 0.324), Vector3(0.10, 0.052, 0.015), PAD)
	# Keep the face looking forward when the shoulders lower onto four paws.
	var neck := G.translate(root, 0, -0.45 * q, 0.43 * q)
	neck = G.translate(neck, 0, 0.36, 0)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.36, 0)
	ctx.prepare_head(neck, 0.74, pose["headTiltDegrees"])
	var head: Transform3D = ctx.head
	ctx.draw_part(head, Vector3(0, 0.69, 0.025), Vector3(0.46, 0.41, 0.34), COAT)
	for side in [-1.0, 1.0]:
		ctx.draw_part(head, Vector3(side * 0.34, 1.00, -0.015), Vector3(0.16, 0.17, 0.12), DARK)
		ctx.draw_part(head, Vector3(side * 0.34, 1.00, 0.09), Vector3(0.085, 0.095, 0.025), PAD)
		ctx.draw_part(head, Vector3(side * 0.185, 0.73, 0.304), Vector3(0.145, 0.18, 0.045), DARK, Vector3(0, 0, -side * 23.0))
		ctx.surface = "EYE"
		ctx.draw_living_eye(head, side * 0.185, 0.75, 0.34, 0.062, 0.078, DARK, Mat.WHITE, Mat.EYE_DARK, false, false)
		ctx.surface = "SOFT_FUR"
		ctx.draw_part(head, Vector3(side * 0.085, 0.53, 0.327), Vector3(0.12, 0.085, 0.07), COAT)
	ctx.surface = "NOSE"
	ctx.draw_part(head, Vector3(0, 0.585, 0.405), Vector3(0.07, 0.047, 0.035), DARK)
	ctx.surface = "MATTE"
	var mouth := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	ctx.draw_part(head, Vector3(0, 0.478, 0.369), Vector3(0.065, 0.009 + mouth * 0.03, 0.014), DARK)
	ctx.surface = previous
