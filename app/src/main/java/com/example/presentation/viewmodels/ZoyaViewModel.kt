package com.example.presentation.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.audio.AudioDeviceManager
import com.example.data.audio.ZoyaVoiceConstants
import com.example.data.audio.ZoyaVoiceSpeaker
import com.example.data.gemini.LiveSessionManager
import com.example.domain.models.ConfirmationLevel
import com.example.domain.models.LiveSessionConfig
import com.example.domain.models.PendingAction
import com.example.domain.models.ZoyaEmotion
import com.example.domain.models.ZoyaPersonality
import com.example.domain.models.ZoyaStartupState
import com.example.domain.models.ZoyaState
import com.example.domain.models.ZoyaToolCall
import com.example.security.PermissionManager
import com.example.security.PermissionRequirement
import com.example.security.PrivacyManager
import com.example.security.VoiceIdentityManager
import com.example.services.BackgroundAudioService
import com.example.services.CallAssistantManager
import com.example.services.IncomingCallInfo
import com.example.services.IncomingCallReceiver
import com.example.services.ZoyaAccessibilityService
import com.example.services.ZoyaNotificationItem
import com.example.services.ZoyaNotificationListenerService
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PresentationMode {
    CYBER_ORB,
    ANIME_AVATAR
}

class ZoyaViewModel(application: Application) : AndroidViewModel(application) {

    val permissionManager = PermissionManager(application)
    val privacyManager = PrivacyManager(application)
    val voiceIdentityManager = VoiceIdentityManager(application)
    val callAssistantManager = CallAssistantManager(application)

    val audioDeviceManager = AudioDeviceManager(application).apply {
        startMonitoring()
    }

    val isBluetoothConnected: StateFlow<Boolean> = audioDeviceManager.isBluetoothConnected
    val activeAudioRoute: StateFlow<String> = audioDeviceManager.activeRouteName

    val voiceSpeaker = ZoyaVoiceSpeaker(application, viewModelScope, audioDeviceManager)

    private val _startupState = MutableStateFlow(ZoyaStartupState.NOT_STARTED)
    val startupState: StateFlow<ZoyaStartupState> = _startupState.asStateFlow()

    private var hasSpokenStartupIntro = false

    private val _pendingConfirmation = MutableStateFlow<PendingAction?>(null)
    val pendingConfirmation: StateFlow<PendingAction?> = _pendingConfirmation.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _announcementText = MutableStateFlow<String?>(null)
    val announcementText: StateFlow<String?> = _announcementText.asStateFlow()

    val isScreenAssistantActive: StateFlow<Boolean> = ZoyaAccessibilityService.isServiceActive
    val incomingCall: StateFlow<IncomingCallInfo?> = callAssistantManager.currentCall

    val toolExecutionEngine = ToolExecutionEngine(
        context = application,
        onConfirmationRequired = { action ->
            _pendingConfirmation.value = action
        },
        callAssistantManager = callAssistantManager
    )

    init {
        IncomingCallReceiver.callAssistantManager = callAssistantManager

        // Trigger startup introduction once per application session
        triggerStartupIntroIfNeeded()

        // Listen for screen safety warnings
        ZoyaAccessibilityService.screenWarnings.onEach { warning ->
            if (privacyManager.isCapabilityEnabled("screen_assistant", false)) {
                _announcementText.value = warning.warningText
                voiceSpeaker.speakText(warning.warningText)
                liveSessionManager.sendTextPrompt("Screen alert warning: ${warning.warningText}")
            }
        }.launchIn(viewModelScope)

        // Listen for new notifications and announce
        ZoyaNotificationListenerService.newNotificationEvents.onEach { notif ->
            if (privacyManager.isCapabilityEnabled("notification_access", false)) {
                val announcement = "Tumhe ${notif.appDisplayName} pe ${notif.sender} ka message aaya hai."
                _announcementText.value = announcement
                voiceSpeaker.speakText(announcement)
            }
        }.launchIn(viewModelScope)

        // Listen for incoming calls and announce
        callAssistantManager.currentCall.onEach { call ->
            if (call != null && call.isRinging) {
                val text = "Tumhe ${call.callerName} ka call aa raha hai. Pick karna hai ya disconnect?"
                voiceSpeaker.speakText(text)
            }
        }.launchIn(viewModelScope)
    }

    fun triggerStartupIntroIfNeeded() {
        if (hasSpokenStartupIntro) return
        hasSpokenStartupIntro = true

        viewModelScope.launch {
            _startupState.value = ZoyaStartupState.INITIALIZING
            try {
                audioDeviceManager.updateAudioRoute()
                _startupState.value = ZoyaStartupState.WAITING_FOR_AUDIO
                delay(350)

                _startupState.value = ZoyaStartupState.SPEAKING_INTRO
                voiceSpeaker.speakIntro(ZoyaVoiceConstants.DEFAULT_STARTUP_INTRO) {
                    _startupState.value = ZoyaStartupState.READY
                    if (permissionManager.isGranted(PermissionRequirement.Microphone)) {
                        startVoiceSession()
                    }
                }
            } catch (e: Exception) {
                Log.e("ZoyaViewModel", "Error in startup sequence", e)
                _startupState.value = ZoyaStartupState.FAILED
                _startupState.value = ZoyaStartupState.READY
            }
        }
    }

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
        ),
        audioDeviceManager = audioDeviceManager
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
        .combine(voiceSpeaker.speechAmplitude) { liveAmp, ttsAmp ->
            maxOf(liveAmp, ttsAmp)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    private val _presentationMode = MutableStateFlow(PresentationMode.ANIME_AVATAR)
    val presentationMode: StateFlow<PresentationMode> = _presentationMode.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _greetingMessage = MutableStateFlow("Haan boss, bolo 😏 kya kaam hai?")
    val greetingMessage: StateFlow<String> = _greetingMessage.asStateFlow()

    val hasMicPermission: StateFlow<Boolean> = permissionManager.permissionStates
        .combine(MutableStateFlow(Unit)) { _, _ ->
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
        voiceSpeaker.stop()
        liveSessionManager.onUserInterruption()
        toolExecutionEngine.executeTool(ZoyaToolCall("stop", "stop", emptyMap()))
    }

    fun speakIntro() {
        voiceSpeaker.speakIntro()
    }

    fun speakText(text: String) {
        voiceSpeaker.speakText(text)
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

    fun handleVoiceOrTextCommand(commandText: String) {
        val lower = commandText.lowercase().trim()

        // Check for local Cross-App & Tool shortcuts
        when {
            lower == "intro" || lower.contains("intro") -> {
                speakIntro()
            }
            lower.contains("youtube") && (lower.contains("kholo") || lower.contains("open")) -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("1", "openApp", mapOf("appName" to "youtube")))
                liveSessionManager.sendTextPrompt("Opened YouTube: ${res.message}")
            }
            lower.contains("whatsapp") && (lower.contains("kholo") || lower.contains("open")) -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("2", "openApp", mapOf("appName" to "whatsapp")))
                liveSessionManager.sendTextPrompt("Opened WhatsApp: ${res.message}")
            }
            lower.contains("telegram") && (lower.contains("kholo") || lower.contains("open")) -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("3", "openApp", mapOf("appName" to "telegram")))
                liveSessionManager.sendTextPrompt("Opened Telegram: ${res.message}")
            }
            lower.contains("instagram") && (lower.contains("kholo") || lower.contains("open")) -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("4", "openApp", mapOf("appName" to "instagram")))
                liveSessionManager.sendTextPrompt("Opened Instagram: ${res.message}")
            }
            lower.contains("calculator") && (lower.contains("kholo") || lower.contains("open")) -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("5", "openApp", mapOf("appName" to "calculator")))
                liveSessionManager.sendTextPrompt("Opened Calculator: ${res.message}")
            }
            lower.contains("chrome") && (lower.contains("kholo") || lower.contains("open")) -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("6", "openApp", mapOf("appName" to "chrome")))
                liveSessionManager.sendTextPrompt("Opened Chrome: ${res.message}")
            }
            lower.contains("settings") && (lower.contains("kholo") || lower.contains("open")) -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("7", "openSettings", mapOf("settingType" to "all")))
                liveSessionManager.sendTextPrompt("Opened Settings: ${res.message}")
            }
            lower.contains("wifi") || lower.contains("wi-fi") -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("8", "openSettings", mapOf("settingType" to "wifi")))
                liveSessionManager.sendTextPrompt("Wi-Fi Settings: ${res.message}")
            }
            lower.contains("bluetooth") -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("9", "openSettings", mapOf("settingType" to "bluetooth")))
                liveSessionManager.sendTextPrompt("Bluetooth Settings: ${res.message}")
            }
            lower.contains("battery") -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("10", "openSettings", mapOf("settingType" to "battery")))
                liveSessionManager.sendTextPrompt("Battery Settings: ${res.message}")
            }
            lower.contains("notification") && (lower.contains("padho") || lower.contains("batao") || lower.contains("read")) -> {
                val res = toolExecutionEngine.executeTool(ZoyaToolCall("11", "readLatestNotification", emptyMap()))
                liveSessionManager.sendTextPrompt("Read Notification: ${res.message}")
            }
            lower.contains("call") || lower.contains("phone lagao") -> {
                val name = lower.substringAfter("call").substringAfter("to").trim()
                if (name.isNotBlank()) {
                    val res = toolExecutionEngine.executeTool(ZoyaToolCall("12", "searchAndCallContact", mapOf("contactName" to name)))
                    liveSessionManager.sendTextPrompt(res.message)
                } else {
                    liveSessionManager.sendTextPrompt(commandText)
                }
            }
            lower == "stop" || lower == "ruk jao" || lower == "chup" -> {
                interruptZoya()
            }
            else -> {
                liveSessionManager.sendTextPrompt(commandText)
            }
        }
    }

    fun confirmPendingAction() {
        val action = _pendingConfirmation.value ?: return
        action.onConfirm()
        _pendingConfirmation.value = null
    }

    fun cancelPendingAction() {
        val action = _pendingConfirmation.value ?: return
        action.onCancel()
        _pendingConfirmation.value = null
    }

    fun answerIncomingCall() {
        toolExecutionEngine.executeTool(ZoyaToolCall("c1", "handleCallAction", mapOf("action" to "pick")))
    }

    fun endIncomingCall() {
        toolExecutionEngine.executeTool(ZoyaToolCall("c2", "handleCallAction", mapOf("action" to "disconnect")))
    }

    fun silenceIncomingCall() {
        toolExecutionEngine.executeTool(ZoyaToolCall("c3", "handleCallAction", mapOf("action" to "silent")))
    }

    fun retryConnection() {
        liveSessionManager.clearErrorAndResume(viewModelScope)
    }

    fun refreshPermissions() {
        permissionManager.refreshAll()
    }

    override fun onCleared() {
        audioDeviceManager.stopMonitoring()
        voiceSpeaker.release()
        liveSessionManager.release()
        super.onCleared()
    }
}
