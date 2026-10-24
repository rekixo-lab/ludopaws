package com.rekixo.ludopaws.rendering.meshes

import kotlin.math.*

/** Immutable-in-use CPU geometry, shared across scenes; each GL context owns its own buffers. */
internal class MeshGeometry(
    val vertices: FloatArray,
    val indices: ShortArray,
    val localPositions: FloatArray? = null,
) {
    init {
        require(localPositions == null || (localPositions.size == vertices.size / 2 && localPositions.all { it.isFinite() }))
        require(vertices.size % 6 == 0 && vertices.isNotEmpty())
        require(vertices.size / 6 < 65536)
        require(vertices.all { it.isFinite() })
        require(indices.isNotEmpty() && indices.size % 3 == 0)
        require(indices.all { (it.toInt() and 65535) < vertices.size / 6 })
    }
}

/** Smooth closed cross-sections produce anatomy with an actual silhouette, not stacked spheres. */
internal object MeshLoft {
    data class Ring(val y: Float, val radiusX: Float, val radiusZ: Float,
        val centerX: Float = 0f, val centerZ: Float = 0f, val roundness: Float = 1f)

    private fun smooth(points: List<Ring>, steps: Int): List<Ring> {
        require(points.size >= 2)
        fun cubic(a: Float, b: Float, c: Float, d: Float, t: Float): Float =
            .5f * ((2f * b) + (-a + c) * t + (2f*a - 5f*b + 4f*c - d) * t*t + (-a + 3f*b - 3f*c + d) * t*t*t)
        val result = ArrayList<Ring>()
        for (index in 0 until points.lastIndex) {
            val a = points[(index - 1).coerceAtLeast(0)]
            val b = points[index]; val c = points[index + 1]
            val d = points[(index + 2).coerceAtMost(points.lastIndex)]
            repeat(steps) { step ->
                val t = step.toFloat() / steps
                result.add(Ring(
                    b.y + (c.y - b.y) * t,
                    cubic(a.radiusX, b.radiusX, c.radiusX, d.radiusX, t).coerceAtLeast(.001f),
                    cubic(a.radiusZ, b.radiusZ, c.radiusZ, d.radiusZ, t).coerceAtLeast(.001f),
                    cubic(a.centerX, b.centerX, c.centerX, d.centerX, t),
                    cubic(a.centerZ, b.centerZ, c.centerZ, d.centerZ, t),
                    b.roundness + (c.roundness - b.roundness) * t,
                ))
            }
        }
        result.add(points.last())
        return result
    }

    fun build(controlRings: List<Ring>, segments: Int = 20, subdivisions: Int = 1): MeshGeometry {
        require(subdivisions in 1..4)
        val rings = if (subdivisions == 1) controlRings else smooth(controlRings, subdivisions)
        require(rings.size >= 2 && segments in 6..64)
        require(rings.all { it.radiusX > 0f && it.radiusZ > 0f && it.roundness > 0f })
        require(rings.zipWithNext().all { (a, b) -> b.y > a.y })
        val vertexCount = rings.size * segments + 2
        require(vertexCount < 65536)
        val data = FloatArray(vertexCount * 6)
        fun put(index: Int, x: Float, y: Float, z: Float) {
            data[index * 6] = x; data[index * 6 + 1] = y; data[index * 6 + 2] = z
        }
        rings.forEachIndexed { index, ring ->
            repeat(segments) { side ->
                val t = side * 2f * PI.toFloat() / segments
                val c = cos(t); val s = sin(t)
                put(index * segments + side,
                    ring.centerX + sign(c) * abs(c).pow(ring.roundness) * ring.radiusX,
                    ring.y, ring.centerZ + sign(s) * abs(s).pow(ring.roundness) * ring.radiusZ)
            }
        }
        val bottom = vertexCount - 2; val top = vertexCount - 1
        put(bottom, rings.first().centerX, rings.first().y, rings.first().centerZ)
        put(top, rings.last().centerX, rings.last().y, rings.last().centerZ)
        val triangles = ArrayList<Short>()
        fun tri(a: Int, b: Int, c: Int) {
            triangles.add(a.toShort()); triangles.add(b.toShort()); triangles.add(c.toShort())
        }
        repeat(rings.size - 1) { ring ->
            repeat(segments) { side ->
                val a = ring * segments + side
                val next = ring * segments + (side + 1) % segments
                tri(a, a + segments, next)
                tri(next, a + segments, next + segments)
            }
        }
        repeat(segments) { side ->
            val next = (side + 1) % segments
            tri(bottom, side, next)
            val offset = (rings.size - 1) * segments
            tri(top, offset + next, offset + side)
        }
        // Area-weighted normals are smooth across ring boundaries and the seam.
        for (i in triangles.indices step 3) {
            val a = (triangles[i].toInt() and 65535) * 6
            val b = (triangles[i + 1].toInt() and 65535) * 6
            val c = (triangles[i + 2].toInt() and 65535) * 6
            val ux = data[b] - data[a]; val uy = data[b + 1] - data[a + 1]; val uz = data[b + 2] - data[a + 2]
            val vx = data[c] - data[a]; val vy = data[c + 1] - data[a + 1]; val vz = data[c + 2] - data[a + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            for (v in intArrayOf(a, b, c)) {
                data[v + 3] += nx; data[v + 4] += ny; data[v + 5] += nz
            }
        }
        repeat(vertexCount) { index ->
            val i = index * 6
            val length = sqrt(data[i + 3].pow(2) + data[i + 4].pow(2) + data[i + 5].pow(2))
            require(length > .0000001f) { "Degenerate loft normal at $index" }
            data[i + 3] /= length; data[i + 4] /= length; data[i + 5] /= length
        }
        return MeshGeometry(data, triangles.toShortArray())
    }
}
