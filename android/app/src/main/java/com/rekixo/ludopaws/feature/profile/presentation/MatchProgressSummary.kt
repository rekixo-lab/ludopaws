package com.rekixo.ludopaws.feature.profile.presentation

import android.content.Context
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression

/** Display only persisted completion progress; never grants currency or XP. */
object MatchProgressSummary {
    fun text(context: Context, matchId: String, won: Boolean, rankedXp: Boolean = true): String {
        val store = ProfileStore(context)
        val profile = store.snapshot()
        val level = ProfileProgression.levelProgress(profile.totalXp)
        return if (rankedXp && store.hasRecordedMatch(matchId)) {
            "+${ProfileProgression.xpAward(won)} XP · Level ${level.level} · ${level.xpIntoLevel}/${level.xpForNextLevel} XP"
        } else if (!rankedXp) {
            "Paws collection · ${profile.characterMatches.count { it.value > 0 }}/${com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions.all.size} pals played"
        } else "Your match progress is being saved"
    }
}
