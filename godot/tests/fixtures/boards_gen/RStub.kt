package com.rekixo.ludopaws

/** Drawable ids used by the live dice renderers, named for the recorder. */
object R {
    object drawable {
        const val dice_forest_dew_3d = 1
        const val dice_lagoon_pearl_3d = 2
        const val dice_frost_crystal_3d = 3
        const val dice_starlight_3d = 4
        fun name(id: Int) = when (id) { 1 -> "dice_forest_dew_3d"; 2 -> "dice_lagoon_pearl_3d"; 3 -> "dice_frost_crystal_3d"; 4 -> "dice_starlight_3d"; else -> "unknown" }
    }
}
