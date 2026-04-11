import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    var currentRecordPath: String? = null
        private set

    fun startRecording() {
        // Создаем временный файл в кеше приложения
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
            setOutputFile(currentRecordPath)
            prepare()
            start()
        }
    }

    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace() // Бывает, если попытаться остановить слишком быстро
        } finally {
            recorder = null
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

    fun playPausePlayback(onCompletion: () -> Unit) {
        if (player?.isPlaying == true) {
            player?.pause()
        } else {
            if (player == null && currentRecordPath != null) {
                player = MediaPlayer().apply {
                    setDataSource(currentRecordPath)
                    prepare()
                    setOnCompletionListener {
                        onCompletion() // Вызываем коллбэк, когда аудио доиграло до конца
                    }
                }
            }
            player?.start()
        }
    }

    fun stopPlayback() {
        player?.apply {
            stop()
            release()
        }
        player = null
    }
}