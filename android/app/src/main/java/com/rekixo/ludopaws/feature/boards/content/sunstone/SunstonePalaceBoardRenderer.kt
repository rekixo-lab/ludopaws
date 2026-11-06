package com.rekixo.ludopaws.feature.boards.content.sunstone

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.rekixo.ludopaws.LudoPawsBoardPalette
import com.rekixo.ludopaws.feature.boards.content.classic.ClassicBoardRenderer
import com.rekixo.ludopaws.feature.boards.content.classic.ClassicRoadProfile
import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.boards.rendering.BoardRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Sunlit brass, enamel and sandstone on the shared Ludo coordinates. */
internal class SunstonePalaceBoardRenderer : BoardRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shape = Path()
    private val teams = intArrayOf(
        LudoPawsBoardPalette.coral, LudoPawsBoardPalette.jade,
        LudoPawsBoardPalette.honey, LudoPawsBoardPalette.sky,
    )
    private val gold = 0xFFF5C163.toInt()
    private val sunlit = 0xFFFFE5A0.toInt()
    private val bronze = 0xFF895020.toInt()
    private val deepTeal = 0xFF103D50.toInt()

    override fun draw(canvas: Canvas, size: Float, density: Float, definition: BoardDefinition) {
        if (!size.isFinite() || size <= 0f) return
        val saved = canvas.save()
        try {
            canvas.scale(size / 150f, size / 150f)
            paint.shader = LinearGradient(0f, 0f, 150f, 150f, sunlit, definition.surfaceColor, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 150f, 150f, paint)
            paint.shader = null
            for (index in teams.indices) {
                val yard = canvas.save()
                canvas.translate(if (index in 1..2) 100f else 0f, if (index >= 2) 100f else 0f)
                courtyard(canvas, definition.teamColor(teams[index]))
                canvas.restoreToCount(yard)
            }
            for (row in 0..14) for (column in 0..14) {
                if (!ClassicRoadProfile.isRoad(row, column)) continue
                val cell = row to column
                val team = ClassicRoadProfile.team(row, column)?.let(definition::teamColor)
                val safe = cell in ClassicBoardRenderer.NEUTRAL_SAFE_CELLS || cell in ClassicBoardRenderer.COLORED_ENTRIES
                roadTile(canvas, ClassicRoadProfile.raw(row, column), team, safe, definition.neutralCellColor)
            }
            finish(canvas, definition)
            edge.color = bronze
            edge.strokeWidth = .65f
            canvas.drawRect(.5f, .5f, 149.5f, 149.5f, edge)
        } finally {
            paint.shader = null
            canvas.restoreToCount(saved)
        }
    }

    private fun courtyard(canvas: Canvas, team: Int) {
        octagon(1.3f, 2.7f, 48.7f, 49.2f, 6f)
        paint.color = bronze
        canvas.drawPath(shape, paint)
        octagon(1.3f, 1.1f, 48.7f, 47.6f, 6f)
        paint.shader = LinearGradient(5f, 1f, 36f, 48f,
            intArrayOf(sunlit, gold, 0xFFB4732C.toInt(), sunlit),
            floatArrayOf(0f, .36f, .77f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(shape, paint)
        paint.shader = null

        octagon(3.2f, 2.9f, 46.8f, 45.9f, 5.5f)
        paint.shader = RadialGradient(20f, 18f, 32f, 0xFF237D84.toInt(), deepTeal, Shader.TileMode.CLAMP)
        canvas.drawPath(shape, paint)
        paint.shader = null
        edge.color = 0xFFE8B763.toInt()
        edge.strokeWidth = .34f
        canvas.drawPath(shape, edge)

        // A tiled sunburst and small brass inlays stay outside the pawn footprints.
        star(25f, 25f, 18f, 13f, 8)
        edge.color = 0x5595E4D9
        edge.strokeWidth = .35f
        canvas.drawPath(shape, edge)
        for (coordinate in 9..41 step 4) {
            inlay(canvas, coordinate.toFloat(), 5.2f, gold)
            inlay(canvas, coordinate.toFloat(), 43.5f, gold)
            inlay(canvas, 5.2f, coordinate.toFloat(), gold)
            inlay(canvas, 44.8f, coordinate.toFloat(), gold)
        }
        for (x in floatArrayOf(15f, 35f)) for (y in floatArrayOf(15f, 35f)) {
            octagon(x - 5.4f, y - 5.2f, x + 5.4f, y + 5.6f, 2f)
            paint.color = deepTeal
            canvas.drawPath(shape, paint)
            octagon(x - 5.2f, y - 5.7f, x + 5.2f, y + 4.7f, 2f)
            paint.shader = LinearGradient(x - 3f, y - 5f, x + 3f, y + 5f,
                blend(team, sunlit, .58f), team, Shader.TileMode.CLAMP)
            canvas.drawPath(shape, paint)
            paint.shader = null
            edge.color = sunlit
            edge.strokeWidth = .42f
            canvas.drawPath(shape, edge)
        }
        star(25f, 24.5f, 4.5f, 2.2f, 8)
        paint.color = sunlit
        canvas.drawPath(shape, paint)
    }

    private fun roadTile(canvas: Canvas, raw: RectF, team: Int?, safe: Boolean, stone: Int) {
        val unit = min(raw.width(), raw.height())
        raw.inset(unit * .045f, unit * .045f)
        val radius = unit * .12f
        paint.color = bronze
        canvas.drawRoundRect(raw, radius, radius, paint)
        val face = RectF(raw.left, raw.top, raw.right, raw.bottom - .75f)
        val color = team ?: stone
        paint.shader = LinearGradient(face.left, face.top, face.right, face.bottom,
            blend(color, Color.WHITE, .39f), color, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(face, radius, radius, paint)
        paint.shader = null
        if (safe) {
            star(face.centerX(), face.centerY(), 2.75f, 1.25f, 5)
            paint.color = deepTeal
            canvas.drawPath(shape, paint)
        }
    }

    private fun finish(canvas: Canvas, definition: BoardDefinition) {
        for (index in teams.indices) {
            val saved = canvas.save()
            canvas.rotate(index * 90f, 75f, 75f)
            shape.rewind()
            shape.moveTo(50f, 50f)
            shape.lineTo(75f, 75f)
            shape.lineTo(50f, 100f)
            shape.close()
            paint.shader = LinearGradient(50f, 75f, 75f, 75f,
                definition.teamColor(teams[index]), sunlit, Shader.TileMode.CLAMP)
            canvas.drawPath(shape, paint)
            paint.shader = null
            edge.color = sunlit
            edge.strokeWidth = .55f
            canvas.drawPath(shape, edge)
            canvas.restoreToCount(saved)
        }
    }

    private fun inlay(canvas: Canvas, x: Float, y: Float, color: Int) {
        star(x, y, 1.1f, .56f, 4)
        paint.color = color
        canvas.drawPath(shape, paint)
    }

    private fun octagon(left: Float, top: Float, right: Float, bottom: Float, cut: Float) {
        shape.rewind()
        shape.moveTo(left + cut, top)
        shape.lineTo(right - cut, top)
        shape.lineTo(right, top + cut)
        shape.lineTo(right, bottom - cut)
        shape.lineTo(right - cut, bottom)
        shape.lineTo(left + cut, bottom)
        shape.lineTo(left, bottom - cut)
        shape.lineTo(left, top + cut)
        shape.close()
    }

    private fun star(x: Float, y: Float, outer: Float, inner: Float, points: Int) {
        shape.rewind()
        repeat(points * 2) { index ->
            val angle = index * PI / points - PI / 2
            val radius = if (index % 2 == 0) outer else inner
            val px = x + cos(angle).toFloat() * radius
            val py = y + sin(angle).toFloat() * radius
            if (index == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
        }
        shape.close()
    }

    private fun blend(from: Int, to: Int, amount: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt(),
    )
}
