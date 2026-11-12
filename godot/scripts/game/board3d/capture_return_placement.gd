extends RefCounted
## Presentation-only trip of a captured pawn from its old track cell to its yard slot:
## impact shake -> pop -> curved return -> soft settle. Never touches game state.
## Port of LudoPawsCaptureReturnPlacement.kt and LudoPawsPawnAnimationPolicy.captureReturnFrame.

const IMPACT_SHAKE := "IMPACT_SHAKE"
const POP := "POP"
const RETURN_TO_YARD := "RETURN_TO_YARD"
const SETTLE := "SETTLE"

const IMPACT_END := 0.18
const POP_END := 0.34
const RETURN_END := 0.90
const CONTACT_END := 0.34
const CONTACT_PUSH_CELLS := 0.14
const CURVE_CELLS := 0.34

## LudoPawsPawnAnimationPolicy.captureReturnFrame.
static func frame(progress: float) -> Dictionary:
	var safe := clampf(progress, 0.0, 1.0)
	if safe < IMPACT_END:
		var local := safe / IMPACT_END
		return {"phase": IMPACT_SHAKE, "route": 0.0, "shake": sin(local * PI * 6.0) * 0.10 * (1.0 - local * 0.35),
			"lift": 0.0, "scale": 1.0 + sin(local * PI) * 0.05}
	if safe < POP_END:
		var pop := sin((safe - IMPACT_END) / (POP_END - IMPACT_END) * PI)
		return {"phase": POP, "route": 0.0, "shake": 0.0, "lift": pop * 0.24, "scale": 1.0 + pop * 0.18}
	if safe < RETURN_END:
		var local := (safe - POP_END) / (RETURN_END - POP_END)
		var inverse := 1.0 - clampf(local, 0.0, 1.0)
		var routed := 1.0 - inverse * inverse * inverse
		return {"phase": RETURN_TO_YARD, "route": routed, "shake": 0.0, "lift": sin(local * PI) * 0.58,
			"scale": 1.05 - routed * 0.13}
	var local := clampf((safe - RETURN_END) / (1.0 - RETURN_END), 0.0, 1.0)
	return {"phase": SETTLE, "route": 1.0, "shake": 0.0, "lift": sin(local * PI) * 0.11 * (1.0 - local),
		"scale": 0.92 + 0.08 * local + sin(local * PI) * 0.04}

## LudoPawsCaptureReturnPlacement.sample: {point, scale, phase} in the same 2D board space
## as `from`/`to` (y grows down the board, so lift moves the pawn toward the top edge).
static func sample(from: Vector2, to: Vector2, cell: float, progress: float) -> Dictionary:
	var safe := clampf(progress, 0.0, 1.0)
	var f := frame(safe)
	var route := clampf(f["route"], 0.0, 1.0)
	var linear := from + (to - from) * route
	var d := to - from
	var distance := d.length()
	var direction := d / distance if distance > 0.001 else Vector2.ZERO
	var perpendicular := Vector2(-d.y, d.x) / distance if distance > 0.001 else Vector2.ZERO
	var curve := sin(route * PI) * cell * CURVE_CELLS if distance > 0.001 else 0.0
	# Contact push: the pawn is shoved away from the square and rebounds before the trip.
	var push := sin(safe / CONTACT_END * PI) * cell * CONTACT_PUSH_CELLS if safe < CONTACT_END else 0.0
	var point := linear + direction * push + perpendicular * curve + Vector2(f["shake"] * cell, -f["lift"] * cell)
	return {"point": point, "scale": f["scale"], "phase": f["phase"]}
