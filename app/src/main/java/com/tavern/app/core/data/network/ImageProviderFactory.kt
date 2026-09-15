package com.tavern.app.core.data.network

import com.tavern.app.core.model.ImageConfig
import com.tavern.app.core.model.ImageProviderType

/**
 * 生图 Provider 工厂
 */
object ImageProviderFactory {

    fun getProvider(config: ImageConfig): ImageProvider = when (config.providerType) {
        ImageProviderType.SD_WEBUI -> SdWebuiImageProvider()
        ImageProviderType.NOVELAI -> NovelAiImageProvider()
        ImageProviderType.DALL_E -> DallEImageProvider()
        ImageProviderType.COMFY_UI -> ComfyUiImageProvider()
    }
}
