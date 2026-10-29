package com.rekixo.ludopaws.feature.referrals

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.core.ui.components.appIconActionButton
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.ui.dialogs.baseDialog
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.launchPlayerScreen

internal fun showReferralBundlePopup(
    activity: Activity, state: String, owned: Set<String>,
    onDismiss: (deferred: Boolean) -> Unit,
): Dialog {
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
    fun string(id: Int) = activity.getString(id)
    fun copy(value: String, size: Float, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size; gravity = Gravity.CENTER
        includeFontPadding = false; setTextColor(if (bold) 0xFF422B1C.toInt() else 0xFF60452D.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(6), 0, dp(6))
    }
    val dialog = baseDialog(activity)
    var deferred = true
    val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(12))
        background = null // Shared plywood is on the outer clipped surface.
    }
    val heading = FrameLayout(activity)
    val title = when (state) {
        "FRIEND_JOINED" -> string(R.string.friendship_joined_title)
        "VERIFYING" -> string(R.string.friendship_pending_title)
        else -> string(if (owned.containsAll(ReferralPopupPolicy.rewardItems)) R.string.friendship_gems_title else R.string.friendship_invite_title)
    }
    heading.addView(copy(title, 19f, true).apply { isAccessibilityHeading = true }, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
        marginStart = dp(48); marginEnd = dp(48)
    })
    heading.addView(appIconActionButton(activity, string(R.string.friendship_close), AppIconName.CLOSE, sizeDp = 48) { dialog.dismiss() },
        FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.TOP))
    panel.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val art = FriendshipBundleView(activity, ownedItems = owned).apply {
        // Use the popup's continuous plywood surface behind the bundle.
        background = null
    }
    body.addView(art, LinearLayout.LayoutParams(-1, -2))
    body.addView(copy(when (state) {
        "FRIEND_JOINED" -> string(R.string.friendship_joined_body)
        "VERIFYING" -> string(R.string.friendship_pending_body)
        else -> if (owned.containsAll(ReferralPopupPolicy.rewardItems))
            string(R.string.friendship_owned_body)
        else string(R.string.friendship_invite_body)
    }, 13f))
    body.addView(copy(string(R.string.friendship_no_install_reward), 12f))
    panel.addView(body, LinearLayout.LayoutParams(-1, -2))
    val scroll = object : ScrollView(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = (resources.displayMetrics.heightPixels * .82f).toInt()
            val limit = available.coerceAtLeast(dp(100))
            val height = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) limit
                else minOf(View.MeasureSpec.getSize(heightMeasureSpec), limit)
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                height, View.MeasureSpec.AT_MOST))
        }
    }.apply {
        background = null
        addView(panel)
        isVerticalScrollBarEnabled = false
    }
    val invite = Button(activity).apply {
        text = string(if (state != "INVITE") R.string.friendship_progress else R.string.friendship_invite_action)
        textSize = 15f; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD; backgroundTintList = null
        minHeight = dp(52); setTextColor(0xFF183F36.toInt())
        background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFFDDFFE6.toInt(), 0xFF9DE2B7.toInt())
        ).apply { cornerRadius = dp(16).toFloat() }, null)
        setOnClickListener {
            deferred = false; dialog.dismiss()
            activity.launchPlayerScreen(Intent(activity, ReferralActivity::class.java))
        }
    }
    panel.addView(invite, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    val footer = LinearLayout(activity)
    for (label in listOf(R.string.friendship_later, R.string.friendship_rules)) footer.addView(Button(activity).apply {
        text = string(label); textSize = 12f; isAllCaps = false; minHeight = dp(48); minimumWidth = 0; backgroundTintList = null
        setTextColor(0xFF60452D.toInt())
        background = RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null, null)
        setOnClickListener {
            if (label == R.string.friendship_later) dialog.dismiss() else {
                deferred = false; dialog.dismiss()
                activity.launchPlayerScreen(Intent(activity, ReferralActivity::class.java).putExtra(ReferralActivity.EXTRA_SHOW_RULES, true))
            }
        }
    }, LinearLayout.LayoutParams(0, -2, 1f))
    panel.addView(footer)

    val surface = object : FrameLayout(activity) {
        // A MATCH_PARENT background child must not determine dialog height.
        // Measure the actual scroll content first, then size the panel
        // to exactly that height. Tall content is still capped by ScrollView.
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxHeight = when (View.MeasureSpec.getMode(heightMeasureSpec)) {
                View.MeasureSpec.UNSPECIFIED ->
                    (resources.displayMetrics.heightPixels * .82f).toInt()
                else -> View.MeasureSpec.getSize(heightMeasureSpec)
            }.coerceAtLeast(0)
            scroll.measure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
            )
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(scroll.measuredHeight, View.MeasureSpec.EXACTLY),
            )
        }
    }.apply {
        background = plywoodBackground(activity, cornerRadiusDp = 26f)
        // Clip the scrolling contents to the same game-themed rounded panel.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width <= 0 || view.height <= 0) {
                    outline.setEmpty()
                } else {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(26).toFloat())
                }
            }
        }
        clipToOutline = true
        addView(scroll, FrameLayout.LayoutParams(-1, -2))
    }
    dialog.setContentView(surface)
    dialog.setOnShowListener {
        dialog.window?.apply {
            setGravity(Gravity.CENTER)
            setLayout(minOf(dp(380), (activity.resources.displayMetrics.widthPixels * .9f).toInt()), -2)
        }
        art.setActive(true)
        if (ValueAnimator.areAnimatorsEnabled()) {
            surface.alpha = 0f; surface.scaleX = .96f; surface.scaleY = .96f
            surface.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220L).start()
        }
    }
    dialog.setOnDismissListener {
        surface.animate().cancel()
        art.setActive(false)
        onDismiss(deferred)
    }
    dialog.show()
    return dialog
}
