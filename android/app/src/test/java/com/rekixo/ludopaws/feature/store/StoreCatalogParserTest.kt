package com.rekixo.ludopaws.feature.store.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCatalogParserTest {
    @Test
    fun overlaysPublishedMetadataForAnExistingRenderer() {
        val snapshot = StoreCatalogParser.parse(
            catalogResponse(
                items = arrayOf(
                    catalogItem(
                        id = "dice_classic",
                        category = "DICE",
                        name = "CLASSIC DICE",
                        channels = arrayOf("GEMS"),
                        gemPrice = 175,
                    ),
                ),
                version = 7,
            ),
            fetchedAtMillis = 1_800_000_000_000L,
        )

        val item = snapshot.items.single()
        assertEquals("dice_classic", item.id)
        assertEquals("CLASSIC DICE", item.title)
        assertEquals(175, item.gemPrice)
        assertEquals(7, snapshot.catalogVersion)
        assertEquals(1, item.catalogVersion)
        assertTrue(item.remoteManaged)
        assertTrue(item.contentAvailable)
        assertEquals("DICE", item.backendCategory)
    }

    @Test
    fun publishedMetadataCannotEnableComingSoonDice() {
        val snapshot = StoreCatalogParser.parse(
            catalogResponse(
                items = arrayOf(
                    catalogItem("dice_heart", "DICE", "HEART FESTIVAL", arrayOf("GEMS"), gemPrice = 175),
                ),
            ),
        )

        val item = snapshot.items.single()
        assertEquals("HEART FESTIVAL", item.title)
        assertEquals(175, item.gemPrice)
        assertTrue(item.remoteManaged)
        assertFalse(item.contentAvailable)
    }

    @Test
    fun starterPawMapsToExistingCharacterPackRenderer() {
        val snapshot = StoreCatalogParser.parse(
            catalogResponse(
                items = arrayOf(
                    catalogItem(
                        id = "character_pack_starter_paws",
                        category = "PAW",
                        name = "STARTER PAWS",
                        channels = arrayOf("FREE"),
                    ),
                ),
            ),
        )

        val item = snapshot.items.single()
        assertEquals(
            com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.CHARACTER_PACK,
            item.category,
        )
        assertEquals("PAW", item.backendCategory)
        assertTrue(item.contentAvailable)
    }

    @Test
    fun unknownPublishedItemsAreDiscoverableButRemainLockedWithoutALocalRenderer() {
        val snapshot = StoreCatalogParser.parse(
            catalogResponse(
                items = arrayOf(
                    catalogItem(
                        id = "dice_safari_01",
                        category = "DICE",
                        name = "SAFARI DICE",
                        channels = arrayOf("GEMS", "REWARDED_ADS"),
                        gemPrice = 80,
                        requiredCompletedAds = 3,
                    ),
                ),
            ),
        )

        val item = snapshot.items.single()
        assertEquals("SAFARI DICE", item.title)
        assertTrue(item.remoteManaged)
        assertFalse(item.contentAvailable)
        assertEquals(2, item.unlockChannels.size)
        assertEquals(80, item.gemPrice)
        assertEquals(3, item.requiredAdViews)
    }

    @Test
    fun suppressionIdsHideBundledLegacyOffersAndIgnoreMalformedIds() {
        val snapshot = StoreCatalogParser.parse(
            catalogResponse(
                items = arrayOf(
                    catalogItem("dice_safari_01", "DICE", "SAFARI DICE", arrayOf("FREE")),
                ),
                suppressedIds = arrayOf("dice_pumpkin", "not an id", "../dice_heart", "dice_safari_01"),
            ),
        )

        assertEquals(setOf("dice_pumpkin"), snapshot.suppressedIds)
    }

    @Test
    fun mergesDynamicItemsAndSuppressesKnownLegacyItems() {
        val snapshot = StoreCatalogParser.parse(
            catalogResponse(
                items = arrayOf(
                    catalogItem("dice_safari_01", "DICE", "SAFARI DICE", arrayOf("GEMS"), gemPrice = 80),
                ),
                suppressedIds = arrayOf("dice_pumpkin"),
            ),
        )

        try {
            com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
                .replaceRemoteCatalog(snapshot.items, snapshot.suppressedIds)

            val dynamicItem = com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
                .dice.firstOrNull { it.id == "dice_safari_01" }
            assertNotNull(dynamicItem)
            assertFalse(requireNotNull(dynamicItem).contentAvailable)
            assertFalse(com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.find("dice_pumpkin") != null)
            assertNotNull(com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.find("dice_classic"))
        } finally {
            com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.clearRemoteCatalog()
        }
    }

    @Test
    fun categoryMismatchDoesNotLeaveTheLegacyOfferVisibleInItsOldTab() {
        val snapshot = StoreCatalogParser.parse(
            catalogResponse(
                items = arrayOf(
                    catalogItem(
                        id = "dice_heart",
                        category = "PAW",
                        name = "Misclassified Paw",
                        channels = arrayOf("GEMS"),
                        gemPrice = 100,
                    ),
                ),
            ),
        )
        try {
            com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
                .replaceRemoteCatalog(snapshot.items, snapshot.suppressedIds)
            assertFalse(
                com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.dice
                    .any { it.id == "dice_heart" },
            )
            val remote = com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.characterPacks
                .firstOrNull { it.id == "dice_heart" }
            assertNotNull(remote)
            assertFalse(requireNotNull(remote).contentAvailable)
        } finally {
            com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.clearRemoteCatalog()
        }
    }

    @Test
    fun rejectsPublishedResponsesWithInvalidOrDuplicateItemIds() {
        val invalid = catalogItem(id = "INVALID", category = "DICE", name = "BAD", channels = arrayOf("FREE"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            StoreCatalogParser.parse(catalogResponse(items = arrayOf(invalid)))
        }

        val duplicate = catalogItem("dice_heart", "DICE", "HEART", arrayOf("GEMS"), gemPrice = 20)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            StoreCatalogParser.parse(catalogResponse(items = arrayOf(duplicate, duplicate)))
        }
    }

    @Test
    fun rejectsPublishedItemsWithMalformedAssetMetadata() {
        val invalidDigest = catalogItem("dice_heart", "DICE", "HEART", arrayOf("GEMS"), gemPrice = 100)
            .put(
                "asset",
                JSONObject()
                    .put("thumbnail", "r2://published/dice_heart/v1/thumbnail.png")
                    .put("manifest", "r2://published/dice_heart/v1/manifest.json")
                    .put("version", 1)
                    .put("sha256", "not-a-sha")
                    .put("format", "BUILT_IN"),
            )
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            StoreCatalogParser.parse(catalogResponse(items = arrayOf(invalidDigest)))
        }

        val unsupportedFormat = catalogItem("dice_other", "DICE", "OTHER", arrayOf("FREE"))
            .put("asset", JSONObject()
                .put("thumbnail", "r2://published/dice_other/v1/thumbnail.png")
                .put("manifest", "r2://published/dice_other/v1/manifest.json")
                .put("version", 1)
                .put("sha256", "b".repeat(64))
                .put("format", "EXE"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            StoreCatalogParser.parse(catalogResponse(items = arrayOf(unsupportedFormat)))
        }
    }

    @Test
    fun staleCacheCanBeMarkedDisplayOnlyByCaller() {
        val parsed = StoreCatalogParser.parse(
            catalogResponse(
                items = arrayOf(
                    catalogItem("dice_heart", "DICE", "HEART", arrayOf("GEMS"), gemPrice = 100),
                ),
            ),
        )
        val stale = parsed.copy(items = parsed.items.map { it.copy(contentAvailable = false) }, stale = true)

        assertTrue(stale.stale)
        assertFalse(stale.items.single().contentAvailable)
    }

    private fun catalogResponse(
        items: Array<JSONObject> = emptyArray(),
        suppressedIds: Array<String> = emptyArray(),
        version: Int = 4,
    ) = JSONObject()
        .put("ok", true)
        .put("catalogVersion", version)
        .put("updatedAt", "2026-10-14T12:00:00.000Z")
        .put("items", JSONArray().apply { items.forEach { item -> put(item) } })
        .put("suppressedIds", JSONArray().apply { suppressedIds.forEach { id -> put(id) } })

    private fun catalogItem(
        id: String,
        category: String,
        name: String,
        channels: Array<String>,
        gemPrice: Int = 0,
        requiredCompletedAds: Int = 0,
    ) = JSONObject()
        .put("id", id)
        .put("category", category)
        .put("name", name)
        .put("description", "Catalog test item")
        .put("status", "PUBLISHED")
        .put("contentReady", true)
        .put(
            "asset",
            JSONObject()
                .put("thumbnail", "r2://published/$id/v1/thumbnail.png")
                .put("manifest", "r2://published/$id/v1/manifest.json")
                .put("version", 1)
                .put("sha256", "a".repeat(64))
                .put("format", "BUILT_IN"),
        )
        .put(
            "unlock",
            JSONObject()
                .put("channels", JSONArray().apply { channels.forEach { channel -> put(channel) } })
                .apply {
                    if ("GEMS" in channels) {
                        put("gems", JSONObject().put("enabled", true).put("price", gemPrice))
                    }
                    if ("REWARDED_ADS" in channels) {
                        put(
                            "rewardedAds",
                            JSONObject().put("enabled", true).put("requiredCompletedAds", requiredCompletedAds),
                        )
                    }
                },
        )
        .put(
            "availability",
            JSONObject()
                .put("worldwide", true)
                .put("allowedCountries", JSONArray())
                .put("blockedCountries", JSONArray())
                .put("startsAt", JSONObject.NULL)
                .put("endsAt", JSONObject.NULL),
        )
        .put("catalogVersion", 1)
}
