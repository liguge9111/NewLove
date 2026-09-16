package com.tavern.app.feature.image

import android.content.Context
import com.tavern.app.core.data.network.ImageProviderFactory
import com.tavern.app.core.data.repository.ProviderRepository
import com.tavern.app.core.data.repository.toModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生图服务
 *
 * 调用生图 Provider 生成图片并保存到本地文件。
 */
@Singleton
class ImageGenerationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerRepository: ProviderRepository
) {

    private val imageDir: File by lazy {
        File(context.filesDir, "images").apply { mkdirs() }
    }

    /**
     * 生成图片并保存到本地文件
     *
     * @param prompt 正向提示词
     * @param negativePrompt 负面提示词（空则使用配置默认值）
     * @return 图片文件路径（失败返回 Result.failure）
     */
    suspend fun generate(
        prompt: String,
        negativePrompt: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val config = providerRepository.getDefaultImageConfig()?.toModel()
                ?: return@withContext Result.failure(IllegalStateException("未配置生图服务"))

            val bytes = ImageProviderFactory.getProvider(config)
                .generate(config, prompt, negativePrompt)
                .getOrThrow()

            val fileName = "img_${System.currentTimeMillis()}.png"
            val file = File(imageDir, fileName)
            file.writeBytes(bytes)

            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
