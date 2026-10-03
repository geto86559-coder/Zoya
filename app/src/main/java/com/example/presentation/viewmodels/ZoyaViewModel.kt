package com.example.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.gemini.LiveSessionManager
import com.example.domain.models.LiveSessionConfig
import com.example.domain.models.ZoyaEmotion
import com.example.domain.models.ZoyaPersonality
import com.example.domain.models.ZoyaState
import com.example.security.PermissionManager
import com.example.security.PermissionRequirement
import com.example.security.PrivacyManager
import com.example.services.BackgroundAudioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PresentationMode {
    CYBER_ORB,
    ANIME_AVATAR
}

class ZoyaViewModel(application: Application) : AndroidViewModel(application) {

    val permissionManager = PermissionManager(application)
    val privacyManager = PrivacyManager(application)

    private fun resolveApiKey(): String {
        val custom = privacyManager.getCustomApiKey()
        if (custom.isNotBlank()) return custom
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    private val _currentApiKey = MutableStateFlow(resolveApiKey())
    val currentApiKey: StateFlow<String> = _currentApiKey.asStateFlow()

    val liveSessionManager = LiveSessionManager(
        context = application,
        apiKey = resolveApiKey(),
        config = LiveSessionConfig(
            model = "gemini-3.1-flash-live-preview",
            voiceName = "Aoede"
        )
    )

    fun saveApiKey(newKey: String) {
        privacyManager.setCustomApiKey(newKey)
        val resolved = resolveApiKey()
        _currentApiKey.value = resolved
        liveSessionManager.updateApiKey(resolved)
        if (_isSessionActive.value) {
            liveSessionManager.clearErrorAndResume(viewModelScope)
        }
    }

    val zoyaState: StateFlow<ZoyaState> = liveSessionManager.zoyaState
    val zoyaEmotion: StateFlow<ZoyaEmotion> = liveSessionManager.zoyaEmotion
    val liveTranscript: StateFlow<String> = liveSessionManager.liveTranscript
    val errorMessage: StateFlow<String?> = liveSessionManager.errorMessage

    val inputAmplitude: StateFlow<Float> = liveSessionManager.audioCaptureManager.inputAmplitude
    val outputAmplitude: StateFlow<Float> = liveSessionManager.audioPlaybackManager.outputAmplitude

    private val _presentationMode = MutableStateFlow(PresentationMode.ANIME_AVATAR)
    val presentationMode: StateFlow<PresentationMode> = _presentationMode.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _greetingMessage = MutableStateFlow("Haan boss, bolo 😏 kya kaam hai?")
    val greetingMessage: StateFlow<String> = _greetingMessage.asStateFlow()

    val hasMicPermission: StateFlow<Boolean> = permissionManager.permissionStates
        .combine(MutableStateFlow(Unit)) { states, _ ->
            permissionManager.isGranted(PermissionRequirement.Microphone)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, permissionManager.isGranted(PermissionRequirement.Microphone))

    fun togglePresentationMode() {
        _presentationMode.value = if (_presentationMode.value == PresentationMode.ANIME_AVATAR) {
            PresentationMode.CYBER_ORB
        } else {
            PresentationMode.ANIME_AVATAR
        }
    }

    fun startVoiceSession() {
        if (!permissionManager.isGranted(PermissionRequirement.Microphone)) {
            // Cannot start without mic
            return
        }
        _isSessionActive.value = true
        _isMuted.value = false
        liveSessionManager.startSession(viewModelScope)

        if (privacyManager.isCapabilityEnabled("background_assistant", true)) {
            BackgroundAudioService.start(getApplication())
        }
    }

    fun stopVoiceSession() {
        _isSessionActive.value = false
        liveSessionManager.stopSession()
        BackgroundAudioService.stop(getApplication())
    }

    fun interruptZoya() {
        liveSessionManager.onUserInterruption()
    }

    fun toggleMute() {
        if (_isMuted.value) {
            _isMuted.value = false
            liveSessionManager.startSession(viewModelScope)
        } else {
            _isMuted.value = true
            liveSessionManager.audioCaptureManager.stopCapture()
        }
    }

    fun sendTextCommand(text: String) {
        liveSessionManager.sendTextPrompt(text)
    }

    fun retryConnection() {
        liveSessionManager.clearErrorAndResume(viewModelScope)
    }

    fun refreshPermissions() {
        permissionManager.refreshAll()
    }

    override fun onCleared() {
        liveSessionManager.release()
        super.onCleared()
    }
}
