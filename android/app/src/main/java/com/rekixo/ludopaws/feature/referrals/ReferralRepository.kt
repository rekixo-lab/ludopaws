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
        if (status.optString("state") == "REWARDED") {
            val wallet = accounts.wallet()
            CosmeticInventoryStore(app).applyServerSnapshot(wallet, account.accountId)
        }
        return status.put("localAccountId", account.accountId)
    }

    fun invite(expectedAccountId: String): String {
        val value = accounts.referralRequest("invite", JSONObject(), expectedAccountId).getString("shareUrl")
        val uri = Uri.parse(value)
        require(uri.scheme == "https" && uri.host == "api.ludopaws.rekixo.com" &&
            uri.path.orEmpty().matches(Regex("/r/[a-f0-9]{64}")) && uri.query == null && uri.fragment == null)
        check(accounts.currentSession()?.accountId == expectedAccountId) { "Your account changed. Please retry." }
        return value
    }

    fun refreshAttribution() {
        if (!BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED || !attributing.compareAndSet(false, true)) return
        if (prefs.getBoolean("bound", false)) { attributing.set(false); return }
        executor.execute {
            val enabled = runCatching { status().let { it.optBoolean("enabled") && it.optBoolean("googleLinked") } }.getOrDefault(false)
            if (enabled) main.post { startAttribution() } else attributing.set(false)
        }
    }

    private fun startAttribution() {
        if (prefs.getBoolean("referrer_read", false)) {
            executor.execute { bindPending() }
            return
        }
        val client = InstallReferrerClient.newBuilder(app).build()
        val finished = AtomicBoolean(false)
        val timeout = Runnable {
            if (finished.compareAndSet(false, true)) { client.endConnection(); attributing.set(false) }
        }
        main.postDelayed(timeout, 20_000L)
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    if (!finished.compareAndSet(false, true)) return
                    main.removeCallbacks(timeout)
                    executor.execute {
                        try {
                            if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                                val details = client.installReferrer
                                val click = Uri.parse("https://referrer.invalid/?" + details.installReferrer).getQueryParameter("lp_ref")
                                val edit = prefs.edit().putBoolean("referrer_read", true)
                                if (click != null && click.matches(Regex("[a-f0-9]{64}"))) {
                                    edit.putString("click", click)
                                        .putLong("install_at", details.installBeginTimestampServerSeconds * 1000L)
                                        .putLong("clicked_at", details.referrerClickTimestampServerSeconds * 1000L)
                                }
                                edit.apply()
                            }
                        } catch (_: Exception) {
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
            attributing.set(false)
        }
    }

    private fun bindPending() {
        try {
            val click = prefs.getString("click", null) ?: return
            val status = status()
            if (!status.optBoolean("enabled") || !status.optBoolean("googleLinked")) return
            val accountId = status.getString("localAccountId")
            val challenge = accounts.referralRequest("challenge", JSONObject().put("click", click)
                .put("installAt", prefs.getLong("install_at", 0L)).put("clickedAt", prefs.getLong("clicked_at", 0L)), accountId)
            if (!challenge.optBoolean("bound")) {
                val manager = IntegrityManagerFactory.createStandard(app)
                val provider = Tasks.await(manager.prepareIntegrityToken(
                    StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(challenge.getString("cloudProjectNumber").toLong()).build(),
                ), 30, TimeUnit.SECONDS)
                val integrity = Tasks.await(provider.request(
                    StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(challenge.getString("requestHash")).build(),
                ), 30, TimeUnit.SECONDS)
                accounts.referralRequest("bind", JSONObject().put("challenge", challenge.getString("challenge"))
                    .put("integrityToken", integrity.token()), accountId)
            }
            prefs.edit().putBoolean("bound", true).remove("click").remove("install_at").remove("clicked_at").apply()
        } catch (_: Exception) {
        } finally {
            attributing.set(false)
        }
    }

    companion object {
        val executor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "LudoPawsReferral") }
        private val main = Handler(Looper.getMainLooper())
        private val attributing = AtomicBoolean(false)
    }
}
