package com.rekixo.ludopaws.feature.store.data.billing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PendingStorePurchase(
    val productId: String,
    val purchaseToken: String,
    val accountId: String,
)

/** A pending Play token may be verified only by the game account that bought it. */
internal object PendingPurchaseAccountPolicy {
    fun mayVerify(purchaseAccountId: String, currentBillingAccountId: String?): Boolean =
        currentBillingAccountId != null && purchaseAccountId == currentBillingAccountId
}

class PendingStorePurchaseStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<PendingStorePurchase> {
        val raw = preferences.getString(KEY_PENDING_PURCHASES, null) ?: return emptyList()
        val plaintext = if (raw.startsWith(ENCRYPTED_PREFIX)) {
            decrypt(raw)
        } else {
            // Migrate pre-encryption private preferences without discarding outstanding purchases.
            raw
        }
        val values = try {
            JSONArray(plaintext)
        } catch (error: Exception) {
            // Fail closed and preserve the stored value; never silently lose a pending paid item.
            throw IllegalStateException("Pending Play purchase recovery data is unreadable; no entitlement was granted.", error)
        }
        require(values.length() <= MAX_PENDING_PURCHASES) {
            "Pending Play purchase recovery data exceeds the safety limit."
        }
        val purchases = List(values.length()) { index ->
            val value = values.getJSONObject(index)
            PendingStorePurchase(
                productId = value.getString("productId"),
                purchaseToken = value.getString("purchaseToken"),
                accountId = value.getString("accountId"),
            ).also(::validate)
        }
        if (!raw.startsWith(ENCRYPTED_PREFIX)) persist(purchases)
        return purchases
    }

    @Synchronized
    fun add(purchase: PendingStorePurchase) {
        validate(purchase)
        val current = all()
        val existing = current.firstOrNull { it.purchaseToken == purchase.purchaseToken }
        if (existing != null) {
            check(existing == purchase) {
                "A pending Play purchase token conflicts with its saved product details."
            }
            return
        }
        check(current.size < MAX_PENDING_PURCHASES) {
            "Too many pending Play purchases are stored; no new purchase was accepted."
        }
        persist(current + purchase)
    }

    @Synchronized
    fun remove(purchaseToken: String) {
        require(purchaseToken.length in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH)
        val current = all()
        persist(current.filterNot { it.purchaseToken == purchaseToken })
    }

    @Synchronized
    fun removeForAccount(billingAccountId: String) {
        require(ACCOUNT_ID_REGEX.matches(billingAccountId)) { "Pending Play account binding is invalid." }
        persist(all().filterNot { it.accountId == billingAccountId })
    }

    private fun validate(purchase: PendingStorePurchase) {
        require(PRODUCT_ID_REGEX.matches(purchase.productId)) { "Pending Play product ID is invalid." }
        require(purchase.purchaseToken.length in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH) {
            "Pending Play purchase token is invalid."
        }
        require(ACCOUNT_ID_REGEX.matches(purchase.accountId)) { "Pending Play account binding is invalid." }
    }

    private fun persist(purchases: List<PendingStorePurchase>) {
        require(purchases.size <= MAX_PENDING_PURCHASES)
        val json = JSONArray().apply {
            purchases.forEach { purchase ->
                validate(purchase)
                put(
                    JSONObject()
                        .put("productId", purchase.productId)
                        .put("purchaseToken", purchase.purchaseToken)
                        .put("accountId", purchase.accountId),
                )
            }
        }
        val encrypted = encrypt(json.toString())
        check(
            preferences.edit()
                .putString(KEY_PENDING_PURCHASES, encrypted)
                .commit(),
        ) { "Could not securely save pending Play purchase verification." }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(PENDING_PURCHASES_AAD)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return ENCRYPTED_PREFIX +
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP or Base64.URL_SAFE) +
            "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decrypt(encoded: String): String {
        val payload = encoded.removePrefix(ENCRYPTED_PREFIX)
        val delimiter = payload.indexOf('.')
        require(delimiter in 1 until payload.lastIndex) { "Pending Play purchase ciphertext is malformed." }
        val iv = Base64.decode(payload.substring(0, delimiter), Base64.NO_WRAP or Base64.URL_SAFE)
        val ciphertext = Base64.decode(payload.substring(delimiter + 1), Base64.NO_WRAP or Base64.URL_SAFE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(PENDING_PURCHASES_AAD)
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
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
        const val PREFERENCES_NAME = "ludoproof_wallet"
        const val KEY_PENDING_PURCHASES = "pending_play_purchases"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ludoproof_pending_play_purchases_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENCRYPTED_PREFIX = "v1."
        const val MIN_TOKEN_LENGTH = 8
        const val MAX_TOKEN_LENGTH = 2048
        const val MAX_PENDING_PURCHASES = 100
        val PENDING_PURCHASES_AAD = "ludoproof:pending-play-purchases:v1".toByteArray(Charsets.UTF_8)
        val ACCOUNT_ID_REGEX = Regex("^[a-f0-9]{64}$")
        val PRODUCT_ID_REGEX = Regex("^[A-Za-z0-9._-]{1,128}$")
    }
}
