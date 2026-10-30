package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsCharacterAudioProfiles
import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsFootstepStyle
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.model.AnimalSpecies
import com.rekixo.ludopaws.feature.characters.domain.model.CharacterSelection
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.online.domain.LudoPawsCharacterIdentityContract
import org.junit.Assert.*
import org.junit.Test

class MonkeyRegistrationTest {
    @Test fun monkeyHasItsOwnRendererAndPersistableLocalIdentity() {
        assertEquals(AnimalSpecies.MONKEY, LudoPawsCharacterCatalog.character("monkey")!!.species)
        assertTrue(LudoPawsCharacterCatalog.isValidSelection(CharacterSelection("starter_paws", "monkey")))
        assertEquals("monkey", CharacterRegistry.resolve("monkey").id)
        assertNotSame(CharacterRegistry.resolve("dog").renderer, CharacterRegistry.resolve("monkey").renderer)
    }

    @Test fun newLocalMonkeyCannotBreakRequestsToTheExistingProductionServer() {
        assertEquals("dog", LudoPawsCharacterIdentityContract.resolveForRequest("monkey"))
        assertEquals("monkey", LudoPawsCharacterIdentityContract.resolveRemote("monkey"))
    }

    @Test fun monkeyUsesPawStepsWithoutBorrowingAnotherAnimalsVoice() {
        val profile = LudoPawsCharacterAudioProfiles.forCharacter("monkey")
        assertEquals(LudoPawsFootstepStyle.PAW, profile.footstepStyle)
        assertNull(profile.voiceAssetOwnerId)
        assertTrue(profile.fallbackStepPaths.isNotEmpty())
    }
}
