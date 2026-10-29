package com.rekixo.ludopaws.feature.team.data.local

import android.content.Context
import com.rekixo.ludopaws.ConnectivityMonitor
import com.rekixo.ludopaws.LudoProofApplication
import com.rekixo.ludopaws.feature.leaderboard.data.local.LeaderboardIdentityStore
import com.rekixo.ludopaws.feature.team.data.remote.TeamMatchmakingApi
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Reconcile an explicit leave request without navigating or abandoning a matched room. */
internal object TeamCancellationRecovery {
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    fun request(context: Context, onMatched: (() -> Unit)? = null) {
        val app = context.applicationContext as LudoProofApplication
        val tickets = TeamMatchmakingStore(app)
        if (!tickets.cancellationRequested() || !ConnectivityMonitor(app) { }.isOnline() || !busy.compareAndSet(false, true)) return
        worker.execute {
            try {
                val ticket = tickets.load() ?: return@execute
                val owner = app.accountRepository.currentSession()?.accountId ?: return@execute
                val identity = LeaderboardIdentityStore(app).load() ?: return@execute
                tickets.bindOwner(ticket.requestId)
                val result = TeamMatchmakingApi().cancel(identity.profileToken, ticket.requestId)
                if (owner == app.accountRepository.currentSession()?.accountId && tickets.load()?.requestId == ticket.requestId) {
                    when (result.optString("status")) {
                        "CANCELLED", "IDLE" -> tickets.clearIfRequest(ticket.requestId)
                        "MATCHED" -> if (tickets.markMatched(ticket.requestId)) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (owner == app.accountRepository.currentSession()?.accountId && tickets.awaitingAssignment()) onMatched?.invoke()
                            }
                        }
                    }
                }
                // Only the foreground Team Up screen may obtain and open the
                // complete authoritative assignment. No background navigation.
            } catch (error: Exception) {
                android.util.Log.w("TeamCancellation", "Saved cancellation will retry on reconnect", error)
            } finally { busy.set(false) }
        }
    }
}
