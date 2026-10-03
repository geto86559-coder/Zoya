package com.example.domain.models

/**
 * High-level state representing Zoya's current voice and operational phase.
 */
enum class ZoyaState {
    /** Slow breathing glow, awaiting user speech or wake word */
    IDLE,
    /** Microphone actively streaming and audio-reactive waveform active */
    LISTENING,
    /** Processing user intent, querying Gemini Live */
    THINKING,
    /** Audio playback streaming response to speaker */
    SPEAKING,
    /** Warning or recovery state with spoken explanation */
    ERROR
}
