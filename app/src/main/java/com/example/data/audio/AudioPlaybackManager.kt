package com.example.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.sqrt

/**
 * High-performance streaming audio playback via AudioTrack.
 * Supports instantaneous interruption, buffer clearing, and audio-reactive amplitude emission.
 */
class AudioPlaybackManager(
    private val context: Context,
    private val sampleRate: Int = 24000
) {
    private val tag = "AudioPlaybackManager"

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val minBufferSize: Int by lazy {
        val calculated = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        maxOf(calculated, 4096)
    }

    @Synchronized
    private fun initAudioTrack() {
        if (audioTrack != null) return

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AudioTrack", e)
        }
    }

    fun startPlaybackLoop(scope: CoroutineScope) {
        if (playbackJob?.isActive == true) return

        initAudioTrack()

        playbackJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val chunk = audioQueue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (chunk != null && chunk.isNotEmpty()) {
                        _isPlaying.value = true
                        val amp = calculateRms(chunk)
                        _outputAmplitude.value = amp

                        val track = audioTrack
                        if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                        }
                    } else {
                        if (audioQueue.isEmpty()) {
                            _isPlaying.value = false
                            _outputAmplitude.value = 0f
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(tag, "Error during playback loop", e)
                }
            }
        }
    }

    /**
     * Enqueue a PCM16 audio chunk received from Gemini Live.
     */
    fun enqueueAudio(data: ByteArray) {
        if (data.isEmpty()) return
        initAudioTrack()
        audioQueue.offer(data)
    }

    /**
     * CRITICAL INTERRUPTION:
     * Immediately stops playback, flushes queued buffers, resets the track.
     */
    @Synchronized
    fun interruptAndStop() {
        audioQueue.clear()
        _isPlaying.value = false
        _outputAmplitude.value = 0f

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(tag, "AudioTrack flush on interrupt failed", e)
        }
    }

    fun release() {
        interruptAndStop()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }

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
        return (rms / 32767f).toFloat().coerceIn(0f, 1f)
    }
}
