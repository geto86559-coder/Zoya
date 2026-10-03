package com.example.security

import android.Manifest
import android.os.Build

sealed class PermissionRequirement(
    val key: String,
    val permissionName: String,
    val title: String,
    val description: String,
    val isCritical: Boolean = false,
    val iconName: String = "mic"
) {
    object Microphone : PermissionRequirement(
        key = "microphone",
        permissionName = Manifest.permission.RECORD_AUDIO,
        title = "Microphone",
        description = "For real-time voice conversations and wake-word detection with Zoya.",
        isCritical = true,
        iconName = "mic"
    )

    object Notifications : PermissionRequirement(
        key = "notifications",
        permissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            ""
        },
        title = "Notifications",
        description = "Keeps Zoya background listening active and delivers quick voice reminders.",
        isCritical = false,
        iconName = "notifications"
    )

    object Contacts : PermissionRequirement(
        key = "contacts",
        permissionName = Manifest.permission.READ_CONTACTS,
        title = "Contacts",
        description = "Allows Zoya to recognize names when you ask to call or message someone.",
        isCritical = false,
        iconName = "contacts"
    )

    object PhoneCalls : PermissionRequirement(
        key = "phone_calls",
        permissionName = Manifest.permission.CALL_PHONE,
        title = "Phone Calls",
        description = "Allows Zoya to initiate phone calls when you request voice dialing.",
        isCritical = false,
        iconName = "phone"
    )

    object ScreenAssistant : PermissionRequirement(
        key = "screen_assistant",
        permissionName = "android.permission.BIND_ACCESSIBILITY_SERVICE",
        title = "Screen Assistant",
        description = "Optional future visual assistance to help explain what's on your screen.",
        isCritical = false,
        iconName = "visibility"
    )

    object FloatingOrb : PermissionRequirement(
        key = "floating_orb",
        permissionName = "android.permission.SYSTEM_ALERT_WINDOW",
        title = "Floating Orb",
        description = "Optional overlay so Zoya's holographic orb stays accessible over other apps.",
        isCritical = false,
        iconName = "bubble_chart"
    )

    companion object {
        val ALL = listOf(Microphone, Notifications, Contacts, PhoneCalls, ScreenAssistant, FloatingOrb)
        val ONBOARDING_ITEMS = listOf(Microphone, Notifications, Contacts, PhoneCalls, ScreenAssistant, FloatingOrb)
    }
}
