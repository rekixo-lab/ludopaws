package com.rekixo.ludopaws.feature.proof.domain.core

/**
 * Consumer-facing explanation of LudoPaws' native dice implementation.
 * The receipt is locally reproducible evidence, not a claim of independent
 * third-party certification.
 */
object FairDiceExplainer {
    const val HOME_ACTION_LABEL = "FAIR DICE"
    const val DIALOG_TITLE = "LUDOPAWS NATIVE DICE"

    const val FRIENDLY_SUMMARY =
        "Ludo Paws generates dice results with its own secure random engine and records a receipt so a completed roll can be checked against the match history."

    const val ONLINE_SUMMARY =
        "Online: the authoritative LudoPaws match server generates each dice result, applies the rules, and stores the result before returning it to players."

    const val OFFLINE_SUMMARY =
        "Offline: the device uses its native secure random generator and a local audit receipt. Because the device hosts the game, this is not independent remote verification."

    const val ADVANCED_DETAILS =
        "The native dice engine derives a uniform face with rejection sampling, binds the receipt to the match and event, and hashes the receipt for replay checks. This is a LudoPaws implementation and is not presented as third-party certification."

    fun dialogBody(): String =
        buildString {
            append(FRIENDLY_SUMMARY)
            append("\n\n")
            append(ONLINE_SUMMARY)
            append("\n\n")
            append(OFFLINE_SUMMARY)
            append("\n\n")
            append("Advanced details:")
            append("\n")
            append(ADVANCED_DETAILS)
        }
}
