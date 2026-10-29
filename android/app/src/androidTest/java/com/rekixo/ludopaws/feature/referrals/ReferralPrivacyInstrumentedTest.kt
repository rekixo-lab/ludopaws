package com.rekixo.ludopaws.feature.referrals

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.feature.account.data.AccountSession
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReferralPrivacyInstrumentedTest {
    private lateinit var context: Context
    private lateinit var sessions: AccountSessionStore
    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var accountId: String

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        check(BuildConfig.LUDOPROOF_API_BASE_URL == "https://127.0.0.1" && context.packageName == "com.rekixo.ludopaws")
        check(BuildConfig.LUDOPROOF_API_BASE_URL == "https://127.0.0.1")
        preferences = context.getSharedPreferences("friendship_attribution", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        context.getSharedPreferences("family_privacy_v1", Context.MODE_PRIVATE).edit().putInt("entry_policy_version", 1).putInt("age_band", 3).commit()
        accountId = UUID.randomUUID().toString()
        sessions = AccountSessionStore(context)
        sessions.save(AccountSession(accountId, UUID.randomUUID().toString(), "qa.header.signature", "lpr_" + "A".repeat(43), "b".repeat(64),
            System.currentTimeMillis() / 1000 + 3600, System.currentTimeMillis() / 1000 + 86400, "QA referral player"))
    }

    @After fun tearDown() {
        if (::preferences.isInitialized) preferences.edit().clear().commit()
        if (::sessions.isInitialized) sessions.clear()
        if (::context.isInitialized) context.getSharedPreferences("family_privacy_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun noConsentDoesNotReadThePlayReferrerOrStartVerification() {
        val completed = CountDownLatch(1)
        ReferralRepository(context).refreshAttribution(force = true) { completed.countDown() }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertFalse(preferences.contains("referrer_read"))
        assertFalse(preferences.contains("verification_state"))
        assertFalse(preferences.contains("click"))
    }

    @Test fun stoppingWhileOfflineImmediatelyClearsPendingLocalAttribution() {
        preferences.edit().putString("consented_account", accountId).putString("consent_terms", ReferralRepository.TERMS_VERSION)
            .putString("consent_revision", "old-revision").putString("click", "c".repeat(64))
            .putBoolean("bound", true).putBoolean("referrer_read", true).putLong("install_at", 123L).commit()
        assertTrue(runCatching { ReferralRepository(context).withdraw(accountId) }.isFailure)
        assertEquals("STOPPED", preferences.getString("verification_state", null))
        assertFalse(preferences.contains("consented_account"))
        assertFalse(preferences.contains("click"))
        assertFalse(preferences.contains("install_at"))
        assertNotEquals("old-revision", preferences.getString("consent_revision", null))
        assertTrue(preferences.getBoolean("bound", false))
    }

    @Test fun restrictedPrivacyHidesTheBundleBeforeAnyNetworkResponse() {
        context.getSharedPreferences("family_privacy_v1", Context.MODE_PRIVATE).edit().putInt("entry_policy_version", 1).putInt("age_band", 0).commit()
        ActivityScenario.launch(ReferralActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val views = descendants(activity.window.decorView)
                assertEquals(View.GONE, views.filterIsInstance<FriendshipBundleView>().single().visibility)
                // Consent now opens on demand; the compact page must not
                // silently create or preselect a consent checkbox.
                assertTrue(views.filterIsInstance<CheckBox>().isEmpty())
                val ordinaryShare = views.filterIsInstance<Button>().single { it.text.toString().startsWith("Share without rewards") }
                assertTrue(ordinaryShare.isEnabled)
                views.filterIsInstance<Button>().single { it.text.toString() == "Got an invite?" }.performClick()
                val dialogField = ReferralActivity::class.java.getDeclaredField("invitationDialog").apply { isAccessible = true }
                val dialog = dialogField.get(activity) as android.app.Dialog
                val verify = descendants(requireNotNull(dialog.window).decorView).filterIsInstance<Button>()
                    .single { it.text.toString() == "Verify my invitation" }
                assertFalse(verify.isEnabled)
                assertFalse(preferences.contains("consented_account"))
                dialog.dismiss()
            }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()
}
