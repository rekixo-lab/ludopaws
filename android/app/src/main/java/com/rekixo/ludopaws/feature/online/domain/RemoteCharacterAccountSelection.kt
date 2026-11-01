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
        fun current(context: Context): RemoteCharacterAccountSelection {
            val characterId = CharacterSelectionStore(context).load().characterId
            val accountToken = if (StoreCosmeticCatalog.characterEntitlementId(characterId) != null) {
                (context.applicationContext as LudoProofApplication)
                    .accountRepository.requireSession().accessToken
            } else null
            return RemoteCharacterAccountSelection(characterId, accountToken)
        }
    }
}
