# Android第一阶段验收记录

交付日期：2026年10月31日。工程：D:/Codex/MedicationAndroid。

## 交付内容

Kotlin + Compose独立Android客户端，复用既有Spring Boot REST契约，完成登录、今日任务、确认已服/跳过、刷新及退出。含加载、空态、失败反馈、提交锁、原请求ID重试和401会话隔离。没有修改后端、HarmonyOS或论文文件。

## 已验证

- 最终执行`testDebugUnitTest lintDebug assembleDebug --offline`成功，调试APK存在，大小12915722字节。
- 单元测试24项：网络层10项、ViewModel14项，失败0。实际执行报告日期为2026-10-30；2026-10-31最终检查复用了未变化的单测任务缓存，没有声称重新执行24项。
- Lint：0错误、6条非阻断警告，为target API及固定依赖/构建工具版本更新提示，未通过屏蔽规则跳过错误。
- 2026-10-31 Gradle设备测试1项通过；随后显式安装同一APK与测试APK，直接运行JUnit复验亦为`OK (1 test)`。覆盖错误密码、按钮登录、键盘完成登录、Bearer读取任务、两种确认、服务端已有结果优先和退出清数据。
- 两张实际设备截图已查看：`phase1-login.png`、`phase1-tasks.png`，页面文字及操作按钮可见。任务截图采用虚构药品。
- 接口测试验证Long ID和BigDecimal剂量、HTTP200但业务401、断网写请求不自动重发、错误任务ID拒收。VM测试验证重复点击、结果不确定时复用ID、旧401不能清空新会话。

设备：独立MedicationPhase1模拟器，API 37，Physical size: 720x1280，时区GMT。构建使用现有API37.1平台、target API36、min API26；Gradle9.5.0、AGP9.3.1、Kotlin2.4.20及Android Studio自带JBR，JVM目标17。

APK SHA-256：`64d11f20e92a51533b74b00f9bcaf1251f5e661a6a6b1b931467a0379d9156be`。

## 排查与修正

先用占位实现执行失败测试，再实现并验证。初始失败证据保留于`red/`。

设备测试最初由Compose传递的Espresso3.5.0反射旧InputManager API而失败，固定测试依赖为3.7.0；依据[官方修复记录](https://developer.android.com/jetpack/androidx/releases/test#espresso_3.7.0)。旧框架失败记录保留于`ui-framework-failure/`。

后续超时诊断显示请求数仍为1，密码仍聚焦，按钮位于键盘遮挡区；第二次点击没有触发请求。测试改为先关闭键盘验证按钮入口，再验证密码框的IME完成入口，没有修改业务逻辑或增加等待时限。修正后的两次执行均通过，原诊断保留于`ui-keyboard-failure/`。

直接ADB复验前须重新安装测试APK，因为Gradle测试清理会卸载它；此前缺少instrumentation的命令没有执行测试，不能算作应用测试失败。正确复验日志为`ui-repeat-installed-20261031.txt`。

## 未验证与范围边界

- 完整界面验收使用隔离MockWebServer及虚构账号/任务，没有读取真实口令或改动实际业务数据库。真实后端账号联调及Android实体机测试未执行。
- API26等其他Android版本、旋转/进程回收的设备行为未逐项实测。会话按内存方案实现，进程结束需要重新登录。
- 系统通知、后台提醒、离线缓存、注册和药品/计划编辑不在本阶段范围；真实后端联调需使用本人可控的既有账号和已生成的任务。
- 提供调试APK，尚未配置发布签名。HTTP仅debug允许，release要求HTTPS。
- Studio启动入口已核对脚本语法与程序路径，未自动打开IDE验证GUI。请按README先关闭已有Studio再从脚本启动，继承D盘缓存及AVD设置。

源码、Gradle缓存、Android用户目录及临时文件均位于D盘；原有SDK和Studio也位于D盘。工具目录内未完成的旧Gradle下载不用于构建，实际运行的是复制到D盘的本机现有分发包。

当前通过的原始XML及Lint文本副本位于`green/`；机器可读结果位于`CURRENT_CHECKS.json`。

## 仓库归档范围

GitHub 仓库保留本验收说明与两张虚构数据截图。本文提及的原始 XML、Lint 文本、诊断目录、机器可读结果和 IDE 设置备份仅保存在作者本机，未上传仓库。
