package com.example.yap

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log

import java.io.File

import android.annotation.SuppressLint

import android.media.AudioFormat
import android.media.AudioRecord

import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceManager(private val context: Context) {

    // Настройки для Vosk
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var player: MediaPlayer? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    var currentRecordPath: String? = null
        private set

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val file = File(context.cacheDir, "yap_record_${System.currentTimeMillis()}.wav")
        currentRecordPath = file.absolutePath

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            writeAudioDataToFile(file, bufferSize)
        }.apply { start() }
    }

    private fun writeAudioDataToFile(file: File, bufferSize: Int) {
        val data = ByteArray(bufferSize)
        FileOutputStream(file).use { out ->
            // 1. Резервируем место под заголовок (44 байта)
            out.write(ByteArray(44))

            while (isRecording) {
                val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                if (read > 0) {
                    out.write(data, 0, read)
                }
            }
        }
        // 2. Когда запись окончена, записываем правильные размеры в заголовок
        updateWavHeader(file)
    }

    fun stopRecording() {
        isRecording = false
        try {
            recordingThread?.join()
            audioRecord?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка при остановке записи: ${e.message}")
        } finally {
            audioRecord = null
            recordingThread = null
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

    // Метод проигрывания остается почти таким же, MediaPlayer отлично играет WAV
    fun playPausePlayback(onStateChanged: (Boolean) -> Unit, onCompletion: () -> Unit) {
        if (player?.isPlaying == true) {
            player?.pause()
            onStateChanged(false)
            return
        }
        if (player != null) {
            player?.start()
            onStateChanged(true)
            return
        }

        val path = currentRecordPath ?: return
        try {
            player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                    onCompletion()
                }
                start()
                onStateChanged(true)
            }
        } catch (e: Exception) {
            stopPlayback()
            onCompletion()
        }
    }

    fun stopPlayback() {
        player?.apply {
            if (isPlaying) stop()
            release()
        }
        player = null
    }

    fun getCurrentPosition(): Int = player?.currentPosition ?: 0

    // Вспомогательный метод для создания заголовка WAV
    private fun updateWavHeader(file: File) {
        val fileSize = file.length()
        val dataSize = fileSize - 44
        val header = createWavHeader(dataSize)

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }

    private fun createWavHeader(dataSize: Long): ByteArray {
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = 16000L
        val byteRate = sampleRate * 2 // 16 bit = 2 bytes, 1 channel

        buffer.put("RIFF".toByteArray()) // ChunkID
        buffer.putInt((dataSize + 36).toInt()) // ChunkSize
        buffer.put("WAVE".toByteArray()) // Format
        buffer.put("fmt ".toByteArray()) // Subchunk1ID
        buffer.putInt(16) // Subchunk1Size (16 для PCM)
        buffer.putShort(1.toShort()) // AudioFormat (1 для PCM)
        buffer.putShort(1.toShort()) // NumChannels
        buffer.putInt(sampleRate.toInt()) // SampleRate
        buffer.putInt(byteRate.toInt()) // ByteRate
        buffer.putShort(2.toShort()) // BlockAlign
        buffer.putShort(16.toShort()) // BitsPerSample
        buffer.put("data".toByteArray()) // Subchunk2ID
        buffer.putInt(dataSize.toInt()) // Subchunk2Size

        return buffer.array()
    }
}