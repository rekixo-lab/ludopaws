package com.rekixo.ludopaws.feature.characters.content.donkey

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

/** Long independently flicking ears and a broad pale muzzle distinguish this equine face. */
internal object DonkeyFaceRenderer {
    fun draw(ctx: CharacterDrawContext,parent: FloatArray,pose: CharacterPose)=with(ctx) {
        surface=CharacterSurface.SOFT_FUR
        donkeyMesh(parent,DonkeyGeometry.head,DONKEY_COAT)
        donkeyMesh(parent,DonkeyGeometry.mane,DONKEY_DARK)
        surface = CharacterSurface.COAT
        donkeyMesh(parent, DonkeyGeometry.forelock, DONKEY_DARK, x = -.035f, y = 1.26f, z = .106f, rz = -11f)
        donkeyMesh(parent, DonkeyGeometry.forelock, DONKEY_MANELIGHT,
            x = .019f, y = 1.245f, z = .123f, sx = .68f, sy = .88f, sz = .68f, rz = 8f)
        for(i in 0..1) {
            val side=if(i==0) -1f else 1f
            val ear=rigTransforms[3]
            System.arraycopy(parent,0,ear,0,16)
            val t=life?.timeSeconds ?: 0f
            val flick = sin(t * 8f + side) * max(0f, sin(t * .65f + side)).pow(12) * 8f *
                (life?.idleWeight ?: 0f)
            Matrix.translateM(ear,0,side*.20f,1.14f,-.045f)
            Matrix.rotateM(ear,0,-side*(13f-pose.primaryAppendageDegrees*.50f+flick+(if(side<0f) 24f else -8f)*pose.gestureWeight),0f,0f,1f)
            Matrix.rotateM(ear,0,side*5f+flick*.45f-pose.neckPitchDegrees*.45f,1f,0f,0f)
            Matrix.rotateM(ear, 0, side * (8f + flick * .5f), 0f, 1f, 0f)
            surface=CharacterSurface.SOFT_FUR
            donkeyMesh(ear,DonkeyGeometry.ear,DONKEY_EAR_RIM)
            donkeyMesh(ear,DonkeyGeometry.ear,DONKEY_COAT,y=.009f,z=.022f,sx=.88f,sy=.97f,sz=.70f)
            donkeyMesh(ear,DonkeyGeometry.ear,DONKEY_EAR_INNER,y=.055f,z=.052f,sx=.57f,sy=.80f,sz=.18f)
        }
        surface=CharacterSurface.SOFT_FUR
        donkeyMesh(parent,DonkeyGeometry.muzzle,DONKEY_CREAM)
        for(i in 0..1) {
            val side=if(i==0) -1f else 1f
            val x=side*.216f
            val open=life?.blink ?: 1f
            val gx=(life?.gazeX ?: 0f)*.35f
            val gy=(life?.gazeY ?: 0f)*.35f
            surface=CharacterSurface.SOFT_FUR
            drawPart(parent,x,1.015f,.228f,.092f,.104f,.037f,DONKEY_CREAM)
            surface=CharacterSurface.MATTE
            drawPart(parent,x,1.017f,.250f,.080f,.093f,.027f,DONKEY_EAR_RIM)
            surface=CharacterSurface.EYE
            drawPart(parent,x,1.017f,.269f,.074f,.087f*open,.021f,DONKEY_EYE_AMBER)
            drawPart(parent,x+gx,1.017f+gy*open,.288f,.049f,.066f*open,.009f,DONKEY_PUPIL)
            if(open>.3f) drawPart(parent,x+gx-.019f,1.048f+gy,.298f,.018f,.021f*open,.006f,WHITE)
            // Separate nostrils on a muzzle rather than a feline triangular nose.
            surface=CharacterSurface.NOSE
            drawPart(parent, side * .142f, .738f, .604f, .042f, .029f, .017f,
                DONKEY_NOSTRIL_RIM, rotateZ = side * 24f)
            drawPart(parent, side * .142f, .741f, .618f, .026f, .018f, .009f,
                DONKEY_NOSE, rotateZ = side * 24f)
            surface=CharacterSurface.MATTE
            drawPart(parent,side*.083f,.590f,.603f,.083f,.004f,.005f,DONKEY_NOSE,rotateZ=side*9f)
            drawPart(parent,x,1.143f,.208f,.053f,.009f,.012f,DONKEY_DARK,rotateZ=-side*9f)
        }
        val chew=max(0f,sin((life?.timeSeconds ?: 0f)*.43f)).pow(18)*
            (.5f+.5f*sin((life?.timeSeconds ?: 0f)*8f))*.16f*(life?.idleWeight ?: 0f)
        val open=((life?.mouthOpen ?: 0f)+chew+pose.jawOpen*.65f).coerceIn(0f,1f)
        surface=CharacterSurface.MATTE
        drawPart(parent,0f,.575f,.591f,.14f,.003f+open*.040f,.015f,DONKEY_PUPIL)
        val jaw=rigTransforms[4]
        System.arraycopy(parent,0,jaw,0,16)
        Matrix.translateM(jaw,0,0f,.62f,.22f)
        Matrix.rotateM(jaw,0,-open*24f,1f,0f,0f)
        Matrix.translateM(jaw,0,0f,-.62f,-.22f)
        surface=CharacterSurface.SOFT_FUR
        donkeyMesh(jaw,DonkeyGeometry.jaw,DONKEY_CREAM)
        if(open>.24f) {
            surface=CharacterSurface.MATTE
            drawPart(parent,0f,.579f,.606f,.060f,.019f,.012f,WHITE)
            surface=CharacterSurface.NOSE
            drawPart(jaw,0f,.541f,.591f,.065f,.012f,.033f,DONKEY_EAR_INNER)
        }
    }
}
