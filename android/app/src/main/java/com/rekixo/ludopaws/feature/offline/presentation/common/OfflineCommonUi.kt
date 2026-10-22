package com.rekixo.ludopaws.ui.offline.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.transparentDarkBlueStrokeBackground
import com.rekixo.ludopaws.core.ui.components.appIconActionButton
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.ui.dialogs.showSettingsDialog
import com.rekixo.ludopaws.ui.offline.gameplay.showGame
import com.rekixo.ludopaws.ui.offline.setup.showSetup

internal fun OfflineGameActivity.centeredSetupTitle(label: String): View {
    val compact = LudoProofTheme.isCompactWidth(this)
    val badge = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(2), dp(2), dp(2), dp(2))
        // Unboxed Robot header is centered between fixed Back and Settings
        // controls with the same small-icon/title scale as Pass & Play.
        background = null
        elevation = 0f
        addView(
            RobotTitleIconView(this@centeredSetupTitle),
            LinearLayout.LayoutParams(
                dp(if (compact) 22 else 26),
                dp(if (compact) 22 else 26),
            ).apply {
                marginEnd = dp(7)
            },
        )
        addView(
            TextView(this@centeredSetupTitle).apply {
                text = label
                LudoProofTheme.title(this, if (compact) 15f else 19f, gold = false)
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                maxLines = 1
            },
        )
    }
    return FrameLayout(this).apply {
        clipChildren = false
        clipToPadding = false
        addView(
            badge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(52),
                Gravity.CENTER,
            ),
        )
    }
}

private class RobotTitleIconView(context: Context) : View(context) {
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return

        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 100f, size / 100f)

        // Antenna and side panels make the silhouette read as a small robot.
        canvas.drawLine(50f, 14f, 50f, 23f, outline)
        canvas.drawCircle(50f, 10f, 4f, fill)
        canvas.drawRoundRect(RectF(9f, 38f, 20f, 59f), 4f, 4f, outline)
        canvas.drawRoundRect(RectF(80f, 38f, 91f, 59f), 4f, 4f, outline)

        // Rounded robot head with two bright eyes and a simple mouth.
        canvas.drawRoundRect(RectF(20f, 23f, 80f, 77f), 12f, 12f, outline)
        canvas.drawCircle(38f, 45f, 4.5f, fill)
        canvas.drawCircle(62f, 45f, 4.5f, fill)
        canvas.drawRoundRect(RectF(37f, 60f, 63f, 65f), 2.5f, 2.5f, fill)

        canvas.restore()
    }
}

internal fun OfflineGameActivity.backHeader(label: String?): View {
    fun openSettings() {
        showSettingsDialog(this@backHeader) {
            val current = engine.snapshot()
            if (current != null) showGame(current) else showSetup()
        }
    }

    if (isComputerMode || label == "PASS & PLAY") {
        return FrameLayout(this).apply {
            minimumHeight = dp(if (LudoProofTheme.isCompactWidth(this@backHeader)) 50 else 56)
            clipChildren = false
            clipToPadding = false

            addView(
                appIconActionButton(
                    context = this@backHeader,
                    contentDescription = "Back",
                    iconName = AppIconName.CHEVRON_LEFT,
                    sizeDp = 44,
                ) {
                    requestOfflineExit()
                }.apply {
                    if (isComputerMode || label == "PASS & PLAY") {
                        background = plywoodBackground(
                            context = this@backHeader,
                            cornerRadiusDp = 999f,
                        )
                    }
                },
                FrameLayout.LayoutParams(
                    dp(44),
                    dp(44),
                    Gravity.START or Gravity.CENTER_VERTICAL,
                ).apply { marginStart = dp(4) },
            )

            val badge =
                LinearLayout(this@backHeader).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(if (LudoProofTheme.isCompactWidth(this@backHeader)) 10 else 14),
                        dp(4),
                        dp(if (LudoProofTheme.isCompactWidth(this@backHeader)) 10 else 14),
                        dp(4),
                    )
                    // Match the unboxed ROBOT title style on Computer while retaining
                    // the existing PASS & PLAY title position and surrounding actions.
                    background = null
                    elevation = 0f
                    addView(
                        SetupPawMarkView(
                            this@backHeader,
                            pawColor = 0xFFFFFFFF.toInt(),
                        ),
                        LinearLayout.LayoutParams(
                            dp(if (LudoProofTheme.isCompactWidth(this@backHeader)) 18 else 22),
                            dp(if (LudoProofTheme.isCompactWidth(this@backHeader)) 18 else 22),
                        ),
                    )
                    addView(
                        TextView(this@backHeader).apply {
                            text = label.orEmpty()
                            LudoProofTheme.title(
                                this,
                                if (LudoProofTheme.isCompactWidth(this@backHeader)) 15f else 19f,
                                gold = false,
                            )
                            setPadding(dp(7), 0, dp(7), 0)
                            includeFontPadding = false
                            maxLines = 1
                        },
                    )
                    addView(
                        SetupPawMarkView(
                            this@backHeader,
                            pawColor = 0xFFFFFFFF.toInt(),
                        ),
                        LinearLayout.LayoutParams(
                            dp(if (LudoProofTheme.isCompactWidth(this@backHeader)) 18 else 22),
                            dp(if (LudoProofTheme.isCompactWidth(this@backHeader)) 18 else 22),
                        ),
                    )
                }
            addView(
                badge,
                FrameLayout.LayoutParams(
                    dp(if (LudoProofTheme.isCompactWidth(this@backHeader)) 222 else 254),
                    dp(if (LudoProofTheme.isCompactWidth(this@backHeader)) 42 else 48),
                    Gravity.CENTER,
                ),
            )

            addView(
                appIconActionButton(
                    context = this@backHeader,
                    contentDescription = "Game settings",
                    iconName = AppIconName.SETTINGS,
                    sizeDp = 44,
                ) {
                    openSettings()
                }.apply {
                    if (isComputerMode || label == "PASS & PLAY") {
                        background = plywoodBackground(
                            context = this@backHeader,
                            cornerRadiusDp = 999f,
                        )
                    }
                },
                FrameLayout.LayoutParams(
                    dp(44),
                    dp(44),
                    Gravity.END or Gravity.CENTER_VERTICAL,
                ).apply { marginEnd = dp(4) },
            )
        }
    }

    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            appIconActionButton(
                context = this@backHeader,
                contentDescription = "Back",
                iconName = AppIconName.CHEVRON_LEFT,
                sizeDp = 52,
            ) {
                requestOfflineExit()
            },
            LinearLayout.LayoutParams(dp(52), dp(52)),
        )
        if (label != null) {
            addView(
                TextView(this@backHeader).apply {
                    text = label
                    LudoProofTheme.body(this, 14f, centered = true, bright = true)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    background =
                        LudoProofTheme.darkPanelDrawable(
                            this@backHeader,
                            goldBorder = true,
                        )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    marginStart = dp(12)
                    marginEnd = dp(10)
                },
            )
        } else {
            addView(
                View(this@backHeader),
                LinearLayout.LayoutParams(
                    0,
                    1,
                    1f,
                ).apply {
                    marginStart = dp(12)
                    marginEnd = dp(10)
                },
            )
        }
        addView(
            appIconActionButton(
                context = this@backHeader,
                contentDescription = "Game settings",
                iconName = AppIconName.SETTINGS,
                sizeDp = 52,
            ) {
                openSettings()
            },
            LinearLayout.LayoutParams(dp(52), dp(52)),
        )
    }
}

internal fun OfflineGameActivity.isCompactSetup():
    Boolean =
    LudoProofTheme
        .isCompactWidth(this)

internal fun OfflineGameActivity.dp(value: Int): Int =
    LudoProofTheme.dp(this, value)

private class SetupPawMarkView(
    context: Context,
    pawColor: Int = 0xFF55C9FF.toInt(),
) : View(context) {
    private val pawPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pawColor
            style = Paint.Style.FILL
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(size / 100f, size / 100f)
        canvas.drawOval(RectF(23f, 48f, 77f, 91f), pawPaint)
        canvas.drawOval(RectF(12f, 29f, 30f, 50f), pawPaint)
        canvas.drawOval(RectF(33f, 15f, 51f, 39f), pawPaint)
        canvas.drawOval(RectF(57f, 15f, 75f, 39f), pawPaint)
        canvas.drawOval(RectF(75f, 29f, 93f, 50f), pawPaint)
        canvas.restore()
    }
}
