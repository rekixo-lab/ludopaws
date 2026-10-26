package com.rekixo.ludopaws.feature.characters.content.rabbit

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

internal object RabbitGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.37f, .12f, .17f, centerZ = -.08f),
        Ring(-.23f, .34f, .39f, centerZ = -.09f),
        Ring(.02f, .39f, .45f, centerZ = -.10f),
        Ring(.28f, .29f, .30f, centerZ = -.035f),
        Ring(.44f, .16f, .16f),
    ), 24, subdivisions = 3)
    val neck = MeshLoft.build(listOf(
        Ring(.26f, .23f, .22f), Ring(.42f, .21f, .20f),
        Ring(.58f, .18f, .18f, centerZ = .035f),
    ), 20, subdivisions = 3)
    val head = MeshLoft.build(listOf(
        Ring(.44f, .12f, .13f, centerZ = .10f),
        Ring(.58f, .30f, .25f, centerZ = .08f),
        Ring(.82f, .38f, .30f, centerZ = .045f),
        Ring(1.04f, .29f, .24f), Ring(1.15f, .13f, .12f),
    ), 28, subdivisions = 3)
    val ear = MeshLoft.build(listOf(
        Ring(0f, .075f, .065f), Ring(.17f, .10f, .075f),
        Ring(.43f, .115f, .065f, centerX = .015f),
        Ring(.64f, .075f, .043f, centerX = .035f),
        Ring(.75f, .008f, .012f, centerX = .045f),
    ), 20, subdivisions = 3)
    val arm = MeshLoft.build(listOf(
        Ring(-.50f, .055f, .06f), Ring(-.32f, .065f, .075f),
        Ring(-.10f, .085f, .095f), Ring(.03f, .055f, .065f),
    ), 16, subdivisions = 2)
    val hindPaw = MeshLoft.build(listOf(
        Ring(-.065f, .085f, .21f, centerZ = .04f),
        Ring(-.035f, .13f, .30f, centerZ = .055f),
        Ring(.055f, .12f, .26f, centerZ = .025f),
        Ring(.10f, .07f, .15f),
    ), 20, subdivisions = 3)
    val forePaw = MeshLoft.build(listOf(
        Ring(-.055f, .05f, .075f), Ring(-.02f, .075f, .12f, centerZ = .015f),
        Ring(.05f, .065f, .095f), Ring(.08f, .035f, .045f),
    ), 18, subdivisions = 2)
}
