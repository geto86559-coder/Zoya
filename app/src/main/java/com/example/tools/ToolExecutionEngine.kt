package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.domain.models.ZoyaToolCall
import com.example.domain.models.ZoyaToolResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ToolExecutionEngine(private val context: Context) {

    fun executeTool(toolCall: ZoyaToolCall): ZoyaToolResult {
        return try {
            when (toolCall.name) {
                "getDeviceTime" -> {
                    val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                    val date = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
                    ZoyaToolResult(
                        callId = toolCall.id,
                        success = true,
                        result = mapOf("time" to time, "date" to date),
                        message = "Time abhi $time hai boss!"
                    )
                }

                "initiatePhoneCall" -> {
                    val phoneNumber = toolCall.arguments["phoneNumber"] as? String ?: ""
                    if (phoneNumber.isNotBlank()) {
                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$phoneNumber")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(dialIntent)
                        ZoyaToolResult(
                            callId = toolCall.id,
                            success = true,
                            result = mapOf("phoneNumber" to phoneNumber),
                            message = "$phoneNumber par dialer khol diya hai boss."
                        )
                    } else {
                        ZoyaToolResult(
                            callId = toolCall.id,
                            success = false,
                            result = null,
                            message = "Number nahi mila."
                        )
                    }
                }

                else -> {
                    ZoyaToolResult(
                        callId = toolCall.id,
                        success = false,
                        result = null,
                        message = "Ye tool feature Part 2 ke liye ready ho raha hai!"
                    )
                }
            }
        } catch (e: Exception) {
            ZoyaToolResult(
                callId = toolCall.id,
                success = false,
                result = null,
                message = "Error executing tool: ${e.localizedMessage}"
            )
        }
    }
}
