package com.rekixo.ludopaws.feature.profile.presentation.components

import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.transparentDarkBlueStrokeBackground
import com.rekixo.ludopaws.ui.dialogs.baseDialog
import com.rekixo.ludopaws.ui.dialogs.sizeDialog
import com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity
import com.rekixo.ludopaws.feature.profile.presentation.BadgesActivity

internal fun ProfileActivity.profilePanel():
    LinearLayout =
    LinearLayout(this).apply {
        orientation =
            LinearLayout.VERTICAL
        setPadding(
            dp(14),
            dp(14),
            dp(14),
            dp(14),
        )
        background =
            plywoodBackground(this@profilePanel)
        elevation =
            dp(4)
                .toFloat()
    }

internal fun ProfileActivity.profileTitle(
    text: String,
):
    TextView =
    TextView(this).apply {
        this.text =
            text
        textSize =
            20f
        setTypeface(
            Typeface.DEFAULT_BOLD,
        )
        setTextColor(
            Color.WHITE,
        )
    }

internal fun ProfileActivity.profileBody(
    text: String,
    size: Float = 13f,
):
    TextView =
    TextView(this).apply {
        this.text =
            text
        textSize =
            size
        setTextColor(
            0xFFE8F5FF.toInt(),
        )
    }

internal fun ProfileActivity.showNameEditor() {
    val current = profileStore.snapshot()
    val compact = LudoProofTheme.isCompactWidth(this)
    val dialog = baseDialog(this)

    val panel =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(if (compact) 18 else 22),
                dp(if (compact) 18 else 22),
                dp(if (compact) 18 else 22),
                dp(if (compact) 20 else 22),
            )
            background = plywoodBackground(this@showNameEditor, cornerRadiusDp = 20f)
            elevation = dp(8).toFloat()
        }

    panel.addView(
        TextView(this).apply {
            text = "EDIT NICKNAME"
            textSize = if (compact) 19f else 22f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(0xFFFFD45E.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            letterSpacing = 0.035f
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    panel.addView(
        TextView(this).apply {
            text = "Use 6–7 characters for your device nickname. Online names are assigned automatically."
            textSize = if (compact) 12f else 13f
            setTextColor(0xFFD9EEFF.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, dp(7), 0, 0)
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    val input =
        EditText(this).apply {
            setText(current.displayName)
            selectAll()
            isSingleLine = true
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9ACBEA.toInt())
            hint = "6–7 character nickname"
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            background = transparentDarkBlueStrokeBackground(
                this@showNameEditor,
                cornerRadiusDp = 12f,
            )
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
    // New nicknames are limited to seven characters; Save validates the six-character minimum.
    input.filters = arrayOf(InputFilter.LengthFilter(7))
    panel.addView(
        input,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(if (compact) 50 else 54),
        ).apply {
            topMargin = dp(16)
        },
    )

    val validationMessage =
        TextView(this).apply {
            text = "Nickname must be 6–7 characters."
            textSize = 11f
            setTextColor(0xFFFFA278.toInt())
            visibility = View.GONE
            setPadding(dp(2), dp(6), dp(2), 0)
        }
    panel.addView(
        validationMessage,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(4)
        },
    )

    val actions =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
    actions.addView(
        Button(this).apply {
            text = "CANCEL"
            textSize = 12f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(0xFFEAF7FF.toInt())
            minWidth = 0
            minHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            background = transparentDarkBlueStrokeBackground(
                this@showNameEditor,
                cornerRadiusDp = 13f,
            )
            setOnClickListener { dialog.dismiss() }
        },
        LinearLayout.LayoutParams(
            0,
            dp(46),
            1f,
        ).apply {
            marginEnd = dp(6)
        },
    )
    actions.addView(
        Button(this).apply {
            text = "SAVE NAME"
            textSize = 12f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(0xFF17304A.toInt())
            minWidth = 0
            minHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            background = LudoProofTheme.rounded(
                0xFFFFD45E.toInt(),
                13f,
                0xFFFFF0A6.toInt(),
                1.5f,
                this@showNameEditor,
            )
            setOnClickListener {
                val nickname = input.text.toString().trim()
                    .replace(Regex("""\s+"""), " ")
                if (nickname.length !in 6..7) {
                    validationMessage.visibility = View.VISIBLE
                    input.requestFocus()
                    return@setOnClickListener
                }
                dialog.dismiss()
                profileStore.updateDisplayName(nickname)
                renderProfile()
            }
        },
        LinearLayout.LayoutParams(
            0,
            dp(46),
            1f,
        ).apply {
            marginStart = dp(6)
        },
    )
    panel.addView(
        actions,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(16)
        },
    )

    dialog.setContentView(
        panel,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )
    sizeDialog(dialog, if (compact) 0.9f else 0.72f)
    dialog.window?.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
    )
    dialog.show()
    input.requestFocus()
}
internal fun ProfileActivity.viewAllBadges() {
    startActivity(
        Intent(
            this,
            BadgesActivity::class.java,
        ),
    )
}

internal fun ProfileActivity.viewPurchases(
    body: String,
) {
    PlywoodAlertDialogBuilder(this)
        .setTitle(
            "Purchased Products",
        )
        .setMessage(
            body,
        )
        .setPositiveButton(
            "CLOSE",
            null,
        )
        .show()
}
