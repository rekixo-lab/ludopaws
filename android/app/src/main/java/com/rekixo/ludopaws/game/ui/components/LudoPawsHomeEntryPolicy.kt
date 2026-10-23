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
