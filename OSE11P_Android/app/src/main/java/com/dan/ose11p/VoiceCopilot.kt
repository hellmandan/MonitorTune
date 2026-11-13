package com.dan.ose11p

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Hands-free spoken gauge reader. Recognition prefers on-device speech where the
 * Android speech service supports it; availability depends on the installed engine.
 */
class VoiceCopilot(
    private val activity: Activity,
    private val answer: (String) -> String,
    private val onState: (String) -> Unit
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var ready = false
    private var wakeMode = false
    private var listening = false
    private var destroyed = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        tts = TextToSpeech(activity, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        val locale = Locale("en", "AU")
        engine.language = locale
        val voices = engine.voices.orEmpty()
        val preferred = voices.firstOrNull {
            it.locale.language == "en" && it.locale.country == "AU" &&
                (it.name.contains("female", true) || it.name.contains("woman", true))
        } ?: voices.firstOrNull {
            it.locale.language == "en" && it.locale.country == "AU" && !it.isNetworkConnectionRequired
        } ?: voices.firstOrNull {
            it.locale.language == "en" && (it.name.contains("female", true) || it.name.contains("woman", true))
        }
        if (preferred != null) engine.voice = preferred
        engine.setPitch(1.08f)
        engine.setSpeechRate(0.94f)
        ready = true
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (wakeMode && !destroyed) {
                        mainHandler.postDelayed({ if (wakeMode && !destroyed) startListening() }, 250)
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { if (wakeMode && !destroyed) startListening() }
            }
        })
    }

    fun startListening() {
        if (destroyed) return
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            speak("Speech recognition isn't available on this phone, darling.")
            onState("VOICE UNAVAILABLE")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity).also { it.setRecognitionListener(listener) }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-AU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-AU")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask MonitorTune for a gauge reading")
        }
        try {
            listening = true
            onState(if (wakeMode) "LISTENING FOR “BABE”" else "LISTENING…")
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            listening = false
            onState("VOICE READY")
        }
    }

    fun setWakeMode(enabled: Boolean) {
        wakeMode = enabled
        if (enabled) startListening() else {
            listening = false
            try { recognizer?.cancel() } catch (_: Exception) {}
            onState("VOICE READY")
        }
    }

    fun isWakeMode() = wakeMode

    /** Stop microphone capture while the activity is not visible, retaining hands-free mode. */
    fun pauseListening() {
        listening = false
        try { recognizer?.cancel() } catch (_: Exception) {}
    }

    fun resumeListening() {
        if (wakeMode && !destroyed) startListening()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onState(if (wakeMode) "SAY “BABE, WHAT’S THE RPM?”" else "LISTENING…") }
        override fun onBeginningOfSpeech() { onState("HEARING YOU…") }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { onState("CHECKING…") }
        override fun onError(error: Int) {
            listening = false
            if (wakeMode && !destroyed) {
                onState("RESTARTING WAKE LISTEN")
                android.os.Handler(activity.mainLooper).postDelayed({ if (wakeMode && !destroyed) startListening() }, 900)
            } else onState("VOICE READY")
        }
        override fun onResults(results: Bundle?) {
            listening = false
            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            val normalized = spoken.trim()
            if (normalized.isNotBlank()) {
                val wakeMatch = Regex("(?i)\\bbabe\\b").containsMatchIn(normalized)
                if (!wakeMode || wakeMatch) {
                    val command = normalized.replace(Regex("(?i)\\bbabe\\b[, ]*"), "").trim()
                    val reply = if (command.isBlank()) "I'm listening, darling. What would you like to know?" else answer(command)
                    speak(reply)
                    if (!wakeMode) onState("VOICE READY")
                    return
                }
            }
            if (wakeMode && !destroyed) {
                mainHandler.postDelayed({ if (wakeMode && !destroyed) startListening() }, 500)
            } else onState("VOICE READY")
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun speak(text: String) {
        if (!ready || destroyed) { onState("VOICE INITIALISING"); return }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "monitortune_voice_reply")
    }

    fun stop() {
        wakeMode = false
        pauseListening()
        onState("VOICE OFF")
    }

    fun destroy() {
        destroyed = true
        wakeMode = false
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
