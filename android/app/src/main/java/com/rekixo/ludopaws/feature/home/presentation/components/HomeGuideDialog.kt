package com.rekixo.ludopaws.ui.home

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.ui.dialogs.baseDialog
import com.rekixo.ludopaws.ui.dialogs.settingsDp
import com.rekixo.ludopaws.ui.dialogs.sizeDialog

/** Help is opened explicitly from Settings; never shown automatically on Home. */
internal fun showLudoPawsGuide(
    activity: Activity,
    onTryRobot: (() -> Unit)? = null,
    onDismissed: (() -> Unit)? = null,
) {
    if (activity.isFinishing || activity.isDestroyed) return

    // Match the Privacy Policy dialog: navy gradient, rounded corners,
    // 2dp gold stroke, gold header and readable white scrollable body.
    val dialog = baseDialog(activity)
    dialog.setOnDismissListener { onDismissed?.invoke() }

    val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            settingsDp(activity, 16),
            settingsDp(activity, 14),
            settingsDp(activity, 16),
            settingsDp(activity, 14),
        )
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF08378F.toInt(), 0xFF031B50.toInt()),
        ).apply {
            cornerRadius = settingsDp(activity, 18).toFloat()
            setStroke(settingsDp(activity, 2), 0xFFFFC735.toInt())
        }
        addView(
            TextView(activity).apply {
                text = "Welcome to Ludo Paws!"
                textSize = 20f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFFFFD34E.toInt())
                gravity = Gravity.CENTER
            },
        )
    }

    // Cap the scrolling text instead of letting a small phone push the
    // actions off-screen. The title and buttons remain visible when scrolling.
    val messageScroll = object : ScrollView(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxHeight = (resources.displayMetrics.heightPixels * 0.52f).toInt()
            val offeredHeight = if (
                View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED
            ) {
                maxHeight
            } else {
                minOf(View.MeasureSpec.getSize(heightMeasureSpec), maxHeight)
            }
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(offeredHeight, View.MeasureSpec.AT_MOST),
            )
        }
    }.apply {
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(
            TextView(activity).apply {
                text =
                    "1. Choose Robot to practice alone, or Pass & Play with friends on one phone.\n\n" +
                    "2. Roll the dice, choose a highlighted pawn and race all four paws home.\n\n" +
                    "3. Earn XP and badges by completing games. You can see your progress in Profile.\n\n" +
                    "4. Change sound and game speed in Settings.\n\n" +
                    "Online games use automatic animal names and fixed reactions. Free-text chat and voice are unavailable."
                textSize = 13.5f
                setTextColor(Color.WHITE)
                setLineSpacing(0f, 1.18f)
                setPadding(
                    settingsDp(activity, 4),
                    settingsDp(activity, 14),
                    settingsDp(activity, 4),
                    settingsDp(activity, 14),
                )
            },
        )
    }
    panel.addView(
        messageScroll,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    fun guideAction(label: String, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            text = label
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFFFFD34E.toInt())
            gravity = Gravity.CENTER
            minHeight = settingsDp(activity, 44)
            setPadding(
                settingsDp(activity, 8),
                settingsDp(activity, 8),
                settingsDp(activity, 8),
                settingsDp(activity, 8),
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF10458B.toInt(), 0xFF06265D.toInt()),
            ).apply {
                cornerRadius = settingsDp(activity, 10).toFloat()
                setStroke(settingsDp(activity, 1), 0xFFFFC735.toInt())
            }
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
        }

    if (onTryRobot != null) {
        panel.addView(
            guideAction("PRACTICE WITH ROBOT") {
                dialog.dismiss()
                onTryRobot()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = settingsDp(activity, 6)
            },
        )
    }

    // A single, centered bottom action: dismissing the guide is enough.
    panel.addView(
        guideAction("Close") { dialog.dismiss() },
        LinearLayout.LayoutParams(
            settingsDp(activity, 152),
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        },
    )

    dialog.setContentView(panel)
    sizeDialog(dialog, .88f)
    dialog.show()
}
