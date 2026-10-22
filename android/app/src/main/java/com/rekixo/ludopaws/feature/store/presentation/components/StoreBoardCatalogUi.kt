package com.rekixo.ludopaws.feature.store.presentation.components

import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.playSelectionPopAnimation
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

private const val LOCKED_BOARD_TITLE = "LOCKED BOARD"

internal fun StoreActivity.boardCatalogContent(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        // Render the full catalog. Draft content stays non-clickable until its
        // renderer/artwork gate is explicitly enabled in the catalog.
        val panels = StoreCosmeticCatalog.boards

        // Use the same two-card row rhythm as Paws so every board preview gets more room.
        panels.chunked(2).forEachIndexed { rowIndex, rowBoards ->
            val row =
                LinearLayout(this@boardCatalogContent).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }

            rowBoards.forEach { board ->
                row.addView(
                    boardPanel(board),
                    LinearLayout.LayoutParams(
                        0,
                        dp(188),
                        1f,
                    ).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    },
                )
            }

            repeat(2 - rowBoards.size) {
                row.addView(
                    TextView(this@boardCatalogContent),
                    LinearLayout.LayoutParams(0, dp(188), 1f),
                )
            }

            addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (rowIndex > 0) topMargin = dp(10)
                },
            )
        }
    }

private fun StoreActivity.boardPanel(
    board: StoreCosmetic,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(8), dp(6), dp(8))

        val locked = !board.contentAvailable
        val owned = cosmetics.isOwned(board.id)
        val selected =
            owned &&
                cosmetics.selectedId(board.category) == board.id

        // Only the timber panel is used. No blue/purple arcade face or coloured icon well.
        background =
            if (locked || !owned) {
                plywoodBackground(this@boardPanel, cornerRadiusDp = 13f)
            } else {
                playerCountSelectionBackground(
                    context = this@boardPanel,
                    selected = selected,
                    outerCornerRadiusDp = 13f,
                    checkBadgeAtTopEnd = true,
                    emphasizeSelected = true,
                    showCheckBadge = selected,
                    checkMarkScale = 2.0f,
                    useRecessedPlywood = false,
                )
            }
        elevation = dp(if (selected) 4 else 2).toFloat()
        isClickable = !locked
        isFocusable = !locked
        contentDescription =
            when {
                locked && board.remoteManaged -> "${board.title}, not available in this app build yet"
                locked -> "$LOCKED_BOARD_TITLE, locked, coming soon"
                !owned -> "${board.title}, available to unlock"
                else -> "${board.title}, unlocked" + if (selected) ", selected" else ""
            }

        addView(
            TextView(this@boardPanel).apply {
                text = if (locked && !board.remoteManaged) LOCKED_BOARD_TITLE else board.title
                textSize = 14.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                setShadowLayer(2f, 0f, dp(1).toFloat(), 0xAA3B2414.toInt())
                setPadding(0, 0, 0, dp(8))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        if (locked) {
            // Leave the locked placeholder blank; its title and lock state are the only preview.
            addView(
                View(this@boardPanel).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(82),
                ),
            )
        } else {
            // A passive Classic-only miniature mirrors the production board geometry and colors.
            // It deliberately has no touch handling, so taps continue to reach the whole panel.
            addView(
                ClassicBoardIconView(this@boardPanel, board.id).apply {
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    contentDescription = "Classic Ludo board preview"
                },
                LinearLayout.LayoutParams(
                    dp(104),
                    dp(104),
                ).apply {
                    topMargin = dp(1)
                    bottomMargin = dp(1)
                },
            )
        }

        addView(
            boardStatusBadge(board = board, locked = locked, owned = owned, selected = selected),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            },
        )

        if (locked) {
            addView(
                TextView(this@boardPanel).apply {
                    text = "COMING SOON"
                    textSize = 8.5f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFF65421E.toInt())
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    letterSpacing = 0.02f
                    setPadding(0, dp(3), 0, 0)
                },
            )
        } else {
            setOnClickListener { clicked ->
                clicked.playSelectionPopAnimation {
                    handleCosmeticTap(board)
                }
            }
            AppFocusRing.apply(this, cornerRadiusDp = 13f)
        }
    }

private fun StoreActivity.boardStatusBadge(
    board: StoreCosmetic,
    locked: Boolean,
    owned: Boolean,
    selected: Boolean,
): TextView =
    TextView(this).apply {
        text = when {
            locked -> "🔒 COMING SOON"
            selected -> "✓ EQUIPPED"
            owned -> "🔓 UNLOCKED"
            board.gemPrice > 0 -> "${board.gemPrice} GEMS"
            board.requiredAdViews > 0 -> "${board.requiredAdViews} ADS"
            else -> "GET"
        }
        textSize = 9.5f
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextColor(
            when {
                locked -> 0xFF65421E.toInt()
                selected -> 0xFF67430F.toInt()
                owned -> 0xFF104B29.toInt()
                else -> 0xFF235A86.toInt()
            },
        )
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        letterSpacing = 0.015f
        setPadding(dp(7), dp(4), dp(7), dp(4))
        background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                when {
                    locked ->
                        intArrayOf(0xFFE5D2B5.toInt(), 0xFFC4A47B.toInt())
                    selected ->
                        intArrayOf(0xFFFFF0AD.toInt(), 0xFFFFCB45.toInt())
                    else ->
                        intArrayOf(0xFFBDF5CB.toInt(), 0xFF7CE69A.toInt())
                },
            ).apply {
                cornerRadius = dp(999).toFloat()
                setStroke(
                    dp(1),
                    when {
                        locked -> 0xFF987044.toInt()
                        selected -> 0xFFFFF5C9.toInt()
                        else -> 0xFF47B86C.toInt()
                    },
                )
            }
    }


/**
 * Small, passive rendering of the production Classic board for the store card.
 *
 * Geometry and colors mirror LudoBoardView, but this view intentionally has no dependency on
 * the selected cosmetic theme and no touch handling. Keep TRACK/HOME_LANES in sync with the
 * production board's locked layout if that layout is ever deliberately changed.
 */
private class ClassicBoardIconView(
    context: android.content.Context,
    private val previewThemeId: String,
) : View(context) {
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = themeSurfaceColor()
    }
    private val yardTexturePaint = createYardTexturePaint()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cellStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.55f)
        color = Color.rgb(70, 70, 70)
    }
    private val yardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
        color = Color.argb(230, 22, 33, 52)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val cell = size / 15f
        val outer = RectF(0f, 0f, size, size)
        val clip = Path().apply {
            addRoundRect(outer, dp(3f), dp(3f), Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRoundRect(outer, dp(3f), dp(3f), surfacePaint)
        drawYards(canvas, cell)
        drawTrack(canvas, cell)
        drawHomeLanes(canvas, cell)
        drawCenter(canvas, cell)
        canvas.restore()
    }

    private fun drawYards(canvas: Canvas, cell: Float) {
        yard(canvas, cell, 0, 0, RED)
        yard(canvas, cell, 0, 9, GREEN)
        yard(canvas, cell, 9, 9, YELLOW)
        yard(canvas, cell, 9, 0, BLUE)
    }

    private fun yard(canvas: Canvas, cell: Float, row: Int, col: Int, color: Int) {
        val yard = RectF(
            boundary(col, cell),
            boundary(row, cell),
            boundary(col + 6, cell),
            boundary(row + 6, cell),
        )
        fillPaint.color = themeColor(color)
        canvas.drawRect(yard, fillPaint)
        canvas.drawRect(yard, yardTexturePaint)

        val inset = cell * 0.5f
        val home = RectF(
            yard.left + inset,
            yard.top + inset,
            yard.right - inset,
            yard.bottom - inset,
        )
        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(home, cell * 0.35f, cell * 0.35f, fillPaint)
        canvas.drawRoundRect(home, cell * 0.35f, cell * 0.35f, yardStrokePaint)
    }


    /**
     * Uses the same deterministic matte yard-grain treatment as the gameplay board.
     */
    private fun createYardTexturePaint(): Paint {
        val tileSize = dp(64f).toInt().coerceAtLeast(32)
        val textureBitmap = Bitmap.createBitmap(
            tileSize,
            tileSize,
            Bitmap.Config.ARGB_8888,
        )
        val textureCanvas = Canvas(textureBitmap)
        val random = Random(0x4C55444FL)
        val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        repeat(950) {
            val x = random.nextFloat() * tileSize
            val y = random.nextFloat() * tileSize
            val fineGrain = random.nextFloat() < 0.65f
            val alpha = if (fineGrain) {
                10 + random.nextInt(23)
            } else {
                22 + random.nextInt(51)
            }
            val highlight = random.nextFloat() < 0.28f
            grainPaint.color = if (highlight) {
                Color.argb((alpha * 0.62f).toInt(), 255, 255, 255)
            } else {
                Color.argb(alpha, 12, 16, 22)
            }
            val radius = dp(
                if (fineGrain) {
                    0.12f + random.nextFloat() * 0.23f
                } else {
                    0.22f + random.nextFloat() * 0.48f
                },
            )
            textureCanvas.drawCircle(x, y, radius, grainPaint)
        }

        grainPaint.style = Paint.Style.STROKE
        repeat(90) {
            val x = random.nextFloat() * tileSize
            val y = random.nextFloat() * tileSize
            val length = dp(0.55f + random.nextFloat() * 1.55f)
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val endX = (x + cos(angle) * length).coerceIn(0f, tileSize - 1f)
            val endY = (y + sin(angle) * length).coerceIn(0f, tileSize - 1f)
            val alpha = 18 + random.nextInt(35)
            grainPaint.color = if (random.nextBoolean()) {
                Color.argb(alpha, 10, 14, 20)
            } else {
                Color.argb((alpha * 0.58f).toInt(), 255, 255, 255)
            }
            grainPaint.strokeWidth = dp(0.16f + random.nextFloat() * 0.28f)
            textureCanvas.drawLine(x, y, endX, endY, grainPaint)
        }

        return Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            style = Paint.Style.FILL
            shader = BitmapShader(
                textureBitmap,
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            )
        }
    }

    private fun drawTrack(canvas: Canvas, cell: Float) {
        TRACK.forEachIndexed { index, coord ->
            val color = when (index) {
                0 -> themeColor(RED)
                13 -> themeColor(GREEN)
                26 -> themeColor(YELLOW)
                39 -> themeColor(BLUE)
                else -> themeNeutralCellColor()
            }
            drawCell(canvas, cell, coord.first, coord.second, color)
            if (index in SAFE_GLOBAL_CELLS) {
                val starColor = when (index) {
                    0 -> themeColor(RED)
                    13 -> themeColor(GREEN)
                    26 -> themeColor(YELLOW)
                    39 -> themeColor(BLUE)
                    else -> themeColor(Color.rgb(45, 138, 204))
                }
                drawStar(
                    canvas,
                    (boundary(coord.second, cell) + boundary(coord.second + 1, cell)) / 2f,
                    (boundary(coord.first, cell) + boundary(coord.first + 1, cell)) / 2f,
                    cell * 0.27f,
                    starColor,
                )
            }
        }
    }

    private fun drawHomeLanes(canvas: Canvas, cell: Float) {
        HOME_LANES.forEach { (color, lane) ->
            lane.forEach { (row, col) ->
                drawCell(canvas, cell, row, col, themeColor(color))
            }
        }
    }

    private fun drawCenter(canvas: Canvas, cell: Float) {
        val left = boundary(6, cell)
        val top = boundary(6, cell)
        val right = boundary(9, cell)
        val bottom = boundary(9, cell)
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        drawTriangle(canvas, themeColor(RED), left, top, left, bottom, cx, cy)
        drawTriangle(canvas, themeColor(GREEN), left, top, right, top, cx, cy)
        drawTriangle(canvas, themeColor(YELLOW), right, top, right, bottom, cx, cy)
        drawTriangle(canvas, themeColor(BLUE), left, bottom, right, bottom, cx, cy)
    }

    private fun drawCell(canvas: Canvas, cell: Float, row: Int, col: Int, color: Int) {
        fillPaint.color = color
        val rect = RectF(
            boundary(col, cell),
            boundary(row, cell),
            boundary(col + 1, cell),
            boundary(row + 1, cell),
        )
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, cellStrokePaint)
    }

    private fun drawTriangle(
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
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
            lineTo(x3, y3)
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, cellStrokePaint)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val path = Path()
        for (i in 0 until 10) {
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val r = if (i % 2 == 0) radius else radius * 0.45f
            val x = cx + (kotlin.math.cos(angle) * r).toFloat()
            val y = cy + (kotlin.math.sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        fillPaint.color = color
        canvas.drawPath(path, fillPaint)
    }

    private fun themeSurfaceColor(): Int = BoardRegistry.resolve(previewThemeId).surfaceColor
    private fun themeNeutralCellColor(): Int = BoardRegistry.resolve(previewThemeId).neutralCellColor
    private fun themeColor(color: Int): Int = BoardRegistry.resolve(previewThemeId).teamColor(color)

    private fun boundary(index: Int, cell: Float): Float {
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

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        val RED = Color.rgb(241, 37, 47)
        val GREEN = Color.rgb(0, 169, 80)
        val YELLOW = Color.rgb(255, 216, 27)
        val BLUE = Color.rgb(48, 151, 215)
        val SAFE_GLOBAL_CELLS = setOf(0, 8, 13, 21, 26, 34, 39, 47)
        val TRACK = listOf(
            6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5,
            5 to 6, 4 to 6, 3 to 6, 2 to 6, 1 to 6, 0 to 6,
            0 to 7, 0 to 8, 1 to 8, 2 to 8, 3 to 8, 4 to 8, 5 to 8,
            6 to 9, 6 to 10, 6 to 11, 6 to 12, 6 to 13, 6 to 14,
            7 to 14, 8 to 14, 8 to 13, 8 to 12, 8 to 11, 8 to 10,
            8 to 9, 9 to 8, 10 to 8, 11 to 8, 12 to 8, 13 to 8,
            14 to 8, 14 to 7, 14 to 6, 13 to 6, 12 to 6, 11 to 6,
            10 to 6, 9 to 6, 8 to 5, 8 to 4, 8 to 3, 8 to 2,
            8 to 1, 8 to 0, 7 to 0, 6 to 0,
        )
        val HOME_LANES = mapOf(
            RED to listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),
            GREEN to listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),
            YELLOW to listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9),
            BLUE to listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7),
        )
    }
}

