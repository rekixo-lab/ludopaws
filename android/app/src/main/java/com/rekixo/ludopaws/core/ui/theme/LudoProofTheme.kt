package com.rekixo.ludopaws

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object LudoProofTheme {
    const val NAVY = 0xFF061D55.toInt()
    const val NAVY_DARK = 0xFF03153F.toInt()
    const val BLUE_DARK = 0xFF06318B.toInt()
    const val BLUE = 0xFF1169D9.toInt()
    const val BLUE_LIGHT = 0xFF2A8CFF.toInt()
    const val CYAN_BORDER = 0xFF20C9FF.toInt()
    const val GOLD = 0xFFFFC62E.toInt()
    const val ORANGE = 0xFFFFA000.toInt()
    const val GREEN = 0xFF35C83D.toInt()
    const val WHITE = Color.WHITE
    const val TEXT_MUTED = 0xFFD4E6FF.toInt()
    const val TEXT_DIM = 0xFF9CBDEB.toInt()
    const val RED = 0xFFF22E35.toInt()
    const val YELLOW = 0xFFFFD324.toInt()

    fun configureWindow(activity: Activity) {
        val window = activity.window
        val decorView = window.decorView

        window.statusBarColor = NAVY_DARK
        window.navigationBarColor = NAVY_DARK

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)

            // Some OEM builds can throw from Window#getInsetsController
            // before PhoneWindow has created/attached its DecorView.
            // Resolve the controller from the DecorView instead; it safely
            // returns null until attachment, then retry on the UI queue.
            applyImmersiveSystemBars(decorView)
            decorView.post {
                applyImmersiveSystemBars(
                    decorView,
                )
            }
        } else {
            applyLegacyImmersiveSystemBars(
                decorView,
            )
        }
    }

    private fun applyImmersiveSystemBars(
        decorView: View,
    ) {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.R
        ) {
            return
        }

        decorView
            .windowInsetsController
            ?.apply {
                hide(
                    WindowInsets.Type.systemBars(),
                )
                systemBarsBehavior =
                    WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyImmersiveSystemBars(
        decorView: View,
    ) {
        decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    fun arcadeRoot(
        context: Context,
        gameplayBackground: Boolean = false,
        gameplayBoardEnvironment: Boolean = false,
    ): Pair<FrameLayout, FrameLayout> {
        val root = FrameLayout(context)
        val backdrop =
            ArcadeBackdropView(context).apply {
                if (gameplayBackground) {
                    useGameplayBackground(boardEnvironment = gameplayBoardEnvironment)
                }
            }
        root.addView(
            backdrop,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val content = FrameLayout(context)
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return root to content
    }

    /** Applies the unified app background through the existing compatibility API. */
    fun setGameplayBackground(
        root: FrameLayout,
        enabled: Boolean,
    ) {
        val backdrop = root.getChildAt(0) as? ArcadeBackdropView ?: return
        if (enabled) {
            backdrop.useGameplayBackground(boardEnvironment = true)
        } else {
            backdrop.useUiBackground()
        }
    }

    fun panel(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            background = panelDrawable(context)
            elevation = dp(context, 8).toFloat()
        }

    fun panelDrawable(context: Context): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                0xEC1B66C7.toInt(),
                0xE8103F9C.toInt(),
            ),
        ).apply {
            cornerRadius = dp(context, 18f).toFloat()
            setStroke(dp(context, 2), CYAN_BORDER)
        }

    fun darkPanelDrawable(
        context: Context,
        goldBorder: Boolean = false,
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                0xF20C2C68.toInt(),
                0xF3071C4E.toInt(),
            ),
        ).apply {
            cornerRadius = dp(context, 18f).toFloat()
            setStroke(dp(context, 2), if (goldBorder) GOLD else CYAN_BORDER)
        }

    fun primary(button: Button) =
        glossy(
            button,
            intArrayOf(
                0xFFFFC72D.toInt(),
                0xFFFF9F00.toInt(),
                0xFFF47A00.toInt(),
            ),
            0xFFFFE07B.toInt(),
        )

    fun positive(button: Button) =
        glossy(
            button,
            intArrayOf(
                0xFF83E92F.toInt(),
                0xFF35C72D.toInt(),
                0xFF07972C.toInt(),
            ),
            0xFFC8FF6F.toInt(),
        )

    fun secondary(button: Button) =
        glossy(
            button,
            intArrayOf(
                0xFF2A8EF7.toInt(),
                0xFF0F61D3.toInt(),
                0xFF073BA1.toInt(),
            ),
            0xFF6DCCFF.toInt(),
        )

    fun danger(button: Button) =
        glossy(
            button,
            intArrayOf(
                0xFFFF6C6B.toInt(),
                0xFFE52B34.toInt(),
                0xFFB7142A.toInt(),
            ),
            0xFFFFB6AF.toInt(),
        )

    fun circularAction(button: Button, symbol: String) {
        button.text = symbol
        button.textSize = 24f
        button.setTypeface(Typeface.DEFAULT_BOLD)
        button.gravity = Gravity.CENTER
        button.setTextColor(WHITE)
        button.background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFFFFC442.toInt(),
                    0xFFFF8B00.toInt(),
                    0xFFE76A00.toInt(),
                ),
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(button.context, 2), 0xFFFFE884.toInt())
            }
        button.elevation = dp(button.context, 6).toFloat()
        button.minWidth = dp(button.context, 48)
        button.minHeight = dp(button.context, 48)
    }

    fun homeCircularAction(
        button: Button,
        symbol: String,
    ) {
        button.text =
            symbol
        button.textSize =
            25f
        button.setTypeface(
            Typeface.DEFAULT_BOLD,
        )
        button.gravity =
            Gravity.CENTER
        button.setPadding(
            0,
            0,
            0,
            0,
        )
        button.setTextColor(
            when (symbol) {
                "★" ->
                    GOLD
                "✓" ->
                    0xFF5AF05A.toInt()
                "↗" ->
                    0xFF91E9FF.toInt()
                else ->
                    WHITE
            },
        )

        val normal =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFF0D66C8.toInt(),
                    0xFF063B8C.toInt(),
                    0xFF031D57.toInt(),
                ),
            ).apply {
                shape =
                    GradientDrawable.OVAL
                setStroke(
                    dp(button.context, 2),
                    0xFF58E3FF.toInt(),
                )
            }

        val pressed =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFF08498F.toInt(),
                    0xFF042E6F.toInt(),
                    0xFF021743.toInt(),
                ),
            ).apply {
                shape =
                    GradientDrawable.OVAL
                setStroke(
                    dp(button.context, 2),
                    0xFFFFD45E.toInt(),
                )
            }

        button.background =
            StateListDrawable().apply {
                addState(
                    intArrayOf(
                        android.R.attr.state_pressed,
                    ),
                    pressed,
                )
                addState(
                    intArrayOf(),
                    normal,
                )
            }
        button.elevation =
            dp(button.context, 7)
                .toFloat()
        button.minWidth =
            dp(button.context, 48)
        button.minHeight =
            dp(button.context, 48)
    }

    fun brandBadgeDrawable(
        context: Context,
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                0xFF1179E8.toInt(),
                0xFF063D9C.toInt(),
                0xFF031D57.toInt(),
            ),
        ).apply {
            cornerRadius =
                dp(context, 16f)
                    .toFloat()
            setStroke(
                dp(context, 2),
                0xFFFFD45E.toInt(),
            )
        }

    fun input(view: EditText) {
        view.setTextColor(WHITE)
        view.setHintTextColor(0xFF9DC5FF.toInt())
        view.textSize = 16f
        view.background = darkPanelDrawable(view.context, goldBorder = true)
        view.setPadding(
            dp(view.context, 14),
            dp(view.context, 12),
            dp(view.context, 14),
            dp(view.context, 12),
        )
    }

    fun title(
        view: TextView,
        size: Float = 28f,
        gold: Boolean = false,
    ) {
        view.textSize = size
        view.setTextColor(if (gold) GOLD else WHITE)
        view.setTypeface(Typeface.DEFAULT_BOLD)
        view.gravity = Gravity.CENTER
        view.setShadowLayer(
            4f,
            0f,
            dp(view.context, 2).toFloat(),
            0xB0000000.toInt(),
        )
    }

    fun body(
        view: TextView,
        size: Float = 14f,
        centered: Boolean = false,
        bright: Boolean = false,
    ) {
        view.textSize = size
        view.setTextColor(if (bright) WHITE else TEXT_MUTED)
        if (centered) view.gravity = Gravity.CENTER
    }

    fun selectedTile(view: View) {
        view.background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFFFFE34D.toInt(),
                    0xFFF2B212.toInt(),
                ),
            ).apply {
                cornerRadius = dp(view.context, 14f).toFloat()
                setStroke(dp(view.context, 2), 0xFFFFEE92.toInt())
            }
        view.elevation = dp(view.context, 5).toFloat()
    }

    fun normalTile(view: View) {
        view.background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFF2B8CF7.toInt(),
                    0xFF1262D1.toInt(),
                ),
            ).apply {
                cornerRadius = dp(view.context, 14f).toFloat()
                setStroke(dp(view.context, 1), 0xFF5BC2FF.toInt())
            }
        view.elevation = dp(view.context, 4).toFloat()
    }

    fun chip(view: View, color: Int = BLUE) {
        view.background =
            rounded(
                color,
                999f,
                0x66FFFFFF,
                1f,
                view.context,
            )
    }

    fun boardFrame(context: Context): FrameLayout =
        FrameLayout(context).apply {
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        0xFF67DEFF.toInt(),
                        0xFF0B86D9.toInt(),
                        0xFF00519A.toInt(),
                    ),
                ).apply {
                    cornerRadius = dp(context, 16f).toFloat()
                    setStroke(dp(context, 2), 0xFFA7F2FF.toInt())
                }
            elevation = dp(context, 9).toFloat()
        }

    private fun glossy(
        button: Button,
        colors: IntArray,
        strokeColor: Int,
    ) {
        button.isAllCaps = false
        button.textSize = 17f
        button.setTypeface(Typeface.DEFAULT_BOLD)
        button.setTextColor(
            ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_enabled),
                ),
                intArrayOf(0xFFAAC1E0.toInt(), WHITE),
            ),
        )
        button.setShadowLayer(2f, 0f, 1f, 0x70000000)
        val enabled =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                colors,
            ).apply {
                cornerRadius = dp(button.context, 26f).toFloat()
                setStroke(dp(button.context, 2), strokeColor)
            }
        val disabled =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFF4D6FA1.toInt(),
                    0xFF34547F.toInt(),
                ),
            ).apply {
                cornerRadius = dp(button.context, 26f).toFloat()
                setStroke(dp(button.context, 1), 0xFF7D9BC4.toInt())
            }
        button.background =
            StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), disabled)
                addState(intArrayOf(), enabled)
            }
        button.minHeight = dp(button.context, 54)
        button.setPadding(
            dp(button.context, 16),
            dp(button.context, 8),
            dp(button.context, 16),
            dp(button.context, 8),
        )
        button.elevation = dp(button.context, 5).toFloat()
    }

    fun rounded(
        fill: Int,
        radiusDp: Float,
        stroke: Int = Color.TRANSPARENT,
        strokeDp: Float = 0f,
        context: Context,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeDp > 0f) {
                setStroke(dp(context, strokeDp), stroke)
            }
        }

    fun hudPanelDrawable(
        context: Context,
        goldBorder: Boolean = false,
    ): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                0xE90A2A68.toInt(),
                0xE7061A48.toInt(),
            ),
        ).apply {
            cornerRadius = dp(context, 20f).toFloat()
            setStroke(
                dp(context, 1.5f),
                if (goldBorder) GOLD else 0xFF47D7FF.toInt(),
            )
        }

    fun screenWidthDp(context: Context): Int {
        val configuredWidth =
            context.resources.configuration.screenWidthDp
        if (configuredWidth > 0) {
            return configuredWidth
        }

        val metrics =
            context.resources.displayMetrics
        return (
            metrics.widthPixels /
                metrics.density
            ).toInt()
    }

    fun isCompactWidth(context: Context): Boolean =
        screenWidthDp(context) < 370

    fun isExpandedWidth(context: Context): Boolean =
        screenWidthDp(context) >= 600

    fun pageHorizontalPaddingDp(context: Context): Int =
        when {
            isCompactWidth(context) -> 14
            isExpandedWidth(context) -> 24
            else -> 18
        }

    fun pageMaxContentWidthDp(context: Context): Int =
        if (isExpandedWidth(context)) 720 else 680

    fun sectionGapDp(context: Context): Int =
        if (isCompactWidth(context)) 14 else 18

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
