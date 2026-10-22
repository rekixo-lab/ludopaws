package com.rekixo.ludopaws.feature.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyOnlineSafetyNoticeCopyTest {
    @Test
    fun noticeExplainsPublicNamesAndPrivateNickname() {
        assertTrue(FamilyOnlineSafetyNoticeCopy.NAME_BODY.contains("animal name"))
        assertTrue(FamilyOnlineSafetyNoticeCopy.NAME_BODY.contains("nickname stays on this device"))
    }

    @Test
    fun noticeExplainsChatRestrictionsAndPlayerControls() {
        assertTrue(FamilyOnlineSafetyNoticeCopy.CHAT_BODY.contains("fixed quick-chat"))
        assertTrue(FamilyOnlineSafetyNoticeCopy.CHAT_BODY.contains("Free-text chat and voice chat are not available"))
        assertTrue(FamilyOnlineSafetyNoticeCopy.PRIVACY_BODY.contains("deletion codes private"))
        assertTrue(FamilyOnlineSafetyNoticeCopy.PRIVACY_BODY.contains("Block or report players"))
        assertTrue(FamilyOnlineSafetyNoticeCopy.PRIVACY_BODY.contains("Settings"))
    }

    @Test
    fun noticeDoesNotMisstateStoreOrAdsAsUnavailable() {
        val copy = listOf(
            FamilyOnlineSafetyNoticeCopy.INTRO,
            FamilyOnlineSafetyNoticeCopy.NAME_BODY,
            FamilyOnlineSafetyNoticeCopy.CHAT_BODY,
            FamilyOnlineSafetyNoticeCopy.PRIVACY_BODY,
        ).joinToString(" ")
        assertFalse(copy.contains("ads and purchases are unavailable", ignoreCase = true))
        assertTrue(FamilyOnlineSafetyNoticeCopy.FOOTER.contains("Continue"))
    }
}
