package com.rekixo.ludopaws.feature.startup

import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.*
import org.junit.Test

class StartupSessionCoordinatorTest {
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun run() { tasks.removeFirst().run() }
    }
    private class Fixture {
        val worker = Queue()
        val ui = Queue()
        var owner = "guest-a"
        var fail = false
        var loads = 0
        val coordinator = StartupSessionCoordinator(worker, { ui.execute(it) }, {
            loads++
            if (fail) throw IllegalStateException("network failure")
            StartupAccountSnapshot(StartupAccountState.GUEST_READY, "guest-a")
        }, { it.accountId == owner })
        fun finish() { worker.run(); ui.run() }
    }

    @Test fun pendingMatchOwnerMustBeSavedBeforeAutomaticAccountBootstrap() {
        assertFalse(StartupReadiness.canBootstrapAccount(true, false))
        assertFalse(StartupReadiness.canBootstrapAccount(false, true))
        assertTrue(StartupReadiness.canBootstrapAccount(true, true))
    }
    @Test fun completedLocalWorkAndValidAccountHaveNoArtificialMinimumDelay() {
        assertTrue(StartupReadiness.canShowHome(true, true, 0))
    }
    @Test fun accountCannotBypassRequiredLocalPreparation() {
        assertFalse(StartupReadiness.canShowHome(false, true, 10_000))
    }
    @Test fun slowAccountStopsHoldingHomeAtExactlyThreeSeconds() {
        assertFalse(StartupReadiness.canShowHome(true, false, 2_999))
        assertTrue(StartupReadiness.canShowHome(true, false, 3_000))
    }
    @Test fun requiredLocalPreparationHasItsOwnWatchdog() {
        assertFalse(StartupReadiness.localTimedOut(false, 4_999))
        assertTrue(StartupReadiness.localTimedOut(false, 5_000))
        assertFalse(StartupReadiness.localTimedOut(true, 9_000))
    }
    @Test fun repeatedRefreshAndNewObserverShareTheInFlightRequest() {
        val f = Fixture()
        f.coordinator.refresh(true)
        f.coordinator.subscribe { }
        f.coordinator.refresh(true)
        assertEquals(1, f.worker.tasks.size)
        f.finish()
        assertEquals(1, f.loads)
    }
    @Test fun workerResultOnlyPublishesThroughUiDispatch() {
        val f = Fixture(); f.coordinator.refresh(true); f.worker.run()
        assertEquals(StartupAccountState.CHECKING, f.coordinator.snapshot.state)
        f.ui.run()
        assertEquals(StartupAccountState.GUEST_READY, f.coordinator.snapshot.state)
    }
    @Test fun offlineStartupDoesNotCreateOrRefreshAServerAccount() {
        val f = Fixture(); f.coordinator.refresh(false)
        assertTrue(f.worker.tasks.isEmpty())
        assertEquals(StartupAccountState.OFFLINE, f.coordinator.snapshot.state)
    }
    @Test fun lateSuccessDoesNotEraseKnownOfflineStatus() {
        val f = Fixture(); f.coordinator.refresh(true); f.coordinator.refresh(false); f.finish()
        assertEquals(StartupAccountState.OFFLINE, f.coordinator.snapshot.state)
    }
    @Test fun networkReturningDuringOneRequestDoesNotCreateAnother() {
        val f = Fixture(); f.coordinator.refresh(true); f.coordinator.refresh(false); f.coordinator.refresh(true)
        assertEquals(1, f.worker.tasks.size)
        assertEquals(StartupAccountState.CHECKING, f.coordinator.snapshot.state)
        f.finish()
        assertEquals(StartupAccountState.GUEST_READY, f.coordinator.snapshot.state)
    }
    @Test fun retryAfterNetworkFailureReleasesInFlightState() {
        val f = Fixture(); f.fail = true; f.coordinator.refresh(true); f.finish()
        assertEquals(StartupAccountState.RETRY_REQUIRED, f.coordinator.snapshot.state)
        f.fail = false; f.coordinator.refresh(true); f.finish()
        assertEquals(StartupAccountState.GUEST_READY, f.coordinator.snapshot.state)
    }
    @Test fun switchedAccountCannotReceiveThePreviousReadyLabel() {
        val f = Fixture(); f.coordinator.refresh(true); f.worker.run(); f.owner = "saved-b"; f.ui.run()
        assertEquals(StartupAccountState.RETRY_REQUIRED, f.coordinator.snapshot.state)
        assertNull(f.coordinator.snapshot.accountId)
    }
    @Test fun loggedOutAccountCannotReceiveThePreviousReadyLabel() {
        val f = Fixture(); f.coordinator.refresh(true); f.worker.run(); f.owner = ""; f.ui.run()
        assertEquals(StartupAccountState.RETRY_REQUIRED, f.coordinator.snapshot.state)
    }
    @Test fun stoppedObserverDoesNotReceiveLateCompletion() {
        val f = Fixture(); val updates = mutableListOf<StartupAccountState>()
        val listener: (StartupAccountSnapshot) -> Unit = { updates += it.state }
        f.coordinator.subscribe(listener); f.coordinator.refresh(true)
        f.coordinator.unsubscribe(listener)
        val count = updates.size; f.finish()
        assertEquals(count, updates.size)
        f.coordinator.subscribe(listener)
        assertEquals(StartupAccountState.GUEST_READY, updates.last())
        assertEquals(1, f.loads)
    }
    @Test fun restoreRequiredIsNotReportedAsGuestReady() {
        val worker = Queue(); val ui = Queue()
        val c = StartupSessionCoordinator(worker, { ui.execute(it) }, {
            StartupAccountSnapshot(StartupAccountState.RESTORE_REQUIRED)
        }, { true })
        c.refresh(true); worker.run(); ui.run()
        assertEquals(StartupAccountState.RESTORE_REQUIRED, c.snapshot.state)
        assertNull(c.snapshot.accountId)
    }
    @Test fun validationFailureIsRecoverableRatherThanCrashingUi() {
        val worker = Queue(); val ui = Queue()
        val c = StartupSessionCoordinator(worker, { ui.execute(it) }, {
            StartupAccountSnapshot(StartupAccountState.GUEST_READY, "old")
        }, { throw IllegalStateException("storage unavailable") })
        c.refresh(true); worker.run(); ui.run()
        assertEquals(StartupAccountState.RETRY_REQUIRED, c.snapshot.state)
    }
    @Test fun unavailableWorkerShowsRetryAndDoesNotStrandChecking() {
        val c = StartupSessionCoordinator(Executor { throw RejectedExecutionException() }, { it() }, {
            error("must not run")
        }, { true })
        c.refresh(true)
        assertEquals(StartupAccountState.RETRY_REQUIRED, c.snapshot.state)
    }
}
