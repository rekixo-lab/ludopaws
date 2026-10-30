package com.rekixo.ludopaws.feature.profile.presentation.components

import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.profile.domain.CharacterMasteryPolicy
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileSnapshot
import com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity

internal fun ProfileActivity.profileMasteryPanel(profile: ProfileSnapshot): LinearLayout = profilePanel().apply {
    addView(profileTitle("Your Paws Collection").apply { setTextColor(0xFF382514.toInt()) })
    addView(profileBody("Play together to earn a new portrait frame. Progress stays with you; there is no daily reset.").apply { setTextColor(0xFF513A24.toInt()) })
    val used = profile.characterMatches.count { it.value > 0 }
    val best = profile.characterMatches.values.maxOrNull() ?: 0
    addView(profileBody("Next goals\nPlay 3 matches: ${profile.totalGames.coerceAtMost(3)}/3\nMeet 3 pals: ${used.coerceAtMost(3)}/3\nPlay 5 with a pal: ${best.coerceAtMost(5)}/5", 12f).apply {
        setPadding(0, dp(10), 0, dp(10))
        setTextColor(0xFF513A24.toInt())
    })
    CharacterDefinitions.all.forEach { definition ->
        val id = definition.character.id
        val matches = profile.characterMatches[id] ?: 0
        val next = CharacterMasteryPolicy.nextGoal(matches)
        val row = LinearLayout(this@profileMasteryPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(LudoPawsCharacterAvatarView(this@profileMasteryPanel).apply {
            bind(id, CharacterMasteryPolicy.frameColor(matches), highlighted = false)
            contentDescription = definition.character.displayName + " portrait frame"
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(TextView(this@profileMasteryPanel).apply {
            text = "${definition.character.displayName} · ${CharacterMasteryPolicy.tier(matches)}\n$matches matches" +
                (next?.let { " · next frame at $it" } ?: " · collection complete")
            textSize = 13f
            setTextColor(0xFF382514.toInt())
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(row)
    }
}
