package com.rekixo.ludopaws.feature.boards.content.monsoon

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.rekixo.ludopaws.LudoPawsBoardPalette
import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.boards.rendering.BoardRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class MonsoonBoardRenderer : BoardRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val mud = 0xFF927452.toInt()
    private val water = 0xFF668D91.toInt()
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
            fill.shader = LinearGradient(0f, 0f, 150f, 150f,
                intArrayOf(0xFFA3B09B.toInt(), definition.surfaceColor, 0xFF738D83.toInt()),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 150f, 150f, fill)
            fill.shader = null
            yard(canvas, 0f, 0f, definition.teamColor(teams[0]))
            yard(canvas, 100f, 0f, definition.teamColor(teams[1]))
            yard(canvas, 100f, 100f, definition.teamColor(teams[2]))
            yard(canvas, 0f, 100f, definition.teamColor(teams[3]))
            roads(canvas, definition)
            repeat(4) { teamIndex ->
                val turn = canvas.save()
                canvas.rotate(teamIndex * 90f, 75f, 75f)
                path.reset()
                path.moveTo(50f, 50f)
                path.lineTo(50f, 100f)
                path.lineTo(75f, 75f)
                path.close()
                fill.color = mix(definition.teamColor(teams[teamIndex]), 0xFFD5D1B4.toInt(), .30f)
                canvas.drawPath(path, fill)
                stroke.color = 0xFFD6CEB4.toInt()
                stroke.strokeWidth = .55f
                canvas.drawPath(path, stroke)
                canvas.restoreToCount(turn)
            }
        } finally {
            fill.shader = null
            canvas.restoreToCount(saved)
        }
    }

    private fun yard(canvas: Canvas, left: Float, top: Float, team: Int) {
        val saved = canvas.save()
        canvas.translate(left, top)
        fill.color = 0xFF566E58.toInt()
        canvas.drawRoundRect(2f, 4f, 48f, 48f, 11f, 11f, fill)
        fill.color = mix(team, mud, .25f)
        canvas.drawRoundRect(3f, 2.5f, 47f, 46f, 10f, 10f, fill)
        fill.shader = LinearGradient(5f, 5f, 44f, 44f,
            intArrayOf(0xFFB69A70.toInt(), mud, 0xFF795F48.toInt()),
            floatArrayOf(0f, .60f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(5.5f, 5f, 44.5f, 43.5f, 8f, 8f, fill)
        fill.shader = null
        puddle(canvas, 10f, 37f, 6f, 3.7f)
        puddle(canvas, 39f, 11f, 5f, 3.1f)
        stroke.color = 0xFF5F754F.toInt()
        stroke.strokeWidth = .65f
        for (tuft in 0..3) {
            val position = 8f + tuft * 10f
            canvas.drawLine(position, 4.5f, position - 1f, 1.5f, stroke)
            canvas.drawLine(position, 4.5f, position + .7f, 1f, stroke)
            canvas.drawLine(50f - position, 46f, 49f - position, 48f, stroke)
        }
        stroke.color = 0xFF715B46.toInt()
        stroke.strokeWidth = .50f
        canvas.drawArc(RectF(14f, 15f, 39f, 40f), 45f, 65f, false, stroke)
        canvas.drawArc(RectF(12f, 13f, 37f, 38f), 45f, 65f, false, stroke)
        canvas.restoreToCount(saved)
    }

    private fun puddle(canvas: Canvas, centerX: Float, centerY: Float, radiusX: Float, radiusY: Float) {
        fill.color = 0xFF654E3E.toInt()
        canvas.drawOval(centerX - radiusX - .8f, centerY - radiusY - .6f,
            centerX + radiusX + .8f, centerY + radiusY + .9f, fill)
        fill.color = water
        canvas.drawOval(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY, fill)
        stroke.color = 0xFFAAC1B8.toInt()
        stroke.strokeWidth = .35f
        canvas.drawArc(RectF(centerX - radiusX * .65f, centerY - radiusY * .6f,
            centerX + radiusX * .65f, centerY + radiusY * .6f), 195f, 100f, false, stroke)
    }

    private fun roads(canvas: Canvas, definition: BoardDefinition) {
        for (row in 0..14) for (column in 0..14) {
            if (!(column in 6..8 && (row < 6 || row > 8)) &&
                !(row in 6..8 && (column < 6 || column > 8))) continue
            val teamIndex = when {
                row == 7 && column in 1..5 || row == 6 && column == 1 -> 0
                column == 7 && row in 1..5 || row == 1 && column == 8 -> 1
                row == 7 && column in 9..13 || row == 8 && column == 13 -> 2
                column == 7 && row in 9..13 || row == 13 && column == 6 -> 3
                else -> -1
            }
            val face = RectF(axis(column), axis(row), axis(column + 1), axis(row + 1))
            face.inset(.55f, .55f)
            fill.color = 0xFF5E6052.toInt()
            canvas.drawRoundRect(face.left, face.top + .6f, face.right, face.bottom + .6f, 1.3f, 1.3f, fill)
            val color = if (teamIndex < 0) definition.neutralCellColor
                else mix(definition.teamColor(teams[teamIndex]), 0xFFD8CDB1.toInt(), .26f)
            fill.shader = LinearGradient(face.left, face.top, face.right, face.bottom,
                intArrayOf(mix(color, 0xFFE4E6CF.toInt(), .25f), color), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(face, 1.3f, 1.3f, fill)
            fill.shader = null
            stroke.color = 0xFF687B71.toInt()
            stroke.strokeWidth = .25f
            canvas.drawRoundRect(face, 1.3f, 1.3f, stroke)
            fill.color = 0x99826645.toInt()
            canvas.drawOval(face.left + .6f, face.bottom - 1.6f,
                face.left + 2.8f, face.bottom - .5f, fill)
            fill.color = 0xAA5E8586.toInt()
            canvas.drawOval(face.right - 2.4f, face.top + 1.1f,
                face.right - .7f, face.top + 2f, fill)
            stroke.color = 0xFFDADECC.toInt()
            stroke.strokeWidth = .4f
            canvas.drawLine(face.left + 1.2f, face.top + .65f, face.right - 1.2f, face.top + .65f, stroke)
            val safe = row == 2 && column == 6 || row == 6 && column == 12 ||
                row == 12 && column == 8 || row == 8 && column == 2
            val entry = row == 6 && column == 1 || row == 1 && column == 8 ||
                row == 8 && column == 13 || row == 13 && column == 6
            if (safe || entry) star(canvas, face.centerX(), face.centerY())
        }
    }

    private fun star(canvas: Canvas, centerX: Float, centerY: Float) {
        path.reset()
        repeat(10) { pointIndex ->
            val angle = pointIndex * PI / 5.0 - PI / 2.0
            val radius = if (pointIndex % 2 == 0) 2.4f else 1.05f
            val pointX = centerX + cos(angle).toFloat() * radius
            val pointY = centerY + sin(angle).toFloat() * radius
            if (pointIndex == 0) path.moveTo(pointX, pointY) else path.lineTo(pointX, pointY)
        }
        path.close()
        fill.color = 0xFF415C56.toInt()
        canvas.drawPath(path, fill)
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
