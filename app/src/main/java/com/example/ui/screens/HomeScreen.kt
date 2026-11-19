package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.NotificationLaunchAction
import com.example.R
import com.example.data.CallSession
import com.example.data.CallStatus
import com.example.data.CallType
import com.example.data.ChatRoom
import com.example.data.MediaHelper
import com.example.data.PublicProfile
import com.example.data.UiState
import com.example.notifications.AppNotificationManager
import com.example.notifications.AppSessionState
import com.example.notifications.NotificationPreferences
import com.example.notifications.NotificationSettings
import com.example.notifications.NotificationTonePlayer
import com.example.notifications.PartnerNotificationService
import com.example.ui.components.AppInfoSheet
import com.example.ui.components.BulkDeleteCategory
import com.example.ui.components.BulkDeleteConfirmDialog
import com.example.ui.components.InAppChatNotificationBanner
import com.example.ui.components.InAppTestCallBanner
import com.example.ui.components.IncomingChatAlert
import com.example.ui.components.MessageReceiptIndicator
import com.example.ui.components.NotificationSettingsSheet
import com.example.ui.components.SelectionActionTopBar
import com.example.ui.components.SelectionCheckIndicator
import com.example.ui.components.SelectionQuickActionStrip
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeDate
import com.example.ui.theme.CoralCallEnd
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintVibrant
import com.example.viewmodel.SapaChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HomeTab {
    CHATS, CALLS, CONTACTS, PROFILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SapaChatHomeApp(
    viewModel: SapaChatViewModel,
    isDarkTheme: Boolean = false,
    onToggleDarkMode: (Boolean) -> Unit = {},
    notificationPrefs: NotificationPreferences? = null,
    appNotificationManager: AppNotificationManager? = null,
    tonePlayer: NotificationTonePlayer? = null,
    isAppInForeground: Boolean = true,
    pendingNotificationAction: NotificationLaunchAction? = null,
    onConsumeNotificationAction: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    val isBackgroundServiceRunning by AppSessionState.isBackgroundServiceRunning
        .collectAsStateWithLifecycle()

    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    val myPrivateUser by viewModel.myPrivateUser.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val activeCallId by viewModel.activeCallId.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val newlyCreatedPathnerId by viewModel.newlyCreatedPathnerId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember { mutableStateOf(HomeTab.CHATS) }
    var showFindByIdDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showTopBarMenu by remember { mutableStateOf(false) }
    var showAppInfoSheet by remember { mutableStateOf(false) }
    var showNotificationSettingsSheet by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    var activeChatBanner by remember { mutableStateOf<IncomingChatAlert?>(null) }
    var isTestingCallBanner by remember { mutableStateOf(false) }
    var incomingCallRingtoneMuted by remember { mutableStateOf(false) }
    var hasAutoPromptedNotifPermission by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember(context) {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            resolvedNotificationManager.ensureChannels(notificationSettings)
            if (notificationSettings.shouldRunBackgroundListener) {
                PartnerNotificationService.syncServiceState(
                    context = context,
                    isSignedIn = true,
                    shouldRun = true
                )
            }
            viewModel.showNotice("Izin notifikasi diaktifkan.")
        }
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = true
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission &&
            !hasAutoPromptedNotifPermission
        ) {
            hasAutoPromptedNotifPermission = true
            delay(600L)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val exitSelectionMode = {
        isSelectionMode = false
        selectedItemIds = emptySet()
        showBulkDeleteConfirm = false
    }

    LaunchedEffect(currentTab) {
        isSelectionMode = false
        selectedItemIds = emptySet()
        showBulkDeleteConfirm = false
    }

    BackHandler(enabled = isSelectionMode) {
        exitSelectionMode()
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val chatsList = (chatsState as? UiState.Success)?.data.orEmpty()
    val callsList = (callsState as? UiState.Success)?.data.orEmpty()
    val contactsList = (contactsState as? UiState.Success)?.data.orEmpty()
    val hiddenContactsCount = myPrivateUser?.hiddenContactUids?.size ?: 0

    val activeTabSelectableIds: List<String> = when (currentTab) {
        HomeTab.CHATS -> chatsList.map { it.id }
        HomeTab.CALLS -> callsList.map { it.id }
        HomeTab.CONTACTS -> contactsList.map { it.userId }
        HomeTab.PROFILE -> emptyList()
    }
    val activeBulkCategory: BulkDeleteCategory? = when (currentTab) {
        HomeTab.CHATS -> BulkDeleteCategory.CHATS
        HomeTab.CALLS -> BulkDeleteCategory.CALLS
        HomeTab.CONTACTS -> BulkDeleteCategory.CONTACTS
        HomeTab.PROFILE -> null
    }

    // Keep selected IDs synced with currently available items
    LaunchedEffect(activeTabSelectableIds, isSelectionMode) {
        if (isSelectionMode) {
            val validSet = activeTabSelectableIds.toSet()
            val pruned = selectedItemIds.intersect(validSet)
            if (pruned != selectedItemIds) {
                selectedItemIds = pruned
            }
            if (activeTabSelectableIds.isEmpty()) {
                isSelectionMode = false
            }
        }
    }

    val toggleItemSelection: (String) -> Unit = { id ->
        val updated = if (id in selectedItemIds) selectedItemIds - id else selectedItemIds + id
        selectedItemIds = updated
        if (updated.isEmpty()) {
            isSelectionMode = false
        }
    }

    val startSelectionWithItem: (String) -> Unit = { id ->
        isSelectionMode = true
        selectedItemIds = selectedItemIds + id
    }

    val toggleSelectAll: () -> Unit = {
        val allIds = activeTabSelectableIds.toSet()
        selectedItemIds = if (allIds.isNotEmpty() && selectedItemIds.size >= allIds.size) {
            emptySet()
        } else {
            allIds
        }
    }

    val selectedChat = remember(chatsList, selectedChatId) {
        chatsList.firstOrNull { it.id == selectedChatId }
    }

    // Watch for either active call initiated by user or incoming ringing call from another user
    val incomingOrActiveCall = remember(callsList, activeCallId, viewModel.currentUserId) {
        val explicit = activeCallId?.let { id ->
            callsList.firstOrNull {
                it.id == id && it.parsedStatus in setOf(CallStatus.RINGING, CallStatus.ACTIVE)
            }
        }
        explicit ?: callsList.firstOrNull {
            it.calleeId == viewModel.currentUserId &&
                !it.isSelfCall() &&
                it.parsedStatus == CallStatus.RINGING
        }
    }

    val incomingRingingCall = remember(incomingOrActiveCall, viewModel.currentUserId) {
        incomingOrActiveCall?.takeIf {
            it.calleeId == viewModel.currentUserId &&
                !it.isSelfCall() &&
                it.parsedStatus == CallStatus.RINGING
        }
    }

    // Handle deep-link actions from Android chat or call notifications
    LaunchedEffect(pendingNotificationAction, callsList) {
        val action = pendingNotificationAction ?: return@LaunchedEffect
        when (action) {
            is NotificationLaunchAction.OpenChat -> {
                if (action.chatId != "test_chat_preview") {
                    currentTab = HomeTab.CHATS
                    viewModel.selectChat(action.chatId)
                    resolvedNotificationManager.cancelChatNotification(action.chatId)
                }
                onConsumeNotificationAction()
            }
            is NotificationLaunchAction.CallAction -> {
                if (action.callId == "test_call_preview") {
                    isTestingCallBanner = false
                    resolvedTonePlayer.stopIncomingCallRingtone()
                    resolvedNotificationManager.cancelIncomingCallNotification()
                    onConsumeNotificationAction()
                } else if (callsList.any { it.id == action.callId }) {
                    resolvedTonePlayer.stopIncomingCallRingtone()
                    resolvedNotificationManager.cancelIncomingCallNotification()
                    viewModel.handleCallNotificationAction(action.callId, action.action)
                    onConsumeNotificationAction()
                }
            }
        }
    }

    // Clear chat notification and inform background service which chat is open
    LaunchedEffect(selectedChatId) {
        AppSessionState.setActiveChatId(selectedChatId)
        if (!selectedChatId.isNullOrBlank()) {
            resolvedNotificationManager.cancelChatNotification(selectedChatId!!)
            if (activeChatBanner?.chatId == selectedChatId) {
                activeChatBanner = null
            }
        }
    }

    // Receive foreground chat banner events emitted by PartnerNotificationService (zero duplicate alerts)
    LaunchedEffect(Unit) {
        AppSessionState.foregroundChatAlerts.collect { alert ->
            if (isAppInForeground && selectedChatId != alert.chatId) {
                activeChatBanner = alert
            }
        }
    }

    // Fallback chat notification listener only when background service is disabled
    LaunchedEffect(
        viewModel,
        notificationSettings,
        selectedChatId,
        isAppInForeground,
        isBackgroundServiceRunning
    ) {
        if (isBackgroundServiceRunning) return@LaunchedEffect
        viewModel.incomingChatAlerts.collect { alert ->
            if (!notificationSettings.chatNotificationsEnabled) return@collect
            val isViewingSameChat = isAppInForeground && selectedChatId == alert.chatId
            if (isViewingSameChat) return@collect

            if (resolvedNotificationManager.canPostNotifications()) {
                resolvedNotificationManager.showChatNotification(
                    chatId = alert.chatId,
                    title = alert.title,
                    messagePreview = alert.messagePreview,
                    toneId = notificationSettings.chatToneId,
                    vibrate = notificationSettings.vibrationEnabled
                )
            } else {
                resolvedTonePlayer.playChatNotificationTone(
                    toneId = notificationSettings.chatToneId,
                    vibrate = notificationSettings.vibrationEnabled
                )
            }
            if (isAppInForeground) {
                activeChatBanner = alert
            }
        }
    }

    // Auto-dismiss in-app chat banner after a brief moment
    LaunchedEffect(activeChatBanner?.id) {
        if (activeChatBanner != null) {
            delay(4_800L)
            activeChatBanner = null
        }
    }

    // Sync call ringtone mute state with PartnerNotificationService
    LaunchedEffect(incomingRingingCall?.id) {
        incomingCallRingtoneMuted = false
        AppSessionState.muteRingingCall(null)
    }

    // Fallback call notification listener only when background service is disabled
    LaunchedEffect(
        incomingRingingCall?.id,
        notificationSettings.callNotificationsEnabled,
        notificationSettings.callToneId,
        notificationSettings.vibrationEnabled,
        incomingCallRingtoneMuted,
        isBackgroundServiceRunning
    ) {
        if (isBackgroundServiceRunning) return@LaunchedEffect
        val ringing = incomingRingingCall
        if (ringing != null && notificationSettings.callNotificationsEnabled) {
            isTestingCallBanner = false
            if (resolvedNotificationManager.canPostNotifications()) {
                resolvedTonePlayer.stopIncomingCallRingtone()
                resolvedNotificationManager.showIncomingCallNotification(
                    callId = ringing.id,
                    callerName = ringing.peerName(viewModel.currentUserId),
                    callType = ringing.parsedType,
                    toneId = notificationSettings.callToneId,
                    vibrate = notificationSettings.vibrationEnabled,
                    isTestPreview = false,
                    silentUpdate = incomingCallRingtoneMuted
                )
            } else if (!incomingCallRingtoneMuted) {
                resolvedTonePlayer.startIncomingCallRingtone(
                    toneId = notificationSettings.callToneId,
                    vibrate = notificationSettings.vibrationEnabled
                )
            } else {
                resolvedTonePlayer.stopIncomingCallRingtone()
            }
        } else if (!isTestingCallBanner) {
            resolvedTonePlayer.stopIncomingCallRingtone()
            resolvedNotificationManager.cancelIncomingCallNotification()
        }
    }

    // Auto-stop test call preview after 8 seconds so it never rings indefinitely
    LaunchedEffect(isTestingCallBanner) {
        if (isTestingCallBanner) {
            delay(8_000L)
            isTestingCallBanner = false
            resolvedTonePlayer.stopIncomingCallRingtone()
            resolvedNotificationManager.cancelIncomingCallNotification()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            resolvedTonePlayer.stopIncomingCallRingtone()
            resolvedTonePlayer.stopPreview()
        }
    }

    LaunchedEffect(incomingOrActiveCall?.chatId) {
        viewModel.setCallChatId(incomingOrActiveCall?.chatId)
    }

    // Unified Video/Voice Call + Live Chat screen when a call is active or ringing
    if (incomingOrActiveCall != null) {
        val chatForCall = chatsList.firstOrNull { it.id == incomingOrActiveCall.chatId }
            ?: ChatRoom(
                id = incomingOrActiveCall.chatId,
                title = incomingOrActiveCall.peerName(viewModel.currentUserId),
                groupChat = false,
                createdBy = incomingOrActiveCall.callerId,
                participantUids = incomingOrActiveCall.participantUids.ifEmpty {
                    listOf(incomingOrActiveCall.callerId, incomingOrActiveCall.calleeId).distinct()
                }
            )
        VideoCallScreen(
            call = incomingOrActiveCall,
            chat = chatForCall,
            viewModel = viewModel,
            ringtoneTitle = notificationSettings.callToneTitle,
            isRingtoneMuted = incomingCallRingtoneMuted,
            onMuteRingtone = {
                incomingCallRingtoneMuted = true
                AppSessionState.muteRingingCall(incomingOrActiveCall.id)
                resolvedTonePlayer.stopIncomingCallRingtone()
            },
            onAccept = {
                AppSessionState.muteRingingCall(null)
                resolvedTonePlayer.stopIncomingCallRingtone()
                resolvedNotificationManager.cancelIncomingCallNotification()
                viewModel.acceptCall(incomingOrActiveCall)
            },
            onDecline = {
                AppSessionState.muteRingingCall(null)
                resolvedTonePlayer.stopIncomingCallRingtone()
                resolvedNotificationManager.cancelIncomingCallNotification()
                viewModel.declineCall(incomingOrActiveCall)
            },
            onToggleMedia = { cam, muted -> viewModel.updateCallMedia(incomingOrActiveCall, cam, muted) },
            onEndCall = { secs ->
                resolvedTonePlayer.stopIncomingCallRingtone()
                resolvedNotificationManager.cancelIncomingCallNotification()
                viewModel.endActiveCall(incomingOrActiveCall, secs, chatForCall)
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        // Compact phone mode: full-screen chat detail when a chat is selected
        if (!isExpanded && selectedChat != null) {
            ChatScreen(
                chat = selectedChat,
                viewModel = viewModel,
                showBackButton = true,
                onBack = { viewModel.selectChat(null) },
                onStartCall = { type -> viewModel.startCall(selectedChat, type) {} }
            )
            return@BoxWithConstraints
        }

        val navContent: @Composable (Modifier) -> Unit = { paneModifier ->
            Scaffold(
                modifier = paneModifier,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (isSelectionMode && activeBulkCategory != null) {
                        SelectionActionTopBar(
                            selectedCount = selectedItemIds.size,
                            totalCount = activeTabSelectableIds.size,
                            onClose = exitSelectionMode,
                            onToggleSelectAll = toggleSelectAll,
                            onDeleteClick = {
                                if (selectedItemIds.isNotEmpty()) {
                                    showBulkDeleteConfirm = true
                                }
                            }
                        )
                    } else {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = EmeraldDeep,
                                titleContentColor = Color.White,
                                actionIconContentColor = Color.White
                            ),
                            title = {
                                Text(
                                    text = when (currentTab) {
                                        HomeTab.CHATS -> stringResource(R.string.app_name)
                                        HomeTab.CALLS -> stringResource(R.string.nav_calls)
                                        HomeTab.CONTACTS -> stringResource(R.string.nav_contacts)
                                        HomeTab.PROFILE -> stringResource(R.string.nav_profile)
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            actions = {
                                if (activeBulkCategory != null && activeTabSelectableIds.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            isSelectionMode = true
                                            selectedItemIds = emptySet()
                                        },
                                        modifier = Modifier.testTag("enter_selection_mode_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteSweep,
                                            contentDescription = stringResource(R.string.select_items_desc)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onToggleDarkMode(!isDarkTheme) },
                                    modifier = Modifier.testTag("topbar_dark_mode_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                        contentDescription = stringResource(
                                            if (isDarkTheme) R.string.dark_mode_toggle_to_light_desc
                                            else R.string.dark_mode_toggle_to_dark_desc
                                        )
                                    )
                                }
                                Box {
                                    IconButton(
                                        onClick = { showTopBarMenu = true },
                                        modifier = Modifier.testTag("home_overflow_menu_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = stringResource(R.string.app_info_more_options_desc)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showTopBarMenu,
                                        onDismissRequest = { showTopBarMenu = false }
                                    ) {
                                        if (activeBulkCategory != null && activeTabSelectableIds.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.select_to_delete_menu_action)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteSweep,
                                                        contentDescription = null,
                                                        tint = EmeraldPrimary
                                                    )
                                                },
                                                onClick = {
                                                    showTopBarMenu = false
                                                    isSelectionMode = true
                                                    selectedItemIds = emptySet()
                                                },
                                                modifier = Modifier.testTag("menu_select_delete_item")
                                            )
                                        }
                                        if (currentTab == HomeTab.CHATS || currentTab == HomeTab.CONTACTS) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.contacts_add_by_id)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PersonAdd,
                                                        contentDescription = null,
                                                        tint = EmeraldPrimary
                                                    )
                                                },
                                                onClick = {
                                                    showTopBarMenu = false
                                                    showFindByIdDialog = true
                                                },
                                                modifier = Modifier.testTag("find_by_id_topbar_button")
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.new_group_action)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.GroupAdd,
                                                        contentDescription = null,
                                                        tint = EmeraldPrimary
                                                    )
                                                },
                                                onClick = {
                                                    showTopBarMenu = false
                                                    showCreateGroupDialog = true
                                                },
                                                modifier = Modifier.testTag("create_group_topbar_button")
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.notif_menu_title)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.NotificationsActive,
                                                    contentDescription = null,
                                                    tint = EmeraldPrimary
                                                )
                                            },
                                            onClick = {
                                                showTopBarMenu = false
                                                showNotificationSettingsSheet = true
                                            },
                                            modifier = Modifier.testTag("menu_notification_settings_item")
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.app_info_menu_title)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Info,
                                                    contentDescription = null,
                                                    tint = EmeraldPrimary
                                                )
                                            },
                                            onClick = {
                                                showTopBarMenu = false
                                                showAppInfoSheet = true
                                            },
                                            modifier = Modifier.testTag("menu_app_info_item")
                                        )
                                    }
                                }
                            }
                        )
                    }
                },
                floatingActionButton = {
                    if (currentTab == HomeTab.CHATS && !isSelectionMode) {
                        FloatingActionButton(
                            onClick = { currentTab = HomeTab.CONTACTS },
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("new_chat_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddComment,
                                contentDescription = stringResource(R.string.new_chat_fab)
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!isExpanded) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp
                        ) {
                            NavigationBarItem(
                                selected = currentTab == HomeTab.CHATS,
                                onClick = { currentTab = HomeTab.CHATS },
                                icon = {
                                    Icon(
                                        if (currentTab == HomeTab.CHATS) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = stringResource(R.string.nav_chats)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_chats)) },
                                modifier = Modifier.testTag("nav_tab_chats")
                            )
                            NavigationBarItem(
                                selected = currentTab == HomeTab.CALLS,
                                onClick = { currentTab = HomeTab.CALLS },
                                icon = {
                                    Icon(
                                        if (currentTab == HomeTab.CALLS) Icons.Rounded.Call else Icons.Outlined.Call,
                                        contentDescription = stringResource(R.string.nav_calls)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_calls)) },
                                modifier = Modifier.testTag("nav_tab_calls")
                            )
                            NavigationBarItem(
                                selected = currentTab == HomeTab.CONTACTS,
                                onClick = { currentTab = HomeTab.CONTACTS },
                                icon = {
                                    Icon(
                                        if (currentTab == HomeTab.CONTACTS) Icons.Rounded.People else Icons.Outlined.PeopleOutline,
                                        contentDescription = stringResource(R.string.nav_contacts)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_contacts)) },
                                modifier = Modifier.testTag("nav_tab_contacts")
                            )
                            NavigationBarItem(
                                selected = currentTab == HomeTab.PROFILE,
                                onClick = { currentTab = HomeTab.PROFILE },
                                icon = {
                                    Icon(
                                        if (currentTab == HomeTab.PROFILE) Icons.Rounded.Person else Icons.Outlined.PersonOutline,
                                        contentDescription = stringResource(R.string.nav_profile)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_profile)) },
                                modifier = Modifier.testTag("nav_tab_profile")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isSelectionMode && activeBulkCategory != null) {
                        SelectionQuickActionStrip(
                            selectedCount = selectedItemIds.size,
                            totalCount = activeTabSelectableIds.size,
                            onToggleSelectAll = toggleSelectAll,
                            onDeleteClick = {
                                if (selectedItemIds.isNotEmpty()) {
                                    showBulkDeleteConfirm = true
                                }
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (currentTab) {
                            HomeTab.CHATS -> ChatsTabContent(
                                chatsState = chatsState,
                                currentUserId = viewModel.currentUserId,
                                selectedChatId = selectedChatId,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedItemIds,
                                onToggleSelect = toggleItemSelection,
                                onLongPressSelect = startSelectionWithItem,
                                onChatClick = { chat -> viewModel.selectChat(chat.id) },
                                onOpenSelfChat = { viewModel.openSelfChat {} }
                            )
                            HomeTab.CALLS -> CallsTabContent(
                                callsState = callsState,
                                chatsList = chatsList,
                                currentUserId = viewModel.currentUserId,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedItemIds,
                                onToggleSelect = toggleItemSelection,
                                onLongPressSelect = startSelectionWithItem,
                                onStartQuickVideoChat = {
                                    val existingSelf = chatsList.firstOrNull { it.isSelfChat(viewModel.currentUserId) }
                                    if (existingSelf != null) {
                                        viewModel.startCall(existingSelf, CallType.VIDEO) {}
                                    } else {
                                        viewModel.openSelfChat { openedId ->
                                            val created = chatsList.firstOrNull { it.id == openedId }
                                                ?: ChatRoom(
                                                    id = openedId,
                                                    title = "Catatan Pribadi (Anda)",
                                                    groupChat = false,
                                                    createdBy = viewModel.currentUserId,
                                                    participantUids = listOf(viewModel.currentUserId)
                                                )
                                            viewModel.startCall(created, CallType.VIDEO) {}
                                        }
                                    }
                                },
                                onRedial = { call ->
                                    val targetChat = chatsList.firstOrNull { it.id == call.chatId }
                                    if (targetChat != null) {
                                        viewModel.startCall(targetChat, call.parsedType) {}
                                    } else {
                                        viewModel.openSelfChat { }
                                    }
                                }
                            )
                            HomeTab.CONTACTS -> ContactsTabContent(
                                contactsState = contactsState,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedItemIds,
                                hiddenContactsCount = hiddenContactsCount,
                                onToggleSelect = toggleItemSelection,
                                onLongPressSelect = startSelectionWithItem,
                                onRestoreHiddenContacts = { viewModel.restoreHiddenContacts() },
                                onOpenSelfChat = { viewModel.openSelfChat { currentTab = HomeTab.CHATS } },
                                onOpenFindById = { showFindByIdDialog = true },
                                onOpenCreateGroup = { showCreateGroupDialog = true },
                                onStartChat = { contact ->
                                    viewModel.openDirectChat(contact) { currentTab = HomeTab.CHATS }
                                },
                                onStartCall = { contact, type ->
                                    viewModel.startCallWithContact(contact, type) {}
                                }
                            )
                            HomeTab.PROFILE -> UserProfileScreen(
                                viewModel = viewModel,
                                isDarkTheme = isDarkTheme,
                                onToggleDarkMode = onToggleDarkMode,
                                notificationSettings = notificationSettings,
                                hasNotificationPermission = hasNotificationPermission,
                                onOpenNotificationSettings = { showNotificationSettingsSheet = true },
                                onSignOut = onSignOut,
                                onOpenAppInfo = { showAppInfoSheet = true }
                            )
                        }
                    }
                }
            }
        }

        if (isExpanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationRailItem(
                        selected = currentTab == HomeTab.CHATS,
                        onClick = { currentTab = HomeTab.CHATS },
                        icon = { Icon(Icons.Rounded.ChatBubble, contentDescription = stringResource(R.string.nav_chats)) },
                        label = { Text(stringResource(R.string.nav_chats)) }
                    )
                    NavigationRailItem(
                        selected = currentTab == HomeTab.CALLS,
                        onClick = { currentTab = HomeTab.CALLS },
                        icon = { Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.nav_calls)) },
                        label = { Text(stringResource(R.string.nav_calls)) }
                    )
                    NavigationRailItem(
                        selected = currentTab == HomeTab.CONTACTS,
                        onClick = { currentTab = HomeTab.CONTACTS },
                        icon = { Icon(Icons.Rounded.People, contentDescription = stringResource(R.string.nav_contacts)) },
                        label = { Text(stringResource(R.string.nav_contacts)) }
                    )
                    NavigationRailItem(
                        selected = currentTab == HomeTab.PROFILE,
                        onClick = { currentTab = HomeTab.PROFILE },
                        icon = { Icon(Icons.Rounded.Person, contentDescription = stringResource(R.string.nav_profile)) },
                        label = { Text(stringResource(R.string.nav_profile)) }
                    )
                }
                navContent(Modifier.width(380.dp).fillMaxHeight())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (selectedChat != null) {
                        ChatScreen(
                            chat = selectedChat,
                            viewModel = viewModel,
                            showBackButton = false,
                            onBack = { viewModel.selectChat(null) },
                            onStartCall = { type -> viewModel.startCall(selectedChat, type) {} }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Chat,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Pilih obrolan untuk mulai mengirim pesan & video call",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            navContent(Modifier.fillMaxSize())
        }
    }

        InAppChatNotificationBanner(
            alert = activeChatBanner,
            toneTitle = notificationSettings.chatToneTitle,
            onOpenChat = { alert ->
                activeChatBanner = null
                if (!alert.isTestPreview && alert.chatId.isNotBlank()) {
                    currentTab = HomeTab.CHATS
                    viewModel.selectChat(alert.chatId)
                    resolvedNotificationManager.cancelChatNotification(alert.chatId)
                }
            },
            onDismiss = { activeChatBanner = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        InAppTestCallBanner(
            visible = isTestingCallBanner,
            callerName = "Partner Bank (Uji Panggilan)",
            callType = CallType.VIDEO,
            ringtoneTitle = notificationSettings.callToneTitle,
            onAcceptOrStop = {
                isTestingCallBanner = false
                resolvedTonePlayer.stopIncomingCallRingtone()
                resolvedNotificationManager.cancelIncomingCallNotification()
            },
            onDecline = {
                isTestingCallBanner = false
                resolvedTonePlayer.stopIncomingCallRingtone()
                resolvedNotificationManager.cancelIncomingCallNotification()
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showFindByIdDialog) {
        FindUserByIdDialog(
            onDismiss = { showFindByIdDialog = false },
            onStartChat = { uid ->
                showFindByIdDialog = false
                viewModel.startChatByUserId(uid) { currentTab = HomeTab.CHATS }
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = contactsList,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { title, selected ->
                showCreateGroupDialog = false
                viewModel.createGroupChat(title, selected) { currentTab = HomeTab.CHATS }
            }
        )
    }

    if (showBulkDeleteConfirm && activeBulkCategory != null && selectedItemIds.isNotEmpty()) {
        val idsToDelete = selectedItemIds
        BulkDeleteConfirmDialog(
            category = activeBulkCategory,
            selectedCount = idsToDelete.size,
            totalCount = activeTabSelectableIds.size,
            onDismiss = { showBulkDeleteConfirm = false },
            onConfirm = {
                when (activeBulkCategory) {
                    BulkDeleteCategory.CHATS -> viewModel.deleteChats(idsToDelete)
                    BulkDeleteCategory.CALLS -> viewModel.deleteCalls(idsToDelete)
                    BulkDeleteCategory.CONTACTS -> viewModel.deleteContacts(idsToDelete)
                }
                exitSelectionMode()
            }
        )
    }

    if (showAppInfoSheet) {
        AppInfoSheet(
            onDismiss = { showAppInfoSheet = false }
        )
    }

    if (showNotificationSettingsSheet) {
        NotificationSettingsSheet(
            settings = notificationSettings,
            hasNotificationPermission = hasNotificationPermission,
            isBackgroundServiceRunning = isBackgroundServiceRunning,
            isTestingCallRingtone = isTestingCallBanner,
            tonePlayer = resolvedTonePlayer,
            onRequestNotificationPermission = requestNotificationPermission,
            onToggleChatNotifications = { enabled ->
                scope.launch { resolvedNotificationPrefs.setChatNotificationsEnabled(enabled) }
            },
            onToggleCallNotifications = { enabled ->
                scope.launch { resolvedNotificationPrefs.setCallNotificationsEnabled(enabled) }
                if (!enabled && isTestingCallBanner) {
                    isTestingCallBanner = false
                    resolvedTonePlayer.stopIncomingCallRingtone()
                    resolvedNotificationManager.cancelIncomingCallNotification()
                }
            },
            onToggleBackgroundService = { enabled ->
                scope.launch { resolvedNotificationPrefs.setBackgroundServiceEnabled(enabled) }
            },
            onToggleVibration = { enabled ->
                scope.launch { resolvedNotificationPrefs.setVibrationEnabled(enabled) }
            },
            onSelectChatTone = { id, title ->
                scope.launch {
                    resolvedNotificationPrefs.setChatTone(id, title)
                    resolvedNotificationManager.ensureChatChannel(id, notificationSettings.vibrationEnabled)
                }
            },
            onSelectCallTone = { id, title ->
                scope.launch {
                    resolvedNotificationPrefs.setCallTone(id, title)
                    resolvedNotificationManager.ensureCallChannel(id, notificationSettings.vibrationEnabled)
                }
            },
            onTestChatNotification = {
                showNotificationSettingsSheet = false
                val preview = IncomingChatAlert(
                    id = "test_chat_${System.currentTimeMillis()}",
                    chatId = "test_chat_preview",
                    title = "Partner Bank",
                    messagePreview = "Contoh notifikasi obrolan masuk • Nada: ${notificationSettings.chatToneTitle}",
                    isTestPreview = true
                )
                if (resolvedNotificationManager.canPostNotifications()) {
                    resolvedNotificationManager.showChatNotification(
                        chatId = preview.chatId,
                        title = preview.title,
                        messagePreview = preview.messagePreview,
                        toneId = notificationSettings.chatToneId,
                        vibrate = notificationSettings.vibrationEnabled
                    )
                } else {
                    resolvedTonePlayer.playChatNotificationTone(
                        toneId = notificationSettings.chatToneId,
                        vibrate = notificationSettings.vibrationEnabled
                    )
                }
                activeChatBanner = preview
            },
            onToggleTestCallNotification = {
                if (isTestingCallBanner) {
                    isTestingCallBanner = false
                    resolvedTonePlayer.stopIncomingCallRingtone()
                    resolvedNotificationManager.cancelIncomingCallNotification()
                } else {
                    showNotificationSettingsSheet = false
                    isTestingCallBanner = true
                    if (resolvedNotificationManager.canPostNotifications()) {
                        resolvedNotificationManager.showIncomingCallNotification(
                            callId = "test_call_preview",
                            callerName = "Partner Bank (Uji Panggilan)",
                            callType = CallType.VIDEO,
                            toneId = notificationSettings.callToneId,
                            vibrate = notificationSettings.vibrationEnabled,
                            isTestPreview = true
                        )
                    } else {
                        resolvedTonePlayer.startIncomingCallRingtone(
                            toneId = notificationSettings.callToneId,
                            vibrate = notificationSettings.vibrationEnabled
                        )
                    }
                }
            },
            onDismiss = { showNotificationSettingsSheet = false }
        )
    }

    val createdId = newlyCreatedPathnerId
    if (!createdId.isNullOrBlank()) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreatedIdDialog() },
            title = {
                Text(
                    text = stringResource(R.string.created_id_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.created_id_dialog_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("created_id_confirmation_badge")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "@$createdId",
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldDeep,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Partner Bank ID", createdId))
                                    viewModel.showNotice("ID User (@$createdId) disalin ke papan klip.")
                                },
                                modifier = Modifier.testTag("copy_created_id_dialog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.profile_copy_id),
                                    tint = EmeraldPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissCreatedIdDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.testTag("confirm_created_id_dialog_button")
                ) {
                    Text(stringResource(R.string.created_id_dialog_start))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatsTabContent(
    chatsState: UiState<List<ChatRoom>>,
    currentUserId: String,
    selectedChatId: String?,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onLongPressSelect: (String) -> Unit,
    onChatClick: (ChatRoom) -> Unit,
    onOpenSelfChat: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    var typingNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val chatsData = (chatsState as? UiState.Success)?.data.orEmpty()
    val hasAnyRemoteTyping = chatsData.any { chat ->
        chat.typingUsers.any { it.key != currentUserId }
    }
    LaunchedEffect(chatsData, hasAnyRemoteTyping) {
        typingNowMs = System.currentTimeMillis()
        while (hasAnyRemoteTyping) {
            delay(2_000L)
            typingNowMs = System.currentTimeMillis()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_chats_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("search_chats_input")
        )

        when (chatsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EmeraldPrimary)
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = chatsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is UiState.Success -> {
                val filtered = remember(chatsState.data, query, currentUserId) {
                    if (query.isBlank()) chatsState.data
                    else chatsState.data.filter {
                        it.resolvedTitle(currentUserId).contains(query, ignoreCase = true) ||
                            it.lastMessageText.contains(query, ignoreCase = true)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    // Quick Saved Messages / Catatan Pribadi starter card when no self chat exists yet
                    val hasSelfChat = chatsState.data.any { it.isSelfChat(currentUserId) }
                    if (!hasSelfChat && query.isBlank()) {
                        item(key = "self_chat_banner") {
                            Card(
                                onClick = onOpenSelfChat,
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("open_saved_messages_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bookmark,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.saved_messages_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = stringResource(R.string.saved_messages_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item(key = "empty_chats") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubble,
                                    contentDescription = null,
                                    tint = EmeraldPrimary.copy(alpha = 0.45f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.empty_chats_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.empty_chats_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { chat ->
                            val title = chat.resolvedTitle(currentUserId)
                            val isChecked = chat.id in selectedIds
                            val isHighlighted = isChecked || (!isSelectionMode && chat.id == selectedChatId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                        else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                onToggleSelect(chat.id)
                                            } else {
                                                onChatClick(chat)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isSelectionMode) {
                                                onToggleSelect(chat.id)
                                            } else {
                                                onLongPressSelect(chat.id)
                                            }
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("chat_item_${chat.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    SelectionCheckIndicator(
                                        selected = isChecked,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                }
                                UserAvatar(
                                    name = title,
                                    photoUrl = chat.resolvedPhotoUrl(currentUserId),
                                    size = 52.dp,
                                    isGroup = chat.groupChat,
                                    isSelf = chat.isSelfChat(currentUserId)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatRelativeDate(chat.lastMessageAt ?: chat.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val typingLabel = chat.formatTypingSummary(currentUserId, typingNowMs)
                                    val showOutgoingReceipt = typingLabel == null &&
                                        chat.lastMessageSenderId == currentUserId &&
                                        chat.lastMessageText.isNotBlank()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (showOutgoingReceipt) {
                                            MessageReceiptIndicator(
                                                status = chat.resolveLastMessageReceiptStatus(currentUserId),
                                                messageId = "chat_preview_${chat.id}",
                                                mutedTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                iconSize = 16.dp
                                            )
                                        }
                                        Text(
                                            text = typingLabel ?: chat.lastMessageText.ifBlank { "Obrolan baru" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (typingLabel != null) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (typingLabel != null) FontWeight.Medium else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallsTabContent(
    callsState: UiState<List<CallSession>>,
    chatsList: List<ChatRoom>,
    currentUserId: String,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onLongPressSelect: (String) -> Unit,
    onStartQuickVideoChat: () -> Unit,
    onRedial: (CallSession) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    when (callsState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(callsState.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is UiState.Success -> {
            if (callsState.data.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = null,
                        tint = EmeraldPrimary.copy(alpha = 0.45f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.empty_calls_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.empty_calls_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onStartQuickVideoChat,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.testTag("quick_video_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Coba Video Call + Chat")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item(key = "quick_video_chat_banner") {
                        Card(
                            onClick = onStartQuickVideoChat,
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("quick_video_chat_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Video Call + Chat Langsung",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Mulai panggilan video sekaligus kirim pesan & file dalam satu layar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                    items(callsState.data, key = { it.id }) { call ->
                        val isOutgoing = call.callerId == currentUserId
                        val isMissedOrDeclined = call.parsedStatus in setOf(CallStatus.MISSED, CallStatus.DECLINED)
                        val peerName = call.peerName(currentUserId)
                        val isChecked = call.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleSelect(call.id)
                                        } else {
                                            onRedial(call)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isSelectionMode) {
                                            onToggleSelect(call.id)
                                        } else {
                                            onLongPressSelect(call.id)
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("call_item_${call.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                SelectionCheckIndicator(
                                    selected = isChecked,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                            UserAvatar(
                                name = peerName,
                                photoUrl = call.peerPhotoUrl(currentUserId),
                                size = 48.dp,
                                isSelf = call.isSelfCall()
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isMissedOrDeclined) CoralCallEnd else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when {
                                            isMissedOrDeclined -> Icons.Rounded.CallMissed
                                            isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
                                            else -> Icons.AutoMirrored.Rounded.CallReceived
                                        },
                                        contentDescription = null,
                                        tint = if (isMissedOrDeclined) CoralCallEnd else MintVibrant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val durationNote = if (call.durationSeconds > 0L) {
                                        " • ${MediaHelper.formatCallSeconds(call.durationSeconds)}"
                                    } else ""
                                    Text(
                                        text = "${formatRelativeDate(call.createdAt)}$durationNote",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!isSelectionMode) {
                                IconButton(
                                    onClick = { onRedial(call) },
                                    enabled = chatsList.any { it.id == call.chatId } || call.isSelfCall()
                                ) {
                                    Icon(
                                        imageVector = if (call.parsedType == CallType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                        contentDescription = "Panggil ulang",
                                        tint = EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactsTabContent(
    contactsState: UiState<List<PublicProfile>>,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    hiddenContactsCount: Int,
    onToggleSelect: (String) -> Unit,
    onLongPressSelect: (String) -> Unit,
    onRestoreHiddenContacts: () -> Unit,
    onOpenSelfChat: () -> Unit,
    onOpenFindById: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onStartChat: (PublicProfile) -> Unit,
    onStartCall: (PublicProfile, CallType) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var search by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "quick_actions") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ContactActionRow(
                        icon = Icons.Rounded.Bookmark,
                        title = stringResource(R.string.saved_messages_title),
                        subtitle = stringResource(R.string.saved_messages_subtitle),
                        onClick = onOpenSelfChat,
                        testTag = "contact_self_chat_row"
                    )
                    ContactActionRow(
                        icon = Icons.Rounded.GroupAdd,
                        title = stringResource(R.string.new_group_action),
                        subtitle = "Obrolan bersama banyak anggota",
                        onClick = onOpenCreateGroup,
                        testTag = "contact_new_group_row"
                    )
                    ContactActionRow(
                        icon = Icons.Rounded.PersonAdd,
                        title = stringResource(R.string.contacts_add_by_id),
                        subtitle = "Tambahkan teman lewat ID Partner Bank",
                        onClick = onOpenFindById,
                        testTag = "contact_find_by_id_row"
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Pengguna di Partner Bank",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            when (contactsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = EmeraldPrimary)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = contactsState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val filtered = contactsState.data.filter {
                        search.isBlank() ||
                            it.displayName.contains(search, ignoreCase = true) ||
                            (it.pathnerId?.contains(search.removePrefix("@"), ignoreCase = true) == true)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = "Belum ada kontak lain yang muncul. Undang teman untuk masuk ke Partner Bank atau gunakan Catatan Pribadi di atas untuk mencoba semua fitur.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        items(filtered, key = { it.userId }) { contact ->
                            val isChecked = contact.userId in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                        else Color.Transparent
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                onToggleSelect(contact.userId)
                                            } else {
                                                onStartChat(contact)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isSelectionMode) {
                                                onToggleSelect(contact.userId)
                                            } else {
                                                onLongPressSelect(contact.userId)
                                            }
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .testTag("contact_item_${contact.userId}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    SelectionCheckIndicator(
                                        selected = isChecked,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                }
                                UserAvatar(
                                    name = contact.displayName,
                                    photoUrl = contact.photoUrl,
                                    photoData = contact.photoData,
                                    size = 46.dp,
                                    showOnlineDot = contact.online
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = if (!contact.pathnerId.isNullOrBlank()) {
                                        "@${contact.pathnerId} • ${contact.statusText.ifBlank { "Ada di Partner Bank" }}"
                                    } else {
                                        contact.statusText.ifBlank { "Ada di Partner Bank" }
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!isSelectionMode) {
                                    IconButton(onClick = { onStartCall(contact, CallType.VIDEO) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Videocam,
                                            contentDescription = stringResource(R.string.video_call_desc),
                                            tint = EmeraldPrimary
                                        )
                                    }
                                    IconButton(onClick = { onStartCall(contact, CallType.VOICE) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Call,
                                            contentDescription = stringResource(R.string.voice_call_desc),
                                            tint = EmeraldPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (hiddenContactsCount > 0 && !isSelectionMode) {
                        item(key = "restore_hidden_contacts") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = onRestoreHiddenContacts,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("restore_hidden_contacts_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.restore_hidden_contacts_label, hiddenContactsCount))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(EmeraldPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FindUserByIdDialog(
    onDismiss: () -> Unit,
    onStartChat: (String) -> Unit
) {
    var uidInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contacts_id_dialog_title)) },
        text = {
            OutlinedTextField(
                value = uidInput,
                onValueChange = { uidInput = it },
                placeholder = { Text(stringResource(R.string.contacts_id_dialog_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onStartChat(uidInput.trim()) },
                enabled = uidInput.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.action_start_chat))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun CreateGroupDialog(
    contacts: List<PublicProfile>,
    onDismiss: () -> Unit,
    onCreate: (String, List<PublicProfile>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contacts_create_group_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { if (it.length <= 100) groupName = it },
                    placeholder = { Text(stringResource(R.string.contacts_group_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (contacts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Pilih anggota (opsional):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.height(160.dp)) {
                        items(contacts, key = { it.userId }) { contact ->
                            val checked = contact.userId in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedIds.remove(contact.userId)
                                        else selectedIds.add(contact.userId)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (checked) selectedIds.remove(contact.userId)
                                        else selectedIds.add(contact.userId)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = contact.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = contacts.filter { it.userId in selectedIds }
                    onCreate(groupName.trim(), chosen)
                },
                enabled = groupName.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
