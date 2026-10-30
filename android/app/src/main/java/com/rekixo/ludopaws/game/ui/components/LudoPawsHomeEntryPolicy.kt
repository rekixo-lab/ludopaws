package com.rekixo.ludopaws

/** Presentation only: token position 57 and all match rules remain authoritative. */
internal data class LudoPawsHomeEntry(
    val startedAtMillis: Long,
    val rank: Int,
) {
    fun elapsed(now: Long): Long = if (startedAtMillis == 0L)
        LudoPawsHomeEntryPolicy.DURATION_MS else now - startedAtMillis
}

internal object LudoPawsHomeEntryPolicy {
    const val CELEBRATE_MS = 1800L
    const val OPEN_MS = 550L
    const val WALK_START_MS = CELEBRATE_MS + OPEN_MS
    const val STAGGER_MS = 500L
    const val WALK_MS = 1050L
    const val CLOSE_START_MS = WALK_START_MS + 3 * STAGGER_MS + WALK_MS
    const val PLAQUE_START_MS = CLOSE_START_MS + 600L
    const val DURATION_MS = PLAQUE_START_MS + 1400L
    // Door/walk/plaque animation may continue without blocking the next legal action.
    const val INTERACTION_HOLD_MS = 900L

    fun complete(player: PlayerSnapshot): Boolean =
        player.tokens.size == 4 && player.tokens.all { it == LudoPathEncoding.HOME_POSITION }

    fun newlyComplete(previous: MatchSnapshot?, current: MatchSnapshot): Boolean =
        previous != null && previous.matchId == current.matchId && current.players.any { player ->
            complete(player) && previous.players.any { it.playerId == player.playerId && !complete(it) }
        }

    fun smooth(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun walk(elapsed: Long, index: Int): Float =
        ((elapsed - WALK_START_MS - index * STAGGER_MS).toFloat() / WALK_MS).coerceIn(0f, 1f)

    fun door(elapsed: Long): Float = smooth((elapsed - CELEBRATE_MS).toFloat() / OPEN_MS) *
        (1f - smooth((elapsed - CLOSE_START_MS).toFloat() / 600f))

    fun plaque(elapsed: Long): Float = smooth((elapsed - PLAQUE_START_MS).toFloat() / 500f)
}

/** One trusted placement mapping for result screens, yard badges and center plaques. */
internal object LudoPawsFinishPresentation {
    private val cachedRanks = ThreadLocal<Pair<MatchSnapshot, Map<String, Int>>>()

    fun ranks(state: MatchSnapshot): Map<String, Int> {
        cachedRanks.get()?.takeIf { it.first === state }?.let { return it.second }
        return resolveRanks(state).also { cachedRanks.set(state to it) }
    }

    private fun resolveRanks(state: MatchSnapshot): Map<String, Int> {
        if (state.matchMode == "TEAM_UP" || state.status == "WAITING") return emptyMap()
        val ids = state.players.map { it.playerId }.toSet()
        val order = state.finishOrderPlayerIds.filter { it in ids }.distinct().toMutableList()
        if (order.isEmpty()) state.winnerPlayerId?.takeIf { it in ids }?.let { order.add(it) }
        // Only one unplaced player can be inferred from a completed race.
        if (state.status == "FINISHED" && order.size == ids.size - 1) {
            ids.firstOrNull { it !in order }?.let { order.add(it) }
        }
        return order.mapIndexed { index, id -> id to index + 1 }.toMap()
    }

    fun label(rank: Int): String = when (rank) {
        1 -> "WINNER"
        2 -> "2ND"
        3 -> "3RD"
        4 -> "4TH"
        else -> "HOME"
    }

    fun headline(rank: Int?): String = when (rank) {
        1 -> "YOU WON!"
        2 -> "2ND PLACE"
        3 -> "3RD PLACE"
        4 -> "4TH PLACE"
        else -> "MATCH COMPLETE"
    }

    fun standings(state: MatchSnapshot): String {
        val placements = ranks(state)
        return state.players.filter { it.playerId in placements }
            .sortedBy { placements[it.playerId] }
            .joinToString("\n") { "${label(placements.getValue(it.playerId))}  ·  ${it.displayName}" }
    }

    fun metalColor(rank: Int, teamColor: Int): Int = when (rank) {
        1 -> 0xFFFFD568.toInt()
        2 -> 0xFFE2EAF2.toInt()
        3 -> 0xFFE8AA70.toInt()
        else -> teamColor
    }
}
