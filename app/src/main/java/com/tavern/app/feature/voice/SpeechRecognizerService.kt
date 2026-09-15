package com.tavern.app.feature.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 语音识别服务（STT）
 *
 * 使用系统 SpeechRecognizer，识别语音输入为文本。
 */
@Singleton
class SpeechRecognizerService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var recognizer: SpeechRecognizer? = null

    /**
     * 开始语音识别
     *
     * @return 识别出的文本（失败或取消返回 Result.failure）
     */
    suspend fun recognize(language: String = "zh-CN"): Result<String> =
        suspendCancellableCoroutine { continuation ->
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speechRecognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(IllegalStateException("语音识别错误：$error"))
                        )
                    }
                    speechRecognizer.destroy()
                    recognizer = null
                }

                override fun onResults(results: Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (continuation.isActive) {
                        continuation.resume(Result.success(text))
                    }
                    speechRecognizer.destroy()
                    recognizer = null
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer.startListening(intent)

            continuation.invokeOnCancellation {
                speechRecognizer.destroy()
                recognizer = null
            }
        }

    /**
     * 停止识别
     */
    fun stop() {
        recognizer?.let {
            it.stopListening()
            it.destroy()
        }
        recognizer = null
    }
}
