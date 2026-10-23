package com.rekixo.ludopaws.feature.characters.content.lumi

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.content.goat.GoatRenderer
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.meshes.SphereGeometry
import kotlin.math.*

/** Reference-inspired seated kid, with an independent head, listening ears and four moving feet. */
internal object LumiGoatRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = GoatRenderer.stepTravelProgress(progress)
    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback) =
        GoatRenderer.sample(motion, progress, hopPlayback)

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose,
        teamColor: LudoPawsTeamColor) = with(context) {
        val previous = surface
        val body = rigTransforms[0]
        System.arraycopy(parent, 0, body, 0, 16)
        Matrix.rotateM(body, 0, -12f, 0f, 1f, 0f)
        Matrix.translateM(body, 0, 0f, -pose.bodyCrouch * .45f, 0f)
        Matrix.rotateM(body, 0, pose.bodyPitchDegrees * .5f, 1f, 0f, 0f)
        Matrix.rotateM(body, 0, pose.bodyRollDegrees * .5f, 0f, 0f, 1f)
        val breath = 1f + (life?.breath ?: 0f)
        Matrix.scaleM(body, 0, breath, 1f, breath)
        val face = rigTransforms[1]
        System.arraycopy(body, 0, face, 0, 16)
        Matrix.translateM(face, 0, 0f, .67f, .13f)
        Matrix.rotateM(face, 0, -8f + pose.headTiltDegrees + (life?.headTilt ?: 0f), 0f, 0f, 1f)
        Matrix.rotateM(face, 0, (life?.headPitch ?: 0f) * .45f + pose.neckPitchDegrees * .3f, 1f, 0f, 0f)
        Matrix.translateM(face, 0, 0f, -.67f, -.13f)
        try {
            for (rig in LumiRig.entries) {
                val transform = rigTransforms[2]
                System.arraycopy(if (rig.headAttached) face else body, 0, transform, 0, 16)
                when (rig) {
                    LumiRig.EYE_LEFT, LumiRig.EYE_RIGHT -> {
                        val side = if (rig == LumiRig.EYE_LEFT) -1f else 1f
                        Matrix.translateM(transform, 0, side * .27f, .68f, .55f)
                        Matrix.scaleM(transform, 0, 1f, (life?.blink ?: 1f).coerceAtLeast(.035f), 1f)
                        Matrix.translateM(transform, 0, (life?.gazeX ?: 0f) * .35f,
                            (life?.gazeY ?: 0f) * .25f, 0f)
                        Matrix.translateM(transform, 0, -side * .27f, -.68f, -.55f)
                    }
                    LumiRig.EAR_LEFT, LumiRig.EAR_RIGHT -> {
                        val side = if (rig == LumiRig.EAR_LEFT) -1f else 1f
                        Matrix.translateM(transform, 0, side * .52f, .84f, 0f)
                        Matrix.rotateM(transform, 0, side * pose.primaryAppendageDegrees * .45f, 0f, 0f, 1f)
                        Matrix.translateM(transform, 0, -side * .52f, -.84f, 0f)
                    }
                    LumiRig.FRONT_LEFT, LumiRig.FRONT_RIGHT, LumiRig.REAR_LEFT, LumiRig.REAR_RIGHT -> {
                        val front = rig == LumiRig.FRONT_LEFT || rig == LumiRig.FRONT_RIGHT
                        val left = rig == LumiRig.FRONT_LEFT || rig == LumiRig.REAR_LEFT
                        val side = if (left) -1f else 1f
                        val x = side * if (front) .22f else .45f
                        val y = if (front) -.32f else -.48f
                        val z = if (front) .32f else -.04f
                        Matrix.translateM(transform, 0, x, y, z)
                        val swing = if (front) pose.frontLegSwingDegrees else pose.rearLegSwingDegrees
                        Matrix.rotateM(transform, 0, (swing ?: pose.legSwingDegrees) * side * .7f,
                            1f, 0f, 0f)
                        Matrix.translateM(transform, 0, -x, -y, -z)
                    }
                    LumiRig.TAIL -> {
                        Matrix.translateM(transform, 0, .44f, -.14f, -.25f)
                        Matrix.rotateM(transform, 0, pose.tertiaryAppendageDegrees, 0f, 1f, 0f)
                        Matrix.translateM(transform, 0, -.44f, .14f, .25f)
                    }
                    LumiRig.MOUTH -> {
                        Matrix.translateM(transform, 0, 0f, .35f, .58f)
                        Matrix.scaleM(transform, 0, 1f, 1f + pose.jawOpen * .25f, 1f)
                        Matrix.translateM(transform, 0, 0f, -.35f, -.58f)
                    }
                    else -> Unit
                }
                LumiGoatGeometry.batches[rig]?.forEach { batch ->
                    surface = batch.tint.surface
                    drawGeometry(transform, batch.mesh, 0f, 0f, 0f, 1f, 1f, 1f,
                        if (batch.tint == LumiTint.TEAM) teamColor.glColor else batch.tint.color)
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
    FUR(0xFFF5EA, CharacterSurface.LUMI_FUR), IVORY(0xFFF9F1, CharacterSurface.LUMI_FUR),
    EAR(0xEF969E, CharacterSurface.MATTE),
    HORN(0xA76943, CharacterSurface.LUMI_GLOSS), HOOF(0x59392E, CharacterSurface.LUMI_GLOSS),
    RIM(0x3B201B, CharacterSurface.MATTE), WHITE(0xFFFFFF, CharacterSurface.LUMI_EYE),
    AMBER(0xC97819, CharacterSurface.LUMI_EYE), IRIS(0x7E3E12, CharacterSurface.LUMI_EYE),
    PUPIL(0x100C0D, CharacterSurface.LUMI_EYE), PINK(0xF997A3, CharacterSurface.LUMI_GLOSS),
    NOSTRIL(0x982E39, CharacterSurface.LUMI_GLOSS), MOUTH(0x521018, CharacterSurface.MATTE),
    TONGUE(0xF97B8B, CharacterSurface.LUMI_GLOSS), RED(0xED242D, CharacterSurface.LUMI_GLOSS),
    SILVER(0xDDE2E6, CharacterSurface.HORN), TEAM(0xF5C64E, CharacterSurface.MATTE);
    val color = floatArrayOf(((hex shr 16) and 255) / 255f,
        ((hex shr 8) and 255) / 255f, (hex and 255) / 255f, 1f)
}

/** Cached colored meshes: individual fur/face pieces do not become one GL draw call apiece. */
internal object LumiGoatGeometry {
    data class Batch(val tint: LumiTint, val mesh: MeshGeometry)
    private data class Part(val rig: LumiRig, val tint: LumiTint, val x: Float, val y: Float, val z: Float,
        val sx: Float, val sy: Float, val sz: Float, val rz: Float = 0f)
    private val parts = buildList {
        fun shape(rig: LumiRig, tint: LumiTint, x: Float, y: Float, z: Float,
            sx: Float, sy: Float, sz: Float, rz: Float = 0f) {
            add(Part(rig, tint, x, y, z, sx, sy, sz, rz))
        }
        shape(LumiRig.BODY, LumiTint.FUR, 0f, -.26f, -.08f, .48f, .55f, .40f)
        shape(LumiRig.BODY, LumiTint.IVORY, 0f, -.20f, .22f, .35f, .43f, .19f)
        shape(LumiRig.TAIL, LumiTint.FUR, .49f, -.13f, -.28f, .21f, .32f, .17f, -30f)
        shape(LumiRig.TAIL, LumiTint.IVORY, .61f, .05f, -.28f, .10f, .19f, .09f, -35f)
        shape(LumiRig.HEAD, LumiTint.FUR, 0f, .67f, .13f, .67f, .57f, .44f)
        shape(LumiRig.HEAD, LumiTint.IVORY, -.08f, 1.19f, .25f, .13f, .23f, .10f, -28f)
        shape(LumiRig.HEAD, LumiTint.IVORY, .05f, 1.22f, .24f, .10f, .21f, .08f, 26f)
        shape(LumiRig.HEAD, LumiTint.IVORY, .15f, 1.17f, .24f, .065f, .18f, .07f, 38f)
        shape(LumiRig.MOUTH, LumiTint.PINK, 0f, .275f, .53f, .185f, .180f, .055f)
        shape(LumiRig.MOUTH, LumiTint.MOUTH, 0f, .285f, .56f, .166f, .159f, .045f)
        shape(LumiRig.MOUTH, LumiTint.TONGUE, 0f, .202f, .602f, .100f, .060f, .022f)
        shape(LumiRig.HEAD, LumiTint.IVORY, -.105f, .405f, .56f, .16f, .115f, .10f)
        shape(LumiRig.HEAD, LumiTint.IVORY, .105f, .405f, .56f, .16f, .115f, .10f)
        shape(LumiRig.HEAD, LumiTint.WHITE, -.06f, .319f, .60f, .032f, .035f, .018f)
        shape(LumiRig.HEAD, LumiTint.WHITE, .06f, .319f, .60f, .032f, .035f, .018f)
        shape(LumiRig.HEAD, LumiTint.PINK, 0f, .475f, .66f, .115f, .070f, .055f)
        shape(LumiRig.HEAD, LumiTint.PINK, -.040f, .502f, .659f, .065f, .044f, .050f)
        shape(LumiRig.HEAD, LumiTint.PINK, .040f, .502f, .659f, .065f, .044f, .050f)
        shape(LumiRig.HEAD, LumiTint.NOSTRIL, -.065f, .466f, .705f, .026f, .014f, .011f, -15f)
        shape(LumiRig.HEAD, LumiTint.NOSTRIL, .065f, .466f, .705f, .026f, .014f, .011f, 15f)
        shape(LumiRig.BODY, LumiTint.RED, 0f, .105f, .09f, .365f, .095f, .34f)
        shape(LumiRig.BODY, LumiTint.SILVER, 0f, .035f, .416f, .034f, .075f, .025f)
        shape(LumiRig.BODY, LumiTint.TEAM, 0f, -.14f, .446f, .153f, .153f, .025f)
        shape(LumiRig.BODY, LumiTint.RED, 0f, -.14f, .467f, .130f, .130f, .020f)
        shape(LumiRig.BODY, LumiTint.WHITE, 0f, -.17f, .490f, .052f, .040f, .010f)
        shape(LumiRig.BODY, LumiTint.WHITE, -.061f, -.116f, .486f, .020f, .026f, .010f, 26f)
        shape(LumiRig.BODY, LumiTint.WHITE, -.023f, -.087f, .486f, .019f, .027f, .010f)
        shape(LumiRig.BODY, LumiTint.WHITE, .023f, -.087f, .486f, .019f, .027f, .010f)
        shape(LumiRig.BODY, LumiTint.WHITE, .061f, -.116f, .486f, .020f, .026f, .010f, -26f)
        for (side in intArrayOf(-1, 1)) {
            val ear = if (side < 0) LumiRig.EAR_LEFT else LumiRig.EAR_RIGHT
            val eye = if (side < 0) LumiRig.EYE_LEFT else LumiRig.EYE_RIGHT
            val front = if (side < 0) LumiRig.FRONT_LEFT else LumiRig.FRONT_RIGHT
            val rear = if (side < 0) LumiRig.REAR_LEFT else LumiRig.REAR_RIGHT
            shape(ear, LumiTint.FUR, side * .70f, .82f, .015f, .38f, .19f, .105f, -side * 23f)
            shape(ear, LumiTint.EAR, side * .75f, .81f, .105f, .27f, .125f, .035f, -side * 23f)
            shape(rear, LumiTint.FUR, side * .39f, -.47f, -.055f, .27f, .35f, .30f)
            shape(front, LumiTint.FUR, side * .22f, -.51f, .32f, .16f, .36f, .16f, -side * 4f)
            for (toe in intArrayOf(-1, 1)) {
                shape(front, LumiTint.HOOF, side * .24f + toe * .040f, -.84f, .36f, .075f, .12f, .17f)
                shape(rear, LumiTint.HOOF, side * .46f + toe * .035f, -.83f, -.04f, .075f, .105f, .16f)
            }
            shape(eye, LumiTint.RIM, side * .27f, .68f, .505f, .202f, .244f, .080f)
            shape(eye, LumiTint.WHITE, side * .27f, .68f, .550f, .184f, .220f, .070f)
            shape(eye, LumiTint.IRIS, side * .27f, .668f, .609f, .147f, .181f, .060f)
            shape(eye, LumiTint.AMBER, side * .27f, .666f, .641f, .130f, .163f, .043f)
            shape(eye, LumiTint.PUPIL, side * .27f, .683f, .676f, .105f, .139f, .025f)
            shape(eye, LumiTint.WHITE, side * .27f - .036f, .746f, .702f, .039f, .045f, .012f)
            shape(eye, LumiTint.WHITE, side * .27f + .047f, .660f, .702f, .014f, .016f, .009f)
            shape(LumiRig.HEAD, LumiTint.HORN, side * .31f, .995f, .39f, .12f, .029f, .035f, -side * 14f)
            repeat(3) { lash ->
                shape(eye, LumiTint.RIM, side * (.39f + lash * .025f), .855f - lash * .026f,
                    .562f, .050f, .013f, .014f, side * (22f + lash * 12f))
            }
        }
    }

    val batches: Map<LumiRig, List<Batch>> by lazy {
        val buckets = linkedMapOf<Pair<LumiRig, LumiTint>, MutableList<MeshGeometry>>()
        parts.forEach { part ->
            val pieces = buckets.getOrPut(part.rig to part.tint) { mutableListOf() }
            pieces.add(ellipsoid(part))
            if (part.tint == LumiTint.FUR || part.tint == LumiTint.IVORY) pieces.add(fur(part))
        }
        val horn = buckets.getOrPut(LumiRig.HEAD to LumiTint.HORN) { mutableListOf() }
        horn.add(curledHorn(-1f)); horn.add(curledHorn(1f))
        buckets.entries.groupBy({ it.key.first }, { (key, pieces) -> Batch(key.second, merge(pieces)) })
    }

    private fun ellipsoid(part: Part): MeshGeometry {
        val original = SphereGeometry.standard
        val vertices = original.vertices.copyOf()
        val angle = part.rz * PI.toFloat() / 180f
        val c = cos(angle); val s = sin(angle)
        for (i in vertices.indices step 6) {
            val x = vertices[i] * part.sx; val y = vertices[i + 1] * part.sy
            val z = vertices[i + 2] * part.sz
            vertices[i] = part.x + c * x - s * y
            vertices[i + 1] = part.y + s * x + c * y
            vertices[i + 2] = part.z + z
            val nx = original.vertices[i + 3] / part.sx
            val ny = original.vertices[i + 4] / part.sy
            val nz = original.vertices[i + 5] / part.sz
            val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(.000001f)
            vertices[i + 3] = (c * nx - s * ny) / length
            vertices[i + 4] = (s * nx + c * ny) / length
            vertices[i + 5] = nz / length
        }
        return MeshGeometry(vertices, original.indices)
    }

    private fun fur(part: Part): MeshGeometry {
        val count = if (part.rig == LumiRig.HEAD && part.sx > .5f) 3200
            else if (part.rig == LumiRig.BODY && part.sx > .4f) 1100 else 180
        val vertices = ArrayList<Float>(count * 54)
        val indices = ArrayList<Short>(count * 9)
        val angle = part.rz * PI.toFloat() / 180f
        val c = cos(angle); val s = sin(angle)
        repeat(count) { hair ->
            val y = 1f - 2f * (hair + .5f) / count
            val radius = sqrt(1f - y * y)
            val phi = hair * 2.39996323f
            val x = cos(phi) * radius; val z = sin(phi) * radius
            val px = part.x + c * x * part.sx - s * y * part.sy
            val py = part.y + s * x * part.sx + c * y * part.sy
            val pz = part.z + z * part.sz
            val nx0 = c * x / part.sx - s * y / part.sy
            val ny0 = s * x / part.sx + c * y / part.sy
            val nz0 = z / part.sz
            val norm = sqrt(nx0 * nx0 + ny0 * ny0 + nz0 * nz0)
            val nx = nx0 / norm; val ny = ny0 / norm; val nz = nz0 / norm
            val dx0 = nx * ny; val dy0 = -1f + ny * ny; val dz0 = nz * ny
            val length = sqrt(dx0 * dx0 + dy0 * dy0 + dz0 * dz0).coerceAtLeast(.00001f)
            val dx = dx0 / length; val dy = dy0 / length; val dz = dz0 / length
            val ax = ny * dz - nz * dy; val ay = nz * dx - nx * dz; val az = nx * dy - ny * dx
            val scale = (part.sy / .5f).coerceIn(.35f, 1.2f)
            val strand = (.014f + .010f * sin(hair * 1.73f).pow(2)) * scale
            val width = .0018f * scale
            fun vertex(t: Float, across: Float) {
                val lift = .009f * t * t * scale
                vertices.addAll(listOf(px + dx * strand * t + nx * lift + ax * width * across,
                    py + dy * strand * t + ny * lift + ay * width * across,
                    pz + dz * strand * t + nz * lift + az * width * across, nx, ny, nz))
                indices.add(indices.size.toShort())
            }
            vertex(0f, -1f); vertex(0f, 1f); vertex(.65f, .55f)
            vertex(0f, -1f); vertex(.65f, .55f); vertex(.65f, -.55f)
            vertex(.65f, -.55f); vertex(.65f, .55f); vertex(1f, 0f)
        }
        return MeshGeometry(vertices.toFloatArray(), indices.toShortArray())
    }

    private fun curledHorn(side: Float): MeshGeometry {
        val rings = 64; val around = 16
        val vertices = ArrayList<Float>(); val indices = ArrayList<Short>()
        for (ring in 0..rings) {
            val t = ring.toFloat() / rings
            val x = side * (.34f + .43f * t)
            val y = 1.10f + .43f * sin(t * PI.toFloat()) - .10f * t
            val z = -.11f - .27f * t
            val dx = side * .43f
            val dy = .43f * PI.toFloat() * cos(t * PI.toFloat()) - .10f
            val dz = -.27f
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            val tx = dx / length; val ty = dy / length; val tz = dz / length
            val planar = sqrt(tx * tx + ty * ty)
            val ax = -ty / planar; val ay = tx / planar
            val bx = -tz * ay; val by = tz * ax; val bz = tx * ay - ty * ax
            val radius = (.14f * (1f - t).pow(.65f) + .008f) * (1f + .075f * sin(t * PI.toFloat() * 36f))
            for (column in 0..around) {
                val angle = column * 2f * PI.toFloat() / around
                val nx = cos(angle) * ax + sin(angle) * bx
                val ny = cos(angle) * ay + sin(angle) * by
                val nz = sin(angle) * bz
                vertices.addAll(listOf(x + nx * radius, y + ny * radius, z + nz * radius, nx, ny, nz))
                if (ring < rings && column < around) {
                    val a = ring * (around + 1) + column
                    val b = a + around + 1
                    indices.addAll(listOf(a, b, a + 1, a + 1, b, b + 1).map(Int::toShort))
                }
            }
        }
        return MeshGeometry(vertices.toFloatArray(), indices.toShortArray())
    }

    private fun merge(pieces: List<MeshGeometry>): MeshGeometry {
        val count = pieces.sumOf { it.vertices.size / 6 }
        require(count < 65536) { "Lumi batch exceeds 16-bit mesh indices" }
        val vertices = FloatArray(count * 6)
        val indices = ShortArray(pieces.sumOf { it.indices.size })
        var vertexOffset = 0; var indexOffset = 0
        pieces.forEach { mesh ->
            System.arraycopy(mesh.vertices, 0, vertices, vertexOffset * 6, mesh.vertices.size)
            mesh.indices.forEach { indices[indexOffset++] = ((it.toInt() and 65535) + vertexOffset).toShort() }
            vertexOffset += mesh.vertices.size / 6
        }
        return MeshGeometry(vertices, indices)
    }
}
