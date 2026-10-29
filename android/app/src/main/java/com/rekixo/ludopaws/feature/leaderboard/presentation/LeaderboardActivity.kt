package com.rekixo.ludopaws.feature.leaderboard.presentation

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.appIconActionButton
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.leaderboard.data.local.LeaderboardCacheStore
import com.rekixo.ludopaws.feature.leaderboard.data.local.LeaderboardCredential
import com.rekixo.ludopaws.feature.leaderboard.data.local.LeaderboardIdentityStore
import com.rekixo.ludopaws.feature.leaderboard.data.remote.LeaderboardApi
import com.rekixo.ludopaws.feature.leaderboard.domain.model.LeaderboardEntry
import com.rekixo.ludopaws.feature.leaderboard.domain.model.LeaderboardSnapshot
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import java.text.NumberFormat
import java.util.Date
import java.util.concurrent.Executors

class LeaderboardActivity :
    Activity() {
    private val executor =
        Executors
            .newSingleThreadExecutor()
    private val mainHandler =
        Handler(
            Looper.getMainLooper(),
        )
    private val api by lazy {
        LeaderboardApi()
    }
    private val identityStore by lazy {
        LeaderboardIdentityStore(
            this,
        )
    }
    private val cacheStore by lazy {
        LeaderboardCacheStore(
            this,
        )
    }
    private val selectedCharacterId by lazy {
        CharacterSelectionStore(this).load().characterId
    }

    private var snapshot:
        LeaderboardSnapshot? =
        null
    private var loading =
        true
    private var showingCached =
        false
    private var cachedAt: Long? =
        null
    private var refreshing =
        false
    private var statusMessage:
        String? =
        null

    private var pageStarted = false
    private var refreshAgain = false
    private var snapshotOwner: String? = null
    private fun owner() = com.rekixo.ludopaws.feature.account.data.AccountSessionStore(this).load()?.accountId
    private fun finishRefresh() {
        refreshing = false
        if (refreshAgain && pageStarted && !isFinishing && !isDestroyed) {
            refreshAgain = false
            refreshLeaderboard()
        }
    }
    private var lastConnected: Boolean? = null
    private val connectivity by lazy {
        com.rekixo.ludopaws.ConnectivityMonitor(this) { online -> mainHandler.post {
            if (!pageStarted || isFinishing || isDestroyed) return@post
            val returned = lastConnected == false && online
            lastConnected = online
            if (returned) { if (refreshing) refreshAgain = true else refreshLeaderboard() }
            else if (!online) {
                statusMessage = if (snapshot != null) "Offline · showing saved ranking" else "Offline · connect to load rankings"
                render()
            }
        } }
    }
    override fun onStart() {
        super.onStart()
        pageStarted = true
        if (snapshotOwner != owner()) {
            snapshotOwner = owner(); snapshot = null; showingCached = false; cachedAt = null
            if (refreshing) refreshAgain = true else refreshLeaderboard()
        }
        connectivity.start()
        if (!refreshing) refreshLeaderboard()
    }
    override fun onStop() { pageStarted = false; connectivity.stop(); super.onStop() }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )
        LudoProofTheme
            .configureWindow(
                this,
            )

        snapshotOwner = owner()
        cacheStore
            .load()
            ?.let {
                    cached ->
                runCatching {
                    LeaderboardSnapshot
                        .fromJson(
                            cached.payload,
                        )
                }.getOrNull()
                    ?.let {
                            cachedSnapshot ->
                        snapshot =
                            cachedSnapshot
                        showingCached =
                            true
                        cachedAt =
                            cached.savedAt
                        loading =
                            false
                    }
            }

        render()
        refreshLeaderboard()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshLeaderboard() {
        if (!connectivity.isOnline()) {
            loading = false
            statusMessage = if (snapshot != null) "Offline · showing saved ranking" else "Offline · connect to load rankings"
            render()
            return
        }
        if (refreshing) {
            return
        }
        refreshing = true
        loading =
            snapshot == null
        statusMessage =
            null
        render()

        val expectedAccountId = com.rekixo.ludopaws.feature.account.data.AccountSessionStore(this).load()?.accountId
        if (expectedAccountId == null) {
            refreshing = false
            loading = false
            statusMessage = "Sign in to view the leaderboard."
            render()
            return
        }
        executor.execute {
            try {
                val credential =
                    ensureCredential()
                val payload =
                    api.load(
                        profileToken =
                            credential.profileToken,
                        limit =
                            50,
                    )
                val fresh =
                    LeaderboardSnapshot
                        .fromJson(
                            payload,
                        )
                cacheStore
                    .save(
                        payload,
                        expectedAccountId,
                    )

                mainHandler.post {
                    if (
                        isDestroyed ||
                        isFinishing ||
                        com.rekixo.ludopaws.feature.account.data.AccountSessionStore(this).load()?.accountId != expectedAccountId
                    ) {
                        finishRefresh()
                        return@post
                    }
                    snapshot =
                        fresh
                    showingCached =
                        false
                    cachedAt =
                        System.currentTimeMillis()
                    refreshing =
                        false
                    loading =
                        false
                    statusMessage =
                        null
                    render()
                    finishRefresh()
                }
            } catch (
                error: Exception,
            ) {
                mainHandler.post {
                    if (
                        isDestroyed ||
                        isFinishing
                    ) {
                        return@post
                    }
                    refreshing =
                        false
                    loading =
                        false
                    if (owner() != expectedAccountId) { finishRefresh(); return@post }
                    statusMessage =
                        if (
                            snapshot !=
                            null
                        ) {
                            "Could not refresh. Showing the last saved ranking."
                        } else {
                            "Leaderboard is unavailable right now. Check your connection and try again."
                        }
                    render()
                    finishRefresh()
                }
            }
        }
    }

    private fun ensureCredential():
        LeaderboardCredential {
        identityStore
            .load()
            ?.let {
                return it
            }

        val requestId =
            identityStore
                .registrationRequestId()
        val response =
            api.registerProfile(
                clientRequestId =
                    requestId,
            )
        val credential =
            LeaderboardCredential(
                profileId =
                    response.getString(
                        "profileId",
                    ),
                profileToken =
                    response.getString(
                        "profileToken",
                    ),
                registrationRequestId =
                    requestId,
            )
        identityStore.save(
            credential,
        )
        return credential
    }

    private fun render() {
        val (root, host) =
            LudoProofTheme
                .arcadeRoot(
                    this,
                )

        val scroll =
            ScrollView(
                this,
            ).apply {
                isFillViewport =
                    true
                overScrollMode =
                    View.OVER_SCROLL_NEVER
                clipToPadding =
                    false
            }

        val contentHost =
            FrameLayout(
                this,
            )
        scroll.addView(
            contentHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val side =
            dp(
                if (
                    LudoProofTheme
                        .isCompactWidth(
                            this,
                        )
                ) {
                    12
                } else {
                    18
                },
            )
        val contentWidth =
            minOf(
                resources
                    .displayMetrics
                    .widthPixels -
                    side *
                    2,
                dp(
                    LudoProofTheme
                        .pageMaxContentWidthDp(
                            this,
                        ),
                ),
            )
                .coerceAtLeast(
                    1,
                )

        val content =
            LinearLayout(
                this,
            ).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    0,
                    dp(12),
                    0,
                    dp(32),
                )
            }

        contentHost.addView(
            content,
            FrameLayout.LayoutParams(
                contentWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or
                    Gravity.CENTER_HORIZONTAL,
            ),
        )
        host.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        content.addView(
            topBar(),
        )
        content.addView(
            currentPlayerCard(),
            sectionParams(
                16,
            ),
        )
        content.addView(
            scoringStrip(),
            sectionParams(
                10,
            ),
        )

        statusMessage
            ?.let {
                    message ->
                content.addView(
                    statusPanel(
                        message,
                    ),
                    sectionParams(
                        10,
                    ),
                )
            }

        content.addView(
            rankingHeader(),
            sectionParams(
                20,
            ),
        )

        when {
            loading &&
                snapshot ==
                null -> {
                content.addView(
                    loadingPanel(),
                    sectionParams(
                        10,
                    ),
                )
            }

            snapshot
                ?.entries
                .isNullOrEmpty() -> {
                content.addView(
                    emptyPanel(),
                    sectionParams(
                        10,
                    ),
                )
            }

            else -> {
                val entries =
                    snapshot
                        ?.entries
                        .orEmpty()

                if (entries.size >= 3) {
                    content.addView(
                        topThreePodium(
                            entries.take(3),
                        ),
                        sectionParams(12),
                    )
                }

                val remainingEntries =
                    if (entries.size >= 3) {
                        entries.drop(3)
                    } else {
                        entries
                    }
                remainingEntries.forEach { entry ->
                    content.addView(
                        rankingRow(entry),
                        sectionParams(8),
                    )
                }
            }
        }

        setContentView(
            root,
        )
        root.setOnApplyWindowInsetsListener { _, insets ->
            val cutoutTop =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    insets.displayCutout?.safeInsetTop ?: 0
                } else {
                    0
                }
            val statusTop =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop
                }
            val safeTopInset = maxOf(cutoutTop, statusTop)
            content.setPadding(
                content.paddingLeft,
                dp(12) + safeTopInset,
                content.paddingRight,
                content.paddingBottom,
            )
            insets
        }
        root.requestApplyInsets()
    }

    private fun topBar():
        LinearLayout =
        LinearLayout(
            this,
        ).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.CENTER_VERTICAL
            minimumHeight =
                dp(58)

            addView(
                appIconActionButton(
                    context = this@LeaderboardActivity,
                    contentDescription = "Back",
                    iconName = AppIconName.CHEVRON_LEFT,
                    sizeDp = 48,
                ) { finish() }.apply {
                    // Match the shared chevron used by Profile and other screens.
                    background =
                        plywoodBackground(
                            this@LeaderboardActivity,
                            cornerRadiusDp = 999f,
                        )
                },
                LinearLayout.LayoutParams(
                    dp(48),
                    dp(48),
                ),
            )

            addView(
                View(this@LeaderboardActivity),
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f,
                ).apply {
                    marginStart = dp(12)
                    marginEnd = dp(8)
                },
            )

            addView(
                Button(
                    this@LeaderboardActivity,
                ).apply {
                    LudoProofTheme
                        .homeCircularAction(
                            this,
                            "↻",
                        )
                    applyPlywoodActionBackground(this)
                    contentDescription =
                        "Refresh leaderboard"
                    isEnabled =
                        !refreshing
                    alpha =
                        if (
                            refreshing
                        ) {
                            0.55f
                        } else {
                            1f
                        }
                    setOnClickListener {
                        refreshLeaderboard()
                    }
                    AppFocusRing.apply(this, cornerRadiusDp = 999f)
                },
                LinearLayout.LayoutParams(
                    dp(48),
                    dp(48),
                ),
            )
        }

    private fun applyPlywoodActionBackground(button: Button) {
        button.backgroundTintList = null
        button.foreground = null
        button.stateListAnimator = null
        button.setPadding(0, 0, 0, 0)
        button.background =
            plywoodBackground(
                context = button.context,
                cornerRadiusDp = 999f,
            )
    }

    private fun currentPlayerCard():
        LinearLayout {
        val viewer =
            snapshot
                ?.viewer
        val localProfile =
            ProfileStore(
                this,
            ).snapshot()
        val displayName =
            viewer
                ?.displayName
                ?: localProfile
                    .displayName
        return LinearLayout(
            this,
        ).apply {
            orientation =
                LinearLayout.VERTICAL
            setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14),
            )
            background =
                plywoodBackground(
                    this@LeaderboardActivity,
                    cornerRadiusDp = 17f,
                )
            elevation =
                dp(7)
                    .toFloat()

            // Keep the title and its verification status together inside the
            // plywood player card, centered above the selected character.
            addView(
                TextView(
                    this@LeaderboardActivity,
                ).apply {
                    text = "TOP LEADERS"
                    textSize =
                        if (LudoProofTheme.isCompactWidth(this@LeaderboardActivity)) {
                            21f
                        } else {
                            24f
                        }
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                TextView(
                    this@LeaderboardActivity,
                ).apply {
                    text =
                        when {
                            showingCached ->
                                "SAVED • " + cachedTimeLabel()
                            refreshing ->
                                "SYNCING VERIFIED RANKS"
                            else ->
                                "SERVER VERIFIED • ALL TIME"
                        }
                    textSize = 10f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFF6EEBFF.toInt())
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    setPadding(0, dp(3), 0, dp(9))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                LinearLayout(
                    this@LeaderboardActivity,
                ).apply {
                    orientation =
                        LinearLayout.HORIZONTAL
                    gravity =
                        Gravity.CENTER_VERTICAL

                    addView(
                        leaderboardCharacterPortrait(),
                        LinearLayout.LayoutParams(
                            dp(84),
                            dp(84),
                        ),
                    )

                    addView(
                        LinearLayout(
                            this@LeaderboardActivity,
                        ).apply {
                            orientation =
                                LinearLayout.VERTICAL

                            addView(
                                TextView(
                                    this@LeaderboardActivity,
                                ).apply {
                                    text =
                                        displayName
                                    textSize =
                                        20f
                                    setTypeface(
                                        Typeface.DEFAULT_BOLD,
                                    )
                                    setTextColor(
                                        Color.WHITE,
                                    )
                                    maxLines =
                                        1
                                    ellipsize =
                                        TextUtils
                                            .TruncateAt
                                            .END
                                },
                            )

                            addView(
                                TextView(
                                    this@LeaderboardActivity,
                                ).apply {
                                    text =
                                        if (
                                            viewer !=
                                            null
                                        ) {
                                            "YOUR RANK  #" +
                                                viewer.rank
                                        } else {
                                            "UNRANKED • FINISH ONLINE MATCH"
                                        }
                                    textSize =
                                        11f
                                    setTypeface(
                                        Typeface.DEFAULT_BOLD,
                                    )
                                    setTextColor(
                                        0xFF65421E.toInt(),
                                    )
                                    setPadding(
                                        0,
                                        dp(5),
                                        0,
                                        0,
                                    )
                                },
                            )
                        },
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        ).apply {
                            marginStart =
                                dp(12)
                        },
                    )
                },
            )

            addView(
                metricRow(
                    viewer,
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin =
                        dp(14)
                },
            )
        }
    }

    private fun metricRow(
        viewer: LeaderboardEntry?,
    ):
        LinearLayout =
        LinearLayout(
            this,
        ).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.CENTER

            listOf(
                "POINTS" to
                    number(
                        viewer
                            ?.points
                            ?: 0,
                    ),
                "WINS" to
                    (
                        viewer
                            ?.wins
                            ?: 0
                        ).toString(),
                "GAMES" to
                    (
                        viewer
                            ?.games
                            ?: 0
                        ).toString(),
            ).forEachIndexed {
                    index,
                    item ->
                addView(
                    metricCell(
                        item.first,
                        item.second,
                    ),
                    LinearLayout.LayoutParams(
                        0,
                        dp(58),
                        1f,
                    ).apply {
                        if (
                            index >
                            0
                        ) {
                            marginStart =
                                dp(7)
                        }
                    },
                )
            }
        }

    private fun metricCell(
        label: String,
        value: String,
    ):
        LinearLayout =
        LinearLayout(
            this,
        ).apply {
            orientation =
                LinearLayout.VERTICAL
            gravity =
                Gravity.CENTER
            background =
                LudoProofTheme
                    .rounded(
                        0xC9092C69.toInt(),
                        12f,
                        0x554FDFFF,
                        1f,
                        this@LeaderboardActivity,
                    )

            addView(
                TextView(
                    this@LeaderboardActivity,
                ).apply {
                    text =
                        value
                    textSize =
                        18f
                    setTypeface(
                        Typeface.DEFAULT_BOLD,
                    )
                    setTextColor(
                        Color.WHITE,
                    )
                    gravity =
                        Gravity.CENTER
                },
            )
            addView(
                TextView(
                    this@LeaderboardActivity,
                ).apply {
                    text =
                        label
                    textSize =
                        9f
                    setTypeface(
                        Typeface.DEFAULT_BOLD,
                    )
                    setTextColor(
                        0xFF83DFFF.toInt(),
                    )
                    gravity =
                        Gravity.CENTER
                },
            )
        }

    private fun scoringStrip():
        TextView {
        val finish =
            snapshot
                ?.finishPoints
                ?: 20
        val bonus =
            snapshot
                ?.winBonus
                ?: 30

        return TextView(
            this,
        ).apply {
            text =
                "SERVER VERIFIED  •  MATCH COMPLETED +" +
                    finish +
                    "  •  WIN BONUS +" +
                    bonus
            textSize =
                10f
            setTypeface(
                Typeface.DEFAULT_BOLD,
            )
            setTextColor(
                0xFF5B381F.toInt(),
            )
            gravity =
                Gravity.CENTER
            setPadding(
                dp(10),
                dp(10),
                dp(10),
                dp(10),
            )
            background =
                plywoodBackground(
                    this@LeaderboardActivity,
                    cornerRadiusDp = 12f,
                )
        }
    }

    private fun rankingHeader():
        LinearLayout =
        LinearLayout(
            this,
        ).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.CENTER_VERTICAL

            addView(
                TextView(
                    this@LeaderboardActivity,
                ).apply {
                    text =
                        "GLOBAL RANKING"
                    textSize =
                        16f
                    setTypeface(
                        Typeface.DEFAULT_BOLD,
                    )
                    setTextColor(
                        Color.WHITE,
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            addView(
                TextView(
                    this@LeaderboardActivity,
                ).apply {
                    text =
                        "ALL TIME"
                    textSize =
                        10f
                    setTypeface(
                        Typeface.DEFAULT_BOLD,
                    )
                    setTextColor(
                        LudoProofTheme.GOLD,
                    )
                    gravity =
                        Gravity.CENTER
                    setPadding(
                        dp(10),
                        dp(6),
                        dp(10),
                        dp(6),
                    )
                    background =
                        LudoProofTheme
                            .rounded(
                                0xE907255E.toInt(),
                                999f,
                                0x88FFC62E.toInt(),
                                1f,
                                this@LeaderboardActivity,
                            )
                },
            )
        }

    private fun topThreePodium(
        entries: List<LeaderboardEntry>,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.BOTTOM

            val podiumEntries =
                listOf(2, 1, 3)
                    .mapNotNull { rank ->
                        entries.firstOrNull {
                            it.rank == rank
                        }
                    }

            podiumEntries.forEach { entry ->
                val accent =
                    when (entry.rank) {
                        1 -> 0xFF9A6500.toInt()
                        2 -> 0xFF65717B.toInt()
                        else -> 0xFF974F1F.toInt()
                    }
                val champion =
                    entry.rank == 1

                val card =
                    LinearLayout(
                        this@LeaderboardActivity,
                    ).apply {
                        orientation =
                            LinearLayout.VERTICAL
                        gravity =
                            Gravity.CENTER
                        setPadding(
                            dp(5),
                            dp(9),
                            dp(5),
                            dp(8),
                        )
                        background =
                            if (entry.isViewer) {
                                playerCountSelectionBackground(
                                    context =
                                        this@LeaderboardActivity,
                                    selected = true,
                                    outerCornerRadiusDp = 13f,
                                    checkBadgeAtTopEnd = true,
                                    emphasizeSelected = true,
                                    showCheckBadge = false,
                                    useRecessedPlywood = false,
                                )
                            } else {
                                plywoodBackground(
                                    this@LeaderboardActivity,
                                    cornerRadiusDp = 13f,
                                )
                            }
                        elevation =
                            dp(if (champion) 6 else 3).toFloat()

                        addView(
                            TextView(
                                this@LeaderboardActivity,
                            ).apply {
                                text =
                                    when (entry.rank) {
                                        1 -> "CHAMPION"
                                        2 -> "2ND PLACE"
                                        else -> "3RD PLACE"
                                    }
                                textSize = 9f
                                setTypeface(
                                    Typeface.DEFAULT_BOLD,
                                )
                                setTextColor(accent)
                                gravity = Gravity.CENTER
                                includeFontPadding = false
                                maxLines = 1
                            },
                        )

                        addView(
                            if (entry.isViewer) {
                                leaderboardCharacterPortrait()
                            } else {
                                TextView(
                                    this@LeaderboardActivity,
                                ).apply {
                                    text =
                                        initials(
                                            entry.displayName,
                                        )
                                    textSize = 14f
                                    setTypeface(
                                        Typeface.DEFAULT_BOLD,
                                    )
                                    setTextColor(Color.WHITE)
                                    gravity = Gravity.CENTER
                                    background =
                                        GradientDrawable(
                                            GradientDrawable.Orientation.TOP_BOTTOM,
                                            intArrayOf(
                                                0xFF278DEB.toInt(),
                                                0xFF0A55B2.toInt(),
                                            ),
                                        ).apply {
                                            shape =
                                                GradientDrawable.OVAL
                                            setStroke(
                                                dp(if (champion) 2 else 1),
                                                accent,
                                            )
                                        }
                                }
                            },
                            LinearLayout.LayoutParams(
                                dp(if (champion) 44 else 40),
                                dp(if (champion) 44 else 40),
                            ).apply {
                                topMargin = dp(7)
                            },
                        )

                        addView(
                            TextView(
                                this@LeaderboardActivity,
                            ).apply {
                                text =
                                    entry.displayName
                                textSize =
                                    if (champion) 12.5f else 11.5f
                                setTypeface(
                                    Typeface.DEFAULT_BOLD,
                                )
                                setTextColor(0xFF503018.toInt())
                                gravity = Gravity.CENTER
                                maxLines = 1
                                ellipsize =
                                    TextUtils.TruncateAt.END
                                includeFontPadding = false
                                setPadding(0, dp(4), 0, 0)
                            },
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ),
                        )

                        addView(
                            TextView(
                                this@LeaderboardActivity,
                            ).apply {
                                text =
                                    number(entry.points)
                                textSize =
                                    if (champion) 18f else 15f
                                setTypeface(
                                    Typeface.DEFAULT_BOLD,
                                )
                                setTextColor(
                                    if (champion) accent else 0xFF503018.toInt(),
                                )
                                gravity = Gravity.CENTER
                                includeFontPadding = false
                                setPadding(0, dp(3), 0, 0)
                            },
                        )

                        addView(
                            TextView(
                                this@LeaderboardActivity,
                            ).apply {
                                text = "POINTS"
                                textSize = 8f
                                setTypeface(
                                    Typeface.DEFAULT_BOLD,
                                )
                                setTextColor(0xFF765333.toInt())
                                gravity = Gravity.CENTER
                                includeFontPadding = false
                            },
                        )

                        addView(
                            TextView(
                                this@LeaderboardActivity,
                            ).apply {
                                text =
                                    "${entry.wins} W  •  ${entry.games} G"
                                textSize = 8.5f
                                setTextColor(0xFF765333.toInt())
                                gravity = Gravity.CENTER
                                maxLines = 1
                                includeFontPadding = false
                                setPadding(0, dp(4), 0, 0)
                            },
                        )
                    }

                addView(
                    card,
                    LinearLayout.LayoutParams(
                        0,
                        dp(if (champion) 164 else 148),
                        1f,
                    ).apply {
                        setMargins(
                            dp(3),
                            0,
                            dp(3),
                            0,
                        )
                    },
                )
            }
        }

    private fun rankingRow(
        entry: LeaderboardEntry,
    ):
        LinearLayout {
        val border =
            when {
                entry.isViewer ->
                    0xFF6A3F18.toInt()
                entry.rank ==
                    1 ->
                    0xFF9A6500.toInt()
                entry.rank ==
                    2 ->
                    0xFF65717B.toInt()
                entry.rank ==
                    3 ->
                    0xFF974F1F.toInt()
                else ->
                    0xFF76502E.toInt()
            }

        return LinearLayout(
            this,
        ).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.CENTER_VERTICAL
            minimumHeight =
                dp(
                    if (
                        entry.rank <=
                        3
                    ) {
                        78
                    } else {
                        70
                    },
                )
            setPadding(
                dp(10),
                dp(9),
                dp(10),
                dp(9),
            )
            background =
                if (entry.isViewer) {
                    playerCountSelectionBackground(
                        context =
                            this@LeaderboardActivity,
                        selected = true,
                        outerCornerRadiusDp = 15f,
                        checkBadgeAtTopEnd = true,
                        emphasizeSelected = true,
                        showCheckBadge = false,
                        useRecessedPlywood = false,
                    )
                } else {
                    plywoodBackground(
                        this@LeaderboardActivity,
                        cornerRadiusDp = 15f,
                    )
                }
            elevation =
                dp(
                    if (
                        entry.rank <=
                        3
                    ) {
                        5
                    } else {
                        2
                    },
                ).toFloat()

            addView(
                TextView(
                    this@LeaderboardActivity,
                ).apply {
                    text =
                        "#" +
                            entry.rank
                    textSize =
                        if (
                            entry.rank <=
                            3
                        ) {
                            16f
                        } else {
                            14f
                        }
                    setTypeface(
                        Typeface.DEFAULT_BOLD,
                    )
                    setTextColor(
                        border,
                    )
                    gravity =
                        Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    dp(48),
                    dp(48),
                ),
            )

            addView(
                if (entry.isViewer) {
                    leaderboardCharacterPortrait()
                } else {
                    TextView(
                        this@LeaderboardActivity,
                    ).apply {
                        text =
                            initials(
                                entry.displayName,
                            )
                        textSize =
                            15f
                        setTypeface(
                            Typeface.DEFAULT_BOLD,
                        )
                        setTextColor(
                            Color.WHITE,
                        )
                        gravity =
                            Gravity.CENTER
                        background =
                            GradientDrawable(
                                GradientDrawable
                                    .Orientation
                                    .TOP_BOTTOM,
                                intArrayOf(
                                    0xFF278DEB.toInt(),
                                    0xFF0A55B2.toInt(),
                                ),
                            ).apply {
                                shape =
                                    GradientDrawable.OVAL
                                setStroke(
                                    dp(1),
                                    border,
                                )
                            }
                    }
                },
                LinearLayout.LayoutParams(
                    dp(48),
                    dp(48),
                ).apply {
                    marginEnd =
                        dp(10)
                },
            )

            addView(
                LinearLayout(
                    this@LeaderboardActivity,
                ).apply {
                    orientation =
                        LinearLayout.VERTICAL

                    addView(
                        TextView(
                            this@LeaderboardActivity,
                        ).apply {
                            text =
                                entry.displayName +
                                if (entry.isViewer) {
                                    "  • YOU"
                                } else {
                                    ""
                                }
                            textSize =
                                16f
                            setTypeface(
                                Typeface.DEFAULT_BOLD,
                            )
                            setTextColor(
                                0xFF503018.toInt(),
                            )
                            maxLines =
                                1
                            ellipsize =
                                TextUtils
                                    .TruncateAt
                                    .END
                        },
                    )

                    val winRate =
                        if (
                            entry.games >
                            0
                        ) {
                            entry.wins *
                                100 /
                                entry.games
                        } else {
                            0
                        }
                    addView(
                        TextView(
                            this@LeaderboardActivity,
                        ).apply {
                            text =
                                "W " +
                                    entry.wins +
                                    "  •  G " +
                                    entry.games +
                                    "  •  " +
                                    winRate +
                                    "% win rate"
                            textSize =
                                11f
                            setTextColor(
                                0xFF765333.toInt(),
                            )
                            setPadding(
                                0,
                                dp(4),
                                0,
                                0,
                            )
                        },
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            addView(
                LinearLayout(
                    this@LeaderboardActivity,
                ).apply {
                    orientation =
                        LinearLayout.VERTICAL
                    gravity =
                        Gravity.END or
                            Gravity.CENTER_VERTICAL

                    addView(
                        TextView(
                            this@LeaderboardActivity,
                        ).apply {
                            text =
                                number(
                                    entry.points,
                                )
                            textSize =
                                16f
                            setTypeface(
                                Typeface.DEFAULT_BOLD,
                            )
                            setTextColor(
                                if (entry.rank == 1) {
                                    0xFF9A6500.toInt()
                                } else {
                                    0xFF503018.toInt()
                                },
                            )
                            gravity =
                                Gravity.END
                        },
                    )
                    addView(
                        TextView(
                            this@LeaderboardActivity,
                        ).apply {
                            text =
                                "PTS"
                            textSize =
                                9f
                            setTypeface(
                                Typeface.DEFAULT_BOLD,
                            )
                            setTextColor(
                                0xFF765333.toInt(),
                            )
                            gravity =
                                Gravity.END
                        },
                    )
                },
                LinearLayout.LayoutParams(
                    dp(76),
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun loadingPanel():
        TextView =
        messagePanel(
            "Loading verified rankings…",
            0xFF65421E.toInt(),
        )

    private fun emptyPanel():
        TextView =
        messagePanel(
            "No ranked players yet. Finish a verified online match to enter the leaderboard.",
            0xFF65421E.toInt(),
        )

    private fun statusPanel(
        message: String,
    ):
        TextView =
        messagePanel(
            message,
            0xFF65421E.toInt(),
        )

    private fun messagePanel(
        message: String,
        color: Int,
    ):
        TextView =
        TextView(
            this,
        ).apply {
            text =
                message
            textSize =
                12f
            setTextColor(
                color,
            )
            gravity =
                Gravity.CENTER
            setPadding(
                dp(14),
                dp(16),
                dp(14),
                dp(16),
            )
            background =
                plywoodBackground(
                    this@LeaderboardActivity,
                    cornerRadiusDp = 14f,
                )
        }

    /**
     * Reuses the Profile page's production 3D Paw portrait on a circular,
     * recessed-plywood pocket. Keep the character art transparent so the
     * same carved-wood surface remains visible around the portrait.
     */
    private fun leaderboardCharacterPortrait(): FrameLayout =
        FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            // Use one circular plywood surface only; the recessed pocket
            // adds a second square-looking layer around this small portrait.
            background =
                plywoodBackground(
                    context = this@LeaderboardActivity,
                    cornerRadiusDp = 999f,
                )
            setPadding(
                dp(2),
                dp(2),
                dp(2),
                dp(2),
            )
            addView(
                LudoPawsCharacterAvatarView(this@LeaderboardActivity).apply {
                    setPadding(0, 0, 0, 0)
                    bind(
                        characterId = selectedCharacterId,
                        ringColor = 0xFF5BE0FF.toInt(),
                        highlighted = false,
                        showCircularChrome = false,
                    )
                    importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_YES
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
        }

    private fun initials(
        name: String,
    ): String {
        val parts =
            name.trim()
                .split(
                    Regex(
                        "\\s+",
                    ),
                )
                .filter {
                    it.isNotBlank()
                }

        return when {
            parts.size >=
                2 ->
                (
                    parts[0]
                        .take(
                            1,
                        ) +
                        parts[1]
                            .take(
                                1,
                            )
                    ).uppercase()

            parts.size ==
                1 ->
                parts[0]
                    .take(
                        2,
                    )
                    .uppercase()

            else ->
                "LP"
        }
    }

    private fun cachedTimeLabel(): String =
        cachedAt
            ?.takeIf { it > 0L }
            ?.let { savedAt ->
                android.text.format.DateFormat
                    .getTimeFormat(this)
                    .format(Date(savedAt))
            }
            ?: "LOCAL CACHE"

    private fun number(
        value: Int,
    ): String =
        NumberFormat
            .getIntegerInstance()
            .format(
                value,
            )

    private fun sectionParams(
        topDp: Int,
    ):
        LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin =
                dp(
                    topDp,
                )
        }

    private fun dp(
        value: Int,
    ): Int =
        LudoProofTheme
            .dp(
                this,
                value,
            )
}
