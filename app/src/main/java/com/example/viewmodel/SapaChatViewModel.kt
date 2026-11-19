package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CallSession
import com.example.data.CallStatus
import com.example.data.CallType
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.IdAuthInitOutcome
import com.example.data.MediaHelper
import com.example.data.PendingIdAuthRequest
import com.example.data.PrivateUser
import com.example.data.PublicProfile
import com.example.data.SapaChatRepository
import com.example.data.UiState
import com.example.data.extractFirestoreException
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.ui.components.IncomingChatAlert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SapaChatViewModel(
    private val repository: SapaChatRepository,
    val currentUserId: String,
    private val pendingIdAuth: PendingIdAuthRequest? = null
) : ViewModel() {

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)
    val activeCallId: StateFlow<String?> = _activeCallId.asStateFlow()

    private val _callChatId = MutableStateFlow<String?>(null)
    val callChatId: StateFlow<String?> = _callChatId.asStateFlow()

    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo: StateFlow<ChatMessage?> = _replyingTo.asStateFlow()

    private val _isSendingMedia = MutableStateFlow(false)
    val isSendingMedia: StateFlow<Boolean> = _isSendingMedia.asStateFlow()

    private val _mediaUploadProgress = MutableStateFlow<Float?>(null)
    val mediaUploadProgress: StateFlow<Float?> = _mediaUploadProgress.asStateFlow()

    private val _isUpdatingProfile = MutableStateFlow(false)
    val isUpdatingProfile: StateFlow<Boolean> = _isUpdatingProfile.asStateFlow()

    private val _isUploadingAvatar = MutableStateFlow(false)
    val isUploadingAvatar: StateFlow<Boolean> = _isUploadingAvatar.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _newlyCreatedPathnerId = MutableStateFlow<String?>(null)
    val newlyCreatedPathnerId: StateFlow<String?> = _newlyCreatedPathnerId.asStateFlow()

    private val _resolvedPathnerId = MutableStateFlow<String?>(null)
    val resolvedPathnerId: StateFlow<String?> = _resolvedPathnerId.asStateFlow()

    private val _authRejectedEvent = MutableStateFlow<IdAuthInitOutcome.Rejected?>(null)
    val authRejectedEvent: StateFlow<IdAuthInitOutcome.Rejected?> = _authRejectedEvent.asStateFlow()

    private val _isVerifyingIdAuth = MutableStateFlow(pendingIdAuth != null)
    val isVerifyingIdAuth: StateFlow<Boolean> = _isVerifyingIdAuth.asStateFlow()

    private val _incomingChatAlerts = MutableSharedFlow<IncomingChatAlert>(extraBufferCapacity = 16)
    val incomingChatAlerts: SharedFlow<IncomingChatAlert> = _incomingChatAlerts.asSharedFlow()

    val myPrivateUser: StateFlow<PrivateUser?> = repository.observePrivateUser(currentUserId)
        .catch { error ->
            val fsError = extractFirestoreException(error)
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Failed observing private user", fsError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val myProfile: StateFlow<PublicProfile?> = repository.observeMyProfile(currentUserId)
        .catch { error ->
            val fsError = extractFirestoreException(error)
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Failed observing own profile", fsError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val chatsState: StateFlow<UiState<List<ChatRoom>>> = combine(
        repository.observeChats(currentUserId),
        myPrivateUser
    ) { chats, privateUser ->
        val hidden = privateUser?.hiddenChatIds?.toSet().orEmpty()
        chats.filterNot { it.id in hidden }
    }
        .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = extractFirestoreException(error)
            if (fsError == null && error is CancellationException) throw error
            if (Firebase.auth.currentUser?.uid != currentUserId) return@catch
            val root = fsError ?: error
            Log.w(TAG, "Error observing chats", root)
            emit(UiState.Error(root.message ?: "Gagal memuat daftar obrolan."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val contactsState: StateFlow<UiState<List<PublicProfile>>> = combine(
        repository.observeDiscoverableContacts(currentUserId),
        myPrivateUser
    ) { contacts, privateUser ->
        val hidden = privateUser?.hiddenContactUids?.toSet().orEmpty()
        contacts.filterNot { it.userId in hidden }
    }
        .map<List<PublicProfile>, UiState<List<PublicProfile>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = extractFirestoreException(error)
            if (fsError == null && error is CancellationException) throw error
            if (Firebase.auth.currentUser?.uid != currentUserId) return@catch
            val root = fsError ?: error
            Log.w(TAG, "Error observing contacts", root)
            emit(UiState.Error(root.message ?: "Gagal memuat daftar kontak."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(chatId, currentUserId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsError = extractFirestoreException(error)
                        if (fsError == null && error is CancellationException) throw error
                        if (Firebase.auth.currentUser?.uid != currentUserId) return@catch
                        val root = fsError ?: error
                        Log.w(TAG, "Error observing messages", root)
                        emit(UiState.Error(root.message ?: "Gagal memuat pesan."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val callMessagesState: StateFlow<UiState<List<ChatMessage>>> = _callChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(chatId, currentUserId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsError = extractFirestoreException(error)
                        if (fsError == null && error is CancellationException) throw error
                        if (Firebase.auth.currentUser?.uid != currentUserId) return@catch
                        val root = fsError ?: error
                        Log.w(TAG, "Error observing call chat messages", root)
                        emit(UiState.Error(root.message ?: "Gagal memuat pesan obrolan."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val callsState: StateFlow<UiState<List<CallSession>>> = combine(
        repository.observeCalls(currentUserId),
        myPrivateUser
    ) { calls, privateUser ->
        val hidden = privateUser?.hiddenCallIds?.toSet().orEmpty()
        calls.filterNot { call ->
            call.id in hidden && call.parsedStatus !in setOf(CallStatus.RINGING, CallStatus.ACTIVE)
        }
    }
        .map<List<CallSession>, UiState<List<CallSession>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = extractFirestoreException(error)
            if (fsError == null && error is CancellationException) throw error
            if (Firebase.auth.currentUser?.uid != currentUserId) return@catch
            val root = fsError ?: error
            Log.w(TAG, "Error observing calls", root)
            emit(UiState.Error(root.message ?: "Gagal memuat riwayat panggilan."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val deliveredSyncTokens = mutableMapOf<String, Long>()
    private var chatsBaselineCaptured = false
    private val lastNotifiedChatSignatures = mutableMapOf<String, String>()
    private var activeTypingChatId: String? = null
    private var typingStopJob: Job? = null
    private var lastTypingHeartbeatMs: Long = 0L

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                repository.ensureUserInitialized(user, pendingIdAuth)
                    .onSuccess { outcome ->
                        when (outcome) {
                            is IdAuthInitOutcome.Authenticated -> {
                                if (!outcome.pathnerId.isNullOrBlank()) {
                                    _resolvedPathnerId.value = outcome.pathnerId
                                }
                                if (!outcome.newlyCreatedId.isNullOrBlank()) {
                                    _newlyCreatedPathnerId.value = outcome.newlyCreatedId
                                } else if (!outcome.notice.isNullOrBlank()) {
                                    _snackbarMessage.value = outcome.notice
                                }
                                _isVerifyingIdAuth.value = false
                            }
                            is IdAuthInitOutcome.Rejected -> {
                                _authRejectedEvent.value = outcome
                            }
                        }
                    }
                    .onFailure { err ->
                        _isVerifyingIdAuth.value = false
                        _snackbarMessage.value = "Sinkronisasi profil tertunda: ${err.localizedMessage}"
                    }
            } ?: run {
                _isVerifyingIdAuth.value = false
            }
        }
        viewModelScope.launch {
            chatsState.collect { state ->
                val rooms = (state as? UiState.Success)?.data ?: return@collect
                if (!chatsBaselineCaptured) {
                    rooms.forEach { room ->
                        if (room.id.isNotBlank()) {
                            val marker = room.lastMessageAt?.toDate()?.time ?: 0L
                            lastNotifiedChatSignatures[room.id] = "$marker|${room.lastMessageSenderId.orEmpty()}|${room.lastMessageText}"
                            if (room.lastMessageSenderId != null && room.lastMessageSenderId != currentUserId) {
                                deliveredSyncTokens[room.id] = marker
                                launch { repository.markChatMessagesDelivered(room.id) }
                            }
                        }
                    }
                    chatsBaselineCaptured = true
                    return@collect
                }

                rooms.forEach { room ->
                    if (room.id.isBlank()) return@forEach
                    val marker = room.lastMessageAt?.toDate()?.time ?: 0L
                    val signature = "$marker|${room.lastMessageSenderId.orEmpty()}|${room.lastMessageText}"
                    val prevSignature = lastNotifiedChatSignatures[room.id]
                    lastNotifiedChatSignatures[room.id] = signature

                    if (room.lastMessageSenderId != null && room.lastMessageSenderId != currentUserId) {
                        val previousToken = deliveredSyncTokens[room.id]
                        if (previousToken == null || marker > previousToken) {
                            deliveredSyncTokens[room.id] = marker
                            launch {
                                repository.markChatMessagesDelivered(room.id)
                            }
                        }

                        if (prevSignature != signature && room.lastMessageText.isNotBlank()) {
                            val senderDisplay = room.participantNames[room.lastMessageSenderId]
                                ?.takeIf { it.isNotBlank() }
                            val title = room.resolvedTitle(currentUserId)
                            val preview = if (room.groupChat && senderDisplay != null) {
                                "$senderDisplay: ${room.lastMessageText}"
                            } else {
                                room.lastMessageText
                            }
                            _incomingChatAlerts.tryEmit(
                                IncomingChatAlert(
                                    id = "${room.id}_$marker",
                                    chatId = room.id,
                                    title = title,
                                    messagePreview = preview,
                                    photoUrl = room.resolvedPhotoUrl(currentUserId)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showNotice(message: String) {
        _snackbarMessage.value = message
    }

    fun dismissCreatedIdDialog() {
        _newlyCreatedPathnerId.value = null
    }

    fun consumeAuthRejectedEvent() {
        _authRejectedEvent.value = null
    }

    fun updatePathnerId(rawId: String) {
        viewModelScope.launch {
            _isUpdatingProfile.value = true
            repository.updatePathnerId(rawId)
                .onSuccess { updatedId ->
                    _resolvedPathnerId.value = updatedId
                    _snackbarMessage.value = "ID Partner Bank diperbarui menjadi @$updatedId"
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal mengubah ID Partner Bank."
                }
            _isUpdatingProfile.value = false
        }
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
        _replyingTo.value = null
    }

    fun setReplyingTo(message: ChatMessage?) {
        _replyingTo.value = message
    }

    fun setActiveCallId(callId: String?) {
        _activeCallId.value = callId
    }

    fun setCallChatId(chatId: String?) {
        if (_callChatId.value != chatId) {
            _callChatId.value = chatId
            if (chatId == null) {
                _replyingTo.value = null
            }
        }
    }

    private fun currentFallbackProfile(): PublicProfile {
        val loaded = myProfile.value
        if (loaded != null) return loaded
        val user = Firebase.auth.currentUser
        return PublicProfile(
            id = currentUserId,
            userId = currentUserId,
            displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Anda",
            photoUrl = user?.photoUrl?.toString(),
            statusText = "Ada di Partner Bank",
            discoverable = true,
            online = true
        )
    }

    fun openSelfChat(onOpened: (String) -> Unit) {
        viewModelScope.launch {
            repository.openOrCreateSelfChat(currentFallbackProfile())
                .onSuccess { chat ->
                    _selectedChatId.value = chat.id
                    onOpened(chat.id)
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal membuka Catatan Pribadi."
                }
        }
    }

    fun openDirectChat(other: PublicProfile, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            repository.openOrCreateDirectChat(currentFallbackProfile(), other)
                .onSuccess { chat ->
                    _selectedChatId.value = chat.id
                    onOpened(chat.id)
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal membuka obrolan."
                }
        }
    }

    fun startChatByUserId(targetUid: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            repository.findUserById(targetUid)
                .onSuccess { found ->
                    if (found == null) {
                        _snackbarMessage.value = "Pengguna dengan ID tersebut tidak ditemukan atau tidak publik."
                    } else {
                        openDirectChat(found, onOpened)
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal mencari pengguna."
                }
        }
    }

    fun createGroupChat(title: String, members: List<PublicProfile>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            repository.createGroupChat(title, currentFallbackProfile(), members)
                .onSuccess { chat ->
                    _selectedChatId.value = chat.id
                    _snackbarMessage.value = "Grup \"${chat.title}\" berhasil dibuat."
                    onCreated(chat.id)
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal membuat grup."
                }
        }
    }

    fun onMessageDraftChanged(chatId: String, draftText: String) {
        if (chatId.isBlank()) return
        if (draftText.isBlank()) {
            clearTypingStatus(chatId)
            return
        }
        val now = System.currentTimeMillis()
        if (activeTypingChatId != null && activeTypingChatId != chatId) {
            clearTypingStatus(activeTypingChatId)
        }
        val shouldPublish = activeTypingChatId != chatId || (now - lastTypingHeartbeatMs) >= TYPING_HEARTBEAT_MS
        if (shouldPublish) {
            activeTypingChatId = chatId
            lastTypingHeartbeatMs = now
            viewModelScope.launch {
                repository.setTypingStatus(chatId, isTyping = true)
            }
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(TYPING_IDLE_TIMEOUT_MS)
            clearTypingStatus(chatId)
        }
    }

    fun clearTypingStatus(chatId: String? = null) {
        val currentTyping = activeTypingChatId ?: return
        if (chatId != null && currentTyping != chatId) return
        typingStopJob?.cancel()
        typingStopJob = null
        activeTypingChatId = null
        lastTypingHeartbeatMs = 0L
        viewModelScope.launch {
            repository.setTypingStatus(currentTyping, isTyping = false)
        }
    }

    fun sendText(chat: ChatRoom, text: String) {
        clearTypingStatus(chat.id)
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            repository.sendTextMessage(
                chat = chat,
                senderName = currentFallbackProfile().displayName,
                text = text,
                replyTo = reply
            ).onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Gagal mengirim pesan."
            }
        }
    }

    fun sendImageUri(context: Context, chat: ChatRoom, uri: Uri, caption: String = "") {
        sendImageUris(context, chat, listOf(uri), caption)
    }

    fun sendImageUris(context: Context, chat: ChatRoom, uris: List<Uri>, caption: String = "") {
        if (uris.isEmpty()) return
        clearTypingStatus(chat.id)
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            _isSendingMedia.value = true
            _mediaUploadProgress.value = 0f
            var usedStorageFallback = false
            val total = uris.size.coerceAtLeast(1)

            uris.forEachIndexed { index, uri ->
                val itemCaption = if (index == 0) caption else ""
                val itemReply = if (index == 0) reply else null
                val preparedResult = MediaHelper.prepareImageForStorage(context, uri)
                preparedResult.onSuccess { prepared ->
                    val storageResult = repository.uploadChatImageToStorage(
                        chatId = chat.id,
                        fileName = prepared.fileName,
                        mimeType = prepared.mimeType,
                        jpegBytes = prepared.jpegBytes,
                        onProgress = { fraction ->
                            _mediaUploadProgress.value = ((index + fraction) / total.toFloat()).coerceIn(0f, 1f)
                        }
                    )
                    val attachment = storageResult.fold(
                        onSuccess = { downloadUrl ->
                            com.example.data.PreparedAttachment(
                                messageType = com.example.data.MessageType.IMAGE,
                                fileName = prepared.fileName,
                                mimeType = prepared.mimeType,
                                fileSizeBytes = prepared.fileSizeBytes,
                                mediaUrl = downloadUrl,
                                localFilePath = prepared.localFilePath
                            )
                        },
                        onFailure = { storageErr ->
                            Log.w(TAG, "Firebase Storage upload unavailable, falling back to Firestore media sync: ${storageErr.message}")
                            usedStorageFallback = true
                            com.example.data.PreparedAttachment(
                                messageType = com.example.data.MessageType.IMAGE,
                                fileName = prepared.fileName,
                                mimeType = prepared.mimeType,
                                fileSizeBytes = prepared.fileSizeBytes,
                                mediaData = prepared.fallbackBase64,
                                localFilePath = prepared.localFilePath
                            )
                        }
                    )
                    repository.sendAttachmentMessage(
                        chat = chat,
                        senderName = currentFallbackProfile().displayName,
                        caption = itemCaption,
                        attachment = attachment,
                        replyTo = itemReply
                    ).onFailure { err ->
                        _snackbarMessage.value = err.localizedMessage ?: "Gagal mengirim gambar."
                    }
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal memproses gambar dari galeri."
                }
            }

            if (usedStorageFallback) {
                _snackbarMessage.value = "Gambar terkirim. Aktifkan Firebase Storage di Firebase Console untuk penyimpanan cloud penuh."
            }
            _mediaUploadProgress.value = null
            _isSendingMedia.value = false
        }
    }

    fun sendVideoUri(context: Context, chat: ChatRoom, uri: Uri, caption: String = "") {
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            _isSendingMedia.value = true
            val prepared = MediaHelper.prepareVideoAttachment(context, uri)
            prepared.onSuccess { attachment ->
                repository.sendAttachmentMessage(
                    chat = chat,
                    senderName = currentFallbackProfile().displayName,
                    caption = caption,
                    attachment = attachment,
                    replyTo = reply
                ).onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal mengirim video."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Gagal memproses video."
            }
            _isSendingMedia.value = false
        }
    }

    fun sendDocumentUri(context: Context, chat: ChatRoom, uri: Uri) {
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            _isSendingMedia.value = true
            val prepared = MediaHelper.prepareDocumentAttachment(context, uri)
            prepared.onSuccess { attachment ->
                repository.sendAttachmentMessage(
                    chat = chat,
                    senderName = currentFallbackProfile().displayName,
                    caption = "",
                    attachment = attachment,
                    replyTo = reply
                ).onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal mengirim file."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Gagal memproses file."
            }
            _isSendingMedia.value = false
        }
    }

    fun sendVoiceClip(chat: ChatRoom, audioFile: java.io.File, durationMs: Long) {
        clearTypingStatus(chat.id)
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            _isSendingMedia.value = true
            _mediaUploadProgress.value = 0f
            val preparedResult = MediaHelper.prepareVoiceClipForStorage(audioFile, durationMs)
            preparedResult.onSuccess { prepared ->
                val storageResult = repository.uploadChatAudioToStorage(
                    chatId = chat.id,
                    fileName = prepared.fileName,
                    mimeType = prepared.mimeType,
                    audioBytes = prepared.audioBytes,
                    onProgress = { fraction ->
                        _mediaUploadProgress.value = fraction.coerceIn(0f, 1f)
                    }
                )
                val attachment = storageResult.fold(
                    onSuccess = { downloadUrl ->
                        com.example.data.PreparedAttachment(
                            messageType = com.example.data.MessageType.AUDIO,
                            fileName = prepared.fileName,
                            mimeType = prepared.mimeType,
                            fileSizeBytes = prepared.fileSizeBytes,
                            durationMs = prepared.durationMs,
                            mediaUrl = downloadUrl,
                            localFilePath = prepared.localFilePath
                        )
                    },
                    onFailure = { storageErr ->
                        Log.w(TAG, "Firebase Storage audio upload unavailable, falling back to Firestore audio sync: ${storageErr.message}")
                        com.example.data.PreparedAttachment(
                            messageType = com.example.data.MessageType.AUDIO,
                            fileName = prepared.fileName,
                            mimeType = prepared.mimeType,
                            fileSizeBytes = prepared.fileSizeBytes,
                            durationMs = prepared.durationMs,
                            mediaData = prepared.fallbackBase64,
                            localFilePath = prepared.localFilePath
                        )
                    }
                )
                repository.sendAttachmentMessage(
                    chat = chat,
                    senderName = currentFallbackProfile().displayName,
                    caption = "",
                    attachment = attachment,
                    replyTo = reply
                ).onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal mengirim pesan suara."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Gagal memproses rekaman suara."
            }
            _mediaUploadProgress.value = null
            _isSendingMedia.value = false
        }
    }

    fun markMessagesAsRead(chatId: String, messages: List<ChatMessage>) {
        viewModelScope.launch {
            repository.markMessagesRead(chatId, messages)
        }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            repository.softDeleteMessage(chatId, messageId)
                .onSuccess {
                    _snackbarMessage.value = "Pesan dihapus untuk semua."
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal menghapus pesan."
                }
        }
    }

    fun deleteMessageForMe(chatId: String, message: ChatMessage) {
        viewModelScope.launch {
            repository.deleteMessageForMe(chatId, message)
                .onSuccess {
                    _snackbarMessage.value = "Pesan dihapus untuk Anda."
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal menghapus pesan."
                }
        }
    }

    fun deleteMessagesForMe(chatId: String, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            repository.deleteMessagesForMe(chatId, messages)
                .onSuccess { count ->
                    _snackbarMessage.value = if (count <= 1) {
                        "1 pesan dihapus untuk Anda."
                    } else {
                        "$count pesan dihapus untuk Anda."
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal menghapus pesan terpilih."
                }
        }
    }

    fun deleteMessagesForEveryone(chatId: String, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            repository.softDeleteMessagesForEveryone(chatId, messages)
                .onSuccess { count ->
                    _snackbarMessage.value = if (count <= 1) {
                        "1 pesan dihapus untuk semua."
                    } else {
                        "$count pesan dihapus untuk semua."
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal menghapus pesan untuk semua."
                }
        }
    }

    fun forwardMessageToTargets(
        message: ChatMessage,
        targetChats: List<ChatRoom>,
        targetContacts: List<PublicProfile>
    ) {
        if (targetChats.isEmpty() && targetContacts.isEmpty()) return
        viewModelScope.launch {
            val myProfileSnapshot = currentFallbackProfile()
            val resolvedRooms = LinkedHashMap<String, ChatRoom>()
            targetChats.forEach { room ->
                if (room.id.isNotBlank()) {
                    resolvedRooms[room.id] = room
                }
            }
            targetContacts.forEach { contact ->
                repository.openOrCreateDirectChat(myProfileSnapshot, contact)
                    .onSuccess { opened ->
                        if (opened.id.isNotBlank()) {
                            resolvedRooms[opened.id] = opened
                        }
                    }
            }

            var successCount = 0
            var lastRoomTitle = ""
            resolvedRooms.values.forEach { room ->
                repository.forwardMessage(
                    sourceMessage = message,
                    targetChat = room,
                    senderName = myProfileSnapshot.displayName
                ).onSuccess {
                    successCount++
                    lastRoomTitle = room.resolvedTitle(currentUserId)
                }
            }

            _snackbarMessage.value = when {
                successCount == 0 -> "Gagal meneruskan pesan."
                successCount == 1 -> "Pesan diteruskan ke $lastRoomTitle."
                else -> "Pesan diteruskan ke $successCount obrolan."
            }
        }
    }

    fun deleteChats(chatIds: Set<String>) {
        if (chatIds.isEmpty()) return
        viewModelScope.launch {
            val currentHidden = myPrivateUser.value?.hiddenChatIds.orEmpty()
            repository.hideChats(chatIds, currentHidden)
                .onSuccess {
                    if (_selectedChatId.value in chatIds) {
                        _selectedChatId.value = null
                    }
                    _snackbarMessage.value = if (chatIds.size == 1) {
                        "1 obrolan dihapus."
                    } else {
                        "${chatIds.size} obrolan dihapus."
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal menghapus obrolan."
                }
        }
    }

    fun deleteCalls(callIds: Set<String>) {
        if (callIds.isEmpty()) return
        viewModelScope.launch {
            val currentHidden = myPrivateUser.value?.hiddenCallIds.orEmpty()
            repository.hideCalls(callIds, currentHidden)
                .onSuccess {
                    _snackbarMessage.value = if (callIds.size == 1) {
                        "1 riwayat panggilan dihapus."
                    } else {
                        "${callIds.size} riwayat panggilan dihapus."
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal menghapus riwayat panggilan."
                }
        }
    }

    fun deleteContacts(contactUids: Set<String>) {
        if (contactUids.isEmpty()) return
        viewModelScope.launch {
            val currentHidden = myPrivateUser.value?.hiddenContactUids.orEmpty()
            repository.hideContacts(contactUids, currentHidden)
                .onSuccess {
                    _snackbarMessage.value = if (contactUids.size == 1) {
                        "1 kontak dihapus dari daftar Anda."
                    } else {
                        "${contactUids.size} kontak dihapus dari daftar Anda."
                    }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal menghapus kontak."
                }
        }
    }

    fun restoreHiddenContacts() {
        viewModelScope.launch {
            repository.restoreHiddenContacts()
                .onSuccess {
                    _snackbarMessage.value = "Kontak yang dihapus berhasil dipulihkan."
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal memulihkan kontak."
                }
        }
    }

    fun updateMyProfile(displayName: String, statusText: String, discoverable: Boolean) {
        viewModelScope.launch {
            _isUpdatingProfile.value = true
            repository.updateProfile(displayName, statusText, discoverable)
                .onSuccess { _snackbarMessage.value = "Profil berhasil disimpan." }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal memperbarui profil."
                }
            _isUpdatingProfile.value = false
        }
    }

    fun updateProfileAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isUploadingAvatar.value = true
            MediaHelper.prepareAvatarBase64(context, uri)
                .onSuccess { encoded ->
                    repository.updateProfilePhoto(encoded)
                        .onSuccess { _snackbarMessage.value = "Foto profil berhasil diperbarui." }
                        .onFailure { err ->
                            _snackbarMessage.value = err.localizedMessage ?: "Gagal menyimpan foto profil."
                        }
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal memproses foto profil."
                }
            _isUploadingAvatar.value = false
        }
    }

    fun removeProfileAvatar() {
        viewModelScope.launch {
            _isUploadingAvatar.value = true
            repository.updateProfilePhoto(null)
                .onSuccess { _snackbarMessage.value = "Foto profil kustom dihapus." }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal menghapus foto profil."
                }
            _isUploadingAvatar.value = false
        }
    }

    suspend fun markOfflineBeforeSignOut() {
        val typingChat = activeTypingChatId
        typingStopJob?.cancel()
        typingStopJob = null
        activeTypingChatId = null
        lastTypingHeartbeatMs = 0L
        if (typingChat != null) {
            repository.setTypingStatus(typingChat, isTyping = false)
        }
        repository.setOnlineStatus(false)
    }

    // --- CALLING ---

    fun startCall(chat: ChatRoom, callType: CallType, onCallStarted: (String) -> Unit) {
        viewModelScope.launch {
            repository.startCall(chat, currentFallbackProfile(), callType)
                .onSuccess { session ->
                    _activeCallId.value = session.id
                    _callChatId.value = session.chatId
                    onCallStarted(session.id)
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal memulai panggilan."
                }
        }
    }

    fun startCallWithContact(contact: PublicProfile, callType: CallType, onCallStarted: (String) -> Unit) {
        viewModelScope.launch {
            repository.openOrCreateDirectChat(currentFallbackProfile(), contact)
                .onSuccess { chat ->
                    startCall(chat, callType, onCallStarted)
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal memulai panggilan."
                }
        }
    }

    fun acceptCall(call: CallSession) {
        viewModelScope.launch {
            repository.acceptCall(call.id, videoEnabled = call.parsedType == CallType.VIDEO)
                .onSuccess {
                    _activeCallId.value = call.id
                    _callChatId.value = call.chatId
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Gagal menerima panggilan."
                }
        }
    }

    fun declineCall(call: CallSession) {
        viewModelScope.launch {
            repository.finishCall(call.id, CallStatus.DECLINED, 0L)
            if (_activeCallId.value == call.id) {
                _activeCallId.value = null
            }
            if (_callChatId.value == call.chatId) {
                _callChatId.value = null
            }
        }
    }

    fun handleCallNotificationAction(callId: String, action: String) {
        val calls = (callsState.value as? UiState.Success)?.data.orEmpty()
        val target = calls.firstOrNull { it.id == callId } ?: return
        when (action) {
            com.example.notifications.AppNotificationManager.ACTION_CALL_ACCEPT -> acceptCall(target)
            com.example.notifications.AppNotificationManager.ACTION_CALL_DECLINE -> declineCall(target)
            else -> {
                _activeCallId.value = target.id
                _callChatId.value = target.chatId
            }
        }
    }

    fun updateCallMedia(call: CallSession, cameraOn: Boolean, muted: Boolean) {
        viewModelScope.launch {
            repository.updateCallMediaState(call, cameraOn, muted)
        }
    }

    fun endActiveCall(call: CallSession, durationSeconds: Long, chatForLog: ChatRoom?) {
        viewModelScope.launch {
            val status = if (call.parsedStatus == CallStatus.RINGING && durationSeconds == 0L) {
                CallStatus.MISSED
            } else {
                CallStatus.ENDED
            }
            repository.finishCall(call.id, status, durationSeconds)
            if (chatForLog != null) {
                val label = if (call.parsedType == CallType.VIDEO) "Panggilan Video" else "Panggilan Suara"
                val detail = if (durationSeconds > 0L) {
                    "$label • ${MediaHelper.formatCallSeconds(durationSeconds)}"
                } else {
                    "$label tidak terjawab"
                }
                repository.sendCallLogMessage(
                    chat = chatForLog,
                    senderName = currentFallbackProfile().displayName,
                    callType = call.parsedType,
                    summaryText = detail
                )
            }
            if (_activeCallId.value == call.id) {
                _activeCallId.value = null
            }
            if (_callChatId.value == call.chatId) {
                _callChatId.value = null
            }
        }
    }

    companion object {
        private const val TAG = "SapaChatVM"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TYPING_HEARTBEAT_MS = 4_000L
        private const val TYPING_IDLE_TIMEOUT_MS = 3_500L
    }
}
