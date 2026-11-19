package com.zhusixing.medication.ui.medicine

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zhusixing.medication.ui.common.LoadingRow
import com.zhusixing.medication.ui.common.MessageCard

@Composable
fun MedicineEditScreen(
    state: MedicineEditUiState,
    onLoad: () -> Unit,
    onNameChange: (String) -> Unit,
    onSpecificationChange: (String) -> Unit,
    onDosageChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onRemarkChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoad() }
    // 保存/删除成功后由状态驱动返回，界面不自己判断请求结果。
    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除药品") },
            text = { Text("删除后不可恢复。已经生成的用药计划与记录会保留。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("medicineEditBack")) { Text("返回") }
            }
        }
        item {
            Text(
                if (state.isNew) "新增药品" else "编辑药品",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (state.loading) {
            item { LoadingRow("正在读取药品信息…") }
        }

        item {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("药品名称") },
                supportingText = { Text("必填，例如「苯磺酸氨氯地平片」") },
                singleLine = true,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().testTag("medicineName"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }
        item {
            OutlinedTextField(
                value = state.specification,
                onValueChange = onSpecificationChange,
                label = { Text("规格（可留空）") },
                supportingText = { Text("例如 5mg×7 片/盒") },
                singleLine = true,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().testTag("medicineSpec"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.dosage,
                    onValueChange = onDosageChange,
                    label = { Text("单次剂量") },
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f).testTag("medicineDosage"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = state.unit,
                    onValueChange = onUnitChange,
                    label = { Text("单位") },
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.width(120.dp).testTag("medicineUnit"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.remark,
                onValueChange = onRemarkChange,
                label = { Text("备注（可留空）") },
                supportingText = { Text("例如「冰箱冷藏」「遵医嘱」") },
                enabled = !state.saving,
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("medicineRemark"),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "停用后不影响已有计划，只是新建计划时不再推荐",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = onEnabledChange, enabled = !state.saving)
            }
        }

        state.error?.let { message -> item { MessageCard(message, isError = true) } }

        item {
            Button(
                onClick = onSave,
                enabled = !state.saving && !state.deleting && !state.loading,
                modifier = Modifier.fillMaxWidth().testTag("medicineSave"),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (state.saving) "正在保存…" else "保存")
            }
        }

        if (!state.isNew) {
            item {
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.saving && !state.deleting,
                    modifier = Modifier.fillMaxWidth().testTag("medicineDelete"),
                ) {
                    if (state.deleting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("删除这种药", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
