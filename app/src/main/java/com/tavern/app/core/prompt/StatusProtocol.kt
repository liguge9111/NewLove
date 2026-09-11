package com.tavern.app.core.prompt

import com.tavern.app.core.model.CharacterState
import com.tavern.app.core.model.StatusItemDef

/**
 * 状态栏协议说明生成器
 *
 * 生成一段注入到系统提示词的指令，引导大模型在角色状态
 * 发生变化时输出 [STATUS] 块，供前端解析并更新状态栏。
 * 状态项由角色卡的状态模板决定（按卡不同）。
 */
object StatusProtocol {

    /**
     * 构建状态协议说明
     *
     * @param state 当前角色状态（用于告知模型当前数值）
     * @param schema 该卡的状态栏模板
     */
    fun build(state: CharacterState?, schema: List<StatusItemDef>): String {
        val items = schema.ifEmpty {
            listOf(
                StatusItemDef("mood", "心情", 50),
                StatusItemDef("energy", "体力", 100),
                StatusItemDef("affection", "好感", 0)
            )
        }

        val currentValues = items.joinToString("，") { item ->
            val value = currentValue(item.key, state) ?: item.defaultValue
            "${item.label}($value/100)"
        }

        val jsonKeys = items.joinToString(", ") { "\"${it.key}\": 变化量" }

        return """
            【角色状态系统】
            当前角色状态：$currentValues。
            当对话中角色的状态发生明显变化时，请在回复末尾输出状态变化量（正数增加、负数减少，无需变化时不要输出）：
            [STATUS]
            {$jsonKeys}
            [/STATUS]
            注意：只输出有变化的状态项，键名必须与上述一致。
        """.trimIndent()
    }

    private fun currentValue(key: String, state: CharacterState?): Int? = when (key) {
        "mood" -> state?.mood
        "energy" -> state?.energy
        "affection" -> state?.affection
        else -> state?.customStates?.get(key)
    }
}
