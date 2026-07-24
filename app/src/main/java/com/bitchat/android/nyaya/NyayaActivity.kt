package com.bitchat.android.nyaya

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.bitchat.android.nyaya.ui.NyayaChatScreen
import com.bitchat.android.nyaya.ui.NyayaHomeScreen
import com.bitchat.android.nyaya.ui.NyayaViewModel
import com.bitchat.android.nyaya.ui.SettingsScreen
import com.bitchat.android.nyaya.ui.VoiceModeScreen

/**
 * Entry point for the Nyaya AI lawyer. Lives alongside bitchat's MainActivity
 * (second launcher icon) so the mesh messenger stays completely untouched.
 */
class NyayaActivity : ComponentActivity() {

    private val viewModel: NyayaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = NyayaColors) {
                NyayaApp(viewModel)
            }
        }
    }
}

private val NyayaColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1220),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1B1C22),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF23242B),
    onSurfaceVariant = Color(0xFFB6BAC3),
    error = Color(0xFFF2B8B5)
)

private enum class NyayaScreen { HOME, CHAT, VOICE, SETTINGS }

@Composable
private fun NyayaApp(vm: NyayaViewModel) {
    val state by vm.state.collectAsState()
    var screen by remember { mutableStateOf(NyayaScreen.HOME) }
    val context = LocalContext.current

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) screen = NyayaScreen.VOICE }

    fun openVoiceMode() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) screen = NyayaScreen.VOICE
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    when (screen) {
        NyayaScreen.HOME -> NyayaHomeScreen(
            state = state,
            onSend = { text ->
                vm.send(text)
                screen = NyayaScreen.CHAT
            },
            onVoiceMode = { openVoiceMode() },
            onOpenSettings = { screen = NyayaScreen.SETTINGS },
            onDownloadModel = { vm.downloadAndLoadModel() },
            onOpenChat = { screen = NyayaScreen.CHAT }
        )
        NyayaScreen.CHAT -> NyayaChatScreen(
            state = state,
            onBack = { screen = NyayaScreen.HOME },
            onSend = { text -> vm.send(text) },
            onVoiceMode = { openVoiceMode() },
            onNewChat = {
                vm.newChat()
                screen = NyayaScreen.HOME
            }
        )
        NyayaScreen.VOICE -> VoiceModeScreen(
            state = state,
            onClose = {
                vm.voice.stopListening()
                vm.voice.stopSpeaking()
                vm.setListening(false)
                screen = if (state.messages.isEmpty()) NyayaScreen.HOME else NyayaScreen.CHAT
            },
            onMicTap = {
                if (state.listening) {
                    vm.voice.stopListening()
                    vm.setListening(false)
                } else {
                    vm.voice.stopSpeaking()
                    vm.setListening(true)
                    vm.voice.startListening(
                        onResult = { text ->
                            vm.setListening(false)
                            vm.send(text, speakReply = true)
                        },
                        onError = { vm.setListening(false) }
                    )
                }
            }
        )
        NyayaScreen.SETTINGS -> SettingsScreen(
            vm = vm,
            state = state,
            onBack = { screen = NyayaScreen.HOME }
        )
    }
}
