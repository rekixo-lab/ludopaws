package com.rekixo.ludopaws.feature.store.domain

import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPackStoreCatalogTest {
    @Test
    fun `starter paws is free content ready and linked to complete character content`() {
        val starter =
            requireNotNull(
                StoreCosmeticCatalog.find(
                    StoreCosmeticCatalog.DEFAULT_CHARACTER_PACK,
                ),
            )

        assertEquals(
            CosmeticCategory.CHARACTER_PACK,
            starter.category,
        )
        assertEquals(
            CosmeticUnlockKind.FREE,
            starter.unlockKind,
        )
        assertTrue(starter.contentAvailable)

        val pack =
            LudoPawsCharacterCatalog.pack(
                requireNotNull(starter.characterPackId),
            )
        assertNotNull(pack)
        val characters =
            LudoPawsCharacterCatalog.charactersForPack(
                requireNotNull(starter.characterPackId),
            )
        assertEquals(13, characters.size)
        characters.forEach { character ->
            assertNotNull(
                LudoPawsCharacterCatalog.voiceSet(
                    character.voiceSetId,
                ),
            )
            assertNotNull(
                LudoPawsCharacterCatalog.animationSet(
                    character.animationSetId,
                ),
            )
            assertTrue(
                character.fallbackDrawableName.startsWith("lp_"),
            )
        }
    }

    @Test
    fun `only ready animal packs and priced character offers appear`() {
        val packs = StoreCosmeticCatalog.characterPacks
        assertTrue(packs.isNotEmpty())
        assertTrue(packs.all { it.contentAvailable })
        assertTrue(packs.any { it.unlockKind == CosmeticUnlockKind.FREE })
        assertTrue(packs.any { it.unlockKind == CosmeticUnlockKind.GEMS })
        assertTrue(packs.any { CosmeticUnlockKind.REWARDED_ADS in it.unlockChannels })
        assertFalse(packs.any {
            it.unlockKind == CosmeticUnlockKind.EVENT || it.unlockKind == CosmeticUnlockKind.LEVEL
        })
    }

    @Test
    fun `future packs are removed instead of disabled placeholders`() {
        assertTrue(StoreCosmeticCatalog.characterPacks.none { it.characterPackId != "starter_paws" })
        assertTrue(StoreCosmeticCatalog.all.none { !it.contentAvailable })
    }

    @Test
    fun `event packs require a stable event key`() {
        StoreCosmeticCatalog.characterPacks
            .filter {
                it.unlockKind ==
                    CosmeticUnlockKind.EVENT
            }
            .forEach { offer ->
                assertFalse(
                    offer.eventKey.isNullOrBlank(),
                )
            }
    }
}
