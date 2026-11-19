package com.zhusixing.medication.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 五个一级页面。顺序与鸿蒙端的 Tabs 保持一致，两个端之间来回看不会错位。 */
enum class HomeTab(val label: String) {
    Today("今日"),
    Medicine("药品"),
    Plan("计划"),
    Family("家属"),
    Profile("我的"),
}

/**
 * 主框架：底部五个 Tab + 内容区。
 *
 * 内容区通过 [content] 注入而不是在这里直接写五个页面，
 * 这样这个文件只负责导航结构，页面各自的 ViewModel 与数据加载不跟它耦合。
 */
@Composable
fun HomeShell(
    current: HomeTab,
    onSelect: (HomeTab) -> Unit,
    content: @Composable (HomeTab) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomTabs(current, onSelect) },
    ) { insets ->
        Box(Modifier.padding(insets)) {
            content(current)
        }
    }
}

/**
 * 手写的底部 Tab。
 *
 * 没用 Material3 的 NavigationBar：它要求每个 Tab 有图标，而图标集要么缺少「药品」「家属」
 * 这类语义图标，要么得引入体积不小的 extended 包。对适老化场景来说，一个够大的汉字
 * 比一个抽象图标更容易认——老人不需要学习图标的含义。
 *
 * 选中态用「顶部短横线 + 加粗 + 主色」三重提示，不只靠颜色，色觉障碍用户也能分辨。
 */
@Composable
private fun BottomTabs(current: HomeTab, onSelect: (HomeTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTab.entries.forEach { tab ->
                val selected = tab == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(3.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
