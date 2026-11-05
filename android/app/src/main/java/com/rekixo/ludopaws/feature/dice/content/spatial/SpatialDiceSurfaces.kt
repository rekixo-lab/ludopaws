package com.rekixo.ludopaws.feature.dice.content.spatial

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.random.Random

/** Two small shared material maps. Geometry, lighting and pips are drawn separately. */
internal object SpatialDiceSurfaces {
    private const val SIZE = 128
    private val hammered by lazy { create(::hammeredIron) }
    private val crystal by lazy { create(::cutCrystal) }

    fun forMaterial(material: SpatialDiceMaterial): Bitmap = when (material) {
        SpatialDiceMaterial.FORGE -> hammered
        SpatialDiceMaterial.PRISM -> crystal
    }

    private fun create(draw: (Canvas, Paint) -> Unit): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.scale(SIZE / 100f, SIZE / 100f)
            draw(canvas, Paint(Paint.ANTI_ALIAS_FLAG))
        }

    private fun hammeredIron(canvas: Canvas, paint: Paint) {
        paint.shader = LinearGradient(0f, 0f, 100f, 100f,
            intArrayOf(0xFF67504B.toInt(), 0xFF342B32.toInt(), 0xFF211F27.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 100f, 100f, paint)
        paint.shader = null

        val random = Random(4127)
        repeat(148) {
            val x = random.nextFloat() * 100f
            val y = random.nextFloat() * 100f
            val r = 1.6f + random.nextFloat() * 3f
            paint.color = 0x426F5145
            canvas.drawOval(x - r, y - r * .55f, x + r, y + r * .74f, paint)
            paint.color = 0x5118131B
            canvas.drawOval(x - r * .88f, y - r * .67f, x + r * .80f, y + r * .42f, paint)
        }
        repeat(240) { index ->
            val x = random.nextFloat() * 100f
            val y = random.nextFloat() * 100f
            paint.color = if (index % 4 == 0) 0x64E3A477 else 0x49221820
            canvas.drawCircle(x, y, .2f + random.nextFloat() * .65f, paint)
        }
        paint.strokeWidth = .38f
        repeat(26) { index ->
            val x = random.nextFloat() * 100f
            val y = random.nextFloat() * 100f
            val length = 1f + random.nextFloat() * 6f
            paint.color = if (index % 3 == 0) 0x73CC9A79 else 0x6231252B
            canvas.drawLine(x, y, x + length, y - length * .31f, paint)
        }
    }

    private fun cutCrystal(canvas: Canvas, paint: Paint) {
        paint.shader = RadialGradient(29f, 16f, 103f,
            intArrayOf(0xFF339FB8.toInt(), 0xFF244782.toInt(), 0xFF242454.toInt()),
            floatArrayOf(0f, .49f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 100f, 100f, paint)
        paint.shader = null

        val facet = Path()
        fun plane(color: Int, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) {
            facet.rewind()
            facet.moveTo(ax, ay)
            facet.lineTo(bx, by)
            facet.lineTo(cx, cy)
            facet.close()
            paint.color = color
            canvas.drawPath(facet, paint)
        }
        plane(0x6687EEE8, 0f, 0f, 76f, 0f, 29f, 39f)
        plane(0x712A216D, 100f, 0f, 100f, 74f, 29f, 39f)
        plane(0x66BD98FF, 76f, 0f, 100f, 0f, 29f, 39f)
        plane(0x5A122544, 0f, 0f, 29f, 39f, 0f, 85f)
        plane(0x6F3A94CE, 0f, 85f, 29f, 39f, 72f, 100f)
        plane(0x6D7848B2, 29f, 39f, 100f, 74f, 72f, 100f)
        plane(0x626BE3E8, 100f, 74f, 100f, 100f, 72f, 100f)

        paint.color = 0x68D4F6FF
        paint.strokeWidth = .46f
        canvas.drawLine(29f, 39f, 76f, 0f, paint)
        canvas.drawLine(29f, 39f, 72f, 100f, paint)
        paint.color = 0x3FE6BEFF
        canvas.drawLine(29f, 39f, 100f, 74f, paint)

        val random = Random(723)
        repeat(32) {
            paint.color = 0x62E2FBFF
            canvas.drawCircle(random.nextFloat() * 100f, random.nextFloat() * 100f,
                .24f + random.nextFloat() * .52f, paint)
        }
    }
}
