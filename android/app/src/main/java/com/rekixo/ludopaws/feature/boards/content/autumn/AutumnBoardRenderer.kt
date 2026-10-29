package com.rekixo.ludopaws.feature.boards.content.autumn

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

internal class AutumnBoardRenderer : BoardRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val bark = 0xFF6D4233.toInt()
    private val cream = 0xFFF2DDBA.toInt()
    private val leafColors = intArrayOf(0xFFB65332.toInt(), 0xFFD7893E.toInt(), 0xFF8A3D3C.toInt(), 0xFFC16935.toInt())
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
                intArrayOf(0xFFF0D9B9.toInt(), definition.surfaceColor, 0xFFD3AB88.toInt()),
                floatArrayOf(0f, .53f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 150f, 150f, fill)
            fill.shader = null
            clearing(canvas, 0f, 0f, definition.teamColor(teams[0]))
            clearing(canvas, 100f, 0f, definition.teamColor(teams[1]))
            clearing(canvas, 100f, 100f, definition.teamColor(teams[2]))
            clearing(canvas, 0f, 100f, definition.teamColor(teams[3]))
            roads(canvas, definition)
            finish(canvas, definition)
            stroke.color = 0xFFB08B68.toInt()
            stroke.strokeWidth = .45f
            canvas.drawRect(.35f, .35f, 149.65f, 149.65f, stroke)
        } finally {
            fill.shader = null
            canvas.restoreToCount(saved)
        }
    }

    private fun clearing(canvas: Canvas, left: Float, top: Float, team: Int) {
        val saved = canvas.save()
        canvas.translate(left, top)
        canvas.clipRect(0f, 0f, 50f, 50f)
        fill.shader = RadialGradient(23f, 22f, 37f,
            intArrayOf(0xFFDCB18A.toInt(), 0xFF985D43.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 50f, 50f, fill)
        fill.shader = null
        leafOutline(1.8f, 3f, 48.2f, 48.8f)
        fill.color = bark
        canvas.drawPath(path, fill)
        leafOutline(2.8f, 1.6f, 47.2f, 46.2f)
        fill.shader = LinearGradient(4f, 4f, 45f, 45f,
            intArrayOf(mix(team, 0xFFE3BD91.toInt(), .42f), mix(team, bark, .38f)),
            null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        fill.shader = null
        leafOutline(5.6f, 4.5f, 44.4f, 43.5f)
        fill.shader = RadialGradient(19f, 17f, 34f,
            intArrayOf(0xFFF1D9B7.toInt(), 0xFFE2BD91.toInt(), 0xFFCB9D73.toInt()),
            floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        fill.shader = null
        stroke.color = 0x80F9E3BF.toInt()
        stroke.strokeWidth = .45f
        canvas.drawPath(path, stroke)
        branch(canvas, 3.3f, 3.8f, 0f)
        branch(canvas, 47f, 46f, 180f)
        for (leafIndex in 0..7) {
            val edge = leafIndex / 2
            val along = if (leafIndex % 2 == 0) 12f else 36f
            val positionX = when (edge) { 0, 2 -> along; 1 -> 47f; else -> 3f }
            val positionY = when (edge) { 1, 3 -> along; 0 -> 3.5f; else -> 47f }
            mapleLeaf(canvas, positionX, positionY, 1.4f + leafIndex % 3 * .20f,
                29f + leafIndex * 47f, leafColors[leafIndex % leafColors.size])
        }
        stroke.color = 0xFFA98160.toInt()
        stroke.strokeWidth = .35f
        canvas.drawArc(RectF(2f, 16f, 8f, 28f), 95f, 110f, false, stroke)
        canvas.drawArc(RectF(42f, 22f, 48f, 34f), -80f, 110f, false, stroke)
        fill.color = mix(team, bark, .12f)
        canvas.drawRoundRect(20f, 43.5f, 30f, 45.2f, .7f, .7f, fill)
        canvas.restoreToCount(saved)
    }

    private fun leafOutline(left: Float, top: Float, right: Float, bottom: Float) {
        val centerX = (left + right) * .5f
        val centerY = (top + bottom) * .5f
        val width = right - left
        val height = bottom - top
        path.reset()
        path.moveTo(centerX, top)
        path.cubicTo(right - width * .08f, top + height * .05f, right + width * .02f, centerY - height * .13f, right, centerY)
        path.cubicTo(right + width * .01f, bottom - height * .14f, centerX + width * .20f, bottom - height * .015f, centerX, bottom)
        path.cubicTo(left + width * .11f, bottom, left - width * .01f, centerY + height * .16f, left, centerY)
        path.cubicTo(left - width * .01f, top + height * .18f, centerX - width * .25f, top + height * .08f, centerX, top)
        path.close()
    }

    private fun branch(canvas: Canvas, positionX: Float, positionY: Float, rotation: Float) {
        val saved = canvas.save()
        canvas.translate(positionX, positionY)
        canvas.rotate(rotation)
        stroke.color = bark
        stroke.strokeWidth = .70f
        path.reset()
        path.moveTo(-2f, 14f)
        path.quadTo(1f, 3f, 13f, -1f)
        path.moveTo(1f, 7f)
        path.lineTo(6f, 8.5f)
        path.moveTo(5f, 2.7f)
        path.lineTo(5f, -1f)
        canvas.drawPath(path, stroke)
        mapleLeaf(canvas, 1.2f, 8f, 2.2f, -30f, leafColors[0])
        mapleLeaf(canvas, 7.5f, 3f, 2.6f, 45f, leafColors[1])
        mapleLeaf(canvas, 5.5f, 8.5f, 1.7f, 125f, leafColors[2])
        canvas.restoreToCount(saved)
    }

    private fun mapleLeaf(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, rotation: Float, color: Int) {
        val saved = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(rotation)
        path.reset()
        path.moveTo(0f, -radius)
        path.lineTo(radius * .24f, -radius * .48f)
        path.lineTo(radius * .66f, -radius * .68f)
        path.lineTo(radius * .54f, -radius * .13f)
        path.lineTo(radius, -radius * .08f)
        path.lineTo(radius * .56f, radius * .40f)
        path.lineTo(radius * .62f, radius * .69f)
        path.lineTo(0f, radius * .51f)
        path.lineTo(-radius * .62f, radius * .69f)
        path.lineTo(-radius * .56f, radius * .40f)
        path.lineTo(-radius, -radius * .08f)
        path.lineTo(-radius * .54f, -radius * .13f)
        path.lineTo(-radius * .66f, -radius * .68f)
        path.lineTo(-radius * .24f, -radius * .48f)
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
        stroke.color = mix(color, 0xFFF0CE96.toInt(), .50f)
        stroke.strokeWidth = .16f
        canvas.drawLine(0f, -radius * .55f, 0f, radius * .80f, stroke)
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
            val tile = RectF(axis(column), axis(row), axis(column + 1), axis(row + 1))
            val team = if (teamIndex < 0) null else definition.teamColor(teams[teamIndex])
            plank(canvas, tile, team, vertical)
            val safe = row == 2 && column == 6 || row == 6 && column == 12 ||
                row == 12 && column == 8 || row == 8 && column == 2
            val entry = row == 6 && column == 1 || row == 1 && column == 8 ||
                row == 8 && column == 13 || row == 13 && column == 6
            if (safe || entry) star(canvas, tile.centerX(), tile.centerY(), 2.5f, bark)
        }
    }

    private fun plank(canvas: Canvas, raw: RectF, team: Int?, vertical: Boolean) {
        val unit = min(raw.width(), raw.height())
        val face = RectF(raw)
        face.inset(unit * .055f, unit * .055f)
        fill.color = 0xFF936A4B.toInt()
        canvas.drawRoundRect(face.left, face.top + .45f, face.right, face.bottom, .50f, .50f, fill)
        face.bottom -= .55f
        val timber = if (team == null) 0xFFE3C399.toInt() else mix(team, 0xFFE8CDA9.toInt(), .30f)
        fill.shader = LinearGradient(face.left, face.top, face.right, face.bottom,
            intArrayOf(mix(timber, 0xFFF5DFC0.toInt(), .20f), timber, mix(timber, bark, .15f)),
            floatArrayOf(0f, .60f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(face, .50f, .50f, fill)
        fill.shader = null
        stroke.color = mix(timber, bark, .18f)
        stroke.strokeWidth = .22f
        for (grainIndex in 1..2) {
            val across = grainIndex / 3f
            path.reset()
            if (vertical) {
                val grainX = face.left + face.width() * across
                path.moveTo(grainX, face.top + .7f)
                path.cubicTo(grainX - .3f, face.centerY(), grainX + .4f, face.centerY(), grainX, face.bottom - .7f)
            } else {
                val grainY = face.top + face.height() * across
                path.moveTo(face.left + .7f, grainY)
                path.cubicTo(face.centerX(), grainY - .3f, face.centerX(), grainY + .35f, face.right - .7f, grainY)
            }
            canvas.drawPath(path, stroke)
        }
        stroke.color = mix(timber, cream, .50f)
        stroke.strokeWidth = .25f
        canvas.drawLine(face.left + .65f, face.top + .45f, face.right - .65f, face.top + .45f, stroke)
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
                intArrayOf(mix(team, bark, .25f), mix(team, 0xFFE8CDA9.toInt(), .30f)), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, fill)
            fill.shader = null
            stroke.color = 0xFFEED5B1.toInt()
            stroke.strokeWidth = .45f
            canvas.drawPath(path, stroke)
            canvas.restoreToCount(saved)
        }
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
