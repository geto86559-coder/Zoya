package com.example.services

import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.security.PrivacyManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ZoyaNotificationListenerService : NotificationListenerService() {
    private val tag = "ZoyaNotifListener"

    companion object {
        var isServiceConnected: Boolean = false
            private set

        private val _newNotificationEvents = MutableSharedFlow<ZoyaNotificationItem>(extraBufferCapacity = 16)
        val newNotificationEvents: SharedFlow<ZoyaNotificationItem> = _newNotificationEvents.asSharedFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        Log.d(tag, "ZoyaNotificationListenerService connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
        Log.d(tag, "ZoyaNotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        // Ignore self notifications and system persistent notifications
        if (pkg == packageName || sbn.isOngoing) return

        val privacyManager = PrivacyManager(applicationContext)
        if (!privacyManager.isCapabilityEnabled("notification_access", false)) {
            return
        }

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked ?: false

        val appName = NotificationStore.resolveAppName(pkg)
        val notifItem = ZoyaNotificationItem(
            id = "${sbn.id}_${sbn.postTime}",
            packageName = pkg,
            appDisplayName = appName,
            sender = if (isLocked) "Private Sender" else title,
            text = if (isLocked) "New message while device is locked" else text
        )

        NotificationStore.addNotification(notifItem)
        _newNotificationEvents.tryEmit(notifItem)
    }
}
