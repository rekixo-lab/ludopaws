extends RefCounted
## Compares the Godot character port with golden vectors recorded from the unchanged live
## Kotlin renderer (tests/fixtures/characters/<id>_golden.json, see characters_gen/generate.sh):
## loft meshes, sampled poses, reactions, life motion, idle blend and every drawn part
## (final matrix, colour, surface, mesh) in draw order.

const G = preload("res://scripts/game/board3d/paw_geometry.gd")
const D = preload("res://scripts/game/board3d/paw_draw.gd")
const P = preload("res://scripts/game/characters/shared/pose.gd")
const Life = preload("res://scripts/game/characters/shared/life_motion.gd")
const Catalog = preload("res://scripts/game/characters/catalog.gd")

const EPS := 0.0005

## Records draw calls instead of creating nodes.
class Rec extends D.Pool:
	var parts: Array = []
	var names := {}

	func _init(mesh_names: Dictionary) -> void:
		super(null)
		names = mesh_names

	func draw_geometry(parent: Transform3D, geo: G.Geo, t: Vector3, s: Vector3, color: Color, r := Vector3.ZERO) -> void:
		var m := G.part_transform(parent, t, s, r)
		parts.append([names.get(geo.get_instance_id(), "sphere"), surface, color, m, limb_bend, party_energy])

## Returns a list of failure strings (empty = parity).
static func run(species: String) -> Array:
	var failures: Array = []
	var file := FileAccess.open("res://tests/fixtures/characters/%s_golden.json" % species, FileAccess.READ)
	if file == null:
		return ["%s: golden fixture missing" % species]
	var data: Dictionary = JSON.parse_string(file.get_as_text())
	var def = Catalog.definition(species)
	if def == null:
		return ["%s: not registered in catalog" % species]
	var names := {G.sphere().get_instance_id(): "sphere"}
	# Species built only from sphere parts (the panda) have no mesh module.
	var geometry = def.Renderer.M if not data["meshes"].is_empty() else null
	for mesh_name in data["meshes"]:
		var geo: G.Geo = geometry.mesh(mesh_name)
		names[geo.get_instance_id()] = mesh_name
		var want: Array = data["meshes"][mesh_name]
		var sums := Vector3.ZERO
		var normal_sum := 0.0
		var local_sum := 0.0
		for k in geo.v.size():
			sums += geo.v[k]
			normal_sum += geo.n[k].x + geo.n[k].y * 2.0 + geo.n[k].z * 3.0
		for k in geo.l.size():
			local_sum += geo.l[k].x + geo.l[k].y * 2.0 + geo.l[k].z * 3.0
		# Kotlin builds meshes in float32; allow its rounding to accumulate over large batches.
		var tol := 0.01 + geo.v.size() * 2e-6
		if geo.v.size() != int(want[0]) or geo.i.size() != int(want[1]) \
				or absf(sums.x - want[2]) > tol + absf(want[2]) * 5e-6 or absf(sums.y - want[3]) > tol + absf(want[3]) * 5e-6 \
				or absf(sums.z - want[4]) > tol + absf(want[4]) * 5e-6 \
				or absf(normal_sum - want[5]) > 0.05 + geo.v.size() * 1e-5 or absf(local_sum - want[6]) > tol + absf(want[6]) * 5e-6:
			failures.append("%s mesh %s: got [%d,%d,%.4f,%.4f,%.4f,%.4f] want %s" % [species, mesh_name, geo.v.size(), geo.i.size(), sums.x, sums.y, sums.z, normal_sum, want])
	for row in data["samples"]:
		var got: Dictionary = def.Renderer.sample(row["m"], row["p"], row["pb"])
		var diff := _pose_diff(got, row["pose"])
		if not diff.is_empty():
			failures.append("%s sample %s %.1f %s: %s" % [species, row["m"], row["p"], row["pb"], diff])
	for row in data["reactions"]:
		var frame := _frame(row["cue"], row["p"])
		var diff := _pose_diff(def.reaction(row["cue"], frame), row["pose"])
		if not diff.is_empty():
			failures.append("%s reaction %s %.2f: %s" % [species, row["cue"], row["p"], diff])
	for row in data["draws"]:
		var pose: Dictionary = def.Renderer.sample(row["m"], row["p"], row["pb"])
		var rec := Rec.new(names)
		def.Renderer.draw(rec, Transform3D.IDENTITY, pose, row["team"])
		failures.append_array(_parts_diff("%s draw %s %.1f %s" % [species, row["m"], row["p"], row["pb"]], rec.parts, row["parts"]))
	# Life motion: same deterministic update sequence as the harness.
	var life := Life.new()
	var target: Dictionary = def.reaction("EXCITED", _frame("EXCITED", 0.4))
	var life_rows: Array = data["life"]
	var row_index := 0
	var last := P.reaction()
	for frame_index in 120:
		var now := 5000 + frame_index * 16
		last = life.update(now, 37, frame_index < 80, 0.3, -0.2, target if frame_index >= 40 and frame_index <= 90 else P.reaction())
		if frame_index % 10 == 9:
			var want: Dictionary = life_rows[row_index]
			row_index += 1
			for key in ["blink", "breath", "headTilt", "headPitch", "mouthOpen", "shake", "gazeX", "gazeY", "idleWeight"]:
				var got_value: float = life.get(_snake(key))
				if absf(got_value - float(want[key])) > EPS * 4.0:
					failures.append("%s life frame %d %s: got %.6f want %s" % [species, frame_index, key, got_value, want[key]])
			var diff := _pose_diff(last, want["reaction"], 0.002)
			if not diff.is_empty():
				failures.append("%s life frame %d reaction: %s" % [species, frame_index, diff])
	for row in data["idle"]:
		var base: Dictionary = def.Renderer.sample(P.IDLE, row["p"], P.ISOLATED)
		var diff := _pose_diff(life.idle_pose(base, species), row["pose"], 0.002)
		if not diff.is_empty():
			failures.append("%s idle blend %.2f: %s" % [species, row["p"], diff])
	var rec := Rec.new(names)
	rec.life = life
	def.Renderer.draw(rec, Transform3D.IDENTITY, life.idle_pose(def.Renderer.sample(P.IDLE, 0.3, P.ISOLATED), species), data["lifeDraw"]["team"])
	failures.append_array(_parts_diff("%s life draw" % species, rec.parts, data["lifeDraw"]["parts"], 0.003))
	return failures

static func _snake(key: String) -> String:
	return {"headTilt": "head_tilt", "headPitch": "head_pitch", "mouthOpen": "mouth_open", "gazeX": "gaze_x",
		"gazeY": "gaze_y", "idleWeight": "idle_weight"}.get(key, key)

static func _frame(cue: String, progress: float) -> Dictionary:
	var raw := clampf(progress, 0.0, 1.0)
	var p := raw
	if cue == "CAPTURED" or cue == "NERVOUS":
		p = 0.5 * (raw / 0.18) if raw < 0.18 else 0.5 + 0.5 * (raw - 0.18) / 0.82
	return {"p": p, "arc": maxf(sin(p * PI), 0.0), "wave": sin(p * PI * 2.0),
		"fast": sin(p * PI * 5.0), "bounce": absf(sin(p * PI * 2.0)) * (1.0 - p * 0.20)}

static func _pose_diff(got: Dictionary, want: Dictionary, eps := EPS) -> String:
	var out: Array = []
	for key in want:
		var w = want[key]
		var g = got.get(key)
		if w == null or g == null:
			if (w == null) != (g == null):
				out.append("%s got %s want %s" % [key, g, w])
			continue
		if absf(float(g) - float(w)) > eps * maxf(1.0, absf(float(w))):
			out.append("%s got %.5f want %.5f" % [key, g, w])
	return ", ".join(out)

static func _parts_diff(label: String, got: Array, want: Array, eps := EPS) -> Array:
	if got.size() != want.size():
		return ["%s: %d parts, want %d" % [label, got.size(), want.size()]]
	for i in want.size():
		var w: Array = want[i]
		var g: Array = got[i]
		var m: Transform3D = g[3]
		var b := m.basis
		var mine := [g[2].r, g[2].g, g[2].b, g[2].a,
			b.x.x, b.x.y, b.x.z, 0.0, b.y.x, b.y.y, b.y.z, 0.0, b.z.x, b.z.y, b.z.z, 0.0,
			m.origin.x, m.origin.y, m.origin.z, 1.0, g[4].x, g[4].y, g[5]]
		if g[0] != w[0] or g[1] != w[1]:
			return ["%s part %d: %s/%s want %s/%s" % [label, i, g[0], g[1], w[0], w[1]]]
		for k in 23:
			if absf(float(mine[k]) - float(w[k + 2])) > eps * maxf(1.0, absf(float(w[k + 2]))):
				return ["%s part %d (%s) value %d: got %.6f want %s" % [label, i, w[0], k, mine[k], w[k + 2]]]
	return []
