package com.rekixo.ludopaws

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import android.view.View
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReaction
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max

internal data class LudoPaws3DPawnKey(
    val playerId: String,
    val tokenIndex: Int,
)

internal data class LudoPaws3DCaptureReturnState(
    val motion: LudoPawsPawnMotion,
    val startedAtMillis: Long,
    val durationMillis: Long,
)

internal data class LudoPaws3DSceneState(
    val snapshot: MatchSnapshot? = null,
    val stoneRoadOverlayEnabled: Boolean = true,
    val classicRoadReliefEnabled: Boolean = false,
    val monsoonEffectsEnabled: Boolean = false,
    val monsoonAnimationEnabled: Boolean = true,
    val localPlayerId: String? = null,
    val perspectiveColor: String? = null,
    val characterIdsBySeat: List<String> = emptyList(),
    val forwardMotion: LudoPawsPawnMotion? = null,
    val forwardStartedAtMillis: Long = 0L,
    val forwardDurationMillis: Long = 0L,
    val captureReturns: Map<LudoPaws3DPawnKey, LudoPaws3DCaptureReturnState> = emptyMap(),
    val pendingPreviewEventIndex: Int? = null,
    val activeReactions: Map<LudoPaws3DPawnKey, LudoPaws3DActiveReaction> = emptyMap(),
    val homeEntries: Map<String, LudoPawsHomeEntry> = emptyMap(),
    val reducedMotion: Boolean = false,
)

/**
 * One production OpenGL surface for every visible Ludo pawn.
 *
 * TextureView keeps a single GL context and participates in Android compositing.
 * The authoritative board remains underneath for geometry/touch/game state.
 * This layer is presentation-only and never mutates gameplay or proof state.
 */
internal class LudoPaws3DSceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {
    private val settingsStore = GameSettingsStore(context)
    private var authoritativeSnapshot: MatchSnapshot? = null
    private var movementAudioMotionKey: String? = null
    private var movementAudioNextHop: Int = 1
    private var movementAudioNextStep: Int = 1
    private val emittedCaptureAudioKeys = LinkedHashSet<String>()

    @Volatile
    private var sceneState = LudoPaws3DSceneState()
    private var previewBoardThemeId: String? = null

    @Volatile
    private var renderLoop: RenderLoop? = null
    private val retiringLoops = mutableMapOf<SurfaceTexture, RenderLoop>()

    @Volatile
    private var operational = false

    // Only the display callback runs on the UI thread. All GL work stays on LudoPaws3D.
    private val displayFrames by lazy { Choreographer.getInstance() }
    private val displayCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val loop = renderLoop ?: return
            if (!isShown || visibility != VISIBLE || windowVisibility != VISIBLE) return
            val refreshHz = display?.refreshRate?.takeIf { it > 0f } ?: 60f
            loop.onDisplayFrame(frameTimeNanos, (1_000_000_000.0 / refreshHz).toLong())
            displayFrames.postFrameCallback(this)
        }
    }

    var onOperationalChanged: ((Boolean) -> Unit)? = null
    internal var onStartupFailure: (() -> Unit)? = null

    init {
        isOpaque = false
        isClickable = false
        isFocusable = false

        // Prewarm every SFX that can occur during pawn travel so the render-time
        // audio clock does not incur a first-play SoundPool load delay.
        GameSoundFeedback.preloadMovementEventSfx(context)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
    }

    fun pendingMovePreviewElapsedMillis(eventIndex: Int? = null): Long? {
        val current = sceneState
        val pendingEventIndex = current.pendingPreviewEventIndex ?: return null
        if (eventIndex != null && eventIndex != pendingEventIndex) return null
        return (SystemClock.uptimeMillis() - current.forwardStartedAtMillis).coerceAtLeast(0L)
    }

    fun acceptedMovePreviewElapsedMillis(authoritative: MatchSnapshot?): Long? {
        val state = authoritative ?: return null
        val current = sceneState
        val eventIndex = current.pendingPreviewEventIndex ?: return null
        val motion = current.forwardMotion ?: return null
        if (state.matchId != current.snapshot?.matchId) return null
        val player = state.players.firstOrNull { it.playerId == motion.playerId } ?: return null
        if (player.tokens.getOrNull(motion.tokenIndex) != motion.toPosition) return null
        val committed = state.history.any { event ->
            event.eventIndex == eventIndex &&
                event.playerId == motion.playerId &&
                event.moveTokenIndex == motion.tokenIndex
        }
        if (!committed) return null
        return (SystemClock.uptimeMillis() - current.forwardStartedAtMillis).coerceAtLeast(0L)
    }

    fun beginPendingMovePreview(
        state: MatchSnapshot,
        playerId: String,
        tokenIndex: Int,
    ): Boolean {
        val pending = state.pendingRoll ?: return false
        if (
            state.status != "ACTIVE" ||
            pending.status != "RESOLVED" ||
            pending.eventIndex < 0 ||
            pending.seat !in state.players.indices ||
            state.players[pending.seat].playerId != playerId ||
            tokenIndex !in pending.legalTokenIndexes
        ) {
            return false
        }
        val player = state.players.firstOrNull { it.playerId == playerId } ?: return false
        val from = player.tokens.getOrNull(tokenIndex) ?: return false
        val roll = pending.outcome ?: return false
        val to = LudoPathEncoding.destinationForRoll(from, roll) ?: return false
        val motion = LudoPawsPawnAnimationPolicy.transition(
            playerId = playerId,
            tokenIndex = tokenIndex,
            from = from,
            to = to,
        ) ?: return false
        if (motion.kind != LudoPawsPawnMotionKind.FORWARD) return false
        val current = sceneState
        if (current.pendingPreviewEventIndex != null) return false

        val now = SystemClock.uptimeMillis()
        val speed = settingsStore.snapshot().gameSpeed
        val previewPlayers = state.players.map { candidate ->
            if (candidate.playerId != playerId) {
                candidate
            } else {
                candidate.copy(
                    tokens = candidate.tokens.toMutableList().also { tokens ->
                        tokens[tokenIndex] = to
                    },
                )
            }
        }
        val previewSnapshot = state.copy(players = previewPlayers)
        movementAudioMotionKey = motionKey(state.matchId, motion)
        movementAudioNextHop = 1
        movementAudioNextStep = 1
        // Publish sceneState last: the render thread observes this volatile write
        // together with the movement's matching audio cursor/key.
        sceneState = current.copy(
            snapshot = previewSnapshot,
            forwardMotion = motion,
            forwardStartedAtMillis = now,
            forwardDurationMillis = LudoPawsGameplayPacingPolicy
                .forwardAnimationDurationMillis(speed, motion.visualSteps),
            pendingPreviewEventIndex = pending.eventIndex,
        )
        return true
    }

    fun cancelPendingMovePreview(authoritative: MatchSnapshot?) {
        val current = sceneState
        if (current.pendingPreviewEventIndex == null) return
        authoritativeSnapshot = authoritative
        movementAudioMotionKey = null
        movementAudioNextHop = 1
        movementAudioNextStep = 1
        // Publish the cleared motion after resetting its associated audio cursor.
        sceneState = current.copy(
            snapshot = authoritative,
            forwardMotion = null,
            forwardStartedAtMillis = 0L,
            forwardDurationMillis = 0L,
            captureReturns = emptyMap(),
            pendingPreviewEventIndex = null,
        )
    }

    fun bind(
        state: MatchSnapshot?,
        playerId: String?,
        perspectiveColor: String?,
        characterIdsBySeat: List<String> = emptyList(),
    ) {
        val now = SystemClock.uptimeMillis()
        val previousState = sceneState
        val previousSnapshot = authoritativeSnapshot
        if (state?.status == "FINISHED" && previousSnapshot?.status == "ACTIVE" && previousSnapshot.matchId == state.matchId) {
            com.rekixo.ludopaws.feature.store.data.ads.AdBreakSession.completed(state.matchId)
        }
        val previewEventIndex = previousState.pendingPreviewEventIndex
        val previewMotion = previousState.forwardMotion.takeIf { previewEventIndex != null }
        val sameMatch =
            previousSnapshot != null &&
                state != null &&
                previousSnapshot.matchId == state.matchId
        val previewPlayerId = previewMotion?.playerId
        val previewPlayer = previewPlayerId?.let { id ->
            state?.players?.firstOrNull { it.playerId == id }
        }
        val previewPosition = previewMotion?.let { motion ->
            previewPlayer?.tokens?.getOrNull(motion.tokenIndex)
        }
        val previewAccepted =
            previewMotion != null &&
                sameMatch &&
                previewPosition == previewMotion.toPosition &&
                state?.history?.any { event ->
                    event.eventIndex == previewEventIndex &&
                        event.playerId == previewMotion.playerId &&
                        event.moveTokenIndex == previewMotion.tokenIndex
                } == true
        val previewStillPending =
            previewMotion != null &&
                !previewAccepted &&
                sameMatch &&
                state != null &&
                state.status == "ACTIVE" &&
                state.pendingRoll?.status == "RESOLVED" &&
                state.pendingRoll?.eventIndex == previewEventIndex &&
                previewPosition == previewMotion.fromPosition

        if (previewStillPending && state != null && previewMotion != null) {
            authoritativeSnapshot = state
            val displaySnapshot = state.copy(
                players = state.players.map { candidate ->
                    if (candidate.playerId != previewMotion.playerId) {
                        candidate
                    } else {
                        candidate.copy(
                            tokens = candidate.tokens.toMutableList().also { tokens ->
                                tokens[previewMotion.tokenIndex] = previewMotion.toPosition
                            },
                        )
                    }
                },
            )
            sceneState = previousState.copy(
                snapshot = displaySnapshot,
                localPlayerId = playerId,
                perspectiveColor = perspectiveColor?.takeIf {
                    it in OfflinePlayerLayout.COLORS
                },
                characterIdsBySeat = characterIdsBySeat.take(4),
            )
            return
        }

        if (!sameMatch || state == null) {
            emittedCaptureAudioKeys.clear()
        }
        val settings = settingsStore.snapshot()
        val motions =
            LudoPawsPawnAnimationPolicy.plans(
                previous = previousSnapshot,
                current = state,
            )
        val detectedForward =
            motions.firstOrNull {
                it.kind == LudoPawsPawnMotionKind.FORWARD
            }
        val acceptedPreviewMotion = if (
            previewAccepted &&
            previewMotion != null &&
            detectedForward != null &&
            detectedForward.playerId == previewMotion.playerId &&
            detectedForward.tokenIndex == previewMotion.tokenIndex &&
            detectedForward.fromPosition == previewMotion.fromPosition &&
            detectedForward.toPosition == previewMotion.toPosition
        ) {
            previewMotion
        } else {
            null
        }
        val newForward = acceptedPreviewMotion ?: detectedForward

        if (newForward != null && acceptedPreviewMotion == null) {
            movementAudioMotionKey = state?.let { motionKey(it.matchId, newForward) }
            movementAudioNextHop = 1
            movementAudioNextStep = 1
        }

        val retainedForward =
            if (
                newForward == null &&
                previousState.pendingPreviewEventIndex == null &&
                sameMatch &&
                previousState.forwardMotion != null &&
                now <
                previousState.forwardStartedAtMillis +
                    previousState.forwardDurationMillis +
                    LudoPaws3DRenderCadencePolicy.HOME_CELEBRATION_TAIL_MILLIS
            ) {
                previousState.forwardMotion
            } else {
                null
            }
        val forward = newForward ?: retainedForward
        val forwardStartedAtMillis =
            if (acceptedPreviewMotion != null) {
                previousState.forwardStartedAtMillis
            } else if (newForward != null) {
                now
            } else if (forward != null) {
                previousState.forwardStartedAtMillis
            } else {
                0L
            }
        val forwardDurationMillis =
            if (acceptedPreviewMotion != null) {
                previousState.forwardDurationMillis
            } else if (newForward != null) {
                LudoPawsGameplayPacingPolicy
                    .forwardAnimationDurationMillis(
                        speed = settings.gameSpeed,
                        visualSteps = newForward.visualSteps,
                    )
            } else if (forward != null) {
                previousState.forwardDurationMillis
            } else {
                0L
            }

        val retainedCaptureReturns =
            if (sameMatch) {
                previousState.captureReturns.filterValues { capture ->
                    now < capture.startedAtMillis + capture.durationMillis
                }
            } else {
                emptyMap()
            }
        val newCaptureReturns =
            motions
                .asSequence()
                .filter {
                    it.kind == LudoPawsPawnMotionKind.CAPTURE_RETURN
                }
                .associate { motion ->
                    // Keep a captured pawn on its square until the attacker's
                    // presentation timeline reaches contact, including optimistic previews.
                    val animationStart = if (acceptedPreviewMotion != null) {
                        previousState.forwardStartedAtMillis
                    } else {
                        now
                    }
                    val contactAtMillis =
                        animationStart +
                            if (newForward != null) {
                                LudoPaws3DRenderCadencePolicy
                                    .captureContactDelayMillis(forwardDurationMillis)
                            } else {
                                0L
                            }
                    LudoPaws3DPawnKey(
                        playerId = motion.playerId,
                        tokenIndex = motion.tokenIndex,
                    ) to
                        LudoPaws3DCaptureReturnState(
                            motion = motion,
                            startedAtMillis = contactAtMillis,
                            durationMillis =
                                LudoPawsGameplayPacingPolicy
                                    .captureReturnDurationMillis(settings.gameSpeed),
                        )
                }
        val retainedReactions =
            if (sameMatch) {
                previousState.activeReactions.filterValues { reaction ->
                    now < reaction.startedAtMillis + reaction.durationMillis
                }
            } else {
                emptyMap()
            }

        val homeEntries = if (sameMatch) previousState.homeEntries.toMutableMap()
            else mutableMapOf()
        state?.let { current ->
            current.players.filter(LudoPawsHomeEntryPolicy::complete).forEach { player ->
                val existing = homeEntries[player.playerId]
                val knownRank = if (current.matchMode == "TEAM_UP") {
                    if (current.winnerTeamId != null && current.winnerTeamId == player.teamId) 1 else 0
                } else LudoPawsFinishPresentation.ranks(current)[player.playerId] ?: 0
                if (existing != null) {
                    if (knownRank > 0 && existing.rank != knownRank)
                        homeEntries[player.playerId] = existing.copy(rank = knownRank)
                } else {
                    val before = previousSnapshot?.takeIf { it.matchId == current.matchId }
                        ?.players?.firstOrNull { it.playerId == player.playerId }
                    val liveArrival = before != null && !LudoPawsHomeEntryPolicy.complete(before)
                    val arrivalAt = if (forward?.playerId == player.playerId)
                        maxOf(now, forwardStartedAtMillis + forwardDurationMillis) else now
                    homeEntries[player.playerId] = LudoPawsHomeEntry(
                        if (liveArrival) arrivalAt else 0L,
                        if (current.matchMode == "TEAM_UP" && current.winnerTeamId == null) 0
                        else knownRank,
                    )
                }
            }
        }

        authoritativeSnapshot = state
        sceneState =
            LudoPaws3DSceneState(
                snapshot = state,
                reducedMotion = settings.reducedMotion,
                stoneRoadOverlayEnabled = previousState.stoneRoadOverlayEnabled,
                classicRoadReliefEnabled = previousState.classicRoadReliefEnabled,
                monsoonEffectsEnabled = previousState.monsoonEffectsEnabled,
                monsoonAnimationEnabled = previousState.monsoonAnimationEnabled,
                localPlayerId = playerId,
                perspectiveColor =
                    perspectiveColor?.takeIf {
                        it in OfflinePlayerLayout.COLORS
                    },
                characterIdsBySeat = characterIdsBySeat.take(4),
                forwardMotion = forward,
                forwardStartedAtMillis = forwardStartedAtMillis,
                forwardDurationMillis = forwardDurationMillis,
                captureReturns = retainedCaptureReturns + newCaptureReturns,
                activeReactions = retainedReactions,
                homeEntries = homeEntries,
            )
    }

    /**
     * Adds short, pawn-local body language after the snapshot has been bound.
     * Higher-priority reactions replace lower-priority animation on the same
     * pawn; gameplay movement remains independent and still has precedence for
     * board position.
     */
    fun playReactions(
        reactions: List<LudoPawsReaction>,
    ) {
        if (reactions.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val current = sceneState
        val snapshot = current.snapshot ?: return

        val next =
            current.activeReactions
                .filterValues {
                    now < it.startedAtMillis + it.durationMillis
                }
                .toMutableMap()

        reactions
            .asSequence()
            .sortedByDescending(LudoPawsReaction::priority)
            .forEach { reaction ->
                val player =
                    snapshot.players.firstOrNull {
                        it.playerId == reaction.playerId
                    } ?: return@forEach
                val characterId = LudoPaws3DCharacterPolicy.characterIdForSeat(
                    current.characterIdsBySeat, player.seat, player.color,
                )
                val targets =
                    targetTokens(
                        player = player,
                        reaction = reaction,
                    )
                targets.forEach { tokenIndex ->
                    val variation = LudoPawsPawnReactionVariation.choose(
                        reaction.animationCue, tokenIndex,
                        reaction.reactionKey.hashCode() xor reaction.eventIndex,
                        group = targets.size > 1,
                    )
                    val duration = (LudoPaws3DReactionMotion.durationMillis(
                        characterId, variation.cue,
                    ) * variation.durationScale).toLong().coerceAtLeast(1L)
                    val key =
                        LudoPaws3DPawnKey(
                            playerId = player.playerId,
                            tokenIndex = tokenIndex,
                        )
                    val previous = next[key]
                    if (
                        previous == null ||
                        reaction.priority >= previous.priority
                    ) {
                        val captureStart =
                            if (reaction.animationCue == AnimationCue.CAPTURED) {
                                current.captureReturns[key]?.startedAtMillis
                            } else {
                                null
                            }
                        val attackerContact =
                            if (
                                reaction.animationCue == AnimationCue.CAPTURE &&
                                current.forwardMotion?.playerId == key.playerId &&
                                current.forwardMotion?.tokenIndex == key.tokenIndex
                            ) {
                                current.forwardStartedAtMillis +
                                    LudoPaws3DRenderCadencePolicy
                                        .captureContactDelayMillis(
                                            current.forwardDurationMillis,
                                        )
                            } else {
                                null
                            }
                        next[key] =
                            LudoPaws3DActiveReaction(
                                cue = variation.cue,
                                startedAtMillis =
                                    captureStart
                                        ?: attackerContact
                                        ?: (now + variation.delayMillis),
                                durationMillis = duration,
                                priority = reaction.priority,
                                variant = variation.variant,
                                moment = reaction.momentType,
                            )
                    }
                }
            }

        // One quiet bystander, never the actor/victim and never an extra voice.
        val principal = reactions.firstOrNull { it.momentType in setOf(
            com.rekixo.ludopaws.feature.characters.domain.reaction.GameMomentType.CAPTURE_MADE,
            com.rekixo.ludopaws.feature.characters.domain.reaction.GameMomentType.HOME_REACHED,
            com.rekixo.ludopaws.feature.characters.domain.reaction.GameMomentType.SAFE_REACHED,
        ) }
        if (principal != null && !settingsStore.snapshot().reducedMotion) {
            val actors = reactions.map { it.playerId }.toSet()
            val bystander = snapshot.players.firstOrNull { it.playerId !in actors && it.tokens.any { p -> p != 57 } }
            bystander?.let { player ->
                val token = player.tokens.indexOfFirst { it != 57 }
                val key = LudoPaws3DPawnKey(player.playerId, token)
                if (next[key] == null && current.forwardMotion?.let { it.playerId == key.playerId && it.tokenIndex == key.tokenIndex } != true) {
                    val contact = next.entries.firstOrNull { it.key.playerId == principal.playerId }?.value?.startedAtMillis ?: now
                    next[key] = LudoPaws3DActiveReaction(AnimationCue.IDLE, maxOf(now, contact) + 140L, 750L, 1, observer = true)
                }
            }
        }
        sceneState = current.copy(activeReactions = next)
    }

    private fun targetTokens(
        player: PlayerSnapshot,
        reaction: LudoPawsReaction,
    ): List<Int> {
        reaction.tokenIndex
            ?.takeIf { it in player.tokens.indices }
            ?.let { return listOf(it) }

        if (
            reaction.animationCue == AnimationCue.VICTORY ||
            reaction.animationCue == AnimationCue.DEFEAT
        ) {
            return player.tokens.indices.toList()
        }

        val active =
            player.tokens.indices.firstOrNull { index ->
                player.tokens[index] in 0..56
            }
        val yard =
            player.tokens.indices.firstOrNull { index ->
                player.tokens[index] == -1
            }
        return listOf(active ?: yard ?: 0)
    }

    override fun onSurfaceTextureAvailable(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        startRenderLoop(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        renderLoop?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(
        surface: SurfaceTexture,
    ): Boolean {
        stopRenderLoop()
        val owner = retiringLoops.remove(surface) ?: return true
        owner.releaseSurfaceWhenStopped()
        // EGL owns the producer until its worker has finished. Never wait on UI.
        return false
    }

    override fun onSurfaceTextureUpdated(
        surface: SurfaceTexture,
    ) = Unit

    override fun onVisibilityChanged(
        changedView: View,
        visibility: Int,
    ) {
        super.onVisibilityChanged(changedView, visibility)
        updateRenderVisibility()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateRenderVisibility()
    }

    internal fun reloadStyle() {
        val themeId = previewBoardThemeId
            ?: com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore(context)
                .selectedId(com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.BOARD)
        val definition = com.rekixo.ludopaws.feature.boards.domain.BoardRegistry.resolve(themeId)
        sceneState = sceneState.copy(
            stoneRoadOverlayEnabled = definition.stoneRoadOverlayEnabled,
            classicRoadReliefEnabled = definition.classicRoadReliefEnabled,
            monsoonEffectsEnabled = definition.monsoonEffectsEnabled,
            monsoonAnimationEnabled = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
                android.animation.ValueAnimator.areAnimatorsEnabled(),
        )
    }

    internal fun setBoardThemeOverride(themeId: String) {
        previewBoardThemeId = themeId
        reloadStyle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reloadStyle()
    }

    override fun onDetachedFromWindow() {
        stopRenderLoop()
        sceneState = LudoPaws3DSceneState()
        authoritativeSnapshot = null
        super.onDetachedFromWindow()
    }

    private fun updateRenderVisibility() {
        val loop = renderLoop ?: return
        val visible = isShown && visibility == VISIBLE && windowVisibility == VISIBLE
        loop.setPresentationVisible(visible)
        displayFrames.removeFrameCallback(displayCallback)
        if (visible && renderLoop != null) displayFrames.postFrameCallback(displayCallback)
    }

    private fun startRenderLoop(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        stopRenderLoop()
        RenderLoop(
            surfaceTexture = surface,
            initialWidth = width,
            initialHeight = height,
            initialPresentationVisible =
                isShown &&
                    visibility == VISIBLE &&
                    windowVisibility == VISIBLE,
        ).also {
            renderLoop = it
            it.start()
        }
        updateRenderVisibility()
    }

    private fun stopRenderLoop() {
        displayFrames.removeFrameCallback(displayCallback)
        val loop =
            renderLoop
                ?: run {
                    setOperational(false)
                    return
                }
        renderLoop = null
        retiringLoops[loop.surfaceTexture] = loop
        loop.shutdown()
        setOperational(false)
    }

    private fun motionKey(matchId: String, motion: LudoPawsPawnMotion): String =
        "${matchId}:${motion.playerId}:${motion.tokenIndex}:${motion.fromPosition}:${motion.toPosition}"

    private fun setOperational(value: Boolean) {
        if (operational == value) return
        operational = value
        post {
            onOperationalChanged?.invoke(value)
        }
    }

    private inner class RenderLoop(
        val surfaceTexture: SurfaceTexture,
        initialWidth: Int,
        initialHeight: Int,
        initialPresentationVisible: Boolean,
    ) : Thread("LudoPaws3D") {
        private val surfaceLifetimeLock = Any()
        private var terminated = false
        private var releaseSurface = false

        fun releaseSurfaceWhenStopped() = synchronized(surfaceLifetimeLock) {
            if (terminated) surfaceTexture.release() else releaseSurface = true
        }

        private val visibilityLock = ReentrantLock()
        private val visibilityChanged = visibilityLock.newCondition()
        private val displayPacer = LudoPaws3DDisplayPacer()
        private var pendingFrameNanos = 0L

        @Volatile
        private var running = true

        @Volatile
        private var targetWidth = initialWidth

        @Volatile
        private var targetHeight = initialHeight

        @Volatile
        private var presentationVisible = initialPresentationVisible

        fun resize(
            width: Int,
            height: Int,
        ) {
            targetWidth = width
            targetHeight = height
        }

        fun setPresentationVisible(visible: Boolean) {
            if (presentationVisible == visible) return
            visibilityLock.withLock {
                presentationVisible = visible
                pendingFrameNanos = 0L
                displayPacer.reset()
                visibilityChanged.signalAll()
            }
        }

        fun onDisplayFrame(frameTimeNanos: Long, refreshPeriodNanos: Long) {
            visibilityLock.withLock {
                if (!running || !presentationVisible) return
                val delay = LudoPaws3DRenderCadencePolicy.frameDelayMillis(
                    sceneState, frameTimeNanos / 1_000_000L,
                )
                if (displayPacer.shouldRender(frameTimeNanos, refreshPeriodNanos, delay)) {
                    pendingFrameNanos = frameTimeNanos
                    visibilityChanged.signalAll()
                }
            }
        }

        fun shutdown() {
            running = false
            visibilityLock.withLock {
                visibilityChanged.signalAll()
            }
            interrupt()
        }

        private fun emitMovementStepSounds(
            state: LudoPaws3DSceneState,
            nowMillis: Long,
        ) {
            val forward = state.forwardMotion
            val travelDuration =
                state.forwardDurationMillis -
                    LudoPaws3DRenderCadencePolicy.FORWARD_LANDING_SETTLE_MILLIS
            if (
                forward == null ||
                travelDuration <= 0L ||
                forward.visualSteps <= 0
            ) {
                emitCaptureImpactSounds(state, nowMillis)
                return
            }

            val motionKey =
                "${state.snapshot?.matchId}:${forward.playerId}:${forward.tokenIndex}:${forward.fromPosition}:${forward.toPosition}"
            if (motionKey != movementAudioMotionKey) {
                movementAudioMotionKey = motionKey
                movementAudioNextHop = 1
                movementAudioNextStep = 1
            }

            val stepDuration =
                LudoPawsGameplayPacingPolicy.movementAudioStepDurationMillis(
                    forwardDurationMillis = state.forwardDurationMillis,
                    visualSteps = forward.visualSteps,
                )
            if (stepDuration <= 0L) {
                emitCaptureImpactSounds(state, nowMillis)
                return
            }

            val elapsed =
                (nowMillis - state.forwardStartedAtMillis)
                    .coerceAtLeast(0L)
            val movingPlayer =
                state.snapshot
                    ?.players
                    ?.firstOrNull {
                        it.playerId == forward.playerId
                    }
            val playerColor = movingPlayer?.color
            val characterId =
                movingPlayer
                    ?.seat
                    ?.let { state.characterIdsBySeat.getOrNull(it) }

            // Audio follows the movement clock, but a delayed render frame must not
            // silently discard the current hop cue. Coalesce missed hops into one cue.
            val dueHop =
                ((elapsed / stepDuration).toInt() + 1)
                    .coerceIn(1, forward.visualSteps)
            if (movementAudioNextHop <= dueHop) {
                if (
                    elapsed < travelDuration &&
                    !(forward.fromPosition == -1 && dueHop == 1)
                ) {
                    GameSoundFeedback.jump(context)
                }
                movementAudioNextHop = dueHop + 1
            }

            val dueStep =
                (elapsed / stepDuration)
                    .toInt()
                    .coerceIn(0, forward.visualSteps)
            if (dueStep >= movementAudioNextStep) {
                val firstPendingStep = movementAudioNextStep
                var latestLandmark: Pair<Int, LudoPawsMovementSoundCue>? = null
                for (step in firstPendingStep..dueStep) {
                    val cues =
                        LudoPawsMovementSoundPolicy.cuesForVisualStep(
                            color = playerColor,
                            fromPosition = forward.fromPosition,
                            step = step,
                        )
                    cues.forEach { cue ->
                        when (cue) {
                            LudoPawsMovementSoundCue.STEP -> {
                                if (step == dueStep) {
                                    GameSoundFeedback.moveStep(
                                        context = context,
                                        characterId = characterId,
                                    )
                                }
                            }
                            LudoPawsMovementSoundCue.YARD_EXIT,
                            LudoPawsMovementSoundCue.SAFE_RELIEF,
                            LudoPawsMovementSoundCue.HOME_LANE,
                            LudoPawsMovementSoundCue.HOME,
                            -> {
                                // Preserve a meaningful landmark across a frame hitch,
                                // but play only the newest one instead of bursting old cues.
                                if (latestLandmark == null || step >= latestLandmark.first) {
                                    latestLandmark = step to cue
                                }
                            }
                        }
                    }
                }
                latestLandmark?.second?.let { cue ->
                    when (cue) {
                        LudoPawsMovementSoundCue.YARD_EXIT -> GameSoundFeedback.yardExit(context)
                        LudoPawsMovementSoundCue.SAFE_RELIEF -> GameSoundFeedback.safe(context)
                        LudoPawsMovementSoundCue.HOME_LANE -> GameSoundFeedback.homeLane(context)
                        LudoPawsMovementSoundCue.HOME -> GameSoundFeedback.home(context)
                        LudoPawsMovementSoundCue.STEP -> Unit
                    }
                }
                movementAudioNextStep = dueStep + 1
            }

            emitCaptureImpactSounds(state, nowMillis)
        }

        private fun emitCaptureImpactSounds(
            state: LudoPaws3DSceneState,
            nowMillis: Long,
        ) {
            val matchId = state.snapshot?.matchId ?: return
            state.captureReturns.values.forEach { capture ->
                if (nowMillis < capture.startedAtMillis) return@forEach

                val motion = capture.motion
                val key =
                    "$matchId:${motion.playerId}:${motion.tokenIndex}:" +
                        "${motion.fromPosition}:${motion.toPosition}"
                if (!emittedCaptureAudioKeys.add(key)) return@forEach

                GameSoundFeedback.capture(
                    context = context,
                )
            }

            while (emittedCaptureAudioKeys.size > MAX_CAPTURE_AUDIO_KEYS) {
                val iterator = emittedCaptureAudioKeys.iterator()
                if (!iterator.hasNext()) break
                iterator.next()
                iterator.remove()
            }
        }

        private fun awaitPresentationVisible(): Long? {
            visibilityLock.withLock {
                while (running && (!presentationVisible || pendingFrameNanos == 0L)) {
                    visibilityChanged.await()
                }
                if (!running) return null
                return pendingFrameNanos.also { pendingFrameNanos = 0L }
            }
        }

        override fun run() {
            val startupAt = SystemClock.elapsedRealtime()
            var egl: EglWindow? = null
            var ownedRenderer: LudoPaws3DSceneRenderer? = null
            try {
                egl = EglWindow(surfaceTexture)
                val renderer = LudoPaws3DSceneRenderer()
                ownedRenderer = renderer
                renderer.onSurfaceCreated()
                var renderedWidth = -1
                var renderedHeight = -1
                var firstBoardSubmitted = false

                while (running) {
                    val frameTimeNanos = awaitPresentationVisible() ?: break
                    val frameStartedAtMillis = frameTimeNanos / 1_000_000L
                    val width = targetWidth.coerceAtLeast(1)
                    val height = targetHeight.coerceAtLeast(1)
                    if (
                        width != renderedWidth ||
                        height != renderedHeight
                    ) {
                        renderer.onSurfaceChanged(width, height)
                        renderedWidth = width
                        renderedHeight = height
                    }

                    val frameState = sceneState
                    LudoPaws3DCharacterPolicy.bindRenderAssignments(
                        snapshot = frameState.snapshot,
                        characterIdsBySeat = frameState.characterIdsBySeat,
                    )
                    renderer.drawFrame(
                        state = frameState,
                        nowMillis = frameStartedAtMillis,
                    )
                    emitMovementStepSounds(
                        state = frameState,
                        nowMillis = frameStartedAtMillis,
                    )
                    if (!egl.swapBuffers()) error("EGL swapBuffers failed")
                    if (frameState.snapshot != null && !firstBoardSubmitted) {
                        firstBoardSubmitted = true
                        Log.d("LudoPawsLoad", "game first_frame_ms=${SystemClock.elapsedRealtime() - startupAt}")
                        // Release the cover only after a complete board buffer is available.
                        // Do not depend on TextureView updates while an opaque cover is above it.
                        post { if (renderLoop === this) setOperational(true) }
                    }
                }
            } catch (interrupted: InterruptedException) {
                // Normal shutdown path.
            } catch (error: Throwable) {
                Log.w(
                    TAG,
                    "3D pawn runtime unavailable; keeping authoritative board fallback",
                    error,
                )
            } finally {
                LudoPaws3DCharacterPolicy.clearRenderAssignments()
                runCatching { ownedRenderer?.release() }
                runCatching { egl?.release() }
                synchronized(surfaceLifetimeLock) {
                    terminated = true
                    if (releaseSurface) surfaceTexture.release()
                }
                post {
                    if (retiringLoops[surfaceTexture] === this) retiringLoops.remove(surfaceTexture)
                    if (renderLoop === this) {
                        setOperational(false)
                        if (running) onStartupFailure?.invoke()
                    }
                }
            }
        }
    }

    private class EglWindow(
        surfaceTexture: SurfaceTexture,
    ) {
        private val display: EGLDisplay
        private val context: EGLContext
        private val surface: EGLSurface

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
            val versions = IntArray(2)
            check(
                EGL14.eglInitialize(
                    display,
                    versions,
                    0,
                    versions,
                    1,
                ),
            ) { "EGL initialize failed" }

            val configAttributes =
                intArrayOf(
                    EGL14.EGL_SURFACE_TYPE,
                    EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RENDERABLE_TYPE,
                    EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                    EGL14.EGL_RED_SIZE,
                    8,
                    EGL14.EGL_GREEN_SIZE,
                    8,
                    EGL14.EGL_BLUE_SIZE,
                    8,
                    EGL14.EGL_ALPHA_SIZE,
                    8,
                    EGL14.EGL_DEPTH_SIZE,
                    16,
                    EGL14.EGL_NONE,
                )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            check(
                EGL14.eglChooseConfig(
                    display,
                    configAttributes,
                    0,
                    configs,
                    0,
                    configs.size,
                    configCount,
                    0,
                ) && configCount[0] > 0,
            ) { "No ES3 RGBA EGL config" }
            val config = checkNotNull(configs[0])

            context =
                EGL14.eglCreateContext(
                    display,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(
                        EGL14.EGL_CONTEXT_CLIENT_VERSION,
                        3,
                        EGL14.EGL_NONE,
                    ),
                    0,
                )
            check(context != EGL14.EGL_NO_CONTEXT) {
                "ES3 EGL context creation failed"
            }

            surface =
                EGL14.eglCreateWindowSurface(
                    display,
                    config,
                    surfaceTexture,
                    intArrayOf(EGL14.EGL_NONE),
                    0,
                )
            check(surface != EGL14.EGL_NO_SURFACE) {
                "EGL window surface creation failed"
            }
            check(
                EGL14.eglMakeCurrent(
                    display,
                    surface,
                    surface,
                    context,
                ),
            ) { "EGL makeCurrent failed" }
            check(EGL14.eglSwapInterval(display, 1)) { "EGL vsync setup failed" }
        }

        fun swapBuffers(): Boolean =
            EGL14.eglSwapBuffers(display, surface)

        fun release() {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private companion object {
        const val TAG = "LudoPaws3D"
        const val MAX_CAPTURE_AUDIO_KEYS = 64
    }
}
