package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.PublicProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionMenuSheet(
    message: ChatMessage,
    isOutgoing: Boolean,
    onDismiss: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onShowInfo: (() -> Unit)?,
    onSelectMultiple: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val copyableText = remember(message) { message.copyableText() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("message_action_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.message_actions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Selected message preview card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message.senderName.ifBlank { "Pesan" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatTimeShort(message.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = message.previewSummary(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MessageActionRow(
                icon = Icons.AutoMirrored.Rounded.Shortcut,
                label = stringResource(R.string.forward_action),
                iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                testTag = "message_action_forward",
                onClick = {
                    onDismiss()
                    onForward()
                }
            )

            if (copyableText != null) {
                MessageActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(R.string.copy_action),
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    testTag = "message_action_copy",
                    onClick = {
                        onDismiss()
                        onCopy()
                    }
                )
            }

            MessageActionRow(
                icon = Icons.AutoMirrored.Rounded.Reply,
                label = stringResource(R.string.reply_action),
                iconBgColor = MaterialTheme.colorScheme.surfaceVariant,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                testTag = "message_action_reply",
                onClick = {
                    onDismiss()
                    onReply()
                }
            )

            if (isOutgoing && onShowInfo != null) {
                MessageActionRow(
                    icon = Icons.Rounded.Info,
                    label = stringResource(R.string.message_info_action),
                    iconBgColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    testTag = "message_action_info",
                    onClick = {
                        onDismiss()
                        onShowInfo()
                    }
                )
            }

            if (onSelectMultiple != null) {
                MessageActionRow(
                    icon = Icons.Rounded.Checklist,
                    label = stringResource(R.string.chat_select_messages_action),
                    iconBgColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    testTag = "message_action_select_multiple",
                    onClick = {
                        onDismiss()
                        onSelectMultiple()
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            MessageActionRow(
                icon = Icons.Rounded.DeleteOutline,
                label = stringResource(R.string.delete_action),
                iconBgColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.error,
                labelColor = MaterialTheme.colorScheme.error,
                testTag = "message_action_delete",
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun MessageActionRow(
    icon: ImageVector,
    label: String,
    iconBgColor: Color,
    iconTint: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = labelColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardMessageSheet(
    message: ChatMessage,
    currentUserId: String,
    chats: List<ChatRoom>,
    contacts: List<PublicProfile>,
    onDismiss: () -> Unit,
    onConfirmForward: (targetChats: List<ChatRoom>, targetContacts: List<PublicProfile>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedContactUids by remember { mutableStateOf<Set<String>>(emptySet()) }

    val directChatPeerUids = remember(chats, currentUserId) {
        chats
            .filter { !it.groupChat && !it.isSelfChat(currentUserId) }
            .mapNotNull { room -> room.participantUids.firstOrNull { it != currentUserId } }
            .toSet()
    }

    val filteredChats = remember(chats, searchQuery, currentUserId) {
        val q = searchQuery.trim()
        if (q.isEmpty()) chats
        else chats.filter { it.resolvedTitle(currentUserId).contains(q, ignoreCase = true) }
    }

    val filteredExtraContacts = remember(contacts, directChatPeerUids, searchQuery) {
        val q = searchQuery.trim()
        contacts
            .filter { it.userId !in directChatPeerUids }
            .filter {
                q.isEmpty() ||
                    it.displayName.contains(q, ignoreCase = true) ||
                    (it.pathnerId?.contains(q, ignoreCase = true) == true)
            }
    }

    val totalSelected = selectedChatIds.size + selectedContactUids.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("forward_message_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.forward_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_cancel)
                    )
                }
            }

            // Compact preview of message to forward
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Shortcut,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.previewSummary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.forward_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_cancel)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forward_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 340.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (filteredChats.isNotEmpty()) {
                    item(key = "header_recent_chats") {
                        Text(
                            text = stringResource(R.string.forward_recent_chats_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                    items(filteredChats, key = { "chat_${it.id}" }) { room ->
                        val isChecked = room.id in selectedChatIds
                        val title = room.resolvedTitle(currentUserId)
                        val subtitle = when {
                            room.groupChat -> "${room.participantUids.size} anggota"
                            room.isSelfChat(currentUserId) -> "Catatan pribadi"
                            else -> room.lastMessageText.ifBlank { "Obrolan langsung" }
                        }
                        ForwardTargetRow(
                            title = title,
                            subtitle = subtitle,
                            photoUrl = room.resolvedPhotoUrl(currentUserId),
                            photoData = null,
                            isGroup = room.groupChat,
                            isSelf = room.isSelfChat(currentUserId),
                            isSelected = isChecked,
                            testTag = "forward_chat_item_${room.id}",
                            onClick = {
                                selectedChatIds = if (isChecked) {
                                    selectedChatIds - room.id
                                } else {
                                    selectedChatIds + room.id
                                }
                            }
                        )
                    }
                }

                if (filteredExtraContacts.isNotEmpty()) {
                    item(key = "header_contacts") {
                        Text(
                            text = stringResource(R.string.forward_contacts_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                    items(filteredExtraContacts, key = { "contact_${it.userId}" }) { contact ->
                        val isChecked = contact.userId in selectedContactUids
                        val subtitle = contact.pathnerId?.takeIf { it.isNotBlank() }?.let { "@$it" }
                            ?: contact.statusText
                        ForwardTargetRow(
                            title = contact.displayName.ifBlank { "Pengguna" },
                            subtitle = subtitle,
                            photoUrl = contact.photoUrl,
                            photoData = contact.photoData,
                            isGroup = false,
                            isSelf = false,
                            isSelected = isChecked,
                            testTag = "forward_contact_item_${contact.userId}",
                            onClick = {
                                selectedContactUids = if (isChecked) {
                                    selectedContactUids - contact.userId
                                } else {
                                    selectedContactUids + contact.userId
                                }
                            }
                        )
                    }
                }

                if (filteredChats.isEmpty() && filteredExtraContacts.isEmpty()) {
                    item(key = "empty_targets") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.forward_empty_targets),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val chosenChats = chats.filter { it.id in selectedChatIds }
                    val chosenContacts = contacts.filter { it.userId in selectedContactUids }
                    onDismiss()
                    onConfirmForward(chosenChats, chosenContacts)
                },
                enabled = totalSelected > 0,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_forward_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (totalSelected > 0) {
                        stringResource(R.string.forward_confirm_button, totalSelected)
                    } else {
                        stringResource(R.string.forward_select_prompt)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ForwardTargetRow(
    title: String,
    subtitle: String,
    photoUrl: String?,
    photoData: String?,
    isGroup: Boolean,
    isSelf: Boolean,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            name = title,
            photoUrl = photoUrl,
            photoData = photoData,
            size = 44.dp,
            isGroup = isGroup,
            isSelf = isSelf
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun DeleteMessageConfirmDialog(
    message: ChatMessage,
    isOutgoing: Boolean,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = stringResource(R.string.delete_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        if (isOutgoing) R.string.delete_dialog_desc_outgoing
                        else R.string.delete_dialog_desc_incoming
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.previewSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (isOutgoing) {
                    Button(
                        onClick = {
                            onDismiss()
                            onDeleteForEveryone()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_for_everyone_button")
                    ) {
                        Text(stringResource(R.string.delete_for_everyone_action))
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onDeleteForMe()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_for_me_button")
                    ) {
                        Text(stringResource(R.string.delete_for_me_action))
                    }
                } else {
                    Button(
                        onClick = {
                            onDismiss()
                            onDeleteForMe()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_for_me_button")
                    ) {
                        Text(stringResource(R.string.delete_for_me_action))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("delete_cancel_button")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun BulkDeleteMessagesConfirmDialog(
    selectedCount: Int,
    totalCount: Int,
    canDeleteForEveryone: Boolean,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    val isDeletingAll = totalCount > 1 && selectedCount >= totalCount
    val title = if (isDeletingAll) {
        stringResource(R.string.bulk_delete_messages_all_title)
    } else {
        stringResource(R.string.bulk_delete_messages_title, selectedCount)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        if (canDeleteForEveryone) R.string.bulk_delete_messages_desc_outgoing
                        else R.string.bulk_delete_messages_desc_for_me,
                        selectedCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (canDeleteForEveryone) {
                    Button(
                        onClick = {
                            onDismiss()
                            onDeleteForEveryone()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("bulk_delete_messages_for_everyone_button")
                    ) {
                        Text(stringResource(R.string.delete_for_everyone_count_action, selectedCount))
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onDeleteForMe()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("bulk_delete_messages_for_me_button")
                    ) {
                        Text(stringResource(R.string.delete_for_me_count_action, selectedCount))
                    }
                } else {
                    Button(
                        onClick = {
                            onDismiss()
                            onDeleteForMe()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("bulk_delete_messages_for_me_button")
                    ) {
                        Text(stringResource(R.string.delete_for_me_count_action, selectedCount))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("bulk_delete_messages_cancel_button")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
