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

class PandaRegistrationTest {
    @Test fun pandaHasItsOwnRendererAndPersistableLocalIdentity() {
        assertEquals(AnimalSpecies.PANDA, LudoPawsCharacterCatalog.character("panda")!!.species)
        assertTrue(LudoPawsCharacterCatalog.isValidSelection(CharacterSelection("starter_paws", "panda")))
        assertEquals("panda", CharacterRegistry.resolve("panda").id)
        assertNotSame(CharacterRegistry.resolve("dog").renderer, CharacterRegistry.resolve("panda").renderer)
    }

    @Test fun newLocalPandaCannotBreakRequestsToTheExistingProductionServer() {
        assertEquals("dog", LudoPawsCharacterIdentityContract.resolveForRequest("panda"))
        assertEquals("panda", LudoPawsCharacterIdentityContract.resolveRemote("panda"))
    }

    @Test fun pandaUsesPawStepsWithoutBorrowingAnotherAnimalsVoice() {
        val profile = LudoPawsCharacterAudioProfiles.forCharacter("panda")
        assertEquals(LudoPawsFootstepStyle.PAW, profile.footstepStyle)
        assertNull(profile.voiceAssetOwnerId)
        assertTrue(profile.fallbackStepPaths.isNotEmpty())
    }
}
