@tool
extends RefCounted
## Monkey: articulated long arms (greeting, head scratch, clap), expressive face, hooked tail.
## Port of MonkeyRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const Mat = preload("res://scripts/game/characters/shared/pet_jump.gd")

const MODEL_SIZE_MULTIPLIER := 1.26
const FUR := Color(0.38, 0.19, 0.085, 1)
const FACE := Color(0.88, 0.66, 0.40, 1)
const DARK := Color(0.14, 0.065, 0.035, 1)
const EAR := Color(0.67, 0.39, 0.25, 1)

static func step_travel_progress(progress: float) -> float:
	return Hop.travel(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var p := clampf(progress, 0.0, 1.0)
	var wave := sin(p * PI * 2.0)
	var anatomy := 1.0 if playback == P.SETTLE else p
	var arc := maxf(sin(step_travel_progress(anatomy) * PI), 0.0)
	var pose := {}
	match motion:
		P.IDLE:
			pose = P.pose({"headTiltDegrees": 9.0 * wave, "primaryAppendageDegrees": 22.0 * wave,
				"secondaryAppendageDegrees": 12.0 * wave, "tertiaryAppendageDegrees": 18.0 * wave, "bodyRollDegrees": 3.0 * wave})
		P.HOP:
			pose = P.pose({"legTuckDegrees": 32.0 * arc, "legSwingDegrees": -22.0 * arc,
				"primaryAppendageDegrees": 42.0 * arc, "secondaryAppendageDegrees": -24.0 * arc,
				"tertiaryAppendageDegrees": -35.0 * arc, "bodyPitchDegrees": -9.0 * arc, "neckPitchDegrees": 7.0 * arc})
		_:
			pose = P.pose({"liftY": 0.24 * absf(sin(p * PI * 3.0)) * (1.0 - p),
				"bodyYawDegrees": 360.0 * p, "primaryAppendageDegrees": 65.0 * wave,
				"secondaryAppendageDegrees": 28.0 * wave, "tertiaryAppendageDegrees": 32.0 * wave, "headTiltDegrees": 10.0 * wave})
	return Hop.apply(pose, motion, progress, 0.64, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var life = ctx.life
	ctx.surface = "SOFT_FUR"
	var model := G.scale(parent, MODEL_SIZE_MULTIPLIER, MODEL_SIZE_MULTIPLIER, MODEL_SIZE_MULTIPLIER)
	var root := G.translate(model, 0, -pose["bodyCrouch"], 0)
	root = G.rotate(root, pose["bodyPitchDegrees"], 1, 0, 0)
	root = G.rotate(root, pose["bodyRollDegrees"], 0, 0, 1)
	var breath: float = 1.0 + (life.breath if life != null else 0.0)
	ctx.draw_part(root, Vector3(0, -0.035, -0.015), Vector3(0.27 * breath, 0.38, 0.23 * breath), FUR)
	ctx.draw_part(root, Vector3(0, -0.035, 0.19), Vector3(0.18, 0.27, 0.065), FACE)
	_tail(ctx, root, pose["tertiaryAppendageDegrees"])
	# A slow head-scratch gesture is independent of the short idle loop.
	var scratch := 0.0
	if life != null:
		var phase: float = (fmod(life.time_seconds, 9.0) / 9.0) * PI
		scratch = pow(sin(phase), 16) * life.idle_weight
	for side in [-1.0, 1.0]:
		var arm := G.translate(root, side * 0.255, 0.22, 0)
		var greeting: float = pose["primaryAppendageDegrees"] * (1.0 if side == 1.0 else 0.45)
		var scratch_weight := scratch if side == 1.0 else 0.0
		var clap: float = pose["gestureWeight"] * (1.0 - scratch_weight)
		var arm_angle: float = ((12.0 + greeting) * (1.0 - scratch_weight) + 155.0 * scratch_weight) * (1.0 - clap) - 20.0 * clap
		arm = G.rotate(arm, side * arm_angle, 0, 0, 1)
		arm = G.rotate(arm, pose["secondaryAppendageDegrees"], 1, 0, 0)
		ctx.draw_part(arm, Vector3(side * 0.025, -0.19, 0), Vector3(0.09, 0.24, 0.09), FUR)
		var elbow := G.translate(arm, side * 0.025, -0.36, 0)
		elbow = G.rotate(elbow, -18.0 - (55.0 * scratch if side == 1.0 else 0.0) - 60.0 * clap, 1, 0, 0)
		ctx.draw_part(elbow, Vector3(0, -0.14, 0), Vector3(0.075, 0.19, 0.08), FUR)
		ctx.draw_part(elbow, Vector3(0, -0.32, 0.02), Vector3(0.105, 0.09, 0.075), FACE)
		ctx.draw_part(elbow, Vector3(-side * 0.085, -0.28, 0.04), Vector3(0.038, 0.065, 0.04), FACE)
		var leg := G.translate(model, side * 0.16, -0.34, -0.025)
		leg = G.rotate(leg, pose["legSwingDegrees"], 1, 0, 0)
		var tuck := clampf(pose["legTuckDegrees"] / 38.0, 0.0, 1.0)
		ctx.draw_part(leg, Vector3(0, -0.12 + tuck * 0.10, 0), Vector3(0.115, 0.21, 0.12), FUR)
		ctx.draw_part(leg, Vector3(0, -0.30 + tuck * 0.18, 0.09), Vector3(0.13, 0.08, 0.19), FACE)
	var neck := G.translate(root, 0, 0.32, 0)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.32, 0)
	ctx.prepare_head(neck, 0.68, pose["headTiltDegrees"])
	var head: Transform3D = ctx.head
	ctx.draw_part(head, Vector3(0, 0.66, 0), Vector3(0.34, 0.36, 0.28), FUR)
	for side in [-1.0, 1.0]:
		ctx.draw_part(head, Vector3(side * 0.35, 0.64, -0.005), Vector3(0.15, 0.17, 0.085), FUR)
		ctx.draw_part(head, Vector3(side * 0.365, 0.64, 0.068), Vector3(0.09, 0.11, 0.025), EAR)
		ctx.draw_part(head, Vector3(side * 0.13, 0.68, 0.225), Vector3(0.17, 0.21, 0.075), FACE)
		ctx.surface = "EYE"
		ctx.draw_living_eye(head, side * 0.135, 0.71, 0.295, 0.067, 0.087, FACE, Mat.WHITE, Mat.EYE_DARK, false, false)
		ctx.surface = "SOFT_FUR"
	ctx.draw_part(head, Vector3(0, 0.50, 0.265), Vector3(0.22, 0.13, 0.105), FACE)
	ctx.surface = "NOSE"
	ctx.draw_part(head, Vector3(0, 0.56, 0.363), Vector3(0.06, 0.035, 0.025), DARK)
	ctx.surface = "MATTE"
	var mouth := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	ctx.draw_part(head, Vector3(0, 0.445, 0.35), Vector3(0.09, 0.009 + 0.045 * mouth, 0.014), DARK)
	ctx.draw_part(head, Vector3(-0.055, 0.46, 0.348), Vector3(0.04, 0.008, 0.012), DARK, Vector3(0, 0, -18))
	ctx.draw_part(head, Vector3(0.055, 0.46, 0.348), Vector3(0.04, 0.008, 0.012), DARK, Vector3(0, 0, 18))
	ctx.surface = "SOFT_FUR"
	ctx.draw_part(head, Vector3(0, 1.00, -0.01), Vector3(0.085, 0.12, 0.07), FUR, Vector3(0, 0, -23))
	ctx.surface = previous

## Closely overlapping rounded sections form a hooked silhouette without gaps.
static func _tail(ctx, parent: Transform3D, wag: float) -> void:
	var tail := G.translate(parent, 0, -0.22, -0.20)
	tail = G.rotate(tail, wag, 0, 1, 0)
	for i in 19:
		var u := i / 18.0
		var angle := u * PI * 1.35
		var radius := 0.065 - 0.025 * u
		ctx.draw_part(tail, Vector3(0.26 * (1.0 - cos(angle)), 0.20 * u + 0.30 * sin(angle), -0.34 * u),
			Vector3(radius, radius, radius), DARK if i == 18 else FUR)
