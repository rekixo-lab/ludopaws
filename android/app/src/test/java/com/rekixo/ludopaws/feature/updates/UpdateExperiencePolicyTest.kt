package com.rekixo.ludopaws.feature.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateExperiencePolicyTest {
    @Test fun freshInstallDoesNotShowReleaseNotes() {
        assertFalse(UpdateExperiencePolicy.shouldShowWhatsNew(-1, 14, false))
        assertFalse(UpdateExperiencePolicy.shouldShowWhatsNew(14, 14, true))
    }

    @Test fun firstVersionTrackingCanDetectUpgradesFromOlderApps() {
        assertTrue(UpdateExperiencePolicy.shouldShowWhatsNew(-1, 14, true))
        assertTrue(UpdateExperiencePolicy.shouldShowWhatsNew(13, 14, false))
        assertFalse(UpdateExperiencePolicy.shouldShowWhatsNew(14, 13, true))
    }

    @Test fun newPlayVersionShowsPromptImmediately() {
        assertTrue(UpdateExperiencePolicy.shouldPromptForUpdate(15, 14, 1_000, 2_000, false))
    }

    @Test fun repeatedNormalPromptIsThrottledForThreeDays() {
        val threeDays = 72L * 60L * 60L * 1000L
        assertFalse(UpdateExperiencePolicy.shouldPromptForUpdate(15, 15, 1_000, 1_000 + threeDays - 1, false))
        assertTrue(UpdateExperiencePolicy.shouldPromptForUpdate(15, 15, 1_000, 1_000 + threeDays, false))
    }

    @Test fun criticalPlayUpdatesCanBePromptedAgain() {
        assertTrue(UpdateExperiencePolicy.shouldPromptForUpdate(15, 15, 1_000, 2_000, true))
    }

    @Test fun invalidOrFutureTimestampsDoNotBlockNewVersions() {
        assertFalse(UpdateExperiencePolicy.shouldPromptForUpdate(0, -1, 0, 1_000, false))
        assertTrue(UpdateExperiencePolicy.shouldPromptForUpdate(15, 15, 9_000, 1_000, false))
    }
}
