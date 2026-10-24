package com.rekixo.ludopaws.feature.characters.content.lumi

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.content.goat.GoatRenderer
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring
import kotlin.math.*

/** Photo-inspired standing kid, with an independent head, listening ears and four moving feet. */
internal object LumiGoatRenderer : CharacterRenderer {
    private const val REST_BODY_YAW = 4f
    private const val LEG_LENGTH_SCALE = .5f
    private const val LEG_LENGTH_ANCHOR_Y = .42f
    override fun stepTravelProgress(progress: Float) = GoatRenderer.stepTravelProgress(progress)
    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback) =
        GoatRenderer.sample(motion, progress, hopPlayback)

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose,
        teamColor: LudoPawsTeamColor) = with(context) {
        val previous = surface
        val body = rigTransforms[0]
        System.arraycopy(parent, 0, body, 0, 16)
        val size = LumiGoatDefinition.MODEL_SIZE_MULTIPLIER
        Matrix.scaleM(body, 0, size, size, size)
        Matrix.rotateM(body, 0, REST_BODY_YAW, 0f, 1f, 0f)
        Matrix.translateM(body, 0, 0f, -pose.bodyCrouch * .45f, 0f)
        Matrix.rotateM(body, 0, pose.bodyPitchDegrees * .5f, 1f, 0f, 0f)
        Matrix.rotateM(body, 0, pose.bodyRollDegrees * .5f, 0f, 0f, 1f)
        val breath = 1f + (life?.breath ?: 0f)
        Matrix.scaleM(body, 0, breath, 1f, breath)
        val face = rigTransforms[1]
        System.arraycopy(body, 0, face, 0, 16)
        Matrix.translateM(face, 0, 0f, 1.0f, .20f)
        Matrix.rotateM(face, 0, (pose.headTiltDegrees + (life?.headTilt ?: 0f)) * .45f, 0f, 0f, 1f)
        Matrix.rotateM(face, 0, -REST_BODY_YAW, 0f, 1f, 0f)
        Matrix.rotateM(face, 0, (life?.headPitch ?: 0f) * .45f + pose.neckPitchDegrees * .3f, 1f, 0f, 0f)
        Matrix.translateM(face, 0, 0f, -1.0f, -.20f)
        try {
            for (rig in LumiRig.entries) {
                val transform = rigTransforms[2]
                System.arraycopy(if (rig.headAttached) face else body, 0, transform, 0, 16)
                when (rig) {
                    LumiRig.EYE_LEFT, LumiRig.EYE_RIGHT -> {
                        val side = if (rig == LumiRig.EYE_LEFT) -1f else 1f
                        Matrix.translateM(transform, 0, side * .188f, 1.066f, .30f)
                        Matrix.scaleM(transform, 0, 1f, (life?.blink ?: 1f).coerceAtLeast(.035f), 1f)
                        Matrix.translateM(transform, 0, (life?.gazeX ?: 0f) * .06f,
                            (life?.gazeY ?: 0f) * .05f, 0f)
                        Matrix.translateM(transform, 0, -side * .188f, -1.066f, -.30f)
                    }
                    LumiRig.EAR_LEFT, LumiRig.EAR_RIGHT -> {
                        val side = if (rig == LumiRig.EAR_LEFT) -1f else 1f
                        Matrix.translateM(transform, 0, side * .19f, 1.17f, .09f)
                        Matrix.rotateM(transform, 0, side * pose.primaryAppendageDegrees * .45f, 0f, 0f, 1f)
                        Matrix.translateM(transform, 0, -side * .19f, -1.17f, -.09f)
                    }
                    LumiRig.FRONT_LEFT, LumiRig.FRONT_RIGHT, LumiRig.REAR_LEFT, LumiRig.REAR_RIGHT -> {
                        val front = rig == LumiRig.FRONT_LEFT || rig == LumiRig.FRONT_RIGHT
                        val left = rig == LumiRig.FRONT_LEFT || rig == LumiRig.REAR_LEFT
                        val side = if (left) -1f else 1f
                        // Swing from the embedded shoulder/hip, not the exposed middle of the leg.
                        val x = side * if (front) .085f else .13f
                        val y = if (front) .72f else .65f
                        val z = if (front) -.01f else -.485f
                        // Preserve the approved half-length legs and planted feet as the joint moves upward.
                        val shortenedPivotY = LEG_LENGTH_ANCHOR_Y + (y - LEG_LENGTH_ANCHOR_Y) * LEG_LENGTH_SCALE
                        Matrix.translateM(transform, 0, x, shortenedPivotY, z)
                        val swing = if (front) pose.frontLegSwingDegrees else pose.rearLegSwingDegrees
                        Matrix.rotateM(transform, 0, (swing ?: pose.legSwingDegrees) * side * .7f,
                            1f, 0f, 0f)
                        Matrix.scaleM(transform, 0, 1f, LEG_LENGTH_SCALE, 1f)
                        Matrix.translateM(transform, 0, -x, -y, -z)
                    }
                    LumiRig.TAIL -> {
                        Matrix.translateM(transform, 0, 0f, .65f, -.77f)
                        Matrix.rotateM(transform, 0, pose.tertiaryAppendageDegrees, 0f, 1f, 0f)
                        Matrix.translateM(transform, 0, 0f, -.65f, .77f)
                    }
                    LumiRig.MOUTH -> {
                        Matrix.translateM(transform, 0, 0f, .81f, .48f)
                        Matrix.scaleM(transform, 0, 1f, 1f + pose.jawOpen * .035f, 1f)
                        Matrix.translateM(transform, 0, 0f, -.81f, -.48f)
                    }
                    else -> Unit
                }
                LumiGoatGeometry.batches[rig]?.forEach { batch ->
                    surface = batch.tint.surface
                    drawGeometry(transform, batch.mesh, 0f, 0f, 0f, 1f, 1f, 1f,
                        batch.tint.color)
                }
            }
        } finally { surface = previous }
    }
}

internal enum class LumiRig(val headAttached: Boolean = false) {
    TAIL, REAR_LEFT, REAR_RIGHT, BODY, FRONT_LEFT, FRONT_RIGHT,
    HEAD(true), EAR_LEFT(true), EAR_RIGHT(true), MOUTH(true), EYE_LEFT(true), EYE_RIGHT(true),
}

internal enum class LumiTint(hex: Int, val surface: CharacterSurface) {
    FUR(0xF5F1E8, CharacterSurface.LUMI_FUR), INNER(0xB2A496, CharacterSurface.LUMI_FUR),
    HORN(0xA79B85, CharacterSurface.LUMI_GLOSS), HOOF(0x393934, CharacterSurface.LUMI_GLOSS),
    LID(0x59564C, CharacterSurface.LUMI_FUR), IRIS(0x594430, CharacterSurface.LUMI_EYE),
    PUPIL(0x171612, CharacterSurface.LUMI_EYE), GLINT(0xF8F6ED, CharacterSurface.LUMI_EYE),
    NOSE(0x57564E, CharacterSurface.LUMI_GLOSS), LIP(0x46443E, CharacterSurface.LUMI_GLOSS);
    val color = floatArrayOf(((hex shr 16) and 255) / 255f,
        ((hex shr 8) and 255) / 255f, (hex and 255) / 255f, 1f)
}

/** Continuous cross-sections replace the old cheek, belly and muzzle ellipsoids. */
internal object LumiGoatGeometry {
    data class Batch(val tint: LumiTint, val mesh: MeshGeometry)
    private data class Part(val rig: LumiRig, val tint: LumiTint, val mesh: MeshGeometry,
        val x: Float, val y: Float, val z: Float, val rx: Float, val rz: Float, val fur: Int)
    private val parts = buildList {
        fun loft(rig: LumiRig, tint: LumiTint, x: Float, y: Float, z: Float,
            rx: Float, rz: Float, fur: Int, points: FloatArray) {
            val rings = points.asList().chunked(5).map {
                Ring(it[0], it[1], it[2], it[3], it[4], if (tint == LumiTint.HOOF) .45f else 1f)
            }
            add(Part(rig, tint, MeshLoft.build(rings, 24, subdivisions = 3), x, y, z, rx, rz, fur))
        }
        loft(LumiRig.BODY, LumiTint.FUR, -.015f, .51f, -.28f, 90f, 0f, 2300, floatArrayOf(
            -.58f,0.0392f,0.0672f,0f,0f, -.49f,0.2016f,0.2688f,0f,0f,
            -.27f,0.2968f,0.3696f,0f,-.025f, .04f,0.3024f,0.3808f,0f,-.02f,
            .29f,0.28f,0.3584f,0f,0f, .48f,0.2016f,0.2576f,0f,0f, .57f,0.028f,0.056f,0f,0f))
        // The chest closes inside the barrel, above the leg gap, rather than hanging below it.
        loft(LumiRig.BODY, LumiTint.FUR, 0f, 0f, 0f, 0f, 0f, 1100, floatArrayOf(
            .30f,0.084f,0.1064f,0f,.08f, .44f,0.224f,0.2464f,0f,.13f,
            .66f,0.2352f,0.2352f,0f,.15f, .88f,0.1904f,0.1792f,0f,.18f, .99f,0.0784f,0.0896f,0f,.20f))
        loft(LumiRig.HEAD, LumiTint.FUR, 0f, 0f, 0f, 0f, 0f, 3500, floatArrayOf(
            .76f,.055f,.065f,0f,.40f, .81f,.135f,.10f,0f,.40f,
            .88f,.178f,.14f,0f,.34f, .99f,.232f,.18f,0f,.23f,
            1.08f,.25f,.20f,0f,.13f, 1.24f,.205f,.145f,0f,.09f,
            1.31f,.09f,.065f,0f,.075f, 1.33f,.012f,.014f,0f,.07f))
        loft(LumiRig.HEAD, LumiTint.NOSE, 0f, 0f, 0f, 0f, 0f, 0, floatArrayOf(
            .81f,.018f,.017f,0f,.493f, .837f,.063f,.034f,0f,.510f,
            .884f,.088f,.042f,0f,.484f, .918f,.062f,.020f,0f,.457f, .927f,.012f,.006f,0f,.447f))
        loft(LumiRig.MOUTH, LumiTint.LIP, 0f, 0f, 0f, 0f, 90f, 0, floatArrayOf(
            -.082f,.002f,.003f,.789f,.48f, -.048f,.003f,.004f,.773f,.495f,
            0f,.003f,.004f,.768f,.50f, .048f,.003f,.004f,.773f,.495f, .082f,.002f,.003f,.789f,.48f))
        loft(LumiRig.TAIL, LumiTint.FUR, 0f, .65f, -.77f, -28f, 0f, 450, floatArrayOf(
            -.04f,.045f,.055f,0f,0f, .04f,.073f,.075f,0f,0f,
            .16f,.055f,.050f,0f,-.025f, .24f,.008f,.009f,0f,-.05f))
        for (side in intArrayOf(-1, 1)) {
            val ear = if (side < 0) LumiRig.EAR_LEFT else LumiRig.EAR_RIGHT
            val eye = if (side < 0) LumiRig.EYE_LEFT else LumiRig.EYE_RIGHT
            val front = if (side < 0) LumiRig.FRONT_LEFT else LumiRig.FRONT_RIGHT
            val rear = if (side < 0) LumiRig.REAR_LEFT else LumiRig.REAR_RIGHT
            loft(ear, LumiTint.FUR, side*.19f, 1.17f, .09f, -6f, -side*61f, 400, floatArrayOf(
                0f,.014f,.016f,0f,0f, .10f,.075f,.027f,0f,.012f,
                .22f,.090f,.022f,0f,.015f, .34f,.052f,.014f,0f,.003f, .41f,.003f,.003f,0f,-.01f))
            loft(ear, LumiTint.INNER, side*.19f, 1.17f, .128f, -6f, -side*61f, 0, floatArrayOf(
                .055f,.008f,.004f,0f,0f, .14f,.050f,.006f,0f,.008f,
                .24f,.054f,.005f,0f,.008f, .33f,.024f,.004f,0f,0f, .365f,.002f,.002f,0f,-.004f))
            loft(LumiRig.HEAD, LumiTint.HORN, side*.13f, 1.265f, .035f, -12f, -side*9f, 0, floatArrayOf(
                0f,.034f,.037f,0f,0f, .06f,.030f,.031f,0f,-.004f,
                .10f,.019f,.020f,0f,-.018f, .14f,.004f,.005f,0f,-.032f))
            // Upper limbs sweep back into the barrel; their caps stay buried during a stride.
            // The lower leg tapers through the knee and pastern without a separate shoulder ball.
            loft(front, LumiTint.FUR, side*.18f, 0f, .18f, 0f, -side*2f, 650, floatArrayOf(
                -.86f,0.0528f,0.0624f,0f,.015f, -.67f,0.0576f,0.0624f,0f,.006f,
                -.49f,0.0648f,0.072f,-side*.004f,0f, -.27f,0.072f,0.084f,-side*.010f,-.010f,
                -.04f,0.0876f,0.114f,-side*.025f,-.045f, .19f,0.1104f,0.156f,-side*.050f,-.13f,
                .44f,0.1248f,0.192f,-side*.075f,-.22f, .72f,0.102f,0.168f,-side*.095f,-.24f,
                .86f,0.042f,0.084f,-side*.11f,-.27f))
            loft(rear, LumiTint.FUR, side*.225f, 0f, -.56f, 0f, side*2f, 600, floatArrayOf(
                -.85f,0.0504f,0.06f,0f,-.02f, -.62f,0.0552f,0.0624f,0f,-.008f,
                -.43f,0.0684f,0.0744f,-side*.008f,.023f, -.20f,0.0888f,0.114f,-side*.025f,.050f,
                .06f,0.1116f,0.156f,-side*.045f,.060f, .35f,0.126f,0.18f,-side*.065f,.065f,
                .65f,0.102f,0.156f,-side*.095f,.075f, .86f,0.036f,0.072f,-side*.115f,.080f))
            for (toe in intArrayOf(-1, 1)) {
                loft(front, LumiTint.HOOF, side*.18f+toe*.027f, -.90f, .202f, 0f, 0f, 0, floatArrayOf(
                    -.06f,0.0258f,0.0694f,0f,.012f, -.03f,0.028f,0.0728f,0f,.008f,
                    .03f,0.0258f,0.056f,0f,0f, .06f,0.0168f,0.037f,0f,-.005f))
                loft(rear, LumiTint.HOOF, side*.225f+toe*.026f, -.89f, -.57f, 0f, 0f, 0, floatArrayOf(
                    -.06f,0.0246f,0.065f,0f,.008f, -.02f,0.0269f,0.0672f,0f,.008f,
                    .04f,0.0224f,0.0504f,0f,0f, .06f,0.0134f,0.0314f,0f,-.005f))
            }
            loft(eye, LumiTint.LID, side*.188f, 1.066f, .286f, 0f, -side*13f, 0, floatArrayOf(
                -.042f,.006f,.005f,0f,0f, -.026f,.036f,.012f,0f,0f,
                0f,.047f,.017f,0f,0f, .028f,.034f,.011f,0f,0f, .042f,.004f,.004f,0f,0f))
            loft(eye, LumiTint.IRIS, side*.188f, 1.066f, .300f, 0f, -side*13f, 0, floatArrayOf(
                -.032f,.004f,.004f,0f,0f, -.018f,.027f,.012f,0f,0f,
                .010f,.031f,.015f,0f,0f, .029f,.014f,.007f,0f,0f, .034f,.002f,.002f,0f,0f))
            loft(eye, LumiTint.PUPIL, side*.188f, 1.068f, .314f, 0f, 0f, 0, floatArrayOf(
                -.014f,.005f,.003f,0f,0f, -.009f,.023f,.006f,0f,0f,
                .009f,.023f,.006f,0f,0f, .014f,.005f,.003f,0f,0f))
            loft(eye, LumiTint.GLINT, side*.188f-.009f, 1.080f, .324f, 0f, 0f, 0, floatArrayOf(
                -.007f,.002f,.002f,0f,0f, 0f,.006f,.004f,0f,0f, .007f,.002f,.002f,0f,0f))
            loft(LumiRig.HEAD, LumiTint.LIP, side*.060f, .868f, .528f, 0f, -side*18f, 0, floatArrayOf(
                -.014f,.004f,.003f,0f,0f, 0f,.016f,.008f,0f,0f, .014f,.005f,.003f,0f,0f))
        }
    }

    val batches: Map<LumiRig, List<Batch>> by lazy {
        val buckets = linkedMapOf<Pair<LumiRig, LumiTint>, MutableList<MeshGeometry>>()
        parts.forEach { part ->
            val mesh = transform(part)
            val pieces = buckets.getOrPut(part.rig to part.tint) { mutableListOf() }
            pieces.add(mesh)
            if (part.fur > 0) pieces.add(fur(mesh, part.fur))
        }
        buckets.entries.groupBy({ it.key.first }, { (key, pieces) -> Batch(key.second, merge(pieces)) })
    }

    private fun transform(part: Part): MeshGeometry {
        val m = FloatArray(16); val inverse = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, part.x, part.y, part.z)
        Matrix.rotateM(m, 0, part.rx, 1f, 0f, 0f)
        Matrix.rotateM(m, 0, part.rz, 0f, 0f, 1f)
        check(Matrix.invertM(inverse, 0, m, 0))
        val data = part.mesh.vertices.copyOf()
        for (i in data.indices step 6) {
            val x=data[i]; val y=data[i+1]; val z=data[i+2]
            val nx=data[i+3]; val ny=data[i+4]; val nz=data[i+5]
            data[i]=m[0]*x+m[4]*y+m[8]*z+m[12]
            data[i+1]=m[1]*x+m[5]*y+m[9]*z+m[13]
            data[i+2]=m[2]*x+m[6]*y+m[10]*z+m[14]
            data[i+3]=inverse[0]*nx+inverse[1]*ny+inverse[2]*nz
            data[i+4]=inverse[4]*nx+inverse[5]*ny+inverse[6]*nz
            data[i+5]=inverse[8]*nx+inverse[9]*ny+inverse[10]*nz
        }
        return MeshGeometry(data, part.mesh.indices)
    }

    private fun fur(mesh: MeshGeometry, count: Int): MeshGeometry {
        val vertices=ArrayList<Float>(count*18); val indices=ArrayList<Short>(count*3)
        val source=mesh.vertices; val triangles=mesh.indices
        repeat(count) { hair ->
            val triangle=(hair*37 % (triangles.size/3))*3
            val a=(triangles[triangle].toInt() and 65535)*6
            val b=(triangles[triangle+1].toInt() and 65535)*6
            val c=(triangles[triangle+2].toInt() and 65535)*6
            val u=((hair*0.6180339f)%1f).coerceIn(.03f,.97f)
            val v=((hair*0.4142135f)%1f)*(1f-u)
            val w=1f-u-v
            val px=source[a]*u+source[b]*v+source[c]*w
            val py=source[a+1]*u+source[b+1]*v+source[c+1]*w
            val pz=source[a+2]*u+source[b+2]*v+source[c+2]*w
            val nx0=source[a+3]*u+source[b+3]*v+source[c+3]*w
            val ny0=source[a+4]*u+source[b+4]*v+source[c+4]*w
            val nz0=source[a+5]*u+source[b+5]*v+source[c+5]*w
            val n=sqrt(nx0*nx0+ny0*ny0+nz0*nz0).coerceAtLeast(.00001f)
            val nx=nx0/n; val ny=ny0/n; val nz=nz0/n
            val dx0=nx*ny; val dy0=-1f+ny*ny; val dz0=nz*ny
            val length=sqrt(dx0*dx0+dy0*dy0+dz0*dz0).coerceAtLeast(.00001f)
            val dx=dx0/length; val dy=dy0/length; val dz=dz0/length
            val ax=ny*dz-nz*dy; val ay=nz*dx-nx*dz; val az=nx*dy-ny*dx
            val strand=.012f+.011f*sin(hair*1.73f).pow(2)
            fun vertex(across: Float, tip: Boolean) {
                val t=if(tip) 1f else 0f
                vertices.addAll(listOf(px+dx*strand*t+nx*.008f*t+ax*.0015f*across,
                    py+dy*strand*t+ny*.008f*t+ay*.0015f*across,
                    pz+dz*strand*t+nz*.008f*t+az*.0015f*across,nx,ny,nz))
                indices.add(indices.size.toShort())
            }
            vertex(-1f,false); vertex(1f,false); vertex(0f,true)
        }
        return MeshGeometry(vertices.toFloatArray(),indices.toShortArray())
    }

    private fun merge(pieces: List<MeshGeometry>): MeshGeometry {
        val count=pieces.sumOf { it.vertices.size/6 }
        require(count<65536) { "Lumi batch exceeds 16-bit mesh indices" }
        val vertices=FloatArray(count*6); val indices=ShortArray(pieces.sumOf { it.indices.size })
        var vertexOffset=0; var indexOffset=0
        pieces.forEach { mesh ->
            System.arraycopy(mesh.vertices,0,vertices,vertexOffset*6,mesh.vertices.size)
            mesh.indices.forEach { indices[indexOffset++]=((it.toInt() and 65535)+vertexOffset).toShort() }
            vertexOffset+=mesh.vertices.size/6
        }
        return MeshGeometry(vertices,indices)
    }
}
