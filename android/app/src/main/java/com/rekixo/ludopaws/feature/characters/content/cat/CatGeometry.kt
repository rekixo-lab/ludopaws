package com.rekixo.ludopaws.feature.characters.content.cat

import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.meshes.MeshLoft
import com.rekixo.ludopaws.rendering.meshes.MeshLoft.Ring

/** Shared immutable meshes with a feline cheek line, pointed ears and compact paws. */
internal object CatGeometry {
    // Longitudinal ribcage, drawn along Z: a tucked belly and gently raised shoulders.
    val body=MeshLoft.build(listOf(
        Ring(-.56f,.025f,.055f),Ring(-.46f,.19f,.22f),
        Ring(-.27f,.275f,.285f,centerZ=-.025f),Ring(.03f,.27f,.28f,centerZ=-.015f),
        Ring(.16f,.235f,.255f,centerZ=-.06f),Ring(.30f,.17f,.23f,centerZ=-.10f),
        Ring(.40f,.09f,.14f,centerZ=-.17f),Ring(.44f,.020f,.050f,centerZ=-.23f),
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
        Ring(-.68f,.30f,.40f,centerZ=.90f),Ring(-.55f,1.15f,1.30f,centerZ=.45f),
        Ring(-.36f,1.25f,1.35f,centerZ=.10f),Ring(-.12f,1.02f,1.10f),
        Ring(.06f,.82f,.90f),Ring(.27f,.63f,.72f),
        Ring(.46f,.56f,.65f),Ring(.54f,.49f,.58f),
    ),20,subdivisions=3).let { coatMapped(it, .10f, .65f) }
    val paw=MeshLoft.build(listOf(
        Ring(-.07f,.055f,.070f),Ring(-.045f,.086f,.105f,centerZ=.015f),
        Ring(0f,.083f,.095f,centerZ=.010f),Ring(.045f,.065f,.065f),
        Ring(.09f,.037f,.040f),
    ),24,subdivisions=3)
    val collar=MeshLoft.build(listOf(
        Ring(.355f,.19f,.177f,centerZ=.11f),Ring(.405f,.19f,.165f,centerZ=.105f),
    ),24)
    // Each segment ends at .80 of its root radius, matching the next segment.
    val tail=MeshLoft.build(listOf(
        Ring(-.5f,1f,1f),Ring(0f,.90f,.90f),Ring(.5f,.80f,.80f),
    ),12).let { coatMapped(it, .075f, .18f) }
    val tailTip=MeshLoft.build(listOf(
        Ring(-.5f,1f,1f),Ring(0f,.80f,.80f),Ring(.5f,.06f,.06f),
    ),12).let { coatMapped(it, .075f, .18f) }

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
