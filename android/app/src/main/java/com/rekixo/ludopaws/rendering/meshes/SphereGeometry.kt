package com.rekixo.ludopaws.rendering.meshes

import kotlin.math.*

/** Existing ellipsoid primitive remains available to all other character renderers. */
internal object SphereGeometry {
    val standard: MeshGeometry by lazy {
        val latitudeSegments = 16
        val longitudeSegments = 24
        val vertices = ArrayList<Float>()
        val indices = ArrayList<Short>()
        for (lat in 0..latitudeSegments) {
            val theta = PI * lat / latitudeSegments
            for (lon in 0..longitudeSegments) {
                val phi = 2.0 * PI * lon / longitudeSegments
                val x = sin(theta).toFloat() * cos(phi).toFloat()
                val y = cos(theta).toFloat()
                val z = sin(theta).toFloat() * sin(phi).toFloat()
                vertices.addAll(listOf(x, y, z, x, y, z))
            }
        }
        val row = longitudeSegments + 1
        for (lat in 0 until latitudeSegments) for (lon in 0 until longitudeSegments) {
            val a = lat * row + lon; val b = a + row
            for (index in intArrayOf(a, b, a + 1, b, b + 1, a + 1)) indices.add(index.toShort())
        }
        MeshGeometry(vertices.toFloatArray(), indices.toShortArray())
    }
}
