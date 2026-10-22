package com.rekixo.ludopaws.feature.team.presentation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.CachedMatchStore
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.core.ui.components.playSelectionPopAnimation
import com.rekixo.ludopaws.core.ui.components.LaserStatusDotView
import com.rekixo.ludopaws.GameMode
import com.rekixo.ludopaws.GameModeIntent
import com.rekixo.ludopaws.GameJson
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.playGamesSignInBackground
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.MainActivity
import com.rekixo.ludopaws.PlayerSession
import com.rekixo.ludopaws.SecureSessionStore
import com.rekixo.ludopaws.feature.friends.presentation.FriendsIcon
import com.rekixo.ludopaws.feature.friends.presentation.FriendsVisualKit
import com.rekixo.ludopaws.feature.leaderboard.data.local.LeaderboardCredential
import com.rekixo.ludopaws.feature.leaderboard.data.local.LeaderboardIdentityStore
import com.rekixo.ludopaws.feature.leaderboard.data.remote.LeaderboardApi
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.team.data.local.TeamMatchmakingStore
import com.rekixo.ludopaws.feature.team.data.local.TeamMatchmakingTicket
import com.rekixo.ludopaws.feature.team.data.remote.TeamMatchmakingApi
import com.rekixo.ludopaws.ui.game.addGameTopActions
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class TeamUpActivity : ComponentActivity() {
    private val api by lazy { TeamMatchmakingApi() }
    private val ticketStore by lazy { TeamMatchmakingStore(this) }
    private val identityStore by lazy { LeaderboardIdentityStore(this) }
    private val profileStore by lazy { ProfileStore(this) }
    private var selectedCharacterId = LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
    private val sessionStore by lazy { SecureSessionStore(this) }
    private val cachedMatchStore by lazy { CachedMatchStore(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var requestInFlight = false
    private var stopped = false
    private var validatingRestoredSearch = false
    private var restartingRestoredSearch = false

    private lateinit var quickTeamButton: FrameLayout
    private lateinit var cancelButton: Button
    private lateinit var setupContent: LinearLayout
    private lateinit var searchPanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var slotsText: TextView
    private lateinit var timerText: TextView
    private lateinit var teamSlotProgressView: TeamSlotProgressView

    private val pollRunnable =
        object : Runnable {
            override fun run() {
                if (!stopped && !restartingRestoredSearch && ticketStore.load() != null) {
                    pollSearch()
                    if (!validatingRestoredSearch) updateTimer()
                }
                if (!stopped) {
                    handler.postDelayed(this, POLL_MS)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (ticketStore.load() != null) cancelSearch(exitAfterCancellation = true) else finish()
            }
        })
        LudoProofTheme.configureWindow(this)
        selectedCharacterId = CharacterSelectionStore(this).load().characterId
        // A persisted ticket from a prior Team Up page session must not silently
        // reuse its old elapsed timer or server-side bot fallback deadline. On a
        // genuinely new Activity launch, cancel the old request before starting a
        // fresh search. Configuration/state restoration continues the active search.
        val restoredTicket = ticketStore.load()
        val startFreshSearch = savedInstanceState == null && restoredTicket != null
        validatingRestoredSearch = restoredTicket != null
        restartingRestoredSearch = startFreshSearch
        buildUi()
        renderSearchState()
        if (startFreshSearch) {
            restartRestoredSearch(requireNotNull(restoredTicket))
        }
    }

    override fun onStart() {
        super.onStart()
        stopped = false
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    override fun onStop() {
        stopped = true
        handler.removeCallbacks(pollRunnable)
        super.onStop()
    }

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val (root, host) = LudoProofTheme.arcadeRoot(this)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Fill-viewport content centers the independent setup or searching
            // controls vertically; short displays still scroll as needed.
            // Symmetric clear space reserves the fixed TEAM UP/ONLINE header.
            setPadding(
                dp(18),
                dp(if (isCompactTeamLayout()) 96 else 100),
                dp(18),
                dp(if (isCompactTeamLayout()) 96 else 100),
            )
            clipChildren = false
            clipToPadding = false
            background = null
        }

        // This mode is launched into TeamUpActivity (not MainActivity's
        // Online lobby). Put its title in the fixed Back/Settings bar, like Robot.
        val fixedTeamTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = null
            elevation = 0f
            addView(
                FriendsVisualKit.iconView(
                    this@TeamUpActivity,
                    FriendsIcon.PEOPLE,
                    FriendsVisualKit.TEXT,
                ),
                LinearLayout.LayoutParams(dp(if (isCompactTeamLayout()) 22 else 26),
                    dp(if (isCompactTeamLayout()) 22 else 26)).apply {
                    marginEnd = dp(7)
                },
            )
            addView(
                TextView(this@TeamUpActivity).apply {
                    text = "TEAM UP"
                    LudoProofTheme.title(
                        this,
                        if (isCompactTeamLayout()) 15f else 19f,
                        gold = false,
                    )
                    gravity = Gravity.CENTER_VERTICAL
                    includeFontPadding = false
                    maxLines = 1
                },
            )
        }

        // Connection indicator stays in the scrolling content, independent
        // of the fixed TEAM UP heading, during setup and matchmaking.
        val connectionStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = null
            elevation = 0f
            addView(
                LaserStatusDotView(this@TeamUpActivity),
                LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                    marginEnd = dp(5)
                },
            )
            addView(
                TextView(this@TeamUpActivity).apply {
                    text = "ONLINE"
                    LudoProofTheme.body(this, 10f, centered = true, bright = true)
                    setTextColor(0xFF62E9FF.toInt())
                    includeFontPadding = false
                },
            )
        }
        // Keep LIVE/ONLINE directly below the fixed TEAM UP title, rather
        // than inside vertically centered setup/search components.
        // No oversized teamPanel: setup, search and status are direct
        // independent children of the scrollable page, as in Robot/Pass & Play.
        setupContent = teamSetupPanel()
        content.addView(
            setupContent,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) },
        )

        quickTeamButton = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Find a team online"
            background = playGamesSignInBackground(this@TeamUpActivity)

            val label = TextView(this@TeamUpActivity).apply {
                text = "FIND A TEAM"
                textSize = 18f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )

            val playIconSize = dp(36)
            val playIcon = ImageView(this@TeamUpActivity).apply {
                setImageDrawable(
                    context.getDrawable(com.rekixo.ludopaws.R.drawable.ic_play_triangle_white)?.mutate(),
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                translationX =
                    label.paint.measureText(label.text.toString()) / 2f + dp(6) + playIconSize / 2f
            }
            addView(
                playIcon,
                FrameLayout.LayoutParams(
                    playIconSize,
                    playIconSize,
                    Gravity.CENTER,
                ),
            )
            AppFocusRing.apply(this, cornerRadiusDp = 13f, strokeWidthDp = 3f)
            setOnClickListener { playSelectionPopAnimation { beginSearch() } }
        }
        setupContent.addView(
            quickTeamButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60),
            ).apply { topMargin = dp(14) },
        )

        setupContent.addView(
            TextView(this).apply {
                text = "Find 3 players • Red + Yellow vs Green + Blue"
                LudoProofTheme.body(this, 11.5f, centered = true, bright = true)
                setPadding(dp(8), dp(8), dp(8), 0)
            },
        )

        searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
            // Layout-only searching section, with standalone progress and buttons.
            background = null
            elevation = 0f
            visibility = View.GONE

            addView(
                TextView(this@TeamUpActivity).apply {
                    text = "SEARCHING FOR TEAMMATES"
                    LudoProofTheme.title(this, 18f)
                    gravity = Gravity.CENTER
                },
            )
            addView(
                TextView(this@TeamUpActivity).apply {
                    text = "Finding 3 players for your 2 vs 2 match"
                    LudoProofTheme.body(this, 11f, centered = true, bright = true)
                    setPadding(0, dp(4), 0, 0)
                },
            )
            addView(
                TextView(this@TeamUpActivity).apply {
                    text = SpannableString("RED + YELLOW   ⚡   GREEN + BLUE").apply {
                        fun styleColorWord(
                            word: String,
                            textColor: Int,
                            glowColor: Int,
                        ) {
                            val start = indexOf(word)
                            if (start < 0) return
                            setSpan(
                                object : CharacterStyle() {
                                    override fun updateDrawState(textPaint: TextPaint) {
                                        textPaint.color = textColor
                                        textPaint.setShadowLayer(
                                            this@TeamUpActivity.dp(5).toFloat(),
                                            0f,
                                            0f,
                                            glowColor,
                                        )
                                    }
                                },
                                start,
                                start + word.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }

                        val boltStart = indexOf("⚡")
                        if (boltStart >= 0) {
                            setSpan(
                                object : ReplacementSpan() {
                                    private val density = this@TeamUpActivity.resources.displayMetrics.density
                                    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        color = Color.WHITE
                                        style = Paint.Style.STROKE
                                        strokeCap = Paint.Cap.ROUND
                                        strokeJoin = Paint.Join.ROUND
                                        strokeWidth = 3.2f * density
                                        alpha = 115
                                        setShadowLayer(5f * density, 0f, 0f, Color.WHITE)
                                    }
                                    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        color = Color.WHITE
                                        style = Paint.Style.FILL
                                    }

                                    override fun getSize(
                                        paint: Paint,
                                        text: CharSequence,
                                        start: Int,
                                        end: Int,
                                        fm: Paint.FontMetricsInt?,
                                    ): Int {
                                        // Reserve line height for the enlarged bolt and its glow.
                                        // Without updating the font metrics the lower point can be clipped.
                                        val iconHeight =
                                            maxOf(paint.textSize * 1.35f, 20f * density) * 1.2f
                                        val halfHeight =
                                            (iconHeight * 1.16f / 2f + 6f * density).toInt() + 1
                                        fm?.let {
                                            it.ascent = minOf(it.ascent, -halfHeight)
                                            it.top = minOf(it.top, -halfHeight)
                                            it.descent = maxOf(it.descent, halfHeight)
                                            it.bottom = maxOf(it.bottom, halfHeight)
                                        }
                                        return maxOf(
                                            (paint.textSize * 1.08f).toInt(),
                                            (18f * density).toInt(),
                                        )
                                    }

                                    override fun draw(
                                        canvas: Canvas,
                                        text: CharSequence,
                                        start: Int,
                                        end: Int,
                                        x: Float,
                                        top: Int,
                                        y: Int,
                                        bottom: Int,
                                        paint: Paint,
                                    ) {
                                        val width = getSize(paint, text, start, end, null).toFloat()
                                        // Scale both width and height by 20%, with font metrics
                                        // above reserving room for the complete lightning shape.
                                        val height = maxOf(paint.textSize * 1.35f, 20f * density) * 1.2f
                                        val cx = x + width / 2f
                                        val cy = (top + bottom) / 2f
                                        val bolt = Path().apply {
                                            moveTo(cx + width * 0.10f, cy - height * 0.58f)
                                            lineTo(cx - width * 0.35f, cy + height * 0.02f)
                                            lineTo(cx - width * 0.02f, cy + height * 0.02f)
                                            lineTo(cx - width * 0.16f, cy + height * 0.58f)
                                            lineTo(cx + width * 0.36f, cy - height * 0.14f)
                                            lineTo(cx + width * 0.05f, cy - height * 0.14f)
                                            close()
                                        }
                                        canvas.drawPath(bolt, glowPaint)
                                        canvas.drawPath(bolt, boltPaint)
                                    }
                                },
                                boltStart,
                                boltStart + 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }

                        // Match Pass & Play's color-label colors and soft glow.
                        styleColorWord("RED", 0xFFFF6670.toInt(), 0xFFFF263D.toInt())
                        styleColorWord("YELLOW", 0xFFFFEB70.toInt(), 0xFFFFD324.toInt())
                        styleColorWord("GREEN", 0xFF5BF58A.toInt(), 0xFF23E968.toInt())
                        styleColorWord("BLUE", 0xFF64C4FF.toInt(), 0xFF35AFFF.toInt())
                    }
                    LudoProofTheme.body(this, 9.5f, centered = true, bright = true)
                    setTextColor(0xFFBDEBFF.toInt())
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setPadding(0, dp(7), 0, 0)
                },
            )

            teamSlotProgressView = TeamSlotProgressView(this@TeamUpActivity).apply {
                setQueuedPlayers(1, listOf(selectedCharacterId))
            }
            addView(
                teamSlotProgressView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(108),
                ).apply { topMargin = dp(12) },
            )
            slotsText = TextView(this@TeamUpActivity).apply {
                text = "1 / 4 PLAYERS"
                LudoProofTheme.title(this, 18f)
                gravity = Gravity.CENTER
                setTextColor(0xFF69E8FF.toInt())
                setPadding(dp(8), dp(8), dp(8), dp(10))
            }
            addView(slotsText)

            timerText = TextView(this@TeamUpActivity).apply {
                text = "SEARCH  00:00"
                LudoProofTheme.body(this, 12f, centered = true, bright = true)
            }
            addView(timerText)

            cancelButton = Button(this@TeamUpActivity).apply {
                text = "CANCEL"
                textSize = 14f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                contentDescription = "Cancel team search"

                // Keep this secondary/destructive action compact and consistently red.
                // Clear the theme tint/ripple so it cannot flash a blue state color.
                backgroundTintList = null
                foreground = null
                stateListAnimator = null
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        0xFFD7434B.toInt(),
                        0xFFB62D36.toInt(),
                    ),
                ).apply {
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), 0xFFE56A70.toInt())
                }
                minWidth = dp(108)
                minHeight = dp(40)
                setPadding(dp(22), dp(3), dp(22), dp(3))
                AppFocusRing.apply(this, cornerRadiusDp = 10f, strokeWidthDp = 2f)
                setOnClickListener { playSelectionPopAnimation { cancelSearch() } }
            }
            addView(
                cancelButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(40),
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(12)
                },
            )
        }
        content.addView(
            searchPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        statusText = TextView(this).apply {
            text = "Quick Team is ready."
            LudoProofTheme.body(this, 11.5f, centered = true, bright = true)
            setPadding(dp(8), dp(12), dp(8), 0)
        }
        content.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        scroll.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        host.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val topActionsBar =
            addGameTopActions(
                host = host,
                context = this,
                onBack = { if (ticketStore.load() != null) cancelSearch(exitAfterCancellation = true) else finish() },
                onSettings = {
                    com.rekixo.ludopaws.ArcadeDialogs.showSettings(this)
                },
            ).apply {
                // Match the Online page's reusable plywood skin on Back and Settings.
                for (index in 0 until childCount) {
                    getChildAt(index).background =
                        plywoodBackground(
                            context = this@TeamUpActivity,
                            cornerRadiusDp = 999f,
                        )
                }
                val availableTitleWidth = (
                    resources.displayMetrics.widthPixels - dp(2 * (16 + 52 + 8))
                ).coerceAtLeast(dp(108))
                addView(
                    fixedTeamTitle,
                    FrameLayout.LayoutParams(
                        minOf(availableTitleWidth, dp(if (isCompactTeamLayout()) 194 else 224)),
                        dp(52),
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    ).apply { topMargin = dp(14) },
                )
                addView(
                    connectionStrip,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        dp(20),
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    ).apply { topMargin = dp(70) },
                )
            }
        setContentView(root)
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
                    dp(if (isCompactTeamLayout()) 96 else 100) + safeTopInset,
                    content.paddingRight,
                    content.paddingBottom,
            )
            topActionsBar.translationY = safeTopInset.toFloat()
            insets
        }
        root.requestApplyInsets()
    }

    /**
     * The character retains its own recessed portrait BG, just like Online;
     * the oversized page-level plywood container stays removed.
     */
    private fun teamCharacterTiles(): FrameLayout =
        FrameLayout(this).apply {
            val profileCharacterId =
                LudoPawsCharacterCatalog.canonicalCharacterId(selectedCharacterId)
                    ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
            val character =
                LudoPawsCharacterCatalog.character(profileCharacterId)
                    ?: LudoPawsCharacterCatalog.character(LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID)
                    ?: return@apply

            background = recessedPlywoodBackground(
                context = this@TeamUpActivity,
                outerCornerRadiusDp = 18f,
            )
            setPadding(dp(3), dp(3), dp(3), dp(3))
            contentDescription = "Your selected character: ${character.displayName}"
            addView(
                LudoPawsCharacterAvatarView(this@TeamUpActivity).apply {
                    bind(
                        characterId = character.id,
                        showCircularChrome = false,
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

    private fun teamSetupPanel(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(
                teamCharacterTiles(),
                LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
            addView(
                TextView(this@TeamUpActivity).apply {
                    text = "2 VS 2 ONLINE"
                    LudoProofTheme.title(this, 26f, gold = false)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(7), 0, 0)
                },
            )
            addView(
                TextView(this@TeamUpActivity).apply {
                    text = "PLAY AS PARTNERS. WIN TOGETHER."
                    LudoProofTheme.body(this, 11f, centered = true, bright = true)
                    setTextColor(0xFFBDEBFF.toInt())
                    setPadding(0, dp(3), 0, dp(15))
                },
            )
            val teamCards = LinearLayout(this@TeamUpActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                addView(
                    teamSelectionCard("TEAM A", 0xFFFF5D63.toInt(), 0xFFFFD324.toInt()),
                    LinearLayout.LayoutParams(0, dp(106), 1f).apply {
                        marginEnd = dp(5)
                    },
                )
                addView(
                    teamSelectionCard("TEAM B", 0xFF35C83D.toInt(), 0xFF2A8CFF.toInt()),
                    LinearLayout.LayoutParams(0, dp(106), 1f).apply {
                        marginStart = dp(5)
                    },
                )
            }
            addView(
                teamCards,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(106),
                ).apply {
                    topMargin = dp(2)
                },
            )
        }

    private fun teamSelectionCard(
        title: String,
        firstColor: Int,
        secondColor: Int,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(5), dp(7), dp(5), dp(7))
            // TEAM A and TEAM B keep their own small card backgrounds,
            // without restoring the removed page-level container.
            background = recessedPlywoodBackground(
                context = this@TeamUpActivity,
                outerCornerRadiusDp = 16f,
            )
            addView(
                TeamColorTokenView(this@TeamUpActivity, firstColor, secondColor),
                LinearLayout.LayoutParams(dp(88), dp(42)),
            )
            addView(
                TextView(this@TeamUpActivity).apply {
                    text = title
                    LudoProofTheme.title(this, 13f, gold = true)
                    setTextColor(0xFFFFD45E.toInt())
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, dp(4), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

    private fun beginSearch() {
        if (requestInFlight) return
        ticketStore.load()?.let {
            renderSearchState()
            pollSearch()
            return
        }

        // Refresh the persisted selection so the YOU portrait and matchmaking request agree.
        selectedCharacterId = CharacterSelectionStore(this).load().characterId

        val displayName =
            profileStore.snapshot().displayName
                .trim()
                .replace(Regex("\\s+"), " ")
                .take(24)
                .ifBlank { "Guest User" }
        if (displayName.length < 2) {
            showStatus("Set a valid profile name before Team Up.")
            return
        }

        val ticket = TeamMatchmakingTicket(
            requestId = UUID.randomUUID().toString(),
            displayName = displayName,
            startedAt = System.currentTimeMillis(),
        )
        validatingRestoredSearch = false
        restartingRestoredSearch = false
        runCatching { ticketStore.save(ticket) }
            .onFailure {
                showStatus("Could not save Team Up search. Try again.")
                return
            }

        renderSearchState()
        showStatus("Searching for 3 real players…")
        runRequest(
            action = {
                val credential = ensureLeaderboardCredential()
                api.search(
                    profileToken = credential.profileToken,
                    displayName = ticket.displayName,
                    clientRequestId = ticket.requestId,
                    characterId = selectedCharacterId,
                )
            },
            onSuccess = ::applyMatchmakingResponse,
        )
    }

    private fun restartRestoredSearch(ticket: TeamMatchmakingTicket) {
        if (requestInFlight || executor.isShutdown || isFinishing || isDestroyed) return
        restartingRestoredSearch = true
        validatingRestoredSearch = true
        timerText.text = "SEARCH  00:00"
        showStatus("Resetting the previous Team Up search…")

        runRequest(
            action = {
                val credential = ensureLeaderboardCredential()
                api.cancel(
                    profileToken = credential.profileToken,
                    clientRequestId = ticket.requestId,
                )
            },
            onSuccess = { response ->
                if (response.optString("status") == "MATCHED") {
                    // The previous search won the race with cancellation; retrieve
                    // its authoritative assignment rather than abandoning a match.
                    restartingRestoredSearch = false
                    validatingRestoredSearch = true
                    showStatus("A team was found in the previous search • reconnecting…")
                    pollSearch()
                } else {
                    ticketStore.clear()
                    restartingRestoredSearch = false
                    validatingRestoredSearch = false
                    beginSearch()
                }
            },
            onFailure = {
                // Preserve the ticket if the server could not confirm cancellation;
                // otherwise a new client request could be rejected as already queued.
                restartingRestoredSearch = true
                validatingRestoredSearch = true
                timerText.text = "SEARCH  CHECKING…"
                showStatus("Could not restart the previous search. Check the connection or cancel it and try again.")
            },
        )
    }

    private fun pollSearch() {
        val ticket = ticketStore.load() ?: return
        runRequest(
            quietFailure = true,
            action = {
                val credential = ensureLeaderboardCredential()
                api.status(
                    profileToken = credential.profileToken,
                    clientRequestId = ticket.requestId,
                )
            },
            onSuccess = ::applyMatchmakingResponse,
        )
    }

    private fun cancelSearch(exitAfterCancellation: Boolean = false) {
        val ticket = ticketStore.load()
            ?: run {
                renderSearchState()
                return
            }
        runRequest(
            action = {
                val credential = ensureLeaderboardCredential()
                api.cancel(
                    profileToken = credential.profileToken,
                    clientRequestId = ticket.requestId,
                )
            },
            onSuccess = { response ->
                if (response.optString("status") == "MATCHED") {
                    restartingRestoredSearch = false
                    validatingRestoredSearch = true
                    showStatus(
                        if (response.optBoolean("pending", false)) {
                            "A team match is being finalized and can no longer be cancelled. Reconnecting…"
                        } else {
                            "Match was found while cancelling. Opening it…"
                        },
                    )
                    pollSearch()
                } else {
                    restartingRestoredSearch = false
                    validatingRestoredSearch = false
                    ticketStore.clear()
                    renderSearchState()
                    showStatus("Team search cancelled.")
                    if (exitAfterCancellation) finish()
                }
            },
        )
    }

    private fun applyMatchmakingResponse(response: JSONObject) {
        validatingRestoredSearch = false
        when (response.optString("status")) {
            "MATCHED" -> openMatchedGame(response)
            "FINALIZING" -> {
                val queued = response.optInt("queuedPlayers", 4).coerceIn(1, 4)
                renderQueuedPlayers(queued, queuedCharacterIds(response))
                timerText.text = "FINALIZING…"
                showStatus("Team match reserved • reconnecting to the room…")
            }
            "SEARCHING" -> {
                val queued = response.optInt("queuedPlayers", 1).coerceIn(1, 4)
                renderQueuedPlayers(queued, queuedCharacterIds(response))
                updateTimer()
            }
            "IDLE" -> {
                ticketStore.clear()
                renderSearchState()
                showStatus("Search expired. Tap QUICK TEAM to search again.")
            }
            else -> showStatus("Unexpected Team Up matchmaking response.")
        }
    }

    private fun queuedCharacterIds(response: JSONObject): List<String> {
        val array = response.optJSONArray("queuedCharacterIds")
            ?: return listOf(selectedCharacterId)
        val received = buildList {
            for (index in 0 until minOf(array.length(), 4)) {
                val id = LudoPawsCharacterCatalog.canonicalCharacterId(array.optString(index))
                    ?: continue
                if (LudoPawsCharacterCatalog.character(id) != null) add(id)
            }
        }
        // The server orders the current request first; use the local profile value
        // for slot zero so YOU always matches the character used by this device.
        return buildList {
            add(selectedCharacterId)
            addAll(received.drop(1).take(3))
        }
    }

    private fun openMatchedGame(response: JSONObject) {
        val matchId = response.optString("matchId").trim().uppercase(Locale.ROOT)
        val playerId = response.optString("playerId").trim()
        val playerToken = response.optString("playerToken").trim()
        val matchMode = response.optString("matchMode")
        val state = runCatching { GameJson.envelope(response).state }.getOrNull()
        val assignmentIsValid =
            matchMode == "TEAM_UP" &&
                state != null &&
                state.matchId == matchId &&
                state.matchMode == "TEAM_UP" &&
                state.status == "ACTIVE" &&
                state.targetPlayerCount == 4 &&
                state.players.size == 4 &&
                playerId.isNotBlank() &&
                playerToken.isNotBlank()
        if (!assignmentIsValid) {
            // Do not discard the ticket or persist a partial session. The next
            // matchmaking poll can replay the authoritative assignment safely.
            showStatus("Team match reserved • waiting for a complete verified room snapshot…")
            return
        }

        val saved = runCatching {
            sessionStore.save(
                PlayerSession(
                    matchId = matchId,
                    playerId = playerId,
                    playerToken = playerToken,
                    modeWire = GameMode.TEAM_UP.wireValue,
                ),
            )
            cachedMatchStore.save(
                playerId,
                response.optJSONObject("state"),
            )
        }
        if (saved.isFailure) {
            sessionStore.clear()
            cachedMatchStore.clear()
            showStatus("Could not securely save the Team Up match.")
            return
        }

        ticketStore.clear()
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(
                    GameModeIntent.EXTRA_GAME_MODE,
                    GameMode.TEAM_UP.wireValue,
                )
                .putExtra(
                    GameModeIntent.EXTRA_RESUME_SAVED_MATCH,
                    true,
                ),
        )
        finish()
    }

    private fun ensureLeaderboardCredential(): LeaderboardCredential {
        identityStore.load()?.let { return it }
        val requestId = identityStore.registrationRequestId()
        val response = LeaderboardApi().registerProfile(requestId)
        val credential = LeaderboardCredential(
            profileId = response.getString("profileId"),
            profileToken = response.getString("profileToken"),
            registrationRequestId = requestId,
        )
        identityStore.save(credential)
        return credential
    }

    private fun runRequest(
        action: () -> JSONObject,
        onSuccess: (JSONObject) -> Unit,
        quietFailure: Boolean = false,
        onFailure: ((Exception) -> Unit)? = null,
    ) {
        if (requestInFlight || executor.isShutdown || isFinishing || isDestroyed) return
        requestInFlight = true
        quickTeamButton.isEnabled = false
        if (::cancelButton.isInitialized) cancelButton.isEnabled = false

        val submitted = runCatching {
            executor.execute {
                try {
                    val response = action()
                    handler.post {
                        requestInFlight = false
                        if (isFinishing || isDestroyed) return@post
                        updateButtons()
                        onSuccess(response)
                    }
                } catch (error: Exception) {
                    handler.post {
                        requestInFlight = false
                        if (isFinishing || isDestroyed) return@post
                        updateButtons()
                        if (onFailure != null) {
                            onFailure(error)
                        } else if (!quietFailure) {
                            showStatus(
                                "Team search error: " +
                                    (error.message ?: "connection failed"),
                            )
                        }
                    }
                }
            }
        }.isSuccess
        if (!submitted) {
            requestInFlight = false
            updateButtons()
        }
    }

    private fun renderSearchState() {
        val searching = ticketStore.load() != null
        setupContent.visibility = if (searching) View.GONE else View.VISIBLE
        searchPanel.visibility = if (searching) View.VISIBLE else View.GONE
        if (searching) {
            renderQueuedPlayers(1)
            if (validatingRestoredSearch) {
                timerText.text = "SEARCH  CHECKING..."
            } else {
                updateTimer()
            }
        }
        updateButtons()
    }

    private fun renderQueuedPlayers(
        queued: Int,
        characterIds: List<String> = listOf(selectedCharacterId),
    ) {
        val safe = queued.coerceIn(1, 4)
        slotsText.text = "$safe / 4 PLAYERS"
        teamSlotProgressView.setQueuedPlayers(safe, characterIds)
        if (safe >= 4) {
            showStatus("Match found • assigning opposite-color teams…")
        } else {
            showStatus("Searching • ${4 - safe} more player" + if (4 - safe == 1) "" else "s" + " needed.")
        }
    }

    private class TeamColorTokenView(
        context: android.content.Context,
        private val firstColor: Int,
        private val secondColor: Int,
    ) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xA8FFFFFF.toInt()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return

            // Two glossy color orbs, using the same light-to-dark shading,
            // bright rim, soft shadow and specular shine as Pass & Play colors.
            val radius = minOf(width * 0.25f, height * 0.42f)
            val cy = height * 0.49f
            val firstX = width * 0.27f
            val secondX = width * 0.73f
            drawToken(canvas, firstX, cy, radius, firstColor)
            drawToken(canvas, secondX, cy, radius, secondColor)
        }

        private fun drawToken(canvas: Canvas, cx: Float, cy: Float, radius: Float, baseColor: Int) {
            fill.shader = null
            fill.color = 0x7000183B
            fill.setShadowLayer(dp(3f), 0f, dp(2f), 0x9900102D.toInt())
            canvas.drawCircle(cx, cy + dp(1f), radius * 1.03f, fill)
            fill.clearShadowLayer()

            fill.shader = RadialGradient(
                cx - radius * 0.38f,
                cy - radius * 0.43f,
                radius * 1.55f,
                intArrayOf(
                    blendWithWhite(baseColor, 0.68f),
                    baseColor,
                    blendWithBlack(baseColor, 0.48f),
                ),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, fill)
            fill.shader = null

            stroke.color = 0xDFFFFFFF.toInt()
            stroke.strokeWidth = dp(1.2f)
            canvas.drawCircle(cx, cy, radius, stroke)

            shine.alpha = 200
            canvas.drawOval(
                RectF(
                    cx - radius * 0.62f,
                    cy - radius * 0.67f,
                    cx - radius * 0.02f,
                    cy - radius * 0.30f,
                ),
                shine,
            )
            shine.alpha = 95
            canvas.drawCircle(
                cx - radius * 0.48f,
                cy - radius * 0.43f,
                radius * 0.09f,
                shine,
            )
        }

        private fun blendWithWhite(color: Int, amount: Float): Int =
            Color.rgb(
                (Color.red(color) + (255 - Color.red(color)) * amount).toInt(),
                (Color.green(color) + (255 - Color.green(color)) * amount).toInt(),
                (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt(),
            )

        private fun blendWithBlack(color: Int, amount: Float): Int =
            Color.rgb(
                (Color.red(color) * (1f - amount)).toInt(),
                (Color.green(color) * (1f - amount)).toInt(),
                (Color.blue(color) * (1f - amount)).toInt(),
            )

        private fun dp(value: Float): Float = value * resources.displayMetrics.density
    }

    /**
     * Shows the server-confirmed queue as a sequential connection animation:
     * YOU -> next open slot. The next slot's ring orbits while we wait. When the
     * server confirms another queued player, that slot locks in and the scan
     * extends from it toward the following slot.
     */
    private class TeamSlotProgressView(context: android.content.Context) : FrameLayout(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val playerColors =
            intArrayOf(
                0xFFFF5D63.toInt(),
                0xFFFFD324.toInt(),
                0xFF35C83D.toInt(),
                0xFF2A8CFF.toInt(),
            )
        private var queuedPlayers = 1
        private var queuedCharacterIds: List<String> = emptyList()
        private var approachStartedAt = 0L
        private val characterViews = Array(4) {
            LudoPawsCharacterAvatarView(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                bind(
                    characterId = LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                    showCircularChrome = false,
                )
                visibility = View.INVISIBLE
            }
        }

        init {
            setWillNotDraw(false)
            characterViews.forEach { avatar ->
                addView(
                    avatar,
                    FrameLayout.LayoutParams(dp(29f).toInt(), dp(29f).toInt()),
                )
            }
        }

        fun setQueuedPlayers(value: Int, characterIds: List<String>) {
            val next = value.coerceIn(1, 4)
            val safeCharacterIds = characterIds.take(4)
            val previousQueuedPlayers = queuedPlayers
            val previousCharacterIds = queuedCharacterIds
            if (
                approachStartedAt == 0L ||
                next != queuedPlayers ||
                safeCharacterIds != queuedCharacterIds
            ) {
                approachStartedAt = SystemClock.uptimeMillis()
            }
            queuedPlayers = next
            queuedCharacterIds = safeCharacterIds

            characterViews.forEachIndexed { index, avatar ->
                val characterId = safeCharacterIds.getOrNull(index)
                if (index < next && characterId != null) {
                    val newlyFilledSlot =
                        index >= previousQueuedPlayers ||
                            previousCharacterIds.getOrNull(index) != characterId
                    avatar.visibility = View.VISIBLE
                    avatar.bind(
                        characterId = characterId,
                        ringColor = 0xFF62E8FF.toInt(),
                        showCircularChrome = false,
                    )
                    if (newlyFilledSlot) avatar.playSelectionPopAnimation()
                } else {
                    avatar.visibility = View.INVISIBLE
                }
            }
            requestLayout()
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (queuedPlayers < 4 && visibility == View.VISIBLE) {
                if (approachStartedAt == 0L) {
                    approachStartedAt = SystemClock.uptimeMillis()
                }
                postInvalidateOnAnimation()
            }
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            if (visibility == View.VISIBLE && queuedPlayers < 4 && isAttachedToWindow) {
                if (approachStartedAt == 0L) {
                    approachStartedAt = SystemClock.uptimeMillis()
                }
                postInvalidateOnAnimation()
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val inset = dp(5f)
            val contentRight = width - inset
            val spacing = (contentRight - inset) / 4f
            val centerY = height * 0.39f
            val avatarSize = dp(29f).toInt()
            for (index in characterViews.indices) {
                val centerX = inset + spacing * (index + 0.5f)
                val childLeft = (centerX - avatarSize / 2f).toInt()
                val childTop = (centerY - avatarSize / 2f).toInt()
                characterViews[index].layout(
                    childLeft,
                    childTop,
                    childLeft + avatarSize,
                    childTop + avatarSize,
                )
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return

            val now = SystemClock.uptimeMillis()
            if (approachStartedAt == 0L) approachStartedAt = now

            val inset = dp(5f)
            val left = inset
            val right = width - inset
            val spacing = (right - left) / 4f
            val radius = minOf(dp(19f), height * 0.25f)
            val cy = height * 0.39f
            val centers = FloatArray(4) { index -> left + spacing * (index + 0.5f) }

            val targetIndex = queuedPlayers
            val sourceIndex = (queuedPlayers - 1).coerceIn(0, 3)
            val isSearching = queuedPlayers < 4 && isAttachedToWindow && visibility == View.VISIBLE
            val elapsed = (now - approachStartedAt).coerceAtLeast(0L)
            val orbitRadius = radius + dp(1f)
            val isDrawingSourceRing =
                isSearching && queuedPlayers == 1 && elapsed < SOURCE_RING_DURATION_MS
            val sourceRingDuration = if (queuedPlayers == 1) SOURCE_RING_DURATION_MS else 0L
            val travelElapsed = (elapsed - sourceRingDuration).coerceAtLeast(0L)

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(3f)
            for (index in 0..2) {
                paint.color = when {
                    index < queuedPlayers - 1 -> 0xFFFFD34A.toInt()
                    index == queuedPlayers - 1 && queuedPlayers < 4 -> 0x6657DFFF
                    else -> 0x665D87B4
                }
                canvas.drawLine(centers[index], cy, centers[index + 1], cy, paint)
            }

            // Confirmed players get their production character portraits as child views.
            // Empty slots retain a neutral placeholder silhouette only.
            for (index in 0..3) {
                val x = centers[index]
                val isQueued = index < queuedPlayers
                val isTarget = isSearching && index == targetIndex
                paint.style = Paint.Style.FILL
                paint.color = if (isQueued) playerColors[index] else 0xFF132A4A.toInt()
                canvas.drawCircle(x, cy, radius, paint)

                // Queued and currently active profiles use the cyan outer path
                // as their outline. Avoid a second, smaller static ring underneath.
                val hasCompletedOuterRing =
                    index < queuedPlayers &&
                        !(queuedPlayers == 1 && index == 0 && isDrawingSourceRing)
                val isDrawingThisSourceRing = isDrawingSourceRing && index == sourceIndex
                if (!isTarget && !hasCompletedOuterRing && !isDrawingThisSourceRing) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(2f)
                    paint.color = 0xFF8CA9C7.toInt()
                    canvas.drawCircle(x, cy, radius, paint)
                }

                if (!isQueued || queuedCharacterIds.getOrNull(index) == null) {
                    drawPlayerPlaceholder(canvas, x, cy, radius, isQueued)
                }
            }

            // Completed connectors stay painted edge-to-edge as more players join.
            // This is intentionally cumulative: prior links are never cleared.
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(2.8f)
            paint.color = 0xFF59DEFF.toInt()
            for (index in 0 until (queuedPlayers - 1).coerceAtLeast(0)) {
                canvas.drawLine(
                    centers[index] + orbitRadius,
                    cy,
                    centers[index + 1] - orbitRadius,
                    cy,
                    paint,
                )
            }

            // Keep every confirmed profile's outer ring permanently visible.
            for (index in 0 until queuedPlayers) {
                val isFirstRingStillDrawing =
                    isSearching && queuedPlayers == 1 && index == 0 && isDrawingSourceRing
                if (!isFirstRingStillDrawing) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = dp(2.8f)
                    paint.color = 0xFF59DEFF.toInt()
                    canvas.drawCircle(centers[index], cy, orbitRadius, paint)
                }
            }

            if (isSearching) {
                val sourceX = centers[sourceIndex]
                val targetX = centers[targetIndex]
                val sourceEdgeX = sourceX + orbitRadius
                val targetEdgeX = targetX - orbitRadius
                val sourceBounds = android.graphics.RectF(
                    sourceX - orbitRadius,
                    cy - orbitRadius,
                    sourceX + orbitRadius,
                    cy + orbitRadius,
                )
                val targetBounds = android.graphics.RectF(
                    targetX - orbitRadius,
                    cy - orbitRadius,
                    targetX + orbitRadius,
                    cy + orbitRadius,
                )

                val headX: Float
                val headY: Float

                if (queuedPlayers == 1 && isDrawingSourceRing) {
                    // Start by drawing the outer ring around YOU. Its moving
                    // white pin finishes at the right edge, where the line leaves.
                    val progress = (elapsed / SOURCE_RING_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                    val headAngle = -360f * progress
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = dp(2.8f)
                    paint.color = 0xFF59DEFF.toInt()
                    if (progress > 0f) {
                        canvas.drawArc(sourceBounds, 0f, headAngle, false, paint)
                    }
                    val radians = Math.toRadians(headAngle.toDouble())
                    headX = sourceX + (kotlin.math.cos(radians) * orbitRadius).toFloat()
                    headY = cy + (kotlin.math.sin(radians) * orbitRadius).toFloat()
                } else if (travelElapsed < APPROACH_DURATION_MS) {
                    val progress = (travelElapsed / APPROACH_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                    headX = sourceEdgeX + (targetEdgeX - sourceEdgeX) * progress
                    headY = cy

                    // The growing line begins exactly where the source ring ends.
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = dp(2.8f)
                    paint.color = 0xFF59DEFF.toInt()
                    canvas.drawLine(sourceEdgeX, cy, headX, cy, paint)
                } else {
                    // Once the pin reaches the next profile, keep the entire
                    // connector and orbit the same pin around the target edge.
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = dp(2.8f)
                    paint.color = 0xFF59DEFF.toInt()
                    canvas.drawLine(sourceEdgeX, cy, targetEdgeX, cy, paint)

                    val orbitElapsed = travelElapsed - APPROACH_DURATION_MS
                    val orbitProgress =
                        (orbitElapsed % ORBIT_DURATION_MS).toFloat() / ORBIT_DURATION_MS.toFloat()
                    val headAngle = 180f + orbitProgress * 360f
                    val sweep = 104f
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = dp(2.8f)
                    paint.color = 0xFF59DEFF.toInt()
                    canvas.drawArc(targetBounds, headAngle - sweep, sweep, false, paint)

                    val radians = Math.toRadians(headAngle.toDouble())
                    headX = targetX + (kotlin.math.cos(radians) * orbitRadius).toFloat()
                    headY = cy + (kotlin.math.sin(radians) * orbitRadius).toFloat()
                }

                // One luminous pin is shared by the starting ring, connector,
                // and target orbit—there is no detached duplicate dot.
                paint.style = Paint.Style.FILL
                paint.color = 0x8859DEFF.toInt()
                canvas.drawCircle(headX, headY, dp(5f), paint)
                paint.color = 0xFFE5FCFF.toInt()
                canvas.drawCircle(headX, headY, dp(2.3f), paint)
            }

            for (index in 0..3) {
                paint.color = if (index < queuedPlayers) 0xFFDDF4FF.toInt() else 0xFF8CA9C7.toInt()
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = dp(8.5f)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(
                    if (index == 0) "YOU" else if (index < queuedPlayers) "PLAYER ${index + 1}" else "OPEN",
                    centers[index],
                    height - dp(13f),
                    paint,
                )
            }

            if (isSearching) postInvalidateOnAnimation()
        }

        private fun drawPlayerPlaceholder(
            canvas: Canvas,
            x: Float,
            y: Float,
            radius: Float,
            isQueued: Boolean,
        ) {
            paint.color = if (isQueued) 0xFFFFF4D1.toInt() else 0xFF8CA9C7.toInt()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y - radius * 0.22f, radius * 0.22f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2.5f)
            canvas.drawArc(
                x - radius * 0.48f,
                y - radius * 0.02f,
                x + radius * 0.48f,
                y + radius * 0.72f,
                200f,
                140f,
                false,
                paint,
            )
            paint.style = Paint.Style.FILL
        }

        private fun dp(value: Float): Float = value * resources.displayMetrics.density

        private companion object {
            const val SOURCE_RING_DURATION_MS = 420L
            const val APPROACH_DURATION_MS = 850L
            const val ORBIT_DURATION_MS = 1_250L
        }
    }

    private fun updateTimer() {
        if (!::timerText.isInitialized) return
        val startedAt = ticketStore.load()?.startedAt ?: return
        val elapsed = ((System.currentTimeMillis() - startedAt) / 1_000L).coerceAtLeast(0L)
        timerText.text = String.format(
            Locale.US,
            "SEARCH  %02d:%02d",
            elapsed / 60L,
            elapsed % 60L,
        )
    }

    private fun updateButtons() {
        val searching = ticketStore.load() != null
        quickTeamButton.isEnabled = !requestInFlight && !searching
        if (::cancelButton.isInitialized) {
            cancelButton.isEnabled = !requestInFlight && searching
        }
    }

    private fun showStatus(value: String) {
        statusText.text = value
    }

    private fun isCompactTeamLayout(): Boolean =
        LudoProofTheme.isCompactWidth(this)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val POLL_MS = 2_000L
    }
}
