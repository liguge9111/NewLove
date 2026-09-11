package com.tavern.app.feature.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.tavern.app.core.data.network.AsrProviderFactory
import com.tavern.app.core.data.repository.ProviderRepository
import com.tavern.app.core.data.repository.toModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 录音 + 网络语音识别服务
 *
 * MediaRecorder 录制 m4a，停止后走 OpenAI 兼容 ASR 接口转写；
 * 未配置 ASR 时回退到系统 SpeechRecognizer。
 */
@Singleton
class SpeechToTextService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerRepository: ProviderRepository,
    private val systemRecognizer: SpeechRecognizerService
) {

    private val recordDir: File by lazy {
        File(context.filesDir, "recordings").apply { mkdirs() }
    }

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** 是否配置了网络 ASR */
    suspend fun hasRemoteAsr(): Boolean =
        providerRepository.getDefaultAsrConfig() != null

    /**
     * 开始录音（重复调用无效）
     */
    fun startRecording(): Result<Unit> = runCatching {
        if (recorder != null) return@runCatching
        val file = File(recordDir, "asr_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        currentFile = file
        startedAt = System.currentTimeMillis()
    }

    /**
     * 停止录音并返回录音文件与时长
     */
    fun stopRecording(): Result<Pair<File, Float>> = runCatching {
        val mediaRecorder = recorder ?: throw IllegalStateException("未在录音")
        val file = currentFile ?: throw IllegalStateException("录音文件丢失")
        mediaRecorder.stop()
        mediaRecorder.release()
        recorder = null
        currentFile = null
        val duration = (System.currentTimeMillis() - startedAt) / 1000f
        file to duration
    }

    /**
     * 识别最近一次录音：网络 ASR 转写，返回 (文本, 音频路径, 时长秒)
     */
    suspend fun transcribeRecent(): Result<Triple<String, String, Float>> =
        withContext(Dispatchers.IO) {
            val (file, duration) = stopRecording().getOrThrow()
            val config = providerRepository.getDefaultAsrConfig()?.toModel()
                ?: return@withContext Result.failure(IllegalStateException("未配置语音识别服务"))
            AsrProviderFactory.getProvider(config)
                .transcribe(config, file.readBytes(), file.name)
                .map { Triple(it, file.absolutePath, duration) }
        }

    /** 直接走系统语音识别（无网络 ASR 时的回退） */
    suspend fun recognizeSystem(language: String = "zh-CN"): Result<String> =
        systemRecognizer.recognize(language)

    /** 放弃当前录音 */
    fun cancelRecording() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        currentFile?.delete()
        currentFile = null
    }
}
