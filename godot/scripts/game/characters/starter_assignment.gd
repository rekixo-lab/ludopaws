extends RefCounted
## Which Paw sits in each local seat: only unlocked characters, every seat unique, the
## player's chosen Paw first. Presentation only; never part of dice, moves or winners.
## Port of StarterPawsAssignmentPolicy.kt (+ RobotTeamComposition.humans).

const Characters = preload("res://scripts/game/characters/catalog.gd")

## Starter Paws pack order (all registered characters, registry order).
static func starter_ids() -> Array:
	return Characters.REGISTRY_ORDER.duplicate()

static func robot_humans(player_count: int, requested: int) -> int:
	return clampi(requested, 1, player_count - 1)

static func normalize(player_count: int, requested: Array, preferred: String, computer_mode: bool,
		unlocked: Array, human_count := 1) -> Array:
	assert(player_count >= 2 and player_count <= 4)
	var humans := robot_humans(player_count, human_count) if computer_mode else player_count
	var available := starter_ids().filter(func(id): return unlocked.has(id))
	assert(available.size() >= humans and available.size() >= player_count)
	var canonical_preferred := Characters.canonical_id(preferred)
	var safe_preferred: String = canonical_preferred if available.has(canonical_preferred) \
		else ("dog" if available.has("dog") else available[0])
	var canon := requested.map(func(id): return Characters.canonical_id(id) if id != null else "")
	if computer_mode:
		var human: String = canon[0] if not canon.is_empty() and available.has(canon[0]) else safe_preferred
		var used := [human]
		var out := [human]
		for seat in range(1, player_count):
			var want = canon[seat] if seat < canon.size() else ""
			var assigned: String = want if available.has(want) and not used.has(want) else available.filter(func(id): return not used.has(id))[0]
			out.append(assigned)
			used.append(assigned)
		return out
	var normalized: Array = []
	normalized.resize(player_count)
	var taken: Array = []
	for slot in player_count:
		var want = canon[slot] if slot < canon.size() else ""
		var candidate = null
		if want != "" and available.has(want) and not taken.has(want):
			candidate = want
		elif slot == 0 and not taken.has(safe_preferred):
			candidate = safe_preferred
		if candidate != null:
			normalized[slot] = candidate
			taken.append(candidate)
	for slot in player_count:
		if normalized[slot] != null:
			continue
		var free := available.filter(func(id): return not taken.has(id))
		var fallback: String = free[0] if not free.is_empty() else available[slot % available.size()]
		normalized[slot] = fallback
		taken.append(fallback)
	return normalized

## Picking a Paw for one seat: locked choices are ignored; owned Paws swap seats so every
## seat stays unique; changing a CPU never takes the human's Paw.
static func select(player_count: int, current: Array, slot: int, requested: String, computer_mode: bool,
		unlocked: Array, human_count := 1) -> Array:
	var humans := robot_humans(player_count, human_count) if computer_mode else player_count
	var available := starter_ids().filter(func(id): return unlocked.has(id))
	var want := Characters.canonical_id(requested)
	var preferred: String = current[0] if not current.is_empty() else "dog"
	var normalized := normalize(player_count, current, preferred, computer_mode, unlocked, humans)
	if want == "" or not available.has(want):
		return normalized
	var previous: String = normalized[slot]
	if previous == want:
		return normalized
	var owner := normalized.find(want)
	if computer_mode and slot >= humans and owner >= 0 and owner < humans:
		return normalized
	normalized[slot] = want
	if owner >= 0 and owner != slot:
		normalized[owner] = previous
	return normalized
