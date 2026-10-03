package com.example.domain.models

/**
 * Encapsulates raw PCM16 audio samples with sample rate and channel info.
 */
data class AudioChunk(
    val data: ByteArray,
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val rmsLevel: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioChunk
        return data.contentEquals(other.data) && sampleRate == other.sampleRate && channels == other.channels
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        return result
    }
}
