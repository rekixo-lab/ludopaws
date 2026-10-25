package com.rekixo.ludopaws.feature.account.data

import android.content.Context
import com.rekixo.ludopaws.BuildConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class AccountRepository(context: Context) {
    private val appContext = context.applicationContext
    private val sessions = AccountSessionStore(appContext)
    private val baseUrl = BuildConfig.LUDOPROOF_API_BASE_URL.trimEnd('/')

    @Synchronized
    fun ensureGuestSession(): AccountSession {
        val current = sessions.load()
        if (current != null) {
            return try {
                validSession(current)
            } catch (error: AccountApiException) {
                val recoverableGuestExpiry = error.status == 401 &&
                    error.code == "REFRESH_TOKEN_INVALID" &&
                    current.guestRecoveryEligible
                if (!recoverableGuestExpiry) throw error
                // Re-use the Keystore-encrypted idempotency credential to reopen a session on
                // the same guest account. Never do this for an account restored through Google.
                sessions.clearSession()
                createGuestRecoverySession()
            }
        }
        if (sessions.lastAccountMode() == "GOOGLE") {
            throw AccountApiException(
                401,
                "ACCOUNT_SESSION_REQUIRED",
                "Your saved account session is unavailable. Restore the Google-linked account instead of creating a new guest account.",
            )
        }
        return createGuestRecoverySession()
    }

    private fun createGuestRecoverySession(): AccountSession {
        val response = request(
            "POST",
            "/api/account/guest",
            JSONObject()
                .put("clientRequestId", sessions.guestRequestId())
                .put("displayName", "Guest User"),
        )
        return parseAndSaveSession(response, guestRecoveryEligible = true)
    }

    @Synchronized
    fun requireSession(): AccountSession {
        val current = sessions.load()
            ?: throw AccountApiException(401, "ACCOUNT_SESSION_REQUIRED", "Sign in or continue as a guest to use the store.")
        return validSession(current)
    }

    fun currentSession(): AccountSession? = sessions.load()

    @Synchronized
    fun validSession(current: AccountSession): AccountSession {
        val latest = sessions.load()
            ?: throw AccountApiException(401, "ACCOUNT_SESSION_REQUIRED", "Reconnect to your game account.")
        if (latest.accountId != current.accountId || latest.sessionId != current.sessionId) {
            throw AccountApiException(409, "ACCOUNT_SESSION_CHANGED", "The active account changed. Please try again.")
        }
        // Another request may already have rotated this session. Never reuse its old
        // refresh token or restore a cached session after account switch/logout.
        if (latest != current) return validSession(latest)
        val nowSeconds = System.currentTimeMillis() / 1000
        if (current.accessExpiresAt > nowSeconds + 45) return current
        val requestId = sessions.pendingRefreshRequestId()
        val response = request(
            "POST",
            "/api/account/refresh",
            JSONObject()
                .put("accountId", current.accountId)
                .put("sessionId", current.sessionId)
                .put("refreshToken", current.refreshToken)
                .put("clientRequestId", requestId),
        )
        return parseAndSaveSession(
            response,
            guestRecoveryEligible = current.guestRecoveryEligible,
            identityRecoveryPending = current.identityRecoveryPending,
            guestDeletionCredential = current.guestDeletionCredential,
        )
    }

    fun googleChallenge(): String {
        val response = request("POST", "/api/account/google/challenge", JSONObject())
        return response.getString("nonce").also {
            require(it.matches(Regex("^[a-f0-9]{64}$"))) { "Server returned an invalid Google sign-in challenge." }
        }
    }

    fun linkGoogleIdentity(idToken: String, nonce: String) {
        val session = requireSession()
        val response = request(
            "POST",
            "/api/account/google/link",
            JSONObject().put("idToken", idToken).put("nonce", nonce),
            session.accessToken,
        )
        val linkedProfile = response.optJSONObject("profile")
        synchronized(this) {
            val latest = sessions.load()
            if (latest != null && latest.accountId == session.accountId) {
                val profileName = linkedProfile
                    ?.optString("displayName")
                    ?.trim()
                    ?.take(24)
                    ?.takeIf(String::isNotBlank)
                    ?: latest.displayName
                // The server-confirmed Google link replaces guest-only recovery as the
                // identity recovery path. Clear the old guest key and persist GOOGLE mode.
                sessions.save(
                    latest.copy(
                        displayName = profileName,
                        guestRecoveryEligible = false,
                        guestDeletionCredential = null,
                    ),
                )
            }
        }
    }

    @Synchronized
    fun restoreGoogleAccount(idToken: String, nonce: String): AccountSession {
        val response = request(
            "POST",
            "/api/account/google/login",
            JSONObject()
                .put("idToken", idToken)
                .put("nonce", nonce),
        )
        return parseAndSaveSession(
            response,
            guestRecoveryEligible = false,
            identityRecoveryPending = true,
        )
    }

    fun wallet(): JSONObject {
        val session = requireSession()
        return request("GET", "/api/wallet", token = session.accessToken)
            .getJSONObject("wallet")
    }

    fun profile(): JSONObject {
        val session = requireSession()
        return request("GET", "/api/account/me", token = session.accessToken)
            .getJSONObject("profile")
    }

    fun updateDisplayName(displayName: String): JSONObject {
        val session = requireSession()
        val profile = request(
            "POST",
            "/api/account/profile",
            JSONObject().put("displayName", com.rekixo.ludopaws.feature.safety.FamilyOnlineSafety.publicName(displayName)),
            session.accessToken,
        ).getJSONObject("profile")
        updateCachedDisplayName(profile)
        return profile
    }

    @Synchronized
    private fun updateCachedDisplayName(profile: JSONObject) {
        val current = sessions.load() ?: return
        val name = profile.optString("displayName").trim().take(24)
        if (name.isBlank() || current.displayName == name) return
        sessions.save(current.copy(displayName = name))
    }

    fun storeStatus(): JSONObject = request("GET", "/api/store/status")

    fun storeCatalog(): JSONObject {
        val session = requireSession()
        return request("GET", "/api/store/catalog", token = session.accessToken)
    }

    fun verifyPlayPurchase(productId: String, purchaseToken: String): JSONObject {
        val session = requireSession()
        return request(
            "POST",
            "/api/store/purchases/verify",
            JSONObject()
                .put("productId", productId)
                .put("purchaseToken", purchaseToken),
            session.accessToken,
        )
    }

    fun purchaseCosmetic(cosmeticId: String, unlockChannel: String? = null): JSONObject {
        val session = requireSession()
        val body = JSONObject()
            .put("cosmeticId", cosmeticId)
            .put("clientRequestId", UUID.randomUUID().toString().lowercase())
        if (unlockChannel != null) body.put("unlockChannel", unlockChannel)
        return request(
            "POST",
            "/api/wallet/cosmetics/purchase",
            body,
            session.accessToken,
        )
    }

    fun selectCosmetic(cosmeticId: String): JSONObject {
        val session = requireSession()
        return request(
            "POST",
            "/api/wallet/cosmetics/select",
            JSONObject().put("cosmeticId", cosmeticId),
            session.accessToken,
        )
    }

    fun linkedIdentities(): JSONObject {
        val session = requireSession()
        return request("GET", "/api/account/links", token = session.accessToken)
    }

    fun recoverLinkedIdentities(): JSONObject {
        val session = requireSession()
        return request("GET", "/api/account/identities/recover", token = session.accessToken)
    }

    fun linkFriendIdentity(friendToken: String): JSONObject {
        val session = requireSession()
        return request(
            "POST",
            "/api/account/link/friend",
            JSONObject().put("friendToken", friendToken),
            session.accessToken,
        )
    }

    fun linkLeaderboardIdentity(profileToken: String): JSONObject {
        val session = requireSession()
        return request(
            "POST",
            "/api/account/link/leaderboard",
            JSONObject().put("profileToken", profileToken),
            session.accessToken,
        )
    }

    @Synchronized
    fun ensureGuestDeletionCredential(rotateExisting: Boolean = false): String {
        val session = requireSession()
        if (!session.guestRecoveryEligible) {
            throw AccountApiException(
                409,
                "GUEST_DELETION_CODE_NOT_AVAILABLE",
                "A delete-only code is available only while this account is a guest. Use Google sign-in for a linked account.",
            )
        }

        session.guestDeletionCredential?.let { existing ->
            if (!rotateExisting) return existing
        }

        // AccountDirectory generates the 256-bit secret server-side and stores only its hash.
        // Keep other installations' saved codes valid unless the owner explicitly replaces them.
        val rotate = rotateExisting
        val response = request(
            "POST",
            "/api/account/guest/deletion-code",
            JSONObject().put("rotate", rotate),
            session.accessToken,
        )
        val credential = response.optString("credential")
        if (!Regex("^[a-f0-9]{64}$").matches(credential)) {
            throw AccountApiException(
                503,
                "GUEST_DELETION_CODE_RESPONSE_INVALID",
                "The account service did not return a valid guest deletion code.",
            )
        }
        val latest = requireSession()
        if (latest.accountId != session.accountId || !latest.guestRecoveryEligible) {
            throw AccountApiException(
                409,
                "GUEST_DELETION_CODE_NOT_AVAILABLE",
                "This account is no longer an eligible guest account.",
            )
        }
        sessions.save(latest.copy(guestDeletionCredential = credential))
        return credential
    }

    @Synchronized
    fun deleteCurrentAccount(): JSONObject {
        val current = sessions.load()
            ?: throw AccountApiException(401, "ACCOUNT_SESSION_REQUIRED", "Sign in again before deleting your account.")
        val session = validSession(current)
        val response = request(
            "POST",
            "/api/account/delete",
            JSONObject().put("confirmation", "DELETE_MY_ACCOUNT"),
            session.accessToken,
        )
        if (!response.optBoolean("deleted", false)) {
            throw AccountApiException(502, "ACCOUNT_DELETE_INCOMPLETE", "The account service did not confirm deletion.")
        }
        sessions.clear()
        return response
    }

    @Synchronized
    fun logout() {
        val session = requireSession()
        request(
            "POST",
            "/api/account/logout",
            JSONObject(),
            session.accessToken,
        )
        sessions.clear()
    }

    @Synchronized
    fun clearLocalSession() = sessions.clearSession()

    fun requiresIdentityRecovery(): Boolean = sessions.hasPendingIdentityRecovery()

    /**
     * A previously Google-linked account must be restored before local social presence can
     * start if its encrypted session is missing or corrupt.
     */
    fun canStartFriendPresence(): Boolean {
        if (sessions.hasPendingIdentityRecovery()) return false
        if (sessions.load() != null) return true
        return sessions.lastAccountMode() != "GOOGLE"
    }

    fun completeIdentityRecovery() {
        val session = sessions.load()
            ?: throw AccountApiException(401, "ACCOUNT_SESSION_REQUIRED", "Restore your saved account to recover linked identities.")
        sessions.completeIdentityRecovery(session.accountId)
    }

    private fun parseAndSaveSession(
        response: JSONObject,
        guestRecoveryEligible: Boolean,
        identityRecoveryPending: Boolean = false,
        guestDeletionCredential: String? = null,
    ): AccountSession {
        val profile = response.optJSONObject("profile") ?: JSONObject()
        val session = AccountSession(
            accountId = response.getString("accountId"),
            sessionId = response.getString("sessionId"),
            accessToken = response.getString("accessToken"),
            refreshToken = response.getString("refreshToken"),
            billingAccountId = response.getString("billingAccountId"),
            accessExpiresAt = response.getLong("accessExpiresAt"),
            sessionExpiresAt = response.getLong("sessionExpiresAt"),
            displayName = profile.optString("displayName", "Guest User").take(24),
            guestRecoveryEligible = guestRecoveryEligible,
            identityRecoveryPending = identityRecoveryPending,
            guestDeletionCredential = guestDeletionCredential?.takeIf { guestRecoveryEligible },
        )
        sessions.save(session)
        return session
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null,
    ): JSONObject {
        require(baseUrl.startsWith("https://")) { "LudoProof API must use HTTPS" }
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "LudoProof-Android/" + BuildConfig.VERSION_NAME)
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_REQUEST_BYTES) { "Account request exceeded the safety limit." }
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { readUtf8Limited(it, MAX_RESPONSE_BYTES) }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) {
                throw AccountApiException(
                    status,
                    json.optString("error").ifBlank { "HTTP_$status" },
                    json.optString("message").ifBlank { "Account request failed with HTTP $status" },
                )
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun readUtf8Limited(stream: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw AccountApiException(502, "RESPONSE_TOO_LARGE", "Account service response exceeded the safety limit.")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val MAX_REQUEST_BYTES = 8 * 1024
        private const val MAX_RESPONSE_BYTES = 512 * 1024
    }
}

class AccountApiException(
    val status: Int,
    val code: String,
    override val message: String,
) : RuntimeException(message)
