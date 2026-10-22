package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import org.junit.Assert.assertEquals
import org.junit.Test

class LudoPaws3DRenderCadencePolicyTest {
    @Test
    fun `idle board uses lower cost cadence`() {
        assertEquals(
            LudoPaws3DRenderCadencePolicy.IDLE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(
                state = LudoPaws3DSceneState(),
                nowMillis = 10_000L,
            ),
        )
    }

    @Test
    fun `forward movement stays at active cadence through home tail`() {
        val state =
            LudoPaws3DSceneState(
                forwardMotion =
                    LudoPawsPawnMotion(
                        playerId = "p1",
                        tokenIndex = 0,
                        fromPosition = 50,
                        toPosition = LudoPathEncoding.HOME_POSITION,
                        kind = LudoPawsPawnMotionKind.FORWARD,
                        visualSteps = 6,
                    ),
                forwardStartedAtMillis = 1_000L,
                forwardDurationMillis = 600L,
            )

        assertEquals(
            LudoPaws3DRenderCadencePolicy.ACTIVE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(
                state = state,
                nowMillis = 2_999L,
            ),
        )
        assertEquals(
            LudoPaws3DRenderCadencePolicy.IDLE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(
                state = state,
                nowMillis = 3_000L,
            ),
        )
    }

    @Test
    fun `ordinary move keeps display cadence through final facing and spring recovery`() {
        val state =
            LudoPaws3DSceneState(
                forwardMotion =
                    LudoPawsPawnMotion(
                        playerId = "p1",
                        tokenIndex = 1,
                        fromPosition = 12,
                        toPosition = 18,
                        kind = LudoPawsPawnMotionKind.FORWARD,
                        visualSteps = 6,
                    ),
                forwardStartedAtMillis = 2_000L,
                forwardDurationMillis = 600L,
            )

        assertEquals(
            LudoPaws3DRenderCadencePolicy.ACTIVE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(state, 2_599L),
        )
        assertEquals(
            LudoPaws3DRenderCadencePolicy.ACTIVE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(state, 2_600L),
        )
        assertEquals(
            LudoPaws3DRenderCadencePolicy.ACTIVE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(state, 2_849L),
        )
        assertEquals(
            LudoPaws3DRenderCadencePolicy.IDLE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(state, 2_850L),
        )
    }

    @Test
    fun `capture return keeps active cadence only while visible`() {
        val key = LudoPaws3DPawnKey("p2", 1)
        val state =
            LudoPaws3DSceneState(
                captureReturns =
                    mapOf(
                        key to
                            LudoPaws3DCaptureReturnState(
                                motion =
                                    LudoPawsPawnMotion(
                                        playerId = "p2",
                                        tokenIndex = 1,
                                        fromPosition = 20,
                                        toPosition = -1,
                                        kind = LudoPawsPawnMotionKind.CAPTURE_RETURN,
                                        visualSteps = 4,
                                    ),
                                startedAtMillis = 5_000L,
                                durationMillis = 800L,
                            ),
                    ),
            )

        assertEquals(
            LudoPaws3DRenderCadencePolicy.ACTIVE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(state, 5_799L),
        )
        assertEquals(
            LudoPaws3DRenderCadencePolicy.IDLE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(state, 5_800L),
        )
    }

    @Test
    fun `body reaction keeps active cadence until its exact expiry`() {
        val state =
            LudoPaws3DSceneState(
                activeReactions =
                    mapOf(
                        LudoPaws3DPawnKey("p3", 2) to
                            LudoPaws3DActiveReaction(
                                cue = AnimationCue.HAPPY,
                                startedAtMillis = 7_000L,
                                durationMillis = 680L,
                                priority = 10,
                            ),
                    ),
            )

        assertEquals(
            LudoPaws3DRenderCadencePolicy.ACTIVE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(state, 7_679L),
        )
        assertEquals(
            LudoPaws3DRenderCadencePolicy.IDLE_FRAME_DELAY_MILLIS,
            LudoPaws3DRenderCadencePolicy.frameDelayMillis(state, 7_680L),
        )
    }

    @Test
    fun `capture contact begins when attacker reaches the target before landing settle tail`() {
        assertEquals(
            510L,
            LudoPaws3DRenderCadencePolicy.captureContactDelayMillis(600L),
        )
        assertEquals(
            0L,
            LudoPaws3DRenderCadencePolicy.captureContactDelayMillis(90L),
        )
        assertEquals(
            0L,
            LudoPaws3DRenderCadencePolicy.captureContactDelayMillis(20L),
        )
    }

}
