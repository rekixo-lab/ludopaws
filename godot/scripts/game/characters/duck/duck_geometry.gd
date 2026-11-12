@tool
extends RefCounted
## Duck anatomy and materials: closed body, feathers, flat bill and scalloped webbed feet.
## Port of DuckGeometry.kt and DuckMaterials.kt. Rings: [y, rx, rz, cx, cz, roundness].

const G = preload("res://scripts/game/board3d/paw_geometry.gd")

const YELLOW := Color(1.00, 0.84, 0.20, 1)
const BELLY := Color(1.00, 0.88, 0.34, 1)
const ORANGE := Color(1.00, 0.42, 0.03, 1)
const BILL_LOWER := Color(0.91, 0.31, 0.025, 1)
const BILL_SHADOW := Color(0.58, 0.22, 0.025, 1)
const EYE_GOLD := Color(0.37, 0.24, 0.09, 1)

static var _cache := {}

static func mesh(name: String) -> G.Geo:
	if _cache.is_empty():
		_build()
	return _cache[name]

static func _build() -> void:
	_cache["body"] = G.loft([[-0.49, 0.12, 0.18], [-0.38, 0.37, 0.43, 0, -0.03], [-0.15, 0.55, 0.59, 0, -0.09],
		[0.10, 0.54, 0.57, 0, -0.10], [0.32, 0.38, 0.36, 0, -0.015], [0.43, 0.16, 0.17]], 24, 3)
	_cache["neck"] = G.loft([[0.25, 0.27, 0.27], [0.43, 0.27, 0.25], [0.57, 0.23, 0.22, 0, 0.02], [0.67, 0.13, 0.14]], 20, 3)
	_cache["head"] = G.loft([[0.59, 0.10, 0.12, 0, 0.10], [0.73, 0.30, 0.28, 0, 0.04], [0.95, 0.43, 0.36],
		[1.14, 0.37, 0.31, 0, -0.025], [1.28, 0.19, 0.17, 0, -0.035], [1.31, 0.025, 0.03]], 28, 3)
	_cache["bill"] = G.loft([[-0.045, 0.21, 0.18, 0, 0.015, 0.65], [-0.015, 0.30, 0.255, 0, 0.015, 0.65],
		[0.035, 0.29, 0.25, 0, 0, 0.70], [0.065, 0.15, 0.15, 0, -0.05]], 24, 2)
	_cache["wing"] = G.loft([[-0.52, 0.015, 0.018, 0, -0.13], [-0.40, 0.13, 0.07, 0, -0.085],
		[-0.23, 0.18, 0.09, 0, -0.035], [-0.07, 0.13, 0.075], [0.0, 0.045, 0.035]], 18, 3)
	_cache["feather"] = G.loft([[-0.27, 0.006, 0.006, 0, -0.035], [-0.16, 0.055, 0.025], [-0.04, 0.045, 0.027], [0.0, 0.012, 0.01]], 12)
	_cache["tuft"] = G.loft([[0.0, 0.055, 0.04], [0.10, 0.07, 0.045, 0, -0.025], [0.21, 0.012, 0.01, 0.055, -0.07]], 12, 2)
	_cache["tail"] = G.loft([[0.0, 0.15, 0.10], [0.13, 0.15, 0.07, 0, -0.055], [0.28, 0.012, 0.012, 0, -0.10]], 16, 2)
	_cache["leg"] = G.loft([[-0.5, 0.65, 0.65], [0.0, 1.0, 1.0], [0.5, 0.75, 0.75]], 12)
	_cache["foot"] = _webbed_foot()

## Flat-shaded extruded webbed foot; each face normal is turned toward an outward hint.
static func _webbed_foot() -> G.Geo:
	var outline := [Vector2(-0.065, -0.15), Vector2(-0.12, -0.02), Vector2(-0.25, 0.19), Vector2(-0.18, 0.25),
		Vector2(-0.065, 0.19), Vector2(0.0, 0.28), Vector2(0.065, 0.19), Vector2(0.18, 0.25),
		Vector2(0.25, 0.19), Vector2(0.12, -0.02), Vector2(0.065, -0.15)]
	var g := G.Geo.new()
	for i in outline.size():
		var a: Vector2 = outline[i]
		var b: Vector2 = outline[(i + 1) % outline.size()]
		var at := Vector3(a.x, 0.035, a.y)
		var bt := Vector3(b.x, 0.035, b.y)
		var ab := Vector3(a.x, -0.035, a.y)
		var bb := Vector3(b.x, -0.035, b.y)
		_tri(g, Vector3(0, 0.035, 0.035), at, bt, Vector3(0, 1, 0))
		_tri(g, Vector3(0, -0.035, 0.035), bb, ab, Vector3(0, -1, 0))
		var normal := Vector3(-(b.y - a.y), 0, b.x - a.x)
		_tri(g, at, ab, bt, normal)
		_tri(g, bt, ab, bb, normal)
	return g

static func _tri(g: G.Geo, a: Vector3, b: Vector3, c: Vector3, outward: Vector3) -> void:
	var n := (b - a).cross(c - a)
	var length := n.length()
	var flip := n.dot(outward) < 0.0
	n = n * ((-1.0 if flip else 1.0) / length)
	for point in ([a, c, b] if flip else [a, b, c]):
		g.i.append(g.v.size())
		g.v.append(point)
		g.n.append(n)
