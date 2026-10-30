package com.rekixo.ludopaws.feature.characters.content.cat

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

/** Ear roots and jaw are hinged; the eyes and fine whiskers follow the skull. */
internal object CatFaceRenderer {
    fun draw(ctx: CharacterDrawContext,parent: FloatArray,pose: CharacterPose)=with(ctx) {
        surface=CharacterSurface.CAT_COAT
        catMesh(parent,CatGeometry.head,CAT_TABBY_FUR)
        surface=CharacterSurface.SOFT_FUR
        catMesh(parent,CatGeometry.muzzle,CAT_CREAM)
        for(i in 0..1) {
            val side=if(i==0) -1f else 1f
            eye(parent,side)
            val ear=rigTransforms[3]
            System.arraycopy(parent,0,ear,0,16)
            val t=life?.timeSeconds ?: 0f
            val twitch=if(life!=null) sin(t*11f+side)*max(0f,sin(t*.61f+side)).pow(16)*4f else 0f
            Matrix.translateM(ear,0,side*.225f,1.15f,-.04f)
            val earAngle=if(pose.primaryAppendageDegrees<0f) 12f-pose.primaryAppendageDegrees*1.4f else 12f-pose.primaryAppendageDegrees*.35f
            Matrix.rotateM(ear,0,-side*(earAngle+twitch),0f,0f,1f)
            Matrix.rotateM(ear,0,-6f,1f,0f,0f)
            surface=CharacterSurface.CAT_COAT
            catMesh(ear,CatGeometry.ear,CAT_TABBY_FUR,sx=side)
            surface=CharacterSurface.MATTE
            catMesh(ear,CatGeometry.ear,CAT_EAR_PINK,y=.025f,z=.060f,sx=side*.66f,sy=.73f,sz=.20f)
        }
        surface=CharacterSurface.NOSE
        catMesh(parent,CatGeometry.nose,CAT_NOSE_PINK,y=.795f,z=.350f)
        surface=CharacterSurface.MATTE
        drawPart(parent,0f,.727f,.361f,.003f,.021f,.004f,CAT_PUPIL_DARK)
        for(side in -1..1 step 2) {
            drawPart(parent,side*.025f,.705f,.353f,.029f,.004f,.006f,CAT_PUPIL_DARK,rotateZ=side*14f)
            // Three slim, fanned whiskers; no thick bars through the face.
            for(i in 0..2) drawPart(parent,side*.23f,.745f-i*.028f,.33f,
                .105f,.0025f,.0025f,CAT_WHISKER,rotateZ=side*(12f-i*12f))
        }
        val open=((life?.mouthOpen ?: 0f)+pose.jawOpen).coerceIn(0f,1f)
        val jaw=rigTransforms[4]
        System.arraycopy(parent,0,jaw,0,16)
        Matrix.translateM(jaw,0,0f,.73f,.19f)
        Matrix.rotateM(jaw,0,-open*25f,1f,0f,0f)
        Matrix.translateM(jaw,0,0f,-.73f,-.19f)
        surface=CharacterSurface.MATTE
        drawPart(parent,0f,.663f,.332f,.082f,.004f+open*.028f,.012f,CAT_PUPIL_DARK)
        if (open > .05f) {
            surface=CharacterSurface.SOFT_FUR
            catMesh(jaw,CatGeometry.jaw,CAT_CREAM)
        }
        if(open>.16f) {
            surface=CharacterSurface.NOSE
            drawPart(jaw,0f,.658f,.335f,.037f,.010f,.025f,CAT_NOSE_PINK)
        }
    }
    private fun CharacterDrawContext.eye(parent: FloatArray,side: Float) {
        val open=(life?.blink ?: 1f).coerceIn(0f,1f)
        val gx=((life?.gazeX ?: 0f)*.32f).coerceIn(-.012f,.012f)
        val gy=((life?.gazeY ?: 0f)*.32f).coerceIn(-.009f,.009f)*open
        val eyeRig=rigTransforms[5]
        System.arraycopy(parent,0,eyeRig,0,16)
        Matrix.translateM(eyeRig,0,side*.16f,1.015f,.224f)
        // Follow the skull's curved surface instead of floating a flat socket in front.
        Matrix.rotateM(eyeRig,0,side*33f,0f,1f,0f)
        Matrix.rotateM(eyeRig,0,-side*9f,0f,0f,1f)
        surface=CharacterSurface.CAT_COAT
        // Fur-colored lids cover the eye during a blink; no permanent dark eye hole.
        drawPart(eyeRig,0f,0f,0f,.088f,.094f,.024f,CAT_TABBY_FUR)
        if(open<=.04f) return
        surface=CharacterSurface.EYE
        drawPart(eyeRig,0f,0f,.020f,.074f,.080f*open,.018f,CAT_EYE_GREEN)
        // A feline slit leaves the green iris readable, rather than a hollow black socket.
        drawPart(eyeRig,gx,gy,.036f,.020f,.059f*open,.008f,CAT_PUPIL_DARK)
        if(open>.3f) drawPart(eyeRig,gx-.013f,gy+.026f*open,.045f,
            .010f,.013f*open,.004f,WHITE)
    }
}
