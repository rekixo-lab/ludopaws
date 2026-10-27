package com.rekixo.ludopaws.feature.referrals

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.LudoProofApplication
import com.rekixo.ludopaws.feature.account.data.AccountApiException
import com.rekixo.ludopaws.feature.safety.FamilyAgeScreen
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class ReferralRepository(context: Context) {
    private val app = context.applicationContext
    private val accounts = (app as LudoProofApplication).accountRepository
    private val prefs = app.getSharedPreferences("friendship_attribution", Context.MODE_PRIVATE)

    fun status(createAccount: Boolean = false): JSONObject {
        if (!BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED) return JSONObject().put("enabled", false)
        val account = (if (createAccount) accounts.ensureGuestSession() else accounts.currentSession())
            ?: return JSONObject().put("enabled", false)
        val status = accounts.referralRequest("status", expectedAccountId = account.accountId)
        if (status.optString("state") == "REWARDED") status.put("walletSynced", runCatching {
            CosmeticInventoryStore(app).applyServerSnapshot(accounts.wallet(), account.accountId)
        }.getOrDefault(false))
        status.put("verificationState", if (prefs.getString("verification_account", null) == account.accountId) prefs.getString("verification_state", "NOT_STARTED") else "NOT_STARTED")
        status.put("localConsent", hasConsent(account.accountId))
        return status.put("localAccountId", account.accountId)
    }

    private fun hasConsent(accountId: String) = prefs.getString("consented_account", null) == accountId &&
        prefs.getString("consent_terms", null) == TERMS_VERSION

    fun enroll(accountId: String, termsVersion: String) {
        check(FamilyAgeScreen.optionalGoogleAllowed(app)) { "Review Age and privacy in Settings before using optional invitation verification." }
        check(termsVersion == TERMS_VERSION) { "Update Ludo Paws to review the current invitation terms." }
        accounts.referralRequest("enroll", JSONObject().put("termsVersion", TERMS_VERSION).put("consent", true), accountId)
        check(accounts.currentSession()?.accountId == accountId) { "Your account changed. Please retry." }
        prefs.edit().putString("consented_account", accountId).putString("consent_terms", TERMS_VERSION)
            .putString("consent_revision", java.util.UUID.randomUUID().toString()).putInt("attempts", 0).putLong("next_attempt_at", 0L).apply()
    }

    fun withdraw(accountId: String) {
        check(accounts.currentSession()?.accountId == accountId) { "Your account changed. Please retry." }
        // Respect an explicit stop for the rest of this campaign; manual Share remains available.
        app.getSharedPreferences("referral_popup_v1", Context.MODE_PRIVATE).edit()
            .putInt("${ReferralPopupPolicy.CAMPAIGN}:$accountId:count", 3).apply()
        prefs.edit().remove("consented_account").remove("consent_terms")
            .putString("consent_revision", java.util.UUID.randomUUID().toString())
            .remove("click").remove("install_at").remove("clicked_at").remove("referrer_read")
            .putString("verification_account", accountId).putString("verification_state", "STOPPED").apply()
        accounts.referralRequest("withdraw", JSONObject(), accountId)
    }

    fun invite(expectedAccountId: String): String {
        val value = accounts.referralRequest("invite", JSONObject(), expectedAccountId).getString("shareUrl")
        val uri = Uri.parse(value)
        require(uri.scheme == "https" && uri.host == "api.ludopaws.rekixo.com" &&
            uri.path.orEmpty().matches(Regex("/r/[a-f0-9]{64}")) && uri.query == null && uri.fragment == null)
        check(accounts.currentSession()?.accountId == expectedAccountId) { "Your account changed. Please retry." }
        return value
    }

    fun refreshAttribution(force: Boolean = false, onComplete: (() -> Unit)? = null) {
        if (!FamilyAgeScreen.optionalGoogleAllowed(app)) { onComplete?.let { main.post(it) }; return }
        val current = accounts.currentSession()?.accountId
        if (current == null || !hasConsent(current)) { onComplete?.let { main.post(it) }; return }
        val delay = prefs.getLong("next_attempt_at", 0L) - System.currentTimeMillis()
        if (!force && delay in 1L..3_600_000L) { onComplete?.let { main.post(it) }; return }
        if (!BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED || !attributing.compareAndSet(false, true)) return
        if (prefs.getBoolean("bound", false)) {
            prefs.edit().putString("verification_account", current).putString("verification_state", "INSTALL_ALREADY_USED").apply()
            attributing.set(false); onComplete?.let { main.post(it) }; return
        }
        completion = onComplete
        activeAccount = current
        activeRevision = prefs.getString("consent_revision", null)
        state("VERIFYING")
        executor.execute {
            try {
                val snapshot = status()
                if (snapshot.optBoolean("enabled") && snapshot.optBoolean("googleLinked") && snapshot.optBoolean("consented") && canContinue()) {
                    main.post {
                        if (canContinue()) {
                            runCatching { startAttribution() }.onFailure { retryLater("STORE_UNAVAILABLE"); complete() }
                        } else complete()
                    }
                } else { state("UNAVAILABLE"); complete() }
            } catch (_: Exception) { retryLater("RETRY_LATER"); complete() }
        }
    }

    private var activeAccount: String? = null
    private var activeRevision: String? = null
    private var completion: (() -> Unit)? = null
    private fun canContinue(): Boolean = activeAccount != null && accounts.currentSession()?.accountId == activeAccount &&
        hasConsent(activeAccount.orEmpty()) && prefs.getString("consent_revision", null) == activeRevision && FamilyAgeScreen.optionalGoogleAllowed(app)
    private fun state(value: String) {
        if (!canContinue()) return
        prefs.edit().putString("verification_account", activeAccount).putString("verification_state", value).apply()
    }
    private fun retryLater(value: String) {
        if (!canContinue()) return
        val attempts = (prefs.getInt("attempts", 0) + 1).coerceAtMost(8)
        prefs.edit().putInt("attempts", attempts)
            .putLong("next_attempt_at", System.currentTimeMillis() + minOf(900_000L, 15_000L * (1L shl attempts))).apply()
        state(value)
    }
    private fun complete() {
        val callback = completion
        completion = null
        attributing.set(false)
        callback?.let { main.post(it) }
    }

    private fun startAttribution() {
        if (prefs.getBoolean("referrer_read", false)) {
            executor.execute { bindPending() }
            return
        }
        val client = InstallReferrerClient.newBuilder(app).build()
        val finished = AtomicBoolean(false)
        val timeout = Runnable {
            if (finished.compareAndSet(false, true)) { client.endConnection(); retryLater("STORE_UNAVAILABLE"); complete() }
        }
        main.postDelayed(timeout, 30_000L)
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    if (!finished.compareAndSet(false, true)) return
                    main.removeCallbacks(timeout)
                    executor.execute {
                        try {
                            if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                                check(canContinue()) { "Verification cancelled" }
                                val details = client.installReferrer
                                check(details.installReferrer.length <= 4096) { "Invalid install referrer" }
                                val click = Uri.parse("https://referrer.invalid/?" + details.installReferrer).getQueryParameter("lp_ref")
                                val edit = prefs.edit().putBoolean("referrer_read", true)
                                if (click != null && click.matches(Regex("[a-f0-9]{64}"))) {
                                    check(details.installBeginTimestampServerSeconds > 0L && details.referrerClickTimestampServerSeconds > 0L) { "Play timestamps unavailable" }
                                    edit.putString("click", click)
                                        .putLong("install_at", details.installBeginTimestampServerSeconds * 1000L)
                                        .putLong("clicked_at", details.referrerClickTimestampServerSeconds * 1000L)
                                }
                                if (canContinue()) edit.apply()
                            } else retryLater("STORE_UNAVAILABLE")
                        } catch (_: Exception) {
                            retryLater("STORE_UNAVAILABLE")
                        } finally {
                            client.endConnection()
                        }
                        bindPending()
                    }
                }
                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        } catch (_: Exception) {
            finished.set(true)
            main.removeCallbacks(timeout)
            client.endConnection()
            retryLater("STORE_UNAVAILABLE"); complete()
        }
    }

    private fun bindPending() {
        try {
            if (!canContinue()) return
            val click = prefs.getString("click", null) ?: run { if (prefs.getBoolean("referrer_read", false)) state("NO_INVITATION"); return }
            val status = status()
            if (!status.optBoolean("enabled") || !status.optBoolean("googleLinked") || !status.optBoolean("consented") || !canContinue()) return
            val accountId = status.getString("localAccountId")
            val challenge = accounts.referralRequest("challenge", JSONObject().put("click", click)
                .put("installAt", prefs.getLong("install_at", 0L)).put("clickedAt", prefs.getLong("clicked_at", 0L)), accountId)
            if (!challenge.optBoolean("bound")) {
                val manager = IntegrityManagerFactory.createStandard(app)
                val provider = Tasks.await(manager.prepareIntegrityToken(
                    StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(challenge.getString("cloudProjectNumber").toLong()).build(),
                ), 60, TimeUnit.SECONDS)
                if (!canContinue()) return
                val integrity = Tasks.await(provider.request(
                    StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(challenge.getString("requestHash")).build(),
                ), 30, TimeUnit.SECONDS)
                if (!canContinue()) return
                accounts.referralRequest("bind", JSONObject().put("challenge", challenge.getString("challenge"))
                    .put("integrityToken", integrity.token()), accountId)
            }
            if (canContinue()) {
                prefs.edit().putBoolean("bound", true).putInt("attempts", 0).putLong("next_attempt_at", 0L)
                    .remove("click").remove("install_at").remove("clicked_at").apply()
                state("BOUND")
            }
        } catch (error: AccountApiException) {
            retryLater(when (error.code) {
                "REFERRAL_NOT_ELIGIBLE", "SELF_REFERRAL" -> "NOT_ELIGIBLE"
                "TERMS_ACCEPTANCE_REQUIRED" -> "REVIEW_TERMS"
                "INSTALL_NOT_VERIFIED" -> "PLAY_NOT_VERIFIED"
                else -> "RETRY_LATER"
            })
        } catch (_: Exception) { retryLater("RETRY_LATER")
        } finally {
            complete()
        }
    }

    companion object {
        const val TERMS_VERSION = "friendship-2026-10-27"
        val executor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "LudoPawsReferral") }
        private val main = Handler(Looper.getMainLooper())
        private val attributing = AtomicBoolean(false)
    }
}
