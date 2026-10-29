package com.rekixo.ludopaws.feature.startup

enum class PlayGamesStartupState {
    CHECKING, CONNECTED, UNAVAILABLE, PRIVACY_REQUIRED, NOT_CONFIGURED;

    val settled: Boolean get() = this != CHECKING
    val message: String get() = when (this) {
        CHECKING -> "Connecting to Google Play Games…"
        CONNECTED -> "Google Play Games connected"
        UNAVAILABLE -> "Play Games unavailable — you can still play"
        PRIVACY_REQUIRED -> "Welcome to Ludo Paws"
        NOT_CONFIGURED -> "Game preview — Play Games not configured"
    }
}

/** Main-thread state only. Platform identity never changes the game's account or wallet. */
class PlayGamesStartupStateMachine {
    var state = PlayGamesStartupState.CHECKING
        private set
    private var generation = 0
    private var inFlight = false
    private val observers = linkedSetOf<(PlayGamesStartupState) -> Unit>()

    fun subscribe(observer: (PlayGamesStartupState) -> Unit) {
        observers += observer
        observer(state)
    }

    fun unsubscribe(observer: (PlayGamesStartupState) -> Unit) { observers -= observer }

    fun refresh(allowed: Boolean, configured: Boolean, start: ((Boolean) -> Unit) -> Unit) {
        if (!allowed || !configured) {
            generation++
            inFlight = false
            publish(if (!allowed) PlayGamesStartupState.PRIVACY_REQUIRED else PlayGamesStartupState.NOT_CONFIGURED)
            return
        }
        if (inFlight) return
        inFlight = true
        val request = ++generation
        publish(PlayGamesStartupState.CHECKING)
        val complete: (Boolean) -> Unit = { connected ->
            if (request == generation && inFlight) {
                inFlight = false
                publish(if (connected) PlayGamesStartupState.CONNECTED else PlayGamesStartupState.UNAVAILABLE)
            }
        }
        try { start(complete) } catch (_: Exception) { complete(false) }
    }

    private fun publish(value: PlayGamesStartupState) {
        state = value
        observers.toList().forEach { it(value) }
    }
}
