package com.rekixo.ludopaws.feature.startup

import java.util.concurrent.Executor

enum class StartupAccountState { CHECKING, GUEST_READY, SAVED_READY, OFFLINE, RESTORE_REQUIRED, RETRY_REQUIRED }

data class StartupAccountSnapshot(val state: StartupAccountState, val accountId: String? = null) {
    val settled: Boolean get() = state != StartupAccountState.CHECKING
    val message: String get() = when (state) {
        StartupAccountState.CHECKING -> "Connecting your guest account…"
        StartupAccountState.GUEST_READY -> "Guest account ready"
        StartupAccountState.SAVED_READY -> "Welcome back"
        StartupAccountState.OFFLINE -> "Offline play available"
        StartupAccountState.RESTORE_REQUIRED -> "Restore your saved account to reconnect"
        StartupAccountState.RETRY_REQUIRED -> "Couldn't connect — offline play available"
    }
}

/** Main-thread-owned coordinator. Workers return through dispatch; observers never own a request. */
class StartupSessionCoordinator(
    private val worker: Executor,
    private val dispatch: (() -> Unit) -> Unit,
    private val load: () -> StartupAccountSnapshot,
    private val isCurrent: (StartupAccountSnapshot) -> Boolean,
) {
    var snapshot = StartupAccountSnapshot(StartupAccountState.CHECKING)
        private set
    var hasShownHome = false
    private var inFlight = false
    private var online = false
    private val observers = linkedSetOf<(StartupAccountSnapshot) -> Unit>()

    fun subscribe(observer: (StartupAccountSnapshot) -> Unit) {
        observers += observer
        observer(snapshot)
    }

    fun unsubscribe(observer: (StartupAccountSnapshot) -> Unit) { observers -= observer }

    fun refresh(isOnline: Boolean) {
        online = isOnline
        if (!isOnline) {
            publish(StartupAccountSnapshot(StartupAccountState.OFFLINE))
            return
        }
        if (inFlight) {
            if (snapshot.state == StartupAccountState.OFFLINE) publish(StartupAccountSnapshot(StartupAccountState.CHECKING))
            return
        }
        inFlight = true
        publish(StartupAccountSnapshot(StartupAccountState.CHECKING))
        try {
            worker.execute {
                val result = try { load() } catch (_: Exception) {
                    StartupAccountSnapshot(StartupAccountState.RETRY_REQUIRED)
                }
                dispatch {
                    inFlight = false
                    publish(when {
                        !online -> StartupAccountSnapshot(StartupAccountState.OFFLINE)
                        result.accountId != null && !matchesCurrentAccount(result) ->
                            StartupAccountSnapshot(StartupAccountState.RETRY_REQUIRED)
                        else -> result
                    })
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            inFlight = false
            publish(StartupAccountSnapshot(StartupAccountState.RETRY_REQUIRED))
        }
    }

    private fun matchesCurrentAccount(value: StartupAccountSnapshot): Boolean =
        try { isCurrent(value) } catch (_: Exception) { false }

    private fun publish(value: StartupAccountSnapshot) {
        snapshot = value
        observers.toList().forEach { it(value) }
    }
}

object StartupReadiness {
    const val ACCOUNT_GRACE_MS = 3_000L
    const val LOCAL_WATCHDOG_MS = 5_000L
    fun canBootstrapAccount(online: Boolean, pendingOwnerSaved: Boolean): Boolean = online && pendingOwnerSaved
    fun canShowHome(localReady: Boolean, accountSettled: Boolean, elapsedMs: Long): Boolean =
        localReady && (accountSettled || elapsedMs >= ACCOUNT_GRACE_MS)
    fun localTimedOut(localReady: Boolean, elapsedMs: Long): Boolean =
        !localReady && elapsedMs >= LOCAL_WATCHDOG_MS
}
