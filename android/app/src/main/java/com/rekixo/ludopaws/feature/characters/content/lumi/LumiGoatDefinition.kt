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
            groundPivotY = -.96f,
            footBaselinePerRadius = 1.18f,
        )
    }
}

/** Lightweight fallback only while the production 3D portrait is rendering. */
internal object LumiGoatAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFF995E3C.toInt()
        canvas.drawOval(RectF(20f, 9f, 44f, 39f), fill)
        canvas.drawOval(RectF(56f, 9f, 80f, 39f), fill)
        fill.color = 0xFFFFF6EF.toInt()
        canvas.drawOval(RectF(5f, 33f, 39f, 54f), fill)
        canvas.drawOval(RectF(61f, 33f, 95f, 54f), fill)
        canvas.drawOval(RectF(22f, 22f, 78f, 85f), fill)
        fill.color = 0xFFF7A6AA.toInt()
        canvas.drawOval(RectF(10f, 37f, 29f, 47f), fill)
        canvas.drawOval(RectF(71f, 37f, 90f, 47f), fill)
        fill.color = 0xFF9E561E.toInt()
        canvas.drawOval(RectF(30f, 42f, 47f, 65f), fill)
        canvas.drawOval(RectF(53f, 42f, 70f, 65f), fill)
        drawEye(canvas, 39f, 54f)
        drawEye(canvas, 61f, 54f)
        fill.color = 0xFFF48F9C.toInt()
        canvas.drawOval(RectF(43f, 65f, 57f, 74f), fill)
    }
}
