package com.example.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.CallSession
import com.example.data.CallStatus
import com.example.data.ChatRoom
import com.example.data.SapaChatRepository
import com.example.ui.components.IncomingChatAlert
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service (remoteMessaging) that keeps Firestore real-time listeners active
 * while the user switches to other apps or locks the screen, delivering heads-up chat
 * and incoming call notifications with the user's selected tones immediately.
 */
class PartnerNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var notificationPrefs: NotificationPreferences
    private lateinit var notificationManager: AppNotificationManager
    private lateinit var tonePlayer: NotificationTonePlayer
    private var firestore: FirebaseFirestore? = null
    private var repository: SapaChatRepository? = null

    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var chatsRegistration: ListenerRegistration? = null
    private var callsRegistration: ListenerRegistration? = null

    @Volatile
    private var currentSettings: NotificationSettings = NotificationSettings()

    @Volatile
    private var listeningUid: String? = null

    private var chatsBaselineCaptured = false
    private val lastChatSignatures = mutableMapOf<String, String>()
    private val deliveredSyncTokens = mutableMapOf<String, Long>()

    @Volatile
    private var activeRingingCall: CallSession? = null

    private var lastPostedCallKey: String? = null

    override fun onCreate() {
        super.onCreate()
        notificationPrefs = NotificationPreferences(applicationContext)
        notificationManager = AppNotificationManager(applicationContext)
        tonePlayer = NotificationTonePlayer(applicationContext)

        promoteToForeground()
        AppSessionState.setBackgroundServiceRunning(true)

        // Move Firestore initialization, preferences observation, and auth listener off the UI thread
        serviceScope.launch {
            val databaseId = getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            firestore = db
            repository = SapaChatRepository(db)

            val auth = Firebase.auth
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val uid = firebaseAuth.currentUser?.uid
                serviceScope.launch {
                    if (uid.isNullOrBlank()) {
                        detachListeners()
                        stopSelf()
                    } else if (uid != listeningUid) {
                        attachListenersForUser(uid)
                    }
                }
            }
            authStateListener = listener
            auth.addAuthStateListener(listener)

            auth.currentUser?.uid?.let { uid ->
                attachListenersForUser(uid)
            }
        }

        // Observe user's notification & tone preferences in real time
        serviceScope.launch {
            notificationPrefs.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                notificationManager.ensureChannels(settings)

                if (!settings.shouldRunBackgroundListener) {
                    stopSelf()
                    return@collectLatest
                }

                // Re-evaluate active ringing call if user changed ringtone or toggled call notifications
                syncRingingCallAlert()
            }
        }

        // Observe mute-ringtone actions triggered from VideoCallScreen
        serviceScope.launch {
            AppSessionState.mutedRingingCallId.collectLatest {
                syncRingingCallAlert()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        serviceScope.launch {
            val uid = Firebase.auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                stopSelf()
            } else if (uid != listeningUid && firestore != null) {
                attachListenersForUser(uid)
            }
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout reached; stopping cleanly.")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppSessionState.setBackgroundServiceRunning(false)
        authStateListener?.let { Firebase.auth.removeAuthStateListener(it) }
        authStateListener = null
        detachListeners()
        tonePlayer.release()
        notificationManager.cancelIncomingCallNotification()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground() {
        val notification = notificationManager.buildForegroundServiceNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    AppNotificationManager.SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                )
            } else {
                startForeground(AppNotificationManager.SERVICE_NOTIFICATION_ID, notification)
            }
        }.onFailure { err ->
            Log.e(TAG, "Unable to enter foreground state: ${err.message}")
            stopSelf()
        }
    }

    @Synchronized
    private fun attachListenersForUser(uid: String) {
        val db = firestore ?: return
        detachListeners()
        listeningUid = uid
        chatsBaselineCaptured = false
        lastChatSignatures.clear()
        deliveredSyncTokens.clear()

        chatsRegistration = db.collection("chats")
            .whereArrayContains("participantUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    if (error != null) {
                        Log.w(TAG, "Background chats listener error: ${error.message}")
                    }
                    return@addSnapshotListener
                }
                val rooms = snapshot.toObjects(
                    ChatRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                handleChatsSnapshot(uid, rooms)
            }

        callsRegistration = db.collection("calls")
            .whereArrayContains("participantUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    if (error != null) {
                        Log.w(TAG, "Background calls listener error: ${error.message}")
                    }
                    return@addSnapshotListener
                }
                val calls = snapshot.toObjects(
                    CallSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                handleCallsSnapshot(uid, calls)
            }
    }

    @Synchronized
    private fun detachListeners() {
        chatsRegistration?.remove()
        chatsRegistration = null
        callsRegistration?.remove()
        callsRegistration = null
        listeningUid = null
        activeRingingCall = null
        lastPostedCallKey = null
        tonePlayer.stopIncomingCallRingtone()
        notificationManager.cancelIncomingCallNotification()
    }

    private fun handleChatsSnapshot(currentUserId: String, rooms: List<ChatRoom>) {
        if (!chatsBaselineCaptured) {
            rooms.forEach { room ->
                if (room.id.isNotBlank()) {
                    val marker = room.lastMessageAt?.toDate()?.time ?: 0L
                    lastChatSignatures[room.id] = "$marker|${room.lastMessageSenderId.orEmpty()}|${room.lastMessageText}"
                    if (room.lastMessageSenderId != null && room.lastMessageSenderId != currentUserId) {
                        deliveredSyncTokens[room.id] = marker
                    }
                }
            }
            chatsBaselineCaptured = true
            return
        }

        val settings = currentSettings
        rooms.forEach { room ->
            if (room.id.isBlank()) return@forEach
            val marker = room.lastMessageAt?.toDate()?.time ?: 0L
            val signature = "$marker|${room.lastMessageSenderId.orEmpty()}|${room.lastMessageText}"
            val prevSignature = lastChatSignatures[room.id]
            lastChatSignatures[room.id] = signature

            if (room.lastMessageSenderId == null || room.lastMessageSenderId == currentUserId) {
                return@forEach
            }

            // Mark delivered in Firestore even when user is in another app
            val prevDelivered = deliveredSyncTokens[room.id]
            if (prevDelivered == null || marker > prevDelivered) {
                deliveredSyncTokens[room.id] = marker
                serviceScope.launch {
                    repository?.markChatMessagesDelivered(room.id)
                }
            }

            if (prevSignature == signature || room.lastMessageText.isBlank()) {
                return@forEach
            }

            if (!settings.chatNotificationsEnabled) return@forEach

            val isForeground = AppSessionState.isAppInForeground.value
            val isViewingSameChat = isForeground && AppSessionState.activeChatId.value == room.id
            if (isViewingSameChat) return@forEach

            val senderDisplay = room.participantNames[room.lastMessageSenderId]
                ?.takeIf { it.isNotBlank() }
            val title = room.resolvedTitle(currentUserId)
            val preview = if (room.groupChat && senderDisplay != null) {
                "$senderDisplay: ${room.lastMessageText}"
            } else {
                room.lastMessageText
            }

            if (notificationManager.canPostNotifications()) {
                notificationManager.showChatNotification(
                    chatId = room.id,
                    title = title,
                    messagePreview = preview,
                    toneId = settings.chatToneId,
                    vibrate = settings.vibrationEnabled
                )
            } else if (isForeground) {
                tonePlayer.playChatNotificationTone(
                    toneId = settings.chatToneId,
                    vibrate = settings.vibrationEnabled
                )
            }

            if (isForeground) {
                AppSessionState.emitForegroundChatAlert(
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

    private fun handleCallsSnapshot(currentUserId: String, calls: List<CallSession>) {
        val incoming = calls.firstOrNull {
            it.calleeId == currentUserId &&
                !it.isSelfCall() &&
                it.parsedStatus == CallStatus.RINGING
        }
        val prevId = activeRingingCall?.id
        activeRingingCall = incoming
        if (incoming?.id != prevId) {
            AppSessionState.muteRingingCall(null)
        }
        syncRingingCallAlert()
    }

    private fun syncRingingCallAlert() {
        val ringing = activeRingingCall
        val settings = currentSettings
        val uid = listeningUid ?: return

        if (ringing == null || !settings.callNotificationsEnabled) {
            if (lastPostedCallKey != null) {
                lastPostedCallKey = null
                tonePlayer.stopIncomingCallRingtone()
                notificationManager.cancelIncomingCallNotification()
            }
            return
        }

        val isMuted = AppSessionState.mutedRingingCallId.value == ringing.id
        val stateKey = "${ringing.id}|${settings.callToneId}|${settings.vibrationEnabled}|$isMuted"
        if (stateKey == lastPostedCallKey) return
        lastPostedCallKey = stateKey

        val callerName = ringing.peerName(uid)
        if (notificationManager.canPostNotifications()) {
            tonePlayer.stopIncomingCallRingtone()
            notificationManager.showIncomingCallNotification(
                callId = ringing.id,
                callerName = callerName,
                callType = ringing.parsedType,
                toneId = settings.callToneId,
                vibrate = settings.vibrationEnabled,
                isTestPreview = false,
                silentUpdate = isMuted
            )
        } else {
            if (isMuted) {
                tonePlayer.stopIncomingCallRingtone()
            } else {
                tonePlayer.startIncomingCallRingtone(
                    toneId = settings.callToneId,
                    vibrate = settings.vibrationEnabled
                )
            }
        }
    }

    companion object {
        private const val TAG = "PartnerNotifService"

        fun syncServiceState(context: Context, isSignedIn: Boolean, shouldRun: Boolean) {
            val appCtx = context.applicationContext
            val serviceIntent = Intent(appCtx, PartnerNotificationService::class.java)
            if (isSignedIn && shouldRun) {
                runCatching {
                    ContextCompat.startForegroundService(appCtx, serviceIntent)
                }.onFailure { err ->
                    Log.w(TAG, "Deferred foreground service start: ${err.message}")
                }
            } else {
                runCatching {
                    appCtx.stopService(serviceIntent)
                }
            }
        }
    }
}
