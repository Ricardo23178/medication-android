package com.zhusixing.medication.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhusixing.medication.data.RemindTask
import com.zhusixing.medication.ui.common.MessageCard
import com.zhusixing.medication.ui.common.formatDose
import com.zhusixing.medication.ui.common.formatTime
import com.zhusixing.medication.ui.common.mealRelationLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    state: TodayUiState,
    userName: String,
    onRefresh: () -> Unit,
    onConfirm: (Long, String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = state.loading || state.busyTaskId != null
    // 每次进入这个 Tab（或从二级页返回）都重新拉一次：刚新建的计划要能立刻看到任务。
    LaunchedEffect(Unit) { onRefresh() }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("todayList"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("今日任务", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (userName.isNotBlank()) {
                    Text(
                        userName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("共 ${state.tasks.size} 项安排", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !working,
                    modifier = Modifier.testTag("refresh"),
                ) { Text("刷新") }
            }
        }

        if (!state.loaded) {
            item { com.zhusixing.medication.ui.common.LoadingRow("正在获取今天的安排…") }
        }

        state.error?.let { message ->
            item {
                MessageCard(message, isError = true) {
                    if (state.pendingConfirmation == null) {
                        TextButton(onClick = onRefresh, enabled = !working) { Text("重新获取") }
                    }
                }
            }
        }
        state.feedback?.let { message -> item { MessageCard(message) } }

        // 结果未知的那笔单独提示，把「重试原操作」和「刷新核对」两条路都摆出来。
        state.pendingConfirmation?.let { pending ->
            item {
                MessageCard(
                    "上次「${if (pending.action == "TAKEN") "确认已服" else "跳过本次"}」的结果待核对。" +
                        "可以重试原操作，或刷新查看服务端结果。",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry, enabled = !working) { Text("重试这次记录") }
                        TextButton(onClick = onRefresh, enabled = !working) { Text("刷新核对") }
                    }
                }
            }
        }

        if (state.loaded && !state.loading && state.error == null && state.tasks.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("今天没有安排", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "在「计划」里新增一条用药计划，就会按时间点生成任务。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(state.tasks, key = { it.id }) { task ->
            TaskCard(
                task = task,
                enabled = !working && state.pendingConfirmation == null,
                submitting = state.busyTaskId == task.id,
                onConfirm = { action -> onConfirm(task.id, action) },
            )
        }

        item {
            Text(
                "这里记录的是本人确认结果，不代表真实服药事实。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun TaskCard(task: RemindTask, enabled: Boolean, submitting: Boolean, onConfirm: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                task.medicineName?.takeIf { it.isNotBlank() } ?: "未命名药品",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                task.statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            val dose = formatDose(task.dosagePerTime)
            Text(
                if (dose == null) "剂量未填写" else "每次 $dose ${task.dosageUnit.orEmpty()}",
                style = MaterialTheme.typography.bodyLarge,
            )
            mealRelationLabel(task.mealRelation)?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("原定时间  ${formatTime(task.scheduledAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("确认截止  ${formatTime(task.expireAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (submitting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在提交记录…")
                }
            }
            if (task.canConfirm) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onConfirm("TAKEN") },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).testTag("taken-${task.id}"),
                    ) { Text("确认已服") }
                    OutlinedButton(
                        onClick = { onConfirm("SKIPPED") },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).testTag("skipped-${task.id}"),
                    ) { Text("跳过本次") }
                }
            }
        }
    }
}
