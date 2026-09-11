package com.tavern.app.core.util

/**
 * 平台风控拦截识别
 *
 * 部分模型服务（如 MiMo 官方 API）在内容审核不通过时，
 * 不返回 HTTP 错误码，而是把拦截提示当作正常 completion 内容返回。
 * 这里识别常见拦截文案，供上层转成错误提示、避免存入聊天记录。
 */
object SafetyFilter {

    private val PATTERNS = listOf(
        // 英文常见拦截文案
        "high risk",
        "rejected because",
        "content policy",
        "content filter",
        "safety system",
        "usage policy",
        "violates our",
        "sensitive content",
        "cannot fulfill",
        "can't assist",
        "unable to assist",
        // 中文常见拦截文案
        "内容违规",
        "高风险",
        "安全策略",
        "内容安全",
        "生成失败，请重试",
        "违反.*规范",
        "无法生成"
    )

    /**
     * 判断回复是否为平台风控拦截提示（而非真正的角色回复）
     */
    fun isRejection(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = text.lowercase()
        return PATTERNS.any { pattern ->
            if (pattern.contains(".*")) {
                Regex(pattern).containsMatchIn(text)
            } else {
                normalized.contains(pattern.lowercase())
            }
        }
    }

    /** 面向用户的拦截说明 */
    fun rejectionMessage(): String =
        "请求被模型服务的安全审核拦截，未生成回复。" +
            "通常是角色卡设定触发了平台内容策略，可尝试：简化角色描述、" +
            "或在模型设置中更换其他模型服务。"
}
