package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.notifications.NotificationSettings
import com.example.notifications.NotificationTonePlayer
import com.example.notifications.ToneCatalog
import com.example.notifications.ToneCategory
import com.example.notifications.ToneOption
import com.example.notifications.ToneSource
import com.example.ui.theme.CoralCallEnd
import com.example.ui.theme.EmeraldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsSheet(
    settings: NotificationSettings,
    hasNotificationPermission: Boolean,
    isBackgroundServiceRunning: Boolean,
    isTestingCallRingtone: Boolean,
    tonePlayer: NotificationTonePlayer,
    onRequestNotificationPermission: () -> Unit,
    onToggleChatNotifications: (Boolean) -> Unit,
    onToggleCallNotifications: (Boolean) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onSelectChatTone: (toneId: String, title: String) -> Unit,
    onSelectCallTone: (toneId: String, title: String) -> Unit,
    onTestChatNotification: () -> Unit,
    onToggleTestCallNotification: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activePickerCategory by remember { mutableStateOf<ToneCategory?>(null) }
    var playingToneId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            tonePlayer.stopPreview()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            tonePlayer.stopPreview()
            onDismiss()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        AnimatedContent(
            targetState = activePickerCategory,
            label = "notif_sheet_content"
        ) { pickerCategory ->
            if (pickerCategory == null) {
                NotificationOverviewContent(
                    settings = settings,
                    hasNotificationPermission = hasNotificationPermission,
                    isBackgroundServiceRunning = isBackgroundServiceRunning,
                    isTestingCallRingtone = isTestingCallRingtone,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onToggleChatNotifications = onToggleChatNotifications,
                    onToggleCallNotifications = onToggleCallNotifications,
                    onToggleBackgroundService = onToggleBackgroundService,
                    onToggleVibration = onToggleVibration,
                    onOpenTonePicker = { category ->
                        tonePlayer.stopPreview()
                        playingToneId = null
                        activePickerCategory = category
                    },
                    onTestChatNotification = onTestChatNotification,
                    onToggleTestCallNotification = onToggleTestCallNotification
                )
            } else {
                TonePickerContent(
                    category = pickerCategory,
                    selectedToneId = if (pickerCategory == ToneCategory.CHAT) {
                        settings.chatToneId
                    } else {
                        settings.callToneId
                    },
                    selectedToneTitle = if (pickerCategory == ToneCategory.CHAT) {
                        settings.chatToneTitle
                    } else {
                        settings.callToneTitle
                    },
                    playingToneId = playingToneId,
                    tonePlayer = tonePlayer,
                    onPlayingChanged = { playingToneId = it },
                    onBack = {
                        tonePlayer.stopPreview()
                        playingToneId = null
                        activePickerCategory = null
                    },
                    onSelectTone = { id, title ->
                        if (pickerCategory == ToneCategory.CHAT) {
                            onSelectChatTone(id, title)
                        } else {
                            onSelectCallTone(id, title)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationOverviewContent(
    settings: NotificationSettings,
    hasNotificationPermission: Boolean,
    isBackgroundServiceRunning: Boolean,
    isTestingCallRingtone: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onToggleChatNotifications: (Boolean) -> Unit,
    onToggleCallNotifications: (Boolean) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onOpenTonePicker: (ToneCategory) -> Unit,
    onTestChatNotification: () -> Unit,
    onToggleTestCallNotification: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notif_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.notif_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasNotificationPermission) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = EmeraldPrimary.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.notif_permission_granted_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (!hasNotificationPermission) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.notif_permission_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(R.string.notif_permission_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = onRequestNotificationPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("grant_notification_permission_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.notif_permission_grant))
                    }
                }
            }
        }

        // Chat Notifications & Tone Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                NotificationToggleRow(
                    icon = Icons.Rounded.ChatBubble,
                    title = stringResource(R.string.notif_chat_toggle_title),
                    subtitle = stringResource(R.string.notif_chat_toggle_desc),
                    checked = settings.chatNotificationsEnabled,
                    onCheckedChange = onToggleChatNotifications,
                    switchTestTag = "toggle_chat_notifications"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
                ToneSelectorRow(
                    icon = Icons.Rounded.MusicNote,
                    label = stringResource(R.string.notif_chat_tone_label),
                    currentToneTitle = settings.chatToneTitle,
                    enabled = settings.chatNotificationsEnabled,
                    testTag = "select_chat_tone_row",
                    onClick = { onOpenTonePicker(ToneCategory.CHAT) }
                )
            }
        }

        // Incoming Call Notifications & Ringtone Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                NotificationToggleRow(
                    icon = Icons.Rounded.Call,
                    title = stringResource(R.string.notif_call_toggle_title),
                    subtitle = stringResource(R.string.notif_call_toggle_desc),
                    checked = settings.callNotificationsEnabled,
                    onCheckedChange = onToggleCallNotifications,
                    switchTestTag = "toggle_call_notifications"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
                ToneSelectorRow(
                    icon = Icons.Rounded.RingVolume,
                    label = stringResource(R.string.notif_call_tone_label),
                    currentToneTitle = settings.callToneTitle,
                    enabled = settings.callNotificationsEnabled,
                    testTag = "select_call_tone_row",
                    onClick = { onOpenTonePicker(ToneCategory.CALL) }
                )
            }
        }

        // Background Listener Toggle Card (keeps notifications active while using other apps)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                NotificationToggleRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.notif_background_toggle_title),
                    subtitle = stringResource(R.string.notif_background_toggle_desc),
                    checked = settings.backgroundServiceEnabled,
                    onCheckedChange = onToggleBackgroundService,
                    switchTestTag = "toggle_background_notification_service"
                )
                if (isBackgroundServiceRunning && settings.shouldRunBackgroundListener) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 66.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.notif_background_active_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Vibration Toggle Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            NotificationToggleRow(
                icon = Icons.Rounded.Vibration,
                title = stringResource(R.string.notif_vibration_title),
                subtitle = stringResource(R.string.notif_vibration_desc),
                checked = settings.vibrationEnabled,
                onCheckedChange = onToggleVibration,
                switchTestTag = "toggle_notification_vibration"
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Interactive Test Notification Strip
        Text(
            text = stringResource(R.string.notif_test_section_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onTestChatNotification,
                enabled = settings.chatNotificationsEnabled,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("test_chat_notification_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChatBubble,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.notif_test_chat_button),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isTestingCallRingtone) {
                Button(
                    onClick = onToggleTestCallNotification,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralCallEnd),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("stop_test_call_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.notif_test_call_stop_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onToggleTestCallNotification,
                    enabled = settings.callNotificationsEnabled,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("test_call_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RingVolume,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.notif_test_call_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(EmeraldPrimary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}

@Composable
private fun ToneSelectorRow(
    icon: ImageVector,
    label: String,
    currentToneTitle: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = EmeraldPrimary.copy(alpha = contentAlpha),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
            Text(
                text = currentToneTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
        )
    }
}

@Composable
private fun TonePickerContent(
    category: ToneCategory,
    selectedToneId: String,
    selectedToneTitle: String,
    playingToneId: String?,
    tonePlayer: NotificationTonePlayer,
    onPlayingChanged: (String?) -> Unit,
    onBack: () -> Unit,
    onSelectTone: (toneId: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val builtInTones = remember(category) { ToneCatalog.builtInOptionsFor(category) }
    val systemTones by produceState(
        initialValue = listOf(ToneCatalog.systemDefaultOption(category), ToneCatalog.silentOption()),
        key1 = context,
        key2 = category
    ) {
        value = withContext(Dispatchers.IO) {
            ToneCatalog.loadSystemTones(context, category)
        }
    }

    val knownIds = remember(builtInTones, systemTones) {
        (builtInTones + systemTones).map { it.id }.toSet()
    }
    val customSelectedOption = remember(selectedToneId, selectedToneTitle, knownIds) {
        if (selectedToneId !in knownIds) {
            ToneOption(
                id = selectedToneId,
                title = selectedToneTitle,
                subtitle = "Nada pilihan dari sistem Android",
                source = ToneSource.SYSTEM_URI,
                uriString = selectedToneId.removePrefix(ToneCatalog.URI_PREFIX)
            )
        } else null
    }

    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val newId = ToneCatalog.uriToToneId(pickedUri)
            val fallbackTitle = if (category == ToneCategory.CHAT) "Nada Sistem" else "Dering Sistem"
            val newTitle = ToneCatalog.resolveUriTitle(context, pickedUri, fallbackTitle)
            onSelectTone(newId, newTitle)
            if (!ToneCatalog.isSilent(newId)) {
                onPlayingChanged(newId)
                tonePlayer.previewTone(newId, category) {
                    onPlayingChanged(null)
                }
            } else {
                tonePlayer.stopPreview()
                onPlayingChanged(null)
            }
        }
    }

    val launchSystemRingtonePicker: () -> Unit = {
        tonePlayer.stopPreview()
        onPlayingChanged(null)
        val existingUri = ToneCatalog.resolveSystemUri(selectedToneId, category)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                if (category == ToneCategory.CHAT) {
                    RingtoneManager.TYPE_NOTIFICATION
                } else {
                    RingtoneManager.TYPE_RINGTONE
                }
            )
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                context.getString(
                    if (category == ToneCategory.CHAT) {
                        R.string.notif_tone_picker_chat_title
                    } else {
                        R.string.notif_tone_picker_call_title
                    }
                )
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
        }
        runCatching { systemPickerLauncher.launch(intent) }
        Unit
    }

    val handleToneSelectAndPreview: (ToneOption) -> Unit = { option ->
        onSelectTone(option.id, option.title)
        if (option.source == ToneSource.SILENT) {
            tonePlayer.stopPreview()
            onPlayingChanged(null)
        } else {
            onPlayingChanged(option.id)
            tonePlayer.previewTone(option.id, category) {
                onPlayingChanged(null)
            }
        }
    }

    val handlePlayToggle: (ToneOption) -> Unit = { option ->
        if (playingToneId == option.id) {
            tonePlayer.stopPreview()
            onPlayingChanged(null)
        } else if (option.source != ToneSource.SILENT) {
            onPlayingChanged(option.id)
            tonePlayer.previewTone(option.id, category) {
                onPlayingChanged(null)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("tone_picker_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back_desc)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (category == ToneCategory.CHAT) {
                            R.string.notif_tone_picker_chat_title
                        } else {
                            R.string.notif_tone_picker_call_title
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = selectedToneTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = launchSystemRingtonePicker,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("open_system_ringtone_picker_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.notif_tone_pick_from_system),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (customSelectedOption != null) {
                item(key = "custom_selected") {
                    ToneOptionRow(
                        option = customSelectedOption,
                        isSelected = true,
                        isPlaying = playingToneId == customSelectedOption.id,
                        onSelect = { handleToneSelectAndPreview(customSelectedOption) },
                        onTogglePlay = { handlePlayToggle(customSelectedOption) }
                    )
                }
            }

            item(key = "header_builtin") {
                Text(
                    text = stringResource(R.string.notif_tone_section_builtin),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            items(builtInTones, key = { it.id }) { option ->
                ToneOptionRow(
                    option = option,
                    isSelected = option.id == selectedToneId,
                    isPlaying = playingToneId == option.id,
                    onSelect = { handleToneSelectAndPreview(option) },
                    onTogglePlay = { handlePlayToggle(option) }
                )
            }

            item(key = "header_system") {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.notif_tone_section_system),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            items(systemTones, key = { it.id }) { option ->
                ToneOptionRow(
                    option = option,
                    isSelected = option.id == selectedToneId,
                    isPlaying = playingToneId == option.id,
                    onSelect = { handleToneSelectAndPreview(option) },
                    onTogglePlay = { handlePlayToggle(option) }
                )
            }
        }
    }
}

@Composable
private fun ToneOptionRow(
    option: ToneOption,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onTogglePlay: () -> Unit
) {
    val containerColor = if (isSelected) {
        EmeraldPrimary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val borderColor = if (isSelected) {
        EmeraldPrimary.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("tone_option_${option.id.replace(':', '_')}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = option.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (option.source == ToneSource.SILENT) {
            Icon(
                imageVector = Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(20.dp)
            )
        } else {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.testTag("preview_tone_${option.id.replace(':', '_')}")
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) EmeraldPrimary else EmeraldPrimary.copy(alpha = 0.14f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.notif_tone_stop_desc else R.string.notif_tone_play_desc
                        ),
                        tint = if (isPlaying) Color.White else EmeraldPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}
