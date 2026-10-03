package com.example.services

import android.app.Service
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.BuildConfig
import com.example.data.gemini.LiveSessionManager
import com.example.domain.models.ZoyaState
import com.example.security.PrivacyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BackgroundAudioService : Service(), AudioManager.OnAudioFocusChangeListener {
    private val tag = "BackgroundAudioService"

    inner class LocalBinder : Binder() {
        fun getService(): BackgroundAudioService = this@BackgroundAudioService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var liveSessionManager: LiveSessionManager? = null
        private set

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Audio / Headset broadcast receiver
    private val audioRoutingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.d(tag, "Headset unplugged - pausing playback")
                    liveSessionManager?.onUserInterruption()
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    Log.d(tag, "Wired headset state changed: $state")
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                    Log.d(tag, "Bluetooth headset state changed: $state")
                }
            }
        }
    }

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

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // Register audio routing broadcast receiver
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(audioRoutingReceiver, filter)

        val privacyManager = PrivacyManager(this)
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

        // Setup local wake-word listener trigger
        if (privacyManager.isCapabilityEnabled("wake_word", false)) {
            liveSessionManager?.wakeWordDetector?.start { wakePhrase ->
                Log.d(tag, "Wake phrase '$wakePhrase' detected in background!")
                liveSessionManager?.startSession(serviceScope)
                updateNotification(ZoyaState.LISTENING)
            }
        }

        // Observe state to update ongoing notification
        liveSessionManager?.zoyaState?.onEach { state ->
            updateNotification(state)
        }?.launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val privacyManager = PrivacyManager(this)
        if (!privacyManager.isCapabilityEnabled("background_assistant", true)) {
            Log.d(tag, "Background assistant is disabled in settings - shutting down service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_START -> {
                requestAudioFocus()
                startInForeground()
                liveSessionManager?.startSession(serviceScope)
            }
            ACTION_STOP -> {
                abandonAudioFocus()
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

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(this)
                    .build()
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(this)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to abandon audio focus", e)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(tag, "Permanent audio focus loss")
                liveSessionManager?.onUserInterruption()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(tag, "Transient audio focus loss")
                liveSessionManager?.onUserInterruption()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(tag, "Ducking audio focus")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(tag, "Audio focus restored")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        try {
            unregisterReceiver(audioRoutingReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
        abandonAudioFocus()
        liveSessionManager?.release()
        serviceScope.cancel()
        super.onDestroy()
    }
}
