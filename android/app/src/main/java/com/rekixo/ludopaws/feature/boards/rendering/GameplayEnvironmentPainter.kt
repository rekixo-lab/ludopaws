package com.rekixo.ludopaws.feature.boards.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.rekixo.ludopaws.feature.boards.content.classic.ClassicJungleHabitat
import kotlin.random.Random

internal class GameplayEnvironmentPainter(private val environment: GameplayBoardEnvironment) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    fun draw(canvas: Canvas, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        if (environment.boardId == "board_classic") {
            ClassicJungleHabitat().drawEnvironment(canvas, width, height)
            return
        }
        val saved = canvas.save()
        val scale = width / 150f
        val extent = height / scale
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, 150f, extent)
        fill.shader = LinearGradient(0f, 0f, 150f, extent,
            environment.groundTop, environment.groundBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 150f, extent, fill)
        fill.shader = null
        val random = Random(environment.boardId.hashCode())
        repeat((extent * 2f).toInt().coerceAtMost(1600)) { index ->
            val positionX = random.nextFloat() * 150f
            val positionY = random.nextFloat() * extent
            fill.color = translucent(environment.detail, if (index % 3 == 0) 30 else 14)
            canvas.drawOval(positionX - 1f, positionY - .3f, positionX + 1f, positionY + .3f, fill)
        }
        when (environment.boardId) {
            "board_autumn_forest" -> autumn(canvas, extent, random)
            "board_blossom_garden" -> blossom(canvas, extent, random)
            "board_winter_haven" -> winter(canvas, extent, random)
            "board_monsoon_mire" -> monsoon(canvas, extent, random)
            "board_cloud_lagoon" -> lagoon(canvas, extent, random)
            "board_astral" -> astral(canvas, extent, random)
            else -> generic(canvas, extent, random)
        }
        canvas.restoreToCount(saved)
    }

    private fun autumn(canvas: Canvas, extent: Float, random: Random) {
        repeat(90) { index ->
            val positionX = if (index % 4 == 0) random.nextFloat() * 150f else edge(random, index)
            val positionY = random.nextFloat() * extent
            val saved = canvas.save()
            canvas.translate(positionX, positionY)
            canvas.rotate(random.nextFloat() * 360f)
            val size = .7f + random.nextFloat() * 1.4f
            fill.color = if (index % 3 == 0) 0xFFDCAC64.toInt() else if (index % 3 == 1) environment.detail else 0xFFA85536.toInt()
            path.reset()
            path.moveTo(0f, -size * 2f)
            path.quadTo(-size * 1.6f, -.3f, 0f, size)
            path.quadTo(size * 1.6f, -.3f, 0f, -size * 2f)
            canvas.drawPath(path, fill)
            stroke.color = 0x70704A2D
            stroke.strokeWidth = .16f
            canvas.drawLine(0f, -size * 1.6f, 0f, size * 1.4f, stroke)
            canvas.restoreToCount(saved)
        }
        trees(canvas, extent, 0xFF70503A.toInt(), false)
    }

    private fun blossom(canvas: Canvas, extent: Float, random: Random) {
        repeat(68) { index ->
            val positionX = edge(random, index)
            val positionY = random.nextFloat() * extent
            fill.color = translucent(0xFF61865E.toInt(), 100)
            canvas.drawOval(positionX - 3.7f, positionY - 1.1f, positionX + 3.7f, positionY + 1.1f, fill)
            flower(canvas, positionX, positionY, 1f + random.nextFloat(), index)
        }
        repeat(55) { index ->
            fill.color = if (index % 2 == 0) 0xA6FCE6EC.toInt() else 0x70D687A2
            val saved = canvas.save()
            canvas.translate(random.nextFloat() * 150f, random.nextFloat() * extent)
            canvas.rotate(random.nextFloat() * 180f)
            canvas.drawOval(-.5f, -1.1f, .5f, 1.1f, fill)
            canvas.restoreToCount(saved)
        }
    }

    private fun flower(canvas: Canvas, positionX: Float, positionY: Float, size: Float, index: Int) {
        val saved = canvas.save()
        canvas.translate(positionX, positionY)
        fill.color = if (index % 2 == 0) 0xFFF3D4DF.toInt() else environment.detail
        repeat(5) {
            canvas.rotate(72f)
            canvas.drawOval(-size * .5f, -size * 1.5f, size * .5f, 0f, fill)
        }
        fill.color = 0xFFE5CB8A.toInt()
        canvas.drawCircle(0f, 0f, size * .3f, fill)
        canvas.restoreToCount(saved)
    }

    private fun winter(canvas: Canvas, extent: Float, random: Random) {
        repeat(26) { index ->
            val positionX = edge(random, index)
            val positionY = random.nextFloat() * extent
            fill.color = 0x7096B8C1
            canvas.drawOval(positionX - 11f, positionY - 2.5f, positionX + 11f, positionY + 4f, fill)
            fill.color = 0xC9EEF6F5.toInt()
            canvas.drawOval(positionX - 10f, positionY - 3f, positionX + 10f, positionY + 1.7f, fill)
        }
        trees(canvas, extent, environment.detail, true)
        repeat(95) {
            val positionX = random.nextFloat() * 150f
            val positionY = random.nextFloat() * extent
            fill.color = 0xAEF5FCFD.toInt()
            canvas.drawCircle(positionX, positionY, .18f + random.nextFloat() * .35f, fill)
        }
    }

    private fun trees(canvas: Canvas, extent: Float, color: Int, snow: Boolean) {
        val count = (extent / 40f).toInt().coerceIn(3, 20)
        repeat(count) { index ->
            val positionX = if (index % 2 == 0) 5f else 145f
            val positionY = 14f + index * (extent - 25f) / count
            fill.color = translucent(color, 70)
            canvas.drawOval(positionX - 6f, positionY + 7f, positionX + 7f, positionY + 10f, fill)
            stroke.color = color
            stroke.strokeWidth = 1.1f
            canvas.drawLine(positionX, positionY, positionX, positionY + 8f, stroke)
            repeat(3) { layer ->
                val top = positionY - 6f + layer * 3f
                val spread = 3f + layer * 1.5f
                path.reset()
                path.moveTo(positionX, top)
                path.lineTo(positionX + spread, top + 6f)
                path.lineTo(positionX - spread, top + 6f)
                path.close()
                fill.color = color
                canvas.drawPath(path, fill)
                if (snow) {
                    path.reset()
                    path.moveTo(positionX, top)
                    path.lineTo(positionX + spread * .72f, top + 4f)
                    path.quadTo(positionX, top + 3f, positionX - spread * .72f, top + 4f)
                    path.close()
                    fill.color = 0xFFEDF6F5.toInt()
                    canvas.drawPath(path, fill)
                }
            }
        }
    }

    private fun monsoon(canvas: Canvas, extent: Float, random: Random) {
        repeat(35) { index ->
            val positionX = if (index % 3 == 0) random.nextFloat() * 150f else edge(random, index)
            val positionY = random.nextFloat() * extent
            val radius = 2.3f + random.nextFloat() * 5f
            fill.color = translucent(environment.detail, 105)
            canvas.drawOval(positionX - radius - 1f, positionY - radius * .4f - .5f,
                positionX + radius + 1f, positionY + radius * .4f + 1f, fill)
            fill.shader = LinearGradient(positionX, positionY - radius, positionX, positionY + radius,
                0xB9A3BBB5.toInt(), 0xC66E9292.toInt(), Shader.TileMode.CLAMP)
            canvas.drawOval(positionX - radius, positionY - radius * .4f, positionX + radius, positionY + radius * .4f, fill)
            fill.shader = null
            stroke.color = 0x709FC8C5
            stroke.strokeWidth = .18f
            canvas.drawOval(positionX - radius * .55f, positionY - radius * .15f,
                positionX + radius * .55f, positionY + radius * .15f, stroke)
        }
        repeat(95) {
            val positionX = random.nextFloat() * 150f
            val positionY = random.nextFloat() * extent
            stroke.color = 0x476D9697
            stroke.strokeWidth = .12f
            canvas.drawLine(positionX, positionY, positionX - .7f, positionY + 2.5f, stroke)
        }
    }

    private fun lagoon(canvas: Canvas, extent: Float, random: Random) {
        repeat(30) { index ->
            val positionX = if (index % 2 == 0) edge(random, index) else random.nextFloat() * 150f
            val positionY = random.nextFloat() * extent
            val radius = 3f + random.nextFloat() * 8f
            stroke.color = 0x5073BFB8
            stroke.strokeWidth = .4f
            canvas.drawOval(positionX - radius, positionY - radius * .25f, positionX + radius, positionY + radius * .25f, stroke)
        }
        repeat(16) { index ->
            val positionX = edge(random, index)
            val positionY = random.nextFloat() * extent
            fill.color = 0xA8F6F9EF.toInt()
            canvas.drawOval(positionX - 8f, positionY - 2f, positionX + 8f, positionY + 2f, fill)
            canvas.drawCircle(positionX - 3f, positionY - 1f, 3f, fill)
            canvas.drawCircle(positionX + 1f, positionY - 2f, 4f, fill)
            canvas.drawCircle(positionX + 5f, positionY - .8f, 2.5f, fill)
        }
    }

    private fun astral(canvas: Canvas, extent: Float, random: Random) {
        fill.shader = RadialGradient(118f, extent * .28f, 95f,
            intArrayOf(0x404C7896, 0x0012263B), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 150f, extent, fill)
        fill.shader = null
        repeat(145) { index ->
            val positionX = random.nextFloat() * 150f
            val positionY = random.nextFloat() * extent
            fill.color = translucent(environment.accent, 90 + index % 140)
            canvas.drawCircle(positionX, positionY, if (index % 9 == 0) .48f else .21f, fill)
            if (index % 19 == 0) {
                stroke.color = translucent(environment.accent, 120)
                stroke.strokeWidth = .18f
                canvas.drawLine(positionX - 1.4f, positionY, positionX + 1.4f, positionY, stroke)
                canvas.drawLine(positionX, positionY - 1.4f, positionX, positionY + 1.4f, stroke)
            }
        }
        generic(canvas, extent, random)
    }

    private fun generic(canvas: Canvas, extent: Float, random: Random) {
        repeat(12) { index ->
            val positionX = edge(random, index)
            val positionY = random.nextFloat() * extent
            stroke.color = translucent(environment.accent, 45)
            stroke.strokeWidth = .45f
            canvas.drawCircle(positionX, positionY, 3f + random.nextFloat() * 7f, stroke)
        }
    }

    private fun edge(random: Random, index: Int) = if (index % 2 == 0) random.nextFloat() * 12f else 138f + random.nextFloat() * 12f
    private fun translucent(color: Int, alpha: Int) = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
