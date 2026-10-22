package com.rekixo.ludopaws.feature.safety

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.core.ui.components.PlywoodCloseButton
import com.rekixo.ludopaws.ui.dialogs.baseDialog
import com.rekixo.ludopaws.ui.dialogs.sizeDialog

/**
 * Brief, age-inclusive instructions for the one-time family online-play notice.
 * Store purchases/ads are intentionally not described as disabled: those
 * features are independent of the online chat restrictions.
 */
internal object FamilyOnlineSafetyNoticeCopy {
    const val TITLE = "PLAY SAFELY ONLINE"
    const val SUBTITLE = "A few tips before your first online game"
    const val INTRO = "Online games connect through the Ludo Paws game server. Here's how we keep play simple and safe."
    const val NAME_TITLE = "Friendly public names"
    const val NAME_BODY = "Other players see an automatic animal name. Your own nickname stays on this device."
    const val CHAT_TITLE = "Preset messages only"
    const val CHAT_BODY = "Players can use fixed quick-chat and reactions. Free-text chat and voice chat are not available."
    const val PRIVACY_TITLE = "Stay in control"
    const val PRIVACY_BODY = "Keep account and deletion codes private. Block or report players in Friends, or turn off quick-chat in Settings."
    const val FOOTER = "Continue to the online mode you selected."
}

/** Local nicknames stay on the device; the server assigns public animal names. */
object FamilyOnlineSafety {
    fun publicName(localName: String): String =
        if (BuildConfig.LUDOPAWS_FAMILY_MODE) "Player" else localName

    fun showNoticeIfNeeded(activity: Activity, onContinue: () -> Unit): Boolean {
        if (!BuildConfig.LUDOPAWS_FAMILY_MODE) return false
        val prefs = activity.getSharedPreferences("ludopaws_family_notice_v1", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seen", false)) return false
        if (activity.isFinishing || activity.isDestroyed) return true

        val dialog = baseDialog(activity)
        val compact = LudoProofTheme.isCompactWidth(activity)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        val animationsEnabled =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (compact) 13 else 18), dp(12), dp(if (compact) 13 else 18), dp(14))
            background = plywoodBackground(activity, cornerRadiusDp = 22f)
            elevation = dp(8).toFloat()
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(activity).apply {
                text = "✓"
                textSize = 25f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(0xFF128D48.toInt())
                setTypeface(Typeface.DEFAULT_BOLD)
                background = recessedPlywoodBackground(activity, outerCornerRadiusDp = 20f)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(9) },
        )
        header.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    TextView(activity).apply {
                        text = FamilyOnlineSafetyNoticeCopy.TITLE
                        textSize = if (compact) 17.5f else 20f
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextColor(0xFF61330F.toInt())
                        maxLines = 2
                    },
                )
                addView(
                    TextView(activity).apply {
                        text = "FRIENDLY • PRIVATE • FAIR"
                        textSize = 9.5f
                        letterSpacing = .065f
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextColor(0xFF79502A.toInt())
                    },
                )
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            PlywoodCloseButton(activity, "Cancel online safety notice") {
                dialog.dismiss()
            },
            LinearLayout.LayoutParams(dp(42), dp(42)),
        )
        panel.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        panel.addView(
            TextView(activity).apply {
                text = FamilyOnlineSafetyNoticeCopy.SUBTITLE
                textSize = if (compact) 14f else 15f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFF533316.toInt())
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(9) },
        )

        // The safety text scrolls on small devices; the two action buttons never
        // scroll out of reach, even at larger accessibility font sizes.
        val scroll = object : ScrollView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val cap = minOf(dp(342), (resources.displayMetrics.heightPixels * .48f).toInt())
                super.onMeasure(
                    widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST),
                )
            }
        }.apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            verticalScrollBarEnabled = false
        }
        val safetyContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(1), dp(4), dp(1), dp(4))
        }
        safetyContent.addView(
            TextView(activity).apply {
                text = FamilyOnlineSafetyNoticeCopy.INTRO
                textSize = if (compact) 12.5f else 13f
                setTextColor(0xFF624224.toInt())
                setLineSpacing(dp(2).toFloat(), 1.06f)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(7) },
        )

        fun addTip(number: String, title: String, details: String) {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(9), dp(10), dp(9))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF154B86.toInt(), 0xFF0B2D61.toInt(), 0xFF091D49.toInt()),
                ).apply {
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), 0xFF399DE1.toInt())
                }
            }
            card.addView(
                TextView(activity).apply {
                    text = number
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFFFFE8AB.toInt())
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(0xFF0B568E.toInt())
                        setStroke(dp(1), 0xFF6AD6FF.toInt())
                    }
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(9) },
            )
            card.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(activity).apply {
                            text = title
                            textSize = if (compact) 13f else 14f
                            setTypeface(Typeface.DEFAULT_BOLD)
                            setTextColor(0xFFFFE3A2.toInt())
                        },
                    )
                    addView(
                        TextView(activity).apply {
                            text = details
                            textSize = if (compact) 11.8f else 12.4f
                            setTextColor(0xFFF0F7FF.toInt())
                            setLineSpacing(dp(1).toFloat(), 1.04f)
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(3) },
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            safetyContent.addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(5) },
            )
        }
        addTip("01", FamilyOnlineSafetyNoticeCopy.NAME_TITLE, FamilyOnlineSafetyNoticeCopy.NAME_BODY)
        addTip("02", FamilyOnlineSafetyNoticeCopy.CHAT_TITLE, FamilyOnlineSafetyNoticeCopy.CHAT_BODY)
        addTip("03", FamilyOnlineSafetyNoticeCopy.PRIVACY_TITLE, FamilyOnlineSafetyNoticeCopy.PRIVACY_BODY)
        scroll.addView(safetyContent)
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(7) },
        )

        panel.addView(
            TextView(activity).apply {
                text = FamilyOnlineSafetyNoticeCopy.FOOTER
                textSize = if (compact) 11f else 12f
                setTextColor(0xFF6C4827.toInt())
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )

        fun buttonSkin(pressed: Boolean, primary: Boolean): GradientDrawable {
            val colors = when {
                primary && pressed -> intArrayOf(0xFFFFC95A.toInt(), 0xFFF7A424.toInt())
                primary -> intArrayOf(0xFFFFF1A6.toInt(), 0xFFFFC849.toInt(), 0xFFF2A92D.toInt())
                pressed -> intArrayOf(0xFF1C5599.toInt(), 0xFF0A2B60.toInt())
                else -> intArrayOf(0xFF163E78.toInt(), 0xFF081F50.toInt())
            }
            return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(if (primary) 2 else 1), if (primary) 0xFFFFE9A1.toInt() else 0xFF3F92DB.toInt())
            }
        }
        fun actionButton(label: String, primary: Boolean, onClick: () -> Unit): Button =
            Button(activity).apply {
                text = label
                textSize = if (compact) 14.5f else 15.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(if (primary) 0xFF562500.toInt() else Color.WHITE)
                gravity = Gravity.CENTER
                minWidth = 0
                minHeight = 0
                isAllCaps = false
                setPadding(dp(7), 0, dp(7), 0)
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), buttonSkin(true, primary))
                    addState(intArrayOf(), buttonSkin(false, primary))
                }
                elevation = dp(if (primary) 5 else 2).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }

        var submitted = false
        val cancel = actionButton("CANCEL", primary = false) { dialog.dismiss() }
        val continueButton = actionButton("CONTINUE  →", primary = true) {
            if (!submitted) {
                submitted = true
                // Only Continue acknowledges this first-run notice.
                prefs.edit().putBoolean("seen", true).apply()
                dialog.dismiss()
                if (!activity.isFinishing && !activity.isDestroyed) onContinue()
            }
        }
        continueButton.contentDescription = "Continue to online play"
        cancel.contentDescription = "Cancel and return to Home"

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(
            cancel,
            LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(6) },
        )
        actions.addView(
            continueButton,
            LinearLayout.LayoutParams(0, dp(54), 1.65f).apply { marginStart = dp(6) },
        )
        panel.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(11) },
        )

        dialog.setOnDismissListener { panel.animate().cancel() }
        dialog.setContentView(panel)
        sizeDialog(dialog, if (compact) .94f else .88f)
        if (animationsEnabled) {
            panel.alpha = 0f
            panel.scaleX = .94f
            panel.scaleY = .94f
            panel.translationY = dp(12).toFloat()
        }
        dialog.show()
        if (animationsEnabled) {
            panel.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(340L)
                .setInterpolator(OvershootInterpolator(.7f))
                .start()
        }
        return true
    }
}
