package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class VoiceRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L
    private var timerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    fun startRecording(scope: CoroutineScope): Result<Unit> {
        return try {
            stopAndCleanupOnly()
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(32_000)
            mr.setAudioSamplingRate(16_000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            startTimeMs = System.currentTimeMillis()
            _isRecording.value = true
            _elapsedMs.value = 0L

            timerJob = scope.launch {
                while (_isRecording.value) {
                    _elapsedMs.value = System.currentTimeMillis() - startTimeMs
                    delay(100L)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            stopAndCleanupOnly()
            Result.failure(e)
        }
    }

    fun stopAndEncode(): Result<Pair<String, Long>> {
        return try {
            val duration = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(500L)
            timerJob?.cancel()
            timerJob = null
            _isRecording.value = false
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val file = outputFile ?: return Result.failure(IllegalStateException("No audio recorded"))
            val bytes = file.readBytes()
            file.delete()
            outputFile = null
            if (bytes.size > 520_000) {
                Result.failure(IllegalArgumentException("Voice message too long (max ~2 minutes)."))
            } else {
                Result.success(Base64.encodeToString(bytes, Base64.NO_WRAP) to duration)
            }
        } catch (e: Exception) {
            stopAndCleanupOnly()
            Result.failure(e)
        }
    }

    fun cancelRecording() {
        stopAndCleanupOnly()
    }

    private fun stopAndCleanupOnly() {
        timerJob?.cancel()
        timerJob = null
        _isRecording.value = false
        _elapsedMs.value = 0L
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class VoicePlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun togglePlay(messageId: String, base64Audio: String, scope: CoroutineScope) {
        if (_playingMessageId.value == messageId) {
            stop()
            return
        }
        stop()
        try {
            val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val tempFile = File(dir, "play_${messageId.hashCode()}.m4a")
            FileOutputStream(tempFile).use { it.write(bytes) }

            val mp = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                }
                start()
            }
            mediaPlayer = mp
            _playingMessageId.value = messageId
            _progress.value = 0f

            progressJob = scope.launch {
                while (_playingMessageId.value == messageId && mp.isPlaying) {
                    val dur = mp.duration.coerceAtLeast(1)
                    _progress.value = (mp.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
                    delay(120L)
                }
            }
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        _playingMessageId.value = null
        _progress.value = 0f
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }
}
