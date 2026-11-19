package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.EmeraldDeep

enum class BulkDeleteCategory {
    CHATS,
    CALLS,
    CONTACTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionActionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val allSelected = totalCount > 0 && selectedCount >= totalCount
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = EmeraldDeep,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        navigationIcon = {
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("selection_close_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.selection_close_desc)
                )
            }
        },
        title = {
            Text(
                text = if (selectedCount > 0) {
                    stringResource(R.string.selection_count_label, selectedCount)
                } else {
                    stringResource(R.string.selection_prompt_label)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            IconButton(
                onClick = onToggleSelectAll,
                enabled = totalCount > 0,
                modifier = Modifier.testTag("selection_select_all_button")
            ) {
                Icon(
                    imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                    contentDescription = stringResource(
                        if (allSelected) R.string.deselect_all_action else R.string.select_all_action
                    )
                )
            }
            IconButton(
                onClick = onDeleteClick,
                enabled = selectedCount > 0,
                modifier = Modifier.testTag("selection_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete_action),
                    tint = if (selectedCount > 0) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
        }
    )
}

@Composable
fun SelectionQuickActionStrip(
    selectedCount: Int,
    totalCount: Int,
    onToggleSelectAll: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val allSelected = totalCount > 0 && selectedCount >= totalCount
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selection_quick_action_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = totalCount > 0, onClick = onToggleSelectAll)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("selection_select_all_chip"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (allSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (allSelected) {
                        stringResource(R.string.deselect_all_action)
                    } else {
                        "${stringResource(R.string.select_all_action)} ($totalCount)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Button(
                onClick = onDeleteClick,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("selection_delete_pill_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.delete_selected_action, selectedCount),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun SelectionCheckIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = modifier.size(24.dp)
    )
}

@Composable
fun BulkDeleteConfirmDialog(
    category: BulkDeleteCategory,
    selectedCount: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isDeletingAll = totalCount > 1 && selectedCount >= totalCount
    val title = when (category) {
        BulkDeleteCategory.CHATS -> if (isDeletingAll) {
            stringResource(R.string.bulk_delete_chats_all_title)
        } else {
            stringResource(R.string.bulk_delete_chats_title, selectedCount)
        }
        BulkDeleteCategory.CALLS -> if (isDeletingAll) {
            stringResource(R.string.bulk_delete_calls_all_title)
        } else {
            stringResource(R.string.bulk_delete_calls_title, selectedCount)
        }
        BulkDeleteCategory.CONTACTS -> if (isDeletingAll) {
            stringResource(R.string.bulk_delete_contacts_all_title)
        } else {
            stringResource(R.string.bulk_delete_contacts_title, selectedCount)
        }
    }
    val description = when (category) {
        BulkDeleteCategory.CHATS -> stringResource(R.string.bulk_delete_chats_desc)
        BulkDeleteCategory.CALLS -> stringResource(R.string.bulk_delete_calls_desc)
        BulkDeleteCategory.CONTACTS -> stringResource(R.string.bulk_delete_contacts_desc)
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
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_bulk_delete_button")
            ) {
                Text(stringResource(R.string.delete_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_bulk_delete_button")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
