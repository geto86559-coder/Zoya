package com.example.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapabilityItem(
    val id: String,
    val title: String,
    val explanation: String,
    val isEnabled: Boolean,
    val requiresOsPermission: Boolean = false,
    val permissionKey: String? = null,
    val settingsAction: String? = null
)

class PrivacyManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("zoya_privacy_prefs", Context.MODE_PRIVATE)

    private val _capabilities = MutableStateFlow<List<CapabilityItem>>(emptyList())
    val capabilities: StateFlow<List<CapabilityItem>> = _capabilities.asStateFlow()

    init {
        loadCapabilities()
    }

    fun loadCapabilities() {
        val list = listOf(
            CapabilityItem(
                id = "microphone",
                title = "Microphone",
                explanation = "Direct audio input for streaming real-time conversations with Zoya.",
                isEnabled = prefs.getBoolean("cap_microphone", true),
                requiresOsPermission = true,
                permissionKey = "microphone"
            ),
            CapabilityItem(
                id = "background_assistant",
                title = "Background Assistant",
                explanation = "Allows Zoya foreground service to stay awake when the app is minimized.",
                isEnabled = prefs.getBoolean("cap_background_assistant", true)
            ),
            CapabilityItem(
                id = "wake_word",
                title = "Wake Word Detection",
                explanation = "Continuously listens for 'Hey Zoya' to awaken hands-free without tapping.",
                isEnabled = prefs.getBoolean("cap_wake_word", false)
            ),
            CapabilityItem(
                id = "notifications",
                title = "Notifications",
                explanation = "Displays ongoing voice status and quick alert prompts.",
                isEnabled = prefs.getBoolean("cap_notifications", true),
                requiresOsPermission = true,
                permissionKey = "notifications"
            ),
            CapabilityItem(
                id = "contacts",
                title = "Contacts Integration",
                explanation = "Allows searching contacts for voice dialing and smart message drafting.",
                isEnabled = prefs.getBoolean("cap_contacts", false),
                requiresOsPermission = true,
                permissionKey = "contacts"
            ),
            CapabilityItem(
                id = "phone_calls",
                title = "Phone Calling",
                explanation = "Initiates voice phone calls when you instruct Zoya.",
                isEnabled = prefs.getBoolean("cap_phone_calls", false),
                requiresOsPermission = true,
                permissionKey = "phone_calls"
            ),
            CapabilityItem(
                id = "notification_access",
                title = "Notification Access",
                explanation = "Allows Zoya to read incoming alerts so you never miss urgent updates.",
                isEnabled = prefs.getBoolean("cap_notification_access", false)
            ),
            CapabilityItem(
                id = "screen_assistant",
                title = "Screen Assistant",
                explanation = "Allows Zoya to analyze on-screen context when asked.",
                isEnabled = prefs.getBoolean("cap_screen_assistant", false)
            ),
            CapabilityItem(
                id = "screen_capture",
                title = "Screen Capture",
                explanation = "Takes screenshots only when you explicitly say 'Zoya dekho ye kya hai'.",
                isEnabled = prefs.getBoolean("cap_screen_capture", false)
            ),
            CapabilityItem(
                id = "floating_orb",
                title = "Floating Hologram Orb",
                explanation = "Draws an animated mini-orb over other apps for fast access.",
                isEnabled = prefs.getBoolean("cap_floating_orb", false),
                requiresOsPermission = true,
                permissionKey = "floating_orb"
            ),
            CapabilityItem(
                id = "voice_identity",
                title = "Voice Identity",
                explanation = "Matches your unique vocal pitch to prevent accidental stranger activation.",
                isEnabled = prefs.getBoolean("cap_voice_identity", false)
            ),
            CapabilityItem(
                id = "bluetooth",
                title = "Bluetooth Headset Routing",
                explanation = "Routes mic and speaker to paired Bluetooth earbuds and car audio.",
                isEnabled = prefs.getBoolean("cap_bluetooth", true)
            ),
            CapabilityItem(
                id = "location",
                title = "Location Context",
                explanation = "Provides local weather, nearby places, and accurate local time.",
                isEnabled = prefs.getBoolean("cap_location", false)
            ),
            CapabilityItem(
                id = "camera",
                title = "Camera Vision",
                explanation = "Allows Zoya to inspect real-world objects when you request visual help.",
                isEnabled = prefs.getBoolean("cap_camera", false)
            )
        )
        _capabilities.value = list
    }

    fun setCapabilityEnabled(id: String, enabled: Boolean) {
        prefs.edit().putBoolean("cap_$id", enabled).apply()
        loadCapabilities()
    }

    fun isCapabilityEnabled(id: String, default: Boolean = false): Boolean {
        return prefs.getBoolean("cap_$id", default)
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }

    fun getCustomApiKey(): String {
        return prefs.getString("custom_gemini_api_key", "") ?: ""
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString("custom_gemini_api_key", key.trim()).apply()
    }
}
