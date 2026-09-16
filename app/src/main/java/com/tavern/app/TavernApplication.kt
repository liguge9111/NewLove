package com.tavern.app

import android.app.Application
import com.tavern.app.core.proactive.ProactiveReceiver
import com.tavern.app.core.util.AppSettings
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 酒馆APP 应用入口
 */
@HiltAndroidApp
class TavernApplication : Application() {

    @Inject
    lateinit var appSettings: AppSettings

    override fun onCreate() {
        super.onCreate()
        // 安排角色主动消息定时检查（开关关闭时引擎内部会跳过）
        ProactiveReceiver.schedule(this, appSettings)
    }
}
