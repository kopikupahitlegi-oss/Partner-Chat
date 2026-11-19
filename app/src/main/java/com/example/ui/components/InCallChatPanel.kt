package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ChatMessage
import com.example.data.MediaHelper
import com.example.data.MessageReceiptStatus
import com.example.data.MessageType
import com.example.data.UiState
import com.example.ui.theme.AttachDocumentColor
import com.example.ui.theme.AttachPhotoColor
import com.example.ui.theme.AttachVideoColor
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalChatPalette
import com.example.ui.theme.MintVibrant

private val CallPanelSurface = Color(0xFF0D171D)
private val CallPanelHeader = Color(0xFF122129)
private val CallOutgoingBubble = Color(0xFF0B6E5B)
private val CallIncomingBubble = Color(0xFF1A2B35)
private val CallComposerSurface = Color(0xFF182832)

@Composable
fun InCallSplitChatPanel(
    peerName: String,
    isGroupChat: Boolean,
    isSelfChat: Boolean,
    currentUserId: String,
    messagesState: UiState<List<ChatMessage>>,
    isSendingMedia: Boolean,
    replyingTo: ChatMessage?,
    draftText: String,
    isSideBySide: Boolean,
    onDraftChange: (String) -> Unit,
    onSendText: () -> Unit,
    onAttachClick: () -> Unit,
    onReply: (ChatMessage) -> Unit,
    onCancelReply: () -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onImageClick: (imageUrl: String?, bitmap: Bitmap?) -> Unit,
    onVideoClick: (ChatMessage) -> Unit,
    onFileClick: (ChatMessage) -> Unit,
    onSwitchToOverlay: () -> Unit,
    onSwitchToFullscreen: () -> Unit,
    typingSummary: String? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val panelShape = if (isSideBySide) {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    }

    Surface(
        shape = panelShape,
        color = CallPanelSurface,
        modifier = modifier.border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.08f),
            shape = panelShape
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact In-Call Chat Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CallPanelHeader)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (!isSideBySide) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(MintVibrant)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.call_chat_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = typingSummary ?: "$peerName • ${messages.size} pesan",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (typingSummary != null) MintVibrant else Color.White.copy(alpha = 0.68f),
                            fontWeight = if (typingSummary != null) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onSwitchToOverlay,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("call_switch_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = stringResource(R.string.call_mode_overlay),
                            tint = Color.White.copy(alpha = 0.88f)
                        )
                    }
                    IconButton(
                        onClick = onSwitchToFullscreen,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("call_switch_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = stringResource(R.string.call_mode_fullscreen),
                            tint = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            AnimatedVisibility(visible = isSendingMedia) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MintVibrant,
                    trackColor = CallPanelHeader
                )
            }

            // Messages Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = messagesState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(
                            color = MintVibrant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is UiState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB4AB),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp)
                        )
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.call_chat_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("incall_messages_list")
                            ) {
                                items(
                                    items = state.data,
                                    key = { msg -> msg.id.ifBlank { msg.hashCode().toString() } }
                                ) { msg ->
                                    val isOutgoing = msg.senderId == currentUserId
                                    val receiptStatus = msg.resolveReceiptStatus(
                                        isSelfChat = isSelfChat,
                                        isGroupChat = isGroupChat
                                    )
                                    InCallMessageBubble(
                                        message = msg,
                                        isOutgoing = isOutgoing,
                                        showSenderName = isGroupChat,
                                        receiptStatus = receiptStatus,
                                        onReply = { onReply(msg) },
                                        onDelete = { onDelete(msg) },
                                        onImageClick = onImageClick,
                                        onVideoClick = onVideoClick,
                                        onFileClick = onFileClick
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Real-time Peer Typing Indicator
            AnimatedVisibility(
                visible = typingSummary != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                typingSummary?.let { label ->
                    TypingIndicatorBubble(
                        label = label,
                        darkSurface = true
                    )
                }
            }

            // Reply Quote Banner
            AnimatedVisibility(visible = replyingTo != null) {
                val reply = replyingTo
                if (reply != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CallPanelHeader)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MintVibrant)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reply.senderName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MintVibrant,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (reply.parsedType) {
                                    MessageType.IMAGE -> reply.text.ifBlank { "Foto" }
                                    MessageType.VIDEO -> reply.text.ifBlank { "Video" }
                                    MessageType.FILE -> reply.fileName ?: "Dokumen"
                                    else -> reply.text
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onCancelReply,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cancel_reply_desc),
                                tint = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }
                }
            }

            // In-Call Message Composer
            InCallMessageComposer(
                draftText = draftText,
                hint = stringResource(R.string.call_chat_input_hint),
                onDraftChange = onDraftChange,
                onSendClick = onSendText,
                onAttachClick = onAttachClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CallPanelHeader)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun InCallOverlayChatFeed(
    messages: List<ChatMessage>,
    currentUserId: String,
    draftText: String,
    onDraftChange: (String) -> Unit,
    onSendText: () -> Unit,
    onAttachClick: () -> Unit,
    onOpenSplitView: () -> Unit,
    typingSummary: String? = null,
    modifier: Modifier = Modifier
) {
    val recentMessages = remember(messages) {
        messages.filter { !it.deleted && it.parsedType != MessageType.CALL }.takeLast(4)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (recentMessages.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("incall_overlay_messages")
            ) {
                recentMessages.forEach { msg ->
                    val isOutgoing = msg.senderId == currentUserId
                    val previewText = when (msg.parsedType) {
                        MessageType.IMAGE -> if (msg.text.isNotBlank()) "📷 ${msg.text}" else "📷 Foto"
                        MessageType.VIDEO -> if (msg.text.isNotBlank()) "🎥 ${msg.text}" else "🎥 Video"
                        MessageType.FILE -> "📎 ${msg.fileName ?: "Dokumen"}"
                        else -> msg.text
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            onClick = onOpenSplitView,
                            shape = RoundedCornerShape(16.dp),
                            color = if (isOutgoing) {
                                CallOutgoingBubble.copy(alpha = 0.84f)
                            } else {
                                Color(0xFF0C181F).copy(alpha = 0.78f)
                            },
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .border(
                                    width = 0.8.dp,
                                    color = Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    if (!isOutgoing) {
                                        Text(
                                            text = msg.senderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MintVibrant,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = previewText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatTimeShort(msg.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.62f)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = typingSummary != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            typingSummary?.let { label ->
                TypingIndicatorBubble(
                    label = label,
                    darkSurface = true
                )
            }
        }

        // Floating Translucent Quick Composer
        InCallMessageComposer(
            draftText = draftText,
            hint = stringResource(R.string.call_chat_quick_hint),
            onDraftChange = onDraftChange,
            onSendClick = onSendText,
            onAttachClick = onAttachClick,
            translucent = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun InCallMessageComposer(
    draftText: String,
    hint: String,
    onDraftChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
    translucent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val canSend = draftText.isNotBlank()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (translucent) Color(0xFF0E1D25).copy(alpha = 0.82f) else CallComposerSurface,
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("incall_attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = stringResource(R.string.attach_file_desc),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draftText.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.56f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = draftText,
                        onValueChange = onDraftChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White
                        ),
                        cursorBrush = SolidColor(MintVibrant),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { if (canSend) onSendClick() }
                        ),
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("incall_message_input")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            onClick = { if (canSend) onSendClick() else onAttachClick() },
            shape = CircleShape,
            color = if (canSend) MintVibrant else EmeraldPrimary.copy(alpha = 0.85f),
            modifier = Modifier
                .size(48.dp)
                .testTag("incall_send_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.send_message_desc),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InCallMessageBubble(
    message: ChatMessage,
    isOutgoing: Boolean,
    showSenderName: Boolean,
    receiptStatus: MessageReceiptStatus,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (imageUrl: String?, bitmap: Bitmap?) -> Unit,
    onVideoClick: (ChatMessage) -> Unit,
    onFileClick: (ChatMessage) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val bubbleShape = RoundedCornerShape(
        topStart = if (isOutgoing) 16.dp else 4.dp,
        topEnd = if (isOutgoing) 4.dp else 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp
    )

    val decodedMediaBitmap = remember(message.mediaData, message.thumbnailData, message.parsedType) {
        when (message.parsedType) {
            MessageType.IMAGE -> MediaHelper.decodeBase64ToBitmap(message.mediaData)
            MessageType.VIDEO -> MediaHelper.decodeBase64ToBitmap(message.thumbnailData)
            else -> null
        }
    }

    if (message.parsedType == MessageType.CALL && !message.deleted) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (message.text.contains("Video", ignoreCase = true)) {
                            Icons.Rounded.Videocam
                        } else {
                            Icons.Rounded.Call
                        },
                        contentDescription = null,
                        tint = MintVibrant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${message.text} • ${formatTimeShort(message.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                shape = bubbleShape,
                color = if (isOutgoing) CallOutgoingBubble else CallIncomingBubble,
                modifier = Modifier
                    .widthIn(min = 84.dp, max = 276.dp)
                    .combinedClickable(
                        onClick = {
                            if (!message.deleted) {
                                when (message.parsedType) {
                                    MessageType.IMAGE -> {
                                        if (!message.mediaUrl.isNullOrBlank() || decodedMediaBitmap != null) {
                                            onImageClick(message.mediaUrl, decodedMediaBitmap)
                                        }
                                    }
                                    MessageType.VIDEO -> onVideoClick(message)
                                    MessageType.FILE -> onFileClick(message)
                                    else -> Unit
                                }
                            }
                        },
                        onLongClick = { if (!message.deleted) showMenu = true }
                    )
                    .testTag("incall_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    if (showSenderName && !isOutgoing) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MintVibrant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    if (!message.deleted && !message.replyToPreview.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.22f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp)
                        ) {
                            Row(modifier = Modifier.padding(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MintVibrant)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = message.replyToSenderName ?: "Pesan",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MintVibrant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = message.replyToPreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (message.deleted) {
                        Text(
                            text = stringResource(R.string.message_deleted_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = Color.White.copy(alpha = 0.56f)
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
                                                .height(145.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF0A141A))
                                        )
                                    }
                                    decodedMediaBitmap != null -> {
                                        Image(
                                            bitmap = decodedMediaBitmap.asImageBitmap(),
                                            contentDescription = message.fileName ?: "Foto",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(145.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                }
                                if (message.text.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            MessageType.VIDEO -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(135.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0A141A)),
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
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Putar video",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                if (message.text.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            MessageType.FILE -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.2f))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AttachDocumentColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = "Dokumen",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = message.fileName ?: "Dokumen",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = MediaHelper.formatFileSize(message.fileSizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.65f)
                                        )
                                    }
                                }
                            }

                            else -> {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimeShort(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.64f)
                        )
                        if (isOutgoing && !message.deleted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            MessageReceiptIndicator(
                                status = receiptStatus,
                                messageId = message.id,
                                mutedTint = Color.White.copy(alpha = 0.64f),
                                iconSize = 15.dp
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reply_action)) },
                    leadingIcon = { Icon(Icons.Rounded.Reply, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onReply()
                    }
                )
                if (isOutgoing) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallAttachmentPickerSheet(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickDocument: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CallPanelHeader,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.call_chat_attach_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InCallAttachOption(
                    icon = Icons.Rounded.Image,
                    label = stringResource(R.string.attach_gallery),
                    color = AttachPhotoColor,
                    testTag = "incall_attach_image_button",
                    onClick = {
                        onDismiss()
                        onPickImage()
                    }
                )
                InCallAttachOption(
                    icon = Icons.Rounded.Videocam,
                    label = stringResource(R.string.attach_video),
                    color = AttachVideoColor,
                    testTag = "incall_attach_video_button",
                    onClick = {
                        onDismiss()
                        onPickVideo()
                    }
                )
                InCallAttachOption(
                    icon = Icons.Rounded.Description,
                    label = stringResource(R.string.attach_document),
                    color = AttachDocumentColor,
                    testTag = "incall_attach_document_button",
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
private fun InCallAttachOption(
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
            .padding(10.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(25.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}
