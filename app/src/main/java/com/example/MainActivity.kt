package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.performSignOut
import com.example.data.PendingIdAuthRequest
import com.example.data.SapaChatRepository
import com.example.data.ThemePreferences
import com.example.notifications.AppNotificationManager
import com.example.notifications.AppSessionState
import com.example.notifications.NotificationPreferences
import com.example.notifications.NotificationSettings
import com.example.notifications.NotificationTonePlayer
import com.example.notifications.PartnerNotificationService
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.SapaChatHomeApp
import com.example.ui.theme.SapaChatTheme
import com.example.viewmodel.SapaChatViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "the_pathner_prefs"
private const val KEY_LAST_PATHNER_ID = "last_pathner_id"

sealed interface NotificationLaunchAction {
    data class OpenChat(val chatId: String) : NotificationLaunchAction
    data class CallAction(val callId: String, val action: String) : NotificationLaunchAction
}

class MainActivity : ComponentActivity() {

    private lateinit var notificationPrefs: NotificationPreferences
    private lateinit var appNotificationManager: AppNotificationManager
    private lateinit var tonePlayer: NotificationTonePlayer

    private val isAppInForegroundFlow = MutableStateFlow(true)
    private val pendingNotificationActionFlow = MutableStateFlow<NotificationLaunchAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        val themePrefs = ThemePreferences(applicationContext)
        notificationPrefs = NotificationPreferences(applicationContext)
        appNotificationManager = AppNotificationManager(applicationContext)
        tonePlayer = NotificationTonePlayer(applicationContext)
        captureNotificationIntent(intent)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkModeOverride by themePrefs.darkModeOverrideFlow
                .collectAsStateWithLifecycle(initialValue = null)
            val isDarkTheme = darkModeOverride ?: systemDark
            val isAppInForeground by isAppInForegroundFlow.collectAsStateWithLifecycle()
            val pendingNotificationAction by pendingNotificationActionFlow.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var appliedDarkTheme by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(isDarkTheme) {
                if (appliedDarkTheme != isDarkTheme) {
                    appliedDarkTheme = isDarkTheme
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                        navigationBarStyle = if (isDarkTheme) {
                            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                        }
                    )
                }
            }

            SapaChatTheme(darkTheme = isDarkTheme) {
                SapaChatRoot(
                    isDarkTheme = isDarkTheme,
                    onToggleDarkMode = { enabled ->
                        scope.launch {
                            themePrefs.setDarkMode(enabled)
                        }
                    },
                    notificationPrefs = notificationPrefs,
                    appNotificationManager = appNotificationManager,
                    tonePlayer = tonePlayer,
                    isAppInForeground = isAppInForeground,
                    pendingNotificationAction = pendingNotificationAction,
                    onConsumeNotificationAction = { pendingNotificationActionFlow.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        isAppInForegroundFlow.value = true
        AppSessionState.setAppInForeground(true)
    }

    override fun onStop() {
        super.onStop()
        isAppInForegroundFlow.value = false
        AppSessionState.setAppInForeground(false)
    }

    override fun onDestroy() {
        if (::tonePlayer.isInitialized) {
            tonePlayer.release()
        }
        super.onDestroy()
    }

    private fun captureNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val callId = intent.getStringExtra(AppNotificationManager.EXTRA_CALL_ID)
        if (!callId.isNullOrBlank()) {
            val action = intent.getStringExtra(AppNotificationManager.EXTRA_CALL_ACTION)
                ?: AppNotificationManager.ACTION_CALL_OPEN
            pendingNotificationActionFlow.value = NotificationLaunchAction.CallAction(callId, action)
            intent.removeExtra(AppNotificationManager.EXTRA_CALL_ID)
            intent.removeExtra(AppNotificationManager.EXTRA_CALL_ACTION)
            return
        }
        val chatId = intent.getStringExtra(AppNotificationManager.EXTRA_OPEN_CHAT_ID)
        if (!chatId.isNullOrBlank()) {
            pendingNotificationActionFlow.value = NotificationLaunchAction.OpenChat(chatId)
            intent.removeExtra(AppNotificationManager.EXTRA_OPEN_CHAT_ID)
        }
    }
}

@Composable
fun SapaChatRoot(
    auth: FirebaseAuth = Firebase.auth,
    isDarkTheme: Boolean = false,
    onToggleDarkMode: (Boolean) -> Unit = {},
    notificationPrefs: NotificationPreferences? = null,
    appNotificationManager: AppNotificationManager? = null,
    tonePlayer: NotificationTonePlayer? = null,
    isAppInForeground: Boolean = true,
    pendingNotificationAction: NotificationLaunchAction? = null,
    onConsumeNotificationAction: () -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    val context = LocalContext.current
    val resolvedNotificationPrefs = remember(context, notificationPrefs) {
        notificationPrefs ?: NotificationPreferences(context.applicationContext)
    }
    val resolvedNotificationManager = remember(context, appNotificationManager) {
        appNotificationManager ?: AppNotificationManager(context.applicationContext)
    }
    val resolvedTonePlayer = remember(context, tonePlayer) {
        tonePlayer ?: NotificationTonePlayer(context.applicationContext)
    }
    val notificationSettings by resolvedNotificationPrefs.settingsFlow
        .collectAsStateWithLifecycle(initialValue = NotificationSettings())

    LaunchedEffect(user?.uid, notificationSettings.shouldRunBackgroundListener, isAppInForeground) {
        val isSignedIn = user != null
        if (!isSignedIn || !notificationSettings.shouldRunBackgroundListener) {
            PartnerNotificationService.syncServiceState(
                context = context,
                isSignedIn = false,
                shouldRun = false
            )
        } else if (isAppInForeground) {
            // Allow first frame / SurfaceSyncGroup to commit before starting background service & channel IPC
            delay(350L)
            withContext(Dispatchers.IO) {
                resolvedNotificationManager.ensureChannels(notificationSettings)
            }
            PartnerNotificationService.syncServiceState(
                context = context,
                isSignedIn = true,
                shouldRun = true
            )
        }
    }

    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var savedPathnerId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) {
        val loaded = withContext(Dispatchers.IO) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_PATHNER_ID, null)
        }
        if (loaded != null) {
            savedPathnerId = loaded
        }
    }
    var pendingIdAuth by remember { mutableStateOf<PendingIdAuthRequest?>(null) }
    var authErrorBanner by remember { mutableStateOf<String?>(null) }
    var openRegisterTab by remember { mutableStateOf(false) }

    if (user == null) {
        AuthScreen(
            savedPathnerId = savedPathnerId,
            externalErrorMessage = authErrorBanner,
            initialOnRegisterTab = openRegisterTab,
            isDarkTheme = isDarkTheme,
            onToggleDarkMode = onToggleDarkMode,
            onClearExternalError = { authErrorBanner = null },
            onPrepareIdAuth = { request ->
                pendingIdAuth = request
                if (request != null) {
                    authErrorBanner = null
                }
            },
            onAuthSuccess = { /* authStateFlow transitions automatically */ }
        )
    } else {
        val uid = user.uid
        val idAuthSnapshot = remember(uid) { pendingIdAuth }
        key(uid) {
            val viewModel: SapaChatViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        SapaChatViewModel(
                            repository = SapaChatRepository(db),
                            currentUserId = uid,
                            pendingIdAuth = idAuthSnapshot
                        )
                    }
                }
            )

            val resolvedPathnerId by viewModel.resolvedPathnerId.collectAsStateWithLifecycle()
            val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
            val authRejectedEvent by viewModel.authRejectedEvent.collectAsStateWithLifecycle()
            val isVerifyingIdAuth by viewModel.isVerifyingIdAuth.collectAsStateWithLifecycle()

            // Persist user's created/active Pathner ID locally so they can easily log back in later
            LaunchedEffect(resolvedPathnerId, myProfile?.pathnerId) {
                val activeId = resolvedPathnerId?.takeIf { it.isNotBlank() }
                    ?: myProfile?.pathnerId?.takeIf { it.isNotBlank() }
                if (!activeId.isNullOrBlank() && activeId != savedPathnerId) {
                    savedPathnerId = activeId
                    withContext(Dispatchers.IO) {
                        context.applicationContext
                            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY_LAST_PATHNER_ID, activeId)
                            .apply()
                    }
                }
            }

            // If entered login/register ID was rejected (not found or already taken), return cleanly to AuthScreen
            LaunchedEffect(authRejectedEvent) {
                val rejected = authRejectedEvent ?: return@LaunchedEffect
                authErrorBanner = rejected.reason
                openRegisterTab = rejected.suggestRegister
                pendingIdAuth = null
                viewModel.consumeAuthRejectedEvent()
                performSignOut(
                    credentialManager = credentialManager,
                    scope = scope,
                    onBeforeSignOut = {}
                )
            }

            if (isVerifyingIdAuth || authRejectedEvent != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Memverifikasi ID Partner Bank...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                SapaChatHomeApp(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                    onToggleDarkMode = onToggleDarkMode,
                    notificationPrefs = resolvedNotificationPrefs,
                    appNotificationManager = resolvedNotificationManager,
                    tonePlayer = resolvedTonePlayer,
                    isAppInForeground = isAppInForeground,
                    pendingNotificationAction = pendingNotificationAction,
                    onConsumeNotificationAction = onConsumeNotificationAction,
                    onSignOut = {
                        PartnerNotificationService.syncServiceState(
                            context = context,
                            isSignedIn = false,
                            shouldRun = false
                        )
                        AppSessionState.setActiveChatId(null)
                        AppSessionState.muteRingingCall(null)
                        resolvedTonePlayer.stopIncomingCallRingtone()
                        resolvedNotificationManager.cancelIncomingCallNotification()
                        pendingIdAuth = null
                        authErrorBanner = null
                        openRegisterTab = false
                        performSignOut(
                            credentialManager = credentialManager,
                            scope = scope,
                            onBeforeSignOut = { viewModel.markOfflineBeforeSignOut() }
                        )
                    }
                )
            }
        }
    }
}
