package com.rekixo.ludopaws.feature.offline.domain.ai

import com.rekixo.ludopaws.LudoPathEncoding
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.PlayerSnapshot

data class LudoPawsComputerMoveEvaluation(
    val tokenIndex: Int,
    val destination: Int,
    val score: Int,
    val captures: Int,
    val destinationSafe: Boolean,
    val threatRoutes: Int,
    val rescuedFromThreat: Boolean,
)

/**
 * Deterministic, presentation-side move policy for local CPU opponents.
 *
 * The policy never rolls dice and never mutates MatchSnapshot. It evaluates
 * only legal moves supplied by the authoritative local engine and uses
 * [LudoPathEncoding.destinationForRoll] for every candidate and threat route,
 * so CPU strategy cannot invent a second movement model.
 *
 * Strategy order is intentionally readable and weight based rather than opaque:
 * finish/capture important pieces, enter the home lane, rescue exposed pieces,
 * develop yard pieces, prefer safe landings, keep making progress, and subtract
 * exposure when opponents can capture the destination on a 1..6 roll.
 */
object LudoPawsComputerMovePolicy {
    fun chooseToken(
        state: MatchSnapshot,
        player: PlayerSnapshot,
        legalTokenIndexes: Set<Int>,
        outcome: Int,
    ): Int? =
        legalTokenIndexes
            .asSequence()
            .mapNotNull { tokenIndex ->
                evaluate(
                    state = state,
                    player = player,
                    tokenIndex = tokenIndex,
                    outcome = outcome,
                )
            }
            .maxWithOrNull(
                compareBy<LudoPawsComputerMoveEvaluation> {
                    it.score
                }.thenBy {
                    -it.tokenIndex
                },
            )
            ?.tokenIndex

    fun evaluate(
        state: MatchSnapshot,
        player: PlayerSnapshot,
        tokenIndex: Int,
        outcome: Int,
    ): LudoPawsComputerMoveEvaluation? {
        val source =
            player.tokens.getOrNull(tokenIndex)
                ?: return null
        val destination = if (state.pendingRoll?.comebackBonusApplied == true && source == -1) 0 else
            destinationForRoll(
                position = source,
                outcome = outcome,
            ) ?: return null

        val captures =
            captureCount(
                state = state,
                mover = player,
                destination = destination,
            )
        val sourceThreatRoutes =
            threatRoutes(
                state = state,
                mover = player,
                position = source,
            )
        val destinationThreatRoutes =
            threatRoutes(
                state = state,
                mover = player,
                position = destination,
            )
        val sourceSafe =
            isSafePosition(
                color = player.color,
                position = source,
            )
        val destinationSafe =
            isSafePosition(
                color = player.color,
                position = destination,
            )
        val rescuedFromThreat =
            sourceThreatRoutes > 0 &&
                destinationThreatRoutes == 0
        val enteredHomeLane =
            LudoPathEncoding.isTrackPosition(source) &&
                destination in HOME_LANE_START until LudoPathEncoding.HOME_POSITION

        var score =
            progressScore(destination)

        if (destination == LudoPathEncoding.HOME_POSITION) {
            score += SCORE_FINISH
        }
        score += captures * SCORE_CAPTURE_EACH

        if (enteredHomeLane) {
            score += SCORE_HOME_LANE
        }
        if (rescuedFromThreat) {
            score += SCORE_RESCUE
        }
        if (source == YARD_POSITION) {
            score += SCORE_YARD_EXIT
        }
        if (destinationSafe) {
            score += SCORE_SAFE_LANDING
        }

        score -= destinationThreatRoutes * PENALTY_THREAT_ROUTE
        if (
            sourceSafe &&
            !destinationSafe &&
            destinationThreatRoutes > 0
        ) {
            score -= PENALTY_LEAVE_SAFETY_FOR_DANGER
        }

        // Stable tie-break keeps replays/debugging deterministic.
        score += (MAX_TOKEN_INDEX - tokenIndex).coerceAtLeast(0)

        return LudoPawsComputerMoveEvaluation(
            tokenIndex = tokenIndex,
            destination = destination,
            score = score,
            captures = captures,
            destinationSafe = destinationSafe,
            threatRoutes = destinationThreatRoutes,
            rescuedFromThreat = rescuedFromThreat,
        )
    }

    fun destinationForRoll(
        position: Int,
        outcome: Int,
    ): Int? =
        LudoPathEncoding.destinationForRoll(
            position = position,
            roll = outcome,
        )

    private fun captureCount(
        state: MatchSnapshot,
        mover: PlayerSnapshot,
        destination: Int,
    ): Int {
        if (
            !LudoPathEncoding.isTrackPosition(destination) ||
            isSafePosition(mover.color, destination)
        ) {
            return 0
        }
        val targetGlobal =
            globalCell(
                color = mover.color,
                position = destination,
            ) ?: return 0

        return state.players
            .asSequence()
            .filter { opponent ->
                opponent.playerId != mover.playerId &&
                    !sameTeam(mover, opponent)
            }
            .sumOf { opponent ->
                opponent.tokens.count { position ->
                    globalCell(
                        color = opponent.color,
                        position = position,
                    ) == targetGlobal
                }
            }
    }

    private fun threatRoutes(
        state: MatchSnapshot,
        mover: PlayerSnapshot,
        position: Int,
    ): Int {
        if (
            !LudoPathEncoding.isTrackPosition(position) ||
            isSafePosition(mover.color, position)
        ) {
            return 0
        }
        val targetGlobal =
            globalCell(
                color = mover.color,
                position = position,
            ) ?: return 0

        return state.players
            .asSequence()
            .filter { opponent ->
                opponent.playerId != mover.playerId &&
                    !sameTeam(mover, opponent)
            }
            .sumOf { opponent ->
                opponent.tokens
                    .asSequence()
                    .map(LudoPathEncoding::normalizeLegacyEntry)
                    .filter(LudoPathEncoding::isTrackPosition)
                    .sumOf { opponentPosition ->
                        (1..6).count { roll ->
                            val destination =
                                LudoPathEncoding.destinationForRoll(
                                    position = opponentPosition,
                                    roll = roll,
                                )
                            destination != null &&
                                LudoPathEncoding.isTrackPosition(destination) &&
                                globalCell(
                                    color = opponent.color,
                                    position = destination,
                                ) == targetGlobal
                        }
                    }
            }
    }

    private fun isSafePosition(
        color: String,
        position: Int,
    ): Boolean {
        if (position in HOME_LANE_START..LudoPathEncoding.HOME_POSITION) {
            return true
        }
        val global =
            globalCell(
                color = color,
                position = position,
            ) ?: return false
        return global in SAFE_GLOBAL_CELLS
    }

    private fun globalCell(
        color: String,
        position: Int,
    ): Int? {
        val normalized =
            LudoPathEncoding.normalizeLegacyEntry(position)
        if (!LudoPathEncoding.isTrackPosition(normalized)) {
            return null
        }
        val offset =
            START_OFFSETS[color]
                ?: return null
        return (offset + normalized) % TRACK_LENGTH
    }

    private fun sameTeam(
        first: PlayerSnapshot,
        second: PlayerSnapshot,
    ): Boolean =
        !first.teamId.isNullOrBlank() &&
            first.teamId == second.teamId

    private fun progressScore(
        destination: Int,
    ): Int =
        when (destination) {
            YARD_POSITION -> 0
            in 0..51 -> (destination + 1) * SCORE_PROGRESS_STEP
            in HOME_LANE_START until LudoPathEncoding.HOME_POSITION ->
                SCORE_HOME_LANE_PROGRESS_BASE +
                    (destination - HOME_LANE_START + 1) * SCORE_HOME_LANE_STEP
            LudoPathEncoding.HOME_POSITION -> SCORE_HOME_PROGRESS
            else -> 0
        }

    private const val YARD_POSITION = -1
    private const val HOME_LANE_START = 52
    private const val TRACK_LENGTH = 52
    private const val MAX_TOKEN_INDEX = 3

    private const val SCORE_FINISH = 120_000
    private const val SCORE_CAPTURE_EACH = 25_000
    private const val SCORE_HOME_LANE = 18_000
    private const val SCORE_RESCUE = 11_000
    private const val SCORE_YARD_EXIT = 6_000
    private const val SCORE_SAFE_LANDING = 3_500
    private const val SCORE_PROGRESS_STEP = 30
    private const val SCORE_HOME_LANE_PROGRESS_BASE = 4_000
    private const val SCORE_HOME_LANE_STEP = 350
    private const val SCORE_HOME_PROGRESS = 8_000
    private const val PENALTY_THREAT_ROUTE = 3_200
    private const val PENALTY_LEAVE_SAFETY_FOR_DANGER = 2_500

    private val START_OFFSETS =
        mapOf(
            "RED" to 0,
            "GREEN" to 13,
            "YELLOW" to 26,
            "BLUE" to 39,
        )

    private val SAFE_GLOBAL_CELLS =
        setOf(
            0,
            8,
            13,
            21,
            26,
            34,
            39,
            47,
        )
}
