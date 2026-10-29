package com.rekixo.ludopaws.feature.account.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AccountSession(
    val accountId: String,
    val sessionId: String,
    val accessToken: String,
    val refreshToken: String,
    val billingAccountId: String,
    val accessExpiresAt: Long,
    val sessionExpiresAt: Long,
    val displayName: String,
    val guestRecoveryEligible: Boolean = false,
    val identityRecoveryPending: Boolean = false,
    val guestDeletionCredential: String? = null,
)

class AccountSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): AccountSession? {
        return try {
        val ivText = prefs.getString(KEY_IV, null) ?: return null
        val cipherText = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val cacheKey = ivText + ":" + cipherText + ":" + prefs.getInt(KEY_VERSION, 0) + ":" +
            prefs.getString(KEY_ACCOUNT_MODE, null) + ":" + prefs.getString(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID, null)
        synchronized(sessionCacheLock) { if (cachedRecord == cacheKey) return cachedSession }
            require(prefs.getInt(KEY_VERSION, 0) == 1)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)),
            )
            cipher.updateAAD(SESSION_AAD)
            val raw = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP))
                .toString(Charsets.UTF_8)
            val json = JSONObject(raw)
            // Older app releases persisted account mode separately before the encrypted
            // session JSON included guestRecoveryEligible. Use that marker only as a
            // compatibility hint; the server still verifies that a delete-only code can
            // be registered only for an unlinked guest account.
            val legacyGuestMode = prefs.getString(KEY_ACCOUNT_MODE, null) == MODE_GUEST
            val guestRecoveryEligible = json.optBoolean("guestRecoveryEligible", legacyGuestMode)
            val value = AccountSession(
                accountId = json.getString("accountId"),
                sessionId = json.getString("sessionId"),
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                billingAccountId = json.getString("billingAccountId"),
                accessExpiresAt = json.getLong("accessExpiresAt"),
                sessionExpiresAt = json.getLong("sessionExpiresAt"),
                displayName = json.optString("displayName", "Guest User"),
                guestRecoveryEligible = guestRecoveryEligible,
                identityRecoveryPending = json.optBoolean(
                    "identityRecoveryPending",
                    prefs.getString(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID, null) == json.optString("accountId"),
                ),
                guestDeletionCredential = json.optString("guestDeletionCredential")
                    .takeIf { guestRecoveryEligible && GUEST_DELETION_CREDENTIAL.matches(it) },
            )
            require(ACCOUNT_ID.matches(value.accountId))
            require(ACCOUNT_ID.matches(value.sessionId))
            require(OPAQUE_BILLING_ID.matches(value.billingAccountId))
            require(value.accessToken.split(".").size == 3)
            require(REFRESH_TOKEN.matches(value.refreshToken))
            synchronized(sessionCacheLock) { cachedRecord = cacheKey; cachedSession = value }
            value
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    @Synchronized
    fun save(session: AccountSession) {
        require(ACCOUNT_ID.matches(session.accountId))
        require(ACCOUNT_ID.matches(session.sessionId))
        require(OPAQUE_BILLING_ID.matches(session.billingAccountId))
        require(session.accessToken.split(".").size == 3)
        require(REFRESH_TOKEN.matches(session.refreshToken))
        require(session.accessExpiresAt > 0)
        require(session.sessionExpiresAt > 0)
        val json = JSONObject()
            .put("accountId", session.accountId)
            .put("sessionId", session.sessionId)
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("billingAccountId", session.billingAccountId)
            .put("accessExpiresAt", session.accessExpiresAt)
            .put("sessionExpiresAt", session.sessionExpiresAt)
            .put("displayName", session.displayName.take(24))
            .put("guestRecoveryEligible", session.guestRecoveryEligible)
        if (session.guestRecoveryEligible && session.guestDeletionCredential != null) {
            require(GUEST_DELETION_CREDENTIAL.matches(session.guestDeletionCredential))
            json.put("guestDeletionCredential", session.guestDeletionCredential)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(SESSION_AAD)
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val editor = prefs.edit()
            .putInt(KEY_VERSION, 1)
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_ACCOUNT_MODE, if (session.guestRecoveryEligible) MODE_GUEST else MODE_GOOGLE)
            .remove(KEY_PENDING_REFRESH_REQUEST_ID)
        if (!session.guestRecoveryEligible) {
            // A Google-restored account must never fall back to this installation's previous guest.
            editor.remove(KEY_GUEST_REQUEST_IV).remove(KEY_GUEST_REQUEST_CIPHERTEXT)
        }
        if (session.identityRecoveryPending) {
            editor.putString(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID, session.accountId)
        } else if (prefs.getString(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID, null) == session.accountId) {
            editor.remove(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID)
        }
        check(editor.commit()) { "Account session could not be durably encrypted and saved" }
    }

    @Synchronized
    fun clearSession() {
        synchronized(sessionCacheLock) { cachedRecord = null; cachedSession = null }
        check(
            prefs.edit()
                .remove(KEY_IV)
                .remove(KEY_CIPHERTEXT)
                .remove(KEY_VERSION)
                .remove(KEY_PENDING_REFRESH_REQUEST_ID)
                .commit(),
        ) { "Account session could not be cleared safely" }
    }

    fun hasPendingIdentityRecovery(): Boolean =
        prefs.getString(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID, null)
            ?.let(ACCOUNT_ID::matches) == true

    @Synchronized
    fun completeIdentityRecovery(accountId: String) {
        require(ACCOUNT_ID.matches(accountId))
        val current = load()
        if (current?.accountId == accountId) {
            save(current.copy(identityRecoveryPending = false))
        } else {
            check(
                prefs.edit()
                    .remove(KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID)
                    .commit(),
            ) { "Account identity recovery marker could not be cleared safely" }
        }
    }

    fun lastAccountMode(): String? =
        prefs.getString(KEY_ACCOUNT_MODE, null)
            ?.takeIf { it == MODE_GUEST || it == MODE_GOOGLE }

    @Synchronized
    fun guestRequestId(): String {
        loadGuestRequestId()?.let { return it }
        val generated = UUID.randomUUID().toString().lowercase()
        persistGuestRequestId(generated)
        return generated
    }

    private fun loadGuestRequestId(): String? {
        val ivText = prefs.getString(KEY_GUEST_REQUEST_IV, null) ?: return null
        val ciphertextText = prefs.getString(KEY_GUEST_REQUEST_CIPHERTEXT, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)),
            )
            cipher.updateAAD(GUEST_REQUEST_AAD)
            val requestId = cipher.doFinal(Base64.decode(ciphertextText, Base64.NO_WRAP))
                .toString(Charsets.UTF_8)
                .trim()
                .lowercase()
            if (UUID_V4.matches(requestId)) requestId else {
                clearGuestRequestId()
                null
            }
        } catch (_: Exception) {
            clearGuestRequestId()
            null
        }
    }

    private fun persistGuestRequestId(requestId: String) {
        require(UUID_V4.matches(requestId))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(GUEST_REQUEST_AAD)
        val encrypted = cipher.doFinal(requestId.toByteArray(Charsets.UTF_8))
        check(
            prefs.edit()
                .putString(KEY_GUEST_REQUEST_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_GUEST_REQUEST_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .commit(),
        ) { "Guest-account recovery secret could not be encrypted and saved" }
    }

    private fun clearGuestRequestId() {
        check(
            prefs.edit()
                .remove(KEY_GUEST_REQUEST_IV)
                .remove(KEY_GUEST_REQUEST_CIPHERTEXT)
                .commit(),
        ) { "Guest-account recovery secret could not be cleared safely" }
    }

    @Synchronized
    fun pendingRefreshRequestId(): String {
        prefs.getString(KEY_PENDING_REFRESH_REQUEST_ID, null)
            ?.takeIf(UUID_V4::matches)
            ?.let { return it }
        val generated = UUID.randomUUID().toString().lowercase()
        check(prefs.edit().putString(KEY_PENDING_REFRESH_REQUEST_ID, generated).commit()) {
            "Account refresh recovery request could not be saved"
        }
        return generated
    }

    @Synchronized
    fun clear() {
        check(prefs.edit().clear().commit()) { "Account session data could not be cleared safely" }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        private val sessionCacheLock = Any()
        private var cachedRecord: String? = null
        private var cachedSession: AccountSession? = null

        const val PREFS_NAME = "ludoproof_account_session_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ludoproof_account_session_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_VERSION = "version"
        const val KEY_ACCOUNT_MODE = "account_mode"
        const val KEY_IDENTITY_RECOVERY_PENDING_ACCOUNT_ID = "identity_recovery_pending_account_id"
        const val MODE_GUEST = "GUEST"
        const val MODE_GOOGLE = "GOOGLE"
        const val KEY_IV = "session_iv"
        const val KEY_CIPHERTEXT = "session_ciphertext"
        const val KEY_GUEST_REQUEST_IV = "guest_request_iv"
        const val KEY_GUEST_REQUEST_CIPHERTEXT = "guest_request_ciphertext"
        const val KEY_PENDING_REFRESH_REQUEST_ID = "pending_refresh_request_id"
        val SESSION_AAD = "ludoproof:account-session:v1".toByteArray(Charsets.UTF_8)
        val GUEST_REQUEST_AAD = "ludoproof:guest-request:v1".toByteArray(Charsets.UTF_8)
        val ACCOUNT_ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        val UUID_V4 = ACCOUNT_ID
        val OPAQUE_BILLING_ID = Regex("^[a-f0-9]{64}$")
        val GUEST_DELETION_CREDENTIAL = Regex("^[a-f0-9]{64}$")
        val REFRESH_TOKEN = Regex("^lpr_[A-Za-z0-9_-]{43}$")
    }
}
