package com.rekixo.ludopaws

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Display-only result summary. This view NEVER grants XP, gems or ownership. */
data class ResultPlace(val rank: String, val name: String, val highlighted: Boolean = false)

data class ResultCardModel(
    val title: String,
    val subtitle: String,
    val placements: List<ResultPlace>,
    val xpTitle: String? = null,
    val xpDetail: String? = null,
    val xpFraction: Float? = null,
    val gemsTitle: String? = null,
    val gemsDetail: String? = null,
    val gemsCredited: Boolean = false,
    val dailyChallengeComplete: Boolean = false,
)

/**
 * Single reusable premium result layout across Robot, Pass & Play, Online,
 * Friends and Team Up. Content remains readable on narrow devices and with
 * large system fonts; actions are deliberately hosted outside the scroll area.
 */
class LudoPawsResultCard(context: Context) : LinearLayout(context) {
    private val gold = 0xFFFFD976.toInt()
    private val cream = 0xFFFFF3DB.toInt()
    private val muted = 0xFFD8C5A5.toInt()
    private val green = 0xFFB8F3AD.toInt()

    val titleView = text(26f, gold, true, Gravity.CENTER).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    val subtitleView = text(12.5f, cream, false, Gravity.CENTER).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val standings = LinearLayout(context).apply { orientation = VERTICAL }
    private val xpPanel = LinearLayout(context).apply { orientation = VERTICAL }
    private val xpHeadline = text(19f, gold, true)
    private val xpInfo = text(11.5f, muted)
    private val xpBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressTintList = ColorStateList.valueOf(0xFF64CD67.toInt())
        progressBackgroundTintList = ColorStateList.valueOf(0xFF67513C.toInt())
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val gemPanel = LinearLayout(context).apply { orientation = VERTICAL }
    private val gemHeadline = text(18f, gold, true)
    private val gemInfo = text(11.5f, muted)
    private val challenge = text(12f, green, true, Gravity.CENTER).apply {
        text = "✓ Daily Challenge complete"
        visibility = GONE
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(16), dp(16), dp(15))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF70482A.toInt(), 0xFF30271F.toInt(), 0xFF192B2D.toInt()),
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(2), 0xFFEFBC66.toInt())
        }
        elevation = dp(9).toFloat()
        addView(text(10f, muted, true, Gravity.CENTER).apply {
            text = "MATCH RESULTS"
            letterSpacing = 0.13f
        }, LayoutParams(-1, -2))
        addView(titleView, LayoutParams(-1, -2).apply { topMargin = dp(4) })
        addView(subtitleView, LayoutParams(-1, -2).apply { topMargin = dp(3) })
        addView(separator(), LayoutParams(-1, dp(1)).apply {
            topMargin = dp(12); bottomMargin = dp(9)
        })
        addView(text(11f, gold, true).apply { text = "FINAL STANDINGS" },
            LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        addView(standings, LayoutParams(-1, -2))
        addView(xpPanel, LayoutParams(-1, -2).apply { topMargin = dp(9) })
        xpPanel.addView(separator(), LayoutParams(-1, dp(1)).apply { bottomMargin = dp(8) })
        xpPanel.addView(xpHeadline, LayoutParams(-1, -2))
        xpPanel.addView(xpInfo, LayoutParams(-1, -2).apply { topMargin = dp(2) })
        xpPanel.addView(xpBar, LayoutParams(-1, dp(7)).apply { topMargin = dp(7) })
        addView(gemPanel, LayoutParams(-1, -2).apply { topMargin = dp(9) })
        gemPanel.addView(separator(), LayoutParams(-1, dp(1)).apply { bottomMargin = dp(8) })
        gemPanel.addView(gemHeadline, LayoutParams(-1, -2))
        gemPanel.addView(gemInfo, LayoutParams(-1, -2).apply { topMargin = dp(2) })
        addView(challenge, LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    fun render(model: ResultCardModel) {
        titleView.text = model.title
        subtitleView.text = model.subtitle
        standings.removeAllViews()
        model.placements.take(4).forEachIndexed { index, place ->
            standings.addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(4), dp(8), dp(4))
                minimumHeight = dp(38)
                if (place.highlighted) {
                    background = GradientDrawable().apply {
                        setColor(0x4459B87B)
                        cornerRadius = dp(10).toFloat()
                    }
                } else if (index % 2 == 0) {
                    background = GradientDrawable().apply {
                        setColor(0x1CFFFFFF)
                        cornerRadius = dp(10).toFloat()
                    }
                }
                addView(text(12f, if (index == 0) gold else muted, true).apply {
                    text = place.rank
                }, LayoutParams(dp(49), -2))
                addView(text(14f, cream, place.highlighted).apply {
                    text = place.name
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    contentDescription = place.name
                }, LayoutParams(0, -2, 1f))
                if (place.highlighted) addView(text(10f, green, true).apply {
                    text = "YOU"
                }, LayoutParams(-2, -2).apply { marginStart = dp(5) })
            }, LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        xpPanel.visibility = if (model.xpTitle == null) GONE else VISIBLE
        xpHeadline.text = model.xpTitle.orEmpty()
        xpInfo.text = model.xpDetail.orEmpty()
        xpInfo.maxLines = 3
        xpBar.visibility = if (model.xpFraction == null) GONE else VISIBLE
        xpBar.progress = ((model.xpFraction ?: 0f).coerceIn(0f, 1f) * 1000).toInt()
        gemPanel.visibility = if (model.gemsTitle == null) GONE else VISIBLE
        gemHeadline.text = model.gemsTitle.orEmpty()
        gemHeadline.setTextColor(if (model.gemsCredited) green else gold)
        gemInfo.text = model.gemsDetail.orEmpty()
        gemInfo.maxLines = 3
        challenge.visibility = if (model.dailyChallengeComplete) VISIBLE else GONE
        contentDescription = buildString {
            append(model.title).append(". ").append(model.subtitle)
            model.placements.forEach { append(". ").append(it.rank).append(" ").append(it.name) }
            model.xpTitle?.let { append(". ").append(it).append(". ").append(model.xpDetail) }
            model.gemsTitle?.let { append(". ").append(it).append(". ").append(model.gemsDetail) }
            if (model.dailyChallengeComplete) append(". Daily Challenge complete.")
        }
    }

    private fun separator(): View = View(context).apply {
        setBackgroundColor(0x66FFCC74)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun text(size: Float, color: Int, bold: Boolean = false, align: Int = Gravity.START):
        TextView = TextView(context).apply {
        textSize = size
        setTextColor(color)
        gravity = align or Gravity.CENTER_VERTICAL
        includeFontPadding = false
        if (bold) typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}

/** Restricts the scrollable result to the viewport while leaving action buttons visible. */
fun resultCardOverlay(context: Context, card: LudoPawsResultCard, reserveBottomDp: Int = 92): FrameLayout =
    FrameLayout(context).apply {
        setBackgroundColor(0xB80C151B.toInt())
        isClickable = true
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + .5f).toInt()
        val metrics = resources.displayMetrics
        val maxWidth = dp(360)
        val maxHeight = minOf(dp(470), (metrics.heightPixels * .65f).toInt())
        val scroll = object : android.widget.ScrollView(context) {
            // Wrap short results tightly, but scroll long names and larger fonts.
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = minOf(maxWidth, View.MeasureSpec.getSize(widthMeasureSpec))
                val heightLimit = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) {
                    maxHeight
                } else {
                    minOf(maxHeight, View.MeasureSpec.getSize(heightMeasureSpec))
                }
                super.onMeasure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightLimit, View.MeasureSpec.AT_MOST),
                )
            }
        }.apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            addView(card, FrameLayout.LayoutParams(-1, -2))
        }
        val cardArea = FrameLayout(context).apply {
            setPadding(dp(14), 0, dp(14), 0)
            addView(scroll, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }
        addView(cardArea, FrameLayout.LayoutParams(-1, -1).apply {
            bottomMargin = dp(reserveBottomDp)
        })
    }
