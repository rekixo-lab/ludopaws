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
    fun donkeyHasNoFallbackAndLumiKeepsGoatFallbackWhileAcceptingOwnRecordings() {
        assertNull(LudoPawsCharacterAudioProfiles.all.getValue("donkey").voiceAssetOwnerId)
        assertEquals("goat", LudoPawsCharacterAudioProfiles.all.getValue("goat_lumi").voiceAssetOwnerId)
        assertTrue("donkey" in LudoPawsCharacterAudioProfiles.supportedVoiceAssetIds)
        assertEquals(CharacterDefinitions.all.map { it.character.id }.toSet(), LudoPawsCharacterAudioProfiles.supportedVoiceAssetIds)
        assertTrue(LudoPawsCharacterAudioProfiles.fallbackVocalNames("donkey", com.rekixo.ludopaws.feature.characters.domain.model.VoiceCue.SIX).isEmpty())
        assertEquals(listOf("lp_vocal_goat_six_01", "lp_vocal_goat_six_02"),
            LudoPawsCharacterAudioProfiles.fallbackVocalNames("goat_lumi", com.rekixo.ludopaws.feature.characters.domain.model.VoiceCue.SIX))
    }

    @Test
    fun allThirteenOwnVoiceFoldersResolveAndLumiDoesNotResolveToGoatsFolder() {
        for (id in LudoPawsCharacterAudioProfiles.all.keys) {
            val definition = CharacterDefinitions.all.single { it.character.id == id }
            assertTrue(id, definition.voiceSet.supportedCues.containsAll(
                com.rekixo.ludopaws.feature.characters.domain.model.VoiceCue.entries))
            val names = listOf("lp_vocal_${id}_six_01", "lp_vocal_${id}_six_02")
            val paths = LudoPawsCharacterAudioProfiles.vocalAssetPaths(names)
            assertEquals(listOf(
                "audio/sfx/voices/$id/${names[0]}.wav", "audio/sfx/voices/$id/${names[0]}.ogg",
                "audio/sfx/voices/$id/${names[1]}.wav", "audio/sfx/voices/$id/${names[1]}.ogg",
            ), paths)
        }
        assertTrue(LudoPawsCharacterAudioProfiles.vocalAssetPaths(listOf("lp_vocal_unknown_six_01")).isEmpty())
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
