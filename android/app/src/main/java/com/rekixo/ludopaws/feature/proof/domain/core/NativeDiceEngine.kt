package com.rekixo.ludopaws

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Native Ludo Paws dice engine using Android's platform CSPRNG and unbiased
 * uint32 rejection sampling. Receipts are reproducible evidence, not third-party
 * certification or proof that a local device operator could not cheat.
 */
object NativeDiceEngine {
    const val PROTOCOL = "ludopaws-native-dice-v1"
    const val ALGORITHM = "secure-random-uint32-rejection-sampling-v1"
    const val RULESET_ID = ClassicRuleset.ID

    private const val DOMAIN = "ludopaws:native-dice:v1:"
    private const val UINT32_RANGE = 0x1_0000_0000L
    private const val ACCEPT_LIMIT = UINT32_RANGE - (UINT32_RANGE % 6L)
    private const val MAX_REJECTIONS = 128
    private val secureRandom = SecureRandom()

    fun createReceipt(
        matchId: String,
        eventIndex: Int,
        playerId: String,
        stateMaterial: String,
        rulesetId: String = RULESET_ID,
        issuedAt: Long = System.currentTimeMillis(),
    ): NativeDiceReceipt {
        require(matchId.isNotBlank() && eventIndex >= 0 && playerId.isNotBlank())
        require(rulesetId.isNotBlank() && issuedAt >= 0)

        var rejected = 0
        var word: Long
        while (true) {
            val bytes = ByteArray(4)
            secureRandom.nextBytes(bytes)
            word = ((bytes[0].toLong() and 0xffL) shl 24) or
                ((bytes[1].toLong() and 0xffL) shl 16) or
                ((bytes[2].toLong() and 0xffL) shl 8) or
                (bytes[3].toLong() and 0xffL)
            if (word < ACCEPT_LIMIT) break
            rejected += 1
            check(rejected <= MAX_REJECTIONS) {
                "native dice rejection sampling did not converge"
            }
        }

        val receipt = NativeDiceReceipt(
            protocol = PROTOCOL,
            algorithm = ALGORITHM,
            matchId = matchId,
            eventIndex = eventIndex,
            playerId = playerId,
            drawWordHex = word.toString(16).padStart(8, '0'),
            rejectedSamples = rejected,
            outcome = (word % 6L).toInt() + 1,
            roundId = UUID.randomUUID().toString(),
            issuedAt = issuedAt,
            actorHash = sha256Hex(DOMAIN + "actor:" + playerId),
            previousStateHash = sha256Hex(DOMAIN + "state:" + stateMaterial),
            rulesetHash = sha256Hex(DOMAIN + "ruleset:" + rulesetId),
            proofDigest = "",
        )
        return receipt.copy(proofDigest = receiptDigest(receipt))
    }

    fun verify(receipt: NativeDiceReceipt): Boolean = runCatching {
        if (
            receipt.protocol != PROTOCOL ||
            receipt.algorithm != ALGORITHM ||
            receipt.matchId.isBlank() ||
            receipt.eventIndex < 0 ||
            receipt.playerId.isBlank() ||
            !Regex("^[0-9a-f]{8}$").matches(receipt.drawWordHex) ||
            receipt.rejectedSamples !in 0..MAX_REJECTIONS ||
            receipt.outcome !in 1..6 ||
            receipt.roundId.isBlank() ||
            receipt.issuedAt < 0 ||
            !isDigest(receipt.actorHash) ||
            !isDigest(receipt.previousStateHash) ||
            !isDigest(receipt.rulesetHash) ||
            !isDigest(receipt.proofDigest)
        ) return false

        val word = receipt.drawWordHex.toLong(16)
        word < ACCEPT_LIMIT &&
            ((word % 6L).toInt() + 1 == receipt.outcome) &&
            receipt.actorHash == sha256Hex(DOMAIN + "actor:" + receipt.playerId) &&
            receiptDigest(receipt) == receipt.proofDigest
    }.getOrDefault(false)

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** Deterministic JSON for maps/lists made only of primitive values. */
    fun canonicalJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> JSONObject.quote(value)
        is Boolean -> value.toString()
        is Byte, is Short, is Int, is Long -> value.toString()
        is Number -> value.toString()
        is Map<*, *> -> value.entries
            .sortedBy { it.key.toString() }
            .joinToString(",", "{", "}") { (key, item) ->
                JSONObject.quote(key.toString()) + ":" + canonicalJson(item)
            }
        is Iterable<*> -> value.joinToString(",", "[", "]") { canonicalJson(it) }
        is Array<*> -> value.joinToString(",", "[", "]") { canonicalJson(it) }
        else -> error("unsupported native dice JSON value: " + value.javaClass.name)
    }

    fun canonicalParts(vararg values: String?): String =
        values.joinToString("") { value ->
            if (value == null) {
                "-1:"
            } else {
                value.toByteArray(StandardCharsets.UTF_8).size.toString() + ":" + value
            }
        }

    private fun receiptDigest(receipt: NativeDiceReceipt): String =
        sha256Hex(
            DOMAIN + "receipt:" + canonicalParts(
                receipt.protocol,
                receipt.algorithm,
                receipt.matchId,
                receipt.eventIndex.toString(),
                receipt.playerId,
                receipt.drawWordHex,
                receipt.rejectedSamples.toString(),
                receipt.outcome.toString(),
                receipt.roundId,
                receipt.issuedAt.toString(),
                receipt.actorHash,
                receipt.previousStateHash,
                receipt.rulesetHash,
            ),
        )

    private fun isDigest(value: String): Boolean = Regex("^[0-9a-f]{64}$").matches(value)
}

data class NativeDiceReceipt(
    val protocol: String,
    val algorithm: String,
    val matchId: String,
    val eventIndex: Int,
    val playerId: String,
    val drawWordHex: String,
    val rejectedSamples: Int,
    val outcome: Int,
    val roundId: String,
    val issuedAt: Long,
    val actorHash: String,
    val previousStateHash: String,
    val rulesetHash: String,
    val proofDigest: String,
) {
    fun verify(): Boolean = NativeDiceEngine.verify(this)

    fun toJson(): JSONObject = JSONObject()
        .put("protocol", protocol)
        .put("algorithm", algorithm)
        .put("matchId", matchId)
        .put("eventIndex", eventIndex)
        .put("playerId", playerId)
        .put("drawWordHex", drawWordHex)
        .put("rejectedSamples", rejectedSamples)
        .put("outcome", outcome)
        .put("roundId", roundId)
        .put("issuedAt", issuedAt)
        .put("actorHash", actorHash)
        .put("previousStateHash", previousStateHash)
        .put("rulesetHash", rulesetHash)
        .put("proofDigest", proofDigest)

    companion object {
        fun fromJson(value: JSONObject?): NativeDiceReceipt? = runCatching {
            requireNotNull(value)
            NativeDiceReceipt(
                protocol = value.getString("protocol"),
                algorithm = value.getString("algorithm"),
                matchId = value.getString("matchId"),
                eventIndex = value.getInt("eventIndex"),
                playerId = value.getString("playerId"),
                drawWordHex = value.getString("drawWordHex"),
                rejectedSamples = value.getInt("rejectedSamples"),
                outcome = value.getInt("outcome"),
                roundId = value.getString("roundId"),
                issuedAt = value.getLong("issuedAt"),
                actorHash = value.getString("actorHash"),
                previousStateHash = value.getString("previousStateHash"),
                rulesetHash = value.getString("rulesetHash"),
                proofDigest = value.getString("proofDigest"),
            ).takeIf { it.verify() }
        }.getOrNull()
    }
}
