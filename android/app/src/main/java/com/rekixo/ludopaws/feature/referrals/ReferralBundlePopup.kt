package com.rekixo.ludopaws.feature.referrals

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.core.ui.components.appIconActionButton
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.ui.dialogs.baseDialog

internal fun showReferralBundlePopup(
    activity: Activity, state: String, linked: Boolean, owned: Set<String>,
    onDismiss: (deferred: Boolean) -> Unit,
): Dialog {
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
    fun copy(value: String, size: Float, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size; gravity = Gravity.CENTER
        includeFontPadding = false; setTextColor(if (bold) 0xFFFFE8B7.toInt() else 0xFFCADFD6.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(6), 0, dp(6))
    }
    val dialog = baseDialog(activity)
    var deferred = true
    val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(12))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF214C40.toInt(), 0xFF122D35.toInt())).apply {
            cornerRadius = dp(26).toFloat(); setStroke(dp(1), 0xFF86B493.toInt())
        }
    }
    val heading = FrameLayout(activity)
    val title = when (state) {
        "FRIEND_JOINED" -> "Your friend joined!"
        "VERIFYING" -> "Reward on the way"
        else -> if (owned.containsAll(ReferralPopupPolicy.rewardItems)) "100 Gems for friendship" else "Better with a friend"
    }
    heading.addView(copy(title, 19f, true), FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
        marginStart = dp(42); marginEnd = dp(42)
    })
    heading.addView(appIconActionButton(activity, "Close friendship offer", AppIconName.CLOSE, sizeDp = 44) { dialog.dismiss() },
        FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END or Gravity.TOP))
    panel.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val art = FriendshipBundleView(activity, ownedItems = owned)
    body.addView(art, LinearLayout.LayoutParams(-1, -2))
    body.addView(copy(when (state) {
        "FRIEND_JOINED" -> "Your friend is connected. Their qualifying match is the next step; they don't have to win."
        "VERIFYING" -> "Your match was verified. Your reward is being delivered; open progress for the latest status."
        else -> if (owned.containsAll(ReferralPopupPolicy.rewardItems))
            "You already own all three items. A new friend’s verified play can still earn you 100 Gems, once this campaign."
        else "A new friend installs through your invitation and finishes a qualifying match. Only you earn the bundle + 100 Gems."
    }, 13f))
    body.addView(copy("Sharing or installing alone earns no reward.", 11f))
    val scroll = object : ScrollView(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = (resources.displayMetrics.heightPixels * .82f).toInt() - dp(190)
            val limit = available.coerceAtLeast(dp(100))
            val height = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) limit
                else minOf(View.MeasureSpec.getSize(heightMeasureSpec), limit)
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                height, View.MeasureSpec.AT_MOST))
        }
    }.apply { addView(body); isVerticalScrollBarEnabled = false }
    panel.addView(scroll, LinearLayout.LayoutParams(-1, -2))
    val invite = Button(activity).apply {
        text = when { state != "INVITE" -> "View progress"; linked -> "Invite a friend"; else -> "Connect Google & invite" }
        textSize = 15f; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(52); setTextColor(0xFF183F36.toInt())
        background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFFDDFFE6.toInt(), 0xFF9DE2B7.toInt())
        ).apply { cornerRadius = dp(16).toFloat() }, null)
        setOnClickListener {
            deferred = false; dialog.dismiss()
            activity.startActivity(Intent(activity, ReferralActivity::class.java))
        }
    }
    panel.addView(invite, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    val footer = LinearLayout(activity)
    for (label in listOf("Later", "Rules & privacy")) footer.addView(Button(activity).apply {
        text = label; textSize = 12f; isAllCaps = false; minHeight = dp(48); minimumWidth = 0
        setTextColor(0xFFCADFD6.toInt())
        background = RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null, null)
        setOnClickListener {
            if (label == "Later") dialog.dismiss() else {
                deferred = false; dialog.dismiss()
                activity.startActivity(Intent(activity, ReferralActivity::class.java).putExtra(ReferralActivity.EXTRA_SHOW_RULES, true))
            }
        }
    }, LinearLayout.LayoutParams(0, -2, 1f))
    panel.addView(footer)
    dialog.setContentView(panel)
    dialog.setOnShowListener {
        dialog.window?.apply {
            setGravity(Gravity.CENTER)
            setLayout(minOf(dp(380), (activity.resources.displayMetrics.widthPixels * .9f).toInt()), -2)
        }
        art.setActive(true)
        if (ValueAnimator.areAnimatorsEnabled()) {
            panel.alpha = 0f; panel.scaleX = .96f; panel.scaleY = .96f
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220L).start()
        }
    }
    dialog.setOnDismissListener { panel.animate().cancel(); art.setActive(false); onDismiss(deferred) }
    dialog.show()
    return dialog
}
