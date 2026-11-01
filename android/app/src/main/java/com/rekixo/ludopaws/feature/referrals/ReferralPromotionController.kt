package com.rekixo.ludopaws.feature.referrals

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Button
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import android.widget.Toast
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.LudoProofApplication
import com.rekixo.ludopaws.feature.safety.FamilyAgeScreen
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import org.json.JSONObject
import java.util.WeakHashMap

/** Explicit Home/Store entry points only. Never enrolls, verifies or grants rewards. */
internal class ReferralPromotionController(
    private val activity: Activity,
    private val uiAvailable: () -> Boolean,
    private val popupVisibility: (Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val preferences = activity.getSharedPreferences("referral_popup_v1", Context.MODE_PRIVATE)
    private val repository = ReferralRepository(activity)
    private val inventory = CosmeticInventoryStore(activity)
    private val entries = WeakHashMap<FrameLayout, String>()
    private var resumed = false
    private var generation = 0
    private var opening = false
    private var popup: Dialog? = null
    private var snapshotCache: JSONObject? = null
    private val automatic = Runnable { requestPopup(automatic = true) }

    fun resume(home: Boolean = false) {
        resumed = true; generation++
        entries.keys.toList().forEach { host -> host.post { refreshEntry(host) } }
        if (home && ReferralPopupSession.current.takeHomeOpportunity() &&
            !ReferralPopupSession.current.fullscreenRecently() && !ReferralPopupSession.current.automaticShown) {
            handler.postDelayed(automatic, 3000L)
        }
    }

    fun pause() {
        resumed = false; generation++; opening = false; snapshotCache = null
        handler.removeCallbacks(automatic)
        popup?.dismiss(); popup = null
    }

    private fun currentAccount() = (activity.application as LudoProofApplication).accountRepository.currentSession()?.accountId
    private fun allowed() = resumed && !activity.isFinishing && !activity.isDestroyed &&
        BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED && FamilyAgeScreen.optionalGoogleAllowed(activity)
    private fun eligible(snapshot: JSONObject) = allowed() && snapshot.optBoolean("enabled") &&
        snapshot.optString("campaign") == ReferralPopupPolicy.CAMPAIGN &&
        snapshot.optString("localAccountId").isNotBlank() && snapshot.optString("localAccountId") == currentAccount() &&
        snapshot.optString("state") in setOf("INVITE", "FRIEND_JOINED", "VERIFYING")

    private fun fetch(fresh: Boolean, complete: (JSONObject?) -> Unit) {
        if (!allowed()) { complete(null); return }
        if (!fresh) snapshotCache?.takeIf { eligible(it) }?.let { complete(it); return }
        val requestGeneration = generation
        ReferralRepository.executor.execute {
            val result = runCatching { repository.status() }.getOrNull()
            handler.post {
                if (!resumed || generation != requestGeneration || activity.isFinishing || activity.isDestroyed) return@post
                snapshotCache = result
                complete(result)
            }
        }
    }

    private fun history(key: String) = ReferralPopupHistory(
        preferences.getInt(key + "count", 0), preferences.getLong(key + "shown", 0), preferences.getLong(key + "dismissed", 0))

    private fun requestPopup(automatic: Boolean) {
        if (!allowed() || opening || popup?.isShowing == true || !activity.hasWindowFocus() || !uiAvailable() ||
            ReferralPopupSession.current.fullscreenRecently()) return
        opening = true
        fetch(fresh = true) { snapshot ->
            opening = false
            if (snapshot == null || !eligible(snapshot) || !activity.hasWindowFocus() || !uiAvailable() ||
                ReferralPopupSession.current.fullscreenRecently()) {
                if (!automatic && resumed && activity.hasWindowFocus()) Toast.makeText(activity, "Friendship offer is unavailable right now. Check Share for progress.", Toast.LENGTH_SHORT).show()
                return@fetch
            }
            val account = snapshot.optString("localAccountId")
            val key = "${ReferralPopupPolicy.CAMPAIGN}:$account:"
            val previous = history(key)
            val now = System.currentTimeMillis()
            if (automatic && !ReferralPopupPolicy.canShowAutomatically(true, snapshot.optString("state"), previous, now,
                    ReferralPopupSession.current.automaticShown)) return@fetch
            popupVisibility(true)
            try {
                popup = showReferralBundlePopup(activity, snapshot.optString("state"), inventory.ownedCosmeticIds()) { deferred ->
                    popup = null
                    if (deferred && resumed && currentAccount() == account) preferences.edit().putLong(key + "dismissed", System.currentTimeMillis()).apply()
                    popupVisibility(false)
                }
                if (automatic) {
                    preferences.edit().putInt(key + "count", previous.impressions + 1).putLong(key + "shown", now).apply()
                    ReferralPopupSession.current.markAutomaticShown()
                }
            } catch (_: android.view.WindowManager.BadTokenException) {
                popup = null; popupVisibility(false)
            }
        }
    }

    fun storeEntry(cosmeticId: String): FrameLayout = FrameLayout(activity).apply {
        visibility = View.GONE
        entries[this] = cosmeticId
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { post { refreshEntry(this@apply) } }
            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }

    private fun refreshEntry(host: FrameLayout) {
        val cosmeticId = entries[host] ?: return
        if (!host.isAttachedToWindow || !allowed() || cosmeticId in inventory.ownedCosmeticIds()) { host.visibility = View.GONE; return }
        fetch(fresh = false) { snapshot ->
            if (!host.isAttachedToWindow) return@fetch
            host.removeAllViews()
            if (snapshot == null || !eligible(snapshot) || cosmeticId in inventory.ownedCosmeticIds()) { host.visibility = View.GONE; return@fetch }
            // One compact referral ribbon; the detailed 3-item artwork and
            // eligibility explanation remain in the existing referral popup.
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = recessedPlywoodBackground(activity, outerCornerRadiusDp = 14f)
                addView(android.widget.ImageView(activity).apply {
                    setImageResource(com.rekixo.ludopaws.R.drawable.referral_gem_bucket_3d)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(44), dp(48)))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(7), 0, dp(3), 0)
                    addView(TextView(activity).apply {
                        text = "Friendship Rewards"
                        textSize = 13.5f
                        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                        setTextColor(0xFF47301D.toInt())
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    addView(TextView(activity).apply {
                        text = "100 Gems + 3 items"
                        textSize = 11.5f
                        setTextColor(0xFF5E432D.toInt())
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    addView(TextView(activity).apply {
                        text = "After verified play"
                        textSize = 10.5f
                        setTextColor(0xFF72553B.toInt())
                        maxLines = 1
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(Button(activity).apply {
                    text = if (snapshot.optString("state") == "INVITE") "Invite ›" else "Progress ›"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    isAllCaps = false
                    minHeight = dp(44)
                    minimumHeight = dp(44)
                    setPadding(dp(3), 0, dp(3), 0)
                    backgroundTintList = null
                    setTextColor(0xFFFFFFFF.toInt())
                    background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0xFF80CD36.toInt(), 0xFF2D821C.toInt())).apply {
                        cornerRadius = dp(13).toFloat()
                    }
                    contentDescription = "View friendship rewards, invite requirements and verified progress"
                    setOnClickListener { requestPopup(automatic = false) }
                }, LinearLayout.LayoutParams(dp(84), dp(44)).apply { marginStart = dp(4) })
                contentDescription = "Friendship rewards. Earn 100 Gems, a board, a character and dice after a qualifying friend's verified match."
            }
            host.addView(card, FrameLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            host.visibility = View.VISIBLE
        }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
}
