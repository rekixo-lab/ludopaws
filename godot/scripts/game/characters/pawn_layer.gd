@tool
extends RefCounted
## Draws the 3D animal pawns. Port of LudoPaws3DSceneRenderer.drawPawn / prepareRoot /
## drawContactShadow / idleMotionFrame and LudoPawsPawnFacingPolicy. Presentation only.
##
## Pawn dict: {key, character_id, seat, token, x, y, radius, yaw, frame:{kind, progress, playback},
##             presentation_scale, attention_scale, focus: Vector2}

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const D = preload("res://scripts/game/board3d/paw_draw.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Life = preload("res://scripts/game/characters/shared/life_motion.gd")
const Living = preload("res://scripts/game/characters/shared/living_behavior.gd")
const Catalog = preload("res://scripts/game/characters/catalog.gd")

## 0.72 -> 1.20 is the live 1.67x visual enlargement.
const MODEL_SCALE_PER_RADIUS := 1.20
const SHADOW_OUTER := Color(0.025, 0.030, 0.040, 0.07)
const SHADOW_INNER := Color(0.020, 0.025, 0.035, 0.16)
## Each animal gets its own depth band (the live renderer clears depth per pawn).
const PAWN_DEPTH_BAND := 2.0
const FRONT_YAW := 0.0
const RETURN_TO_PLAYER_MS := 180
const TURN_PORTION_OF_HOP := 0.28

var pool: D.Pool
var reduced_motion := false
var _life := {}

func _init(target_pool: D.Pool) -> void:
	pool = target_pool

## pawns must already be sorted by screen y (back to front).
func draw(pawns: Array, now_ms: int, cell: float, board_size: Vector2) -> void:
	pool.begin()
	var live_keys := {}
	for pawn in pawns:
		live_keys[pawn["key"]] = true
	for key in _life.keys():
		if not live_keys.has(key):
			_life.erase(key)
	# Shadows first, in their own pass, so one pawn's shadow never darkens another pawn.
	for pawn in pawns:
		_contact_shadow(pawn, cell)
	var band := 1.0
	for pawn in pawns:
		pool.depth_offset = band
		band += PAWN_DEPTH_BAND
		_draw_pawn(pawn, now_ms, board_size)
	pool.depth_offset = 0.0
	pool.life = null
	pool.end()

func _draw_pawn(pawn: Dictionary, now_ms: int, board_size: Vector2) -> void:
	var def = Catalog.definition(pawn["character_id"])
	if def == null:
		return
	var renderer = def.Renderer
	var frame: Dictionary = pawn["frame"]
	var reaction_target: Dictionary = pawn.get("reaction", P.reaction())
	var life: Life = _life.get(pawn["key"])
	if life == null:
		life = Life.new()
		_life[pawn["key"]] = life
	life.reduced_motion = reduced_motion
	pool.surface = "MATTE"
	pool.life = life
	var focus: Vector2 = pawn.get("focus", board_size * 0.5)
	var reaction := life.update(now_ms, int(pawn["seat"]) * 397 + int(pawn["token"]) * 103,
		pawn.get("idle_allowed", frame["kind"] == P.IDLE),
		(focus.x - pawn["x"]) / (board_size.x * 0.35),
		(pawn["y"] - focus.y) / (board_size.y * 0.35), reaction_target)
	var ground_pivot: float = def.GROUND_PIVOT_Y
	var model_scale: float = pawn["radius"] * MODEL_SCALE_PER_RADIUS * pawn.get("presentation_scale", 1.0) \
		* pawn.get("attention_scale", 1.0) * reaction["scale"]
	# The board coordinate is the FOOT CONTACT, not the body origin.
	var root_y: float = pawn["y"] + model_scale * ground_pivot
	var sampled: Dictionary = renderer.sample(frame["kind"], frame["progress"], frame.get("playback", P.ISOLATED))
	var base: Dictionary
	if reduced_motion and frame["kind"] != P.HOP:
		base = P.pose()
	elif frame["kind"] == P.IDLE:
		base = life.idle_pose(sampled, def.ID)
	else:
		base = sampled
	var pose := P.copy(base, {
		"liftY": base["liftY"] + reaction["liftY"],
		"bodyYawDegrees": pawn["yaw"] + base["bodyYawDegrees"] + reaction["bodyYawDegrees"],
		"headTiltDegrees": base["headTiltDegrees"] + reaction["headTiltDegrees"],
		"primaryAppendageDegrees": life.follow_primary(base["primaryAppendageDegrees"] + reaction["primaryAppendageDegrees"] + life.shake),
		"secondaryAppendageDegrees": life.follow_secondary(base["secondaryAppendageDegrees"] + reaction["secondaryAppendageDegrees"] + life.shake),
		"tertiaryAppendageDegrees": life.follow_tertiary(base["tertiaryAppendageDegrees"] + reaction["tertiaryAppendageDegrees"] + life.shake),
		"bodyPitchDegrees": base["bodyPitchDegrees"] + reaction["bodyPitchDegrees"],
		"neckPitchDegrees": base["neckPitchDegrees"] + reaction["neckPitchDegrees"],
		"jawOpen": clampf(base["jawOpen"] + reaction["jawOpen"], 0.0, 1.0),
	})
	var root := _root(pawn["x"], root_y, model_scale, pose["liftY"], pose["bodyYawDegrees"], base["bodyScaleY"], ground_pivot)
	renderer.draw(pool, root, pose, pawn.get("team", ""))

## prepareRoot: screen Y grows down, so local Y is mirrored to keep +Y-up anatomy upright.
static func _root(x: float, y: float, scale: float, lift: float, yaw: float, stretch: float, pivot: float) -> Transform3D:
	var m := G.translate(Transform3D.IDENTITY, x, y, 0)
	m = G.scale(m, scale, -scale, scale)
	if lift != 0.0:
		m = G.translate(m, 0, lift, 0)
	if yaw != 0.0:
		m = G.rotate(m, yaw, 0, 1, 0)
	if stretch != 1.0:
		# Compress around the feet, preserving volume instead of resizing the animal.
		var width := 1.0 / sqrt(stretch)
		m = G.translate(m, 0, pivot, 0)
		m = G.scale(m, width, stretch, width)
		m = G.translate(m, 0, -pivot, 0)
	return m

func _contact_shadow(pawn: Dictionary, cell: float) -> void:
	var radius := minf(cell * 0.30, pawn["radius"] * pawn.get("presentation_scale", 1.0) * 0.70)
	var root := G.translate(Transform3D.IDENTITY, pawn["x"], pawn["y"], -0.80)
	pool.surface = "MATTE"
	pool.draw_part(root, Vector3.ZERO, Vector3(radius, radius * 0.36, 0.02), SHADOW_OUTER)
	pool.draw_part(root, Vector3(0, 0, 0.01), Vector3(radius * 0.75, radius * 0.24, 0.02), SHADOW_INNER)

# ---------------------------------------------------------------- frames and facing

static func idle_frame(character_id: String, seat: int, token: int, now_ms: int) -> Dictionary:
	var def = Catalog.definition(character_id)
	var base: int = def.IDLE_DURATION_MS if def != null else 2000
	var duration := Living.idle_duration_ms(base, seat * 4 + token)
	var phase_offset := ((seat * 4 + token) * 173) % duration
	return {"kind": P.IDLE, "progress": float((now_ms + phase_offset) % duration) / duration}

static func yaw_for_visible_delta(dx: float, dy: float) -> float:
	if absf(dx) <= 0.0001 and absf(dy) <= 0.0001:
		return FRONT_YAW
	# Animals face the viewer at yaw 0; screen Y grows down: down=0, right=+90, up=180, left=-90.
	return rad_to_deg(atan2(dx, dy))

static func moving_yaw(previous: float, target: float, step_progress: float) -> float:
	var turn := clampf(clampf(step_progress, 0.0, 1.0) / TURN_PORTION_OF_HOP, 0.0, 1.0)
	return interpolate_yaw(previous, target, _smooth(turn))

static func settling_yaw(last_travel: float, elapsed_after_ms: int) -> float:
	var progress := clampf(maxf(elapsed_after_ms, 0) / float(RETURN_TO_PLAYER_MS), 0.0, 1.0)
	return interpolate_yaw(last_travel, FRONT_YAW, _smooth(progress))

static func interpolate_yaw(from: float, to: float, progress: float) -> float:
	return _normalize(from + _normalize(to - from) * clampf(progress, 0.0, 1.0))

static func _normalize(value: float) -> float:
	var n := fmod(value, 360.0)
	if n > 180.0:
		n -= 360.0
	if n <= -180.0:
		n += 360.0
	return n

static func _smooth(v: float) -> float:
	return v * v * (3.0 - 2.0 * v)
