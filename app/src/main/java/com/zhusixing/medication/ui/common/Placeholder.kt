package com.zhusixing.medication.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 尚未实现的模块占位。
 *
 * 存在的意义是让导航骨架先跑通：每个 Tab、每个二级页都能真正跳进去、按返回能出来，
 * 而不是点上去没反应。页面上直接写清「这里将来做什么、需要调哪个接口」，
 * 后面补 UI 时不用再回去翻后端代码。
 *
 * 这个文件在 14 个页面全部落地后应当被删掉——留着它就是留着一堆假页面。
 */
@Composable
fun ModulePlaceholder(
    title: String,
    description: String,
    apiHint: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "待接入：$apiHint",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            "页面骨架已接入导航，补齐 UI 与 ViewModel 后即可替换本占位。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
