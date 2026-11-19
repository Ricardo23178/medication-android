package com.zhusixing.medication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhusixing.medication.data.InMemorySessionStore
import com.zhusixing.medication.data.MedicationRepository
import com.zhusixing.medication.data.RemindTask
import com.zhusixing.medication.data.RepositoryException
import com.zhusixing.medication.data.Session
import com.zhusixing.medication.data.SessionStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * 历史版本：单页面时期的「登录 + 今日任务」ViewModel。
 *
 * 它的职责已经拆进 SessionManager（登录态）、LoginViewModel（登录表单）与
 * TodayViewModel（今日任务与确认），界面不再使用这个类。
 *
 * 暂时保留是因为 MedicationViewModelTest 的 6 个用例仍然跑在它上面，
 * 而那些用例覆盖的「重复点击只提交一次」「结果未知保留原请求 ID」等行为，
 * 与 TodayViewModel 是同一套逻辑。等测试迁到 TodayViewModel 之后删除本文件。
 */
data class PendingConfirmation(val taskId: Long, val action: String, val requestId: String)

data class MedicationUiState(
    val session: Session? = null,
    val tasks: List<RemindTask> = emptyList(),
    val loading: Boolean = false,
    val busyTaskId: Long? = null,
    val error: String? = null,
    val feedback: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    /** 冷启动时正在尝试恢复上次的会话，界面应先显示载入态而不是登录页。 */
    val restoring: Boolean = false,
)

class MedicationViewModel(
    private val repository: MedicationRepository,
    private val sessionStore: SessionStore = InMemorySessionStore(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(MedicationUiState(restoring = true))
    val state: StateFlow<MedicationUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    // 即使网络请求无法及时取消，旧会话的响应也不能覆盖新会话。
    private var sessionVersion = 0L

    init {
        restoreSession()
    }

    /**
     * 冷启动时尝试用本地会话直接进入今日任务。
     *
     * 恢复阶段任何失败（令牌过期、后端换了地址、数据被清）都退回登录页而不是停在一个空壳页面上：
     * 用户此时没有可用的交互入口，重新登录是唯一可靠的出路。
     */
    private fun restoreSession() {
        viewModelScope.launch {
            val saved = runCatching { sessionStore.load() }.getOrNull()
            val current = mutableState.value
            if (saved == null || current.session != null || current.loading) {
                mutableState.value = current.copy(restoring = false)
                return@launch
            }
            val version = ++sessionVersion
            mutableState.value = MedicationUiState(session = saved, loading = true, restoring = false)
            try {
                val tasks = repository.today(saved)
                if (version == sessionVersion) {
                    mutableState.value = mutableState.value.copy(tasks = tasks, loading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version != sessionVersion) return@launch
                val message = (error as? RepositoryException)?.message ?: "无法连接后端，请检查服务是否启动"
                if (error is RepositoryException && error.sessionExpired) {
                    // 令牌真的失效：本地会话没有价值了，清掉并退回登录页。
                    runCatching { sessionStore.clear() }
                    sessionVersion++
                    mutableState.value = MedicationUiState(error = message)
                } else {
                    // 网络断了、后端没启动、服务器 5xx：会话本身仍然有效，不能毁掉。
                    // 保留会话进任务页，用错误提示 + 刷新按钮让用户重试。
                    mutableState.value = mutableState.value.copy(loading = false, error = message)
                }
            }
        }
    }

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
                // 会话落盘失败不该阻断本次登录；只是下次冷启动要重新输入。
                runCatching { sessionStore.save(session) }
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
            viewModelScope.launch { runCatching { sessionStore.clear() } }
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
        viewModelScope.launch { runCatching { sessionStore.clear() } }
    }
}
