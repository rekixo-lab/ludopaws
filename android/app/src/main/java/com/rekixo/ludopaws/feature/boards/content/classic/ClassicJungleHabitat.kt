package com.rekixo.ludopaws.feature.boards.content.classic

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.rekixo.ludopaws.LudoPawsBoardPalette
import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import kotlin.random.Random

internal class ClassicJungleHabitat {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val moss = 0xFF698349.toInt()
    private val leaf = 0xFF3E713E.toInt()
    private val deepLeaf = 0xFF284F35.toInt()

    fun draw(canvas: Canvas, size: Float, definition: BoardDefinition) {
        val saved = canvas.save()
        canvas.scale(size / 150f, size / 150f)
        val colors = intArrayOf(LudoPawsBoardPalette.coral, LudoPawsBoardPalette.jade,
            LudoPawsBoardPalette.honey, LudoPawsBoardPalette.sky)
        clearing(canvas, 0f, 0f, definition.teamColor(colors[0]), 742)
        clearing(canvas, 100f, 0f, definition.teamColor(colors[1]), 1321)
        clearing(canvas, 100f, 100f, definition.teamColor(colors[2]), 2089)
        clearing(canvas, 0f, 100f, definition.teamColor(colors[3]), 3011)
        canvas.restoreToCount(saved)
    }

    private fun clearing(canvas: Canvas, left: Float, top: Float, team: Int, seed: Int) {
        val saved = canvas.save()
        canvas.translate(left, top)
        canvas.clipRect(0f, 0f, 50f, 50f)
        fill.shader = LinearGradient(0f, 0f, 50f, 50f,
            intArrayOf(0xFF506B3D.toInt(), 0xFF314F35.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 50f, 50f, fill)
        fill.shader = null
        val random = Random(seed)
        repeat(180) {
            val positionX = random.nextFloat() * 50f
            val positionY = random.nextFloat() * 50f
            fill.color = if (it % 3 == 0) 0x305D8B43 else 0x2831412A
            canvas.drawOval(positionX - 1.4f, positionY - .7f,
                positionX + 1.4f, positionY + .7f, fill)
        }
        path.reset()
        path.moveTo(12f, 6.7f)
        path.cubicTo(23f, 3f, 43f, 5f, 44f, 15f)
        path.cubicTo(47f, 23f, 44f, 40f, 37f, 44f)
        path.cubicTo(26f, 47f, 9f, 44f, 6f, 35f)
        path.cubicTo(3f, 24f, 5f, 11f, 12f, 6.7f)
        path.close()
        fill.color = 0xFF354C30.toInt()
        canvas.drawPath(path, fill)
        val ground = canvas.save()
        canvas.clipPath(path)
        fill.shader = RadialGradient(22f, 20f, 32f,
            intArrayOf(0xFFADB17B.toInt(), 0xFF8D9861.toInt(), 0xFF607E49.toInt()),
            floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 50f, 50f, fill)
        fill.shader = null
        repeat(260) {
            val positionX = 5f + random.nextFloat() * 40f
            val positionY = 5f + random.nextFloat() * 40f
            fill.color = if (it % 2 == 0) 0x2072623D else 0x2090AC67
            canvas.drawOval(positionX - .6f, positionY - .28f,
                positionX + .6f, positionY + .28f, fill)
        }
        canvas.restoreToCount(ground)
        stroke.color = 0xFF526A3E.toInt()
        stroke.strokeWidth = .55f
        canvas.drawPath(path, stroke)
        stroke.color = team
        stroke.strokeWidth = .9f
        canvas.drawLine(18f, 47f, 32f, 47f, stroke)
        stroke.strokeWidth = .6f
        canvas.drawLine(47f, 19f, 47f, 31f, stroke)
        repeat(16) { index ->
            val along = 4f + index * 2.7f
            fern(canvas, along, 3.5f, 12f + index * 19f, .7f)
            fern(canvas, 50f - along, 46.7f, 164f + index * 17f, .72f)
        }
        bamboo(canvas, 3.5f, 22f)
        bamboo(canvas, 46.5f, 28f)
        palm(canvas, 5.8f, 6f, 1f)
        palm(canvas, 44.5f, 44f, .9f)
        fern(canvas, 43.5f, 5.5f, 125f, 1.25f)
        fern(canvas, 6.3f, 44f, -45f, 1.25f)
        rock(canvas, 38f, 4f, 2f)
        rock(canvas, 12f, 46f, 1.6f)
        canvas.restoreToCount(saved)
    }

    private fun fern(canvas: Canvas, positionX: Float, positionY: Float, rotation: Float, scale: Float) {
        val saved = canvas.save()
        canvas.translate(positionX, positionY)
        canvas.rotate(rotation)
        canvas.scale(scale, scale)
        stroke.color = moss
        stroke.strokeWidth = .24f
        canvas.drawLine(0f, 0f, 0f, -3.2f, stroke)
        repeat(4) { index ->
            val along = -.45f - index * .65f
            val width = 1.6f - index * .29f
            fill.color = if (index % 2 == 0) leaf else moss
            path.reset()
            path.moveTo(0f, along)
            path.quadTo(-width, along + .15f, -width, along - .7f)
            path.quadTo(-.4f, along - .65f, 0f, along)
            path.quadTo(width, along + .15f, width, along - .7f)
            path.quadTo(.4f, along - .65f, 0f, along)
            canvas.drawPath(path, fill)
        }
        canvas.restoreToCount(saved)
    }

    private fun palm(canvas: Canvas, positionX: Float, positionY: Float, scale: Float) {
        fill.color = 0x35304528
        canvas.drawOval(positionX - 4.5f, positionY - 2f, positionX + 5f, positionY + 5f, fill)
        val saved = canvas.save()
        canvas.translate(positionX, positionY)
        canvas.scale(scale, scale)
        stroke.color = 0xFF7D7950.toInt()
        stroke.strokeWidth = .8f
        canvas.drawLine(0f, 2.8f, .5f, -.6f, stroke)
        repeat(8) { frond ->
            val turn = canvas.save()
            canvas.rotate(frond * 45f)
            fill.color = if (frond % 2 == 0) leaf else deepLeaf
            path.reset()
            path.moveTo(0f, 0f)
            path.quadTo(-2.2f, -3f, .8f, -5.2f)
            path.quadTo(2f, -1.6f, 0f, 0f)
            canvas.drawPath(path, fill)
            stroke.color = moss
            stroke.strokeWidth = .20f
            canvas.drawLine(0f, 0f, .8f, -4.7f, stroke)
            canvas.restoreToCount(turn)
        }
        canvas.restoreToCount(saved)
    }

    private fun bamboo(canvas: Canvas, positionX: Float, positionY: Float) {
        repeat(3) { stem ->
            val offset = (stem - 1) * .9f
            stroke.color = if (stem == 1) moss else deepLeaf
            stroke.strokeWidth = .65f
            canvas.drawLine(positionX + offset, positionY + 5f, positionX + offset + .5f, positionY - 5f, stroke)
            stroke.color = 0xFF96AA68.toInt()
            stroke.strokeWidth = .22f
            repeat(4) { joint ->
                val height = positionY - 3.5f + joint * 2.4f
                canvas.drawLine(positionX + offset -.2f, height, positionX + offset + .6f, height, stroke)
            }
        }
        fern(canvas, positionX, positionY - 4f, -70f, .85f)
        fern(canvas, positionX, positionY + 3f, 70f, .85f)
    }

    private fun rock(canvas: Canvas, positionX: Float, positionY: Float, radius: Float) {
        fill.color = 0xFF43543D.toInt()
        canvas.drawOval(positionX - radius, positionY - radius * .5f,
            positionX + radius, positionY + radius * .7f, fill)
        fill.shader = LinearGradient(positionX - radius, positionY - radius, positionX + radius, positionY + radius,
            intArrayOf(0xFF8C9974.toInt(), 0xFF5D7051.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawOval(positionX - radius, positionY - radius * .8f,
            positionX + radius, positionY + radius * .35f, fill)
        fill.shader = null
    }
}
