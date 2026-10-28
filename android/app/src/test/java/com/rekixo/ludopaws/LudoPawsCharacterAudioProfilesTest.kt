package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsCharacterAudioProfiles
import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsFootstepStyle
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPawsCharacterAudioProfilesTest {
    @Test
    fun everyShippedCharacterHasOneAudioProfile() {
        assertEquals(
            CharacterDefinitions.all.map { it.character.id }.toSet(),
            LudoPawsCharacterAudioProfiles.all.keys,
        )
    }

    @Test
    fun donkeyRemainsSilentAndLumiUsesGoatVoiceAssets() {
        assertNull(LudoPawsCharacterAudioProfiles.all.getValue("donkey").voiceAssetOwnerId)
        assertEquals("goat", LudoPawsCharacterAudioProfiles.all.getValue("goat_lumi").voiceAssetOwnerId)
        assertFalse("donkey" in LudoPawsCharacterAudioProfiles.supportedVoiceAssetIds)
    }

    @Test
    fun existingStarterSoundPathsStayTheSame() {
        for (id in listOf("dog", "goat", "duck", "cat")) {
            val profile = LudoPawsCharacterAudioProfiles.all.getValue(id)
            assertEquals(listOf(profile.fallbackStepPaths), profile.stepAssetFamilies())
        }
        assertEquals(
            LudoPawsFootstepStyle.HOOF,
            LudoPawsCharacterAudioProfiles.all.getValue("horse").footstepStyle,
        )
    }

    @Test
    fun futureRecordedStepsTakePrecedenceOverLegacyStyleFallback() {
        val horse = LudoPawsCharacterAudioProfiles.all.getValue("horse")
        assertTrue(
            horse.stepAssetFamilies().first().first().endsWith(
                "horse/lp_sfx_step_horse_01.wav",
            ),
        )
        assertEquals(horse.fallbackStepPaths, horse.stepAssetFamilies().last())
    }
}
