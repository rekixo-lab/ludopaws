package com.rekixo.ludopaws.feature.characters.content.dog

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

/** Cached continuous anatomy: a golden companion with a chest, muzzle and floppy ear silhouette. */
internal object DogGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.36f, .10f, .17f), Ring(-.25f, .34f, .45f, centerZ = -.04f),
        Ring(-.02f, .46f, .56f, centerZ = -.08f), Ring(.21f, .43f, .48f, centerZ = -.07f),
        Ring(.38f, .30f, .30f), Ring(.45f, .12f, .13f),
    ), 24, subdivisions = 3)
    val neck = MeshLoft.build(listOf(
        Ring(.28f, .28f, .29f), Ring(.44f, .30f, .29f, centerZ = .015f),
        Ring(.60f, .25f, .24f, centerZ = .02f), Ring(.73f, .12f, .13f),
    ), 20, subdivisions = 3)
    val head = MeshLoft.build(listOf(
        Ring(.55f, .10f, .12f, centerZ = .12f), Ring(.66f, .27f, .25f, centerZ = .08f),
        Ring(.87f, .41f, .34f), Ring(1.09f, .40f, .33f, centerZ = -.035f),
        Ring(1.25f, .25f, .20f, centerZ = -.04f), Ring(1.29f, .03f, .035f, centerZ = -.04f),
    ), 28, subdivisions = 3)
    val muzzle = MeshLoft.build(listOf(
        Ring(.68f, .15f, .13f, centerZ = .38f), Ring(.75f, .26f, .20f, centerZ = .40f),
        Ring(.85f, .24f, .18f, centerZ = .39f), Ring(.92f, .13f, .10f, centerZ = .32f),
    ), 20, subdivisions = 3)
    val jaw = MeshLoft.build(listOf(
        Ring(.59f, .10f, .09f, centerZ = .36f), Ring(.63f, .22f, .16f, centerZ = .38f),
        Ring(.69f, .22f, .17f, centerZ = .38f), Ring(.72f, .12f, .10f, centerZ = .34f),
    ), 18)
    val ear = MeshLoft.build(listOf(
        Ring(-.48f, .025f, .02f, centerX = .02f, centerZ = .06f),
        Ring(-.40f, .13f, .055f, centerX = .035f, centerZ = .055f),
        Ring(-.23f, .145f, .060f, centerX = .025f, centerZ = .025f),
        Ring(-.06f, .09f, .05f), Ring(0f, .045f, .035f),
    ), 16, subdivisions = 3)
    val limb = MeshLoft.build(listOf(
        Ring(-.5f, .55f, .63f), Ring(-.30f, .77f, .78f),
        Ring(.10f, 1f, 1f), Ring(.40f, .80f, .85f), Ring(.5f, .55f, .60f),
    ), 14)
    val paw = MeshLoft.build(listOf(
        Ring(-.08f, .075f, .10f, centerZ = .02f),
        Ring(-.05f, .13f, .18f, centerZ = .04f),
        Ring(.025f, .12f, .16f, centerZ = .025f), Ring(.07f, .055f, .075f),
    ), 18, subdivisions = 2)
    val tail = MeshLoft.build(listOf(
        Ring(-.5f, .85f, .85f), Ring(0f, 1f, 1f), Ring(.5f, .72f, .72f),
    ), 12)
    val all get() = listOf(body, neck, head, muzzle, jaw, ear, limb, paw, tail)
}
