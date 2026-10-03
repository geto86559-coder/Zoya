package com.example.data.gemini

import android.content.Context
import android.util.Log
import com.example.data.audio.AudioBufferManager
import com.example.data.audio.AudioCaptureManager
import com.example.data.audio.AudioPlaybackManager
import com.example.data.audio.SoftwareWakeWordDetector
import com.example.data.audio.WakeWordDetector
import com.example.domain.models.LiveSessionConfig
import com.example.domain.models.ZoyaEmotion
import com.example.domain.models.ZoyaPersonality
import com.example.domain.models.ZoyaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central orchestrator for Zoya live sessions:
 * Seamlessly manages audio capture, streaming playback, Gemini Live bidirectional pipeline,
 * and user interruption handling.
 */
class LiveSessionManager(
    private val context: Context,
    private val apiKey: String,
    private val config: LiveSessionConfig = LiveSessionConfig()
) {
    private val tag = "LiveSessionManager"

    val audioCaptureManager = AudioCaptureManager(sampleRate = config.sampleRateInput)
    val audioPlaybackManager = AudioPlaybackManager(context = context, sampleRate = config.sampleRateOutput)
    val audioBufferManager = AudioBufferManager()
    val wakeWordDetector: WakeWordDetector = SoftwareWakeWordDetector()

    private val _zoyaState = MutableStateFlow(ZoyaState.IDLE)
    val zoyaState: StateFlow<ZoyaState> = _zoyaState.asStateFlow()

    private val _zoyaEmotion = MutableStateFlow(ZoyaEmotion.PLAYFUL)
    val zoyaEmotion: StateFlow<ZoyaEmotion> = _zoyaEmotion.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var sessionScope: CoroutineScope? = null
    private var stateTransitionJob: Job? = null
    private var lastUserSpeechTime = 0L

    private var currentApiKey: String = apiKey

    private fun createClient(key: String): GeminiLiveClient {
        return GeminiLiveClient(
            apiKey = key,
            config = config,
            onAudioReceived = { pcmBytes ->
                handleServerAudio(pcmBytes)
            },
            onTranscriptReceived = { text ->
                handleServerTranscript(text)
            },
            onInterruptedReceived = {
                handleServerInterrupted()
            },
            onTurnCompleted = {
                handleTurnCompleted()
            },
            onErrorReceived = { err ->
                handleError(err)
            }
        )
    }

    var geminiLiveClient = createClient(apiKey)
        private set

    fun updateApiKey(newKey: String) {
        currentApiKey = newKey
        geminiLiveClient.disconnect()
        geminiLiveClient = createClient(newKey)
    }

    fun startSession(scope: CoroutineScope) {
        sessionScope = scope
        _zoyaState.value = ZoyaState.LISTENING
        _zoyaEmotion.value = ZoyaEmotion.LISTENING
        _errorMessage.value = null

        audioPlaybackManager.startPlaybackLoop(scope)
        geminiLiveClient.connect(scope)

        audioCaptureManager.startCapture(scope) { chunk ->
            // Check for user interruption if Zoya is currently speaking
            if (_zoyaState.value == ZoyaState.SPEAKING && chunk.rmsLevel > config.interruptionThresholdRms) {
                onUserInterruption()
            }

            // Also feed to wake word detector if enabled
            wakeWordDetector.processChunk(chunk)

            // Stream to Gemini Live
            if (_zoyaState.value != ZoyaState.ERROR) {
                geminiLiveClient.sendAudioChunk(chunk.data)
                if (chunk.rmsLevel > 0.05f) {
                    lastUserSpeechTime = System.currentTimeMillis()
                    if (_zoyaState.value == ZoyaState.IDLE) {
                        _zoyaState.value = ZoyaState.LISTENING
                    }
                }
            }
        }
    }

    private fun handleServerAudio(pcmBytes: ByteArray) {
        if (_zoyaState.value != ZoyaState.SPEAKING) {
            _zoyaState.value = ZoyaState.SPEAKING
            _zoyaEmotion.value = ZoyaEmotion.PLAYFUL
        }
        audioPlaybackManager.enqueueAudio(pcmBytes)
    }

    private fun handleServerTranscript(text: String) {
        _liveTranscript.value = (_liveTranscript.value + text).takeLast(250)
        // Detect subtle personality emotion markers from text
        if (text.contains("😏") || text.contains("boss", ignoreCase = true)) {
            _zoyaEmotion.value = ZoyaEmotion.TEASING
        } else if (text.contains("haha", ignoreCase = true) || text.contains("😄") || text.contains("✨")) {
            _zoyaEmotion.value = ZoyaEmotion.HAPPY
        } else if (text.contains("soch", ignoreCase = true) || text.contains("check", ignoreCase = true)) {
            _zoyaEmotion.value = ZoyaEmotion.THINKING
        }
    }

    private fun handleTurnCompleted() {
        stateTransitionJob?.cancel()
        stateTransitionJob = sessionScope?.launch {
            // Wait for audio queue to finish playing
            while (audioPlaybackManager.isPlaying.value) {
                delay(100)
            }
            delay(500)
            _zoyaState.value = ZoyaState.LISTENING
            _zoyaEmotion.value = ZoyaEmotion.NEUTRAL
        }
    }

    private fun handleServerInterrupted() {
        Log.d(tag, "Gemini server acknowledged interruption")
        audioPlaybackManager.interruptAndStop()
        _zoyaState.value = ZoyaState.LISTENING
        _zoyaEmotion.value = ZoyaEmotion.LISTENING
    }

    /**
     * User starts speaking while Zoya is speaking:
     * Immediately cut off playback, flush buffers, transition state.
     */
    fun onUserInterruption() {
        Log.d(tag, "User interrupted Zoya! Halting output stream.")
        audioPlaybackManager.interruptAndStop()
        _zoyaState.value = ZoyaState.LISTENING
        _zoyaEmotion.value = ZoyaEmotion.LISTENING
    }

    fun sendTextPrompt(text: String) {
        _liveTranscript.value = "You: $text\n"
        _zoyaState.value = ZoyaState.THINKING
        _zoyaEmotion.value = ZoyaEmotion.THINKING
        geminiLiveClient.sendTextMessage(text)
    }

    fun triggerQuickLine(text: String) {
        sendTextPrompt(text)
    }

    private fun handleError(err: String) {
        _zoyaState.value = ZoyaState.ERROR
        _zoyaEmotion.value = ZoyaEmotion.CONCERNED
        _errorMessage.value = err
    }

    fun clearErrorAndResume(scope: CoroutineScope) {
        _errorMessage.value = null
        _zoyaState.value = ZoyaState.LISTENING
        geminiLiveClient.connect(scope)
    }

    fun stopSession() {
        stateTransitionJob?.cancel()
        audioCaptureManager.stopCapture()
        audioPlaybackManager.interruptAndStop()
        geminiLiveClient.disconnect()
        wakeWordDetector.stop()
        _zoyaState.value = ZoyaState.IDLE
        _zoyaEmotion.value = ZoyaEmotion.NEUTRAL
    }

    fun release() {
        stopSession()
        audioPlaybackManager.release()
    }
}
