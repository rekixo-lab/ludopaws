package com.rekixo.ludopaws.feature.safety

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import com.rekixo.ludopaws.BuildConfig

/** Local nicknames stay on the device; the server assigns public animal names. */
object FamilyOnlineSafety {
    fun publicName(localName: String): String = if (BuildConfig.LUDOPAWS_FAMILY_MODE) "Player" else localName

    fun showNoticeIfNeeded(activity: Activity, onContinue: () -> Unit): Boolean {
        if (!BuildConfig.LUDOPAWS_FAMILY_MODE) return false
        val prefs = activity.getSharedPreferences("ludopaws_family_notice_v1", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seen", false)) return false
        AlertDialog.Builder(activity)
            .setTitle("Play safely online")
            .setMessage("Online play connects to the game server. Players use automatic animal names and fixed reactions. Free-text chat, voice, ads and purchases are unavailable.\n\nKeep your account and deletion codes private. You can block or report players in Friends and turn off quick-chat in Settings. Your local nickname stays on this device.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CONTINUE") { _, _ ->
                prefs.edit().putBoolean("seen", true).apply()
                onContinue()
            }
            .show()
        return true
    }
}
