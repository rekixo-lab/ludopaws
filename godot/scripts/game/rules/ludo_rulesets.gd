@tool
extends RefCounted
## Ludo Paws rule contracts, ported from the live authoritative backend
## (server/src/rulesets.js). Keys and ids are kept identical so online snapshots
## and saved matches stay compatible with the production server.

const COLORS := ["RED", "GREEN", "YELLOW", "BLUE"]
const START_OFFSETS := {"RED": 0, "GREEN": 13, "YELLOW": 26, "BLUE": 39}
const TEAM_ASSIGNMENTS := ["A", "B", "A", "B"]

const TRACK_CELLS := 52
const HOME := 57
const YARD := -1
const LAST_TRACK_POSITION := 50
const RESERVED_HOME_ENTRY := 51
const FIRST_HOME_LANE := 52
const SAFE_GLOBAL_CELLS := [0, 8, 13, 21, 26, 34, 39, 47]

const TURN_ROLL_TIMEOUT_MS := 15000
const MOVE_SELECTION_TIMEOUT_MS := 45000
const MAX_MISSED_ROLLS_BEFORE_FORFEIT := 4

## Newest contracts used by the live Android client for new matches.
const DEFAULT_CLASSIC := "CLASSIC_V8"
const DEFAULT_TEAM := "TEAM_UP_V7"

const CLASSIC_KEYS := ["CLASSIC_V1", "CLASSIC_V2", "CLASSIC_V3", "CLASSIC_V4", "CLASSIC_V5", "CLASSIC_V6", "CLASSIC_V7", "CLASSIC_V8"]
const TEAM_KEYS := ["TEAM_UP_V1", "TEAM_UP_V2", "TEAM_UP_V3", "TEAM_UP_V4", "TEAM_UP_V5", "TEAM_UP_V6", "TEAM_UP_V7"]

const IDS := {
	"CLASSIC_V1": "ludoproof-standard-v1", "CLASSIC_V2": "ludoproof-standard-v2",
	"CLASSIC_V3": "ludoproof-standard-v3", "CLASSIC_V4": "ludoproof-standard-v4",
	"CLASSIC_V5": "ludoproof-standard-v5", "CLASSIC_V6": "ludoproof-standard-v6",
	"CLASSIC_V7": "ludoproof-standard-v7", "CLASSIC_V8": "ludoproof-standard-v8",
	"TEAM_UP_V1": "ludoproof-team-v1", "TEAM_UP_V2": "ludoproof-team-v2",
	"TEAM_UP_V3": "ludoproof-team-v3", "TEAM_UP_V4": "ludoproof-team-v4",
	"TEAM_UP_V5": "ludoproof-team-v5", "TEAM_UP_V6": "ludoproof-team-v6",
	"TEAM_UP_V7": "ludoproof-team-v7",
}

static func is_known(key: String) -> bool:
	return key in CLASSIC_KEYS or key in TEAM_KEYS

static func is_team_key(key: String) -> bool:
	return key in TEAM_KEYS

static func ruleset_id(key: String) -> String:
	return IDS.get(key, "")

## Opening bonus: first roll counts as six while all four tokens are in the yard (v3+).
static func has_opening_six(key: String) -> bool:
	return key in ["CLASSIC_V3", "CLASSIC_V4", "CLASSIC_V5", "CLASSIC_V6", "CLASSIC_V7", "CLASSIC_V8",
		"TEAM_UP_V3", "TEAM_UP_V4", "TEAM_UP_V5", "TEAM_UP_V6", "TEAM_UP_V7"]

## Stranded-player comeback entry (classic v7/v8, team v6/v7).
static func has_comeback(key: String) -> bool:
	return key in ["CLASSIC_V7", "CLASSIC_V8", "TEAM_UP_V6", "TEAM_UP_V7"]

## Yard comeback counts misses without needing a prior capture (classic v8, team v7).
static func has_yard_comeback(key: String) -> bool:
	return key in ["CLASSIC_V8", "TEAM_UP_V7"]

## Classic v6+ ranks every place instead of ending at the first finisher.
static func finishes_all_places(key: String) -> bool:
	return key in ["CLASSIC_V6", "CLASSIC_V7", "CLASSIC_V8"]

## Team v4+ protects a cell holding two or more opposing-team tokens.
static func has_team_stack_protection(key: String) -> bool:
	return key in ["TEAM_UP_V4", "TEAM_UP_V5", "TEAM_UP_V6", "TEAM_UP_V7"]

## v4+ turn deadlines (15 s roll timer, forfeit after four misses).
static func has_turn_deadline(key: String) -> bool:
	return key in ["CLASSIC_V4", "CLASSIC_V5", "CLASSIC_V6", "CLASSIC_V7", "CLASSIC_V8",
		"TEAM_UP_V4", "TEAM_UP_V5", "TEAM_UP_V6", "TEAM_UP_V7"]
