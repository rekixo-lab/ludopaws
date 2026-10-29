package com.rekixo.ludopaws.feature.characters.content.pig

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

internal object PigGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.38f, .14f, .22f, centerZ = -.09f),
        Ring(-.26f, .39f, .48f, centerZ = -.09f),
        Ring(.01f, .49f, .60f, centerZ = -.10f),
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
        Ring(.38f, .012f, .015f, centerX = .09f, centerZ = .12f),
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
}
