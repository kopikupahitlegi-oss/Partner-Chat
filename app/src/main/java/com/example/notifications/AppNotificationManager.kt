package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CallType

class AppNotificationManager(private val context: Context) {

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureServiceChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_SERVICE_ID) != null) return
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE_ID,
            context.getString(R.string.notif_channel_service_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.notif_channel_service_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(serviceChannel)
    }

    fun ensureChannels(settings: NotificationSettings = NotificationSettings()) {
        ensureServiceChannel()
        ensureChatChannel(settings.chatToneId, settings.vibrationEnabled)
        ensureCallChannel(settings.vibrationEnabled)
    }

    fun ensureChatChannel(toneId: String, vibrate: Boolean): String {
        val channelId = chatChannelId(toneId, vibrate)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return channelId

        if (manager.getNotificationChannel(channelId) == null) {
            val soundUri = ToneCatalog.resolvePlayableUri(context, toneId, ToneCategory.CHAT)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notif_channel_chat_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_chat_desc)
                setSound(soundUri, audioAttrs)
                enableVibration(vibrate)
                if (vibrate) {
                    vibrationPattern = longArrayOf(0L, 140L, 80L, 140L)
                }
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
            cleanupOldChannels(manager, CHAT_CHANNEL_PREFIX, keepChannelId = channelId, legacyId = LEGACY_CHANNEL_CHAT_ID)
        }
        return channelId
    }

    /**
     * Incoming call heads-up channel.
     * Sound is driven directly by NotificationTonePlayer inside PartnerNotificationService
     * (MediaPlayer + AudioFocus + looping) so the ringtone is 100% audible across all devices
     * and while other apps are open without double-audio echo.
     */
    fun ensureCallChannel(vibrate: Boolean): String {
        val channelId = callChannelId(vibrate)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return channelId

        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notif_channel_call_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_call_desc)
                setSound(null, null)
                enableVibration(vibrate)
                if (vibrate) {
                    vibrationPattern = longArrayOf(0L, 450L, 350L, 450L)
                }
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
            cleanupOldChannels(manager, CALL_CHANNEL_PREFIX, keepChannelId = channelId, legacyId = LEGACY_CHANNEL_CALL_ID)
        }
        return channelId
    }

    private fun cleanupOldChannels(
        manager: NotificationManager,
        prefix: String,
        keepChannelId: String,
        legacyId: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            manager.deleteNotificationChannel(legacyId)
            manager.notificationChannels
                .map { it.id }
                .filter { it.startsWith(prefix) && it != keepChannelId }
                .forEach { oldId -> manager.deleteNotificationChannel(oldId) }
        }
    }

    fun buildForegroundServiceNotification(): Notification {
        ensureServiceChannel()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            SERVICE_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_service_title))
            .setContentText(context.getString(R.string.notif_service_body))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openPending)
            .build()
    }

    fun showChatNotification(
        chatId: String,
        title: String,
        messagePreview: String,
        toneId: String = ToneCatalog.DEFAULT_CHAT_TONE_ID,
        vibrate: Boolean = true,
        subText: String? = null
    ) {
        if (!canPostNotifications()) return
        val channelId = ensureChatChannel(toneId, vibrate)
        val soundUri = ToneCatalog.resolvePlayableUri(context, toneId, ToneCategory.CHAT)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT_ID, chatId)
        }
        val requestCode = chatId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messagePreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messagePreview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(soundUri)
            if (vibrate) {
                builder.setVibrate(longArrayOf(0L, 140L, 80L, 140L))
            }
        }
        if (ToneCatalog.isSilent(toneId) && !vibrate) {
            builder.setSilent(true)
        }
        if (!subText.isNullOrBlank()) {
            builder.setSubText(subText)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(chatNotificationId(chatId), builder.build())
        }.onFailure { err ->
            Log.e(TAG, "Failed posting chat notification: ${err.message}", err)
        }
    }

    fun cancelChatNotification(chatId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(chatNotificationId(chatId))
        }
    }

    fun showIncomingCallNotification(
        callId: String,
        callerName: String,
        callType: CallType,
        vibrate: Boolean = true,
        isTestPreview: Boolean = false
    ) {
        if (!canPostNotifications()) return
        val channelId = ensureCallChannel(vibrate)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALL_ACTION, ACTION_CALL_OPEN)
        }
        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALL_ACTION, ACTION_CALL_ACCEPT)
        }

        val declineIntent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = CallNotificationActionReceiver.ACTION_DECLINE_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_IS_TEST_PREVIEW, isTestPreview)
        }

        val baseCode = callId.hashCode()
        val openPending = PendingIntent.getActivity(
            context,
            baseCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val acceptPending = PendingIntent.getActivity(
            context,
            baseCode + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declinePending = PendingIntent.getBroadcast(
            context,
            baseCode + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = if (callType == CallType.VIDEO) {
            context.getString(R.string.call_incoming_video)
        } else {
            context.getString(R.string.call_incoming_voice)
        }

        // Standard high-priority notification with Accept & Decline actions works cleanly on all Android
        // versions including Android 14/15/16 without throwing CallStyle fullScreenIntent exceptions.
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(callerName)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$callerName — $subtitle"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(!isTestPreview)
            .setAutoCancel(isTestPreview)
            .setOnlyAlertOnce(false)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_call, context.getString(R.string.call_accept), acceptPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.call_decline), declinePending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(INCOMING_CALL_NOTIFICATION_ID, notification)
        }.onFailure { err ->
            Log.e(TAG, "Failed posting incoming call notification: ${err.message}", err)
        }
    }

    fun cancelIncomingCallNotification() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(INCOMING_CALL_NOTIFICATION_ID)
        }
    }

    private fun chatChannelId(toneId: String, vibrate: Boolean): String {
        val key = "${toneId}_v$vibrate".hashCode().toUInt().toString(16)
        return "${CHAT_CHANNEL_PREFIX}$key"
    }

    private fun callChannelId(vibrate: Boolean): String {
        val key = "call_v3_v$vibrate".hashCode().toUInt().toString(16)
        return "${CALL_CHANNEL_PREFIX}$key"
    }

    private fun chatNotificationId(chatId: String): Int {
        return CHAT_NOTIFICATION_ID_BASE + (chatId.hashCode() and 0x7FFF)
    }

    companion object {
        private const val TAG = "AppNotificationManager"

        const val CHANNEL_SERVICE_ID = "partner_bank_bg_service_v1"
        private const val LEGACY_CHANNEL_CHAT_ID = "partner_bank_chat_alerts_v1"
        private const val LEGACY_CHANNEL_CALL_ID = "partner_bank_call_alerts_v1"
        private const val CHAT_CHANNEL_PREFIX = "partner_bank_chat_v2_"
        private const val CALL_CHANNEL_PREFIX = "partner_bank_call_v2_"

        const val EXTRA_OPEN_CHAT_ID = "extra_open_chat_id"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALL_ACTION = "extra_call_action"
        const val EXTRA_IS_TEST_PREVIEW = "extra_is_test_preview"

        const val ACTION_CALL_OPEN = "open"
        const val ACTION_CALL_ACCEPT = "accept"
        const val ACTION_CALL_DECLINE = "decline"

        const val SERVICE_NOTIFICATION_ID = 9000
        private const val INCOMING_CALL_NOTIFICATION_ID = 9001
        private const val CHAT_NOTIFICATION_ID_BASE = 10_000
    }
}
