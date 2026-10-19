package com.rekixo.ludopaws

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureStateRecoveryInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecureSessionStore(context).clear()
    }

    @After
    fun tearDown() {
        SecureSessionStore(context).clear()
    }

    @Test
    fun playerSessionSurvivesStoreRecreationThroughAndroidKeystore() {
        val expected =
            PlayerSession(
                matchId = MATCH_ID,
                playerId = "player-restore-1",
                playerToken = PLAYER_TOKEN,
                modeWire = GameMode.TEAM_UP.wireValue,
            )

        SecureSessionStore(context).save(expected)

        val restored = SecureSessionStore(context).load()

        assertEquals(expected, restored)
    }

    @Test
    fun retryIdentitySurvivesStoreRecreation() {
        val operationKey = "$MATCH_ID:42"
        val first = PendingOperationStore(context).getOrCreate("roll", operationKey)
        assertEquals(first, PendingOperationStore(context).getOrCreate("roll", operationKey))
        PendingOperationStore(context).clear("roll", operationKey)
        assertFalse(first == PendingOperationStore(context).getOrCreate("roll", operationKey))
    }

    @Test
    fun tamperedPlayerSessionCiphertextFailsClosedAndIsCleared() {
        SecureSessionStore(context).save(
            PlayerSession(
                matchId = MATCH_ID,
                playerId = "player-tamper-1",
                playerToken = PLAYER_TOKEN,
                modeWire = GameMode.ONLINE.wireValue,
            ),
        )
        val prefs =
            context.getSharedPreferences(
                "ludoproof_secure_session",
                Context.MODE_PRIVATE,
            )
        val ciphertext =
            requireNotNull(
                prefs.getString(
                    "playerTokenCiphertext",
                    null,
                ),
            )
        prefs.edit()
            .putString(
                "playerTokenCiphertext",
                mutateBase64(ciphertext),
            )
            .commit()

        assertNull(SecureSessionStore(context).load())
        assertFalse(prefs.contains("playerTokenCiphertext"))
    }

    private fun mutateBase64(value: String): String {
        val replacement = if (value.last() == 'A') 'B' else 'A'
        return value.dropLast(1) + replacement
    }

    private companion object {
        const val MATCH_ID = "LPABCDEFGH"
        const val PLAYER_TOKEN =
            "lp_abcdefghijklmnopqrstuvwxyzABCDEF"
    }
}
