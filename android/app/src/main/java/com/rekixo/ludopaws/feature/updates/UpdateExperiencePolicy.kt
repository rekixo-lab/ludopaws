package com.rekixo.ludopaws.feature.updates

/** Small deterministic rules, independent of Play services and Android UI. */
internal object UpdateExperiencePolicy {
    private const val REPEAT_PROMPT_AFTER_MS = 72L * 60L * 60L * 1000L

    fun shouldShowWhatsNew(
        previousVersion: Long,
        currentVersion: Long,
        installedPackageHasBeenUpdated: Boolean,
    ): Boolean {
        if (currentVersion <= 0L) return false
        // A first install must never be mistaken for an upgrade. Older
        // installs that predate the preference are detected via package times.
        return if (previousVersion < 0L) installedPackageHasBeenUpdated
        else currentVersion > previousVersion
    }

    fun shouldPromptForUpdate(
        availableVersion: Int,
        previouslyPromptedVersion: Int,
        lastPromptTimestamp: Long,
        now: Long,
        critical: Boolean,
    ): Boolean {
        if (availableVersion <= 0 || now <= 0L) return false
        if (critical || availableVersion != previouslyPromptedVersion) return true
        if (lastPromptTimestamp <= 0L || lastPromptTimestamp > now) return true
        return now - lastPromptTimestamp >= REPEAT_PROMPT_AFTER_MS
    }
}
