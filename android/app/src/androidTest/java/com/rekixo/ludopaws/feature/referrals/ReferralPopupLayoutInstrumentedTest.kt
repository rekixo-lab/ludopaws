package com.rekixo.ludopaws.feature.referrals

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rekixo.ludopaws.HomeActivity
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.core.ui.art.PlywoodTextureDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.rekixo.ludopaws.ui.home.HomeSpaceBackgroundView
import org.junit.Test
import org.junit.runner.RunWith

/** Run on an isolated emulator/QA install, never by clearing a player's data. */
@RunWith(AndroidJUnit4::class)
class ReferralPopupLayoutInstrumentedTest {
    @Suppress("DEPRECATION")
    @Test fun largeFontsAndRtlKeepTheWholeOfferScrollableAndCloseTargetAccessible() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val original = Configuration(activity.resources.configuration)
                try {
                    val large = Configuration(original).apply {
                        fontScale = 2f
                        setLayoutDirection(java.util.Locale.forLanguageTag("ar"))
                    }
                    activity.resources.updateConfiguration(large, activity.resources.displayMetrics)
                    val dialog = showReferralBundlePopup(activity, "INVITE", emptySet()) { }
                    try {
                        val decor = dialog.window!!.decorView
                        val density = activity.resources.displayMetrics.density
                        val width = (320 * density).toInt()
                        val height = (480 * density).toInt()
                        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
                        decor.layout(0, 0, width, decor.measuredHeight)
                        val views = descendants(decor)
                        val close = views.single { it.contentDescription == activity.getString(R.string.friendship_close) }
                        assertTrue(close.isClickable)
                        assertTrue(close.measuredWidth >= (48 * density).toInt())
                        assertTrue(close.measuredHeight >= (48 * density).toInt())
                        val scroll = views.filterIsInstance<ScrollView>().single()
                        assertTrue(scroll.measuredHeight <= height)
                        assertTrue(scroll.getChildAt(0).measuredHeight > scroll.measuredHeight)
                        // One shared plywood surface; scrolling content and reward
                        // artwork stay transparent over the rounded background.
                        val space = views.filterIsInstance<HomeSpaceBackgroundView>()
                        assertTrue(space.isEmpty())
                        val surface = scroll.parent as FrameLayout
                        assertTrue(surface.clipToOutline)
                        assertEquals(scroll.measuredHeight, surface.measuredHeight)
                        assertTrue(surface.background is PlywoodTextureDrawable)
                        assertNull(scroll.background)
                        assertNull(scroll.getChildAt(0).background)
                        assertNull(views.filterIsInstance<FriendshipBundleView>().single().background)
                    } finally { dialog.dismiss() }
                } finally { activity.resources.updateConfiguration(original, activity.resources.displayMetrics) }
            }
        }
    }

    @Test fun defaultFontPopupWrapsContentAndDoesNotStretchToAvailableScreenHeight() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = showReferralBundlePopup(activity, "INVITE", emptySet()) { }
                try {
                    val scroll = descendants(dialog.window!!.decorView).filterIsInstance<ScrollView>().single()
                    val surface = scroll.parent as FrameLayout
                    val density = activity.resources.displayMetrics.density
                    val width = (360f * density).toInt()
                    // A deliberately oversized available height exposes the
                    // previous full-height background expanding the entire popup.
                    val availableHeight = (1600f * density).toInt()
                    surface.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST),
                    )
                    assertEquals(scroll.measuredHeight, surface.measuredHeight)
                    assertTrue(surface.background is PlywoodTextureDrawable)
                    assertTrue(surface.measuredHeight < availableHeight)
                    assertEquals(Gravity.CENTER, dialog.window!!.attributes.gravity)
                } finally {
                    dialog.dismiss()
                }
            }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
