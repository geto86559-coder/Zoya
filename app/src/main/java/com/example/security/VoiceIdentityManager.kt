package com.example.security

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.models.AudioChunk
import com.example.domain.models.TrustedVoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * On-device voice verification layer.
 * Compares incoming acoustic features (fundamental frequency / zero-crossing rate)
 * against the enrolled user baseline. Does NOT store raw audio.
 */
class VoiceIdentityManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("zoya_voice_identity", Context.MODE_PRIVATE)

    private val _voiceProfile = MutableStateFlow(loadProfile())
    val voiceProfile: StateFlow<TrustedVoiceProfile> = _voiceProfile.asStateFlow()

    private fun loadProfile(): TrustedVoiceProfile {
        val enrolled = prefs.getBoolean("is_enrolled", false)
        val pitch = prefs.getFloat("mean_pitch", 0f)
        val variance = prefs.getFloat("variance", 0f)
        val date = prefs.getLong("date", 0L)
        return TrustedVoiceProfile(
            isEnrolled = enrolled,
            meanPitchEstimate = pitch,
            spectralVariance = variance,
            enrolledDate = date
        )
    }

    fun enrollCurrentVoice(chunks: List<AudioChunk>): Boolean {
        if (chunks.isEmpty()) return false
        var totalZcr = 0f
        var count = 0
        for (chunk in chunks) {
            val zcr = computeZeroCrossingRate(chunk.data)
            if (zcr > 0.02f) {
                totalZcr += zcr
                count++
            }
        }
        if (count == 0) return false
        val meanZcr = totalZcr / count

        prefs.edit()
            .putBoolean("is_enrolled", true)
            .putFloat("mean_pitch", meanZcr)
            .putFloat("variance", 0.05f)
            .putLong("date", System.currentTimeMillis())
            .apply()

        _voiceProfile.value = loadProfile()
        return true
    }

    /**
     * Verifies if the speaker matches the enrolled profile.
     * Returns true if verified or if voice identity is not strictly enforced.
     */
    fun verifySpeaker(chunk: AudioChunk): Boolean {
        val profile = _voiceProfile.value
        if (!profile.isEnrolled) {
            // Not enrolled -> do not block, allow normal interaction
            return true
        }

        val zcr = computeZeroCrossingRate(chunk.data)
        if (zcr < 0.01f) return true // Silence or low energy, ignore

        val diff = abs(zcr - profile.meanPitchEstimate)
        // Acoustic tolerance threshold
        return diff < 0.12f
    }

    fun resetVoiceIdentity() {
        prefs.edit().clear().apply()
        _voiceProfile.value = TrustedVoiceProfile()
    }

    private fun computeZeroCrossingRate(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 4) return 0f
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var crossings = 0
        var prev = buffer.get()
        while (buffer.hasRemaining()) {
            val current = buffer.get()
            if ((prev > 0 && current < 0) || (prev < 0 && current > 0)) {
                crossings++
            }
            prev = current
        }
        return crossings.toFloat() / (pcmBytes.size / 2)
    }
}
