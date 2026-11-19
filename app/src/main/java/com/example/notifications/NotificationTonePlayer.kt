package com.example.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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
import kotlinx.coroutines.launch

/**
 * Reliable audio & vibration engine backed by MediaPlayer (for bundled res/raw WAV tones
 * and system RingtoneManager URIs) with transient AudioFocus so incoming call ringtones
 * and chat tones play clearly even while other apps are open.
 */
class NotificationTonePlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val lock = Any()

    private var previewPlayer: MediaPlayer? = null
    private var previewRingtone: Ringtone? = null
    private var previewJob: Job? = null

    private var chatPlayer: MediaPlayer? = null
    private var chatRingtone: Ringtone? = null
    private var chatJob: Job? = null

    private var callPlayer: MediaPlayer? = null
    private var callRingtone: Ringtone? = null
    private var callJob: Job? = null
    private var callFocusRequest: AudioFocusRequest? = null

    fun playChatNotificationTone(toneId: String, vibrate: Boolean) {
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

        if (vibrate) {
            triggerVibration(longArrayOf(0L, 140L, 80L, 140L), repeat = -1)
        }
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE || ToneCatalog.isSilent(toneId)) return

        stopChatTone()
        val attrs = buildAudioAttributes(isCall = false)

        chatJob = scope.launch {
            val started = startMediaPlayer(
                toneId = toneId,
                category = ToneCategory.CHAT,
                attrs = attrs,
                looping = false,
                onPlayerReady = { player ->
                    synchronized(lock) { chatPlayer = player }
                },
                onComplete = { stopChatTone() }
            )
            if (!started) {
                playRingtoneFallback(
                    toneId = toneId,
                    category = ToneCategory.CHAT,
                    attrs = attrs,
                    looping = false,
                    onRingtoneReady = { rt -> synchronized(lock) { chatRingtone = rt } }
                )
            }
        }
    }

    fun startIncomingCallRingtone(toneId: String, vibrate: Boolean) {
        stopIncomingCallRingtone()
        stopPreview()

        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

        if (vibrate) {
            triggerVibration(longArrayOf(0L, 450L, 350L, 450L, 900L), repeat = 0)
        }
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE || ToneCatalog.isSilent(toneId)) return

        val attrs = buildAudioAttributes(isCall = true)
        requestCallAudioFocus(attrs)

        callJob = scope.launch {
            val started = startMediaPlayer(
                toneId = toneId,
                category = ToneCategory.CALL,
                attrs = attrs,
                looping = true,
                onPlayerReady = { player ->
                    synchronized(lock) { callPlayer = player }
                },
                onComplete = {}
            )
            if (!started) {
                playRingtoneFallback(
                    toneId = toneId,
                    category = ToneCategory.CALL,
                    attrs = attrs,
                    looping = true,
                    onRingtoneReady = { rt -> synchronized(lock) { callRingtone = rt } }
                )
            }
        }
    }

    fun stopIncomingCallRingtone() {
        synchronized(lock) {
            callJob?.cancel()
            callJob = null
            releaseMediaPlayer(callPlayer)
            callPlayer = null
            runCatching { callRingtone?.stop() }
            callRingtone = null
        }
        abandonCallAudioFocus()
        cancelVibration()
    }

    fun previewTone(toneId: String, category: ToneCategory, onFinished: () -> Unit = {}) {
        stopPreview()
        if (ToneCatalog.isSilent(toneId)) {
            onFinished()
            return
        }

        // Preview always uses USAGE_MEDIA so users can hear their selection clearly even if ring volume is low
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        previewJob = scope.launch {
            val started = startMediaPlayer(
                toneId = toneId,
                category = category,
                attrs = attrs,
                looping = false,
                onPlayerReady = { player ->
                    synchronized(lock) { previewPlayer = player }
                },
                onComplete = {
                    stopPreview()
                    onFinished()
                }
            )
            if (!started) {
                playRingtoneFallback(
                    toneId = toneId,
                    category = category,
                    attrs = attrs,
                    looping = false,
                    onRingtoneReady = { rt -> synchronized(lock) { previewRingtone = rt } }
                )
                delay(if (category == ToneCategory.CALL) 3_400L else 2_000L)
                stopPreview()
                onFinished()
            }
        }
    }

    fun stopPreview() {
        synchronized(lock) {
            previewJob?.cancel()
            previewJob = null
            releaseMediaPlayer(previewPlayer)
            previewPlayer = null
            runCatching { previewRingtone?.stop() }
            previewRingtone = null
        }
    }

    private fun stopChatTone() {
        synchronized(lock) {
            chatJob?.cancel()
            chatJob = null
            releaseMediaPlayer(chatPlayer)
            chatPlayer = null
            runCatching { chatRingtone?.stop() }
            chatRingtone = null
        }
    }

    fun release() {
        stopIncomingCallRingtone()
        stopPreview()
        stopChatTone()
    }

    private fun buildAudioAttributes(isCall: Boolean): AudioAttributes {
        val stream = if (isCall) AudioManager.STREAM_RING else AudioManager.STREAM_NOTIFICATION
        val streamVol = runCatching { audioManager?.getStreamVolume(stream) ?: 1 }.getOrDefault(1)
        val usage = when {
            streamVol > 0 && isCall -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            streamVol > 0 && !isCall -> AudioAttributes.USAGE_NOTIFICATION
            else -> AudioAttributes.USAGE_MEDIA
        }
        return AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private fun startMediaPlayer(
        toneId: String,
        category: ToneCategory,
        attrs: AudioAttributes,
        looping: Boolean,
        onPlayerReady: (MediaPlayer) -> Unit,
        onComplete: () -> Unit
    ): Boolean {
        return runCatching {
            val player = MediaPlayer()
            player.setAudioAttributes(attrs)
            val rawResId = ToneCatalog.resolveRawResId(toneId)
            if (rawResId != null) {
                val afd = context.resources.openRawResourceFd(rawResId)
                afd.use { fd ->
                    player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
            } else {
                val uri = ToneCatalog.resolveSystemUri(toneId, category)
                    ?: run {
                        player.release()
                        return false
                    }
                player.setDataSource(context, uri)
            }
            player.isLooping = looping
            player.setVolume(1.0f, 1.0f)
            if (!looping) {
                player.setOnCompletionListener {
                    onComplete()
                }
            }
            player.setOnErrorListener { mp, _, _ ->
                releaseMediaPlayer(mp)
                onComplete()
                true
            }
            player.prepare()
            onPlayerReady(player)
            player.start()
            true
        }.onFailure { err ->
            Log.w(TAG, "MediaPlayer failed for tone $toneId: ${err.message}")
        }.getOrDefault(false)
    }

    private suspend fun playRingtoneFallback(
        toneId: String,
        category: ToneCategory,
        attrs: AudioAttributes,
        looping: Boolean,
        onRingtoneReady: (Ringtone) -> Unit
    ) {
        val uri = ToneCatalog.resolvePlayableUri(context, toneId, category) ?: return
        runCatching {
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.audioAttributes = attrs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = looping
            }
            onRingtoneReady(ringtone)
            ringtone.play()
            if (looping && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                while (true) {
                    delay(1_500L)
                    if (!ringtone.isPlaying) {
                        ringtone.play()
                    }
                }
            } else if (!looping) {
                delay(2_400L)
                if (ringtone.isPlaying) ringtone.stop()
            }
        }.onFailure { err ->
            Log.w(TAG, "Ringtone fallback failed for tone $toneId: ${err.message}")
        }
    }

    private fun requestCallAudioFocus(attrs: AudioAttributes) {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                callFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_RING,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        }
    }

    private fun abandonCallAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                callFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                callFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }

    private fun releaseMediaPlayer(player: MediaPlayer?) {
        if (player == null) return
        runCatching {
            if (player.isPlaying) {
                player.stop()
            }
        }
        runCatching { player.release() }
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
        runCatching { getVibrator()?.cancel() }
    }

    companion object {
        private const val TAG = "NotificationTonePlayer"
    }
}
