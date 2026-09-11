package com.tavern.app.core.prompt

/**
 * 联动旁白协议生成器
 *
 * 聊天模式下注入：要求模型在正常回复后附带 [NARRATION] 块，
 * 以场景叙事视角详细描述正在发生的事，供互动模式实时展示。
 */
object NarrationProtocol {

    val INSTRUCTION = """
        【联动叙事】
        在每次回复的最后，额外输出一段"场景旁白"：以第三人称、细节丰富的方式描述当前场景中角色的神态、动作与环境氛围（就像互动游戏的实时画面描述），与你刚才的回复内容保持一致。
        格式（放在回复最末尾）：
        [NARRATION]
        场景旁白内容……
        [/NARRATION]
        旁白与正文不要重复相同句子，旁白侧重"画面感"。
    """.trimIndent()
}
