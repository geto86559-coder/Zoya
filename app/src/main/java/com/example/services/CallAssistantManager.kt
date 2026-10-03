package com.example.services

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IncomingCallInfo(
    val phoneNumber: String,
    val callerName: String,
    val isRinging: Boolean = false
)

class CallAssistantManager(private val context: Context) {
    private val tag = "CallAssistantManager"

    private val _currentCall = MutableStateFlow<IncomingCallInfo?>(null)
    val currentCall: StateFlow<IncomingCallInfo?> = _currentCall.asStateFlow()

    fun onCallRinging(number: String, callerName: String) {
        _currentCall.value = IncomingCallInfo(
            phoneNumber = number,
            callerName = callerName.ifBlank { "Unknown Number" },
            isRinging = true
        )
    }

    fun onCallEnded() {
        _currentCall.value = null
    }

    @SuppressLint("MissingPermission")
    fun answerCall(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecom?.acceptRingingCall()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to accept call", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun endCall(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecom?.endCall()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to end call", e)
            false
        }
    }

    fun silenceRinger() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (e: Exception) {
            Log.e(tag, "Failed to silence ringer", e)
        }
    }
}
