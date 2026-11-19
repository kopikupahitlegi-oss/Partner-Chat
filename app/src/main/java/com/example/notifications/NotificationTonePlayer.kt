package com.example.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plays both curated synthesized Partner Bank chimes/ringtones and system RingtoneManager tones,
 * with clean lifecycle management for one-shot chat alerts, looping incoming call ringtones,
 * and interactive tone previews.
 */
class NotificationTonePlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var previewJob: Job? = null
    private var previewRingtone: Ringtone? = null
    private var previewTrack: AudioTrack? = null

    private var callRingJob: Job? = null
    private var callRingtone: Ringtone? = null
    private var callTrack: AudioTrack? = null

    private var chatJob: Job? = null
    private var chatRingtone: Ringtone? = null
    private var chatTrack: AudioTrack? = null

    fun playChatNotificationTone(toneId: String, vibrate: Boolean) {
        if (vibrate) {
            triggerVibration(longArrayOf(0L, 140L, 80L, 140L), repeat = -1)
        }
        if (ToneCatalog.isSilent(toneId)) return

        stopChatTone()
        if (ToneCatalog.isBuiltIn(toneId)) {
            chatJob = scope.launch {
                val samples = synthesizeTone(toneId)
                playPcmOnce(
                    samples = samples,
                    usage = AudioAttributes.USAGE_NOTIFICATION,
                    contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION,
                    onTrackCreated = { chatTrack = it },
                    onTrackCleared = { chatTrack = null }
                )
            }
        } else {
            val uri = ToneCatalog.resolveSystemUri(toneId, ToneCategory.CHAT) ?: return
            chatJob = scope.launch {
                runCatching {
                    val ringtone = RingtoneManager.getRingtone(context, uri) ?: return@launch
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    chatRingtone = ringtone
                    ringtone.play()
                    delay(2_600L)
                    if (ringtone.isPlaying) {
                        ringtone.stop()
                    }
                    if (chatRingtone === ringtone) {
                        chatRingtone = null
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Failed playing system chat tone: ${err.message}")
                }
            }
        }
    }

    fun startIncomingCallRingtone(toneId: String, vibrate: Boolean) {
        stopIncomingCallRingtone()
        stopPreview()

        if (vibrate) {
            triggerVibration(longArrayOf(0L, 450L, 350L, 450L, 900L), repeat = 0)
        }
        if (ToneCatalog.isSilent(toneId)) return

        if (ToneCatalog.isBuiltIn(toneId)) {
            callRingJob = scope.launch {
                val samples = synthesizeTone(toneId)
                while (isActive) {
                    playPcmOnce(
                        samples = samples,
                        usage = AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
                        contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION,
                        onTrackCreated = { callTrack = it },
                        onTrackCleared = { callTrack = null }
                    )
                    delay(850L)
                }
            }
        } else {
            val uri = ToneCatalog.resolveSystemUri(toneId, ToneCategory.CALL) ?: return
            callRingJob = scope.launch {
                runCatching {
                    val ringtone = RingtoneManager.getRingtone(context, uri) ?: return@launch
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.isLooping = true
                    }
                    callRingtone = ringtone
                    ringtone.play()
                    while (isActive) {
                        delay(1_200L)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && !ringtone.isPlaying) {
                            ringtone.play()
                        }
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Failed playing system call ringtone: ${err.message}")
                }
            }
        }
    }

    fun stopIncomingCallRingtone() {
        callRingJob?.cancel()
        callRingJob = null
        runCatching {
            callRingtone?.stop()
        }
        callRingtone = null
        releaseTrack(callTrack)
        callTrack = null
        cancelVibration()
    }

    fun previewTone(toneId: String, category: ToneCategory, onFinished: () -> Unit = {}) {
        stopPreview()
        if (ToneCatalog.isSilent(toneId)) {
            onFinished()
            return
        }

        if (ToneCatalog.isBuiltIn(toneId)) {
            previewJob = scope.launch {
                val samples = synthesizeTone(toneId)
                val usage = if (category == ToneCategory.CALL) {
                    AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                } else {
                    AudioAttributes.USAGE_NOTIFICATION
                }
                playPcmOnce(
                    samples = samples,
                    usage = usage,
                    contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION,
                    onTrackCreated = { previewTrack = it },
                    onTrackCleared = { previewTrack = null }
                )
                onFinished()
            }
        } else {
            val uri = ToneCatalog.resolveSystemUri(toneId, category)
            if (uri == null) {
                onFinished()
                return
            }
            previewJob = scope.launch {
                runCatching {
                    val ringtone = RingtoneManager.getRingtone(context, uri)
                    if (ringtone != null) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(
                                if (category == ToneCategory.CALL) {
                                    AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                                } else {
                                    AudioAttributes.USAGE_NOTIFICATION
                                }
                            )
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        previewRingtone = ringtone
                        ringtone.play()
                        delay(if (category == ToneCategory.CALL) 3_600L else 2_200L)
                        if (ringtone.isPlaying) {
                            ringtone.stop()
                        }
                        if (previewRingtone === ringtone) {
                            previewRingtone = null
                        }
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Failed previewing system tone: ${err.message}")
                }
                onFinished()
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        runCatching {
            previewRingtone?.stop()
        }
        previewRingtone = null
        releaseTrack(previewTrack)
        previewTrack = null
    }

    private fun stopChatTone() {
        chatJob?.cancel()
        chatJob = null
        runCatching {
            chatRingtone?.stop()
        }
        chatRingtone = null
        releaseTrack(chatTrack)
        chatTrack = null
    }

    fun release() {
        stopIncomingCallRingtone()
        stopPreview()
        stopChatTone()
    }

    private fun releaseTrack(track: AudioTrack?) {
        if (track == null) return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        }
    }

    private suspend fun playPcmOnce(
        samples: ShortArray,
        usage: Int,
        contentType: Int,
        onTrackCreated: (AudioTrack) -> Unit,
        onTrackCleared: () -> Unit
    ) {
        if (samples.isEmpty()) return
        val byteLength = samples.size * 2
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(byteLength)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(contentType)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return

        onTrackCreated(track)
        try {
            track.write(samples, 0, samples.size)
            track.play()
            val durationMs = ((samples.size * 1000L) / SAMPLE_RATE) + 80L
            delay(durationMs)
        } catch (t: Throwable) {
            // Cancelled or playback interrupted cleanly
        } finally {
            releaseTrack(track)
            onTrackCleared()
        }
    }

    private fun getVibrator(): Vibrator? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    private fun triggerVibration(pattern: LongArray, repeat: Int) {
        val vibrator = getVibrator() ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, repeat)
            }
        }
    }

    private fun cancelVibration() {
        runCatching {
            getVibrator()?.cancel()
        }
    }

    private data class Note(
        val freqHz: Double,
        val startMs: Int,
        val durationMs: Int,
        val volume: Double = 0.65,
        val harmonic: Double = 0.25,
        val glideToHz: Double? = null
    )

    private fun synthesizeTone(toneId: String): ShortArray {
        val notes = when (toneId) {
            ToneCatalog.BUILTIN_CHAT_EMERALD -> listOf(
                Note(523.25, 0, 170, 0.58, 0.25),   // C5
                Note(659.25, 95, 200, 0.62, 0.28),  // E5
                Note(783.99, 195, 340, 0.68, 0.22)  // G5
            )
            ToneCatalog.BUILTIN_CHAT_CRYSTAL -> listOf(
                Note(880.00, 0, 220, 0.56, 0.35),   // A5
                Note(1318.51, 130, 380, 0.62, 0.30) // E6
            )
            ToneCatalog.BUILTIN_CHAT_BUBBLE -> listOf(
                Note(420.0, 0, 140, 0.62, 0.15, glideToHz = 760.0),
                Note(580.0, 150, 190, 0.65, 0.18, glideToHz = 980.0)
            )
            ToneCatalog.BUILTIN_CHAT_AURORA -> listOf(
                Note(440.00, 0, 420, 0.45, 0.22),   // A4
                Note(554.37, 60, 420, 0.42, 0.20),  // C#5
                Note(659.25, 120, 460, 0.48, 0.24)  // E5
            )
            ToneCatalog.BUILTIN_CHAT_PULSE -> listOf(
                Note(740.00, 0, 110, 0.65, 0.32),   // F#5
                Note(987.77, 135, 160, 0.68, 0.28)  // B5
            )
            ToneCatalog.BUILTIN_CALL_CLASSIC -> listOf(
                Note(523.25, 0, 220, 0.58, 0.28),
                Note(659.25, 190, 220, 0.60, 0.28),
                Note(783.99, 380, 260, 0.64, 0.28),
                Note(1046.50, 600, 360, 0.66, 0.24),
                Note(783.99, 980, 220, 0.58, 0.26),
                Note(1046.50, 1190, 420, 0.65, 0.24)
            )
            ToneCatalog.BUILTIN_CALL_HARMONY -> listOf(
                Note(440.00, 0, 340, 0.48, 0.24),
                Note(554.37, 140, 340, 0.50, 0.24),
                Note(659.25, 280, 380, 0.54, 0.22),
                Note(830.61, 440, 420, 0.56, 0.20),
                Note(659.25, 900, 320, 0.50, 0.24),
                Note(880.00, 1140, 480, 0.58, 0.22)
            )
            ToneCatalog.BUILTIN_CALL_HORIZON -> listOf(
                Note(587.33, 0, 180, 0.62, 0.20),   // D5
                Note(739.99, 170, 180, 0.62, 0.20), // F#5
                Note(880.00, 340, 220, 0.65, 0.20), // A5
                Note(1174.66, 540, 260, 0.64, 0.18),// D6
                Note(880.00, 820, 180, 0.60, 0.20), // A5
                Note(1174.66, 1020, 360, 0.65, 0.18)// D6
            )
            ToneCatalog.BUILTIN_CALL_CRYSTAL -> listOf(
                Note(987.77, 0, 280, 0.52, 0.35),   // B5
                Note(1174.66, 220, 280, 0.54, 0.35),// D6
                Note(1479.98, 440, 380, 0.56, 0.32),// F#6
                Note(1174.66, 860, 260, 0.52, 0.34),// D6
                Note(1479.98, 1100, 440, 0.58, 0.30)// F#6
            )
            ToneCatalog.BUILTIN_CALL_PULSE -> listOf(
                Note(659.25, 0, 140, 0.64, 0.30),
                Note(659.25, 180, 140, 0.64, 0.30),
                Note(880.00, 360, 240, 0.68, 0.28),
                Note(783.99, 680, 140, 0.62, 0.28),
                Note(783.99, 860, 140, 0.62, 0.28),
                Note(1046.50, 1040, 320, 0.68, 0.26)
            )
            else -> listOf(
                Note(523.25, 0, 180, 0.58, 0.25),
                Note(659.25, 110, 260, 0.62, 0.25)
            )
        }

        val totalMs = notes.maxOfOrNull { it.startMs + it.durationMs } ?: 400
        val totalSamples = ((totalMs + 40) * SAMPLE_RATE) / 1000
        val mix = DoubleArray(totalSamples)

        for (note in notes) {
            val startSample = (note.startMs * SAMPLE_RATE) / 1000
            val lengthSamples = (note.durationMs * SAMPLE_RATE) / 1000
            var phase = 0.0
            for (i in 0 until lengthSamples) {
                val idx = startSample + i
                if (idx >= totalSamples) break
                val progress = i.toDouble() / lengthSamples.coerceAtLeast(1)
                val freq = if (note.glideToHz != null) {
                    note.freqHz + (note.glideToHz - note.freqHz) * progress
                } else {
                    note.freqHz
                }
                phase += (2.0 * PI * freq) / SAMPLE_RATE

                // Smooth attack & natural exponential decay envelope (eliminates audio clicks)
                val attack = (i.toDouble() / (SAMPLE_RATE * 0.012)).coerceIn(0.0, 1.0)
                val release = ((lengthSamples - i).toDouble() / (SAMPLE_RATE * 0.035)).coerceIn(0.0, 1.0)
                val decay = exp(-2.6 * progress)
                val env = attack * release * decay

                val fundamental = sin(phase)
                val overtone = sin(phase * 2.0) * note.harmonic
                mix[idx] += (fundamental + overtone) * note.volume * env
            }
        }

        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val clamped = mix[i].coerceIn(-0.92, 0.92)
            pcm[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return pcm
    }

    companion object {
        private const val TAG = "NotificationTonePlayer"
        private const val SAMPLE_RATE = 44_100
    }
}
