extends RefCounted
## The live dice skins, ported line by line against canvas_emitter.gd and proven command for
## command against frames recorded from the unchanged live renderers (dice_golden.json).
## Appearance only: a renderer receives an already-decided face and can never choose it.
## Ports ClassicDiceRenderer, PearlPawDiceRenderer, EnvironmentDiceRenderer,
## SpatialDiceRenderer (+ SpatialDiceMotion, SpatialDiceSurfaces via recorded commands).

const CE = preload("res://scripts/game/render/canvas_emitter.gd")

## frame: {face, rolling, settling, attention, pulse, deadline, start_face, elapsed_ms, settle}
static func frame(face: int, rolling := false, settling := false, attention := false, pulse := 0.0,
		deadline := false, start_face := 1, elapsed_ms := 0, settle := 0.0) -> Dictionary:
	return {"face": face, "rolling": rolling, "settling": settling, "attention": attention, "pulse": pulse,
		"deadline": deadline, "start_face": start_face, "elapsed_ms": elapsed_ms, "settle": settle}

## Live palettes: [faceColors..., border, pip] (DicePalette; border replaced by the team colour).
const PALETTES := {
	"dice_classic": [[0xFFFFFCF1, 0xFFF1EDE1, 0xFFE0DED2], 0xFFF5C34C, 0xFF102536],
	"dice_pearl_paw": [[0xFFFFFCF3, 0xFFFDE6EB, 0xFFE9DDF5], 0xFFE8B66A, 0xFFCE6B94],
	"dice_forest_dew": [[0xFFFFE8B3, 0xFFD9A568, 0xFF805037], 0xFFC18C50, 0xFF382217],
	"dice_lagoon_pearl": [[0xFFF0FFFD, 0xFF9FE5DD, 0xFF2B829C], 0xFF25617A, 0xFFE0F9F5],
	"dice_frost_crystal": [[0xFFFFFFFF, 0xFFD7EDFF, 0xFF779EDE], 0xFF9ACCEB, 0xFF23457D],
	"dice_starlight": [[0xFF595384, 0xFF30294F, 0xFF14172F], 0xFF9B6930, 0xFFFFE8A6],
	"dice_ember_forge": [[0xFF785445, 0xFF432620, 0xFF23191E], 0xFFC17342, 0xFFFFC36B],
	"dice_prism_royal": [[0xFF36B7C6, 0xFF3459B4, 0xFF32265D], 0xFF94BFE4, 0xFFECFBFF],
}

## DiceStylePalettePolicy.resolve: the skin palette with the player's team colour as border.
static func palette(id: String, team_argb = null) -> Array:
	var p: Array = PALETTES.get(id, PALETTES["dice_classic"]).duplicate(true)
	if team_argb != null:
		p[1] = team_argb
	return p

static func create(id: String, density := 2.0) -> Variant:
	match id:
		"dice_classic": return Classic.new(density)
		"dice_pearl_paw": return Pearl.new()
		"dice_forest_dew": return EnvDice.new("FOREST")
		"dice_lagoon_pearl": return EnvDice.new("LAGOON")
		"dice_frost_crystal": return EnvDice.new("FROST")
		"dice_starlight": return EnvDice.new("STARLIGHT")
		"dice_ember_forge": return Spatial.new(true)
		"dice_prism_royal": return Spatial.new(false)
	return null

static func i32(v: float) -> int:
	return int(v)

# ---------------------------------------------------------------- classic

class Classic:
	var density: float
	var face_paint := CE.APaint.new()
	var shadow_paint := CE.APaint.new()
	var border_paint := CE.APaint.new()
	var pip_paint := CE.APaint.new()
	var rolling_paint := CE.APaint.new()
	var attention_paint := CE.APaint.new()
	var attention_glow_paint := CE.APaint.new()
	var uses_perimeter_countdown := true
	var uses_spatial_roll := false

	func _init(d: float) -> void:
		density = d
		shadow_paint.color = 0x33000000
		shadow_paint.shadow = ["shadow", dp(3.0), 0.0, dp(1.5), 0x26000000]
		border_paint.style = "STROKE"; border_paint.stroke_width = dp(2.4); border_paint.color = 0xFF8A8A8A
		pip_paint.color = 0xFF3A3A3A; pip_paint.shadow = ["shadow", dp(1.2), 0.0, dp(0.8), 0x26000000]
		rolling_paint.style = "STROKE"; rolling_paint.stroke_width = dp(5.0); rolling_paint.color = 0xFFB8B8B8
		rolling_paint.shadow = ["shadow", dp(4.0), 0.0, 0.0, 0x446B7280]
		attention_paint.style = "STROKE"; attention_paint.stroke_width = dp(2.8); attention_paint.color = 0xFF8A8A8A; attention_paint.join = "ROUND"
		attention_glow_paint.style = "STROKE"; attention_glow_paint.stroke_width = dp(5.0); attention_glow_paint.color = 0xFF8A8A8A; attention_glow_paint.join = "ROUND"

	func dp(v: float) -> float:
		return v * density

	func draw(c: CE, size: float, palette: Array, f: Dictionary) -> Rect2:
		face_paint.shader = null
		face_paint.color = 0x33000000
		face_paint.shadow = ["shadow", dp(3.0), 0.0, dp(1.5), 0x26000000]
		c.draw_round_rect(size * 0.09, size * 0.11, size * 0.91, size * 0.92, size * 0.16, size * 0.16, shadow_paint)
		var inset := size * 0.07
		var rect := Rect2(inset, inset, size - inset * 2.0, size - inset * 2.0)
		face_paint.set_alpha(255)
		face_paint.shader = CE.linear(rect.position.x, rect.position.y, rect.end.x, rect.end.y, palette[0])
		c.draw_round_rect(rect.position.x, rect.position.y, rect.end.x, rect.end.y, size * 0.17, size * 0.17, face_paint)
		face_paint.shader = null
		face_paint.color = 0x42FFFFFF
		c.draw_round_rect(rect.position.x + size * 0.035, rect.position.y + size * 0.035, rect.end.x - size * 0.035,
			rect.position.y + rect.size.y * 0.43, size * 0.13, size * 0.13, face_paint)
		border_paint.color = palette[1]
		pip_paint.color = palette[2]
		rolling_paint.color = palette[1]
		attention_paint.color = palette[1]
		attention_glow_paint.color = palette[1]
		var pulse := clampf(f["pulse"], 0.0, 1.0)
		rolling_paint.set_alpha(int(145.0 + pulse * 110.0))
		if f["attention"]:
			var g := dp(2.6)
			attention_glow_paint.set_alpha(int(34.0 + pulse * 42.0))
			var r := maxf(size * 0.17 - g, 0.0)
			c.draw_round_rect(rect.position.x + g, rect.position.y + g, rect.end.x - g, rect.end.y - g, r, r, attention_glow_paint)
			attention_paint.set_alpha(int(190.0 + pulse * 65.0))
		var outline: CE.APaint = rolling_paint if f["rolling"] or f["settling"] else (attention_paint if f["attention"] else border_paint)
		if f["deadline"] and not f["rolling"] and not f["settling"]:
			outline.set_alpha(mini((outline.color >> 24) & 255, 72))
		elif outline == border_paint:
			border_paint.set_alpha(255)
		c.draw_round_rect(rect.position.x, rect.position.y, rect.end.x, rect.end.y, size * 0.17, size * 0.17, outline)
		var l := size * 0.31; var m := size * 0.50; var r2 := size * 0.69
		var radius := size * 0.055
		var spots := {1: [[m, m]], 2: [[l, l], [r2, r2]], 3: [[l, l], [m, m], [r2, r2]], 4: [[l, l], [r2, l], [l, r2], [r2, r2]],
			5: [[l, l], [r2, l], [m, m], [l, r2], [r2, r2]], 6: [[l, l], [r2, l], [l, m], [r2, m], [l, r2], [r2, r2]]}
		for p in spots.get(int(f["face"]), []):
			c.draw_circle(p[0], p[1], radius, pip_paint)
		return rect

# ---------------------------------------------------------------- pearl paw

class Pearl:
	var uses_perimeter_countdown := true
	var uses_spatial_roll := false
	var front := CE.APath.new().add_round_rect(10, 25, 79, 92, 14, 14)
	var top := CE.APath.new().move_to(12, 29).quad_to(10, 26, 14, 23).line_to(27, 11).quad_to(30, 8, 36, 9).line_to(85, 13).quad_to(92, 14, 88, 20).line_to(77, 32).close()
	var side := CE.APath.new().move_to(73, 28).line_to(86, 17).quad_to(92, 12, 92, 22).line_to(92, 76).quad_to(92, 82, 87, 86).line_to(74, 94).close()
	var paw := CE.APath.new().move_to(-4, -1).cubic_to(-8, 1, -14, 7, -12, 12).cubic_to(-10, 18, -4, 12, 0, 13).cubic_to(5, 13, 11, 19, 14, 12).cubic_to(17, 6, 9, 1, 6, -2).cubic_to(3, -5, -1, -5, -4, -1).close()
	var glint := CE.APath.new().move_to(14, 48).cubic_to(13, 34, 19, 29, 29, 29)
	var face_p := CE.APaint.new({"shader": CE.linear(12, 27, 76, 91, [0xFFFFFDF2, 0xFFFFF6EF, 0xFFFDE7EE, 0xFFEADDF3], [0.0, 0.42, 0.75, 1.0])})
	var top_p := CE.APaint.new({"shader": CE.linear(30, 9, 65, 31, [0xFFFFFDEB, 0xFFFDF2F3, 0xFFE7EAF7])})
	var side_p := CE.APaint.new({"shader": CE.linear(77, 22, 94, 89, [0xFFF5E8F0, 0xFFDACCE8, 0xFFC5B2CB])})
	var gold := CE.APaint.new({"style": "STROKE", "stroke_width": 3.2, "join": "ROUND",
		"shader": CE.linear(16, 8, 86, 95, [0xFFFFF5CC, 0xFFF7D793, 0xFFC18B45, 0xFFFFEAB0, 0xFFB88042], [0.0, 0.25, 0.5, 0.72, 1.0])})
	var under_rim := CE.APaint.new({"color": 0xFFB58656, "style": "STROKE", "stroke_width": 4.6, "join": "ROUND"})
	var inner_rim := CE.APaint.new({"color": 0xFFFFF9DE, "style": "STROKE", "stroke_width": 0.75})
	var glow := CE.APaint.new()
	var blush := CE.radial(19, 69, 35, [0x52F3A8C8, 0x00F3A8C8])
	var mint := CE.radial(62, 39, 22, [0x4DCEF6E9, 0x00CEF6E9])
	var pearl := CE.radial(54, 78, 29, [0x59E9D2FA, 0x00E9D2FA])
	var reflection := CE.APaint.new({"shader": CE.linear(30, 29, 42, 52, [0xBFFFFFFF, 0x08FFFFFF])})
	var shadow := CE.APaint.new({"shader": CE.radial(50, 92, 46, [0x400D0B23, 0x000D0B23])})
	var pip_rim := CE.APaint.new({"shader": CE.linear(-4, -4, 4, 4, [0xFFFFE6AA, 0xFFAC703E, 0xFFF3CB80])})
	var pip := CE.APaint.new({"shader": CE.radial(-0.8, -1.2, 4.5, [0xFFF9B5CB, 0xFFE389AA, 0xFFAF4A76])})
	var paw_p := CE.APaint.new({"shader": CE.radial(-4, -8, 28, [0xFFFCC4D2, 0xFFE38BAE, 0xFFB85483])})
	var paw_rim := CE.APaint.new({"style": "STROKE", "stroke_width": 1.8, "color": 0xFFD4A15D, "join": "ROUND"})
	var shine := CE.APaint.new({"color": 0xBFFFFFFF, "stroke_width": 0.8, "cap": "ROUND"})
	var glint_p := CE.APaint.new({"color": 0xBFFFFFFF, "stroke_width": 1.2, "cap": "ROUND", "style": "STROKE"})
	var attention := CE.APaint.new({"style": "STROKE"})
	const PIPS := [[], [], [-14, -14, 14, 14], [-14, -14, 0, 0, 14, 14], [-14, -14, 14, -14, -14, 14, 14, 14],
		[-14, -14, 14, -14, 0, 0, -14, 14, 14, 14], [-14, -14, 14, -14, -14, 0, 14, 0, -14, 14, 14, 14]]
	const TOES := [-13, -7, -5, -14, 5, -14, 14, -6]

	func draw(c: CE, size: float, palette: Array, f: Dictionary) -> Rect2:
		c.save()
		c.scale(size / 100.0, size / 100.0)
		c.draw_oval(9, 86, 95, 99, shadow)
		c.draw_path(top, top_p)
		c.draw_path(side, side_p)
		c.draw_path(top, under_rim)
		c.draw_path(side, under_rim)
		c.draw_path(top, gold)
		c.draw_path(side, gold)
		c.draw_round_rect(10, 25, 79, 92, 14, 14, face_p)
		c.save()
		c.clip_path(front)
		for g in [blush, mint, pearl]:
			glow.shader = g
			c.draw_rect(10, 25, 79, 92, glow)
		c.draw_oval(17, 29, 73, 51, reflection)
		c.restore()
		c.draw_round_rect(10, 25, 79, 92, 14, 14, under_rim)
		c.draw_round_rect(10, 25, 79, 92, 14, 14, gold)
		c.draw_round_rect(12.6, 27.6, 76.4, 89.4, 11.4, 11.4, inner_rim)
		c.draw_path(glint, glint_p)
		var face := int(f["face"])
		if face == 1:
			_paw(c)
		elif face >= 2 and face <= 6:
			var p: Array = PIPS[face]
			for i in range(0, p.size(), 2):
				c.save()
				c.translate(44.5 + p[i], 58.5 + p[i + 1])
				c.draw_circle(0, 0, 4.1, pip_rim)
				c.draw_circle(0, 0, 3.1, pip)
				c.draw_circle(-0.8, -1.2, 0.85, shine)
				c.restore()
		c.draw_line(29, 10, 29, 14, shine)
		c.draw_line(27, 12, 31, 12, shine)
		if f["rolling"] or f["settling"] or f["attention"]:
			attention.color = palette[1]
			attention.set_alpha(72 if f["deadline"] and not f["rolling"] and not f["settling"] else int(110 + clampf(f["pulse"], 0.0, 1.0) * 145))
			attention.stroke_width = 2.4 if f["rolling"] or f["settling"] else 1.7
			c.draw_round_rect(5, 5, 95, 95, 17, 17, attention)
		c.restore()
		return Rect2(size * 0.05, size * 0.05, size * 0.9, size * 0.9)

	func _paw(c: CE) -> void:
		c.save()
		c.translate(44.5, 58)
		c.draw_path(paw, paw_rim)
		c.draw_path(paw, paw_p)
		for i in range(0, TOES.size(), 2):
			var x: float = TOES[i]
			var y: float = TOES[i + 1]
			c.draw_oval(x - 4, y - 5.6, x + 4, y + 5.6, paw_rim)
			c.draw_oval(x - 4, y - 5.6, x + 4, y + 5.6, paw_p)
			c.draw_line(x - 1, y - 3, x + 1, y - 3.5, shine)
		c.draw_line(-6, 6, -2, 2.5, shine)
		c.restore()

# ---------------------------------------------------------------- environment (photo body + projected pips)

class EnvDice:
	const THEMES := {
		"FOREST": ["dice_forest_dew_3d", [14, 21, 79, 20, 79, 86, 14, 87], 0xFFFFE8B3, 0xFFD9A568, 0xFF805037, 0xFFC18C50, 0xFF382217, 0xFF59834B],
		"LAGOON": ["dice_lagoon_pearl_3d", [18, 27, 77, 24, 77, 83, 18, 86], 0xFFF0FFFD, 0xFF9FE5DD, 0xFF2B829C, 0xFF25617A, 0xFFE0F9F5, 0xFF4DADB6],
		"FROST": ["dice_frost_crystal_3d", [17, 24, 76, 24, 76, 82, 17, 82], 0xFFFFFFFF, 0xFFD7EDFF, 0xFF779EDE, 0xFF9ACCEB, 0xFF23457D, 0xFF739ECD],
		"STARLIGHT": ["dice_starlight_3d", [18, 27, 78, 25, 78, 87, 18, 89], 0xFF595384, 0xFF30294F, 0xFF14172F, 0xFF9B6930, 0xFFFFE8A6, 0xFFB9AFE7],
	}
	const POSITIONS := [[], [50, 50], [27, 27, 73, 73], [27, 27, 50, 50, 73, 73], [27, 27, 73, 27, 27, 73, 73, 73],
		[27, 27, 73, 27, 50, 50, 27, 73, 73, 73], [27, 27, 73, 27, 27, 50, 73, 50, 27, 73, 73, 73]]
	var theme: String
	var t: Array
	var uses_perimeter_countdown := false
	var uses_spatial_roll := false
	var projection: PackedFloat64Array
	var pip_shadow := CE.APaint.new({"shader": CE.radial(0, 1.5, 8, [0x66091120, 0x00091120])})
	var pip_rim := CE.APaint.new()
	var pip_body := CE.APaint.new()
	var pip_glint := CE.APaint.new({"color": 0xDFFFFFFF})
	var cavity := CE.APaint.new({"style": "STROKE", "stroke_width": 0.75, "color": 0x99E6C08B})
	var diamond := CE.APath.new().move_to(0, -7.1).line_to(6.3, 0).line_to(0, 7.1).line_to(-6.3, 0).close()

	func _init(key: String) -> void:
		theme = key
		t = THEMES[key]
		projection = CE.poly_to_poly([0, 0, 100, 0, 100, 100, 0, 100], t[1])
		pip_rim.shader = CE.linear(-5, -6, 5, 6, [t[4], t[5], t[2]])
		match key:
			"FOREST": pip_body.shader = CE.radial(-1.5, -2, 8, [0xFF24150E, t[6], 0xFF8D5E38])
			"LAGOON": pip_body.shader = CE.radial(-2, -2.5, 8, [0xFFFFFFFF, t[6], 0xFF68A8B6])
			"FROST": pip_body.shader = CE.linear(-4, -5, 4, 5, [0xFF476D9F, t[6], 0xFF142E58])
			"STARLIGHT": pip_body.shader = CE.linear(-5, -6, 5, 6, [0xFFFFF5CE, t[6], 0xFFC68F3E])

	func draw(c: CE, size: float, _palette: Array, f: Dictionary) -> Rect2:
		c.save()
		c.scale(size / 100.0, size / 100.0)
		c.save()
		c.translate(5, 5)
		c.scale(0.9, 0.9)
		c.draw_bitmap(t[0], 0, 0, 100, 100, 255)
		c.save()
		c.concat(projection)
		var face := int(f["face"])
		var p: Array = POSITIONS[face] if face >= 0 and face < POSITIONS.size() else []
		for i in range(0, p.size(), 2):
			_pip(c, p[i], p[i + 1])
		c.restore()
		c.restore()
		c.restore()
		return Rect2(size * 0.045, size * 0.045, size * 0.91, size * 0.91)

	func _pip(c: CE, x: float, y: float) -> void:
		c.save()
		c.translate(x, y)
		if theme == "FROST":
			c.draw_path(diamond, pip_rim)
			c.scale(0.8, 0.8)
			c.draw_path(diamond, pip_body)
			c.draw_circle(-1.3, -2.5, 0.7, pip_glint)
		else:
			if theme != "FOREST":
				c.draw_circle(0, 1.5, 8, pip_shadow)
			c.draw_circle(0, 0, 6.7, pip_rim)
			c.draw_circle(0, 0, 5.5, pip_body)
			if theme == "FOREST":
				c.draw_arc(-6.2, -6.2, 6.2, 6.2, 25, 120, false, cavity)
			else:
				c.draw_circle(-1.7, -2.3, 1.1, pip_glint)
				c.draw_circle(1.8, 2, 0.45, pip_glint)
		c.restore()

# ---------------------------------------------------------------- spatial (bevelled mesh, real orientation)

class Spatial:
	const CAMERA := 6.5
	const FACE_SQUARE := [-1, -1, 1, -1, 1, 1, -1, 1]
	const RIVETS := [-0.75, 0.75]
	const PIPS := [[0, 0], [-0.46, -0.46, 0.46, 0.46], [-0.46, -0.46, 0, 0, 0.46, 0.46],
		[-0.46, -0.46, 0.46, -0.46, -0.46, 0.46, 0.46, 0.46], [-0.46, -0.46, 0.46, -0.46, 0, 0, -0.46, 0.46, 0.46, 0.46],
		[-0.46, -0.54, 0.46, -0.54, -0.46, 0, 0.46, 0, -0.46, 0.54, 0.46, 0.54]]
	## [light, mid, dark, metal, pip]
	const MATERIALS := {true: [0xFF785445, 0xFF432620, 0xFF23191E, 0xFFC17342, 0xFFFFC36B],
		false: [0xFF36B7C6, 0xFF3459B4, 0xFF32265D, 0xFF94BFE4, 0xFFECFBFF]}
	var forged: bool
	var m: Array
	var facets: Array
	var uses_perimeter_countdown := false
	var uses_spatial_roll := true
	var rot := PackedFloat64Array([1, 0, 0, 0, 1, 0, 0, 0, 1])
	var path := CE.APath.new()
	var body := CE.APaint.new()
	var details := CE.APaint.new()
	var rim := CE.APaint.new({"style": "STROKE", "stroke_width": 0.025, "cap": "ROUND", "join": "ROUND"})
	var reflection: Array
	var pit_light: Array
	var pip_light: Array
	var shadow := CE.APaint.new({"shader": CE.radial(0, 0, 1, [0x630E1018, 0x000E1018])})

	func _init(forge: bool) -> void:
		forged = forge
		m = MATERIALS[forge]
		facets = _mesh(0.86, 0.77) if forge else _mesh(0.81, 0.47)
		reflection = CE.linear(-1, -0.46, 1, 0.46,
			[0x00FFD7A2, 0x4AFFB86F, 0x9AFFDBB0, 0x23FFC388, 0x00FFD7A2] if forge else [0x00D6FFFF, 0x52D1A9FF, 0xE0F4FFFF, 0x62A0EDFF, 0x00D6FFFF],
			[0.0, 0.31, 0.48, 0.70, 1.0] if forge else [0.0, 0.43, 0.50, 0.56, 1.0])
		pit_light = CE.linear(-0.16, -0.16, 0.16, 0.16, [0xFF161322, m[3]])
		pip_light = CE.radial(-0.045, -0.065, 0.22, [0xFFFFE3A2 if forge else 0xFFFFFFFF, m[4]])

	func draw(c: CE, size: float, _palette: Array, f: Dictionary) -> Rect2:
		var pose: Quaternion
		if f["rolling"]:
			pose = Motion.rolling(f["start_face"], f["elapsed_ms"])
		elif f["settling"]:
			pose = Motion.settling(f["start_face"], f["face"], f["elapsed_ms"], f["settle"])
		else:
			pose = Motion.resting(f["face"])
		rot = Motion.matrix(pose)
		var rise := clampf(float(f["elapsed_ms"]) / 230.0, 0.0, 1.0)
		var landing := clampf(f["settle"], 0.0, 1.0)
		var lift := 0.0
		if f["rolling"]:
			lift = 0.045 * _ease(rise)
		elif f["settling"]:
			lift = 0.045 * _ease(rise) * (1.0 - _ease(landing))
		c.save()
		c.translate(size * 0.5, size * 0.825)
		c.scale(size * (0.41 + lift), size * 0.12)
		shadow.set_alpha(int(255.0 - 1200.0 * lift))
		c.draw_circle(0, 0, 1, shadow)
		c.restore()
		c.save()
		c.translate(size * 0.5, size * (0.475 - lift))
		c.scale(size * 0.255, size * 0.255)
		for facet in facets:
			_facet(c, facet)
		c.restore()
		return Rect2(size * 0.07, size * 0.07, size * 0.86, size * 0.86)

	func _tr(row: int, p: Vector3) -> float:
		return rot[row] * p.x + rot[row + 1] * p.y + rot[row + 2] * p.z

	func _facet(c: CE, facet: Dictionary) -> void:
		var n: Vector3 = facet["normal"]
		var nx := _tr(0, n); var ny := _tr(3, n); var nz := _tr(6, n)
		if nz * CAMERA <= facet["plane"]:
			return
		path.rewind()
		var verts: Array = facet["vertices"]
		for i in verts.size():
			var depth := CAMERA / (CAMERA - _tr(6, verts[i]))
			var x := _tr(0, verts[i]) * depth
			var y := _tr(3, verts[i]) * depth
			if i == 0:
				path.move_to(x, y)
			else:
				path.line_to(x, y)
		path.close()
		var light := clampf(-0.42 * nx - 0.55 * ny + 0.71 * nz, 0.0, 1.0)
		var refl := pow(clampf(-0.22 * nx - 0.30 * ny + 0.928 * nz, 0.0, 1.0), 7 if forged else 19)
		var bevel := _lit(m[3], 0.42 + light * 0.78)
		body.color = _mix(bevel, 0xFFFFE0A7 if forged else 0xFFE8FDFF, refl * 0.72) if facet["face"] == 0 else m[1]
		c.draw_path(path, body)
		var corners = facet["corners"]
		if corners == null:
			return
		var proj: Array = []
		for v in corners:
			var depth := CAMERA / (CAMERA - _tr(6, v))
			proj.append(_tr(0, v) * depth)
			proj.append(_tr(3, v) * depth)
		var fm = CE.poly_to_poly(FACE_SQUARE, proj)
		if fm == null:
			return
		c.save()
		c.clip_path(path)
		c.concat(fm)
		details.color = 0xFFFFFFFF
		c.draw_bitmap("surface_forge" if forged else "surface_prism", -1, -1, 1, 1, 255)
		details.color = int(76.0 * (1.0 - light)) << 24
		c.draw_rect(-1, -1, 1, 1, details)
		var sx := nx * 0.68
		var sy := ny * 0.47
		c.save()
		c.translate(sx, sy)
		details.color = 0xFFFFFFFF
		details.set_alpha(int(refl * (202.0 if forged else 255.0)))
		details.shader = reflection
		c.draw_rect(-1 - sx, -1 - sy, 1 - sx, 1 - sy, details)
		details.shader = null
		c.restore()
		_inlay(c, facet["face"])
		c.restore()

	func _inlay(c: CE, face: int) -> void:
		rim.color = m[3]
		if forged:
			c.draw_round_rect(-0.91, -0.91, 0.91, 0.91, 0.19, 0.19, rim)
			for x in RIVETS:
				for y in RIVETS:
					details.color = 0xFF211620
					c.draw_circle(x, y, 0.073, details)
					details.color = m[3]
					c.draw_circle(x - 0.009, y - 0.015, 0.049, details)
					details.color = 0xFFFFD59C
					c.draw_circle(x - 0.023, y - 0.03, 0.014, details)
		else:
			path.rewind()
			path.move_to(-0.90, -0.28).line_to(-0.90, -0.50).line_to(-0.50, -0.90).line_to(-0.28, -0.90)
			path.move_to(0.90, 0.28).line_to(0.90, 0.50).line_to(0.50, 0.90).line_to(0.28, 0.90)
			c.draw_path(path, rim)
		var dots: Array = PIPS[face - 1]
		for i in range(0, dots.size(), 2):
			c.save()
			c.translate(dots[i], dots[i + 1])
			details.color = 0xFFFFFFFF
			details.shader = pit_light
			if forged:
				c.draw_circle(0, 0, 0.165, details)
				details.shader = pip_light
				c.draw_circle(0, 0, 0.113, details)
			else:
				_diamond(0.18)
				c.draw_path(path, details)
				details.shader = pip_light
				_diamond(0.132)
				c.draw_path(path, details)
			details.shader = null
			c.restore()

	func _diamond(r: float) -> void:
		path.rewind()
		path.move_to(0, -r).line_to(r, 0).line_to(0, r).line_to(-r, 0).close()

	static func _ease(p: float) -> float:
		return p * p * (3.0 - 2.0 * p)

	static func _lit(color: int, light: float) -> int:
		return 0xFF000000 | (clampi(int(((color >> 16) & 255) * light), 0, 255) << 16) \
			| (clampi(int(((color >> 8) & 255) * light), 0, 255) << 8) | clampi(int((color & 255) * light), 0, 255)

	static func _mix(a: int, b: int, t: float) -> int:
		var out := 0xFF000000
		for shift in [16, 8, 0]:
			var x: int = (a >> shift) & 255
			var y: int = (b >> shift) & 255
			out |= (int(x + (y - x) * t) & 255) << shift
		return out

	static func _mesh(edge: float, corner: float) -> Array:
		var out: Array = []
		var axes := [Vector3(1, 0, 0), Vector3(0, 1, 0), Vector3(0, 0, 1)]
		for i in 3:
			for j in range(i + 1, 3):
				for a in [-1.0, 1.0]:
					for b in [-1.0, 1.0]:
						var u: Vector3 = axes[i] * a
						var v: Vector3 = axes[j] * b
						var span: Vector3 = axes[3 - i - j] * corner
						var first := u + v * edge
						var second := u * edge + v
						out.append(_facet_of((u + v).normalized(), [first + span, first - span, second - span, second + span]))
		for a in [-1.0, 1.0]:
			for b in [-1.0, 1.0]:
				for cc in [-1.0, 1.0]:
					var u := Vector3(a, 0, 0); var v := Vector3(0, b, 0); var w := Vector3(0, 0, cc)
					out.append(_facet_of((u + v + w).normalized(), [u + v * edge + w * corner, u * edge + v + w * corner,
						u * corner + v + w * edge, u * corner + v * edge + w, u * edge + v * corner + w, u + v * corner + w * edge]))
		var x := Vector3(1, 0, 0); var y := Vector3(0, 1, 0); var z := Vector3(0, 0, 1)
		out.append(_face(z, x, y, 1, edge, corner))
		out.append(_face(y, x, -z, 2, edge, corner))
		out.append(_face(x, -z, y, 3, edge, corner))
		out.append(_face(-x, z, y, 4, edge, corner))
		out.append(_face(-y, x, z, 5, edge, corner))
		out.append(_face(-z, -x, y, 6, edge, corner))
		return out

	static func _facet_of(normal: Vector3, vertices: Array, face := 0, corners = null) -> Dictionary:
		return {"normal": normal, "vertices": vertices, "face": face, "corners": corners, "plane": normal.dot(vertices[0])}

	static func _face(n: Vector3, h: Vector3, v: Vector3, number: int, edge: float, corner: float) -> Dictionary:
		var pt := func(px: float, py: float) -> Vector3: return n + h * px + v * py
		return _facet_of(n, [pt.call(-corner, -edge), pt.call(corner, -edge), pt.call(edge, -corner), pt.call(edge, corner),
			pt.call(corner, edge), pt.call(-corner, edge), pt.call(-edge, corner), pt.call(-edge, -corner)], number,
			[pt.call(-edge, -edge), pt.call(edge, -edge), pt.call(edge, edge), pt.call(-edge, edge)])

## SpatialDiceMotion: pure orientation maths (Kotlin DiceRotation = x, y, z, w quaternion).
class Motion:
	const RAMP_SECONDS := 0.34
	const INITIAL_SPEED := 1.2
	const FULL_SPEED := 15.5
	const SETTLE_SECONDS := 0.45 # DiceRollAnimationPolicy.SETTLE_DURATION_MILLIS

	static func around(x: float, y: float, z: float, radians: float) -> Quaternion:
		var half := radians * 0.5
		var s := sin(half) / sqrt(x * x + y * y + z * z)
		return Quaternion(x * s, y * s, z * s, cos(half))

	static func mul(a: Quaternion, b: Quaternion) -> Quaternion:
		return Quaternion(a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y, a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
			a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w, a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z)

	static func tilt() -> Quaternion:
		return mul(around(1, 0, 0, 0.37), around(0, 1, 0, -0.42))

	static func resting(face: int) -> Quaternion:
		var q := PI / 2.0
		var rests := [tilt(), mul(tilt(), around(1, 0, 0, q)), mul(tilt(), around(0, 1, 0, -q)),
			mul(tilt(), around(0, 1, 0, q)), mul(tilt(), around(1, 0, 0, -q)), mul(tilt(), around(0, 1, 0, PI))]
		return rests[clampi(face, 1, 6) - 1]

	static func spin(angle: float) -> Quaternion:
		return around(0.74, 0.64, 0.21, angle)

	static func angle_at(ms: int) -> float:
		var e := maxf(ms, 0) / 1000.0
		var ramp := minf(e, RAMP_SECONDS)
		var angle := INITIAL_SPEED * ramp + (FULL_SPEED - INITIAL_SPEED) * ramp * ramp / (2.0 * RAMP_SECONDS) + FULL_SPEED * (e - ramp)
		return fmod(angle, TAU)

	static func rolling(start: int, ms: int) -> Quaternion:
		return mul(spin(angle_at(ms)), resting(start))

	static func settling(start: int, target_face: int, ms: int, progress: float) -> Quaternion:
		var from := rolling(start, ms)
		var target := resting(target_face)
		var t := clampf(progress, 0.0, 1.0)
		var speed := INITIAL_SPEED + (FULL_SPEED - INITIAL_SPEED) * clampf(ms / 1000.0 / RAMP_SECONDS, 0.0, 1.0)
		var extra := speed * SETTLE_SECONDS * (t - t * t / 2.0)
		var inv := spin(speed * SETTLE_SECONDS / 2.0)
		var corrected := mul(Quaternion(-inv.x, -inv.y, -inv.z, inv.w), target)
		return mul(spin(extra), toward(from, corrected, t * t * (3.0 - 2.0 * t)))

	static func toward(a: Quaternion, b: Quaternion, t: float) -> Quaternion:
		var dot := a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
		var sign := -1.0 if dot < 0.0 else 1.0
		var theta := acos(clampf(dot * sign, 0.0, 1.0))
		var d := sin(theta)
		var ka := sin((1.0 - t) * theta) / d if d > 0.001 else 1.0 - t
		var kb := sign * (sin(t * theta) / d if d > 0.001 else t)
		var n := Quaternion(a.x * ka + b.x * kb, a.y * ka + b.y * kb, a.z * ka + b.z * kb, a.w * ka + b.w * kb)
		var len := sqrt(n.x * n.x + n.y * n.y + n.z * n.z + n.w * n.w)
		return Quaternion(n.x / len, n.y / len, n.z / len, n.w / len)

	static func matrix(q: Quaternion) -> PackedFloat64Array:
		var x: float = q.x; var y: float = q.y; var z: float = q.z; var w: float = q.w
		return PackedFloat64Array([1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
			2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
			2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)])
