package com.rekixo.ludopaws.feature.rewards

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Button
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
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.core.ui.components.appIconActionButton
import com.rekixo.ludopaws.ui.home.HomeIconKind
import com.rekixo.ludopaws.ui.home.HomeIconView
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.growth.AutomaticPromptGate
import com.rekixo.ludopaws.feature.growth.EngagementStore
import com.rekixo.ludopaws.feature.growth.PawsBadgeView
import com.rekixo.ludopaws.feature.growth.WeeklyChallengeStore
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
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
    private var rewardsUnavailable = false
    private var generation = 0
    private var giftButton: Button? = null
    private var spinButton: Button? = null
    private var claimError: Pair<String, String>? = null
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
    private fun action(parent: LinearLayout, label: String, enabled: Boolean = true, secondary: Boolean = false, callback: () -> Unit): Button =
        GrowthUi.button(this, label, primary = !secondary, enabled = enabled && !busy, action = { if (!busy) callback() })
            .also { add(parent, it) }
    private fun card(title: String, subtitle: String? = null): LinearLayout = GrowthUi.card(this).also {
        GrowthUi.heading(it, title, subtitle)
        add(body, it, 14)
    }
    private fun timestamp(value: Long) = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(value))
    private fun render() {
        val previousScroll = page.scroll.scrollY
        body.removeAllViews()
        giftButton = null
        spinButton = null
        if (owner != null && repository.currentSession()?.accountId != owner) {
            owner = null; snapshot = null; claimError = null
        }
        val rewards = snapshot?.optJSONObject("rewards")
        val wallet = snapshot?.optJSONObject("wallet")
        val walletRow = GrowthUi.card(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }
        val balance = wallet?.optInt("balance") ?: GemWalletStore(this).balance()
        walletRow.addView(GemClusterView(this, 1).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(30), dp(30)))
        walletRow.addView(copy("$balance Gems", 16f, true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        add(body, walletRow, 0)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        listOf("Gifts", "Challenges", "Collections").forEachIndexed { index, title ->
            if (rewardsUnavailable && index != 1) return@forEachIndexed
            val tab = GrowthUi.button(this, title, primary = selectedTab == index, enabled = !busy && ad?.isBusy != true) {
                if (!busy && ad?.isBusy != true) { selectedTab = index; page.scroll.scrollTo(0, 0); render() }
            }.apply { textSize = 13f; isSelected = selectedTab == index; contentDescription = "$title, " + if (selectedTab == index) "selected" else "tab" }
            tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply { if (tabs.childCount > 0) marginStart = dp(5) })
        }
        if (tabs.childCount > 1) add(body, tabs, 10)
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
        handler.removeCallbacks(rollover)
        if (resumed) {
            val now = System.currentTimeMillis()
            val nextDay = (now / AutomaticPromptGate.DAY + 1) * AutomaticPromptGate.DAY
            handler.postDelayed(rollover, (nextDay - now).coerceAtLeast(1000L))
        }
        page.scroll.post { if (!isDestroyed) page.scroll.scrollTo(0, previousScroll) }
    }
    private fun gifts(rewards: JSONObject?) {
        if (rewards == null) {
            if (!busy) action(body, "Refresh gifts", secondary = true) { load() }
            return
        }
        if (resumed) {
            val discovery = getSharedPreferences(HomeRewardsController.DISCOVERY_PREFS, MODE_PRIVATE)
            if (!discovery.getBoolean(HomeRewardsController.GIFTS_SEEN, false)) {
                discovery.edit().putBoolean(HomeRewardsController.GIFTS_SEEN, true).apply()
            }
        }
        val giftAmount = rewards.optInt("dailyGems", 3)
        val giftClaimed = rewards.optBoolean("dailyClaimed")
        val spinClaimed = rewards.optBoolean("spinClaimed")
        val spinAmount = rewards.optInt("spinGems")
        val gift = GrowthUi.card(this).apply { setPadding(dp(10), dp(13), dp(10), dp(12)) }
        val spin = GrowthUi.card(this).apply { setPadding(dp(10), dp(13), dp(10), dp(12)) }
        val pair = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            isBaselineAligned = false
            addView(gift, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
            addView(spin, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        }
        add(body, pair, 14)
        fun giftHeading(card: LinearLayout, title: String, detail: String) {
            add(card, copy(title, 18f, true).apply {
                gravity = Gravity.CENTER
                isAccessibilityHeading = true
            }, 0)
            add(card, copy(detail, 12f).apply { gravity = Gravity.CENTER }, 3)
        }
        giftHeading(gift, "Daily Gift", "$giftAmount Gems")
        giftHeading(spin, "Free Spin", "Win 1–5 Gems")
        val chest = DailyGiftChestView(this, opened = giftClaimed).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val wheel = RewardWheelView(this).apply {
            contentDescription = "Paw Treasure Wheel. Possible prizes are 1, 2, 3 or 5 Gems."
        }
        if (spinClaimed) wheel.stopAt(spinAmount, animated = false) {}
        gift.addView(chest, LinearLayout.LayoutParams(-1, dp(128)).apply { topMargin = dp(4) })
        spin.addView(wheel, LinearLayout.LayoutParams(-1, dp(128)).apply { topMargin = dp(4) })
        giftButton = action(gift, if (giftClaimed) "+$giftAmount Gems saved" else "Collect gift",
            !giftClaimed) { claim("daily", chest = chest) }.apply { compactGiftAction() }
        spinButton = action(spin, if (spinClaimed) "+$spinAmount Gems saved" else "Spin for free",
            !spinClaimed) { claim("spin", wheel = wheel) }.apply { compactGiftAction() }
        claimError?.let { (action, error) ->
            add(if (action == "daily") gift else spin, copy(error, 12f))
        }
        val info = GrowthUi.card(this)
        add(info, copy("Next gifts · ${timestamp(rewards.getLong("resetsAt"))}", 12f, true), 0)
        action(info, "Spin odds", secondary = true) {
            PlywoodAlertDialogBuilder(this).setTitle("Free spin")
                .setMessage("1 Gem · 50%\n2 Gems · 30%\n3 Gems · 15%\n5 Gems · 5%\n\nEvery free spin wins Gems.")
                .setPositiveButton("Got it", null).show()
        }
        add(body, info, 10)
        val total = rewards.optInt("dailyClaims")
        val tier = rewards.optInt("milestoneTier")
        val earnedTier = total > 0 && total % 7 == 0
        val badge = card("Garden Friend", "A badge tier every 7 gifts")
        if (tier > 0) add(badge, GrowthUi.pill(this, "TIER $tier EARNED", green = true))
        badge.addView(RewardPawTrailView(this, if (earnedTier) 7 else total % 7),
            LinearLayout.LayoutParams(-1, dp(34)).apply { topMargin = dp(7) })
        add(badge, copy(if (earnedTier) "Badge tier $tier complete" else "${total % 7} of 7 toward your next tier", 13f, true), 5)
        val bonusPending = owner?.let { account -> runCatching { PendingAdRewardStore(this).forAccount(account).any {
            it.dailyRewardDay == rewards.optLong("day") && !it.abandoned
        } }.getOrDefault(true) } ?: false
        if (BuildConfig.LUDOPROOF_ADS_ENABLED && BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.isNotBlank()) {
            val extra = card("Daily ad bonus")
            action(extra, when {
                rewards.optBoolean("bonusClaimed") -> "Collected"
                bonusPending -> "Checking reward…"
                else -> "Watch ad · +${rewards.optInt("bonusGems", 2)} Gems"
            }, !rewards.optBoolean("bonusClaimed") && !bonusPending, secondary = true) { bonus() }
        }
    }
    private fun Button.compactGiftAction() {
        textSize = 13f
        setPadding(dp(5), dp(8), dp(5), dp(8))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun challenges() {
        val today = EngagementStore(this).todayMatches().coerceAtMost(1)
        val weekly = WeeklyChallengeStore(this).status()
        val paw = CharacterSelectionStore(this).load().characterId
        val dailyArt = FrameLayout(this).apply {
            addView(GrowthUi.portrait(this@RewardsActivity, paw), FrameLayout.LayoutParams(dp(72), dp(80), Gravity.TOP or Gravity.START))
            GrowthUi.preview(this@RewardsActivity, StoreCosmeticCatalog.DEFAULT_DICE)?.let {
                addView(it, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.BOTTOM or Gravity.END))
            }
        }
        challengeCard("Daily challenge", "Finish 1 match", today, 1, dailyArt)
        challengeCard(weekly.title, weekly.goal, weekly.progress, weekly.target,
            PawsBadgeView(this, weekly.complete), "Weekly · Ends ${timestamp(weekly.endsAt)}") {
            com.rekixo.ludopaws.feature.sharing.MatchShareCard.weekly(this, weekly.title, weekly.complete)
        }
        if (today == 0 || !weekly.complete) {
            action(body, "Play Pass & Play") { openGameMode(GameMode.PASS_AND_PLAY) }
        }
        val shortcuts = LinearLayout(this).apply { isBaselineAligned = false }
        listOf<Pair<String, () -> Unit>>(
            "Quick Match" to { chooseQuickMatch() },
            "Friends" to { openGameMode(GameMode.FRIENDS) },
        ).forEach { (title, click) ->
            shortcuts.addView(GrowthUi.button(this, title, primary = false, enabled = !busy, action = click),
                LinearLayout.LayoutParams(0, -2, 1f).apply { if (shortcuts.childCount > 0) marginStart = dp(6) })
        }
        add(body, shortcuts)
        val profile = ProfileStore(this).snapshot()
        val palsUsed = profile.characterMatches.count { it.value > 0 }.coerceAtMost(3)
        val topPal = (profile.characterMatches.values.maxOrNull() ?: 0).coerceAtMost(5)
        val mastery = card("Paw mastery")
        add(mastery, copy("$palsUsed/3 pals played  ·  $topPal/5 matches with one pal", 12f), 4)
        action(mastery, "My badges & pals", secondary = true) {
            launchPlayerScreen(Intent(this, com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity::class.java))
        }
    }

    private fun challengeCard(
        title: String, goal: String, progress: Int, target: Int, art: View, deadline: String? = null,
        share: (() -> Unit)? = null,
    ) {
        val panel = GrowthUi.card(this)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        GrowthUi.heading(info, title)
        add(info, copy(goal, 14f), 2)
        deadline?.let { add(info, copy(it, 11f), 2) }
        header.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val artFrame = FrameLayout(this).apply {
            background = recessedPlywoodBackground(this@RewardsActivity, 18f)
            addView(art, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        header.addView(artFrame, LinearLayout.LayoutParams(dp(96), dp(96)).apply { marginStart = dp(6) })
        add(panel, header, 0)
        val track = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        track.addView(RewardPawTrailView(this, progress, target, "$title: $progress of $target").apply {
            // The adjacent counter announces the same state.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(if (target == 1) 38 else 95), dp(30)))
        track.addView(copy(if (progress >= target) "Complete ✓" else "$progress / $target", 14f, true).apply {
            contentDescription = if (progress >= target) "$title complete" else "$title: $progress of $target"
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        if (share != null) {
            val shareIcon = HomeIconView(this).apply { kind = HomeIconKind.SHARE }
            track.addView(appIconActionButton(this, "Share challenge", shareIcon, sizeDp = 48) { if (!busy) share() },
                LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(6) })
        }
        add(panel, track, 2)
        add(body, panel, 10)
    }

    private fun collections(rewards: JSONObject?) {
        owner?.let { account ->
            val pending = prefs.getString("$account:collection", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (pending != null) action(body, "Recover purchase", secondary = true) { perform(pending) }
        }
        val rows = snapshot?.optJSONArray("collections")
        val featured = rewards?.optString("featuredCollectionId")
        val items = (0 until (rows?.length() ?: 0)).mapNotNull { rows?.optJSONObject(it) }
            .sortedByDescending { it.optString("id") == featured }
        var shown = 0
        for (item in items) {
            val ids = item.optJSONArray("items") ?: continue
            val missing = item.optJSONArray("missing") ?: continue
            val price = item.optInt("price", -1)
            val title = item.optString("title")
            if (title.isBlank() || price < 0 || ids.length() == 0 || item.optString("id").isBlank()) continue
            val itemIds = (0 until ids.length()).map { ids.optString(it) }
            val missingIds = (0 until missing.length()).map { missing.optString(it) }.toSet()
            if (!itemIds.containsAll(missingIds) || itemIds.distinct().size != itemIds.size ||
                (missingIds.isEmpty() && price != 0) || (missingIds.isNotEmpty() && price == 0)) continue
            val offers = itemIds.mapNotNull(StoreCosmeticCatalog::findShopItem)
            if (offers.size != itemIds.size) continue
            val artwork = itemIds.mapNotNull { GrowthUi.preview(this, it) }
            if (artwork.size != itemIds.size) continue

            val collection = card(title)
            val gallery = LinearLayout(this).apply {
                gravity = Gravity.TOP
                isBaselineAligned = false
                background = recessedPlywoodBackground(this@RewardsActivity, 16f)
                setPadding(dp(6), dp(5), dp(6), dp(8))
            }
            offers.forEachIndexed { index, offer ->
                val owned = offer.id !in missingIds
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    isFocusable = true
                    contentDescription = if (owned) "${offer.title}, owned. View in Shop" else "${offer.title}. View in Shop"
                    setOnClickListener {
                        if (!busy) launchPlayerScreen(Intent(this@RewardsActivity, StoreActivity::class.java)
                            .putExtra(StoreActivity.EXTRA_PROMOTED_COSMETIC_ID, offer.id))
                    }
                }
                tile.addView(artwork[index], LinearLayout.LayoutParams(-1, dp(92)))
                add(tile, copy(offer.title, 11f, true).apply {
                    gravity = Gravity.CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, 2)
                if (owned) add(tile, copy("✓ Owned", 10f, true).apply {
                    gravity = Gravity.CENTER; setTextColor(0xFF245332.toInt())
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, 1)
                gallery.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(5) })
            }
            add(collection, gallery, 8)
            if (missingIds.isEmpty()) {
                add(collection, GrowthUi.pill(this, "✓ Collection complete", green = true), 8)
            } else {
                action(collection, "Unlock ${missingIds.size} · $price Gems") { buy(item) }
            }
            shown++
        }
        if (shown == 0 && !busy) {
            if (snapshot != null) card("No collections available")
            action(body, "Refresh collections", secondary = true) { load() }
        }
        action(body, "Open Shop", secondary = true) { launchPlayerScreen(Intent(this, StoreActivity::class.java)) }
        action(body, "Friendship rewards", secondary = true) {
            launchPlayerScreen(Intent(this, com.rekixo.ludopaws.feature.referrals.ReferralActivity::class.java))
        }
    }
    private fun load() {
        if (!resumed || busy) return
        perform(null)
    }
    private fun perform(request: JSONObject?, wheel: RewardWheelView? = null, chest: DailyGiftChestView? = null) {
        if (busy || !resumed) return
        val expectedOwner = repository.currentSession()?.accountId
        if (request != null && (owner == null || owner != expectedOwner)) {
            snapshot = null; owner = null; render(); message.text = "Account changed. Refresh Rewards."; return
        }
        busy = true
        claimError = null
        val expectedGeneration = generation
        val claimAction = request?.optString("action").orEmpty()
        if (wheel == null && chest == null) render()
        else {
            giftButton?.isEnabled = false
            spinButton?.isEnabled = false
            (if (claimAction == "daily") giftButton else spinButton)?.text = "Checking…"
        }
        executor.execute {
            val result = runCatching {
                val session = repository.ensureGuestSession()
                if (expectedOwner != null) check(session.accountId == expectedOwner) { "Account changed. Reopen Rewards." }
                val pendingReceiptIds = runCatching {
                    PendingAdRewardStore(this).forAccount(session.accountId).map { it.requestId }
                }.getOrDefault(emptyList())
                val response = repository.engagementRewards(request, session.accountId, pendingReceiptIds)
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
                    rewardsUnavailable = false
                    owner = account; snapshot = response
                    prefs.edit().putInt("$account:milestoneTier", response.getJSONObject("rewards").optInt("milestoneTier")).apply()
                    val stillPending = runCatching { PendingAdRewardStore(this).forAccount(account).any { it.dailyRewardDay != null && it.earned } }.getOrDefault(false)
                    if (stillPending && confirmationRefreshes < 5) { handler.removeCallbacks(refresh); handler.postDelayed(refresh, 2000L) }
                    if (request != null) prefs.edit().remove("$account:${request.optString("action")}").apply()
                    val receipt = response.optJSONObject("receipt")
                    val claimConfirmed = receipt != null && receipt.optLong("day", -1L) ==
                        response.getJSONObject("rewards").getLong("day")
                    if (claimConfirmed && (wheel?.isAttachedToWindow == true || chest?.isAttachedToWindow == true)) {
                        busy = true
                        (if (wheel != null) spinButton else giftButton)?.text = if (wheel != null) "Spinning…" else "Opening…"
                        val animate = !GameSettingsStore(this).snapshot().reducedMotion &&
                            android.animation.ValueAnimator.areAnimatorsEnabled()
                        val finishReveal: () -> Unit = {
                            if (resumed && generation == expectedGeneration) {
                                busy = false
                                render()
                                val resultButton = if (wheel != null) spinButton else giftButton
                                resultButton?.let { it.announceForAccessibility(it.text) }
                            }
                        }
                        if (wheel != null) wheel.stopAt(checkNotNull(receipt).getInt("amount"), animate, finishReveal)
                        else chest?.open(animate, finishReveal)
                    } else {
                        render()
                        if (claimAction == "collection" && receipt != null) message.text = "Collection saved."
                    }
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
                    if (request == null && error is com.rekixo.ludopaws.feature.account.data.AccountApiException &&
                        error.status == 404 && error.code in setOf("NOT_FOUND", "HTTP_404")) {
                        rewardsUnavailable = true
                    }
                    if (rewardsUnavailable) {
                        snapshot = null
                        selectedTab = 1
                        page.scroll.scrollTo(0, 0)
                    }
                    val failure = when {
                        rewardsUnavailable -> ""
                        error is java.io.IOException -> "You're offline. Refresh when connected."
                        error is com.rekixo.ludopaws.feature.account.data.AccountApiException && error.status in setOf(502, 503, 504) ->
                            "Rewards unavailable. Try again later."
                        else -> error.message ?: "Couldn't load rewards. Try Refresh."
                    }
                    if (claimAction == "daily" || claimAction == "spin") claimError = claimAction to failure
                    render()
                    if (claimError == null) message.text = failure
                    if (dayChanged) load()
                }
            }
        }
    }
    private fun claim(action: String, wheel: RewardWheelView? = null, chest: DailyGiftChestView? = null) {
        val day = snapshot?.optJSONObject("rewards")?.optLong("day") ?: return
        val account = owner ?: return
        if (repository.currentSession()?.accountId != account) { snapshot = null; render(); return }
        val key = "$account:$action"
        val request = prefs.getString(key, null)?.let { JSONObject(it) }?.takeIf { it.optLong("day") == day }
            ?: JSONObject().put("action", action).put("day", day).put("clientRequestId", UUID.randomUUID().toString())
        if (!prefs.edit().putString(key, request.toString()).commit()) { message.text = "Could not save reward recovery. Retry."; return }
        perform(request, wheel, chest)
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
