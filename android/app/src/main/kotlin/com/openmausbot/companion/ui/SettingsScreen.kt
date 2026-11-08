package com.openmausbot.companion.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What little the phone gets to configure — the port of
 * `ios/App/SettingsView.swift`.
 *
 * Almost nothing, on purpose: Phone settings, API keys and pairing all live
 * on the computer, because losing the phone must not mean losing the ability to
 * lock it out (§13). This is a status page with an unpair button.
 *
 * It is also the screen the unpaired home reaches, which is why both actions are
 * optional. `SettingsView(onConnect:)` in `ios/App/SettingsView.swift` does the
 * same thing: someone who answered "Not now" can still get to notifications
 * without first entering the connection flow they just declined, and the parts
 * that need a pairing — routines, unpairing, the address — are simply not there
 * to be pressed.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRoutines: (() -> Unit)? = null,
    onOpenConnectedApps: (() -> Unit)? = null,
    onOpenVoice: (() -> Unit)? = null,
    /** Offered instead of the computer's details when there is no pairing. */
    onConnect: (() -> Unit)? = null,
) {
    val environment = LocalCompanion.current
    val session = environment.session
    val connection by session.connection.collectAsState()
    val connections by session.connections.collectAsState()
    val status by session.status.collectAsState()
    val notifications by environment.notifications.access.collectAsState()
    val activityDetail by environment.chatPreferences.activityDetail.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val haptics = rememberHaptics()

    var editingAddress by remember { mutableStateOf(false) }
    var addressText by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    var showingFullAddress by remember { mutableStateOf(false) }
    var addressCopied by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }
    var confirmingUnpair by remember { mutableStateOf(false) }
    var pendingComputerRemoval by remember { mutableStateOf<Connection?>(null) }
    var choosingActivity by remember { mutableStateOf(false) }
    var editingQuickReplies by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderBackButton(onBack)
            Text("设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection("电脑") {
                val bound = connection
                if (bound != null) {
                    val address = SettingsPolicy.addressText(bound)
                    SettingsRow("名称", bound.name)
                    AddressRow(
                        address = address,
                        expanded = showingFullAddress,
                        copied = addressCopied,
                        onToggle = { showingFullAddress = !showingFullAddress },
                        onCopy = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText(ADDRESS_CLIP_LABEL, address)),
                                )
                                addressCopied = true
                                delay(COPIED_LABEL_MILLIS)
                                addressCopied = false
                            }
                        },
                    )
                    // The stored address can simply go stale. Editing it here
                    // keeps the pairing and its token (§7).
                    SettingsButton("编辑地址") {
                        addressText = address
                        addressError = null
                        editingAddress = true
                    }
                } else if (onConnect != null) {
                    SettingsButton("连接电脑", onClick = onConnect)
                }
                SettingsRow("连接状态", SettingsPolicy.statusText(status))
                if (bound != null) {
                    SettingsButton("连接另一台电脑") {
                        haptics.play(TactileAction.CONNECT_ANOTHER_COMPUTER)
                        session.beginPairing()
                    }
                }
            }

            val otherComputers = connections.filter { it.id != connection?.id }
            if (otherComputers.isNotEmpty()) {
                SettingsSection("其他电脑") {
                    otherComputers.forEach { computer ->
                        SettingsButton("使用 ${computer.name}") {
                            haptics.play(TactileAction.SWITCH_COMPUTER)
                            session.switchComputer(computer.id)
                        }
                        SettingsButton("移除 ${computer.name}", destructive = true) {
                            pendingComputerRemoval = computer
                        }
                    }
                    Footnote("每台电脑单独配对。同一时间只有选中的电脑处于活动状态。")
                }
            }

            if (connection != null) {
                SettingsSection("故障排除") {
                    Footnote(troubleshootingText(status))
                    SettingsButton(
                        text = "尝试重新连接",
                        enabled = !reconnecting,
                        trailing = {
                            if (reconnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                    ) {
                        scope.launch {
                            reconnecting = true
                            // Waits until the stream leaves connecting (or 10s),
                            // so the spinner means what it appears to mean.
                            session.refresh()
                            reconnecting = false
                        }
                    }
                }
            }

            SettingsSection("通知") {
                SettingsRow(
                    "状态",
                    NotificationPermissionController.statusText(notifications),
                )
                SettingsButton(
                    text = NotificationPermissionController.buttonText(notifications),
                    enabled = NotificationPermissionController.buttonEnabled(notifications),
                    onClick = environment.notifications::act,
                )
                Footnote(SettingsPolicy.NOTIFICATIONS_FOOTER)
            }

            SettingsSection("后台连接") {
                val alwaysOnEnabled by environment.alwaysOnEnabled.collectAsState()
                SettingsRow("状态", if (alwaysOnEnabled) "始终保持" else "仅在打开应用时")
                SettingsButton(
                    text = if (alwaysOnEnabled) "关闭" else "开启",
                    onClick = environment.onToggleAlwaysOn,
                )
                Footnote(
                    if (alwaysOnEnabled) {
                        "开启此选项后，OpenMausBot 会保留一条常驻通知，以便即使应用完全关闭，" +
                            "定时提醒和例程结果也能及时送达。"
                    } else {
                        "仅在应用打开或刚切入后台时才会收到通知。" +
                            "如果你依赖定时例程稍后提醒你，请开启此选项——它会添加一条低优先级常驻通知，并略微增加耗电。"
                    },
                )
            }

            SettingsSection("聊天") {
                SettingsRow("活动详情", activityDetail.label)
                SettingsButton("更改活动详情级别") { choosingActivity = true }
                SettingsButton("快捷回复") { editingQuickReplies = true }
                Footnote(activityDetail.caption)
            }

            // Routine schedules live on the computer this phone is bound to.
            // With no binding there is nothing to schedule against, so the row
            // is absent rather than present and dead.
            if (onOpenRoutines != null || onOpenConnectedApps != null || onOpenVoice != null) {
                SettingsSection("工作区") {
                    onOpenRoutines?.let { openRoutines ->
                        SettingsButton(
                            text = "会话与例程",
                            icon = R.drawable.ic_schedule,
                            onClick = openRoutines,
                        )
                    }
                    onOpenConnectedApps?.let { openConnectedApps ->
                        SettingsButton(
                            text = "已连接应用",
                            onClick = openConnectedApps,
                        )
                    }
                    onOpenVoice?.let { openVoice ->
                        SettingsButton(
                            text = "语音",
                            icon = R.drawable.ic_phone,
                            onClick = openVoice,
                        )
                    }
                    Footnote(SettingsPolicy.WORKSPACE_FOOTER)
                }
            }

            if (connection != null) {
                SettingsSection(null) {
                    SettingsButton(
                        text = if (connections.size > 1) "移除此电脑" else "取消配对此手机",
                        destructive = true,
                    ) { confirmingUnpair = true }
                    Footnote(SettingsPolicy.UNPAIR_FOOTER)
                }
            }

            SettingsSection("不在此处管理") {
                Footnote(SettingsPolicy.NOT_HERE)
            }
        }
    }

    if (editingAddress) {
        AlertDialog(
            onDismissRequest = { editingAddress = false },
            title = { Text("编辑地址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(SettingsPolicy.EDIT_ADDRESS_MESSAGE, fontSize = 14.sp)
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = {
                            addressText = it
                            addressError = null
                        },
                        placeholder = { Text("https://mac.example 或 192.168.1.42:8810") },
                        singleLine = true,
                        isError = addressError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    addressError?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Session re-parses, re-dials and persists; the walk and
                        // promote semantics stay its job. The form only refuses
                        // what it can already tell is not an address.
                        if (session.updateAddress(addressText)) {
                            editingAddress = false
                        } else {
                            addressError = AddressEdit.INVALID
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingAddress = false }) { Text("取消") }
            },
        )
    }

    if (confirmingUnpair) {
        AlertDialog(
            onDismissRequest = { confirmingUnpair = false },
            title = { Text(if (connections.size > 1) "移除 ${connection?.name}？" else SettingsPolicy.UNPAIR_CONFIRM_TITLE) },
            text = {
                Text(
                    if (connections.size > 1) {
                        "这仅会从本手机移除已保存的连接。其他已保存的电脑仍可使用。"
                    } else {
                        SettingsPolicy.UNPAIR_CONFIRM_MESSAGE
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingUnpair = false
                        // Local token and connection only. Revoking the device
                        // itself is Settings → Phone on the computer (§6).
                        session.signOut()
                    },
                ) {
                    // With another computer saved this removes one of them; the
                    // phone stays paired, so "Unpair" would be the wrong promise.
                    Text(
                        text = if (connections.size > 1) "移除" else "取消配对",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUnpair = false }) { Text("取消") }
            },
        )
    }

    pendingComputerRemoval?.let { computer ->
        AlertDialog(
            onDismissRequest = { pendingComputerRemoval = null },
            title = { Text("移除 ${computer.name}？") },
            text = { Text("这仅会从本手机移除已保存的连接。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingComputerRemoval = null
                        session.forgetConnection(computer.id)
                    },
                ) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingComputerRemoval = null }) { Text("取消") }
            },
        )
    }

    if (choosingActivity) {
        AlertDialog(
            onDismissRequest = { choosingActivity = false },
            title = { Text("活动详情") },
            text = {
                // iOS draws a Picker (SettingsView.swift:67-78), which marks the
                // choice already in force; three plain buttons do not.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActivityDetail.entries.forEach { detail ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MIN_TOUCH_TARGET)
                                .selectable(
                                    selected = detail == activityDetail,
                                    role = Role.RadioButton,
                                    onClick = {
                                        environment.chatPreferences.setActivityDetail(detail)
                                        choosingActivity = false
                                    },
                                )
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = detail == activityDetail, onClick = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(detail.label, textAlign = TextAlign.Start)
                                Text(detail.caption, fontSize = 12.sp, color = secondaryTint)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingActivity = false }) { Text("取消") } },
        )
    }

    if (editingQuickReplies) {
        QuickRepliesEditor(
            preferences = environment.chatPreferences,
            onDismiss = { editingQuickReplies = false },
        )
    }
}

@Composable
private fun SettingsSection(title: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        title?.let {
            Text(
                text = it.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTint,
            )
        }
        HorizontalDivider()
        content()
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = secondaryTint)
        Text(
            text = value,
            fontSize = 15.sp,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
    }
}

/**
 * The address, short enough to read at a glance and long enough to copy — the
 * port of the connection details in `ios/App/SettingsView.swift:346-371`.
 */
@Composable
private fun AddressRow(
    address: String,
    expanded: Boolean,
    copied: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("地址", fontSize = 15.sp, color = secondaryTint)
        if (expanded) {
            // Selectable, because the reason to show it in full is to take it away.
            SelectionContainer {
                Text(
                    text = address,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = secondaryTint,
                )
            }
        } else {
            Text(
                text = shortenedAddress(address),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = secondaryTint,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onToggle) {
                Text(if (expanded) "隐藏完整地址" else "显示完整地址")
            }
            TextButton(onClick = onCopy) {
                Text(if (copied) "已复制" else "复制")
            }
        }
    }
}

@Composable
private fun SettingsButton(
    text: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: Int? = null,
    /** Drawn at the end of the row — a progress indicator while one is running. */
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                color = tint,
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text = text, fontSize = 13.sp, color = secondaryTint)
}

private const val ADDRESS_CLIP_LABEL = "OpenMausMobile 电脑地址"

/** Long enough for "Copied" to be read, short enough not to linger (iOS `:363-367`). */
private const val COPIED_LABEL_MILLIS = 2_000L

/**
 * What the Troubleshooting section says before offering to reconnect — the port
 * of `ConnectionSecurityView.troubleshootingText` (`ios/App/SettingsView.swift:445-458`).
 */
internal fun troubleshootingText(status: Session.Status): String = when (status) {
    Session.Status.Live -> "此电脑已连接且响应正常。"
    Session.Status.Connecting -> "OpenMausBot 正在自动尝试已保存的连接。"
    Session.Status.Unauthorized -> "此手机已从电脑移除，请重新配对以恢复连接。"
    Session.Status.Unpaired -> "此手机尚未与电脑配对。"
    is Session.Status.Offline -> status.message
}

/**
 * The address with its middle taken out, so a long tailnet name still shows the
 * host and the port it ends in — `shortened` in `ios/App/SettingsView.swift:460-464`.
 */
internal fun shortenedAddress(address: String): String {
    if (address.length <= 14) return address
    val leading = minOf(20, maxOf(8, address.length - 8))
    return address.take(leading) + "…" + address.takeLast(6)
}
