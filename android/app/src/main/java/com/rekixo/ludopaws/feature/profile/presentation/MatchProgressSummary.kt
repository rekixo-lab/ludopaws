package com.rekixo.ludopaws.feature.profile.presentation

import android.content.Context
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression

/** Display only persisted completion progress; never grants currency or XP. */
data class MatchXpCard(val title: String, val detail: String, val fraction: Float?)

object MatchProgressSummary {
    /** Persisted match XP only. A pending write must not be described as awarded. */
    fun card(context: Context, matchId: String, won: Boolean, eligible: Boolean): MatchXpCard? {
        if (!eligible) return null
        val store = ProfileStore(context)
        if (!store.hasRecordedMatch(matchId)) {
            return MatchXpCard("Saving match XP…", "XP will appear after your result is saved", null)
        }
        val level = ProfileProgression.levelProgress(store.snapshot().totalXp)
        return MatchXpCard(
            "+${ProfileProgression.xpAward(won)} XP",
            "Level ${level.level} · ${level.xpIntoLevel}/${level.xpForNextLevel} XP",
            level.fraction,
        )
    }

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
