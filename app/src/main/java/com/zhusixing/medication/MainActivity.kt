package com.zhusixing.medication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhusixing.medication.ui.AppRoot
import com.zhusixing.medication.ui.theme.MedicationTheme

/**
 * 单 Activity。
 *
 * 页面全部由 Compose 在 [AppRoot] 里按路由切换，这里只负责三件事：
 * 打开边到边、拿到进程级容器、套上主题。业务与导航都不在这一层。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MedicationApplication).container

        setContent {
            MedicationTheme {
                AppRoot(container)
            }
        }
    }
}
