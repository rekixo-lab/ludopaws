package com.rekixo.ludopaws.feature.boards.content.spring

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

internal class BlossomBoardRenderer : BoardRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val cream = 0xFFF5EEDF.toInt()
    private val leafGreen = 0xFF659268.toInt()
    private val deepGreen = 0xFF3E694E.toInt()
    private val pink = 0xFFE3A0B2.toInt()
    private val petal = 0xFFFFE3E4.toInt()
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
                intArrayOf(0xFFEAF0D9.toInt(), definition.surfaceColor, 0xFFC9DDBD.toInt()),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 150f, 150f, fill)
            fill.shader = null
            garden(canvas, 0f, 0f, definition.teamColor(teams[0]))
            garden(canvas, 100f, 0f, definition.teamColor(teams[1]))
            garden(canvas, 100f, 100f, definition.teamColor(teams[2]))
            garden(canvas, 0f, 100f, definition.teamColor(teams[3]))
            roads(canvas, definition)
            finish(canvas, definition)
            stroke.color = 0xFFADC3A0.toInt()
            stroke.strokeWidth = .35f
            canvas.drawRect(.3f, .3f, 149.7f, 149.7f, stroke)
        } finally {
            fill.shader = null
            canvas.restoreToCount(saved)
        }
    }

    private fun garden(canvas: Canvas, left: Float, top: Float, team: Int) {
        val saved = canvas.save()
        canvas.translate(left, top)
        canvas.clipRect(0f, 0f, 50f, 50f)
        fill.shader = RadialGradient(21f, 19f, 37f,
            intArrayOf(0xFFE5EBCF.toInt(), 0xFFB6CEA4.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 50f, 50f, fill)
        fill.shader = null
        terrace(RectF(4f, 5.5f, 46f, 47f), 7f)
        fill.color = 0xFF92AE82.toInt()
        canvas.drawPath(path, fill)
        terrace(RectF(4f, 3.5f, 46f, 44.7f), 7f)
        fill.shader = LinearGradient(5f, 4f, 45f, 44f,
            intArrayOf(mix(team, cream, .55f), mix(team, cream, .18f)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        fill.shader = null
        terrace(RectF(6.2f, 5.6f, 43.8f, 42.8f), 5.8f)
        fill.shader = LinearGradient(8f, 7f, 42f, 42f,
            intArrayOf(0xFFFFEFE4.toInt(), cream, 0xFFE6DDC5.toInt()),
            floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        fill.shader = null
        stroke.color = 0xFFE7F0D2.toInt()
        stroke.strokeWidth = .45f
        canvas.drawPath(path, stroke)
        vine(canvas, 2.8f, 2.5f, 0f)
        vine(canvas, 47.2f, 47.5f, 180f)
        bloom(canvas, 5.1f, 6f, 2.5f, pink, 8f)
        bloom(canvas, 9f, 3.7f, 1.6f, petal, -16f)
        bloom(canvas, 44.8f, 44.6f, 2.4f, petal, 18f)
        bloom(canvas, 40.5f, 47f, 1.7f, pink, -10f)
        bloom(canvas, 44.5f, 5.5f, 1.8f, pink, 27f)
        bloom(canvas, 5.5f, 44.5f, 1.8f, petal, 15f)
        leaf(canvas, 3.2f, 30f, 36f)
        leaf(canvas, 46.8f, 20f, -144f)
        leaf(canvas, 23f, 3f, -12f)
        leaf(canvas, 27f, 47f, 168f)
        stroke.color = mix(team, cream, .12f)
        stroke.strokeWidth = .75f
        canvas.drawLine(18f, 44.2f, 32f, 44.2f, stroke)
        canvas.restoreToCount(saved)
    }

    private fun terrace(bounds: RectF, corner: Float) {
        path.reset()
        path.moveTo(bounds.left + corner, bounds.top)
        path.lineTo(bounds.right - corner, bounds.top)
        path.quadTo(bounds.right, bounds.top, bounds.right, bounds.top + corner)
        path.lineTo(bounds.right, bounds.bottom - corner)
        path.quadTo(bounds.right, bounds.bottom, bounds.right - corner, bounds.bottom)
        path.lineTo(bounds.left + corner, bounds.bottom)
        path.quadTo(bounds.left, bounds.bottom, bounds.left, bounds.bottom - corner)
        path.lineTo(bounds.left, bounds.top + corner)
        path.quadTo(bounds.left, bounds.top, bounds.left + corner, bounds.top)
        path.close()
    }

    private fun vine(canvas: Canvas, positionX: Float, positionY: Float, rotation: Float) {
        val saved = canvas.save()
        canvas.translate(positionX, positionY)
        canvas.rotate(rotation)
        stroke.color = leafGreen
        stroke.strokeWidth = .50f
        path.reset()
        path.moveTo(0f, 17f)
        path.cubicTo(-1f, 7f, 3f, 0f, 17f, 0f)
        canvas.drawPath(path, stroke)
        leaf(canvas, 1.5f, 11f, -35f)
        leaf(canvas, 4.3f, 5.3f, -55f)
        leaf(canvas, 12f, 1.5f, 25f)
        canvas.restoreToCount(saved)
    }

    private fun leaf(canvas: Canvas, centerX: Float, centerY: Float, rotation: Float) {
        val saved = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(rotation)
        path.reset()
        path.moveTo(-2f, 0f)
        path.quadTo(.2f, -2.1f, 2.5f, 0f)
        path.quadTo(.2f, 1.8f, -2f, 0f)
        fill.color = leafGreen
        canvas.drawPath(path, fill)
        stroke.color = 0xFFB9D09C.toInt()
        stroke.strokeWidth = .20f
        canvas.drawLine(-1.3f, 0f, 1.7f, 0f, stroke)
        canvas.restoreToCount(saved)
    }

    private fun bloom(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, color: Int, rotation: Float) {
        val saved = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(rotation)
        fill.color = 0x24446143
        canvas.drawCircle(.18f, .35f, radius, fill)
        fill.color = mix(color, 0xFFBC718F.toInt(), .12f)
        for (petalIndex in 0..4) {
            val petalSave = canvas.save()
            canvas.rotate(petalIndex * 72f)
            canvas.drawOval(-radius * .48f, -radius, radius * .48f, radius * .19f, fill)
            canvas.restoreToCount(petalSave)
        }
        fill.color = color
        for (petalIndex in 0..4) {
            val petalSave = canvas.save()
            canvas.rotate(petalIndex * 72f)
            canvas.drawOval(-radius * .31f, -radius * .83f, radius * .31f, radius * .03f, fill)
            canvas.restoreToCount(petalSave)
        }
        fill.color = 0xFFE2B85D.toInt()
        canvas.drawCircle(0f, 0f, radius * .24f, fill)
        fill.color = 0xFFFFE7A4.toInt()
        canvas.drawCircle(-radius * .06f, -radius * .07f, radius * .10f, fill)
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
            if (safe || entry) star(canvas, raw.centerX(), raw.centerY(), 2.4f)
        }
    }

    private fun tile(canvas: Canvas, raw: RectF, team: Int?) {
        val unit = min(raw.width(), raw.height())
        val face = RectF(raw)
        face.inset(unit * .06f, unit * .06f)
        terrace(face, unit * .18f)
        fill.color = 0xFF98AF90.toInt()
        val saved = canvas.save()
        canvas.translate(0f, .45f)
        canvas.drawPath(path, fill)
        canvas.restoreToCount(saved)
        val color = if (team == null) cream else mix(team, cream, .38f)
        fill.shader = LinearGradient(face.left, face.top, face.right, face.bottom,
            intArrayOf(mix(color, 0xFFFFF4E7.toInt(), .20f), color, mix(color, 0xFF92AA82.toInt(), .12f)),
            floatArrayOf(0f, .60f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        fill.shader = null
        stroke.color = if (team == null) 0xFFADC1A3.toInt() else mix(team, deepGreen, .16f)
        stroke.strokeWidth = .23f
        canvas.drawPath(path, stroke)
        stroke.color = mix(color, 0xFFFFF4E7.toInt(), .65f)
        stroke.strokeWidth = .35f
        canvas.drawLine(face.left + unit * .22f, face.top + .55f,
            face.right - unit * .22f, face.top + .55f, stroke)
    }

    private fun star(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        path.reset()
        repeat(10) { pointIndex ->
            val angle = pointIndex * PI / 5.0 - PI / 2.0
            val length = radius * if (pointIndex % 2 == 0) 1f else .43f
            val pointX = centerX + cos(angle).toFloat() * length
            val pointY = centerY + sin(angle).toFloat() * length
            if (pointIndex == 0) path.moveTo(pointX, pointY) else path.lineTo(pointX, pointY)
        }
        path.close()
        fill.color = deepGreen
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
                intArrayOf(mix(team, cream, .27f), mix(team, cream, .66f)), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, fill)
            fill.shader = null
            stroke.color = cream
            stroke.strokeWidth = .50f
            canvas.drawPath(path, stroke)
            canvas.restoreToCount(saved)
        }
        bloom(canvas, 75f, 75f, 2f, petal, 0f)
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
