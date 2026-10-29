package com.rekixo.ludopaws.feature.boards.content.astral

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.rekixo.ludopaws.LudoPawsBoardPalette
import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.boards.rendering.BoardRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal class AstralBoardRenderer : BoardRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val gold = 0xFFE3C787.toInt()
    private val ink = 0xFF12263B.toInt()
    private val teams = intArrayOf(
        LudoPawsBoardPalette.coral, LudoPawsBoardPalette.jade,
        LudoPawsBoardPalette.honey, LudoPawsBoardPalette.sky,
    )

    override fun draw(canvas: Canvas, size: Float, density: Float, definition: BoardDefinition) {
        if (!size.isFinite() || size <= 0f) return
        val saved = canvas.save()
        try {
            canvas.scale(size / 150f, size / 150f)
            canvas.clipRect(0f, 0f, 150f, 150f)
            fill.color = definition.surfaceColor
            canvas.drawRect(0f, 0f, 150f, 150f, fill)
            yard(canvas, 0f, 0f, definition.teamColor(teams[0]))
            yard(canvas, 100f, 0f, definition.teamColor(teams[1]))
            yard(canvas, 100f, 100f, definition.teamColor(teams[2]))
            yard(canvas, 0f, 100f, definition.teamColor(teams[3]))
            for (row in 0..14) for (column in 0..14) {
                val verticalRoad = column in 6..8 && (row < 6 || row > 8)
                val horizontalRoad = row in 6..8 && (column < 6 || column > 8)
                if (!verticalRoad && !horizontalRoad) continue
                val teamIndex = when {
                    row == 7 && column in 1..5 || row == 6 && column == 1 -> 0
                    column == 7 && row in 1..5 || row == 1 && column == 8 -> 1
                    row == 7 && column in 9..13 || row == 8 && column == 13 -> 2
                    column == 7 && row in 9..13 || row == 13 && column == 6 -> 3
                    else -> -1
                }
                val color = if (teamIndex < 0) definition.neutralCellColor else definition.teamColor(teams[teamIndex])
                tile(canvas, row, column, color)
                val safe = row == 2 && column == 6 || row == 6 && column == 12 ||
                    row == 12 && column == 8 || row == 8 && column == 2
                val entry = row == 6 && column == 1 || row == 1 && column == 8 ||
                    row == 8 && column == 13 || row == 13 && column == 6
                if (safe || entry) {
                    val centerX = (axis(column) + axis(column + 1)) * .5f
                    val centerY = (axis(row) + axis(row + 1)) * .5f
                    star(canvas, centerX, centerY, 2.65f, if (safe) ink else Color.WHITE)
                }
            }
            center(canvas, definition)
            outline(canvas, RectF(.3f, .3f, 149.7f, 149.7f), .8f, gold, .35f)
        } finally {
            fill.shader = null
            canvas.restoreToCount(saved)
        }
    }

    private fun yard(canvas: Canvas, left: Float, top: Float, team: Int) {
        val saved = canvas.save()
        canvas.translate(left, top)
        fill.shader = LinearGradient(0f, 0f, 50f, 50f,
            intArrayOf(mix(ink, team, .27f), ink, 0xFF091727.toInt()),
            floatArrayOf(0f, .56f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 50f, 50f, fill)
        fill.shader = null
        outline(canvas, RectF(1.4f, 1.4f, 48.6f, 48.6f), 4f, gold, .38f)
        outline(canvas, RectF(2.4f, 2.4f, 47.6f, 47.6f), 3.2f, mix(ink, team, .70f), .28f)
        fill.shader = LinearGradient(6f, 6f, 44f, 44f,
            intArrayOf(mix(team, Color.WHITE, .48f), team, mix(team, ink, .35f)),
            floatArrayOf(0f, .40f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(5f, 5f, 45f, 45f, 6f, 6f, fill)
        fill.shader = null
        fill.color = 0xFF0B1B2A.toInt()
        canvas.drawRoundRect(7f, 7f, 43f, 43f, 4.6f, 4.6f, fill)
        fill.shader = RadialGradient(19f, 16f, 34f,
            intArrayOf(mix(team, ink, .80f), ink), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(7.7f, 7.7f, 42.3f, 42.3f, 4f, 4f, fill)
        fill.shader = null
        outline(canvas, RectF(7.6f, 7.6f, 42.4f, 42.4f), 4.1f, gold, .30f)
        for (corner in 0..3) {
            val cornerSave = canvas.save()
            canvas.rotate(corner * 90f, 25f, 25f)
            branch(canvas, team)
            canvas.restoreToCount(cornerSave)
        }
        stroke.color = 0x30E3C787
        stroke.strokeWidth = .25f
        path.reset()
        path.moveTo(12f, 5.8f)
        path.lineTo(20f, 3.9f)
        path.lineTo(28f, 5.7f)
        path.lineTo(37f, 3.8f)
        canvas.drawPath(path, stroke)
        for (starIndex in 0..3) {
            val starX = 12f + starIndex * 8.3f
            val starY = if (starIndex % 2 == 0) 5.7f else 3.9f
            sparkle(canvas, starX, starY, .7f)
        }
        canvas.restoreToCount(saved)
    }

    private fun branch(canvas: Canvas, team: Int) {
        stroke.color = gold
        stroke.strokeWidth = .35f
        path.reset()
        path.moveTo(4f, 14f)
        path.quadTo(4f, 4f, 14f, 4f)
        canvas.drawPath(path, stroke)
        fill.color = mix(team, Color.WHITE, .40f)
        for (leafIndex in 0..2) {
            val offset = leafIndex * 2.1f
            canvas.drawOval(3.2f + offset, 7.3f - offset, 5.3f + offset, 10.5f - offset, fill)
        }
        sparkle(canvas, 4.5f, 4.5f, 1f)
    }

    private fun tile(canvas: Canvas, row: Int, column: Int, color: Int) {
        val bounds = RectF(axis(column), axis(row), axis(column + 1), axis(row + 1))
        val unit = min(bounds.width(), bounds.height())
        bounds.inset(unit * .055f, unit * .055f)
        fill.color = 0xFF071321.toInt()
        canvas.drawRoundRect(bounds.left, bounds.top + .55f, bounds.right, bounds.bottom, .8f, .8f, fill)
        bounds.bottom -= .65f
        fill.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
            intArrayOf(mix(color, Color.WHITE, .28f), color, mix(color, ink, .16f)),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, .8f, .8f, fill)
        fill.shader = null
        outline(canvas, bounds, .8f, mix(color, gold, .45f), .18f)
        stroke.color = 0x70FFFFFF
        stroke.strokeWidth = .22f
        canvas.drawLine(bounds.left + .9f, bounds.top + .6f, bounds.right - .9f, bounds.top + .6f, stroke)
    }

    private fun center(canvas: Canvas, definition: BoardDefinition) {
        for (teamIndex in teams.indices) {
            val saved = canvas.save()
            canvas.rotate(teamIndex * 90f, 75f, 75f)
            path.reset()
            path.moveTo(50f, 50f)
            path.lineTo(50f, 100f)
            path.lineTo(75f, 75f)
            path.close()
            val team = definition.teamColor(teams[teamIndex])
            fill.shader = LinearGradient(50f, 75f, 75f, 75f,
                intArrayOf(mix(team, ink, .45f), team), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, fill)
            fill.shader = null
            stroke.color = gold
            stroke.strokeWidth = .38f
            canvas.drawPath(path, stroke)
            canvas.restoreToCount(saved)
        }
        stroke.color = 0x90E3C787.toInt()
        stroke.strokeWidth = .35f
        canvas.drawCircle(75f, 75f, 6f, stroke)
        sparkle(canvas, 75f, 75f, 3f)
    }

    private fun sparkle(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        path.reset()
        path.moveTo(centerX, centerY - radius)
        path.lineTo(centerX + radius * .25f, centerY - radius * .25f)
        path.lineTo(centerX + radius, centerY)
        path.lineTo(centerX + radius * .25f, centerY + radius * .25f)
        path.lineTo(centerX, centerY + radius)
        path.lineTo(centerX - radius * .25f, centerY + radius * .25f)
        path.lineTo(centerX - radius, centerY)
        path.lineTo(centerX - radius * .25f, centerY - radius * .25f)
        path.close()
        fill.color = gold
        canvas.drawPath(path, fill)
    }

    private fun star(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, color: Int) {
        path.reset()
        repeat(10) { pointIndex ->
            val angle = pointIndex * PI / 5.0 - PI / 2.0
            val length = radius * if (pointIndex % 2 == 0) 1f else .43f
            val pointX = centerX + cos(angle).toFloat() * length
            val pointY = centerY + sin(angle).toFloat() * length
            if (pointIndex == 0) path.moveTo(pointX, pointY) else path.lineTo(pointX, pointY)
        }
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
    }

    private fun outline(canvas: Canvas, bounds: RectF, radius: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        canvas.drawRoundRect(bounds, radius, radius, stroke)
    }

    private fun axis(index: Int): Float = when {
        index <= 6 -> index * 50f / 6f
        index <= 9 -> 50f + (index - 6) * 50f / 3f
        else -> 100f + (index - 9) * 50f / 6f
    }

    private fun mix(first: Int, second: Int, amount: Float): Int = Color.rgb(
        (Color.red(first) * (1f - amount) + Color.red(second) * amount).toInt(),
        (Color.green(first) * (1f - amount) + Color.green(second) * amount).toInt(),
        (Color.blue(first) * (1f - amount) + Color.blue(second) * amount).toInt(),
    )
}
