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

    fun label(playerCount: Int, current: Int): String {
        val value = humans(playerCount, current)
        return "${value}H · ${playerCount - value}R"
    }
}
