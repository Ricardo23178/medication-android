package com.zhusixing.medication.ui.medicine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhusixing.medication.data.MedicineRepository
import com.zhusixing.medication.data.MedicineRequest
import com.zhusixing.medication.data.RepositoryException
import com.zhusixing.medication.ui.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedicineEditUiState(
    val isNew: Boolean,
    val name: String = "",
    val specification: String = "",
    val dosage: String = "",
    val unit: String = "",
    val remark: String = "",
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val loaded: Boolean = true,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val error: String? = null,
    /** 保存或删除成功。界面看到它就返回上一页，不再自己判断。 */
    val finished: Boolean = false,
)

class MedicineEditViewModel(
    private val medicineId: Long?,
    private val repository: MedicineRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        MedicineEditUiState(isNew = medicineId == null, loaded = medicineId == null),
    )
    val state: StateFlow<MedicineEditUiState> = mutableState.asStateFlow()

    private var loadStarted = false

    /** 编辑模式进入时拉一次详情。重复进入同一个页面不会重复请求。 */
    fun load() {
        if (medicineId == null || loadStarted) return
        loadStarted = true
        val session = sessions.session ?: return
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val medicine = repository.detail(session, medicineId)
                mutableState.value = mutableState.value.copy(
                    name = medicine.name,
                    specification = medicine.specification.orEmpty(),
                    dosage = medicine.dosage.orEmpty(),
                    unit = medicine.unit.orEmpty(),
                    remark = medicine.remark.orEmpty(),
                    enabled = medicine.isEnabled,
                    loading = false,
                    loaded = true,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                loadStarted = false
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    loaded = true,
                    error = failureMessage(error),
                )
            }
        }
    }

    fun updateName(value: String) = update { copy(name = value) }
    fun updateSpecification(value: String) = update { copy(specification = value) }
    fun updateDosage(value: String) = update { copy(dosage = value) }
    fun updateUnit(value: String) = update { copy(unit = value) }
    fun updateRemark(value: String) = update { copy(remark = value) }
    fun updateEnabled(value: Boolean) = update { copy(enabled = value) }

    private fun update(block: MedicineEditUiState.() -> MedicineEditUiState) {
        mutableState.value = mutableState.value.block().copy(error = null)
    }

    fun save() {
        val current = mutableState.value
        if (current.saving || current.deleting) return
        if (current.name.isBlank()) {
            mutableState.value = current.copy(error = "请填写药品名称")
            return
        }
        val session = sessions.session ?: return
        val request = MedicineRequest(
            name = current.name.trim(),
            specification = current.specification.blankToNull(),
            dosage = current.dosage.blankToNull(),
            unit = current.unit.blankToNull(),
            remark = current.remark.blankToNull(),
            enabled = if (current.enabled) 1 else 0,
        )
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                if (medicineId == null) {
                    repository.create(session, request)
                } else {
                    repository.update(session, medicineId, request)
                }
                mutableState.value = mutableState.value.copy(saving = false, finished = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(saving = false, error = failureMessage(error))
            }
        }
    }

    fun delete() {
        val id = medicineId ?: return
        val current = mutableState.value
        if (current.deleting || current.saving) return
        val session = sessions.session ?: return
        mutableState.value = current.copy(deleting = true, error = null)
        viewModelScope.launch {
            try {
                repository.delete(session, id)
                mutableState.value = mutableState.value.copy(deleting = false, finished = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(deleting = false, error = failureMessage(error))
            }
        }
    }

    private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }

    private fun failureMessage(error: Exception): String {
        if (error is RepositoryException && error.sessionExpired) {
            viewModelScope.launch { sessions.expire(error.message ?: "登录已失效，请重新登录") }
        }
        return (error as? RepositoryException)?.message ?: "请求未完成，请稍后重试"
    }
}
