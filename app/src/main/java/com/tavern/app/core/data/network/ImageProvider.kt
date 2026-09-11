package com.tavern.app.core.data.network

import com.tavern.app.core.model.ImageConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 生图 Provider 统一接口
 */
interface ImageProvider {

    /**
     * 生成图片
     *
     * @param config 生图配置
     * @param prompt 正向提示词
     * @param negativePrompt 负面提示词（空则使用配置默认值）
     * @return 图片二进制数据（失败返回 Result.failure）
     */
    suspend fun generate(
        config: ImageConfig,
        prompt: String,
        negativePrompt: String = ""
    ): Result<ByteArray>
}

/**
 * 生图请求默认 OkHttpClient（生图耗时较长，放宽超时）
 */
fun defaultImageClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(300, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()
