package com.rekixo.ludopaws.feature.characters.content.pig

import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal object PigGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.38f, .14f, .22f, centerZ = -.09f),
        Ring(-.26f, .39f, .48f, centerZ = -.09f),
        Ring(.01f, .52f, .60f, centerZ = -.10f),
        Ring(.25f, .41f, .47f, centerZ = -.10f),
        Ring(.38f, .20f, .25f, centerZ = -.06f),
    ), 28, subdivisions = 3)
    val neck = MeshLoft.build(listOf(
        Ring(.17f, .30f, .27f, centerZ = .08f),
        Ring(.38f, .27f, .25f, centerZ = .10f),
        Ring(.54f, .23f, .22f, centerZ = .10f),
    ), 20, subdivisions = 3)
    val head = MeshLoft.build(listOf(
        Ring(.43f, .14f, .14f, centerZ = .10f),
        Ring(.56f, .34f, .28f, centerZ = .11f),
        Ring(.80f, .42f, .33f, centerZ = .07f),
        Ring(1.02f, .34f, .27f, centerZ = .03f),
        Ring(1.12f, .15f, .14f),
    ), 28, subdivisions = 3)
    val ear = MeshLoft.build(listOf(
        Ring(0f, .10f, .065f), Ring(.14f, .16f, .075f),
        Ring(.28f, .12f, .05f, centerX = .04f, centerZ = .04f),
        Ring(.36f, .055f, .028f, centerX = .078f, centerZ = .097f),
        Ring(.40f, .009f, .010f, centerX = .094f, centerZ = .14f),
    ), 18, subdivisions = 3)
    val snout = MeshLoft.build(listOf(
        Ring(0f, .19f, .12f), Ring(.12f, .24f, .15f),
        Ring(.23f, .23f, .14f), Ring(.26f, .19f, .11f),
    ), 24, subdivisions = 2)
    val leg = MeshLoft.build(listOf(
        Ring(-.46f, .075f, .08f), Ring(-.28f, .095f, .10f),
        Ring(-.08f, .13f, .14f), Ring(.04f, .10f, .11f),
    ), 16, subdivisions = 2)
    val hoof = MeshLoft.build(listOf(
        Ring(-.06f, .042f, .10f, roundness = .65f),
        Ring(-.045f, .052f, .115f, roundness = .65f),
        Ring(.045f, .05f, .105f, roundness = .65f),
        Ring(.07f, .03f, .07f),
    ), 16, subdivisions = 2)

    // A tapered continuous sweep keeps the corkscrew silhouette without eighteen
    // separate primitives. The depth offset separates each turn of the curl.
    val curledTail: MeshGeometry = run {
        val rings = 31
        val sides = 12
        val vertices = FloatArray((rings * sides + 2) * 6)
        val indices = ArrayList<Short>((rings - 1) * sides * 6 + sides * 6)
        val radius = .116f
        val sweep = PI.toFloat() * 2.68f
        val depth = -.18f
        val tangentLength = hypot(radius * sweep, depth)
        val planarTangent = radius * sweep / tangentLength
        val depthTangent = depth / tangentLength
        fun put(index: Int, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            val offset = index * 6
            vertices[offset] = x
            vertices[offset + 1] = y
            vertices[offset + 2] = z
            vertices[offset + 3] = nx
            vertices[offset + 4] = ny
            vertices[offset + 5] = nz
        }
        fun triangle(a: Int, b: Int, c: Int) {
            indices.add(a.toShort())
            indices.add(b.toShort())
            indices.add(c.toShort())
        }
        for (ring in 0 until rings) {
            val t = ring.toFloat() / (rings - 1)
            val angle = t * sweep
            val tangentX = cos(angle)
            val tangentY = sin(angle)
            val x = radius * tangentY
            val y = radius * (1f - tangentX)
            val z = depth * t
            val thickness = .029f * (1f - t) + .008f
            for (side in 0 until sides) {
                val around = 2f * PI.toFloat() * side / sides
                val across = cos(around)
                val aroundDepth = sin(around)
                val nx = -tangentY * across - depthTangent * tangentX * aroundDepth
                val ny = tangentX * across - depthTangent * tangentY * aroundDepth
                val nz = planarTangent * aroundDepth
                put(ring * sides + side, x + nx * thickness, y + ny * thickness,
                    z + nz * thickness, nx, ny, nz)
            }
            if (ring == 0) put(rings * sides, x, y, z,
                -planarTangent * tangentX, -planarTangent * tangentY, -depthTangent)
            if (ring == rings - 1) put(rings * sides + 1, x, y, z,
                planarTangent * tangentX, planarTangent * tangentY, depthTangent)
        }
        for (ring in 0 until rings - 1) for (side in 0 until sides) {
            val a = ring * sides + side
            val b = ring * sides + (side + 1) % sides
            triangle(a, b, a + sides)
            triangle(b, b + sides, a + sides)
        }
        for (side in 0 until sides) {
            val next = (side + 1) % sides
            val last = (rings - 1) * sides
            triangle(rings * sides, next, side)
            triangle(rings * sides + 1, last + side, last + next)
        }
        MeshGeometry(vertices, indices.toShortArray())
    }
}
