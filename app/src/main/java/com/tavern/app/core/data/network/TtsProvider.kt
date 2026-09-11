package com.tavern.app.core.data.network

import com.tavern.app.core.model.VoiceConfig

/**
 * TTS（文本转语音）Provider 统一接口
 *
 * 各 TTS 服务（ElevenLabs / Azure / OpenAI TTS）实现此接口，
 * 返回合成后的音频数据（MP3/OGG 等格式的字节数组）。
 */
interface TtsProvider {

    /**
     * 将文本合成为语音
     *
     * @param config 语音配置
     * @param text 待合成的文本
     * @return 音频字节数组（失败返回 Result.failure）
     */
    suspend fun synthesize(config: VoiceConfig, text: String): Result<ByteArray>
}
