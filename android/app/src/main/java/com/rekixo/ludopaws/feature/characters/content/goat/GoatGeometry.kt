package com.rekixo.ludopaws.feature.characters.content.goat

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring
import kotlin.math.*

/** Authored cross-sections, cached once. Curved horns, leaf ears and cloven hooves have real silhouettes. */
internal object GoatGeometry {
    val body = MeshLoft.build(listOf(
        Ring(-.27f, .12f, .17f, centerZ = -.05f),
        Ring(-.19f, .29f, .33f, centerZ = -.05f),
        Ring(-.02f, .40f, .50f, centerZ = -.09f),
        Ring(.19f, .43f, .51f, centerZ = -.12f),
        Ring(.36f, .36f, .39f, centerZ = -.13f),
        Ring(.46f, .23f, .23f, centerZ = -.07f),
        Ring(.50f, .06f, .08f, centerZ = -.04f),
    ), 24, subdivisions = 3)
    val neck = MeshLoft.build(listOf(
        Ring(.31f, .23f, .25f, centerZ = .04f),
        Ring(.48f, .25f, .25f, centerZ = .055f),
        Ring(.66f, .23f, .22f, centerZ = .06f),
        Ring(.81f, .19f, .19f, centerZ = .025f),
        Ring(.90f, .10f, .13f),
    ), 20, subdivisions = 3)
    val head = MeshLoft.build(listOf(
        Ring(.60f, .07f, .10f, centerZ = .20f),
        Ring(.69f, .15f, .16f, centerZ = .18f),
        Ring(.82f, .23f, .25f, centerZ = .10f),
        Ring(1.01f, .32f, .265f, centerZ = -.015f),
        Ring(1.18f, .29f, .23f, centerZ = -.045f),
        Ring(1.30f, .18f, .16f, centerZ = -.06f),
        Ring(1.34f, .04f, .045f, centerZ = -.06f),
    ), 28, subdivisions = 3)
    val muzzle = MeshLoft.build(listOf(
        Ring(-.12f, .10f, .095f), Ring(-.07f, .185f, .16f, centerZ = .035f),
        Ring(.04f, .19f, .17f), Ring(.12f, .12f, .10f, centerZ = -.035f),
    ), 20)
    val jaw = MeshLoft.build(listOf(
        Ring(-.065f, .08f, .10f), Ring(-.025f, .15f, .15f),
        Ring(.045f, .16f, .15f), Ring(.08f, .09f, .09f),
    ), 18)
    val ear = MeshLoft.build(listOf(
        Ring(0f, .045f, .035f), Ring(.11f, .11f, .038f, centerZ = .025f),
        Ring(.26f, .14f, .03f, centerZ = .045f), Ring(.40f, .095f, .022f, centerZ = .04f),
        Ring(.51f, .008f, .006f, centerX = .035f, centerZ = -.015f),
    ), 16)
    val horn = MeshLoft.build((0..22).map { step ->
        val t = step / 22f
        val radius = .077f * (1f - t).pow(.8f) + .003f
        val ridge = 1f + .035f * cos(t * PI.toFloat() * 26f)
        Ring(t * .45f, radius * ridge, radius * ridge,
            centerX = .035f * t + .055f * t * t,
            centerZ = -.28f * t * t)
    }, 14)
    val shin = MeshLoft.build(listOf(
        Ring(-.5f, .64f, .70f), Ring(-.38f, .81f, .82f),
        Ring(.18f, .72f, .80f), Ring(.43f, .97f, 1f), Ring(.5f, .68f, .75f),
    ), 14)
    val thigh = MeshLoft.build(listOf(
        Ring(-.5f, .56f, .60f), Ring(-.30f, .90f, .89f),
        Ring(.12f, 1f, 1f), Ring(.38f, .81f, .88f), Ring(.5f, .50f, .50f),
    ), 16)
    val hoof = MeshLoft.build(listOf(
        Ring(-.09f, .047f, .11f, roundness = .36f),
        Ring(-.05f, .052f, .12f, centerZ = .015f, roundness = .36f),
        Ring(.05f, .045f, .09f, roundness = .50f),
        Ring(.09f, .025f, .052f, centerZ = -.015f),
    ), 16)
    val beard = MeshLoft.build(listOf(
        Ring(-.27f, .004f, .004f, centerZ = .035f),
        Ring(-.16f, .045f, .025f, centerZ = .015f),
        Ring(-.05f, .075f, .040f), Ring(0f, .055f, .035f),
    ), 14)
    val tuft = MeshLoft.build(listOf(
        Ring(-.09f, .004f, .004f), Ring(-.035f, .045f, .012f),
        Ring(.02f, .03f, .013f),
    ), 10)
    val tail = MeshLoft.build(listOf(
        Ring(0f, .075f, .075f), Ring(.10f, .075f, .065f, centerZ = -.03f),
        Ring(.22f, .065f, .05f, centerZ = -.055f), Ring(.30f, .008f, .01f, centerZ = -.07f),
    ), 14)

    val all get() = listOf(body, neck, head, muzzle, jaw, ear, horn, shin, thigh, hoof, beard, tuft, tail)
}
