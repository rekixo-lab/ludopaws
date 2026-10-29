package com.rekixo.ludopaws.feature.store.data.local

import android.content.Context
import com.rekixo.ludopaws.ConnectivityMonitor
import com.rekixo.ludopaws.LudoProofApplication
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Sync an already-owned appearance preference; never purchase or grant ownership. */
internal object LocalEquipmentSync {
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    fun request(context: Context) {
        val app = context.applicationContext as LudoProofApplication
        if (!ConnectivityMonitor(app) { }.isOnline() || !busy.compareAndSet(false, true)) return
        worker.execute {
            var confirmed = false
            val inventory = CosmeticInventoryStore(app)
            val owner = app.accountRepository.currentSession()?.accountId
            try {
                if (owner != null) inventory.pendingEquipment(owner).forEach { (category, id) ->
                    if (app.accountRepository.currentSession()?.accountId != owner) return@forEach
                    val response = app.accountRepository.selectCosmetic(id)
                    val wallet = response.optJSONObject("wallet") ?: return@forEach
                    if (inventory.applyServerSnapshot(wallet, owner) && wallet.optJSONObject("selectedCosmetics")?.optString(category.name) == id) {
                        inventory.confirmEquipment(owner, category, id)
                        confirmed = true
                    }
                }
            } catch (error: Exception) {
                android.util.Log.w("LocalEquipment", "Appearance will sync when connected", error)
            } finally {
                busy.set(false)
                if (confirmed && owner != null && inventory.pendingEquipment(owner).isNotEmpty()) request(app)
            }
        }
    }
}
