package com.rekixo.ludopaws.feature.store.domain

import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCosmeticCatalogTest {
    @Test
    fun cosmeticIdsAreUnique() {
        val ids =
            StoreCosmeticCatalog
                .all
                .map {
                    it.id
                }

        assertEquals(
            ids.size,
            ids.toSet()
                .size,
        )
    }

    @Test
    fun everyCategoryHasAFreeDefault() {
        CosmeticCategory
            .entries
            .forEach {
                    category ->
                val defaultId =
                    StoreCosmeticCatalog
                        .defaultId(
                            category,
                        )
                val item =
                    requireNotNull(
                        StoreCosmeticCatalog
                            .find(
                                defaultId,
                            ),
                    )

                assertEquals(
                    category,
                    item.category,
                )
                assertEquals(
                    CosmeticUnlockKind.FREE,
                    item.unlockKind,
                )
                assertTrue(
                    defaultId in
                        StoreCosmeticCatalog
                            .defaultOwnedIds,
                )
            }
    }

    @Test
    fun onlyClassicBoardIsAvailableAndOtherEightRemainLocked() {
        val boards = StoreCosmeticCatalog.boards
        assertEquals(9, boards.size)

        val classic =
            requireNotNull(
                StoreCosmeticCatalog.find(StoreCosmeticCatalog.DEFAULT_BOARD),
            )
        assertEquals("CLASSIC", classic.title)
        assertTrue(classic.contentAvailable)

        val lockedBoards =
            boards.filter {
                it.id != StoreCosmeticCatalog.DEFAULT_BOARD
            }
        assertEquals(8, lockedBoards.size)
        assertTrue(
            lockedBoards.all {
                !it.contentAvailable && it.title == "LOCKED BOARD"
            },
        )
    }

    @Test
    fun contentReadyPaidOrRewardedCosmeticsDoNotUseClientOnlyLevelProgress() {
        val unlockable = StoreCosmeticCatalog.all.filter {
            it.contentAvailable &&
                it.unlockKind in setOf(CosmeticUnlockKind.GEMS, CosmeticUnlockKind.REWARDED_ADS)
        }
        assertTrue(unlockable.isNotEmpty())
        assertTrue(unlockable.all { it.requiredLevel == 0 })
    }

    @Test
    fun onlyClassicDiceAreAvailableAndGrantedByDefault() {
        val available = StoreCosmeticCatalog.dice.filter { it.contentAvailable }
        assertEquals(listOf("dice_classic"), available.map { it.id })
        assertEquals(CosmeticUnlockKind.FREE, available.single().unlockKind)
        assertEquals(setOf("dice_classic"), StoreCosmeticCatalog.defaultOwnedIds.filter { it.startsWith("dice_") }.toSet())
    }

    @Test
    fun lumiAndDonkeyHaveIndependentVerifiedAdRequirements() {
        assertEquals(10, StoreCosmeticCatalog.characterUnlock("goat_lumi")?.requiredAdViews)
        assertEquals(15, StoreCosmeticCatalog.characterUnlock("donkey")?.requiredAdViews)
        StoreCosmeticCatalog.characterUnlocks.forEach {
            assertTrue(it.contentAvailable)
            assertEquals(CosmeticUnlockKind.REWARDED_ADS, it.unlockKind)
            assertTrue(it.id !in StoreCosmeticCatalog.defaultOwnedIds)
        }
    }

    @Test
    fun starterPawsHasSixReservedFuturePacksThatRemainLocked() {
        val futurePacks =
            StoreCosmeticCatalog.characterPacks.filter {
                it.id != StoreCosmeticCatalog.DEFAULT_CHARACTER_PACK
            }

        assertEquals(6, futurePacks.size)
        assertTrue(futurePacks.all { !it.contentAvailable })
        assertTrue(futurePacks.any { it.id == "character_pack_jungle_paws" })
    }

    @Test
    fun paidCosmeticsHavePositiveGemPrices() {
        val gemItems =
            StoreCosmeticCatalog
                .all
                .filter {
                    it.unlockKind ==
                        CosmeticUnlockKind.GEMS
                }

        assertTrue(
            gemItems.isNotEmpty(),
        )
        assertTrue(
            gemItems.all {
                it.gemPrice >
                    0
            },
        )
    }

    @Test
    fun rewardedItemsHavePositiveTargets() {
        val adItems =
            StoreCosmeticCatalog
                .all
                .filter {
                    it.unlockKind ==
                        CosmeticUnlockKind
                            .REWARDED_ADS
                }

        assertTrue(
            adItems.isNotEmpty(),
        )
        assertTrue(
            adItems.all {
                it.requiredAdViews >
                    0
            },
        )
    }
}
