extends RefCounted
## Result card text: title, subtitle and placements, read from the authoritative finished
## match (never minted here). Port of LudoPawsResultPresentation.model (title / subtitle /
## placements). XP and Gems lines come with progression and the wallet (G, H).
## mode: "COMPUTER" | "PASS_AND_PLAY" | "ONLINE" | "TEAM_UP"; state: engine/MatchSnapshot dict.

const Board = preload("res://scripts/game/board3d/classic_board_3d.gd")
const Seq = preload("res://scripts/game/reactions/seq.gd")

static func model(state: Dictionary, mode: String, viewer_id) -> Dictionary:
	var team := mode == "TEAM_UP"
	var players: Array = state.get("players", [])
	var viewer = null
	for p in players:
		if p["playerId"] == viewer_id:
			viewer = p
	var won: bool
	if team:
		won = state.get("winnerTeamId") != null and viewer != null and viewer.get("teamId") == state.get("winnerTeamId")
	else:
		won = viewer_id != null and viewer_id == state.get("winnerPlayerId")
	var ranks: Dictionary = {} if team else Board.finish_ranks(state)
	var my_rank = ranks.get(viewer_id) if viewer_id != null else null
	var title := "MATCH COMPLETE"
	if team and won:
		title = "YOUR TEAM WON"
	elif team and state.get("winnerTeamId") != null:
		title = "TEAM %s WINS" % state["winnerTeamId"]
	elif not team and mode != "PASS_AND_PLAY" and my_rank != null and int(my_rank) in [1, 2, 3, 4]:
		title = ["1ST PLACE", "2ND PLACE", "3RD PLACE", "4TH PLACE"][int(my_rank) - 1]
	var forfeited := players.filter(func(p): return p.get("forfeited", false))
	var viewer_forfeited := forfeited.any(func(p): return p["playerId"] == viewer_id)
	var timeout := forfeited.any(func(p): return p.get("forfeitReason") in ["TURN_TIMEOUT", "TEAMMATE_TURN_TIMEOUT"])
	var subtitle := "Thanks for playing!"
	if viewer_forfeited and timeout:
		subtitle = "Your side forfeited after missed turns."
	elif viewer_forfeited:
		subtitle = "Your side forfeited the match."
	elif not forfeited.is_empty() and timeout:
		subtitle = "Match ended after a turn timeout."
	elif not forfeited.is_empty():
		subtitle = "Match ended after a player left."
	elif team and won:
		subtitle = "A brilliant team victory!"
	elif team:
		subtitle = "Well played, team!"
	elif my_rank != null and int(my_rank) in [1, 2, 3, 4]:
		subtitle = ["Champion! Fantastic game.", "Great game!", "Well played!", "Keep playing, more wins ahead!"][int(my_rank) - 1]
	var placements: Array = []
	if team:
		var teams := ["A", "B"]
		teams = Seq.stable_sort(teams, func(a, b): return (0 if a == state.get("winnerTeamId") else 1) < (0 if b == state.get("winnerTeamId") else 1))
		for t in teams:
			var names := players.filter(func(p): return p.get("teamId") == t).map(func(p): return p.get("displayName", ""))
			var label := " + ".join(names)
			placements.append({"label": "WIN" if t == state.get("winnerTeamId") else "TEAM",
				"name": label if not label.strip_edges().is_empty() else "Team " + t,
				"you": players.any(func(p): return p["playerId"] == viewer_id and p.get("teamId") == t)})
	else:
		var order: Array = state.get("finishOrderPlayerIds", [])
		var rows: Array = []
		for i in players.size():
			var p: Dictionary = players[i]
			var rank = ranks.get(p["playerId"])
			if rank == null and order.has(p["playerId"]):
				rank = order.find(p["playerId"]) + 1
			rows.append({"player": p, "rank": rank, "seat": int(p.get("seat", i))})
		# compareBy(rank ?: 99).thenBy(seat); stable.
		rows = Seq.stable_sort(rows, func(a, b):
			var ra: int = 99 if a["rank"] == null else int(a["rank"])
			var rb: int = 99 if b["rank"] == null else int(b["rank"])
			return ra < rb or (ra == rb and a["seat"] < b["seat"]))
		for r in rows:
			placements.append({"label": "—" if r["rank"] == null else "%d%s" % [r["rank"], _ordinal(int(r["rank"]))],
				"name": r["player"].get("displayName", ""), "characterId": str(r["player"].get("characterId", "dog")),
				"you": r["player"]["playerId"] == viewer_id and mode != "PASS_AND_PLAY"})
	return {"title": title, "subtitle": subtitle, "placements": placements, "won": won}

static func _ordinal(rank: int) -> String:
	return {1: "ST", 2: "ND", 3: "RD"}.get(rank, "TH")
