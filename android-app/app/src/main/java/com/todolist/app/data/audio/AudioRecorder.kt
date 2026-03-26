package com.todolist.app.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * 16 kHz, mono, PCM 16-bit — matches server / Whisper expectations.
 * Emits fixed-size chunks (~[chunkMs] ms) for streaming.
 */
class AudioRecorder(
    private val sampleRate: Int = 16_000,
    private val chunkMs: Int = 480,
) {
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null

    private val _chunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    val chunks: SharedFlow<ByteArray> = _chunks.asSharedFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val chunkBytes: Int
        get() = sampleRate * 2 * chunkMs / 1000

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        stop()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return
        val bufferSize = maxOf(minBuf, chunkBytes * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        audioRecord = recorder
        recorder.startRecording()
        recordJob = scope.launch(Dispatchers.IO) {
            val readBuf = ByteArray(chunkBytes)
            var acc = byteArrayOf()
            while (isActive) {
                val n = recorder.read(readBuf, 0, readBuf.size)
                if (n <= 0) continue
                updateLevel(readBuf, n)
                acc = acc + readBuf.copyOf(n)
                while (acc.size >= chunkBytes) {
                    val chunk = acc.copyOfRange(0, chunkBytes)
                    acc = acc.copyOfRange(chunkBytes, acc.size)
                    _chunks.emit(chunk)
                }
            }
        }
    }

    private fun updateLevel(pcm: ByteArray, length: Int) {
        if (length < 2) {
            _audioLevel.value = 0f
            return
        }
        var sum = 0.0
        var i = 0
        while (i + 1 < length) {
            val s = (pcm[i].toInt() and 0xFF or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
            val f = s / 32768.0
            sum += f * f
            i += 2
        }
        val samples = length / 2
        val rms = sqrt(sum / samples)
        val db = if (rms > 1e-10) 20 * kotlin.math.log10(rms) else -100.0
        val n = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
        _audioLevel.value = n
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
        audioRecord?.apply {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        audioRecord = null
        _audioLevel.value = 0f
    }
}
