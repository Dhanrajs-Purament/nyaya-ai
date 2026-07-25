package com.bitchat.android.nyaya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ui.theme.NyayaPill

/**
 * The Nyaya top bar: drawer button, the active engine as a tappable chip, and a
 * new-chat button.
 *
 * The engine chip is not decoration. Whether an answer came from the on-device
 * model or the user's own cloud key changes both its privacy and its accuracy,
 * so the app states which is active at all times rather than burying it in
 * settings. When a conversation is incognito the chip says so too — an incognito
 * mode the user cannot see is one they cannot trust.
 */
@Composable
fun NyayaTopBar(
    engineLabel: String,
    incognito: Boolean,
    onOpenDrawer: () -> Unit,
    onEngineClick: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open menu",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        if (incognito) {
            IncognitoChip()
        } else {
            EngineChip(engineLabel = engineLabel, onClick = onEngineClick)
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onNewChat) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "New conversation",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EngineChip(engineLabel: String, onClick: () -> Unit) {
    val ready = engineLabel.isNotBlank()
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), NyayaPill)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Nyaya",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (ready) engineLabel else "Set up",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(112.dp)
        )
        // Green when an engine is loaded, amber when the user still has to choose
        // one — the same at-a-glance signal as a connection indicator.
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    if (ready) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.tertiary,
                    CircleShape
                )
        )
    }
}

@Composable
private fun IncognitoChip() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, NyayaPill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Incognito \u00B7 not saved",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
