package com.example.domain.models

/**
 * Representation of tool calls requested by Gemini Live or local commands.
 */
data class ZoyaToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)

data class ZoyaToolResult(
    val callId: String,
    val success: Boolean,
    val result: Any?,
    val message: String
)
