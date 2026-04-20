package com.example.yap.data.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class VoiceManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    var currentRecordPath: String? = null
        private set

    var isRecording: Boolean = false
        private set

    private var currentDataSource: String? = null

    private var progressJob: Job? = null


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
        stopProgressTracker()
        try {
            // Проверяем именно через isPlaying, чтобы не вызвать ошибку состояния MediaPlayer
            if (player?.isPlaying == true) {
                player?.stop()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка при остановке плеера: ${e.message}")
        } finally {
            player?.release()
            player = null
            currentDataSource = null // КРИТИЧНО: чтобы следующий togglePlayback считал новый запуск "чистым"
        }
    }

    fun pausePlaybackOnly() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка паузы: ${e.message}")
        }
    }


    fun isActuallyPlaying(): Boolean = player?.isPlaying ?: false

    fun getCurrentPosition(): Int = player?.currentPosition ?: 0


    fun seekTo(positionMs: Int) {
        try {
            player?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка перемотки: ${e.message}")
        }
    }

    fun togglePlayback(
        source: String,
        onStateChanged: (Boolean) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit, // Новый коллбэк
        onCompletion: () -> Unit
    ) {
        // 1. Если это тот же самый файл и он играет — ставим на паузу
        if (currentDataSource == source && player?.isPlaying == true) {
            player?.pause()
            stopProgressTracker()
            onStateChanged(false)
            return
        }

        // 2. Если это тот же файл и он на паузе — продолжаем
        if (currentDataSource == source && player != null) {
            player?.start()
            startProgressTracker(onProgress, onCompletion, onStateChanged)
            onStateChanged(true)
            return
        }

        // 3. Если файл новый или плеера нет — создаем с нуля
        stopPlayback()
        currentDataSource = source

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(source)

                val isNetwork = source.startsWith("http")

                if (isNetwork) {
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        startProgressTracker(onProgress, onCompletion, onStateChanged)
                        onStateChanged(true)
                    }
                } else {
                    prepare() // Локальный файл из кэша или записи грузится мгновенно
                    start()
                    startProgressTracker(onProgress, onCompletion, onStateChanged)
                    onStateChanged(true)
                }

                setOnCompletionListener {
                    Log.d("VoiceManager", "Playback COMPLETED")
                    handleManualCompletion(onCompletion, onStateChanged)
                }

                setOnErrorListener { _, _, _ ->
                    stopProgressTracker()
                    onCompletion()
                    onStateChanged(false)
                    resetState()
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка проигрывания: ${e.message}")
            handleManualCompletion(onCompletion, onStateChanged)
        }
    }

    private fun startProgressTracker(onProgress: (Int, Int) -> Unit, onCompletion: () -> Unit, onStateChanged: (Boolean) -> Unit) {
        progressJob?.cancel()
        progressJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val p = player
                if (p != null && p.isPlaying) {
                    val current = p.currentPosition
                    val total = p.duration

                    onProgress(current, total)

                    // ХАК: Если до конца осталось меньше 150мс — считаем, что всё.
                    // Некоторые файлы никогда не выбрасывают OnCompletionListener.
                    if (total > 0 && (total - current) < 150) {
                        Log.d("VoiceManager", "Конец близок (ручной детект)")
                        handleManualCompletion(onCompletion, onStateChanged)
                        break
                    }
                } else if (p != null && !p.isPlaying && currentDataSource != null) {
                    // Если плеер перестал играть сам по себе, но мы не ставили на паузу
                    // (Это происходит на некоторых прошивках вместо OnCompletion)
                    handleManualCompletion(onCompletion, onStateChanged)
                    break
                }
                delay(100)
            }
        }
    }

    private fun handleManualCompletion(onCompletion: () -> Unit, onStateChanged: (Boolean) -> Unit) {
        stopProgressTracker()
        onCompletion()
        onStateChanged(false)
        resetState()
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun resetState() {
        player?.release()
        player = null
        currentDataSource = null
    }

    fun prepareTrack(source: String, onPrepared: (Int) -> Unit) {
        // Если этот файл уже загружен, просто отдаем длительность
        if (currentDataSource == source && player != null) {
            onPrepared(player?.duration ?: 0)
            return
        }

        stopPlayback()
        currentDataSource = source
        try {
            player = MediaPlayer().apply {
                setDataSource(source)
                setOnPreparedListener {
                    onPrepared(it.duration)
                }
                // Готовим в фоне, чтобы UI не фризил
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error preparing: ${e.message}")
        }
    }

    fun isPlayingSource(source: String): Boolean =
        currentDataSource == source && player?.isPlaying == true



}