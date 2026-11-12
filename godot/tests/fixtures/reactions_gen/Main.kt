// Reaction pipeline golden harness. Replays tests/fixtures/reactions_gen/inputs.json through the
// UNCHANGED live LudoPawsGameMomentDetector / LudoPawsReactionEngine / LudoPawsReactionDirector /
// LudoPawsIdleReactionPolicy / LudoPawsFeedbackLedger / LudoPawsFxPolicy and prints the results.
// The idle clock follows LudoPawsReactiveBoardView.updateIdleClock / playIdleReactionIfEligible.
import com.rekixo.ludopaws.HistoryEventSnapshot
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.PendingRollSnapshot
import com.rekixo.ludopaws.PlayerSnapshot
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsFeedbackLedger
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsFxPolicy
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsGameMomentDetector
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsIdleReactionPolicy
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReaction
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReactionDirector
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReactionEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name) || !has(name)) null else getInt(name)
private fun JSONObject.strOrNull(name: String): String? = if (isNull(name) || !has(name)) null else getString(name)

private fun event(e: JSONObject) = HistoryEventSnapshot(
    eventIndex = e.getInt("eventIndex"), playerId = e.getString("playerId"), roundId = null, proofDigest = null,
    outcome = e.intOrNull("outcome"), moveTokenIndex = e.intOrNull("moveTokenIndex"), captures = e.getInt("captures"),
)

private fun snapshot(s: JSONObject, history: List<HistoryEventSnapshot>): MatchSnapshot {
    val players = s.getJSONArray("players").let { a ->
        (0 until a.length()).map { i ->
            val p = a.getJSONObject(i)
            val tokens = p.getJSONArray("tokens").let { t -> (0 until t.length()).map(t::getInt) }
            PlayerSnapshot(playerId = p.getString("playerId"), displayName = "", color = p.getString("color"),
                seat = p.getInt("seat"), tokens = tokens, teamId = p.strOrNull("teamId"))
        }
    }
    val pending = if (s.isNull("pendingRoll")) null else s.getJSONObject("pendingRoll").let { r ->
        val legal = r.getJSONArray("legalTokenIndexes").let { l -> (0 until l.length()).map(l::getInt).toSet() }
        PendingRollSnapshot(status = r.getString("status"), seat = r.getInt("seat"), eventIndex = r.getInt("eventIndex"),
            eventId = null, roundId = null, serverCommitment = null, clientCommitment = null, revealDeadlineAt = null,
            proofDigest = null, outcome = r.intOrNull("outcome"), legalTokenIndexes = legal)
    }
    return MatchSnapshot(matchId = s.getString("matchId"), status = s.getString("status"), hostPlayerId = "",
        players = players, turnSeat = s.getInt("turnSeat"), randomEventIndex = s.getInt("randomEventIndex"),
        pendingRoll = pending, winnerPlayerId = s.strOrNull("winnerPlayerId"), rulesetId = "", history = history,
        actingSeat = s.intOrNull("actingSeat"), winnerTeamId = s.strOrNull("winnerTeamId"))
}

private fun r(x: LudoPawsReaction) = "${x.reactionKey}|${x.priority}|${x.animationCue}|${x.voiceCue}|${x.seat}"

fun main(args: Array<String>) {
    val input = JSONObject(File(args[0]).readText())
    val steps = input.getJSONArray("steps")
    val director = LudoPawsReactionDirector()
    val ledger = LudoPawsFeedbackLedger()
    val history = mutableListOf<HistoryEventSnapshot>()
    var previous: MatchSnapshot? = null
    var meaningfulKey: String? = null
    var lastChange = 0L
    var lastIdleKey: String? = null
    val out = JSONArray()
    for (i in 0 until steps.length()) {
        val step = steps.getJSONObject(i)
        val t = step.getLong("t")
        val h = step.getJSONArray("h")
        // A new match starts with an empty history.
        if (previous != null && previous.matchId != step.getJSONObject("s").getString("matchId")) history.clear()
        for (k in 0 until h.length()) {
            val e = event(h.getJSONObject(k))
            val at = history.indexOfFirst { it.eventIndex == e.eventIndex }
            if (at >= 0) history[at] = e else history += e
        }
        while (history.size > 100) history.removeAt(0)
        val current = snapshot(step.getJSONObject("s"), history.toList())
        // updateIdleClock
        val nextKey = LudoPawsIdleReactionPolicy.meaningfulStateKey(current)
        if (current.status != "ACTIVE") {
            meaningfulKey = nextKey; lastChange = t; lastIdleKey = null
        } else if (nextKey != meaningfulKey) {
            meaningfulKey = nextKey; lastChange = t; lastIdleKey = null
        }
        val moments = LudoPawsGameMomentDetector.detect(previous, current).map {
            "${it.type}|${it.playerId}|${it.seat}|${it.eventIndex}|${it.tokenIndex ?: ""}|${it.relatedPlayerId ?: ""}|${it.value ?: ""}"
        }
        val derived = LudoPawsReactionEngine.derive(previous, current)
        val decision = director.submit(derived, t)
        val ledgerKept = ledger.filterReactions(derived).map { it.reactionKey }
        val probe = if (i + 1 < steps.length()) steps.getJSONObject(i + 1).getLong("t") - 1 else t + 30_000L
        val delay = LudoPawsIdleReactionPolicy.delayUntilEligibleMillis(current, lastChange, probe)
        val idleKey = LudoPawsIdleReactionPolicy.idleReactionKey(current)
        var idle = emptyList<LudoPawsReaction>()
        if (delay == 0L && idleKey != null && idleKey != lastIdleKey) {
            idle = LudoPawsReactionEngine.deriveIdle(current, probe, lastChange, LudoPawsIdleReactionPolicy.IDLE_THRESHOLD_MILLIS)
            if (idle.isNotEmpty()) lastIdleKey = idleKey
        }
        val row = JSONObject()
        if (moments.isNotEmpty()) row.put("m", JSONArray(moments))
        if (derived.isNotEmpty()) row.put("d", JSONArray(derived.map(::r)))
        if (decision.reactions.isNotEmpty()) row.put("p", JSONArray(decision.reactions.map { it.reactionKey }))
        if (decision.interrupted) row.put("i", true)
        if (decision.queuedBatchCount > 0) row.put("q", decision.queuedBatchCount)
        if (ledgerKept.size != derived.size) row.put("l", JSONArray(ledgerKept))
        row.put("k", meaningfulKey ?: JSONObject.NULL)
        if (idleKey != null) row.put("ik", idleKey)
        row.put("w", delay ?: JSONObject.NULL)
        if (idle.isNotEmpty()) row.put("idle", JSONArray(idle.map(::r)))
        out.put(row)
        previous = current
    }
    val fx = JSONObject()
    AnimationCue.values().forEach { cue ->
        val p = LudoPawsFxPolicy.plan(cue)
        fx.put(cue.name, JSONArray(listOf(p.durationMs, p.particleCount, p.allowTranslation, p.allowRotation, p.allowShake, p.allowConfetti)))
    }
    println(JSONObject().put("inputHash", input.getString("inputHash")).put("fx", fx).put("steps", out).toString())
}
