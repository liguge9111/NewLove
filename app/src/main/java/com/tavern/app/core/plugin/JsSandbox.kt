package com.tavern.app.core.plugin

import android.util.Log
import com.quickjs.JSContext
import com.quickjs.QuickJS
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * QuickJS 受限沙箱（插件一期）
 *
 * - 每次执行独立 Runtime，用完即毁；无网络/文件/Android API
 * - 工作线程执行 + 2 秒超时熔断（超时后线程放弃，不影响主流程）
 *
 * 插件脚本约定（一期）：
 * - 宿主注入：CharacterName / UserInput / RecentMessages / OriginalText（被改写的回复原文）
 * - 脚本定义 function transformReply(text) 返回改写文本；或直接赋值全局 __out
 * - 返回空/null 表示不改写
 */
object JsSandbox {

    private const val TAG = "NewLove"
    private const val TIMEOUT_MS = 2_000L

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "quickjs-sandbox").apply { isDaemon = true }
    }

    data class SandboxContext(
        val characterName: String,
        val userInput: String,
        val recentMessages: List<String>,
        /** 正在被改写的回复原文 */
        val replyText: String
    )

    /**
     * 执行插件脚本改写回复；返回改写结果，null 表示不改写或执行失败
     */
    fun transformReply(script: String, ctx: SandboxContext): String? {
        val future = executor.submit<String?> {
            runScript(script, ctx)
        }
        return try {
            future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "js sandbox timeout/error: ${e.javaClass.simpleName}")
            null // 超时或线程异常：放弃改写
        }
    }

    private fun runScript(script: String, ctx: SandboxContext): String? {
        var quickJS: QuickJS? = null
        var context: JSContext? = null
        return try {
            quickJS = QuickJS.createRuntime()
            context = quickJS.createContext()
            val prelude = buildString {
                appendLine("var CharacterName = ${quote(ctx.characterName)};")
                appendLine("var UserInput = ${quote(ctx.userInput)};")
                appendLine(
                    "var RecentMessages = ${
                        ctx.recentMessages.take(10)
                            .joinToString(",", "[", "]") { quote(it.take(200)) }
                    };"
                )
                appendLine("var OriginalText = ${quote(ctx.replyText)};")
                appendLine("var __out = null;")
                appendLine("var log = function(msg) {};")
            }
            val epilogue = """
                try {
                    if (typeof transformReply === 'function') {
                        var __r = transformReply(OriginalText);
                        if (__r !== undefined && __r !== null) { __out = String(__r); }
                    }
                } catch (e) { __out = null; }
            """.trimIndent()
            context.executeStringScript(prelude + "\n" + script + "\n" + epilogue, "plugin.js")
            context.executeStringScript(
                "(typeof __out === 'string') ? __out : ''",
                "out.js"
            )?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Log.w(TAG, "js sandbox run error: ${t.message}")
            null
        } finally {
            runCatching { context?.close() }
            runCatching { quickJS?.close() }
        }
    }

    private fun quote(s: String): String =
        "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "") + "\""
}
