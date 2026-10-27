package com.rekixo.ludopaws.feature.safety

/** Local-only explanation for a capability disabled by build/server policy.
 * This release does not request Play age sharing or collect dates of birth.
 * An age band is never treated as server authorization.
 */
object PlayAgeSignalsObserver {
    /**
     * Local-only UX explanation; cannot grant multiplayer or purchases.
     * The Android build flag AND the server lockdown remain authoritative.
     */
    fun offlineExplanation(): String = offlineExplanationFor(AgeBand.UNKNOWN)

    internal fun offlineExplanationFor(band: AgeBand): String = when (band) {
        AgeBand.CHILD, AgeBand.TEEN ->
            "Online features aren't available in this release. You can play Ludo offline without entering your birthday."
        AgeBand.ADULT ->
            "Online play remains temporarily disabled until server safety checks are approved. You can play offline without entering your birthday."
        AgeBand.UNKNOWN ->
            "Online play is temporarily unavailable. No birthday is required; you can play offline."
    }
}
