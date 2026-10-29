package com.rekixo.ludopaws.feature.characters.content.donkey

import android.opengl.Matrix
import kotlin.math.*
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*

/** One continuous torso/skull, four connected IK chains and articulated face/tail. */
internal object DonkeyRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = Donkey3DMotionTimeline.stepTravelProgress(progress)
    override fun sample(
        motion: CharacterMotion,
        progress: Float,
        hopPlayback: CharacterHopPlayback,
    ): CharacterPose {
        val anatomyProgress = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else progress
        val p = Donkey3DMotionTimeline.sample(Donkey3DMotion.valueOf(motion.name), anatomyProgress)
        return CharacterPose(
            stridePhase = p.stridePhase, strideWeight = p.strideWeight,
            bodyRollDegrees = p.bodyRollDegrees,
            liftY = p.liftY, bodyYawDegrees = p.bodyYawDegrees, headTiltDegrees = p.headTiltDegrees,
            legSwingDegrees = p.legSwingDegrees, legTuckDegrees = p.legTuckDegrees,
            primaryAppendageDegrees = p.earTwitchDegrees, secondaryAppendageDegrees = p.tailSwayDegrees,
            bodyPitchDegrees = p.bodyPitchDegrees, neckPitchDegrees = p.neckPitchDegrees,
            bodyCrouch = p.bodyCrouch,
            frontLegTuckDegrees = p.frontLegTuckDegrees, rearLegTuckDegrees = p.rearLegTuckDegrees,
            frontLegSwingDegrees = p.frontLegSwingDegrees, rearLegSwingDegrees = p.rearLegSwingDegrees,
        ).let { CharacterHopDynamics.apply(it, motion, progress, .48f, hopPlayback) }
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val previous = surface
        try {
            val rig = rigTransforms[0]
            System.arraycopy(parent, 0, rig, 0, 16)
            Matrix.rotateM(rig, 0, -32f, 0f, 1f, 0f)
            val time=life?.timeSeconds ?: 0f
            val idle=life?.idleWeight ?: 0f
            val shift=sin(time*.72f)*.018f*idle
            val roll=pose.bodyRollDegrees+sin(time*.72f)*1.2f*idle
            // The shared jump arc owns vertical motion; fast gait oscillations added a tremor.
            val heave=0f
            legs(rig, pose, shift, roll, heave)
            val torso = rigTransforms[1]
            System.arraycopy(rig, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, shift, -pose.bodyCrouch+heave, 0f)
            Matrix.rotateM(torso, 0, roll, 0f, 0f, 1f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            val breath = 1f + (life?.breath ?: 0f)
            surface = CharacterSurface.SOFT_FUR
            donkeyMesh(torso, DonkeyGeometry.body, DONKEY_COAT, sx = breath, sz = breath)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .38f, 0f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.38f, 0f)
            surface = CharacterSurface.SOFT_FUR
            donkeyMesh(neck, DonkeyGeometry.neck, DONKEY_COAT)
            prepareHead(neck, .82f, pose.headTiltDegrees)
            // Keep room above the forehead for the long expressive ears.
            Matrix.translateM(head,0,0f,.73f,.20f)
            Matrix.rotateM(head,0,8f,0f,1f,0f)
            Matrix.scaleM(head,0,1.04f,1.01f,1.09f)
            Matrix.translateM(head,0,0f,-.94f,0f)
            DonkeyFaceRenderer.draw(this, head, pose)
            tail(torso, pose.secondaryAppendageDegrees)
        } finally {
            surface = previous
            limbBendY = 0f
            limbBendZ = 0f
        }
    }

    private fun CharacterDrawContext.legs(parent: FloatArray, pose: CharacterPose,
        shift: Float, roll: Float, heave: Float) {
        val pitch=pose.bodyPitchDegrees*PI.toFloat()/180f
        val rollRad=roll*PI.toFloat()/180f
        val time=life?.timeSeconds ?: 0f
        for (leg in 0 until 4) {
            val rear=leg<2
            val side=if(leg%2==0) -1f else 1f
            val x=side*(if(rear) .275f else .24f)
            var hoofPitch=0f
            var footY=DonkeyLegRig.GROUND_ANKLE_Y
            var footZ=if(rear) -.475f else .335f
            if(pose.strideWeight>0f) {
                DonkeyGait.foot(rear,side,pose.stridePhase,pose.strideWeight,rigScratch)
                footY=rigScratch[0];footZ=rigScratch[1];hoofPitch=rigScratch[2]
            } else {
                val tuck=(if(rear) pose.rearLegTuckDegrees else pose.frontLegTuckDegrees)
                    ?: pose.legTuckDegrees
                footY+=(tuck/38f).coerceIn(0f,1f)*.22f
                // Occasionally rest one hind hoof, with the other three carrying the weight.
                val rest=if(rear && side>0f)
                    max(0f,sin(time*.57f)).pow(12)*(life?.idleWeight ?: 0f) else 0f
                footY+=rest*.055f
                footZ-=rest*.025f
                hoofPitch=rest*16f
            }
            val hy=if(rear) -.26f else -.24f
            val hz=if(rear) -.43f else .30f
            // The hip follows the torso; stance hooves remain on the ground.
            val hipY=(hy*cos(pitch)-hz*sin(pitch))*cos(rollRad)+x*sin(rollRad)-pose.bodyCrouch+heave
            val hipZ=hy*sin(pitch)+hz*cos(pitch)
            DonkeyLegRig.solveFoot(rear,hipY,hipZ,footY,footZ,rigScratch)
            val ay=rigScratch[4];val az=rigScratch[5]
            val legFrame=rigTransforms[6]
            System.arraycopy(parent,0,legFrame,0,16)
            // Upper leg follows lateral weight shift; the small correction fades toward the hoof.
            Matrix.translateM(legFrame,0,shift*.35f,0f,0f)
            surface=CharacterSurface.SOFT_FUR
            donkeyBone(legFrame,DonkeyGeometry.leg,x,hipY,hipZ,ay,az,
                if(rear) .084f else .077f,DONKEY_COAT)
            surface=CharacterSurface.HOOF
            donkeyMesh(legFrame,DonkeyGeometry.hoof,DONKEY_HOOF,x,ay-.03f,az+.025f,
                sx=.90f,sz=.94f,rx=hoofPitch)
        }
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, sway: Float) {
        val chain=rigTransforms[5]
        System.arraycopy(parent,0,chain,0,16)
        Matrix.translateM(chain,0,.09f,-.02f,-.72f)
        Matrix.rotateM(chain,0,162f,1f,0f,0f)
        val time=life?.timeSeconds ?: 0f
        val swish=sin(time*6f)*max(0f,sin(time*.48f)).pow(12)*22f*(life?.idleWeight ?: 0f)
        Matrix.rotateM(chain,0,sway*.45f+swish,0f,0f,1f)
        surface=CharacterSurface.SOFT_FUR
        var radius=.045f
        for(i in 0..4) {
            Matrix.rotateM(chain,0,sway*(.10f+i*.025f),0f,0f,1f)
            Matrix.rotateM(chain,0,-5f,1f,0f,0f)
            val tuft=i==4
            val thickness=if(tuft) .075f else radius
            donkeyMesh(chain,if(tuft) DonkeyGeometry.tailTuft else DonkeyGeometry.tail,
                if(tuft) DONKEY_DARK else DONKEY_COAT,
                y=.075f,sx=thickness,sy=.16f,sz=thickness)
            Matrix.translateM(chain,0,0f,.145f,0f)
            radius*=.88f
        }
    }
}
