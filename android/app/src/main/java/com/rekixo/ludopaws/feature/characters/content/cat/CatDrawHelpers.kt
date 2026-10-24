package com.rekixo.ludopaws.feature.characters.content.cat

import com.rekixo.ludopaws.feature.characters.rendering.CharacterDrawContext
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import kotlin.math.*

internal fun CharacterDrawContext.catMesh(parent: FloatArray, geometry: MeshGeometry, color: FloatArray,
    x: Float = 0f, y: Float = 0f, z: Float = 0f,
    sx: Float = 1f, sy: Float = 1f, sz: Float = 1f,
    rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) =
    drawGeometry(parent, geometry, x, y, z, sx, sy, sz, color, rx, ry, rz)

internal fun CharacterDrawContext.catBone(parent: FloatArray, mesh: MeshGeometry,
    x: Float, y1: Float, z1: Float, y2: Float, z2: Float, radius: Float, color: FloatArray) {
    val dy = y2 - y1; val dz = z2 - z1
    val length = sqrt(dy * dy + dz * dz).coerceAtLeast(.001f)
    val kneeY = rigScratch[2] - (y1 + y2) * .5f
    val kneeZ = rigScratch[3] - (z1 + z2) * .5f
    limbBendY = ((kneeY * dy + kneeZ * dz) / (length * length)).coerceIn(-.10f, .10f)
    limbBendZ = (kneeZ * dy - kneeY * dz) / (length * radius)
    try {
        catMesh(parent, mesh, color, x, (y1 + y2) * .5f, (z1 + z2) * .5f,
            radius, length, radius, rx = atan2(dz, dy) * 180f / PI.toFloat())
    } finally {
        limbBendY = 0f
        limbBendZ = 0f
    }
}
