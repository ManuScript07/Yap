package com.example.yap.data.manager

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    var currentRecordPath: String? = null
        private set

    var isRecording: Boolean = false
        private set



    fun startRecording() {
        if (isRecording)
            return

        val file = File(context.cacheDir, "yap_record_${System.currentTimeMillis()}.m4a")
        currentRecordPath = file.absolutePath

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                (MediaRecorder())
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setAudioChannels(1)

                setOutputFile(currentRecordPath)
                prepare()
                start()
            }

            isRecording = true

        }catch (e: Exception) {
            Log.e("VoiceManager", "Не удалось начать запись: ${e.message}")
            isRecording = false
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка при стопе: ${e.message}")
        } finally {
            recorder = null
            isRecording = false
        }
    }

    fun cancelRecording() {

        stopRecording()

        currentRecordPath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
        currentRecordPath = null
    }

    fun playPausePlayback(
        onStateChanged: (Boolean) -> Unit, // Новый коллбэк для мгновенного обновления иконки
        onCompletion: () -> Unit
    ) {
        if (player?.isPlaying == true) {
            player?.pause()
            onStateChanged(false) // Уведомляем: теперь пауза
            return
        }

        if (player != null) {
            player?.start()
            onStateChanged(true) // Уведомляем: теперь играет
            return
        }

        val path = currentRecordPath ?: return
        val file = File(path)

        if (!file.exists() || file.length() < 100) {
            onCompletion()
            return
        }

        try {
            player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                    onCompletion()
                }
                start()
                onStateChanged(true) // Уведомляем: начали играть
            }
        } catch (e: Exception) {
            stopPlayback()
            onCompletion()
        }
    }

    fun stopPlayback() {
        try {
            if (player?.isPlaying == true) {
                player?.stop()
            }
        } catch (e: Exception) {
            // Игнорируем ошибки при остановке
        } finally {
            player?.release()
            player = null
        }
    }
    // В VoiceManager
    fun pausePlaybackOnly() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка паузы: ${e.message}")
        }
    }

    // Убедись, что этот метод у тебя точно есть:
    fun isActuallyPlaying(): Boolean = player?.isPlaying ?: false


    fun getCurrentPosition(): Int = player?.currentPosition ?: 0


}