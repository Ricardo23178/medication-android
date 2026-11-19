package com.zhusixing.medication.ui.medicine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhusixing.medication.data.Medicine
import com.zhusixing.medication.data.MedicineRepository
import com.zhusixing.medication.data.RepositoryException
import com.zhusixing.medication.ui.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedicineListUiState(
    val items: List<Medicine> = emptyList(),
    val keyword: String = "",
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val deletingId: Long? = null,
    val error: String? = null,
    val message: String? = null,
) {
    /** 本地过滤，不额外打接口：药品档案是家庭级数据量，几十条以内，一次全量拿回来更省事。 */
    val visible: List<Medicine>
        get() {
            if (keyword.isBlank()) return items
            val key = keyword.trim()
            return items.filter {
                it.name.contains(key, ignoreCase = true) ||
                    it.specification?.contains(key, ignoreCase = true) == true ||
                    it.remark?.contains(key, ignoreCase = true) == true
            }
        }
}

class MedicineListViewModel(
    private val repository: MedicineRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow(MedicineListUiState())
    val state: StateFlow<MedicineListUiState> = mutableState.asStateFlow()

    fun refresh() {
        val session = sessions.session ?: return
        val current = mutableState.value
        if (current.loading || current.deletingId != null) return
        mutableState.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val items = repository.list(session)
                mutableState.value = mutableState.value.copy(items = items, loading = false, loaded = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleFailure(error)
            }
        }
    }

    fun updateKeyword(value: String) {
        mutableState.value = mutableState.value.copy(keyword = value)
    }

    fun delete(medicine: Medicine) {
        val session = sessions.session ?: return
        if (mutableState.value.deletingId != null) return
        mutableState.value = mutableState.value.copy(deletingId = medicine.id, error = null, message = null)
        viewModelScope.launch {
            try {
                repository.delete(session, medicine.id)
                // 不整表重拉，只把删掉的那条移出列表：少一次往返，也不会打乱滚动位置。
                mutableState.value = mutableState.value.copy(
                    items = mutableState.value.items.filterNot { it.id == medicine.id },
                    deletingId = null,
                    message = "已删除「${medicine.name}」",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(deletingId = null, error = failureMessage(error))
            }
        }
    }

    fun consumeMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    private fun handleFailure(error: Exception) {
        if (error is RepositoryException && error.sessionExpired) {
            viewModelScope.launch { sessions.expire(error.message ?: "登录已失效，请重新登录") }
            return
        }
        mutableState.value = mutableState.value.copy(loading = false, loaded = true, error = failureMessage(error))
    }

    private fun failureMessage(error: Exception): String =
        (error as? RepositoryException)?.message ?: "请求未完成，请稍后重试"
}
