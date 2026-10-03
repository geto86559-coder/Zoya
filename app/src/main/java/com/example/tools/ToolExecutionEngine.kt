package com.example.tools

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.example.domain.models.ConfirmationLevel
import com.example.domain.models.PendingAction
import com.example.domain.models.ZoyaToolCall
import com.example.domain.models.ZoyaToolResult
import com.example.services.CallAssistantManager
import com.example.services.NotificationStore
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContactMatch(
    val id: String,
    val name: String,
    val phoneNumber: String
)

class ToolExecutionEngine(
    private val context: Context,
    private val onConfirmationRequired: ((PendingAction) -> Unit)? = null,
    private val callAssistantManager: CallAssistantManager? = null
) {
    private var isBusyWithTask = false

    private val commonAppPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "instagram" to "com.instagram.android",
        "chrome" to "com.android.chrome",
        "calculator" to "com.google.android.calculator",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "phone" to "com.google.android.dialer",
        "camera" to "com.google.android.GoogleCamera",
        "spotify" to "com.spotify.music",
        "settings" to "com.android.settings"
    )

    fun executeTool(toolCall: ZoyaToolCall): ZoyaToolResult {
        if (isBusyWithTask && toolCall.name != "stop") {
            return ZoyaToolResult(
                callId = toolCall.id,
                success = false,
                result = null,
                message = "Ek second boss, pehle ye task finish karne do 😏."
            )
        }

        return try {
            when (toolCall.name) {
                "getDeviceTime" -> getDeviceTime(toolCall)
                "openApp" -> openApp(toolCall)
                "searchAndCallContact" -> searchAndCallContact(toolCall)
                "sendWhatsAppMessage" -> sendWhatsAppMessage(toolCall)
                "sendGmail" -> sendGmail(toolCall)
                "openSettings" -> openSettings(toolCall)
                "readLatestNotification" -> readLatestNotification(toolCall)
                "suggestNotificationReply" -> suggestNotificationReply(toolCall)
                "handleCallAction" -> handleCallAction(toolCall)
                "stop" -> stopCurrentOperation(toolCall)
                else -> {
                    // Try to interpret as app launch or speech
                    if (toolCall.name.startsWith("open_") || toolCall.arguments.containsKey("appName")) {
                        openApp(toolCall)
                    } else {
                        ZoyaToolResult(
                            callId = toolCall.id,
                            success = false,
                            result = null,
                            message = "Ye action abhi supported nahi hai boss."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            ZoyaToolResult(
                callId = toolCall.id,
                success = false,
                result = null,
                message = "Action perform karne mein issue aaya: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    private fun getDeviceTime(toolCall: ZoyaToolCall): ZoyaToolResult {
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val date = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
        return ZoyaToolResult(
            callId = toolCall.id,
            success = true,
            result = mapOf("time" to time, "date" to date),
            message = "Time abhi $time hai aur date $date hai boss!"
        )
    }

    private fun openApp(toolCall: ZoyaToolCall): ZoyaToolResult {
        val appQuery = (toolCall.arguments["appName"] ?: toolCall.arguments["packageName"] ?: "").toString().trim().lowercase()
        if (appQuery.isBlank()) {
            return ZoyaToolResult(toolCall.id, false, null, "Kaunsa app kholna hai boss?")
        }

        val targetPackage = commonAppPackages[appQuery] ?: if (appQuery.contains(".")) appQuery else findPackageByLabel(appQuery)

        if (targetPackage.isNullOrBlank()) {
            return ZoyaToolResult(
                toolCall.id,
                false,
                null,
                "Mujhe '$appQuery' app phone mein nahi mila boss."
            )
        }

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            val displayName = appQuery.replaceFirstChar { it.uppercase() }
            ZoyaToolResult(toolCall.id, true, mapOf("package" to targetPackage), "$displayName open kar diya hai boss! ✨")
        } else {
            ZoyaToolResult(toolCall.id, false, null, "'$appQuery' launch karne ka intent nahi mila.")
        }
    }

    private fun findPackageByLabel(query: String): String? {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installed) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(query)) {
                return app.packageName
            }
        }
        return null
    }

    private fun searchAndCallContact(toolCall: ZoyaToolCall): ZoyaToolResult {
        val contactName = (toolCall.arguments["contactName"] ?: toolCall.arguments["name"] ?: "").toString().trim()
        if (contactName.isBlank()) {
            return ZoyaToolResult(toolCall.id, false, null, "Kisko call lagana hai? Contact name batao.")
        }

        val matches = queryContacts(contactName)
        if (matches.isEmpty()) {
            return ZoyaToolResult(
                toolCall.id,
                false,
                null,
                "Contacts mein '$contactName' nahi mila boss."
            )
        }

        // AMBIGUITY RESOLUTION: Multiple contacts with same or similar name
        if (matches.size > 1) {
            val contactChoices = matches.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
            return ZoyaToolResult(
                toolCall.id,
                false,
                mapOf("matches" to matches),
                "Mujhe multiple contacts mile: $contactChoices. Kispar call lagau?"
            )
        }

        val target = matches.first()

        // LEVEL 2 CONFIRMATION
        val dialAction = {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${target.phoneNumber}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        }

        if (onConfirmationRequired != null) {
            onConfirmationRequired.invoke(
                PendingAction(
                    title = "Confirm Call",
                    description = "${target.name} (${target.phoneNumber}) ko call lagayein?",
                    level = ConfirmationLevel.LEVEL_2_CONFIRMATION,
                    actionType = "CALL",
                    payload = mapOf("name" to target.name, "phone" to target.phoneNumber),
                    onConfirm = dialAction
                )
            )
            return ZoyaToolResult(
                toolCall.id,
                true,
                mapOf("pendingConfirmation" to true, "contact" to target),
                "${target.name} ko call lagau boss? Confirm karo please."
            )
        } else {
            dialAction()
            return ZoyaToolResult(
                toolCall.id,
                true,
                mapOf("contact" to target),
                "${target.name} ke number par dialer open kar diya hai."
            )
        }
    }

    private fun queryContacts(query: String): List<ContactMatch> {
        val results = mutableListOf<ContactMatch>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val id = it.getString(idIdx) ?: ""
                    val name = it.getString(nameIdx) ?: query
                    val number = it.getString(numIdx) ?: ""
                    if (number.isNotBlank()) {
                        results.add(ContactMatch(id, name, number))
                    }
                }
            }
        } catch (e: Exception) {
            // Permission or security exception
        }
        return results
    }

    private fun sendWhatsAppMessage(toolCall: ZoyaToolCall): ZoyaToolResult {
        val contactName = (toolCall.arguments["contactName"] ?: toolCall.arguments["name"] ?: "").toString().trim()
        val message = (toolCall.arguments["message"] ?: toolCall.arguments["text"] ?: "").toString().trim()

        if (message.isBlank()) {
            return ZoyaToolResult(toolCall.id, false, null, "WhatsApp message mein kya bhejna hai boss?")
        }

        var targetPhone = ""
        var targetDisplayName = contactName

        if (contactName.isNotBlank()) {
            val matches = queryContacts(contactName)
            if (matches.size > 1) {
                val choices = matches.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                return ZoyaToolResult(
                    toolCall.id,
                    false,
                    null,
                    "Ambiguous contact! Mujhe multiple options mile: $choices. Kaunsa select karoon?"
                )
            } else if (matches.size == 1) {
                targetPhone = matches.first().phoneNumber.replace("[^0-9+]".toRegex(), "")
                targetDisplayName = matches.first().name
            }
        }

        val sendAction = {
            val encodedText = URLEncoder.encode(message, "UTF-8")
            val uri = if (targetPhone.isNotBlank()) {
                val cleanDigits = targetPhone.replace("+", "")
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=$encodedText")
            } else {
                Uri.parse("whatsapp://send?text=$encodedText")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        if (onConfirmationRequired != null) {
            onConfirmationRequired.invoke(
                PendingAction(
                    title = "Confirm WhatsApp Message",
                    description = "$targetDisplayName ko message bhejna hai:\n\"$message\"",
                    level = ConfirmationLevel.LEVEL_2_CONFIRMATION,
                    actionType = "WHATSAPP",
                    payload = mapOf("recipient" to targetDisplayName, "message" to message),
                    onConfirm = sendAction
                )
            )
            return ZoyaToolResult(
                toolCall.id,
                true,
                mapOf("pending" to true),
                "$targetDisplayName ko ye WhatsApp message bhejne se pehle check kar lo boss: '$message'. Confirm?"
            )
        } else {
            sendAction()
            return ZoyaToolResult(toolCall.id, true, null, "WhatsApp open kar diya hai boss!")
        }
    }

    private fun sendGmail(toolCall: ZoyaToolCall): ZoyaToolResult {
        val recipient = (toolCall.arguments["recipientEmail"] ?: toolCall.arguments["email"] ?: "").toString().trim()
        val subject = (toolCall.arguments["subject"] ?: "Message via Zoya Assistant").toString().trim()
        val body = (toolCall.arguments["body"] ?: toolCall.arguments["message"] ?: "").toString().trim()

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${if (recipient.isNotBlank()) recipient else ""}")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(emailIntent)
        return ZoyaToolResult(
            toolCall.id,
            true,
            mapOf("recipient" to recipient),
            "Email draft prepare ho gaya hai boss. Send karne se pehle review kar lo!"
        )
    }

    private fun openSettings(toolCall: ZoyaToolCall): ZoyaToolResult {
        val settingType = (toolCall.arguments["settingType"] ?: toolCall.arguments["type"] ?: "").toString().trim().lowercase()

        val intentAction = when {
            settingType.contains("wifi") || settingType.contains("wi-fi") -> Settings.ACTION_WIFI_SETTINGS
            settingType.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            settingType.contains("battery") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            settingType.contains("notification") -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            settingType.contains("accessibility") || settingType.contains("screen") -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            settingType.contains("sound") || settingType.contains("volume") -> Settings.ACTION_SOUND_SETTINGS
            settingType.contains("display") || settingType.contains("brightness") -> Settings.ACTION_DISPLAY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        val intent = Intent(intentAction).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)

        return ZoyaToolResult(
            toolCall.id,
            true,
            mapOf("action" to intentAction),
            "${settingType.replaceFirstChar { it.uppercase() }} settings open kar di hain boss."
        )
    }

    private fun readLatestNotification(toolCall: ZoyaToolCall): ZoyaToolResult {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true) {
            return ZoyaToolResult(
                toolCall.id,
                false,
                null,
                "Phone locked hai boss. Security privacy ke liye pehle phone unlock karo, phir main padhke sunati hoon! 🔒"
            )
        }

        val latest = NotificationStore.getLatest()
        return if (latest != null) {
            ZoyaToolResult(
                toolCall.id,
                true,
                mapOf("notification" to latest),
                "Tumhe ${latest.appDisplayName} pe ${latest.sender} ka message aaya hai: \"${latest.text}\""
            )
        } else {
            ZoyaToolResult(
                toolCall.id,
                true,
                null,
                "Abhi koi naya unread message ya notification nahi hai boss!"
            )
        }
    }

    private fun suggestNotificationReply(toolCall: ZoyaToolCall): ZoyaToolResult {
        val suggestions = listOf(
            "Haan main 10 minute mein aa raha hoon.",
            "Theek hai boss, check karke batata hoon.",
            "Abhi thoda busy hoon, thodi der mein call karta hoon!"
        )
        return ZoyaToolResult(
            toolCall.id,
            true,
            mapOf("suggestions" to suggestions),
            "Aap ye reply bhej sakte ho: \"${suggestions.first()}\". Bhej du?"
        )
    }

    private fun handleCallAction(toolCall: ZoyaToolCall): ZoyaToolResult {
        val action = (toolCall.arguments["action"] ?: "").toString().lowercase()
        return when (action) {
            "pick", "answer" -> {
                val res = callAssistantManager?.answerCall() ?: false
                ZoyaToolResult(toolCall.id, res, null, if (res) "Call pick kar rahi hoon!" else "Call answer karne ke liye system screen use karein.")
            }
            "disconnect", "end", "cut" -> {
                val res = callAssistantManager?.endCall() ?: false
                ZoyaToolResult(toolCall.id, res, null, if (res) "Call cut kar diya hai." else "Call screen par disconnect tap karein.")
            }
            "silent", "mute" -> {
                callAssistantManager?.silenceRinger()
                ZoyaToolResult(toolCall.id, true, null, "Ringer silent kar diya hai boss.")
            }
            else -> ZoyaToolResult(toolCall.id, false, null, "Call command samajh nahi aayi.")
        }
    }

    private fun stopCurrentOperation(toolCall: ZoyaToolCall): ZoyaToolResult {
        isBusyWithTask = false
        return ZoyaToolResult(toolCall.id, true, null, "All running operations stopped boss.")
    }
}
