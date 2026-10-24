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
        val x=side*.16f
        val open=life?.blink ?: 1f
        val gx=(life?.gazeX ?: 0f)*.45f
        val gy=(life?.gazeY ?: 0f)*.45f
        surface=CharacterSurface.CAT_COAT
        drawPart(parent,x,1.015f,.247f,.088f,.092f,.029f,CAT_STRIPE_DARK,rotateZ=-side*9f)
        surface=CharacterSurface.EYE
        drawPart(parent,x,1.015f,.270f,.077f,.085f*open,.022f,CAT_EYE_GREEN,rotateZ=-side*9f)
        drawPart(parent,x+gx,1.015f+gy*open,.290f,.050f,.064f*open,.010f,CAT_PUPIL_DARK)
        if(open>.3f) drawPart(parent,x+gx-.017f,1.043f+gy,.302f,.013f,.016f*open,.005f,WHITE)
    }
}
