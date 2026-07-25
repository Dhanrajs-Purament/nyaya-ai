package com.bitchat.android.nyaya.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ai.NyayaModelCatalog
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
    var preferGpu by remember { mutableStateOf(settings.preferGpu) }

    fun saveAll() {
        vm.updateSettings(
            engineMode = engineMode,
            cloudBaseUrl = baseUrl,
            cloudApiKey = apiKey,
            cloudModel = cloudModel,
            modelUrl = modelUrl,
            hfToken = hfToken,
            voiceReplies = voiceReplies,
            preferGpu = preferGpu
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "On-device model", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Gemma 4 runs entirely on this phone. It is a large one-time " +
                    "download, so use Wi-Fi. No account or token is needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NyayaModelCatalog.all.forEach { model ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = modelUrl == model.url,
                        onClick = { modelUrl = model.url }
                    )
                    Column {
                        Text(text = model.displayName)
                        Text(
                            text = model.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = preferGpu, onCheckedChange = { preferGpu = it })
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Use GPU when available")
                    Text(
                        text = "Faster and uses less memory. Falls back to CPU automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = modelUrl,
                onValueChange = { modelUrl = it },
                label = { Text("Advanced: model URL (.litertlm)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = hfToken,
                onValueChange = { hfToken = it },
                label = { Text("Hugging Face token (only for gated custom models)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            val progress = state.downloadProgress
            if (progress != null) {
                if (progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloading\u2026 " + (progress * 100).toInt() + "%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { saveAll(); vm.downloadAndLoadModel() }) {
                        Text(if (state.modelDownloaded) "Re-download model" else "Download & load model")
                    }
                    if (state.modelLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(text = "Loading\u2026", style = MaterialTheme.typography.bodySmall)
                    } else if (state.modelDownloaded) {
                        Text(
                            text = "Model ready \u2713",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "Cloud (bring your own key)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Works with any OpenAI-compatible endpoint (OpenAI, Groq, OpenRouter, " +
                    "self-hosted). Your key is stored encrypted on this phone and is only " +
                    "sent to the endpoint you set here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (e.g. https://api.openai.com/v1)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = cloudModel,
                onValueChange = { cloudModel = it },
                label = { Text("Model name (e.g. gpt-4o-mini)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = voiceReplies, onCheckedChange = { voiceReplies = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Speak replies aloud in voice mode")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { saveAll(); onBack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
