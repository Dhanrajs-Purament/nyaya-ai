package com.bitchat.android.nyaya.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Free, on-device voice for V1:
 * - STT: Android SpeechRecognizer with EXTRA_PREFER_OFFLINE (on-device models
 *   where available; Hindi and major Indian languages supported by device packs).
 * - TTS: Android TextToSpeech (device voices) — zero API cost.
 * Upgrade path (V1.1): sherpa-onnx bundled voices for fully offline guarantees.
 */
class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    fun init() {
        if (tts == null) tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            try {
                tts?.language = Locale.getDefault()
            } catch (_: Exception) {
            }
        }
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nyaya_" + System.currentTimeMillis())
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onLevel: (Float) -> Unit = {}
    ) {
        stopListening()
        if (!isRecognitionAvailable) {
            onError("Speech recognition is not available on this device.")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) onResult(text)
                else onError("Could not hear that clearly. Please try again.")
            }

            override fun onError(error: Int) {
                onError("Didn't catch that (error $error). Tap the mic and try again.")
            }

            override fun onRmsChanged(rmsdB: Float) = onLevel(rmsdB)
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        r.startListening(intent)
    }

    fun stopListening() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    fun release() {
        stopListening()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
