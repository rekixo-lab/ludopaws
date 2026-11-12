@tool
extends RefCounted
## Lumi Goat anatomy: continuous lofted parts pre-transformed per rig, with procedural fur strands,
## merged into one batch per (rig, tint). Port of LumiGoatGeometry / LumiRig / LumiTint in
## LumiGoatRenderer.kt. Rings: [y, rx, rz, cx, cz, roundness].

const G = preload("res://scripts/game/board3d/paw_geometry.gd")

## LumiRig order (draw order); head-attached rigs follow the independent face transform.
const RIGS := ["TAIL", "REAR_LEFT", "REAR_RIGHT", "BODY", "FRONT_LEFT", "FRONT_RIGHT",
	"HEAD", "EAR_LEFT", "EAR_RIGHT", "MOUTH", "EYE_LEFT", "EYE_RIGHT"]
const HEAD_ATTACHED := ["HEAD", "EAR_LEFT", "EAR_RIGHT", "MOUTH", "EYE_LEFT", "EYE_RIGHT"]
## LumiTint: [hex, surface]
const TINTS := {
	"FUR": [0xEEEAE2, "LUMI_FUR"], "FLEECE_TIP": [0xF8F2E7, "LUMI_FUR"], "INNER": [0xC68C86, "LUMI_FUR"],
	"HORN": [0xA79B85, "LUMI_GLOSS"], "HOOF": [0x393934, "LUMI_GLOSS"], "LID": [0x59564C, "LUMI_FUR"],
	"IRIS": [0x96603A, "LUMI_EYE"], "PUPIL": [0x171612, "LUMI_EYE"], "GLINT": [0xF8F6ED, "LUMI_EYE"],
	"NOSE": [0x57564E, "LUMI_GLOSS"], "LIP": [0x46443E, "LUMI_GLOSS"],
}

static var _batches := {}

static func color(tint: String) -> Color:
	return G.rgb(TINTS[tint][0])

static func surface(tint: String) -> String:
	return TINTS[tint][1]

## rig -> [[tint, Geo], ...] in bucket insertion order.
static func batches() -> Dictionary:
	if _batches.is_empty():
		_build()
	return _batches

## Named meshes for parity tests: "RIG/TINT" -> Geo.
static func mesh(name: String) -> G.Geo:
	var parts := name.split("/")
	for batch in batches().get(parts[0], []):
		if batch[0] == parts[1]:
			return batch[1]
	return null

static func _f32(x: float) -> float:
	return PackedFloat32Array([x])[0]

static func _build() -> void:
	var buckets := {}
	var order: Array = []
	for part in _parts():
		var geo := _transform(part)
		var key: String = part["rig"] + "/" + part["tint"]
		if not buckets.has(key):
			buckets[key] = []
			order.append(key)
		buckets[key].append(geo)
		if part["fur"] > 0:
			buckets[key].append(_fur(geo, part["fur"]))
	for key in order:
		var rig: String = key.get_slice("/", 0)
		if not _batches.has(rig):
			_batches[rig] = []
		_batches[rig].append([key.get_slice("/", 1), _merge(buckets[key])])

static func _part(list: Array, rig: String, tint: String, x: float, y: float, z: float, rx: float, rz: float, fur: int, points: Array) -> void:
	var rings: Array = []
	for i in range(0, points.size(), 5):
		rings.append([points[i], points[i + 1], points[i + 2], points[i + 3], points[i + 4], 0.45 if tint == "HOOF" else 1.0])
	rings = _round_anatomy(rig, tint, rx, rings)
	list.append({"rig": rig, "tint": tint, "geo": G.loft(rings, 24, 3), "x": x, "y": y, "z": z, "rx": rx, "rz": rz, "fur": fur})

static func _parts() -> Array:
	var p: Array = []
	_part(p, "BODY", "FUR", -0.015, 0.51, -0.28, 90, 0, 2300, [
		-0.58, 0.0392, 0.0672, 0, 0, -0.49, 0.2016, 0.2688, 0, 0,
		-0.27, 0.2968, 0.3696, 0, -0.025, 0.04, 0.3024, 0.3808, 0, -0.02,
		0.29, 0.28, 0.3584, 0, 0, 0.48, 0.2016, 0.2576, 0, 0, 0.57, 0.028, 0.056, 0, 0])
	_part(p, "BODY", "FUR", 0, 0, 0, 0, 0, 1100, [
		0.30, 0.084, 0.1064, 0, 0.08, 0.44, 0.224, 0.2464, 0, 0.13,
		0.66, 0.2352, 0.2352, 0, 0.15, 0.88, 0.1904, 0.1792, 0, 0.18, 0.99, 0.0784, 0.0896, 0, 0.20])
	_part(p, "HEAD", "FUR", 0, 0, 0, 0, 0, 3500, [
		0.76, 0.055, 0.065, 0, 0.40, 0.81, 0.135, 0.10, 0, 0.40,
		0.88, 0.178, 0.14, 0, 0.34, 0.99, 0.232, 0.18, 0, 0.23,
		1.08, 0.25, 0.20, 0, 0.13, 1.24, 0.205, 0.145, 0, 0.09,
		1.31, 0.09, 0.065, 0, 0.075, 1.33, 0.012, 0.014, 0, 0.07])
	_part(p, "HEAD", "FLEECE_TIP", -0.064, 1.255, 0.208, 22, 17, 290, [
		-0.067, 0.010, 0.008, 0.032, 0.035, -0.032, 0.046, 0.019, 0.022, 0.031,
		0.018, 0.063, 0.028, 0, 0, 0.067, 0.036, 0.028, -0.017, -0.028,
		0.081, 0.008, 0.009, -0.021, -0.037])
	_part(p, "BODY", "FLEECE_TIP", 0, 0.52, 0.335, -18, 0, 300, [
		-0.16, 0.014, 0.011, 0, 0.016, -0.10, 0.077, 0.039, 0, 0.018,
		-0.01, 0.129, 0.059, 0, 0.012, 0.08, 0.107, 0.068, 0, -0.009,
		0.13, 0.044, 0.037, 0, -0.023])
	_part(p, "HEAD", "NOSE", 0, 0, 0, 0, 0, 0, [
		0.81, 0.018, 0.017, 0, 0.493, 0.837, 0.063, 0.034, 0, 0.510,
		0.884, 0.088, 0.042, 0, 0.484, 0.918, 0.062, 0.020, 0, 0.457, 0.927, 0.012, 0.006, 0, 0.447])
	_part(p, "MOUTH", "LIP", 0, 0, 0, 0, 90, 0, [
		-0.082, 0.002, 0.003, 0.789, 0.48, -0.048, 0.003, 0.004, 0.773, 0.495,
		0, 0.003, 0.004, 0.768, 0.50, 0.048, 0.003, 0.004, 0.773, 0.495, 0.082, 0.002, 0.003, 0.789, 0.48])
	_part(p, "TAIL", "FUR", 0, 0.65, -0.77, -28, 0, 450, [
		-0.04, 0.045, 0.055, 0, 0, 0.04, 0.073, 0.075, 0, 0,
		0.16, 0.055, 0.050, 0, -0.025, 0.24, 0.008, 0.009, 0, -0.05])
	for side in [-1.0, 1.0]:
		var ear := "EAR_LEFT" if side < 0.0 else "EAR_RIGHT"
		var eye := "EYE_LEFT" if side < 0.0 else "EYE_RIGHT"
		var front := "FRONT_LEFT" if side < 0.0 else "FRONT_RIGHT"
		var rear := "REAR_LEFT" if side < 0.0 else "REAR_RIGHT"
		_part(p, ear, "FUR", side * 0.19, 1.17, 0.09, -6, -side * 61.0, 400, [
			0, 0.014, 0.016, 0, 0, 0.10, 0.075, 0.027, 0, 0.012,
			0.22, 0.090, 0.022, 0, 0.015, 0.34, 0.052, 0.014, 0, 0.003, 0.41, 0.003, 0.003, 0, -0.01])
		_part(p, ear, "INNER", side * 0.19, 1.17, 0.128, -6, -side * 61.0, 0, [
			0.055, 0.008, 0.004, 0, 0, 0.14, 0.050, 0.006, 0, 0.008,
			0.24, 0.054, 0.005, 0, 0.008, 0.33, 0.024, 0.004, 0, 0, 0.365, 0.002, 0.002, 0, -0.004])
		_part(p, "HEAD", "HORN", side * 0.13, 1.265, 0.035, -12, -side * 9.0, 0, [
			0, 0.034, 0.037, 0, 0, 0.06, 0.030, 0.031, 0, -0.004,
			0.10, 0.019, 0.020, 0, -0.018, 0.14, 0.004, 0.005, 0, -0.032])
		_part(p, front, "FUR", side * 0.18, 0, 0.18, 0, -side * 2.0, 650, [
			-0.86, 0.0528, 0.0624, 0, 0.015, -0.67, 0.0576, 0.0624, 0, 0.006,
			-0.49, 0.0648, 0.072, -side * 0.004, 0, -0.27, 0.072, 0.084, -side * 0.010, -0.010,
			-0.04, 0.0876, 0.114, -side * 0.025, -0.045, 0.19, 0.1104, 0.156, -side * 0.050, -0.13,
			0.44, 0.1248, 0.192, -side * 0.075, -0.22, 0.72, 0.102, 0.168, -side * 0.095, -0.24,
			0.86, 0.042, 0.084, -side * 0.11, -0.27])
		_part(p, rear, "FUR", side * 0.225, 0, -0.56, 0, side * 2.0, 600, [
			-0.85, 0.0504, 0.06, 0, -0.02, -0.62, 0.0552, 0.0624, 0, -0.008,
			-0.43, 0.0684, 0.0744, -side * 0.008, 0.023, -0.20, 0.0888, 0.114, -side * 0.025, 0.050,
			0.06, 0.1116, 0.156, -side * 0.045, 0.060, 0.35, 0.126, 0.18, -side * 0.065, 0.065,
			0.65, 0.102, 0.156, -side * 0.095, 0.075, 0.86, 0.036, 0.072, -side * 0.115, 0.080])
		for toe in [-1.0, 1.0]:
			_part(p, front, "HOOF", side * 0.18 + toe * 0.027, -0.90, 0.202, 0, 0, 0, [
				-0.06, 0.0258, 0.0694, 0, 0.012, -0.03, 0.028, 0.0728, 0, 0.008,
				0.03, 0.0258, 0.056, 0, 0, 0.06, 0.0168, 0.037, 0, -0.005])
			_part(p, rear, "HOOF", side * 0.225 + toe * 0.026, -0.89, -0.57, 0, 0, 0, [
				-0.06, 0.0246, 0.065, 0, 0.008, -0.02, 0.0269, 0.0672, 0, 0.008,
				0.04, 0.0224, 0.0504, 0, 0, 0.06, 0.0134, 0.0314, 0, -0.005])
		_part(p, eye, "LID", side * 0.188, 1.066, 0.286, 0, -side * 13.0, 0, [
			-0.042, 0.006, 0.005, 0, 0, -0.026, 0.036, 0.012, 0, 0,
			0, 0.047, 0.017, 0, 0, 0.028, 0.034, 0.011, 0, 0, 0.042, 0.004, 0.004, 0, 0])
		_part(p, eye, "IRIS", side * 0.188, 1.066, 0.300, 0, -side * 13.0, 0, [
			-0.032, 0.004, 0.004, 0, 0, -0.018, 0.027, 0.012, 0, 0,
			0.010, 0.031, 0.015, 0, 0, 0.029, 0.014, 0.007, 0, 0, 0.034, 0.002, 0.002, 0, 0])
		_part(p, eye, "PUPIL", side * 0.188, 1.068, 0.314, 0, 0, 0, [
			-0.011, 0.006, 0.003, 0, 0, -0.006, 0.024, 0.006, 0, 0,
			0.006, 0.024, 0.006, 0, 0, 0.011, 0.006, 0.003, 0, 0])
		_part(p, eye, "GLINT", side * 0.188 - 0.009, 1.080, 0.324, 0, 0, 0, [
			-0.007, 0.002, 0.002, 0, 0, 0, 0.006, 0.004, 0, 0, 0.007, 0.002, 0.002, 0, 0])
		_part(p, "HEAD", "LIP", side * 0.060, 0.868, 0.528, 0, -side * 18.0, 0, [
			-0.014, 0.004, 0.003, 0, 0, 0, 0.016, 0.008, 0, 0, 0.014, 0.005, 0.003, 0, 0])
	return p

## roundAnatomy: add volume in each part's own axes before its rig transform.
static func _round_anatomy(rig: String, tint: String, rx: float, rings: Array) -> Array:
	var out: Array = []
	var sole: float = rings[0][0]
	for r in rings:
		var ring: Array = r.duplicate()
		if rig == "HEAD" and tint == "FUR":
			var extra: float = ring[2] * 0.50
			ring[2] += extra
			ring[4] -= extra
		elif (rig == "EAR_LEFT" or rig == "EAR_RIGHT") and tint == "FUR":
			var extra: float = ring[2] * 1.4
			ring[2] += extra
			ring[4] -= extra
		elif rig == "BODY" and tint == "FUR":
			if rx == 90.0:
				ring[0] *= 1.12
			else:
				var extra: float = ring[2] * 0.22
				ring[2] += extra
				ring[4] -= extra
		elif tint == "HOOF":
			ring[0] = sole + (ring[0] - sole) * 1.7
		elif rig in ["FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT"] and tint == "FUR":
			ring[2] *= 1.22
		elif rig == "TAIL" and tint == "FUR":
			ring[2] *= 1.2
		out.append(ring)
	return out

static func _transform(part: Dictionary) -> G.Geo:
	var m := G.translate(Transform3D.IDENTITY, part["x"], part["y"], part["z"])
	m = G.rotate(m, part["rx"], 1, 0, 0)
	m = G.rotate(m, part["rz"], 0, 0, 1)
	var normal_basis := m.basis.inverse().transposed()
	var src: G.Geo = part["geo"]
	var out := G.Geo.new()
	out.i = src.i
	for k in src.v.size():
		out.v.append(m * src.v[k])
		out.n.append(normal_basis * src.n[k])
	return out

## Procedural hair strands on a transformed part (one triangle per strand).
static func _fur(mesh: G.Geo, count: int) -> G.Geo:
	var out := G.Geo.new()
	var triangles := mesh.i.size() / 3
	for hair in count:
		var tri := ((hair * 37) % triangles) * 3
		var a := mesh.i[tri]
		var b := mesh.i[tri + 1]
		var c := mesh.i[tri + 2]
		var u := clampf(fmod(_f32(_f32(hair) * _f32(0.6180339)), 1.0), 0.03, 0.97)
		var v := fmod(_f32(_f32(hair) * _f32(0.4142135)), 1.0) * (1.0 - u)
		var w := 1.0 - u - v
		var p := mesh.v[a] * u + mesh.v[b] * v + mesh.v[c] * w
		var n0 := mesh.n[a] * u + mesh.n[b] * v + mesh.n[c] * w
		var n := n0 / maxf(n0.length(), 0.00001)
		var d0 := Vector3(n.x * n.y, -1.0 + n.y * n.y, n.z * n.y)
		var d := d0 / maxf(d0.length(), 0.00001)
		var across := Vector3(n.y * d.z - n.z * d.y, n.z * d.x - n.x * d.z, n.x * d.y - n.y * d.x)
		var strand := 0.012 + 0.011 * pow(sin(_f32(_f32(hair) * _f32(1.73))), 2)
		for vertex in [[-1.0, 0.0], [1.0, 0.0], [0.0, 1.0]]:
			var t: float = vertex[1]
			out.i.append(out.v.size())
			out.v.append(p + d * strand * t + n * 0.008 * t + across * 0.0015 * vertex[0])
			out.n.append(n)
	return out

static func _merge(pieces: Array) -> G.Geo:
	var out := G.Geo.new()
	for piece in pieces:
		var offset := out.v.size()
		out.v.append_array(piece.v)
		out.n.append_array(piece.n)
		for index in piece.i:
			out.i.append(index + offset)
	return out
