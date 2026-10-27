package com.rekixo.ludopaws.feature.characters.content.elephant

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

internal object ElephantGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.39f, .16f, .24f, centerZ = -.12f),
        Ring(-.24f, .43f, .53f, centerZ = -.12f),
        Ring(.05f, .52f, .64f, centerZ = -.13f),
        Ring(.31f, .43f, .53f, centerZ = -.12f),
        Ring(.43f, .19f, .25f, centerZ = -.09f),
    ), 28, subdivisions = 3)
    val neck = MeshLoft.build(listOf(
        Ring(.17f, .32f, .30f, centerZ = .10f),
        Ring(.40f, .31f, .28f, centerZ = .11f),
        Ring(.62f, .25f, .24f, centerZ = .10f),
    ), 20, subdivisions = 3)
    val head = MeshLoft.build(listOf(
        Ring(.40f, .14f, .14f, centerZ = .10f),
        Ring(.56f, .32f, .29f, centerZ = .10f),
        Ring(.83f, .40f, .34f, centerZ = .04f),
        Ring(1.08f, .33f, .28f), Ring(1.23f, .16f, .15f),
    ), 28, subdivisions = 3)
    val ear = MeshLoft.build(listOf(
        Ring(-.39f, .025f, .025f, centerX = .02f),
        Ring(-.26f, .20f, .06f, centerX = .09f),
        Ring(-.02f, .29f, .085f, centerX = .13f),
        Ring(.23f, .25f, .07f, centerX = .10f),
        Ring(.38f, .09f, .035f, centerX = .035f),
    ), 24, subdivisions = 3)
    val leg = MeshLoft.build(listOf(
        Ring(-.53f, .115f, .135f), Ring(-.37f, .14f, .15f),
        Ring(-.12f, .16f, .18f), Ring(.04f, .12f, .14f),
    ), 20, subdivisions = 3)
    val foot = MeshLoft.build(listOf(
        Ring(-.065f, .12f, .14f, roundness = .8f),
        Ring(-.045f, .15f, .17f, centerZ = .015f, roundness = .8f),
        Ring(.06f, .14f, .15f), Ring(.09f, .10f, .10f),
    ), 20, subdivisions = 2)
    val trunk = MeshLoft.build(listOf(
        Ring(-.08f, .91f, .91f), Ring(0f, 1f, 1f), Ring(.08f, 1.02f, 1.02f),
    ), 18, subdivisions = 2)
    val tusk = MeshLoft.build(listOf(
        Ring(0f, .047f, .045f), Ring(.10f, .043f, .04f, centerZ = -.015f),
        Ring(.22f, .028f, .028f, centerZ = -.07f),
        Ring(.30f, .004f, .004f, centerZ = -.14f),
    ), 16, subdivisions = 3)
}
