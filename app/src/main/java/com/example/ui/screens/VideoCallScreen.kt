package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CallSession
import com.example.data.CallStatus
import com.example.data.CallType
import com.example.data.ChatRoom
import com.example.data.MediaHelper
import com.example.data.UiState
import com.example.ui.components.FullscreenImageViewerDialog
import com.example.ui.components.InCallAttachmentPickerSheet
import com.example.ui.components.InCallOverlayChatFeed
import com.example.ui.components.InCallSplitChatPanel
import com.example.ui.components.UserAvatar
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.theme.CoralCallEnd
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintVibrant
import com.example.viewmodel.SapaChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class CallLayoutMode {
    SPLIT,
    OVERLAY,
    FULLSCREEN
}

@Composable
fun VideoCallScreen(
    call: CallSession,
    chat: ChatRoom,
    viewModel: SapaChatViewModel,
    ringtoneTitle: String = "",
    isRingtoneMuted: Boolean = false,
    onMuteRingtone: () -> Unit = {},
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onToggleMedia: (cameraOn: Boolean, muted: Boolean) -> Unit,
    onEndCall: (durationSeconds: Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = viewModel.currentUserId
    val isVideoCall = call.parsedType == CallType.VIDEO
    val isIncomingRinging = call.calleeId == currentUserId &&
        !call.isSelfCall() &&
        call.parsedStatus == CallStatus.RINGING

    val callMessagesState by viewModel.callMessagesState.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val isSendingMedia by viewModel.isSendingMedia.collectAsStateWithLifecycle()

    var cameraEnabled by remember(call.id) { mutableStateOf(isVideoCall) }
    var micMuted by remember(call.id) { mutableStateOf(false) }
    var speakerOn by remember(call.id) { mutableStateOf(isVideoCall) }
    var useFrontCamera by remember(call.id) { mutableStateOf(true) }
    var elapsedSeconds by remember(call.id) { mutableLongStateOf(0L) }
    var layoutMode by remember(call.id) { mutableStateOf(CallLayoutMode.SPLIT) }
    var draftText by remember(call.id) { mutableStateOf("") }
    var showAttachSheet by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<Pair<String?, Bitmap?>?>(null) }
    var playingVideo by remember { mutableStateOf<Pair<File, String>?>(null) }
    var lastSeenMessageCount by remember(call.id) { mutableIntStateOf(0) }

    // Ensure ViewModel observes this call's Firestore chat messages
    LaunchedEffect(call.chatId) {
        viewModel.setCallChatId(call.chatId)
    }

    DisposableEffect(chat.id) {
        onDispose {
            if (chat.id.isNotBlank()) {
                viewModel.clearTypingStatus(chat.id)
            }
        }
    }

    var typingNowMs by remember(chat.id) { mutableLongStateOf(System.currentTimeMillis()) }
    val hasRemoteTypingEntry = chat.typingUsers.any { it.key != currentUserId }
    LaunchedEffect(chat.id, chat.typingUsers, hasRemoteTypingEntry) {
        typingNowMs = System.currentTimeMillis()
        while (hasRemoteTypingEntry) {
            delay(2_000L)
            typingNowMs = System.currentTimeMillis()
        }
    }
    val typingSummary = chat.formatTypingSummary(currentUserId, typingNowMs)

    val messages = (callMessagesState as? UiState.Success)?.data.orEmpty()
    val effectiveMode = if (isIncomingRinging) CallLayoutMode.FULLSCREEN else layoutMode

    // Track unread messages when user is in FULLSCREEN video mode
    LaunchedEffect(messages.size, effectiveMode) {
        if (effectiveMode != CallLayoutMode.FULLSCREEN) {
            lastSeenMessageCount = messages.size
            if (messages.isNotEmpty() && chat.id.isNotBlank()) {
                viewModel.markMessagesAsRead(chat.id, messages)
            }
        }
    }
    val unreadBadgeCount = if (effectiveMode == CallLayoutMode.FULLSCREEN) {
        (messages.size - lastSeenMessageCount).coerceAtLeast(0)
    } else 0

    // Zero-permission Pickers for in-call media sharing
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendImageUri(context, chat, uri, draftText)
            draftText = ""
        }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendVideoUri(context, chat, uri, draftText)
            draftText = ""
        }
    }
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.sendDocumentUri(context, chat, uri)
        }
    }

    val requiredPermissions = remember(isVideoCall) {
        if (isVideoCall) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }
    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = requiredPermissions.all { results[it] == true }
    }
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // Auto-connect self-call preview after brief ring so single-device users experience full live video + chat
    LaunchedEffect(call.id, call.status) {
        if (call.isSelfCall() && call.parsedStatus == CallStatus.RINGING) {
            delay(1000L)
            onAccept()
        }
    }

    // Live call duration timer while ACTIVE
    LaunchedEffect(call.id, call.status) {
        if (call.parsedStatus == CallStatus.ACTIVE) {
            while (true) {
                delay(1000L)
                elapsedSeconds += 1L
            }
        }
    }

    // Real AudioManager control for mic mute & speakerphone
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    DisposableEffect(micMuted, speakerOn) {
        runCatching {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isMicrophoneMute = micMuted
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = speakerOn
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                audioManager?.isMicrophoneMute = false
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = false
                audioManager?.mode = AudioManager.MODE_NORMAL
            }
        }
    }

    BackHandler {
        when {
            isIncomingRinging -> onDecline()
            replyingTo != null -> viewModel.setReplyingTo(null)
            else -> onEndCall(elapsedSeconds)
        }
    }

    val peerName = call.peerName(currentUserId)
    val peerPhoto = call.peerPhotoUrl(currentUserId)
    val peerMuted = if (call.callerId == currentUserId) call.calleeMuted else call.callerMuted
    val peerCameraOn = if (call.callerId == currentUserId) call.calleeCameraOn else call.callerCameraOn

    val handleSendText = {
        val trimmed = draftText.trim()
        if (trimmed.isNotEmpty()) {
            viewModel.sendText(chat, trimmed)
            draftText = ""
        }
    }

    val handleOpenVideo: (com.example.data.ChatMessage) -> Unit = { videoMsg ->
        scope.launch {
            val file = MediaHelper.resolveLocalOrSyncedFile(context, videoMsg)
            if (file != null) {
                playingVideo = file to (videoMsg.fileName ?: "Video")
            } else {
                viewModel.showNotice("File video besar hanya tersimpan di perangkat pengirim.")
            }
        }
    }

    val handleOpenFile: (com.example.data.ChatMessage) -> Unit = { fileMsg ->
        scope.launch {
            val file = MediaHelper.resolveLocalOrSyncedFile(context, fileMsg)
            if (file != null) {
                val opened = MediaHelper.openFileExternally(context, file, fileMsg.mimeType)
                if (!opened) {
                    viewModel.showNotice("Tidak ada aplikasi untuk membuka jenis file ini.")
                }
            } else {
                viewModel.showNotice("File besar tersimpan secara lokal di perangkat pengirim.")
            }
        }
    }

    val statusLabel = when {
        isIncomingRinging && isVideoCall -> stringResource(R.string.call_incoming_video)
        isIncomingRinging -> stringResource(R.string.call_incoming_voice)
        call.parsedStatus == CallStatus.ACTIVE -> MediaHelper.formatCallSeconds(elapsedSeconds)
        else -> stringResource(R.string.call_ringing)
    }

    val videoStageContent: @Composable (Modifier, Boolean) -> Unit = { stageModifier, isCompactSplit ->
        Box(
            modifier = stageModifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A1D22), Color(0xFF072B27), Color(0xFF0B141A))
                    )
                )
        ) {
            if (isVideoCall && cameraEnabled && permissionsGranted) {
                LiveCameraPreviewSurface(
                    useFrontCamera = useFrontCamera,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.56f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )
            } else {
                CallAvatarPulseStage(
                    peerName = peerName,
                    peerPhoto = peerPhoto,
                    compact = isCompactSplit,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Top Call Header & Mode Switcher
            CallTopHeader(
                peerName = peerName,
                statusLabel = statusLabel,
                isVideoCall = isVideoCall,
                isIncomingRinging = isIncomingRinging,
                isSelfCall = call.isSelfCall(),
                isActive = call.parsedStatus == CallStatus.ACTIVE,
                peerMuted = peerMuted,
                peerCameraOn = peerCameraOn,
                compact = isCompactSplit,
                layoutMode = effectiveMode,
                onSelectMode = { layoutMode = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = if (isCompactSplit) 8.dp else 14.dp)
            )

            // Peer mini PiP badge during active video call
            if (isVideoCall && call.parsedStatus == CallStatus.ACTIVE && !isIncomingRinging) {
                val pipWidth = if (isCompactSplit) 76.dp else 102.dp
                val pipHeight = if (isCompactSplit) 96.dp else 136.dp
                val pipBottomPad = when {
                    isCompactSplit -> 74.dp
                    effectiveMode == CallLayoutMode.OVERLAY -> 260.dp
                    else -> 132.dp
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF102229).copy(alpha = 0.84f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = pipBottomPad)
                        .size(width = pipWidth, height = pipHeight)
                        .border(1.2.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        UserAvatar(
                            name = peerName,
                            photoUrl = peerPhoto,
                            size = if (isCompactSplit) 36.dp else 46.dp,
                            showOnlineDot = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (call.isSelfCall()) "Live" else peerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Bottom Area on Video Stage: Overlay Chat (if OVERLAY) + Call Controls Dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .then(
                        if (!isCompactSplit) Modifier.navigationBarsPadding() else Modifier
                    )
                    .padding(
                        horizontal = if (isCompactSplit) 12.dp else 16.dp,
                        vertical = if (isCompactSplit) 10.dp else 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isIncomingRinging && effectiveMode == CallLayoutMode.OVERLAY) {
                    InCallOverlayChatFeed(
                        messages = messages,
                        currentUserId = currentUserId,
                        draftText = draftText,
                        onDraftChange = {
                            if (it.length <= 2000) {
                                draftText = it
                                if (chat.id.isNotBlank()) {
                                    viewModel.onMessageDraftChanged(chat.id, it)
                                }
                            }
                        },
                        onSendText = handleSendText,
                        onAttachClick = { showAttachSheet = true },
                        onOpenSplitView = { layoutMode = CallLayoutMode.SPLIT },
                        typingSummary = typingSummary
                    )
                }

                if (isIncomingRinging) {
                    if (ringtoneTitle.isNotBlank()) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                onClick = onMuteRingtone,
                                enabled = !isRingtoneMuted,
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = if (isRingtoneMuted) 0.12f else 0.18f),
                                modifier = Modifier.testTag("incoming_call_mute_ringtone_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRingtoneMuted) Icons.Rounded.VolumeOff else Icons.Rounded.RingVolume,
                                        contentDescription = stringResource(R.string.call_mute_ringtone_desc),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isRingtoneMuted) {
                                            "Dering disenyapkan"
                                        } else {
                                            stringResource(R.string.call_ringtone_active_format, ringtoneTitle)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallCircleAction(
                            icon = Icons.Rounded.CallEnd,
                            label = stringResource(R.string.call_decline),
                            containerColor = CoralCallEnd,
                            contentColor = Color.White,
                            size = 64.dp,
                            testTag = "decline_call_button",
                            onClick = onDecline
                        )
                        CallCircleAction(
                            icon = if (isVideoCall) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            label = stringResource(R.string.call_accept),
                            containerColor = MintVibrant,
                            contentColor = Color.White,
                            size = 64.dp,
                            testTag = "accept_call_button",
                            onClick = onAccept
                        )
                    }
                } else {
                    CallControlsDock(
                        isVideoCall = isVideoCall,
                        cameraEnabled = cameraEnabled,
                        micMuted = micMuted,
                        speakerOn = speakerOn,
                        compact = isCompactSplit,
                        layoutMode = effectiveMode,
                        unreadChatCount = unreadBadgeCount,
                        onFlipCamera = { useFrontCamera = !useFrontCamera },
                        onToggleCamera = {
                            cameraEnabled = !cameraEnabled
                            onToggleMedia(cameraEnabled, micMuted)
                        },
                        onToggleMute = {
                            micMuted = !micMuted
                            onToggleMedia(cameraEnabled, micMuted)
                        },
                        onToggleSpeaker = { speakerOn = !speakerOn },
                        onToggleChatMode = {
                            layoutMode = when (layoutMode) {
                                CallLayoutMode.SPLIT -> CallLayoutMode.OVERLAY
                                CallLayoutMode.OVERLAY -> CallLayoutMode.FULLSCREEN
                                CallLayoutMode.FULLSCREEN -> CallLayoutMode.SPLIT
                            }
                        },
                        onEndCall = { onEndCall(elapsedSeconds) }
                    )
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A141A))
            .imePadding()
    ) {
        val isWide = maxWidth >= 720.dp
        val showSplitChat = !isIncomingRinging && effectiveMode == CallLayoutMode.SPLIT

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                videoStageContent(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    false
                )
                if (showSplitChat) {
                    InCallSplitChatPanel(
                        peerName = peerName,
                        isGroupChat = chat.groupChat,
                        isSelfChat = chat.isSelfChat(currentUserId),
                        currentUserId = currentUserId,
                        messagesState = callMessagesState,
                        isSendingMedia = isSendingMedia,
                        replyingTo = replyingTo,
                        draftText = draftText,
                        isSideBySide = true,
                        onDraftChange = {
                            if (it.length <= 2000) {
                                draftText = it
                                if (chat.id.isNotBlank()) {
                                    viewModel.onMessageDraftChanged(chat.id, it)
                                }
                            }
                        },
                        onSendText = handleSendText,
                        onAttachClick = { showAttachSheet = true },
                        onReply = { viewModel.setReplyingTo(it) },
                        onCancelReply = { viewModel.setReplyingTo(null) },
                        onDelete = { viewModel.deleteMessage(chat.id, it.id) },
                        onImageClick = { url, bmp -> viewingImage = url to bmp },
                        onVideoClick = handleOpenVideo,
                        onFileClick = handleOpenFile,
                        onSwitchToOverlay = { layoutMode = CallLayoutMode.OVERLAY },
                        onSwitchToFullscreen = { layoutMode = CallLayoutMode.FULLSCREEN },
                        typingSummary = typingSummary,
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxHeight()
                            .statusBarsPadding()
                    )
                }
            }
        } else {
            val videoWeight by animateFloatAsState(
                targetValue = if (showSplitChat) 0.44f else 1f,
                animationSpec = spring(stiffness = 380f),
                label = "video_stage_weight"
            )
            Column(modifier = Modifier.fillMaxSize()) {
                videoStageContent(
                    Modifier
                        .fillMaxWidth()
                        .weight(videoWeight),
                    showSplitChat
                )
                if (showSplitChat) {
                    InCallSplitChatPanel(
                        peerName = peerName,
                        isGroupChat = chat.groupChat,
                        isSelfChat = chat.isSelfChat(currentUserId),
                        currentUserId = currentUserId,
                        messagesState = callMessagesState,
                        isSendingMedia = isSendingMedia,
                        replyingTo = replyingTo,
                        draftText = draftText,
                        isSideBySide = false,
                        onDraftChange = {
                            if (it.length <= 2000) {
                                draftText = it
                                if (chat.id.isNotBlank()) {
                                    viewModel.onMessageDraftChanged(chat.id, it)
                                }
                            }
                        },
                        onSendText = handleSendText,
                        onAttachClick = { showAttachSheet = true },
                        onReply = { viewModel.setReplyingTo(it) },
                        onCancelReply = { viewModel.setReplyingTo(null) },
                        onDelete = { viewModel.deleteMessage(chat.id, it.id) },
                        onImageClick = { url, bmp -> viewingImage = url to bmp },
                        onVideoClick = handleOpenVideo,
                        onFileClick = handleOpenFile,
                        onSwitchToOverlay = { layoutMode = CallLayoutMode.OVERLAY },
                        onSwitchToFullscreen = { layoutMode = CallLayoutMode.FULLSCREEN },
                        typingSummary = typingSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.56f)
                    )
                }
            }
        }
    }

    if (showAttachSheet) {
        InCallAttachmentPickerSheet(
            onDismiss = { showAttachSheet = false },
            onPickImage = {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickVideo = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onPickDocument = {
                documentPickerLauncher.launch(arrayOf("*/*"))
            }
        )
    }

    viewingImage?.let { (url, bmp) ->
        FullscreenImageViewerDialog(
            imageUrl = url,
            bitmap = bmp,
            onDismiss = { viewingImage = null }
        )
    }

    playingVideo?.let { (file, title) ->
        VideoPlayerDialog(
            videoFile = file,
            title = title,
            onDismiss = { playingVideo = null }
        )
    }
}

@Composable
private fun CallTopHeader(
    peerName: String,
    statusLabel: String,
    isVideoCall: Boolean,
    isIncomingRinging: Boolean,
    isSelfCall: Boolean,
    isActive: Boolean,
    peerMuted: Boolean,
    peerCameraOn: Boolean,
    compact: Boolean,
    layoutMode: CallLayoutMode,
    onSelectMode: (CallLayoutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.48f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isActive) MintVibrant else Color(0xFFFFB74D))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (compact) "$peerName • $statusLabel" else statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            if (!isIncomingRinging) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModePillChip(
                        label = stringResource(R.string.call_mode_split),
                        selected = layoutMode == CallLayoutMode.SPLIT,
                        testTag = "call_mode_split_chip",
                        onClick = { onSelectMode(CallLayoutMode.SPLIT) }
                    )
                    ModePillChip(
                        label = stringResource(R.string.call_mode_overlay),
                        selected = layoutMode == CallLayoutMode.OVERLAY,
                        testTag = "call_mode_overlay_chip",
                        onClick = { onSelectMode(CallLayoutMode.OVERLAY) }
                    )
                }
            }
        }

        if (!compact) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = peerName,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!isSelfCall && isActive && (peerMuted || (!peerCameraOn && isVideoCall))) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.52f)
            ) {
                Text(
                    text = buildString {
                        if (peerMuted) append("Mikrofon lawan bicara dibisukan")
                        if (peerMuted && !peerCameraOn && isVideoCall) append(" • ")
                        if (!peerCameraOn && isVideoCall) append("Kamera dimatikan")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ModePillChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) EmeraldPrimary else Color.Black.copy(alpha = 0.48f),
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (selected) MintVibrant else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CallAvatarPulseStage(
    peerName: String,
    peerPhoto: String?,
    compact: Boolean,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(if (compact) 96.dp else 136.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.28f))
            )
            UserAvatar(
                name = peerName,
                photoUrl = peerPhoto,
                size = if (compact) 74.dp else 104.dp
            )
        }

        if (!permissionsGranted) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.call_permission_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = MintVibrant),
                        modifier = Modifier.testTag("grant_call_permission_button")
                    ) {
                        Text(stringResource(R.string.call_grant_permission), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlsDock(
    isVideoCall: Boolean,
    cameraEnabled: Boolean,
    micMuted: Boolean,
    speakerOn: Boolean,
    compact: Boolean,
    layoutMode: CallLayoutMode,
    unreadChatCount: Int,
    onFlipCamera: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleChatMode: () -> Unit,
    onEndCall: () -> Unit
) {
    val btnSize = if (compact) 48.dp else 50.dp
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF132028).copy(alpha = 0.88f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isVideoCall) {
                CallCircleAction(
                    icon = Icons.Rounded.Cameraswitch,
                    label = stringResource(R.string.call_flip),
                    containerColor = Color.White.copy(alpha = 0.16f),
                    contentColor = Color.White,
                    size = btnSize,
                    showLabel = !compact,
                    testTag = "flip_camera_button",
                    onClick = onFlipCamera
                )
                CallCircleAction(
                    icon = if (cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    label = stringResource(R.string.call_camera),
                    containerColor = if (cameraEnabled) Color.White.copy(alpha = 0.16f) else Color.White,
                    contentColor = if (cameraEnabled) Color.White else EmeraldDeep,
                    size = btnSize,
                    showLabel = !compact,
                    testTag = "toggle_camera_button",
                    onClick = onToggleCamera
                )
            }

            CallCircleAction(
                icon = if (micMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                label = if (micMuted) stringResource(R.string.call_unmute) else stringResource(R.string.call_mute),
                containerColor = if (micMuted) Color.White else Color.White.copy(alpha = 0.16f),
                contentColor = if (micMuted) EmeraldDeep else Color.White,
                size = btnSize,
                showLabel = !compact,
                testTag = "toggle_mute_button",
                onClick = onToggleMute
            )

            CallCircleAction(
                icon = if (speakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                label = stringResource(R.string.call_speaker),
                containerColor = if (speakerOn) EmeraldPrimary else Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                size = btnSize,
                showLabel = !compact,
                testTag = "toggle_speaker_button",
                onClick = onToggleSpeaker
            )

            // Live Chat Mode Toggle with Unread Badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BadgedBox(
                    badge = {
                        if (unreadChatCount > 0) {
                            Badge(containerColor = MintVibrant, contentColor = Color.White) {
                                Text(if (unreadChatCount > 9) "9+" else unreadChatCount.toString())
                            }
                        }
                    }
                ) {
                    Surface(
                        onClick = onToggleChatMode,
                        shape = CircleShape,
                        color = if (layoutMode != CallLayoutMode.FULLSCREEN) MintVibrant else Color.White.copy(alpha = 0.16f),
                        modifier = Modifier
                            .size(btnSize)
                            .testTag("toggle_incall_chat_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (layoutMode == CallLayoutMode.OVERLAY) Icons.Rounded.Layers else Icons.AutoMirrored.Rounded.Chat,
                                contentDescription = stringResource(R.string.call_chat_toggle_desc),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                if (!compact) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            CallCircleAction(
                icon = Icons.Rounded.CallEnd,
                label = stringResource(R.string.call_end),
                containerColor = CoralCallEnd,
                contentColor = Color.White,
                size = btnSize,
                showLabel = !compact,
                testTag = "end_call_button",
                onClick = onEndCall
            )
        }
    }
}

@Composable
private fun CallCircleAction(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    size: Dp = 50.dp,
    showLabel: Boolean = true,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier
                .size(size)
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun LiveCameraPreviewSurface(
    useFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(useFrontCamera, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var providerRef: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                providerRef = cameraProvider
                val preferred = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val fallback = if (useFrontCamera) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                val selector = when {
                    cameraProvider.hasCamera(preferred) -> preferred
                    cameraProvider.hasCamera(fallback) -> fallback
                    else -> null
                }
                if (selector != null) {
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                }
            }.onFailure { err ->
                Log.w("VideoCallScreen", "CameraX preview bind warning: ${err.message}")
            }
        }, executor)

        onDispose {
            runCatching { providerRef?.unbindAll() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
