package com.example.data.gemini

import android.util.Base64
import com.example.domain.models.LiveSessionConfig
import org.json.JSONArray
import org.json.JSONObject

object GeminiLiveProtocol {

    fun createSetupMessage(config: LiveSessionConfig): String {
        val root = JSONObject()
        val setup = JSONObject()

        setup.put("model", "models/${config.model}")

        val generationConfig = JSONObject()
        val modalities = JSONArray()
        config.responseModalities.forEach { modalities.put(it) }
        generationConfig.put("responseModalities", modalities)

        val speechConfig = JSONObject()
        val voiceConfig = JSONObject()
        val prebuiltVoiceConfig = JSONObject()
        prebuiltVoiceConfig.put("voiceName", config.voiceName)
        voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
        speechConfig.put("voiceConfig", voiceConfig)
        generationConfig.put("speechConfig", speechConfig)

        setup.put("generationConfig", generationConfig)

        val systemInstruction = JSONObject()
        val parts = JSONArray()
        val part = JSONObject()
        part.put("text", config.systemInstruction)
        parts.put(part)
        systemInstruction.put("parts", parts)
        setup.put("systemInstruction", systemInstruction)

        root.put("setup", setup)
        return root.toString()
    }

    fun createAudioChunkMessage(pcmBytes: ByteArray, sampleRate: Int = 16000): String {
        val base64Data = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        val root = JSONObject()
        val realtimeInput = JSONObject()
        val mediaChunks = JSONArray()
        val chunk = JSONObject()
        chunk.put("mimeType", "audio/pcm;rate=$sampleRate")
        chunk.put("data", base64Data)
        mediaChunks.put(chunk)
        realtimeInput.put("mediaChunks", mediaChunks)
        root.put("realtimeInput", realtimeInput)
        return root.toString()
    }

    fun createTextMessage(userText: String): String {
        val root = JSONObject()
        val clientContent = JSONObject()
        val turns = JSONArray()
        val turn = JSONObject()
        turn.put("role", "user")
        val parts = JSONArray()
        val part = JSONObject()
        part.put("text", userText)
        parts.put(part)
        turn.put("parts", parts)
        turns.put(turn)
        clientContent.put("turns", turns)
        clientContent.put("turnComplete", true)
        root.put("clientContent", clientContent)
        return root.toString()
    }

    data class ServerResponse(
        val audioBytes: List<ByteArray> = emptyList(),
        val transcript: String? = null,
        val isTurnComplete: Boolean = false,
        val isInterrupted: Boolean = false,
        val functionCalls: List<FunctionCallData> = emptyList()
    )

    data class FunctionCallData(
        val id: String,
        val name: String,
        val argsJson: String
    )

    fun parseServerMessage(jsonString: String): ServerResponse {
        val audioList = mutableListOf<ByteArray>()
        var textBuilder = StringBuilder()
        var turnComplete = false
        var interrupted = false
        val funcCalls = mutableListOf<FunctionCallData>()

        try {
            val root = JSONObject(jsonString)

            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")
                if (serverContent.optBoolean("interrupted", false)) {
                    interrupted = true
                }
                if (serverContent.optBoolean("turnComplete", false)) {
                    turnComplete = true
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                textBuilder.append(part.getString("text"))
                            }
                            if (part.has("inlineData")) {
                                val inline = part.getJSONObject("inlineData")
                                val dataBase64 = inline.optString("data")
                                if (dataBase64.isNotEmpty()) {
                                    val bytes = Base64.decode(dataBase64, Base64.DEFAULT)
                                    audioList.add(bytes)
                                }
                            }
                        }
                    }
                }
            }

            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val fCalls = toolCall.optJSONArray("functionCalls")
                if (fCalls != null) {
                    for (i in 0 until fCalls.length()) {
                        val fc = fCalls.getJSONObject(i)
                        funcCalls.add(
                            FunctionCallData(
                                id = fc.optString("id", System.currentTimeMillis().toString()),
                                name = fc.optString("name"),
                                argsJson = fc.optJSONObject("args")?.toString() ?: "{}"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Non-fatal parse warning
        }

        return ServerResponse(
            audioBytes = audioList,
            transcript = if (textBuilder.isNotEmpty()) textBuilder.toString() else null,
            isTurnComplete = turnComplete,
            isInterrupted = interrupted,
            functionCalls = funcCalls
        )
    }
}
