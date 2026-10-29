package com.rekixo.ludopaws.feature.store.presentation.actions

import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticAcquireResult
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity

internal fun StoreActivity.handleCosmeticTap(cosmetic: StoreCosmetic) {
    // CONTENT_UNAVAILABLE is handled before any server request or value-bearing action.
    if (!cosmetic.contentAvailable) {
        showStoreMessage(
            if (cosmetic.remoteManaged) {
                cosmetic.title +
                    " is listed in the server catalog, but this app build does not yet contain a verified renderer/asset pack for it. No Gems, ad progress, or entitlement was spent."
            } else {
                cosmetic.title +
                    " is reserved for a future complete content drop. No Gems, ad progress, or event entitlement was spent."
            },
        )
        return
    }

    if (cosmetic.unlockChannels.size > 1) {
        purchaseOrSelectCosmetic(cosmetic)
        return
    }

    val level = ProfileProgression
        .levelProgress(ProfileStore(this).snapshot().totalXp)
        .level
    if (!cosmetics.isOwned(cosmetic.id) && level < cosmetic.requiredLevel) {
        showStoreMessage("Reach level ${cosmetic.requiredLevel} to unlock ${cosmetic.title}.")
        return
    }

    if (cosmetics.isOwned(cosmetic.id)) {
        purchaseOrSelectCosmetic(cosmetic)
        return
    }

    when (cosmetic.unlockKind) {
        CosmeticUnlockKind.REWARDED_ADS -> {
            if (cosmetics.rewardedAdProgress(cosmetic.id) < cosmetic.requiredAdViews) {
                requestRewardedAd(cosmetic.id)
            } else {
                purchaseOrSelectCosmetic(cosmetic)
            }
        }
        CosmeticUnlockKind.EVENT -> {
            when (cosmetics.acquireOrSelect(cosmetic, level)) {
                CosmeticAcquireResult.NEED_EVENT -> showStoreMessage(
                    cosmetic.title +
                        " is an event item. It can only be unlocked after the server confirms the event entitlement.",
                )
                else -> showStoreMessage(
                    cosmetic.title +
                        " cannot be unlocked until its event entitlement is confirmed by the server.",
                )
            }
        }
        CosmeticUnlockKind.PLAY_PRODUCT -> purchaseOrSelectCosmetic(cosmetic)
        CosmeticUnlockKind.FREE,
        CosmeticUnlockKind.LEVEL,
        CosmeticUnlockKind.GEMS -> purchaseOrSelectCosmetic(cosmetic)
    }
}
