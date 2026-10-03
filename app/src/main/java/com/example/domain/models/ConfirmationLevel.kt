package com.example.domain.models

enum class ConfirmationLevel {
    LEVEL_1_LOW_RISK,
    LEVEL_2_CONFIRMATION,
    LEVEL_3_AUTHENTICATION
}

data class PendingAction(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val description: String,
    val level: ConfirmationLevel,
    val actionType: String,
    val payload: Map<String, Any?>,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit = {}
)
