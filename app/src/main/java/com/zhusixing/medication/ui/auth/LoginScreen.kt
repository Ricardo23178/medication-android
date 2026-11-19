package com.zhusixing.medication.ui.auth

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zhusixing.medication.ui.common.MessageCard

@Composable
fun LoginScreen(
    state: LoginUiState,
    onServerChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onTestConnection: () -> Unit,
    onLogin: () -> Unit,
    onGoRegister: () -> Unit,
    notice: String?,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        if (state.canSubmit) {
            focusManager.clearFocus()
            onLogin()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Text("安心用药", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "查看今天的服药安排，确认每一次",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        // 被动退出的原因（令牌过期等）要单独提示，用户才知道为什么回到这里。
        notice?.let { message -> item { MessageCard(message, isError = true) } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("登录账号", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = state.server,
                        onValueChange = onServerChange,
                        label = { Text("服务器地址") },
                        supportingText = { Text("模拟器用 10.0.2.2；手机填电脑的局域网地址") },
                        singleLine = true,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth().testTag("serverAddress"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onTestConnection,
                            enabled = !state.loading && !state.testing && state.server.isNotBlank(),
                            modifier = Modifier.testTag("testConnection"),
                        ) {
                            if (state.testing) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在连接…")
                            } else {
                                Text("测试连接")
                            }
                        }
                        state.probe?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.account,
                        onValueChange = onAccountChange,
                        label = { Text("账号") },
                        singleLine = true,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth().testTag("account"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = { Text("密码") },
                        singleLine = true,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth().testTag("password"),
                        visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = onTogglePassword) {
                                Text(if (state.showPassword) "隐藏" else "显示")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )

                    Button(
                        onClick = { submit() },
                        enabled = state.canSubmit,
                        modifier = Modifier.fillMaxWidth().testTag("login"),
                    ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("还没有账号？", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onGoRegister, modifier = Modifier.testTag("goRegister")) {
                    Text("注册一个")
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
