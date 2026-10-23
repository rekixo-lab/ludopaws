package com.rekixo.ludopaws.ui.quickchat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import kotlin.math.min
import kotlin.math.roundToInt

/** Emoji order follows the four rows in the approved Quick Chat reference. */
internal object QuickChatEmojiCatalog {
    const val COOLDOWN_MS = 700L

    val EMOJIS: List<String> = listOf(
        "👍", "😂", "😮", "😭", "😠", "🥳",
        "😅", "😛", "😎", "🤔", "😘", "🤭",
        "🤦", "😈", "🔥", "💪", "🤡", "👑",
        "😵‍💫", "😞", "🤣", "😆", "😤", "😡",
    )
}

/** Small circular speech-bubble control drawn to match the supplied red/gold icon. */
internal class QuickChatButtonView(
    context: Context,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Open Quick Chat"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(36)
        setMeasuredDimension(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * .465f

        fill.shader = null
        fill.color = 0xFF51170D.toInt()
        fill.setShadowLayer(size * .10f, 0f, size * .035f, 0xAA000000.toInt())
        canvas.drawCircle(cx, cy, radius, fill)
        fill.clearShadowLayer()

        fill.shader = LinearGradient(
            cx, cy - radius, cx, cy + radius,
            intArrayOf(0xFFFFD85B.toInt(), 0xFFE9A000.toInt(), 0xFFB93716.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius * .91f, fill)
        fill.shader = null

        stroke.color = 0xFF8B2617.toInt()
        stroke.strokeWidth = size * .055f
        canvas.drawCircle(cx, cy, radius * .82f, stroke)

        fill.shader = LinearGradient(
            cx, cy - radius * .62f, cx, cy + radius * .62f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFF1F5F7.toInt(), 0xFFD5E0E8.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius * .68f, fill)
        fill.shader = null
        stroke.color = 0xFFB67827.toInt()
        stroke.strokeWidth = size * .025f
        canvas.drawCircle(cx, cy, radius * .68f, stroke)

        val bubble = Path().apply {
            moveTo(cx - radius * .31f, cy + radius * .30f)
            lineTo(cx - radius * .38f, cy + radius * .48f)
            lineTo(cx - radius * .06f, cy + radius * .34f)
            close()
        }
        fill.color = 0xFFF5F7FA.toInt()
        canvas.drawPath(bubble, fill)

        fill.color = 0xFF233448.toInt()
        val dotRadius = size * .055f
        val dotY = cy
        canvas.drawCircle(cx - radius * .28f, dotY, dotRadius, fill)
        canvas.drawCircle(cx, dotY, dotRadius, fill)
        canvas.drawCircle(cx + radius * .28f, dotY, dotRadius, fill)

        if (isPressed) {
            stroke.color = 0xAAFFFFFF.toInt()
            stroke.strokeWidth = size * .05f
            canvas.drawCircle(cx, cy, radius * .76f, stroke)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}

/**
 * Shows a compact, anchored 6x4 emoji palette. It flips above/below the profile
 * icon according to available screen space and dismisses before dispatching.
 */
internal fun showQuickChatPopup(
    anchor: View,
    onEmojiSelected: (String) -> Unit,
) {
    if (!anchor.isAttachedToWindow || !anchor.isEnabled) return
    val context = anchor.context
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).roundToInt()

    val screenWidth = context.resources.displayMetrics.widthPixels
    val popupWidth = min(dp(360), screenWidth - dp(24)).coerceAtLeast(dp(280))

    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(dp(9), dp(8), dp(9), dp(9))
        background = plywoodBackground(
            context = context,
            cornerRadiusDp = 20f,
        )
        elevation = dp(10).toFloat()
    }

    QuickChatEmojiCatalog.EMOJIS.chunked(6).forEach { rowEmojis ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        rowEmojis.forEach { emoji ->
            val cell = QuickChatAnimatedEmojiView(context, emoji, animated = false).apply {
                tag = emoji
                isClickable = true
                isFocusable = true
                contentDescription = "Send $emoji"
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = null
            }
            row.addView(
                cell,
                LinearLayout.LayoutParams(
                    0,
                    dp(45),
                    1f,
                ).apply {
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                },
            )
        }
        panel.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(47),
            ),
        )
    }

    val wrapper = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }
    val arrow = TextView(context).apply {
        textSize = 18f
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        setTextColor(0xFFFFCD38.toInt())
        gravity = Gravity.CENTER
        includeFontPadding = false
        setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x99000000.toInt())
    }

    val location = IntArray(2)
    anchor.getLocationOnScreen(location)
    val visibleFrame = Rect()
    anchor.rootView.getWindowVisibleDisplayFrame(visibleFrame)
    val estimatedHeight = dp(38 + 8 + 9 + 4 * 47 + 12)
    val spaceBelow = visibleFrame.bottom - (location[1] + anchor.height)
    val spaceAbove = location[1] - visibleFrame.top
    val openAbove = spaceBelow < estimatedHeight && spaceAbove > spaceBelow

    if (openAbove) {
        wrapper.addView(
            panel,
            LinearLayout.LayoutParams(popupWidth, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        arrow.text = "▼"
        wrapper.addView(arrow, LinearLayout.LayoutParams(dp(24), dp(16)))
    } else {
        arrow.text = "▲"
        wrapper.addView(arrow, LinearLayout.LayoutParams(dp(24), dp(16)))
        wrapper.addView(
            panel,
            LinearLayout.LayoutParams(popupWidth, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    val popup = PopupWindow(
        wrapper,
        popupWidth,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true,
    ).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isOutsideTouchable = true
        isClippingEnabled = true
        elevation = dp(12).toFloat()
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        animationStyle = android.R.style.Animation_Dialog
    }

    wrapper.measure(
        View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    val measuredHeight = wrapper.measuredHeight
    popup.setOnDismissListener { anchor.isPressed = false }
    var selectionInProgress = false
    for (row in (panel.childCount - 4) until panel.childCount) {
        // Each picked emoji gets a short pop-and-settle animation before it is sent.
        val rowView = panel.getChildAt(row)
        if (rowView is ViewGroup) {
            for (i in 0 until rowView.childCount) {
                val cell = rowView.getChildAt(i)
                cell.setOnClickListener {
                    if (selectionInProgress) return@setOnClickListener
                    val emoji = cell.tag as? String ?: return@setOnClickListener
                    selectionInProgress = true
                    cell.pivotX = cell.width / 2f
                    cell.pivotY = cell.height / 2f
                    cell.animate().cancel()
                    cell.animate()
                        .scaleX(1.26f)
                        .scaleY(1.26f)
                        .setDuration(85L)
                        .withEndAction {
                            cell.animate()
                                .scaleX(0.92f)
                                .scaleY(0.92f)
                                .setDuration(65L)
                                .withEndAction {
                                    cell.animate()
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(55L)
                                        .withEndAction {
                                            popup.dismiss()
                                            onEmojiSelected(emoji)
                                        }
                                        .start()
                                }
                                .start()
                        }
                        .start()
                }
            }
        }
    }

    anchor.post {
        if (!anchor.isAttachedToWindow || !anchor.isEnabled) return@post
        val yOffset = if (openAbove) -measuredHeight - anchor.height else 0
        popup.showAsDropDown(
            anchor,
            0,
            yOffset,
            Gravity.CENTER_HORIZONTAL,
        )
    }
}

/** One reaction per sender, anchored next to their current player card. */
internal fun animateQuickChatReaction(
    parent: FrameLayout,
    emoji: String,
    senderPlayerId: String,
    anchor: View,
): View {
    val host = parent.rootView.findViewById<FrameLayout>(android.R.id.content) ?: parent
    val tag = "quickchat-reaction:$senderPlayerId"
    for (index in host.childCount - 1 downTo 0) {
        val old = host.getChildAt(index)
        if (old.tag == tag) {
            (old as? QuickChatAnimatedEmojiView)?.dispose()
            host.removeView(old)
        }
    }
    val density = host.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).roundToInt()
    val sticker = QuickChatAnimatedEmojiView(host.context, emoji, animated = true).apply {
        this.tag = tag
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        elevation = dp(24).toFloat()
        alpha = 0f
        scaleX = .65f
        scaleY = .65f
    }
    val stickerSize = dp(68)
    host.addView(sticker, FrameLayout.LayoutParams(stickerSize, stickerSize))
    fun remove() {
        sticker.dispose()
        (sticker.parent as? ViewGroup)?.removeView(sticker)
    }
    sticker.post {
        if (!anchor.isAttachedToWindow || !host.isAttachedToWindow) {
            remove()
            return@post
        }
        val origin = IntArray(2)
        val card = IntArray(2)
        host.getLocationOnScreen(origin)
        anchor.getLocationOnScreen(card)
        val cardX = card[0] - origin[0]
        val cardY = card[1] - origin[1]
        val leftSide = cardX + anchor.width / 2f < host.width / 2f
        // Keep clear of dice beside the lower cards and chat controls below their centers.
        sticker.x = (if (leftSide) cardX + anchor.width - stickerSize * .25f
            else cardX - stickerSize * .75f)
            .coerceIn(0f, (host.width - stickerSize).coerceAtLeast(0).toFloat())
        val topRail = cardY + anchor.height / 2f < host.height / 2f
        sticker.y = (if (topRail) cardY - stickerSize - dp(6).toFloat()
            else cardY + anchor.height + dp(6).toFloat())
            .coerceIn(0f, (host.height - stickerSize).coerceAtLeast(0).toFloat())
        sticker.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()
        sticker.postDelayed({
            if (sticker.isAttachedToWindow) sticker.animate().alpha(0f)
                .scaleX(.85f).scaleY(.85f).setDuration(220L).withEndAction { remove() }.start()
        }, 3000L)
    }
    return sticker
}

internal fun clearQuickChatReactions(parent: View) {
    val host = parent.rootView.findViewById<FrameLayout>(android.R.id.content) ?: return
    for (index in host.childCount - 1 downTo 0) {
        val view = host.getChildAt(index)
        if ((view.tag as? String)?.startsWith("quickchat-reaction:") == true) {
            (view as? QuickChatAnimatedEmojiView)?.dispose()
            host.removeView(view)
        }
    }
}
