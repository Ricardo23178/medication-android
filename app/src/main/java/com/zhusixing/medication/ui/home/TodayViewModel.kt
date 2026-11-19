package com.zhusixing.medication.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhusixing.medication.data.MedicationRepository
import com.zhusixing.medication.data.RemindTask
import com.zhusixing.medication.data.RepositoryException
import com.zhusixing.medication.data.Session
import com.zhusixing.medication.ui.session.SessionManager
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「上次提交结果未知」的待核对意图。
 *
 * 请求超时但服务端可能已经写入时，必须保留原始的 requestId 让用户重试：
 * 换一个 id 重发就不再是「同一笔」，幂等会失效，同一颗药会被记两次。
 */
data class PendingConfirmation(val taskId: Long, val action: String, val requestId: String)

data class TodayUiState(
    val tasks: List<RemindTask> = emptyList(),
    val loading: Boolean = false,
    val busyTaskId: Long? = null,
    val error: String? = null,
    val feedback: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    /** 首次加载完成前不显示「暂无任务」，否则会闪一下空状态。 */
    val loaded: Boolean = false,
)

class TodayViewModel(
    private val repository: MedicationRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = mutableState.asStateFlow()

    /*
     * 不在 init 里加载：切回这个 Tab、或者从二级页返回时，界面会重新进入组合，
     * 那时应当再拉一次最新数据（刚建的计划要能立刻看到任务）。
     * 由界面用 LaunchedEffect 触发 refresh，ViewModel 只管状态。
     */

    fun refresh() {
        val session = sessions.session ?: return
        val current = mutableState.value
        // 正在刷新或正在提交时不再叠加请求，切 Tab 回来重复触发也不会打两次接口。
        if (current.loading || current.busyTaskId != null) return
        mutableState.value = current.copy(loading = true, error = null, feedback = null)
        viewModelScope.launch {
            try {
                val tasks = repository.today(session)
                val pending = current.pendingConfirmation
                // 核对上次未决的那笔：任务已结束或已确认，就可以把待核对提示撤掉。
                val task = tasks.find { it.id == pending?.taskId }
                val unresolved = pending?.takeIf {
                    task != null && task.confirmed != true &&
                        task.status !in setOf("CONFIRMED", "EXPIRED", "CANCELLED")
                }
                mutableState.value = mutableState.value.copy(
                    tasks = tasks,
                    loading = false,
                    loaded = true,
                    pendingConfirmation = unresolved,
                    feedback = if (pending != null && unresolved == null) {
                        task?.statusLabel ?: "列表已刷新，请核对任务状态"
                    } else {
                        null
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleFailure(error, current.pendingConfirmation)
            }
        }
    }

    fun confirm(taskId: Long, action: String) {
        val current = mutableState.value
        if (current.loading || current.busyTaskId != null || current.pendingConfirmation != null) return
        if (action !in setOf("TAKEN", "SKIPPED")) return
        val task = current.tasks.find { it.id == taskId }
        if (task?.canConfirm != true) return
        val session = sessions.session ?: return
        send(session, PendingConfirmation(taskId, action, UUID.randomUUID().toString()))
    }

    fun retryConfirmation() {
        val current = mutableState.value
        val pending = current.pendingConfirmation ?: return
        if (current.loading || current.busyTaskId != null) return
        val session = sessions.session ?: return
        send(session, pending)
    }

    private fun send(session: Session, pending: PendingConfirmation) {
        // 在启动协程之前上锁：连点两次也只有第一次能进来。
        mutableState.value = mutableState.value.copy(busyTaskId = pending.taskId, error = null, feedback = null)
        viewModelScope.launch {
            try {
                val returned = repository.confirm(session, pending.taskId, pending.action, pending.requestId)
                mutableState.value = mutableState.value.copy(
                    tasks = mutableState.value.tasks.map { if (it.id == returned.id) returned else it },
                    busyTaskId = null,
                    pendingConfirmation = null,
                    feedback = returned.statusLabel,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 只有「确定失败」才丢弃意图；结果未知时保留原 id 供重试。
                val uncertain = error !is RepositoryException || error.outcomeUnknown
                handleFailure(error, if (uncertain) pending else null)
            }
        }
    }

    private fun handleFailure(error: Exception, pending: PendingConfirmation?) {
        val message = (error as? RepositoryException)?.message ?: "请求未完成，请稍后重试"
        if (error is RepositoryException && error.sessionExpired) {
            viewModelScope.launch { sessions.expire(message) }
            return
        }
        mutableState.value = mutableState.value.copy(
            loading = false,
            loaded = true,
            busyTaskId = null,
            error = message,
            feedback = null,
            pendingConfirmation = pending,
        )
    }
}
