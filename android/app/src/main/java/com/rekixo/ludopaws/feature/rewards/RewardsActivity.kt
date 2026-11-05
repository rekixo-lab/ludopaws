package com.rekixo.ludopaws.feature.rewards

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.widget.FrameLayout
import android.text.Editable
import android.text.TextWatcher
import com.rekixo.ludopaws.feature.growth.GrowthUi
import com.rekixo.ludopaws.feature.growth.GrowthPage
import com.rekixo.ludopaws.feature.store.presentation.art.GemClusterView
import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.growth.EngagementStore
import com.rekixo.ludopaws.feature.growth.GrowthMetrics
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
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
    private lateinit var page: GrowthPage
    private var selectedTab = 0
    private lateinit var message: TextView
    private var snapshot: JSONObject? = null
    private var owner: String? = null
    private var resumed = false
    private var busy = false
    private var rewardsNotReleased = false
    private var generation = 0
    private data class BonusRequest(val account: String, val ticket: String, val requestId: String, val day: Long)
    private var adsAvailable = false
    private var ad: RewardedAdsController? = null
    private var confirmationRefreshes = 0
    private val refresh = Runnable { if (confirmationRefreshes < 5) { confirmationRefreshes++; load() } }
    private val rollover = Runnable { refreshOnRollover() }
    private fun refreshOnRollover() {
        if (!resumed) return
        if (busy || ad?.isBusy == true) { handler.postDelayed(rollover, 1000L); return }
        snapshot = null
        render()
        load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        selectedTab = savedInstanceState?.getInt("rewards_tab", 0)?.coerceIn(0, 2) ?: 0
        page = GrowthPage(this, "Rewards", onBack = { finish() }, onRefresh = { load() })
        body = page.body
        render()
    }
    override fun onResume() { super.onResume(); resumed = true; generation++; ad?.onHostResumed(); load() }
    override fun onPause() { resumed = false; generation++; busy = false; ad?.cancelPendingShow(); handler.removeCallbacks(refresh); handler.removeCallbacks(rollover); super.onPause() }
    override fun onDestroy() { ad?.close(); handler.removeCallbacksAndMessages(null); executor.shutdownNow(); super.onDestroy() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("rewards_tab", selectedTab)
        super.onSaveInstanceState(outState)
    }
    private fun copy(value: String, size: Float = 14f, bold: Boolean = false) = GrowthUi.copy(this, value, size, bold)
    private fun add(parent: LinearLayout, view: View, gap: Int = 10) = GrowthUi.add(parent, view, gap)
    private fun action(parent: LinearLayout, label: String, enabled: Boolean = true, secondary: Boolean = false, callback: () -> Unit) {
        add(parent, GrowthUi.button(this, label, primary = !secondary, enabled = enabled && !busy, action = { if (!busy) callback() }))
    }
    private fun card(title: String, subtitle: String? = null): LinearLayout = GrowthUi.card(this).also {
        GrowthUi.heading(it, title, subtitle)
        add(body, it, 14)
    }
    private fun timestamp(value: Long) = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(value))
    private fun render() {
        val previousScroll = page.scroll.scrollY
        body.removeAllViews()
        if (owner != null && repository.currentSession()?.accountId != owner) { owner = null; snapshot = null }
        val rewards = snapshot?.optJSONObject("rewards")
        val wallet = snapshot?.optJSONObject("wallet")
        val hero = GrowthUi.card(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val intro = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(intro, GrowthUi.pill(this, "PLAY · COLLECT · ENJOY"), 0)
        add(intro, copy("Little gifts.\nBig adventures.", 25f, true), 10)
        add(intro, copy("A little something for your next game.", 13f), 6)
        row.addView(intro, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(GrowthUi.portrait(this, com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore(this).load().characterId),
            LinearLayout.LayoutParams(dp(88), dp(108)).apply { marginStart = dp(8) })
        add(hero, row, 0)
        val balance = wallet?.optInt("balance") ?: GemWalletStore(this).balance()
        val walletRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        walletRow.addView(GemClusterView(this, 1).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(36), dp(36)))
        walletRow.addView(copy("$balance Gems", 18f, true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        walletRow.addView(GrowthUi.pill(this, "YOUR GEMS"), LinearLayout.LayoutParams(-2, -2))
        add(hero, walletRow, 14); add(body, hero, 0)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        listOf("Gifts", "Challenges", "Collections").forEachIndexed { index, title ->
            val tab = GrowthUi.button(this, title, primary = selectedTab == index, enabled = !busy && ad?.isBusy != true) {
                if (!busy && ad?.isBusy != true) { selectedTab = index; page.scroll.scrollTo(0, 0); render() }
            }.apply { textSize = 13f; isSelected = selectedTab == index; contentDescription = "$title, " + if (selectedTab == index) "selected" else "tab" }
            tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(5) })
        }
        add(body, tabs, 16)
        message = copy(if (busy) "Checking your rewards…" else "", 13f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            background = com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground(this@RewardsActivity, 14f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        add(body, message, 10)
        when (selectedTab) {
            0 -> gifts(rewards)
            1 -> challenges()
            else -> collections(rewards)
        }
        add(body, copy("Play your way. Gifts and sharing are always optional.", 12f).apply { gravity = Gravity.CENTER }, 20)
        handler.removeCallbacks(rollover)
        if (resumed) {
            val weekly = com.rekixo.ludopaws.feature.growth.WeeklyChallengeStore(this).status()
            val next = minOf(weekly.endsAt, rewards?.optLong("resetsAt")?.takeIf { it > 0L } ?: weekly.endsAt)
            handler.postDelayed(rollover, (next - System.currentTimeMillis()).coerceIn(1000L, 86_400_000L))
        }
        page.scroll.post { if (!isDestroyed) page.scroll.scrollTo(0, previousScroll) }
    }
    private fun gifts(rewards: JSONObject?) {
        if (rewards == null) {
            val status = when { busy -> "CHECKING AVAILABILITY"; rewardsNotReleased -> "COMING IN THE NEXT UPDATE"; else -> "CONNECT TO CHECK AVAILABILITY" }
            val dailyPreview = card("Daily Gift", "A daily gift of 3 Gems for your collection.")
            add(dailyPreview, GrowthUi.pill(this, status))
            val gemArt = GemClusterView(this, 3).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            dailyPreview.addView(gemArt, LinearLayout.LayoutParams(-1, dp(100)).apply { topMargin = dp(12) })
            action(dailyPreview, if (busy) "Checking…" else if (rewardsNotReleased) "Available with the next update" else "Connect to collect", enabled = false) {}
            add(dailyPreview, copy(if (rewardsNotReleased) "Daily gifts are not active yet. No Gems have been claimed." else "Your claim status hasn't loaded. Check your connection and refresh.", 13f))
            val spinPreview = card("Daily Free Spin", "One free spin each day, with 1, 2, 3 or 5 Gems.")
            add(spinPreview, GrowthUi.pill(this, "WHEEL PREVIEW · NO REWARD CLAIM"))
            spinPreview.addView(RewardWheelView(this).apply {
                contentDescription = "Daily spin preview. Spin is unavailable until reward status loads."
            }, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(10) })
            action(spinPreview, if (busy) "Checking…" else if (rewardsNotReleased) "Available with the next update" else "Connect to spin", enabled = false) {}
            add(spinPreview, copy("Chances: 1 Gem 50% · 2 Gems 30% · 3 Gems 15% · 5 Gems 5%. No paid spin or cash prize.", 12f))
            action(body, "Check availability", secondary = true) { load() }
            action(body, "See my challenges", secondary = true) { selectedTab = 1; page.scroll.scrollTo(0, 0); render() }
            return
        }
        val daily = card("A gift for today", "A few Gems to grow your collection.")
        val giftRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        giftRow.addView(GemClusterView(this, 3).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(86), dp(82)))
        val numbers = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(numbers, copy("+${rewards.optInt("dailyGems", 3)} Gems", 30f, true), 0)
        add(numbers, GrowthUi.pill(this, if (rewards.optBoolean("dailyClaimed")) "COLLECTED TODAY" else "FREE DAILY GIFT", rewards.optBoolean("dailyClaimed")), 5)
        giftRow.addView(numbers, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) }); add(daily, giftRow)
        action(daily, if (rewards.optBoolean("dailyClaimed")) "Gift collected" else "Collect my gift", !rewards.optBoolean("dailyClaimed")) { claim("daily") }
        val total = rewards.optInt("dailyClaims")
        val remaining = 7 - total % 7
        add(daily, copy(if (total > 0 && total % 7 == 0) "Garden Friend badge earned · tier ${rewards.optInt("milestoneTier")}" else "$remaining more daily gifts to your next Garden Friend badge", 13f, true), 14)
        val progress = GrowthUi.progress(this, if (total > 0 && total % 7 == 0) 7 else total % 7, 7, "$total daily gifts collected")
        daily.addView(progress, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(8) })
        add(daily, copy("Miss a day? Your badge progress stays.", 12f), 7)
        val spin = card("Take a little spin", "One free spin each day. Every spin wins Gems.")
        val wheel = RewardWheelView(this).apply { contentDescription = "Free reward wheel. Possible prizes are 1, 2, 3 or 5 Gems." }
        if (rewards.optBoolean("spinClaimed")) wheel.stopAt(rewards.optInt("spinGems"), animated = false) {}
        spin.addView(wheel, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(8) })
        action(spin, if (rewards.optBoolean("spinClaimed")) "Collected · ${rewards.optInt("spinGems")} Gems" else "Spin for free", !rewards.optBoolean("spinClaimed")) { claim("spin", wheel) }
        val odds = LinearLayout(this).apply { gravity = Gravity.CENTER }
        listOf("1 Gem · 50%", "2 · 30%", "3 · 15%", "5 · 5%").forEach { label ->
            odds.addView(copy(label, 11f, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        add(spin, odds, 12)
        add(spin, copy("Next free gifts: ${timestamp(rewards.getLong("resetsAt"))} (your time).", 12f), 10)
        val bonusPending = owner?.let { account -> runCatching { PendingAdRewardStore(this).forAccount(account).any {
            it.dailyRewardDay == rewards.optLong("day") && !it.abandoned
        } }.getOrDefault(true) } ?: false
        val extra = card("A little extra", "An optional ad. Your free gift and spin need no ads.")
        action(extra, when { rewards.optBoolean("bonusClaimed") -> "Today's bonus collected"; bonusPending -> "Checking your ad reward…"; else -> "Watch an ad · +${rewards.optInt("bonusGems", 2)} Gems" },
            !rewards.optBoolean("bonusClaimed") && !bonusPending, secondary = true) { bonus() }
        add(extra, copy("Once per day. Remove Ads owners may also choose this bonus.", 12f), 8)
        action(extra, "Reward details", secondary = true) {
            PlywoodAlertDialogBuilder(this).setTitle("Your free gifts")
                .setMessage("Daily gift: 3 Gems. Free spin chances: 1 Gem 50%, 2 Gems 30%, 3 Gems 15%, 5 Gems 5%. Every spin wins. No entry fee, paid spin or cash prize. Your gift and free spin do not require an ad or purchase.")
                .setPositiveButton("Got it", null).show()
        }
    }
    private fun challenges() {
        val today = EngagementStore(this).todayMatches().coerceAtMost(1)
        val daily = card("Daily Challenge", "TODAY'S PLAY GOAL")
        add(daily, copy("Finish one match today", 20f, true))
        add(daily, GrowthUi.pill(this, if (today == 1) "TODAY'S GOAL COMPLETE" else "WINS ARE OPTIONAL", today == 1))
        daily.addView(GrowthUi.progress(this, today, 1, "Daily Challenge: $today of 1 match"), LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(14) })
        add(daily, copy("$today / 1 match completed", 16f, true))
        add(daily, copy("All modes count. This is a play goal; daily Gems are collected separately in Gifts when available.", 13f))
        action(daily, "Play today's challenge") { openGameMode(GameMode.PASS_AND_PLAY) }
        val weekly = com.rekixo.ludopaws.feature.growth.WeeklyChallengeStore(this).status()
        val challenge = card(weekly.title, "THIS WEEK'S PAWS ADVENTURE")
        add(challenge, GrowthUi.pill(this, if (weekly.complete) "WEEKLY BADGE EARNED" else "WINS ARE OPTIONAL", weekly.complete))
        add(challenge, copy(weekly.goal, 20f, true), 16)
        val achievement = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        achievement.addView(com.rekixo.ludopaws.feature.growth.PawsBadgeView(this, weekly.complete), LinearLayout.LayoutParams(dp(90), dp(94)))
        achievement.addView(copy("${weekly.progress} / ${weekly.target}", 32f, true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
        add(challenge, achievement, 8)
        challenge.addView(GrowthUi.progress(this, weekly.progress, weekly.target, "${weekly.goal}: ${weekly.progress} of ${weekly.target}"),
            LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(8) })
        add(challenge, copy("All modes count. Earn a Weekly Paws badge for this device.\nNext adventure: ${timestamp(weekly.endsAt)} (your time).", 13f), 12)
        action(challenge, "Let's play · Pass & Play") { openGameMode(GameMode.PASS_AND_PLAY) }
        action(challenge, "Share this adventure", secondary = true) { com.rekixo.ludopaws.feature.sharing.MatchShareCard.weekly(this, weekly.title, weekly.complete) }
        val profile = ProfileStore(this).snapshot()
        val goals = card("Little goals, happy paws", "XP, badges and mastery grow as you play.")
        val used = profile.characterMatches.count { it.value > 0 }
        val best = profile.characterMatches.values.maxOrNull() ?: 0
        listOf(Triple("Meet three pals", used, 3), Triple("Play five with a pal", best, 5)).forEach { (title, done, target) ->
            add(goals, copy("$title · ${done.coerceAtMost(target)}/$target", 14f, true), 14)
            goals.addView(GrowthUi.progress(this, done, target, "$title: ${done.coerceAtMost(target)} of $target"), LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(6) })
        }
        action(goals, "My badges & pals", secondary = true) { launchPlayerScreen(Intent(this, com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity::class.java)) }
        val play = card("A quick game together", "Two players. Two racing paws each. The same playful Ludo.")
        val pals = LinearLayout(this).apply { gravity = Gravity.CENTER }
        listOf("dog", "duck").forEach { pals.addView(GrowthUi.portrait(this, it), LinearLayout.LayoutParams(dp(88), dp(88))) }
        add(play, pals)
        action(play, "Quick Match") { chooseQuickMatch() }
        action(play, "Open Friends", secondary = true) { openGameMode(GameMode.FRIENDS) }
    }
    private fun collections(rewards: JSONObject?) {
        val intro = card("Find your next favourites", "A pal, a board and a little sparkle. Keep what you own; pay only for missing items.")
        add(intro, GrowthUi.pill(this, "COSMETICS ONLY · SAME DICE ODDS"))
        owner?.let { account -> prefs.getString("$account:collection", null)?.let { pending ->
            action(intro, "Recover pending purchase", secondary = true) { perform(JSONObject(pending)) }
        } }
        val rows = snapshot?.optJSONArray("collections")
        if (rows == null || rows.length() == 0) {
            val empty = card("Collections are taking a little break", "Connect and refresh to see the items and your current prices.")
            action(empty, "Refresh collections") { load() }
        } else for (i in 0 until rows.length()) {
            val item = rows.getJSONObject(i)
            val ids = item.getJSONArray("items")
            val missing = item.getJSONArray("missing")
            val collection = card(item.getString("title"))
            if (rewards?.optString("featuredCollectionId") == item.optString("id")) add(collection, GrowthUi.pill(this, "IN THE SPOTLIGHT"), 8)
            val gallery = LinearLayout(this).apply { gravity = Gravity.TOP }
            for (n in 0 until ids.length()) {
                val id = ids.getString(n)
                val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                val frame = FrameLayout(this).apply {
                    background = com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground(this@RewardsActivity, 14f)
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    addView(GrowthUi.preview(this@RewardsActivity, id), FrameLayout.LayoutParams(-1, -1))
                }
                tile.addView(frame, LinearLayout.LayoutParams(-1, dp(84)))
                add(tile, copy(com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.find(id)?.title ?: id.replace('_', ' '), 11f, true).apply { gravity = Gravity.CENTER }, 6)
                gallery.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { if (n > 0) marginStart = dp(7) })
            }
            add(collection, gallery, 14)
            add(collection, copy("${ids.length() - missing.length()} of ${ids.length()} already yours", 13f), 12)
            action(collection, if (missing.length() == 0) "Collection complete" else "Unlock missing · ${item.getInt("price")} Gems", missing.length() > 0) { buy(item) }
            action(collection, "Preview in Shop", secondary = true) {
                launchPlayerScreen(Intent(this, StoreActivity::class.java).putExtra(StoreActivity.EXTRA_PROMOTED_COSMETIC_ID, ids.getString(0)))
            }
        }
        action(body, "Explore the Shop", secondary = true) { launchPlayerScreen(Intent(this, StoreActivity::class.java)) }
        action(body, "Share & friendship rewards", secondary = true) { launchPlayerScreen(Intent(this, com.rekixo.ludopaws.feature.referrals.ReferralActivity::class.java)) }
    }
    private fun load() {
        if (!resumed || busy) return
        perform(null)
    }
    private fun perform(request: JSONObject?, wheel: RewardWheelView? = null) {
        if (busy || !resumed) return
        val expectedOwner = repository.currentSession()?.accountId
        if (request != null && (owner == null || owner != expectedOwner)) {
            snapshot = null; owner = null; render(); message.text = "Account changed. Refresh Rewards."; return
        }
        busy = true
        val expectedGeneration = generation
        if (wheel == null) render() else message.text = "Preparing your free spin…"
        executor.execute {
            val result = runCatching {
                val session = repository.ensureGuestSession()
                if (expectedOwner != null) check(session.accountId == expectedOwner) { "Account changed. Reopen Rewards." }
                val response = repository.engagementRewards(request, session.accountId)
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
                    rewardsNotReleased = false
                    owner = account; snapshot = response
                    prefs.edit().putInt("$account:milestoneTier", response.getJSONObject("rewards").optInt("milestoneTier")).apply()
                    val stillPending = runCatching { PendingAdRewardStore(this).forAccount(account).any { it.dailyRewardDay != null && it.earned } }.getOrDefault(false)
                    if (stillPending && confirmationRefreshes < 5) { handler.removeCallbacks(refresh); handler.postDelayed(refresh, 2000L) }
                    if (request != null) prefs.edit().remove("$account:${request.optString("action")}").apply()
                    val receipt = response.optJSONObject("receipt")
                    if (wheel != null && receipt != null && wheel.isAttachedToWindow) {
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
                    val dayChanged = request != null && error is com.rekixo.ludopaws.feature.account.data.AccountApiException && error.code == "REWARD_DAY_CHANGED"
                    if (dayChanged) {
                        prefs.edit().remove("${expectedOwner}:${request?.optString("action")}").apply()
                        snapshot = null
                    }
                    if (request?.optString("action") == "collection" && error is com.rekixo.ludopaws.feature.account.data.AccountApiException && error.status == 409) prefs.edit().remove("${expectedOwner}:collection").apply()
                    rewardsNotReleased = error is com.rekixo.ludopaws.feature.account.data.AccountApiException && error.status == 404
                    if (rewardsNotReleased) snapshot = null
                    render()
                    message.text = when {
                        rewardsNotReleased -> "Daily gifts and spins aren't active yet. You can explore Challenges now."
                        error is java.io.IOException -> "Couldn't connect. Your game is ready; try Refresh when you're online."
                        error is com.rekixo.ludopaws.feature.account.data.AccountApiException && error.status in setOf(502, 503, 504) ->
                            "Gifts are taking a little break. You can still play; try Refresh later."
                        else -> error.message ?: "Couldn't check your gifts. You can still play; try Refresh later."
                    }
                    if (dayChanged) load()
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
        val quoteOwner = owner ?: return
        val cost = collection.getInt("price")
        PlywoodAlertDialogBuilder(this).setTitle(collection.getString("title"))
            .setMessage("Unlock the missing items for $cost Gems? Owned items stay yours.")
            .setNegativeButton("Later", null).setPositiveButton("Unlock") { _, _ ->
                if (quoteOwner != owner || repository.currentSession()?.accountId != quoteOwner) {
                    snapshot = null; render(); message.text = "Account changed. Refresh the collection before purchasing."; return@setPositiveButton
                }
                val account = quoteOwner
                val key = "$account:collection"
                val request = prefs.getString(key, null)?.let(::JSONObject) ?: JSONObject().put("action", "collection")
                    .put("collectionId", collection.getString("id")).put("expectedPrice", cost).put("clientRequestId", UUID.randomUUID().toString())
                if (request.optString("collectionId") != collection.getString("id")) { message.text = "Retry the pending collection first."; return@setPositiveButton }
                if (prefs.edit().putString(key, request.toString()).commit()) perform(request)
            }.show()
    }
    private fun bonus() {
        if (busy || !resumed || ad?.isBusy == true) return
        val expectedOwner = owner ?: return
        if (repository.currentSession()?.accountId != expectedOwner) { snapshot = null; render(); return }
        busy = true; message.text = "Preparing optional +2 Gems ad…"
        val expectedGeneration = generation
        executor.execute {
            val result = runCatching {
                check(BuildConfig.LUDOPROOF_ADS_ENABLED && repository.storeStatus().optBoolean("rewardedAdsV2Available")) { "No rewarded ad is available. Free gifts stay yours." }
                val session = repository.ensureGuestSession()
                check(session.accountId == expectedOwner) { "Account changed. Refresh Rewards." }
                val ticket = repository.engagementRewards(JSONObject().put("action", "bonus-ticket"), expectedOwner).getJSONObject("receipt")
                BonusRequest(session.accountId, ticket.getString("ticket"), UUID.randomUUID().toString(), ticket.getLong("day"))
            }
            handler.post {
                if (!resumed || generation != expectedGeneration || isFinishing || isDestroyed) return@post
                busy = false
                result.onSuccess { (account, ticket, requestId, day) ->
                    if (repository.currentSession()?.accountId != account) { snapshot = null; render(); return@onSuccess }
                    adsAvailable = true
                    confirmationRefreshes = 0
                    val controller = ad ?: RewardedAdsController(activity = this, enabled = { adsAvailable }, adUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID, onMessage = { message.text = it }, canPresent = { resumed && !isFinishing && !isDestroyed }).also { ad = it }
                    val pending = PendingAdReward(account, requestId, null, dailyRewardDay = day)
                    controller.showRewardedAd(account, JSONObject().put("kind", "gems").put("dailyTicket", ticket).put("requestId", requestId).toString(),
                        onBeforeShow = { if (resumed && repository.currentSession()?.accountId == account) { PendingAdRewardStore(this).put(pending); true } else false },
                        onFinished = { earned ->
                            if (!earned) runCatching { PendingAdRewardStore(this).put(pending.copy(abandoned = true)) }
                            if (resumed) { render(); handler.postDelayed(refresh, 1000L) }
                        }, onRewardEarned = {
                            runCatching { PendingAdRewardStore(this).put(pending.copy(earned = true)) }
                            GrowthMetrics.increment(this, "rewarded_earned")
                            if (resumed) handler.postDelayed(refresh, 1500L)
                        })
                }.onFailure { message.text = it.message ?: "Ad unavailable. Free rewards are unchanged." }
            }
        }
    }
    private fun chooseQuickMatch() {
        PlywoodAlertDialogBuilder(this).setTitle("Quick Match · two paws per player")
            .setMessage("Race two paws each. Share this phone with a friend, or play with a Robot. Same fair dice, no entry fee or Gem prize.")
            .setNegativeButton("Later", null)
            .setNeutralButton("Pass & Play") { _, _ -> quick(false) }
            .setPositiveButton("Play with Robot") { _, _ -> quick(true) }.show()
    }
    private fun quick(computer: Boolean) {
        launchPlayerScreen(Intent(this, OfflineGameActivity::class.java).putExtra(OfflineGameActivity.EXTRA_GAME_MODE,
            if (computer) "COMPUTER" else "PASS_AND_PLAY").putExtra("QUICK_MATCH", true))
    }
}
