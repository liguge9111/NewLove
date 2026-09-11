package com.tavern.app.feature.voice

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.tavern.app.core.data.network.TtsProviderFactory
import com.tavern.app.core.data.repository.ProviderRepository
import com.tavern.app.core.data.repository.toModel
import com.tavern.app.core.model.VoiceConfig
import com.tavern.app.core.model.VoiceProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 语音服务
 *
 * 负责：文本合成语音（网络 TTS / 系统 TTS）、音频文件管理、播放。
 */
@Singleton
class VoiceService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerRepository: ProviderRepository
) {

    private val audioDir: File by lazy {
        File(context.filesDir, "voice").apply { mkdirs() }
    }

    private var mediaPlayer: MediaPlayer? = null

    /**
     * 将文本合成为语音并保存到本地文件（使用默认语音配置）
     *
     * @return 音频文件路径（失败返回 Result.failure）
     */
    suspend fun synthesizeToFile(text: String): Result<String> {
        val config = providerRepository.getDefaultVoiceConfig()?.toModel()
            ?: return Result.failure(IllegalStateException("未配置语音服务"))
        return synthesizeToFile(text, config)
    }

    /**
     * 将文本合成为语音并保存到本地文件（使用指定语音配置）
     *
     * @return 音频文件路径（失败返回 Result.failure）
     */
    suspend fun synthesizeToFile(text: String, config: VoiceConfig): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val audio: ByteArray = when (config.providerType) {
                    VoiceProviderType.SYSTEM_TTS -> {
                        // 系统 TTS 输出 WAV
                        synthesizeWithSystemTts(text, config.speed, config.pitch)
                    }
                    else -> {
                        TtsProviderFactory.getProvider(config)
                            .synthesize(config, text)
                            .getOrThrow()
                    }
                }

                if (audio.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("语音合成结果为空"))
                }

                val extension = if (config.providerType == VoiceProviderType.SYSTEM_TTS) "wav" else "mp3"
                val fileName = "tts_${System.currentTimeMillis()}.$extension"
                val file = File(audioDir, fileName)
                file.writeBytes(audio)
                Result.success(file.absolutePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 播放音频文件
     */
    fun play(audioPath: String) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            stop()
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    /**
     * 使用系统 TextToSpeech 合成（输出到临时 WAV 文件再读取）
     */
    private suspend fun synthesizeWithSystemTts(
        text: String,
        speed: Float,
        pitch: Float
    ): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val tempFile = File(audioDir, "sys_${System.currentTimeMillis()}.wav")

            lateinit var tts: TextToSpeech
            tts = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    if (continuation.isActive) {
                        continuation.resume(ByteArray(0))
                    }
                    return@TextToSpeech
                }

                tts.language = Locale.CHINA
                tts.setSpeechRate(speed)
                tts.setPitch(pitch)

                val utteranceId = "tts_${System.currentTimeMillis()}"
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        val result = if (tempFile.exists()) tempFile.readBytes() else ByteArray(0)
                        tts.shutdown()
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        tts.shutdown()
                        if (continuation.isActive) {
                            continuation.resume(ByteArray(0))
                        }
                    }
                })

                val resultCode = tts.synthesizeToFile(text, null, tempFile, utteranceId)
                if (resultCode == TextToSpeech.ERROR) {
                    tts.shutdown()
                    if (continuation.isActive) {
                        continuation.resume(ByteArray(0))
                    }
                }
            }
        }

    /**
     * 清理音频目录
     */
    fun clearCache() {
        audioDir.listFiles()?.forEach { it.delete() }
    }
}
