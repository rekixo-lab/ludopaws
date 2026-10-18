package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCryptoTest {
    @Test
    fun preparedRollUsesFresh32ByteSeedAndMatchingCommitment() {
        val first =
            SeedCommitment.prepare()
        val second =
            SeedCommitment.prepare()

        assertTrue(
            HEX.matches(
                first.clientSeed,
            ),
        )
        assertTrue(
            HEX.matches(
                first.clientCommitment,
            ),
        )
        assertEquals(
            SeedCommitment.sha256Hex(
                "ludopaws:native-roll:v1:client-commit:" +
                    first.clientSeed,
            ),
            first.clientCommitment,
        )
        assertNotEquals(
            first.clientSeed,
            second.clientSeed,
        )
        assertNotEquals(
            first.clientCommitment,
            second.clientCommitment,
        )
    }

    @Test
    fun localSecretAadIsDomainSeparatedAndMetadataBound() {
        val matchA =
            "LPABCDEFGH"
        val matchB =
            "LPABCDEFGJ"
        val commitmentA =
            "a".repeat(64)
        val commitmentB =
            "b".repeat(64)

        val rollA =
            LocalSecretBinding.pendingRollAad(
                matchA,
                commitmentA,
            )
                .toString(
                    Charsets.UTF_8,
                )
        val rollB =
            LocalSecretBinding.pendingRollAad(
                matchA,
                commitmentB,
            )
                .toString(
                    Charsets.UTF_8,
                )
        val session =
            LocalSecretBinding.playerSessionAad(
                matchA,
                "player-1",
            )
                .toString(
                    Charsets.UTF_8,
                )

        assertNotEquals(
            rollA,
            rollB,
        )
        assertNotEquals(
            rollA,
            session,
        )
        assertTrue(
            rollA.contains(
                matchA,
            ),
        )
        assertFalse(
            rollA.contains(
                matchB,
            ),
        )
        assertEquals(
            2,
            LocalSecretBinding.FORMAT_VERSION,
        )
    }

    private companion object {
        val HEX =
            Regex(
                "^[0-9a-f]{64}$",
            )
    }
}
