package com.example.security

import android.content.Context

class PermissionGuard(
    val permissionManager: PermissionManager
) {
    sealed class GuardResult<out T> {
        data class Success<out T>(val data: T) : GuardResult<T>()
        data class Blocked(
            val requirement: PermissionRequirement,
            val messageHinglish: String
        ) : GuardResult<Nothing>()
    }

    fun <T> runWithMic(
        block: () -> T
    ): GuardResult<T> {
        return if (permissionManager.isGranted(PermissionRequirement.Microphone)) {
            GuardResult.Success(block())
        } else {
            GuardResult.Blocked(
                requirement = PermissionRequirement.Microphone,
                messageHinglish = "Mic permission off hai. Settings se allow kar do, phir main ready hoon! 🎙"
            )
        }
    }
}
