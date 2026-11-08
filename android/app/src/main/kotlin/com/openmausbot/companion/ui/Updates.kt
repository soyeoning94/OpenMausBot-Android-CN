package com.openmausbot.companion.ui

import androidx.compose.runtime.Immutable
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.PendingApproval
import com.openmausbot.companion.core.forTask
import com.openmausbot.companion.core.takeLastCharacters
import com.openmausbot.companion.core.visibleTasks

/**
 * What the Updates pill shows: only the chats doing something — the port of
 * `ios/App/Updates.swift`.
 *
 * Three kinds, in the order a person cares about them — a bot that has stopped
 * and needs an answer, a bot mid-turn, and a bot that finished with something you
 * have not read. A bot that is idle and read is not an update and never appears
 * here; that is what the roster below is for.
 */
internal enum class UpdateKind { NEEDS_YOU, WORKING, TO_REVIEW }

@Immutable
internal data class ChatUpdate(
    val chat: Chat,
    val kind: UpdateKind,
    /** One line under the name — the question, what it is doing, or what it said. */
    val line: String,
    /** The card to answer, when [kind] is [UpdateKind.NEEDS_YOU]. */
    val card: OptionCard? = null,
) {
    val id: String get() = chat.conversationId
}

internal val CompanionState.updates: List<ChatUpdate>
    get() = updates(pendingApprovals)

/**
 * The same derivation for a caller that already holds the pending approvals.
 * [CompanionState.pendingApprovals] walks every thread's visible transcript, and
 * the roster reads it for its own rows on the frame it draws the pill.
 */
internal fun CompanionState.updates(pending: List<PendingApproval>): List<ChatUpdate> {
    val out = mutableListOf<ChatUpdate>()
    val seen = mutableSetOf<String>()

    // Newest approval first, one per conversation: the pill headlines the most recent
    // thing that stopped, and the sheet lists the rest.
    for (approval in pending) {
        val chat = ThreadResolution.chatOrNull(this, approval.threadId) ?: continue
        if (!seen.add(chat.conversationId)) continue
        val card = approval.message.card
        // iOS writes `card?.subtitle ?? card?.title ?? ""`, where `subtitle` is
        // not optional — so the title arm is reachable only for a null card, and
        // an empty subtitle stays empty rather than falling back to the title.
        out += ChatUpdate(chat, UpdateKind.NEEDS_YOU, card?.subtitle.orEmpty(), card)
    }

    for (bot in bots) {
        if (bot.hidden == true) continue
        // Old desktops expose only the selected conversation. New desktops
        // expose each task's own runtime flags, including active siblings.
        val conversations = if (bot.tasks == null) listOf(bot)
            else bot.visibleTasks.mapNotNull { bot.forTask(it.threadId) }
        for (conversation in conversations) {
            val chat = Chat.BotChat(conversation)
            if (!seen.add(chat.conversationId)) continue
            val held = pendingQueued[conversation.threadId]?.size ?: 0
            when {
                conversation.activity == "waiting-on-you" ->
                    out += ChatUpdate(chat, UpdateKind.NEEDS_YOU, "等待你的回复")
                held > 0 ->
                    out += ChatUpdate(
                        chat, UpdateKind.WORKING,
                        if (held == 1) "已排队 — 正在等待可用槽位" else "已排队 $held 条消息",
                    )
                conversation.busy == true ->
                    out += ChatUpdate(chat, UpdateKind.WORKING, workingLine(chat.threadId))
                conversation.unread ->
                    out += ChatUpdate(chat, UpdateKind.TO_REVIEW, lastLine(chat.threadId))
            }
        }
    }

        for (room in rooms) {
            val chat = Chat.RoomChat(room)
            if (chat.conversationId in seen) continue
            val held = pendingQueued[room.threadId]?.size ?: 0
            when {
                held > 0 -> {
                    seen += chat.conversationId
                    out += ChatUpdate(
                        chat, UpdateKind.WORKING,
                        if (held == 1) "已排队 — 正在等待可用槽位" else "已排队 $held 条消息",
                    )
                }
                room.busyBotId != null -> {
                seen += chat.conversationId
                out += ChatUpdate(chat, UpdateKind.WORKING, workingLine(room.threadId))
            }
            room.unread -> {
                seen += chat.conversationId
                out += ChatUpdate(chat, UpdateKind.TO_REVIEW, lastLine(room.threadId))
            }
        }
    }

    // Stable, so what each pass appended survives the grouping: approvals newest
    // first, then bots and rooms in fleet order.
    return out.sortedBy(ChatUpdate::kind)
}

private fun CompanionState.workingLine(threadId: String): String {
    val live = streaming[threadId]
    if (!live.isNullOrEmpty()) return live.takeLastCharacters(STREAM_TAIL).replace('\n', ' ')
    val last = visibleTranscript(threadId).lastOrNull()
    if (last?.kind == Message.Kind.ACTIVITY) last.tool?.let { return it.name }
    return WORKING_LINE
}

private fun CompanionState.lastLine(threadId: String): String {
    val last = visibleTranscript(threadId).lastOrNull() ?: return ""
    return when (last.kind) {
        Message.Kind.TEXT, Message.Kind.UNKNOWN -> last.text.orEmpty()
        Message.Kind.OPTIONS -> last.card?.title.orEmpty()
        Message.Kind.ACTIVITY -> last.tool?.name.orEmpty()
        Message.Kind.SCREEN -> "屏幕截图"
        Message.Kind.DIGEST -> ""
        Message.Kind.COMPACTION -> last.compaction?.chipText ?: last.text.orEmpty()
    }
}

/** How much of a streaming turn the working line carries. */
private const val STREAM_TAIL = 120

private const val WORKING_LINE = "处理中…"

/**
 * The words the pill and the sheet put around a derivation, kept out of the
 * composables so they can be pinned without a device.
 */
internal object UpdatesSummary {
    /** At most this many faces stack in the pill. */
    const val MASCOTS = 3

    const val EMPTY_TITLE = "暂无待办事项"
    const val EMPTY_DESCRIPTION =
        "当智能体暂停等待你的回复、正在处理任务或刚完成某项工作时，都会显示在这里。"

    /** The pill's first line: who, and what they are doing about it. */
    fun headline(updates: List<ChatUpdate>): String {
        val first = updates.firstOrNull() ?: return "一切平静"
        return when (first.kind) {
            UpdateKind.NEEDS_YOU -> "${first.chat.name} 需要你处理"
            UpdateKind.WORKING -> "${first.chat.name} 正在工作"
            UpdateKind.TO_REVIEW -> "${first.chat.name} 有新动态"
        }
    }

    /**
     * The pill's second line: the one update's own line while it is alone, and
     * how many others there are once it is not.
     */
    fun subline(updates: List<ChatUpdate>): String {
        val first = updates.firstOrNull() ?: return "暂无待办事项"
        val rest = updates.size - 1
        if (rest == 0) return first.line.ifEmpty { " " }
        return "还有 $rest 条动态"
    }

    /** The sheet's header, beside the title. */
    fun count(updates: List<ChatUpdate>): String =
        if (updates.isEmpty()) "一切平静" else "${updates.size} 项活动"

    fun section(kind: UpdateKind): String = when (kind) {
        UpdateKind.NEEDS_YOU -> "需要你处理"
        UpdateKind.WORKING -> "正在工作"
        UpdateKind.TO_REVIEW -> "待查看"
    }

    /**
     * The heading as the sheet draws it. Uppercased by the invariant rules, which
     * is what Swift's `uppercased()` does — a reader in `tr-TR` must still read
     * WORKING and not WORKİNG.
     */
    fun sectionLabel(kind: UpdateKind): String = section(kind).uppercase()
}
