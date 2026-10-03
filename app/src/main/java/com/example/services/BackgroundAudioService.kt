package com.example.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.BuildConfig
import com.example.data.gemini.LiveSessionManager
import com.example.domain.models.ZoyaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BackgroundAudioService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): BackgroundAudioService = this@BackgroundAudioService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var liveSessionManager: LiveSessionManager? = null
        private set

    companion object {
        const val ACTION_START = "com.example.action.START_VOICE"
        const val ACTION_STOP = "com.example.action.STOP_VOICE"
        const val ACTION_INTERRUPT = "com.example.action.INTERRUPT"

        fun start(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        val privacyManager = com.example.security.PrivacyManager(this)
        val apiKey = privacyManager.getCustomApiKey().ifBlank {
            try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        }

        liveSessionManager = LiveSessionManager(
            context = applicationContext,
            apiKey = apiKey
        )

        // Observe state to update notification
        liveSessionManager?.zoyaState?.onEach { state ->
            updateNotification(state)
        }?.launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startInForeground()
                liveSessionManager?.startSession(serviceScope)
            }
            ACTION_STOP -> {
                liveSessionManager?.stopSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_INTERRUPT -> {
                liveSessionManager?.onUserInterruption()
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = NotificationHelper.buildForegroundNotification(
            this,
            liveSessionManager?.zoyaState?.value ?: ZoyaState.LISTENING
        )
        val foregroundTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID,
            notification,
            foregroundTypes
        )
    }

    private fun updateNotification(state: ZoyaState) {
        val notification = NotificationHelper.buildForegroundNotification(this, state)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        liveSessionManager?.release()
        serviceScope.cancel()
        super.onDestroy()
    }
}
