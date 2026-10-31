package com.zhusixing.medication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhusixing.medication.data.MedicationRepository
import com.zhusixing.medication.data.RemindTask
import com.zhusixing.medication.data.RepositoryException
import com.zhusixing.medication.data.Session
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingConfirmation(val taskId: Long, val action: String, val requestId: String)

data class MedicationUiState(
    val session: Session? = null,
    val tasks: List<RemindTask> = emptyList(),
    val loading: Boolean = false,
    val busyTaskId: Long? = null,
    val error: String? = null,
    val feedback: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
)

class MedicationViewModel(private val repository: MedicationRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(MedicationUiState())
    val state: StateFlow<MedicationUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    // 即使网络请求无法及时取消，旧会话的响应也不能覆盖新会话。
    private var sessionVersion = 0L

    fun login(baseUrl: String, account: String, password: String) {
        if (state.value.loading || state.value.session != null) return
        if (baseUrl.isBlank() || account.isBlank() || password.isBlank()) {
            mutableState.value = MedicationUiState(error = "请填写后端地址、账号和密码")
            return
        }
        val version = ++sessionVersion
        mutableState.value = MedicationUiState(loading = true)
        operation = viewModelScope.launch {
            try {
                val session = repository.login(baseUrl.trim(), account.trim(), password)
                if (version != sessionVersion) return@launch
                mutableState.value = state.value.copy(session = session)
                val tasks = repository.today(session)
                if (version == sessionVersion) {
                    mutableState.value = state.value.copy(tasks = tasks, loading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showFailure(error, version)
            }
        }
    }

    fun refresh() {
        val current = state.value
        val session = current.session ?: return
        if (current.loading || current.busyTaskId != null) return
        val version = sessionVersion
        mutableState.value = current.copy(loading = true, error = null, feedback = null)
        operation = viewModelScope.launch {
            try {
                val tasks = repository.today(session)
                if (version != sessionVersion) return@launch
                val pending = current.pendingConfirmation
                val task = tasks.find { it.id == pending?.taskId }
                val unresolved = pending?.takeIf {
                    task != null && task.confirmed != true &&
                        task.status !in setOf("CONFIRMED", "EXPIRED", "CANCELLED")
                }
                mutableState.value = state.value.copy(
                    tasks = tasks,
                    loading = false,
                    pendingConfirmation = unresolved,
                    feedback = if (pending != null && unresolved == null) {
                        task?.statusLabel ?: "列表已刷新，请核对任务状态"
                    } else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showFailure(error, version, current.pendingConfirmation)
            }
        }
    }

    fun confirm(taskId: Long, action: String) {
        val current = state.value
        val session = current.session ?: return
        if (current.loading || current.busyTaskId != null || current.pendingConfirmation != null) return
        if (action !in setOf("TAKEN", "SKIPPED")) return
        val task = current.tasks.find { it.id == taskId }
        if (task?.canConfirm != true) return
        sendConfirmation(session, PendingConfirmation(taskId, action, UUID.randomUUID().toString()))
    }

    fun retryConfirmation() {
        val current = state.value
        val session = current.session ?: return
        val pending = current.pendingConfirmation ?: return
        if (current.loading || current.busyTaskId != null) return
        sendConfirmation(session, pending)
    }

    private fun sendConfirmation(session: Session, pending: PendingConfirmation) {
        val version = sessionVersion
        // 在启动协程前上锁，连续两次点击也只能发出一次请求。
        mutableState.value = state.value.copy(busyTaskId = pending.taskId, error = null, feedback = null)
        operation = viewModelScope.launch {
            try {
                val returned = repository.confirm(session, pending.taskId, pending.action, pending.requestId)
                if (version != sessionVersion) return@launch
                mutableState.value = state.value.copy(
                    tasks = state.value.tasks.map { if (it.id == returned.id) returned else it },
                    busyTaskId = null,
                    pendingConfirmation = null,
                    feedback = returned.statusLabel,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 网络不确定时保留原意图，手动重试必须使用同一个请求ID。
                val uncertain = error !is RepositoryException || error.outcomeUnknown
                showFailure(error, version, if (uncertain) pending else null)
            }
        }
    }

    private fun showFailure(error: Exception, version: Long, pending: PendingConfirmation? = null) {
        if (version != sessionVersion) return
        val message = if (error is RepositoryException) {
            error.message ?: "请求未完成，请稍后重试"
        } else "请求未完成，请稍后重试"
        if (error is RepositoryException && error.sessionExpired) {
            sessionVersion++
            mutableState.value = MedicationUiState(error = message)
        } else {
            mutableState.value = state.value.copy(
                loading = false,
                busyTaskId = null,
                error = message,
                feedback = null,
                pendingConfirmation = pending,
            )
        }
    }

    fun logout() {
        sessionVersion++
        operation?.cancel()
        operation = null
        mutableState.value = MedicationUiState()
    }
}
