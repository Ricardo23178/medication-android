# Android第一阶段 Implementation Plan

Goal: 独立Android APK打通登录、今日任务、已服/跳过确认。
Architecture: 单app模块，Retrofit数据层、ViewModel+StateFlow状态层、Compose界面。
Tech Stack: Kotlin, Jetpack Compose, ViewModel, Coroutines, Retrofit, JUnit。
Spec: docs/design.md

## Global Constraints
- 仅新增D盘独立工程，不改毕设功能和数据库。
- 无明文持久化凭证，HTTP仅debug；同时检查HTTP和业务码。
- 新手能沿核心调用链阅读，不引入多模块、DI容器或通用框架。

## Tasks
- [x] 1. 核对后端契约和本机工具；创建构建配置及接口模型。
- [x] 2. 先写ViewModel测试：登录、空态/失败、提交防重、401退出、旧响应隔离、重试ID；观察失败后最小实现。
- [x] 3. 先写网络测试：三接口JSON、Bearer、HTTP/业务失败、错误数据；观察失败后实现Repository。
- [x] 4. Compose登录页及今日任务页，绑定真实状态，不做假成功；构建debug APK并运行全套测试/lint。
- [x] 5. 独立复核，补必要回归；可用时模拟器验证；写README及学习说明，记录实际验证边界。

## Review Focus
- HTTP200但code401不能当成功。
- 旧请求401不能清掉新登录，退出后不能回显旧任务。
- 确认结果以服务端action为准，网络不确定时重试沿用意图ID。
- 非法地址、空token、未知/取消/超时任务不能继续操作。
- 真实网络失败提供可理解反馈，不记录密码或token。

Completed: 2026-10-31. Evidence: verification/RESULTS.md.
