package com.openmausbot.companion.ui

import com.openmausbot.companion.core.Routine
import com.openmausbot.companion.core.RoutineInput
import com.openmausbot.companion.core.RoutineRun
import com.openmausbot.companion.core.RoutineRunAvailability
import com.openmausbot.companion.core.RoutineRunLocation
import com.openmausbot.companion.core.RoutineSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Tasks & Routines, as rules — the decision half of
 * `ios/App/TasksRoutinesView.swift`.
 *
 * A task is one conversation and result; a routine runs with fresh context and
 * collects results in one thread. No cron syntax reaches the phone, and the two
 * operations not on the paired allowlist — webhook management, and cancel/seen on a run —
 * have no representation here at all.
 */
object RoutineRules {
    /** `String(name.trimming….prefix(80))` in `RoutineEditorView.save`. */
    const val NAME_LIMIT: Int = 80

    /** `String(prompt.trimming….prefix(20_000))`. */
    const val PROMPT_LIMIT: Int = 20_000

    /** Kept on requests for compatibility with desktop builds that still require the field. */
    const val LEGACY_DURATION_MINUTES: Int = 30

    /** Optional execution guard. It is deliberately separate from cadence and calendar size. */
    val TIMEOUT_RANGE: IntRange = 5..240
    val TIMEOUT_OPTIONS: List<Int> = TIMEOUT_RANGE.step(5).toList()
    const val DEFAULT_INTERVAL_TIMEOUT: Int = 30

    val INTERVAL_RANGE: IntRange = 5..1_440
    val INTERVAL_PRESETS: List<Int> = listOf(5, 10, 15, 30, 60)
    const val DEFAULT_INTERVAL: Int = 15

    /** `.prefix(50)` on the sorted receipts. */
    const val RECEIPT_LIMIT: Int = 50

    /** Sunday-first, exactly as the wire's 0…6 weekdays are indexed. */
    val DAY_LETTERS: List<String> = listOf("日", "一", "二", "三", "四", "五", "六")
    val DAY_NAMES: List<String> = listOf(
        "周日", "周一", "周二", "周三", "周四", "周五", "周六",
    )
    val DEFAULT_WEEKDAYS: Set<Int> = setOf(1, 2, 3, 4, 5)
    const val DEFAULT_TIME: String = "09:00"

    /** `Date().addingTimeInterval(3_600)` — the default one-time instant. */
    const val ONCE_DEFAULT_OFFSET_MILLIS: Long = 3_600_000L

    const val HEADER_TASK: String = "会话 = 单次对话与结果"
    const val HEADER_ROUTINE: String = "例程 = 定时工作并将结果汇总在单个会话中"

    const val HEADER_FOOTER: String =
        "无需编写 cron 表达式。每次运行都会使用智能体现有的模型、工具、权限、" +
            "电脑以及已连接应用。时间遵循已配对电脑的本地时区。"

    const val NO_ROUTINES_TITLE: String = "暂无例程"
    const val NO_ROUTINES_DESCRIPTION: String = "安排定期或单次运行的智能体工作。"
    const val NO_RECEIPTS: String =
        "已完成、等待中、失败和手动启动的运行记录会显示在这里。"

    const val WEBHOOKS_LABEL: String = "仅限电脑端"
    const val WEBHOOKS_FOOTER: String =
        "创建或轮换 Webhook 会更改公网可访问的触发器和签名密钥，" +
            "因此 Webhook 管理保留在已配对的电脑上。Webhook 运行回执仍会显示在上方。"

    const val DELETE_MESSAGE: String = "过往的运行回执仍会保留。"
    const val DELETED_AGENT: String = "已删除的智能体"
    const val WAITING_ON_YOU: String = "此会话正在等待你的回复。"
    const val UNSUPPORTED_SCHEDULE_ERROR: String =
        "请先选择受支持的计划时间再保存此例程。"

    const val SCHEDULE_FOOTER: String =
        "每次运行都会以全新上下文开始。结果汇总在同一个会话中，" +
            "并保留完整的运行日志。无需使用 cron 表达式。"
    const val INTERVAL_SCHEDULE_FOOTER: String =
        "每次运行都会以全新上下文开始。结果汇总在同一个会话中，" +
            "并保留完整的运行日志。如果上一次运行仍在进行中，" +
            "下一次触发将被跳过而不是排队。"
    const val UNKNOWN_SCHEDULE_NOTE: String =
        "此例程使用了新版 OpenMausBot 添加的计划类型。请在保存前选择“单次运行”、" +
            "“指定星期”或“每隔 X 分钟”。"

    const val CHECKING_CLOUD: String = "正在检查云端虚拟机可用性…"
    const val CLOUD_STATUS_UNAVAILABLE: String = "云端虚拟机状态不可用"

    const val MAUS_FOOTER: String =
        "使用此智能体在已配对电脑上所选的模型和电脑设置。"
    const val CLOUD_READY_FOOTER: String =
        "在智能体的 Box 虚拟机内运行该智能体及其工具。虚拟机会在每次运行时自动唤醒；" +
            "请保持 OpenMausBot 运行以便调度器启动任务。"
    const val CLOUD_BLOCKED_FOOTER: String =
        "此现有的云端虚拟机选项已被保留，但在已配对电脑配置好 Box API 密钥" +
            "并有可用的 Box 智能体之前无法运行。"

    /** Soonest first; a routine with no next run sorts last. */
    fun sorted(routines: List<Routine>): List<Routine> =
        routines.sortedBy { it.nextRunAt ?: Double.MAX_VALUE }

    /** Newest scheduled instant first, and only the last [RECEIPT_LIMIT]. */
    fun receipts(runs: List<RoutineRun>): List<RoutineRun> =
        runs.sortedByDescending { it.scheduledFor }.take(RECEIPT_LIMIT)

    fun locationLabel(location: RoutineRunLocation): String = when (location) {
        RoutineRunLocation.MAUS -> "此电脑"
        RoutineRunLocation.CLOUD -> "云端虚拟机"
    }

    /**
     * The routine's one-line schedule.
     *
     * Two quirks are the Swift's and are mirrored rather than repaired: seven
     * weekdays read as "Every day" whatever the seven are, and only the exact
     * ordered list `[1,2,3,4,5]` reads as "Weekdays".
     */
    fun scheduleSummary(
        schedule: RoutineSchedule,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        when (schedule.type) {
            RoutineSchedule.Kind.ONCE -> {
                val at = schedule.at ?: return "单次运行 · 日期不可用"
                return RelativeStamp.dateAndTime(at, zone, locale)
            }
            RoutineSchedule.Kind.INTERVAL -> {
                val minutes = schedule.everyMinutes ?: return "间隔不可用"
                val cadence = "每 $minutes 分钟"
                val anchor = schedule.anchorAt ?: return cadence
                return "$cadence · 自 ${RelativeStamp.dateAndTime(anchor.toDouble(), zone, locale)} 起"
            }
            RoutineSchedule.Kind.UNKNOWN -> return "较新版本的计划"
            RoutineSchedule.Kind.DAILY -> Unit
        }
        val values = schedule.weekdays.orEmpty()
        val dayText = when {
            values.size == 7 -> "每天"
            values == listOf(1, 2, 3, 4, 5) -> "工作日"
            else -> values.mapNotNull { DAY_NAMES.getOrNull(it)?.take(3) }.joinToString("、")
        }
        return "$dayText ${schedule.time ?: "—"}"
    }

    /** `"\(bot?.name ?? "Deleted agent") · \(schedule.summary) · \(runLocation.label)"`. */
    fun routineSubtitle(
        routine: Routine,
        botName: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = listOf(
        botName ?: DELETED_AGENT,
        scheduleSummary(routine.schedule, zone, locale),
        locationLabel(routine.runLocation),
    ).joinToString(" · ")

    fun runSubtitle(
        run: RoutineRun,
        botName: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = "${botName ?: DELETED_AGENT} · ${RelativeStamp.dateAndTime(run.scheduledFor, zone, locale)}"

    /** What a paused routine's trailing glyph means, or null while it is running. */
    fun pausedBadge(routine: Routine, atMillis: Double): PausedBadge? = when {
        routine.enabled -> null
        routine.canToggle(atMillis) -> PausedBadge.PAUSED
        else -> PausedBadge.COMPLETED
    }

    enum class PausedBadge(val label: String) { PAUSED("已暂停"), COMPLETED("已完成") }

    enum class RunStatus { RUNNING, COMPLETED, WAITING, FAILED, CANCELLED, PENDING }

    /** `String.symbol` / `String.tint` in the Swift, as one decision. */
    fun runStatus(status: String): RunStatus = when (status) {
        "running" -> RunStatus.RUNNING
        "completed" -> RunStatus.COMPLETED
        "waiting" -> RunStatus.WAITING
        "failed", "missed" -> RunStatus.FAILED
        "cancelled" -> RunStatus.CANCELLED
        else -> RunStatus.PENDING
    }

    /** `run.status == "waiting" ? "Needs you" : run.status.capitalized`. */
    fun runStatusLabel(status: String): String = when (status.lowercase(Locale.ROOT)) {
        "waiting" -> "需要你处理"
        "running" -> "运行中"
        "completed" -> "已完成"
        "failed" -> "失败"
        "missed" -> "已错过"
        "cancelled" -> "已取消"
        "pending" -> "等待中"
        else -> status.split(' ').joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    fun cloudSelectable(
        availability: RoutineRunAvailability?,
        current: RoutineRunLocation,
    ): Boolean = availability?.canSelect(RoutineRunLocation.CLOUD, current)
        ?: (current == RoutineRunLocation.CLOUD)

    fun locationFooter(
        runOn: RoutineRunLocation,
        availability: RoutineRunAvailability?,
    ): String = when {
        runOn == RoutineRunLocation.MAUS -> MAUS_FOOTER
        availability?.cloudReady == true -> CLOUD_READY_FOOTER
        else -> CLOUD_BLOCKED_FOOTER
    }

    fun canSave(
        saving: Boolean,
        kind: RoutineSchedule.Kind,
        name: String,
        prompt: String,
        botId: String,
        weekdays: Set<Int>,
        everyMinutes: Int? = null,
    ): Boolean = !saving &&
        kind != RoutineSchedule.Kind.UNKNOWN &&
        name.trim().isNotEmpty() &&
        prompt.trim().isNotEmpty() &&
        botId.isNotEmpty() &&
        !(kind == RoutineSchedule.Kind.DAILY && weekdays.isEmpty()) &&
        !(kind == RoutineSchedule.Kind.INTERVAL &&
            (everyMinutes == null || everyMinutes !in INTERVAL_RANGE))

    fun intervalMinutes(raw: String): Int? =
        raw.trim().toIntOrNull()?.takeIf { it in INTERVAL_RANGE }

    fun defaultIntervalAnchor(nowMillis: Long, everyMinutes: Int = DEFAULT_INTERVAL): Long =
        nowMillis + everyMinutes * 60_000L

    fun intervalTimeoutOnFirstSelection(current: Int?, defaultAlreadyApplied: Boolean): Int? =
        if (defaultAlreadyApplied) current else DEFAULT_INTERVAL_TIMEOUT

    fun validTimeout(minutes: Int?): Boolean = minutes == null || minutes in TIMEOUT_RANGE

    /** `"%02d:%02d"` — what `DateFormatter("HH:mm")` writes for a wall-clock time. */
    fun timeText(hour: Int, minute: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /**
     * The stored `"HH:mm"` as a picker value. A field the phone cannot read
     * falls back to the current time, which is what `date(bySettingHour:)`
     * returning nil does on iOS.
     */
    fun parseTime(raw: String?, now: LocalTime): LocalTime {
        val parts = (raw ?: DEFAULT_TIME).split(":").mapNotNull { it.toIntOrNull() }
        val hour = parts.firstOrNull() ?: 9
        val minute = if (parts.size > 1) parts[1] else 0
        if (hour !in 0..23 || minute !in 0..59) return now
        return LocalTime.of(hour, minute)
    }

    /**
     * Whether the instant a date and a wall-clock time would produce is inside
     * `DatePicker("Run", selection: $onceAt, in: Date()...)`.
     *
     * The Swift bounds the **instant**, not the day and not the hour-minute
     * pair, and it bounds only what can be *selected* — a value already bound to
     * the picker stays exactly as it is, which is why an already-fired routine
     * re-saves untouched.
     *
     * The comparison is therefore against the candidate instant: at 10:30:45,
     * 10:30 resolves to 10:30:00, which has passed, so the first minute the
     * picker can offer today is 10:31.
     */
    fun onceInstantSelectable(
        date: LocalDate,
        hour: Int,
        minute: Int,
        now: Instant,
        zone: ZoneId,
    ): Boolean = !date.atTime(hour, minute).atZone(zone).toInstant().isBefore(now)

    /**
     * The first minute on [date] whose instant is not already past, or null when
     * the day has none left — a date whose last minute is behind [now], and the
     * seconds after 23:59 on the current day.
     */
    fun earliestSelectableMinute(date: LocalDate, now: Instant, zone: ZoneId): LocalTime? {
        if (!date.atStartOfDay(zone).toInstant().isBefore(now)) return LocalTime.MIN
        val local = now.atZone(zone)
        if (local.toLocalDate() != date) return null
        val onTheMinute = local.toLocalTime().withSecond(0).withNano(0)
        if (onTheMinute == local.toLocalTime()) return onTheMinute
        val next = onTheMinute.plusMinutes(1)
        // 23:59:xx wraps to 00:00, which belongs to the next day, not this one.
        return if (next.isAfter(onTheMinute)) next else null
    }

    /**
     * Where the time picker opens. A bounded picker cannot rest on a position
     * outside its range, so neither does this: a stored time that has already
     * passed opens on the first minute still inside the range. Opening a picker
     * changes nothing until the choice is confirmed.
     */
    fun onceTimeStart(date: LocalDate, time: LocalTime, now: Instant, zone: ZoneId): LocalTime =
        if (onceInstantSelectable(date, time.hour, time.minute, now, zone)) {
            time
        } else {
            earliestSelectableMinute(date, now, zone) ?: time
        }

    fun schedule(
        kind: RoutineSchedule.Kind,
        onceAtMillis: Double,
        hour: Int,
        minute: Int,
        weekdays: Set<Int>,
        everyMinutes: Int = DEFAULT_INTERVAL,
    ): RoutineSchedule = when (kind) {
        RoutineSchedule.Kind.ONCE -> RoutineSchedule.once(onceAtMillis)
        RoutineSchedule.Kind.DAILY ->
            RoutineSchedule.daily(timeText(hour, minute), weekdays.sorted())
        RoutineSchedule.Kind.INTERVAL ->
            RoutineSchedule.interval(everyMinutes, onceAtMillis.toLong())
        RoutineSchedule.Kind.UNKNOWN -> RoutineSchedule(kind)
    }

    fun input(
        name: String,
        prompt: String,
        botId: String,
        runOn: RoutineRunLocation,
        enabled: Boolean?,
        schedule: RoutineSchedule,
        durationMinutes: Int,
        timeoutMinutes: Int? = null,
    ): RoutineInput {
        require(validTimeout(timeoutMinutes)) {
            "Timeout must be empty or a whole number from 5 to 240"
        }
        return RoutineInput(
            name = name.trim().take(NAME_LIMIT),
            prompt = prompt.trim().take(PROMPT_LIMIT),
            botId = botId,
            runOn = runOn.wireValue,
            enabled = enabled,
            schedule = schedule,
            durationMinutes = durationMinutes,
            timeoutMinutes = timeoutMinutes,
            clearTimeout = timeoutMinutes == null,
        )
    }
}

/**
 * The one-time instant while it is being picked.
 *
 * The Swift edits it with a single bounded `DatePicker`, so there is no moment
 * where half a choice exists. Material picks the day and the clock in two
 * dialogs, which creates one: a day the user has chosen and a time they have
 * not yet confirmed. That half lives in [stagedDate] and touches nothing —
 * only [confirm] writes [millis], so cancelling either dialog leaves the
 * instant the routine already had exactly as it was.
 */
data class OnceDraft(
    val millis: Long,
    /** Chosen in the date dialog, waiting for the time dialog. Never saved. */
    val stagedDate: LocalDate? = null,
) {
    /** The day the time dialog is bounding: the staged one, else the committed one. */
    fun date(zone: ZoneId): LocalDate =
        stagedDate ?: Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** The committed time of day, which the time dialog opens near. */
    fun time(zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    fun stage(date: LocalDate): OnceDraft = copy(stagedDate = date)

    /** Cancelling, at either step. */
    fun discard(): OnceDraft = copy(stagedDate = null)

    /** The only write: the day being bounded, at the confirmed time. */
    fun confirm(hour: Int, minute: Int, zone: ZoneId): OnceDraft = OnceDraft(
        millis = date(zone)
            .atTime(hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli(),
    )
}

/**
 * The editor's agent, and whether the one-time default has been applied yet.
 *
 * `onAppear { if botId.isEmpty { botId = …first } }` runs once in the Swift,
 * because a rotation does not rebuild the view. On Android the effect runs again
 * after every recreation, so "no agent has been chosen yet" and "the user chose
 * the empty tag" have to be distinguishable — otherwise restoring a deliberate
 * "Choose an agent" hands it the first agent instead. [applied] is what carries
 * that distinction across the recreation.
 */
data class AgentChoice(val botId: String, val applied: Boolean = false) {
    /**
     * The default, once. Every later call is a no-op whatever was chosen — the
     * Swift's `onAppear` fires once too, so a fleet frame landing afterwards
     * never re-seeds either.
     */
    fun withDefault(firstAgentId: String?): AgentChoice =
        if (applied) this else AgentChoice(botId.ifEmpty { firstAgentId.orEmpty() }, applied = true)

    /** Any pick from the picker is a made choice, the empty tag included. */
    fun choose(id: String): AgentChoice = AgentChoice(id, applied = true)
}
