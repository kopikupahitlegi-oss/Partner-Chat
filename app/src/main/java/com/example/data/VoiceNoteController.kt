package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class RecordedVoiceClip(
    val file: File,
    val durationMs: Long
)

data class VoicePlaybackUiState(
    val activeMessageId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun startRecording(): Result<File> = runCatching {
        cancelRecording()
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(mediaDir, "voice_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(22_050)
                setAudioEncodingBitRate(32_000)
                setMaxDuration(MAX_RECORDING_DURATION_MS.toInt())
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            file
        } catch (e: Exception) {
            runCatching { mediaRecorder.reset() }
            runCatching { mediaRecorder.release() }
            runCatching { file.delete() }
            recorder = null
            outputFile = null
            throw e
        }
    }

    fun pollNormalizedAmplitude(): Float {
        val current = recorder ?: return 0f
        return try {
            val raw = current.maxAmplitude.coerceIn(0, 32_767)
            (raw / 14_000f).coerceIn(0.08f, 1f)
        } catch (_: Exception) {
            0.12f
        }
    }

    fun elapsedMs(): Long {
        if (recorder == null || startedAtMs <= 0L) return 0L
        return (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
    }

    fun stopRecording(): Result<RecordedVoiceClip> {
        val current = recorder ?: return Result.failure(IllegalStateException("Tidak ada rekaman aktif."))
        val file = outputFile
        val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
        recorder = null
        outputFile = null
        startedAtMs = 0L

        return try {
            current.stop()
            current.reset()
            current.release()
            if (file == null || !file.exists() || file.length() <= 0L) {
                Result.failure(IllegalStateException("Rekaman audio kosong."))
            } else if (elapsed < MIN_RECORDING_DURATION_MS) {
                runCatching { file.delete() }
                Result.failure(IllegalStateException("Rekaman terlalu singkat. Rekam minimal 1 detik."))
            } else {
                Result.success(RecordedVoiceClip(file = file, durationMs = elapsed.coerceAtMost(MAX_RECORDING_DURATION_MS)))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stop recording failed: ${e.message}")
            runCatching { current.reset() }
            runCatching { current.release() }
            runCatching { file?.delete() }
            Result.failure(IllegalStateException("Rekaman suara terlalu singkat atau gagal disimpan."))
        }
    }

    fun cancelRecording() {
        val current = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        startedAtMs = 0L
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
        if (file != null && file.exists()) {
            runCatching { file.delete() }
        }
    }

    companion object {
        private const val TAG = "VoiceNoteRecorder"
        const val MIN_RECORDING_DURATION_MS = 600L
        const val MAX_RECORDING_DURATION_MS = 60_000L
    }
}

class VoiceNotePlayer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPlaybackError: (String) -> Unit = {}
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(VoicePlaybackUiState())
    val playbackState: StateFlow<VoicePlaybackUiState> = _playbackState.asStateFlow()

    fun togglePlayback(message: ChatMessage) {
        val current = _playbackState.value
        if (current.activeMessageId == message.id && mediaPlayer != null) {
            val player = mediaPlayer ?: return
            if (current.isBuffering) return
            if (current.isPlaying) {
                runCatching { player.pause() }
                progressJob?.cancel()
                _playbackState.value = current.copy(
                    isPlaying = false,
                    positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(current.positionMs)
                )
            } else {
                runCatching { player.start() }
                _playbackState.value = current.copy(isPlaying = true)
                startProgressTicker(message.id)
            }
            return
        }

        stopAndReset()
        val fallbackDuration = (message.durationMs ?: 0L).coerceAtLeast(0L)
        _playbackState.value = VoicePlaybackUiState(
            activeMessageId = message.id,
            isPlaying = false,
            isBuffering = true,
            positionMs = 0L,
            durationMs = fallbackDuration
        )

        scope.launch {
            val localFile = MediaHelper.resolveLocalOrSyncedFile(context, message)
            val remoteUrl = message.mediaUrl?.takeIf { it.isNotBlank() }

            if (localFile == null && remoteUrl == null) {
                stopAndReset()
                onPlaybackError("Audio tidak tersedia di perangkat ini.")
                return@launch
            }

            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setOnCompletionListener { mp ->
                        progressJob?.cancel()
                        val dur = runCatching { mp.duration.toLong() }.getOrDefault(fallbackDuration)
                        _playbackState.value = VoicePlaybackUiState(
                            activeMessageId = message.id,
                            isPlaying = false,
                            isBuffering = false,
                            positionMs = 0L,
                            durationMs = dur.coerceAtLeast(fallbackDuration)
                        )
                        runCatching { mp.seekTo(0) }
                    }
                    setOnErrorListener { _, _, _ ->
                        stopAndReset()
                        onPlaybackError("Gagal memutar pesan suara.")
                        true
                    }
                }

                if (localFile != null) {
                    withContext(Dispatchers.IO) {
                        player.setDataSource(localFile.absolutePath)
                        player.prepare()
                    }
                    if (_playbackState.value.activeMessageId != message.id) {
                        runCatching { player.release() }
                        return@launch
                    }
                    mediaPlayer = player
                    val actualDur = runCatching { player.duration.toLong() }.getOrDefault(fallbackDuration)
                        .coerceAtLeast(fallbackDuration)
                    player.start()
                    _playbackState.value = VoicePlaybackUiState(
                        activeMessageId = message.id,
                        isPlaying = true,
                        isBuffering = false,
                        positionMs = 0L,
                        durationMs = actualDur
                    )
                    startProgressTicker(message.id)
                } else if (remoteUrl != null) {
                    mediaPlayer = player
                    player.setDataSource(remoteUrl)
                    player.setOnPreparedListener { prepared ->
                        if (_playbackState.value.activeMessageId != message.id) {
                            runCatching { prepared.release() }
                            return@setOnPreparedListener
                        }
                        val actualDur = runCatching { prepared.duration.toLong() }.getOrDefault(fallbackDuration)
                            .coerceAtLeast(fallbackDuration)
                        prepared.start()
                        _playbackState.value = VoicePlaybackUiState(
                            activeMessageId = message.id,
                            isPlaying = true,
                            isBuffering = false,
                            positionMs = 0L,
                            durationMs = actualDur
                        )
                        startProgressTicker(message.id)
                    }
                    player.prepareAsync()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Playback error: ${e.message}")
                stopAndReset()
                onPlaybackError("Gagal memutar pesan suara.")
            }
        }
    }

    fun seekToFraction(messageId: String, fraction: Float) {
        val current = _playbackState.value
        val player = mediaPlayer ?: return
        if (current.activeMessageId != messageId || current.isBuffering || current.durationMs <= 0L) return
        val targetMs = (current.durationMs * fraction.coerceIn(0f, 1f)).toInt()
        runCatching {
            player.seekTo(targetMs)
            _playbackState.value = current.copy(positionMs = targetMs.toLong())
        }
    }

    fun stopAndReset() {
        progressJob?.cancel()
        progressJob = null
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        _playbackState.value = VoicePlaybackUiState()
    }

    private fun startProgressTicker(messageId: String) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer ?: break
                val state = _playbackState.value
                if (state.activeMessageId != messageId || !state.isPlaying) break
                val pos = runCatching { player.currentPosition.toLong() }.getOrDefault(state.positionMs)
                _playbackState.value = state.copy(positionMs = pos.coerceAtLeast(0L))
                delay(120L)
            }
        }
    }

    companion object {
        private const val TAG = "VoiceNotePlayer"
    }
}
