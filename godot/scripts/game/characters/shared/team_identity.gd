@tool
extends RefCounted
## Authoritative Ludo colour -> team identity; species never changes it.
## Port of LudoPawsTeamIdentityPolicy.kt (LudoPawsTeamColor + sigils).

## name -> {argb, sigil_argb, gl, belly, sigil}
const TEAMS := {
	"RED": {"argb": 0xF1252F, "sigil_argb": 0xC91D27, "gl": Color(241 / 255.0, 37 / 255.0, 47 / 255.0, 1), "belly": Color(0.98, 0.08, 0.11, 1), "sigil": "DIAMOND"},
	"GREEN": {"argb": 0x00A950, "sigil_argb": 0x007A3A, "gl": Color(0, 169 / 255.0, 80 / 255.0, 1), "belly": Color(0.03, 0.78, 0.30, 1), "sigil": "LEAF"},
	"YELLOW": {"argb": 0xFFD81B, "sigil_argb": 0x9A7410, "gl": Color(1, 216 / 255.0, 27 / 255.0, 1), "belly": Color(1, 216 / 255.0, 27 / 255.0, 1), "sigil": "SUN"},
	"BLUE": {"argb": 0x3097D7, "sigil_argb": 0x12689F, "gl": Color(48 / 255.0, 151 / 255.0, 215 / 255.0, 1), "belly": Color(0.12, 0.62, 0.98, 1), "sigil": "WAVE"},
}

static func resolve(color) -> Dictionary:
	if color == null:
		return {}
	return TEAMS.get(str(color).strip_edges().to_upper(), {})

static func gl(color) -> Color:
	var team := resolve(color)
	return team.get("gl", Color.WHITE)

static func belly(color) -> Color:
	var team := resolve(color)
	return team.get("belly", Color.WHITE)
