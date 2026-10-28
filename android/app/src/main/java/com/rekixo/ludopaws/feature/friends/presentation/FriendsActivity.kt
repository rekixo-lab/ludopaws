package com.rekixo.ludopaws.feature.friends.presentation

import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.ConnectivityMonitor
import com.rekixo.ludopaws.ArcadeDialogs
import com.rekixo.ludopaws.GameApi
import com.rekixo.ludopaws.GameApiException
import com.rekixo.ludopaws.GameJson
import com.rekixo.ludopaws.GameMode
import com.rekixo.ludopaws.GameModeIntent
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.components.playSelectionPopAnimation
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.LaserStatusDotView
import com.rekixo.ludopaws.MainActivity
import com.rekixo.ludopaws.PendingOperationStore
import com.rekixo.ludopaws.PlayerSession
import com.rekixo.ludopaws.SecureSessionStore
import com.rekixo.ludopaws.feature.friends.data.local.FriendCredential
import com.rekixo.ludopaws.feature.friends.data.local.FriendIdentityStore
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.ui.game.addGameTopActions
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class FriendsActivity : Activity() {
    private val api by lazy { GameApi() }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val identityStore by lazy { FriendIdentityStore(this) }
    private val profileStore by lazy { ProfileStore(this) }
    private val sessionStore by lazy { SecureSessionStore(this) }
    private val pendingOperationStore by lazy { PendingOperationStore(this) }

    private var credential: FriendCredential? = null
    private var snapshot: JSONObject? = null
    private var isOnline = true

    @Volatile
    private var requestInFlight = false

    private var selectedPlayerCount = 2
    private var friendQuery = ""

    private lateinit var friendIdText: TextView
    private lateinit var addFriendInput: EditText
    private lateinit var roomContainer: LinearLayout
    private lateinit var inboxContainer: LinearLayout
    private lateinit var friendsContainer: LinearLayout
    private lateinit var statusText: TextView

    private val connectivityMonitor by lazy {
        ConnectivityMonitor(this) { online ->
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                val changed = isOnline != online
                isOnline = online
                if (!online) {
                    showStatus("Offline — friends and invites will refresh when internet returns.")
                } else if (changed) {
                    syncFriends(announce = false)
                }
            }
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed && isOnline) {
                syncFriends(announce = false)
            }
            mainHandler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        credential = identityStore.load()
        buildUi()
        renderAll()
    }

    override fun onStart() {
        super.onStart()
        connectivityMonitor.start()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
    }

    override fun onStop() {
        connectivityMonitor.stop()
        mainHandler.removeCallbacks(refreshRunnable)
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val (root, host) = LudoProofTheme.arcadeRoot(this)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val contentHost = FrameLayout(this).apply {
            // Symmetric reserved space avoids overlap with the fixed
            // Friends/ONLINE header without pinning the sections to the top.
            setPadding(0, dp(96), 0, dp(26))
        }
        scroll.addView(
            contentHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val horizontalPadding = dp(if (isCompact()) 14 else 22)
        val width = minOf(
            resources.displayMetrics.widthPixels - horizontalPadding * 2,
            dp(LudoProofTheme.pageMaxContentWidthDp(this)),
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(6), dp(6), dp(6), dp(22))
            // Like Robot/Pass & Play, the page itself has no enclosing card.
            background = null
            elevation = 0f
        }
        // The invisible host centers short Friends sections on the device.
        // Long friend lists can still grow naturally and scroll from the top.
        val contentLayoutParams = FrameLayout.LayoutParams(
            width,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )
        contentHost.addView(content, contentLayoutParams)
        host.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        // The title and live ONLINE indicator are attached to the fixed
        // Back/Settings bar, never repeated among scrollable Friends sections.
        // Independent groups with consistent spacing instead of cramped rows.
        content.addView(buildIdentitySection(), FriendsVisualKit.sectionGap(this, 10))
        content.addView(buildAddFriendSection(), FriendsVisualKit.sectionGap(this, 14))

        roomContainer = FriendsVisualKit.flatSection(this)
        content.addView(roomContainer, FriendsVisualKit.sectionGap(this, 20))

        inboxContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(inboxContainer, FriendsVisualKit.sectionGap(this, 18))

        friendsContainer = FriendsVisualKit.flatSection(this)
        content.addView(friendsContainer, FriendsVisualKit.sectionGap(this, 20))

        statusText = FriendsVisualKit.body(
            this,
            "Invite friends and start a private match.",
            sizeSp = 11f,
            muted = true,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        content.addView(statusText, FriendsVisualKit.sectionGap(this, 13))
        val topActionsBar =
            addGameTopActions(
                host = host,
                context = this,
                onBack = { finish() },
                onSettings = { ArcadeDialogs.showSettings(this) },
            ).apply {
                for (index in 0 until childCount) {
                    getChildAt(index).background =
                        plywoodBackground(context = this@FriendsActivity, cornerRadiusDp = 999f)
                }

                // The 52dp title row matches Robot, Online and Team Up:
                // centered between the fixed Back and Settings buttons.
                val availableTitleWidth = (
                    resources.displayMetrics.widthPixels - dp(2 * (16 + 52 + 8))
                ).coerceAtLeast(dp(108))
                addView(
                    buildFixedFriendsTitle(),
                    FrameLayout.LayoutParams(
                        minOf(availableTitleWidth, dp(if (isCompact()) 194 else 224)),
                        dp(52),
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    ).apply { topMargin = dp(14) },
                )
                addView(
                    buildFixedFriendsConnectionStatus(),
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
            // Center short content below the fixed header; long lists scroll.
            contentHost.setPadding(
                contentHost.paddingLeft,
                dp(96) + safeTopInset,
                contentHost.paddingRight,
                dp(26),
            )
            topActionsBar.translationY = safeTopInset.toFloat()
            insets
        }
        root.requestApplyInsets()
    }

    /** Fixed center title in the same row as Back and Settings. */
    private fun buildFixedFriendsTitle(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = null
            elevation = 0f
            addView(
                FriendsVisualKit.iconView(
                    this@FriendsActivity,
                    FriendsIcon.PEOPLE,
                    FriendsVisualKit.TEXT,
                ),
                LinearLayout.LayoutParams(
                    dp(if (isCompact()) 28 else 32),
                    dp(if (isCompact()) 28 else 32),
                ).apply { marginEnd = dp(8) },
            )
            addView(
                TextView(this@FriendsActivity).apply {
                    text = "FRIENDS"
                    LudoProofTheme.title(
                        this,
                        if (isCompact()) 20f else 23f,
                        gold = false,
                    )
                    gravity = Gravity.CENTER_VERTICAL
                    letterSpacing = 0.025f
                    includeFontPadding = false
                    maxLines = 1
                },
            )
        }

    /** Blinking connection dot/status stays directly beneath the fixed title. */
    private fun buildFixedFriendsConnectionStatus(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = null
            elevation = 0f
            addView(
                LaserStatusDotView(this@FriendsActivity),
                LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                    marginEnd = dp(5)
                },
            )
            addView(
                FriendsVisualKit.body(this@FriendsActivity, "ONLINE", sizeSp = 10.5f).apply {
                    setTextColor(0xFFD4FFB2.toInt())
                    includeFontPadding = false
                },
            )
        }


    private fun buildIdentitySection(): View {
        val section = FriendsVisualKit.flatSection(this)
        val idRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        idRow.addView(
            FriendsVisualKit.iconView(this, FriendsIcon.ID_CARD, FriendsVisualKit.GOLD),
            LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) },
        )
        val idLabel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(FriendsVisualKit.title(this@FriendsActivity, "YOUR FRIEND ID", 12.5f))
        }
        friendIdText = FriendsVisualKit.title(
            this,
            credential?.friendId ?: "CREATING…",
            sizeSp = if (isCompact()) 14f else 16f,
            gold = true,
        ).apply {
            maxLines = 1
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }
        idLabel.addView(friendIdText)
        idRow.addView(idLabel, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
        ))
        section.addView(idRow)

        // The ID is no longer squeezed between icon, COPY and SHARE.
        // These standalone actions are equally wide in a second row.
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(
            FriendsVisualKit.button(this, "COPY", FriendsButtonStyle.GREEN, FriendsIcon.COPY) {
                copyFriendId()
            }.apply { textSize = 12f },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) },
        )
        actions.addView(
            FriendsVisualKit.button(this, "SHARE", FriendsButtonStyle.BLUE, FriendsIcon.SHARE) {
                shareFriendId()
            }.apply { textSize = 12f },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) },
        )
        section.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )
        return section
    }


    private fun buildAddFriendSection(): View {
        val section = FriendsVisualKit.flatSection(this)
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(
            FriendsVisualKit.iconView(this, FriendsIcon.ADD_FRIEND, 0xFFD5F1FF.toInt()),
            LinearLayout.LayoutParams(dp(29), dp(29)).apply { marginEnd = dp(9) },
        )
        heading.addView(
            FriendsVisualKit.title(this, "ADD FRIEND", if (isCompact()) 13.5f else 15f),
        )
        section.addView(heading)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addFriendInput = FriendsVisualKit.input(this, "Enter Friend ID").apply {
            setHintTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(12), 0, dp(12), 0)
        }
        inputRow.addView(
            addFriendInput, LinearLayout.LayoutParams(0, dp(46), 1f),
        )
        inputRow.addView(
            FriendsVisualKit.button(
                this, "SEND", FriendsButtonStyle.ORANGE, FriendsIcon.INVITE,
            ) { sendFriendRequest() },
            LinearLayout.LayoutParams(dp(if (isCompact()) 88 else 104), dp(46))
                .apply { marginStart = dp(9) },
        )
        section.addView(
            inputRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(9) },
        )
        return section
    }

    private fun renderAll() {
        if (::friendIdText.isInitialized) {
            friendIdText.text = credential?.friendId ?: "CREATING…"
        }
        renderRoom()
        renderInboxCards()
        renderFriends()
        if (credential == null) ensureIdentity()
    }

    private fun renderRoom() {
        roomContainer.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                FriendsVisualKit.iconView(this@FriendsActivity, FriendsIcon.HOUSE, 0xFFFFA22A.toInt()),
                LinearLayout.LayoutParams(dp(25), dp(25)).apply { marginEnd = dp(6) },
            )
            addView(
                FriendsVisualKit.title(this@FriendsActivity, "PRIVATE ROOM", if (isCompact()) 14f else 16f),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        roomContainer.addView(header)

        val session = activeFriendSession()
        if (session != null) {
            roomContainer.addView(
                FriendsVisualKit.title(this, "ACTIVE  •  ${session.matchId}", 11f, gold = true),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(5) },
            )
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(
                FriendsVisualKit.button(
                    this,
                    "OPEN ROOM",
                    FriendsButtonStyle.GREEN,
                    FriendsIcon.PLAY,
                ) { openFriendRoom() },
                LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(3) },
            )
            buttons.addView(
                FriendsVisualKit.button(
                    this,
                    "SHARE ROOM",
                    FriendsButtonStyle.BLUE,
                    FriendsIcon.SHARE,
                ) { shareRoom(session.matchId) },
                LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(3) },
            )
            roomContainer.addView(
                buttons,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(5) },
            )
            return
        }

        val countRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(2, 3, 4).forEachIndexed { index, count ->
            val selected = selectedPlayerCount == count
            val playerCountButton = FriendsVisualKit.button(
                this,
                count.toString(),
                if (selected) FriendsButtonStyle.GREEN else FriendsButtonStyle.BLUE,
            ) {}.apply {
                // These are the compact PRIVATE ROOM size controls (not the
                // separate Offline setup tiles). Reuse Online's recessed timber
                // and gold selected treatment, with a small top-end check badge
                // so the selected option is recognizable at this compact size.
                background = playerCountSelectionBackground(
                    context = this@FriendsActivity,
                    selected = selected,
                    outerCornerRadiusDp = 14f,
                    checkBadgeAtTopEnd = true,
                    emphasizeSelected = true,
                    showCheckBadge = true,
                )
                setTextColor(if (selected) 0xFFFFD45E.toInt() else Color.WHITE)
                setShadowLayer(2f, 0f, dp(1).toFloat(), 0xA6000000.toInt())
                elevation = dp(if (selected) 6 else 4).toFloat()
                isSelected = selected
                contentDescription =
                    if (selected) "$count players, selected" else "$count players"
            }
            playerCountButton.setOnClickListener {
                selectedPlayerCount = count
                playerCountButton.playSelectionPopAnimation { renderRoom() }
            }
            countRow.addView(
                playerCountButton,
                LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    if (index > 0) marginStart = dp(6)
                },
            )
        }
        countRow.addView(
            FriendsVisualKit.button(
                this,
                "CREATE ROOM",
                FriendsButtonStyle.PURPLE,
                FriendsIcon.HOUSE,
            ) { createFriendRoom() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(9) },
        )
        roomContainer.addView(
            countRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6) },
        )
    }

    private fun renderInboxCards() {
        inboxContainer.removeAllViews()
        val incomingCount = snapshot?.optJSONArray("incomingRequests")?.length() ?: 0
        val inviteCount = snapshot?.optJSONArray("invites")?.length() ?: 0
        inboxContainer.addView(
            inboxAction(
                title = "FRIEND REQUESTS",
                summary = if (incomingCount == 0) "No pending" else "$incomingCount pending",
                icon = FriendsIcon.REQUESTS,
                count = incomingCount,
                accent = FriendsVisualKit.CYAN,
            ) { showRequestsDialog() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(5)
            },
        )
        inboxContainer.addView(
            inboxAction(
                title = "ROOM INVITES",
                summary = if (inviteCount == 0) "No invites" else "$inviteCount waiting",
                icon = FriendsIcon.INVITES,
                count = inviteCount,
                accent = 0xFF39E73B.toInt(),
            ) { showInvitesDialog() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(5)
            },
        )
    }

    private fun inboxAction(
        title: String,
        summary: String,
        icon: FriendsIcon,
        count: Int,
        accent: Int,
        onClick: () -> Unit,
    ): View = FriendsVisualKit.flatSection(this).apply {
        // Requests and invites are interactive buttons, not wooden section cards.
        background = FriendsVisualKit.rowSurface(this@FriendsActivity)
        setPadding(dp(9), dp(10), dp(9), dp(10))
        isClickable = true
        isFocusable = true
        AppFocusRing.apply(this, cornerRadiusDp = 18f, strokeWidthDp = 2f)
        setOnClickListener { onClick() }
        val row = LinearLayout(this@FriendsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconFrame = FrameLayout(this@FriendsActivity)
        iconFrame.addView(
            FriendsVisualKit.iconView(this@FriendsActivity, icon, accent),
            FrameLayout.LayoutParams(dp(29), dp(29), Gravity.CENTER),
        )
        if (count > 0) {
            iconFrame.addView(
                FriendsVisualKit.badge(this@FriendsActivity, count),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    dp(18),
                    Gravity.TOP or Gravity.END,
                ),
            )
        }
        row.addView(iconFrame, LinearLayout.LayoutParams(dp(34), dp(34)))
        val textBox = LinearLayout(this@FriendsActivity).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(FriendsVisualKit.title(this@FriendsActivity, title, if (isCompact()) 10f else 11.5f))
        textBox.addView(
            FriendsVisualKit.body(this@FriendsActivity, summary, 10f, muted = true),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(2) },
        )
        row.addView(textBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (!isCompact()) row.addView(FriendsVisualKit.title(this@FriendsActivity, "›", 28f))
        addView(row)
    }

    private fun renderFriends() {
        while (friendsContainer.childCount > 1) friendsContainer.removeViewAt(friendsContainer.childCount - 1)
        val allFriends = snapshot?.optJSONArray("friends").objects()
        val recentPlayers = snapshot
            ?.optJSONObject("_recent")
            ?.optJSONArray("recent")
            .objects()
            .take(MAX_RECENT_VISIBLE)
        val recentById = recentPlayers
            .mapNotNull { player ->
                player.optString("opponentFriendId")
                    .takeIf(String::isNotBlank)
                    ?.let { it to player }
            }
            .toMap()
        val acceptedFriendIds = allFriends.map { it.optString("friendId") }.toSet()
        val blockedArray = snapshot?.optJSONArray("blockedFriends")
        val blockedIds = (0 until (blockedArray?.length() ?: 0))
            .map { blockedArray?.optString(it).orEmpty() }.filter(String::isNotBlank).toSet()
        val recentFriends = recentPlayers.filter { player ->
            val id = player.optString("opponentFriendId")
            id.isNotBlank() && id !in acceptedFriendIds && id !in blockedIds
        }
        val query = friendQuery.trim().lowercase()
        val friends = allFriends
            .filter { friend ->
                query.isBlank() ||
                    friend.optString("displayName").lowercase().contains(query) ||
                    friend.optString("friendId").lowercase().contains(query)
            }
            .sortedWith(
                compareByDescending<JSONObject> { it.optBoolean("online", false) }
                    .thenBy { it.optString("displayName").lowercase() },
            )
        val filteredRecent = recentFriends.filter { player ->
            query.isBlank() ||
                player.optString("displayName").lowercase().contains(query) ||
                player.optString("opponentFriendId").lowercase().contains(query)
        }


        if (friendsContainer.childCount == 0) {
            // Full-width search beneath a clear heading; no narrow compact
            // input or oversized parent container.
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            heading.addView(
                FriendsVisualKit.iconView(this, FriendsIcon.PEOPLE, 0xFFDDF5FF.toInt()),
                LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) },
            )
            heading.addView(
                FriendsVisualKit.title(
                    this, "FRIENDS & RECENT", if (isCompact()) 13f else 15f,
                ),
            )
            header.addView(heading)
            val search = FriendsVisualKit.input(this, "Search friends…").apply {
                setHintTextColor(Color.WHITE)
                textSize = 13f
                setText(friendQuery)
                setSelection(text.length)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        val next = s?.toString().orEmpty()
                        if (next != friendQuery) {
                            friendQuery = next
                            renderFriends()
                        }
                    }
                })
            }
            search.setPadding(
                search.paddingLeft, search.paddingTop, dp(33), search.paddingBottom,
            )
            val searchInputWithIcon = FrameLayout(this).apply {
                addView(
                    search,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    FriendsVisualKit.iconView(
                        this@FriendsActivity, FriendsIcon.SEARCH, Color.WHITE,
                    ).apply {
                        isClickable = false
                        isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    FrameLayout.LayoutParams(
                        dp(18),
                        dp(18),
                        Gravity.END or Gravity.CENTER_VERTICAL,
                    ).apply { marginEnd = dp(11) },
                )
            }
            header.addView(
                searchInputWithIcon,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44),
                ).apply { topMargin = dp(9) },
            )
            friendsContainer.addView(header)
        }
        renderBlockedPlayers()

        if (friends.isEmpty() && filteredRecent.isEmpty()) {
            friendsContainer.addView(
                FriendsVisualKit.body(
                    this,
                    if (friendQuery.isBlank()) {
                        "No friends or recent players yet. Share your Friend ID or add someone above."
                    } else {
                        "No friends or recent players match your search."
                    },
                    11f,
                    muted = true,
                ).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(40),
                ).apply { topMargin = dp(5) },
            )
            return
        }

        friends.forEach { friend ->
            friendsContainer.addView(
                buildFriendRow(friend, recentById[friend.optString("friendId")]),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(5) },
            )
        }

        if (filteredRecent.isNotEmpty()) {
            val recentHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    FriendsVisualKit.iconView(this@FriendsActivity, FriendsIcon.CLOCK, FriendsVisualKit.GOLD),
                    LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(5) },
                )
                addView(
                    FriendsVisualKit.title(this@FriendsActivity, "PLAYED WITH  •  SEND A REQUEST TO ADD", 9.5f),
                )
            }
            friendsContainer.addView(
                recentHeader,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(if (friends.isEmpty()) 6 else 10) },
            )
            filteredRecent.forEach { player ->
                friendsContainer.addView(
                    buildRecentRow(player),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(5) },
                )
            }
        }
    }

    private fun renderBlockedPlayers() {
        val blocked = snapshot?.optJSONArray("blockedFriends") ?: return
        if (blocked.length() == 0) return
        friendsContainer.addView(FriendsVisualKit.title(this, "BLOCKED PLAYERS", 10f))
        for (i in 0 until blocked.length()) {
            val blockedId = blocked.optString(i)
            if (!FRIEND_ID.matches(blockedId)) continue
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(4), dp(6), dp(4))
                background = FriendsVisualKit.rowSurface(this@FriendsActivity)
                addView(
                    FriendsVisualKit.body(this@FriendsActivity, blockedId, 9.5f),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    FriendsVisualKit.button(
                        this@FriendsActivity, "UNBLOCK", FriendsButtonStyle.BLUE, FriendsIcon.MORE,
                    ) { unblockFriend(blockedId) },
                    LinearLayout.LayoutParams(dp(95), dp(34)),
                )
            }
            friendsContainer.addView(row)
        }
    }

    private fun buildFriendRow(friend: JSONObject, recent: JSONObject? = null): View {
        val online = friend.optBoolean("online", false)
        val friendId = friend.optString("friendId")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = FriendsVisualKit.rowSurface(this@FriendsActivity)
        }
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        identity.addView(
            FriendsVisualKit.avatar(this, friendId, online),
            LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(6) },
        )
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(FriendsVisualKit.title(this, friend.optString("displayName", "Friend"), 12f))
        val presence = FriendsVisualKit.body(
            this,
            if (online) "● Online" else "● ${formatLastSeen(friend.optLong("lastSeenAt", 0L))}",
            9f,
            muted = !online,
        ).apply { if (online) setTextColor(FriendsVisualKit.GREEN) }
        text.addView(presence, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(1) })
        recent?.let { player ->
            text.addView(
                FriendsVisualKit.body(this, "◷ ${formatPlayed(player.optLong("playedAt", 0L))}", 8f, muted = true),
            )
        }
        identity.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(identity, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(
            FriendsVisualKit.button(
                this,
                "PLAY",
                FriendsButtonStyle.GREEN,
                FriendsIcon.PLAY,
            ) { playFriend(friendId) }.apply { textSize = 9f },
            LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(2) },
        )
        actions.addView(
            FriendsVisualKit.button(
                this,
                "INV",
                FriendsButtonStyle.BLUE,
                FriendsIcon.INVITE,
            ) { inviteFromFriendRow(friendId) }.apply { textSize = 9f },
            LinearLayout.LayoutParams(0, dp(34), 0.85f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            },
        )
        val more = FriendsVisualKit.iconView(this, FriendsIcon.MORE, FriendsVisualKit.TEXT).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "More actions for " + friend.optString("displayName", "friend")
            AppFocusRing.apply(this, cornerRadiusDp = 8f, strokeWidthDp = 2f)
            setOnClickListener { anchor -> showFriendMenu(friend) }
        }
        actions.addView(more, LinearLayout.LayoutParams(dp(26), dp(34)).apply { marginStart = dp(2) })
        row.addView(
            actions,
            LinearLayout.LayoutParams(if (isCompact()) dp(176) else dp(330), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(4) },
        )
        return row
    }

    private fun buildRecentRow(player: JSONObject): View {
        val friendId = player.optString("opponentFriendId")
        val currentFriend = currentFriend(friendId)
        val online = currentFriend?.optBoolean("online", false) == true
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = FriendsVisualKit.rowSurface(this@FriendsActivity)
        }
        row.addView(
            FriendsVisualKit.avatar(this, friendId, online),
            LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(6) },
        )
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(
            FriendsVisualKit.title(
                this,
                player.optString("displayName", "Player"),
                12f,
            ),
        )
        text.addView(
            FriendsVisualKit.body(
                this,
                "◷ ${formatPlayed(player.optLong("playedAt", 0L))}",
                8.5f,
                muted = true,
            ),
        )
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            FriendsVisualKit.button(
                this,
                "ADD",
                FriendsButtonStyle.BLUE,
                FriendsIcon.ADD_FRIEND,
            ) { sendFriendRequestTo(friendId, clearInput = false) }.apply {
                textSize = 8f
            },
            LinearLayout.LayoutParams(if (isCompact()) dp(58) else dp(88), dp(34)).apply {
                marginStart = dp(3)
            },
        )
        row.addView(
            FriendsVisualKit.button(
                this,
                "PLAY",
                FriendsButtonStyle.ORANGE,
                FriendsIcon.PLAY,
            ) { playRecent(friendId, player.optString("displayName", "Player")) }.apply {
                textSize = 8f
            },
            LinearLayout.LayoutParams(if (isCompact()) dp(64) else dp(92), dp(34)).apply {
                marginStart = dp(3)
            },
        )
        return row
    }

    private fun playRecent(friendId: String, displayName: String) {
        if (currentFriend(friendId) != null) {
            playFriend(friendId)
            return
        }
        PlywoodAlertDialogBuilder(this)
            .setTitle("Add friend to play again?")
            .setMessage("$displayName is no longer in your friends list. Send a friend request first?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SEND REQUEST") { _, _ ->
                sendFriendRequestTo(friendId, clearInput = false)
            }
            .show()
    }

    private fun showFriendMenu(friend: JSONObject) {
        // Replace the platform's plain PopupMenu with the same Plywood /
        // translucent-blue options used throughout the app.
        PlywoodAlertDialogBuilder(this)
            .setTitle("FRIEND OPTIONS")
            .setItems(arrayOf("Copy Friend ID", "Remove Friend", "Block Player", "Report Player")) { _, choice ->
                when (choice) {
                    0 -> {
                        copyText("Ludo Paws Friend ID", friend.optString("friendId"))
                        showStatus("Friend ID copied.")
                    }
                    1 -> confirmRemoveFriend(
                        friend.optString("friendId"),
                        friend.optString("displayName", "Friend"),
                    )
                    2 -> {
                        val friendId = friend.optString("friendId")
                        val name = friend.optString("displayName", "this player")
                        PlywoodAlertDialogBuilder(this@FriendsActivity)
                            .setTitle("Block $name?")
                            .setMessage("Blocking removes the friendship and stops future friend requests, invites, and messages. You can unblock later.")
                            .setNegativeButton("CANCEL", null)
                            .setPositiveButton("BLOCK") { _, _ -> blockFriend(friendId) }
                            .show()
                    }
                    3 -> showReportFriend(friend.optString("friendId"))
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showRequestsDialog() {
        val incoming = snapshot?.optJSONArray("incomingRequests").objects()
        val outgoing = snapshot?.optJSONArray("outgoingRequests").objects()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            content.addView(FriendsVisualKit.body(this, "No pending friend requests.", 12f))
        }
        incoming.forEach { request ->
            val friend = request.optJSONObject("friend") ?: return@forEach
            content.addView(
                dialogFriendRow(
                    friend = friend,
                    subtitle = "Incoming request",
                    positiveLabel = "ACCEPT",
                    negativeLabel = "DECLINE",
                    onPositive = { respondFriendRequest(request.optString("requestId"), true) },
                    onNegative = { respondFriendRequest(request.optString("requestId"), false) },
                ),
                itemParams(),
            )
        }
        outgoing.forEach { request ->
            val friend = request.optJSONObject("friend") ?: return@forEach
            content.addView(simpleDialogFriendRow(friend, "Request sent • Pending"), itemParams())
        }
        showScrollableDialog("Friend Requests", content)
    }

    private fun showInvitesDialog() {
        val invites = snapshot?.optJSONArray("invites").objects()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        if (invites.isEmpty()) {
            content.addView(FriendsVisualKit.body(this, "No private-room invites.", 12f))
        }
        invites.forEach { invite ->
            val from = invite.optJSONObject("from") ?: return@forEach
            content.addView(
                dialogFriendRow(
                    friend = from,
                    subtitle = "Invited you • ${invite.optString("matchId")}",
                    positiveLabel = "JOIN ROOM",
                    negativeLabel = "DECLINE",
                    onPositive = {
                        acceptInvite(invite.optString("inviteId"), invite.optString("matchId"))
                    },
                    onNegative = { declineInvite(invite.optString("inviteId")) },
                ),
                itemParams(),
            )
        }
        showScrollableDialog("Room Invites", content)
    }

    private fun showScrollableDialog(title: String, content: View) {
        val scroll = ScrollView(this).apply { addView(content) }
        PlywoodAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun dialogFriendRow(
        friend: JSONObject,
        subtitle: String,
        positiveLabel: String,
        negativeLabel: String,
        onPositive: () -> Unit,
        onNegative: () -> Unit,
    ): View {
        val container = simpleDialogFriendRow(friend, subtitle)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            FriendsVisualKit.button(this, positiveLabel, FriendsButtonStyle.GREEN) { onPositive() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) },
        )
        actions.addView(
            FriendsVisualKit.button(this, negativeLabel, FriendsButtonStyle.RED) { onNegative() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) },
        )
        container.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        return container
    }

    private fun simpleDialogFriendRow(friend: JSONObject, subtitle: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = FriendsVisualKit.rowSurface(this@FriendsActivity)
            addView(FriendsVisualKit.title(this@FriendsActivity, friend.optString("displayName", "Friend"), 15f))
            addView(
                FriendsVisualKit.body(
                    this@FriendsActivity,
                    "${friend.optString("friendId")}  •  $subtitle",
                    10.5f,
                    muted = true,
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(3) },
            )
        }

    private fun iconTitle(icon: FriendsIcon, title: String, tint: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                FriendsVisualKit.iconView(this@FriendsActivity, icon, tint),
                LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) },
            )
            addView(FriendsVisualKit.title(this@FriendsActivity, title, 19f))
        }

    private fun ensureIdentity() {
        if (credential != null || requestInFlight || !isOnline) return
        val displayName = profileStore.snapshot().displayName
        val requestId = runCatching { identityStore.registrationRequestId() }
            .getOrElse {
                showStatus("Could not prepare Friend ID.")
                return
            }

        runRequest(
            working = "Creating your Friend ID…",
            action = {
                api.registerFriend(
                    displayName = displayName,
                    clientRequestId = requestId,
                ).also { registered ->
                    runCatching {
                        (application as LudoProofApplication).accountRepository
                            .ensureLinkedFriendWallet(registered.getString("friendToken"))
                    }
                }
            },
            onSuccess = success@ { response ->
                val next = FriendCredential(
                    friendId = response.getString("friendId"),
                    friendToken = response.getString("friendToken"),
                    registrationRequestId = requestId,
                )
                runCatching { identityStore.save(next) }
                    .onFailure {
                        showStatus("Friend ID was created but could not be secured on this device.")
                        return@success
                    }
                credential = next
                friendIdText.text = next.friendId
                showStatus("Friend ID ready.")
                syncFriends(announce = false)
            },
        )
    }

    private fun syncFriends(announce: Boolean) {
        val auth = credential ?: run {
            ensureIdentity()
            return
        }
        if (requestInFlight || !isOnline) return
        runRequest(
            working = if (announce) "Refreshing friends…" else null,
            action = {
                runCatching {
                    (application as LudoProofApplication).accountRepository
                        .ensureLinkedFriendWallet(auth.friendToken)
                }
                val result = api.friendSnapshot(auth.friendToken)
                runCatching { api.friendRecent(auth.friendToken) }
                    .onSuccess { recent -> result.put("_recent", recent) }
                activeFriendSession()?.let { session ->
                    try {
                        api.state(session.matchId, session.playerToken)
                            .optJSONObject("state")
                            ?.let { roomState -> result.put("_roomState", roomState) }
                    } catch (error: GameApiException) {
                        if (error.code == "MATCH_NOT_FOUND" || error.code == "AUTH_INVALID") {
                            result.put("_roomUnavailable", true)
                        }
                    }
                }
                result
            },
            onSuccess = {
                snapshot = it
                val roomState = it.optJSONObject("_roomState")
                if (
                    it.optBoolean("_roomUnavailable", false) ||
                    roomState?.optString("status") == "FINISHED"
                ) {
                    sessionStore.clear()
                }
                renderAll()
                if (announce) showStatus("Friends refreshed.")
            },
            quietFailure = !announce,
        )
    }

    private fun sendFriendRequest() {
        val target = addFriendInput.text.toString().trim().uppercase()
        if (!FRIEND_ID.matches(target)) {
            showStatus("Enter a valid Friend ID like LPF-ABCD-EFGH-JKLM.")
            return
        }
        sendFriendRequestTo(target, clearInput = true)
    }

    private fun sendFriendRequestTo(target: String, clearInput: Boolean) {
        val auth = credential ?: run {
            ensureIdentity()
            return
        }
        if (!FRIEND_ID.matches(target)) {
            showStatus("This Friend ID is invalid.")
            return
        }
        runRequest(
            working = "Sending friend request…",
            action = { api.sendFriendRequest(auth.friendToken, target) },
            onSuccess = { response ->
                if (clearInput) addFriendInput.setText("")
                showStatus(
                    when (response.optString("status")) {
                        "ALREADY_FRIENDS" -> "You are already friends."
                        "INCOMING_PENDING" -> "They already sent you a request. Open Friend Requests to accept it."
                        else -> "Friend request sent."
                    },
                )
                syncFriends(announce = false)
            },
        )
    }

    private fun respondFriendRequest(requestId: String, accept: Boolean) {
        val auth = credential ?: return
        runRequest(
            working = if (accept) "Accepting friend request…" else "Declining friend request…",
            action = { api.respondFriendRequest(auth.friendToken, requestId, accept) },
            onSuccess = {
                showStatus(if (accept) "Friend added." else "Friend request declined.")
                syncFriends(announce = false)
            },
        )
    }

    private fun createFriendRoom() {
        createFriendRoomInternal(
            playerCount = selectedPlayerCount,
            working = "Creating $selectedPlayerCount-player private room…",
        ) { session ->
            renderRoom()
            renderFriends()
            showStatus("Private room ${session.matchId} created. Invite friends below.")
        }
    }

    private fun createFriendRoomInternal(
        playerCount: Int,
        working: String,
        onReady: (PlayerSession) -> Unit,
    ) {
        val auth = credential ?: run {
            showStatus("Friend identity is not ready yet.")
            return
        }
        activeFriendSession()?.let {
            onReady(it)
            return
        }
        val displayName = profileStore.snapshot().displayName
        val operationKey = "friends:$playerCount:$displayName"
        val requestId = runCatching {
            pendingOperationStore.getOrCreate("friend_create", operationKey)
        }.getOrElse {
            showStatus("Could not prepare private room.")
            return
        }
        runRequest(
            working = working,
            action = {
                api.createFriendRoom(
                    friendToken = auth.friendToken,
                    displayName = displayName,
                    clientRequestId = requestId,
                    playerCount = playerCount,
                )
            },
            onSuccess = success@ { response ->
                val roomId = response.optString("matchId").trim().uppercase(Locale.ROOT)
                val resolvedPlayerId = response.optString("playerId").trim()
                val roomToken = response.optString("playerToken").trim()
                if (!isValidFriendRoomHandoff(
                        response = response,
                        expectedMatchId = roomId,
                        expectedPlayerId = resolvedPlayerId,
                        expectedPlayerCount = playerCount,
                    )
                ) {
                    showStatus("Private room response is incomplete • retry the room request.")
                    return@success
                }
                val session = PlayerSession(
                    matchId = roomId,
                    playerId = resolvedPlayerId,
                    playerToken = roomToken,
                    modeWire = GameMode.FRIENDS.wireValue,
                )
                runCatching { sessionStore.save(session) }
                    .onFailure {
                        showStatus("Room was created but its secure session could not be saved.")
                        return@success
                    }
                pendingOperationStore.clear("friend_create", operationKey)
                onReady(session)
            },
        )
    }

    private fun playFriend(friendId: String) {
        val active = activeFriendSession()
        if (active != null) {
            inviteFriend(friendId, active.matchId, active.playerToken, openAfter = true)
            return
        }
        createFriendRoomInternal(
            playerCount = 2,
            working = "Preparing a 2-player room…",
        ) { session ->
            inviteFriend(friendId, session.matchId, session.playerToken, openAfter = true)
        }
    }

    private fun inviteFromFriendRow(friendId: String) {
        val active = activeFriendSession()
        if (active != null) {
            inviteFriend(friendId, active.matchId, active.playerToken, openAfter = false)
            return
        }
        createFriendRoomInternal(
            playerCount = selectedPlayerCount,
            working = "Preparing a private room…",
        ) { session ->
            inviteFriend(friendId, session.matchId, session.playerToken, openAfter = false)
        }
    }

    private fun inviteFriend(
        friendId: String,
        matchId: String,
        roomPlayerToken: String,
        openAfter: Boolean,
    ) {
        val auth = credential ?: return
        runRequest(
            working = "Sending room invite…",
            action = {
                api.sendFriendInvite(
                    friendToken = auth.friendToken,
                    friendId = friendId,
                    matchId = matchId,
                    roomPlayerToken = roomPlayerToken,
                    clientRequestId = UUID.randomUUID().toString(),
                )
            },
            onSuccess = {
                showStatus("Private-room invite sent.")
                syncFriends(announce = false)
                if (openAfter) openFriendRoom()
            },
        )
    }

    private fun acceptInvite(inviteId: String, matchId: String) {
        val auth = credential ?: return
        val displayName = profileStore.snapshot().displayName
        val operationKey = "friends_join:$matchId:$displayName"
        val requestId = runCatching {
            pendingOperationStore.getOrCreate("friend_join", operationKey)
        }.getOrElse {
            showStatus("Could not prepare room join.")
            return
        }
        runRequest(
            working = "Joining private room…",
            action = {
                val accepted = api.respondFriendInvite(
                    friendToken = auth.friendToken,
                    inviteId = inviteId,
                    accept = true,
                    clientRequestId = requestId,
                )
                val authorizedMatchId = accepted.getString("matchId")
                if (authorizedMatchId != matchId) {
                    throw IllegalStateException("Accepted invite does not match the selected room.")
                }
                api.joinMatch(
                    matchId = matchId,
                    displayName = displayName,
                    clientRequestId = requestId,
                    friendJoinToken = accepted.getString("friendJoinToken"),
                )
            },
            onSuccess = success@ { response ->
                val roomId = response.optString("matchId").trim().uppercase(Locale.ROOT)
                val resolvedPlayerId = response.optString("playerId").trim()
                val roomToken = response.optString("playerToken").trim()
                if (
                    roomId != matchId.uppercase(Locale.ROOT) ||
                    !isValidFriendRoomHandoff(
                        response = response,
                        expectedMatchId = roomId,
                        expectedPlayerId = resolvedPlayerId,
                        expectedPlayerCount = null,
                    )
                ) {
                    showStatus("Private-room join response is incomplete • reopen the invite to retry.")
                    return@success
                }
                val session = PlayerSession(
                    matchId = roomId,
                    playerId = resolvedPlayerId,
                    playerToken = roomToken,
                    modeWire = GameMode.FRIENDS.wireValue,
                )
                runCatching { sessionStore.save(session) }
                    .onFailure {
                        showStatus("Joined room but could not secure the session locally.")
                        return@success
                    }
                pendingOperationStore.clear("friend_join", operationKey)
                openFriendRoom()
            },
        )
    }

    private fun declineInvite(inviteId: String) {
        val auth = credential ?: return
        runRequest(
            working = "Declining room invite…",
            action = { api.respondFriendInvite(auth.friendToken, inviteId, false) },
            onSuccess = {
                showStatus("Room invite declined.")
                syncFriends(announce = false)
            },
        )
    }

    private fun confirmRemoveFriend(friendId: String, displayName: String) {
        PlywoodAlertDialogBuilder(this)
            .setTitle("Remove friend?")
            .setMessage("Remove $displayName ($friendId) from your friends?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("REMOVE") { _, _ -> removeFriend(friendId) }
            .show()
    }

    private fun blockFriend(friendId: String) {
        val auth = credential ?: return
        runRequest(
            working = "Blocking player…",
            action = { api.blockFriend(auth.friendToken, friendId) },
            onSuccess = {
                showStatus("Player blocked. They cannot message or invite you.")
                syncFriends(announce = false)
            },
        )
    }

    private fun unblockFriend(friendId: String) {
        val auth = credential ?: return
        runRequest(
            working = "Unblocking player…",
            action = { api.unblockFriend(auth.friendToken, friendId) },
            onSuccess = {
                showStatus("Player unblocked. Friendship is not restored automatically.")
                syncFriends(announce = false)
            },
        )
    }

    private fun showReportFriend(friendId: String, messageId: String? = null) {
        if (!FRIEND_ID.matches(friendId)) return
        val labels = arrayOf("Harassment", "Spam", "Hate", "Threats", "Sexual content", "Other")
        val reasons = arrayOf("HARASSMENT", "SPAM", "HATE", "THREATS", "SEXUAL_CONTENT", "OTHER")
        PlywoodAlertDialogBuilder(this)
            .setTitle("Report player")
            .setItems(labels) { _, selected ->
                val auth = credential ?: return@setItems
                runRequest(
                    working = "Submitting report…",
                    action = {
                        api.reportFriend(
                            auth.friendToken, friendId, reasons[selected],
                            UUID.randomUUID().toString(), messageId,
                        )
                    },
                    onSuccess = {
                        showStatus("Report submitted. You can also block this player from the menu.")
                    },
                )
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun removeFriend(friendId: String) {
        val auth = credential ?: return
        runRequest(
            working = "Removing friend…",
            action = { api.removeFriend(auth.friendToken, friendId) },
            onSuccess = {
                showStatus("Friend removed.")
                syncFriends(announce = false)
            },
        )
    }

    private fun openFriendRoom() {
        val session = activeFriendSession() ?: run {
            showStatus("Create or join a private friend room first.")
            return
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(GameModeIntent.EXTRA_GAME_MODE, GameMode.FRIENDS.wireValue)
                .putExtra(EXTRA_FRIEND_ROOM_ID, session.matchId),
        )
    }

    private fun copyFriendId() {
        val id = credential?.friendId ?: run {
            showStatus("Friend ID is still being created.")
            return
        }
        copyText("Ludo Paws Friend ID", id)
        showStatus("Friend ID copied.")
    }

    private fun copyText(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    private fun shareFriendId() {
        val id = credential?.friendId ?: run {
            showStatus("Friend ID is still being created.")
            return
        }
        shareText(
            "Add me on Ludo Paws\nFriend ID: $id\n\n${playStoreUrl()}",
            "Share Friend ID",
        )
    }

    private fun shareRoom(matchId: String) {
        shareText(
            "Join my private Ludo Paws room: $matchId\n\n${playStoreUrl()}",
            "Share private room",
        )
    }

    private fun shareText(text: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun playStoreUrl(): String =
        "https://play.google.com/store/apps/details?id=$packageName"

    private fun activeFriendSession(): PlayerSession? =
        sessionStore.load()?.takeIf {
            GameMode.fromWireValue(it.modeWire) == GameMode.FRIENDS
        }

    private fun currentFriend(friendId: String): JSONObject? =
        snapshot?.optJSONArray("friends").objects()
            .firstOrNull { it.optString("friendId") == friendId }

    private fun runRequest(
        working: String?,
        action: () -> JSONObject,
        onSuccess: (JSONObject) -> Unit,
        quietFailure: Boolean = false,
    ) {
        if (requestInFlight || !isOnline || executor.isShutdown) {
            if (!quietFailure) {
                showStatus(
                    when {
                        !isOnline -> "Internet connection is required."
                        executor.isShutdown -> "This screen is closing. Reopen Friends to try again."
                        else -> "Friends is syncing. Please retry your action in a moment."
                    },
                )
            }
            return
        }
        requestInFlight = true
        if (working != null) showStatus(working)
        val submitted = runCatching {
            executor.execute {
                try {
                    val response = action()
                    mainHandler.post {
                        requestInFlight = false
                        if (isFinishing || isDestroyed) return@post
                        onSuccess(response)
                    }
                } catch (error: Exception) {
                    mainHandler.post {
                        requestInFlight = false
                        if (isFinishing || isDestroyed) return@post
                        val apiError = error as? GameApiException
                        if (apiError?.code == "FRIEND_AUTH_INVALID") {
                            identityStore.clearCredentialOnly()
                            credential = null
                            friendIdText.text = "RECOVERING…"
                            ensureIdentity()
                            return@post
                        }
                        if (!quietFailure) {
                            showStatus("Error: ${error.message ?: error}")
                        }
                    }
                }
            }
        }.isSuccess
        if (!submitted) requestInFlight = false
    }

    private fun showStatus(value: String) {
        if (::statusText.isInitialized) statusText.text = value
    }

    private fun itemParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(7) }

    private fun dp(value: Int): Int = FriendsVisualKit.dp(this, value)

    private fun isCompact(): Boolean = resources.configuration.screenWidthDp < 420

    private companion object {
        const val REFRESH_MS = 10_000L
        const val CHAT_REFRESH_MS = 5_000L
        const val MAX_MESSAGE_CHARS = 240
        const val MAX_RECENT_VISIBLE = 10
        const val EXTRA_FRIEND_ROOM_ID = "ludoproof_friend_room_id_v1"
        val FRIEND_ID = Regex("^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$")
    }
}
