package com.rekixo.ludopaws.feature.characters.content.donkey

import kotlin.math.*

/** Allocation-free foot path. Diagonal pairs share phase; planted feet sweep backward as the body advances. */
internal object DonkeyGait {
    fun foot(rear: Boolean, side: Float, phase: Float, weight: Float, out: FloatArray) {
        val w=weight.coerceIn(0f,1f)
        val offset=if ((rear && side>0f) || (!rear && side<0f)) .5f else 0f
        val angle=(phase+offset)*2f*PI.toFloat()
        val swing=max(0f,sin(angle))
        out[0]=DonkeyLegRig.GROUND_ANKLE_Y + swing.pow(1.35f)*.245f*w
        out[1]=(if(rear) -.475f else .335f) - cos(angle)*.185f*w
        // Tucked toe clears the ground, then extends before contact; stance stays flat.
        out[2]=(sin(angle*2f)*18f+sin(angle)*15f)*swing*w
    }
}
