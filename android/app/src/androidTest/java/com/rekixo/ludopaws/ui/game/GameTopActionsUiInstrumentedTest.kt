package com.rekixo.ludopaws.ui.game

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import com.rekixo.ludopaws.MainActivity
import com.rekixo.ludopaws.OfflineGameActivity
import com.rekixo.ludopaws.HomeActivity
import com.rekixo.ludopaws.feature.friends.presentation.FriendsActivity
import com.rekixo.ludopaws.feature.leaderboard.presentation.LeaderboardActivity
import com.rekixo.ludopaws.feature.profile.presentation.BadgesActivity
import com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.team.presentation.TeamUpActivity
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.core.ui.icons.AppIconView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class GameTopActionsUiInstrumentedTest {
    @Test
    fun topActionsUseSharedSemanticIconsAndCommonChrome() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val host = FrameLayout(context)
            var backClicks = 0
            var settingsClicks = 0

            val bar = addGameTopActions(
                host = host,
                context = context,
                onBack = { backClicks += 1 },
                onSettings = { settingsClicks += 1 },
            )

            assertEquals(2, bar.childCount)
            val backButton = bar.getChildAt(0) as FrameLayout
            val settingsButton = bar.getChildAt(1) as FrameLayout

            assertEquals("Back", backButton.contentDescription)
            assertEquals("Game settings", settingsButton.contentDescription)
            assertTrue(backButton.isClickable)
            assertTrue(settingsButton.isClickable)
            assertTrue(backButton.isFocusable)
            assertTrue(settingsButton.isFocusable)
            assertNotNull(backButton.background)
            assertNotNull(settingsButton.background)
            assertEquals("PlywoodTextureDrawable", backButton.background.javaClass.simpleName)
            assertEquals("PlywoodTextureDrawable", settingsButton.background.javaClass.simpleName)
            assertNotNull(backButton.foreground)
            assertNotNull(settingsButton.foreground)
            assertEquals(AppIconName.CHEVRON_LEFT, (backButton.getChildAt(0) as AppIconView).iconName)
            assertEquals(AppIconName.SETTINGS, (settingsButton.getChildAt(0) as AppIconView).iconName)

            backButton.performClick()
            settingsButton.performClick()
            assertEquals(1, backClicks)
            assertEquals(1, settingsClicks)
        }
    }
    @Suppress("DEPRECATION")
    @Test
    fun everyAppScreenIsPortraitOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val activities = listOf(
            HomeActivity::class.java,
            OfflineGameActivity::class.java,
            MainActivity::class.java,
            TeamUpActivity::class.java,
            FriendsActivity::class.java,
            LeaderboardActivity::class.java,
            ProfileActivity::class.java,
            BadgesActivity::class.java,
            StoreActivity::class.java,
        )

        activities.forEach { activityClass ->
            val info = packageManager.getActivityInfo(
                ComponentName(context, activityClass),
                0,
            )
            assertEquals(
                "${activityClass.simpleName} must stay portrait-only",
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                info.screenOrientation,
            )
        }
    }

}
