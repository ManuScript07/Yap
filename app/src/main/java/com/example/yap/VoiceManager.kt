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

    fun startRecording() {
        val file = File(context.cacheDir, "yap_record_${System.currentTimeMillis()}.m4a")
        currentRecordPath = file.absolutePath

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            // --- Настройки качества ---
            setAudioSamplingRate(44100) // Частота дискретизации (как на CD)
            setAudioEncodingBitRate(128000) // Битрейт 128 кбит/с (золотой стандарт для голоса)
            setAudioChannels(1) // Для голоса лучше моно, чтобы не было фазовых искажений
            // --------------------------

            setOutputFile(currentRecordPath)
            prepare()
            start()
        }
    }

    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                reset() // КРИТИЧЕСНО: Сбрасывает рекордер в состояние Idle, закрывая дескриптор файла
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка при стопе: ${e.message}")
        } finally {
            recorder = null
        }
    }

    fun cancelRecording() {
        try {
            stopRecording()
        } catch (e: Exception) {
            // Игнорируем ошибки остановки, нам главное удалить файл
        }

        currentRecordPath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
        currentRecordPath = null
    }

    fun playPausePlayback(onCompletion: () -> Unit) {
        if (player?.isPlaying == true) {
            player?.pause()
            return
        }

        // Если плеер в паузе — просто запускаем
        if (player != null) {
            player?.start()
            return
        }

        // Если плеера нет — создаем с нуля
        val path = currentRecordPath ?: return
        val file = File(path)

        if (!file.exists() || file.length() < 100) {
            Log.e("VoiceManager", "Файл не готов: ${file.length()} байт")
            onCompletion() // Возвращаем UI в стоп
            return
        }

        try {
            player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    stopPlayback() // Важно: зануляем плеер после конца
                    onCompletion()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка плеера: ${e.message}")
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

    fun deleteCurrentRecord() {
        try {
            currentRecordPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d("VoiceManager", "Файл удален: $path, успех: $deleted")
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка при удалении файла: ${e.message}")
        } finally {
            currentRecordPath = null
        }
    }
}