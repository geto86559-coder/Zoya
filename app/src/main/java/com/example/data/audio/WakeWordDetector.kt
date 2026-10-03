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
    private var speechEnvelope = mutableListOf<Float>()
    private var lastTriggerTime = 0L

    override fun start(onWakeWordDetected: (String) -> Unit) {
        this.onDetected = onWakeWordDetected
        _isListening.value = true
        speechEnvelope.clear()
    }

    override fun stop() {
        _isListening.value = false
        onDetected = null
        speechEnvelope.clear()
    }

    override fun processChunk(chunk: AudioChunk) {
        if (!_isListening.value) return

        val rms = chunk.rmsLevel
        val now = System.currentTimeMillis()

        if (rms > 0.08f) {
            speechEnvelope.add(rms)
            // Limit buffer to recent 15 frames (~500ms of utterance)
            if (speechEnvelope.size > 15) {
                speechEnvelope.removeAt(0)
            }

            // Check if envelope matches a 2-syllable peak pattern (Zo-ya)
            if (speechEnvelope.size >= 8 && (now - lastTriggerTime > 2500)) {
                val hasInitialBurst = speechEnvelope.take(4).any { it > 0.15f }
                val hasSecondaryBurst = speechEnvelope.takeLast(4).any { it > 0.12f }
                if (hasInitialBurst && hasSecondaryBurst) {
                    lastTriggerTime = now
                    speechEnvelope.clear()
                    onDetected?.invoke("Zoya")
                }
            }
        } else {
            if (speechEnvelope.isNotEmpty()) {
                speechEnvelope.removeAt(0)
            }
        }
    }
}
