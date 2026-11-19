package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MessageReceiptStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
}

enum class MessageType {
    TEXT, IMAGE, VIDEO, AUDIO, FILE, CALL;

    companion object {
        fun fromRaw(raw: String?): MessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class CallType {
    VIDEO, VOICE;

    companion object {
        fun fromRaw(raw: String?): CallType =
            entries.firstOrNull { it.name == raw } ?: VIDEO
    }
}

enum class CallStatus {
    RINGING, ACTIVE, ENDED, DECLINED, MISSED;

    companion object {
        fun fromRaw(raw: String?): CallStatus =
            entries.firstOrNull { it.name == raw } ?: ENDED
    }
}

@Keep
@IgnoreExtraProperties
data class PrivateUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val pathnerId: String? = null,
    val email: String? = null,
    val displayName: String = "",
    val photoUrl: String? = null,
    val photoData: String? = null,
    val statusText: String = "Ada di Partner Bank",
    val discoverable: Boolean = true,
    val hiddenChatIds: List<String> = emptyList(),
    val hiddenCallIds: List<String> = emptyList(),
    val hiddenContactUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val pathnerId: String? = null,
    val displayName: String = "",
    val photoUrl: String? = null,
    val photoData: String? = null,
    val statusText: String = "Ada di Partner Bank",
    val discoverable: Boolean = true,
    val online: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val title: String? = null,
    val groupChat: Boolean = false,
    val createdBy: String = "",
    val participantUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageType: String = MessageType.TEXT.name,
    val lastMessageSenderId: String? = null,
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val lastMessageDeliveredAt: Timestamp? = null,
    val lastMessageReadAt: Timestamp? = null,
    val typingUsers: Map<String, Timestamp> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolveLastMessageReceiptStatus(currentUserId: String): MessageReceiptStatus {
        if (isSelfChat(currentUserId)) return MessageReceiptStatus.READ
        return when {
            lastMessageReadAt != null -> MessageReceiptStatus.READ
            lastMessageDeliveredAt != null -> MessageReceiptStatus.DELIVERED
            lastMessageAt != null -> MessageReceiptStatus.SENT
            else -> MessageReceiptStatus.SENDING
        }
    }
    fun resolvedTitle(currentUserId: String): String {
        if (groupChat) return title?.takeIf { it.isNotBlank() } ?: "Grup Partner Bank"
        if (participantUids.size == 1 && participantUids.firstOrNull() == currentUserId) {
            return title?.takeIf { it.isNotBlank() } ?: "Catatan Pribadi (Anda)"
        }
        val otherUid = participantUids.firstOrNull { it != currentUserId }
        return otherUid?.let { participantNames[it] }
            ?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: "Pengguna Partner Bank"
    }

    fun resolvedPhotoUrl(currentUserId: String): String? {
        if (groupChat) return null
        val otherUid = participantUids.firstOrNull { it != currentUserId } ?: currentUserId
        return participantPhotos[otherUid]?.takeIf { it.isNotBlank() }
    }

    fun otherParticipantUid(currentUserId: String): String =
        participantUids.firstOrNull { it != currentUserId } ?: currentUserId

    fun isSelfChat(currentUserId: String): Boolean =
        !groupChat && participantUids.size == 1 && participantUids.firstOrNull() == currentUserId

    fun activeTypingPeerNames(
        currentUserId: String,
        nowMs: Long = System.currentTimeMillis(),
        activeWindowMs: Long = 10_000L
    ): List<String> {
        if (typingUsers.isEmpty()) return emptyList()
        return typingUsers.entries
            .filter { (uid, ts) ->
                if (uid == currentUserId) return@filter false
                val ageMs = nowMs - ts.toDate().time
                ageMs in -5_000L..activeWindowMs
            }
            .map { (uid, _) ->
                participantNames[uid]?.takeIf { it.isNotBlank() } ?: "Seseorang"
            }
    }

    fun formatTypingSummary(
        currentUserId: String,
        nowMs: Long = System.currentTimeMillis()
    ): String? {
        val names = activeTypingPeerNames(currentUserId, nowMs)
        if (names.isEmpty()) return null
        return when {
            !groupChat -> "sedang mengetik…"
            names.size == 1 -> "${names.first()} sedang mengetik…"
            names.size == 2 -> "${names[0]} & ${names[1]} sedang mengetik…"
            else -> "${names[0]} dan ${names.size - 1} lainnya sedang mengetik…"
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val participantUids: List<String> = emptyList(),
    val messageType: String = MessageType.TEXT.name,
    val text: String = "",
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSizeBytes: Long? = null,
    val durationMs: Long? = null,
    val mediaUrl: String? = null,
    val mediaData: String? = null,
    val thumbnailData: String? = null,
    val localFilePath: String? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    @ServerTimestamp val sentAt: Timestamp? = null,
    val deliveredToUids: List<String> = emptyList(),
    val deliveredAt: Timestamp? = null,
    val readByUids: List<String> = emptyList(),
    val readAt: Timestamp? = null,
    val forwarded: Boolean = false,
    val hiddenForUids: List<String> = emptyList(),
    val deleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val pendingSend: Boolean = false
) {
    val parsedType: MessageType
        get() = MessageType.fromRaw(messageType)

    fun copyableText(): String? {
        if (deleted) return null
        return when (parsedType) {
            MessageType.TEXT -> text.trim().takeIf { it.isNotEmpty() }
            MessageType.IMAGE, MessageType.VIDEO -> text.trim().takeIf { it.isNotEmpty() }
                ?: fileName?.trim()?.takeIf { it.isNotEmpty() }
            MessageType.FILE -> fileName?.trim()?.takeIf { it.isNotEmpty() }
                ?: text.trim().takeIf { it.isNotEmpty() }
            MessageType.AUDIO -> "Pesan suara (${MediaHelper.formatDurationMs(durationMs)})"
            MessageType.CALL -> text.trim().takeIf { it.isNotEmpty() }
        }
    }

    fun previewSummary(): String {
        if (deleted) return "Pesan ini telah dihapus"
        return when (parsedType) {
            MessageType.IMAGE -> if (text.isNotBlank()) "📷 $text" else "📷 Foto"
            MessageType.VIDEO -> if (text.isNotBlank()) "🎥 $text" else "🎥 Video (${MediaHelper.formatDurationMs(durationMs)})"
            MessageType.AUDIO -> "🎤 Pesan suara (${MediaHelper.formatDurationMs(durationMs)})"
            MessageType.FILE -> "📎 ${fileName ?: "Dokumen"}"
            else -> text
        }
    }

    fun resolveReceiptStatus(isSelfChat: Boolean, isGroupChat: Boolean): MessageReceiptStatus {
        val effectiveSentAt = sentAt ?: createdAt
        if (pendingSend || id.isBlank() || effectiveSentAt == null) return MessageReceiptStatus.SENDING
        if (isSelfChat) return MessageReceiptStatus.READ

        val peers = participantUids.filter { it != senderId }
        if (peers.isEmpty()) return MessageReceiptStatus.READ

        val peerReaders = readByUids.filter { it != senderId }.toSet()
        val peerDelivered = (deliveredToUids + readByUids).filter { it != senderId }.toSet()

        val isRead = if (isGroupChat) {
            (readAt != null || peerReaders.isNotEmpty()) && peers.all { it in peerReaders }
        } else {
            readAt != null || peerReaders.isNotEmpty()
        }
        val isDelivered = deliveredAt != null || peerDelivered.isNotEmpty()

        return when {
            isRead -> MessageReceiptStatus.READ
            isDelivered -> MessageReceiptStatus.DELIVERED
            else -> MessageReceiptStatus.SENT
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String? = null,
    val calleeId: String = "",
    val calleeName: String = "",
    val calleePhotoUrl: String? = null,
    val participantUids: List<String> = emptyList(),
    val callType: String = CallType.VIDEO.name,
    val status: String = CallStatus.RINGING.name,
    val callerCameraOn: Boolean = true,
    val callerMuted: Boolean = false,
    val calleeCameraOn: Boolean = true,
    val calleeMuted: Boolean = false,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: CallType get() = CallType.fromRaw(callType)
    val parsedStatus: CallStatus get() = CallStatus.fromRaw(status)

    fun peerName(currentUserId: String): String =
        if (callerId == currentUserId) calleeName else callerName

    fun peerPhotoUrl(currentUserId: String): String? =
        if (callerId == currentUserId) calleePhotoUrl else callerPhotoUrl

    fun isSelfCall(): Boolean = callerId == calleeId
}

data class PreparedAttachment(
    val messageType: MessageType,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val durationMs: Long? = null,
    val mediaUrl: String? = null,
    val mediaData: String? = null,
    val thumbnailData: String? = null,
    val localFilePath: String? = null
)

data class PreparedStorageImage(
    val fileName: String,
    val mimeType: String,
    val jpegBytes: ByteArray,
    val fileSizeBytes: Long,
    val localFilePath: String,
    val fallbackBase64: String
)

data class PreparedStorageAudio(
    val fileName: String,
    val mimeType: String,
    val audioBytes: ByteArray,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val localFilePath: String,
    val fallbackBase64: String?
)

enum class IdAuthMode {
    LOGIN,
    REGISTER
}

data class PendingIdAuthRequest(
    val mode: IdAuthMode,
    val pathnerId: String
)

sealed class IdAuthInitOutcome {
    data class Authenticated(
        val pathnerId: String?,
        val newlyCreatedId: String? = null,
        val notice: String? = null
    ) : IdAuthInitOutcome()

    data class Rejected(
        val reason: String,
        val suggestRegister: Boolean = false
    ) : IdAuthInitOutcome()
}

object PathnerIdHelper {
    private val VALID_PATTERN = Regex("^[a-z0-9_\\-]+$")
    private val RANDOM_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

    fun normalize(raw: String): String =
        raw.trim().removePrefix("@").lowercase()

    fun isValid(normalizedId: String): Boolean =
        normalizedId.length in 3..32 && VALID_PATTERN.matches(normalizedId)

    fun validationError(raw: String): String? {
        val normalized = normalize(raw)
        return when {
            normalized.isEmpty() -> "Masukkan ID User Partner Bank Anda."
            normalized.length < 3 -> "ID minimal 3 karakter."
            normalized.length > 32 -> "ID maksimal 32 karakter."
            !VALID_PATTERN.matches(normalized) -> "ID hanya boleh huruf kecil, angka, tanda '_' atau '-'."
            else -> null
        }
    }

    fun generateRandomId(): String {
        val digits = (1000..9999).random()
        val tag = (1..2).map { RANDOM_ALPHABET.random() }.joinToString("")
        return "tp_$digits$tag"
    }
}
