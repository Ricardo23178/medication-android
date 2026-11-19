package com.zhusixing.medication.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhusixing.medication.data.AuthRepository
import com.zhusixing.medication.data.RepositoryException
import com.zhusixing.medication.ui.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val server: String = DEFAULT_SERVER,
    val account: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val loading: Boolean = false,
    val testing: Boolean = false,
    val error: String? = null,
    /** 连接测试的结果，成功后告诉用户后端版本与账号数，比只说「成功」有信息量。 */
    val probe: String? = null,
) {
    val canSubmit: Boolean
        get() = !loading && !testing && server.isNotBlank() && account.isNotBlank() && password.isNotBlank()

    companion object {
        /** 模拟器里 10.0.2.2 就是宿主机的 127.0.0.1；真机要换成电脑的局域网地址。 */
        const val DEFAULT_SERVER = "http://10.0.2.2:8080/"
    }
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    fun updateServer(value: String) {
        mutableState.value = mutableState.value.copy(server = value, error = null, probe = null)
    }

    fun updateAccount(value: String) {
        mutableState.value = mutableState.value.copy(account = value, error = null)
    }

    fun updatePassword(value: String) {
        mutableState.value = mutableState.value.copy(password = value, error = null)
    }

    fun togglePasswordVisible() {
        mutableState.value = mutableState.value.copy(showPassword = !mutableState.value.showPassword)
    }

    /** 先探一次 /api/health，把「地址填错了」和「密码错了」两种失败分开。 */
    fun testConnection() {
        val current = mutableState.value
        if (current.testing || current.loading) return
        mutableState.value = current.copy(testing = true, error = null, probe = null)
        viewModelScope.launch {
            try {
                val health = authRepository.health(current.server)
                val detail = buildString {
                    append("连接成功")
                    health.dbVersion?.let { append("，数据库 $it") }
                    health.sysUserCount?.let { append("，已有 $it 个账号") }
                }
                mutableState.value = mutableState.value.copy(testing = false, probe = detail)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    testing = false,
                    error = (error as? RepositoryException)?.message ?: "无法连接服务器，请检查地址与网络",
                )
            }
        }
    }

    fun login() {
        val current = mutableState.value
        if (!current.canSubmit) {
            if (!current.loading) {
                mutableState.value = current.copy(error = "请填写服务器地址、账号和密码")
            }
            return
        }
        mutableState.value = current.copy(loading = true, error = null, probe = null)
        viewModelScope.launch {
            try {
                val session = authRepository.login(current.server, current.account, current.password)
                // 登录成功立即抹掉内存里的密码：ViewModel 在 Activity 范围内常驻，
                // 留着密码意味着用户退出登录后，下次进登录页还能看到它。
                mutableState.value = mutableState.value.copy(password = "", loading = false)
                sessions.signIn(session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = (error as? RepositoryException)?.message ?: "登录失败，请稍后重试",
                )
            }
        }
    }
}
