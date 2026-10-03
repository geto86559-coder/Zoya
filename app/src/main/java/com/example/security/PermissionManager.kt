package com.example.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionManager(private val context: Context) {

    private val _permissionStates = MutableStateFlow<Map<String, PermissionState>>(emptyMap())
    val permissionStates: StateFlow<Map<String, PermissionState>> = _permissionStates.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        val updated = mutableMapOf<String, PermissionState>()
        PermissionRequirement.ALL.forEach { req ->
            updated[req.key] = checkPermission(req)
        }
        _permissionStates.value = updated
    }

    fun checkPermission(requirement: PermissionRequirement): PermissionState {
        // Special case for overlay and accessibility
        if (requirement is PermissionRequirement.FloatingOrb) {
            return if (Settings.canDrawOverlays(context)) {
                PermissionState.GRANTED
            } else {
                PermissionState.DENIED
            }
        }

        if (requirement.permissionName.isEmpty()) {
            return PermissionState.GRANTED // Not required on this Android API level
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            requirement.permissionName
        ) == PackageManager.PERMISSION_GRANTED

        return if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }

    fun isGranted(requirement: PermissionRequirement): Boolean {
        return checkPermission(requirement) == PermissionState.GRANTED
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
