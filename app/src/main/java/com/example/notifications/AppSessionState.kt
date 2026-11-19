package com.example.notifications

import com.example.ui.components.IncomingChatAlert
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared in-process state bridging MainActivity / Compose UI and PartnerNotificationService
 * so background notifications, foreground heads-up banners, and call ringtones never duplicate.
 */
object AppSessionState {
    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _mutedRingingCallId = MutableStateFlow<String?>(null)
    val mutedRingingCallId: StateFlow<String?> = _mutedRingingCallId.asStateFlow()

    private val _isBackgroundServiceRunning = MutableStateFlow(false)
    val isBackgroundServiceRunning: StateFlow<Boolean> = _isBackgroundServiceRunning.asStateFlow()

    private val _foregroundChatAlerts = MutableSharedFlow<IncomingChatAlert>(extraBufferCapacity = 16)
    val foregroundChatAlerts: SharedFlow<IncomingChatAlert> = _foregroundChatAlerts.asSharedFlow()

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun setActiveChatId(chatId: String?) {
        _activeChatId.value = chatId
    }

    fun muteRingingCall(callId: String?) {
        _mutedRingingCallId.value = callId
    }

    fun setBackgroundServiceRunning(running: Boolean) {
        _isBackgroundServiceRunning.value = running
    }

    fun emitForegroundChatAlert(alert: IncomingChatAlert) {
        _foregroundChatAlerts.tryEmit(alert)
    }
}
