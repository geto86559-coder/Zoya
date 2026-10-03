package com.example.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.security.PrivacyManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenSafetyWarning(
    val warningText: String,
    val sourcePackage: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ZoyaAccessibilityService : AccessibilityService() {
    private val tag = "ZoyaAccessibility"

    companion object {
        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _screenWarnings = MutableSharedFlow<ScreenSafetyWarning>(extraBufferCapacity = 8)
        val screenWarnings: SharedFlow<ScreenSafetyWarning> = _screenWarnings.asSharedFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceActive.value = true
        Log.d(tag, "Zoya Screen Assistant connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val privacyManager = PrivacyManager(applicationContext)
        if (!privacyManager.isCapabilityEnabled("screen_assistant", false)) {
            return
        }

        val pkg = event.packageName?.toString() ?: ""
        val rootNode = rootInActiveWindow ?: return

        try {
            inspectNodeForMistakes(rootNode, pkg)
        } catch (e: Exception) {
            Log.w(tag, "Error inspecting node tree", e)
        }
    }

    private fun inspectNodeForMistakes(node: AccessibilityNodeInfo, pkg: String) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val combined = "$text $contentDesc".lowercase()

        // Check for destructive action indicators
        if (combined.contains("delete all") || combined.contains("erase all data") || combined.contains("clear all chats")) {
            _screenWarnings.tryEmit(
                ScreenSafetyWarning(
                    warningText = "Ye button delete karne wala hai. Confirm karne se pehle dhyan se check kar lo boss!",
                    sourcePackage = pkg
                )
            )
            return
        }

        // Traverse children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                inspectNodeForMistakes(child, pkg)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(tag, "Screen Assistant interrupted")
    }

    override fun onDestroy() {
        _isServiceActive.value = false
        super.onDestroy()
    }
}
