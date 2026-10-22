package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.settings.data.local.GameSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4AccessibilitySettingsTest {
    @Test
    fun defaultSettingsRetainSoundHapticsAndQuickChat() {
        val settings = GameSettings()
        assertTrue(settings.soundEnabled)
        assertTrue(settings.hapticsEnabled)
        assertTrue(settings.quickChatEnabled)
    }
}
