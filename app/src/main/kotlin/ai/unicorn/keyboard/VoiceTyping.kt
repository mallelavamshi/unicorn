/*
 * Copyright (C) 2026 Unicorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.unicorn.keyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.android.inputmethod.latin.permissions.PermissionsManager
import java.util.Locale

/**
 * Voice typing inside the keys, with Android's speech recognizer. The engine's original mic switched to a separate
 * voice keyboard, which current Android versions no longer allow, so it did nothing.
 *
 * It keeps listening after each phrase until the user stops or goes quiet, and hands every finished phrase to
 * [onText]. Recording happens in the system's speech service; Unicorn only receives the text.
 */
class VoiceTyping(private val context: Context) {
    sealed interface State {
        data object Idle : State
        data object Starting : State
        /** [level] is the input loudness from 0 to 1, for the pulsing dot. */
        data class Listening(val partial: String, val level: Float) : State
        data class Failed(val message: String) : State
    }

    var onState: (State) -> Unit = {}
    var onText: (String) -> Unit = {}

    var state: State = State.Idle
        private set(value) {
            field = value
            onState(value)
        }

    val isActive get() = state is State.Starting || state is State.Listening

    private var recognizer: SpeechRecognizer? = null
    private var heardSomething = false
    private var stopRequested = false
    private var locale: Locale = Locale.getDefault()

    fun start(locale: Locale) {
        this.locale = locale
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state = State.Failed("Allow the microphone to type with your voice")
            PermissionsManager.get(context).requestPermissions({ granted ->
                pendingAfterPermission = granted
                if (!granted) state = State.Failed("Voice typing needs the microphone")
            }, null, Manifest.permission.RECORD_AUDIO)
            return
        }
        val created = recognizer ?: create() ?: run {
            state = State.Failed("Voice typing needs Google's speech services. Update the Google app and try again.")
            return
        }
        recognizer = created
        heardSomething = false
        stopRequested = false
        state = State.Starting
        listen()
    }

    /** Set when the microphone was just allowed; the keys start listening the next time they show. */
    var pendingAfterPermission = false

    /** Stops listening; the phrase heard so far is still delivered. */
    fun stop() {
        if (!isActive) return
        stopRequested = true
        recognizer?.stopListening()
    }

    /** Stops at once and drops what was being heard, for when the keys hide. */
    fun cancel() {
        recognizer?.cancel()
        if (state !is State.Idle) state = State.Idle
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        if (state !is State.Idle) state = State.Idle
    }

    private fun create(): SpeechRecognizer? {
        val recognizer = when {
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else -> null
        } ?: return null
        recognizer.setRecognitionListener(listener)
        return recognizer
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Punctuation and capitals where the service supports it
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            }
        }
        runCatching { recognizer?.startListening(intent) }.onFailure {
            Log.w(TAG, "startListening failed", it)
            state = State.Failed("Voice typing couldn't start. Try again.")
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            state = State.Listening("", 0f)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val current = state as? State.Listening ?: return
            // Speech services report roughly -2 dB (silence) to 10 dB (loud)
            state = current.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Silence: after a phrase that simply ends dictation
                    state = if (heardSomething || stopRequested) State.Idle else State.Failed("Didn't catch that. Tap the mic and speak.")
                }
                SpeechRecognizer.ERROR_CLIENT -> if (state !is State.Idle) state = State.Idle
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    state = State.Failed("Voice typing needs the microphone")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                    state = State.Failed("Voice typing needs a connection right now")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    state = State.Failed("Voice typing isn't available in ${locale.displayLanguage} yet")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Another app holds the service: start over with a fresh connection
                    release()
                    state = State.Failed("The microphone is busy. Try again.")
                }
                else -> state = State.Failed("Voice typing stopped. Tap the mic to try again.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            val current = state as? State.Listening ?: return
            if (!text.isNullOrBlank()) state = current.copy(partial = text)
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) {
                heardSomething = true
                onText(text)
            }
            if (state is State.Idle) return
            if (stopRequested) {
                state = State.Idle
                return
            }
            // Keep going for the next phrase; silence ends it (onError with a timeout)
            if (heardSomething) {
                state = State.Listening("", 0f)
                listen()
            } else {
                state = State.Failed("Didn't catch that. Tap the mic and speak.")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val TAG = "UnicornVoice"
    }
}
