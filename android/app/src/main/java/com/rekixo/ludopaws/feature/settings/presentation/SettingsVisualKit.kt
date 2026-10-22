package com.rekixo.ludopaws.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.transparentDarkBlueStrokeBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.PlywoodCloseButton

internal fun settingsCompactPanel(
    context: Context,
    dialog: Dialog,
): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        // Let the close mark draw over the popup's own top-right border.
        clipChildren = false
        clipToPadding = false
        setPadding(
            settingsDp(context, 9),
            settingsDp(context, 7),
            settingsDp(context, 9),
            settingsDp(context, 9),
        )
        background =
            plywoodBackground(
                context = context,
                cornerRadiusDp = 20f,
            )

        addView(
            settingsHeader(
                context,
                dialog,
            ),
        )
        addView(
            settingsDivider(
                context,
                topBottomMarginDp = 4,
            ),
        )
    }

private fun settingsHeader(
    context: Context,
    dialog: Dialog,
): FrameLayout =
    FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        val title =
            TextView(context).apply {
                text = "SETTINGS"
                textSize =
                    if (
                        context.resources
                            .configuration
                            .screenWidthDp < 380
                    ) {
                        20f
                    } else {
                        23f
                    }
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFFFFD34E.toInt())
                gravity = Gravity.CENTER
                setShadowLayer(
                    3f,
                    0f,
                    2f,
                    0xAA000000.toInt(),
                )
            }

        addView(
            title,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                settingsDp(context, 42),
                Gravity.CENTER,
            ).apply {
                leftMargin = settingsDp(context, 48)
                rightMargin = settingsDp(context, 48)
            },
        )

        val close =
            PlywoodCloseButton(context, contentDescription = "Close settings") {
                dialog.dismiss()
            }

        addView(
            close,
            FrameLayout.LayoutParams(
                settingsDp(context, 42),
                settingsDp(context, 42),
                Gravity.END or Gravity.TOP,
            ).apply {
                // Keep the entire 40dp target inside the dialog like Leave Match;
                // negative margins put the cross outside the plywood panel.
                rightMargin = 0
                topMargin = 0
            },
        )
    }

internal fun settingsCompactRow(
    context: Context,
    label: String,
    control: View,
): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = settingsDp(context, 50)
        setPadding(
            settingsDp(context, 8),
            settingsDp(context, 2),
            settingsDp(context, 4),
            settingsDp(context, 2),
        )
        // Keep this full-width row FLAT. Only the icon/toggle/dropdown
        // drawn inside it owns the translucent blue fill + blue outline.
        background = null
        elevation = 0f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = settingsDp(context, 3)
        }

        addView(
            TextView(context).apply {
                text = label
                textSize =
                    if (
                        context.resources
                            .configuration
                            .screenWidthDp < 380
                    ) {
                        14.5f
                    } else {
                        16f
                    }
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                // Soft dark contrast on warm, light plywood without a row BG.
                setShadowLayer(
                    settingsDp(context, 2).toFloat(),
                    0f,
                    settingsDp(context, 1).toFloat(),
                    0xFF183250.toInt(),
                )
                gravity = Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        addView(control)
    }

internal fun settingsDivider(
    context: Context,
    topBottomMarginDp: Int = 0,
): View =
    View(context).apply {
        background =
            GradientDrawable().apply {
                setColor(0x5587BFFF)
            }
        layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                settingsDp(context, 1),
            ).apply {
                topMargin = settingsDp(context, topBottomMarginDp)
                bottomMargin = settingsDp(context, topBottomMarginDp)
            }
    }

internal fun settingsDropdownField(
    context: Context,
    value: String,
): TextView =
    TextView(context).apply {
        text = settingsDropdownLabel(value)
        textSize =
            if (
                context.resources
                    .configuration
                    .screenWidthDp < 380
            ) {
                13.5f
            } else {
                14.5f
            }
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextColor(Color.WHITE)
        // Same white as the labels; shadow keeps it legible over the
        // intentionally translucent blue field on the plywood popup.
        setShadowLayer(
            settingsDp(context, 2).toFloat(),
            0f,
            settingsDp(context, 1).toFloat(),
            0xFF0A244F.toInt(),
        )
        gravity = Gravity.CENTER
        setPadding(
            settingsDp(context, 6),
            0,
            settingsDp(context, 6),
            0,
        )
        minHeight = settingsDp(context, 40)
        isClickable = true
        isFocusable = true
        elevation = 0f
        background = transparentDarkBlueStrokeBackground(
            context = context,
            cornerRadiusDp = 11f,
        )
        layoutParams =
            LinearLayout.LayoutParams(
                settingsDp(
                    context,
                    if (
                        context.resources
                            .configuration
                            .screenWidthDp < 380
                    ) {
                        146
                    } else {
                        170
                    },
                ),
                settingsDp(context, 40),
            )
        AppFocusRing.apply(this, cornerRadiusDp = 11f, strokeWidthDp = 2f)
    }

internal fun settingsDropdownLabel(
    value: String,
): String =
    "$value  ▼"

internal class SettingsBooleanControl(
    context: Context,
    initialValue: Boolean,
    private val onValueChanged: (Boolean) -> Unit,
) : LinearLayout(context) {
    private var value = initialValue
    private val offText = optionText("OFF")
    private val onText = optionText("ON")

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        background = transparentDarkBlueStrokeBackground(
            context = context,
            cornerRadiusDp = 999f,
        )
        setPadding(
            settingsDp(context, 2),
            settingsDp(context, 2),
            settingsDp(context, 2),
            settingsDp(context, 2),
        )
        elevation = 0f
        layoutParams =
            LinearLayout.LayoutParams(
                settingsDp(context, 102),
                settingsDp(context, 38),
            )

        addView(
            offText,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f,
            ),
        )
        addView(
            onText,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f,
            ),
        )

        offText.setOnClickListener {
            setValue(false, notify = true)
        }
        onText.setOnClickListener {
            setValue(true, notify = true)
        }
        render()
    }

    private fun optionText(
        label: String,
    ): TextView =
        TextView(context).apply {
            text = label
            textSize = 12.5f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            AppFocusRing.apply(this, cornerRadiusDp = 8f, strokeWidthDp = 2f)
        }

    private fun setValue(
        next: Boolean,
        notify: Boolean,
    ) {
        if (value == next) {
            return
        }
        value = next
        render()
        if (notify) {
            onValueChanged(next)
        }
    }

    private fun render() {
        offText.background =
            segmentBackground(
                selected = !value,
                accent =
                    if (!value) {
                        0xFFFFB52E.toInt()
                    } else {
                        Color.TRANSPARENT
                    },
            )
        onText.background =
            segmentBackground(
                selected = value,
                accent =
                    if (value) {
                        0xFF29C8FF.toInt()
                    } else {
                        Color.TRANSPARENT
                    },
            )
        offText.setTextColor(
            if (!value) {
                Color.WHITE
            } else {
                0xFF294A68.toInt()
            },
        )
        onText.setTextColor(
            if (value) {
                Color.WHITE
            } else {
                0xFF294A68.toInt()
            },
        )
    }

    private fun segmentBackground(
        selected: Boolean,
        accent: Int,
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (selected) {
                intArrayOf(
                    if (accent == 0xFF29C8FF.toInt()) {
                        0xFF39D6FF.toInt()
                    } else {
                        0xFFFFC53B.toInt()
                    },
                    if (accent == 0xFF29C8FF.toInt()) {
                        0xFF0E7ECC.toInt()
                    } else {
                        0xFFFF8500.toInt()
                    },
                )
            } else {
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                )
            },
        ).apply {
            cornerRadius =
                settingsDp(context, 999)
                    .toFloat()
        }
}

internal fun settingsPrivacyLink(
    context: Context,
): TextView =
    TextView(context).apply {
        text = "Privacy Policy"
        textSize = 14f
        setTypeface(
            Typeface.DEFAULT,
            Typeface.ITALIC,
        )
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(
            0,
            settingsDp(context, 10),
            0,
            settingsDp(context, 2),
        )
        isClickable = true
        isFocusable = true
        contentDescription = "Open Privacy Policy"
    }

internal fun showSettingsChoiceDialog(
    context: Context,
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    if (options.isEmpty()) {
        return
    }

    val dialog = baseDialog(context)
    val panel =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                settingsDp(context, 10),
                settingsDp(context, 10),
                settingsDp(context, 10),
                settingsDp(context, 10),
            )
            background =
            plywoodBackground(
                context = context,
                cornerRadiusDp = 20f,
            )

            addView(
                TextView(context).apply {
                    text = title
                    textSize = 19f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFFFFD34E.toInt())
                    gravity = Gravity.CENTER
                    setPadding(
                        0,
                        settingsDp(context, 4),
                        0,
                        settingsDp(context, 10),
                    )
                },
            )
        }

    val scroll = ScrollView(context)
    val list =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

    options.forEach { option ->
        list.addView(
            TextView(context).apply {
                text =
                    if (option == selected) {
                        "✓  $option"
                    } else {
                        "    $option"
                    }
                textSize = 15f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(
                    if (option == selected) {
                        0xFFFFD34E.toInt()
                    } else {
                        Color.WHITE
                    },
                )
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    settingsDp(context, 12),
                    settingsDp(context, 9),
                    settingsDp(context, 12),
                    settingsDp(context, 9),
                )
                isClickable = true
                isFocusable = true
                background = transparentDarkBlueStrokeBackground(
                    context = context,
                    cornerRadiusDp = 10f,
                )
                setShadowLayer(
                    settingsDp(context, 2).toFloat(),
                    0f,
                    settingsDp(context, 1).toFloat(),
                    0xFF0A244F.toInt(),
                )
                AppFocusRing.apply(this, cornerRadiusDp = 10f, strokeWidthDp = 2f)
                setOnClickListener {
                    onSelect(option)
                    dialog.dismiss()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = settingsDp(context, 4)
            },
        )
    }

    scroll.addView(list)
    panel.addView(scroll)

    dialog.setContentView(panel)
    sizeDialog(dialog, .72f)
    dialog.show()
}

internal fun settingsDp(
    context: Context,
    value: Int,
): Int =
    (
        value *
            context.resources
                .displayMetrics
                .density
        )
        .toInt()

internal fun settingsDp(
    context: Context,
    value: Float,
): Int =
    (
        value *
            context.resources
                .displayMetrics
                .density
        )
        .toInt()
