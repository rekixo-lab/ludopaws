package com.rekixo.ludopaws.feature.proof.domain.core

/**
 * Consumer-facing explanation of the existing proof system.
 *
 * This object contains presentation copy only. It does not participate in
 * randomness, commitments, proof verification, legal moves, or match state.
 */
object FairDiceExplainer {
    const val HOME_ACTION_LABEL =
        "FAIR DICE"
    const val DIALOG_TITLE =
        "VERIFIED FAIR DICE"

    const val FRIENDLY_SUMMARY =
        "Ludo Paws checks every dice result so the game can show that the roll was produced by the configured fair-dice system instead of being chosen by the app UI."

    const val ONLINE_SUMMARY =
        "Online: the game server generates each dice result with cryptographic randomness and applies the game rules. Requests can be retried without rolling twice. Online dice are server-authoritative; the receipt is not independent proof of fairness."

    const val OFFLINE_SUMMARY =
        "Offline: the device uses the same frozen v4 derivation locally and recomputes its proof. Offline play has no remote EntroNex attestation."

    const val ADVANCED_DETAILS =
        "Advanced details: online dice use rejection sampling of cryptographic random bytes in the match server. Offline EntroNex v4 proof flow uses the frozen v4 HKDF, rejection sampling and Natural World derivation locally, then recomputes the local proof."

    fun dialogBody(): String =
        buildString {
            append(FRIENDLY_SUMMARY)
            append("\n\n")
            append(ONLINE_SUMMARY)
            append("\n\n")
            append(OFFLINE_SUMMARY)
            append("\n\n")
            append(ADVANCED_DETAILS)
        }
}
