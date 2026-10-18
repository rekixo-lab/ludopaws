package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModeMatchSpecTest {
    @Test
    fun legacyLocalModeMapsToPassAndPlay() {
        assertEquals(
            GameMode.PASS_AND_PLAY,
            GameMode.fromWireValue(
                "LOCAL",
            ),
        )
        assertEquals(
            GameMode.PASS_AND_PLAY,
            GameMode.fromWireValue(
                "pass_and_play",
            ),
        )
    }

    @Test
    fun remoteAndLocalAuthoritiesAreExplicit() {
        assertTrue(
            GameMode.ONLINE.isRemote,
        )
        assertTrue(
            GameMode.TEAM_UP.isRemote,
        )
        assertTrue(
            GameMode.FRIENDS.isRemote,
        )
        assertTrue(
            GameMode.COMPUTER.isLocal,
        )
        assertTrue(
            GameMode.PASS_AND_PLAY.isLocal,
        )
    }

    @Test
    fun publicOnlineUsesTwoOrFourPlayers() {
        assertTrue(
            GameMode.ONLINE
                .supportsPlayerCount(
                    2,
                ),
        )
        assertFalse(
            GameMode.ONLINE
                .supportsPlayerCount(
                    3,
                ),
        )
        assertTrue(
            GameMode.ONLINE
                .supportsPlayerCount(
                    4,
                ),
        )
    }

    @Test
    fun teamUpIsFourPlayerUnrankedTeamMode() {
        assertFalse(
            GameMode.TEAM_UP
                .supportsPlayerCount(
                    2,
                ),
        )
        assertTrue(
            GameMode.TEAM_UP
                .supportsPlayerCount(
                    4,
                ),
        )
        assertTrue(
            GameMode.TEAM_UP.teamBased,
        )
        assertFalse(
            GameMode.TEAM_UP.ranked,
        )
        assertEquals(
            MatchAuthority.REMOTE,
            GameMode.TEAM_UP.authority,
        )
    }

    @Test
    fun classicSpecKeepsDeployedRulesetId() {
        val spec =
            MatchSpec.classic(
                mode =
                    GameMode.COMPUTER,
                playerCount =
                    4,
            )

        assertEquals(
            ClassicRuleset.ID,
            spec.rulesetId,
        )
        assertEquals(
            NativeDiceEngine
                .RULESET_ID,
            spec.rulesetId,
        )
        assertEquals(
            MatchAuthority.LOCAL,
            spec.authority,
        )
        assertFalse(
            spec.ranked,
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class,
    )
    fun invalidModePlayerCountIsRejected() {
        MatchSpec.classic(
            mode =
                GameMode.TEAM_UP,
            playerCount =
                2,
        )
    }
}
