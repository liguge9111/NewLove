package com.tavern.app.core.prompt

import com.tavern.app.core.model.InteractiveMode

/**
 * 互动模式提示词模板
 *
 * 每种互动模式注入一段系统指令，改变大模型的行为方式。
 * CHAT（聊天）模式不注入额外指令，保持传统对话行为。
 */
object InteractiveModePrompts {

    /**
     * 获取指定互动模式的系统指令（CHAT 返回空字符串）
     */
    fun instructionFor(mode: InteractiveMode): String = when (mode) {
        InteractiveMode.CHAT -> ""

        InteractiveMode.TEXT_ADVENTURE -> """
            【互动模式：文字冒险】
            你现在是一个交互式文字冒险游戏的故事引擎。请以第二人称（"你"）进行叙述，营造沉浸式的画面感与氛围。根据玩家输入的动作或选择推进剧情，不要替玩家做决定。当剧情出现需要玩家决策的关键节点时，用以下格式给出 2~4 个可选行动：
            [CHOICES]
            {"choices":["选项1","选项2","选项3"]}
            [/CHOICES]
            让故事始终充满张力与未知，等待玩家的下一步行动。
        """.trimIndent()

        InteractiveMode.VISUAL_NOVEL -> """
            【互动模式：视觉小说】
            你现在是视觉小说中的角色。请以角色台词为主推进剧情，台词简短口语化，适当穿插场景描写。

            每轮回复的最后，必须输出分支选项，格式：
            [CHOICES]
            {"choices":[
              {"text":"选项描述","delta":{"affection":0,"mood":0}},
              {"text":"选项描述","delta":{"affection":0}},
              {"text":"选项描述","delta":{}},
              {"text":"选项描述（可选第4个）","delta":{}}
            ]}
            [/CHOICES]

            选项硬性要求：
            1. 固定 3～4 个，不得缺少
            2. 每个选项 30～60 字，写成「具体动作 + 目的/语气/神态」的完整句子，禁止「答应」「拒绝」这类两三个字的干巴选项
            3. 选项之间方向明显分化：至少覆盖温柔亲近、试探观察、强势推进、冷淡回避等不同路线，让玩家性格不同则走向不同
            4. delta 是选择该选项后角色状态的预估变化（增量，可正可负）；没有状态栏的角色卡也请照常给出合理 delta
            5. 只输出选项文字与 delta，不要在选项里写旁白

            让故事节奏紧凑，像日式 AVG 一样引导玩家推进剧情。
        """.trimIndent()

        InteractiveMode.FREE_ROLEPLAY -> """
            【互动模式：自由角色扮演】
            请完全沉浸地扮演角色，始终以角色的身份、语气、口吻回应，不要跳出角色，不要以"AI"自居。玩家会扮演另一个角色与你互动，你可以即兴发挥推动剧情，始终保持角色人设一致。
        """.trimIndent()
    }
}

/**
 * 互动模式显示名
 */
fun InteractiveMode.displayName(): String = when (this) {
    InteractiveMode.CHAT -> "聊天"
    InteractiveMode.TEXT_ADVENTURE -> "文字冒险"
    InteractiveMode.VISUAL_NOVEL -> "视觉小说"
    InteractiveMode.FREE_ROLEPLAY -> "自由扮演"
}
