package com.rekixo.ludopaws.feature.offline.domain.engine

/** The first seat owns the device; Robot matches always retain at least one CPU. */
object RobotTeamComposition {
    fun humans(playerCount: Int, requested: Int): Int {
        require(playerCount in 2..4)
        return requested.coerceIn(1, playerCount - 1)
    }

    fun nextHumans(playerCount: Int, current: Int): Int {
        val value = humans(playerCount, current)
        return if (value == playerCount - 1) 1 else value + 1
    }

    /** Re-tapping the selected size cycles; a different size always begins at one human. */
    fun humansAfterPlayerTileTap(
        playerCount: Int,
        previouslySelectedPlayerCount: Int,
        previouslySelectedHumans: Int,
    ): Int = if (playerCount == previouslySelectedPlayerCount) {
        nextHumans(playerCount, previouslySelectedHumans)
    } else {
        humans(playerCount, 1)
    }

    /** Two readable lines fit inside the existing 98/106dp player tile. */
    fun tileLabel(playerCount: Int, requestedHumans: Int, selected: Boolean): String {
        val humanCount = humans(playerCount, requestedHumans)
        val robotCount = playerCount - humanCount
        val humanLabel = if (humanCount == 1) "HUMAN" else "HUMANS"
        val robotLabel = if (robotCount == 1) "ROBOT" else "ROBOTS"
        val cycleIcon = if (selected && playerCount > 2) " ↻" else ""
        return "$humanCount $humanLabel\n$robotCount $robotLabel$cycleIcon"
    }

    fun label(playerCount: Int, current: Int): String {
        val value = humans(playerCount, current)
        return "${value}H · ${playerCount - value}R"
    }
}
