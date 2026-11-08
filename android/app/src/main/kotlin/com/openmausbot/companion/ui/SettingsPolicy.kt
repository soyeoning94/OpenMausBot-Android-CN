package com.openmausbot.companion.ui

import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What little the phone gets to configure — `ios/App/SettingsView.swift`.
 *
 * Almost nothing, on purpose: Phone settings, API keys and pairing all live
 * on the computer, because losing the phone must not mean losing the ability to
 * lock it out (§13). This is a status page with an unpair button.
 */
object SettingsPolicy {
    const val NOTIFICATIONS_FOOTER =
        "在 MausBot 保持连接期间（包括后台短暂暂停后重放的消息帧），审批请求和已完成的工作都会显示通知。" +
            "应用完全关闭后的推送通知需要单独的推送中继版本支持（目前尚未推出）。"

    const val WORKSPACE_FOOTER =
        "例程计划可在此安全管理。提供商密钥、Webhook 密钥、配对、撤销权限、" +
            "本地虚拟机以及智能体执行策略均保留在你的电脑上。"

    const val UNPAIR_FOOTER =
        "仅从本手机移除配对。若要彻底禁止此手机访问电脑，请在电脑端 OpenMausBot → 设置 → 手机 中移除该设备。"

    const val NOT_HERE =
        "API 密钥、配对和本地虚拟机均在电脑上管理。为安全起见，手机端不允许更改这些设置。"

    const val UNPAIR_CONFIRM_TITLE = "取消配对此手机？"
    const val UNPAIR_CONFIRM_MESSAGE = "再次连接需要新的配对码。"

    const val EDIT_ADDRESS_MESSAGE =
        "请输入电脑端“手机设置”中显示的地址。现有配对将保留。"

    fun statusText(status: Session.Status): String = when (status) {
        Session.Status.Live -> "已连接"
        Session.Status.Connecting -> "正在连接…"
        Session.Status.Unpaired -> "未配对"
        Session.Status.Unauthorized -> "已在电脑上取消配对"
        is Session.Status.Offline -> status.message
    }

    fun addressText(connection: Connection?): String =
        connection?.displayAddress ?: "—"
}

/**
 * The stored address can simply go stale — a tailnet name on a phone that left
 * the tailnet, a LAN address after the router reshuffled. Editing it keeps the
 * pairing and its token; the alternative is a walk to the computer for a new code
 * (§7).
 */
object AddressEdit {
    const val INVALID =
        "请输入安全的 https:// 地址、192.168.1.42:8810，或类似 macbook.tail1234.ts.net 的域名。"

    /** The same parse `Session.updateAddress` will do, so the form can say no first. */
    fun isValid(text: String): Boolean = Connection.parse(text) != null
}

/** What the notification row can offer right now. */
enum class NotificationAccess {
    GRANTED,

    /** Not granted, and the OS will still show a prompt. */
    ASKABLE,

    /** Only the system settings page can turn these back on. */
    BLOCKED,
}

/**
 * The notification permission, as a row with one button.
 *
 * Two platform facts shape this, and getting either wrong shows the user a button
 * that does nothing:
 *
 * - **Being granted the runtime permission is not the same as notifications being
 *   on.** Below API 33 there is no runtime permission at all, yet a person can
 *   still switch the app's notifications off in system settings; on 33+ they can
 *   do it after granting. `areNotificationsEnabled()` is the question that
 *   actually matters on every version, so that is what [isGranted] asks.
 * - **"Denied for good" is not directly observable.** After a refusal
 *   `shouldShowRequestPermissionRationale` stays true while the OS is still
 *   willing to prompt and goes false once it is not — but it is *also* false
 *   before the first request. The two are told apart by [hasAskedBefore], which
 *   outlives the Activity and the process, so a recreation does not turn a dead
 *   request back into a live-looking button. `PermissionRequests` sets that flag
 *   as part of launching any prompt, so every path that can ask is covered — not
 *   only the one this class starts.
 *
 * Where no request can help — pre-33, or a permanent denial — the button routes
 * to the app's notification settings instead.
 */
class NotificationPermissionController(
    private val isGranted: () -> Boolean,
    /** False below API 33, where there is no runtime permission to ask for. */
    private val canRequest: () -> Boolean,
    private val shouldShowRationale: () -> Boolean,
    private val hasAskedBefore: () -> Boolean,
    /** Goes through [PermissionRequests], which records the asking. */
    private val request: () -> Unit,
    private val openSettings: () -> Unit,
) {
    private val _access = MutableStateFlow(read())
    val access: StateFlow<NotificationAccess> = _access.asStateFlow()

    /** The single button: ask when asking can work, otherwise hand over to settings. */
    fun act() {
        val current = read()
        _access.value = current
        when (current) {
            NotificationAccess.GRANTED -> Unit
            NotificationAccess.BLOCKED -> openSettings()
            // PermissionRequests records the asking as part of launching it, so
            // a process death mid-prompt still counts as having asked.
            NotificationAccess.ASKABLE -> request()
        }
    }

    fun onResult(granted: Boolean) {
        _access.value = read(grantedOverride = granted)
    }

    /** A change made in system settings should take effect on return. */
    fun refresh() {
        _access.value = read()
    }

    private fun read(grantedOverride: Boolean? = null): NotificationAccess {
        val granted = grantedOverride?.let { it && isGranted() } ?: isGranted()
        return when {
            granted -> NotificationAccess.GRANTED
            // Nothing to ask for: only system settings can turn these back on.
            !canRequest() -> NotificationAccess.BLOCKED
            // The OS is still willing to prompt.
            shouldShowRationale() -> NotificationAccess.ASKABLE
            // No rationale and we have asked before: the prompt is spent.
            hasAskedBefore() -> NotificationAccess.BLOCKED
            else -> NotificationAccess.ASKABLE
        }
    }

    companion object {
        fun statusText(access: NotificationAccess): String = when (access) {
            NotificationAccess.GRANTED -> "已允许"
            NotificationAccess.ASKABLE -> "未允许"
            NotificationAccess.BLOCKED -> "已在系统设置中关闭"
        }

        fun buttonText(access: NotificationAccess): String = when (access) {
            NotificationAccess.GRANTED -> "通知已开启"
            NotificationAccess.ASKABLE -> "开启通知"
            NotificationAccess.BLOCKED -> "打开通知设置"
        }

        fun buttonEnabled(access: NotificationAccess): Boolean =
            access != NotificationAccess.GRANTED
    }
}
