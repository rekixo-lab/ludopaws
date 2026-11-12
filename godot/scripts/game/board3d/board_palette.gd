@tool
extends RefCounted
## Board-only colours from the live LudoPawsBoardPalette and ClassicBoardDefinition.

const CORAL := 0xE85468
const JADE := 0x18B568
const HONEY := 0xF3C62D
const SKY := 0x168FDB
const STONE := 0xD9D5C9
const STONE_LIGHT := 0xF1EADB
const STONE_SHADE := 0xC5BFB0
const JOINT := 0x756956
const SAFE := 0x167B98
const TEXT := 0xF9F5E9
const GOLD := 0xF5C34C

const CLASSIC_SURFACE := 0x52634A
const CLASSIC_NEUTRAL_CELL := 0xB6B8A0

## Board colour index (0 RED, 1 GREEN, 2 YELLOW, 3 BLUE) -> palette hex.
const TEAM := [CORAL, JADE, HONEY, SKY]
const COLOR_NAMES := ["RED", "GREEN", "YELLOW", "BLUE"]

static func color(hex: int, alpha := 1.0) -> Color:
	return Color(((hex >> 16) & 255) / 255.0, ((hex >> 8) & 255) / 255.0, (hex & 255) / 255.0, alpha)
