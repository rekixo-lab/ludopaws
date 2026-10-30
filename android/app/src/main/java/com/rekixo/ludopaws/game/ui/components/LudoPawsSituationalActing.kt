package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.domain.reaction.GameMomentType
import kotlin.math.PI
import kotlin.math.sin

/** Contextual accents on real committed events, without route/scale changes. */
internal object LudoPawsSituationalActing {
    fun pose(base: LudoPaws3DReactionPose, moment: GameMomentType?, progress: Float): LudoPaws3DReactionPose {
        val p = progress.coerceIn(0f, 1f)
        val arc = sin(p * PI).toFloat().coerceAtLeast(0f)
        val wave = sin(p * PI * 2).toFloat()
        val neck = when (moment) {
            GameMomentType.TOKEN_LEFT_YARD -> -6f * arc
            GameMomentType.SAFE_REACHED -> 4f * arc
            GameMomentType.EXACT_HOME_MISS -> 10f * arc
            GameMomentType.POOR_ROLL_STREAK, GameMomentType.NO_LEGAL_MOVE -> 6f * arc
            GameMomentType.HOME_REACHED -> -7f * arc
            else -> 0f
        }
        return base.copy(
            neckPitchDegrees = base.neckPitchDegrees + neck,
            headTiltDegrees = base.headTiltDegrees + when (moment) {
                GameMomentType.EXACT_HOME_MISS -> 7f * arc
                GameMomentType.THIRD_SIX_FORFEIT -> 5f * wave * arc
                GameMomentType.CAPTURE_MADE -> -4f * arc
                else -> 0f
            },
        )
    }

    fun observer(progress: Float): LudoPaws3DReactionPose {
        val p = progress.coerceIn(0f, 1f)
        val arc = sin(p * PI).toFloat().coerceAtLeast(0f)
        return LudoPaws3DReactionPose(headTiltDegrees = 4f * sin(p * PI * 2).toFloat() * arc, neckPitchDegrees = -3f * arc)
    }
}
