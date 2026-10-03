package com.example.domain.models

/**
 * Configuration options for Gemini Live real-time bidirectional session.
 */
data class LiveSessionConfig(
    val model: String = "gemini-3.1-flash-live-preview",
    val fallbackModel: String = "gemini-2.5-flash-native-audio-preview-12-2025",
    val voiceName: String = "Aoede", // Female expressive voice
    val responseModalities: List<String> = listOf("AUDIO"),
    val sampleRateInput: Int = 16000,
    val sampleRateOutput: Int = 24000,
    val systemInstruction: String = ZoyaPersonality.SYSTEM_PROMPT,
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 3,
    val interruptionThresholdRms: Float = 0.12f
)
