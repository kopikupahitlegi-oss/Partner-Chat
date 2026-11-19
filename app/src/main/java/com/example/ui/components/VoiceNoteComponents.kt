package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChatMessage
import com.example.data.MediaHelper
import com.example.data.VoicePlaybackUiState
import com.example.ui.theme.CoralCallEnd
import com.example.ui.theme.EmeraldPrimary
import kotlin.math.abs

@Composable
fun VoiceRecordingBar(
    elapsedMs: Long,
    amplitudes: List<Float>,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "voice_rec_pulse")
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("voice_recording_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cancel_voice_recording_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.voice_record_cancel_desc),
                        tint = CoralCallEnd
                    )
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CoralCallEnd.copy(alpha = dotAlpha))
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = MediaHelper.formatDurationMs(elapsedMs),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("voice_recording_timer")
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Live audio waveform bars driven by MediaRecorder amplitude
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .padding(end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayBars = amplitudes.takeLast(22)
                    val paddingBars = (22 - displayBars.size).coerceAtLeast(0)
                    repeat(paddingBars) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
                        )
                    }
                    displayBars.forEach { amp ->
                        val heightDp = (4f + (amp.coerceIn(0f, 1f) * 24f)).dp
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(heightDp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(EmeraldPrimary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            onClick = onSend,
            shape = CircleShape,
            color = EmeraldPrimary,
            shadowElevation = 3.dp,
            modifier = Modifier
                .size(52.dp)
                .testTag("send_voice_recording_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.voice_record_send_desc),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun VoiceMessageBubbleContent(
    message: ChatMessage,
    isOutgoing: Boolean,
    textColor: Color,
    playbackState: VoicePlaybackUiState,
    onTogglePlay: () -> Unit,
    onSeekFraction: (Float) -> Unit
) {
    val isThisActive = playbackState.activeMessageId == message.id
    val isPlaying = isThisActive && playbackState.isPlaying
    val isBuffering = isThisActive && playbackState.isBuffering
    val totalDurationMs = if (isThisActive && playbackState.durationMs > 0L) {
        playbackState.durationMs
    } else {
        (message.durationMs ?: 0L).coerceAtLeast(1_000L)
    }
    val currentPosMs = if (isThisActive) playbackState.positionMs.coerceAtMost(totalDurationMs) else 0L
    val progress = if (totalDurationMs > 0L) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val waveformHeights = remember(message.id, message.durationMs) {
        val seed = abs((message.id.ifBlank { message.fileName ?: "voice" }).hashCode())
        List(24) { idx ->
            val v = abs((seed * 31 + idx * 17 + (idx * idx * 7)) % 100)
            0.22f + (v / 100f) * 0.78f
        }
    }

    val accentColor = if (isOutgoing) EmeraldPrimary else MaterialTheme.colorScheme.primary
    val inactiveBarColor = textColor.copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .width(236.dp)
            .padding(vertical = 2.dp)
            .testTag("voice_message_player_${message.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = onTogglePlay,
                shape = CircleShape,
                color = accentColor,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("voice_play_button_${message.id}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.voice_pause_desc else R.string.voice_play_desc
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(message.id, isThisActive) {
                        detectTapGestures { offset ->
                            if (size.width > 0) {
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                if (isThisActive) {
                                    onSeekFraction(fraction)
                                } else {
                                    onTogglePlay()
                                }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                waveformHeights.forEachIndexed { index, ratio ->
                    val barFraction = (index + 1).toFloat() / waveformHeights.size.toFloat()
                    val isPlayed = isThisActive && barFraction <= progress
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((6f + ratio * 20f).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isPlayed) accentColor else inactiveBarColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isThisActive && (isPlaying || currentPosMs > 0L)) {
                            "${MediaHelper.formatDurationMs(currentPosMs)} / ${MediaHelper.formatDurationMs(totalDurationMs)}"
                        } else {
                            MediaHelper.formatDurationMs(totalDurationMs)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Medium
                    )
                }
                if (message.fileSizeBytes != null && message.fileSizeBytes > 0L) {
                    Text(
                        text = MediaHelper.formatFileSize(message.fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.56f)
                    )
                }
            }
        }
    }
}
