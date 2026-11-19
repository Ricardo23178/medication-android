package com.zhusixing.medication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhusixing.medication.di.AppContainer
import com.zhusixing.medication.ui.auth.LoginScreen
import com.zhusixing.medication.ui.auth.LoginViewModel
import com.zhusixing.medication.ui.auth.RegisterScreen
import com.zhusixing.medication.ui.auth.RegisterViewModel
import com.zhusixing.medication.ui.common.ModulePlaceholder
import com.zhusixing.medication.ui.home.TodayScreen
import com.zhusixing.medication.ui.home.TodayViewModel
import com.zhusixing.medication.ui.main.HomeShell
import com.zhusixing.medication.ui.main.HomeTab
import com.zhusixing.medication.ui.medicine.MedicineEditScreen
import com.zhusixing.medication.ui.medicine.MedicineEditViewModel
import com.zhusixing.medication.ui.medicine.MedicineListScreen
import com.zhusixing.medication.ui.medicine.MedicineListViewModel
import com.zhusixing.medication.ui.navigation.AppNavigator
import com.zhusixing.medication.ui.navigation.AppRoute
import com.zhusixing.medication.ui.profile.ProfileScreen
import com.zhusixing.medication.ui.session.SessionState
import kotlinx.coroutines.launch

/**
 * 应用的装配入口。
 *
 * 只做两件事：把登录态同步到导航栈、按当前路由渲染页面。
 * 页面自己管自己的 ViewModel 和数据，这里不碰业务逻辑。
 */
@Composable
fun AppRoot(container: AppContainer) {
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()
    val navigator = remember { AppNavigator(AppRoute.Login) }

    LaunchedEffect(Unit) { container.sessionManager.restore() }

    /*
     * 登录态与导航栈必须保持一致，否则会出现「已经登录却停在登录页」
     * 或者「退出后按返回还能回到别人数据页」这类问题。
     */
    LaunchedEffect(sessionState) {
        when (sessionState) {
            is SessionState.SignedIn -> {
                val top = navigator.current
                if (top is AppRoute.Login || top is AppRoute.Register) {
                    navigator.replaceAll(AppRoute.Home)
                }
            }
            is SessionState.SignedOut -> {
                if (navigator.current !is AppRoute.Login) {
                    navigator.replaceAll(AppRoute.Login)
                }
            }
            SessionState.Restoring -> Unit
        }
    }

    // 恢复本地会话期间不渲染登录页，否则已登录用户会看到一闪而过的登录界面。
    if (sessionState is SessionState.Restoring) {
        SplashScreen()
        return
    }

    val stack by navigator.stack.collectAsStateWithLifecycle()
    BackHandler(enabled = stack.size > 1) { navigator.pop() }

    when (val route = stack.last()) {
        AppRoute.Login -> LoginRoute(
            container = container,
            navigator = navigator,
            notice = (sessionState as? SessionState.SignedOut)?.notice,
        )

        is AppRoute.Register -> RegisterRoute(
            container = container,
            navigator = navigator,
            server = route.server,
        )

        AppRoute.Home -> HomeRoute(container = container, navigator = navigator)

        is AppRoute.MedicineEdit -> MedicineEditRoute(
            container = container,
            navigator = navigator,
            medicineId = route.medicineId,
        )

        is AppRoute.PlanEdit -> ModulePlaceholder(
            title = if (route.planId == null) "新建计划" else "编辑计划",
            description = "选药、设剂量、定开始结束日期，再添加每天的几个提醒时间点。",
            apiHint = "GET/POST/PUT /api/plans、POST /api/plans/{id}/pause|resume",
            onBack = { navigator.pop() },
        )

        AppRoute.RecordList -> ModulePlaceholder(
            title = "服药记录",
            description = "按时间倒序展示每一次确认结果：已服、稍后补服、跳过。",
            apiHint = "GET /api/records",
            onBack = { navigator.pop() },
        )

        AppRoute.NotificationList -> ModulePlaceholder(
            title = "消息中心",
            description = "游标分页加载，支持只看未读、全部已读。",
            apiHint = "GET /api/notifications、POST /api/notifications/read-all",
            onBack = { navigator.pop() },
        )

        is AppRoute.NotificationDetail -> ModulePlaceholder(
            title = "消息详情",
            description = "查看单条消息正文，进入即标记已读。",
            apiHint = "GET /api/notifications/{id}、POST /api/notifications/{id}/read",
            onBack = { navigator.pop() },
        )

        AppRoute.FamilyAbnormal -> ModulePlaceholder(
            title = "家属异常",
            description = "家属查看被授权对象的漏服与长时间未确认事件，可标记知晓并关闭。",
            apiHint = "GET /api/family/abnormal-events、POST .../acknowledge|close",
            onBack = { navigator.pop() },
        )

        AppRoute.Statistics -> ModulePlaceholder(
            title = "用药统计",
            description = "近 30 天的应完成数、已服数、完成率，以及按天分布。",
            apiHint = "GET /api/statistics/calendar",
            onBack = { navigator.pop() },
        )
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("用药提醒", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun LoginRoute(container: AppContainer, navigator: AppNavigator, notice: String?) {
    val model: LoginViewModel = viewModel(
        factory = remember {
            simpleViewModelFactory { LoginViewModel(container.authRepository, container.sessionManager) }
        },
    )
    val state by model.state.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onServerChange = model::updateServer,
        onAccountChange = model::updateAccount,
        onPasswordChange = model::updatePassword,
        onTogglePassword = model::togglePasswordVisible,
        onTestConnection = model::testConnection,
        onLogin = model::login,
        onGoRegister = { navigator.push(AppRoute.Register(state.server)) },
        notice = notice,
    )
}

@Composable
private fun RegisterRoute(container: AppContainer, navigator: AppNavigator, server: String) {
    val model: RegisterViewModel = viewModel(
        // 换服务器地址就换一个 ViewModel，避免把上一台的填写内容带过来。
        key = "register-$server",
        factory = remember(server) {
            simpleViewModelFactory { RegisterViewModel(server, container.authRepository, container.sessionManager) }
        },
    )
    val state by model.state.collectAsStateWithLifecycle()
    RegisterScreen(
        state = state,
        onAccountChange = model::updateAccount,
        onPasswordChange = model::updatePassword,
        onConfirmChange = model::updateConfirm,
        onNicknameChange = model::updateNickname,
        onPhoneChange = model::updatePhone,
        onTogglePassword = model::togglePasswordVisible,
        onSubmit = model::submit,
        onBack = { navigator.pop() },
    )
}

@Composable
private fun HomeRoute(container: AppContainer, navigator: AppNavigator) {
    /*
     * 用 Int 而不是枚举存状态：从二级页返回、切换 Tab 都会让这个 Composable 重新组合，
     * 状态必须能跨组合销毁存活；存 Int 也免去给枚举写 Saver。
     */
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = HomeTab.entries[tabIndex.coerceIn(0, HomeTab.entries.lastIndex)]
    HomeShell(current = tab, onSelect = { tabIndex = it.ordinal }) { selected ->
        when (selected) {
            HomeTab.Today -> TodayRoute(container)
            HomeTab.Medicine -> MedicineListRoute(container, navigator)
            HomeTab.Plan -> ModulePlaceholder(
                title = "用药计划",
                description = "一条计划包含一种药、一个日期区间和每天多个提醒时间。",
                apiHint = "GET /api/plans、POST /api/plans/{id}/pause|resume",
            )
            HomeTab.Family -> ModulePlaceholder(
                title = "家属",
                description = "老人端生成邀请码，家属凭码绑定后可见异常事件。",
                apiHint = "POST /api/family/invitations、GET /api/family/relations",
            )
            HomeTab.Profile -> ProfileRoute(container, navigator)
        }
    }
}

@Composable
private fun TodayRoute(container: AppContainer) {
    val model: TodayViewModel = viewModel(
        factory = remember {
            simpleViewModelFactory {
                TodayViewModel(container.medicationRepository, container.sessionManager)
            }
        },
    )
    val state by model.state.collectAsStateWithLifecycle()
    TodayScreen(
        state = state,
        userName = container.sessionManager.session?.user?.displayName.orEmpty(),
        onRefresh = model::refresh,
        onConfirm = model::confirm,
        onRetry = model::retryConfirmation,
    )
}

@Composable
private fun MedicineListRoute(container: AppContainer, navigator: AppNavigator) {
    val model: MedicineListViewModel = viewModel(
        factory = remember {
            simpleViewModelFactory {
                MedicineListViewModel(container.medicineRepository, container.sessionManager)
            }
        },
    )
    val state by model.state.collectAsStateWithLifecycle()
    MedicineListScreen(
        state = state,
        onRefresh = model::refresh,
        onKeywordChange = model::updateKeyword,
        onAdd = { navigator.push(AppRoute.MedicineEdit()) },
        onEdit = { id -> navigator.push(AppRoute.MedicineEdit(id)) },
        onDelete = model::delete,
        onConsumeMessage = model::consumeMessage,
    )
}

@Composable
private fun MedicineEditRoute(container: AppContainer, navigator: AppNavigator, medicineId: Long?) {
    val model: MedicineEditViewModel = viewModel(
        // 新增和编辑是两个不同的表单状态，用 id 区分实例，避免「编辑 A 之后点新增还带着 A 的内容」。
        key = "medicine-edit-${medicineId ?: "new"}",
        factory = remember(medicineId) {
            simpleViewModelFactory {
                MedicineEditViewModel(medicineId, container.medicineRepository, container.sessionManager)
            }
        },
    )
    val state by model.state.collectAsStateWithLifecycle()
    MedicineEditScreen(
        state = state,
        onLoad = model::load,
        onNameChange = model::updateName,
        onSpecificationChange = model::updateSpecification,
        onDosageChange = model::updateDosage,
        onUnitChange = model::updateUnit,
        onRemarkChange = model::updateRemark,
        onEnabledChange = model::updateEnabled,
        onSave = model::save,
        onDelete = model::delete,
        onBack = { navigator.pop() },
    )
}

@Composable
private fun ProfileRoute(container: AppContainer, navigator: AppNavigator) {
    val scope = rememberCoroutineScope()
    val user = container.sessionManager.session?.user
    ProfileScreen(
        displayName = user?.displayName.orEmpty(),
        account = user?.account.orEmpty(),
        roleLabel = when (user?.role) {
            "ADMIN" -> "管理员"
            "FAMILY" -> "家属"
            "OWNER" -> "本人"
            else -> null
        },
        onOpenRecords = { navigator.push(AppRoute.RecordList) },
        onOpenNotifications = { navigator.push(AppRoute.NotificationList) },
        onOpenStatistics = { navigator.push(AppRoute.Statistics) },
        onOpenFamilyAbnormal = { navigator.push(AppRoute.FamilyAbnormal) },
        onLogout = { scope.launch { container.sessionManager.signOut() } },
    )
}
