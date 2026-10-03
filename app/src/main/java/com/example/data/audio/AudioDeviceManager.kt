package com.example.data.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Monitors and manages audio input and output routing across phone speaker,
 * built-in microphone, wired headsets, and Bluetooth/BLE earbuds.
 * Uses modern Android 12+ (API 31+) CommunicationDevice APIs with safe fallbacks.
 */
class AudioDeviceManager(private val context: Context) {

    private val tag = "AudioDeviceManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private val _activeRouteName = MutableStateFlow("Phone Speaker / Mic")
    val activeRouteName: StateFlow<String> = _activeRouteName.asStateFlow()

    private val routeChangeListeners = CopyOnWriteArrayList<(AudioDeviceInfo?, AudioDeviceInfo?) -> Unit>()

    private var activeCommunicationDevice: AudioDeviceInfo? = null

    // API 23+ AudioDeviceCallback
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(tag, "Audio devices added (${addedDevices?.size ?: 0})")
            updateAudioRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(tag, "Audio devices removed (${removedDevices?.size ?: 0})")
            updateAudioRoute()
        }
    }

    // BroadcastReceiver for Bluetooth & Headset state events
    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(tag, "Received audio broadcast: $action")
            when (action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    Log.d(tag, "Bluetooth headset connection state: $state")
                    updateAudioRoute()
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    updateAudioRoute()
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.d(tag, "Audio becoming noisy - falling back to phone speaker")
                    updateAudioRoute()
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    updateAudioRoute()
                }
            }
        }
    }

    private var isRegistered = false

    fun startMonitoring() {
        if (isRegistered) return
        isRegistered = true

        try {
            audioManager?.registerAudioDeviceCallback(deviceCallback, null)
        } catch (e: Exception) {
            Log.w(tag, "Failed to register AudioDeviceCallback", e)
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        try {
            context.registerReceiver(connectionReceiver, filter)
        } catch (e: Exception) {
            Log.w(tag, "Failed to register connectionReceiver", e)
        }

        // On API 31+, register communication device listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager?.addOnCommunicationDeviceChangedListener(
                    context.mainExecutor,
                    AudioManager.OnCommunicationDeviceChangedListener { device ->
                        Log.d(tag, "Active communication device changed to: ${device?.productName}")
                        activeCommunicationDevice = device
                    }
                )
            } catch (e: Exception) {
                Log.w(tag, "Failed to add communication device changed listener", e)
            }
        }

        updateAudioRoute()
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        isRegistered = false

        try {
            audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        } catch (e: Exception) {
            Log.w(tag, "Failed to unregister AudioDeviceCallback", e)
        }

        try {
            context.unregisterReceiver(connectionReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }

        clearCommunicationDevice()
    }

    fun addRouteChangeListener(listener: (inputDevice: AudioDeviceInfo?, outputDevice: AudioDeviceInfo?) -> Unit) {
        routeChangeListeners.add(listener)
    }

    fun removeRouteChangeListener(listener: (inputDevice: AudioDeviceInfo?, outputDevice: AudioDeviceInfo?) -> Unit) {
        routeChangeListeners.remove(listener)
    }

    @Synchronized
    fun updateAudioRoute() {
        val am = audioManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableDevices = am.availableCommunicationDevices
                var btDevice: AudioDeviceInfo? = null

                // Search for Bluetooth SCO or BLE communication device
                for (device in availableDevices) {
                    when (device.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                            btDevice = device
                            break
                        }
                    }
                }

                if (btDevice != null) {
                    val success = am.setCommunicationDevice(btDevice)
                    if (success) {
                        activeCommunicationDevice = btDevice
                        _isBluetoothConnected.value = true
                        val name = btDevice.productName.toString().ifBlank { "Bluetooth Earbuds" }
                        _activeRouteName.value = "Bluetooth: $name"
                        Log.d(tag, "Successfully routed communication audio to Bluetooth device: $name")
                        notifyRouteChanged(btDevice, btDevice)
                        return
                    }
                }

                // If no Bluetooth device available or setting failed, clear communication device
                // and fallback to normal speaker/mic
                am.clearCommunicationDevice()
                activeCommunicationDevice = null
                _isBluetoothConnected.value = false
                _activeRouteName.value = "Phone Speaker / Mic"
                notifyRouteChanged(null, null)
            } else {
                // API < 31 Legacy fallback
                val devices = am.getDevices(AudioManager.GET_DEVICES_ALL)
                val hasBt = devices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
                _isBluetoothConnected.value = hasBt
                if (hasBt) {
                    try {
                        @Suppress("DEPRECATION")
                        am.startBluetoothSco()
                        @Suppress("DEPRECATION")
                        am.isBluetoothScoOn = true
                        _activeRouteName.value = "Bluetooth Headset"
                    } catch (e: Exception) {
                        _activeRouteName.value = "Phone Speaker / Mic"
                    }
                } else {
                    try {
                        @Suppress("DEPRECATION")
                        am.stopBluetoothSco()
                        @Suppress("DEPRECATION")
                        am.isBluetoothScoOn = false
                    } catch (e: Exception) {
                        // ignore
                    }
                    _activeRouteName.value = "Phone Speaker / Mic"
                }
                notifyRouteChanged(null, null)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error updating audio route", e)
            _activeRouteName.value = "Phone Speaker / Mic"
        }
    }

    private fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager?.clearCommunicationDevice()
            } catch (e: Exception) {
                Log.w(tag, "Failed to clear communication device", e)
            }
        }
    }

    private fun notifyRouteChanged(input: AudioDeviceInfo?, output: AudioDeviceInfo?) {
        for (listener in routeChangeListeners) {
            try {
                listener(input, output)
            } catch (e: Exception) {
                Log.e(tag, "Error executing route change listener", e)
            }
        }
    }

    fun getPreferredInputDevice(): AudioDeviceInfo? = activeCommunicationDevice
    fun getPreferredOutputDevice(): AudioDeviceInfo? = activeCommunicationDevice
}
