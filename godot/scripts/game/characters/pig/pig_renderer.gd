@tool
extends RefCounted
## Pig: four short legs with split hooves, curled tail, floppy ears, twitching snout.
## Port of PigRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Hop = preload("res://scripts/game/characters/shared/hop_dynamics.gd")
const M = preload("res://scripts/game/characters/pig/pig_geometry.gd")
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
			pose = P.pose({"headTiltDegrees": 3.0 * wave, "primaryAppendageDegrees": 5.0 * wave,
				"secondaryAppendageDegrees": 9.0 * wave, "neckPitchDegrees": 2.0 * wave})
		P.HOP:
			pose = P.pose({"legSwingDegrees": 22.0 * wave * arc, "frontLegTuckDegrees": 29.0 * arc, "rearLegTuckDegrees": 18.0 * arc,
				"primaryAppendageDegrees": -18.0 * arc, "secondaryAppendageDegrees": 15.0 * arc,
				"bodyPitchDegrees": -5.0 * arc, "bodyRollDegrees": 3.0 * wave * arc, "neckPitchDegrees": 4.0 * arc})
		_:
			pose = P.pose({"liftY": 0.18 * absf(sin(phase * PI * 3.0)) * (1.0 - phase),
				"bodyYawDegrees": 360.0 * phase, "headTiltDegrees": 7.0 * wave,
				"primaryAppendageDegrees": 16.0 * wave, "secondaryAppendageDegrees": 24.0 * wave})
	return Hop.apply(pose, motion, progress, 0.48, playback)

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var rig := G.rotate(parent, -18.0, 0, 1, 0)
	_legs(ctx, rig, pose)
	var torso := G.translate(rig, 0, -pose["bodyCrouch"], 0)
	torso = G.rotate(torso, pose["bodyPitchDegrees"], 1, 0, 0)
	torso = G.rotate(torso, pose["bodyRollDegrees"], 0, 0, 1)
	var breath: float = 1.0 + (ctx.life.breath if ctx.life != null else 0.0)
	ctx.surface = "PIG_SKIN"
	ctx.draw_geometry(torso, M.mesh("body"), Vector3.ZERO, Vector3(breath, 1, breath), M.PINK)
	_tail(ctx, torso, pose["secondaryAppendageDegrees"])
	var neck := G.translate(torso, 0, 0.30, 0.10)
	neck = G.rotate(neck, pose["neckPitchDegrees"], 1, 0, 0)
	neck = G.translate(neck, 0, -0.30, -0.10)
	ctx.draw_geometry(neck, M.mesh("neck"), Vector3.ZERO, Vector3.ONE, M.PINK)
	ctx.prepare_head(neck, 0.85, pose["headTiltDegrees"])
	_face(ctx, ctx.head, pose)
	ctx.surface = previous

static func _legs(ctx, parent: Transform3D, pose: Dictionary) -> void:
	for leg_index in 4:
		var rear := leg_index < 2
		var side := -1.0 if leg_index % 2 == 0 else 1.0
		var leg := G.translate(parent, side * 0.29, -0.20, -0.38 if rear else 0.27)
		var swing: float = (-side if rear else side) * pose["legSwingDegrees"]
		var pair_tuck = pose["rearLegTuckDegrees"] if rear else pose["frontLegTuckDegrees"]
		var tuck := clampf(float(pair_tuck if pair_tuck != null else pose["legTuckDegrees"]) / 38.0, 0.0, 1.0)
		leg = G.rotate(leg, swing, 1, 0, 0)
		leg = G.scale(leg, 1, 1.0 - 0.35 * tuck - 0.30 * pose["bodyCrouch"], 1)
		ctx.surface = "PIG_SKIN"
		ctx.draw_geometry(leg, M.mesh("leg"), Vector3.ZERO, Vector3.ONE, M.PINK)
		ctx.surface = "HOOF"
		for toe_side in [-1.0, 1.0]:
			ctx.draw_geometry(leg, M.mesh("hoof"), Vector3(toe_side * 0.055, -0.50, 0.025), Vector3.ONE, M.HOOF)

static func _face(ctx, parent: Transform3D, pose: Dictionary) -> void:
	var life = ctx.life
	ctx.surface = "PIG_SKIN"
	ctx.draw_geometry(parent, M.mesh("head"), Vector3.ZERO, Vector3.ONE, M.PINK)
	for side in [-1.0, 1.0]:
		var ear := G.translate(parent, side * 0.27, 1.01, 0.015)
		var perk: float = sin((life.time_seconds if life != null else 0.0) * 2.3 + side) * (life.idle_weight if life != null else 0.0)
		ear = G.rotate(ear, -side * (22.0 + pose["primaryAppendageDegrees"] + side * 12.0 * pose["gestureWeight"] + perk * 6.0), 0, 0, 1)
		ear = G.rotate(ear, -6.0 + perk * 6.0, 1, 0, 0)
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3.ZERO, Vector3(side, 1, 1), M.PINK)
		ctx.draw_geometry(ear, M.mesh("ear"), Vector3(0, 0.035, 0.047), Vector3(side * 0.62, 0.75, 0.32), M.BLUSH)
		ctx.surface = "EYE"
		ctx.draw_living_eye(parent, side * 0.21, 0.86, 0.34, 0.082, 0.095, M.PINK, Mat.WHITE, Mat.EYE_DARK, false, false)
		ctx.surface = "PIG_SKIN"
		ctx.draw_part(parent, Vector3(side * 0.30, 0.68, 0.31), Vector3(0.067, 0.037, 0.026), M.BLUSH)
	ctx.surface = "NOSE"
	var twitch: float = 0.015 * pose["gestureWeight"] * sin(pose["gesturePhase"] * PI * 6.0)
	ctx.draw_geometry(parent, M.mesh("snout"), Vector3(twitch, 0.70, 0.30), Vector3.ONE, M.SNOUT, Vector3(90, 0, 0))
	for side in [-1.0, 1.0]:
		var x: float = twitch + side * 0.086
		ctx.draw_part(parent, Vector3(x, 0.701, 0.558), Vector3(0.043, 0.054, 0.010), M.BLUSH, Vector3(0, 0, side * 13.0))
		ctx.draw_part(parent, Vector3(x, 0.704, 0.566), Vector3(0.028, 0.038, 0.008), M.HOOF, Vector3(0, 0, side * 13.0))
	var opening := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
	ctx.surface = "MATTE"
	ctx.draw_part(parent, Vector3(0, 0.535, 0.37), Vector3(0.105, 0.009 + opening * 0.035, 0.018), M.HOOF)
	ctx.surface = "PIG_SKIN"
	ctx.draw_part(parent, Vector3(0, 0.509 - opening * 0.035, 0.36), Vector3(0.12, 0.027, 0.045), M.PINK)

static func _tail(ctx, parent: Transform3D, sway: float) -> void:
	var tail := G.translate(parent, 0.22, 0.105, -0.59)
	tail = G.rotate(tail, sway + 12.0, 0, 1, 0)
	tail = G.rotate(tail, 17.0 + sway * 0.30, 0, 0, 1)
	ctx.surface = "PIG_SKIN"
	ctx.draw_geometry(tail, M.mesh("curledTail"), Vector3.ZERO, Vector3.ONE, M.SNOUT)
