@tool
extends RefCounted
## Pig anatomy and materials, including the continuous corkscrew tail sweep.
## Port of PigGeometry.kt and the colours in PigRenderer.kt.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")

const PINK := Color(0.97, 0.61, 0.65, 1)
const BLUSH := Color(0.89, 0.37, 0.46, 1)
const SNOUT := Color(0.99, 0.51, 0.57, 1)
const HOOF := Color(0.30, 0.20, 0.24, 1)

static var _cache := {}

static func mesh(name: String) -> G.Geo:
	if _cache.is_empty():
		_build()
	return _cache[name]

static func _build() -> void:
	_cache["body"] = G.loft([[-0.38, 0.14, 0.22, 0, -0.09], [-0.26, 0.39, 0.48, 0, -0.09], [0.01, 0.52, 0.60, 0, -0.10],
		[0.25, 0.41, 0.47, 0, -0.10], [0.38, 0.20, 0.25, 0, -0.06]], 28, 3)
	_cache["neck"] = G.loft([[0.17, 0.30, 0.27, 0, 0.08], [0.38, 0.27, 0.25, 0, 0.10], [0.54, 0.23, 0.22, 0, 0.10]], 20, 3)
	_cache["head"] = G.loft([[0.43, 0.14, 0.14, 0, 0.10], [0.56, 0.34, 0.28, 0, 0.11], [0.80, 0.42, 0.33, 0, 0.07],
		[1.02, 0.34, 0.27, 0, 0.03], [1.12, 0.15, 0.14]], 28, 3)
	_cache["ear"] = G.loft([[0.0, 0.10, 0.065], [0.14, 0.16, 0.075], [0.28, 0.12, 0.05, 0.04, 0.04],
		[0.36, 0.055, 0.028, 0.078, 0.097], [0.40, 0.009, 0.010, 0.094, 0.14]], 18, 3)
	_cache["snout"] = G.loft([[0.0, 0.19, 0.12], [0.12, 0.24, 0.15], [0.23, 0.23, 0.14], [0.26, 0.19, 0.11]], 24, 2)
	_cache["leg"] = G.loft([[-0.46, 0.075, 0.08], [-0.28, 0.095, 0.10], [-0.08, 0.13, 0.14], [0.04, 0.10, 0.11]], 16, 2)
	_cache["hoof"] = G.loft([[-0.06, 0.042, 0.10, 0, 0, 0.65], [-0.045, 0.052, 0.115, 0, 0, 0.65],
		[0.045, 0.05, 0.105, 0, 0, 0.65], [0.07, 0.03, 0.07]], 16, 2)
	_cache["curledTail"] = _curled_tail()

## Tapered continuous sweep; the depth offset separates each turn of the curl.
static func _curled_tail() -> G.Geo:
	var rings := 31
	var sides := 12
	var g := G.Geo.new()
	g.v.resize(rings * sides + 2)
	g.n.resize(rings * sides + 2)
	var radius := 0.116
	var sweep := PI * 2.68
	var depth := -0.18
	var tangent_length := Vector2(radius * sweep, depth).length()
	var planar := radius * sweep / tangent_length
	var depth_tangent := depth / tangent_length
	for ring in rings:
		var t := float(ring) / (rings - 1)
		var angle := t * sweep
		var tx := cos(angle)
		var ty := sin(angle)
		var x := radius * ty
		var y := radius * (1.0 - tx)
		var z := depth * t
		var thickness := 0.029 * (1.0 - t) + 0.008
		for side in sides:
			var around := TAU * side / sides
			var across := cos(around)
			var around_depth := sin(around)
			var n := Vector3(-ty * across - depth_tangent * tx * around_depth,
				tx * across - depth_tangent * ty * around_depth, planar * around_depth)
			g.v[ring * sides + side] = Vector3(x, y, z) + n * thickness
			g.n[ring * sides + side] = n
		if ring == 0:
			g.v[rings * sides] = Vector3(x, y, z)
			g.n[rings * sides] = Vector3(-planar * tx, -planar * ty, -depth_tangent)
		if ring == rings - 1:
			g.v[rings * sides + 1] = Vector3(x, y, z)
			g.n[rings * sides + 1] = Vector3(planar * tx, planar * ty, depth_tangent)
	for ring in rings - 1:
		for side in sides:
			var a := ring * sides + side
			var b := ring * sides + (side + 1) % sides
			g.i.append_array([a, b, a + sides, b, b + sides, a + sides])
	for side in sides:
		var next := (side + 1) % sides
		var last := (rings - 1) * sides
		g.i.append_array([rings * sides, next, side, rings * sides + 1, last + side, last + next])
	return g
