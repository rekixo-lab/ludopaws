package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.model.*
import com.rekixo.ludopaws.feature.characters.domain.selection.StarterPawsAssignmentPolicy
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.online.domain.LudoPawsCharacterIdentityContract
import org.junit.Assert.*
import org.junit.Test

class DonkeyRegistrationTest {
    @Test fun donkeySelectsLocallyWithoutReplacingExistingCharacters() {
        val selection=CharacterSelection("starter_paws","donkey")
        assertTrue(LudoPawsCharacterCatalog.isValidSelection(selection))
        assertEquals(AnimalSpecies.DONKEY,LudoPawsCharacterCatalog.character("donkey")!!.species)
        assertEquals("donkey",CharacterRegistry.resolve("donkey").id)
        assertEquals(listOf("donkey","goat","duck","cat"),StarterPawsAssignmentPolicy.select(
            4,listOf("dog","goat","duck","cat"),0,"donkey",false))
        assertEquals(5,LudoPawsCharacterCatalog.charactersForPack("starter_paws").size)
    }
    @Test fun removedLionMigratesToDonkey() {
        assertEquals("donkey",LudoPawsCharacterCatalog.canonicalCharacterId("lion"))
        assertEquals("donkey",CharacterRegistry.resolve("lion").id)
        assertFalse(LudoPawsCharacterCatalog.characters.any { it.id == "lion" })
    }

    @Test fun oldProductionServerCannotRejectANewLocalDonkeySelection() {
        assertEquals("dog",LudoPawsCharacterIdentityContract.resolveForRequest("donkey"))
        assertEquals("donkey",LudoPawsCharacterIdentityContract.resolveRemote("donkey"))
        assertEquals("cat",LudoPawsCharacterIdentityContract.resolveForRequest("cat"))
    }
}
