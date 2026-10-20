package com.rekixo.ludopaws.feature.friends.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class FriendCredential(
    val friendId: String,
    val friendToken: String,
    val registrationRequestId: String,
)

class FriendIdentityStore(
    context: Context,
) {
    private val prefs =
        context
            .applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

    fun registrationRequestId():
        String {
        synchronized(IDENTITY_LOCK) {
            loadRegistrationRequestId()
                ?.let {
                    return it
                }

            val generated =
                UUID.randomUUID()
                    .toString()
                    .lowercase(
                        Locale.ROOT,
                    )
            persistRegistrationRequestId(
                generated,
            )
            return generated
        }
    }

    fun save(
        credential: FriendCredential,
    ) {
        synchronized(IDENTITY_LOCK) {
            require(
                FRIEND_ID.matches(
                    credential.friendId,
                ),
            )
            require(
                FRIEND_TOKEN.matches(
                    credential.friendToken,
                ),
            )
            require(
                UUID_V4.matches(
                    credential.registrationRequestId,
                ),
            )

            val protectedRequestId =
                registrationRequestId()
            require(
                protectedRequestId ==
                    credential.registrationRequestId,
            ) {
                "Friend credential registration identity does not match this device"
            }

            val cipher =
                Cipher.getInstance(
                    TRANSFORMATION,
                )
            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey(),
            )
            cipher.updateAAD(
                credentialAad(
                    credential.friendId,
                    protectedRequestId,
                ),
            )
            val ciphertext =
                cipher.doFinal(
                    credential
                        .friendToken
                        .toByteArray(
                            Charsets.UTF_8,
                        ),
                )

            check(
                prefs.edit()
                    .putString(
                        KEY_FRIEND_ID,
                        credential.friendId,
                    )
                    .putString(
                        KEY_TOKEN_IV,
                        Base64.encodeToString(
                            cipher.iv,
                            Base64.NO_WRAP,
                        ),
                    )
                    .putString(
                        KEY_TOKEN_CIPHERTEXT,
                        Base64.encodeToString(
                            ciphertext,
                            Base64.NO_WRAP,
                        ),
                    )
                    .commit(),
            ) {
                "Friend credential could not be persisted"
            }
        }
    }

    /**
     * Restores a credential returned by the authenticated account-recovery API.
     * The registration recovery secret and token are encrypted with Android Keystore.
     */
    fun restoreFromAccount(credential: FriendCredential) {
        synchronized(IDENTITY_LOCK) {
            require(FRIEND_ID.matches(credential.friendId))
            require(FRIEND_TOKEN.matches(credential.friendToken))
            require(UUID_V4.matches(credential.registrationRequestId))
            persistRegistrationRequestId(credential.registrationRequestId)
            save(credential)
        }
    }

    fun clearAllForAccountSwitch() {
        synchronized(IDENTITY_LOCK) {
            check(prefs.edit().clear().commit()) {
                "Previous account's friend credentials could not be cleared safely"
            }
        }
    }

    fun load():
        FriendCredential? {
        synchronized(IDENTITY_LOCK) {
            val friendId =
                prefs.getString(
                    KEY_FRIEND_ID,
                    null,
                )
                    ?: return null
            val requestId =
                loadRegistrationRequestId()
                    ?: run {
                        clearCredentialOnly()
                        return null
                    }
            val ivText =
                prefs.getString(
                    KEY_TOKEN_IV,
                    null,
                )
                    ?: return null
            val ciphertextText =
                prefs.getString(
                    KEY_TOKEN_CIPHERTEXT,
                    null,
                )
                    ?: return null

            if (
                !FRIEND_ID.matches(
                    friendId,
                ) ||
                !UUID_V4.matches(
                    requestId,
                )
            ) {
                clearCredentialOnly()
                return null
            }

            return try {
                val cipher =
                    Cipher.getInstance(
                        TRANSFORMATION,
                    )
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(
                        128,
                        Base64.decode(
                            ivText,
                            Base64.NO_WRAP,
                        ),
                    ),
                )
                cipher.updateAAD(
                    credentialAad(
                        friendId,
                        requestId,
                    ),
                )

                val token =
                    cipher.doFinal(
                        Base64.decode(
                            ciphertextText,
                            Base64.NO_WRAP,
                        ),
                    )
                        .toString(
                            Charsets.UTF_8,
                        )

                if (
                    !FRIEND_TOKEN.matches(
                        token,
                    )
                ) {
                    clearCredentialOnly()
                    null
                } else {
                    FriendCredential(
                        friendId =
                            friendId,
                        friendToken =
                            token,
                        registrationRequestId =
                            requestId,
                    )
                }
            } catch (_: Exception) {
                clearCredentialOnly()
                null
            }
        }
    }

    fun clearCredentialOnly() {
        synchronized(IDENTITY_LOCK) {
            prefs.edit()
                .remove(
                    KEY_FRIEND_ID,
                )
                .remove(
                    KEY_TOKEN_IV,
                )
                .remove(
                    KEY_TOKEN_CIPHERTEXT,
                )
                .apply()
        }
    }

    fun clearCredentialIfMatches(expected: FriendCredential) {
        synchronized(IDENTITY_LOCK) {
            if (load() == expected) clearCredentialOnly()
        }
    }

    private fun persistRegistrationRequestId(
        requestId: String,
    ) {
        require(
            UUID_V4.matches(
                requestId,
            ),
        )

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION,
            )
        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey(),
        )
        cipher.updateAAD(
            REGISTRATION_AAD,
        )

        val ciphertext =
            cipher.doFinal(
                requestId
                    .toByteArray(
                        Charsets.UTF_8,
                    ),
            )

        check(
            prefs.edit()
                .putString(
                    KEY_REGISTRATION_IV,
                    Base64.encodeToString(
                        cipher.iv,
                        Base64.NO_WRAP,
                    ),
                )
                .putString(
                    KEY_REGISTRATION_CIPHERTEXT,
                    Base64.encodeToString(
                        ciphertext,
                        Base64.NO_WRAP,
                    ),
                )
                .commit(),
        ) {
            "Friend registration recovery secret could not be persisted"
        }
    }

    private fun loadRegistrationRequestId():
        String? {
        val ivText =
            prefs.getString(
                KEY_REGISTRATION_IV,
                null,
            )
                ?: return null
        val ciphertextText =
            prefs.getString(
                KEY_REGISTRATION_CIPHERTEXT,
                null,
            )
                ?: return null

        return try {
            val cipher =
                Cipher.getInstance(
                    TRANSFORMATION,
                )
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(
                    128,
                    Base64.decode(
                        ivText,
                        Base64.NO_WRAP,
                    ),
                ),
            )
            cipher.updateAAD(
                REGISTRATION_AAD,
            )
            val requestId =
                cipher.doFinal(
                    Base64.decode(
                        ciphertextText,
                        Base64.NO_WRAP,
                    ),
                )
                    .toString(
                        Charsets.UTF_8,
                    )
                    .trim()
                    .lowercase(
                        Locale.ROOT,
                    )

            requestId
                .takeIf {
                    UUID_V4.matches(
                        it,
                    )
                }
                ?: run {
                    clearAllIdentity()
                    null
                }
        } catch (_: Exception) {
            clearAllIdentity()
            null
        }
    }

    private fun clearAllIdentity() {
        prefs.edit()
            .clear()
            .apply()
    }

    private fun secretKey():
        SecretKey {
        val keyStore =
            KeyStore
                .getInstance(
                    ANDROID_KEYSTORE,
                )
                .apply {
                    load(
                        null,
                    )
                }

        val existing =
            keyStore.getKey(
                KEY_ALIAS,
                null,
            ) as? SecretKey
        if (
            existing !=
            null
        ) {
            return existing
        }

        val generator =
            KeyGenerator
                .getInstance(
                    KeyProperties
                        .KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE,
                )
        generator.init(
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or
                        KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(
                    KeyProperties
                        .BLOCK_MODE_GCM,
                )
                .setEncryptionPaddings(
                    KeyProperties
                        .ENCRYPTION_PADDING_NONE,
                )
                .setRandomizedEncryptionRequired(
                    true,
                )
                .build(),
        )
        return generator
            .generateKey()
    }

    private fun credentialAad(
        friendId: String,
        requestId: String,
    ):
        ByteArray =
        (
            "ludoproof:friend-credential:v1:" +
                friendId +
                ":" +
                requestId
            )
            .toByteArray(
                Charsets.UTF_8,
            )

    private companion object {
        val IDENTITY_LOCK = Any()
        const val PREFS_NAME =
            "ludoproof_friend_identity_v1"
        const val ANDROID_KEYSTORE =
            "AndroidKeyStore"
        const val KEY_ALIAS =
            "ludoproof_friend_identity_key_v1"
        const val TRANSFORMATION =
            "AES/GCM/NoPadding"
        const val KEY_FRIEND_ID =
            "friend_id"
        const val KEY_REGISTRATION_IV =
            "registration_request_iv"
        const val KEY_REGISTRATION_CIPHERTEXT =
            "registration_request_ciphertext"
        const val KEY_TOKEN_IV =
            "friend_token_iv"
        const val KEY_TOKEN_CIPHERTEXT =
            "friend_token_ciphertext"

        val REGISTRATION_AAD =
            "ludoproof:friend-registration:v1"
                .toByteArray(
                    Charsets.UTF_8,
                )

        val FRIEND_ID =
            Regex(
                "^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$",
            )
        val FRIEND_TOKEN =
            Regex(
                "^lf_[A-Za-z0-9_-]{32,}$",
            )
        val UUID_V4 =
            Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            )
    }
}
