package com.rekixo.ludopaws.feature.characters.content.donkey

import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

/** Authored equine silhouette: long ears, forward muzzle, short upright mane and solid hooves. */
internal object DonkeyGeometry {
    val body=MeshLoft.build(listOf(
        Ring(-.39f,.12f,.24f,centerZ=-.13f),Ring(-.30f,.32f,.55f,centerZ=-.14f),
        Ring(-.08f,.42f,.68f,centerZ=-.16f),Ring(.14f,.39f,.61f,centerZ=-.15f),
        Ring(.29f,.26f,.39f,centerZ=-.10f),Ring(.34f,.08f,.13f),
    ),28,subdivisions=3)
    val neck=MeshLoft.build(listOf(
        Ring(.08f,.24f,.25f,centerZ=.17f),Ring(.28f,.24f,.24f,centerZ=.21f),
        Ring(.48f,.20f,.20f,centerZ=.24f),Ring(.64f,.14f,.15f,centerZ=.24f),
    ),24,subdivisions=3)
    val head=MeshLoft.build(listOf(
        Ring(.54f,.12f,.17f,centerZ=.13f),Ring(.68f,.24f,.27f,centerZ=.10f),
        Ring(.90f,.31f,.30f),Ring(1.09f,.30f,.27f,centerZ=-.015f),
        Ring(1.22f,.21f,.20f,centerZ=-.025f),Ring(1.28f,.07f,.08f),
    ),32,subdivisions=3)
    val muzzle=MeshLoft.build(listOf(
        Ring(.53f,.09f,.11f,centerZ=.37f),Ring(.60f,.24f,.22f,centerZ=.41f),
        Ring(.72f,.255f,.235f,centerZ=.40f),Ring(.83f,.19f,.18f,centerZ=.33f),
        Ring(.88f,.08f,.09f,centerZ=.25f),
    ),28,subdivisions=3)
    val jaw=MeshLoft.build(listOf(
        Ring(.49f,.08f,.09f,centerZ=.36f),Ring(.54f,.20f,.19f,centerZ=.40f),
        Ring(.60f,.21f,.20f,centerZ=.40f),Ring(.63f,.10f,.11f,centerZ=.36f),
    ),24,subdivisions=3)
    val ear=MeshLoft.build(listOf(
        Ring(0f,.065f,.055f),Ring(.12f,.09f,.060f),Ring(.31f,.105f,.058f),
        Ring(.49f,.075f,.045f),Ring(.60f,.035f,.024f),Ring(.64f,.006f,.006f),
    ),24,subdivisions=3)
    // Canonical skin: y=-.5 at the hip, +.5 at the fetlock. Extra longitudinal
    // rings let the GPU bend a single surface through the IK knee.
    val leg=MeshLoft.build(listOf(
        Ring(-.5f,1.45f,1.40f),Ring(-.38f,1.40f,1.36f),Ring(-.23f,1.18f,1.15f),
        Ring(-.08f,.92f,.94f),Ring(.06f,.82f,.86f),Ring(.21f,.67f,.72f),
        Ring(.36f,.56f,.62f),Ring(.5f,.66f,.73f),
    ),20,subdivisions=3)
    val hoof=MeshLoft.build(listOf(
        Ring(-.085f,.115f,.15f,centerZ=.025f),Ring(-.06f,.132f,.17f,centerZ=.025f),
        Ring(.035f,.105f,.13f,centerZ=.02f),Ring(.085f,.07f,.09f),
    ),20,subdivisions=2)
    val tail=MeshLoft.build(listOf(Ring(-.5f,1f,1f),Ring(0f,.9f,.9f),Ring(.5f,.8f,.8f)),12)
    val tailTuft=MeshLoft.build(listOf(
        Ring(-.5f,.60f,.60f),Ring(-.25f,1f,1f),Ring(.15f,1.10f,1f),Ring(.5f,.05f,.05f),
    ),16,subdivisions=2)
    val mane=MeshLoft.build(listOf(
        Ring(.34f,.035f,.14f,centerZ=.01f),Ring(.52f,.043f,.16f,centerZ=.015f),
        Ring(.72f,.041f,.15f,centerZ=-.01f),Ring(.96f,.04f,.15f,centerZ=-.12f),
        Ring(1.14f,.06f,.16f,centerZ=-.11f),Ring(1.24f,.065f,.10f,centerZ=-.08f),
        Ring(1.31f,.01f,.035f,centerZ=-.06f),
    ),16,subdivisions=2)
    val all get()=listOf(body,neck,head,muzzle,jaw,ear,leg,hoof,tail,tailTuft,mane)
}
