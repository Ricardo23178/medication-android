package com.zhusixing.medication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zhusixing.medication.data.RemindTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MedicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF147D83),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9F1EF),
            onPrimaryContainer = Color(0xFF12484C),
            background = Color(0xFFF3F7F8),
            surface = Color.White,
            surfaceVariant = Color(0xFFE7EFEF),
            onSurfaceVariant = Color(0xFF52686B),
        ),
        content = content,
    )
}

@Composable
fun MedicationApp(
    state: MedicationUiState,
    onLogin: (String, String, String) -> Unit,
    onRefresh: () -> Unit,
    onConfirm: (Long, String) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        if (state.session == null) {
            LoginScreen(state, onLogin, Modifier.padding(insets))
        } else {
            TodayScreen(state, onRefresh, onConfirm, onRetry, onLogout, Modifier.padding(insets))
        }
    }
}

@Composable
private fun LoginScreen(
    state: MedicationUiState,
    onLogin: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var server by rememberSaveable { mutableStateOf("http://10.0.2.2:8080/") }
    var account by rememberSaveable { mutableStateOf("") }
    // 密码只留在本次登录页面内存，不写入可恢复状态。
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val canLogin = !state.loading && server.isNotBlank() && account.isNotBlank() && password.isNotBlank()
    val submit = {
        if (canLogin) {
            focusManager.clearFocus()
            onLogin(server, account, password)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Text("安心用药", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("查看安排，记录每次确认", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("登录账号", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = server, onValueChange = { server = it }, label = { Text("服务器地址") },
                        supportingText = { Text("模拟器默认地址；手机使用电脑的局域网地址") },
                        singleLine = true, enabled = !state.loading, modifier = Modifier.fillMaxWidth().testTag("serverAddress"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = account, onValueChange = { account = it }, label = { Text("账号") },
                        singleLine = true, enabled = !state.loading, modifier = Modifier.fillMaxWidth().testTag("account"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("密码") },
                        singleLine = true, enabled = !state.loading, modifier = Modifier.fillMaxWidth().testTag("password"),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "隐藏" else "显示")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    Button(onClick = { submit() }, enabled = canLogin, modifier = Modifier.fillMaxWidth().testTag("login")) {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(if (state.loading) "正在登录…" else "登录")
                    }
                }
            }
        }
        state.error?.let { message -> item { MessageCard(message, isError = true) } }
        item {
            Text("使用现有系统账号登录；请先启动后端服务。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TodayScreen(
    state: MedicationUiState,
    onRefresh: () -> Unit,
    onConfirm: (Long, String) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = state.loading || state.busyTaskId != null
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("todayList"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今日任务", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(state.session?.user?.displayName.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
                TextButton(onClick = onLogout, modifier = Modifier.testTag("logout")) { Text("退出登录") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${state.tasks.size} 项安排", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onRefresh, enabled = !working, modifier = Modifier.testTag("refresh")) { Text("刷新任务") }
            }
        }
        if (state.loading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("正在获取任务…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.error?.let { message ->
            item {
                MessageCard(message, isError = true) {
                    if (state.pendingConfirmation == null) {
                        TextButton(onClick = onRefresh, enabled = !working) { Text("重新获取任务") }
                    }
                }
            }
        }
        state.feedback?.let { message -> item { MessageCard(message) } }
        state.pendingConfirmation?.let { pending ->
            item {
                MessageCard("上次“${if (pending.action == "TAKEN") "确认已服" else "跳过本次"}”的结果待核对。可重试原操作，或刷新查看结果。") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry, enabled = !working) { Text("重试这次记录") }
                        TextButton(onClick = onRefresh, enabled = !working) { Text("刷新核对") }
                    }
                }
            }
        }
        if (!state.loading && state.error == null && state.tasks.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("暂无任务", style = MaterialTheme.typography.titleLarge)
                        Text("可在现有系统中建立用药计划，再刷新查看。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("这里记录的是用户确认结果，不代表真实服药事实。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun TaskCard(task: RemindTask, enabled: Boolean, submitting: Boolean, onConfirm: (String) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(task.medicineName?.takeIf { it.isNotBlank() } ?: "未命名药品",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(task.statusLabel, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp))
            val dose = task.dosagePerTime?.stripTrailingZeros()?.toPlainString()
            Text(if (dose == null) "剂量未填写" else "每次 $dose ${task.dosageUnit.orEmpty()}",
                style = MaterialTheme.typography.bodyLarge)
            mealLabel(task.mealRelation)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    Button(onClick = { onConfirm("TAKEN") }, enabled = enabled, modifier = Modifier.weight(1f).testTag("taken-${task.id}")) {
                        Text("确认已服")
                    }
                    OutlinedButton(onClick = { onConfirm("SKIPPED") }, enabled = enabled, modifier = Modifier.weight(1f).testTag("skipped-${task.id}")) {
                        Text("跳过本次")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean = false, actions: @Composable (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(
        containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    )) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            actions?.invoke()
        }
    }
}

private fun mealLabel(value: Int?): String? = when (value) {
    0 -> "不限餐前餐后"
    1 -> "餐前"
    2 -> "餐后"
    3 -> "晨起空腹"
    4 -> "睡前"
    else -> null
}

private fun formatTime(value: String?): String {
    if (value.isNullOrBlank()) return "未提供"
    return runCatching {
        LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
    }.getOrElse { value.replace('T', ' ') }
}
