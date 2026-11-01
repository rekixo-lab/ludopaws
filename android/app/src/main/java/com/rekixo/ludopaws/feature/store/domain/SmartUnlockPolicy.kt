package com.rekixo.ludopaws.feature.store.domain

import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic

/** A decision only: never debits Gems, starts playback or grants ownership. */
enum class SmartUnlockAction {
    SELECT, FREE, GEMS, PLAY_PRODUCT, WATCH_AD, CLAIM_ADS, GET_GEMS, LEVEL, EVENT, UNAVAILABLE,
}

data class SmartUnlockDecision(
    val primary: SmartUnlockAction,
    val alternative: SmartUnlockAction? = null,
)

object SmartUnlockPolicy {
    fun decide(
        cosmetic: StoreCosmetic,
        owned: Boolean,
        gems: Int,
        adProgress: Int,
        playAvailable: Boolean,
        adsAvailable: Boolean,
    ): SmartUnlockDecision {
        if (owned) return SmartUnlockDecision(SmartUnlockAction.SELECT)
        if (!cosmetic.contentAvailable || !cosmetic.purchaseAvailable) {
            return SmartUnlockDecision(SmartUnlockAction.UNAVAILABLE)
        }
        if (cosmetic.requiredLevel > 0) return SmartUnlockDecision(SmartUnlockAction.LEVEL)
        val channels = cosmetic.unlockChannels.ifEmpty { listOf(cosmetic.unlockKind) }
        val gemsPossible = CosmeticUnlockKind.GEMS in channels && cosmetic.gemPrice > 0
        val canSpendGems = gemsPossible && gems >= cosmetic.gemPrice
        val adsPossible = CosmeticUnlockKind.REWARDED_ADS in channels && cosmetic.requiredAdViews > 0
        val playPossible = playAvailable && CosmeticUnlockKind.PLAY_PRODUCT in channels
        val adAction = when {
            !adsPossible -> null
            adProgress >= cosmetic.requiredAdViews -> SmartUnlockAction.CLAIM_ADS
            adsAvailable -> SmartUnlockAction.WATCH_AD
            else -> null
        }
        if (canSpendGems) return SmartUnlockDecision(
            SmartUnlockAction.GEMS,
            if (playPossible) SmartUnlockAction.PLAY_PRODUCT else adAction,
        )
        if (adAction == SmartUnlockAction.CLAIM_ADS) return SmartUnlockDecision(
            SmartUnlockAction.CLAIM_ADS,
            if (playPossible) SmartUnlockAction.PLAY_PRODUCT else null,
        )
        if (playPossible) return SmartUnlockDecision(SmartUnlockAction.PLAY_PRODUCT, adAction)
        if (adAction != null) return SmartUnlockDecision(
            adAction,
            if (gemsPossible) SmartUnlockAction.GET_GEMS else null,
        )
        if (gemsPossible) return SmartUnlockDecision(SmartUnlockAction.GET_GEMS)
        if (CosmeticUnlockKind.FREE in channels) return SmartUnlockDecision(SmartUnlockAction.FREE)
        if (CosmeticUnlockKind.LEVEL in channels) return SmartUnlockDecision(SmartUnlockAction.LEVEL)
        if (CosmeticUnlockKind.EVENT in channels) return SmartUnlockDecision(SmartUnlockAction.EVENT)
        return SmartUnlockDecision(SmartUnlockAction.UNAVAILABLE)
    }
}
