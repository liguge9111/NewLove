package com.tavern.app.core.model

/**
 * 视觉小说/互动选项（含可选的状态增量）
 */
data class VNChoice(
    /** 选项文本（30-60 字的动作描述） */
    val text: String,

    /** 选择后结算的状态增量（可为空） */
    val delta: StatusDelta = StatusDelta()
)
