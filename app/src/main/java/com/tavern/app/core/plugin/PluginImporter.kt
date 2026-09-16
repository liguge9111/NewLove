package com.tavern.app.core.plugin

import com.tavern.app.core.data.network.ModelProviderFactory
import com.tavern.app.core.model.MessageRole
import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.model.PluginAction
import com.tavern.app.core.model.PluginRule
import com.tavern.app.core.model.PluginTrigger
import com.tavern.app.core.prompt.PromptMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 插件导入转换器
 *
 * 下载/接收插件源码 → 类型识别 → LLM 转换为本地规则草稿。
 * 仅支持正则替换/注入类转换；JS 扩展类明确拒绝。
 */
class PluginImporter {

    /** 源码类型 */
    enum class SourceKind { REGEX_LIKE, JS_EXTENSION, UNKNOWN }

    /** 转换草稿 */
    @Serializable
    data class Draft(
        val name: String,
        val description: String,
        val rules: List<PluginRule>,
        /** 警告信息（如部分规则被跳过） */
        val warnings: List<String> = emptyList()
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 下载插件源码（限制 2MB） */
    suspend fun download(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url.trim()).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("下载失败（HTTP ${response.code}）")
                }
                val body = response.body?.string() ?: ""
                if (body.isBlank()) throw IllegalStateException("内容为空")
                if (body.length > 2_000_000) throw IllegalStateException("文件过大（>2MB）")
                body
            }
        }
    }

    /** 粗分类：JS 扩展特征明显则标记 */
    fun classify(source: String): SourceKind {
        val markers = listOf(
            "SillyTavern.getContext",
            "eventSource.on",
            "document.querySelector",
            "window.jQuery",
            "$('#'",
            "$(\""
        )
        val hits = markers.count { source.contains(it) }
        return when {
            hits >= 2 -> SourceKind.JS_EXTENSION
            source.contains("findRegex") || source.contains("replaceString") ||
                source.contains("regex_scripts") -> SourceKind.REGEX_LIKE
            else -> SourceKind.UNKNOWN
        }
    }

    /**
     * 调用大模型把源码转换为规则草稿
     */
    suspend fun convertViaLlm(
        modelConfig: ModelConfig,
        source: String,
        fallbackName: String = ""
    ): Result<Draft> = withContext(Dispatchers.IO) {
        runCatching {
            val truncated = source.take(60_000)
            val systemPrompt = """
                你是「酒馆插件→规则」转换器。把用户给出的插件源码转换为本地规则引擎可用的 JSON。
                只输出一个 JSON 对象，不要输出任何解释、markdown 代码块或多余文字。

                JSON Schema：
                {
                  "name": "插件名（简短中文）",
                  "description": "一句话说明用途",
                  "rules": [
                    {
                      "trigger": "ON_REPLY" 或 "ON_SEND",
                      "useRegex": true,
                      "pattern": "Kotlin/Java 兼容的正则，不要 /delimiter/ 包裹；常驻规则可留空字符串",
                      "action": "REPLACE" 或 "PREFIX" 或 "SUFFIX" 或 "INJECT_PROMPT",
                      "config": {
                        "find": "REPLACE 用，同 pattern",
                        "replace": "REPLACE 用，替换文本，可用 $1",
                        "text": "PREFIX/SUFFIX/INJECT_PROMPT 用的文本"
                      }
                    }
                  ]
                }

                要求：
                1. 最多 20 条规则
                2. 用户输入处理用 ON_SEND，AI 回复处理用 ON_REPLY
                3. 「提示词工具/宏包/状态栏模板」类内容 → INJECT_PROMPT（pattern 留空=常驻注入，config.text 为注入片段）
                4. 若源码是依赖浏览器 DOM/事件/酒馆 API 的 JS 扩展、无法转为纯文本规则，则返回 {"name":"...","description":"不支持","rules":[]}
                5. pattern 必须是能直接编译的 Java 正则
            """.trimIndent()

            val userPrompt = buildString {
                if (fallbackName.isNotBlank()) {
                    appendLine("参考名称：$fallbackName")
                }
                appendLine("——以下为插件源码——")
                append(truncated)
            }

            val provider = ModelProviderFactory.getProvider(modelConfig)
            val reply = provider.chat(
                modelConfig.copy(maxTokens = 4096, streamEnabled = false),
                listOf(
                    PromptMessage(MessageRole.SYSTEM, systemPrompt),
                    PromptMessage(MessageRole.USER, userPrompt)
                )
            ).getOrThrow()

            parseDraft(reply, fallbackName)
        }
    }

    /** 解析 LLM 输出（容忍代码块包裹） */
    fun parseDraft(reply: String, fallbackName: String): Draft {
        var text = reply.trim()
        // 去除 ```json ... ``` 包裹
        val fence = Regex("(?s)```(?:json)?\\s*(.*?)\\s*```")
        fence.find(text)?.let { text = it.groupValues[1].trim() }
        // 截取首个 { 到最后一个 }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1)
        }

        val dto = json.decodeFromString<DraftDto>(text)
        val rules = dto.rules.mapNotNull { r ->
            val trigger = when (r.trigger) {
                "ON_SEND" -> PluginTrigger.ON_SEND
                else -> PluginTrigger.ON_REPLY
            }
            val action = when (r.action) {
                "PREFIX" -> PluginAction.PREFIX
                "SUFFIX" -> PluginAction.SUFFIX
                "INJECT_PROMPT" -> PluginAction.INJECT_PROMPT
                else -> PluginAction.REPLACE
            }
            val pattern = r.pattern
            if (pattern.isBlank()) return@mapNotNull null
            PluginRule(
                trigger = trigger,
                useRegex = r.useRegex,
                pattern = pattern,
                action = action,
                config = r.config
            )
        }
        val warnings = buildList {
            if (rules.isEmpty()) add("未能提取到可用规则（可能是 JS 扩展类插件）")
            if (dto.rules.size > rules.size) add("有 ${dto.rules.size - rules.size} 条规则无效被丢弃")
        }
        return Draft(
            name = dto.name.ifBlank { fallbackName.ifBlank { "导入的插件" } },
            description = dto.description,
            rules = rules,
            warnings = warnings
        )
    }

    @Serializable
    private data class DraftDto(
        val name: String = "",
        val description: String = "",
        val rules: List<RuleDto> = emptyList()
    )

    @Serializable
    private data class RuleDto(
        val trigger: String = "ON_REPLY",
        val useRegex: Boolean = true,
        val pattern: String = "",
        val action: String = "REPLACE",
        val config: Map<String, String> = emptyMap()
    )
}
