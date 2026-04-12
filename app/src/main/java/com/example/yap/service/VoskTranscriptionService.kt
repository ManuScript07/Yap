package com.example.yap.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume


@Suppress("MISSING_DEPENDENCY_SUPERCLASS_WARNING")
class VoskTranscriptionService(private val context: Context) {

    private var model: Model? = null
    private var isModelInitialized = false

    // 1. Метод для инициализации модели (лучше вызывать при старте приложения или экрана)
    suspend fun initModel(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        if (isModelInitialized && model != null) {
            continuation.resume(Result.success(Unit))
            return@suspendCancellableCoroutine
        }

        // StorageService распаковывает папку из assets во внутреннюю память
        StorageService.unpack(context, "model-ru", "model",
            { downloadedModel ->
                this.model = downloadedModel
                this.isModelInitialized = true
                Log.d("VoskSTT", "Модель успешно загружена")
                continuation.resume(Result.success(Unit))
            },
            { exception ->
                Log.e("VoskSTT", "Ошибка загрузки модели: ${exception.message}")
                continuation.resume(Result.failure(exception))
            }
        )
    }

    // 2. Метод расшифровки (уже с файлом, а не с Uri)
    suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        if (!isModelInitialized || model == null) {
            // Если пытаемся расшифровать, а модель еще не готова — пробуем инициализировать
            val initResult = initModel()
            if (initResult.isFailure) {
                return@withContext Result.failure(Exception("Модель Vosk не инициализирована"))
            }
        }

        try {
            // Создаем распознаватель. 16000.0f — частота дискретизации, должна совпадать с записью!
            val recognizer = Recognizer(model, 16000.0f)

            // Читаем файл кусками (буферами)
            audioFile.inputStream().use { inputStream ->
                if (audioFile.extension.lowercase() == "wav") {
                    inputStream.skip(44)
                }
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                    // Кормим аудио-данные распознавателю
                    recognizer.acceptWaveForm(buffer, bytesRead)
                }
            }

            // Получаем финальный результат в формате JSON: { "text": "какой то текст" }
            val finalResultJson = recognizer.finalResult
            Log.d("VoskSTT", "Сырой ответ: $finalResultJson")

            // Парсим JSON, чтобы достать только текст
            val jsonObject = JSONObject(finalResultJson)
            val text = jsonObject.optString("text", "")

            recognizer.close() // Освобождаем память
            Result.success(text)

        } catch (e: Exception) {
            Log.e("VoskSTT", "Ошибка при обработке файла: ${e.message}")
            Result.failure(e)
        }
    }
}