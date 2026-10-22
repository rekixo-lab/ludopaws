package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.shared.LudoPaws3DLifeMotion
import org.junit.Assert.*
import com.rekixo.ludopaws.feature.characters.rendering.CharacterPose
import org.junit.Test

class CharacterIdleTransitionTest {
    @Test fun landingFadesIdleGesturesInInsteadOfEnablingThemAtFullStrength() {
        val life=LudoPaws3DLifeMotion()
        val target=LudoPaws3DReactionPose()
        for(frame in 0..30) life.update(100_000L+frame*16L,397,false,0f,0f,target)
        assertEquals(0f,life.idleWeight,.0001f)
        life.update(100_496L,397,true,0f,0f,target)
        assertTrue(life.idleWeight in 0f.. .07f)
        assertTrue(life.idlePose(CharacterPose(headTiltDegrees=10f,bodyPitchDegrees=5f)).headTiltDegrees<.7f)
        for(frame in 32..100) life.update(100_000L+frame*16L,397,true,0f,0f,target)
        assertTrue(life.idleWeight>.98f)
        val before=life.idleWeight
        life.update(101_616L,397,false,0f,0f,target)
        assertTrue(life.idleWeight<before && life.idleWeight>.7f)
    }
}
