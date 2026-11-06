package com.rekixo.ludopaws.feature.characters.content.giraffe

import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal object GiraffeGeometry {
    class Coat(val gold: MeshGeometry, val patches: MeshGeometry)

    val body = pattern(listOf(
        Ring(-.32f, .095f, .20f, centerZ = -.08f),
        Ring(-.25f, .25f, .48f, centerZ = -.10f),
        Ring(-.10f, .34f, .63f, centerZ = -.13f),
        Ring(.06f, .35f, .61f, centerZ = -.12f),
        Ring(.22f, .30f, .51f, centerZ = -.10f),
        Ring(.34f, .19f, .34f, centerZ = -.07f),
        Ring(.39f, .065f, .14f, centerZ = -.03f),
    ), columns = 8, rows = 3)

    val neck = pattern(listOf(
        Ring(.13f, .215f, .235f, centerZ = .23f),
        Ring(.32f, .205f, .225f, centerZ = .255f),
        Ring(.51f, .151f, .172f, centerZ = .263f),
        Ring(.74f, .121f, .145f, centerZ = .278f),
        Ring(.96f, .105f, .126f, centerZ = .295f),
        Ring(1.17f, .094f, .108f, centerZ = .323f),
        Ring(1.35f, .105f, .110f, centerZ = .347f),
        Ring(1.49f, .09f, .09f, centerZ = .371f),
    ), columns = 4, rows = 5)

    val mane = MeshLoft.build(listOf(
        Ring(.44f, .016f, .025f, centerZ = .065f),
        Ring(.64f, .032f, .036f, centerZ = .115f),
        Ring(.89f, .031f, .036f, centerZ = .155f),
        Ring(1.12f, .025f, .032f, centerZ = .192f),
        Ring(1.37f, .021f, .026f, centerZ = .227f),
        Ring(1.52f, .009f, .012f, centerZ = .241f),
    ), 14, subdivisions = 2)

    val head = MeshLoft.build(listOf(
        Ring(1.31f, .073f, .083f, centerZ = .435f),
        Ring(1.39f, .172f, .19f, centerZ = .437f),
        Ring(1.52f, .23f, .246f, centerZ = .405f),
        Ring(1.67f, .236f, .21f, centerZ = .357f),
        Ring(1.79f, .176f, .145f, centerZ = .317f),
        Ring(1.84f, .058f, .068f, centerZ = .294f),
    ), 26, subdivisions = 3)

    val ear = MeshLoft.build(listOf(
        Ring(0f, .033f, .027f),
        Ring(.11f, .078f, .045f),
        Ring(.21f, .067f, .035f),
        Ring(.30f, .018f, .015f),
        Ring(.33f, .003f, .004f),
    ), 16, subdivisions = 2)

    val hoof = MeshLoft.build(listOf(
        Ring(GiraffeDefinition.HOOF_BOTTOM, .077f, .107f, centerZ = .025f, roundness = .78f),
        Ring(-.05f, .089f, .112f, centerZ = .02f, roundness = .78f),
        Ring(.035f, .069f, .086f),
        Ring(.075f, .057f, .067f),
    ), 16, subdivisions = 2)

    private data class Patch(
        val angle: Float, val height: Float, val width: Float, val length: Float, val phase: Float,
    )

    /**
     * Partition one loft into two materials. Shared positions and normals keep the coat smooth
     * under the game lights, and the patches remain attached when the neck or body rotates.
     */
    private fun pattern(rings: List<Ring>, columns: Int, rows: Int): Coat {
        val segments = 40
        val mesh = MeshLoft.build(rings, segments, subdivisions = 4)
        val patches = List(rows * columns) { i ->
            val row = i / columns
            val column = i % columns
            val phase = i * 2.39996f
            Patch(
                (column + .5f + (row % 2) * .46f + .13f * sin(phase)) / columns,
                (row + .51f + .12f * cos(phase)) / rows,
                (.39f + .06f * sin(phase + .8f)) / columns,
                (.35f + .07f * cos(phase + 1.4f)) / rows,
                phase,
            )
        }
        val gold = ArrayList<Short>()
        val spots = ArrayList<Short>()
        val low = rings.first().y
        val height = rings.last().y - low
        val pole = mesh.vertices.size / 6 - 2
        for (i in mesh.indices.indices step 3) {
            val a = mesh.indices[i].toInt() and 65535
            val b = mesh.indices[i + 1].toInt() and 65535
            val c = mesh.indices[i + 2].toInt() and 65535
            val firstAngle = (a % segments).toFloat() / segments
            val angle = firstAngle + (wrap((b % segments).toFloat() / segments - firstAngle) +
                wrap((c % segments).toFloat() / segments - firstAngle)) / 3f
            val y = ((mesh.vertices[a * 6 + 1] + mesh.vertices[b * 6 + 1] +
                mesh.vertices[c * 6 + 1]) / 3f - low) / height
            val spotted = a < pole && b < pole && c < pole && patches.any { patch ->
                val x = wrap(angle - patch.angle) / patch.width
                val dy = (y - patch.height) / patch.length
                if (abs(x) > 1.25f || abs(dy) > 1.25f) false else {
                    val edge = atan2(dy, x)
                    val radius = 1f + .15f * cos(5f * edge + patch.phase) + .09f * sin(3f * edge)
                    x * x + dy * dy < radius * radius
                }
            }
            val material = if (spotted) spots else gold
            material.add(mesh.indices[i])
            material.add(mesh.indices[i + 1])
            material.add(mesh.indices[i + 2])
        }
        return Coat(MeshGeometry(mesh.vertices, gold.toShortArray()), MeshGeometry(mesh.vertices, spots.toShortArray()))
    }

    private fun wrap(turns: Float): Float = when {
        turns > .5f -> turns - 1f
        turns < -.5f -> turns + 1f
        else -> turns
    }
}
