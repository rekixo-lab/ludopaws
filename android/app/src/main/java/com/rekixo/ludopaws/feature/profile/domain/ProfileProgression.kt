package com.rekixo.ludopaws.feature.profile.domain

import com.rekixo.ludopaws.feature.profile.domain.model.ProfileBadge
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileLevelProgress
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileSnapshot

object ProfileProgression {
    fun xpAward(
        won: Boolean,
    ): Int =
        20 +
            if (won) {
                30
            } else {
                0
            }

    fun levelProgress(
        totalXp: Int,
    ): ProfileLevelProgress {
        var remaining =
            totalXp.coerceAtLeast(0)
        var level =
            1
        var requirement =
            xpRequirement(level)

        while (
            remaining >=
                requirement &&
            level <
                100
        ) {
            remaining -=
                requirement
            level +=
                1
            requirement =
                xpRequirement(level)
        }

        return ProfileLevelProgress(
            level =
                level,
            xpIntoLevel =
                remaining,
            xpForNextLevel =
                requirement,
            fraction =
                if (requirement > 0) {
                    (
                        remaining
                            .toFloat() /
                            requirement
                                .toFloat()
                        )
                        .coerceIn(
                            0f,
                            1f,
                        )
                } else {
                    1f
                },
        )
    }

    /**
     * Cosmetic-only achievements, derived from already persisted completed-match statistics.
     * No currency awards, streak pressure, daily login gates, server requests or new identifiers.
     */
    fun badges(profile: ProfileSnapshot): List<ProfileBadge> = listOf(
        badge("first-win", "First Win", "★", "Win your first game", profile.totalWins, 1),
        badge("strategist", "Strategist", "◆", "Win 5 games", profile.totalWins, 5),
        badge("unstoppable", "Unstoppable", "✦", "Win 3 games in a row", profile.bestWinStreak, 3),
        badge("friendship", "Friendship", "♢", "Complete 3 online games", profile.onlineGames, 3),
        badge("social-gamer", "Social Gamer", "♛", "Complete 10 online games", profile.onlineGames, 10),
        badge("ludo-novice", "Ludo Novice", "✪", "Complete a Ludo game", profile.totalGames, 1),
        badge("couch-champion", "Couch Champion", "♟", "Complete 3 local Pass & Play games", profile.localGames, 3),
        badge("returning-paws", "Returning Paws", "❖", "Play on 3 consecutive days", profile.bestDayStreak, 3),
        badge("game-night", "Game Night", "✧", "Complete 20 games", profile.totalGames, 20),
    )

    private fun badge(
        id: String,
        title: String,
        symbol: String,
        requirement: String,
        current: Int,
        goal: Int,
    ): ProfileBadge {
        val progress = current.coerceAtLeast(0).coerceAtMost(goal)
        return ProfileBadge(
            id = id, title = title, symbol = symbol,
            unlocked = progress >= goal, requirement = requirement,
            progressCurrent = progress, progressGoal = goal,
        )
    }

    private fun xpRequirement(
        level: Int,
    ): Int =
        100 +
            (
                level -
                    1
                ) *
            25
}
