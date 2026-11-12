@tool
extends RefCounted
## Character identity catalog. Port of LudoPawsCharacterCatalog.kt, CharacterDefinitions,
## StarterPawsPack and the per-species *Definition.kt identity fields.
## Rendering modules register in RENDERED as each species is ported.

const STARTER_PACK_ID := "starter_paws"
const STARTER_PACK_NAME := "3D Animal Paws"
const DEFAULT_CHARACTER_ID := "dog"
## Matches the server's FREE_HUMAN_CHARACTER_IDS; server bots are not restricted by it.
const FREE_HUMAN_CHARACTER_IDS := ["dog", "goat", "duck", "cat"]
## Compatibility for installs/matches persisted before the 3D roster.
const LEGACY_ALIASES := {"squirrel": "dog", "hedgehog": "goat", "sheep": "cat", "lion": "donkey"}

## id -> [displayName, species, personality, storeDisplayOrder]; all in pack starter_paws.
const CHARACTERS := {
	"cat": ["Cat", "CAT", "SASSY", 0],
	"dog": ["Dog", "DOG", "PLAYFUL", 1],
	"goat": ["Goat", "GOAT", "CURIOUS", 2],
	"duck": ["Duck", "DUCK", "CHEERFUL", 3],
	"donkey": ["Donkey", "DONKEY", "PLAYFUL", 4],
	"goat_lumi": ["Lumi Goat", "GOAT", "", 6],
	"cock": ["Cock", "COCK", "PROUD", 7],
	"pig": ["Pig", "PIG", "PLAYFUL", 8],
	"elephant": ["Elephant", "ELEPHANT", "GENTLE", 9],
	"horse": ["Horse", "HORSE", "BRAVE", 10],
	"rabbit": ["Rabbit", "RABBIT", "CURIOUS", 11],
	"panda": ["Panda", "PANDA", "GENTLE", 12],
	"monkey": ["Monkey", "MONKEY", "MISCHIEVOUS", 13],
	"giraffe": ["Giraffe", "GIRAFFE", "CURIOUS", 14],
}

## Live registry order (GeneratedCharacterEntries).
const REGISTRY_ORDER := ["dog", "goat", "duck", "cat", "donkey", "goat_lumi", "cock", "pig",
	"elephant", "horse", "rabbit", "panda", "monkey", "giraffe"]

## Species already ported to Godot: id -> definition script (renderer, motion, metadata).
const RENDERED := {
	"dog": preload("res://scripts/game/characters/dog/dog_definition.gd"),
	"goat": preload("res://scripts/game/characters/goat/goat_definition.gd"),
	"duck": preload("res://scripts/game/characters/duck/duck_definition.gd"),
	"cat": preload("res://scripts/game/characters/cat/cat_definition.gd"),
	"donkey": preload("res://scripts/game/characters/donkey/donkey_definition.gd"),
	"goat_lumi": preload("res://scripts/game/characters/goat_lumi/lumi_definition.gd"),
	"cock": preload("res://scripts/game/characters/cock/cock_definition.gd"),
	"pig": preload("res://scripts/game/characters/pig/pig_definition.gd"),
	"elephant": preload("res://scripts/game/characters/elephant/elephant_definition.gd"),
	"horse": preload("res://scripts/game/characters/horse/horse_definition.gd"),
	"rabbit": preload("res://scripts/game/characters/rabbit/rabbit_definition.gd"),
	"panda": preload("res://scripts/game/characters/panda/panda_definition.gd"),
	"monkey": preload("res://scripts/game/characters/monkey/monkey_definition.gd"),
	"giraffe": preload("res://scripts/game/characters/giraffe/giraffe_definition.gd"),
}

static func canonical_id(id) -> String:
	if id == null or str(id).strip_edges().is_empty():
		return ""
	var safe := str(id)
	return LEGACY_ALIASES.get(safe, safe)

static func is_known(id) -> bool:
	return CHARACTERS.has(canonical_id(id))

static func display_name(id) -> String:
	var key := canonical_id(id)
	return CHARACTERS[key][0] if CHARACTERS.has(key) else ""

static func is_free(id) -> bool:
	return canonical_id(id) in FREE_HUMAN_CHARACTER_IDS

## Definition script for a ported species, or null (the board then shows the fallback pawn).
static func definition(id):
	return RENDERED.get(canonical_id(id))

## CharacterRegistry.resolve: unknown ids resolve to the default character.
static func resolve_id(id) -> String:
	var key := canonical_id(id)
	return key if CHARACTERS.has(key) else DEFAULT_CHARACTER_ID
