package com.rekixo.ludopaws.feature.characters.content.dog

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

/** Cached continuous anatomy: a chubby, big-headed cartoon puppy with floppy ears. */
internal object DogGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.36f, .10f, .17f), Ring(-.25f, .36f, .45f, centerZ = -.04f),
        Ring(-.02f, .48f, .52f, centerZ = -.08f), Ring(.21f, .44f, .47f, centerZ = -.07f),
        Ring(.38f, .31f, .30f), Ring(.45f, .12f, .13f),
    ), 24, subdivisions = 3)
    val neck = MeshLoft.build(listOf(
        Ring(.28f, .28f, .29f), Ring(.44f, .30f, .29f, centerZ = .015f),
        Ring(.60f, .25f, .24f, centerZ = .02f), Ring(.73f, .12f, .13f),
    ), 20, subdivisions = 3)
    val head = MeshLoft.build(listOf(
        // Larger, rounder cranium: puppy-like silhouette without changing head pivot.
        Ring(.55f, .12f, .13f, centerZ = .12f), Ring(.66f, .30f, .28f, centerZ = .08f),
        Ring(.87f, .47f, .40f), Ring(1.09f, .47f, .39f, centerZ = -.035f),
        Ring(1.25f, .31f, .24f, centerZ = -.04f), Ring(1.30f, .035f, .04f, centerZ = -.04f),
    ), 28, subdivisions = 3)
    val muzzle = MeshLoft.build(listOf(
        // Wide rounded cheeks, less pointy than the previous natural muzzle.
        Ring(.68f, .16f, .13f, centerZ = .38f), Ring(.75f, .29f, .21f, centerZ = .40f),
        Ring(.85f, .27f, .19f, centerZ = .39f), Ring(.92f, .14f, .10f, centerZ = .32f),
    ), 20, subdivisions = 3)
    val jaw = MeshLoft.build(listOf(
        Ring(.59f, .10f, .09f, centerZ = .36f), Ring(.63f, .22f, .16f, centerZ = .38f),
        Ring(.69f, .22f, .17f, centerZ = .38f), Ring(.72f, .12f, .10f, centerZ = .34f),
    ), 18)
    val ear = MeshLoft.build(listOf(
        // Broader and slightly longer floppy ears, still attached to the animation hinge.
        Ring(-.55f, .028f, .025f, centerX = .02f, centerZ = .06f),
        Ring(-.45f, .15f, .070f, centerX = .035f, centerZ = .055f),
        Ring(-.25f, .175f, .078f, centerX = .025f, centerZ = .025f),
        Ring(-.06f, .105f, .060f), Ring(0f, .048f, .038f),
    ), 16, subdivisions = 3)
    val limb = MeshLoft.build(listOf(
        Ring(-.5f, .55f, .63f), Ring(-.30f, .77f, .78f),
        Ring(.10f, 1f, 1f), Ring(.40f, .80f, .85f), Ring(.5f, .55f, .60f),
    ), 14)
    val paw = MeshLoft.build(listOf(
        Ring(-.08f, .075f, .10f, centerZ = .02f),
        Ring(-.05f, .145f, .185f, centerZ = .04f),
        Ring(.025f, .135f, .17f, centerZ = .025f), Ring(.07f, .055f, .075f),
    ), 18, subdivisions = 2)
    val collar = MeshLoft.build(listOf(
        Ring(.405f, .305f, .305f, centerZ = .015f),
        Ring(.485f, .294f, .285f, centerZ = .018f),
    ), 24)
    val tail = MeshLoft.build(listOf(
        Ring(-.5f, .85f, .85f), Ring(0f, 1f, 1f), Ring(.5f, .72f, .72f),
    ), 12)
    val all get() = listOf(body, neck, head, muzzle, jaw, ear, limb, paw, collar, tail)
}
