package com.rekixo.ludopaws

data class OfflineFairnessMaterial(
    val eventIndex: Int,
    val playerId: String,
    val color: String?,
    val roundId: String?,
    val serverCommitment: String?,
    val clientCommitment: String?,
    val actorHash: String?,
    val previousStateHash: String?,
    val rulesetHash: String?,
    val proofDigest: String?,
    val outcome: Int?,
    val fairnessProtocol: String? = null,
    val previousFairnessDigest: String? = null,
    val fairnessDigest: String? = null,
)

object OfflineFairnessChain {
    const val PROTOCOL =
        "ludoproof-roll-chain-v1"

    private const val DOMAIN =
        "ludoproof:fairness-receipt:v1:"

    fun seal(
        material: OfflineFairnessMaterial,
        previousFairnessDigest: String?,
    ): OfflineFairnessMaterial {
        val normalizedPrevious =
            previousFairnessDigest
                ?.lowercase()
        val prepared =
            material.copy(
                fairnessProtocol =
                    PROTOCOL,
                previousFairnessDigest =
                    normalizedPrevious,
                fairnessDigest =
                    null,
            )
        return prepared.copy(
            fairnessDigest =
                digest(
                    prepared,
                ),
        )
    }

    fun verify(
        material: OfflineFairnessMaterial,
    ): Boolean {
        val supplied =
            material.fairnessDigest
                ?: return false
        if (
            material.fairnessProtocol !=
                PROTOCOL
        ) {
            return false
        }
        return supplied.equals(
            digest(
                material.copy(
                    fairnessDigest =
                        null,
                ),
            ),
            ignoreCase =
                true,
        )
    }

    fun verifyHistory(
        history: List<OfflineFairnessMaterial>,
    ): Boolean {
        val sealed =
            history.filter {
                it.fairnessDigest !=
                    null
            }
        if (
            sealed.isEmpty()
        ) {
            return true
        }

        var previous =
            sealed.first()
                .previousFairnessDigest

        for (
            event in sealed
        ) {
            if (
                event.previousFairnessDigest !=
                    previous ||
                !verify(
                    event,
                )
            ) {
                return false
            }
            previous =
                event.fairnessDigest
        }
        return true
    }

    private fun digest(
        material: OfflineFairnessMaterial,
    ): String =
        NativeDiceEngine
            .sha256Hex(
                DOMAIN +
                    NativeDiceEngine
                        .canonicalJson(
                            mapOf(
                                "protocol" to
                                    PROTOCOL,
                                "eventIndex" to
                                    material.eventIndex,
                                "eventId" to
                                    (
                                        "roll:" +
                                            material.eventIndex
                                        ),
                                "playerId" to
                                    material.playerId,
                                "color" to
                                    material.color,
                                "roundId" to
                                    material.roundId,
                                "serverCommitment" to
                                    material.serverCommitment,
                                "clientCommitment" to
                                    material.clientCommitment,
                                "actorHash" to
                                    material.actorHash,
                                "previousStateHash" to
                                    material.previousStateHash,
                                "rulesetHash" to
                                    material.rulesetHash,
                                "proofDigest" to
                                    material.proofDigest,
                                "outcome" to
                                    material.outcome,
                                "status" to
                                    "RESOLVED",
                                "timeoutReason" to
                                    null,
                                "replacementRoundAllowed" to
                                    null,
                                "previousFairnessDigest" to
                                    material.previousFairnessDigest,
                            ),
                        ),
            )
}
