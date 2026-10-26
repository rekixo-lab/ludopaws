package com.rekixo.ludopaws.feature.characters.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsProceduralVocal
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Copyright-safe fallback synthesizer used only when a named packaged WAV/OGG
 * override is absent. These are intentionally stylized non-verbal game sounds,
 * not claims to be field recordings of real animals.
 */
internal object LudoPawsProceduralAudio {
    private const val SAMPLE_RATE = 24_000
    private const val TWO_PI = PI * 2.0

    private val executor =
        Executors.newScheduledThreadPool(2) { runnable ->
            Thread(runnable, "LudoPawsAudio").apply {
                isDaemon = true
            }
        }

    private val generatedSamples = mutableMapOf<String, ShortArray>()

    enum class Sfx {
        CLICK,
        DICE_ROLL,
        DICE_SETTLE,
        MOVE_PAW,
        MOVE_HOOF,
        MOVE_WEB,
        JUMP,
        SIX_SPARK,
        YARD_EXIT,
        CAPTURE_IMPACT,
        SAFE_SHIMMER,
        HOME_LANE,
        HOME_SPARKLE,
        FAIL_SOFT,
        EXACT_HOME_MISS,
        THIRD_SIX,
        VICTORY,
        DEFEAT,
    }

    @Synchronized
    fun playVocal(
        preset: LudoPawsProceduralVocal,
        volume: Float,
        playbackRate: Float,
    ) {
        val rate = playbackRate.coerceIn(.65f, 1.45f)
        val samples = generatedSamples.getOrPut("vocal:$preset:$rate") {
            vocalSamples(preset = preset, rate = rate)
        }
        play(samples, volume)
    }

    @Synchronized
    fun playSfx(
        preset: Sfx,
        volume: Float,
        playbackRate: Float = 1f,
    ) {
        val rate = playbackRate.coerceIn(.65f, 1.45f)
        val samples = generatedSamples.getOrPut("sfx:$preset:$rate") {
            sfxSamples(preset = preset, rate = rate)
        }
        play(samples, volume)
    }

    private fun play(
        samples: ShortArray,
        volume: Float,
    ) {
        if (samples.isEmpty()) return
        executor.execute {
            var track: AudioTrack? = null
            try {
                val bytes = samples.size * 2
                track =
                    AudioTrack
                        .Builder()
                        .setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        .setAudioFormat(
                            AudioFormat
                                .Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                        )
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(bytes)
                        .build()
                if (
                    track.state != AudioTrack.STATE_INITIALIZED ||
                    track.write(
                        samples,
                        0,
                        samples.size,
                        AudioTrack.WRITE_BLOCKING,
                    ) <= 0
                ) {
                    return@execute
                }
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()
                val durationMillis =
                    (samples.size * 1_000L / SAMPLE_RATE) + 45L
                Thread.sleep(durationMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Throwable) {
                // Audio is presentation-only. A device audio failure must never
                // affect authoritative game state or crash a match.
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }
    }

    private fun vocalSamples(
        preset: LudoPawsProceduralVocal,
        rate: Float,
    ): ShortArray =
        when (preset) {
            LudoPawsProceduralVocal.DOG_YIP ->
                synth(
                    durationMs = 210,
                    rate = rate,
                    startHz = 470.0,
                    endHz = 710.0,
                    harmonic = .30,
                    noise = .05,
                    tremoloHz = 5.0,
                    doublePulse = true,
                )

            LudoPawsProceduralVocal.DOG_WHINE ->
                synth(
                    durationMs = 370,
                    rate = rate,
                    startHz = 520.0,
                    endHz = 760.0,
                    harmonic = .18,
                    noise = .02,
                    vibratoHz = 6.5,
                    vibratoDepth = .035,
                )

            LudoPawsProceduralVocal.DOG_RUFF ->
                synth(
                    durationMs = 230,
                    rate = rate,
                    startHz = 190.0,
                    endHz = 145.0,
                    harmonic = .55,
                    noise = .20,
                    tremoloHz = 12.0,
                )

            LudoPawsProceduralVocal.GOAT_BLEAT ->
                synth(
                    durationMs = 390,
                    rate = rate,
                    startHz = 290.0,
                    endHz = 320.0,
                    harmonic = .42,
                    noise = .04,
                    vibratoHz = 12.5,
                    vibratoDepth = .075,
                    tremoloHz = 8.5,
                )

            LudoPawsProceduralVocal.GOAT_SOFT_BLEAT ->
                synth(
                    durationMs = 360,
                    rate = rate,
                    startHz = 250.0,
                    endHz = 225.0,
                    harmonic = .34,
                    noise = .035,
                    vibratoHz = 8.0,
                    vibratoDepth = .05,
                )

            LudoPawsProceduralVocal.COCK_CROW ->
                synth(
                    durationMs = 560,
                    rate = rate,
                    startHz = 620.0,
                    endHz = 980.0,
                    harmonic = .55,
                    noise = .07,
                    vibratoHz = 7.0,
                    vibratoDepth = .10,
                    tremoloHz = 6.0,
                    doublePulse = true,
                )

            LudoPawsProceduralVocal.COCK_CLUCK ->
                synth(
                    durationMs = 180,
                    rate = rate,
                    startHz = 580.0,
                    endHz = 270.0,
                    harmonic = .50,
                    noise = .09,
                    tremoloHz = 13.0,
                    doublePulse = true,
                )

            LudoPawsProceduralVocal.DUCK_QUACK ->
                synth(
                    durationMs = 185,
                    rate = rate,
                    startHz = 720.0,
                    endHz = 330.0,
                    harmonic = .62,
                    noise = .18,
                    tremoloHz = 16.0,
                )

            LudoPawsProceduralVocal.DUCK_SOFT_QUACK ->
                synth(
                    durationMs = 205,
                    rate = rate,
                    startHz = 480.0,
                    endHz = 285.0,
                    harmonic = .48,
                    noise = .11,
                    tremoloHz = 11.0,
                )

            LudoPawsProceduralVocal.CAT_CHIRP ->
                synth(
                    durationMs = 205,
                    rate = rate,
                    startHz = 690.0,
                    endHz = 980.0,
                    harmonic = .22,
                    noise = .015,
                    vibratoHz = 8.0,
                    vibratoDepth = .025,
                    doublePulse = true,
                )

            LudoPawsProceduralVocal.CAT_MEW ->
                synth(
                    durationMs = 360,
                    rate = rate,
                    startHz = 510.0,
                    endHz = 430.0,
                    harmonic = .25,
                    noise = .015,
                    vibratoHz = 5.0,
                    vibratoDepth = .045,
                    midpointPeakHz = 790.0,
                )

            LudoPawsProceduralVocal.CAT_PURR ->
                synth(
                    durationMs = 430,
                    rate = rate,
                    startHz = 82.0,
                    endHz = 86.0,
                    harmonic = .65,
                    noise = .055,
                    tremoloHz = 25.0,
                )

            LudoPawsProceduralVocal.CAT_HUFF ->
                synth(
                    durationMs = 155,
                    rate = rate,
                    startHz = 175.0,
                    endHz = 135.0,
                    harmonic = .10,
                    noise = .45,
                    tremoloHz = 7.0,
                )
        }

    private fun sfxSamples(
        preset: Sfx,
        rate: Float,
    ): ShortArray =
        when (preset) {
            Sfx.CLICK ->
                synth(70, rate, 1_050.0, 620.0, .08, .03)
            Sfx.DICE_ROLL ->
                synth(330, rate, 190.0, 125.0, .12, .50, tremoloHz = 24.0)
            Sfx.DICE_SETTLE ->
                synth(72, rate, 880.0, 490.0, .18, .08)
            Sfx.MOVE_PAW ->
                synth(75, rate, 170.0, 105.0, .10, .22)
            Sfx.MOVE_HOOF ->
                synth(82, rate, 520.0, 270.0, .18, .11)
            Sfx.MOVE_WEB ->
                synth(86, rate, 310.0, 165.0, .10, .32)
            Sfx.JUMP ->
                synth(105, rate, 240.0, 650.0, .18, .16, tremoloHz = 9.0)
            Sfx.SIX_SPARK ->
                synth(230, rate, 720.0, 1_180.0, .28, .015, doublePulse = true)
            Sfx.YARD_EXIT ->
                synth(150, rate, 360.0, 690.0, .24, .05)
            Sfx.CAPTURE_IMPACT ->
                synth(210, rate, 180.0, 72.0, .55, .32, tremoloHz = 6.0)
            Sfx.SAFE_SHIMMER ->
                synth(300, rate, 620.0, 920.0, .36, .01, vibratoHz = 4.0, vibratoDepth = .02)
            Sfx.HOME_LANE ->
                synth(330, rate, 460.0, 760.0, .24, .01, doublePulse = true)
            Sfx.HOME_SPARKLE ->
                synth(520, rate, 540.0, 1_080.0, .34, .01, tremoloHz = 5.0, doublePulse = true)
            Sfx.FAIL_SOFT ->
                synth(220, rate, 330.0, 165.0, .18, .04)
            Sfx.EXACT_HOME_MISS ->
                synth(260, rate, 460.0, 170.0, .23, .07, tremoloHz = 5.0)
            Sfx.THIRD_SIX ->
                synth(320, rate, 360.0, 92.0, .38, .16, tremoloHz = 13.0)
            Sfx.VICTORY ->
                synth(720, rate, 480.0, 1_040.0, .36, .01, vibratoHz = 5.5, doublePulse = true)
            Sfx.DEFEAT ->
                synth(560, rate, 360.0, 145.0, .30, .03, vibratoHz = 3.2)
        }

    private fun synth(
        durationMs: Int,
        rate: Float,
        startHz: Double,
        endHz: Double,
        harmonic: Double,
        noise: Double,
        vibratoHz: Double = 0.0,
        vibratoDepth: Double = 0.0,
        tremoloHz: Double = 0.0,
        doublePulse: Boolean = false,
        midpointPeakHz: Double? = null,
    ): ShortArray {
        val adjustedDuration =
            (durationMs / rate)
                .toInt()
                .coerceAtLeast(55)
        val count =
            (SAMPLE_RATE * adjustedDuration / 1_000)
                .coerceAtLeast(1)
        var phase = 0.0
        var noiseState =
            (durationMs * 1103515245 + startHz.toInt()) xor endHz.toInt()

        return ShortArray(count) { index ->
            val t = index.toDouble() / SAMPLE_RATE
            val p = index.toDouble() / count.toDouble()
            val baseHz =
                if (midpointPeakHz != null) {
                    if (p < .5) {
                        lerp(startHz, midpointPeakHz, p * 2.0)
                    } else {
                        lerp(midpointPeakHz, endHz, (p - .5) * 2.0)
                    }
                } else {
                    lerp(startHz, endHz, p)
                }
            val vibrato =
                1.0 +
                    if (vibratoHz > 0.0) {
                        sin(TWO_PI * vibratoHz * t) * vibratoDepth
                    } else {
                        0.0
                    }
            phase += TWO_PI * baseHz * vibrato / SAMPLE_RATE

            noiseState = noiseState * 1664525 + 1013904223
            val white =
                (((noiseState ushr 9) and 0x7fffff) / 4194303.5) - 1.0
            val tone =
                sin(phase) +
                    harmonic * sin(phase * 2.0 + .35)
            val tremolo =
                if (tremoloHz > 0.0) {
                    .72 + .28 * sin(TWO_PI * tremoloHz * t)
                } else {
                    1.0
                }
            val pulse =
                if (doublePulse) {
                    val first = bell(p, .23, .17)
                    val second = bell(p, .70, .19)
                    (first + second).coerceAtMost(1.0)
                } else {
                    1.0
                }
            val envelope =
                attackReleaseEnvelope(p) * pulse
            val sample =
                ((tone * (1.0 - noise) + white * noise) *
                    tremolo *
                    envelope *
                    .58)
                    .coerceIn(-1.0, 1.0)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun attackReleaseEnvelope(progress: Double): Double {
        val attack = (progress / .09).coerceIn(0.0, 1.0)
        val release = ((1.0 - progress) / .20).coerceIn(0.0, 1.0)
        return attack * release
    }

    private fun bell(
        value: Double,
        center: Double,
        radius: Double,
    ): Double {
        val x = ((value - center) / radius).coerceIn(-1.0, 1.0)
        return if (kotlin.math.abs(x) >= 1.0) 0.0 else .5 + .5 * cosPi(x)
    }

    private fun cosPi(value: Double): Double =
        kotlin.math.cos(PI * value)

    private fun lerp(
        start: Double,
        end: Double,
        progress: Double,
    ): Double =
        start + (end - start) * progress.coerceIn(0.0, 1.0)
}
