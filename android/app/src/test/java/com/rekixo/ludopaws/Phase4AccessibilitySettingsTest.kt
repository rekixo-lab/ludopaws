package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.settings.data.local.GameSettings
import org.junit.Assert.assertFalse
import org.junit.Test

class Phase4AccessibilitySettingsTest {
    @Test fun reducedMotionIsOptInAndOtherDefaultsRemainUnchanged() {
        val settings = GameSettings()
        assertFalse(settings.reduceMotionEnabled)
        assert(settings.soundEnabled)
        assert(settings.hapticsEnabled)
        assert(settings.quickChatEnabled)
    }
}
