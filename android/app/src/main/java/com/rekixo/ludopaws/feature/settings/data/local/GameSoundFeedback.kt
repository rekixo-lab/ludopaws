package com.rekixo.ludopaws.feature.settings.data.local

import android.content.Context
import android.util.Log
import com.rekixo.ludopaws.core.audio.LudoPawsAudioCatalog
import com.rekixo.ludopaws.feature.characters.data.audio.LudoPawsAudioAssetPlayer
import com.rekixo.ludopaws.feature.characters.data.audio.LudoPawsProceduralAudio
import com.rekixo.ludopaws.feature.characters.domain.reaction.GameMomentType
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReaction

/**
 * Short game/UI sound facade.
 *
 * Every sound resolves a preferred authored asset family and rotates through
 * the numbered takes that exist. Missing authored assets fall back to stable
 * res/raw names and then the situation-specific procedural sound. Legacy
 * placeholder WAV aliases are not consulted because several historical files
 * contained identical audio.
 */
object GameSoundFeedback {
    fun click(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_ui_click"),
            fallback = LudoPawsProceduralAudio.Sfx.CLICK,
            volume = .46f,
            priority = PRIORITY_UI,
            assetPaths = LudoPawsAudioCatalog.Sfx.UI_CLICK,
        )

    /**
     * Preloads the exact movement-step clip family used by the synchronized
     * visual movement clock. This does not play anything and does not alter
     * gameplay timing.
     */
    fun preloadMovementStep(context: Context) {
        preload(
            context = context,
            rawResourceNames = listOf("lp_sfx_move_paw"),
            assetPaths = LudoPawsAudioCatalog.Sfx.MOVE_STEP,
        )
        preload(
            context = context,
            rawResourceNames = listOf("lp_sfx_jump"),
            assetPaths = LudoPawsAudioCatalog.Sfx.MOVE_JUMP,
        )
    }

    /**
     * Preloads only the SFX that can occur during pawn travel. These are kept
     * hot before the first move so the visual step clock never has to wait for
     * an authored clip to finish loading.
     */
    fun preloadMovementEventSfx(context: Context) {
        val sounds =
            listOf(
                listOf("lp_sfx_move_paw") to LudoPawsAudioCatalog.Sfx.MOVE_STEP_DOG,
                listOf("lp_sfx_move_hoof") to LudoPawsAudioCatalog.Sfx.MOVE_STEP_GOAT,
                listOf("lp_sfx_move_web") to LudoPawsAudioCatalog.Sfx.MOVE_STEP_DUCK,
                listOf("lp_sfx_move_paw") to LudoPawsAudioCatalog.Sfx.MOVE_STEP_CAT,
                listOf("lp_sfx_move_paw") to LudoPawsAudioCatalog.Sfx.MOVE_STEP,
                listOf("lp_sfx_jump") to LudoPawsAudioCatalog.Sfx.MOVE_JUMP,
                listOf("lp_sfx_yard_exit") to LudoPawsAudioCatalog.Sfx.YARD_EXIT,
                listOf("lp_sfx_dice_roll") to LudoPawsAudioCatalog.Sfx.DICE_ROLL,
                listOf("lp_sfx_dice_settle") to LudoPawsAudioCatalog.Sfx.DICE_SETTLE,
                listOf("lp_sfx_six") to LudoPawsAudioCatalog.Sfx.SIX,
                listOf("lp_sfx_capture_impact") to LudoPawsAudioCatalog.Sfx.CAPTURE,
                listOf("lp_sfx_safe_shimmer") to LudoPawsAudioCatalog.Sfx.SAFE_RELIEF,
                listOf("lp_sfx_home_lane") to LudoPawsAudioCatalog.Sfx.HOME_LANE,
                listOf("lp_sfx_home_sparkle") to LudoPawsAudioCatalog.Sfx.HOME,
                listOf("lp_sfx_fail_soft") to LudoPawsAudioCatalog.Sfx.FAIL,
                listOf("lp_sfx_exact_home_miss") to LudoPawsAudioCatalog.Sfx.EXACT_HOME_MISS,
                listOf("lp_sfx_third_six") to LudoPawsAudioCatalog.Sfx.THIRD_SIX,
                listOf("lp_sfx_victory_sting") to LudoPawsAudioCatalog.Sfx.VICTORY,
                listOf("lp_sfx_defeat_sting") to LudoPawsAudioCatalog.Sfx.DEFEAT,
            )
        sounds.forEach { (rawResourceNames, assetPaths) ->
            preload(
                context = context,
                rawResourceNames = rawResourceNames,
                assetPaths = assetPaths,
            )
        }
    }

    /**
     * Plays the soft landing/contact sound for one completed visual board step.
     * Species-specific assets win first, then the shared step family, then the
     * legacy raw/procedural fallback. The authored jump clip is never used as a
     * normal footfall.
     */
    /** Preloads roll/settle audio to avoid first-tap load latency in online matches. */
    fun preloadDiceFeedback(context: Context) {
        preload(
            context = context,
            rawResourceNames = listOf("lp_sfx_dice_roll"),
            assetPaths = LudoPawsAudioCatalog.Sfx.DICE_ROLL,
        )
        preload(
            context = context,
            rawResourceNames = listOf("lp_sfx_dice_settle"),
            assetPaths = LudoPawsAudioCatalog.Sfx.DICE_SETTLE,
        )
    }

    fun moveStep(
        context: Context,
        characterId: String? = null,
    ) {
        if (!GameSettingsStore(context).snapshot().soundEnabled) return

        val movement =
            when (characterId) {
                "goat", "goat_lumi", "pig", "horse" ->
                    Triple(
                        listOf("lp_sfx_move_hoof"),
                        LudoPawsProceduralAudio.Sfx.MOVE_HOOF,
                        LudoPawsAudioCatalog.Sfx.MOVE_STEP_GOAT,
                    )
                "duck" ->
                    Triple(
                        listOf("lp_sfx_move_web"),
                        LudoPawsProceduralAudio.Sfx.MOVE_WEB,
                        LudoPawsAudioCatalog.Sfx.MOVE_STEP_DUCK,
                    )
                "cat", "elephant", "rabbit" ->
                    Triple(
                        listOf("lp_sfx_move_paw"),
                        LudoPawsProceduralAudio.Sfx.MOVE_PAW,
                        LudoPawsAudioCatalog.Sfx.MOVE_STEP_CAT,
                    )
                else ->
                    Triple(
                        listOf("lp_sfx_move_paw"),
                        LudoPawsProceduralAudio.Sfx.MOVE_PAW,
                        LudoPawsAudioCatalog.Sfx.MOVE_STEP_DOG,
                    )
            }
        val volume =
            when (characterId) {
                "goat", "goat_lumi", "pig", "horse" -> .30f
                "duck" -> .28f
                else -> .30f
            }
        val priority = PRIORITY_MOVEMENT
        if (
            LudoPawsAudioAssetPlayer.playAuthoredSfx(
                context = context,
                assetPaths = movement.third,
                volume = volume,
                priority = priority,
            )
        ) {
            return
        }
        if (
            LudoPawsAudioAssetPlayer.playAuthoredSfx(
                context = context,
                assetPaths = LudoPawsAudioCatalog.Sfx.MOVE_STEP,
                volume = volume,
                priority = priority,
            )
        ) {
            return
        }
        LudoPawsAudioAssetPlayer.playSfx(
            context = context,
            rawResourceNames = movement.first,
            fallback = movement.second,
            volume = volume,
            priority = priority,
        )
    }

    /** Plays at hop take-off; the corresponding soft step lands on the visual cell. */
    fun jump(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_jump"),
            fallback = LudoPawsProceduralAudio.Sfx.JUMP,
            volume = .30f,
            priority = PRIORITY_JUMP,
            assetPaths = LudoPawsAudioCatalog.Sfx.MOVE_JUMP,
        )

    /** Legacy call site compatibility: this now means landing/step, not jump. */
    fun move(
        context: Context,
        characterId: String? = null,
    ) = moveStep(context, characterId)

    fun roll(context: Context, eventIndex: Int? = null) =
        playEventForIndex(
            context = context,
            eventIndex = eventIndex,
            names = listOf("lp_sfx_dice_roll"),
            fallback = LudoPawsProceduralAudio.Sfx.DICE_ROLL,
            volume = .50f,
            priority = PRIORITY_ROLL,
            assetPaths = LudoPawsAudioCatalog.Sfx.DICE_ROLL,
        )

    /** Short landing tick when the verified dice face is revealed. */
    fun diceSettle(context: Context, eventIndex: Int? = null) =
        playEventForIndex(
            context = context,
            eventIndex = eventIndex,
            names = listOf("lp_sfx_dice_settle"),
            fallback = LudoPawsProceduralAudio.Sfx.DICE_SETTLE,
            volume = .32f,
            priority = PRIORITY_HIGH,
            assetPaths = LudoPawsAudioCatalog.Sfx.DICE_SETTLE,
        )

    fun six(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_six"),
            fallback = LudoPawsProceduralAudio.Sfx.SIX_SPARK,
            volume = .45f,
            priority = PRIORITY_HIGH,
            assetPaths = LudoPawsAudioCatalog.Sfx.SIX,
        )

    fun yardExit(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_yard_exit"),
            fallback = LudoPawsProceduralAudio.Sfx.YARD_EXIT,
            volume = .43f,
            priority = PRIORITY_EVENT,
            assetPaths = LudoPawsAudioCatalog.Sfx.YARD_EXIT,
        )

    fun capture(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_capture_impact"),
            fallback = LudoPawsProceduralAudio.Sfx.CAPTURE_IMPACT,
            volume = .62f,
            priority = PRIORITY_CRITICAL,
            assetPaths = LudoPawsAudioCatalog.Sfx.CAPTURE,
        )

    fun safe(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_safe_shimmer"),
            fallback = LudoPawsProceduralAudio.Sfx.SAFE_SHIMMER,
            volume = .44f,
            priority = PRIORITY_EVENT,
            assetPaths = LudoPawsAudioCatalog.Sfx.SAFE_RELIEF,
        )

    fun homeLane(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_home_lane"),
            fallback = LudoPawsProceduralAudio.Sfx.HOME_LANE,
            volume = .45f,
            priority = PRIORITY_EVENT,
            assetPaths = LudoPawsAudioCatalog.Sfx.HOME_LANE,
        )

    fun home(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_home_sparkle"),
            fallback = LudoPawsProceduralAudio.Sfx.HOME_SPARKLE,
            volume = .56f,
            priority = PRIORITY_HIGH,
            assetPaths = LudoPawsAudioCatalog.Sfx.HOME,
        )

    fun frustrated(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_fail_soft"),
            fallback = LudoPawsProceduralAudio.Sfx.FAIL_SOFT,
            volume = .32f,
            priority = PRIORITY_SOFT_EVENT,
            assetPaths = LudoPawsAudioCatalog.Sfx.FAIL,
        )

    /** A distinct, gentle descending spring for a short exact-home roll. */
    fun exactHomeMiss(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_exact_home_miss"),
            fallback = LudoPawsProceduralAudio.Sfx.EXACT_HOME_MISS,
            volume = .30f,
            priority = PRIORITY_SOFT_EVENT,
            assetPaths = LudoPawsAudioCatalog.Sfx.EXACT_HOME_MISS,
        )

    fun thirdSix(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_third_six"),
            fallback = LudoPawsProceduralAudio.Sfx.THIRD_SIX,
            volume = .48f,
            priority = PRIORITY_CRITICAL,
            assetPaths = LudoPawsAudioCatalog.Sfx.THIRD_SIX,
        )

    fun victory(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_victory_sting"),
            fallback = LudoPawsProceduralAudio.Sfx.VICTORY,
            volume = .60f,
            priority = PRIORITY_CRITICAL,
            assetPaths = LudoPawsAudioCatalog.Sfx.VICTORY,
        )

    fun defeat(context: Context) =
        play(
            context = context,
            names = listOf("lp_sfx_defeat_sting"),
            fallback = LudoPawsProceduralAudio.Sfx.DEFEAT,
            volume = .42f,
            priority = PRIORITY_HIGH,
            assetPaths = LudoPawsAudioCatalog.Sfx.DEFEAT,
        )

    /** Returns true when this batch emitted a dedicated physical/game SFX. */
    fun reaction(
        context: Context,
        reactions: List<LudoPawsReaction>,
        characterIdsBySeat: List<String> = emptyList(),
    ): Boolean {
        val highest =
            reactions.maxByOrNull(LudoPawsReaction::priority)
                ?: return false
        val characterId = characterIdsBySeat.getOrNull(highest.seat)

        return when (highest.momentType) {
            GameMomentType.SIX_ROLLED -> played { six(context) }
            // Yard exit is emitted by the movement step clock when the pawn
            // actually arrives on its first road cell.
            GameMomentType.TOKEN_LEFT_YARD -> false
            // Per-cell movement ticks are emitted by the synchronized 3D movement
            // clock. Playing the legacy one-shot move cue here would double-fire
            // the sound for every move.
            GameMomentType.ONLY_LEGAL_MOVE -> false
            // Physical movement events are emitted by the shared 3D movement
            // clock at their actual visual contact/arrival time.
            GameMomentType.CAPTURE_MADE,
            GameMomentType.TOKEN_CAPTURED,
            GameMomentType.SAFE_REACHED,
            GameMomentType.HOME_LANE_ENTERED,
            GameMomentType.HOME_REACHED,
            -> false
            GameMomentType.POOR_ROLL_STREAK,
            GameMomentType.NO_LEGAL_MOVE,
            -> played { frustrated(context) }
            GameMomentType.EXACT_HOME_MISS -> played { exactHomeMiss(context) }
            GameMomentType.THIRD_SIX_FORFEIT -> played { thirdSix(context) }
            GameMomentType.MATCH_WIN,
            GameMomentType.TEAM_WIN,
            -> played { victory(context) }
            GameMomentType.MATCH_LOSS,
            GameMomentType.TEAM_LOSS,
            -> played { defeat(context) }
            GameMomentType.TURN_STARTED,
            GameMomentType.ROLL_STARTED,
            GameMomentType.LOW_ROLL,
            GameMomentType.TOKEN_MOVED,
            GameMomentType.TOKEN_THREATENED,
            GameMomentType.PLAYER_LEADING,
            GameMomentType.IDLE_WAITING,
            null,
            -> false
        }
    }

    private fun playEventForIndex(
        context: Context,
        eventIndex: Int?,
        names: List<String>,
        fallback: LudoPawsProceduralAudio.Sfx,
        volume: Float,
        priority: Int,
        assetPaths: List<String>,
    ) {
        val soundName = names.firstOrNull() ?: "unknown"
        if (!GameSettingsStore(context).snapshot().soundEnabled) {
            Log.w(TAG, "EVENT_SUPPRESSED sound=" + soundName + " reason=sound-disabled eventIndex=" + eventIndex)
            return
        }
        Log.d(TAG, "EVENT_REQUEST sound=" + soundName + " eventIndex=" + eventIndex + " priority=" + priority)
        val authoredAccepted =
            eventIndex != null &&
                LudoPawsAudioAssetPlayer.playAuthoredSfxForEvent(
                    context = context,
                    assetPaths = assetPaths,
                    eventIndex = eventIndex,
                    volume = volume,
                    priority = priority,
                )
        Log.d(TAG, "EVENT_AUTHORED sound=" + soundName + " eventIndex=" + eventIndex + " accepted=" + authoredAccepted)
        if (authoredAccepted) return

        Log.w(TAG, "EVENT_FALLBACK sound=" + soundName + " eventIndex=" + eventIndex)
        LudoPawsAudioAssetPlayer.playSfx(
            context = context,
            rawResourceNames = names,
            fallback = fallback,
            volume = volume,
            priority = priority,
            assetPaths = assetPaths,
        )
    }

    private inline fun played(block: () -> Unit): Boolean {
        block()
        return true
    }

    private fun preload(
        context: Context,
        rawResourceNames: List<String>,
        assetPaths: List<String>,
    ) {
        if (
            !GameSettingsStore(context)
                .snapshot()
                .soundEnabled
        ) {
            return
        }
        LudoPawsAudioAssetPlayer.preloadSfx(
            context = context,
            rawResourceNames = rawResourceNames,
            assetPaths = assetPaths,
        )
    }

    private fun play(
        context: Context,
        names: List<String>,
        fallback: LudoPawsProceduralAudio.Sfx,
        volume: Float,
        playbackRate: Float = 1f,
        priority: Int = PRIORITY_NORMAL,
        assetPaths: List<String> = emptyList(),
    ) {
        if (
            !GameSettingsStore(context)
                .snapshot()
                .soundEnabled
        ) {
            return
        }
        LudoPawsAudioAssetPlayer.playSfx(
            context = context,
            rawResourceNames = names,
            fallback = fallback,
            volume = volume,
            playbackRate = playbackRate,
            priority = priority,
            assetPaths = assetPaths,
        )
    }

    private const val TAG = "LudoPawsAudio"
    private const val PRIORITY_UI = 0
    private const val PRIORITY_MOVEMENT = 1
    private const val PRIORITY_JUMP = 2
    private const val PRIORITY_SOFT_EVENT = 3
    private const val PRIORITY_ROLL = 4
    private const val PRIORITY_EVENT = 5
    private const val PRIORITY_HIGH = 7
    private const val PRIORITY_CRITICAL = 10
    private const val PRIORITY_NORMAL = 1
}
