package com.rekixo.ludopaws.feature.boards.domain

import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic

/** Visual theme only. Logical track, safe cells and hit testing remain shared gameplay geometry. */
data class BoardDefinition(
    val offer: StoreCosmetic,
    val surfaceColor: Int = 0xFFF8F8F8.toInt(),
    val neutralCellColor: Int = 0xFFFFFFFF.toInt(),
    val tintColor: Int? = null,
    val tintAmount: Float = 0f,
    val brightness: Float = 1f,
    val rendererKey: String = "classic",
    val yardHabitatEnabled: Boolean = true,
    val stoneRoadOverlayEnabled: Boolean = true,
    val monsoonEffectsEnabled: Boolean = false,
) {
    val id: String get() = offer.id
    fun teamColor(classic: Int): Int {
        fun component(shift: Int): Int {
            val original = (classic ushr shift) and 255
            val tint = tintColor?.let { (it ushr shift) and 255 } ?: original
            return ((original * (1f - tintAmount) + tint * tintAmount) * brightness).toInt().coerceIn(0, 255)
        }
        return (255 shl 24) or (component(16) shl 16) or (component(8) shl 8) or component(0)
    }
}
