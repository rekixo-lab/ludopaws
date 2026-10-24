package com.rekixo.ludopaws.feature.characters.content.cat

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

/** Ear roots and jaw are hinged; the eyes and fine whiskers follow the skull. */
internal object CatFaceRenderer {
    fun draw(ctx: CharacterDrawContext,parent: FloatArray,pose: CharacterPose)=with(ctx) {
        surface=CharacterSurface.CAT_COAT
        catMesh(parent,CatGeometry.head,CAT_SILVER_FUR)
        surface=CharacterSurface.SOFT_FUR
        catMesh(parent,CatGeometry.muzzle,CAT_CREAM)
        for(i in 0..1) {
            val side=if(i==0) -1f else 1f
            eye(parent,side)
            val ear=rigTransforms[3]
            System.arraycopy(parent,0,ear,0,16)
            val t=life?.timeSeconds ?: 0f
            val twitch=if(life!=null) sin(t*11f+side)*max(0f,sin(t*.61f+side)).pow(16)*4f else 0f
            Matrix.translateM(ear,0,side*.285f,1.16f,-.04f)
            val earAngle=if(pose.primaryAppendageDegrees<0f) 12f-pose.primaryAppendageDegrees*1.4f else 12f-pose.primaryAppendageDegrees*.35f
            Matrix.rotateM(ear,0,-side*(earAngle+twitch),0f,0f,1f)
            Matrix.rotateM(ear,0,-6f,1f,0f,0f)
            surface=CharacterSurface.CAT_COAT
            catMesh(ear,CatGeometry.ear,CAT_SILVER_FUR,sx=side)
            surface=CharacterSurface.MATTE
            catMesh(ear,CatGeometry.ear,CAT_EAR_PINK,y=.025f,z=.060f,sx=side*.66f,sy=.73f,sz=.20f)
        }
        surface=CharacterSurface.NOSE
        catMesh(parent,CatGeometry.nose,CAT_NOSE_PINK,y=.797f,z=.470f)
        surface=CharacterSurface.MATTE
        drawPart(parent,0f,.727f,.474f,.004f,.027f,.006f,CAT_PUPIL_DARK)
        for(side in -1..1 step 2) {
            drawPart(parent,side*.038f,.705f,.460f,.043f,.006f,.009f,CAT_PUPIL_DARK,rotateZ=side*14f)
            // Three slim, fanned whiskers; no thick bars through the face.
            for(i in 0..2) drawPart(parent,side*.315f,.745f-i*.035f,.427f,
                .145f,.0035f,.0035f,CAT_WHISKER,rotateZ=side*(12f-i*12f))
        }
        surface=CharacterSurface.CAT_COAT
        // Small tabby forehead marks remain close to the skull surface.
        for(i in -1..1) drawPart(parent,i*.105f,1.14f,.258f,
            .008f,.060f,.009f,CAT_STRIPE_DARK,rotateZ=i*18f)
        val open=((life?.mouthOpen ?: 0f)+pose.jawOpen).coerceIn(0f,1f)
        val jaw=rigTransforms[4]
        System.arraycopy(parent,0,jaw,0,16)
        Matrix.translateM(jaw,0,0f,.73f,.19f)
        Matrix.rotateM(jaw,0,-open*25f,1f,0f,0f)
        Matrix.translateM(jaw,0,0f,-.73f,-.19f)
        surface=CharacterSurface.MATTE
        drawPart(parent,0f,.663f,.422f,.12f,.006f+open*.038f,.020f,CAT_PUPIL_DARK)
        surface=CharacterSurface.SOFT_FUR
        catMesh(jaw,CatGeometry.jaw,CAT_CREAM)
        if(open>.16f) {
            surface=CharacterSurface.NOSE
            drawPart(jaw,0f,.658f,.419f,.048f,.012f,.037f,CAT_NOSE_PINK)
        }
    }
    private fun CharacterDrawContext.eye(parent: FloatArray,side: Float) {
        val x=side*.215f
        val open=life?.blink ?: 1f
        val gx=(life?.gazeX ?: 0f)*.45f
        val gy=(life?.gazeY ?: 0f)*.45f
        surface=CharacterSurface.CAT_COAT
        drawPart(parent,x,1.015f,.268f,.116f,.122f,.051f,CAT_STRIPE_DARK,rotateZ=-side*9f)
        surface=CharacterSurface.EYE
        drawPart(parent,x,1.015f,.297f,.103f,.108f*open,.034f,CAT_EYE_GREEN,rotateZ=-side*9f)
        drawPart(parent,x+gx,1.015f+gy*open,.327f,.025f,.084f*open,.014f,CAT_PUPIL_DARK)
        if(open>.3f) drawPart(parent,x+gx-.022f,1.05f+gy,.339f,.018f,.022f*open,.007f,WHITE)
    }
}
