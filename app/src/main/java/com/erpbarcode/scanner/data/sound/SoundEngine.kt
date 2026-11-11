package com.erpbarcode.scanner.data.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Percentage of the media stream's volume. */
private const val VOLUME = 80

/**
 * The media stream, not notifications.
 *
 * These used to play on STREAM_NOTIFICATION, which Android silences whenever the device is on
 * vibrate or silent and which follows the notification volume. A scanner left on vibrate therefore
 * went quiet while the app's own Sound setting still said "on" — and the tone it lost first was
 * the rejection, the one that matters most. Media is not muted by the ringer mode, so the app's
 * own setting is what decides; the operator still controls loudness with the volume keys.
 */
private const val STREAM = AudioManager.STREAM_MUSIC

/** One step of a synthesised sound: a pitch held for [ms], or silence when [hz] is 0. */
private class Step(val hz: Int, val ms: Int)

/**
 * The warning: high falling to lower, four times — "dee-doo, dee-doo, dee-doo, dee-doo".
 *
 * The app's own sound rather than one of ToneGenerator's, because none of those fits. The double
 * beep this replaced was 400 Hz + 1200 Hz, heard on the floor as the device's bass "bloop": duller
 * and quieter than the scanner's decode beep that plays just before it, so the beep that says
 * "read" drowned out the one that says "look". The highest clean tone ToneGenerator has is 1200 Hz,
 * which Android uses for acknowledgement; the higher ones are telephony signals that sound
 * different from one manufacturer to the next.
 *
 * - **2–3 kHz** is where a handheld's small speaker is most efficient and the ear most sensitive,
 *   so it is the loudest sound a given volume setting can make. Same register as the scanner's
 *   beep; the falling pitch is what says "no".
 * - **Four times**, about 1.4 s — two at first, doubled on request. One pair is easy to miss in a
 *   loud warehouse, and a longer sound is heard as a louder one. The warning dialog stops the
 *   operator anyway, so the length costs no time.
 *
 * Chosen on paper, not by ear on a device. These numbers are the place to tune it.
 */
private val WARNING_PATTERN = listOf(
    Step(2_900, 120), Step(1_900, 180), Step(0, 70),
    Step(2_900, 120), Step(1_900, 180), Step(0, 70),
    Step(2_900, 120), Step(1_900, 180), Step(0, 70),
    Step(2_900, 120), Step(1_900, 180),
)

private const val SAMPLE_RATE = 44_100

/** Peak level of a synthesised sound, as a fraction of full scale — just short of clipping. */
private const val PEAK = 0.9

/** Ramp at each end of a tone; one that starts or stops mid-wave clicks. */
private const val EDGE_MS = 3

/** Rendered once and shared — every screen's engine plays the same samples. */
private val WARNING_PCM: ShortArray by lazy { synthesize(WARNING_PATTERN) }

/**
 * Audible scan feedback, the counterpart to `HapticEngine`.
 *
 * A warehouse is loud and a handheld is often on a belt or in a gloved hand, so vibration alone is
 * easy to miss — particularly the one signal that matters most, a scan that did *not* land. This
 * plays on both input paths: the tone used to live inside `CameraPreview`, which meant the camera
 * beeped and the hardware trigger never did.
 *
 * Unlike the vibrator, [ToneGenerator] and the warning's [AudioTrack] each hold audio resources
 * that have to be released, so instances come from [rememberSoundEngine] rather than being
 * constructed directly.
 */
class SoundEngine {

    // Constructing this allocates an AudioTrack and is documented to fail when audio resources are
    // busy. A device that can't give us one should simply be quiet, not crash mid-scan.
    private var toneGen: ToneGenerator? = runCatching {
        ToneGenerator(STREAM, VOLUME)
    }.onFailure { Timber.w(it, "No ToneGenerator — scan sounds are off on this device") }
        .getOrNull()

    // Built up front, not on the first warning: creating a track takes long enough on some devices
    // to put a gap between the scanner's beep and the warning that follows it.
    private val warningSound = SynthesizedSound(WARNING_PCM)

    /** Short single beep — a scan that landed. */
    fun confirm() = play(ToneGenerator.TONE_PROP_BEEP, 80)

    /** Rejection tone — now only for a scanned code that isn't a sign-in code. */
    fun error() = play(ToneGenerator.TONE_PROP_NACK, 200)

    /**
     * The loud falling "dee-doo" ×4 of [WARNING_PATTERN] — the scan brought up a warning: an
     * over-scan, a barcode that is not on the document, or a document number that was not found.
     *
     * One signal for all three on purpose. Whether the scan counted or not, the operator's next
     * move is the same — look at the screen — and a single sound for that is easier to learn than
     * a distinction they would have to stop and decode. An operator working through a run of items
     * hears a rhythm of the scanner's beeps, and this breaks it.
     */
    fun warning() = warningSound.play()

    private fun play(tone: Int, durationMs: Int) {
        runCatching { toneGen?.startTone(tone, durationMs) }
    }

    fun release() {
        runCatching { toneGen?.release() }
        toneGen = null
        warningSound.release()
    }
}

/** Ties a [SoundEngine]'s audio tracks to the composition that uses it. */
@Composable
fun rememberSoundEngine(): SoundEngine {
    val engine = remember { SoundEngine() }
    DisposableEffect(engine) { onDispose { engine.release() } }
    return engine
}

/** A sound the app renders itself, loaded once into a static [AudioTrack] and rewound for each play. */
private class SynthesizedSound(pcm: ShortArray) {

    private var track: AudioTrack? = runCatching { staticTrack(pcm) }
        .onFailure { Timber.w(it, "No AudioTrack — the warning sound is off on this device") }
        .getOrNull()

    fun play() {
        runCatching {
            track?.let {
                // A static track that has finished still reports itself playing, parked at its
                // end, and has to be stopped before it can be rewound. Stopping an idle one is
                // harmless; stopping one still sounding cuts it short, so a second warning starts
                // the sound over instead of being lost behind the first.
                it.stop()
                it.reloadStaticData()
                it.play()
            }
        }
    }

    fun release() {
        runCatching { track?.release() }
        track = null
    }
}

private fun staticTrack(pcm: ShortArray): AudioTrack {
    val track = AudioTrack.Builder()
        // USAGE_MEDIA is STREAM_MUSIC under its attributes-API name: the same volume, for the same
        // reason as [STREAM].
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.size * 2)
        .build()
    if (track.write(pcm, 0, pcm.size) != pcm.size) {
        track.release()
        throw IllegalStateException("AudioTrack accepted only part of the sound")
    }
    return track
}

/**
 * Renders [pattern] as 16-bit mono PCM.
 *
 * Each tone is the first three terms of a square wave rather than a pure sine: brighter, and louder
 * on a small speaker at the same peak. Nothing above the fifth harmonic — 14.5 kHz at the highest
 * pitch used here, well inside what this sample rate carries — so nothing folds back into a buzz.
 */
private fun synthesize(pattern: List<Step>): ShortArray {
    val samples = FloatArray(pattern.sumOf { samplesFor(it.ms) })
    val edge = samplesFor(EDGE_MS)
    var at = 0
    for (step in pattern) {
        val n = samplesFor(step.ms)
        if (step.hz > 0) {
            val ramp = min(edge, n / 2).coerceAtLeast(1)
            for (i in 0 until n) {
                val x = 2 * PI * step.hz * i / SAMPLE_RATE
                val wave = sin(x) + sin(3 * x) / 3 + sin(5 * x) / 5
                val envelope = min(1.0, min(i, n - 1 - i).toDouble() / ramp)
                samples[at + i] = (wave * envelope).toFloat()
            }
        }
        at += n
    }
    val loudest = samples.maxOf { abs(it) }
    val scale = PEAK * Short.MAX_VALUE / loudest
    return ShortArray(samples.size) { (samples[it] * scale).toInt().toShort() }
}

private fun samplesFor(ms: Int): Int = ms * SAMPLE_RATE / 1000
