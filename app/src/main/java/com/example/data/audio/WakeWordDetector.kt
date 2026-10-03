package com.example.data.audio

import com.example.domain.models.AudioChunk
import kotlinx.coroutines.flow.StateFlow

/**
 * Wake word detection abstraction. Allows plugging on-device ML/Porcupine/Vosk in future phases
 * while providing an active energy-based heuristic detector in Phase 1.
 */
interface WakeWordDetector {
    val isListening: StateFlow<Boolean>
    fun start(onWakeWordDetected: (String) -> Unit)
    fun stop()
    fun processChunk(chunk: AudioChunk)
}

class SoftwareWakeWordDetector : WakeWordDetector {
    private val _isListening = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening

    private var onDetected: ((String) -> Unit)? = null
    private var consecutiveVoiceFrames = 0
    private var lastTriggerTime = 0L

    override fun start(onWakeWordDetected: (String) -> Unit) {
        this.onDetected = onWakeWordDetected
        _isListening.value = true
        consecutiveVoiceFrames = 0
    }

    override fun stop() {
        _isListening.value = false
        onDetected = null
    }

    override fun processChunk(chunk: AudioChunk) {
        if (!_isListening.value) return

        // Energy and acoustic speech frame heuristic for hands-free wake trigger
        if (chunk.rmsLevel > 0.22f) {
            consecutiveVoiceFrames++
            val now = System.currentTimeMillis()
            if (consecutiveVoiceFrames in 3..6 && (now - lastTriggerTime > 3000)) {
                lastTriggerTime = now
                consecutiveVoiceFrames = 0
                onDetected?.invoke("Hey Zoya")
            }
        } else {
            consecutiveVoiceFrames = maxOf(0, consecutiveVoiceFrames - 1)
        }
    }
}
