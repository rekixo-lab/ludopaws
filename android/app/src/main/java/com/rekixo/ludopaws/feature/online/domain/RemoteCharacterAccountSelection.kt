package com.rekixo.ludopaws.feature.online.domain

import android.content.Context
import com.rekixo.ludopaws.LudoProofApplication
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import org.json.JSONObject

data class RemoteCharacterAccountSelection(
    val characterId: String,
    val accountToken: String?,
    val matchStyle: JSONObject,
) {
    companion object {
        /**
         * Capture the equipped Paw and dice for server ownership checks.
         * The board is a local display preference.
         */
        fun current(
            context: Context,
            leaderboardProfileToken: String? = null,
            friendToken: String? = null,
        ): RemoteCharacterAccountSelection {
            val characterId = CharacterSelectionStore(context).load().characterId
            val diceId = CosmeticInventoryStore(context).selectedId(CosmeticCategory.DICE)
            val needsPaw = StoreCosmeticCatalog.characterEntitlementId(characterId) != null
            val dice = StoreCosmeticCatalog.find(diceId)
            val needsDice = dice == null || dice.unlockKind != CosmeticUnlockKind.FREE || dice.requiredLevel > 0
            fun linkedAccountToken(): String {
                val repository = (context.applicationContext as LudoProofApplication).accountRepository
                when {
                    leaderboardProfileToken != null -> repository.ensureLinkedLeaderboardWallet(leaderboardProfileToken)
                    friendToken != null -> repository.ensureLinkedFriendWallet(friendToken)
                    else -> error("A verified game profile is required for premium online cosmetics")
                }
                return repository.requireSession().accessToken
            }
            val accountToken = when {
                needsPaw -> linkedAccountToken()
                needsDice -> runCatching { linkedAccountToken() }.getOrNull()
                else -> null
            }
            return RemoteCharacterAccountSelection(
                characterId,
                accountToken,
                JSONObject().put("diceId", diceId),
            )
        }
    }
}
