package com.rekixo.ludopaws.feature.boards.content.winter

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
import kotlin.math.min
import kotlin.math.sin

internal class WinterBoardRenderer : BoardRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val snow = 0xFFF0F4EF.toInt()
    private val evergreen = 0xFF436D68.toInt()
    private val slate = 0xFF547780.toInt()
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
                intArrayOf(0xFFDEE9E5.toInt(), definition.surfaceColor, 0xFFACC7CF.toInt()),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 150f, 150f, fill)
            fill.shader = null
            courtyard(canvas, 0f, 0f, definition.teamColor(teams[0]))
            courtyard(canvas, 100f, 0f, definition.teamColor(teams[1]))
            courtyard(canvas, 100f, 100f, definition.teamColor(teams[2]))
            courtyard(canvas, 0f, 100f, definition.teamColor(teams[3]))
            roads(canvas, definition)
            finish(canvas, definition)
            stroke.color = 0xFF8CAEB7.toInt()
            stroke.strokeWidth = .45f
            canvas.drawRect(.3f, .3f, 149.7f, 149.7f, stroke)
        } finally {
            fill.shader = null
            canvas.restoreToCount(saved)
        }
    }

    private fun courtyard(canvas: Canvas, left: Float, top: Float, team: Int) {
        val saved = canvas.save()
        canvas.translate(left, top)
        canvas.clipRect(0f, 0f, 50f, 50f)
        fill.color = 0xFF8AAEB1.toInt()
        canvas.drawRoundRect(3.5f, 5f, 46.5f, 47.8f, 10f, 10f, fill)
        fill.shader = LinearGradient(4f, 4f, 46f, 46f,
            intArrayOf(mix(team, snow, .62f), mix(team, slate, .20f)), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(3.5f, 3f, 46.5f, 45.5f, 10f, 10f, fill)
        fill.shader = null
        path.reset()
        path.moveTo(6f, 15f)
        path.cubicTo(5f, 4f, 16f, 8f, 24f, 6f)
        path.cubicTo(34f, 4f, 44f, 5f, 44f, 15f)
        path.lineTo(44f, 34f)
        path.cubicTo(46f, 45f, 33f, 42f, 25f, 43f)
        path.cubicTo(13f, 45f, 5f, 42f, 6f, 34f)
        path.close()
        fill.shader = LinearGradient(8f, 8f, 43f, 44f,
            intArrayOf(snow, 0xFFDCE7E7.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        fill.shader = null
        stroke.color = 0xFFF8F7EC.toInt()
        stroke.strokeWidth = .65f
        canvas.drawPath(path, stroke)
        pine(canvas, 5.5f, 8.2f, 1f)
        pine(canvas, 44.5f, 43f, .90f)
        pine(canvas, 9.8f, 4.5f, .64f)
        pine(canvas, 40.2f, 46f, .60f)
        snowflake(canvas, 43f, 7f, 2.3f)
        snowflake(canvas, 7f, 43f, 2.3f)
        stroke.color = 0xFFBED3D5.toInt()
        stroke.strokeWidth = .5f
        path.reset()
        path.moveTo(15f, 10f)
        path.quadTo(23f, 7.5f, 33f, 10f)
        canvas.drawPath(path, stroke)
        path.reset()
        path.moveTo(17f, 39f)
        path.quadTo(27f, 42f, 35f, 39f)
        canvas.drawPath(path, stroke)
        canvas.restoreToCount(saved)
    }

    private fun pine(canvas: Canvas, centerX: Float, centerY: Float, scale: Float) {
        val saved = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(scale, scale)
        fill.color = 0x28547780
        canvas.drawOval(-3.7f, 3f, 4.1f, 5.4f, fill)
        fill.color = 0xFF7B7968.toInt()
        canvas.drawRoundRect(-.45f, 1f, .45f, 4.2f, .2f, .2f, fill)
        for (tier in 0..2) {
            val top = -5.4f + tier * 2.3f
            val width = 1.8f + tier * .8f
            path.reset()
            path.moveTo(0f, top)
            path.lineTo(width, top + 3.5f)
            path.quadTo(0f, top + 4.4f, -width, top + 3.5f)
            path.close()
            fill.color = evergreen
            canvas.drawPath(path, fill)
            path.reset()
            path.moveTo(0f, top)
            path.lineTo(width * .72f, top + 2.5f)
            path.quadTo(0f, top + 2f, -width * .72f, top + 2.5f)
            path.close()
            fill.color = snow
            canvas.drawPath(path, fill)
        }
        canvas.restoreToCount(saved)
    }

    private fun snowflake(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val saved = canvas.save()
        canvas.translate(centerX, centerY)
        stroke.color = slate
        stroke.strokeWidth = .32f
        repeat(6) {
            canvas.drawLine(0f, .3f, 0f, -radius, stroke)
            canvas.drawLine(0f, -radius * .63f, -.55f, -radius * .85f, stroke)
            canvas.drawLine(0f, -radius * .63f, .55f, -radius * .85f, stroke)
            canvas.rotate(60f)
        }
        canvas.restoreToCount(saved)
    }

    private fun roads(canvas: Canvas, definition: BoardDefinition) {
        for (row in 0..14) for (column in 0..14) {
            val vertical = column in 6..8 && (row < 6 || row > 8)
            val horizontal = row in 6..8 && (column < 6 || column > 8)
            if (!vertical && !horizontal) continue
            val teamIndex = when {
                row == 7 && column in 1..5 || row == 6 && column == 1 -> 0
                column == 7 && row in 1..5 || row == 1 && column == 8 -> 1
                row == 7 && column in 9..13 || row == 8 && column == 13 -> 2
                column == 7 && row in 9..13 || row == 13 && column == 6 -> 3
                else -> -1
            }
            val raw = RectF(axis(column), axis(row), axis(column + 1), axis(row + 1))
            val team = if (teamIndex < 0) null else definition.teamColor(teams[teamIndex])
            tile(canvas, raw, team)
            val safe = row == 2 && column == 6 || row == 6 && column == 12 ||
                row == 12 && column == 8 || row == 8 && column == 2
            val entry = row == 6 && column == 1 || row == 1 && column == 8 ||
                row == 8 && column == 13 || row == 13 && column == 6
            if (safe || entry) star(canvas, raw.centerX(), raw.centerY())
        }
    }

    private fun tile(canvas: Canvas, raw: RectF, team: Int?) {
        val unit = min(raw.width(), raw.height())
        val face = RectF(raw).apply { inset(unit * .065f, unit * .065f) }
        val corner = unit * .12f
        fill.color = 0xFF91B1BB.toInt()
        canvas.drawRoundRect(face.left, face.top + .50f, face.right, face.bottom + .50f, corner, corner, fill)
        val color = if (team == null) 0xFFE4EEED.toInt() else mix(team, snow, .32f)
        fill.shader = LinearGradient(face.left, face.top, face.right, face.bottom,
            intArrayOf(mix(color, snow, .42f), color, mix(color, slate, .12f)),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(face, corner, corner, fill)
        fill.shader = null
        stroke.color = if (team == null) 0xFF9DBBC3.toInt() else mix(team, slate, .28f)
        stroke.strokeWidth = .24f
        canvas.drawRoundRect(face, corner, corner, stroke)
        stroke.color = mix(color, snow, .75f)
        stroke.strokeWidth = .45f
        canvas.drawLine(face.left + .9f, face.top + .65f, face.right - .9f, face.top + .65f, stroke)
        stroke.strokeWidth = .25f
        canvas.drawLine(face.right - unit * .28f, face.bottom - .7f, face.right - .7f, face.bottom - unit * .28f, stroke)
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
        fill.color = slate
        canvas.drawPath(path, fill)
    }

    private fun finish(canvas: Canvas, definition: BoardDefinition) {
        for (teamIndex in teams.indices) {
            val saved = canvas.save()
            canvas.rotate(teamIndex * 90f, 75f, 75f)
            val team = definition.teamColor(teams[teamIndex])
            path.reset()
            path.moveTo(50f, 50f)
            path.lineTo(50f, 100f)
            path.lineTo(75f, 75f)
            path.close()
            fill.shader = LinearGradient(50f, 75f, 75f, 75f,
                intArrayOf(mix(team, snow, .25f), mix(team, snow, .70f)), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, fill)
            fill.shader = null
            stroke.color = snow
            stroke.strokeWidth = .5f
            canvas.drawPath(path, stroke)
            canvas.restoreToCount(saved)
        }
        fill.color = snow
        canvas.drawCircle(75f, 75f, 3.1f, fill)
        snowflake(canvas, 75f, 75f, 2.25f)
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
