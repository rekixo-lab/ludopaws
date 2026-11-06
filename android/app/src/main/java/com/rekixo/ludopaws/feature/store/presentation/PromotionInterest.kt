package com.rekixo.ludopaws.feature.store.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.domain.model.StoreTab
import com.rekixo.ludopaws.launchPlayerScreen
import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder

/** One explicit saved offer per account. No purchase, ad or game-state mutation. */
internal object PromotionInterest {
    private fun owner(context: Context) = AccountSessionStore(context).load()?.accountId ?: "installation"
    private fun prefs(context: Context) = context.getSharedPreferences("saved_promotion_interest_v1", Context.MODE_PRIVATE)
    fun tabFor(item: StoreCosmetic) = when (item.category) {
        CosmeticCategory.BOARD -> StoreTab.BOARD
        CosmeticCategory.DICE -> StoreTab.DICE
        else -> StoreTab.PAWS
    }

    fun save(context: Context, id: String) {
        val item = StoreCosmeticCatalog.findShopItem(id)?.takeIf { it.contentAvailable } ?: return
        val key = owner(context)
        prefs(context).edit().putString(key, item.id).putLong("$key:time", System.currentTimeMillis()).apply()
    }

    fun showWhenFinished(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) return
        val key = owner(activity)
        val preferences = prefs(activity)
        val id = preferences.getString(key, null) ?: return
        val item = StoreCosmeticCatalog.findShopItem(id)
        val age = System.currentTimeMillis() - preferences.getLong("$key:time", 0L)
        fun clear() { preferences.edit().remove(key).remove("$key:time").apply() }
        if (age !in 0L..86_400_000L || item == null || !item.contentAvailable || CosmeticInventoryStore(activity).isOwned(id)) {
            clear(); return
        }
        // A saved-interest suggestion shares the same unsolicited budget as review/referral.
        if (!com.rekixo.ludopaws.feature.growth.AutomaticPromptGate.reserve(activity, "saved-item")) return
        // Called only after the result panel is visible, never during a turn.
        val action = com.rekixo.ludopaws.feature.store.domain.SmartUnlockPolicy.decide(
            cosmetic = item, owned = false,
            gems = com.rekixo.ludopaws.feature.store.data.local.GemWalletStore(activity).balance(),
            adProgress = CosmeticInventoryStore(activity).rewardedAdProgress(id),
            playAvailable = com.rekixo.ludopaws.BuildConfig.LUDOPROOF_BILLING_ENABLED &&
                com.rekixo.ludopaws.feature.store.domain.StoreCatalog.cosmeticProductId(id) != null,
            adsAvailable = com.rekixo.ludopaws.BuildConfig.LUDOPROOF_ADS_ENABLED,
        ).primary
        try {
            PlywoodAlertDialogBuilder(activity).setTitle("Your saved item")
                .setMessage(if (action == com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction.GEMS)
                    "Unlock ${item.title} for ${item.gemPrice} Gems?" else
                    "You saved ${item.title}. Start its unlock now?")
                .setPositiveButton("Unlock item") { _, _ ->
                    if (owner(activity) == key && StoreCosmeticCatalog.findShopItem(id)?.contentAvailable == true) {
                        activity.launchPlayerScreen(Intent(activity, StoreActivity::class.java)
                            .putExtra(StoreActivity.EXTRA_PROMOTED_COSMETIC_ID, id)
                            .putExtra(StoreActivity.EXTRA_START_SMART_UNLOCK, true)
                            .putExtra(StoreActivity.EXTRA_EXPECTED_UNLOCK_OWNER, key.takeUnless { it == "installation" })
                            .putExtra(StoreActivity.EXTRA_EXPECTED_UNLOCK_ACTION, action.name)
                            .putExtra(StoreActivity.EXTRA_EXPECTED_GEMS_PRICE,
                                if (action == com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction.GEMS) item.gemPrice else -1)
                            .putExtra(StoreActivity.EXTRA_INITIAL_TAB, tabFor(item).name))
                    }
                }.setNegativeButton("Later", null).show().also { dialog ->
                    com.rekixo.ludopaws.feature.growth.AutomaticPromptGate.presented(activity, "saved-item")
                    dialog.setOnDismissListener { com.rekixo.ludopaws.feature.growth.AutomaticPromptGate.release("saved-item") }
                }
        } catch (_: android.view.WindowManager.BadTokenException) {
            com.rekixo.ludopaws.feature.growth.AutomaticPromptGate.release("saved-item")
            return // Keep the explicit saved interest for a later visible result.
        }
        clear()
    }
}
