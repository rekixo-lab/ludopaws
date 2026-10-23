package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Outline
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Production Ludo Paws board shell.
 *
 * The approved [LudoBoardView] still owns the canonical board geometry. While the
 * 3D scene is operational, this shell renders a board-only copy and handles token
 * taps against the same shared presentation geometry so the classic 2D pawns do
 * not remain visible underneath the animals. If 3D becomes unavailable, the
 * original bound board is restored as the safe classic-pawn fallback.
 *
 * BOARD GEOMETRY LOCK: do not move, resize or reinterpret the approved board here.
 */
class LudoPawsBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    /** Board-only visual surface used while the 3D pawn scene is operational. */
    private val boardSurface =
        LudoBoardView(context)

    /** Full classic board retained for hit-tested fallback when 3D is unavailable. */
    private val baseBoard =
        LudoBoardView(context)

    /** Presentation-only edge treatment; it never participates in board geometry. */
    private val boardChrome =
        LudoPawsBoardChromeView(context)

    private var snapshot: MatchSnapshot? = null
    private var localPlayerId: String? = null
    private var perspectiveColor: String? = null
    private var classicPawnFallbackVisible = true
    private var characterIds: List<String> = emptyList()
    private var inputBlockedUntil = 0L
    private var submittedRoll: String? = null
    private var downRoll: String? = null
    private var pointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var pressedTarget: PawnTarget? = null
    private var lastNoticeAt = 0L
    private var focusedToken = -1
    private var hoveredToken = -1
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFE393
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }

    private data class PawnTarget(
        val playerId: String, val tokenIndex: Int, val bounds: RectF,
        val legal: Boolean, val footY: Float, val label: String,
    )

    private fun rollKey(): String? = snapshot?.let { state ->
        state.pendingRoll?.let { "${state.matchId}:${it.eventIndex}:${localPlayerId}" }
    }

    private fun inputReady(): Boolean =
        SystemClock.uptimeMillis() >= inputBlockedUntil &&
            (submittedRoll == null || submittedRoll != rollKey())

    private fun clearPress() {
        pointerId = -1
        pressedTarget = null
        invalidate()
    }

    private fun notice(message: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastNoticeAt < 1200L) return
        lastNoticeAt = now
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        announceForAccessibility(message)
    }

    var onTokenSelected: ((Int) -> Unit)?
        get() = baseBoard.onTokenSelected
        set(value) {
            baseBoard.onTokenSelected = value
        }

    init {
        // Footprints are drawn only by the Ludo Paws boards; standalone classic
        // boards remain visually unchanged.
        // Exactly one of the two board copies draws footprints: the classic
        // fallback while GL is unavailable, or the board-only surface during 3D play.
        boardSurface.setLudoPawsYardFootprintsEnabled(false)
        boardSurface.setClassicTokenDrawingEnabled(false)
        baseBoard.setLudoPawsYardFootprintsEnabled(true)
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width,
                        view.height,
                        density(3f),
                    )
                }
            }
        clipToOutline = true

        addView(
            boardSurface,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            baseBoard,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            boardChrome,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
        // The raised chrome is the top board-art layer, so route footprint drawing
        // through it instead of leaving prints trapped under its opaque yard faces.
        boardChrome.setFootprintSource(baseBoard)
    }

    fun bind(
        state: MatchSnapshot?,
        playerId: String?,
        perspectiveColor: String? = null,
        characterIdsBySeat: List<String> = emptyList(),
        alreadyPresentedMillis: Long = 0L,
    ) {
        val old = snapshot
        val oldPlayerId = localPlayerId
        if (old != null && state != null && old.matchId == state.matchId &&
            LudoPawsPawnAnimationPolicy.plans(old, state).isNotEmpty()) {
            inputBlockedUntil = SystemClock.uptimeMillis() +
                LudoPawsGameplayPacingPolicy.remainingPresentationDelayMillis(
                    LudoPawsGameplayPacingPolicy.interactionDelayAfterMoveMillis(
                        old, state, GameSettingsStore(context).snapshot().gameSpeed,
                    ), alreadyPresentedMillis,
                )
        } else if (old?.matchId != state?.matchId) {
            inputBlockedUntil = 0L
            submittedRoll = null
        }
        snapshot = state
        characterIds = characterIdsBySeat.toList()
        localPlayerId = playerId
        if (downRoll != rollKey()) clearPress()
        if (submittedRoll != rollKey()) submittedRoll = null
        if (old?.matchId != state?.matchId || oldPlayerId != playerId) {
            if (focusedToken >= 0) sendVirtualEvent(focusedToken,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
            focusedToken = -1
            hoveredToken = -1
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        this.perspectiveColor =
            perspectiveColor
                ?.takeIf {
                    it in OfflinePlayerLayout.COLORS
                }

        // Keep the authoritative snapshot on the board-only surface so vacated-yard
        // footprints can be derived reliably. Classic tokens are explicitly disabled
        // there and remain available only on the true 3D failure fallback.
        boardSurface.bind(
            state = state,
            playerId = playerId,
            perspectiveColor = this.perspectiveColor,
            characterIdsBySeat = characterIdsBySeat,
        )
        baseBoard.bind(
            state = state,
            playerId = playerId,
            perspectiveColor = this.perspectiveColor,
            characterIdsBySeat = characterIdsBySeat,
        )

        // Keep presentation-only chrome aligned with the player's perspective.
        val chromeQuarterTurns =
            this.perspectiveColor
                ?.let(OfflinePlayerLayout::rotationQuarterTurns)
                ?: 0
        boardChrome.rotation = chromeQuarterTurns * 90f
        // Board copies invalidate themselves on bind, but sibling invalidation does
        // not redraw this topmost layer. Refresh it on every authoritative snapshot
        // so footprints appear/disappear on the same committed move.
        boardChrome.invalidate()
    }

    /**
     * Keeps the proven classic pawn renderer only as an actual 3D failure fallback.
     * The board-only surface remains visible in both modes, while [baseBoard] is
     * hidden completely during normal 3D play so no circular legacy pawn can leak
     * through or move out of sync with an animal.
     */
    fun setClassicPawnFallbackVisible(visible: Boolean) {
        if (classicPawnFallbackVisible == visible) {
            return
        }
        classicPawnFallbackVisible = visible
        baseBoard.visibility =
            if (visible) View.VISIBLE else View.INVISIBLE

        // Keep the paw-print pass on the topmost active board copy. This avoids
        // double alpha/strokes during GL startup or safe fallback transitions.
        boardSurface.setLudoPawsYardFootprintsEnabled(!visible)
        baseBoard.setLudoPawsYardFootprintsEnabled(visible)
        boardChrome.setFootprintSource(if (visible) baseBoard else boardSurface)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // One input path covers GL and the fallback, avoiding different selection rules.
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                downRoll = rollKey()
                pressedTarget = targetAt(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                clearPress()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0 || kotlin.math.hypot(
                        event.getX(index) - downX, event.getY(index) - downY,
                    ) > touchSlop) clearPress()
            }
            MotionEvent.ACTION_UP -> {
                val target = pressedTarget
                val valid = pointerId == event.getPointerId(event.actionIndex) && downRoll == rollKey()
                clearPress()
                parent?.requestDisallowInterceptTouchEvent(false)
                if (valid && target != null) {
                    performClick()
                    activate(target)
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val highlighted = pressedTarget ?: if (focusedToken >= 0) targets().firstOrNull {
            it.playerId == localPlayerId && it.tokenIndex == focusedToken
        } else null
        highlighted?.let {
            canvas.drawRoundRect(it.bounds, density(12f), density(12f), pressPaint)
        }
    }

    private fun activate(target: PawnTarget) {
        val state = snapshot ?: return
        when {
            state.status != "ACTIVE" -> notice("Match complete")
            !inputReady() || localPlayerId == null -> notice("Please wait for the current turn or paw action")
            state.players.getOrNull(state.turnSeat)?.playerId != localPlayerId ||
                target.playerId != localPlayerId -> notice("It is not this character's turn")
            state.pendingRoll == null -> notice("Roll the dice first")
            state.pendingRoll.status != "RESOLVED" -> notice("Wait for the dice result")
            !target.legal -> notice("This character cannot use this roll")
            else -> {
                val callback = onTokenSelected ?: return
                submittedRoll = rollKey()
                if (GameSettingsStore(context).snapshot().hapticsEnabled) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                // Reject a second tap on the same roll, but allow a retry after a failed request.
                val key = submittedRoll
                postDelayed({
                    if (submittedRoll == key) submittedRoll = null
                }, 900L)
                callback(target.tokenIndex)
            }
        }
    }

    private fun targets(): List<PawnTarget> {
        val state = snapshot ?: return emptyList()
        val size = min(width, height).toFloat()
        if (size <= 0f) return emptyList()
        val cell = size / LudoPawsFxBoardGeometry.BOARD_SIZE
        val stacks = LudoPawsPawnLayout.stackPlacements(state, cell)
        val turns = perspectiveColor?.let(OfflinePlayerLayout::rotationQuarterTurns) ?: 0
        return buildList {
            for (player in state.players) {
                for ((index, position) in player.tokens.withIndex()) {
                    val center = LudoPawsFxBoardGeometry.tokenCenter(
                        player.color, index, position, cell, turns,
                    ) ?: continue
                    val stack = stacks[LudoPawsPawnVisualKey(player.playerId, index)]
                    val x = center.first + (stack?.offsetXFraction ?: 0f) * cell
                    val y = center.second + (stack?.offsetYFraction ?: 0f) * cell
                    val screen = when ((turns % 4 + 4) % 4) {
                        1 -> (size - y) to x
                        2 -> (size - x) to (size - y)
                        3 -> y to (size - x)
                        else -> x to y
                    }
                    val id = LudoPaws3DCharacterPolicy.characterIdForSeat(characterIds, player.seat, player.color)
                    val fit = LudoPawsPawnLayout.roadPresentation(center.first, center.second,
                        cell, position, stack?.occupancy ?: 1, id, turns)
                    val radius = if (classicPawnFallbackVisible)
                        cell * LudoPawsPawnLayout.radiusScale(position, stack?.occupancy ?: 1) else fit.radius
                    val footY = screen.second + if (classicPawnFallbackVisible) 0f else fit.footDrop
                    val heightRatio = LudoPawsPawnLayout.silhouetteHeight(id)
                    val halfWidth = radius * if (id == "duck") .95f else .86f
                    val bodyHeight = radius * heightRatio
                    val bounds = if (classicPawnFallbackVisible) {
                        RectF(screen.first - radius, screen.second - radius,
                            screen.first + radius, screen.second + radius)
                    } else RectF(screen.first - halfWidth, footY - bodyHeight * 1.14f,
                        screen.first + halfWidth, footY + radius * .18f)
                    val legal = player.playerId == localPlayerId &&
                        state.players.getOrNull(state.turnSeat)?.playerId == localPlayerId &&
                        state.pendingRoll?.status == "RESOLVED" &&
                        index in state.pendingRoll.legalTokenIndexes
                    add(PawnTarget(player.playerId, index, bounds, legal, footY,
                        "${player.displayName}, ${id}, character ${index + 1}, " +
                            if (legal) "can move" else "cannot move now"))
                }
            }
        }
    }

    private fun targetAt(x: Float, y: Float): PawnTarget? {
        val all = targets()
        // Direct visible-body hits win over enlarged padding, then frontmost/legal targets.
        val direct = all.filter { it.bounds.contains(x, y) }
        if (direct.isNotEmpty()) return direct.sortedWith(
            compareByDescending<PawnTarget> { it.legal }.thenByDescending { it.footY }
                .thenBy { kotlin.math.abs(x - it.bounds.centerX()) },
        ).first()
        val padding = density(10f)
        return all.mapNotNull { target ->
            val expanded = RectF(target.bounds)
            expanded.inset(-maxOf(padding, (density(48f) - expanded.width()) * .5f),
                -maxOf(padding, (density(48f) - expanded.height()) * .5f))
            if (!expanded.contains(x, y)) null else {
                val dx = maxOf(target.bounds.left - x, 0f, x - target.bounds.right)
                val dy = maxOf(target.bounds.top - y, 0f, y - target.bounds.bottom)
                target to (dx * dx + dy * dy)
            }
        }.sortedWith(compareBy<Pair<PawnTarget, Float>> { it.second }
            .thenByDescending { it.first.legal }).firstOrNull()?.first
    }

    internal fun hasPawnAt(x: Float, y: Float): Boolean = targetAt(x, y) != null

    private val accessibilityNodes = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
            if (id == View.NO_ID) return AccessibilityNodeInfo.obtain(this@LudoPawsBoardView).apply {
                this@LudoPawsBoardView.onInitializeAccessibilityNodeInfo(this)
                className = "android.view.ViewGroup"
                for (target in targets().filter { it.playerId == localPlayerId }) {
                    addChild(this@LudoPawsBoardView, target.tokenIndex)
                }
            }
            val target = targets().firstOrNull { it.playerId == localPlayerId && it.tokenIndex == id }
                ?: return null
            return AccessibilityNodeInfo.obtain().apply {
                setSource(this@LudoPawsBoardView, id)
                setParent(this@LudoPawsBoardView)
                packageName = context.packageName
                className = "android.widget.Button"
                contentDescription = target.label
                isEnabled = true
                isClickable = true
                isFocusable = true
                isVisibleToUser = isShown
                isAccessibilityFocused = focusedToken == id
                val rect = Rect()
                target.bounds.roundOut(rect)
                setBoundsInParent(rect)
                val location = IntArray(2)
                getLocationOnScreen(location)
                rect.offset(location[0], location[1])
                setBoundsInScreen(rect)
                addAction(AccessibilityNodeInfo.ACTION_CLICK)
                addAction(if (focusedToken == id) AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                    else AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            }
        }

        override fun findFocus(focus: Int): AccessibilityNodeInfo? =
            if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY && focusedToken >= 0)
                createAccessibilityNodeInfo(focusedToken) else null

        override fun performAction(id: Int, action: Int, arguments: Bundle?): Boolean {
            val target = targets().firstOrNull { it.playerId == localPlayerId && it.tokenIndex == id }
                ?: return false
            when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> activate(target)
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    if (focusedToken >= 0) sendVirtualEvent(focusedToken,
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    focusedToken = id
                    sendVirtualEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    focusedToken = -1
                    sendVirtualEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                }
                else -> return false
            }
            invalidate()
            return true
        }
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = accessibilityNodes

    private fun sendVirtualEvent(id: Int, type: Int) {
        val event = AccessibilityEvent.obtain(type)
        event.packageName = context.packageName
        event.className = "android.widget.Button"
        event.setSource(this, id)
        parent?.requestSendAccessibilityEvent(this, event)
    }

    public override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!manager.isTouchExplorationEnabled) return super.dispatchHoverEvent(event)
        val target = targetAt(event.x, event.y)?.takeIf { it.playerId == localPlayerId }
        val id = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) -1 else target?.tokenIndex ?: -1
        if (id != hoveredToken) {
            if (id >= 0) sendVirtualEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
            if (hoveredToken >= 0) sendVirtualEvent(hoveredToken, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            hoveredToken = id
        }
        return true
    }

    override fun onDetachedFromWindow() {
        clearPress()
        submittedRoll = null
        inputBlockedUntil = 0L
        focusedToken = -1
        hoveredToken = -1
        super.onDetachedFromWindow()
    }

    fun reloadStyle() {
        boardSurface.reloadStyle()
        baseBoard.reloadStyle()
        boardChrome.reloadStyle()
    }

    // BOARD SIZE LOCK: exactly mirror the approved LudoBoardView square.
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val desired = density(380f).roundToInt()
        val resolvedWidth = resolveSize(desired, widthMeasureSpec)
        val resolvedHeight = resolveSize(resolvedWidth, heightMeasureSpec)
        val size = min(resolvedWidth, resolvedHeight)
        val exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(exact, exact)
    }

    private fun density(value: Float): Float =
        value * resources.displayMetrics.density

}
