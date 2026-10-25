package com.rekixo.ludopaws

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rekixo.ludopaws.feature.account.data.AccountSession
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore
import com.rekixo.ludopaws.feature.store.data.billing.PendingStorePurchase
import com.rekixo.ludopaws.feature.store.data.billing.PendingStorePurchaseStore
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.data.local.GemWalletStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountWalletSecurityInstrumentedTest {
    private lateinit var context: Context
    private lateinit var walletPreferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ludoproof_account_session_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        walletPreferences = context.getSharedPreferences("ludoproof_wallet", Context.MODE_PRIVATE)
        walletPreferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("ludoproof_account_session_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        walletPreferences.edit().clear().commit()
    }

    @Test
    fun pendingPurchaseTokensAreEncryptedAndSurviveStoreRecreation() {
        val purchase = pendingPurchase()
        PendingStorePurchaseStore(context).add(purchase)

        val stored = requireNotNull(walletPreferences.getString("pending_play_purchases", null))
        assertTrue(stored.startsWith("v1."))
        assertFalse(stored.contains(purchase.purchaseToken))
        assertEquals(listOf(purchase), PendingStorePurchaseStore(context).all())
    }

    @Test
    fun legacyPendingPurchaseRecordsAreMigratedWithoutLosingTheToken() {
        val purchase = pendingPurchase()
        val legacy = org.json.JSONArray().put(
            JSONObject()
                .put("productId", purchase.productId)
                .put("purchaseToken", purchase.purchaseToken)
                .put("accountId", purchase.accountId),
        )
        walletPreferences.edit().putString("pending_play_purchases", legacy.toString()).commit()

        assertEquals(listOf(purchase), PendingStorePurchaseStore(context).all())
        val encrypted = requireNotNull(walletPreferences.getString("pending_play_purchases", null))
        assertTrue(encrypted.startsWith("v1."))
        assertFalse(encrypted.contains(purchase.purchaseToken))
        assertEquals(listOf(purchase), PendingStorePurchaseStore(context).all())
    }

    @Test
    fun corruptedPendingPurchaseCiphertextNeverReturnsARewardableRecord() {
        PendingStorePurchaseStore(context).add(pendingPurchase())
        val stored = requireNotNull(walletPreferences.getString("pending_play_purchases", null))
        val replacement = if (stored.last() == 'A') 'B' else 'A'
        walletPreferences.edit()
            .putString("pending_play_purchases", stored.dropLast(1) + replacement)
            .commit()

        try {
            PendingStorePurchaseStore(context).all()
            fail("Corrupted purchase-token ciphertext must not be accepted.")
        } catch (_: Exception) {
            // Expected: authentication failed. Keep the record quarantined rather than granting.
        }
        assertTrue(walletPreferences.contains("pending_play_purchases"))
    }

    @Test
    fun convertingGuestToGoogleModeClearsTheGuestRecoveryCredential() {
        val sessionStore = AccountSessionStore(context)
        val sessionPreferences = context.getSharedPreferences(
            "ludoproof_account_session_v1",
            Context.MODE_PRIVATE,
        )
        saveTestSession()
        val guestRecoveryId = sessionStore.guestRequestId()
        val storedGuestCiphertext = requireNotNull(
            sessionPreferences.getString("guest_request_ciphertext", null),
        )
        assertFalse(storedGuestCiphertext.contains(guestRecoveryId))
        assertEquals("GUEST", sessionStore.lastAccountMode())
        assertTrue(requireNotNull(sessionStore.load()).guestRecoveryEligible)

        val deleteOnlyCode = "ab".repeat(32)
        val guestSession = requireNotNull(sessionStore.load())
        sessionStore.save(guestSession.copy(guestDeletionCredential = deleteOnlyCode))
        assertEquals(deleteOnlyCode, sessionStore.load()?.guestDeletionCredential)
        val encryptedSession = requireNotNull(sessionPreferences.getString("session_ciphertext", null))
        assertFalse(encryptedSession.contains(deleteOnlyCode))

        val session = requireNotNull(sessionStore.load())
        sessionStore.save(session.copy(guestRecoveryEligible = false, guestDeletionCredential = null))

        assertEquals("GOOGLE", sessionStore.lastAccountMode())
        assertFalse(sessionPreferences.contains("guest_request_ciphertext"))
        assertFalse(sessionPreferences.contains("guest_request_iv"))
        assertEquals(null, sessionStore.load()?.guestDeletionCredential)
    }

    @Test
    fun firstServerWalletSnapshotArchivesLegacyLocalDataButNeverGrantsIt() {
        saveTestSession()
        walletPreferences.edit()
            .putInt("gem_balance", 500)
            .putBoolean("ads_removed", true)
            .putStringSet("owned_cosmetics", setOf("dice_heart"))
            .putString("selected_dice", "dice_heart")
            .putInt("ad_progress_dice_summers", 17)
            .commit()

        val expectedAccountId = ACCOUNT_ID
        val serverWallet = JSONObject()
            .put("accountId", expectedAccountId)
            .put("balance", 0)
            .put("adsRemoved", false)
            .put("ownedCosmetics", org.json.JSONArray())
            .put("selectedCosmetics", JSONObject())
            .put("rewardedAdProgress", JSONObject())

        val store = GemWalletStore(context)
        assertTrue(store.applyServerSnapshot(serverWallet, expectedAccountId))

        val archive = JSONObject(
            requireNotNull(walletPreferences.getString("legacy_wallet_unverified_archive_v1", null)),
        )
        assertFalse(archive.getBoolean("verified"))
        assertEquals(500, archive.getInt("balance"))
        assertTrue(archive.getBoolean("adsRemoved"))
        assertTrue(archive.getJSONArray("ownedCosmetics").toString().contains("dice_heart"))
        assertEquals(0, store.balance())
        assertFalse(store.adsRemoved())
        assertFalse(CosmeticInventoryStore(context).isOwned("dice_heart"))
        assertTrue(store.hasLegacyUnverifiedSnapshot())
    }

    @Test
    fun olderRepliesFromAnotherStoreInstanceCannotRegressWalletOrSelection() {
        saveTestSession()
        val newer = JSONObject().put("accountId", ACCOUNT_ID).put("revision", 12)
            .put("balance", 50).put("selectedCosmetics", JSONObject().put("DICE", "dice_pumpkin"))
        assertTrue(GemWalletStore(context).applyServerSnapshot(newer, ACCOUNT_ID))
        val delayed = JSONObject().put("accountId", ACCOUNT_ID).put("revision", 11).put("balance", 5)
        assertFalse(GemWalletStore(context).applyServerSnapshot(delayed, ACCOUNT_ID))
        assertEquals(50, GemWalletStore(context).balance())
        assertEquals("dice_pumpkin", walletPreferences.getString("selected_dice", null))
    }

    @Test
    fun lateLeaderboardReplyCannotBeCachedForTheNextAccount() {
        saveTestSession()
        val cache = com.rekixo.ludopaws.feature.leaderboard.data.local.LeaderboardCacheStore(context)
        cache.clear()
        cache.save(JSONObject().put("viewer", "first account"), ACCOUNT_ID)
        assertEquals("first account", cache.load()?.payload?.getString("viewer"))
        val sessions = AccountSessionStore(context)
        sessions.save(requireNotNull(sessions.load()).copy(accountId = UUID.randomUUID().toString()))
        cache.save(JSONObject().put("viewer", "late first account"), ACCOUNT_ID)
        assertEquals(null, cache.load())
        cache.clear()
    }

    private fun pendingPurchase() = PendingStorePurchase(
        productId = "gems_300",
        purchaseToken = "secure-purchase-token-1234567890",
        accountId = "a".repeat(64),
    )

    @Test
    fun staleSessionUsesAlreadyRotatedCredentialsWithoutRefreshingAgain() {
        saveTestSession()
        val sessions = AccountSessionStore(context)
        val old = requireNotNull(sessions.load())
        val rotated = old.copy(accessToken = "rotated.access.token", refreshToken = "lpr_" + "B".repeat(43))
        sessions.save(rotated)

        assertEquals(rotated, com.rekixo.ludopaws.feature.account.data.AccountRepository(context).validSession(old))
        assertEquals(rotated, sessions.load())
    }

    @Test
    fun staleSessionCannotReplaceTheNextAccountOrReturnAfterLogout() {
        saveTestSession()
        val sessions = AccountSessionStore(context)
        val old = requireNotNull(sessions.load())
        val next = old.copy(accountId = UUID.randomUUID().toString(), sessionId = UUID.randomUUID().toString())
        sessions.save(next)
        val repository = com.rekixo.ludopaws.feature.account.data.AccountRepository(context)
        try {
            repository.validSession(old)
            fail("An old account session must not authorize the next account.")
        } catch (error: com.rekixo.ludopaws.feature.account.data.AccountApiException) {
            assertEquals("ACCOUNT_SESSION_CHANGED", error.code)
        }
        assertEquals(next, sessions.load())

        repository.clearLocalSession()
        try {
            repository.validSession(old)
            fail("A logged-out session must not be returned or refreshed.")
        } catch (error: com.rekixo.ludopaws.feature.account.data.AccountApiException) {
            assertEquals("ACCOUNT_SESSION_REQUIRED", error.code)
        }
        assertEquals(null, sessions.load())
    }

    private fun saveTestSession() {
        AccountSessionStore(context).save(
            AccountSession(
                accountId = ACCOUNT_ID,
                sessionId = UUID.fromString("6382a0ee-f216-46f8-b10d-51411e1f38f2").toString(),
                accessToken = "a.b.c",
                refreshToken = "lpr_" + "A".repeat(43),
                billingAccountId = "a".repeat(64),
                accessExpiresAt = System.currentTimeMillis() / 1000 + 600,
                sessionExpiresAt = System.currentTimeMillis() + 86_400_000,
                displayName = "Test",
                guestRecoveryEligible = true,
            ),
        )
    }

    private companion object {
        const val ACCOUNT_ID = "0a2c3a83-e0fb-4abc-8ac4-581a6d994fe7"
    }
}
