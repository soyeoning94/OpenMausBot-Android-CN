package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The three first-run surfaces — the behavioural port of
 * `ios/App/OnboardingViews.swift`.
 *
 * What is ported is the **timing and the routing**: the app says what it is
 * before it asks for anything, offers a way to defer that leads somewhere
 * useful, and explains notifications only once a pairing has made approvals and
 * finished work real. The gradient, the hero avatar, the glass inset and the
 * `DisclosureGroup` are not ported — Material is the right idiom here, and the
 * standing policy is that behaviour mirrors iOS while painting goes native.
 */

/** The sentences these screens are made of, named so a test can look them up. */
object OnboardingCopy {
    const val WELCOME_TITLE = "随身携带您的机器人"
    const val WELCOME_SUBTITLE =
        "在手机上打开对话、审批操作并派发新任务。"
    const val WELCOME_CONNECT = "连接我的电脑"
    const val NOT_NOW = "暂不"

    const val UNPAIRED_HOME_TITLE = "随时准备连接"
    const val UNPAIRED_HOME_BODY =
        "将此手机与 OpenMausBot 配对，即可查看对话并回复您的机器人。"
    const val UNPAIRED_HOME_HINT =
        "请在电脑上打开 OpenMausBot → 设置 → 手机。"
    const val UNPAIRED_HOME_CONNECT = "连接电脑"

    const val NOTIFICATIONS_TITLE = "随时掌握最新动态"

    /**
     * The limit is stated here rather than softened, and it is the same sentence
     * Settings shows, from [SettingsPolicy.NOTIFICATIONS_FOOTER]: someone
     * deciding whether to grant this should be told what it can and cannot do
     * before the system sheet appears, not after.
     */
    const val NOTIFICATIONS_BODY = SettingsPolicy.NOTIFICATIONS_FOOTER
    const val NOTIFICATIONS_APPROVALS = "等待您审批的请求"
    const val NOTIFICATIONS_FINISHED = "已完成的任务和重要更新"
    const val NOTIFICATIONS_ENABLE = "开启通知"
}

/**
 * First launch. Says what the app is, then offers exactly two answers — and the
 * second one is real: "Not now" leads to [UnpairedHomeScreen], not back here.
 *
 * Nothing on this screen asks the system for anything. That is the whole point
 * of it existing.
 */
@Composable
fun WelcomeScreen(onConnect: () -> Unit, onSkip: () -> Unit) {
    OnboardingLayout(
        actions = {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text(OnboardingCopy.WELCOME_CONNECT)
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(OnboardingCopy.NOT_NOW)
            }
        },
    ) {
        OnboardingHero(Icons.AutoMirrored.Filled.Send)
        Text(
            text = OnboardingCopy.WELCOME_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = OnboardingCopy.WELCOME_SUBTITLE,
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryTint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Benefit(
            icon = Icons.AutoMirrored.Filled.Send,
            title = "对话尽在掌握",
            detail = "在手机上无缝接续电脑上的同一场对话。",
        )
        Benefit(
            icon = Icons.Filled.CheckCircle,
            title = "在机器人需要时及时响应",
            detail = "无需回到电脑桌前即可审阅并批准请求。",
        )
        Benefit(
            icon = Icons.Filled.Lock,
            title = "原生隐私保护",
            detail = "由您自主选择此手机连接的受信任电脑。",
        )
    }
}

/**
 * Where "Not now" leads. A home that is useful without a pairing: it says how to
 * get one, offers the way in, and — the part iOS puts in a toolbar and this puts
 * in a header — reaches Settings, so notifications can be recovered later
 * without first entering a pairing flow the person just declined.
 */
@Composable
fun UnpairedHomeScreen(onConnect: () -> Unit, onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "OpenMausBot",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            ChromeButton(
                icon = Icons.Filled.Settings,
                contentDescription = "设置",
                onClick = onOpenSettings,
            )
        }
        HorizontalDivider()
        OnboardingLayout(
            actions = {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Text(OnboardingCopy.UNPAIRED_HOME_CONNECT)
                }
            },
        ) {
            OnboardingHero(Icons.Filled.Phone)
            Text(
                text = OnboardingCopy.UNPAIRED_HOME_TITLE,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = OnboardingCopy.UNPAIRED_HOME_BODY,
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryTint,
                textAlign = TextAlign.Center,
            )
            Text(
                text = OnboardingCopy.UNPAIRED_HOME_HINT,
                fontSize = 13.sp,
                color = secondaryTint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The only screen in this app that asks for the notification permission, and it
 * is reached only after a first pairing has committed.
 *
 * Both buttons end the step: [onEnable] fires the system prompt through the one
 * object allowed to fire it, and "Not now" declines without one. Either way the
 * step is answered once — see the marker's lifecycle in
 * [com.openmausbot.companion.core.NotificationOnboardingPolicy].
 */
@Composable
fun NotificationOnboardingScreen(
    enabling: Boolean,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardingLayout(
        actions = {
            Button(
                onClick = onEnable,
                enabled = !enabling,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (enabling) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(OnboardingCopy.NOTIFICATIONS_ENABLE)
                }
            }
            TextButton(
                onClick = onSkip,
                enabled = !enabling,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(OnboardingCopy.NOT_NOW)
            }
        },
    ) {
        OnboardingHero(Icons.Filled.Notifications)
        Text(
            text = OnboardingCopy.NOTIFICATIONS_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = OnboardingCopy.NOTIFICATIONS_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Benefit(
            icon = Icons.Filled.CheckCircle,
            title = OnboardingCopy.NOTIFICATIONS_APPROVALS,
            detail = null,
        )
        Benefit(
            icon = Icons.AutoMirrored.Filled.Send,
            title = OnboardingCopy.NOTIFICATIONS_FINISHED,
            detail = null,
        )
    }
}

/** Scrolling body, actions pinned to the bottom — the same frame for all three. */
@Composable
private fun OnboardingLayout(
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            actions()
        }
    }
}

/**
 * A tinted disc behind one glyph. iOS draws a hero mascot here; Android draws
 * the shape Material draws, which is this.
 */
@Composable
private fun OnboardingHero(icon: ImageVector) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(104.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, detail: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            detail?.let {
                Text(text = it, fontSize = 13.sp, color = secondaryTint)
            }
        }
    }
}
