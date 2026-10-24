package com.rekixo.ludopaws.feature.characters.content.lumi

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.content.goat.GoatDefinition
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationSet
import com.rekixo.ludopaws.feature.characters.domain.model.VoiceSet
import com.rekixo.ludopaws.feature.characters.rendering.CharacterAvatarContext
import com.rekixo.ludopaws.feature.characters.rendering.CharacterAvatarRenderer

/** Separate starter character; the original Goat and saved Goat selections stay intact. */
object LumiGoatDefinition {
    val content = GoatDefinition.content.let { goat ->
        goat.copy(
            character = goat.character.copy(
                id = "goat_lumi", displayName = "Lumi Goat",
                voiceSetId = "goat_lumi_default", animationSetId = "goat_lumi_default",
            ),
            voiceSet = VoiceSet("goat_lumi_default", goat.character.species),
            animationSet = AnimationSet("goat_lumi_default"),
            storeDisplayOrder = 6,
            // .42 + (-.96 - .42) * .5: feet follow the shortened leg rig.
            groundPivotY = -.27f,
            footBaselinePerRadius = 1.18f,
        )
    }
}

/** Lightweight fallback only while the production 3D portrait is rendering. */
internal object LumiGoatAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFFA79B85.toInt()
        canvas.drawOval(RectF(37f, 12f, 44f, 31f), fill)
        canvas.drawOval(RectF(56f, 12f, 63f, 31f), fill)
        fill.color = 0xFFFFF6EF.toInt()
        canvas.drawOval(RectF(5f, 33f, 39f, 54f), fill)
        canvas.drawOval(RectF(61f, 33f, 95f, 54f), fill)
        canvas.drawOval(RectF(29f, 22f, 71f, 85f), fill)
        fill.color = 0xFF9C968C.toInt()
        canvas.drawOval(RectF(10f, 37f, 29f, 47f), fill)
        canvas.drawOval(RectF(71f, 37f, 90f, 47f), fill)
        fill.color = 0xFF3C3934.toInt()
        canvas.drawOval(RectF(35f, 43f, 43f, 50f), fill)
        canvas.drawOval(RectF(57f, 43f, 65f, 50f), fill)
        fill.color = 0xFF57564E.toInt()
        canvas.drawOval(RectF(43f, 67f, 57f, 76f), fill)
    }
}
