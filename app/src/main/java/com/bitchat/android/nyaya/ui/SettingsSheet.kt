package com.bitchat.android.nyaya.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.settings.NyayaSettings

/**
 * Settings: engine mode (on-device vs BYOK cloud), model download,
 * BYOK endpoint/key/model, Hugging Face token, and voice replies.
 */
@Composable
fun SettingsScreen(
    vm: NyayaViewModel,
    state: NyayaViewModel.UiState,
    onBack: () -> Unit
) {
    val settings = vm.settings
    var engineMode by remember { mutableStateOf(settings.engineMode) }
    var baseUrl by remember { mutableStateOf(settings.cloudBaseUrl) }
    var apiKey by remember { mutableStateOf(settings.cloudApiKey) }
    var cloudModel by remember { mutableStateOf(settings.cloudModel) }
    var modelUrl by remember { mutableStateOf(settings.modelUrl) }
    var hfToken by remember { mutableStateOf(settings.hfToken) }
    var voiceReplies by remember { mutableStateOf(settings.voiceRepliesEnabled) }

    fun saveAll() {
        vm.updateSettings(
            engineMode = engineMode,
            cloudBaseUrl = baseUrl,
            cloudApiKey = apiKey,
            cloudModel = cloudModel,
            modelUrl = modelUrl,
            hfToken = hfToken,
            voiceReplies = voiceReplies
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { saveAll(); onBack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "AI engine", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = engineMode == NyayaSettings.MODE_ON_DEVICE,
                    onClick = { engineMode = NyayaSettings.MODE_ON_DEVICE }
                )
                Text(text = "On-device (offline, fully private)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = engineMode == NyayaSettings.MODE_CLOUD,
                    onClick = { engineMode = NyayaSettings.MODE_CLOUD }
                )
                Text(text = "Cloud with my own API key (BYOK)")
            }

            Spacer(modifier = Modifier.height = 16.dp)
        }
    }
}
