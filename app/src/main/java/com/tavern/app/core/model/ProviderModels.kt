package com.tavern.app.core.model

/**
 * 模型服务类型
 */
enum class ModelProviderType {
    /** OpenAI 兼容格式（含各类第三方服务） */
    OPENAI_COMPATIBLE,

    /** Anthropic Claude */
    CLAUDE,

    /** 本地模型（Ollama） */
    OLLAMA
}

/**
 * 大模型配置
 */
data class ModelConfig(
    /** 配置 ID */
    val id: Long = 0,

    /** 配置名称（用户自定义） */
    val name: String = "",

    /** 服务类型 */
    val providerType: ModelProviderType = ModelProviderType.OPENAI_COMPATIBLE,

    /** API 端点地址 */
    val baseUrl: String = "",

    /** API Key */
    val apiKey: String = "",

    /** 模型名称 */
    val modelName: String = "",

    /** 最大上下文令牌数 */
    val maxContextTokens: Int = 4096,

    /** 温度参数（0-2） */
    val temperature: Float = 0.7f,

    /** top_p 采样参数 */
    val topP: Float = 1.0f,

    /** 最大生成长度（推理模型需足够大，思考过程也占额度） */
    val maxTokens: Int = 4096,

    /** 频率惩罚 */
    val frequencyPenalty: Float = 0f,

    /** 存在惩罚 */
    val presencePenalty: Float = 0f,

    /** 是否启用流式输出 */
    val streamEnabled: Boolean = true,

    /** 是否默认模型 */
    val isDefault: Boolean = false
)

/**
 * 生图服务类型
 */
enum class ImageProviderType {
    /** Stable Diffusion WebUI */
    SD_WEBUI,

    /** NovelAI */
    NOVELAI,

    /** OpenAI DALL-E */
    DALL_E,

    /** 本地 ComfyUI */
    COMFY_UI
}

/**
 * 生图配置
 */
data class ImageConfig(
    /** 配置 ID */
    val id: Long = 0,

    /** 配置名称 */
    val name: String = "",

    /** 生图服务类型 */
    val providerType: ImageProviderType = ImageProviderType.SD_WEBUI,

    /** API 端点 */
    val baseUrl: String = "",

    /** API Key */
    val apiKey: String = "",

    /** 默认提示词 */
    val defaultPrompt: String = "",

    /** 默认负面提示词 */
    val defaultNegativePrompt: String = "",

    /** 采样器 */
    val sampler: String = "Euler a",

    /** 步数 */
    val steps: Int = 20,

    /** CFG Scale */
    val cfgScale: Float = 7.0f,

    /** 图片宽度 */
    val width: Int = 512,

    /** 图片高度 */
    val height: Int = 512,

    /** 是否默认 */
    val isDefault: Boolean = false
)

/**
 * 语音服务类型
 */
enum class VoiceProviderType {
    /** ElevenLabs */
    ELEVENLABS,

    /** Azure TTS */
    AZURE,

    /** 自定义 OpenAI 兼容 TTS */
    OPENAI_TTS,

    /** 系统本地 TTS */
    SYSTEM_TTS
}

/**
 * 语音配置
 */
data class VoiceConfig(
    /** 配置 ID */
    val id: Long = 0,

    /** 配置名称 */
    val name: String = "",

    /** 语音服务类型 */
    val providerType: VoiceProviderType = VoiceProviderType.SYSTEM_TTS,

    /** API 端点 */
    val baseUrl: String = "",

    /** API Key */
    val apiKey: String = "",

    /** 语音 ID */
    val voiceId: String = "",

    /** 语速（0.5-2.0） */
    val speed: Float = 1.0f,

    /** 音调 */
    val pitch: Float = 1.0f,

    /** 是否默认 */
    val isDefault: Boolean = false
)

/**
 * 语音识别（ASR）配置
 *
 * 走 OpenAI 兼容 /audio/transcriptions 接口（Whisper 格式），
 * baseUrl 指向服务地址（如 MiMo、OpenAI 或各类中转网关）。
 */
data class AsrConfig(
    /** 配置 ID */
    val id: Long = 0,

    /** 配置名称 */
    val name: String = "",

    /** API 端点（不含 /audio/transcriptions 后缀） */
    val baseUrl: String = "",

    /** API Key */
    val apiKey: String = "",

    /** 模型名（如 whisper-1） */
    val modelName: String = "whisper-1",

    /** 识别语言（如 zh、en，空=自动） */
    val language: String = "zh",

    /** 是否默认 */
    val isDefault: Boolean = false
)
