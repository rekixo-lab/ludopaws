package com.rekixo.ludopaws.feature.store.data.local

import android.content.Context
import android.content.SharedPreferences
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore
import org.json.JSONObject

class GemWalletStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(StorePreferences.PREFS_NAME, Context.MODE_PRIVATE)

    fun balance(): Int {
        val accountId = AccountSessionStore(appContext).load()?.accountId ?: return 0
        if (prefs.getString(StorePreferences.KEY_WALLET_ACCOUNT_ID, null) != accountId) return 0
        return prefs.getInt(StorePreferences.KEY_BALANCE, 0).coerceAtLeast(0)
    }

    fun adsRemoved(): Boolean {
        val accountId = AccountSessionStore(appContext).load()?.accountId ?: return false
        if (prefs.getString(StorePreferences.KEY_WALLET_ACCOUNT_ID, null) != accountId) return false
        return prefs.getBoolean(StorePreferences.KEY_ADS_REMOVED, false)
    }

    /**
     * Saves only a snapshot returned by the authenticated server. This cache is for rendering;
     * no purchase/reward/spend is allowed to originate in local preferences.
     */
    fun applyServerSnapshot(snapshot: JSONObject, expectedAccountId: String): Boolean {
        synchronized(WALLET_LOCK) {
            if (
                snapshot.optString("accountId") != expectedAccountId ||
                AccountSessionStore(appContext).load()?.accountId != expectedAccountId
            ) {
                return false
            }
            val balance = snapshot.optLong("balance", -1L)
            if (balance !in 0..MAX_BALANCE.toLong()) return false

            val priorAccountId = prefs.getString(StorePreferences.KEY_WALLET_ACCOUNT_ID, null)
            val revision = snapshot.optLong("revision", 0L)
            if (revision < 0L || (priorAccountId == expectedAccountId &&
                    revision < prefs.getLong("wallet_revision_v1", 0L))) return false
            val editor = prefs.edit()
            val hasLegacyProgress = prefs.all.keys.any {
                it.startsWith(StorePreferences.KEY_AD_PROGRESS_PREFIX)
            }
            val hasLegacyWalletData =
                prefs.contains(StorePreferences.KEY_BALANCE) ||
                    prefs.contains(StorePreferences.KEY_ADS_REMOVED) ||
                    prefs.contains(StorePreferences.KEY_OWNED_COSMETICS) ||
                    prefs.contains(StorePreferences.KEY_EVENT_ENTITLEMENTS) ||
                    prefs.contains(StorePreferences.KEY_SELECTED_BOARD) ||
                    prefs.contains(StorePreferences.KEY_SELECTED_DICE) ||
                    prefs.contains(StorePreferences.KEY_SELECTED_AVATAR) ||
                    prefs.contains(StorePreferences.KEY_SELECTED_CHARACTER_PACK) ||
                    prefs.contains(StorePreferences.KEY_APPLIED_TOKENS) ||
                    hasLegacyProgress
            if (
                priorAccountId == null &&
                hasLegacyWalletData &&
                !prefs.contains(StorePreferences.KEY_LEGACY_WALLET_ARCHIVE)
            ) {
                // Preserve pre-account device data for a future verified migration/support workflow.
                // This archive is explicitly untrusted and is never read to grant currency or inventory.
                editor.putString(
                    StorePreferences.KEY_LEGACY_WALLET_ARCHIVE,
                    legacyUnverifiedSnapshot().toString(),
                )
            }
            if (priorAccountId != expectedAccountId) {
                // Do not leak cached inventory or cosmetic progress between accounts.
                editor.remove(StorePreferences.KEY_APPLIED_TOKENS)
                editor.remove(StorePreferences.KEY_OWNED_COSMETICS)
                editor.remove(StorePreferences.KEY_EVENT_ENTITLEMENTS)
                editor.remove(StorePreferences.KEY_ADS_REMOVED)
                editor.remove(StorePreferences.KEY_SELECTED_BOARD)
                editor.remove(StorePreferences.KEY_SELECTED_DICE)
                editor.remove(StorePreferences.KEY_SELECTED_AVATAR)
                editor.remove(StorePreferences.KEY_SELECTED_CHARACTER_PACK)
                prefs.all.keys.filter { it.startsWith(StorePreferences.KEY_AD_PROGRESS_PREFIX) }
                    .forEach(editor::remove)
            }

            editor.putString(StorePreferences.KEY_WALLET_ACCOUNT_ID, expectedAccountId)
                .putLong("wallet_revision_v1", revision)
                .putInt(StorePreferences.KEY_BALANCE, balance.toInt())
                .putBoolean(StorePreferences.KEY_ADS_REMOVED, snapshot.optBoolean("adsRemoved", false))
                .putStringSet(
                    StorePreferences.KEY_OWNED_COSMETICS,
                    snapshot.optJSONArray("ownedCosmetics").toStringSet(),
                )

            listOf(StorePreferences.KEY_SELECTED_BOARD, StorePreferences.KEY_SELECTED_DICE,
                StorePreferences.KEY_SELECTED_AVATAR, StorePreferences.KEY_SELECTED_CHARACTER_PACK)
                .forEach(editor::remove)
            prefs.all.keys.filter { it.startsWith(StorePreferences.KEY_AD_PROGRESS_PREFIX) }.forEach(editor::remove)
            val selected = snapshot.optJSONObject("selectedCosmetics") ?: JSONObject()
            selected.optString("BOARD").takeIf(String::isNotBlank)?.let { editor.putString(StorePreferences.KEY_SELECTED_BOARD, it) }
            selected.optString("DICE").takeIf(String::isNotBlank)?.let { editor.putString(StorePreferences.KEY_SELECTED_DICE, it) }
            selected.optString("AVATAR").takeIf(String::isNotBlank)?.let { editor.putString(StorePreferences.KEY_SELECTED_AVATAR, it) }
            selected.optString("CHARACTER_PACK").takeIf(String::isNotBlank)?.let { editor.putString(StorePreferences.KEY_SELECTED_CHARACTER_PACK, it) }

            val progress = snapshot.optJSONObject("rewardedAdProgress") ?: JSONObject()
            progress.keys().forEach { cosmeticId ->
                val value = progress.optInt(cosmeticId, 0).coerceAtLeast(0)
                editor.putInt(StorePreferences.KEY_AD_PROGRESS_PREFIX + cosmeticId, value)
            }

            check(editor.commit()) { "Server wallet snapshot could not be cached safely." }
            return true
        }
    }

    fun clearAccountData(accountId: String) {
        synchronized(WALLET_LOCK) {
            if (prefs.getString(StorePreferences.KEY_WALLET_ACCOUNT_ID, null) != accountId) return
            val editor = prefs.edit()
                .remove(StorePreferences.KEY_WALLET_ACCOUNT_ID)
                .remove("wallet_revision_v1")
                .remove(StorePreferences.KEY_BALANCE)
                .remove(StorePreferences.KEY_ADS_REMOVED)
                .remove(StorePreferences.KEY_APPLIED_TOKENS)
                .remove(StorePreferences.KEY_OWNED_COSMETICS)
                .remove(StorePreferences.KEY_EVENT_ENTITLEMENTS)
                .remove(StorePreferences.KEY_SELECTED_BOARD)
                .remove(StorePreferences.KEY_SELECTED_DICE)
                .remove(StorePreferences.KEY_SELECTED_AVATAR)
                .remove(StorePreferences.KEY_SELECTED_CHARACTER_PACK)
                .remove(StorePreferences.KEY_LEGACY_WALLET_ARCHIVE)
                .remove(StorePreferences.KEY_LEGACY_WALLET_NOTICE_SHOWN)
            prefs.all.keys.filter { it.startsWith(StorePreferences.KEY_AD_PROGRESS_PREFIX) || it.startsWith("local_equipment_${accountId}_") }.forEach(editor::remove)
            check(editor.commit()) { "Local wallet data could not be cleared after account deletion." }
        }
    }

    fun hasLegacyUnverifiedSnapshot(): Boolean =
        prefs.contains(StorePreferences.KEY_LEGACY_WALLET_ARCHIVE)

    @Synchronized
    fun markLegacyUnverifiedNoticeShown(): Boolean {
        if (!hasLegacyUnverifiedSnapshot()) return false
        if (prefs.getBoolean(StorePreferences.KEY_LEGACY_WALLET_NOTICE_SHOWN, false)) return false
        check(
            prefs.edit()
                .putBoolean(StorePreferences.KEY_LEGACY_WALLET_NOTICE_SHOWN, true)
                .commit(),
        ) { "Legacy wallet migration notice state could not be saved" }
        return true
    }

    private fun legacyUnverifiedSnapshot(): JSONObject {
        val cosmetics = JSONObject()
        val adProgress = JSONObject()
        prefs.all.keys
            .filter { it.startsWith(StorePreferences.KEY_AD_PROGRESS_PREFIX) }
            .sorted()
            .forEach { key ->
                adProgress.put(
                    key.removePrefix(StorePreferences.KEY_AD_PROGRESS_PREFIX),
                    prefs.getInt(key, 0).coerceAtLeast(0),
                )
            }
        return JSONObject()
            .put("formatVersion", 1)
            .put("verified", false)
            .put("capturedAt", System.currentTimeMillis())
            .put("balance", prefs.getInt(StorePreferences.KEY_BALANCE, 0).coerceAtLeast(0))
            .put("adsRemoved", prefs.getBoolean(StorePreferences.KEY_ADS_REMOVED, false))
            .put("ownedCosmetics", org.json.JSONArray(
                prefs.getStringSet(StorePreferences.KEY_OWNED_COSMETICS, emptySet<String>())?.toList() ?: emptyList<String>(),
            ))
            .put("selectedBoard", prefs.getString(StorePreferences.KEY_SELECTED_BOARD, null))
            .put("selectedDice", prefs.getString(StorePreferences.KEY_SELECTED_DICE, null))
            .put("selectedAvatar", prefs.getString(StorePreferences.KEY_SELECTED_AVATAR, null))
            .put("selectedCharacterPack", prefs.getString(StorePreferences.KEY_SELECTED_CHARACTER_PACK, null))
            .put("eventEntitlements", org.json.JSONArray(
                prefs.getStringSet(StorePreferences.KEY_EVENT_ENTITLEMENTS, emptySet<String>())?.toList() ?: emptyList<String>(),
            ))
            .put("hadAppliedPurchaseTokenCache", prefs.contains(StorePreferences.KEY_APPLIED_TOKENS))
            .put("appliedPurchaseTokenCount", prefs.getStringSet(
                StorePreferences.KEY_APPLIED_TOKENS,
                emptySet<String>(),
            )?.size ?: 0)
            .put("rewardedAdProgress", adProgress)
    }

    private fun org.json.JSONArray?.toStringSet(): Set<String> {
        val result = linkedSetOf<String>()
        if (this == null) return result
        for (index in 0 until length()) {
            optString(index).takeIf(String::isNotBlank)?.let(result::add)
        }
        return result
    }

    private companion object {
        val WALLET_LOCK = Any()
        const val MAX_BALANCE = 2_000_000_000
    }
}
