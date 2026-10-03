package com.example.domain.models

data class TrustedVoiceProfile(
    val isEnrolled: Boolean = false,
    val meanPitchEstimate: Float = 0f,
    val spectralVariance: Float = 0f,
    val enrolledDate: Long = 0L
)
