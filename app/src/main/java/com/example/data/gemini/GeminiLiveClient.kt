package com.example.data.gemini

import android.util.Log
import com.example.domain.models.LiveSessionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class LiveConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

class GeminiLiveClient(
    private val apiKey: String,
    private val config: LiveSessionConfig = LiveSessionConfig(),
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onTranscriptReceived: (String) -> Unit,
    private val onInterruptedReceived: () -> Unit,
    private val onTurnCompleted: () -> Unit,
    private val onErrorReceived: (String) -> Unit
) {
    private val tag = "GeminiLiveClient"

    private val _connectionState = MutableStateFlow(LiveConnectionState.DISCONNECTED)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isIntentionalDisconnect = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    private val wsEndpoint: String
        get() = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"

    fun connect(scope: CoroutineScope) {
        if (_connectionState.value == LiveConnectionState.CONNECTED ||
            _connectionState.value == LiveConnectionState.CONNECTING
        ) {
            return
        }

        isIntentionalDisconnect = false
        _connectionState.value = LiveConnectionState.CONNECTING

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(tag, "Gemini API key is blank or placeholder")
            _connectionState.value = LiveConnectionState.ERROR
            onErrorReceived("API key missing. Secret panel mein GEMINI_API_KEY add kar lo boss!")
            return
        }

        val requestBuilder = Request.Builder().url(wsEndpoint)
        if (apiKey.startsWith("AQ.") || apiKey.startsWith("ya29.")) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        val request = requestBuilder.build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "Gemini Live WebSocket opened!")
                _connectionState.value = LiveConnectionState.CONNECTED
                reconnectAttempts = 0

                // Send setup message
                val setupJson = GeminiLiveProtocol.createSetupMessage(config)
                webSocket.send(setupJson)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = GeminiLiveProtocol.parseServerMessage(text)
                if (parsed.isInterrupted) {
                    onInterruptedReceived()
                }
                parsed.audioBytes.forEach { audioData ->
                    onAudioReceived(audioData)
                }
                parsed.transcript?.let { textChunk ->
                    onTranscriptReceived(textChunk)
                }
                if (parsed.isTurnComplete) {
                    onTurnCompleted()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code / $reason")
                _connectionState.value = LiveConnectionState.DISCONNECTED
                if (!isIntentionalDisconnect && config.autoReconnect) {
                    scheduleReconnect(scope)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure", t)
                _connectionState.value = LiveConnectionState.ERROR
                onErrorReceived("Connection issue: ${t.localizedMessage ?: "Network error"}")
                if (!isIntentionalDisconnect && config.autoReconnect) {
                    scheduleReconnect(scope)
                }
            }
        })
    }

    private fun scheduleReconnect(scope: CoroutineScope) {
        if (reconnectAttempts >= config.maxReconnectAttempts) {
            Log.w(tag, "Max reconnect attempts reached")
            _connectionState.value = LiveConnectionState.ERROR
            onErrorReceived("Internet gaya hua hai 😅. Connection aate hi reconnect karenge.")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            reconnectAttempts++
            _connectionState.value = LiveConnectionState.RECONNECTING
            val delayMs = 1500L * reconnectAttempts
            delay(delayMs)
            connect(scope)
        }
    }

    fun sendAudioChunk(pcmBytes: ByteArray) {
        if (_connectionState.value != LiveConnectionState.CONNECTED) return
        val message = GeminiLiveProtocol.createAudioChunkMessage(pcmBytes, config.sampleRateInput)
        webSocket?.send(message)
    }

    fun sendTextMessage(text: String) {
        if (_connectionState.value != LiveConnectionState.CONNECTED) return
        val message = GeminiLiveProtocol.createTextMessage(text)
        webSocket?.send(message)
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectAttempts = 0
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebSocket", e)
        } finally {
            webSocket = null
            _connectionState.value = LiveConnectionState.DISCONNECTED
        }
    }
}
