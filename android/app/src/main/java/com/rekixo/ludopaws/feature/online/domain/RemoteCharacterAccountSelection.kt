package com.rekixo.ludopaws.feature.online.domain

import android.content.Context
import com.rekixo.ludopaws.LudoProofApplication
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog

data class RemoteCharacterAccountSelection(
    val characterId: String,
    val accountToken: String?,
) {
    companion object {
        /**
         * Resolve the current, account-bound character on the network worker.
         * Free paws need no extra auth. Premium paw requests carry a refreshed
         * game-account session for authoritative server ownership checks.
         */
        fun current(
            context: Context,
            leaderboardProfileToken: String? = null,
            friendToken: String? = null,
        ): RemoteCharacterAccountSelection {
            val characterId = CharacterSelectionStore(context).load().characterId
            val accountToken = if (StoreCosmeticCatalog.characterEntitlementId(characterId) != null) {
                val repository = (context.applicationContext as LudoProofApplication).accountRepository
                when {
                    leaderboardProfileToken != null -> repository.ensureLinkedLeaderboardWallet(leaderboardProfileToken)
                    friendToken != null -> repository.ensureLinkedFriendWallet(friendToken)
                    else -> error("A verified game profile is required for a premium online character")
                }
                repository.requireSession().accessToken
            } else null
            return RemoteCharacterAccountSelection(characterId, accountToken)
        }
    }
}
