package com.openmausbot.companion.core

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** How much of a bot's working activity the transcript shows. */
enum class ActivityDetail(val wireValue: String, val label: String, val caption: String) {
    FULL("full", "完整", "显示智能体执行的每一步。"),
    REDUCED("reduced", "精简", "将多个步骤折叠为一行，失败步骤始终显示。"),
    HIDDEN("hidden", "隐藏", "不显示活动步骤，仅显示消息。"),
    ;

    companion object {
        fun fromWire(value: String?): ActivityDetail =
            entries.firstOrNull { it.wireValue == value } ?: FULL
    }
}

/** One user-editable chip on the composer's quick-reply row. */
@Serializable
data class QuickReply(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val prompt: String,
    val icon: String,
) {
    companion object {
        val DEFAULTS: List<QuickReply> = listOf(
            QuickReply("default.diff", "查看差异", "显示最新的 git 差异", "diff"),
            QuickReply("default.tests", "运行测试", "运行所有自动化测试", "tests"),
            QuickReply("default.explain", "解释步骤", "详细解释所做的更改", "explain"),
            QuickReply("default.next", "下一步？", "接下来我们该做什么？", "next"),
        )

        val ICON_CHOICES: List<String> = listOf(
            "next", "diff", "tests", "explain", "build", "bug", "document", "terminal",
            "send", "search", "history", "list",
        )

        fun encode(replies: List<QuickReply>): String =
            runCatching { CompanionJson.encodeToString(replies) }.getOrDefault("")

        /**
         * An empty or corrupt store falls back to defaults. An encoded empty list is a deliberate
         * choice and remains empty.
         */
        fun decode(json: String): List<QuickReply> {
            if (json.isEmpty()) return DEFAULTS
            val decoded = runCatching {
                CompanionJson.decodeFromString<List<QuickReply>>(json)
            }.getOrElse { return DEFAULTS }
            val ids = decoded.map { it.id.trim() }
            if (ids.any(String::isEmpty) || ids.toSet().size != ids.size) return DEFAULTS
            return decoded
        }
    }
}

/** A transcript item: one message, consecutive activity, or completed narration. */
sealed interface TranscriptRow {
    val head: Message
    val id: String
    val at: Double get() = head.at
    val endAt: Double
    val role: Message.Role get() = head.role
    val kind: Message.Kind get() = head.kind
    val senderName: String? get() = head.from?.name

    /** Search and pagination anchors can land inside a folded row. */
    fun containsMessage(messageId: String): Boolean = when (this) {
        is Single -> message.id == messageId
        is ActivityRun -> items.any { it.id == messageId }
        is AssistantTurn -> items.any { it.id == messageId }
    }

    data class Single(val message: Message) : TranscriptRow {
        override val head: Message get() = message
        override val id: String get() = message.id
        override val endAt: Double get() = message.at
    }

    data class ActivityRun(val items: List<Message>) : TranscriptRow {
        init {
            require(items.isNotEmpty()) { "An activity run must contain at least one message." }
        }

        override val head: Message get() = items.first()
        override val id: String get() = "run.${head.id}"
        override val endAt: Double get() = items.last().at
        val running: Boolean get() = items.any { it.tool?.ok == null }
    }

    data class AssistantTurn(val turnId: String, val items: List<Message>, val elapsed: Double) : TranscriptRow {
        init {
            require(items.isNotEmpty()) { "A turn fold must contain narration." }
        }

        override val head: Message get() = items.first()
        override val id: String get() = "turn.$turnId"
        override val endAt: Double get() = items.last().at
        val label: String get() {
            if (elapsed < 1_000) return "已处理"
            val seconds = (elapsed / 1_000).toLong()
            val duration = if (seconds < 60) "${seconds}秒"
                else "${seconds / 60}分 ${(seconds % 60).toString().padStart(2, '0')}秒"
            return "已处理 $duration"
        }
    }
}

/**
 * The one line a roster row shows under a chat's name.
 *
 * Folded by the same rule as the transcript, and for the same reason: a reader who has turned
 * activity off has said they do not want to see tool calls, and the roster is where they see the
 * most of them — one per chat, on the screen they spend the most time on. Reading the preview off
 * the raw last message made "Hidden" mean "hidden in one place".
 */
fun rosterPreview(messages: List<Message>, detail: ActivityDetail): String =
    when (val last = transcriptRows(messages, detail).lastOrNull()) {
        null -> ""
        is TranscriptRow.Single -> previewText(last.message)
        is TranscriptRow.ActivityRun ->
            "${if (last.running) "正在运行" else "已运行"} ${last.items.size} 个步骤"
        is TranscriptRow.AssistantTurn -> last.label
    }

/** What a single message reads as in a roster row. */
internal fun previewText(message: Message): String = when (message.kind) {
    Message.Kind.TEXT -> message.webhookContent?.task ?: message.text.orEmpty()
    // a pending card's question is the preview; the roster row already says
    // "waiting on you" beside it
    Message.Kind.OPTIONS -> {
        val card = message.card
        when {
            card == null -> ""
            card.isPending && card.subtitle.isNotEmpty() -> card.subtitle
            else -> card.title
        }
    }
    Message.Kind.ACTIVITY -> message.tool?.name.orEmpty()
    Message.Kind.SCREEN -> "屏幕截图"
    Message.Kind.DIGEST -> ""
    Message.Kind.COMPACTION -> message.compaction?.chipText ?: message.text.orEmpty()
    Message.Kind.UNKNOWN -> message.text.orEmpty()
}

/**
 * Rows the harness writes about a turn rather than in it: tool chips and, since
 * Phase 0, the digest and compaction receipts. Hidden together, because a reader
 * who turned activity off does not want the summary of exactly those calls either.
 * Port of `isActivityReceipt` in `ChatPreferences.swift`.
 */
fun isActivityReceipt(message: Message): Boolean = when (message.kind) {
    Message.Kind.ACTIVITY, Message.Kind.DIGEST, Message.Kind.COMPACTION -> true
    else -> false
}

/**
 * Fold a transcript to the selected activity detail. Failed steps are never folded in reduced
 * mode; hidden mode intentionally removes all activity, including failures.
 */
fun transcriptRows(messages: List<Message>, detail: ActivityDetail): List<TranscriptRow> {
    // Only a server completion marker makes narration foldable. Legacy and
    // unfinished turns stay visible, matching desktop and iOS.
    val narration = mutableMapOf<String, MutableList<Message>>()
    val startedAt = mutableMapOf<String, Double>()
    var lastUserAt: Double? = null
    val folds = mutableMapOf<String, TranscriptRow.AssistantTurn>()
    val hiddenIds = mutableSetOf<String>()
    for (message in messages) {
        if (message.role == Message.Role.USER) lastUserAt = message.at
        val turnId = message.turnId?.takeIf { it.isNotEmpty() } ?: continue
        if (message.role != Message.Role.BOT || message.kind != Message.Kind.TEXT) continue
        if (message.turnTerminal == true) {
            val items = narration.remove(turnId)?.takeIf { it.isNotEmpty() } ?: continue
            folds[items.first().id] = TranscriptRow.AssistantTurn(
                turnId, items.toList(), (message.at - (startedAt[turnId] ?: items.first().at)).coerceAtLeast(0.0),
            )
            hiddenIds.addAll(items.map { it.id })
        } else {
            if (turnId !in narration) startedAt[turnId] = lastUserAt ?: message.at
            narration.getOrPut(turnId) { mutableListOf() }.add(message)
        }
    }
    return buildList {
        val run = mutableListOf<Message>()
        fun flush() {
            when (run.size) {
                0 -> Unit
                1 -> add(TranscriptRow.Single(run.single()))
                else -> add(TranscriptRow.ActivityRun(run.toList()))
            }
            run.clear()
        }

        messages.forEach { message ->
            if (message.kind == Message.Kind.DIGEST) return@forEach
            val turn = folds[message.id]
            if (turn != null) {
                flush()
                add(turn)
            } else if (message.id in hiddenIds || (detail == ActivityDetail.HIDDEN && isActivityReceipt(message))) {
                // The reversible turn fold owns narration; Hidden owns tools.
            } else if (detail != ActivityDetail.REDUCED || message.kind != Message.Kind.ACTIVITY) {
                flush()
                add(TranscriptRow.Single(message))
            } else if (message.tool?.ok == false) {
                flush()
                add(TranscriptRow.Single(message))
            } else {
                run += message
            }
        }
        flush()
    }
}
