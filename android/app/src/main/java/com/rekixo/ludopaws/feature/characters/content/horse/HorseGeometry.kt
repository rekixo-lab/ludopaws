package com.rekixo.ludopaws.feature.characters.content.horse

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

internal object HorseGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.36f, .12f, .23f, centerZ = -.15f),
        Ring(-.24f, .33f, .56f, centerZ = -.17f),
        Ring(.01f, .41f, .69f, centerZ = -.18f),
        Ring(.24f, .36f, .57f, centerZ = -.15f),
        Ring(.36f, .19f, .29f, centerZ = -.08f),
    ), 28, subdivisions = 3)
    val neck = MeshLoft.build(listOf(
        Ring(.13f, .26f, .29f, centerZ = .17f),
        Ring(.39f, .24f, .28f, centerZ = .18f),
        Ring(.65f, .18f, .24f, centerZ = .20f),
        Ring(.89f, .13f, .17f, centerZ = .18f),
        Ring(.98f, .08f, .10f, centerZ = .15f),
    ), 24, subdivisions = 3)
    val head = MeshLoft.build(listOf(
        Ring(.67f, .10f, .14f, centerZ = .23f),
        Ring(.83f, .23f, .27f, centerZ = .17f),
        Ring(1.05f, .28f, .30f, centerZ = .07f),
        Ring(1.26f, .24f, .24f, centerZ = .015f),
        Ring(1.37f, .09f, .10f),
    ), 28, subdivisions = 3)
    val muzzle = MeshLoft.build(listOf(
        Ring(.64f, .09f, .10f, centerZ = .43f),
        Ring(.72f, .20f, .22f, centerZ = .48f),
        Ring(.85f, .21f, .23f, centerZ = .47f),
        Ring(.96f, .15f, .17f, centerZ = .37f),
        Ring(1.02f, .07f, .08f, centerZ = .27f),
    ), 24, subdivisions = 3)
    val jaw = MeshLoft.build(listOf(
        Ring(.59f, .07f, .08f, centerZ = .42f),
        Ring(.65f, .18f, .19f, centerZ = .47f),
        Ring(.72f, .18f, .18f, centerZ = .45f),
        Ring(.77f, .08f, .08f, centerZ = .38f),
    ), 20, subdivisions = 3)
    val ear = MeshLoft.build(listOf(
        Ring(0f, .065f, .05f), Ring(.12f, .085f, .055f),
        Ring(.25f, .050f, .035f), Ring(.32f, .004f, .008f),
    ), 18, subdivisions = 3)
    val mane = MeshLoft.build(listOf(
        Ring(.25f, .02f, .035f, centerX = .08f, centerZ = -.09f),
        Ring(.50f, .10f, .12f, centerX = .08f, centerZ = -.075f),
        Ring(.80f, .13f, .15f, centerX = .07f, centerZ = -.08f),
        Ring(1.12f, .095f, .14f, centerX = .035f, centerZ = -.16f),
        Ring(1.35f, .06f, .11f, centerX = .025f, centerZ = -.11f),
        Ring(1.40f, .015f, .035f, centerZ = -.05f),
    ), 20, subdivisions = 3)
    val limb = MeshLoft.build(listOf(
        Ring(-.5f, .72f, .78f), Ring(-.24f, 1f, 1f),
        Ring(.20f, .92f, .92f), Ring(.5f, .65f, .70f),
    ), 16, subdivisions = 2)
    val hoof = MeshLoft.build(listOf(
        Ring(-.085f, .105f, .14f, centerZ = .025f, roundness = .75f),
        Ring(-.05f, .12f, .155f, centerZ = .025f, roundness = .75f),
        Ring(.04f, .095f, .12f), Ring(.08f, .065f, .08f),
    ), 20, subdivisions = 2)
    val tail = MeshLoft.build(listOf(
        Ring(-.5f, .90f, .90f), Ring(0f, 1f, 1f), Ring(.5f, .82f, .82f),
    ), 18, subdivisions = 2)
    val tailTip = MeshLoft.build(listOf(
        Ring(-.5f, .90f, .90f), Ring(0f, .65f, .65f), Ring(.5f, .015f, .015f),
    ), 18, subdivisions = 2)
}
