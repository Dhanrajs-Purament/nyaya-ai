package com.bitchat.android.nyaya

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bitchat.android.MainActivity
import com.bitchat.android.nyaya.ui.LegalLibraryScreen
import com.bitchat.android.nyaya.ui.NyayaChatScreen
import com.bitchat.android.nyaya.ui.NyayaHomeScreen
import com.bitchat.android.nyaya.ui.NyayaViewModel
import com.bitchat.android.nyaya.ui.SettingsScreen
import com.bitchat.android.nyaya.ui.VoiceModeScreen
import com.bitchat.android.nyaya.ui.components.NyayaActionsSheet
import com.bitchat.android.nyaya.ui.components.NyayaDrawer
import com.bitchat.android.nyaya.ui.theme.NyayaTheme
import com.bitchat.android.util.AppConstants
import kotlinx.coroutines.launch

/**
 * Entry point for the whole application.
 *
 * Nyaya AI and bitchat's encrypted mesh messenger ship as one app with a
 * single launcher icon, as two modes rather than as one feature nested inside
 * another. This activity is that icon; the mesh messenger is reached from the
 * navigation drawer, which starts [MainActivity] in the same task, so the system
 * Back gesture returns here.
 *
 * The messenger deliberately keeps its own activity rather than being embedded
 * as a screen inside this one. [MainActivity] owns the mesh service lifecycle,
 * the onboarding and Bluetooth-permission flow, its own back-press handling and
 * several broadcast receivers; re-hosting its Compose tree here would mean
 * duplicating all of that, and every bitchat source file is currently
 * byte-identical to upstream, which keeps future merges clean.
 *
 * Each mode owns its own data. bitchat's panic wipe clears bitchat's messages and
 * identity; Nyaya's conversations belong to the user and are removed only when the
 * user deletes them, from the drawer or from Settings.
 */
class NyayaActivity : ComponentActivity() {

    private val viewModel: NyayaViewModel by viewModels()

    /**
     * bitchat's shutdown coordinator broadcasts a force-finish when the user
     * quits the app. [MainActivity] listens for it; this activity must too,
     * otherwise quitting would leave the AI screen open behind the messenger.
     */
    private val forceFinishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AppConstants.UI.ACTION_FORCE_FINISH) {
                finishAffinity()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        registerForceFinishReceiver()
        setContent {
            NyayaTheme {
                NyayaApp(viewModel)
            }
        }
    }

    /**
     * Registered through [ContextCompat] so the not-exported flag is applied on
     * every API level. Calling the platform overload directly would leave the
     * receiver unflagged below API 33, which is exactly the case lint flags: the
     * broadcast is permission-protected, but an unflagged receiver on an older
     * device is still reachable by anything holding that permission.
     */
    private fun registerForceFinishReceiver() {
        ContextCompat.registerReceiver(
            this,
            forceFinishReceiver,
            IntentFilter(AppConstants.UI.ACTION_FORCE_FINISH),
            AppConstants.UI.PERMISSION_FORCE_FINISH,
            null,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(forceFinishReceiver) }
        super.onDestroy()
    }
}

private enum class NyayaScreen { HOME, CHAT, VOICE, LIBRARY, SETTINGS }

@Composable
private fun NyayaApp(vm: NyayaViewModel) {
    val state by vm.state.collectAsState()
    var screen by remember { mutableStateOf(NyayaScreen.HOME) }
    var showActions by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val micPermission = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) screen = NyayaScreen.VOICE }

    fun openVoiceMode() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) screen = NyayaScreen.VOICE
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun openMeshChat() {
        context.startActivity(Intent(context, MainActivity::class.java))
    }

    /**
     * Dials the free legal aid helpline. Uses ACTION_DIAL rather than ACTION_CALL
     * so the number is placed in the dialler for the user to confirm — the app
     * never needs the CALL_PHONE permission, and never dials on its own.
     */
    fun callLegalAid() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:15100".toUri()))
        }
    }

    fun closeDrawer() = scope.launch { drawerState.close() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = screen != NyayaScreen.VOICE,
        drawerContent = {
            // ModalDrawerSheet, not a bare Column: without it the drawer takes the
            // full screen width instead of the standard inset panel.
            androidx.compose.material3.ModalDrawerSheet(
                drawerContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
            ) {
                NyayaDrawer(
                    chats = state.chats,
                    activeChatId = state.activeChatId,
                    onNewChat = {
                        vm.newChat()
                        screen = NyayaScreen.HOME
                    },
                    onNewIncognitoChat = {
                        vm.newChat(incognito = true)
                        screen = NyayaScreen.HOME
                    },
                    onOpenChat = { id ->
                        vm.openChat(id)
                        screen = NyayaScreen.CHAT
                    },
                    onDeleteChat = { id -> vm.deleteChat(id) },
                    onOpenLibrary = { screen = NyayaScreen.LIBRARY },
                    onOpenMeshChat = { openMeshChat() },
                    onOpenSettings = { screen = NyayaScreen.SETTINGS },
                    onClose = { closeDrawer() }
                )
            }
        }
    ) {
        when (screen) {
            NyayaScreen.HOME -> NyayaHomeScreen(
                state = state,
                onSend = { text ->
                    vm.send(text)
                    screen = NyayaScreen.CHAT
                },
                onVoiceMode = { openVoiceMode() },
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenSettings = { screen = NyayaScreen.SETTINGS },
                onActions = { showActions = true },
                onDownloadModel = { vm.downloadAndLoadModel() },
                onNewChat = { vm.newChat() },
                onDismissError = { vm.dismissError() }
            )

            NyayaScreen.CHAT -> NyayaChatScreen(
                state = state,
                onSend = { text -> vm.send(text) },
                onVoiceMode = { openVoiceMode() },
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenSettings = { screen = NyayaScreen.SETTINGS },
                onActions = { showActions = true },
                onNewChat = {
                    vm.newChat()
                    screen = NyayaScreen.HOME
                },
                onStop = { vm.stopGenerating() },
                onSpeak = { text -> vm.voice.speak(text) }
            )

            NyayaScreen.VOICE -> VoiceModeScreen(
                state = state,
                onClose = {
                    vm.voice.stopListening()
                    vm.voice.stopSpeaking()
                    vm.setListening(false)
                    screen = if (state.messages.isEmpty()) NyayaScreen.HOME else NyayaScreen.CHAT
                },
                onOpenTranscript = {
                    vm.voice.stopListening()
                    vm.setListening(false)
                    screen = NyayaScreen.CHAT
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
                            onError = { vm.setListening(false) },
                            onLevel = { rms -> vm.setMicLevel(rms) }
                        )
                    }
                }
            )

            NyayaScreen.LIBRARY -> LegalLibraryScreen(
                state = state,
                onBack = {
                    vm.closeDocument()
                    screen = NyayaScreen.HOME
                },
                onLoad = { vm.loadLibrary() },
                onOpenDocument = { doc -> vm.openDocument(doc) },
                onCloseDocument = { vm.closeDocument() },
                onAsk = { question ->
                    vm.closeDocument()
                    vm.send(question)
                    screen = NyayaScreen.CHAT
                }
            )

            NyayaScreen.SETTINGS -> SettingsScreen(
                vm = vm,
                state = state,
                onBack = { screen = NyayaScreen.HOME }
            )
        }
    }

    if (showActions) {
        NyayaActionsSheet(
            incognito = state.incognito,
            onIncognitoChange = { vm.setIncognito(it) },
            onVoiceMode = { openVoiceMode() },
            onOpenLibrary = { screen = NyayaScreen.LIBRARY },
            onOpenMeshChat = { openMeshChat() },
            onOpenSettings = { screen = NyayaScreen.SETTINGS },
            onCallLegalAid = { callLegalAid() },
            onDismiss = { showActions = false }
        )
    }
}
