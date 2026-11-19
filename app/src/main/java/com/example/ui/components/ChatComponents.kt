package com.example.ui.components

import android.graphics.Bitmap
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.MediaHelper
import com.example.data.MessageReceiptStatus
import com.example.data.MessageType
import com.example.data.VoicePlaybackUiState
import com.example.ui.theme.AttachCameraColor
import com.example.ui.theme.AttachDocumentColor
import com.example.ui.theme.AttachPhotoColor
import com.example.ui.theme.AttachVideoColor
import com.example.ui.theme.LocalChatPalette
import com.google.firebase.Timestamp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val AvatarColors = listOf(
    Color(0xFF008069),
    Color(0xFF1E88E5),
    Color(0xFF8E24AA),
    Color(0xFFE65100),
    Color(0xFF00838F),
    Color(0xFFC2185B)
)

@Composable
fun UserAvatar(
    name: String,
    photoUrl: String?,
    photoData: String? = null,
    size: Dp = 48.dp,
    isGroup: Boolean = false,
    isSelf: Boolean = false,
    showOnlineDot: Boolean = false,
    modifier: Modifier = Modifier
) {
    val chatPalette = LocalChatPalette.current
    val bgColor = remember(name) {
        AvatarColors[abs(name.hashCode()) % AvatarColors.size]
    }
    val initials = remember(name) {
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "P" }
    }
    val decodedAvatarBitmap = remember(photoData) {
        MediaHelper.decodeBase64ToBitmap(photoData)
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            when {
                isGroup -> Icon(
                    imageVector = Icons.Rounded.Group,
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.54f)
                )
                isSelf -> Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.52f)
                )
                decodedAvatarBitmap != null -> Image(
                    bitmap = decodedAvatarBitmap.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                !photoUrl.isNullOrBlank() -> AsyncImage(
                    model = photoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> Text(
                    text = initials,
                    color = Color.White,
                    style = if (size >= 48.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(chatPalette.onlineDot)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun ChatWallpaperBackground(
    modifier: Modifier = Modifier
) {
    val chatPalette = LocalChatPalette.current
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(chatPalette.chatCanvas)
    ) {
        val step = 64.dp.toPx()
        val stroke = 1.2.dp.toPx()
        var row = 0
        var y = step / 2f
        while (y < size.height + step) {
            var x = if (row % 2 == 0) step / 2f else step
            while (x < size.width + step) {
                drawCircle(
                    color = chatPalette.chatPattern.copy(alpha = 0.45f),
                    radius = 7.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = stroke)
                )
                drawCircle(
                    color = chatPalette.chatPattern.copy(alpha = 0.25f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(x + step * 0.45f, y + step * 0.25f)
                )
                x += step
            }
            y += step * 0.85f
            row++
        }
    }
}

fun formatTimeShort(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(date)
}

fun formatRelativeDate(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return ""
    val now = Date()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).let { fmt ->
        fmt.format(date) == fmt.format(now)
    }
    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("dd MMM", Locale("id", "ID")).format(date)
    }
}

@Composable
fun MessageReceiptIndicator(
    status: MessageReceiptStatus,
    messageId: String = "",
    mutedTint: Color,
    iconSize: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val chatPalette = LocalChatPalette.current
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(initialScale = 0.82f, animationSpec = tween(180)))
                .togetherWith(fadeOut(tween(120)))
        },
        label = "receipt_status_transition",
        modifier = modifier
    ) { targetStatus ->
        val (icon, tint, descRes) = when (targetStatus) {
            MessageReceiptStatus.SENDING -> Triple(
                Icons.Rounded.Schedule,
                mutedTint,
                R.string.receipt_sending
            )
            MessageReceiptStatus.SENT -> Triple(
                Icons.Rounded.Done,
                mutedTint,
                R.string.receipt_sent
            )
            MessageReceiptStatus.DELIVERED -> Triple(
                Icons.Rounded.DoneAll,
                mutedTint,
                R.string.receipt_delivered
            )
            MessageReceiptStatus.READ -> Triple(
                Icons.Rounded.DoneAll,
                chatPalette.readTick,
                R.string.receipt_read
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = stringResource(descRes),
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .testTag("receipt_${targetStatus.name.lowercase()}_$messageId")
        )
    }
}

@Composable
fun TypingIndicatorBubble(
    label: String,
    darkSurface: Boolean = false,
    modifier: Modifier = Modifier
) {
    val chatPalette = LocalChatPalette.current
    val transition = rememberInfiniteTransition(label = "typing_dots_transition")
    val dotAlphas = listOf(0, 180, 360).map { delayMs ->
        transition.animateFloat(
            initialValue = 0.28f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 550),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(delayMs)
            ),
            label = "typing_dot_$delayMs"
        )
    }

    val bubbleColor = if (darkSurface) Color(0xFF1A2B35) else chatPalette.incomingBubble
    val dotColor = if (darkSurface) Color(0xFF25D366) else MaterialTheme.colorScheme.primary
    val textColor = if (darkSurface) Color.White.copy(alpha = 0.85f) else chatPalette.incomingText.copy(alpha = 0.78f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = bubbleColor,
            shadowElevation = if (darkSurface) 0.dp else 1.dp,
            modifier = Modifier.testTag("typing_indicator_bubble")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dotAlphas.forEach { alphaState ->
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor.copy(alpha = alphaState.value))
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isOutgoing: Boolean,
    showSenderName: Boolean,
    receiptStatus: MessageReceiptStatus,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onReply: () -> Unit,
    onForward: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onShowInfo: (() -> Unit)? = null,
    onImageClick: (imageUrl: String?, bitmap: Bitmap?) -> Unit,
    onVideoClick: (ChatMessage) -> Unit,
    onFileClick: (ChatMessage) -> Unit,
    voicePlaybackState: VoicePlaybackUiState = VoicePlaybackUiState(),
    onVoicePlayToggle: (ChatMessage) -> Unit = {},
    onVoiceSeek: (messageId: String, fraction: Float) -> Unit = { _, _ -> }
) {
    val chatPalette = LocalChatPalette.current
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val bubbleColor = if (isOutgoing) chatPalette.outgoingBubble else chatPalette.incomingBubble
    val textColor = if (isOutgoing) chatPalette.outgoingText else chatPalette.incomingText
    val bubbleShape = RoundedCornerShape(
        topStart = if (isOutgoing) 18.dp else 4.dp,
        topEnd = if (isOutgoing) 4.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )

    val decodedMediaBitmap = remember(message.mediaData, message.thumbnailData, message.parsedType) {
        when (message.parsedType) {
            MessageType.IMAGE -> MediaHelper.decodeBase64ToBitmap(message.mediaData)
            MessageType.VIDEO -> MediaHelper.decodeBase64ToBitmap(message.thumbnailData)
            else -> null
        }
    }

    if (message.parsedType == MessageType.CALL && !message.deleted) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else Color.Transparent
                )
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onSelectToggle?.invoke()
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectToggle?.invoke()
                    }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("message_bubble_${message.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                SelectionCheckIndicator(
                    selected = isSelected,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (message.text.contains("Video", ignoreCase = true)) {
                                Icons.Rounded.Videocam
                            } else {
                                Icons.Rounded.Call
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${message.text} • ${formatTimeShort(message.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else Color.Transparent
            )
            .then(
                if (isSelectionMode) {
                    Modifier.clickable { onSelectToggle?.invoke() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            SelectionCheckIndicator(
                selected = isSelected,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                shadowElevation = if (isSelected) 4.dp else 1.dp,
                modifier = Modifier
                    .widthIn(min = 96.dp, max = 300.dp)
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                onSelectToggle?.invoke()
                            } else if (!message.deleted) {
                                when (message.parsedType) {
                                    MessageType.IMAGE -> {
                                        if (!message.mediaUrl.isNullOrBlank() || decodedMediaBitmap != null) {
                                            onImageClick(message.mediaUrl, decodedMediaBitmap)
                                        }
                                    }
                                    MessageType.VIDEO -> onVideoClick(message)
                                    MessageType.AUDIO -> onVoicePlayToggle(message)
                                    MessageType.FILE -> onFileClick(message)
                                    else -> Unit
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isSelectionMode) {
                                onSelectToggle?.invoke()
                            } else if (message.deleted) {
                                onSelectToggle?.invoke()
                            } else if (onLongPress != null) {
                                onLongPress()
                            } else {
                                showMenu = true
                            }
                        }
                    )
                    .testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    if (showSenderName && !isOutgoing) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    if (!message.deleted && message.forwarded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Shortcut,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.62f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.forwarded_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = textColor.copy(alpha = 0.65f)
                            )
                        }
                    }

                    if (!message.deleted && !message.replyToPreview.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(modifier = Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = message.replyToSenderName ?: "Pesan",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = message.replyToPreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor.copy(alpha = 0.78f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (message.deleted) {
                        Text(
                            text = stringResource(R.string.message_deleted_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.58f)
                        )
                    } else {
                        when (message.parsedType) {
                            MessageType.IMAGE -> {
                                when {
                                    !message.mediaUrl.isNullOrBlank() -> {
                                        AsyncImage(
                                            model = message.mediaUrl,
                                            contentDescription = message.fileName ?: "Foto",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(210.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    }
                                    decodedMediaBitmap != null -> {
                                        Image(
                                            bitmap = decodedMediaBitmap.asImageBitmap(),
                                            contentDescription = message.fileName ?: "Foto",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(210.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    }
                                    else -> {
                                        MediaPlaceholderBox(
                                            icon = Icons.Rounded.Image,
                                            label = message.fileName ?: "Foto"
                                        )
                                    }
                                }
                                if (message.text.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = textColor
                                    )
                                }
                            }

                            MessageType.VIDEO -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(195.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF101D24)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (decodedMediaBitmap != null) {
                                        Image(
                                            bitmap = decodedMediaBitmap.asImageBitmap(),
                                            contentDescription = message.fileName ?: "Video",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.62f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Putar video",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Videocam,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${MediaHelper.formatDurationMs(message.durationMs)} • ${MediaHelper.formatFileSize(message.fileSizeBytes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                if (message.text.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = textColor
                                    )
                                }
                            }

                            MessageType.AUDIO -> {
                                VoiceMessageBubbleContent(
                                    message = message,
                                    isOutgoing = isOutgoing,
                                    textColor = textColor,
                                    playbackState = voicePlaybackState,
                                    onTogglePlay = { onVoicePlayToggle(message) },
                                    onSeekFraction = { fraction -> onVoiceSeek(message.id, fraction) }
                                )
                            }

                            MessageType.FILE -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(AttachDocumentColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Description,
                                                contentDescription = "Dokumen",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = message.fileName ?: "Dokumen",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = textColor,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${MediaHelper.formatFileSize(message.fileSizeBytes)} • Ketuk untuk buka",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textColor.copy(alpha = 0.68f)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = stringResource(R.string.open_file_action),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            else -> {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = textColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .then(
                                if (isOutgoing && !message.deleted && !isSelectionMode && onShowInfo != null) {
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onShowInfo() }
                                        .padding(horizontal = 2.dp, vertical = 1.dp)
                                } else {
                                    Modifier
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimeShort(message.sentAt ?: message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.6f)
                        )
                        if (isOutgoing && !message.deleted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            MessageReceiptIndicator(
                                status = receiptStatus,
                                messageId = message.id,
                                mutedTint = textColor.copy(alpha = 0.58f),
                                iconSize = 16.dp
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (onForward != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.forward_action)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Shortcut, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onForward()
                        }
                    )
                }
                if (onCopy != null && message.copyableText() != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_action)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onCopy()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reply_action)) },
                    leadingIcon = { Icon(Icons.Rounded.Reply, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onReply()
                    }
                )
                if (isOutgoing && onShowInfo != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.message_info_action)) },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShowInfo()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun MessageReceiptInfoDialog(
    message: ChatMessage,
    chat: ChatRoom,
    currentUserId: String,
    onDismiss: () -> Unit
) {
    val isSelf = chat.isSelfChat(currentUserId)
    val receiptStatus = message.resolveReceiptStatus(isSelfChat = isSelf, isGroupChat = chat.groupChat)
    val peerUids = message.participantUids.filter { it != message.senderId }
    val readerUids = message.readByUids.filter { it != message.senderId }.distinct()
    val deliveredUids = (message.deliveredToUids + message.readByUids)
        .filter { it != message.senderId }
        .distinct()

    val readerNames = readerUids.map { uid -> chat.participantNames[uid]?.takeIf { it.isNotBlank() } ?: "Penerima" }
    val deliveredNames = deliveredUids.map { uid -> chat.participantNames[uid]?.takeIf { it.isNotBlank() } ?: "Penerima" }

    val previewText = when (message.parsedType) {
        MessageType.IMAGE -> message.text.ifBlank { "📷 Foto" }
        MessageType.VIDEO -> message.text.ifBlank { "🎥 Video" }
        MessageType.AUDIO -> "🎤 Pesan suara (${MediaHelper.formatDurationMs(message.durationMs)})"
        MessageType.FILE -> "📎 ${message.fileName ?: "Dokumen"}"
        else -> message.text
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.message_info_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Sent row (Firestore sentAt / createdAt server timestamp)
                val sentTimestamp = message.sentAt ?: message.createdAt
                ReceiptTimelineRow(
                    status = MessageReceiptStatus.SENT,
                    title = stringResource(R.string.receipt_sent),
                    subtitle = formatDateTimeFull(sentTimestamp),
                    isCompleted = receiptStatus != MessageReceiptStatus.SENDING
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                // Delivered row (Firestore deliveredAt server timestamp)
                val deliveredCompleted = isSelf || message.deliveredAt != null || deliveredUids.isNotEmpty()
                val deliveredSubtitle = when {
                    isSelf -> formatDateTimeFull(sentTimestamp)
                    deliveredCompleted -> {
                        val timeText = formatDateTimeFull(message.deliveredAt ?: message.readAt ?: message.updatedAt)
                        if (chat.groupChat && deliveredNames.isNotEmpty()) {
                            "${deliveredUids.size}/${peerUids.size} anggota (${deliveredNames.joinToString(", ")}) • $timeText"
                        } else {
                            "${deliveredNames.firstOrNull() ?: "Penerima"} • $timeText"
                        }
                    }
                    else -> stringResource(R.string.message_info_waiting)
                }
                ReceiptTimelineRow(
                    status = MessageReceiptStatus.DELIVERED,
                    title = stringResource(R.string.receipt_delivered),
                    subtitle = deliveredSubtitle,
                    isCompleted = deliveredCompleted
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                // Read row (Firestore readAt server timestamp)
                val readCompleted = isSelf ||
                    (if (chat.groupChat) peerUids.isNotEmpty() && peerUids.all { it in readerUids } else message.readAt != null || readerUids.isNotEmpty())
                val readSubtitle = when {
                    isSelf -> formatDateTimeFull(sentTimestamp)
                    message.readAt != null || readerUids.isNotEmpty() -> {
                        val timeText = formatDateTimeFull(message.readAt ?: message.updatedAt)
                        if (chat.groupChat && readerNames.isNotEmpty()) {
                            "${readerUids.size}/${peerUids.size} anggota (${readerNames.joinToString(", ")}) • $timeText"
                        } else {
                            "${readerNames.firstOrNull() ?: "Penerima"} • $timeText"
                        }
                    }
                    else -> stringResource(R.string.message_info_not_read)
                }
                ReceiptTimelineRow(
                    status = MessageReceiptStatus.READ,
                    title = stringResource(R.string.receipt_read),
                    subtitle = readSubtitle,
                    isCompleted = readCompleted
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_message_info_button")
            ) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun ReceiptTimelineRow(
    status: MessageReceiptStatus,
    title: String,
    subtitle: String,
    isCompleted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MessageReceiptIndicator(
            status = status,
            mutedTint = if (isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            iconSize = 20.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDateTimeFull(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "Baru saja"
    return SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID")).format(date)
}

@Composable
private fun MediaPlaceholderBox(icon: ImageVector, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onPickCamera: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickDocument: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.attach_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentOptionItem(
                    icon = Icons.Rounded.CameraAlt,
                    label = stringResource(R.string.attach_camera),
                    color = AttachCameraColor,
                    testTag = "attach_camera_button",
                    onClick = {
                        onDismiss()
                        onPickCamera()
                    }
                )
                AttachmentOptionItem(
                    icon = Icons.Rounded.Image,
                    label = stringResource(R.string.attach_gallery),
                    color = AttachPhotoColor,
                    testTag = "attach_image_button",
                    onClick = {
                        onDismiss()
                        onPickImage()
                    }
                )
                AttachmentOptionItem(
                    icon = Icons.Rounded.Videocam,
                    label = stringResource(R.string.attach_video),
                    color = AttachVideoColor,
                    testTag = "attach_video_button",
                    onClick = {
                        onDismiss()
                        onPickVideo()
                    }
                )
                AttachmentOptionItem(
                    icon = Icons.Rounded.Description,
                    label = stringResource(R.string.attach_document),
                    color = AttachDocumentColor,
                    testTag = "attach_document_button",
                    onClick = {
                        onDismiss()
                        onPickDocument()
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachmentOptionItem(
    icon: ImageVector,
    label: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FullscreenImageViewerDialog(
    imageUrl: String? = null,
    bitmap: Bitmap? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                !imageUrl.isNullOrBlank() -> AsyncImage(
                    model = imageUrl,
                    contentDescription = "Pratinjau gambar",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Pratinjau gambar",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun VideoPlayerDialog(
    videoFile: File,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
            DisposableEffect(Unit) {
                onDispose { videoViewRef?.stopPlayback() }
            }

            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoPath(videoFile.absolutePath)
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            start()
                        }
                        videoViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .align(Alignment.Center)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup pemutar video",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
