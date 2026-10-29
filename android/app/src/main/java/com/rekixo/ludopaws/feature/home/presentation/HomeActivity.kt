package com.rekixo.ludopaws

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.rekixo.ludopaws.feature.startup.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.content.Intent
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileGameMode
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileMatchSource
import com.rekixo.ludopaws.feature.online.retryPendingRemoteForfeits
import com.rekixo.ludopaws.ui.home.*

class HomeActivity : Activity() {
    internal lateinit var connectivityText:
        TextView
    internal var homeProfileNameText:
        TextView? = null
    internal var homeProfileLevelText:
        TextView? = null
    internal var homeProfileXpFill:
        View? = null
    internal var homeGemBalanceText:
        TextView? = null
    internal lateinit var connectivityMonitor:
        ConnectivityMonitor

    private var promotionCarousel: HomePromotionCarousel? = null
    private var referralPopupVisible = false
    private var promotionsStarted = false
    private val referralPromotions by lazy {
        com.rekixo.ludopaws.feature.referrals.ReferralPromotionController(this,
            uiAvailable = { resumed && homeShown && com.rekixo.ludopaws.feature.safety.FamilyAgeScreen.hasChoice(this) },
            popupVisibility = { visible ->
                referralPopupVisible = visible
                promotionCarousel?.setActive(resumed && !visible)
            })
    }

    private val startupHandler = Handler(Looper.getMainLooper())
    private val startupSession get() = (application as LudoProofApplication).startupSession
    private val startupPlayGames get() = (application as LudoProofApplication).startupPlayGames
    private var splash: GameStartupSplashView? = null
    private var heroBitmap: Bitmap? = null
    private var localReady = false
    private var preparationFailed = false
    private var preparationGeneration = 0
    private var startedAt = 0L
    private var splashDrawnAt = 0L
    private var launchGate: ViewTreeObserver.OnPreDrawListener? = null
    private val releaseLaunchGate = Runnable { window.decorView.invalidate() }
    private val splashMinimum = Runnable { maybeShowHome() }
    private companion object {
        const val SCREEN_MINIMUM_MS = 1_000L
    }
    private var homeShown = false
    private var foreground = false
    private var resumed = false
    private var lastOnline: Boolean? = null
    private var remoteExitSaved = true
    private var startupBanner: TextView? = null
    private var homeRoot: FrameLayout? = null
    private var privacyBlocker: View? = null
    private val startupObserver: (StartupAccountSnapshot) -> Unit = { snapshot ->
        if (foreground && !isFinishing && !isDestroyed) {
            updateSplash()
            updateStartupBanner(snapshot)
            maybeShowHome()
        }
    }
    private val privacyReady: () -> Unit = {
        syncHomePrivacyGate()
        if (resumed && homeShown && !isFinishing && !isDestroyed) {
            startupPlayGames.refresh(this)
            resumeHomePromotions()
        }
    }

    private fun updateSplash() {
        if (!preparationFailed) splash?.update(startupSession.snapshot, localReady)
    }
    private var hudRefreshQueued = false
    private val observedStorePrefs by lazy {
        getSharedPreferences(com.rekixo.ludopaws.feature.store.data.local.StorePreferences.PREFS_NAME, MODE_PRIVATE)
    }
    private val walletChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { if (foreground && resumed) scheduleHomeHudRefresh() }
    }
    private val deferredHomePreparation = Runnable {
            if (resumed && homeShown && !isFinishing && !isDestroyed) {
                com.rekixo.ludopaws.feature.referrals.ReferralRepository(this).refreshAttribution()
                val selected = com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore(this).load()
                com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry.prewarm(
                    listOf(selected.characterId, "dog", "goat", "duck", "cat"))
                com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback.preloadMovementEventSfx(this)
            }
    }
    private fun scheduleHomeHudRefresh() {
        if (!homeShown || hudRefreshQueued || isFinishing || isDestroyed) return
        hudRefreshQueued = true
        window.decorView.postOnAnimation {
            hudRefreshQueued = false
            if (foreground && homeShown && !isFinishing && !isDestroyed) {
                refreshHomeProfileSummary()
                refreshHomeGemBalance()
            }
        }
    }
    private val grace = Runnable { maybeShowHome() }
    private val watchdog = Runnable {
        if (!homeShown && StartupReadiness.localTimedOut(localReady, elapsedStartup())) {
            preparationGeneration += 1
            preparationFailed = true
            splash?.showPreparationError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        if (intent?.action == Intent.ACTION_VIEW && intent?.data?.host == "api.ludopaws.rekixo.com") {
            android.widget.Toast.makeText(this, "Welcome! Only a new Play installation from an invitation can qualify. Your inviter receives the reward after verified play.", android.widget.Toast.LENGTH_LONG).show()
        }
        heroBitmap = (application as LudoProofApplication).startupArtwork
        // A restored Activity starts new artwork work. Reusing an old elapsed
        // timestamp could immediately trip its watchdog after process death.
        startedAt = SystemClock.elapsedRealtime()
        connectivityMonitor = ConnectivityMonitor(this) { online ->
            runOnUiThread {
                if (!foreground || isFinishing || isDestroyed) return@runOnUiThread
                if (lastOnline != online) {
                    lastOnline = online
                    startupSession.refresh(StartupReadiness.canBootstrapAccount(online, remoteExitSaved))
                    if (homeShown && resumed) resumeHomePrivacy()
                }
                if (::connectivityText.isInitialized) connectivityText.text = if (online) "● ONLINE" else "● OFFLINE"
                updateStartupBanner(startupSession.snapshot)
            }
        }
        if (savedInstanceState?.getBoolean("startup_home_shown", false) == true || startupSession.hasShownHome) {
            localReady = true
            showHome()
        } else {
            holdNativeLaunchBriefly()
            showPreparation()
        }
    }

    private fun holdNativeLaunchBriefly() {
        val decor = window.decorView
        val readyAt = SystemClock.elapsedRealtime() + SCREEN_MINIMUM_MS
        val gate = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (SystemClock.elapsedRealtime() < readyAt) return false
                if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnPreDrawListener(this)
                launchGate = null
                return true
            }
        }
        launchGate = gate
        decor.viewTreeObserver.addOnPreDrawListener(gate)
        // The UI thread remains free for real asset/session preparation.
        startupHandler.postDelayed(releaseLaunchGate, SCREEN_MINIMUM_MS)
    }

    private fun elapsedStartup() = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)

    private fun showPreparation() {
        preparationFailed = false
        localReady = false
        splashDrawnAt = 0L
        startupHandler.removeCallbacks(splashMinimum)
        splash = GameStartupSplashView(this, retry = {
            startedAt = SystemClock.elapsedRealtime()
            showPreparation()
            armStartupTimers()
            startupSession.refresh(StartupReadiness.canBootstrapAccount(connectivityMonitor.isOnline(), remoteExitSaved))
        }, exit = { finish() }, firstFrame = {
            splashDrawnAt = SystemClock.elapsedRealtime()
            maybeShowHome()
        }).also { setContentView(it) }
        val generation = ++preparationGeneration
        heroBitmap?.let {
            localReady = true
            updateSplash()
            maybeShowHome()
            return
        }
        (application as LudoProofApplication).startupArtworkExecutor.execute {
            val bitmap = try { BitmapFactory.decodeResource(resources, R.drawable.ludo_paws_home_hero) } catch (_: Exception) { null }
            startupHandler.post {
                if (isDestroyed || isFinishing || generation != preparationGeneration) return@post
                if (bitmap == null) {
                    preparationFailed = true
                    if (foreground) splash?.showPreparationError()
                } else {
                    heroBitmap = bitmap
                    (application as LudoProofApplication).startupArtwork = bitmap
                    localReady = true
                    startupHandler.removeCallbacks(watchdog)
                    if (foreground) {
                        updateSplash()
                    }
                    maybeShowHome()
                }
            }
        }
    }

    private fun armStartupTimers() {
        startupHandler.removeCallbacks(grace)
        startupHandler.removeCallbacks(watchdog)
        if (homeShown) return
        startupHandler.postDelayed(grace, (StartupReadiness.ACCOUNT_GRACE_MS - elapsedStartup()).coerceAtLeast(0L))
        startupHandler.postDelayed(watchdog, (StartupReadiness.LOCAL_WATCHDOG_MS - elapsedStartup()).coerceAtLeast(0L))
    }

    private fun maybeShowHome() {
        if (!foreground || homeShown || preparationFailed || isFinishing || isDestroyed) return
        if (splashDrawnAt == 0L) return
        val remaining = SCREEN_MINIMUM_MS - (SystemClock.elapsedRealtime() - splashDrawnAt)
        if (remaining > 0L) {
            startupHandler.removeCallbacks(splashMinimum)
            startupHandler.postDelayed(splashMinimum, remaining)
            return
        }
        if (StartupReadiness.canShowHome(localReady,
                startupSession.snapshot.settled, elapsedStartup())) showHome()
    }

    private fun showHome() {
        if (homeShown) return
        val firstHomeInProcess = !startupSession.hasShownHome
        homeShown = true
        startupSession.hasShownHome = true
        startupHandler.removeCallbacks(grace)
        startupHandler.removeCallbacks(watchdog)
        startupHandler.removeCallbacks(splashMinimum)
        splash = null
        createHome()
        if (foreground) {
            updateStartupBanner(startupSession.snapshot)
            if (resumed) resumeHome()
        }
        // Optional preparation runs once per process, not again for the PGS
        // host recreation or a return from another screen.
        if (!firstHomeInProcess) return
        com.rekixo.ludopaws.feature.boards.rendering.BoardRendererRegistry
            .create(com.rekixo.ludopaws.feature.boards.domain.BoardRegistry.resolve("board_classic"))
            .prepare {}
        LudoPawsYardHabitatRenderer.prepare {}
        LudoPawsCenterHouses3D.prepare()
        LudoPawsBoardChromeView.prewarm(this)
    }

    private fun createHome() {
        val (root, host) =
            LudoProofTheme.arcadeRoot(this)

        val contentHost =
            FrameLayout(this)

        val horizontalPaddingDp =
            LudoProofTheme.pageHorizontalPaddingDp(this)
        val topHudInsetReductionDp =
            if (isCompact()) 6 else 8
        val availableWidth =
            (
                resources.displayMetrics.widthPixels -
                    dp(horizontalPaddingDp * 2)
                ).coerceAtLeast(1)
        val lowerContentWidth =
            minOf(
                availableWidth,
                dp(
                    LudoProofTheme
                        .pageMaxContentWidthDp(this),
                ),
            )
        val topHudHorizontalPaddingDp =
            (
                horizontalPaddingDp -
                    topHudInsetReductionDp
                )
                .coerceAtLeast(6)
        val topHudWidth =
            minOf(
                resources.displayMetrics.widthPixels -
                    dp(
                        topHudHorizontalPaddingDp *
                            2,
                    ),
                dp(
                    LudoProofTheme
                        .pageMaxContentWidthDp(this) +
                        topHudInsetReductionDp *
                        2,
                ),
            )
        val modeSectionWidth =
            (
                lowerContentWidth *
                    0.92f
                )
                .toInt()

        val content =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(26),
                )
            }
        contentHost.addView(
            content,
            FrameLayout.LayoutParams(
                topHudWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ),
        )

        // The supplied artwork already contains the complete Home hero:
        // Ludo Paws logo, wood, pets, pedestals and paw die. Render it as one
        // transparent image so the old generated logo/pet layers cannot stack
        // or leak into the center area. Mode/footer positions stay unchanged.
        val heroWidth =
            minOf(
                topHudWidth,
                dp(if (isCompact()) 350 else 388),
            )
        contentHost.addView(
            HomePromotionCarousel(this) { tab, characterId ->
                launchPlayerScreen(Intent(this, StoreActivity::class.java).apply {
                    putExtra(StoreActivity.EXTRA_INITIAL_TAB, tab)
                    characterId?.let { putExtra(StoreActivity.EXTRA_FEATURED_CHARACTER_ID, it) }
                })
            }.also { promotionCarousel = it },
            FrameLayout.LayoutParams(
                heroWidth,
                dp(if (isCompact()) 272 else 302),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin =
                    dp(if (isCompact()) 158 else 168)
            },
        )

        // Home is a fixed game menu, not a document-style scrolling screen.
        // Keeping the HUD/mode controls directly in the host prevents swipe
        // gestures from sliding the profile/shortcut controls into the status
        // bar/camera cutout while preserving their existing positions.
        host.addView(
            contentHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        host.addView(
            homeBottomActions(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(if (isCompact()) 54 else 60),
                Gravity.BOTTOM or
                    Gravity.CENTER_HORIZONTAL,
            ).apply {
                leftMargin =
                    dp(if (isCompact()) 18 else 24)
                rightMargin =
                    dp(if (isCompact()) 18 else 24)
                bottomMargin =
                    dp(if (isCompact()) 46 else 54)
            },
        )

        content.addView(
            profileHud(),
        )

        content.addView(
            modeSection(),
            LinearLayout.LayoutParams(
                modeSectionWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity =
                    Gravity.CENTER_HORIZONTAL
                topMargin =
                    // Lift only the five main game-mode cards. The hero artwork
                    // and the top HUD keep their own existing positions.
                    dp(
                        if (isCompact()) 322 else 335,
                    )
            },
        )

        setContentView(root)

        startupBanner = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(LudoProofTheme.TEXT_MUTED)
            setPadding(dp(8), 0, dp(8), dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnClickListener {
                if (startupSession.snapshot.state == StartupAccountState.RESTORE_REQUIRED) {
                    launchPlayerScreen(android.content.Intent(this@HomeActivity,
                        com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity::class.java)
                        .putExtra("open_account_actions", true))
                } else {
                    if (!remoteExitSaved) prepareHomeRemoteExit()
                    startupSession.refresh(StartupReadiness.canBootstrapAccount(connectivityMonitor.isOnline(), remoteExitSaved))
                    updateStartupBanner(startupSession.snapshot)
                }
            }
        }.also { banner ->
            host.addView(banner, FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(if (isCompact()) 140 else 144)
                leftMargin = dp(24); rightMargin = dp(24)
            })
        }
        homeRoot = root
        privacyBlocker = View(this).apply {
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            elevation = dp(100).toFloat()
        }.also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        syncHomePrivacyGate()
    }

    private fun syncHomePrivacyGate() {
        val ready = com.rekixo.ludopaws.feature.safety.FamilyAgeScreen.hasChoice(this)
        privacyBlocker?.visibility = if (ready) View.GONE else View.VISIBLE
        // The dialog owns a separate window. Hide only the underlying Home
        // from accessibility/focus while its required privacy choice is pending.
        homeRoot?.apply {
            importantForAccessibility = if (ready) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            descendantFocusability = if (ready) android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS else android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    private fun updateStartupBanner(snapshot: StartupAccountSnapshot) {
        startupBanner?.apply {
            visibility = if (remoteExitSaved && (snapshot.state == StartupAccountState.GUEST_READY || snapshot.state == StartupAccountState.SAVED_READY)) View.GONE else View.VISIBLE
            text = if (!remoteExitSaved) "Couldn't save match result · Tap to retry" else when (snapshot.state) {
                StartupAccountState.RESTORE_REQUIRED -> "Restore your saved account to reconnect · Tap here"
                StartupAccountState.CHECKING -> "Connecting — offline play available"
                StartupAccountState.OFFLINE -> "Offline play available · Tap to retry"
                StartupAccountState.RETRY_REQUIRED -> "Couldn't connect · Tap to retry"
                else -> snapshot.message
            }
        }
        if (homeShown) scheduleHomeHudRefresh()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (homeShown) resumeHome() else maybeShowHome()
    }

    private fun prepareHomeRemoteExit() {
        // Capture the abandoned match owner BEFORE startup can create/recover a
        // different session. Never attribute an old match to a newly created guest.
        remoteExitSaved = runCatching {
            recordAbandonedRemoteLossIfNeeded()
            SecureSessionStore(this).clear()
            CachedMatchStore(this).clear()
            PublicMatchmakingStore(this).clear()
        }.isSuccess
    }

    private fun resumeHome() {
        scheduleHomeHudRefresh()
        resumeHomePrivacy()
    }

    private fun resumeHomePrivacy() {
        if (!homeShown || !resumed || isFinishing || isDestroyed) return
        syncHomePrivacyGate()
        com.rekixo.ludopaws.feature.safety.FamilyAgeScreen.ensureEntryChoice(this, privacyReady)
    }

    internal fun isHomeReadyForPlatformConnection(): Boolean =
        resumed && homeShown && !isFinishing && !isDestroyed

    private fun resumeHomePromotions() {
        if (!isHomeReadyForPlatformConnection() || !com.rekixo.ludopaws.feature.safety.FamilyAgeScreen.hasChoice(this)) return
        promotionCarousel?.setActive(!referralPopupVisible)
        // The PGS host recreation must not consume and cancel the one-per-session
        // referral opportunity before the restored Home can display it.
        if (!com.rekixo.ludopaws.feature.safety.FamilyAgeScreen.optionalGoogleAllowed(this) ||
            startupPlayGames.authenticationBootstrapPending || promotionsStarted) return
        promotionsStarted = true
        referralPromotions.resume(home = true)
        startupHandler.removeCallbacks(deferredHomePreparation)
        startupHandler.postDelayed(deferredHomePreparation, 1200L)
    }

    override fun onPause() {
        resumed = false
        promotionsStarted = false
        startupHandler.removeCallbacks(deferredHomePreparation)
        referralPromotions.pause()
        promotionCarousel?.setActive(false)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("startup_home_shown", homeShown)
        super.onSaveInstanceState(outState)
    }

    private fun recordAbandonedRemoteLossIfNeeded() {
        val session = SecureSessionStore(this).load() ?: return
        val mode = requireNotNull(GameMode.fromWireValue(session.modeWire)) {
            "Cannot save the pending match result for an unknown game mode"
        }
        SecureSessionStore(this).savePendingForfeit(PendingRemoteForfeit(
            matchId = session.matchId, playerId = session.playerId, playerToken = session.playerToken,
            ranked = mode.ranked,
            ownerAccountId = com.rekixo.ludopaws.feature.account.data.AccountSessionStore(this).load()?.accountId,
        ))
        retryPendingRemoteForfeits(this)
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        observedStorePrefs.registerOnSharedPreferenceChangeListener(walletChanged)
        prepareHomeRemoteExit()
        // Time spent backgrounded is not a local preparation failure.
        if (!homeShown && !preparationFailed) startedAt = SystemClock.elapsedRealtime()
        if (preparationFailed) splash?.showPreparationError()
        startupSession.subscribe(startupObserver)
        lastOnline = null
        armStartupTimers()
        retryPendingRemoteForfeits(this)
        com.rekixo.ludopaws.feature.offline.data.local.OfflineResultRecovery.retry(this)
        connectivityMonitor.start()
    }

    override fun onStop() {
        foreground = false
        observedStorePrefs.unregisterOnSharedPreferenceChangeListener(walletChanged)
        startupSession.unsubscribe(startupObserver)
        startupHandler.removeCallbacks(grace)
        startupHandler.removeCallbacks(watchdog)
        startupHandler.removeCallbacks(splashMinimum)
        connectivityMonitor.stop()
        super.onStop()
    }

    override fun onDestroy() {
        observedStorePrefs.unregisterOnSharedPreferenceChangeListener(walletChanged)
        launchGate?.let { gate ->
            if (window.decorView.viewTreeObserver.isAlive) window.decorView.viewTreeObserver.removeOnPreDrawListener(gate)
        }
        launchGate = null
        preparationGeneration += 1
        referralPromotions.pause()
        com.rekixo.ludopaws.feature.safety.FamilyAgeScreen.detach(this)
        startupSession.unsubscribe(startupObserver)
        startupHandler.removeCallbacksAndMessages(null)
        if (
            ::connectivityMonitor
                .isInitialized
        ) {
            connectivityMonitor.stop()
        }
        super.onDestroy()
    }
}
