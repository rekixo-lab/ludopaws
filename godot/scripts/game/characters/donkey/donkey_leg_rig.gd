@tool
extends RefCounted
## Analytic two-bone IK; hooves stay planted in idle. Port of DonkeyLegRig.kt.

const UPPER_LENGTH := 0.265
const LOWER_LENGTH := 0.285
const GROUND_ANKLE_Y := -0.74

static func solve(rear: bool, side: float, stride_degrees: float, tuck_degrees: float, crouch := 0.0, paired_stride := false) -> PackedFloat32Array:
	var hip_y := (-0.26 if rear else -0.24) - clampf(crouch, 0.0, 0.14)
	var hip_z := -0.43 if rear else 0.30
	var tuck := clampf(tuck_degrees / 38.0, 0.0, 1.0)
	var diagonal := 1.0 if paired_stride else (-side if rear else side)
	var ankle_y := GROUND_ANKLE_Y + tuck * 0.17
	var ankle_z := hip_z + (-0.045 if rear else 0.035) + sin(deg_to_rad(stride_degrees * diagonal)) * 0.10 + tuck * (-0.08 if rear else 0.08)
	return solve_foot(rear, hip_y, hip_z, ankle_y, ankle_z)

## Returns [hipY, hipZ, kneeY, kneeZ, ankleY, ankleZ]; donkey knees bend the other way.
static func solve_foot(rear: bool, hip_y: float, hip_z: float, ankle_y: float, ankle_z: float) -> PackedFloat32Array:
	var dy := ankle_y - hip_y
	var dz := ankle_z - hip_z
	var raw := maxf(sqrt(dy * dy + dz * dz), 0.0001)
	var distance := clampf(raw, 0.05, UPPER_LENGTH + LOWER_LENGTH - 0.0001)
	var along := (UPPER_LENGTH * UPPER_LENGTH - LOWER_LENGTH * LOWER_LENGTH + distance * distance) / (2.0 * distance)
	var height := sqrt(maxf(UPPER_LENGTH * UPPER_LENGTH - along * along, 0.0))
	var uy := dy / raw
	var uz := dz / raw
	var bend := 1.0 if rear else -1.0
	return PackedFloat32Array([hip_y, hip_z,
		hip_y + uy * along - uz * height * bend, hip_z + uz * along + uy * height * bend,
		hip_y + uy * distance, hip_z + uz * distance])
