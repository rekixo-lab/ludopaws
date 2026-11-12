extends RefCounted
## Where each player's card and dice sit around the board (pure, testable).
## Port of OfflinePlayerLayout.slotForColor / rotationQuarterTurns and the online
## addPlayerSlot rule. See docs/MATCH_RAIL_SPEC.md §2.

const TOP_LEFT := 0
const TOP_RIGHT := 1
const BOTTOM_RIGHT := 2
const BOTTOM_LEFT := 3
const COLORS := ["RED", "GREEN", "YELLOW", "BLUE"]

## Board quarter-turns that put `first_color`'s yard at the bottom-left.
static func rotation_quarter_turns(first_color: String) -> int:
	return (3 - COLORS.find(first_color) + 4) % 4

## Offline (and online 3-4 players): the card sits in its own yard's screen corner.
static func slot_for_color(color: String, first_color: String) -> int:
	return (COLORS.find(color) + rotation_quarter_turns(first_color)) % 4

## Online / Friends: 2 players are local BOTTOM_LEFT vs opponent TOP_RIGHT; 3-4 use yards.
static func online_slot(color: String, local_color: String, is_local: bool, player_count: int) -> int:
	if player_count != 2:
		return slot_for_color(color, local_color)
	return BOTTOM_LEFT if is_local else TOP_RIGHT

static func is_top(slot: int) -> bool:
	return slot == TOP_LEFT or slot == TOP_RIGHT

static func is_left(slot: int) -> bool:
	return slot == TOP_LEFT or slot == BOTTOM_LEFT

## Dice on the inner side of the card: left slots `card | dice`, right slots `dice | card`.
static func dice_after_card(slot: int) -> bool:
	return is_left(slot)

## The seat whose card is active: the turn seat, except while a committed move is still
## presenting (move lock) — then the previous roller keeps the dice (renderPlayerRails).
static func active_seat(turn_seat: int, presentation_locked: bool, presenting_seat: int) -> int:
	return presenting_seat if presentation_locked and presenting_seat >= 0 else turn_seat
