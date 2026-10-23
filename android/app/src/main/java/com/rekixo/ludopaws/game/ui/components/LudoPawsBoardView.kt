package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Outline
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
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
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
    ) {
        snapshot = state
        localPlayerId = playerId
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

        // The selected seat's animal, not its fixed color, determines the
        // corresponding yard habitat. The same binding works offline and online.
        boardChrome.bindHabitats(
            snapshot = state,
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
        if (classicPawnFallbackVisible) {
            return super.dispatchTouchEvent(event)
        }

        // In 3D mode the full classic board is intentionally invisible. Consume the
        // gesture here and resolve ACTION_UP against the same token geometry used by
        // the 3D renderer instead of relying on invisible legacy pawn hit regions.
        if (event.action == MotionEvent.ACTION_UP) {
            selectLegal3DTokenAt(
                x = event.x,
                y = event.y,
            )
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun selectLegal3DTokenAt(
        x: Float,
        y: Float,
    ) {
        val state = snapshot ?: return
        val localId = localPlayerId ?: return
        val pending = state.pendingRoll ?: return
        if (pending.status != "RESOLVED") {
            return
        }

        val player =
            state.players
                .firstOrNull {
                    it.playerId == localId
                }
                ?: return
        val legal = pending.legalTokenIndexes
        if (legal.isEmpty()) {
            return
        }

        val size = min(width, height).toFloat()
        if (size <= 0f) {
            return
        }
        val cell = size / LudoPawsFxBoardGeometry.BOARD_SIZE
        val stackPlacements =
            LudoPawsPawnLayout.stackPlacements(
                snapshot = state,
                cell = cell,
            )
        val turns =
            perspectiveColor
                ?.let(OfflinePlayerLayout::rotationQuarterTurns)
                ?: 0
        val logicalTouch =
            unrotateTouch(
                x = x,
                y = y,
                size = size,
                quarterTurns = turns,
            )

        val roadMin = cell * 5f
        val roadMax = cell * 10f
        val hit =
            legal
                .mapNotNull { tokenIndex ->
                    val position =
                        player.tokens
                            .getOrNull(tokenIndex)
                            ?: return@mapNotNull null
                    val center =
                        LudoPawsFxBoardGeometry
                            .tokenCenter(
                                color = player.color,
                                tokenIndex = tokenIndex,
                                position = position,
                                cell = cell,
                            )
                            ?: return@mapNotNull null
                    val placement =
                        stackPlacements[
                            LudoPawsPawnVisualKey(
                                playerId = player.playerId,
                                tokenIndex = tokenIndex,
                            )
                        ]
                    val tokenX =
                        center.first +
                            (placement?.offsetXFraction ?: 0f) * cell
                    val tokenY =
                        center.second +
                            (placement?.offsetYFraction ?: 0f) * cell

                    // The released board has stretched three-lane roads. Side-road
                    // cells are tall/narrow while top/bottom-road cells are
                    // wide/short, so a fixed circular hit radius makes side-road
                    // animals unnecessarily hard to tap. Match the touch target to
                    // the actual visual cell proportions without changing geometry.
                    val onSideRoad =
                        tokenY in roadMin..roadMax &&
                            (tokenX < roadMin || tokenX > roadMax)
                    val onTopBottomRoad =
                        tokenX in roadMin..roadMax &&
                            (tokenY < roadMin || tokenY > roadMax)
                    val halfWidth =
                        when {
                            onSideRoad -> cell * 0.58f
                            onTopBottomRoad -> cell * 0.98f
                            else -> cell * 0.70f
                        }
                    val halfHeight =
                        when {
                            onSideRoad -> cell * 0.98f
                            onTopBottomRoad -> cell * 0.58f
                            else -> cell * 0.70f
                        }
                    val dx = (logicalTouch.first - tokenX) / halfWidth
                    val dy = (logicalTouch.second - tokenY) / halfHeight
                    val normalizedDistanceSquared =
                        dx.toDouble() * dx.toDouble() +
                            dy.toDouble() * dy.toDouble()

                    TokenTouchCandidate(
                        tokenIndex = tokenIndex,
                        normalizedDistanceSquared = normalizedDistanceSquared,
                    )
                }
                .minByOrNull(TokenTouchCandidate::normalizedDistanceSquared)
                ?: return

        if (hit.normalizedDistanceSquared <= 1.0) {
            onTokenSelected?.invoke(hit.tokenIndex)
        }
    }

    private fun unrotateTouch(
        x: Float,
        y: Float,
        size: Float,
        quarterTurns: Int,
    ): Pair<Float, Float> =
        when (
            (quarterTurns % 4 + 4) % 4
        ) {
            1 ->
                y to (size - x)
            2 ->
                (size - x) to (size - y)
            3 ->
                (size - y) to x
            else ->
                x to y
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

    private data class TokenTouchCandidate(
        val tokenIndex: Int,
        val normalizedDistanceSquared: Double,
    )
}
