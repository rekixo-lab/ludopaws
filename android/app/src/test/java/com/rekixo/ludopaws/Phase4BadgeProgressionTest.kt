package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4BadgeProgressionTest {
    @Test fun freshPlayersSeeNineLockedAndEarnableBadges() {
        val badges = ProfileProgression.badges(ProfileSnapshot())
        assertEquals(9, badges.size)
        assertEquals(9, badges.map { it.id }.distinct().size)
        assertTrue(badges.none { it.unlocked })
        assertTrue(badges.all { it.progressGoal > 0 && it.progressCurrent == 0 })
    }

    @Test fun offlineProgressDoesNotRequireOnlineOrPayments() {
        val badges = ProfileProgression.badges(
            ProfileSnapshot(totalGames = 3, totalWins = 1, localGames = 3, bestDayStreak = 2),
        ).associateBy { it.id }
        assertTrue(badges.getValue("first-win").unlocked)
        assertTrue(badges.getValue("ludo-novice").unlocked)
        assertTrue(badges.getValue("couch-champion").unlocked)
        assertFalse(badges.getValue("friendship").unlocked)
        assertEquals(2, badges.getValue("returning-paws").progressCurrent)
        assertEquals(3, badges.getValue("returning-paws").progressGoal)
    }

    @Test fun badgeProgressIsBoundedAndWinsAreNotRewardedRepeatedly() {
        val badges = ProfileProgression.badges(ProfileSnapshot(
            totalGames = 999, totalWins = 999, onlineGames = 999,
            localGames = 999, bestWinStreak = 999, bestDayStreak = 999,
        ))
        assertTrue(badges.all { it.unlocked && it.progressCurrent == it.progressGoal })
        assertEquals(20, ProfileProgression.xpAward(false))
        assertEquals(50, ProfileProgression.xpAward(true))
    }

    @Test fun invalidNegativeImportedStatsCannotUnlock() {
        val badges = ProfileProgression.badges(ProfileSnapshot(
            totalGames = -100, totalWins = -1, onlineGames = -3,
            localGames = -1, bestDayStreak = -2,
        ))
        assertTrue(badges.none { it.unlocked })
        assertTrue(badges.all { it.progressCurrent == 0 })
    }
}
