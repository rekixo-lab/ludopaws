package com.rekixo.ludopaws

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import com.rekixo.ludopaws.feature.safety.PlayAgeSignalsObserver

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
    internal var homeGuideVisible: Boolean = false
    private var promotionCarousel: HomePromotionCarousel? = null

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        if (intent?.action == Intent.ACTION_VIEW && intent?.data?.host == "api.ludopaws.rekixo.com") {
            android.widget.Toast.makeText(this, "Welcome! Only a new Play installation from an invitation can qualify. Your inviter receives the reward after verified play.", android.widget.Toast.LENGTH_LONG).show()
        }

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

        val heroWidth =
            minOf(
                topHudWidth,
                dp(if (isCompact()) 350 else 388),
            )
        contentHost.addView(
            HomePromotionCarousel(this) { tab, characterId ->
                startActivity(Intent(this, StoreActivity::class.java).apply {
                    putExtra(StoreActivity.EXTRA_INITIAL_TAB, tab)
                    characterId?.let { putExtra(StoreActivity.EXTRA_FEATURED_CHARACTER_ID, it) }
                })
            }.also { promotionCarousel = it },
            FrameLayout.LayoutParams(
                heroWidth,
                dp(if (isCompact()) 272 else 302),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin = dp(if (isCompact()) 158 else 168)
            },
        )

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

        content.addView(profileHud())

        content.addView(
            modeSection(),
            LinearLayout.LayoutParams(
                modeSectionWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity =
                    Gravity.CENTER_HORIZONTAL
                topMargin = dp(if (isCompact()) 322 else 335)
            },
        )

        setContentView(root)

        connectivityMonitor =
            ConnectivityMonitor(this) { online ->
                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed
                    ) {
                        return@runOnUiThread
                    }

                    connectivityText.text =
                        if (online) {
                            "● ONLINE"
                        } else {
                            "● OFFLINE"
                        }
                    connectivityText
                        .setTextColor(
                            if (online) {
                                0xFF68F053.toInt()
                            } else {
                                LudoProofTheme.GOLD
                            },
                        )
                    connectivityText.contentDescription =
                        if (online) {
                            "Online"
                        } else {
                            "Offline"
                        }
                }
            }
    }

    override fun onResume() {
        super.onResume()
        homeResumed = true
        window.decorView.postDelayed({
            if (homeResumed && !isFinishing && !isDestroyed) com.rekixo.ludopaws.feature.referrals.ReferralRepository(this).refreshAttribution()
        }, 1200L)
        promotionCarousel?.setActive(true)
        window.decorView.postOnAnimation {
            window.decorView.post {
                if (homeResumed && !isFinishing && !isDestroyed) {
                    LudoPawsCenterHouses3D.prepare()
                    LudoPawsBoardChromeView.prewarm(this)
                    val selected = com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore(this).load()
                    com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry.prewarm(
                        listOf(selected.characterId, "dog", "goat", "duck", "cat"),
                    )
                    com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback.preloadMovementEventSfx(this)
                }
            }
        }

        recordAbandonedRemoteLossIfNeeded()

        // Remote matches are never resumable from Home. Returning Home means
        // the remote match was abandoned, so stale remote credentials and
        // cached state must not survive into a future ONLINE/FRIENDS/TEAM_UP
        // entry. Local modes start from their normal Computer/Pass & Play card.
        SecureSessionStore(this).clear()
        CachedMatchStore(this).clear()
        PublicMatchmakingStore(this).clear()

        refreshHomeProfileSummary()
        refreshHomeGemBalance()
        showFirstRunGuideIfNeeded()
        // Never show an in-app DOB screen. Google Play may request age-range sharing.
        // First-run tutorial is dismissed before any Play-managed prompt may appear.
        // Family gameplay does not request or depend on age-range sharing.
    }

    private var homeResumed = false

    override fun onPause() {
        homeResumed = false
        promotionCarousel?.setActive(false)
        super.onPause()
    }

    private fun recordAbandonedRemoteLossIfNeeded() {
        val session =
            SecureSessionStore(this)
                .load()
                ?: return
        val cached =
            CachedMatchStore(this)
                .load()
                ?.optJSONObject(
                    "state",
                )
                ?: return

        if (
            cached.optString(
                "matchId",
            ) !=
            session.matchId ||
            cached.optString(
                "status",
            ) !=
            "ACTIVE"
        ) {
            return
        }

        ProfileStore(this)
            .recordCompletedMatch(
                matchId =
                    session.matchId,
                mode =
                    ProfileGameMode.CLASSIC,
                source =
                    ProfileMatchSource.ONLINE,
                won =
                    false,
            )
    }

    override fun onStart() {
        super.onStart()
        retryPendingRemoteForfeits(this)
        connectivityMonitor.start()
    }

    override fun onStop() {
        connectivityMonitor.stop()
        super.onStop()
    }

    override fun onDestroy() {
        if (
            ::connectivityMonitor
                .isInitialized
        ) {
            connectivityMonitor.stop()
        }
        super.onDestroy()
    }
}
