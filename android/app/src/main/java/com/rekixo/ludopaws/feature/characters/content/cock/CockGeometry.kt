package com.rekixo.ludopaws.feature.characters.content.cock

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

internal object CockGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.43f, .10f, .14f), Ring(-.30f, .34f, .36f, centerZ = -.04f),
        Ring(-.06f, .46f, .47f, centerZ = -.08f),
        Ring(.19f, .38f, .36f), Ring(.36f, .22f, .20f),
    ), 24, subdivisions = 3)
    val neck = MeshLoft.build(listOf(
        Ring(.19f, .30f, .29f), Ring(.39f, .26f, .24f),
        Ring(.62f, .18f, .17f, centerZ = .015f), Ring(.79f, .15f, .15f),
    ), 20, subdivisions = 3)
    val head = MeshLoft.build(listOf(
        Ring(.65f, .10f, .12f), Ring(.77f, .25f, .24f, centerZ = .02f),
        Ring(.98f, .31f, .28f), Ring(1.15f, .23f, .22f), Ring(1.21f, .06f, .07f),
    ), 24, subdivisions = 3)
    val wing = MeshLoft.build(listOf(
        Ring(-.45f, .015f, .015f, centerZ = -.15f),
        Ring(-.30f, .15f, .07f, centerZ = -.08f),
        Ring(-.10f, .18f, .095f), Ring(.04f, .06f, .04f),
    ), 18, subdivisions = 3)
    val feather = MeshLoft.build(listOf(
        Ring(0f, .035f, .025f), Ring(.18f, .10f, .04f, centerZ = -.02f),
        Ring(.43f, .085f, .035f, centerZ = -.12f),
        Ring(.64f, .015f, .012f, centerZ = -.25f),
    ), 16, subdivisions = 3)
    val beak = MeshLoft.build(listOf(
        Ring(0f, .12f, .07f), Ring(.11f, .09f, .055f), Ring(.24f, .005f, .005f),
    ), 16, subdivisions = 2)
}
