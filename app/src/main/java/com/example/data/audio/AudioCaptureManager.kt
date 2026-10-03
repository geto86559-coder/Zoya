package com.example.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.domain.models.AudioChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Handles real-time PCM16 audio recording at 16kHz mono.
 * Thread-safe and non-blocking.
 */
class AudioCaptureManager(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private val tag = "AudioCaptureManager"

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var preferredDevice: android.media.AudioDeviceInfo? = null

    fun updatePreferredDevice(device: android.media.AudioDeviceInfo?) {
        preferredDevice = device
        try {
            audioRecord?.setPreferredDevice(device)
            Log.d(tag, "Updated AudioRecord preferred input device to: ${device?.productName}")
        } catch (e: Exception) {
            Log.w(tag, "Failed to set preferred device on AudioRecord", e)
        }
    }

    private val _audioChunks = MutableSharedFlow<AudioChunk>(extraBufferCapacity = 64)
    val audioChunks: SharedFlow<AudioChunk> = _audioChunks.asSharedFlow()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val bufferSize: Int by lazy {
        val minSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        maxOf(minSize, 2048)
    }

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope, onAudioChunk: (AudioChunk) -> Unit) {
        if (_isRecording.value) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to default MIC
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord failed to initialize")
                return
            }

            preferredDevice?.let {
                try {
                    audioRecord?.setPreferredDevice(it)
                } catch (e: Exception) {
                    Log.w(tag, "Could not set preferred device on initial start", e)
                }
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(1024) // 512 samples of PCM16 = 32ms at 16kHz
                while (isActive && _isRecording.value) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val chunkBytes = buffer.copyOf(readBytes)
                        val rms = calculateRms(chunkBytes)
                        _inputAmplitude.value = rms

                        val chunk = AudioChunk(
                            data = chunkBytes,
                            sampleRate = sampleRate,
                            channels = 1,
                            rmsLevel = rms
                        )
                        _audioChunks.tryEmit(chunk)
                        onAudioChunk(chunk)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting audio capture", e)
            stopCapture()
        }
    }

    fun stopCapture() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
            _inputAmplitude.value = 0f
        }
    }

    /**
     * Calculates RMS (Root Mean Square) energy of PCM16 byte array normalized to 0.0f - 1.0f.
     */
    private fun calculateRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumSquares = 0.0
        val sampleCount = shortBuffer.remaining()
        if (sampleCount == 0) return 0f

        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get()
            sumSquares += sample * sample
        }
        val mean = sumSquares / sampleCount
        val rms = sqrt(mean)
        // Normalize against max short value (32767)
        return (rms / 32767f).toFloat().coerceIn(0f, 1f)
    }
}
