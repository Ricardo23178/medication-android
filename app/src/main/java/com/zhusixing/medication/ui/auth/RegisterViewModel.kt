package com.zhusixing.medication.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhusixing.medication.data.AuthRepository
import com.zhusixing.medication.data.RegisterRequest
import com.zhusixing.medication.data.RepositoryException
import com.zhusixing.medication.ui.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val account: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nickname: String = "",
    val phone: String = "",
    val showPassword: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val fieldError: String? = null,
) {
    val canSubmit: Boolean
        get() = !loading && account.isNotBlank() && password.isNotBlank() &&
            confirmPassword.isNotBlank() && nickname.isNotBlank()
}

class RegisterViewModel(
    private val server: String,
    private val authRepository: AuthRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    private val mutableState = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = mutableState.asStateFlow()

    fun updateAccount(value: String) = update { copy(account = value) }
    fun updatePassword(value: String) = update { copy(password = value) }
    fun updateConfirm(value: String) = update { copy(confirmPassword = value) }
    fun updateNickname(value: String) = update { copy(nickname = value) }
    fun updatePhone(value: String) = update { copy(phone = value) }

    fun togglePasswordVisible() = update { copy(showPassword = !showPassword) }

    private fun update(block: RegisterUiState.() -> RegisterUiState) {
        mutableState.value = mutableState.value.block().copy(error = null, fieldError = null)
    }

    fun submit() {
        val current = mutableState.value
        if (current.loading) return

        // 本地先挡一次，用户不必等一次网络往返才知道密码不一致或账号格式不对。
        // 校验规则与后端 @Pattern / @Size 保持一致，改一处就要同步另一处。
        if (!ACCOUNT.matches(current.account.trim())) {
            mutableState.value = current.copy(fieldError = "账号只能包含字母、数字、下划线，长度 4-32 位")
            return
        }
        if (current.password.length !in 6..32) {
            mutableState.value = current.copy(fieldError = "密码长度需为 6-32 位")
            return
        }
        if (current.password != current.confirmPassword) {
            mutableState.value = current.copy(fieldError = "两次输入的密码不一致")
            return
        }
        if (current.nickname.isBlank()) {
            mutableState.value = current.copy(fieldError = "请填写昵称，家属端会用它显示")
            return
        }
        val phone = current.phone.trim()
        if (phone.isNotEmpty() && !PHONE.matches(phone)) {
            mutableState.value = current.copy(fieldError = "手机号格式不正确，或留空")
            return
        }

        mutableState.value = current.copy(loading = true, error = null, fieldError = null)
        viewModelScope.launch {
            try {
                val session = authRepository.register(
                    baseUrl = server,
                    request = RegisterRequest(
                        account = current.account.trim(),
                        password = current.password,
                        confirmPassword = current.confirmPassword,
                        nickname = current.nickname.trim(),
                        phone = phone.takeIf { it.isNotEmpty() },
                    ),
                )
                mutableState.value = mutableState.value.copy(password = "", confirmPassword = "", loading = false)
                sessions.signIn(session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = (error as? RepositoryException)?.message ?: "注册失败，请稍后重试",
                )
            }
        }
    }

    private companion object {
        val ACCOUNT = Regex("^[a-zA-Z0-9_]{4,32}$")
        val PHONE = Regex("^1[3-9]\\d{9}$")
    }
}
