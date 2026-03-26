package com.todolist.app.ui.health

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Hold-to-talk speech recognition using Android [SpeechRecognizer].
 */
class VoiceHoldSpeechRecognizer(
    private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    private val _isListening = mutableStateOf(false)
    val isListening: State<Boolean> = _isListening

    private val _transcript = mutableStateOf("")
    val transcript: State<String> = _transcript

    private val _audioLevel = mutableStateOf(0f)
    val audioLevel: State<Float> = _audioLevel

    private val listener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                val n = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _audioLevel.value = n
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _audioLevel.value = 0f
            }

            override fun onError(error: Int) {
                _isListening.value = false
                _audioLevel.value = 0f
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                _transcript.value = matches?.firstOrNull().orEmpty()
                _isListening.value = false
                _audioLevel.value = 0f
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotEmpty()) {
                    _transcript.value = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return null
        }
        if (recognizer == null) {
            recognizer =
                SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(listener)
                }
        }
        return recognizer
    }

    fun startListening() {
        val r = ensureRecognizer() ?: return
        _transcript.value = ""
        _audioLevel.value = 0f
        r.cancel()
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}

@Composable
fun rememberVoiceHoldSpeechRecognizer(): VoiceHoldSpeechRecognizer {
    val context = LocalContext.current
    val holder = remember(context) { VoiceHoldSpeechRecognizer(context) }
    DisposableEffect(holder) {
        onDispose { holder.destroy() }
    }
    return holder
}
