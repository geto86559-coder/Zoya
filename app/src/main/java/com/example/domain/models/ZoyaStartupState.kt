package com.example.domain.models

/**
 * Lifecycle-safe startup state tracking Zoya's automatic voice initialization.
 * Ensures the introduction is spoken exactly once per application launch.
 */
enum class ZoyaStartupState {
    NOT_STARTED,
    INITIALIZING,
    WAITING_FOR_AUDIO,
    SPEAKING_INTRO,
    READY,
    FAILED
}
