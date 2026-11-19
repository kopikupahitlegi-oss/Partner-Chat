package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CallType
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.MediaHelper
import com.example.data.MessageType
import com.example.data.UiState
import com.example.data.VoiceNotePlayer
import com.example.data.VoiceNoteRecorder
import com.example.ui.components.AttachmentPickerSheet
import com.example.ui.components.BulkDeleteMessagesConfirmDialog
import com.example.ui.components.ChatWallpaperBackground
import com.example.ui.components.DeleteMessageConfirmDialog
import com.example.ui.components.ForwardMessageSheet
import com.example.ui.components.FullscreenImageViewerDialog
import com.example.ui.components.MessageActionMenuSheet
import com.example.ui.components.MessageBubble
import com.example.ui.components.MessageReceiptInfoDialog
import com.example.ui.components.SelectionActionTopBar
import com.example.ui.components.SelectionQuickActionStrip
import com.example.ui.components.TypingIndicatorBubble
import com.example.ui.components.UserAvatar
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.components.VoiceRecordingBar
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent
import com.example.viewmodel.SapaChatViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chat: ChatRoom,
    viewModel: SapaChatViewModel,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onStartCall: (CallType) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val isSendingMedia by viewModel.isSendingMedia.collectAsStateWithLifecycle()
    val mediaUploadProgress by viewModel.mediaUploadProgress.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (showBackButton) {
        LaunchedEffect(snackbarMessage) {
            val msg = snackbarMessage
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearSnackbar()
            }
        }
    }

    var draftText by remember(chat.id) { mutableStateOf("") }
    var selectedGalleryUris by remember(chat.id) { mutableStateOf<List<Uri>>(emptyList()) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<Pair<String?, Bitmap?>?>(null) }
    var playingVideo by remember { mutableStateOf<Pair<File, String>?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var inspectingMessageId by remember { mutableStateOf<String?>(null) }
    var actionMenuMessage by remember(chat.id) { mutableStateOf<ChatMessage?>(null) }
    var forwardMessageTarget by remember(chat.id) { mutableStateOf<ChatMessage?>(null) }
    var deleteMessageTarget by remember(chat.id) { mutableStateOf<ChatMessage?>(null) }
    var isSelectionMode by remember(chat.id) { mutableStateOf(false) }
    var selectedMessageIds by remember(chat.id) { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteConfirm by remember(chat.id) { mutableStateOf(false) }

    val exitSelectionMode = {
        isSelectionMode = false
        selectedMessageIds = emptySet()
        showBulkDeleteConfirm = false
    }

    // Voice note recorder & shared playback controller
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val voicePlayer = remember(context, scope) {
        VoiceNotePlayer(
            context = context,
            scope = scope,
            onPlaybackError = { msg -> viewModel.showNotice(msg) }
        )
    }
    val voicePlaybackState by voicePlayer.playbackState.collectAsStateWithLifecycle()
    var isRecordingVoice by remember(chat.id) { mutableStateOf(false) }
    var recordingElapsedMs by remember(chat.id) { mutableLongStateOf(0L) }
    var recordingAmplitudes by remember(chat.id) { mutableStateOf<List<Float>>(emptyList()) }

    val listState = rememberLazyListState()

    BackHandler(enabled = isSelectionMode || isRecordingVoice || showBackButton) {
        when {
            isSelectionMode -> exitSelectionMode()
            isRecordingVoice -> {
                voiceRecorder.cancelRecording()
                isRecordingVoice = false
                recordingElapsedMs = 0L
                recordingAmplitudes = emptyList()
            }
            showBackButton -> onBack()
        }
    }

    // Clear typing status, active voice recording, and audio playback when leaving or switching chats
    DisposableEffect(chat.id) {
        onDispose {
            viewModel.clearTypingStatus(chat.id)
            voiceRecorder.cancelRecording()
            voicePlayer.stopAndReset()
        }
    }

    val stopAndSendVoiceRecording: () -> Unit = {
        if (isRecordingVoice) {
            isRecordingVoice = false
            val result = voiceRecorder.stopRecording()
            recordingElapsedMs = 0L
            recordingAmplitudes = emptyList()
            result.onSuccess { clip ->
                viewModel.sendVoiceClip(chat, clip.file, clip.durationMs)
            }.onFailure { err ->
                viewModel.showNotice(err.localizedMessage ?: "Gagal menyimpan rekaman suara.")
            }
        }
    }

    val cancelVoiceRecording: () -> Unit = {
        voiceRecorder.cancelRecording()
        isRecordingVoice = false
        recordingElapsedMs = 0L
        recordingAmplitudes = emptyList()
    }

    val beginVoiceRecording: () -> Unit = {
        voicePlayer.stopAndReset()
        viewModel.clearTypingStatus(chat.id)
        voiceRecorder.startRecording()
            .onSuccess {
                recordingElapsedMs = 0L
                recordingAmplitudes = listOf(0.18f)
                isRecordingVoice = true
            }
            .onFailure { err ->
                isRecordingVoice = false
                viewModel.showNotice(err.localizedMessage ?: "Tidak dapat memulai perekaman audio.")
            }
    }

    // Poll elapsed time & live microphone amplitude while recording; auto-send at 60s max clip length
    LaunchedEffect(isRecordingVoice) {
        if (!isRecordingVoice) return@LaunchedEffect
        while (isRecordingVoice) {
            val elapsed = voiceRecorder.elapsedMs()
            recordingElapsedMs = elapsed
            val amp = voiceRecorder.pollNormalizedAmplitude()
            recordingAmplitudes = (recordingAmplitudes + amp).takeLast(32)
            if (elapsed >= VoiceNoteRecorder.MAX_RECORDING_DURATION_MS) {
                stopAndSendVoiceRecording()
                break
            }
            delay(110L)
        }
    }

    // Auto-expire remote typing indicators if a peer disconnects without clearing their status
    var typingNowMs by remember(chat.id) { mutableLongStateOf(System.currentTimeMillis()) }
    val hasRemoteTypingEntry = chat.typingUsers.any { it.key != viewModel.currentUserId }
    LaunchedEffect(chat.id, chat.typingUsers, hasRemoteTypingEntry) {
        typingNowMs = System.currentTimeMillis()
        while (hasRemoteTypingEntry) {
            delay(2_000L)
            typingNowMs = System.currentTimeMillis()
        }
    }
    val typingSummary = chat.formatTypingSummary(viewModel.currentUserId, typingNowMs)

    // Zero-permission Android Photo Picker for selecting 1..5 gallery images to upload to Firebase Storage
    val galleryImagesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedGalleryUris = (selectedGalleryUris + uris).distinct().take(5)
        }
    }

    val launchGalleryPicker = {
        galleryImagesPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Zero-permission Android Photo Picker for Videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.clearTypingStatus(chat.id)
            viewModel.sendVideoUri(context, chat, uri, draftText)
            draftText = ""
        }
    }

    // Zero-permission SAF Document Picker for files
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.sendDocumentUri(context, chat, uri)
        }
    }

    // Camera capture via FileProvider
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            viewModel.clearTypingStatus(chat.id)
            viewModel.sendImageUri(context, chat, uri, draftText)
            draftText = ""
        }
    }

    val launchCameraCapture = {
        val (uri, _) = MediaHelper.createCameraCaptureUri(context)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            viewModel.showNotice("Izin kamera diperlukan untuk mengambil foto.")
        }
    }

    val requestOrLaunchCamera = {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) launchCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginVoiceRecording()
        } else {
            viewModel.showNotice(context.getString(R.string.voice_permission_needed))
        }
    }

    val requestOrStartVoiceRecording = {
        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPerm) {
            beginVoiceRecording()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val chatTitle = chat.resolvedTitle(viewModel.currentUserId)
    val chatPhoto = chat.resolvedPhotoUrl(viewModel.currentUserId)
    val isSelf = chat.isSelfChat(viewModel.currentUserId)
    val subtitleText = when {
        chat.groupChat -> "${chat.participantUids.size} anggota"
        isSelf -> "Catatan & uji kirim file / video call"
        else -> stringResource(R.string.status_online)
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val selectableMessageIds = remember(messages) {
        messages.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }
    }

    LaunchedEffect(selectableMessageIds, isSelectionMode) {
        if (isSelectionMode) {
            val validIds = selectableMessageIds.toSet()
            val pruned = selectedMessageIds.intersect(validIds)
            if (pruned != selectedMessageIds) {
                selectedMessageIds = pruned
            }
            if (selectableMessageIds.isEmpty()) {
                isSelectionMode = false
            }
        }
    }

    val enterSelectionModeWith: (String?) -> Unit = { initialId ->
        if (isRecordingVoice) {
            cancelVoiceRecording()
        }
        actionMenuMessage = null
        isSelectionMode = true
        selectedMessageIds = if (!initialId.isNullOrBlank()) {
            selectedMessageIds + initialId
        } else {
            emptySet()
        }
    }

    val toggleMessageSelection: (String) -> Unit = { msgId ->
        if (msgId.isNotBlank()) {
            if (!isSelectionMode) {
                enterSelectionModeWith(msgId)
            } else {
                val updated = if (msgId in selectedMessageIds) {
                    selectedMessageIds - msgId
                } else {
                    selectedMessageIds + msgId
                }
                selectedMessageIds = updated
                if (updated.isEmpty()) {
                    isSelectionMode = false
                }
            }
        }
    }

    val toggleSelectAllMessages: () -> Unit = {
        val allIds = selectableMessageIds.toSet()
        selectedMessageIds = if (allIds.isNotEmpty() && selectedMessageIds.size >= allIds.size) {
            emptySet()
        } else {
            allIds
        }
    }

    val showScrollToBottom by remember(messages.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.size > 4 && lastVisible < (messages.lastIndex - 1)
        }
    }

    // Scroll to newest Firestore message when list grows or chat changes
    LaunchedEffect(messages.size, chat.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Mark incoming unread messages as read (writes Firestore readAt & deliveredAt timestamps)
    val unreadIncomingIds = remember(messages, viewModel.currentUserId) {
        messages
            .filter {
                it.senderId != viewModel.currentUserId &&
                    it.id.isNotBlank() &&
                    (viewModel.currentUserId !in it.readByUids || it.readAt == null)
            }
            .map { it.id }
    }
    LaunchedEffect(chat.id, unreadIncomingIds) {
        if (unreadIncomingIds.isNotEmpty()) {
            viewModel.markMessagesAsRead(chat.id, messages)
        }
    }

    val handleSend = {
        val trimmed = draftText.trim()
        if (selectedGalleryUris.isNotEmpty()) {
            val urisToSend = selectedGalleryUris
            selectedGalleryUris = emptyList()
            draftText = ""
            viewModel.sendImageUris(context, chat, urisToSend, trimmed)
        } else if (trimmed.isNotEmpty()) {
            viewModel.sendText(chat, trimmed)
            draftText = ""
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            if (showBackButton) {
                SnackbarHost(snackbarHostState)
            }
        },
        topBar = {
            if (isSelectionMode) {
                SelectionActionTopBar(
                    selectedCount = selectedMessageIds.size,
                    totalCount = selectableMessageIds.size,
                    onClose = exitSelectionMode,
                    onToggleSelectAll = toggleSelectAllMessages,
                    onDeleteClick = {
                        if (selectedMessageIds.isNotEmpty()) {
                            showBulkDeleteConfirm = true
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = EmeraldDeep,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("chat_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.back_desc)
                                )
                            }
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                name = chatTitle,
                                photoUrl = chatPhoto,
                                size = 40.dp,
                                isGroup = chat.groupChat,
                                isSelf = isSelf,
                                showOnlineDot = !chat.groupChat && !isSelf
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = chatTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = typingSummary ?: subtitleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (typingSummary != null) MintAccent else Color.White.copy(alpha = 0.82f),
                                    fontWeight = if (typingSummary != null) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (typingSummary != null) {
                                        Modifier.testTag("chat_typing_status")
                                    } else {
                                        Modifier
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        if (selectableMessageIds.isNotEmpty()) {
                            IconButton(
                                onClick = { enterSelectionModeWith(null) },
                                modifier = Modifier.testTag("chat_enter_selection_mode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = stringResource(R.string.chat_select_messages_desc)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onStartCall(CallType.VIDEO) },
                            modifier = Modifier.testTag("start_video_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = stringResource(R.string.video_call_desc)
                            )
                        }
                        IconButton(
                            onClick = { onStartCall(CallType.VOICE) },
                            modifier = Modifier.testTag("start_voice_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = stringResource(R.string.voice_call_desc)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            ChatWallpaperBackground()

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = isSelectionMode) {
                    SelectionQuickActionStrip(
                        selectedCount = selectedMessageIds.size,
                        totalCount = selectableMessageIds.size,
                        onToggleSelectAll = toggleSelectAllMessages,
                        onDeleteClick = {
                            if (selectedMessageIds.isNotEmpty()) {
                                showBulkDeleteConfirm = true
                            }
                        }
                    )
                }

                AnimatedVisibility(visible = isSendingMedia) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val progress = mediaUploadProgress
                            if (progress != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.media_uploading_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmeraldPrimary
                                    )
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmeraldPrimary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = EmeraldPrimary
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = EmeraldPrimary
                                )
                            }
                        }
                    }
                }

                // Firestore Messages List Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (val state = messagesState) {
                        is UiState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .testTag("chat_loading_indicator"),
                                color = EmeraldPrimary
                            )
                        }
                        is UiState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        is UiState.Success -> {
                            if (state.data.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 32.dp)
                                ) {
                                    Text(
                                        text = "Belum ada pesan di sini. Ketik pesan teks atau lampirkan foto, video, dan dokumen untuk mulai mengobrol.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("chat_messages_list")
                                ) {
                                    itemsIndexed(
                                        items = state.data,
                                        key = { _, msg -> msg.id.ifBlank { msg.hashCode().toString() } }
                                    ) { index, msg ->
                                        val prevMsg = state.data.getOrNull(index - 1)
                                        val currentDayKey = dayKey(msg.createdAt)
                                        val prevDayKey = prevMsg?.let { dayKey(it.createdAt) }
                                        if (currentDayKey != prevDayKey) {
                                            ChatDateDividerChip(label = formatDateHeader(msg.createdAt))
                                        }

                                        val isOutgoing = msg.senderId == viewModel.currentUserId
                                        val receiptStatus = msg.resolveReceiptStatus(
                                            isSelfChat = isSelf,
                                            isGroupChat = chat.groupChat
                                        )
                                        MessageBubble(
                                            message = msg,
                                            isOutgoing = isOutgoing,
                                            showSenderName = chat.groupChat,
                                            receiptStatus = receiptStatus,
                                            isSelected = if (isSelectionMode) {
                                                msg.id in selectedMessageIds
                                            } else {
                                                actionMenuMessage?.id == msg.id
                                            },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = { toggleMessageSelection(msg.id) },
                                            onLongPress = {
                                                if (isSelectionMode) {
                                                    toggleMessageSelection(msg.id)
                                                } else {
                                                    actionMenuMessage = msg
                                                }
                                            },
                                            onReply = { viewModel.setReplyingTo(msg) },
                                            onForward = { forwardMessageTarget = msg },
                                            onCopy = {
                                                msg.copyableText()?.let { copyText ->
                                                    clipboardManager.setText(AnnotatedString(copyText))
                                                    viewModel.showNotice(context.getString(R.string.message_copied_notice))
                                                }
                                            },
                                            onDelete = { deleteMessageTarget = msg },
                                            onShowInfo = { inspectingMessageId = msg.id },
                                            onImageClick = { url, bmp -> viewingImage = url to bmp },
                                            onVideoClick = { videoMsg ->
                                                scope.launch {
                                                    val file = MediaHelper.resolveLocalOrSyncedFile(context, videoMsg)
                                                    if (file != null) {
                                                        playingVideo = file to (videoMsg.fileName ?: "Video")
                                                    } else {
                                                        viewModel.showNotice("File video besar hanya tersimpan di perangkat pengirim.")
                                                    }
                                                }
                                            },
                                            onFileClick = { fileMsg ->
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
                                            },
                                            voicePlaybackState = voicePlaybackState,
                                            onVoicePlayToggle = { audioMsg -> voicePlayer.togglePlayback(audioMsg) },
                                            onVoiceSeek = { msgId, fraction -> voicePlayer.seekToFraction(msgId, fraction) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating scroll-to-latest button
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottom,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 12.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.lastIndex)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape,
                            modifier = Modifier.testTag("scroll_to_bottom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Ke pesan terbaru"
                            )
                        }
                    }
                }

                // Real-time animated peer typing indicator bubble
                AnimatedVisibility(
                    visible = typingSummary != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    typingSummary?.let { label ->
                        TypingIndicatorBubble(label = label)
                    }
                }

                // Reply quote banner
                AnimatedVisibility(visible = !isSelectionMode && replyingTo != null) {
                    val reply = replyingTo
                    if (reply != null) {
                        ReplyPreviewBar(
                            reply = reply,
                            onCancel = { viewModel.setReplyingTo(null) }
                        )
                    }
                }

                // Selected Gallery Images Staging Tray (for Firebase Storage upload)
                AnimatedVisibility(visible = !isSelectionMode && selectedGalleryUris.isNotEmpty()) {
                    SelectedGalleryImagesTray(
                        uris = selectedGalleryUris,
                        onRemoveUri = { target ->
                            selectedGalleryUris = selectedGalleryUris.filterNot { it == target }
                        },
                        onAddMore = launchGalleryPicker,
                        onClearAll = { selectedGalleryUris = emptyList() }
                    )
                }

                // Text Input Bar + Voice Note Recorder Toggle (hidden while selecting messages to delete)
                AnimatedVisibility(visible = !isSelectionMode) {
                    AnimatedContent(
                        targetState = isRecordingVoice,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "chat_composer_voice_switch"
                    ) { recording ->
                        if (recording) {
                            VoiceRecordingBar(
                                elapsedMs = recordingElapsedMs,
                                amplitudes = recordingAmplitudes,
                                onCancel = cancelVoiceRecording,
                                onSend = stopAndSendVoiceRecording
                            )
                        } else {
                            ChatMessageInputBar(
                                draftText = draftText,
                                hasSelectedMedia = selectedGalleryUris.isNotEmpty(),
                                onDraftChange = {
                                    if (it.length <= 2000) {
                                        draftText = it
                                        viewModel.onMessageDraftChanged(chat.id, it)
                                    }
                                },
                                onSendClick = handleSend,
                                onVoiceRecordClick = requestOrStartVoiceRecording,
                                onGalleryClick = launchGalleryPicker,
                                onAttachClick = { showAttachSheet = true },
                                onCameraClick = requestOrLaunchCamera
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAttachSheet) {
        AttachmentPickerSheet(
            onDismiss = { showAttachSheet = false },
            onPickCamera = requestOrLaunchCamera,
            onPickImage = launchGalleryPicker,
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

    val inspectedMessage = inspectingMessageId?.let { id ->
        messages.firstOrNull { it.id == id }
    }
    if (inspectedMessage != null) {
        MessageReceiptInfoDialog(
            message = inspectedMessage,
            chat = chat,
            currentUserId = viewModel.currentUserId,
            onDismiss = { inspectingMessageId = null }
        )
    }

    actionMenuMessage?.let { targetMsg ->
        val isTargetOutgoing = targetMsg.senderId == viewModel.currentUserId
        MessageActionMenuSheet(
            message = targetMsg,
            isOutgoing = isTargetOutgoing,
            onDismiss = { actionMenuMessage = null },
            onForward = { forwardMessageTarget = targetMsg },
            onCopy = {
                targetMsg.copyableText()?.let { copyText ->
                    clipboardManager.setText(AnnotatedString(copyText))
                    viewModel.showNotice(context.getString(R.string.message_copied_notice))
                }
            },
            onReply = { viewModel.setReplyingTo(targetMsg) },
            onShowInfo = if (isTargetOutgoing) {
                { inspectingMessageId = targetMsg.id }
            } else {
                null
            },
            onSelectMultiple = { enterSelectionModeWith(targetMsg.id) },
            onDelete = { deleteMessageTarget = targetMsg }
        )
    }

    forwardMessageTarget?.let { targetMsg ->
        val availableChats = (chatsState as? UiState.Success)?.data.orEmpty()
        val availableContacts = (contactsState as? UiState.Success)?.data.orEmpty()
        ForwardMessageSheet(
            message = targetMsg,
            currentUserId = viewModel.currentUserId,
            chats = availableChats,
            contacts = availableContacts,
            onDismiss = { forwardMessageTarget = null },
            onConfirmForward = { chosenChats, chosenContacts ->
                viewModel.forwardMessageToTargets(
                    message = targetMsg,
                    targetChats = chosenChats,
                    targetContacts = chosenContacts
                )
            }
        )
    }

    deleteMessageTarget?.let { targetMsg ->
        val isTargetOutgoing = targetMsg.senderId == viewModel.currentUserId
        DeleteMessageConfirmDialog(
            message = targetMsg,
            isOutgoing = isTargetOutgoing,
            onDismiss = { deleteMessageTarget = null },
            onDeleteForMe = {
                if (voicePlaybackState.activeMessageId == targetMsg.id) {
                    voicePlayer.stopAndReset()
                }
                viewModel.deleteMessageForMe(chat.id, targetMsg)
            },
            onDeleteForEveryone = {
                if (voicePlaybackState.activeMessageId == targetMsg.id) {
                    voicePlayer.stopAndReset()
                }
                viewModel.deleteMessage(chat.id, targetMsg.id)
            }
        )
    }

    if (showBulkDeleteConfirm && selectedMessageIds.isNotEmpty()) {
        val chosenMessages = messages.filter { it.id in selectedMessageIds }
        if (chosenMessages.isNotEmpty()) {
            val allOutgoingAndActive = chosenMessages.all {
                it.senderId == viewModel.currentUserId && !it.deleted
            }
            BulkDeleteMessagesConfirmDialog(
                selectedCount = chosenMessages.size,
                totalCount = selectableMessageIds.size,
                canDeleteForEveryone = allOutgoingAndActive,
                onDismiss = { showBulkDeleteConfirm = false },
                onDeleteForMe = {
                    if (voicePlaybackState.activeMessageId in selectedMessageIds) {
                        voicePlayer.stopAndReset()
                    }
                    viewModel.deleteMessagesForMe(chat.id, chosenMessages)
                    exitSelectionMode()
                },
                onDeleteForEveryone = {
                    if (voicePlaybackState.activeMessageId in selectedMessageIds) {
                        voicePlayer.stopAndReset()
                    }
                    viewModel.deleteMessagesForEveryone(chat.id, chosenMessages)
                    exitSelectionMode()
                }
            )
        }
    }
}

@Composable
private fun ChatDateDividerChip(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 1.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun ReplyPreviewBar(
    reply: ChatMessage,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EmeraldPrimary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reply.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldPrimary
                )
                Text(
                    text = when (reply.parsedType) {
                        MessageType.IMAGE -> reply.text.ifBlank { "Foto" }
                        MessageType.VIDEO -> reply.text.ifBlank { "Video" }
                        MessageType.AUDIO -> "Pesan suara (${MediaHelper.formatDurationMs(reply.durationMs)})"
                        MessageType.FILE -> reply.fileName ?: "Dokumen"
                        else -> reply.text
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel_reply_desc)
                )
            }
        }
    }
}

@Composable
private fun SelectedGalleryImagesTray(
    uris: List<Uri>,
    onRemoveUri: (Uri) -> Unit,
    onAddMore: () -> Unit,
    onClearAll: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag("selected_gallery_images_tray")
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uris.size} foto siap diunggah ke Firebase Storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldPrimary
                )
                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("clear_selected_images_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cancel_reply_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(uris, key = { it.toString() }) { uri ->
                    Box(modifier = Modifier.size(74.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Pratinjau gambar terpilih",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    RoundedCornerShape(14.dp)
                                )
                        )
                        Surface(
                            onClick = { onRemoveUri(uri) },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.72f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.remove_selected_image_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                if (uris.size < 5) {
                    item(key = "add_more_gallery") {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .border(
                                    1.dp,
                                    EmeraldPrimary.copy(alpha = 0.45f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable(onClick = onAddMore)
                                .testTag("add_more_gallery_images_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.add_more_images_desc),
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageInputBar(
    draftText: String,
    hasSelectedMedia: Boolean,
    onDraftChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onVoiceRecordClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onAttachClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    val canSend = draftText.isNotBlank() || hasSelectedMedia

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = stringResource(R.string.attach_file_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draftText.isEmpty()) {
                        Text(
                            text = stringResource(
                                if (hasSelectedMedia) R.string.caption_input_hint else R.string.message_input_hint
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = draftText,
                        onValueChange = onDraftChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(EmeraldPrimary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { if (canSend) onSendClick() }
                        ),
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_input_field")
                    )
                }

                IconButton(
                    onClick = onGalleryClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("gallery_media_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.gallery_picker_desc),
                        tint = if (hasSelectedMedia) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onCameraClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quick_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = stringResource(R.string.camera_capture_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            onClick = { if (canSend) onSendClick() else onVoiceRecordClick() },
            shape = CircleShape,
            color = EmeraldPrimary,
            shadowElevation = 3.dp,
            modifier = Modifier
                .size(52.dp)
                .testTag(if (canSend) "send_message_button" else "record_voice_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (canSend) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Mic,
                    contentDescription = stringResource(
                        if (canSend) R.string.send_message_desc else R.string.voice_record_desc
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun dayKey(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SimpleDateFormat("yyyyMMdd", Locale.US).format(date)
}

private fun formatDateHeader(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    val todayCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }

    val sameYear = todayCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    val dayDiff = todayCal.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR)

    return when {
        sameYear && dayDiff == 0 -> "Hari ini"
        sameYear && dayDiff == 1 -> "Kemarin"
        else -> SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(date)
    }
}
