extends RefCounted
## Which authoritative token changes get a pawn animation, and the visual cells a step move
## walks through. Presentation-only; never changes token state.
## Port of LudoPawsPawnAnimationPolicy.transition and LudoPathEncoding visual-step helpers.

const LAST_TRACK := 50
const LEGACY_ENTRY := 51
const FIRST_HOME_LANE := 52
const HOME := 57
const CAPTURE_RETURN_VISUAL_STEPS := 4

## {kind: "forward"|"return", steps} or null (the pawn just appears at its new cell).
static func transition(from: int, to: int) -> Variant:
	if to > from and from >= -1 and to <= HOME:
		return {"kind": "forward", "steps": maxi(visual_step_count(from, to), 1)}
	if from >= 0 and from <= LAST_TRACK and to == -1:
		return {"kind": "return", "steps": CAPTURE_RETURN_VISUAL_STEPS}
	return null

static func visual_step_count(from: int, to: int) -> int:
	var a = _logical(from)
	var b = _logical(to)
	if a == null or b == null:
		return 0
	return maxi(int(b) - int(a), 0)

## LudoPathEncoding.positionAtVisualStep.
static func position_at_visual_step(from: int, step: int) -> Variant:
	if step < 0:
		return null
	var a = _logical(from)
	if a == null:
		return null
	return _encoded(int(a) + step)

## Cells visited by a forward move, ending at the destination (renderer path for `steps`).
static func forward_path(from: int, steps: int) -> Array:
	var path: Array = []
	for k in range(1, maxi(steps, 1) + 1):
		path.append(position_at_visual_step(from, k))
	return path

static func _logical(position: int) -> Variant:
	var p := FIRST_HOME_LANE if position == LEGACY_ENTRY else position
	if p == -1:
		return -1
	if p >= 0 and p <= LAST_TRACK:
		return p
	if p >= FIRST_HOME_LANE and p <= HOME:
		return p - 1
	return null

static func _encoded(position: int) -> Variant:
	if position == -1:
		return -1
	if position >= 0 and position <= LAST_TRACK:
		return position
	if position >= LAST_TRACK + 1 and position <= HOME - 1:
		return position + 1
	return null
