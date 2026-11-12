@tool
extends RefCounted
## A white kid with an independent head, listening ears and compact planted hooves.
## Port of LumiGoatRenderer.kt (sample reuses the Goat timeline).

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const M = preload("res://scripts/game/characters/goat_lumi/lumi_geometry.gd")
const Goat = preload("res://scripts/game/characters/goat/goat_renderer.gd")

const REST_BODY_YAW := 13.0
const LEG_LENGTH_SCALE := 0.5
const LEG_LENGTH_ANCHOR_Y := 0.42
const MODEL_SIZE_MULTIPLIER := 1.38

static func step_travel_progress(progress: float) -> float:
	return Goat.step_travel_progress(progress)

static func sample(motion: String, progress: float, playback := P.ISOLATED) -> Dictionary:
	var base := Goat.sample(motion, progress, playback)
	if motion != P.IDLE:
		return base
	var wave := sin(clampf(progress, 0.0, 1.0) * TAU)
	return P.copy(base, {"headTiltDegrees": 5.5 * wave, "neckPitchDegrees": 3.5 * wave,
		"primaryAppendageDegrees": 9.0 * wave, "tertiaryAppendageDegrees": 15.0 * wave})

static func draw(ctx, parent: Transform3D, pose: Dictionary, _team: String) -> void:
	var previous: String = ctx.surface
	var life = ctx.life
	var stance := G.scale(parent, MODEL_SIZE_MULTIPLIER, MODEL_SIZE_MULTIPLIER, MODEL_SIZE_MULTIPLIER)
	stance = G.rotate(stance, REST_BODY_YAW, 0, 1, 0)
	var body := G.translate(stance, 0, -pose["bodyCrouch"] * 0.45, 0)
	body = G.rotate(body, pose["bodyPitchDegrees"] * 0.5, 1, 0, 0)
	body = G.rotate(body, pose["bodyRollDegrees"] * 0.5, 0, 0, 1)
	var breath: float = 1.0 + (life.breath if life != null else 0.0)
	body = G.scale(body, breath, 1, breath)
	var face := G.translate(body, 0, 1.0, 0.20)
	face = G.rotate(face, (pose["headTiltDegrees"] + (life.head_tilt if life != null else 0.0)) * 0.45, 0, 0, 1)
	face = G.rotate(face, -REST_BODY_YAW, 0, 1, 0)
	face = G.rotate(face, (life.head_pitch if life != null else 0.0) * 0.45 + pose["neckPitchDegrees"] * 0.3, 1, 0, 0)
	face = G.translate(face, 0, -1.0, -0.20)
	var batches := M.batches()
	for rig in M.RIGS:
		var transform: Transform3D = face if rig in M.HEAD_ATTACHED else body
		match rig:
			"EYE_LEFT", "EYE_RIGHT":
				var side := -1.0 if rig == "EYE_LEFT" else 1.0
				# Enlarge lid, iris, pupil and catchlight as one eye seated inside the head.
				transform = G.translate(transform, side * 0.165, 1.066, 0.315)
				transform = G.scale(transform, 1.70, 1.70 * maxf(life.blink if life != null else 1.0, 0.035), 1.08)
				transform = G.translate(transform, (life.gaze_x if life != null else 0.0) * 0.06, (life.gaze_y if life != null else 0.0) * 0.05, 0)
				transform = G.translate(transform, -side * 0.188, -1.066, -0.30)
			"EAR_LEFT", "EAR_RIGHT":
				var side := -1.0 if rig == "EAR_LEFT" else 1.0
				transform = G.translate(transform, side * 0.19, 1.17, 0.09)
				var listen: float = sin((life.time_seconds if life != null else 0.0) * 1.65 + side * 1.2) * (life.idle_weight if life != null else 0.0)
				var gesture: float = (14.0 if side < 0.0 else -6.0) * pose["gestureWeight"]
				transform = G.rotate(transform, side * (pose["primaryAppendageDegrees"] * 0.58 + listen * 5.0 + gesture), 0, 0, 1)
				transform = G.rotate(transform, listen * 7.0, 1, 0, 0)
				transform = G.translate(transform, -side * 0.19, -1.17, -0.09)
			"FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT":
				var front: bool = rig.begins_with("FRONT")
				var side := -1.0 if rig.ends_with("LEFT") else 1.0
				# Hooves stay with the contact frame; legs swing from the embedded shoulder/hip.
				transform = stance
				var x := side * (0.085 if front else 0.13)
				var y := 0.72 if front else 0.65
				var z := -0.01 if front else -0.485
				var pivot := LEG_LENGTH_ANCHOR_Y + (y - LEG_LENGTH_ANCHOR_Y) * LEG_LENGTH_SCALE
				var pair_tuck = pose["frontLegTuckDegrees"] if front else pose["rearLegTuckDegrees"]
				var tuck := clampf(float(pair_tuck if pair_tuck != null else pose["legTuckDegrees"]) / 38.0, 0.0, 1.0)
				transform = G.translate(transform, x, pivot + 0.09 * tuck, z)
				var swing = pose["frontLegSwingDegrees"] if front else pose["rearLegSwingDegrees"]
				transform = G.rotate(transform, float(swing if swing != null else pose["legSwingDegrees"] * side) * 0.67, 1, 0, 0)
				transform = G.scale(transform, 1, LEG_LENGTH_SCALE, 1)
				transform = G.translate(transform, -x, -y, -z)
			"TAIL":
				transform = G.translate(transform, 0, 0.65, -0.77)
				transform = G.rotate(transform, pose["tertiaryAppendageDegrees"], 0, 1, 0)
				transform = G.translate(transform, 0, -0.65, 0.77)
			"MOUTH":
				var opening := clampf((life.mouth_open if life != null else 0.0) + pose["jawOpen"], 0.0, 1.0)
				transform = G.translate(transform, 0, 0.81 - 0.018 * opening, 0.48 + 0.008 * opening)
				transform = G.scale(transform, 1, 1.0 + opening * 0.24, 1)
				transform = G.translate(transform, 0, -0.81, -0.48)
		for batch in batches.get(rig, []):
			ctx.surface = M.surface(batch[0])
			ctx.draw_geometry(transform, batch[1], Vector3.ZERO, Vector3.ONE, M.color(batch[0]))
	ctx.surface = previous
