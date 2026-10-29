package com.rekixo.ludopaws

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import java.util.WeakHashMap

/** Suppress duplicate navigation while Android is opening the next screen. */
internal object PlayerNavigation {
    private val launches = WeakHashMap<Activity, Long>()
    fun resumed(activity: Activity) { launches.remove(activity) }
    fun destroyed(activity: Activity) { launches.remove(activity) }
    fun launch(activity: Activity, intent: Intent): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val now = SystemClock.elapsedRealtime()
        if (launches[activity]?.let { now - it < 1_000L } == true) return false
        launches[activity] = now
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            launches.remove(activity)
            Toast.makeText(activity, "This screen could not be opened. Please try again.", Toast.LENGTH_SHORT).show()
            false
        } catch (_: SecurityException) {
            launches.remove(activity)
            Toast.makeText(activity, "This screen is unavailable on this device.", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

internal fun Activity.launchPlayerScreen(intent: Intent): Boolean = PlayerNavigation.launch(this, intent)
