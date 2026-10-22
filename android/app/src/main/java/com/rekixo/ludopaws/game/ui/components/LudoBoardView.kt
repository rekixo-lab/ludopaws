package com.rekixo.ludopaws

import android.animation.ValueAnimator
import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Outline
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewOutlineProvider
import android.view.View
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import java.util.Random

/**
 * BOARD GEOMETRY LOCK (approved final layout).
 *
 * The board position and all board geometry in this view are fixed by product decision:
 * board size/aspect, four yard locations, white-home size and margins, all road squares,
 * home lanes, center triangles, safe cells, track order, and token coordinate mapping.
 *
 * Do not move, resize, redistribute, reorder, or "normalize" any board geometry unless an
 * explicit future board-redesign task requires it. Any intentional geometry change here must
 * be mirrored in LudoPawsBoardView/AnimalPawnOverlayView so cosmetics remain pixel-aligned.
 */
class LudoBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var onTokenSelected: ((Int) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Ludo board"
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
    }

    private var boardThemeId =
        "board_classic"
    private var snapshot: MatchSnapshot? = null
    private var localPlayerId: String? = null
    private var perspectiveColor: String? = null
    private var characterIdsBySeat: List<String> = emptyList()
    private var ludoPawsYardFootprintsEnabled = false
    private var classicTokenDrawingEnabled = true
    private var moveAnimator: ValueAnimator? = null
    private var moveAnimation: TokenMoveAnimation? = null
    private val tokenHits = mutableListOf<TokenHit>()

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    // A cached transparent grain tile makes only the four colored home yards
    // feel matte/rough. It is deliberately separate from fillPaint so road cells,
    // white token homes, pawn colors and hit-testing remain untouched.
    private val yardTexturePaint = createYardTexturePaint()
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density(1f)
            color = Color.rgb(70, 70, 70)
        }
    private val tokenStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density(1.5f)
            color = Color.rgb(35, 35, 35)
        }
    private val legalPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density(4f)
            color = Color.rgb(255, 193, 7)
        }
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    private val pawPrintFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val pawPrintGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val pawPrintEdgePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val pawPrintDetailPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val pawPrintTexturePaint = createPawPrintTexturePaint()

    private data class PawPrintArtwork(
        val pads: List<Path>,
        val details: List<Path> = emptyList(),
    )

    /**
     * Enables animal-specific footprints on the Ludo Paws presentation board.
     * The legacy setter remains as a compatibility bridge for older callers.
     */
    fun setLudoPawsYardFootprintsEnabled(enabled: Boolean) {
        if (ludoPawsYardFootprintsEnabled == enabled) return
        ludoPawsYardFootprintsEnabled = enabled
        invalidate()
    }

    @Deprecated("Use setLudoPawsYardFootprintsEnabled")
    fun setLudoPawsTeamSigilsEnabled(enabled: Boolean) {
        setLudoPawsYardFootprintsEnabled(enabled)
    }

    /**
     * Allows the production board surface to retain authoritative state for
     * footprints without drawing legacy circular pawns below the 3D animals.
     */
    fun setClassicTokenDrawingEnabled(enabled: Boolean) {
        if (classicTokenDrawingEnabled == enabled) return
        classicTokenDrawingEnabled = enabled
        if (!enabled) {
            moveAnimator?.cancel()
            moveAnimator = null
            moveAnimation = null
            tokenHits.clear()
        }
        invalidate()
    }

    fun bind(
        state: MatchSnapshot?,
        playerId: String?,
        perspectiveColor: String? = null,
        characterIdsBySeat: List<String> = emptyList(),
    ) {
        val previous =
            snapshot
        snapshot = state
        localPlayerId = playerId
        this.perspectiveColor =
            perspectiveColor
                ?.takeIf {
                    it in
                        OfflinePlayerLayout.COLORS
                }
        this.characterIdsBySeat = characterIdsBySeat.take(4).toList()

        val localPlayer =
            state?.players?.find {
                it.playerId == playerId
            }
        val legalTokens =
            state?.pendingRoll
                ?.legalTokenIndexes
                .orEmpty()
                .sorted()
                .joinToString(", ") {
                    (it + 1).toString()
                }

        contentDescription =
            when {
                state == null ->
                    "Ludo board. No active match."
                state.status == "FINISHED" ->
                    "Ludo board. Match finished."
                localPlayer == null ->
                    "Ludo board. Spectator state."
                legalTokens.isNotBlank() ->
                    "Ludo board. Your legal tokens are $legalTokens."
                else ->
                    "Ludo board. No legal token is currently selectable."
            }

        if (classicTokenDrawingEnabled) {
            startMoveAnimationIfNeeded(
                previous = previous,
                current = state,
            )
        } else {
            moveAnimator?.cancel()
            moveAnimator = null
            moveAnimation = null
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reloadStyle()
    }

    fun reloadStyle() {
        boardThemeId =
            CosmeticInventoryStore(
                context,
            )
                .selectedId(
                    CosmeticCategory.BOARD,
                )
        invalidate()
    }

    override fun onDetachedFromWindow() {
        moveAnimator
            ?.cancel()
        moveAnimator =
            null
        moveAnimation =
            null
        super.onDetachedFromWindow()
    }

    // BOARD SIZE LOCK: keep the board square. Do not change this sizing contract as part of
    // unrelated UI work; all approved board geometry scales inside this square.
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val desired = density(380f).toInt()
        val width =
            resolveSize(desired, widthMeasureSpec)
        val height =
            resolveSize(width, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        tokenHits.clear()

        val size = min(width, height).toFloat()
        if (size <= 0f) return

        // BASE COORDINATE LOCK: gameplay remains a logical 15x15 board. The approved visual
        // stretching is handled only by axisBoundary(); do not replace it with uniform cells.
        val cell = size / 15f

        canvas.drawColor(boardSurfaceColor())

        val turns =
            perspectiveColor
                ?.let {
                    OfflinePlayerLayout
                        .rotationQuarterTurns(
                            it,
                        )
                }
                ?: 0

        if (
            turns !=
            0
        ) {
            canvas.save()
            canvas.rotate(
                turns *
                    90f,
                size /
                    2f,
                size /
                    2f,
            )
        }

        drawYards(canvas, cell)
        // Ludo Paws footprints are composed by the topmost board-chrome layer;
        // drawing them here would be hidden by that opaque layer.
        drawTrack(canvas, cell)
        drawHomeLanes(canvas, cell)
        drawCenter(canvas, cell)
        if (classicTokenDrawingEnabled) {
            drawTokens(canvas, cell)
        }

        if (
            turns !=
            0
        ) {
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val size =
            min(
                width,
                height,
            ).toFloat()
        val turns =
            perspectiveColor
                ?.let {
                    OfflinePlayerLayout
                        .rotationQuarterTurns(
                            it,
                        )
                }
                ?: 0
        val logicalTouch =
            unrotateTouch(
                event.x,
                event.y,
                size,
                turns,
            )

        val hit =
            tokenHits.minByOrNull {
                hypot(
                    (
                        logicalTouch.first -
                            it.x
                        ).toDouble(),
                    (
                        logicalTouch.second -
                            it.y
                        ).toDouble(),
                )
            }

        if (
            hit != null &&
            hypot(
                (
                    logicalTouch.first -
                        hit.x
                    ).toDouble(),
                (
                    logicalTouch.second -
                        hit.y
                    ).toDouble(),
            ) <= hit.radius
        ) {
            performClick()
            onTokenSelected?.invoke(hit.tokenIndex)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun unrotateTouch(
        x: Float,
        y: Float,
        size: Float,
        quarterTurns: Int,
    ): Pair<Float, Float> =
        when (
            (
                quarterTurns %
                    4 +
                    4
                ) %
                4
        ) {
            1 ->
                y to
                    (
                        size -
                            x
                        )
            2 ->
                (
                    size -
                        x
                    ) to
                    (
                        size -
                            y
                        )
            3 ->
                (
                    size -
                        y
                    ) to
                    x
            else ->
                x to
                    y
        }

    // YARD POSITION LOCK: these four logical origins permanently anchor the four color homes.
    // RED=top-left, GREEN=top-right, YELLOW=bottom-right, BLUE=bottom-left.
    private fun drawYards(
        canvas: Canvas,
        cell: Float,
    ) {
        yard(
            canvas,
            cell,
            row = 0,
            col = 0,
            color = colorFor("RED"),
        )
        yard(
            canvas,
            cell,
            row = 0,
            col = 9,
            color = colorFor("GREEN"),
        )
        yard(
            canvas,
            cell,
            row = 9,
            col = 9,
            color = colorFor("YELLOW"),
        )
        yard(
            canvas,
            cell,
            row = 9,
            col = 0,
            color = colorFor("BLUE"),
        )
    }

    private fun yard(
        canvas: Canvas,
        cell: Float,
        row: Int,
        col: Int,
        color: Int,
    ) {
        val yardRect =
            RectF(
                axisBoundary(col, cell),
                axisBoundary(row, cell),
                axisBoundary(col + 6, cell),
                axisBoundary(row + 6, cell),
            )

        // WHITE-HOME GEOMETRY LOCK: 0.5 base-cell margin on ALL four sides.
        // The white home remains exactly 4 x 4 base cells. Do not move or resize it independently;
        // the released road stretch, yard token centers, and overlay geometry depend on this.
        val whiteRect =
            RectF(
                yardRect.left + cell * 0.5f,
                yardRect.top + cell * 0.5f,
                yardRect.right - cell * 0.5f,
                yardRect.bottom - cell * 0.5f,
            )

        fillPaint.shader = null
        fillPaint.color = color
        canvas.drawRect(
            yardRect,
            fillPaint,
        )
        // Overlay a stable grain pattern before drawing the clean inner home.
        // The existing whiteRect masks the texture automatically, so only the
        // red/green/blue/yellow surface receives the rough finish.
        canvas.drawRect(yardRect, yardTexturePaint)

        // Ludo Paws keeps the four token bays white for readable footprints.
        // Classic boards retain their existing theme-specific neutral fill.
        fillPaint.color =
            if (ludoPawsYardFootprintsEnabled) {
                Color.WHITE
            } else {
                neutralCellColor()
            }
        canvas.drawRoundRect(
            whiteRect,
            cell * 0.35f,
            cell * 0.35f,
            fillPaint,
        )

        strokePaint.color = Color.argb(
            230,
            22,
            33,
            52,
        )
        strokePaint.strokeWidth = density(1.4f)
        canvas.drawRoundRect(
            whiteRect,
            cell * 0.35f,
            cell * 0.35f,
            strokePaint,
        )
        strokePaint.strokeWidth = density(1f)
        strokePaint.color = Color.rgb(70, 70, 70)
    }

    /**
     * Builds a deterministic, transparent grain tile once per board view.
     * A fixed seed prevents the texture from crawling or flickering on redraw.
     */
    private fun createYardTexturePaint(): Paint {
        val tileSize = density(64f).toInt().coerceAtLeast(32)
        val textureBitmap =
            Bitmap.createBitmap(
                tileSize,
                tileSize,
                Bitmap.Config.ARGB_8888,
            )
        val textureCanvas = Canvas(textureBitmap)
        val random = Random(0x4C55444FL)
        val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Fine flecks provide an overall rough matte surface; larger, darker
        // grains add tactile variation without changing the base team color.
        repeat(950) {
            val x = random.nextFloat() * tileSize
            val y = random.nextFloat() * tileSize
            val fineGrain = random.nextFloat() < 0.65f
            val alpha =
                if (fineGrain) {
                    10 + random.nextInt(23)
                } else {
                    22 + random.nextInt(51)
                }
            val highlight = random.nextFloat() < 0.28f
            grainPaint.color =
                if (highlight) {
                    Color.argb((alpha * 0.62f).toInt(), 255, 255, 255)
                } else {
                    Color.argb(alpha, 12, 16, 22)
                }
            val radius =
                density(
                    if (fineGrain) {
                        0.12f + random.nextFloat() * 0.23f
                    } else {
                        0.22f + random.nextFloat() * 0.48f
                    },
                )
            textureCanvas.drawCircle(x, y, radius, grainPaint)
        }

        // Short, irregular micro-scratches make the texture read as rough paint,
        // not as a smooth gradient or a flat transparency overlay.
        grainPaint.style = Paint.Style.STROKE
        repeat(90) {
            val x = random.nextFloat() * tileSize
            val y = random.nextFloat() * tileSize
            val length = density(0.55f + random.nextFloat() * 1.55f)
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val endX = (x + cos(angle) * length).coerceIn(0f, tileSize - 1f)
            val endY = (y + sin(angle) * length).coerceIn(0f, tileSize - 1f)
            val alpha = 18 + random.nextInt(35)
            grainPaint.color =
                if (random.nextBoolean()) {
                    Color.argb(alpha, 10, 14, 20)
                } else {
                    Color.argb((alpha * 0.58f).toInt(), 255, 255, 255)
                }
            grainPaint.strokeWidth = density(0.16f + random.nextFloat() * 0.28f)
            textureCanvas.drawLine(x, y, endX, endY, grainPaint)
        }

        return Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            style = Paint.Style.FILL
            shader =
                BitmapShader(
                    textureBitmap,
                    Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT,
                )
        }
    }

    // ROAD/TRACK LOCK: draw the approved 52-square movement path exactly from TRACK.
    // Do not change road-square positions, order, start cells, or safe-cell alignment here.
    private fun drawTrack(
        canvas: Canvas,
        cell: Float,
    ) {
        for ((index, coord) in TRACK.withIndex()) {
            val color =
                when (index) {
                    0 -> colorFor("RED")
                    13 -> colorFor("GREEN")
                    26 -> colorFor("YELLOW")
                    39 -> colorFor("BLUE")
                    else -> neutralCellColor()
                }
            drawCell(
                canvas,
                cell,
                coord.first,
                coord.second,
                color,
            )

            if (index in SAFE_GLOBAL_CELLS) {
                val center =
                    centerForCell(
                        coord.first,
                        coord.second,
                        cell,
                    )
                drawStar(
                    canvas,
                    center.first,
                    center.second,
                    cell * 0.27f,
                    when (index) {
                        0 -> colorFor("RED")
                        13 -> colorFor("GREEN")
                        26 -> colorFor("YELLOW")
                        39 -> colorFor("BLUE")
                        else -> Color.rgb(45, 138, 204)
                    },
                )
            }
        }
    }

    // HOME-LANE LOCK: each color keeps the approved five-cell lane into the center.
    // These coordinates are part of the fixed board and must stay synchronized with tokenCenter().
    private fun drawHomeLanes(
        canvas: Canvas,
        cell: Float,
    ) {
        HOME_LANES.forEach { (colorName, lane) ->
            val color = colorFor(colorName)
            lane.forEach { coord ->
                drawCell(
                    canvas,
                    cell,
                    coord.first,
                    coord.second,
                    color,
                )
            }
        }
    }

    // CENTER LOCK: the four finish triangles are permanently bounded by logical indices 6..9.
    // Derive them from axisBoundary() so they always meet the stretched three-lane road exactly.
    private fun drawCenter(
        canvas: Canvas,
        cell: Float,
    ) {
        val left = axisBoundary(6, cell)
        val top = axisBoundary(6, cell)
        val right = axisBoundary(9, cell)
        val bottom = axisBoundary(9, cell)
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f

        triangle(
            canvas,
            colorFor("RED"),
            left,
            top,
            left,
            bottom,
            cx,
            cy,
        )
        triangle(
            canvas,
            colorFor("GREEN"),
            left,
            top,
            right,
            top,
            cx,
            cy,
        )
        triangle(
            canvas,
            colorFor("YELLOW"),
            right,
            top,
            right,
            bottom,
            cx,
            cy,
        )
        triangle(
            canvas,
            colorFor("BLUE"),
            left,
            bottom,
            right,
            bottom,
            cx,
            cy,
        )
    }

    private fun triangle(
        canvas: Canvas,
        color: Int,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
    ) {
        fillPaint.color = color
        val path =
            Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
                lineTo(x3, y3)
                close()
            }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }

    // ROAD CELL RECT LOCK: every square/rectangle must come from axisBoundary().
    // Do not calculate track cells with col * cell / row * cell; that would break the final layout.
    private fun drawCell(
        canvas: Canvas,
        cell: Float,
        row: Int,
        col: Int,
        color: Int,
    ) {
        fillPaint.color = color
        val rect =
            RectF(
                axisBoundary(col, cell),
                axisBoundary(row, cell),
                axisBoundary(col + 1, cell),
                axisBoundary(row + 1, cell),
            )
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, strokePaint)
    }

    private fun drawTokens(
        canvas: Canvas,
        cell: Float,
    ) {
        val state = snapshot ?: return
        val legal =
            state.pendingRoll
                ?.legalTokenIndexes
                .orEmpty()
        val localId = localPlayerId

        state.players.forEach { player ->
            player.tokens.forEachIndexed {
                    tokenIndex,
                    position,
                ->
                val base =
                    animatedTokenCenter(
                        player =
                            player,
                        tokenIndex =
                            tokenIndex,
                        currentPosition =
                            position,
                        cell =
                            cell,
                    )
                        ?: return@forEachIndexed

                val offset =
                    if (position in 0..57) {
                        tokenOffset(
                            tokenIndex + player.seat,
                            cell,
                        )
                    } else {
                        0f to 0f
                    }

                val x = base.first + offset.first
                val y = base.second + offset.second
                val radius = cell * 0.27f
                val isLegal =
                    player.playerId == localId &&
                        tokenIndex in legal &&
                        state.pendingRoll?.status ==
                        "RESOLVED"

                if (isLegal) {
                    canvas.drawCircle(
                        x,
                        y,
                        radius * 1.28f,
                        legalPaint,
                    )
                    tokenHits +=
                        TokenHit(
                            tokenIndex = tokenIndex,
                            x = x,
                            y = y,
                            radius = radius * 1.8f,
                        )
                }

                drawPawn(
                    canvas,
                    x,
                    y,
                    radius,
                    colorFor(player.color),
                )
            }
        }
    }


    /**
     * Top-layer entry point used by LudoPawsBoardChromeView after all opaque board
     * chrome has been composed. Uses this board copy's current authoritative state
     * and identity assignment, so fallback transitions do not leave stale prints.
     */
    internal fun drawYardPawPrintsOverlay(
        canvas: Canvas,
        cell: Float,
    ) {
        if (!ludoPawsYardFootprintsEnabled || snapshot == null) return
        drawYardPawPrints(canvas, cell)
    }

    internal fun drawFinishRanksOverlay(canvas: Canvas, cell: Float) {
        val state = snapshot ?: return
        if (state.players.size != 4 || state.status == "WAITING" || state.matchMode == "TEAM_UP") return
        val rankedIds = state.finishOrderPlayerIds.take(4).toMutableList()
        // Remote classic servers may expose only the winner, not the full finish order.
        // In that case, mark only the authoritative winner as First; never invent the other ranks.
        if (state.status == "FINISHED" && rankedIds.isEmpty()) {
            state.winnerPlayerId?.let { rankedIds += it }
        }
        // Local classic games finish after three players have placed. Only then is the
        // single remaining player safely known to be Fourth.
        if (state.status == "FINISHED" && state.finishOrderPlayerIds.size == state.players.size - 1) {
            state.players.firstOrNull { it.playerId !in rankedIds }?.let { rankedIds += it.playerId }
        }
        if (rankedIds.isEmpty()) return
        val labels = listOf("First", "Second", "Third", "Fourth")
        val labelsByPlayerId = rankedIds.take(4).mapIndexed { rank, id -> id to labels[rank] }.toMap()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 43, 58)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = (cell * 0.34f).coerceIn(density(7f), density(12f))
        }
        state.players.forEach { player ->
            val label = labelsByPlayerId[player.playerId] ?: return@forEach
            val origin = when (player.color) {
                "RED" -> 0 to 0
                "GREEN" -> 0 to 9
                "YELLOW" -> 9 to 9
                "BLUE" -> 9 to 0
                else -> return@forEach
            }
            val left = axisBoundary(origin.second, cell) + cell * 0.5f
            val top = axisBoundary(origin.first, cell) + cell * 0.5f
            val whiteSize = cell * 4f
            val centerX = left + whiteSize * 0.5f
            val centerY = top + whiteSize * 0.5f
            val baseline = centerY - (labelPaint.ascent() + labelPaint.descent()) * 0.5f
            canvas.drawText(label, centerX, baseline, labelPaint)
        }
    }

    /**
     * Draw a species-specific pressed footprint at every starting slot whose
     * token is currently outside its yard. Positions and species both come from
     * the authoritative state/seat assignment, never from a visual animation.
     */
    private fun drawYardPawPrints(
        canvas: Canvas,
        cell: Float,
    ) {
        val state = snapshot ?: return
        val footprints =
            LudoPawsYardFootprintPolicy.footprints(
                snapshot = state,
                characterIdsBySeat = characterIdsBySeat,
            )
        for (footprint in footprints) {
            val center =
                yardTokenCenter(
                    color = footprint.teamColor,
                    tokenIndex = footprint.tokenIndex,
                    cell = cell,
                ) ?: continue
            drawYardPawPrint(
                canvas = canvas,
                x = center.first,
                y = center.second,
                cell = cell,
                species = footprint.species,
                teamColor = colorFor(footprint.teamColor),
            )
        }
    }

    private fun drawYardPawPrint(
        canvas: Canvas,
        x: Float,
        y: Float,
        cell: Float,
        species: LudoPaws3DSpecies,
        teamColor: Int,
    ) {
        val artwork = pawPrintArtwork[species] ?: return
        // Slightly enlarge the impression while keeping all four starting slots
        // visually separate. The team's outer edge/glow colours remain unchanged.
        val scale = (cell * 0.34f).coerceAtLeast(density(0.1f))
        val red = Color.red(teamColor)
        val green = Color.green(teamColor)
        val blue = Color.blue(teamColor)

        // Warm, compressed-dirt pigment instead of a cold, flat grey stamp.
        pawPrintFillPaint.shader = null
        pawPrintFillPaint.color = Color.argb(54, 59, 51, 43)
        pawPrintGlowPaint.color = Color.argb(76, red, green, blue)
        pawPrintEdgePaint.color = Color.argb(232, red, green, blue)
        // Creases read as shallow grooves inside the print, not white decorative
        // lines. Fine grain adds small irregularity without changing the outline.
        pawPrintDetailPaint.color = Color.argb(66, 38, 33, 29)
        // Canvas scaling also scales stroke widths, so normalize them to retain
        // stable dp-sized highlights on phones, tablets and high-density screens.
        pawPrintGlowPaint.strokeWidth = density(3.5f) / scale
        pawPrintEdgePaint.strokeWidth = density(1.35f) / scale
        pawPrintDetailPaint.strokeWidth = density(0.8f) / scale

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        artwork.pads.forEach { pad ->
            canvas.drawPath(pad, pawPrintFillPaint)
            // Clip the dirt grain to each pad. That gives the stamp a lightly
            // pressed, uneven surface while preserving each animal's silhouette.
            canvas.save()
            canvas.clipPath(pad)
            canvas.drawRect(-1.2f, -1.2f, 1.2f, 1.2f, pawPrintTexturePaint)
            canvas.restore()
            canvas.drawPath(pad, pawPrintGlowPaint)
            canvas.drawPath(pad, pawPrintEdgePaint)
        }
        artwork.details.forEach { detail ->
            canvas.drawPath(detail, pawPrintDetailPaint)
        }
        canvas.restore()
    }

    private fun createPawPrintArtwork(): Map<LudoPaws3DSpecies, PawPrintArtwork> {
        fun path(block: Path.() -> Unit) = Path().apply(block)

        // Toe pads are deliberately asymmetric: each has a soft tapered end and a
        // fuller pressure pad, avoiding the identical oval beads that read as a flower.
        fun toePad(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            lean: Float = 0f,
        ) = path {
            val w = right - left
            val h = bottom - top
            val cx = (left + right) / 2f + lean
            moveTo(cx, top)
            cubicTo(
                cx + w * 0.34f, top - h * 0.02f,
                right + w * 0.04f, top + h * 0.18f,
                right - w * 0.04f, top + h * 0.48f,
            )
            cubicTo(
                right - w * 0.10f, bottom - h * 0.10f,
                cx + w * 0.23f, bottom + h * 0.035f,
                cx - w * 0.02f, bottom,
            )
            cubicTo(
                left + w * 0.19f, bottom + h * 0.01f,
                left - w * 0.04f, top + h * 0.63f,
                left + w * 0.05f, top + h * 0.28f,
            )
            cubicTo(
                left + w * 0.15f, top + h * 0.02f,
                cx - w * 0.29f, top - h * 0.025f,
                cx, top,
            )
            close()
        }

        // Broad central pads have a shallow upper cleft and a rounded heel. They
        // sit visibly below the toes, so the whole stamp reads as a walking paw.
        val catCenter =
            path {
                moveTo(-0.40f, 0.00f)
                cubicTo(-0.52f, -0.17f, -0.36f, -0.34f, -0.18f, -0.28f)
                cubicTo(-0.07f, -0.24f, -0.07f, -0.13f, 0f, -0.13f)
                cubicTo(0.07f, -0.13f, 0.07f, -0.24f, 0.18f, -0.28f)
                cubicTo(0.36f, -0.34f, 0.52f, -0.17f, 0.40f, 0.00f)
                cubicTo(0.31f, 0.25f, 0.18f, 0.45f, 0f, 0.47f)
                cubicTo(-0.18f, 0.45f, -0.31f, 0.25f, -0.40f, 0.00f)
                close()
            }
        val dogCenter =
            path {
                moveTo(-0.43f, 0.02f)
                cubicTo(-0.55f, -0.16f, -0.36f, -0.35f, -0.16f, -0.30f)
                cubicTo(-0.06f, -0.28f, -0.04f, -0.22f, 0f, -0.22f)
                cubicTo(0.04f, -0.22f, 0.06f, -0.28f, 0.16f, -0.30f)
                cubicTo(0.36f, -0.35f, 0.55f, -0.16f, 0.43f, 0.02f)
                cubicTo(0.36f, 0.30f, 0.21f, 0.53f, 0f, 0.56f)
                cubicTo(-0.21f, 0.53f, -0.36f, 0.30f, -0.43f, 0.02f)
                close()
            }

        // Duck prints retain a connected webbed three-toe silhouette, but the
        // outer toe ends and heel are intentionally uneven like a real wet print.
        val duckWeb =
            path {
                moveTo(-0.58f, 0.40f)
                cubicTo(-0.75f, 0.18f, -1.01f, -0.12f, -0.95f, -0.42f)
                cubicTo(-0.91f, -0.65f, -0.76f, -0.70f, -0.66f, -0.46f)
                cubicTo(-0.57f, -0.26f, -0.47f, -0.08f, -0.34f, 0.04f)
                cubicTo(-0.39f, -0.22f, -0.46f, -0.52f, -0.31f, -0.77f)
                cubicTo(-0.19f, -0.96f, -0.04f, -0.86f, -0.02f, -0.61f)
                cubicTo(0.00f, -0.40f, 0.00f, -0.15f, 0.03f, 0.04f)
                cubicTo(0.20f, -0.14f, 0.37f, -0.39f, 0.55f, -0.57f)
                cubicTo(0.72f, -0.75f, 0.90f, -0.57f, 0.80f, -0.34f)
                cubicTo(0.70f, -0.08f, 0.51f, 0.22f, 0.42f, 0.40f)
                cubicTo(0.20f, 0.62f, -0.39f, 0.62f, -0.58f, 0.40f)
                close()
            }

        val goatLeft =
            path {
                moveTo(-0.52f, 0.39f)
                cubicTo(-0.63f, 0.20f, -0.48f, -0.18f, -0.35f, -0.43f)
                cubicTo(-0.28f, -0.58f, -0.09f, -0.56f, -0.05f, -0.37f)
                cubicTo(0.00f, -0.10f, -0.02f, 0.21f, -0.12f, 0.42f)
                cubicTo(-0.23f, 0.57f, -0.44f, 0.55f, -0.52f, 0.39f)
                close()
            }
        val goatRight =
            Path(goatLeft).apply {
                transform(
                    android.graphics.Matrix().apply {
                        setScale(-1f, 1f)
                    },
                )
            }
        val duckCreaseLeft =
            path {
                moveTo(-0.48f, -0.07f)
                quadTo(-0.34f, 0.17f, -0.26f, 0.34f)
            }
        val duckCreaseCenter =
            path {
                moveTo(-0.02f, -0.08f)
                quadTo(0.00f, 0.18f, 0.00f, 0.39f)
            }
        val duckCreaseRight =
            path {
                moveTo(0.40f, -0.05f)
                quadTo(0.30f, 0.19f, 0.22f, 0.36f)
            }
        val goatSplit =
            path {
                moveTo(0f, -0.34f)
                cubicTo(-0.04f, -0.08f, -0.02f, 0.22f, 0f, 0.38f)
            }
        val catPadCrease =
            path {
                moveTo(-0.18f, 0.25f)
                quadTo(0f, 0.35f, 0.18f, 0.25f)
            }
        val dogPadCrease =
            path {
                moveTo(-0.21f, 0.27f)
                quadTo(0f, 0.42f, 0.21f, 0.27f)
            }

        return mapOf(
            LudoPaws3DSpecies.CAT to
                PawPrintArtwork(
                    pads = listOf(
                        toePad(-0.76f, -0.80f, -0.45f, -0.42f, lean = -0.035f),
                        toePad(-0.39f, -0.91f, -0.08f, -0.53f, lean = -0.015f),
                        toePad(0.08f, -0.91f, 0.39f, -0.53f, lean = 0.018f),
                        toePad(0.45f, -0.80f, 0.76f, -0.42f, lean = 0.035f),
                        catCenter,
                    ),
                    details = listOf(catPadCrease),
                ),
            LudoPaws3DSpecies.DOG to
                PawPrintArtwork(
                    pads = listOf(
                        toePad(-0.79f, -0.72f, -0.44f, -0.34f, lean = -0.04f),
                        toePad(-0.41f, -0.89f, -0.04f, -0.49f, lean = -0.018f),
                        toePad(0.04f, -0.89f, 0.41f, -0.49f, lean = 0.02f),
                        toePad(0.44f, -0.72f, 0.79f, -0.34f, lean = 0.04f),
                        dogCenter,
                    ),
                    details = listOf(dogPadCrease),
                ),
            LudoPaws3DSpecies.DUCK to
                PawPrintArtwork(
                    pads = listOf(duckWeb),
                    details = listOf(
                        duckCreaseLeft,
                        duckCreaseCenter,
                        duckCreaseRight,
                    ),
                ),
            LudoPaws3DSpecies.GOAT to
                PawPrintArtwork(
                    pads = listOf(goatLeft, goatRight),
                    details = listOf(goatSplit),
                ),
        )
    }

    /**
     * Transparent organic flecks are clipped to each print pad. A small local shader
     * tile keeps the grain subtle and consistent without allocating anything per frame.
     */
    private fun createPawPrintTexturePaint(): Paint {
        val size = 64
        val bitmap =
            Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888,
            )
        val textureCanvas = Canvas(bitmap)
        val random = Random(0x5052494E54L)
        val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        repeat(100) {
            grainPaint.color =
                if (random.nextInt(4) == 0) {
                    Color.argb(17 + random.nextInt(13), 235, 221, 199)
                } else {
                    Color.argb(15 + random.nextInt(19), 35, 29, 24)
                }
            val x = random.nextFloat() * size
            val y = random.nextFloat() * size
            val radius =
                if (random.nextInt(5) == 0) {
                    1.2f + random.nextFloat() * 1.4f
                } else {
                    0.45f + random.nextFloat() * 0.9f
                }
            textureCanvas.drawCircle(x, y, radius, grainPaint)
        }

        grainPaint.style = Paint.Style.STROKE
        repeat(18) {
            grainPaint.color = Color.argb(12 + random.nextInt(13), 35, 29, 24)
            grainPaint.strokeWidth = 0.4f + random.nextFloat() * 0.7f
            val x = random.nextFloat() * size
            val y = random.nextFloat() * size
            textureCanvas.drawLine(
                x,
                y,
                (x + 1.5f + random.nextFloat() * 4f).coerceAtMost(size - 1f),
                (y + (random.nextFloat() - 0.5f) * 2.5f).coerceIn(0f, size - 1f),
                grainPaint,
            )
        }

        val shader =
            BitmapShader(
                bitmap,
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            )
        shader.setLocalMatrix(
            android.graphics.Matrix().apply {
                setScale(1f / size, 1f / size)
            },
        )
        return Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            style = Paint.Style.FILL
            this.shader = shader
        }
    }

    // Artwork paths are normalized once, not recreated during gameplay redraws.
    private val pawPrintArtwork by lazy { createPawPrintArtwork() }

    private fun startMoveAnimationIfNeeded(
        previous: MatchSnapshot?,
        current: MatchSnapshot?,
    ) {
        moveAnimator
            ?.cancel()
        moveAnimator =
            null
        moveAnimation =
            null

        if (
            previous ==
                null ||
            current ==
                null ||
            previous.matchId !=
                current.matchId
        ) {
            return
        }

        val movement =
            current.players
                .asSequence()
                .mapNotNull {
                        currentPlayer ->
                    val previousPlayer =
                        previous.players
                            .firstOrNull {
                                it.playerId ==
                                    currentPlayer.playerId
                            }
                            ?: return@mapNotNull null

                    currentPlayer.tokens
                        .indices
                        .firstNotNullOfOrNull {
                                tokenIndex ->
                            val from =
                                previousPlayer.tokens
                                    .getOrNull(
                                        tokenIndex,
                                    )
                                    ?: return@firstNotNullOfOrNull null
                            val to =
                                currentPlayer.tokens
                                    .getOrNull(
                                        tokenIndex,
                                    )
                                    ?: return@firstNotNullOfOrNull null

                            if (
                                to >
                                from
                            ) {
                                TokenMoveAnimation(
                                    playerId =
                                        currentPlayer.playerId,
                                    tokenIndex =
                                        tokenIndex,
                                    fromPosition =
                                        from,
                                    toPosition =
                                        to,
                                    progress =
                                        0f,
                                )
                            } else {
                                null
                            }
                        }
                }
                .firstOrNull()
                ?: return

        val steps =
            LudoPathEncoding
                .visualStepCount(
                    fromPosition = movement.fromPosition,
                    toPosition = movement.toPosition,
                )
                .coerceAtLeast(1)
        val speed =
            GameSettingsStore(
                context,
            )
                .snapshot()
                .gameSpeed
        val duration =
            (
                steps.toLong() *
                    speed.moveStepMs
                )
                .coerceAtLeast(
                    speed.moveStepMs,
                )

        moveAnimation =
            movement
        moveAnimator =
            ValueAnimator
                .ofFloat(
                    0f,
                    steps.toFloat(),
                )
                .apply {
                    this.duration =
                        duration
                    addUpdateListener {
                            animator ->
                        moveAnimation =
                            moveAnimation
                                ?.copy(
                                    progress =
                                        animator
                                            .animatedValue as Float,
                                )
                        invalidate()
                    }
                    addListener(
                        object :
                            android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(
                                animation: android.animation.Animator,
                            ) {
                                moveAnimation =
                                    null
                                moveAnimator =
                                    null
                                invalidate()
                            }

                            override fun onAnimationCancel(
                                animation: android.animation.Animator,
                            ) {
                                moveAnimation =
                                    null
                                moveAnimator =
                                    null
                                invalidate()
                            }
                        },
                    )
                    start()
                }
    }

    private fun animatedTokenCenter(
        player: PlayerSnapshot,
        tokenIndex: Int,
        currentPosition: Int,
        cell: Float,
    ): Pair<Float, Float>? {
        val animation =
            moveAnimation
                ?.takeIf {
                    it.playerId ==
                        player.playerId &&
                        it.tokenIndex ==
                        tokenIndex
                }
                ?: return tokenCenter(
                    player,
                    tokenIndex,
                    currentPosition,
                    cell,
                )

        val totalSteps =
            LudoPathEncoding
                .visualStepCount(
                    fromPosition = animation.fromPosition,
                    toPosition = animation.toPosition,
                )
                .coerceAtLeast(1)
        val progress =
            animation.progress
                .coerceIn(
                    0f,
                    totalSteps.toFloat(),
                )
        val whole =
            kotlin.math.floor(
                progress,
            )
                .toInt()
                .coerceAtMost(
                    totalSteps -
                        1,
                )
        val fraction =
            (
                progress -
                    whole
                )
                .coerceIn(
                    0f,
                    1f,
                )
        val fromPosition =
            LudoPathEncoding
                .positionAtVisualStep(
                    fromPosition = animation.fromPosition,
                    step = whole,
                )
                ?: return null
        val toPosition =
            LudoPathEncoding
                .positionAtVisualStep(
                    fromPosition = animation.fromPosition,
                    step =
                        (whole + 1)
                            .coerceAtMost(totalSteps),
                )
                ?: return null
        val from =
            tokenCenter(
                player,
                tokenIndex,
                fromPosition,
                cell,
            )
                ?: return null
        val to =
            tokenCenter(
                player,
                tokenIndex,
                toPosition,
                cell,
            )
                ?: return from

        return (
            from.first +
                (
                    to.first -
                        from.first
                    ) *
                fraction
            ) to
            (
                from.second +
                    (
                        to.second -
                            from.second
                        ) *
                    fraction
                )
    }

    private fun drawStar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
    ) {
        val path = Path()
        for (i in 0 until 10) {
            val angle =
                Math.toRadians(
                    (-90.0 + i * 36.0),
                )
            val r =
                if (i % 2 == 0) {
                    radius
                } else {
                    radius * 0.45f
                }
            val x =
                cx +
                    (Math.cos(angle) * r)
                        .toFloat()
            val y =
                cy +
                    (Math.sin(angle) * r)
                        .toFloat()
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        fillPaint.shader = null
        fillPaint.color = color
        canvas.drawPath(path, fillPaint)
    }

    private fun drawPawn(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int,
    ) {
        fillPaint.shader = null
        fillPaint.color =
            Color.argb(
                80,
                0,
                0,
                0,
            )
        canvas.drawOval(
            x - radius * 0.8f,
            y + radius * 0.62f,
            x + radius * 0.8f,
            y + radius * 1.02f,
            fillPaint,
        )

        fillPaint.shader =
            RadialGradient(
                x - radius * 0.28f,
                y - radius * 0.35f,
                radius * 1.25f,
                intArrayOf(
                    Color.WHITE,
                    color,
                    darken(color),
                ),
                floatArrayOf(
                    0f,
                    0.30f,
                    1f,
                ),
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(
            x,
            y - radius * 0.18f,
            radius * 0.72f,
            fillPaint,
        )
        fillPaint.shader = null
        fillPaint.color = color
        canvas.drawRoundRect(
            x - radius * 0.58f,
            y + radius * 0.16f,
            x + radius * 0.58f,
            y + radius * 0.72f,
            radius * 0.28f,
            radius * 0.28f,
            fillPaint,
        )
        canvas.drawCircle(
            x,
            y - radius * 0.18f,
            radius * 0.72f,
            tokenStrokePaint,
        )
    }

    private fun darken(
        color: Int,
    ): Int =
        Color.rgb(
            (Color.red(color) * 0.56f)
                .toInt(),
            (Color.green(color) * 0.56f)
                .toInt(),
            (Color.blue(color) * 0.56f)
                .toInt(),
        )

    // TOKEN POSITION LOCK: movement exits the shared track after relative position 50.
    // Encoded position 51 is legacy-only and is rendered as the first home-lane position.
    private fun tokenCenter(
        player: PlayerSnapshot,
        tokenIndex: Int,
        position: Int,
        cell: Float,
    ): Pair<Float, Float>? {
        val renderPosition =
            LudoPathEncoding
                .normalizeLegacyEntry(position)

        if (renderPosition == -1) {
            return yardTokenCenter(
                player.color,
                tokenIndex,
                cell,
            )
        }

        if (LudoPathEncoding.isTrackPosition(renderPosition)) {
            val offset =
                START_OFFSETS[player.color]
                    ?: return null
            val global =
                (offset + renderPosition) %
                    TRACK.size
            val coord = TRACK[global]
            return centerForCell(
                coord.first,
                coord.second,
                cell,
            )
        }

        if (
            renderPosition in
            LudoPathEncoding.FIRST_HOME_LANE_POSITION..
                LudoPathEncoding.LAST_HOME_LANE_POSITION
        ) {
            val lane =
                HOME_LANES[player.color]
                    ?: return null
            val coord =
                lane[
                    renderPosition -
                        LudoPathEncoding.FIRST_HOME_LANE_POSITION
                ]
            return centerForCell(
                coord.first,
                coord.second,
                cell,
            )
        }

        if (renderPosition == LudoPathEncoding.HOME_POSITION) {
            val left = axisBoundary(6, cell)
            val top = axisBoundary(6, cell)
            val right = axisBoundary(9, cell)
            val bottom = axisBoundary(9, cell)
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            val offset = (right - left) * 0.20f
            return when (player.color) {
                "RED" -> cx - offset to cy
                "GREEN" -> cx to cy - offset
                "YELLOW" -> cx + offset to cy
                "BLUE" -> cx to cy + offset
                else -> cx to cy
            }
        }

        return null
    }

    // YARD TOKEN LOCK: the four resting-token slots are tied to the fixed 4x4 white home.
    // Keep the 0.5-cell inset and 25%/75% slot fractions unchanged unless the board is redesigned.
    private fun yardTokenCenter(
        color: String,
        tokenIndex: Int,
        cell: Float,
    ): Pair<Float, Float>? {
        val origin =
            when (color) {
                "RED" -> 0 to 0
                "GREEN" -> 0 to 9
                "YELLOW" -> 9 to 9
                "BLUE" -> 9 to 0
                else -> return null
            }

        val yardRect =
            RectF(
                axisBoundary(origin.second, cell),
                axisBoundary(origin.first, cell),
                axisBoundary(origin.second + 6, cell),
                axisBoundary(origin.first + 6, cell),
            )
        val whiteLeft = yardRect.left + cell * 0.5f
        val whiteTop = yardRect.top + cell * 0.5f
        val whiteSize = cell * 4f

        val rowFraction =
            if (tokenIndex < 2) 0.25f else 0.75f
        val colFraction =
            if (tokenIndex % 2 == 0) 0.25f else 0.75f

        return (whiteLeft + whiteSize * colFraction) to
            (whiteTop + whiteSize * rowFraction)
    }

    private fun tokenOffset(
        slot: Int,
        cell: Float,
    ): Pair<Float, Float> =
        when (slot % 4) {
            0 -> -cell * 0.13f to -cell * 0.13f
            1 -> cell * 0.13f to -cell * 0.13f
            2 -> -cell * 0.13f to cell * 0.13f
            else -> cell * 0.13f to cell * 0.13f
        }

    // CELL CENTER LOCK: road/home-lane token centers are the midpoint of the same boundaries
    // used to draw their cells. This keeps hit-testing, animation, and visuals aligned.
    private fun centerForCell(
        row: Int,
        col: Int,
        cell: Float,
    ): Pair<Float, Float> =
        ((axisBoundary(col, cell) +
            axisBoundary(col + 1, cell)) / 2f) to
            ((axisBoundary(row, cell) +
                axisBoundary(row + 1, cell)) / 2f)

    /**
     * FINAL BOARD GEOMETRY — LOCKED.
     *
     * Maps the logical 15x15 Ludo grid onto the approved final visual geometry:
     * - left/top corner-home span: 5 base cells
     * - middle three-lane road span: 5 base cells
     * - right/bottom corner-home span: 5 base cells
     * - white home inside each yard: 0.5 + 4 + 0.5 base cells
     *
     * Six logical yard rows/columns share a 5-cell physical span, while the three logical road
     * rows/columns share a 5-cell physical span. This is the intentional road stretch.
     *
     * DO NOT change yardSpan, roadSpan, yardStep, roadStep, or the boundary ranges for routine UI
     * work. Changing this method moves every road square, yard, center triangle, and token center.
     */
    private fun axisBoundary(
        index: Int,
        cell: Float,
    ): Float {
        val yardSpan = cell * 5f
        val roadSpan = cell * 5f
        val yardStep = yardSpan / 6f
        val roadStep = roadSpan / 3f

        return when {
            index <= 6 -> index * yardStep
            index <= 9 -> yardSpan + (index - 6) * roadStep
            else -> yardSpan + roadSpan + (index - 9) * yardStep
        }
    }

    private fun colorFor(
        name: String,
    ): Int {
        val classic =
            when (name) {
                "RED" -> Color.rgb(241, 37, 47)
                "GREEN" -> Color.rgb(0, 169, 80)
                "YELLOW" -> Color.rgb(255, 216, 27)
                "BLUE" -> Color.rgb(48, 151, 215)
                else -> Color.rgb(108, 117, 125)
            }

        return BoardRegistry.resolve(boardThemeId).teamColor(classic)
    }

    private fun boardSurfaceColor(): Int = BoardRegistry.resolve(boardThemeId).surfaceColor
    private fun neutralCellColor(): Int = BoardRegistry.resolve(boardThemeId).neutralCellColor

    private fun density(value: Float): Float =
        value * resources.displayMetrics.density

    private data class TokenMoveAnimation(
        val playerId: String,
        val tokenIndex: Int,
        val fromPosition: Int,
        val toPosition: Int,
        val progress: Float,
    )

    private data class TokenHit(
        val tokenIndex: Int,
        val x: Float,
        val y: Float,
        val radius: Float,
    )

    private companion object {
        val SAFE_GLOBAL_CELLS =
            setOf(0, 8, 13, 21, 26, 34, 39, 47)

        val START_OFFSETS =
            mapOf(
                "RED" to 0,
                "GREEN" to 13,
                "YELLOW" to 26,
                "BLUE" to 39,
            )

        // LOGICAL TRACK LOCK: exactly 52 movement positions in this exact order.
        // Geometry may scale through axisBoundary(), but these logical coordinates must not drift.
        val TRACK =
            listOf(
                6 to 1,
                6 to 2,
                6 to 3,
                6 to 4,
                6 to 5,
                5 to 6,
                4 to 6,
                3 to 6,
                2 to 6,
                1 to 6,
                0 to 6,
                0 to 7,
                0 to 8,
                1 to 8,
                2 to 8,
                3 to 8,
                4 to 8,
                5 to 8,
                6 to 9,
                6 to 10,
                6 to 11,
                6 to 12,
                6 to 13,
                6 to 14,
                7 to 14,
                8 to 14,
                8 to 13,
                8 to 12,
                8 to 11,
                8 to 10,
                8 to 9,
                9 to 8,
                10 to 8,
                11 to 8,
                12 to 8,
                13 to 8,
                14 to 8,
                14 to 7,
                14 to 6,
                13 to 6,
                12 to 6,
                11 to 6,
                10 to 6,
                9 to 6,
                8 to 5,
                8 to 4,
                8 to 3,
                8 to 2,
                8 to 1,
                8 to 0,
                7 to 0,
                6 to 0,
            )

        // LOGICAL HOME-LANE LOCK: exactly five approach cells per color in these positions.
        val HOME_LANES =
            mapOf(
                "RED" to
                    listOf(
                        7 to 1,
                        7 to 2,
                        7 to 3,
                        7 to 4,
                        7 to 5,
                    ),
                "GREEN" to
                    listOf(
                        1 to 7,
                        2 to 7,
                        3 to 7,
                        4 to 7,
                        5 to 7,
                    ),
                "YELLOW" to
                    listOf(
                        7 to 13,
                        7 to 12,
                        7 to 11,
                        7 to 10,
                        7 to 9,
                    ),
                "BLUE" to
                    listOf(
                        13 to 7,
                        12 to 7,
                        11 to 7,
                        10 to 7,
                        9 to 7,
                    ),
            )
    }
}
