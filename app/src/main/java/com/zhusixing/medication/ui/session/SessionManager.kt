package com.zhusixing.medication.ui.session

import com.zhusixing.medication.data.Session
import com.zhusixing.medication.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 登录态。三种状态互斥，界面按状态选页面，不靠一堆布尔量拼。
 */
sealed interface SessionState {
    /** 冷启动正在读本地会话。此时不能显示登录页，否则已登录用户会看到闪一下。 */
    data object Restoring : SessionState

    /** 未登录。[notice] 用于携带「登录已失效」这类被动退出的原因。 */
    data class SignedOut(val notice: String? = null) : SessionState

    data class SignedIn(val session: Session) : SessionState
}

/**
 * 会话的单一持有者。
 *
 * 放在 AppContainer 里做进程单例，而不是做成 ViewModel：登录态要跨 5 个 Tab 和 9 个二级页
 * 共享，挂在某个 ViewModel 上会导致「从哪进来决定看到谁」的问题，也会在页面切换时被回收。
 *
 * 所有写本地存储的失败都只吞不抛：登录成功但落盘失败，用户这次能正常用，
 * 只是下次冷启动要重新登录。这比因为磁盘错误把已经成功的登录判为失败要合理。
 */
class SessionManager(private val store: SessionStore) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val session: Session? get() = (_state.value as? SessionState.SignedIn)?.session

    /** 冷启动恢复。重复调用只会生效一次，避免热重载或多处调用互相覆盖。 */
    suspend fun restore() {
        if (_state.value !is SessionState.Restoring) return
        val saved = runCatching { store.load() }.getOrNull()
        _state.value = if (saved == null) SessionState.SignedOut() else SessionState.SignedIn(saved)
    }

    suspend fun signIn(session: Session) {
        runCatching { store.save(session) }
        _state.value = SessionState.SignedIn(session)
    }

    /** 主动退出：本地凭证必须清干净，下次进来重新登录。 */
    suspend fun signOut() {
        runCatching { store.clear() }
        _state.value = SessionState.SignedOut()
    }

    /**
     * 被动失效：任何页面收到 401 都调这里。
     *
     * 令牌过期时后端已经不再认这个凭证，继续留着只会让每个请求都失败一遍，
     * 所以直接清本地并退回登录页，把「重新登录」作为唯一出路明确摆给用户。
     */
    suspend fun expire(notice: String = "登录已失效，请重新登录") {
        runCatching { store.clear() }
        _state.value = SessionState.SignedOut(notice)
    }
}
