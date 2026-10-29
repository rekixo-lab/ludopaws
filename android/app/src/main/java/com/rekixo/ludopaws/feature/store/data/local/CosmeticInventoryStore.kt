package com.rekixo.ludopaws.feature.store.data.local

import android.content.Context
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticAcquireResult
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic

class CosmeticInventoryStore(
    context: Context,
) {
    private val appContext =
        context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            StorePreferences.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun applyServerSnapshot(snapshot: org.json.JSONObject, expectedAccountId: String): Boolean {
        if (!GemWalletStore(appContext).applyServerSnapshot(snapshot, expectedAccountId)) return false
        val selected = snapshot.optJSONObject("selectedCosmetics") ?: org.json.JSONObject()
        val selectedPackId = selected.optString("CHARACTER_PACK")
        val cosmetic = StoreCosmeticCatalog.find(selectedPackId)
        if (cosmetic != null && cosmetic.contentAvailable && cosmetic.characterPackId != null) {
            syncCharacterPackSelection(cosmetic)
        }
        return true
    }

    private fun hasCurrentAccountSnapshot(): Boolean {
        val accountId = AccountSessionStore(appContext).load()?.accountId ?: return false
        return prefs.getString(StorePreferences.KEY_WALLET_ACCOUNT_ID, null) == accountId
    }

    /**
     * Returns the owned inventory for the current account in one read.
     * Settings uses this to populate Boards and Dice without reloading account
     * credentials and the owned set once per catalog item.
     */
    fun ownedCosmeticIds(): Set<String> {
        val shippedFreeIds = StoreCosmeticCatalog.all
            .filter(::isShippedFreeCosmetic)
            .map { it.id }.toSet()
        return if (!com.rekixo.ludopaws.BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED || !hasCurrentAccountSnapshot()) {
            shippedFreeIds
        } else {
            shippedFreeIds + (prefs.getStringSet(
                StorePreferences.KEY_OWNED_COSMETICS,
                emptySet(),
            )?.toSet() ?: emptySet())
        }
    }

    private fun isShippedFreeCosmetic(cosmetic: StoreCosmetic): Boolean {
        val shipped = StoreCosmeticCatalog.findBuiltIn(cosmetic.id) ?: return false
        return shipped.category == cosmetic.category && shipped.contentAvailable &&
            shipped.unlockKind == CosmeticUnlockKind.FREE && shipped.requiredLevel == 0 &&
            cosmetic.contentAvailable && cosmetic.unlockKind == CosmeticUnlockKind.FREE &&
            cosmetic.requiredLevel == 0
    }

    fun selectOfflineFreeCosmetic(cosmetic: StoreCosmetic): Boolean {
        val current = StoreCosmeticCatalog.find(cosmetic.id) ?: return false
        if (current.category != cosmetic.category || !isShippedFreeCosmetic(current)) return false
        prefs.edit().putString("offline_selection_" + current.category.name, current.id).apply()
        if (current.category == CosmeticCategory.DICE && developerDicePreviewAllowed()) {
            prefs.edit().remove("developer_dice_preview").apply()
        }
        return true
    }

    fun clearOfflineSelection(category: CosmeticCategory) {
        prefs.edit().remove("offline_selection_" + category.name).apply()
        if (category == CosmeticCategory.DICE && developerDicePreviewAllowed()) {
            prefs.edit().remove("developer_dice_preview").apply()
        }
    }

    fun isOwned(
        cosmeticId: String,
    ): Boolean =
        cosmeticId in ownedCosmeticIds()

    fun selectedId(
        category: CosmeticCategory,
    ): String {
        prefs.getString("offline_selection_" + category.name, null)
            ?.takeIf { id -> StoreCosmeticCatalog.find(id)?.let {
                it.category == category && isShippedFreeCosmetic(it)
            } == true }?.let { return it }
        if (!hasCurrentAccountSnapshot()) return StoreCosmeticCatalog.defaultId(category)
        return prefs.getString(
            selectedKey(
                category,
            ),
            null,
        )
            ?.takeIf {
                StoreCosmeticCatalog
                    .find(
                        it,
                    )
                    ?.let { item ->
                        item.category == category &&
                            item.contentAvailable
                    } == true
            }
            ?.takeIf { isOwned(it) }
            ?: StoreCosmeticCatalog
                .defaultId(
                    category,
                )
    }

    fun rewardedAdProgress(
        cosmeticId: String,
    ): Int {
        if (!hasCurrentAccountSnapshot()) return 0
        return prefs.getInt(
            StorePreferences
                .KEY_AD_PROGRESS_PREFIX +
                cosmeticId,
            0,
        )
            .coerceAtLeast(
                0,
            )
    }

    fun hasVerifiedEventEntitlement(
        cosmeticId: String,
    ): Boolean {
        // Local preferences are a display cache and cannot prove an event entitlement.
        return false
    }

    @Synchronized
    fun acquireOrSelect(
        cosmetic: StoreCosmetic,
        currentLevel: Int,
    ): CosmeticAcquireResult {
        if (!cosmetic.contentAvailable) {
            return CosmeticAcquireResult.CONTENT_UNAVAILABLE
        }
        if (!isSelectableCharacterPack(cosmetic)) return CosmeticAcquireResult.INVALID
        if (currentLevel < cosmetic.requiredLevel) return CosmeticAcquireResult.NEED_LEVEL
        if (isOwned(cosmetic.id)) {
            // Selection is performed by /api/wallet/cosmetics/select, never by local cache mutation.
            return CosmeticAcquireResult.INVALID
        }
        return when (cosmetic.unlockKind) {
            CosmeticUnlockKind.GEMS -> {
                if (GemWalletStore(appContext).balance() < cosmetic.gemPrice) {
                    CosmeticAcquireResult.NEED_GEMS
                } else {
                    // The old local balance - cosmetic.gemPrice debit is intentionally disabled.
                    // The authenticated server performs the debit and inventory grant atomically.
                    CosmeticAcquireResult.INVALID
                }
            }
            CosmeticUnlockKind.REWARDED_ADS ->
                if (rewardedAdProgress(cosmetic.id) < cosmetic.requiredAdViews) {
                    CosmeticAcquireResult.NEED_REWARDED_ADS
                } else {
                    CosmeticAcquireResult.INVALID
                }
            CosmeticUnlockKind.EVENT ->
                if (hasVerifiedEventEntitlement(cosmetic.id)) CosmeticAcquireResult.INVALID
                else CosmeticAcquireResult.NEED_EVENT
            CosmeticUnlockKind.FREE,
            CosmeticUnlockKind.LEVEL,
            CosmeticUnlockKind.PLAY_PRODUCT -> CosmeticAcquireResult.INVALID
        }
    }

    @Synchronized
    fun recordVerifiedRewardedAd(
        cosmeticId: String,
    ): Int {
        // No client-side callback increments progress; only a new server snapshot may do so.
        return rewardedAdProgress(cosmeticId)
    }

    @Synchronized
    fun recordVerifiedEventEntitlement(
        cosmeticId: String,
    ): Boolean = false

    private fun isSelectableCharacterPack(
        cosmetic: StoreCosmetic,
    ): Boolean {
        if (
            cosmetic.category !=
            CosmeticCategory.CHARACTER_PACK
        ) {
            return true
        }
        val packId =
            cosmetic.characterPackId
                ?: return false
        return LudoPawsCharacterCatalog
            .pack(
                packId,
            ) != null
    }

    private fun syncCharacterPackSelection(
        cosmetic: StoreCosmetic,
    ) {
        if (
            cosmetic.category !=
            CosmeticCategory.CHARACTER_PACK
        ) {
            return
        }
        val packId =
            cosmetic.characterPackId
                ?: return
        val pack =
            LudoPawsCharacterCatalog
                .pack(
                    packId,
                )
                ?: return
        val selectionStore =
            CharacterSelectionStore(
                appContext,
            )
        val current =
            selectionStore.load()
        if (
            current.packId == pack.id &&
            current.characterId in pack.characterIds
        ) {
            return
        }
        val first =
            pack.characterIds
                .firstOrNull()
                ?: return
        selectionStore.select(
            packId = pack.id,
            characterId = first,
        )
    }

    private fun ownedIds(): MutableSet<String> =
        if (!com.rekixo.ludopaws.BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED || !hasCurrentAccountSnapshot()) {
            mutableSetOf()
        } else {
            prefs.getStringSet(
                StorePreferences.KEY_OWNED_COSMETICS,
                emptySet(),
            )?.toMutableSet() ?: mutableSetOf()
        }


    private fun selectedKey(
        category: CosmeticCategory,
    ): String =
        when (category) {
            CosmeticCategory.BOARD ->
                StorePreferences
                    .KEY_SELECTED_BOARD

            CosmeticCategory.DICE ->
                StorePreferences
                    .KEY_SELECTED_DICE

            CosmeticCategory.AVATAR ->
                StorePreferences
                    .KEY_SELECTED_AVATAR

            CosmeticCategory.CHARACTER_PACK ->
                StorePreferences
                    .KEY_SELECTED_CHARACTER_PACK
        }
}
