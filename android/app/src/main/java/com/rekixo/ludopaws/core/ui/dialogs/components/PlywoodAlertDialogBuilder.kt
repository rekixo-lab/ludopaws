package com.rekixo.ludopaws.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.core.ui.art.playGamesSignInBackground
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.transparentDarkBlueStrokeBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.PlywoodCloseButton

/**
 * Common premium replacement for Android's plain AlertDialog.Builder.
 * Preserves the user's action, item, cancellation and dismissal callbacks.
 * The outside is wood; only interactive options and buttons receive blue skins.
 */
internal class PlywoodAlertDialogBuilder(private val context: Context) {
    private data class Action(
        val label: CharSequence,
        val callback: DialogInterface.OnClickListener?,
    )

    private var title: CharSequence? = null
    private var message: CharSequence? = null
    private var customView: View? = null
    private var choices: List<CharSequence> = emptyList()
    private var onChoice: DialogInterface.OnClickListener? = null
    private var affirmative: Action? = null
    private var negative: Action? = null
    private var neutral: Action? = null
    private var cancelListener: DialogInterface.OnCancelListener? = null
    private var dismissListener: DialogInterface.OnDismissListener? = null
    private var cancelable = true

    fun setTitle(value: CharSequence): PlywoodAlertDialogBuilder = apply { title = value }
    fun setMessage(value: CharSequence): PlywoodAlertDialogBuilder = apply { message = value }
    fun setView(value: View): PlywoodAlertDialogBuilder = apply { customView = value }

    fun setItems(
        values: Array<out CharSequence>,
        listener: DialogInterface.OnClickListener,
    ): PlywoodAlertDialogBuilder = apply {
        choices = values.toList()
        onChoice = listener
    }

    fun setPositiveButton(
        label: CharSequence,
        listener: DialogInterface.OnClickListener?,
    ): PlywoodAlertDialogBuilder = apply { affirmative = Action(label, listener) }

    fun setNegativeButton(
        label: CharSequence,
        listener: DialogInterface.OnClickListener?,
    ): PlywoodAlertDialogBuilder = apply { negative = Action(label, listener) }

    fun setNeutralButton(
        label: CharSequence,
        listener: DialogInterface.OnClickListener?,
    ): PlywoodAlertDialogBuilder = apply { neutral = Action(label, listener) }

    fun setOnCancelListener(listener: DialogInterface.OnCancelListener): PlywoodAlertDialogBuilder =
        apply { cancelListener = listener }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener): PlywoodAlertDialogBuilder =
        apply { dismissListener = listener }

    fun setCancelable(value: Boolean): PlywoodAlertDialogBuilder = apply { cancelable = value }

    fun show(): Dialog {
        val dialog = baseDialog(context)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(9), dp(12), dp(10))
            background = plywoodBackground(context, cornerRadiusDp = 22f)
        }

        val header = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        header.addView(
            TextView(context).apply {
                text = title ?: "LUDO PAWS"
                textSize = if (context.resources.configuration.screenWidthDp < 380) 18f else 20f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFFFFD34E.toInt())
                setShadowLayer(3f, 0f, 2f, 0xCC3D200F.toInt())
                gravity = Gravity.CENTER
                maxLines = 3
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                marginStart = dp(38)
                marginEnd = dp(38)
                topMargin = dp(8)
                bottomMargin = dp(8)
            },
        )
        header.addView(
            PlywoodCloseButton(context, "Close dialog") { dialog.dismiss() },
            FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP or Gravity.END),
        )
        panel.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(4))
        }
        message?.let { copy ->
            body.addView(
                TextView(context).apply {
                    text = copy
                    textSize = 13.5f
                    setTextColor(0xFF583717.toInt())
                    setLineSpacing(dp(2).toFloat(), 1.08f)
                    setPadding(dp(6), dp(5), dp(6), dp(9))
                    gravity = Gravity.CENTER
                    setTextIsSelectable(true)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        customView?.let { supplied ->
            // Friend Requests/Room Invites already pass a ScrollView. Reuse its
            // body in this single capped scroll rather than nesting scroll views.
            val view = if (supplied is ScrollView && supplied.childCount == 1) {
                supplied.getChildAt(0).also { supplied.removeView(it) }
            } else {
                supplied
            }
            (view.parent as? ViewGroup)?.removeView(view)
            body.addView(
                view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        choices.forEachIndexed { index, option ->
            body.addView(
                TextView(context).apply {
                    text = option
                    textSize = 14f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(Color.WHITE)
                    setShadowLayer(2f, 0f, 1f, 0xFF071C41.toInt())
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(10), dp(10))
                    minHeight = dp(48)
                    isClickable = true
                    isFocusable = true
                    background = transparentDarkBlueStrokeBackground(context, cornerRadiusDp = 12f)
                    AppFocusRing.apply(this, cornerRadiusDp = 12f, strokeWidthDp = 2f)
                    setOnClickListener {
                        isEnabled = false
                        try {
                            onChoice?.onClick(dialog, index)
                        } finally {
                            dialog.dismiss()
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(5) },
            )
        }

        if (body.childCount > 0) {
            val scroll = object : ScrollView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val maxHeight = (resources.displayMetrics.heightPixels * .52f).toInt()
                    super.onMeasure(
                        widthMeasureSpec,
                        View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
                    )
                }
            }.apply {
                isFillViewport = false
                setVerticalScrollBarEnabled(false)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(body)
            }
            panel.addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        fun invoke(action: Action, which: Int) {
            try {
                action.callback?.onClick(dialog, which)
            } finally {
                dialog.dismiss()
            }
        }

        fun bigAction(action: Action, which: Int, destructive: Boolean, blue: Boolean): TextView =
            TextView(context).apply {
                text = action.label
                textSize = 14.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                setShadowLayer(2f, 0f, 1f, 0xFF09203A.toInt())
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                minHeight = dp(48)
                setPadding(dp(8), dp(7), dp(8), dp(7))
                background = when {
                    destructive -> GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0xFFE14E4D.toInt(), 0xFFAD2433.toInt()),
                    ).apply {
                        cornerRadius = dp(13).toFloat()
                        setStroke(dp(1), 0xFFFFB3A8.toInt())
                    }
                    blue -> transparentDarkBlueStrokeBackground(context, cornerRadiusDp = 13f)
                    else -> playGamesSignInBackground(context)
                }
                AppFocusRing.apply(this, cornerRadiusDp = 13f, strokeWidthDp = 2f)
                setOnClickListener {
                    isEnabled = false
                    invoke(action, which)
                }
            }

        affirmative?.let { action ->
            val danger = Regex("DELETE|REMOVE|BLOCK|REPLACE|DISCARD", RegexOption.IGNORE_CASE)
                .containsMatchIn(action.label)
            panel.addView(
                bigAction(action, DialogInterface.BUTTON_POSITIVE, danger, blue = false),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(50),
                ).apply { topMargin = dp(10) },
            )
        }
        neutral?.let { action ->
            panel.addView(
                bigAction(action, DialogInterface.BUTTON_NEUTRAL, destructive = false, blue = true),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(46),
                ).apply { topMargin = dp(5) },
            )
        }
        negative?.let { action ->
            val isOnlyAction = affirmative == null && neutral == null
            if (isOnlyAction) {
                panel.addView(
                    bigAction(action, DialogInterface.BUTTON_NEGATIVE, destructive = false, blue = true),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(46),
                    ).apply { topMargin = dp(9) },
                )
            } else {
                panel.addView(
                    TextView(context).apply {
                        text = action.label
                        textSize = 13.5f
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextColor(0xFF603B1C.toInt())
                        gravity = Gravity.CENTER
                        minHeight = dp(44)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            isEnabled = false
                            invoke(action, DialogInterface.BUTTON_NEGATIVE)
                        }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(44),
                    ),
                )
            }
        }

        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(cancelable)
        dialog.setOnCancelListener { cancelListener?.onCancel(it) }
        dialog.setOnDismissListener { dismissListener?.onDismiss(it) }
        dialog.setContentView(panel)
        sizeDialog(
            dialog,
            if (context.resources.configuration.screenWidthDp < 380) .94f else .86f,
        )
        dialog.show()
        return dialog
    }
}
