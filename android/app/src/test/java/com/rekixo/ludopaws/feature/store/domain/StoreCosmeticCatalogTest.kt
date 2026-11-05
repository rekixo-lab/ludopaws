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
    fun shippedBoardsOnlyAppearInStore() {
        val boards = StoreCosmeticCatalog.boards
        assertEquals(7, boards.size)
        assertEquals(
            setOf("board_classic", "board_astral", "board_cloud_lagoon",
                "board_autumn_forest", "board_blossom_garden", "board_winter_haven", "board_monsoon_mire"),
            boards.map { it.id }.toSet(),
        )
        assertTrue(boards.all { it.contentAvailable })
        assertEquals("CLASSIC", requireNotNull(StoreCosmeticCatalog.find(StoreCosmeticCatalog.DEFAULT_BOARD)).title)
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
    fun unpublishedBoardsDicePawsAndAvatarsAreNotInTheShop() {
        assertTrue(StoreCosmeticCatalog.boards.all { it.contentAvailable })
        assertTrue(StoreCosmeticCatalog.dice.all { it.contentAvailable })
        assertTrue(StoreCosmeticCatalog.characterPacks.all { it.contentAvailable })
        assertTrue(StoreCosmeticCatalog.avatars.isEmpty())
        assertTrue(StoreCosmeticCatalog.all.none { it.category == CosmeticCategory.AVATAR })
        assertEquals(null, StoreCosmeticCatalog.findShopItem("avatar_2"))
        assertEquals(null, StoreCosmeticCatalog.findShopItem("avatar_4"))
        assertEquals(null, StoreCosmeticCatalog.findShopItem("board_chess"))
        assertEquals(null, StoreCosmeticCatalog.findShopItem("dice_diwali"))
        assertTrue(StoreCosmeticCatalog.findShopItem("board_classic") != null)
        assertTrue(StoreCosmeticCatalog.findShopItem("character_pack_starter_paws") != null)
        assertTrue(StoreCosmeticCatalog.boards.none { it.id in setOf("board_chess", "board_diwali", "board_neon") })
        assertTrue(StoreCosmeticCatalog.dice.none { it.id in setOf("dice_diwali", "dice_heart", "dice_cricket") })
        assertTrue(StoreCosmeticCatalog.characterPacks.none {
            it.id.startsWith("character_pack_") && it.id != StoreCosmeticCatalog.DEFAULT_CHARACTER_PACK
        })
        // Existing ownership IDs are intentionally recognized, not advertised.
        assertEquals(100, StoreCosmeticCatalog.findBuiltIn("avatar_2")?.gemPrice)
        assertEquals(100, StoreCosmeticCatalog.findBuiltIn("avatar_10")?.gemPrice)
        assertTrue(StoreCosmeticCatalog.DEFAULT_AVATAR in StoreCosmeticCatalog.defaultOwnedIds)
    }

    @Test
    fun archivedPaidPawsNeverBecomeFreeAndKeepStableEntitlementIds() {
        try {
            StoreCosmeticCatalog.replaceRemoteCatalog(
                emptyList(),
                setOf("character_panda", "character_monkey", "character_rabbit", "character_donkey"),
            )
            for (id in listOf("panda", "monkey", "rabbit", "donkey")) {
                assertEquals(null, StoreCosmeticCatalog.characterUnlock(id))
                assertEquals("character_$id", StoreCosmeticCatalog.characterEntitlementId(id))
            }
            for (id in listOf("dog", "cat", "goat", "duck")) {
                assertEquals(null, StoreCosmeticCatalog.characterEntitlementId(id))
            }
        } finally {
            StoreCosmeticCatalog.clearRemoteCatalog()
        }
    }

    @Test
    fun starterPawsIsTheOnlyPackUntilNewContentShips() {
        val packIds = StoreCosmeticCatalog.characterPacks.mapNotNull {
            it.id.takeIf { id -> id.startsWith("character_pack_") }
        }
        assertEquals(listOf(StoreCosmeticCatalog.DEFAULT_CHARACTER_PACK), packIds)
        assertTrue(StoreCosmeticCatalog.findBuiltIn("character_pack_jungle_paws") == null)
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
