package com.rekixo.ludopaws.feature.characters.content.duck

import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring
import kotlin.math.*

/** Cached closed anatomy, feather silhouettes, a flat bill and scalloped webbed feet. */
internal object DuckGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.49f, .12f, .18f), Ring(-.38f, .37f, .43f, centerZ = -.03f),
        Ring(-.15f, .55f, .59f, centerZ = -.09f), Ring(.10f, .54f, .57f, centerZ = -.10f),
        Ring(.32f, .38f, .36f, centerZ = -.015f), Ring(.43f, .16f, .17f),
    ), 24, subdivisions = 3)
    val neck = MeshLoft.build(listOf(
        Ring(.25f, .27f, .27f), Ring(.43f, .27f, .25f),
        Ring(.57f, .23f, .22f, centerZ = .02f), Ring(.67f, .13f, .14f),
    ), 20, subdivisions = 3)
    val head = MeshLoft.build(listOf(
        Ring(.59f, .10f, .12f, centerZ = .10f), Ring(.73f, .30f, .28f, centerZ = .04f),
        Ring(.95f, .43f, .36f), Ring(1.14f, .37f, .31f, centerZ = -.025f),
        Ring(1.28f, .19f, .17f, centerZ = -.035f), Ring(1.31f, .025f, .03f),
    ), 28, subdivisions = 3)
    val bill = MeshLoft.build(listOf(
        Ring(-.045f, .21f, .18f, centerZ = .015f, roundness = .65f),
        Ring(-.015f, .30f, .255f, centerZ = .015f, roundness = .65f),
        Ring(.035f, .29f, .25f, roundness = .70f),
        Ring(.065f, .15f, .15f, centerZ = -.05f),
    ), 24, subdivisions = 2)
    val wing = MeshLoft.build(listOf(
        Ring(-.52f, .015f, .018f, centerZ = -.13f),
        Ring(-.40f, .13f, .07f, centerZ = -.085f),
        Ring(-.23f, .18f, .09f, centerZ = -.035f),
        Ring(-.07f, .13f, .075f), Ring(0f, .045f, .035f),
    ), 18, subdivisions = 3)
    val feather = MeshLoft.build(listOf(
        Ring(-.27f, .006f, .006f, centerZ = -.035f),
        Ring(-.16f, .055f, .025f), Ring(-.04f, .045f, .027f), Ring(0f, .012f, .01f),
    ), 12)
    val tuft = MeshLoft.build(listOf(
        Ring(0f, .055f, .04f), Ring(.10f, .07f, .045f, centerZ = -.025f),
        Ring(.21f, .012f, .01f, centerX = .055f, centerZ = -.07f),
    ), 12, subdivisions = 2)
    val tail = MeshLoft.build(listOf(
        Ring(0f, .15f, .10f), Ring(.13f, .15f, .07f, centerZ = -.055f),
        Ring(.28f, .012f, .012f, centerZ = -.10f),
    ), 16, subdivisions = 2)
    val band = MeshLoft.build(listOf(
        Ring(.38f, .284f, .269f), Ring(.45f, .273f, .258f),
    ), 24)
    val leg = MeshLoft.build(listOf(
        Ring(-.5f,.65f,.65f), Ring(0f,1f,1f), Ring(.5f,.75f,.75f),
    ),12)
    val foot = webbedFoot()
    val all get() = listOf(body, neck, head, bill, wing, feather, tuft, tail, band, leg, foot)

    private fun webbedFoot(): MeshGeometry {
        val outline = arrayOf(
            floatArrayOf(-.065f, -.15f), floatArrayOf(-.12f, -.02f),
            floatArrayOf(-.25f, .19f), floatArrayOf(-.18f, .25f),
            floatArrayOf(-.065f, .19f), floatArrayOf(0f, .28f),
            floatArrayOf(.065f, .19f), floatArrayOf(.18f, .25f),
            floatArrayOf(.25f, .19f), floatArrayOf(.12f, -.02f), floatArrayOf(.065f, -.15f),
        )
        val vertices = ArrayList<Float>()
        val indices = ArrayList<Short>()
        fun point(x: Float, y: Float, z: Float) = floatArrayOf(x,y,z)
        fun triangle(a: FloatArray, b: FloatArray, c: FloatArray, outward: FloatArray) {
            val ux = b[0]-a[0]; val uy = b[1]-a[1]; val uz = b[2]-a[2]
            val vx = c[0]-a[0]; val vy = c[1]-a[1]; val vz = c[2]-a[2]
            var nx = uy*vz-uz*vy; var ny = uz*vx-ux*vz; var nz = ux*vy-uy*vx
            val length = sqrt(nx*nx+ny*ny+nz*nz)
            val flip = nx*outward[0]+ny*outward[1]+nz*outward[2] < 0f
            val sign = if (flip) -1f else 1f
            nx *= sign/length; ny *= sign/length; nz *= sign/length
            for (p in if (flip) listOf(a,c,b) else listOf(a,b,c)) {
                indices.add((vertices.size/6).toShort())
                vertices.addAll(listOf(p[0],p[1],p[2],nx,ny,nz))
            }
        }
        for (i in outline.indices) {
            val a = outline[i]; val b = outline[(i+1)%outline.size]
            val at = point(a[0], .035f, a[1]); val bt = point(b[0], .035f, b[1])
            val ab = point(a[0], -.035f, a[1]); val bb = point(b[0], -.035f, b[1])
            triangle(point(0f,.035f,.035f), at, bt, point(0f,1f,0f))
            triangle(point(0f,-.035f,.035f), bb, ab, point(0f,-1f,0f))
            val normal = point(-(b[1]-a[1]),0f,b[0]-a[0])
            // Outline runs clockwise around +Y; this side normal points outward.
            triangle(at,ab,bt,normal); triangle(bt,ab,bb,normal)
        }
        return MeshGeometry(vertices.toFloatArray(), indices.toShortArray())
    }
}
