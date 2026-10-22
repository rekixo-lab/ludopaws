package com.rekixo.ludopaws.feature.characters.content.cat

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

/** Shared immutable meshes with a feline cheek line, pointed ears and compact paws. */
internal object CatGeometry {
    val body=MeshLoft.build(listOf(
        Ring(-.34f,.10f,.14f),Ring(-.24f,.30f,.38f,centerZ=-.04f),
        Ring(-.03f,.42f,.48f,centerZ=-.07f),Ring(.20f,.39f,.43f,centerZ=-.06f),
        Ring(.39f,.26f,.25f),Ring(.46f,.10f,.11f),
    ),24,subdivisions=3)
    val neck=MeshLoft.build(listOf(
        Ring(.28f,.25f,.24f),Ring(.44f,.26f,.25f),
        Ring(.60f,.22f,.21f),Ring(.73f,.11f,.13f),
    ),20,subdivisions=3)
    val head=MeshLoft.build(listOf(
        Ring(.57f,.08f,.12f,centerZ=.10f),Ring(.68f,.25f,.25f,centerZ=.045f),
        Ring(.86f,.41f,.32f),Ring(1.08f,.38f,.30f,centerZ=-.025f),
        Ring(1.23f,.23f,.20f,centerZ=-.03f),Ring(1.28f,.03f,.035f),
    ),28,subdivisions=3)
    val muzzle=MeshLoft.build(listOf(
        Ring(.66f,.10f,.07f,centerZ=.32f),Ring(.72f,.225f,.14f,centerZ=.34f),
        Ring(.80f,.21f,.13f,centerZ=.34f),Ring(.85f,.11f,.065f,centerZ=.30f),
    ),20,subdivisions=3)
    val jaw=MeshLoft.build(listOf(
        Ring(.60f,.08f,.07f,centerZ=.31f),Ring(.65f,.16f,.12f,centerZ=.32f),
        Ring(.70f,.15f,.11f,centerZ=.32f),
    ),18)
    val ear=MeshLoft.build(listOf(
        Ring(0f,.14f,.085f),Ring(.10f,.12f,.065f,centerZ=-.015f),
        Ring(.25f,.055f,.025f,centerX=.025f,centerZ=-.045f),
        Ring(.34f,.006f,.006f,centerX=.045f,centerZ=-.055f),
    ),16)
    val nose=MeshLoft.build(listOf(
        Ring(-.042f,.005f,.006f),Ring(.018f,.067f,.027f),Ring(.040f,.045f,.018f),
    ),14)
    val limb=MeshLoft.build(listOf(
        Ring(-.5f,.55f,.63f),Ring(-.25f,.80f,.84f),
        Ring(.12f,1f,1f),Ring(.5f,.55f,.65f),
    ),14)
    val paw=MeshLoft.build(listOf(
        Ring(-.07f,.06f,.08f),Ring(-.035f,.115f,.15f,centerZ=.035f),
        Ring(.035f,.10f,.13f,centerZ=.02f),Ring(.07f,.045f,.065f),
    ),18,subdivisions=2)
    val collar=MeshLoft.build(listOf(
        Ring(.405f,.267f,.262f),Ring(.485f,.259f,.250f),
    ),24)
    // Each segment ends at .80 of its root radius, matching the next segment.
    val tail=MeshLoft.build(listOf(
        Ring(-.5f,1f,1f),Ring(0f,.90f,.90f),Ring(.5f,.80f,.80f),
    ),12)
    val tailTip=MeshLoft.build(listOf(
        Ring(-.5f,1f,1f),Ring(0f,.80f,.80f),Ring(.5f,.06f,.06f),
    ),12)
    val all get()=listOf(body,neck,head,muzzle,jaw,ear,nose,limb,paw,collar,tail,tailTip)
}
