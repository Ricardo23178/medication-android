package com.zhusixing.medication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhusixing.medication.data.HttpMedicationRepository
import com.zhusixing.medication.ui.MedicationApp
import com.zhusixing.medication.ui.MedicationTheme
import com.zhusixing.medication.ui.MedicationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(MedicationViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return MedicationViewModel(HttpMedicationRepository()) as T
            }
        })[MedicationViewModel::class.java]

        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            MedicationTheme {
                MedicationApp(
                    state = state,
                    onLogin = model::login,
                    onRefresh = model::refresh,
                    onConfirm = model::confirm,
                    onRetry = model::retryConfirmation,
                    onLogout = model::logout,
                )
            }
        }
    }
}
