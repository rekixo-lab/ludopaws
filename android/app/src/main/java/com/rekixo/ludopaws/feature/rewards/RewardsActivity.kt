package com.rekixo.ludopaws.feature.rewards

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.account.data.AccountRepository
import com.rekixo.ludopaws.feature.growth.EngagementStore
import com.rekixo.ludopaws.feature.growth.GrowthMetrics
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.CharacterMasteryPolicy
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.store.data.ads.*
import com.rekixo.ludopaws.feature.store.data.local.GemWalletStore
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/** One explicit hub; no auto-open, local currency grants, paid spins or reset pressure. */
class RewardsActivity : Activity() {
    private val repository by lazy { (application as LudoProofApplication).accountRepository }
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("reward_requests_v1", MODE_PRIVATE) }
    private lateinit var body: LinearLayout
    private lateinit var message: TextView
    private var snapshot: JSONObject? = null
    private var owner: String? = null
    private var resumed = false
    private var busy = false
    private var generation = 0
    private data class BonusRequest(val account: String, val ticket: String, val requestId: String, val day: Long)
    private var adsAvailable = false
    private var ad: RewardedAdsController? = null
    private var confirmationRefreshes = 0
    private val refresh = Runnable { if (confirmationRefreshes < 5) { confirmationRefreshes++; load() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF143B3F.toInt()); isFillViewport = true }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(28)) }
        scroll.addView(body); setContentView(scroll); render()
    }
    override fun onResume() { super.onResume(); resumed = true; generation++; ad?.onHostResumed(); load() }
    override fun onPause() { resumed = false; generation++; busy = false; ad?.cancelPendingShow(); handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { ad?.close(); handler.removeCallbacksAndMessages(null); executor.shutdownNow(); super.onDestroy() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 15f): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(0xFFFFF1D8.toInt()); setPadding(0, dp(8), 0, dp(8))
    }
    private fun button(label: String, enabled: Boolean = true, action: () -> Unit) {
        body.addView(Button(this).apply {
            text = label; isAllCaps = false; minHeight = dp(48); isEnabled = enabled && !busy
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
    }
    private fun render() {
        body.removeAllViews()
        body.addView(text("REWARDS & COLLECTIONS", 23f).apply { typeface = Typeface.DEFAULT_BOLD })
        body.addView(text("Free gifts are optional. Gameplay stays available without claims or ads."))
        message = text(if (busy) "Checking your rewards…" else "")
        body.addView(message)
        if (owner != null && repository.currentSession()?.accountId != owner) {
            owner = null; snapshot = null
        }
        val rewards = snapshot?.optJSONObject("rewards")
        val wallet = snapshot?.optJSONObject("wallet")
        body.addView(text("Gems: ${wallet?.optInt("balance") ?: GemWalletStore(this).balance()}", 20f))
        button("Refresh / retry") { load() }
        owner?.let { account ->
            prefs.getString("$account:collection", null)?.let { pending ->
                button("Recover pending collection purchase") { perform(JSONObject(pending)) }
            }
        }
        if (rewards != null) {
            val reset = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                .format(java.util.Date(rewards.getLong("resetsAt")))
            body.addView(text("Next free gifts: $reset (your local time)"))
            button(if (rewards.optBoolean("dailyClaimed")) "Daily gift claimed" else "Claim free daily gift · 3 Gems", !rewards.optBoolean("dailyClaimed")) { claim("daily") }
            val total = rewards.optInt("dailyClaims")
            val tier = rewards.optInt("milestoneTier")
            body.addView(text("$total gifts collected · next milestone at ${rewards.optInt("nextMilestone")}\n" +
                if (tier > 0) "Garden Friend badge · tier $tier · progress never resets" else "Collect 7 daily gifts for your Garden Friend badge. Missed days do not erase progress."))
            body.addView(text("DAILY FREE SPIN", 20f))
            val wheel = RewardWheelView(this).apply { contentDescription = "Reward wheel. Outcomes and chances are listed below. Every free spin wins Gems." }
            body.addView(wheel, LinearLayout.LayoutParams(-1, dp(220)))
            button(if (rewards.optBoolean("spinClaimed")) "Spin claimed · ${rewards.optInt("spinGems")} Gems" else "Spin free · once today", !rewards.optBoolean("spinClaimed")) { claim("spin", wheel) }
            body.addView(text("Every spin wins Gems. Chances: 1 Gem 50% · 2 Gems 30% · 3 Gems 15% · 5 Gems 5%. No money, entry fee or cash prize."))
            val bonusPending = owner?.let { account -> runCatching { PendingAdRewardStore(this).forAccount(account).any {
                it.dailyRewardDay == rewards.optLong("day") && !it.abandoned
            } }.getOrDefault(true) } ?: false
            button(when { rewards.optBoolean("bonusClaimed") -> "Today's ad bonus claimed"; bonusPending -> "Ad bonus verification pending · Refresh to check"; else -> "Optional: watch one ad for +5 Gems" },
                !rewards.optBoolean("bonusClaimed") && !bonusPending) { bonus() }
            body.addView(text("Your free gifts do not require an ad. Remove Ads owners can still choose rewarded ads."))
        }
        val profile = ProfileStore(this).snapshot()
        val today = EngagementStore(this).todayMatches()
        val used = profile.characterMatches.count { it.value > 0 }
        val best = profile.characterMatches.values.maxOrNull() ?: 0
        body.addView(text("PLAY GOALS", 20f))
        body.addView(text("Today: complete one match · ${today.coerceAtMost(1)}/1\nMeet three pals · ${used.coerceAtMost(3)}/3\nPlay five matches with a pal · ${best.coerceAtMost(5)}/5\n" +
            "All modes count. Wins are optional. Build XP, badges and mastery frames as you play. Free gifts are claimed separately."))
        button("View mastery & badges") { launchPlayerScreen(Intent(this, com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity::class.java)) }
        body.addView(text("CURATED COLLECTIONS", 20f))
        body.addView(text("Keep every item you already own. Pay only the discounted cost of missing items. All items are visual; dice odds are unchanged."))
        snapshot?.optJSONArray("collections")?.let { rows ->
            for (i in 0 until rows.length()) {
                val item = rows.getJSONObject(i); val missing = item.getJSONArray("missing")
                body.addView(text((if (rewards?.optString("featuredCollectionId") == item.optString("id")) "Featured this month · " else "") + item.getString("title"), 18f))
                body.addView(text((0 until item.getJSONArray("items").length()).joinToString(" · ") { index ->
                    com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.find(item.getJSONArray("items").getString(index))?.title
                        ?: item.getJSONArray("items").getString(index).replace('_', ' ')
                }))
                button(if (missing.length() == 0) "Collection owned" else "Unlock missing items · ${item.getInt("price")} Gems", missing.length() > 0) { buy(item) }
                button("Preview / equip in Shop") {
                    launchPlayerScreen(Intent(this, StoreActivity::class.java).putExtra(StoreActivity.EXTRA_PROMOTED_COSMETIC_ID,
                        item.getJSONArray("items").getString(0)))
                }
            }
        }
        button("Open Shop · cosmetics, Gems & Remove Ads") { launchPlayerScreen(Intent(this, StoreActivity::class.java)) }
        button("Quick Match · two paws each") { chooseQuickMatch() }
        button("Share / invitation progress") { launchPlayerScreen(Intent(this, com.rekixo.ludopaws.feature.referrals.ReferralActivity::class.java)) }
        button("Back to game menu") { finish() }
    }
    private fun load() {
        if (!resumed || busy) return
        perform(null)
    }
    private fun perform(request: JSONObject?, wheel: RewardWheelView? = null) {
        if (busy || !resumed) return
        busy = true
        val expectedGeneration = generation
        val expectedOwner = repository.currentSession()?.accountId
        if (wheel == null) render()
        executor.execute {
            val result = runCatching {
                val session = repository.ensureGuestSession()
                if (expectedOwner != null) check(session.accountId == expectedOwner) { "Account changed. Reopen Rewards." }
                val response = repository.engagementRewards(request)
                val rewards = response.getJSONObject("rewards")
                check(rewards.getLong("day") >= 0 && rewards.getLong("resetsAt") > 0) { "Reward status could not be read. Retry." }
                response.getJSONArray("collections")
                response.optJSONObject("receipt")?.let { receipt ->
                    if (request?.optString("action") == "spin") check(receipt.getInt("amount") in setOf(1, 2, 3, 5)) { "Spin receipt could not be read. Retry." }
                }
                check(GemWalletStore(this).applyServerSnapshot(response.getJSONObject("wallet"), session.accountId)) { "Account or wallet changed. Refresh Rewards." }
                session.accountId to response
            }
            handler.post {
                if (!resumed || generation != expectedGeneration || isFinishing || isDestroyed) return@post
                busy = false
                result.onSuccess { (account, response) ->
                    if (repository.currentSession()?.accountId != account) { snapshot = null; render(); message.text = "Account changed. Refresh Rewards."; return@onSuccess }
                    val confirmed = response.getJSONObject("wallet").optJSONArray("creditedAdRequestIds")
                    if (confirmed != null) runCatching { PendingAdRewardStore(this).confirm(account, (0 until confirmed.length()).map { confirmed.getString(it) }.toSet()) }
                    owner = account; snapshot = response
                    prefs.edit().putInt("$account:milestoneTier", response.getJSONObject("rewards").optInt("milestoneTier")).apply()
                    val stillPending = runCatching { PendingAdRewardStore(this).forAccount(account).any { it.dailyRewardDay != null && it.earned } }.getOrDefault(false)
                    if (stillPending && confirmationRefreshes < 5) { handler.removeCallbacks(refresh); handler.postDelayed(refresh, 2000L) }
                    if (request != null) prefs.edit().remove("$account:${request.optString("action")}").apply()
                    val receipt = response.optJSONObject("receipt")
                    if (wheel != null && receipt != null) {
                        busy = true
                        val amount = receipt.optInt("amount")
                        wheel.stopAt(amount, !GameSettingsStore(this).snapshot().reducedMotion && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                            if (resumed && generation == expectedGeneration) { busy = false; render(); message.text = "Spin reward saved: $amount Gems" }
                        }
                    } else { render(); if (receipt != null) message.text = if (receipt.has("amount")) "Reward saved: ${receipt.optInt("amount")} Gems" else "Collection saved." }
                    if (request?.optString("action") == "daily" && receipt?.optBoolean("replay") == false) GrowthMetrics.increment(this, "reward_claim")
                    if (request?.optString("action") == "spin" && receipt?.optBoolean("replay") == false) GrowthMetrics.increment(this, "spin_claim")
                    if (request?.optString("action") == "collection" && receipt?.optBoolean("replay") == false) GrowthMetrics.increment(this, "collection_purchase")
                }.onFailure { error ->
                    if (request != null && error is com.rekixo.ludopaws.feature.account.data.AccountApiException && error.code == "REWARD_DAY_CHANGED") {
                        prefs.edit().remove("${expectedOwner}:${request.optString("action")}").apply()
                    }
                    if (request?.optString("action") == "collection" && error is com.rekixo.ludopaws.feature.account.data.AccountApiException && error.status == 409) prefs.edit().remove("${expectedOwner}:collection").apply()
                    render(); message.text = error.message ?: "Rewards are unavailable. Your game remains playable; retry when online."
                }
            }
        }
    }
    private fun claim(action: String, wheel: RewardWheelView? = null) {
        val day = snapshot?.optJSONObject("rewards")?.optLong("day") ?: return
        val account = owner ?: return
        if (repository.currentSession()?.accountId != account) { snapshot = null; render(); return }
        val key = "$account:$action"
        val request = prefs.getString(key, null)?.let { JSONObject(it) }
            ?: JSONObject().put("action", action).put("day", day).put("clientRequestId", UUID.randomUUID().toString())
        if (!prefs.edit().putString(key, request.toString()).commit()) { message.text = "Could not save reward recovery. Retry."; return }
        perform(request, wheel)
    }
    private fun buy(collection: JSONObject) {
        val cost = collection.getInt("price")
        android.app.AlertDialog.Builder(this).setTitle(collection.getString("title"))
            .setMessage("Unlock the missing items for $cost Gems? Owned items stay yours.")
            .setNegativeButton("Later", null).setPositiveButton("Unlock") { _, _ ->
                val account = owner ?: return@setPositiveButton
                val key = "$account:collection"
                val request = prefs.getString(key, null)?.let(::JSONObject) ?: JSONObject().put("action", "collection")
                    .put("collectionId", collection.getString("id")).put("expectedPrice", cost).put("clientRequestId", UUID.randomUUID().toString())
                if (request.optString("collectionId") != collection.getString("id")) { message.text = "Retry the pending collection first."; return@setPositiveButton }
                if (prefs.edit().putString(key, request.toString()).commit()) perform(request)
            }.show()
    }
    private fun bonus() {
        if (busy || !resumed || ad?.isBusy == true) return
        busy = true; message.text = "Preparing optional +5 Gems ad…"
        val expectedGeneration = generation
        executor.execute {
            val result = runCatching {
                check(BuildConfig.LUDOPROOF_ADS_ENABLED && repository.storeStatus().optBoolean("rewardedAdsAvailable")) { "No rewarded ad is available. Free gifts stay yours." }
                val session = repository.ensureGuestSession()
                val ticket = repository.engagementRewards(JSONObject().put("action", "bonus-ticket")).getJSONObject("receipt")
                BonusRequest(session.accountId, ticket.getString("ticket"), UUID.randomUUID().toString(), ticket.getLong("day"))
            }
            handler.post {
                if (!resumed || generation != expectedGeneration || isFinishing || isDestroyed) return@post
                busy = false
                result.onSuccess { (account, ticket, requestId, day) ->
                    if (repository.currentSession()?.accountId != account) return@onSuccess
                    adsAvailable = true
                    confirmationRefreshes = 0
                    val controller = ad ?: RewardedAdsController(activity = this, enabled = { adsAvailable }, adUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID, onMessage = { message.text = it }, canPresent = { resumed && !isFinishing && !isDestroyed }).also { ad = it }
                    val pending = PendingAdReward(account, requestId, null, dailyRewardDay = day)
                    controller.showRewardedAd(account, JSONObject().put("kind", "gems").put("dailyTicket", ticket).put("requestId", requestId).toString(),
                        onBeforeShow = { if (resumed && repository.currentSession()?.accountId == account) { PendingAdRewardStore(this).put(pending); true } else false },
                        onFinished = { earned ->
                            if (!earned) runCatching { PendingAdRewardStore(this).put(pending.copy(abandoned = true)) }
                            handler.postDelayed(refresh, 1000L)
                        }, onRewardEarned = {
                            runCatching { PendingAdRewardStore(this).put(pending.copy(earned = true)) }
                            GrowthMetrics.increment(this, "rewarded_earned")
                            handler.postDelayed(refresh, 1500L)
                        })
                }.onFailure { message.text = it.message ?: "Ad unavailable. Free rewards are unchanged." }
            }
        }
    }
    private fun chooseQuickMatch() {
        android.app.AlertDialog.Builder(this).setTitle("Quick Match · two paws per player")
            .setMessage("A separate local mode. Two players, two racing paws each; the normal board and dice rules stay the same. No wager or wallet reward.")
            .setNegativeButton("Later", null)
            .setNeutralButton("Pass & Play") { _, _ -> quick(false) }
            .setPositiveButton("Play with Robot") { _, _ -> quick(true) }.show()
    }
    private fun quick(computer: Boolean) {
        launchPlayerScreen(Intent(this, OfflineGameActivity::class.java).putExtra(OfflineGameActivity.EXTRA_GAME_MODE,
            if (computer) "COMPUTER" else "PASS_AND_PLAY").putExtra("QUICK_MATCH", true))
    }
}
