package com.rekixo.ludopaws.feature.boards.content.lagoon

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
import kotlin.math.min

internal class CloudLagoonBoardRenderer : BoardRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val pearl = 0xFFF5F2E8.toInt()
    private val sea = 0xFF447D7D.toInt()
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
            fill.alpha = 255
            fill.shader = LinearGradient(0f, 0f, 150f, 150f,
                intArrayOf(0xFFEAF2E7.toInt(), definition.surfaceColor, 0xFFD6E7EA.toInt()),
                floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 150f, 150f, fill)
            fill.shader = null
            island(canvas, 0f, 0f, definition.teamColor(teams[0]))
            island(canvas, 100f, 0f, definition.teamColor(teams[1]))
            island(canvas, 100f, 100f, definition.teamColor(teams[2]))
            island(canvas, 0f, 100f, definition.teamColor(teams[3]))
            roads(canvas, definition)
            finish(canvas, definition)
            stroke.color = 0xFFB9D1CE.toInt()
            stroke.strokeWidth = .35f
            canvas.drawRoundRect(.5f, .5f, 149.5f, 149.5f, 3f, 3f, stroke)
        } finally {
            fill.shader = null
            canvas.restoreToCount(saved)
        }
    }

    private fun island(canvas: Canvas, left: Float, top: Float, team: Int) {
        val saved = canvas.save()
        canvas.translate(left, top)
        fill.shader = RadialGradient(25f, 25f, 34f,
            intArrayOf(mix(team, pearl, .85f), 0x00E2F0EA), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 50f, 50f, fill)
        fill.shader = null
        fill.color = 0x17447D7D
        canvas.drawOval(3f, 7f, 47f, 48f, fill)
        fill.color = 0x20447D7D
        canvas.drawOval(4.3f, 6.5f, 45.7f, 46.3f, fill)
        fill.alpha = 255
        fill.shader = LinearGradient(8f, 4f, 43f, 45f,
            intArrayOf(mix(team, pearl, .75f), mix(team, pearl, .49f), mix(team, sea, .26f)),
            floatArrayOf(0f, .66f, 1f), Shader.TileMode.CLAMP)
        canvas.drawOval(3.5f, 3.2f, 46.5f, 45.8f, fill)
        fill.shader = null
        fill.color = mix(team, pearl, .86f)
        canvas.drawOval(5f, 4f, 45f, 43.5f, fill)
        fill.shader = RadialGradient(19f, 16f, 32f,
            intArrayOf(pearl, mix(team, pearl, .93f), mix(team, pearl, .78f)),
            floatArrayOf(0f, .64f, 1f), Shader.TileMode.CLAMP)
        canvas.drawOval(6.3f, 5.2f, 43.7f, 42.7f, fill)
        fill.shader = null
        stroke.color = 0x90F5F2E8.toInt()
        stroke.strokeWidth = .55f
        canvas.drawArc(RectF(4.7f, 4.3f, 45.3f, 44.2f), 195f, 135f, false, stroke)
        wave(canvas, 0f, 42f, 50f, 5f, 0x3061ABA7)
        wave(canvas, 0f, 45f, 50f, 3.3f, 0x28569CA8)
        cloud(canvas, 7f, 9f, .60f)
        cloud(canvas, 39f, 6.6f, .43f)
        stroke.color = 0x4073A5A4
        stroke.strokeWidth = .28f
        canvas.drawArc(RectF(6f, 42f, 15f, 46f), 10f, 155f, false, stroke)
        canvas.drawArc(RectF(37f, 44f, 45f, 47f), 195f, 145f, false, stroke)
        leaf(canvas, 8f, 42f, -18f)
        leaf(canvas, 42f, 41f, 24f)
        fill.color = mix(team, pearl, .38f)
        canvas.drawCircle(25f, 45f, 1.15f, fill)
        fill.color = pearl
        canvas.drawCircle(24.7f, 44.7f, .30f, fill)
        canvas.restoreToCount(saved)
    }

    private fun wave(canvas: Canvas, left: Float, top: Float, width: Float, depth: Float, color: Int) {
        path.reset()
        path.moveTo(left, top)
        path.cubicTo(left + width * .25f, top - depth, left + width * .55f, top + depth, left + width, top - depth * .45f)
        path.lineTo(left + width, 50f)
        path.lineTo(left, 50f)
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
    }

    private fun cloud(canvas: Canvas, centerX: Float, centerY: Float, scale: Float) {
        val saved = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(scale, scale)
        fill.color = 0x20447D7D
        canvas.drawOval(-5.5f, -.5f, 6.2f, 4f, fill)
        fill.color = pearl
        canvas.drawOval(-6f, -2f, 1f, 3f, fill)
        canvas.drawOval(-2.5f, -5f, 4f, 3f, fill)
        canvas.drawOval(1f, -2f, 6f, 3f, fill)
        canvas.restoreToCount(saved)
    }

    private fun leaf(canvas: Canvas, centerX: Float, centerY: Float, rotation: Float) {
        val saved = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(rotation)
        fill.color = 0xFF8CBEB0.toInt()
        canvas.drawOval(-2.2f, -.8f, 2.2f, .8f, fill)
        stroke.color = 0xFFD9E8D8.toInt()
        stroke.strokeWidth = .22f
        canvas.drawLine(-1.4f, 0f, 1.4f, 0f, stroke)
        canvas.restoreToCount(saved)
    }

    private fun roads(canvas: Canvas, definition: BoardDefinition) {
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
            val team = if (teamIndex < 0) sea else definition.teamColor(teams[teamIndex])
            val bounds = RectF(axis(column), axis(row), axis(column + 1), axis(row + 1))
            tile(canvas, bounds, team, teamIndex >= 0)
            val safe = row == 2 && column == 6 || row == 6 && column == 12 ||
                row == 12 && column == 8 || row == 8 && column == 2
            val entry = row == 6 && column == 1 || row == 1 && column == 8 ||
                row == 8 && column == 13 || row == 13 && column == 6
            if (safe) safeMark(canvas, bounds)
            if (entry) entryMark(canvas, bounds, teamIndex, team)
        }
    }

    private fun tile(canvas: Canvas, raw: RectF, team: Int, colored: Boolean) {
        val unit = min(raw.width(), raw.height())
        val face = RectF(raw)
        face.inset(unit * .075f, unit * .075f)
        val radius = unit * .22f
        fill.color = 0x2D527E7D
        canvas.drawRoundRect(face.left, face.top + .6f, face.right, face.bottom + .35f, radius, radius, fill)
        val tileColor = if (colored) mix(team, pearl, .48f) else pearl
        fill.color = tileColor
        canvas.drawRoundRect(face, radius, radius, fill)
        stroke.color = if (colored) mix(team, pearl, .22f) else 0xFFB7CECA.toInt()
        stroke.strokeWidth = .28f
        canvas.drawRoundRect(face, radius, radius, stroke)
        stroke.color = mix(tileColor, pearl, .66f)
        stroke.strokeWidth = .42f
        canvas.drawLine(face.left + radius, face.top + .65f, face.right - radius, face.top + .65f, stroke)
        if (colored) {
            stroke.color = mix(team, pearl, .12f)
            stroke.strokeWidth = .42f
            canvas.drawLine(face.left + radius, face.bottom - .70f, face.right - radius, face.bottom - .70f, stroke)
        }
    }

    private fun safeMark(canvas: Canvas, bounds: RectF) {
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val radius = min(bounds.width(), bounds.height()) * .25f
        fill.color = 0xFFD8E9E0.toInt()
        canvas.drawCircle(centerX, centerY, radius, fill)
        stroke.color = sea
        stroke.strokeWidth = .38f
        canvas.drawCircle(centerX, centerY, radius, stroke)
        path.reset()
        path.moveTo(centerX - radius * .45f, centerY)
        path.lineTo(centerX - radius * .08f, centerY + radius * .36f)
        path.lineTo(centerX + radius * .46f, centerY - radius * .38f)
        canvas.drawPath(path, stroke)
    }

    private fun entryMark(canvas: Canvas, bounds: RectF, teamIndex: Int, team: Int) {
        val saved = canvas.save()
        canvas.translate(bounds.centerX(), bounds.centerY())
        canvas.rotate(teamIndex * 90f)
        stroke.color = mix(team, 0xFF284A50.toInt(), .60f)
        stroke.strokeWidth = .65f
        path.reset()
        path.moveTo(-1.9f, 0f)
        path.lineTo(1.9f, 0f)
        path.moveTo(.25f, -1.6f)
        path.lineTo(1.9f, 0f)
        path.lineTo(.25f, 1.6f)
        canvas.drawPath(path, stroke)
        canvas.restoreToCount(saved)
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
                intArrayOf(mix(team, pearl, .37f), mix(team, pearl, .76f)), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, fill)
            fill.shader = null
            stroke.color = pearl
            stroke.strokeWidth = .55f
            canvas.drawPath(path, stroke)
            canvas.restoreToCount(saved)
        }
        fill.color = pearl
        canvas.drawCircle(75f, 75f, 2f, fill)
        stroke.color = 0xFF91B8B1.toInt()
        stroke.strokeWidth = .3f
        canvas.drawCircle(75f, 75f, 2f, stroke)
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
