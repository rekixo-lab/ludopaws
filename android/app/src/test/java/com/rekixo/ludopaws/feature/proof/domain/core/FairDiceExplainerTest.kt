package com.rekixo.ludopaws.feature.proof.domain.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FairDiceExplainerTest {
    @Test
    fun homeLabelUsesConsumerFriendlyFairDiceLanguage() {
        assertTrue(
            FairDiceExplainer.HOME_ACTION_LABEL.contains(
                "FAIR DICE",
            ),
        )
        assertFalse(
            FairDiceExplainer.HOME_ACTION_LABEL.contains(
                "PROOF",
            ),
        )
    }

    @Test
    fun friendlySummaryAvoidsProtocolJargon() {
        val summary =
            FairDiceExplainer.FRIENDLY_SUMMARY

        assertFalse(summary.contains("HKDF"))
        assertFalse(summary.contains("rejection sampling"))
        assertFalse(summary.contains("Natural World"))
    }

    @Test
    fun advancedDetailsStillExposeAuditVocabulary() {
        val details =
            FairDiceExplainer.ADVANCED_DETAILS

        assertTrue(details.contains("native dice engine"))
        assertTrue(details.contains("HKDF"))
        assertTrue(details.contains("rejection sampling"))
        assertTrue(details.contains("Natural World"))
    }

    @Test
    fun dialogBodyKeepsFriendlyExplanationBeforeAdvancedDetails() {
        val body =
            FairDiceExplainer.dialogBody()

        assertTrue(
            body.indexOf(
                FairDiceExplainer.FRIENDLY_SUMMARY,
            ) <
                body.indexOf(
                    FairDiceExplainer.ADVANCED_DETAILS,
                ),
        )
    }
}
