package com.rekixo.ludopaws.feature.growth

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppButton
import com.rekixo.ludopaws.core.ui.components.AppButtonVariant
import com.rekixo.ludopaws.core.ui.components.appIconActionButton
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory

/** The existing game's wood, portrait and button system; no feature/reward authority. */
internal object GrowthUi {
    const val INK = 0xFF302114.toInt()
    const val MUTED = 0xFF302114.toInt()
    fun dp(context: Context, value: Int) = LudoProofTheme.dp(context, value)
    fun copy(context: Context, value: CharSequence, size: Float = 14f, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(INK)
        typeface = Typeface.create("sans-serif-rounded", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = true; setLineSpacing(dp(context, 2).toFloat(), 1f)
    }
    fun card(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18))
        background = plywoodBackground(context, cornerRadiusDp = 24f, rimThicknessDp = 4f)
        elevation = dp(context, 3).toFloat()
    }
    fun button(context: Context, label: String, primary: Boolean = true, enabled: Boolean = true, action: () -> Unit): Button =
        AppButton.create(context, label, if (primary) AppButtonVariant.POSITIVE else AppButtonVariant.SECONDARY,
            enabled = enabled, onClick = action).apply {
            // Explicitly remove the platform tint before using the game's state drawable.
            backgroundTintList = null
            setSingleLine(false)
            textSize = 15f; minHeight = dp(context, 50)
            setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11))
        }
    fun pill(context: Context, label: String, green: Boolean = false) = copy(context, label, 11f, true).apply {
        gravity = Gravity.CENTER; setTextColor(if (green) 0xFF20522D.toInt() else INK)
        setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        background = LudoProofTheme.rounded(if (green) 0xFFE0EDB5.toInt() else 0xFFF9E6BA.toInt(), 20f,
            if (green) 0xFF8CBA60.toInt() else 0xFFC99448.toInt(), 1f, context)
    }
    fun progress(context: Context, value: Int, total: Int, label: String) = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate = false; max = total.coerceAtLeast(1)
        val track = GradientDrawable().apply { setColor(0xFFAD855C.toInt()); cornerRadius = dp(context, 6).toFloat() }
        val fill = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFF82CF43.toInt(), 0xFF36983B.toInt())).apply { cornerRadius = dp(context, 6).toFloat() }
        progressDrawable = LayerDrawable(arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL))).apply {
            setId(0, android.R.id.background); setId(1, android.R.id.progress)
        }
        progressTintList = null; progressBackgroundTintList = null
        progress = value.coerceIn(0, max)
        contentDescription = label
    }
    fun add(parent: LinearLayout, child: View, gap: Int = 10) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(parent.context, gap) })
    }
    fun heading(parent: LinearLayout, title: String, subtitle: String? = null) {
        add(parent, copy(parent.context, title, 21f, true).apply { isAccessibilityHeading = true }, 0)
        subtitle?.let { add(parent, copy(parent.context, it, 13f).apply { setTextColor(MUTED) }, 4) }
    }
    fun portrait(context: Context, id: String?) = LudoPawsCharacterAvatarView(context).apply {
        bind(id, showCircularChrome = false); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun preview(context: Context, id: String): View {
        val item = StoreCosmeticCatalog.find(id) ?: return copy(context, "Preview unavailable", 11f)
        val preview = when (item.category) {
            CosmeticCategory.BOARD -> if (com.rekixo.ludopaws.feature.boards.domain.BoardRegistry.find(id) == null) copy(context, "Preview unavailable", 11f) else FrameLayout(context).apply {
                addView(LudoBoardView(context).apply { setBoardThemeOverride(id); setClassicTokenDrawingEnabled(false) }, FrameLayout.LayoutParams(-1, -1))
                addView(LudoPawsBoardChromeView(context).apply { setBoardThemeOverride(id) }, FrameLayout.LayoutParams(-1, -1))
            }
            CosmeticCategory.DICE -> if (com.rekixo.ludopaws.feature.dice.domain.DiceRegistry.find(id) == null) copy(context, "Preview unavailable", 11f) else DiceView(context).apply { setPreviewStyle(id); showOutcome(1, animate = false); setPlayerTeamColor(null) }
            else -> {
                val character = CharacterDefinitions.all.firstOrNull { StoreCosmeticCatalog.characterUnlock(it.character.id)?.id == id }
                if (character == null) copy(context, "Preview unavailable", 11f) else portrait(context, character.character.id)
            }
        }
        return preview.apply {
            isClickable = false; isFocusable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }
}

/** Fixed navigation, scrollable bounded content, and cutout/gesture-safe edges. */
internal class GrowthPage(activity: Activity, title: String, onBack: () -> Unit, onRefresh: (() -> Unit)? = null) {
    val body: LinearLayout
    val scroll: ScrollView
    init {
        val (root, host) = LudoProofTheme.arcadeRoot(activity)
        val page = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        fun dp(n: Int) = GrowthUi.dp(activity, n)
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(appIconActionButton(activity, "Back", AppIconName.CHEVRON_LEFT, onClick = onBack), LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(GrowthUi.copy(activity, title, 20f, true).apply {
                gravity = Gravity.CENTER; setTextColor(0xFFFFE4A1.toInt()); setShadowLayer(3f, 0f, dp(1).toFloat(), 0xFF51301B.toInt())
                isAccessibilityHeading = true
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
            val trailing = onRefresh?.let { appIconActionButton(activity, "Refresh rewards", AppIconName.REFRESH, onClick = it) } ?: View(activity)
            addView(trailing, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        page.addView(header, LinearLayout.LayoutParams(-1, -2))
        body = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val bounded = MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(widthMeasureSpec), dp(560)), MeasureSpec.getMode(widthMeasureSpec))
                super.onMeasure(bounded, heightMeasureSpec)
            }
        }.apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(28)) }
        val content = FrameLayout(activity).apply { addView(body, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)) }
        scroll = ScrollView(activity).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = false
            clipToPadding = false; addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        page.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int; val bottom: Int; val left: Int; val right: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = safe.top; bottom = safe.bottom; left = safe.left; right = safe.right
            } else {
                @Suppress("DEPRECATION") val safeTop = insets.systemWindowInsetTop
                @Suppress("DEPRECATION") val safeBottom = insets.systemWindowInsetBottom
                top = maxOf(safeTop, insets.displayCutout?.safeInsetTop ?: 0); bottom = safeBottom
                @Suppress("DEPRECATION") val safeLeft = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION") val safeRight = insets.systemWindowInsetRight
                left = maxOf(safeLeft, insets.displayCutout?.safeInsetLeft ?: 0); right = maxOf(safeRight, insets.displayCutout?.safeInsetRight ?: 0)
            }
            page.setPadding(left, top, right, bottom); insets
        }
        host.addView(page, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(root); page.requestApplyInsets()
    }
}
