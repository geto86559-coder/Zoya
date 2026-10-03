package com.example.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

object ZoyaVoiceConstants {
    /**
     * Default spoken startup greeting. Natural, friendly, and feminine.
     */
    const val DEFAULT_STARTUP_INTRO = "Hi! Main Zoya hoon. Main ready hoon. Aap mujhe command de sakte ho."
}

class ZoyaVoiceSpeaker(
    private val context: Context,
    private val scope: CoroutineScope,
    private val audioDeviceManager: AudioDeviceManager? = null
) : TextToSpeech.OnInitListener {

    private val tag = "ZoyaVoiceSpeaker"
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingIntroAction: (() -> Unit)? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechAmplitude = MutableStateFlow(0f)
    val speechAmplitude: StateFlow<Float> = _speechAmplitude.asStateFlow()

    private var amplitudeSimulationJob: Job? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var currentOnDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsEngine = tts ?: return

            // Prefer Hindi (India) or English (India)
            val hiLocale = Locale("hi", "IN")
            val enInLocale = Locale("en", "IN")

            val langResult = when {
                ttsEngine.isLanguageAvailable(hiLocale) >= TextToSpeech.LANG_AVAILABLE -> ttsEngine.setLanguage(hiLocale)
                ttsEngine.isLanguageAvailable(enInLocale) >= TextToSpeech.LANG_AVAILABLE -> ttsEngine.setLanguage(enInLocale)
                else -> ttsEngine.setLanguage(Locale.getDefault())
            }

            // High-spirited, confident, feminine voice tone
            ttsEngine.setPitch(1.15f)
            ttsEngine.setSpeechRate(1.03f)

            // Configure audio attributes to USAGE_MEDIA so audio routes to Bluetooth buds if connected,
            // or built-in phone speaker if buds are not connected!
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            ttsEngine.setAudioAttributes(attributes)

            ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    startAmplitudeSimulation()
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    stopAmplitudeSimulation()
                    abandonAudioFocus()
                    currentOnDoneCallback?.invoke()
                    currentOnDoneCallback = null
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    stopAmplitudeSimulation()
                    abandonAudioFocus()
                    currentOnDoneCallback?.invoke()
                    currentOnDoneCallback = null
                }
            })

            isTtsReady = true
            Log.d(tag, "TextToSpeech initialized successfully (lang: $langResult)")

            pendingIntroAction?.invoke()
            pendingIntroAction = null
        } else {
            Log.e(tag, "Failed to initialize TextToSpeech engine")
        }
    }

    fun speakIntro(
        introText: String = ZoyaVoiceConstants.DEFAULT_STARTUP_INTRO,
        onDone: (() -> Unit)? = null
    ) {
        if (!isTtsReady) {
            Log.d(tag, "TTS not ready yet, queuing intro execution")
            pendingIntroAction = {
                speakText(introText, utteranceId = "zoya_startup_intro", onDone = onDone)
            }
            return
        }
        speakText(introText, utteranceId = "zoya_startup_intro", onDone = onDone)
    }

    fun speakText(
        text: String,
        utteranceId: String = "zoya_speech_${System.currentTimeMillis()}",
        onDone: (() -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        if (!isTtsReady) {
            Log.d(tag, "TTS not ready yet, queuing text")
            pendingIntroAction = {
                speakText(text, utteranceId, onDone)
            }
            return
        }

        try {
            audioDeviceManager?.updateAudioRoute()
            requestAudioFocus()

            currentOnDoneCallback = onDone

            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e(tag, "Error speaking text with TTS", e)
            abandonAudioFocus()
            onDone?.invoke()
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            stop()
                        }
                    }
                    .build()
                audioFocusRequest?.let { am.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to request audio focus in VoiceSpeaker", e)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to abandon audio focus in VoiceSpeaker", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping TTS", e)
        }
        _isSpeaking.value = false
        stopAmplitudeSimulation()
        abandonAudioFocus()
    }

    private fun startAmplitudeSimulation() {
        amplitudeSimulationJob?.cancel()
        amplitudeSimulationJob = scope.launch(Dispatchers.Default) {
            var step = 0
            while (isActive && _isSpeaking.value) {
                val base = (kotlin.math.sin(step * 0.4) * 0.35 + 0.45).toFloat()
                val variation = ((step % 3) * 0.1f)
                _speechAmplitude.value = (base + variation).coerceIn(0.1f, 0.9f)
                step++
                delay(60)
            }
            _speechAmplitude.value = 0f
        }
    }

    private fun stopAmplitudeSimulation() {
        amplitudeSimulationJob?.cancel()
        amplitudeSimulationJob = null
        _speechAmplitude.value = 0f
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down TTS", e)
        }
        tts = null
        isTtsReady = false
    }
}
