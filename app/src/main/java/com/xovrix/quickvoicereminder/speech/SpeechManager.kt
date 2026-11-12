package com.xovrix.quickvoicereminder.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface SpeechState {
    object Idle : SpeechState
    data class Listening(val partialText: String = "", val rmsDb: Float = 0f) : SpeechState
    data class Result(val text: String) : SpeechState
    data class Error(val message: String) : SpeechState
}

class SpeechManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var keepListening = false
    private var recognitionGeneration = 0
    private var committedText = ""
    private var partialCallback: ((String) -> Unit)? = null
    private var completeCallback: ((String) -> Unit)? = null
    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(onPartial: ((String) -> Unit)? = null, onComplete: ((String) -> Unit)? = null) {
        if (!isAvailable()) {
            _speechState.value = SpeechState.Error("Speech recognition is not available on this device")
            return
        }

        endRecognizer()
        keepListening = true
        committedText = ""
        partialCallback = onPartial
        completeCallback = onComplete
        recognitionGeneration++
        startRecognitionCycle(recognitionGeneration)
    }

    private fun startRecognitionCycle(generation: Int) {
        if (!keepListening || generation != recognitionGeneration) return

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (generation != recognitionGeneration) return
                        Log.d(TAG, "onReadyForSpeech")
                        _speechState.value = SpeechState.Listening(committedText, 0f)
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (generation != recognitionGeneration) return
                        val current = _speechState.value
                        if (current is SpeechState.Listening) {
                            _speechState.value = current.copy(rmsDb = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f))
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech")
                    }

                    override fun onError(error: Int) {
                        if (generation != recognitionGeneration || !keepListening) return
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) {
                            Log.d(TAG, "Speech paused; continuing listening session")
                            restartRecognitionCycle(generation)
                            return
                        }
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Speech recognition error ($error)"
                        }
                        Log.w(TAG, "SpeechRecognizer onError: $message")
                        keepListening = false
                        _speechState.value = SpeechState.Error(message)
                    }

                    override fun onResults(results: Bundle?) {
                        if (generation != recognitionGeneration || !keepListening) return
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = bestRecognitionResult(results, matches)
                        Log.d(TAG, "onResults: $spokenText")
                        if (spokenText.isNotBlank()) {
                            committedText = mergeTranscript(committedText, spokenText)
                            _speechState.value = SpeechState.Listening(committedText, 0f)
                            partialCallback?.invoke(committedText)
                        }
                        restartRecognitionCycle(generation)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (generation != recognitionGeneration || !keepListening) return
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            val currentRms = (_speechState.value as? SpeechState.Listening)?.rmsDb ?: 0f
                            val combinedText = mergeTranscript(committedText, text)
                            _speechState.value = SpeechState.Listening(combinedText, currentRms)
                            partialCallback?.invoke(combinedText)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
            }

            speechRecognizer?.startListening(intent)
            _speechState.value = SpeechState.Listening(committedText, 0f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}", e)
            keepListening = false
            _speechState.value = SpeechState.Error("Could not start microphone: ${e.localizedMessage}")
        }
    }

    private fun restartRecognitionCycle(generation: Int) {
        if (generation != recognitionGeneration || !keepListening) return
        val nextGeneration = ++recognitionGeneration
        endRecognizer()
        _speechState.value = SpeechState.Listening(committedText, 0f)
        mainHandler.postDelayed({ startRecognitionCycle(nextGeneration) }, RESTART_DELAY_MS)
    }

    fun stopListening() {
        try {
            val finalText = (_speechState.value as? SpeechState.Listening)?.partialText
                ?.takeIf { it.isNotBlank() }
                ?: committedText
            keepListening = false
            recognitionGeneration++
            mainHandler.removeCallbacksAndMessages(null)
            endRecognizer()
            if (finalText.isNotBlank()) {
                _speechState.value = SpeechState.Result(finalText)
                completeCallback?.invoke(finalText)
            } else {
                _speechState.value = SpeechState.Idle
            }
            partialCallback = null
            completeCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SpeechRecognizer: ${e.message}")
        }
    }

    private fun endRecognizer() {
        runCatching { speechRecognizer?.cancel() }
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
    }

    private fun bestRecognitionResult(results: Bundle?, matches: List<String>?): String {
        if (matches.isNullOrEmpty()) return ""
        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val bestIndex = confidence
            ?.indices
            ?.filter { it < matches.size && confidence[it] >= 0f }
            ?.maxByOrNull { confidence[it] }
            ?: 0
        return matches[bestIndex].trim()
    }

    fun resetState() {
        _speechState.value = SpeechState.Idle
    }

    companion object {
        private const val TAG = "SpeechManager"
        private const val RESTART_DELAY_MS = 150L
    }
}

internal fun mergeTranscript(committed: String, incoming: String): String {
    val prefix = committed.trim().replace(Regex("""\s+"""), " ")
    val addition = incoming.trim().replace(Regex("""\s+"""), " ")
    if (prefix.isBlank()) return addition
    if (addition.isBlank()) return prefix
    if (prefix.equals(addition, ignoreCase = true) || prefix.endsWith(addition, ignoreCase = true)) {
        return prefix
    }
    if (addition.startsWith(prefix, ignoreCase = true)) return addition

    val prefixWords = prefix.split(' ')
    val additionWords = addition.split(' ')
    val overlap = (minOf(prefixWords.size, additionWords.size) downTo 1).firstOrNull { count ->
        prefixWords.takeLast(count).zip(additionWords.take(count)).all { (left, right) ->
            left.trimEnd('.', ',', '!', '?').equals(
                right.trimEnd('.', ',', '!', '?'),
                ignoreCase = true
            )
        }
    } ?: 0
    return (prefixWords + additionWords.drop(overlap)).joinToString(" ")
}
