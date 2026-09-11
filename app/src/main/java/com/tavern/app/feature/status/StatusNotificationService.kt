package com.tavern.app.feature.status

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tavern.app.core.model.CharacterState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 状态栏通知服务
 *
 * 在通知栏常驻显示当前角色的状态（心情/体力/好感），
 * 让用户在聊天之外也能随时看到角色状态。
 */
@Singleton
class StatusNotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val CHANNEL_ID = "character_status"
        private const val NOTIFICATION_ID = 1001
    }

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "角色状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "常驻显示当前角色的心情/体力/好感状态"
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * 更新状态栏通知
     */
    fun updateStatus(characterName: String, state: CharacterState?) {
        if (state == null) {
            cancel()
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(characterName.ifBlank { "角色状态" })
            .setContentText(
                "心情 ${state.mood} · 体力 ${state.energy} · 好感 ${state.affection}"
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(buildStatusDetail(state))
            )
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * 取消状态栏通知
     */
    fun cancel() {
        runCatching {
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    private fun buildStatusDetail(state: CharacterState): String {
        val sb = StringBuilder()
        sb.append("心情：${state.mood}/100\n")
        sb.append("体力：${state.energy}/100\n")
        sb.append("好感：${state.affection}/100")
        state.customStates.forEach { (name, value) ->
            sb.append("\n$name：$value/100")
        }
        return sb.toString()
    }
}
