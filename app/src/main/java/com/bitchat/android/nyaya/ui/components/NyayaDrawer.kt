package com.bitchat.android.nyaya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.history.SavedChat
import com.bitchat.android.nyaya.ui.theme.NyayaPill

/**
 * The navigation drawer.
 *
 * Structure follows the two-mode nature of the app: the mode switch is the first
 * thing in it, because "Nyaya AI" and "Mesh chat" are peers, not a feature buried
 * inside a screen. Below that come the actions on the current mode, then the
 * user's saved conversations, then settings.
 *
 * Chat rows are plain text, not cards: a list of twenty legal questions is
 * already visually heavy, and boxing each one makes it unreadable.
 */
@Composable
fun NyayaDrawer(
    chats: List<SavedChat>,
    activeChatId: String?,
    onNewChat: () -> Unit,
    onNewIncognitoChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenMeshChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(chats, query) {
        if (query.isBlank()) chats
        else chats.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NyayaBrandMark(size = 22.dp)
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "Nyaya AI",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                DrawerAction(
                    icon = Icons.Outlined.Edit,
                    label = "New conversation",
                    highlighted = true,
                    onClick = { onNewChat(); onClose() }
                )
                DrawerAction(
                    icon = Icons.Outlined.VisibilityOff,
                    label = "New incognito chat",
                    supporting = "Not saved anywhere",
                    onClick = { onNewIncognitoChat(); onClose() }
                )
                SearchField(query = query, onQueryChange = { query = it })
                DrawerAction(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    label = "Legal library",
                    supporting = "Read the Acts offline",
                    onClick = { onOpenLibrary(); onClose() }
                )
                DrawerAction(
                    icon = Icons.Outlined.Hub,
                    label = "Mesh chat",
                    supporting = "Encrypted, no internet needed",
                    onClick = { onOpenMeshChat(); onClose() }
                )
                SectionLabel(
                    text = if (chats.isEmpty()) "Your conversations" else "Your conversations \u00B7 ${chats.size}"
                )
            }

            if (visible.isEmpty()) {
                item {
                    Text(
                        text = if (chats.isEmpty()) {
                            "Conversations you have are saved on this phone, and stay " +
                                "until you delete them."
                        } else {
                            "No conversation matches \u201C$query\u201D."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                }
            }

            items(visible, key = { it.id }) { chat ->
                ChatRow(
                    chat = chat,
                    active = chat.id == activeChatId,
                    onClick = { onOpenChat(chat.id); onClose() },
                    onDelete = { onDeleteChat(chat.id) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        DrawerAction(
            icon = Icons.Outlined.Settings,
            label = "Settings",
            onClick = { onOpenSettings(); onClose() }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, NyayaPill)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search your conversations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DrawerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    supporting: String? = null,
    highlighted: Boolean = false
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.background,
                NyayaPill
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun ChatRow(
    chat: SavedChat,
    active: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .background(
                if (active) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.background,
                NyayaPill
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chat.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Delete is always visible rather than hidden behind a long-press: the
        // user must be able to remove a sensitive conversation immediately,
        // without discovering a gesture first.
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Delete \u201C${chat.title}\u201D",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
