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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zhusixing.medication.ui.common.MessageCard

@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("registerBack")) { Text("返回") }
            }
        }
        item {
            Text("注册账号", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "注册成功后直接登录，无需再输一次密码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        item {
            OutlinedTextField(
                value = state.account,
                onValueChange = onAccountChange,
                label = { Text("账号") },
                supportingText = { Text("字母、数字、下划线，4-32 位") },
                singleLine = true,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().testTag("registerAccount"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }
        item {
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("密码") },
                supportingText = { Text("6-32 位") },
                singleLine = true,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().testTag("registerPassword"),
                visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = onTogglePassword) {
                        Text(if (state.showPassword) "隐藏" else "显示")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
        }
        item {
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = onConfirmChange,
                label = { Text("确认密码") },
                singleLine = true,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().testTag("registerConfirm"),
                visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
        }
        item {
            OutlinedTextField(
                value = state.nickname,
                onValueChange = onNicknameChange,
                label = { Text("昵称") },
                supportingText = { Text("家属端看到的就是这个名字") },
                singleLine = true,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().testTag("registerNickname"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }
        item {
            OutlinedTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                label = { Text("手机号（可留空）") },
                singleLine = true,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().testTag("registerPhone"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            )
        }

        state.fieldError?.let { message -> item { MessageCard(message, isError = true) } }
        state.error?.let { message -> item { MessageCard(message, isError = true) } }

        item {
            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().testTag("registerSubmit"),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (state.loading) "正在注册…" else "注册并登录")
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
