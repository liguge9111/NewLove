package com.tavern.app.core.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tavern.app.core.data.network.ModelProviderFactory
import com.tavern.app.core.data.repository.CharacterRepository
import com.tavern.app.core.data.repository.ChatRepository
import com.tavern.app.core.data.repository.ProviderRepository
import com.tavern.app.core.data.repository.toModel
import com.tavern.app.core.model.MessageRole
import com.tavern.app.core.prompt.PromptMessage
import com.tavern.app.core.util.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 角色主动消息引擎
 *
 * 定时检查：近期活跃的角色按概率主动发来消息 + 通知提醒。
 */
@Singleton
class ProactiveEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettings,
    private val characterRepository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository
) {

    companion object {
        private const val TAG = "NewLove"
        private const val CHANNEL_ID = "proactive_messages"
    }

    /** 定时触发入口（由 Alarm 唤醒） */
    suspend fun checkAndMaybeSend() = withContext(Dispatchers.IO) {
        try {
            if (!appSettings.proactiveEnabled.value) return@withContext
            if (isQuietHours()) return@withContext

            val prob = appSettings.proactiveProb.value / 100.0
            if (Random.nextDouble() >= prob) return@withContext

            val cards = characterRepository.getAllCards()
            if (cards.isEmpty()) return@withContext

            val now = System.currentTimeMillis()
            val recentCards = cards.filter { card ->
                chatRepository.getSessionsForCard(card.id)
                    .any { now - it.lastActiveAt <= TimeUnit.DAYS.toMillis(7) }
            }
            if (recentCards.isEmpty()) return@withContext

            val card = recentCards.random()
            val session = chatRepository.getSessionsForCard(card.id).firstOrNull()
                ?: return@withContext

            val model = providerRepository.getDefaultModelConfig()?.toModel()
                ?: return@withContext

            // 轻量生成主动搭话
            val history = chatRepository.getLastMessages(session.id, 10)
            val memoryHint = "" // 主动消息不强制注入记忆，保持轻量
            val system = """
                你现在是「${card.name}」。请以角色身份，主动给玩家发一条简短的消息（1～3句，像手机上主动找对方聊天）。
                符合角色人设与当前剧情上下文，口语化。只输出消息内容本身，不要任何前缀、动作描写或解释。
            """.trimIndent()
            val prompt = buildList {
                add(PromptMessage(MessageRole.SYSTEM, system + memoryHint))
                history.forEach {
                    add(
                        PromptMessage(
                            role = if (it.role == "USER") MessageRole.USER else MessageRole.ASSISTANT,
                            content = it.content.take(400)
                        )
                    )
                }
                add(PromptMessage(MessageRole.SYSTEM, "请作为${card.name}，主动发一条消息。"))
            }

            val provider = ModelProviderFactory.getProvider(model)
            val reply = provider.chat(
                model.copy(maxTokens = 256, streamEnabled = false),
                prompt
            ).getOrNull()?.trim()

            if (reply.isNullOrBlank() || reply.length < 2) return@withContext

            chatRepository.addMessage(
                sessionId = session.id,
                role = MessageRole.ASSISTANT,
                content = reply,
                isProactive = true
            )
            notify(card.name)
            Log.i(TAG, "proactive message sent for ${card.name}")
        } catch (e: Exception) {
            Log.w(TAG, "proactive failed: ${e.message}")
        }
    }

    private fun isQuietHours(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 8
    }

    private fun notify(characterName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "角色消息",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        // 按需求：通知内容不暴露消息详情
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("收到一条通知")
            .setAutoCancel(true)
            .build()
        manager.notify(characterName.hashCode().coerceAtLeast(0), notification)
    }
}
