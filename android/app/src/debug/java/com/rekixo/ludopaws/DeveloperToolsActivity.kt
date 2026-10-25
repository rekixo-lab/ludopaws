package com.rekixo.ludopaws

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.UserMessagingPlatform
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity
import com.rekixo.ludopaws.feature.store.data.ads.FamilyAdsPrivacy
import com.rekixo.ludopaws.feature.store.data.billing.StorePurchaseVerifier
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import java.util.UUID
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Debug source set only. Fixtures never enter an engine, wallet, saved match or network request. */
class DeveloperToolsActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var board: LudoPawsReactiveBoardView? = null
    private var scenario = "Yard"
    private var perspective = "YELLOW"
    private var fixtureId = ""
    private var state: MatchSnapshot? = null
    private var adBusy = false
    private var foreground = false
    private var screenGeneration = 0
    private val colors = listOf("RED", "GREEN", "YELLOW", "BLUE")
    private val characters = listOf("dog", "goat_lumi", "donkey", "cat")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        showPanel()
    }

    private fun beginScreen(title: String) {
        handler.removeCallbacksAndMessages(null)
        screenGeneration++
        board = null
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(28))
            setBackgroundColor(0xFF152333.toInt())
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
        label(title, 22f)
    }

    private fun showPanel() {
        beginScreen("Developer tools")
        action("Close") { finish() }
        label("${BuildConfig.APPLICATION_ID}\n${BuildConfig.VERSION_NAME} · debug\nAPI: ${BuildConfig.LUDOPROOF_API_BASE_URL}\nSigning SHA-1: ${signingFingerprint()}")
        label(buildString {
            appendLine("Billing: ${onOff(BuildConfig.LUDOPROOF_BILLING_ENABLED)}")
            appendLine("Rewarded ads: ${onOff(BuildConfig.LUDOPROOF_ADS_ENABLED)}")
            appendLine("Interstitial ads: ${onOff(BuildConfig.LUDOPROOF_INTERSTITIAL_ADS_ENABLED)}")
            appendLine("Google linking: ${if (BuildConfig.GOOGLE_SIGN_IN_WEB_CLIENT_ID.isBlank()) "MISSING CLIENT ID" else "configured; OAuth certificate must match"}")
            appendLine("Play Games: ${if (BuildConfig.PLAY_GAMES_PROJECT_ID == "0") "MISSING PROJECT ID" else "configured; Android credential/tester required"}")
            appendLine("Family mode: ${onOff(BuildConfig.LUDOPAWS_FAMILY_MODE)}")
            append("QA isolation: ${onOff(BuildConfig.LUDOPAWS_QA_BUILD)}")
        })
        status = label("Server status: not checked")
        action("Check server feature availability") { checkServer() }
        action("Open real Store") { startActivity(Intent(this, StoreActivity::class.java)) }
        action("Open Profile / Google / Play Games") { startActivity(Intent(this, ProfileActivity::class.java)) }
        label("Billing needs the original package and a Play license tester account. The .dev app has separate local data. Previews below do not grant coins, purchases or match results.")
        action("Board scenarios / winner animation") { showBoard("Yard") }
        CharacterRegistry.all.forEach { character ->
            action("3D: ${character.definition.character.displayName}") {
                startActivity(Intent(this, CharacterPreviewActivity::class.java)
                    .putExtra("character_id", character.id))
            }
        }
        label("Google demo ads only. Reward callbacks here never change your wallet. Store reward verification must be checked separately.")
        action("Show demo rewarded ad") { demoAd(true) }
        action("Show demo interstitial ad") { demoAd(false) }
    }

    private fun checkServer() {
        val target = status
        target.text = "Checking server…"
        worker.execute {
            val message = runCatching {
                val response = StorePurchaseVerifier().status()
                "Server Billing: ${onOff(response.optBoolean("billingAvailable"))}\n" +
                    "Server rewarded ads: ${onOff(response.optBoolean("rewardedAdsAvailable"))}\n" +
                    "Availability still depends on the build switches above and account/configuration."
            }.getOrElse { "Server check failed (${it.javaClass.simpleName}). Check API and connection." }
            runOnUiThread { if (!isFinishing && !isDestroyed) target.text = message }
        }
    }

    private fun showBoard(next: String) {
        scenario = next
        beginScreen("Board preview · $scenario")
        action("Back to developer tools") { showPanel() }
        label("Local visual fixture · no saved-game, account or leaderboard changes")
        val side = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(200))
        val preview = LudoPawsReactiveBoardView(this)
        board = preview
        content.addView(preview, LinearLayout.LayoutParams(-1, side))
        fixtureId = "preview-${UUID.randomUUID()}"
        val initial = fixture(complete = false)
        state = initial
        preview.onTokenSelected = { index ->
            label("Tapped token ${index + 1}; input works. This is a visual fixture, not a live match.")
        }
        preview.bind(initial, "preview-2", perspective, characters)
        if (scenario == "Winner") {
            // A real incomplete -> complete transition is needed to replay the door/party sequence.
            handler.postDelayed({
                if (board === preview) {
                    state = fixture(complete = true)
                    preview.bind(state, "preview-2", perspective, characters)
                }
            }, 1800L)
        }
        action("Yard") { showBoard("Yard") }
        action("Road stacks / legal tap hints") { showBoard("Stacks") }
        action("Replay all four finishes / ranks") { showBoard("Winner") }
        action("Rotate perspective ($perspective)") {
            perspective = colors[(colors.indexOf(perspective) + 1) % colors.size]
            showBoard(scenario)
        }
    }

    private fun fixture(complete: Boolean): MatchSnapshot {
        val players = colors.mapIndexed { seat, color ->
            val tokens = when (scenario) {
                "Winner" -> List(4) { if (complete) LudoPathEncoding.HOME_POSITION else LudoPathEncoding.LAST_HOME_LANE_POSITION }
                "Stacks" -> List(4) { 8 }
                else -> List(4) { -1 }
            }
            PlayerSnapshot("preview-$seat", "Preview ${seat + 1}", color, seat, tokens,
                characterId = characters[seat])
        }
        return MatchSnapshot(
            matchId = fixtureId, status = if (complete) "FINISHED" else "ACTIVE",
            hostPlayerId = "preview-2", targetPlayerCount = 4, players = players,
            turnSeat = 2, randomEventIndex = 0,
            pendingRoll = if (complete) null else PendingRollSnapshot(
                "RESOLVED", 2, 0, null, null, null, null, null, null, 6, setOf(0, 1, 2, 3)),
            winnerPlayerId = if (complete) "preview-2" else null,
            rulesetId = "ludoproof-standard-v5", history = emptyList(),
            finishOrderPlayerIds = if (complete) listOf("preview-2", "preview-3", "preview-0", "preview-1") else emptyList(),
        )
    }

    private fun demoAd(rewarded: Boolean) {
        if (adBusy) return
        adBusy = true
        val generation = screenGeneration
        status.text = "Checking ad privacy before loading Google demo ad…"
        // This path is compiled only into debug APKs and uses only Google's demo unit IDs.
        FamilyAdsPrivacy.configureBeforeInitialization()
        val consent = UserMessagingPlatform.getConsentInformation(this)
        consent.requestConsentInfoUpdate(this, FamilyAdsPrivacy.consentParameters(), {
            if (!adHostReady(generation)) { adBusy = false; return@requestConsentInfoUpdate }
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                if (consent.canRequestAds() && adHostReady(generation)) loadDemoAd(rewarded, generation)
                else adDone(generation, "Ad privacy is not ready; demo ad was not requested.")
            }
        }, { error ->
            if (consent.canRequestAds() && adHostReady(generation)) loadDemoAd(rewarded, generation)
            else adDone(generation, "Ad privacy setup unavailable (${error.errorCode}); check the AdMob app's UMP configuration.")
        })
    }

    private fun loadDemoAd(rewarded: Boolean, generation: Int) {
        status.text = "Loading Google demo ad…"
        MobileAds.initialize(this) {
            runOnUiThread {
                if (!adHostReady(generation)) { adBusy = false; return@runOnUiThread }
                if (rewarded) {
                    RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", FamilyAdsPrivacy.request(),
                        object : RewardedAdLoadCallback() {
                            override fun onAdFailedToLoad(error: LoadAdError) { adDone(generation, "Demo ad load failed: ${error.code}") }
                            override fun onAdLoaded(ad: RewardedAd) {
                                if (!adHostReady(generation)) { adBusy = false; return }
                                ad.fullScreenContentCallback = adCallback(generation)
                                ad.show(this@DeveloperToolsActivity) {
                                    status.text = "Demo reward callback received. No wallet changes."
                                }
                            }
                        })
                } else {
                    InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", FamilyAdsPrivacy.request(),
                        object : InterstitialAdLoadCallback() {
                            override fun onAdFailedToLoad(error: LoadAdError) { adDone(generation, "Demo ad load failed: ${error.code}") }
                            override fun onAdLoaded(ad: InterstitialAd) {
                                if (!adHostReady(generation)) { adBusy = false; return }
                                ad.fullScreenContentCallback = adCallback(generation)
                                ad.show(this@DeveloperToolsActivity)
                            }
                        })
                }
            }
        }
    }

    private fun adHostReady(generation: Int) =
        !isFinishing && !isDestroyed && foreground && generation == screenGeneration

    private fun adDone(generation: Int, message: String) {
        adBusy = false
        if (!isFinishing && !isDestroyed && generation == screenGeneration) status.text = message
    }

    private fun adCallback(generation: Int) = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() { adDone(generation, "Demo ad closed. No wallet changes.") }
        override fun onAdFailedToShowFullScreenContent(error: AdError) { adDone(generation, "Demo ad could not show: ${error.code}") }
    }

    private fun label(value: String, size: Float = 14f) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(0, dp(10), 0, dp(10))
        content.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    private fun action(title: String, callback: () -> Unit) {
        content.addView(Button(this).apply {
            text = title
            isAllCaps = false
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setOnClickListener { callback() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun onOff(enabled: Boolean) = if (enabled) "ON" else "OFF"
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun signingFingerprint(): String = runCatching {
        val certificate = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo!!.apkContentsSigners.first().toByteArray()
        MessageDigest.getInstance("SHA-1").digest(certificate).joinToString(":") { "%02X".format(it) }
    }.getOrDefault("unavailable")

    override fun onStart() {
        super.onStart()
        foreground = true
    }

    override fun onStop() {
        foreground = false
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }
}
