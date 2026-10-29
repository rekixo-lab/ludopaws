package com.rekixo.ludopaws.feature.startup

import org.junit.Assert.*
import org.junit.Test

class PlayGamesStartupStateTest {
    @Test fun unknownOrRestrictedPrivacyNeverStartsTheSdk() {
        val model = PlayGamesStartupStateMachine()
        model.refresh(false, true) { fail("SDK must not start") }
        assertEquals(PlayGamesStartupState.PRIVACY_REQUIRED, model.state)
        assertTrue(model.state.settled)
    }

    @Test fun unconfiguredPreviewNeverStartsTheSdk() {
        val model = PlayGamesStartupStateMachine()
        model.refresh(true, false) { fail("SDK must not start") }
        assertEquals(PlayGamesStartupState.NOT_CONFIGURED, model.state)
    }

    @Test fun authenticationSuccessIsRequiredForConnectedStatus() {
        val model = PlayGamesStartupStateMachine()
        lateinit var complete: (Boolean) -> Unit
        model.refresh(true, true) { complete = it }
        assertFalse(model.state.settled)
        complete(true)
        assertEquals(PlayGamesStartupState.CONNECTED, model.state)
    }

    @Test fun repeatedRefreshCoalescesAcrossActivityRecreation() {
        val model = PlayGamesStartupStateMachine()
        var attempts = 0
        repeat(5) { model.refresh(true, true) { attempts++ } }
        assertEquals(1, attempts)
    }

    @Test fun privacyChangeInvalidatesAnAlreadyRunningAuthentication() {
        val model = PlayGamesStartupStateMachine()
        lateinit var complete: (Boolean) -> Unit
        model.refresh(true, true) { complete = it }
        model.refresh(false, true) { fail("SDK must not start") }
        complete(true)
        assertEquals(PlayGamesStartupState.PRIVACY_REQUIRED, model.state)
    }

    @Test fun timeoutThenLateSuccessCannotOverrideTheNextAttempt() {
        val model = PlayGamesStartupStateMachine()
        lateinit var old: (Boolean) -> Unit
        lateinit var current: (Boolean) -> Unit
        model.refresh(true, true) { old = it }
        old(false) // Adapter's bounded timeout.
        model.refresh(true, true) { current = it }
        old(true)
        assertEquals(PlayGamesStartupState.CHECKING, model.state)
        current(true)
        assertEquals(PlayGamesStartupState.CONNECTED, model.state)
    }

    @Test fun offlineOrProviderFailureDoesNotHoldHome() {
        val model = PlayGamesStartupStateMachine()
        model.refresh(true, true) { it(false) }
        assertEquals(PlayGamesStartupState.UNAVAILABLE, model.state)
        assertTrue(StartupReadiness.canShowHome(true, model.state.settled, 0))
    }

    @Test fun evenAHungPlatformTaskCannotHoldHomeBeyondTheStartupBudget() {
        val model = PlayGamesStartupStateMachine()
        model.refresh(true, true) { }
        assertFalse(StartupReadiness.canShowHome(true, model.state.settled, 2_999))
        assertTrue(StartupReadiness.canShowHome(true, model.state.settled, 3_000))
    }

    @Test fun synchronousSdkExceptionIsRecoverable() {
        val model = PlayGamesStartupStateMachine()
        model.refresh(true, true) { throw IllegalStateException("Play services missing") }
        assertEquals(PlayGamesStartupState.UNAVAILABLE, model.state)
        model.refresh(true, true) { it(true) }
        assertEquals(PlayGamesStartupState.CONNECTED, model.state)
    }

    @Test fun detachedActivityDoesNotReceiveLatePlatformEvents() {
        val model = PlayGamesStartupStateMachine()
        var events = 0
        val observer: (PlayGamesStartupState) -> Unit = { events++ }
        model.subscribe(observer)
        lateinit var complete: (Boolean) -> Unit
        model.refresh(true, true) { complete = it }
        model.unsubscribe(observer)
        complete(true)
        assertEquals(2, events)
        assertEquals(PlayGamesStartupState.CONNECTED, model.state)
    }
}
