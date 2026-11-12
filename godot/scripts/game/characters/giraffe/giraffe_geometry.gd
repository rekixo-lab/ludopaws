@tool
extends RefCounted
## Giraffe anatomy and materials. Port of GiraffeGeometry.kt and the colours in GiraffeRenderer.kt.
## Body and neck are one loft each, split into a honey and a cinnamon-patch index set that
## share positions and normals (GiraffeGeometry.pattern).

const G = preload("res://scripts/game/board3d/paw_geometry.gd")

const HOOF_BOTTOM := -0.09

const HONEY := Color(0.98, 0.67, 0.29, 1)
const CINNAMON := Color(0.49, 0.205, 0.072, 1)
const CREAM := Color(0.98, 0.83, 0.57, 1)
const EAR_PINK := Color(0.90, 0.49, 0.43, 1)
const HOOF_BROWN := Color(0.21, 0.13, 0.10, 1)
const DARK := Color(0.10, 0.052, 0.038, 1)

static var _cache := {}

static func mesh(name: String) -> G.Geo:
	if _cache.is_empty():
		_build()
	return _cache[name]

static func _build() -> void:
	_pattern("body", [[-0.32, 0.095, 0.20, 0, -0.08], [-0.25, 0.25, 0.48, 0, -0.10], [-0.10, 0.34, 0.63, 0, -0.13],
		[0.06, 0.35, 0.61, 0, -0.12], [0.22, 0.30, 0.51, 0, -0.10], [0.34, 0.19, 0.34, 0, -0.07],
		[0.39, 0.065, 0.14, 0, -0.03]], 8, 3)
	_pattern("neck", [[0.13, 0.215, 0.235, 0, 0.23], [0.32, 0.205, 0.225, 0, 0.255], [0.51, 0.151, 0.172, 0, 0.263],
		[0.74, 0.121, 0.145, 0, 0.278], [0.96, 0.105, 0.126, 0, 0.295], [1.17, 0.094, 0.108, 0, 0.323],
		[1.35, 0.105, 0.110, 0, 0.347], [1.49, 0.09, 0.09, 0, 0.371]], 4, 5)
	_cache["mane"] = G.loft([[0.44, 0.016, 0.025, 0, 0.065], [0.64, 0.032, 0.036, 0, 0.115], [0.89, 0.031, 0.036, 0, 0.155],
		[1.12, 0.025, 0.032, 0, 0.192], [1.37, 0.021, 0.026, 0, 0.227], [1.52, 0.009, 0.012, 0, 0.241]], 14, 2)
	_cache["head"] = G.loft([[1.31, 0.073, 0.083, 0, 0.435], [1.39, 0.172, 0.19, 0, 0.437], [1.52, 0.23, 0.246, 0, 0.405],
		[1.67, 0.236, 0.21, 0, 0.357], [1.79, 0.176, 0.145, 0, 0.317], [1.84, 0.058, 0.068, 0, 0.294]], 26, 3)
	_cache["ear"] = G.loft([[0.0, 0.033, 0.027], [0.11, 0.078, 0.045], [0.21, 0.067, 0.035], [0.30, 0.018, 0.015],
		[0.33, 0.003, 0.004]], 16, 2)
	_cache["hoof"] = G.loft([[HOOF_BOTTOM, 0.077, 0.107, 0, 0.025, 0.78], [-0.05, 0.089, 0.112, 0, 0.02, 0.78],
		[0.035, 0.069, 0.086], [0.075, 0.057, 0.067]], 16, 2)

## Kotlin evaluates the patch test in float32; rounding keeps borderline triangles identical.
static func _f(x: float) -> float:
	var a := PackedFloat32Array([x])
	return a[0]

static func _wrap(turns: float) -> float:
	if turns > 0.5:
		return _f(turns - 1.0)
	if turns < -0.5:
		return _f(turns + 1.0)
	return turns

static func _pattern(name: String, rings: Array, columns: int, rows: int) -> void:
	var segments := 40
	var geo := G.loft(rings, segments, 4)
	var patches: Array = []
	for i in rows * columns:
		var row := i / columns
		var column := i % columns
		var phase := _f(i * _f(2.39996))
		patches.append([
			_f(_f(column + 0.5 + (row % 2) * _f(0.46) + _f(_f(0.13) * _f(sin(phase)))) / columns),
			_f(_f(row + _f(0.51) + _f(_f(0.12) * _f(cos(phase)))) / rows),
			_f(_f(_f(0.39) + _f(_f(0.06) * _f(sin(_f(phase + _f(0.8)))))) / columns),
			_f(_f(_f(0.35) + _f(_f(0.07) * _f(cos(_f(phase + _f(1.4)))))) / rows),
			phase,
		])
	var gold := G.Geo.new()
	var spots := G.Geo.new()
	for out in [gold, spots]:
		out.v = geo.v
		out.n = geo.n
	var low := _f(rings[0][0])
	var height := _f(_f(rings[rings.size() - 1][0]) - low)
	var pole := geo.v.size() - 2
	for k in range(0, geo.i.size(), 3):
		var a := geo.i[k]
		var b := geo.i[k + 1]
		var c := geo.i[k + 2]
		var first_angle := _f(float(a % segments) / segments)
		var angle := _f(first_angle + _f(_f(_wrap(_f(_f(float(b % segments) / segments) - first_angle))
			+ _wrap(_f(_f(float(c % segments) / segments) - first_angle))) / 3.0))
		var y := _f(_f(_f(_f(_f(_f(geo.v[a].y) + _f(geo.v[b].y)) + _f(geo.v[c].y)) / 3.0) - low) / height)
		var spotted := false
		if a < pole and b < pole and c < pole:
			for patch in patches:
				var x := _f(_wrap(_f(angle - patch[0])) / patch[2])
				var dy := _f(_f(y - patch[1]) / patch[3])
				if absf(x) > 1.25 or absf(dy) > 1.25:
					continue
				var edge := _f(atan2(dy, x))
				var radius := _f(_f(1.0 + _f(_f(0.15) * _f(cos(_f(_f(5.0 * edge) + patch[4]))))) + _f(_f(0.09) * _f(sin(_f(3.0 * edge)))))
				if _f(_f(x * x) + _f(dy * dy)) < _f(radius * radius):
					spotted = true
					break
		(spots if spotted else gold).i.append_array([a, b, c])
	_cache[name + "_gold"] = gold
	_cache[name + "_patches"] = spots
