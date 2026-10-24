package com.rekixo.ludopaws.feature.characters.content.cat

import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

/** Shared immutable meshes with a feline cheek line, pointed ears and compact paws. */
internal object CatGeometry {
    // Longitudinal ribcage, drawn along Z: a tucked belly and gently raised shoulders.
    val body=MeshLoft.build(listOf(
        Ring(-.56f,0.0275f,0.0605f),Ring(-.46f,0.209f,0.242f),
        Ring(-.27f,0.3025f,0.3135f,centerZ=-.025f),Ring(.03f,0.297f,0.308f,centerZ=-.015f),
        Ring(.16f,0.2585f,0.2805f,centerZ=-.06f),Ring(.30f,0.187f,0.253f,centerZ=-.10f),
        Ring(.40f,0.099f,0.154f,centerZ=-.17f),Ring(.44f,0.022f,0.055f,centerZ=-.23f),
    ),28,subdivisions=3)
    val neck=MeshLoft.build(listOf(
        Ring(.17f,.11f,.12f,centerZ=.08f),Ring(.29f,.18f,.17f,centerZ=.11f),
        Ring(.40f,.18f,.16f,centerZ=.11f),Ring(.49f,.09f,.10f,centerZ=.10f),
    ),24,subdivisions=3)
    val head=MeshLoft.build(listOf(
        Ring(.57f,.065f,.070f,centerZ=.20f),Ring(.68f,.19f,.19f,centerZ=.08f),
        Ring(.86f,.32f,.28f,centerZ=.015f),Ring(1.08f,.30f,.26f,centerZ=-.015f),
        Ring(1.23f,.19f,.17f,centerZ=-.025f),Ring(1.28f,.025f,.030f),
    ),28,subdivisions=3)
    val muzzle=MeshLoft.build(listOf(
        Ring(.66f,.065f,.035f,centerZ=.27f),Ring(.72f,.14f,.075f,centerZ=.28f),
        Ring(.80f,.13f,.070f,centerZ=.27f),Ring(.85f,.075f,.040f,centerZ=.25f),
    ),24,subdivisions=3)
    val jaw=MeshLoft.build(listOf(
        Ring(.63f,.040f,.030f,centerZ=.26f),Ring(.66f,.095f,.050f,centerZ=.27f),
        Ring(.70f,.10f,.060f,centerZ=.27f),
    ),20,subdivisions=3)
    val ear=MeshLoft.build(listOf(
        Ring(0f,.12f,.07f),Ring(.10f,.105f,.055f,centerZ=-.015f),
        Ring(.25f,.055f,.025f,centerX=.025f,centerZ=-.045f),
        Ring(.30f,.006f,.006f,centerX=.045f,centerZ=-.055f),
    ),16)
    val nose=MeshLoft.build(listOf(
        Ring(-.030f,.005f,.005f),Ring(.012f,.045f,.020f),Ring(.030f,.030f,.013f),
    ),14)
    // One continuous hip-to-ankle skin. The shared shader bends it at the solved knee.
    // The narrow upper cap stays inside the torso; no exposed round joint is needed.
    val limb=MeshLoft.build(listOf(
        Ring(-.95f,.08f,.12f,centerZ=1.20f),Ring(-.80f,.50f,.70f,centerZ=.90f),
        Ring(-.63f,.90f,1.05f,centerZ=.65f),Ring(-.42f,1.10f,1.20f,centerZ=.20f),
        Ring(-.15f,.95f,1.0f),
        Ring(.06f,.82f,.90f),Ring(.27f,.63f,.72f),
        Ring(.46f,.56f,.65f),Ring(.54f,.49f,.58f),
    ),20,subdivisions=3).let { coatMapped(it, .10f, .65f) }
    val paw=MeshLoft.build(listOf(
        Ring(-.07f,0.0594f,0.0756f),Ring(-.025f,0.0929f,0.1134f,centerZ=.015f),
        Ring(.045f,0.0896f,0.1026f,centerZ=.010f),Ring(.115f,0.0702f,0.0702f),
        Ring(.18f,0.04f,0.0432f),
    ),24,subdivisions=3)
    val collar=MeshLoft.build(listOf(
        Ring(.355f,.19f,.177f,centerZ=.11f),Ring(.405f,.19f,.165f,centerZ=.105f),
    ),24)
    // Closely spaced tapered segments overlap and match the next root around the curl.
    val tail=MeshLoft.build(listOf(
        Ring(-.5f,1f,1f),Ring(0f,.95f,.95f),Ring(.5f,.90f,.90f),
    ),12).let { coatMapped(it, .075f, .14f) }
    val tailTip=MeshLoft.build(listOf(
        Ring(-.5f,1f,1f),Ring(0f,.80f,.80f),Ring(.5f,.06f,.06f),
    ),12).let { coatMapped(it, .075f, .14f) }

    /** Unit bone/tail meshes need coat coordinates at their visible anatomical scale. */
    private fun coatMapped(mesh: MeshGeometry, width: Float, length: Float): MeshGeometry {
        val coatPositions = FloatArray(mesh.vertices.size / 2) { index ->
            val component = index % 3
            mesh.vertices[(index / 3) * 6 + component] * if (component == 1) length else width
        }
        return MeshGeometry(mesh.vertices, mesh.indices, coatPositions)
    }

    val all get()=listOf(body,neck,head,muzzle,jaw,ear,nose,limb,paw,collar,tail,tailTip)
}
