package com.rekixo.ludopaws.feature.store.presentation.components

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

internal fun LinearLayout.addPreviewCue() {
    addView(TextView(context).apply {
        text = "Preview ›"
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(0xFF65442F.toInt())
    }, LinearLayout.LayoutParams(-1, -2))
}

/** Keep a stale card or dialog from silently accepting a changed offer. */
internal fun StoreActivity.activateCatalogOffer(offer: StoreCosmetic, expectedAction: SmartUnlockAction) {
    val current = StoreCosmeticCatalog.find(offer.id)
    if (current != offer || smartUnlockDecision(offer).primary != expectedAction) {
        showStoreMessage("This offer changed. Open the current item to continue.")
        renderStore()
        return
    }
    handleCosmeticTap(offer)
}
