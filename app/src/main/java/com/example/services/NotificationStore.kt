package com.example.services

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

data class ZoyaNotificationItem(
    val id: String,
    val packageName: String,
    val appDisplayName: String,
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

object NotificationStore {
    private val recentQueue = ConcurrentLinkedDeque<ZoyaNotificationItem>()
    private val appNameMap = mapOf(
        "com.whatsapp" to "WhatsApp",
        "org.telegram.messenger" to "Telegram",
        "com.instagram.android" to "Instagram",
        "com.google.android.apps.messaging" to "Messages",
        "com.google.android.gm" to "Gmail"
    )

    fun resolveAppName(pkg: String): String {
        return appNameMap[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    fun addNotification(item: ZoyaNotificationItem) {
        recentQueue.offerFirst(item)
        // Keep max 25 in memory cache
        while (recentQueue.size > 25) {
            recentQueue.pollLast()
        }
    }

    fun getLatest(): ZoyaNotificationItem? = recentQueue.peekFirst()

    fun getAllRecent(): List<ZoyaNotificationItem> = recentQueue.toList()

    fun clear() {
        recentQueue.clear()
    }
}
