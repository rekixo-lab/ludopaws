package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import kotlin.math.roundToInt

/**
 * Compact character panel used by offline and remote gameplay rails.
 *
 * The card is presentation-only: token progress is read from PlayerSnapshot and
 * the selected animal is cosmetic metadata supplied by the caller.
 */
class LudoPawsPlayerCardView(
    context: Context,
) : LinearLayout(context) {
    private val portrait =
        LudoPawsCharacterAvatarView(context)
    private val nameText =
        TextView(context)
    private val stateText =
        TextView(context)
    private val progressText =
        TextView(context)
    private val copy =
        LinearLayout(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(dp(4), dp(5), dp(6), dp(5))

        addView(
            portrait,
            LayoutParams(
                dp(50),
                dp(50),
            ).apply {
                marginEnd = dp(6)
            },
        )

        copy.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        nameText.apply {
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            textSize = 11.5f
            maxLines = 1
        }
        stateText.apply {
            setTypeface(Typeface.DEFAULT_BOLD)
            textSize = 9.5f
            maxLines = 1
        }
        progressText.apply {
            setTextColor(0xFFDCEBFF.toInt())
            textSize = 9f
            maxLines = 1
        }

        copy.addView(nameText)
        copy.addView(stateText)
        copy.addView(progressText)
        addView(
            copy,
            LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
    }

    fun bind(
        player: PlayerSnapshot,
        characterId: String?,
        active: Boolean,
        computer: Boolean,
        compact: Boolean,
        localPlayer: Boolean = true,
        portraitOnEnd: Boolean = false,
    ) {
        layoutDirection =
            if (portraitOnEnd) {
                View.LAYOUT_DIRECTION_RTL
            } else {
                View.LAYOUT_DIRECTION_LTR
            }
        copy.layoutDirection = View.LAYOUT_DIRECTION_LTR
        nameText.textDirection = View.TEXT_DIRECTION_LTR
        stateText.textDirection = View.TEXT_DIRECTION_LTR
        progressText.textDirection = View.TEXT_DIRECTION_LTR
        setPadding(
            dp(if (portraitOnEnd) 6 else 2),
            dp(5),
            dp(if (portraitOnEnd) 2 else 6),
            dp(5),
        )

        val character =
            characterId
                ?.let(LudoPawsCharacterCatalog::character)
                ?: LudoPawsCharacterCatalog
                    .character(
                        LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                    )
        val characterName =
            character?.displayName ?: "Paw"

        portrait.bind(
            characterId = character?.id,
            ringColor = playerColor(player.color),
            highlighted = active,
            modelTeamColor = player.color,
        )

        val isBot = player.playerType == "BOT"
        val renderedName = displayPlayerName(player.displayName, computer)
        nameText.text = if (isBot) "$renderedName • BOT" else renderedName
        nameText.textSize =
            if (compact) 10.5f else 11.5f

        val turnLabel =
            when {
                active && isBot -> "BOT TURN"
                active && computer -> "CPU TURN"
                active && localPlayer -> "YOUR TURN"
                active && player.teamId != null -> "TEAM ${player.teamId} TURN"
                active -> "TURN"
                player.teamId != null -> "TEAM ${player.teamId}"
                else -> "WAITING"
            }
        stateText.text = "$characterName • $turnLabel"
        stateText.setTextColor(
            if (active) {
                0xFFFFD54F.toInt()
            } else {
                0xFF76E4F7.toInt()
            },
        )
        stateText.textSize =
            if (compact) 8.4f else 9.3f

        val home =
            player.tokens.count {
                it == LudoPathEncoding.HOME_POSITION
            }
        val yard =
            player.tokens.count {
                it == -1
            }
        val racing =
            player.tokens.size - home - yard
        progressText.text =
            when {
                home >= player.tokens.size -> "ALL PAWS HOME"
                racing > 0 -> "$home/4 HOME • $racing MOVING"
                else -> "$home/4 HOME • READY"
            }
        progressText.textSize =
            if (compact) 8.1f else 8.8f

        background =
            cardBackground(
                player.color,
                active,
            )
        elevation =
            dp(
                if (active) 7 else 2,
            ).toFloat()

        contentDescription =
            buildString {
                append(characterName)
                append(", ")
                append(player.displayName)
                if (isBot) append(", bot player")
                append(", ")
                append(player.color.lowercase())
                append(", ")
                append(home)
                append(" home, ")
                append(racing)
                append(" moving, ")
                append(yard)
                append(" in yard")
                if (active) {
                    append(", active turn")
                }
            }
        importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun displayPlayerName(
        displayName: String,
        computer: Boolean,
    ): String {
        if (!computer) return displayName
        // Rename only the AI presentation label; identity and all non-AI names
        // remain exactly as supplied by the match state.
        val cpuNumber = Regex("^CPU\\s+(\\d+)$", RegexOption.IGNORE_CASE)
            .matchEntire(displayName.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?: return displayName
        return "Robot $cpuNumber"
    }

    private fun cardBackground(
        colorName: String,
        active: Boolean,
    ): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(15).toFloat()
            val team = playerColor(colorName)
            fun tint(amount: Float): Int = android.graphics.Color.rgb(
                (12 + android.graphics.Color.red(team) * amount).toInt().coerceAtMost(255),
                (15 + android.graphics.Color.green(team) * amount).toInt().coerceAtMost(255),
                (24 + android.graphics.Color.blue(team) * amount).toInt().coerceAtMost(255),
            )
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(tint(if (active) .25f else .18f), tint(.04f))
            // Active turns may change stroke weight, but never override the
            // player's team identity with a shared yellow highlight.
            setStroke(
                dp(if (active) 3 else 2),
                playerColor(colorName),
            )
        }

    private fun playerColor(
        colorName: String,
    ): Int =
        when (colorName) {
            "RED" -> 0xFFF1252F.toInt()
            "GREEN" -> 0xFF00A950.toInt()
            "YELLOW" -> 0xFFFFD81B.toInt()
            "BLUE" -> 0xFF3097D7.toInt()
            else -> 0xFF6C757D.toInt()
        }

    private fun dp(
        value: Int,
    ): Int =
        (
            value *
                resources.displayMetrics.density
            )
            .roundToInt()
}
