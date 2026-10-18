package com.rekixo.ludopaws.feature.online

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.concurrent.Executors
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.ui.online.*
import com.rekixo.ludopaws.feature.leaderboard.data.local.LeaderboardCredential
import com.rekixo.ludopaws.feature.leaderboard.data.local.LeaderboardIdentityStore
import com.rekixo.ludopaws.feature.leaderboard.data.remote.LeaderboardApi
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback

internal fun MainActivity.createMatch() {
    val displayName =
        runCatching {
            playerName()
        }.getOrElse {
            showStatus(
                it.message
                    ?: "Invalid player name",
            )
            return
        }
    runCatching {
        ProfileStore(
            this,
        ).updateDisplayName(
            displayName,
        )
    }
    val operationKey =
        "create:" + displayName
    val requestId =
        runCatching {
            pendingOperationStore
                .getOrCreate(
                    "create",
                    operationKey,
                )
        }.getOrElse {
            showStatus(
                it.message
                    ?: "Could not prepare match request",
            )
            return
        }

    runNetwork(
        action = {
            val credential =
                ensureLeaderboardCredential()
            api.createMatch(
                profileToken =
                    credential.profileToken,
                displayName =
                    displayName,
                clientRequestId =
                    requestId,
                rulesetKey = "CLASSIC_V3",
            )
        },
        onSuccess = {
            pendingOperationStore.clear(
                "create",
                operationKey,
            )
            captureSession(it)
            applyResponse(it)
        },
    )
}

internal fun MainActivity.joinMatch() {
    val displayName =
        runCatching {
            playerName()
        }.getOrElse {
            showStatus(
                it.message
                    ?: "Invalid player name",
            )
            return
        }

    runCatching {
        ProfileStore(
            this,
        ).updateDisplayName(
            displayName,
        )
    }
    val code =
        matchInput.text
            .toString()
            .trim()
            .uppercase()

    if (
        !Regex(
            "^LP[A-Z2-9]{8}$",
        ).matches(code)
    ) {
        showStatus(
            "Enter a valid Ludo Paws match code.",
        )
        return
    }

    val operationKey =
        "join:" +
            code +
            ":" +
            displayName
    val requestId =
        runCatching {
            pendingOperationStore
                .getOrCreate(
                    "join",
                    operationKey,
                )
        }.getOrElse {
            showStatus(
                it.message
                    ?: "Could not prepare join request",
            )
            return
        }

    runNetwork(
        action = {
            val credential =
                ensureLeaderboardCredential()
            api.joinMatch(
                matchId =
                    code,
                displayName =
                    displayName,
                clientRequestId =
                    requestId,
                profileToken =
                    credential.profileToken,
            )
        },
        onSuccess = {
            pendingOperationStore.clear(
                "join",
                operationKey,
            )
            captureSession(it)
            applyResponse(it)
        },
    )
}

internal fun MainActivity.refreshState(
    silent: Boolean = false,
) {
    withSession {
            code,
            token,
        ->
        runNetwork(
            action = {
                api.state(
                    code,
                    token,
                )
            },
            onSuccess = {
                applyResponse(
                    it,
                    announce = !silent,
                    silentValidationFailure = silent,
                )
            },
            showWorking = !silent,
        )
    }
}

internal fun MainActivity.rollVerifiedDice() {
    if (!canSubmitOnlineGameplayAction()) {
        showStatus("Showing the dice result or finishing the paw animation…")
        return
    }
    val state =
        currentState
            ?: run {
                showStatus("Sync the active match before rolling.")
                return
            }
    val decision = onlineRollActionDecision(state)
    if (!decision.enabled) {
        showStatus(
            when (decision.kind) {
                OnlineRollActionKind.WAIT_FOR_RECOVERY ->
                    "This committed roll is locked to its original secret • waiting for recovery or server timeout."
                OnlineRollActionKind.MOVE_REQUIRED ->
                    "Move a highlighted token before rolling again."
                else ->
                    if (isOnline) "Syncing the latest authoritative match state before rolling."
                    else "Offline — verified rolls resume when internet returns."
            },
        )
        if (isOnline && !hasAuthoritativeActionState()) {
            refreshState(silent = true)
        }
        updateControls(state)
        return
    }

    withSession {
            code,
            token,
        ->
        var secret =
            pendingSecret
                ?.takeIf {
                    it.matchId == code
                }

        if (decision.kind == OnlineRollActionKind.NEW_ROLL && secret == null) {
            // Keep a saved seed across uncertain network outcomes. The server's
            // native roll endpoint is idempotent for the same commitment, so a
            // retry can recover the exact result without drawing a replacement.
            val prepared = SeedCommitment.prepare()
            secret = PendingRollSecret(
                matchId = code,
                clientSeed = prepared.clientSeed,
                clientCommitment = prepared.clientCommitment,
            )
            pendingRollStore.save(requireNotNull(secret))
            pendingSecret = secret
        }

        val resumableSecret =
            secret
                ?: run {
                    showStatus("Roll recovery secret is unavailable • waiting for server recovery.")
                    currentState?.let(::updateControls)
                    return@withSession
                }

        onlineRollRequestInFlight = true
        val rollEventIndex = state.pendingRoll?.eventIndex ?: state.randomEventIndex
        diceView.startRolling()
        OnlineLudoPawsPresentation.noteLocalRollStarted(this, code, rollEventIndex)
        GameSoundFeedback.roll(this, rollEventIndex)
        verificationText.text = "Rolling with native LudoPaws dice…"

        runNetwork(
            action = {
                // Native rolls are atomic and idempotent: a retry with the same
                // local request secret returns the same stored result.
                val result = api.roll(
                    code,
                    token,
                    resumableSecret.clientSeed,
                    resumableSecret.clientCommitment,
                )
                pendingRollStore.clear()
                pendingSecret = null
                result
            },
            reconcileOnError = true,
            prioritizeGameplay = true,
            onComplete = { releaseOnlineGameplayRequest() },
        )
    }
}

internal fun MainActivity.moveToken(
    index: Int,
) {
    if (!canSubmitOnlineGameplayAction()) {
        showStatus("Wait for the current dice or paw animation to finish.")
        return
    }
    if (!hasAuthoritativeActionState()) {
        showStatus("Syncing the latest authoritative match state before moving.")
        if (isOnline) {
            refreshState(silent = true)
        }
        return
    }

    val state =
        currentState
            ?: run {
                showStatus("Sync the active match before moving.")
                return
            }
    val eventIndex =
        state.pendingRoll
            ?.eventIndex
            ?.takeIf {
                it >= 0
            }
            ?: run {
                showStatus(
                    "No verified roll is available for this move.",
                )
                return
            }

    withSession {
            code,
            token,
        ->
        onlineMoveRequestInFlight = true
        val previewRequestedAt = SystemClock.uptimeMillis()
        val previewStarted =
            OnlineLudoPawsPresentation.beginPendingMovePreview(
                activity = this,
                state = state,
                tokenIndex = index,
            )
        onlineMovePreviewStartedAtMillis =
            previewRequestedAt.takeIf { previewStarted }
        // The pawn begins a presentation-only preview immediately. The server
        // response remains the sole source of truth for its committed position.
        boardFrame.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        runNetwork(
            action = {
                api.move(
                    code,
                    token,
                    index,
                    eventIndex,
                )
            },
            reconcileOnError = true,
            prioritizeGameplay = true,
            onComplete = { releaseOnlineGameplayRequest() },
        )
    }
}

internal fun MainActivity.ensureLeaderboardCredential():
    LeaderboardCredential {
    val store =
        LeaderboardIdentityStore(
            this,
        )
    store.load()
        ?.let {
            return it
        }

    val requestId =
        store.registrationRequestId()
    val response =
        LeaderboardApi()
            .registerProfile(
                clientRequestId =
                    requestId,
            )
    val credential =
        LeaderboardCredential(
            profileId =
                response.getString(
                    "profileId",
                ),
            profileToken =
                response.getString(
                    "profileToken",
                ),
            registrationRequestId =
                requestId,
        )
    store.save(
        credential,
    )
    return credential
}

internal fun MainActivity.captureSession(
    response: JSONObject,
) {
    val code =
        response.getString(
            "matchId",
        )
    val token =
        response.getString(
            "playerToken",
        )
    val id =
        response.getString(
            "playerId",
        )

    if (
        matchId !=
        code
    ) {
        lastRealtimeRevision =
            -1
        uiStateHolder.update {
            it.copy(
                currentStateSource = null,
            )
        }
    }

    if (
        pendingSecret != null &&
        pendingSecret?.matchId != code
    ) {
        pendingRollStore.clear()
        pendingSecret = null
    }

    matchId = code
    playerToken = token
    playerId = id
    matchInput.setText(code)

    persistSessionSecurely(
        code = code,
        id = id,
        token = token,
    )
    connectRealtimeIfPossible()
}
