package com.rekixo.ludopaws.feature.safety

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.transparentDarkBlueStrokeBackground
import com.rekixo.ludopaws.ui.home.RatingSpectrumDrawable
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
            setPadding(dp(if (compact) 12 else 16), dp(12), dp(if (compact) 12 else 16), dp(9))
            background = plywoodBackground(activity, cornerRadiusDp = 22f)
            elevation = dp(8).toFloat()
        }

        // Compact rating-style title: no tick badge, no close circle.
        panel.addView(
            TextView(activity).apply {
                text = FamilyOnlineSafetyNoticeCopy.TITLE
                textSize = if (compact) 20f else 22f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFF61330F.toInt())
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
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
            ).apply { topMargin = dp(4) },
        )

        panel.addView(
            TextView(activity).apply {
                text = FamilyOnlineSafetyNoticeCopy.INTRO
                textSize = if (compact) 11.5f else 12.5f
                setTextColor(0xFF624224.toInt())
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(5) },
        )

        // One unified, gap-free section. Identical navy fill and blue stroke
        // to the Rating Popup's five-star strip and Profile's Classic row.
        val safetyContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = transparentDarkBlueStrokeBackground(
                context = activity,
                cornerRadiusDp = 14f,
            )
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }

        fun addTip(number: String, title: String, details: String) {
            if (safetyContent.childCount > 0) {
                safetyContent.addView(
                    View(activity).apply {
                        setBackgroundColor(0x663DA2E6)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1),
                    ).apply {
                        marginStart = dp(6)
                        marginEnd = dp(6)
                    },
                )
            }
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(if (compact) 5 else 7), dp(5), dp(if (compact) 5 else 7))
            }
            row.addView(
                TextView(activity).apply {
                    text = number
                    textSize = if (compact) 14f else 15f
                    gravity = Gravity.CENTER
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFFFFDA70.toInt())
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(7)
                },
            )
            row.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(activity).apply {
                            text = title
                            textSize = if (compact) 12.5f else 13.5f
                            setTypeface(Typeface.DEFAULT_BOLD)
                            setTextColor(0xFFFFE3A2.toInt())
                            includeFontPadding = false
                        },
                    )
                    addView(
                        TextView(activity).apply {
                            text = details
                            textSize = if (compact) 11.3f else 12f
                            setTextColor(0xFFF0F7FF.toInt())
                            includeFontPadding = false
                            setLineSpacing(0f, 1.02f)
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(2) },
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            safetyContent.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        addTip("01", FamilyOnlineSafetyNoticeCopy.NAME_TITLE, FamilyOnlineSafetyNoticeCopy.NAME_BODY)
        addTip("02", FamilyOnlineSafetyNoticeCopy.CHAT_TITLE, FamilyOnlineSafetyNoticeCopy.CHAT_BODY)
        addTip("03", FamilyOnlineSafetyNoticeCopy.PRIVACY_TITLE, FamilyOnlineSafetyNoticeCopy.PRIVACY_BODY)

        panel.addView(
            safetyContent,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
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
            ).apply { topMargin = dp(5) },
        )

        var submitted = false
        val spectrum = RatingSpectrumDrawable(activity)
        var shimmerAnimator: ValueAnimator? = null
        val continueButton = TextView(activity).apply {
            text = "CONTINUE  →"
            textSize = if (compact) 15.5f else 16.5f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = spectrum
            elevation = dp(6).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = "Continue to online play"
            setOnClickListener {
                if (!submitted) {
                    submitted = true
                    // Only Continue acknowledges the first-run safety notice.
                    prefs.edit().putBoolean("seen", true).apply()
                    dialog.dismiss()
                    if (!activity.isFinishing && !activity.isDestroyed) onContinue()
                }
            }
        }
        panel.addView(
            continueButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(if (compact) 49 else 54),
            ).apply { topMargin = dp(9) },
        )

        panel.addView(
            TextView(activity).apply {
                text = "Cancel"
                textSize = if (compact) 13f else 14f
                setTextColor(0xFF66401E.toInt())
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "Cancel and return to Home"
                setOnClickListener { dialog.dismiss() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42),
            ),
        )

        dialog.setOnDismissListener {
            shimmerAnimator?.cancel()
            panel.animate().cancel()
        }
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
            shimmerAnimator = ValueAnimator.ofFloat(-.35f, 1.35f).apply {
                duration = 2750L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    spectrum.shimmerPosition = it.animatedValue as Float
                }
                start()
            }
        }
        return true
    }
}
