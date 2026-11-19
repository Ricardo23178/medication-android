package com.zhusixing.medication.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 「我的」。既是个人信息的展示位，也是二级页的入口集合。
 *
 * 退出登录放在最底部并做成描边按钮：这是不可逆操作，不该和日常功能长一个样，
 * 也不该放在最容易被误触的位置。
 */
@Composable
fun ProfileScreen(
    displayName: String,
    account: String,
    roleLabel: String?,
    onOpenRecords: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenFamilyAbnormal: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp)) {
                Text(displayName.ifBlank { "未设置昵称" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append("账号 ")
                        append(account.ifBlank { "—" })
                        roleLabel?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    MenuRow("消息中心", "家属提醒与异常通知", onOpenNotifications, "menuNotifications")
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    MenuRow("用药记录", "每次确认的历史", onOpenRecords, "menuRecords")
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    MenuRow("用药统计", "近 30 天完成率与日历", onOpenStatistics, "menuStatistics")
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    MenuRow("家属异常", "漏服与长时间未确认", onOpenFamilyAbnormal, "menuAbnormal")
                }
            }
        }

        item {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().testTag("logout"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) { Text("退出登录") }
        }

        item {
            Text(
                "退出后本机将清除登录凭证，下次进入需要重新输入账号密码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
    }
}
