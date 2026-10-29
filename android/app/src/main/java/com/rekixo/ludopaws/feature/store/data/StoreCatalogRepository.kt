package com.rekixo.ludopaws.feature.store.data

import android.content.Context
import com.rekixo.ludopaws.feature.account.data.AccountRepository
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import org.json.JSONArray
import org.json.JSONObject

data class StoreCatalogSnapshot(
    val catalogVersion: Int,
    val items: List<StoreCosmetic>,
    val suppressedIds: Set<String>,
    val fetchedAtMillis: Long,
    val stale: Boolean,
) {
    fun asStale(): StoreCatalogSnapshot = copy(
        items = items.map { it.copy(purchaseAvailable = false) }, stale = true,
    )
}

object StoreCatalogParser {
    private val itemIdPattern = Regex("^[a-z0-9][a-z0-9_-]{2,63}$")

    fun parse(
        response: JSONObject,
        fetchedAtMillis: Long = System.currentTimeMillis(),
    ): StoreCatalogSnapshot {
        require(response.optBoolean("ok", false)) {
            "Catalog response did not confirm success."
        }
        val catalogVersion = response.optInt("catalogVersion", -1)
        require(catalogVersion >= 0) { "Catalog version is invalid." }
        val entries = response.optJSONArray("items")
            ?: throw IllegalArgumentException("Catalog items are missing.")
        require(entries.length() <= MAX_ITEMS) { "Catalog contains too many items." }

        val items = ArrayList<StoreCosmetic>(entries.length())
        val seen = mutableSetOf<String>()
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index)
                ?: throw IllegalArgumentException("Catalog item $index is not an object.")
            val parsed = parseItem(item, catalogVersion)
            require(seen.add(parsed.id)) { "Catalog contains a duplicate item ID." }
            items += parsed
        }

        val suppressedArray = response.optJSONArray("suppressedIds") ?: JSONArray()
        require(suppressedArray.length() <= MAX_SUPPRESSED_IDS) {
            "Catalog suppression list is too large."
        }
        val suppressed = buildSet {
            for (index in 0 until suppressedArray.length()) {
                val id = suppressedArray.optString(index)
                if (itemIdPattern.matches(id)) add(id)
            }
        } - seen

        return StoreCatalogSnapshot(
            catalogVersion = catalogVersion,
            items = items,
            suppressedIds = suppressed,
            fetchedAtMillis = fetchedAtMillis,
            stale = false,
        )
    }

    private fun parseItem(
        item: JSONObject,
        catalogVersion: Int,
    ): StoreCosmetic {
        val id = item.optString("id")
        require(itemIdPattern.matches(id)) { "Catalog item ID is invalid." }
        require(item.optString("status") == "PUBLISHED") { "Client catalog contains an unpublished item." }
        require(item.optBoolean("contentReady", false)) { "Client catalog contains incomplete content." }

        val backendCategory = item.optString("category")
        val category = when (backendCategory) {
            "PAW" -> CosmeticCategory.CHARACTER_PACK
            "BOARD" -> CosmeticCategory.BOARD
            "DICE" -> CosmeticCategory.DICE
            "AVATAR" -> CosmeticCategory.AVATAR
            else -> throw IllegalArgumentException("Catalog category is unsupported by this app.")
        }
        val itemVersion = item.optInt("catalogVersion", -1)
        require(itemVersion in 1..catalogVersion) { "Catalog item version is invalid." }
        val asset = item.optJSONObject("asset")
            ?: throw IllegalArgumentException("Catalog asset metadata is missing.")
        require(asset.optString("thumbnail").isNotBlank()) { "Catalog thumbnail metadata is missing." }
        require(asset.optString("manifest").isNotBlank()) { "Catalog manifest metadata is missing." }
        require(asset.optInt("version", 0) > 0) { "Catalog asset version is invalid." }
        require(Regex("^[a-f0-9]{64}$").matches(asset.optString("sha256"))) {
            "Catalog asset digest is invalid."
        }
        require(asset.optString("format") in SUPPORTED_ASSET_FORMATS) {
            "Catalog asset format is not supported by the catalog schema."
        }

        val unlock = item.optJSONObject("unlock")
            ?: throw IllegalArgumentException("Catalog unlock rules are missing.")
        val channelsJson = unlock.optJSONArray("channels")
            ?: throw IllegalArgumentException("Catalog unlock channels are missing.")
        require(channelsJson.length() in 1..6) { "Catalog unlock channels are invalid." }
        val channels = ArrayList<CosmeticUnlockKind>(channelsJson.length())
        for (index in 0 until channelsJson.length()) {
            val channelName = channelsJson.optString(index)
            val channel = when (channelName) {
                "FREE" -> CosmeticUnlockKind.FREE
                "REWARDED_ADS" -> CosmeticUnlockKind.REWARDED_ADS
                "GEMS" -> CosmeticUnlockKind.GEMS
                "LEVEL" -> CosmeticUnlockKind.LEVEL
                "EVENT" -> CosmeticUnlockKind.EVENT
                "PLAY_PRODUCT" -> CosmeticUnlockKind.PLAY_PRODUCT
                else -> throw IllegalArgumentException("Catalog has an unknown unlock channel.")
            }
            require(channel !in channels) { "Catalog unlock channels are duplicated." }
            channels += channel
        }

        val gems = unlock.optJSONObject("gems")
        val rewardedAds = unlock.optJSONObject("rewardedAds")
        if (CosmeticUnlockKind.GEMS in channels) {
            val gemPrice = gems?.optInt("price", 0) ?: 0
            require(gems?.optBoolean("enabled", false) == true) { "Catalog Gem unlock is not enabled." }
            require(gemPrice in 1..MAX_GEM_PRICE) { "Catalog Gem price is invalid." }
        }
        if (CosmeticUnlockKind.REWARDED_ADS in channels) {
            val completedAds = rewardedAds?.optInt("requiredCompletedAds", 0) ?: 0
            require(rewardedAds?.optBoolean("enabled", false) == true) {
                "Catalog rewarded-ad unlock is not enabled."
            }
            require(completedAds in 1..MAX_REWARDED_ADS) {
                "Catalog rewarded-ad target is invalid."
            }
        }

        val builtIn = StoreCosmeticCatalog.findBuiltIn(id)
        val rendererCompatible = builtIn != null &&
            builtIn.category == category &&
            builtIn.contentAvailable
        val prioritizedChannel = channels.firstOrNull { it in CHANNEL_PRIORITY }
            ?: channels.first()
        val title = item.optString("name").trim().take(MAX_TITLE_LENGTH)
            .ifBlank { id }
        val color = defaultColor(category)
        return StoreCosmetic(
            id = id,
            title = title,
            category = category,
            unlockKind = prioritizedChannel,
            gemPrice = if (CosmeticUnlockKind.GEMS in channels) gems?.optInt("price", 0) ?: 0 else 0,
            requiredLevel = unlock.optJSONObject("level")?.optInt("minimumLevel", 0) ?: 0,
            requiredAdViews = if (CosmeticUnlockKind.REWARDED_ADS in channels) {
                rewardedAds?.optInt("requiredCompletedAds", 0) ?: 0
            } else {
                0
            },
            previewSymbol = builtIn?.previewSymbol ?: previewSymbol(category),
            previewPrimary = builtIn?.previewPrimary ?: color.first,
            previewSecondary = builtIn?.previewSecondary ?: color.second,
            characterPackId = builtIn?.characterPackId,
            eventKey = unlock.optJSONObject("event")?.optString("eventKey")?.takeIf(String::isNotBlank),
            // Only an already-shipped renderer with a complete local asset pack is interactive.
            // Newly published assets remain visible but locked until the asset/render pipeline ships.
            contentAvailable = rendererCompatible,
            remoteManaged = true,
            unlockChannels = channels.toList(),
            catalogVersion = itemVersion,
            backendCategory = backendCategory,
        )
    }

    private fun previewSymbol(category: CosmeticCategory): String = when (category) {
        CosmeticCategory.BOARD -> "BOARD"
        CosmeticCategory.DICE -> "DICE"
        CosmeticCategory.AVATAR -> "AVATAR"
        CosmeticCategory.CHARACTER_PACK -> "PAW"
    }

    private fun defaultColor(category: CosmeticCategory): Pair<Int, Int> = when (category) {
        CosmeticCategory.BOARD -> 0xFFF7F4EC.toInt() to 0xFFDDD0B4.toInt()
        CosmeticCategory.DICE -> 0xFFF5F5F5.toInt() to 0xFF334155.toInt()
        CosmeticCategory.AVATAR -> 0xFF3986CB.toInt() to 0xFF183C73.toInt()
        CosmeticCategory.CHARACTER_PACK -> 0xFF20A86B.toInt() to 0xFF075B72.toInt()
    }

    private val SUPPORTED_ASSET_FORMATS = setOf("GLB", "GLTF", "PNG_SET", "SPRITE_ATLAS", "BUILT_IN")

    private val CHANNEL_PRIORITY = listOf(
        CosmeticUnlockKind.GEMS,
        CosmeticUnlockKind.REWARDED_ADS,
        CosmeticUnlockKind.FREE,
        CosmeticUnlockKind.LEVEL,
        CosmeticUnlockKind.EVENT,
        CosmeticUnlockKind.PLAY_PRODUCT,
    )

    private const val MAX_ITEMS = 500
    private const val MAX_SUPPRESSED_IDS = 1_000
    private const val MAX_GEM_PRICE = 2_000_000_000
    private const val MAX_REWARDED_ADS = 100
    private const val MAX_TITLE_LENGTH = 80
}

class StoreCatalogRepository(
    context: Context,
    private val accountRepository: AccountRepository,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun refresh(): StoreCatalogSnapshot {
        val now = System.currentTimeMillis()
        return try {
            val response = accountRepository.storeCatalog()
            val snapshot = StoreCatalogParser.parse(response, now)
            check(prefs.edit()
                .putString(KEY_JSON, response.toString())
                .putLong(KEY_FETCHED_AT, now)
                .commit()) { "Store catalog cache could not be saved." }
            snapshot
        } catch (error: Exception) {
            cached()
        }
    }

    /** Display metadata must remain available even when session refresh fails. */
    fun cached(): StoreCatalogSnapshot {
        return runCatching {
            val raw = prefs.getString(KEY_JSON, null) ?: return emptyCache()
            val cachedAt = prefs.getLong(KEY_FETCHED_AT, 0)
            StoreCatalogParser.parse(JSONObject(raw), cachedAt).let { parsed ->
                parsed.asStale()
            }
        }.getOrElse { emptyCache() }
    }

    private fun emptyCache() = StoreCatalogSnapshot(0, emptyList(), emptySet(), 0, stale = true)

    companion object {
        private const val PREFS_NAME = "ludopaws_store_catalog_cache_v1"
        private const val KEY_JSON = "published_catalog_json"
        private const val KEY_FETCHED_AT = "published_catalog_fetched_at"
    }
}
