package com.tavern.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.app.core.data.repository.ProviderRepository
import com.tavern.app.core.data.repository.toModel
import com.tavern.app.core.model.ImageConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 生图配置 ViewModel
 */
@HiltViewModel
class ImageSettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository
) : ViewModel() {

    val configs: StateFlow<List<ImageConfig>> = providerRepository.getAllImageConfigsFlow()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(config: ImageConfig) {
        viewModelScope.launch {
            if (config.id > 0) {
                providerRepository.updateImageConfig(config)
            } else {
                // 第一个配置自动设为默认
                val isFirst = providerRepository.getAllImageConfigsFlow().first().isEmpty()
                providerRepository.addImageConfig(
                    config.copy(isDefault = config.isDefault || isFirst)
                )
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { providerRepository.deleteImageConfig(id) }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch { providerRepository.setDefaultImage(id) }
    }
}
