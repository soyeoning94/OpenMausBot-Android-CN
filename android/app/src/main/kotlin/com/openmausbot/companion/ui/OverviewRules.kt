package com.openmausbot.companion.ui

/**
 * "What this bot does", as rules — the copy behind [BotOverviewScreen].
 *
 * Everything the screen shows is server-authored prose (`who`, `does`,
 * `reaches`, `wont`, `recent`): there is nothing here to validate or compute,
 * only what to say. Section names and the empty-state copy for `does` and
 * `recent` match `ios/App/BotOverviewView.swift` — `reaches` and `wont` carry
 * no empty-state copy there either, so an empty list there is simply a header
 * with nothing under it, on both phones.
 */
object OverviewRules {
    fun title(name: String): String = "$name 的职责说明"

    const val EMPTY_DOES: String = "暂无定时任务或已学习的技能。"
    const val EMPTY_RECENT: String = "暂无变更记录。"
    const val FAILED: String = "无法加载职责概览。"

    const val WHO: String = "身份"
    const val DOES: String = "职责与任务"
    const val REACHES: String = "可访问范围"
    const val WONT: String = "限制与拒绝"
    const val RECENT: String = "最近变更"
}
