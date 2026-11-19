# 第二阶段：工程底座与本地提醒

Goal: 把第一阶段的可演示 demo 改造成能写进简历、能被追问的 Android 工程。
Architecture: 单 app 模块；Hilt 提供依赖；Repository 统一网络出口；DataStore 保存会话；AlarmManager + WorkManager 负责提醒调度。
Spec: docs/design.md（第一阶段）、本文档（第二阶段）。

## 决策记录

| 议题 | 结论 | 被否决方案与原因 |
| --- | --- | --- |
| 提醒通道 | App 侧 AlarmManager 精确闹钟 + WorkManager 兜底重排 | 服务端推送 / FCM：需改毕设后端，且国内 FCM 不可达，换厂商通道成本过高 |
| 精确闹钟 | `setExactAndAllowWhileIdle`，Android 12+ 声明 `SCHEDULE_EXACT_ALARM` 并在缺失时降级为 `setWindow` | 只用 WorkManager：最小周期 15 分钟，无法做到"8:00 准时" |
| 会话存储 | DataStore Preferences，只存 baseUrl / token / 账号昵称，**不存密码** | EncryptedSharedPreferences：`androidx.security:security-crypto` 已废弃，且引入后仍需自行管理密钥；明文存 token 的边界在 README 写明 |
| 依赖注入 | 手写 `AppContainer`（构造器注入） | Hilt 已试并否决：AGP 9.3.1 启用内置 Kotlin 支持，`org.jetbrains.kotlin.kapt` 与之不兼容（报 "The kapt plugin is not compatible with built-in Kotlin support"）；KSP 最新仅到 Kotlin 2.3，无 2.4 版本。绕开的代价是关闭 built-in Kotlin 与 newDsl，会连带破坏现有 `compileSdk { version = release(37) { minorApiLevel = 1 } }` 写法，不划算 |
| 会话传递 | `SessionHolder` 作为不可变请求标签（`@Tag`）随请求下发 | 全局可变快照：前台与后台请求会互相改写地址与令牌，并发下不可靠 |
| 网络实例 | 全局单例 OkHttp + Retrofit，用拦截器动态改写 host | 每次请求重建 Retrofit：无法复用连接池，且鉴权逻辑散落在各方法 |

## 任务

### 阶段 1：工程底座（约 1 周）

- [x] 1. 引入 `gradle/libs.versions.toml` 版本目录，收敛现有依赖坐标。
- [x] 2. ~~Hilt~~ → 手写 `AppContainer`（构造器注入 + `Application` 持有），替换 `MainActivity` 中内联创建的依赖。否决理由见上表。
- [x] 3. 网络层重构：`BaseUrlInterceptor` 改写 host + `AuthInterceptor` 统一注入 Bearer + debug 构建启用的 `HttpLoggingInterceptor`（`Level.BASIC`，不记录请求体，避免密码与 token 落日志）。OkHttp / Retrofit 收敛为进程内单例。
- [x] 4. `SessionStore`（DataStore）持久化会话；冷启动自动恢复并拉取今日任务；退出登录与 401 均清除本地会话。
- [x] 5. 补测试：单测 24 → 36 项（新增 host 改写、单例跨服务器复用、SessionStore 存取清与残缺数据、ViewModel 冷启动恢复/过期退回登录/登录落盘/退出清除），失败 0。

### 阶段 2：本地提醒（约 1.5 周）

- [ ] 6. `NotificationChannel` 分「用药提醒」「系统消息」两个渠道；`POST_NOTIFICATIONS` 运行时权限（Android 13+）申请与拒绝后的引导。
- [ ] 7. `ReminderScheduler`：按今日任务的原定时点排 `AlarmManager` 精确闹钟，`PendingIntent` 携带 taskId；过期任务不排。
- [ ] 8. `BroadcastReceiver` 到点发通知；点击通知深链进入对应任务卡片（不新开 Activity 栈）。
- [ ] 9. `WorkManager` 周期任务负责重排（App 被杀、时区/时间变更后）；`BOOT_COMPLETED` 广播兜底。
- [ ] 10. 验收：模拟器到点弹通知、点通知定位任务、撤销通知权限后给出引导、`adb reboot` 后闹钟仍生效。

## 验收标准

- `testDebugUnitTest lintDebug assembleDebug` 全绿，Lint 无错误。
- 冷启动免登录：杀进程重开直接进今日任务页。
- 提醒：任务到点 1 分钟内收到通知，点击直达该任务；权限被拒时不静默失败。
- 不虚构未执行项：无真机，凡"杀后台/厂商 ROM 限制"类结论只写实现与已知边界，不写"已验证"。

## Review Focus

- 拦截器不得把 token 写进日志；日志只在 debug 生效。
- 401 统一由拦截器/统一出口处理，不能出现"某处漏判 401"。
- 冷启动恢复期间不能闪现登录页；恢复失败要能退回登录。
- 提醒的 PendingIntent 必须 `FLAG_IMMUTABLE`，且 requestCode 唯一，避免任务互相覆盖。
- 已确认/已过期的任务不得再排闹钟。

## 验收记录 · 2026-11-19（会话恢复）

改前问题：冷启动恢复时任何异常都会清掉本地会话，包括"后端没启动"。用户明明登录过，只因本机服务没开就要重敲地址与账号。

改动：`MedicationViewModel.restoreSession()` 按错误类型分叉——只有 `sessionExpired` 清会话并退回登录页；网络/服务不可用保留会话，进任务页显示错误与重试按钮。新增单测 `networkFailureOnColdStartKeepsSavedSession`。

已验证（Pixel_10a 模拟器 API 37，本机假后端 `10.0.2.2:8080`）：

1. 登录成功，任务列表 2 项，`files/datastore/session.preferences_pb` 生成（179 字节）。
2. 停后端 → `adb shell am force-stop` → 重开：**留在任务页**，显示"无法连接后端，请检查地址、网络和服务是否启动"，会话文件仍在（时间戳未变）。
3. 重启后端 → 点"重新获取任务"：2 项任务恢复显示，错误卡片消失，**全程未重新登录**。

边界：使用本机假后端 fixture，未接真实 Spring Boot 与真实账号；未在实体机验证；未验证令牌真实过期（401）路径在界面上的表现，该路径由单测 `expiredSavedSessionFallsBackToLoginAndClearsStorage` 覆盖。
