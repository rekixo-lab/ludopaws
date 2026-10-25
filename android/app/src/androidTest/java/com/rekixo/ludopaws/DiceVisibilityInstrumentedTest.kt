package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DiceVisibilityInstrumentedTest {
    @Test
    fun neutralDiceFaceStaysBrightAndOpaqueOverDarkBlueGameplayBackground() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()
        val size = 192
        val backgroundColor = 0xFF071A47.toInt()

        // Render the View directly into a known-size bitmap. A detached
        // FrameLayout can vary in child drawing/layout behavior across Android
        // versions; that is unrelated to the dice contrast this test protects.
        // A static outcome also prevents a frame callback from rotating/scaling
        // the die while the pixel assertion is running.
        val dice =
            DiceView(context).apply {
                isEnabled = false
                alpha = 1f
                setPreviewStyle("dice_classic")
                showOutcome(
                    outcome = 6,
                    animate = false,
                )
            }
        dice.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(
                size,
                android.view.View.MeasureSpec.EXACTLY,
            ),
            android.view.View.MeasureSpec.makeMeasureSpec(
                size,
                android.view.View.MeasureSpec.EXACTLY,
            ),
        )
        dice.layout(0, 0, size, size)

        val bitmap =
            Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)
        dice.draw(canvas)

        // Sample the upper-middle face, away from the rounded corners and
        // the six pips. Keep the location fixed so failures are reproducible.
        val pixel =
            bitmap.getPixel(
                size / 2,
                (size * .20f).toInt(),
            )
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)

        // Protect visibility against the real background rather than requiring
        // every RGB channel to be gray; Classic uses a deliberately warm ivory.
        assertTrue(
            "dice face must remain bright (sampled RGB=$red,$green,$blue)",
            androidx.core.graphics.ColorUtils.calculateContrast(pixel, backgroundColor) >= 7.0,
        )
        assertTrue(
            "dice face must remain neutral, not blue-tinted (sampled RGB=$red,$green,$blue)",
            abs(red - blue) <= 18,
        )
        assertEquals(
            "dice view must not be dimmed",
            1f,
            dice.alpha,
            0f,
        )
    }
}
