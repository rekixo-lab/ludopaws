package com.rekixo.ludopaws.ui.offline.setup

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.components.playSelectionPopAnimation
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.core.ui.icons.AppIconVariant
import com.rekixo.ludopaws.core.ui.icons.AppIconView
import com.rekixo.ludopaws.feature.offline.*
import com.rekixo.ludopaws.ui.offline.common.*

internal fun OfflineGameActivity.playersPanel(
    embeddedInCombinedPanel: Boolean = false,
):
    LinearLayout =
    selectionPanel(
        "SELECT PLAYERS",
        if (isComputerMode) "You + 1–3 computer players" else "",
        embeddedInCombinedPanel = embeddedInCombinedPanel,
    ).apply {
        val playersRow = LinearLayout(this@playersPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        val playerMap = linkedMapOf<Int, View>()

        listOf(2, 3, 4).forEach { playerCount ->
            val number = TextView(this@playersPanel).apply {
                text = playerCount.toString()
                textSize = if (isCompactSetup()) 28f else 31f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setShadowLayer(2f, 0f, dp(1).toFloat(), 0x7A00112D)
            }
            val label = TextView(this@playersPanel).apply {
                text = "PLAYERS"
                textSize = if (isCompactSetup()) 10f else 11.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            val tile = LinearLayout(this@playersPanel).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                setPadding(dp(4), dp(5), dp(4), dp(6))
                isClickable = true
                isFocusable = true
                contentDescription = "$playerCount players"
                addView(
                    ModeArtView(this@playersPanel).apply {
                        this.playerCount = playerCount
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(
                        dp(if (isCompactSetup()) 42 else 46),
                        dp(30),
                    ),
                )
                addView(
                    number,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(31),
                    ),
                )
                addView(
                    label,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                setOnClickListener { clicked ->
                    clicked.playSelectionPopAnimation()
                    updateSelectedPlayerCount(playerCount)
                }
            }
            playerMap[playerCount] = tile
            playersRow.addView(
                tile,
                setupTileParams(
                    if (isCompactSetup()) 98 else 106,
                    marginDp = if (isCompactSetup()) 3 else 5,
                ),
            )
        }

        playerButtons = playerMap
        addView(playersRow)
    }

internal fun OfflineGameActivity.colorPanel(
    embeddedInCombinedPanel: Boolean = false,
):
    LinearLayout =
    selectionPanel(
        "CHOOSE YOUR COLOR",
        if (isComputerMode) "Your color will rotate to the bottom-left side."
        else "Pick each player color using P1–P4 above. Two players use opposite corners.",
        embeddedInCombinedPanel = embeddedInCombinedPanel,
    ).apply {
        colorSelectionStatus = TextView(this@colorPanel).apply {
            textSize = if (isCompactSetup()) 12f else 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFFFFD45E.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(6))
        }.also(::addView)
        val colorRow = LinearLayout(this@colorPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }

        val colors = linkedMapOf(
            "BLUE" to 0xFF167BFF.toInt(),
            "RED" to 0xFFF22E35.toInt(),
            "GREEN" to 0xFF22C653.toInt(),
            "YELLOW" to 0xFFFFD324.toInt(),
        )
        val colorMap = linkedMapOf<String, View>()

        colors.forEach { entry ->
            val colorTile = LinearLayout(this@colorPanel).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                setPadding(dp(3), dp(5), dp(3), dp(5))
                isClickable = true
                isFocusable = true
                contentDescription = "${entry.key.lowercase().replaceFirstChar(Char::uppercase)} color"

                addView(
                    SetupColorOrbView(this@colorPanel, entry.value),
                    LinearLayout.LayoutParams(
                        dp(if (isCompactSetup()) 48 else 56),
                        dp(if (isCompactSetup()) 48 else 56),
                    ),
                )
                addView(
                    TextView(this@colorPanel).apply {
                        text = entry.key
                        textSize = if (isCompactSetup()) 10f else 11.5f
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextColor(setupColorLabelColor(entry.key))
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setShadowLayer(
                            dp(5).toFloat(),
                            0f,
                            0f,
                            setupColorGlowColor(entry.key),
                        )
                        setPadding(0, dp(4), 0, 0)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                setOnClickListener { clicked ->
                    clicked.playSelectionPopAnimation()
                    selectColorForActiveSlot(entry.key)
                }
            }
            colorMap[entry.key] = colorTile
            colorRow.addView(
                colorTile,
                setupTileParams(
                    if (isCompactSetup()) 94 else 104,
                    marginDp = if (isCompactSetup()) 3 else 5,
                ),
            )
        }

        colorButtons = colorMap
        addView(colorRow)
    }

internal fun OfflineGameActivity.localTrustStrip():
    LinearLayout =
    LinearLayout(this).apply {
        orientation =
            LinearLayout.HORIZONTAL
        gravity =
            Gravity.CENTER_VERTICAL
        setPadding(
            dp(14),
            dp(12),
            dp(14),
            dp(12),
        )
        background =
            LudoProofTheme
                .hudPanelDrawable(
                    this@localTrustStrip,
                    goldBorder = false,
                )
        elevation =
            dp(4).toFloat()

        addView(
            TextView(
                this@localTrustStrip,
            ).apply {
                text =
                    "✓"
                textSize =
                    25f
                setTextColor(
                    0xFF68F053.toInt(),
                )
                gravity =
                    Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                dp(38),
                dp(38),
            ),
        )

        addView(
            LinearLayout(
                this@localTrustStrip,
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                addView(
                    TextView(
                        this@localTrustStrip,
                    ).apply {
                        text =
                            "LOCAL V4 RECOMPUTATION"
                        LudoProofTheme.body(
                            this,
                            12f,
                            bright = true,
                        )
                    },
                )
                addView(
                    TextView(
                        this@localTrustStrip,
                    ).apply {
                        text =
                            "Works offline • no remote server attestation"
                        LudoProofTheme.body(
                            this,
                            10.5f,
                        )
                        setPadding(
                            0,
                            dp(2),
                            0,
                            0,
                        )
                    },
                )
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart =
                    dp(8)
            },
        )
    }

internal fun OfflineGameActivity.selectionPanel(
    title: String,
    subtitle: String,
    embeddedInCombinedPanel: Boolean = false,
):
    LinearLayout =
    LudoProofTheme.panel(this).apply {
        val horizontalPadding = if (embeddedInCombinedPanel) {
            dp(if (isCompactSetup()) 4 else 6)
        } else {
            dp(if (isCompactSetup()) 10 else 14)
        }
        val verticalPadding = if (embeddedInCombinedPanel) {
            dp(if (isCompactSetup()) 3 else 5)
        } else {
            dp(if (isCompactSetup()) 10 else 14)
        }
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        // Embedded subsections are transparent: their shared parent supplies
        // the single plywood surface for the combined setup.
        background = when {
            embeddedInCombinedPanel -> ColorDrawable(Color.TRANSPARENT)
            isComputerMode -> recessedPlywoodBackground(
                context = this@selectionPanel,
                outerCornerRadiusDp = 20f,
            )
            else -> recessedPlywoodBackground(
                context = this@selectionPanel,
                outerCornerRadiusDp = 20f,
            )
        }
        if (embeddedInCombinedPanel) elevation = 0f

        val heading = LinearLayout(this@selectionPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val sectionIcon: View = when (title) {
                "SELECT PLAYERS" -> AppIconView(
                    this@selectionPanel,
                    AppIconName.PEOPLE,
                    iconTint = LudoProofTheme.GOLD,
                    iconVariant = AppIconVariant.FILLED,
                    strokeWidthDp = 2f,
                )
                "CHOOSE YOUR COLOR" -> SetupPaletteIconView(this@selectionPanel)
                else -> SetupPawIconView(this@selectionPanel)
            }
            addView(
                sectionIcon,
                LinearLayout.LayoutParams(
                    dp(if (isCompactSetup()) 30 else 36),
                    dp(if (isCompactSetup()) 30 else 36),
                ).apply {
                    marginEnd = dp(7)
                },
            )

            val highlightedWord = when (title) {
                "CHOOSE YOUR PAW" -> "PAW"
                "SELECT PLAYERS" -> "PLAYERS"
                "CHOOSE YOUR COLOR" -> "COLOR"
                else -> null
            }
            addView(
                TextView(this@selectionPanel).apply {
                    text = SpannableString(title).apply {
                        val wordStart = highlightedWord?.let(title::lastIndexOf) ?: -1
                        if (wordStart >= 0 && highlightedWord != null) {
                            setSpan(
                                ForegroundColorSpan(LudoProofTheme.GOLD),
                                wordStart,
                                wordStart + highlightedWord.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }
                    }
                    textSize = if (isCompactSetup()) 17f else 19f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    includeFontPadding = false
                    setShadowLayer(3f, 0f, dp(1).toFloat(), 0xB0000000.toInt())
                },
            )
        }
        addView(heading)

        if (subtitle.isNotBlank()) {
            addView(
                TextView(this@selectionPanel).apply {
                    text = subtitle
                    LudoProofTheme.body(
                        this,
                        if (isCompactSetup()) 10.5f else 11.5f,
                        centered = true,
                        bright = true,
                    )
                    setTextColor(0xFFD4E8FF.toInt())
                    setPadding(
                        0,
                        dp(3),
                        0,
                        dp(if (isCompactSetup()) 8 else 11),
                    )
                },
            )
        }
    }

internal fun OfflineGameActivity.choiceTile(
    icon: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    action: () -> Unit,
): LinearLayout =
    LinearLayout(this).apply {
        orientation =
            LinearLayout.VERTICAL
        gravity =
            Gravity.CENTER
        isEnabled =
            enabled
        alpha =
            if (enabled) 1f else .52f

        if (selected) {
            LudoProofTheme.selectedTile(this)
        } else {
            LudoProofTheme.normalTile(this)
        }

        setPadding(
            dp(10),
            dp(8),
            dp(10),
            dp(8),
        )

        addView(
            TextView(this@choiceTile).apply {
                text =
                    icon
                textSize =
                    27f
                gravity =
                    Gravity.CENTER
                setTextColor(
                    if (selected) {
                        0xFF053177.toInt()
                    } else {
                        Color.WHITE
                    },
                )
            },
        )
        addView(
            TextView(this@choiceTile).apply {
                text =
                    title
                LudoProofTheme.body(
                    this,
                    16f,
                    centered = true,
                    bright = true,
                )
                if (selected) {
                    setTextColor(
                        0xFF052A68.toInt(),
                    )
                }
            },
        )
        addView(
            TextView(this@choiceTile).apply {
                text =
                    subtitle
                LudoProofTheme.body(
                    this,
                    10.5f,
                    centered = true,
                    bright = true,
                )
                if (selected) {
                    setTextColor(
                        0xFF174A78.toInt(),
                    )
                }
            },
        )
        setOnClickListener {
            if (enabled) {
                action()
            }
        }
    }

internal fun OfflineGameActivity.tileButton(
    text: String,
    action: () -> Unit,
): Button =
    Button(this).apply {
        this.text =
            text
        textSize =
            if (isCompactSetup()) 12.5f else 14f
        setTextColor(
            Color.WHITE,
        )
        gravity =
            Gravity.CENTER
        setPadding(
            dp(4),
            dp(8),
            dp(4),
            dp(8),
        )
        setOnClickListener {
            action()
        }
    }

private fun blendWithWhite(color: Int, amount: Float): Int =
    Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt(),
    )

private fun blendWithBlack(color: Int, amount: Float): Int =
    Color.rgb(
        (Color.red(color) * (1f - amount)).toInt(),
        (Color.green(color) * (1f - amount)).toInt(),
        (Color.blue(color) * (1f - amount)).toInt(),
    )

private fun setupColor(name: String): Int =
    when (name) {
        "BLUE" -> 0xFF298DFF.toInt()
        "RED" -> 0xFFF22E35.toInt()
        "GREEN" -> 0xFF1FB257.toInt()
        else -> 0xFFFFD324.toInt()
    }

private fun setupColorLabelColor(name: String): Int =
    when (name) {
        "BLUE" -> 0xFF64C4FF.toInt()
        "RED" -> 0xFFFF6670.toInt()
        "GREEN" -> 0xFF5BF58A.toInt()
        else -> 0xFFFFEB70.toInt()
    }

private fun setupColorGlowColor(name: String): Int =
    when (name) {
        "BLUE" -> 0xFF35AFFF.toInt()
        "RED" -> 0xFFFF263D.toInt()
        "GREEN" -> 0xFF23E968.toInt()
        else -> 0xFFFFD324.toInt()
    }

internal fun OfflineGameActivity.refreshSetupSelections() {
    playerButtons.forEach { (playerCount, tile) ->
        val selected = playerCount == selectedPlayers
        if (isComputerMode) {
            tile.background = playerCountSelectionBackground(
                context = this@refreshSetupSelections,
                selected = selected,
                outerCornerRadiusDp = 12f,
                checkBadgeAtTopEnd = true,
                emphasizeSelected = true,
                showCheckBadge = true,
                checkMarkScale = 3f,
            )
            tile.elevation = dp(if (selected) 6 else 4).toFloat()
        } else {
            tile.background = playerCountSelectionBackground(
                context = this@refreshSetupSelections,
                selected = selected,
                outerCornerRadiusDp = 12f,
                checkBadgeAtTopEnd = true,
                emphasizeSelected = true,
                showCheckBadge = true,
                checkMarkScale = 2f,
            )
            tile.elevation = dp(if (selected) 3 else 1).toFloat()
        }
        (tile as? LinearLayout)?.let { content ->
            (content.getChildAt(0) as? ModeArtView)?.playerCountSelected = selected
            val countTextColor = if (selected) 0xFFFFD45E.toInt() else Color.WHITE
            (content.getChildAt(1) as? TextView)?.setTextColor(countTextColor)
            (content.getChildAt(2) as? TextView)?.setTextColor(countTextColor)
        }
    }

    colorSelectionStatus?.text = if (isComputerMode) "YOUR COLOR: $selectedColor"
    else "PLAYER ${selectedCharacterSlot + 1} COLOR: $selectedColor"
    colorButtons.forEach { (colorName, tile) ->
        val selected = colorName == selectedColor
        tile.contentDescription = if (isComputerMode) "$colorName color"
            else "$colorName color for Player ${selectedCharacterSlot + 1}"
        tile.background = playerCountSelectionBackground(
            context = this@refreshSetupSelections,
            selected = selected,
            outerCornerRadiusDp = 12f,
            checkBadgeAtTopEnd = true,
            emphasizeSelected = true,
            showCheckBadge = true,
            checkMarkScale = 2f,
        )
        tile.elevation = dp(if (selected) 3 else 1).toFloat()
        ((tile as? LinearLayout)?.getChildAt(1) as? TextView)?.apply {
            setTextColor(setupColorLabelColor(colorName))
            setShadowLayer(
                dp(if (selected) 8 else 5).toFloat(),
                0f,
                0f,
                setupColorGlowColor(colorName),
            )
        }
    }

    refreshCharacterSelectionUi()
}

internal fun OfflineGameActivity.setupSectionParams(
    topMarginDp: Int,
) =
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin =
            dp(topMarginDp)
    }

internal fun OfflineGameActivity.setupTileParams(
    heightDp: Int,
    marginDp: Int = 6,
) =
    LinearLayout.LayoutParams(
        0,
        dp(heightDp),
        1f,
    ).apply {
        setMargins(
            dp(marginDp),
            0,
            dp(marginDp),
            0,
        )
    }


private class SetupPawIconView(context: android.content.Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LudoProofTheme.GOLD }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 100f, size / 100f)
        canvas.drawOval(RectF(22f, 48f, 78f, 91f), fill)
        canvas.drawOval(RectF(8f, 29f, 29f, 52f), fill)
        canvas.drawOval(RectF(30f, 13f, 49f, 39f), fill)
        canvas.drawOval(RectF(53f, 13f, 72f, 39f), fill)
        canvas.drawOval(RectF(72f, 29f, 93f, 52f), fill)
        canvas.restore()
    }
}

private class SetupPaletteIconView(context: android.content.Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 100f, size / 100f)

        val palette = Path().apply {
            moveTo(49f, 12f)
            cubicTo(24f, 10f, 11f, 28f, 13f, 49f)
            cubicTo(15f, 71f, 34f, 87f, 55f, 82f)
            cubicTo(66f, 79f, 58f, 68f, 57f, 61f)
            cubicTo(55f, 53f, 62f, 48f, 70f, 51f)
            cubicTo(86f, 58f, 93f, 43f, 83f, 27f)
            cubicTo(75f, 15f, 62f, 12f, 49f, 12f)
            close()
        }
        fill.shader = RadialGradient(
            34f, 28f, 72f,
            intArrayOf(0xFFBCEBFF.toInt(), 0xFF55A6F8.toInt(), 0xFF1264D3.toInt()),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(palette, fill)
        fill.shader = null
        stroke.color = 0xFFBCEBFF.toInt()
        stroke.strokeWidth = 2.6f
        canvas.drawPath(palette, stroke)

        fill.color = 0xFF073276.toInt()
        canvas.drawCircle(35f, 31f, 5.5f, fill)
        canvas.drawCircle(54f, 25f, 5.5f, fill)
        canvas.drawCircle(69f, 35f, 5.5f, fill)
        fill.color = 0xFFFFD13B.toInt()
        canvas.drawCircle(38f, 52f, 5.5f, fill)
        fill.color = 0xFF72E9FF.toInt()
        canvas.drawCircle(55f, 45f, 5.5f, fill)
        canvas.restore()
    }
}

private class SetupColorOrbView(
    context: android.content.Context,
    private val baseColor: Int,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xA8FFFFFF.toInt() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = minOf(width, height).toFloat()
        if (diameter <= 0f) return
        val radius = diameter * 0.42f
        val cx = width * 0.5f
        val cy = height * 0.49f

        fill.shader = null
        fill.color = 0x7000183B
        fill.setShadowLayer(dp(3f), 0f, dp(2f), 0x9900102D.toInt())
        canvas.drawCircle(cx, cy + dp(1f), radius * 1.03f, fill)
        fill.clearShadowLayer()

        fill.shader = RadialGradient(
            cx - radius * 0.38f,
            cy - radius * 0.43f,
            radius * 1.55f,
            intArrayOf(blendWithWhite(baseColor, 0.68f), baseColor, blendWithBlack(baseColor, 0.48f)),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, fill)
        fill.shader = null

        stroke.color = 0xDFFFFFFF.toInt()
        stroke.strokeWidth = dp(1.2f)
        canvas.drawCircle(cx, cy, radius, stroke)

        shine.alpha = 200
        canvas.drawOval(
            RectF(cx - radius * 0.62f, cy - radius * 0.67f, cx - radius * 0.02f, cy - radius * 0.30f),
            shine,
        )
        shine.alpha = 95
        canvas.drawCircle(cx - radius * 0.48f, cy - radius * 0.43f, radius * 0.09f, shine)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
