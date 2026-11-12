@tool
extends RefCounted
## Proud rooster: feather fan, comb and wattles, hinged beak, three-toed feet. Port of CockRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/cock/cock_geometry.gd")
const Mat = preload("res://scripts/game/characters/shared/pet_jump.gd")

static func step_travel_progress(progress: float) -> float:
	return Hop.travel(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var phase := clampf(progress, 0.0, 1.0)
	var wave := sin(phase * PI * 2.0)
	var anatomy_phase := 1.0 if motion == P.HOP and playback == P.SETTLE else phase
	var travel := step_travel_progress(anatomy_phase)
	var arc := maxf(sin(travel * PI), 0.0)
	var pose := {}
	match motion:
		P.IDLE:
			pose = P.pose({"headTiltDegrees": 3.0 * wave, "neckPitchDegrees": -2.0 * wave,
				"primaryAppendageDegrees": 3.0 * wave, "secondaryAppendageDegrees": 4.0 * wave})
		P.HOP:
			pose = P.pose({"liftY": 0.54 * 4.0 * travel * (1.0 - travel), "legTuckDegrees": 32.0 * arc,
				"legPaddleDegrees": 34.0 * wave * arc, "primaryAppendageDegrees": 38.0 * arc,
				"secondaryAppendageDegrees": -12.0 * arc, "bodyPitchDegrees": -5.0 * arc, "headTiltDegrees": -5.0 * arc})
		_:
			pose = P.pose({"liftY": 0.20 * absf(sin(phase * PI * 3.0)) * (1.0 - phase),
				"bodyYawDegrees": 360.0 * phase, "primaryAppendageDegrees": 48.0 * sin(phase * PI),
				"neckPitchDegrees": -10.0 * sin(phase * PI), "secondaryAppendageDegrees": 10.0 * wave})
	return Hop.apply(pose, motion, progress, 0.54, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var torso := G.translate(parent, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	torso = G.rotate(torso, pose["bodyRollDegrees"], 0, 0, 1)
	_feet(ctx, parent, pose)
	ctx.surface = "BODY_COAT"
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.draw_geometry(torso, M.mesh("body"), Vector3.ZERO, Vector3(breath, 1, breath * (1.0 + 0.07 * pose["gestureWeight"])), M.COAT)
	ctx.surface = "COAT"
	for feather_index in range(-2, 3):
		var spread := float(feather_index)
		ctx.draw_geometry(torso, M.mesh("feather"), Vector3(spread * 0.065, 0.02, -0.38),
			Vector3(1, 1.20 - absf(spread) * 0.10, 1), M.TAIL if feather_index % 2 == 0 else M.TEAL,
			Vector3(-28.0 + pose["secondaryAppendageDegrees"], 0, spread * 19.0))
	for side in [-1.0, 1.0]:
		_wing(ctx, torso, side, pose)
	var neck := G.translate(torso, 0, 0.30, 0)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.30, 0)
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.GOLD)
	for feather_index in range(-2, 3):
		ctx.draw_part(neck, Vector3(feather_index * 0.087, 0.36, 0.24), Vector3(0.055, 0.17, 0.035), M.COAT, Vector3(0, 0, feather_index * 9.0))
	ctx.prepare_head(neck, 0.97, pose["headTiltDegrees"])
	_face(ctx, ctx.head, pose)
	ctx.surface = previous

static func _wing(ctx, parent: Transform3D, side: float, pose: Dictionary) -> void:
	var wing := G.translate(parent, side * 0.37, 0.19, -0.02)
	wing = G.rotate(wing, side * (12.0 + pose["primaryAppendageDegrees"]), 0, 0, 1)
	ctx.draw_geometry(wing, M.mesh("wing"), Vector3.ZERO, Vector3.ONE, M.COAT)
	for feather_index in 3:
		ctx.draw_part(wing, Vector3(side * (feather_index - 1) * 0.07, -0.23, 0.06), Vector3(0.045, 0.17, 0.035),
			M.TEAL if feather_index == 1 else M.TAIL, Vector3(0, 0, side * (feather_index - 1) * 10.0))

static func _feet(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var tuck := clampf(pose["legTuckDegrees"] / 38.0, 0.0, 1.0)
	ctx.surface = "NOSE"
	for side in [-1.0, 1.0]:
		var foot := G.translate(parent, side * 0.19, -0.37, 0.02)
		foot = G.rotate(foot, side * pose["legPaddleDegrees"], 1, 0, 0)
		ctx.draw_part(foot, Vector3(0, -0.17 + tuck * 0.07, 0), Vector3(0.045, 0.18 - tuck * 0.07, 0.045), M.GOLD)
		for toe_index in range(-1, 2):
			ctx.draw_part(foot, Vector3(toe_index * 0.065, -0.37 + tuck * 0.14, 0.10), Vector3(0.029, 0.04, 0.15), M.GOLD, Vector3(0, toe_index * 22.0, 0))
		ctx.draw_part(foot, Vector3(0, -0.37 + tuck * 0.14, -0.09), Vector3(0.025, 0.035, 0.09), M.GOLD)

static func _face(ctx, parent: Transform3D, pose: Dictionary) -> void:
	ctx.surface = "COAT"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.GOLD)
	for side in [-1.0, 1.0]:
		ctx.surface = "EYE"
		ctx.draw_living_eye(parent, side * 0.155, 0.99, 0.245, 0.095, 0.115, M.RED, Mat.WHITE, Mat.EYE_DARK, false, false)
		ctx.surface = "NOSE"
		ctx.draw_part(parent, Vector3(side * 0.067, 0.66, 0.235), Vector3(0.065, 0.14, 0.06), M.RED)
	for comb_index in 4:
		ctx.draw_part(parent, Vector3(0, 1.20 + (0.10 if comb_index == 1 or comb_index == 2 else 0.04), 0.14 - comb_index * 0.105),
			Vector3(0.065, 0.14, 0.09), M.RED)
	var opening := clampf((ctx.life.mouth_open if ctx.life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	ctx.surface = "NOSE"
	ctx.draw_geometry(parent, M.mesh("beak"), Vector3(0, 0.84, 0.245), Vector3.ONE, M.GOLD, Vector3(90, 0, 0))
	var jaw := G.translate(parent, 0, 0.80, 0.245)
	jaw = G.rotate(jaw, opening * 25.0, 1, 0, 0)
	ctx.draw_geometry(jaw, M.mesh("beak"), Vector3.ZERO, Vector3(0.85, 0.85, 0.50), M.COAT, Vector3(90, 0, 0))
