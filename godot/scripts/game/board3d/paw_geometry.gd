@tool
extends RefCounted
## Procedural mesh toolkit ported from the live Android renderer
## (rendering/meshes/MeshGeometry.kt, SphereGeometry.kt and the per-scene MeshBuilders).
##
## Coordinates follow the live "pixel space": +X right, +Y DOWN the screen, +Z toward
## the viewer. Board3D flips Y once at its root so the ported numbers stay unchanged.
## A Geo is {v: PackedVector3Array, n: PackedVector3Array, i: PackedInt32Array, l: PackedVector3Array}
## where l is the material-space position the shader reads (vLocalPosition).

class Geo:
	var v := PackedVector3Array()
	var n := PackedVector3Array()
	var i := PackedInt32Array()
	var l := PackedVector3Array()

	func to_mesh(material: Material = null, mesh: ArrayMesh = null) -> ArrayMesh:
		var out := mesh if mesh != null else ArrayMesh.new()
		if v.is_empty():
			return out
		var uv := PackedVector2Array()
		var uv2 := PackedVector2Array()
		var locals := l if l.size() == v.size() else v
		uv.resize(locals.size())
		uv2.resize(locals.size())
		for k in locals.size():
			uv[k] = Vector2(locals[k].x, locals[k].y)
			uv2[k] = Vector2(locals[k].z, 0.0)
		var arrays := []
		arrays.resize(Mesh.ARRAY_MAX)
		arrays[Mesh.ARRAY_VERTEX] = v
		arrays[Mesh.ARRAY_NORMAL] = n
		arrays[Mesh.ARRAY_TEX_UV] = uv
		arrays[Mesh.ARRAY_TEX_UV2] = uv2
		arrays[Mesh.ARRAY_INDEX] = i
		out.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
		if material != null:
			out.surface_set_material(out.get_surface_count() - 1, material)
		return out

## Flat-shaded builder (houses, roads, fences). Like the live MeshBuilder, normals are
## forced toward the camera (+Z) because the board view never culls faces.
class Builder:
	var geo := Geo.new()

	func triangle(a: Vector3, b: Vector3, c: Vector3) -> void:
		var normal := (b - a).cross(c - a)
		var length := normal.length()
		if length < 0.000001:
			return
		normal /= length
		if normal.z < 0.0:
			normal = -normal
		for p in [a, b, c]:
			geo.i.append(geo.v.size())
			geo.v.append(p)
			geo.n.append(normal)

	func quad(a: Vector3, b: Vector3, c: Vector3, d: Vector3) -> void:
		triangle(a, b, c)
		triangle(a, c, d)

	## Extrudes a closed XY outline between z=back and z=front.
	func prism(points: Array, back: float, front: float) -> void:
		var center := Vector3.ZERO
		for p in points:
			center += p
		center /= points.size()
		for k in points.size():
			var a: Vector3 = points[k]
			var b: Vector3 = points[(k + 1) % points.size()]
			var af := Vector3(a.x, a.y, front)
			var bf := Vector3(b.x, b.y, front)
			var ab := Vector3(a.x, a.y, back)
			var bb := Vector3(b.x, b.y, back)
			triangle(Vector3(center.x, center.y, front), af, bf)
			triangle(Vector3(center.x, center.y, back), bb, ab)
			quad(af, ab, bb, bf)

	func build() -> Geo:
		return geo

## SphereGeometry.standard: 16 x 24 smooth unit ellipsoid.
static var _sphere: Geo
static func sphere() -> Geo:
	if _sphere != null:
		return _sphere
	var g := Geo.new()
	var lat_segments := 16
	var lon_segments := 24
	for lat in lat_segments + 1:
		var theta := PI * lat / lat_segments
		for lon in lon_segments + 1:
			var phi := TAU * lon / lon_segments
			var p := Vector3(sin(theta) * cos(phi), cos(theta), sin(theta) * sin(phi))
			g.v.append(p)
			g.n.append(p)
	var row := lon_segments + 1
	for lat in lat_segments:
		for lon in lon_segments:
			var a := lat * row + lon
			var b := a + row
			for index in [a, b, a + 1, b, b + 1, a + 1]:
				g.i.append(index)
	_sphere = g
	return g

## MeshLoft.build: smooth closed cross-sections (rings) along +Y.
## Ring = [y, radiusX, radiusZ, centerX, centerZ, roundness]
static func loft(control_rings: Array, segments := 20, subdivisions := 1) -> Geo:
	var rings: Array = control_rings if subdivisions == 1 else _smooth_rings(control_rings, subdivisions)
	var g := Geo.new()
	var count := rings.size() * segments + 2
	g.v.resize(count)
	g.n.resize(count)
	for r in rings.size():
		var ring: Array = _ring(rings[r])
		for side in segments:
			var t := side * TAU / segments
			var c := cos(t)
			var s := sin(t)
			g.v[r * segments + side] = Vector3(
				ring[3] + signf(c) * pow(absf(c), ring[5]) * ring[1],
				ring[0],
				ring[4] + signf(s) * pow(absf(s), ring[5]) * ring[2])
	var first: Array = _ring(rings[0])
	var last: Array = _ring(rings[rings.size() - 1])
	var bottom := count - 2
	var top := count - 1
	g.v[bottom] = Vector3(first[3], first[0], first[4])
	g.v[top] = Vector3(last[3], last[0], last[4])
	for r in rings.size() - 1:
		for side in segments:
			var a := r * segments + side
			var next := r * segments + (side + 1) % segments
			g.i.append_array([a, a + segments, next, next, a + segments, next + segments])
	for side in segments:
		var next := (side + 1) % segments
		g.i.append_array([bottom, side, next])
		var offset := (rings.size() - 1) * segments
		g.i.append_array([top, offset + next, offset + side])
	# Area-weighted smooth normals, like the live loft.
	for k in range(0, g.i.size(), 3):
		var a := g.i[k]
		var b := g.i[k + 1]
		var c := g.i[k + 2]
		var face := (g.v[b] - g.v[a]).cross(g.v[c] - g.v[a])
		g.n[a] += face
		g.n[b] += face
		g.n[c] += face
	for k in count:
		g.n[k] = g.n[k].normalized()
	return g

static func _ring(value) -> Array:
	var r: Array = value
	return [r[0], r[1], r[2], r[3] if r.size() > 3 else 0.0, r[4] if r.size() > 4 else 0.0, r[5] if r.size() > 5 else 1.0]

static func _smooth_rings(points: Array, steps: int) -> Array:
	var p: Array = []
	for value in points:
		p.append(_ring(value))
	var result: Array = []
	for index in p.size() - 1:
		var a: Array = p[maxi(index - 1, 0)]
		var b: Array = p[index]
		var c: Array = p[index + 1]
		var d: Array = p[mini(index + 2, p.size() - 1)]
		for step in steps:
			var t := float(step) / steps
			result.append([
				b[0] + (c[0] - b[0]) * t,
				maxf(_cubic(a[1], b[1], c[1], d[1], t), 0.001),
				maxf(_cubic(a[2], b[2], c[2], d[2], t), 0.001),
				_cubic(a[3], b[3], c[3], d[3], t),
				_cubic(a[4], b[4], c[4], d[4], t),
				b[5] + (c[5] - b[5]) * t,
			])
	result.append(p[p.size() - 1])
	return result

static func _cubic(a: float, b: float, c: float, d: float, t: float) -> float:
	return 0.5 * ((2.0 * b) + (-a + c) * t + (2.0 * a - 5.0 * b + 4.0 * c - d) * t * t + (-a + 3.0 * b - 3.0 * c + d) * t * t * t)

# ---------------------------------------------------------------- transforms

## android.opengl.Matrix equivalents (post-multiplying, degrees, right-handed).
static func translate(m: Transform3D, x: float, y: float, z: float) -> Transform3D:
	return m * Transform3D(Basis.IDENTITY, Vector3(x, y, z))

static func rotate(m: Transform3D, degrees: float, x: float, y: float, z: float) -> Transform3D:
	if degrees == 0.0:
		return m
	return m * Transform3D(Basis(Vector3(x, y, z).normalized(), deg_to_rad(degrees)), Vector3.ZERO)

static func scale(m: Transform3D, x: float, y: float, z: float) -> Transform3D:
	return m * Transform3D(Basis.from_scale(Vector3(x, y, z)), Vector3.ZERO)

## The drawPart/drawGeometry transform: translate, rotate X, Y, Z, then scale.
static func part_transform(parent: Transform3D, t: Vector3, s: Vector3, r := Vector3.ZERO) -> Transform3D:
	var m := translate(parent, t.x, t.y, t.z)
	if r.x != 0.0:
		m = rotate(m, r.x, 1, 0, 0)
	if r.y != 0.0:
		m = rotate(m, r.y, 0, 1, 0)
	if r.z != 0.0:
		m = rotate(m, r.z, 0, 0, 1)
	return scale(m, s.x, s.y, s.z)

static func rgb(hex: int, alpha := 1.0) -> Color:
	return Color(((hex >> 16) & 255) / 255.0, ((hex >> 8) & 255) / 255.0, (hex & 255) / 255.0, alpha)

static func tint(base: Color, factor: float) -> Color:
	return Color(minf(base.r * factor, 1.0), minf(base.g * factor, 1.0), minf(base.b * factor, 1.0), 1.0)

## CatGeometry.coatMapped: unit bone/tail meshes get coat coordinates at their visible scale
## (x and z times width, y times length) so fur patterns keep their anatomical size.
static func coat_mapped(geo: Geo, width: float, length: float) -> Geo:
	var out := Geo.new()
	out.v = geo.v
	out.n = geo.n
	out.i = geo.i
	for p in geo.v:
		out.l.append(Vector3(p.x * width, p.y * length, p.z * width))
	return out
