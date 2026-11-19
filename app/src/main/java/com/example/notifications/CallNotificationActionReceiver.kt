package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.R
import com.example.data.CallStatus
import com.example.data.SapaChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles background incoming-call notification actions (such as tapping Decline
 * while the user is inside another app) without forcing the user out of their current app.
 */
class CallNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECLINE_CALL) return

        val callId = intent.getStringExtra(AppNotificationManager.EXTRA_CALL_ID)
        val isTestPreview = intent.getBooleanExtra(AppNotificationManager.EXTRA_IS_TEST_PREVIEW, false)

        val notificationManager = AppNotificationManager(context.applicationContext)
        notificationManager.cancelIncomingCallNotification()

        if (isTestPreview || callId.isNullOrBlank()) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val databaseId = context.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = SapaChatRepository(db)
                repo.finishCall(callId, CallStatus.DECLINED, 0L)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed declining call $callId from notification", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "CallNotifActionReceiver"
        const val ACTION_DECLINE_CALL = "com.example.notifications.ACTION_DECLINE_CALL"
    }
}
