package com.rekixo.ludopaws.feature.friends.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.rekixo.ludopaws.GameApi
import com.rekixo.ludopaws.GameApiException
import com.rekixo.ludopaws.feature.friends.data.local.FriendIdentityStore
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FriendPresenceController(
    context: Context,
) {
    private val appContext =
        context
            .applicationContext
    private val identityStore =
        FriendIdentityStore(
            appContext,
        )
    private val profileStore =
        ProfileStore(
            appContext,
        )
    private val api =
        GameApi()
    private val handler =
        Handler(
            Looper
                .getMainLooper(),
        )
    private val executor =
        Executors
            .newSingleThreadExecutor()
    private val heartbeatInFlight =
        AtomicBoolean(
            false,
        )

    @Volatile
    private var running =
        false

    private val heartbeatRunnable =
        object :
            Runnable {
            override fun run() {
                if (!running) {
                    return
                }

                val credential =
                    runCatching {
                        identityStore
                            .load()
                    }
                        .getOrNull()

                if (
                    credential !=
                        null &&
                    heartbeatInFlight
                        .compareAndSet(
                            false,
                            true,
                        )
                ) {
                    runCatching {
                        executor.execute {
                            try {
                                api.friendHeartbeat(
                                    friendToken =
                                        credential
                                            .friendToken,
                                    displayName =
                                        profileStore
                                            .snapshot()
                                            .displayName,
                                )
                            } catch (
                                error:
                                    GameApiException,
                            ) {
                                if (
                                    error.code ==
                                    "FRIEND_AUTH_INVALID"
                                ) {
                                    identityStore
                                        .clearCredentialIfMatches(credential)
                                }
                            } catch (
                                _: Exception,
                            ) {
                                // Presence is best-effort. The next heartbeat retries.
                            } finally {
                                heartbeatInFlight
                                    .set(
                                        false,
                                    )
                            }
                        }
                    }
                        .onFailure {
                            heartbeatInFlight
                                .set(
                                    false,
                                )
                        }
                }

                handler.postDelayed(
                    this,
                    HEARTBEAT_MS,
                )
            }
        }

    fun start() {
        if (running) {
            return
        }
        running =
            true
        handler.removeCallbacks(
            heartbeatRunnable,
        )
        handler.post(
            heartbeatRunnable,
        )
    }

    fun stop() {
        running =
            false
        handler.removeCallbacks(
            heartbeatRunnable,
        )
    }

    private companion object {
        const val HEARTBEAT_MS =
            30_000L
    }
}
