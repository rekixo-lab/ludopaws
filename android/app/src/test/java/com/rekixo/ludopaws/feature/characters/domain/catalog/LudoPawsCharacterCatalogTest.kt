package com.rekixo.ludopaws.feature.characters.domain.catalog

import com.rekixo.ludopaws.feature.characters.domain.model.AnimalPersonality
import com.rekixo.ludopaws.feature.characters.domain.model.AnimalSpecies
import com.rekixo.ludopaws.feature.characters.domain.model.CharacterSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPawsCharacterCatalogTest {
    @Test
    fun `starter pack contains the registered production 3d animals in deterministic order`() {
        val pack =
            requireNotNull(
                LudoPawsCharacterCatalog
                    .pack(LudoPawsCharacterCatalog.STARTER_PACK_ID),
            )

        assertTrue(pack.starter)
        assertEquals(
            listOf("dog", "goat", "duck", "cat", "donkey", "goat_lumi", "cock", "pig", "elephant", "horse", "rabbit", "panda"),
            pack.characterIds,
        )
        assertEquals(
            pack.characterIds,
            LudoPawsCharacterCatalog
                .charactersForPack(pack.id)
                .map { it.id },
        )
    }

    @Test
    fun `production animals carry matching species personality voice animation and placeholder identity`() {
        val dog = requireNotNull(LudoPawsCharacterCatalog.character("dog"))
        val goat = requireNotNull(LudoPawsCharacterCatalog.character("goat"))
        val duck = requireNotNull(LudoPawsCharacterCatalog.character("duck"))
        val cat = requireNotNull(LudoPawsCharacterCatalog.character("cat"))

        assertEquals(AnimalSpecies.DOG, dog.species)
        assertEquals(AnimalPersonality.PLAYFUL, dog.personality)
        assertEquals("dog_default", dog.voiceSetId)
        assertEquals("dog_default", dog.animationSetId)
        assertEquals("lp_3d_pawn_placeholder", dog.fallbackDrawableName)

        assertEquals(AnimalSpecies.GOAT, goat.species)
        assertEquals(AnimalPersonality.CURIOUS, goat.personality)
        assertEquals(AnimalSpecies.DUCK, duck.species)
        assertEquals(AnimalPersonality.CHEERFUL, duck.personality)
        assertEquals(AnimalSpecies.CAT, cat.species)
        assertEquals(AnimalPersonality.SASSY, cat.personality)
    }

    @Test
    fun `every character resolves its matching voice and animation set`() {
        for (character in LudoPawsCharacterCatalog.characters) {
            val voiceSet = LudoPawsCharacterCatalog.voiceSet(character.voiceSetId)
            val animationSet = LudoPawsCharacterCatalog.animationSet(character.animationSetId)

            assertNotNull(voiceSet)
            assertNotNull(animationSet)
            assertEquals(character.species, voiceSet?.species)
            assertTrue(voiceSet?.supportedCues?.isNotEmpty() == true)
            assertTrue(animationSet?.supportedCues?.isNotEmpty() == true)
        }
    }

    @Test
    fun `valid selection remains unchanged`() {
        val selection =
            CharacterSelection(
                packId = "starter_paws",
                characterId = "goat",
            )

        assertTrue(LudoPawsCharacterCatalog.isValidSelection(selection))
        assertEquals(
            selection,
            LudoPawsCharacterCatalog.resolveSelection(
                selection.packId,
                selection.characterId,
            ),
        )
    }

    @Test
    fun `legacy persisted ids resolve to canonical 3d animals`() {
        assertEquals("dog", LudoPawsCharacterCatalog.character("squirrel")?.id)
        assertEquals("goat", LudoPawsCharacterCatalog.character("hedgehog")?.id)
        assertEquals("cat", LudoPawsCharacterCatalog.character("sheep")?.id)
        assertEquals(
            CharacterSelection("starter_paws", "cat"),
            LudoPawsCharacterCatalog.resolveSelection("starter_paws", "sheep"),
        )
    }

    @Test
    fun `corrupt pack or character falls back safely to starter dog`() {
        val fallback = LudoPawsCharacterCatalog.defaultSelection

        assertEquals("dog", fallback.characterId)
        assertEquals(
            fallback,
            LudoPawsCharacterCatalog.resolveSelection("unknown_pack", "duck"),
        )
        assertEquals(
            fallback,
            LudoPawsCharacterCatalog.resolveSelection("starter_paws", "unknown_character"),
        )
        assertEquals(
            fallback,
            LudoPawsCharacterCatalog.resolveSelection(null, null),
        )
    }

    @Test
    fun `pack without explicit character resolves its first valid character`() {
        assertEquals(
            LudoPawsCharacterCatalog.defaultSelection,
            LudoPawsCharacterCatalog.resolveSelection("starter_paws", null),
        )
    }

    @Test
    fun `character cannot be selected outside its pack`() {
        val invalid =
            CharacterSelection(
                packId = "missing_pack",
                characterId = "dog",
            )

        assertFalse(LudoPawsCharacterCatalog.isValidSelection(invalid))
    }
}
