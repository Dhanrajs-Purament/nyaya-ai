package com.bitchat.android.nyaya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ui.theme.NyayaPill

/**
 * The actions sheet behind the `+` in the input bar.
 *
 * Only things this app can actually do. The inspiration for this sheet lists
 * image, video and music generation; offering those here would be a lie, so the
 * row holds what Nyaya really has — voice, the offline library, and mesh chat —
 * with the incognito switch and the legal-aid helpline below it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NyayaActionsSheet(
    incognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    onVoiceMode: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenMeshChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onCallLegalAid: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAction(
                icon = Icons.Outlined.Mic,
                label = "Speak",
                onClick = { onVoiceMode(); onDismiss() }
            )
            QuickAction(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                label = "Library",
                onClick = { onOpenLibrary(); onDismiss() }
            )
            QuickAction(
                icon = Icons.Outlined.Hub,
                label = "Mesh chat",
                onClick = { onOpenMeshChat(); onDismiss() }
            )
            QuickAction(
                icon = Icons.Outlined.Settings,
                label = "Settings",
                onClick = { onOpenSettings(); onDismiss() }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SheetRow(
            icon = Icons.Outlined.VisibilityOff,
            title = "Incognito chat",
            subtitle = "Nothing is saved. This conversation will not appear in your list.",
            trailing = {
                Switch(checked = incognito, onCheckedChange = onIncognitoChange)
            }
        )

        SheetRow(
            icon = Icons.Outlined.Phone,
            title = "Free legal aid \u00B7 15100",
            subtitle = "NALSA helpline. A real lawyer, at no cost, is your right in India.",
            onClick = { onCallLegalAid(); onDismiss() }
        )

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(MaterialTheme.colorScheme.surface, NyayaPill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}
