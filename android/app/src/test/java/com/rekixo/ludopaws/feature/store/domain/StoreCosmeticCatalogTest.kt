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
    fun shippedBoardsAreAvailableAndDraftBoardsRemainLocked() {
        val boards = StoreCosmeticCatalog.boards
        assertEquals(15, boards.size)
        assertEquals(setOf("board_classic", "board_astral", "board_cloud_lagoon", "board_autumn_forest", "board_blossom_garden", "board_winter_haven", "board_monsoon_mire"), boards.filter { it.contentAvailable }.map { it.id }.toSet())

        val classic =
            requireNotNull(
                StoreCosmeticCatalog.find(StoreCosmeticCatalog.DEFAULT_BOARD),
            )
        assertEquals("CLASSIC", classic.title)
        assertTrue(classic.contentAvailable)

        val lockedBoards =
            boards.filter {
                !it.contentAvailable
            }
        assertEquals(8, lockedBoards.size)
        assertTrue(
            lockedBoards.all {
                !it.contentAvailable && it.title.isNotBlank() && it.title != "LOCKED BOARD"
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
    fun pearlPawIsAvailableButOnlyClassicDiceAreGrantedByDefault() {
        val available = StoreCosmeticCatalog.dice.filter { it.contentAvailable }
        assertEquals(listOf("dice_classic", "dice_pearl_paw"), available.map { it.id })
        assertEquals(CosmeticUnlockKind.FREE, available.first().unlockKind)
        val pearl = available.last()
        assertEquals(100, pearl.gemPrice)
        assertEquals(20, pearl.requiredAdViews)
        assertEquals(listOf(CosmeticUnlockKind.GEMS, CosmeticUnlockKind.REWARDED_ADS), pearl.unlockChannels)
        assertEquals(setOf("dice_classic"), StoreCosmeticCatalog.defaultOwnedIds.filter { it.startsWith("dice_") }.toSet())
    }

    @Test
    fun lumiAndDonkeyHaveIndependentVerifiedAdRequirements() {
        assertEquals(10, StoreCosmeticCatalog.characterUnlock("goat_lumi")?.requiredAdViews)
        assertEquals(15, StoreCosmeticCatalog.characterUnlock("donkey")?.requiredAdViews)
        StoreCosmeticCatalog.characterUnlocks.forEach {
            assertTrue(it.contentAvailable)
            if (it.id in setOf("character_goat_lumi", "character_donkey")) {
                assertEquals(CosmeticUnlockKind.GEMS, it.unlockKind)
                assertEquals(if (it.id == "character_goat_lumi") 50 else 75, it.gemPrice)
                assertEquals(listOf(CosmeticUnlockKind.GEMS, CosmeticUnlockKind.REWARDED_ADS), it.unlockChannels)
            } else {
                assertEquals(CosmeticUnlockKind.GEMS, it.unlockKind)
                assertEquals(if (it.id in setOf("character_panda", "character_monkey")) 150 else 300, it.gemPrice)
                assertEquals(listOf(CosmeticUnlockKind.GEMS, CosmeticUnlockKind.PLAY_PRODUCT), it.unlockChannels)
            }
            assertTrue(it.id !in StoreCosmeticCatalog.defaultOwnedIds)
        }
    }

    @Test
    fun allProposedComingSoonPricesAreVisibleButNotPurchasable() {
        val boards = StoreCosmeticCatalog.boards.associateBy { it.id }
        val dice = StoreCosmeticCatalog.dice.associateBy { it.id }
        val avatars = StoreCosmeticCatalog.avatars.associateBy { it.id }
        for ((id, price) in mapOf(
            "board_chess" to 50, "board_diwali" to 75, "board_denim" to 75,
            "board_neon" to 150, "board_pirate" to 200, "board_alien" to 250,
            "board_penguin" to 250,
        )) {
            assertEquals(price, boards.getValue(id).gemPrice)
            assertTrue(!boards.getValue(id).contentAvailable)
        }
        for ((id, price) in mapOf(
            "dice_diwali" to 50, "dice_football" to 75, "dice_cricket" to 100,
            "dice_summers" to 150, "dice_colors" to 150, "dice_heart" to 200,
        )) {
            assertEquals(price, dice.getValue(id).gemPrice)
            assertTrue(!dice.getValue(id).contentAvailable)
        }
        assertEquals(100, avatars.getValue("avatar_2").gemPrice)
        assertEquals(100, avatars.getValue("avatar_10").gemPrice)
        assertEquals(150, StoreCosmeticCatalog.characterUnlock("panda")?.gemPrice)
        assertEquals(150, StoreCosmeticCatalog.characterUnlock("monkey")?.gemPrice)
    }

    @Test
    fun starterPawsHasSixReservedFuturePacksThatRemainLocked() {
        val futurePacks =
            StoreCosmeticCatalog.characterPacks.filter {
                it.characterPackId != "starter_paws"
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
                    it.unlockKind == CosmeticUnlockKind.REWARDED_ADS ||
                        CosmeticUnlockKind.REWARDED_ADS in it.unlockChannels
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
