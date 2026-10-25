package com.rekixo.ludopaws

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)
        com.rekixo.ludopaws.feature.boards.rendering.BoardRendererRegistry
            .create(com.rekixo.ludopaws.feature.boards.domain.BoardRegistry.resolve("board_classic"))
            .prepare {}
        LudoPawsYardHabitatRenderer.prepare {}
        LudoPawsCenterHouses3D.prepare()
        LudoPawsBoardChromeView.prewarm(this)
        LudoProofTheme.configureWindow(this)

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
            ImageView(this).apply {
                setImageResource(
                    R.drawable.ludo_paws_home_hero,
                )
                scaleType =
                    ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
                contentDescription =
                    "Ludo Paws characters"
                importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES
            },
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
