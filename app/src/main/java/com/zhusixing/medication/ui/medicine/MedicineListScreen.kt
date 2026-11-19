package com.zhusixing.medication.ui.medicine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.zhusixing.medication.data.Medicine
import com.zhusixing.medication.ui.common.LoadingRow
import com.zhusixing.medication.ui.common.MessageCard
import com.zhusixing.medication.ui.common.StatusChip

@Composable
fun MedicineListScreen(
    state: MedicineListUiState,
    onRefresh: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Medicine) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh() }
    var pendingDelete by remember { mutableStateOf<Medicine?>(null) }

    // 删除是不可撤销的，必须二次确认，而且要点明确写清删的是哪一个。
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除药品") },
            text = { Text("将删除「${target.name}」的档案。已生成的用药计划不受影响，但之后不能再选这种药。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target)
                        pendingDelete = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("药品", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "共 ${state.items.size} 种药",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onAdd, modifier = Modifier.testTag("addMedicine")) { Text("新增") }
            }
        }

        item {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = onKeywordChange,
                label = { Text("搜索药名、规格或备注") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("medicineSearch"),
            )
        }

        state.message?.let { message ->
            item {
                MessageCard(message) {
                    TextButton(onClick = onConsumeMessage) { Text("知道了") }
                }
            }
        }
        state.error?.let { message ->
            item {
                MessageCard(message, isError = true) {
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text("重新获取") }
                }
            }
        }

        if (!state.loaded) {
            item { LoadingRow("正在获取药品列表…") }
        }

        if (state.loaded && state.error == null && state.visible.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (state.keyword.isBlank()) "还没有药品" else "没有匹配的药品",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (state.keyword.isBlank()) "先添加一种药，之后建计划时就能直接选。" else "换个关键词试试。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(state.visible, key = { it.id }) { medicine ->
            MedicineCard(
                medicine = medicine,
                deleting = state.deletingId == medicine.id,
                onClick = { onEdit(medicine.id) },
                onDelete = { pendingDelete = medicine },
            )
        }

        item { Column(Modifier.padding(bottom = 24.dp)) {} }
    }
}

@Composable
private fun MedicineCard(
    medicine: Medicine,
    deleting: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(enabled = !deleting, onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    medicine.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!medicine.isEnabled) {
                    StatusChip("已停用")
                }
            }
            val spec = listOfNotNull(
                medicine.specification?.takeIf { it.isNotBlank() },
                medicine.dosage?.takeIf { it.isNotBlank() }?.let {
                    buildString {
                        append(it)
                        medicine.unit?.takeIf { u -> u.isNotBlank() }?.let { u -> append(u) }
                    }
                },
            )
            if (spec.isNotEmpty()) {
                Text(
                    spec.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            medicine.remark?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (deleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onDelete, modifier = Modifier.testTag("deleteMedicine-${medicine.id}")) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
