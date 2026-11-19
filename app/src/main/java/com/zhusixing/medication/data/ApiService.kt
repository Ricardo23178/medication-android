package com.zhusixing.medication.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

/**
 * 移动端用到的全部 REST 接口：后端 43 个中的 34 个。
 *
 * 未纳入的 9 个是 api/admin 下的管理后台接口，App 不需要。
 *
 * 两个刻意的设计：
 *   1. 每个方法都带 `@Tag ctx: SessionHolder`。服务器地址与令牌不走全局单例，
 *      而是随请求携带，由 BaseUrlInterceptor / AuthInterceptor 就地改写 URL 与 Authorization。
 *      换账号、换服务器地址时不需要重建 Retrofit。
 *   2. 一律返回 `Response<ApiEnvelope<T>>`。HTTP 状态码与业务 code 是两层语义，
 *      必须都能拿到才能区分「401 令牌失效」「500 服务端故障」「400 业务拒绝」——
 *      这三者对用户该说什么、能不能重试完全不同。具体判断在 ApiCall 里统一做。
 *
 * 不写默认参数：Retrofit 靠注解处理器生成实现，Kotlin 的默认参数会产生合成方法，
 * 运行时拿到的是 null 而不是默认值。所有入参由调用方显式给出。
 */
internal interface ApiService {

    // ──────────────────────── 认证与身份 ────────────────────────

    /** 注册即登录，返回结构与登录一致。 */
    @POST("api/auth/register")
    suspend fun register(
        @Tag ctx: SessionHolder,
        @Body body: RegisterRequest,
    ): Response<ApiEnvelope<LoginData>>

    @POST("api/auth/login")
    suspend fun login(
        @Tag ctx: SessionHolder,
        @Body body: LoginRequest,
    ): Response<ApiEnvelope<LoginData>>

    /** 凭令牌认人。用来验证本地缓存的会话是否还有效。 */
    @GET("api/user/me")
    suspend fun me(@Tag ctx: SessionHolder): Response<ApiEnvelope<CurrentUser>>

    // ──────────────────────── 药品档案 ────────────────────────

    @GET("api/medicines")
    suspend fun medicines(@Tag ctx: SessionHolder): Response<ApiEnvelope<List<Medicine?>>>

    @POST("api/medicines")
    suspend fun createMedicine(
        @Tag ctx: SessionHolder,
        @Body body: MedicineRequest,
    ): Response<ApiEnvelope<Medicine>>

    @GET("api/medicines/{id}")
    suspend fun medicine(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Medicine>>

    @PUT("api/medicines/{id}")
    suspend fun updateMedicine(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
        @Body body: MedicineRequest,
    ): Response<ApiEnvelope<Medicine>>

    @DELETE("api/medicines/{id}")
    suspend fun deleteMedicine(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Any?>>

    // ──────────────────────── 用药计划 ────────────────────────

    @GET("api/plans")
    suspend fun plans(@Tag ctx: SessionHolder): Response<ApiEnvelope<List<Plan?>>>

    @POST("api/plans")
    suspend fun createPlan(
        @Tag ctx: SessionHolder,
        @Body body: PlanRequest,
    ): Response<ApiEnvelope<Plan>>

    @GET("api/plans/{id}")
    suspend fun plan(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Plan>>

    @PUT("api/plans/{id}")
    suspend fun updatePlan(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
        @Body body: PlanRequest,
    ): Response<ApiEnvelope<Plan>>

    @DELETE("api/plans/{id}")
    suspend fun deletePlan(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Any?>>

    /** ACTIVE → PAUSED。后端会校验当前状态，非法流转返回 400。 */
    @POST("api/plans/{id}/pause")
    suspend fun pausePlan(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Plan>>

    /** PAUSED → ACTIVE，同时递增 planVersion。 */
    @POST("api/plans/{id}/resume")
    suspend fun resumePlan(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Plan>>

    // ──────────────────────── 提醒任务 ────────────────────────

    @GET("api/reminders/today")
    suspend fun today(@Tag ctx: SessionHolder): Response<ApiEnvelope<List<RemindTask?>>>

    /** 幂等：同一个 clientRequestId 重复提交返回首次结果，不会写两条记录。 */
    @POST("api/reminders/{id}/confirm")
    suspend fun confirm(
        @Tag ctx: SessionHolder,
        @Path("id") taskId: Long,
        @Body body: ConfirmRequest,
    ): Response<ApiEnvelope<RemindTask>>

    /** 管理员接口：立即生成当天任务，演示时不必等定时器。 */
    @POST("api/reminders/generate-today")
    suspend fun generateToday(@Tag ctx: SessionHolder): Response<ApiEnvelope<String>>

    /** 管理员接口：立即扫描超时任务并生成异常事件。 */
    @POST("api/reminders/scan-expired")
    suspend fun scanExpired(@Tag ctx: SessionHolder): Response<ApiEnvelope<String>>

    // ──────────────────────── 服药记录 ────────────────────────

    @GET("api/records")
    suspend fun records(@Tag ctx: SessionHolder): Response<ApiEnvelope<List<MedicationRecord?>>>

    // ──────────────────────── 站内消息 ────────────────────────

    @GET("api/notifications")
    suspend fun notifications(
        @Tag ctx: SessionHolder,
        @Query("unreadOnly") unreadOnly: Boolean,
        @Query("beforeId") beforeId: Long?,
        @Query("limit") limit: Int,
    ): Response<ApiEnvelope<NotificationPage>>

    @GET("api/notifications/unread-count")
    suspend fun unreadCount(@Tag ctx: SessionHolder): Response<ApiEnvelope<Long>>

    @GET("api/notifications/{id}")
    suspend fun notification(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<NotificationItem>>

    @POST("api/notifications/{id}/read")
    suspend fun readNotification(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Any?>>

    @POST("api/notifications/read-all")
    suspend fun readAllNotifications(@Tag ctx: SessionHolder): Response<ApiEnvelope<Int>>

    // ──────────────────────── 家属协同 ────────────────────────

    /** 老人端发起绑定，返回带 inviteCode 的关系记录。 */
    @POST("api/family/invitations")
    suspend fun invite(
        @Tag ctx: SessionHolder,
        @Body body: InviteRequest,
    ): Response<ApiEnvelope<FamilyRelation>>

    /** 家属端凭邀请码接受绑定。 */
    @POST("api/family/invitations/accept")
    suspend fun acceptInvite(
        @Tag ctx: SessionHolder,
        @Body body: AcceptInviteRequest,
    ): Response<ApiEnvelope<FamilyRelation>>

    @GET("api/family/relations")
    suspend fun relations(@Tag ctx: SessionHolder): Response<ApiEnvelope<List<FamilyRelation?>>>

    @DELETE("api/family/relations/{id}")
    suspend fun unbind(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Any?>>

    @GET("api/family/abnormal-events")
    suspend fun abnormalEvents(
        @Tag ctx: SessionHolder,
        @Query("includeClosed") includeClosed: Boolean,
    ): Response<ApiEnvelope<List<AbnormalEvent?>>>

    @POST("api/family/abnormal-events/{id}/acknowledge")
    suspend fun acknowledgeAbnormal(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Any?>>

    @POST("api/family/abnormal-events/{id}/close")
    suspend fun closeAbnormal(
        @Tag ctx: SessionHolder,
        @Path("id") id: Long,
    ): Response<ApiEnvelope<Any?>>

    // ──────────────────────── 统计 ────────────────────────

    /** startDate / endDate 都是 "yyyy-MM-dd"；不传则后端默认最近 30 天。 */
    @GET("api/statistics/calendar")
    suspend fun calendar(
        @Tag ctx: SessionHolder,
        @Query("startDate") startDate: String?,
        @Query("endDate") endDate: String?,
    ): Response<ApiEnvelope<CalendarStat>>

    // ──────────────────────── 健康检查 ────────────────────────

    /** 特例：不套统一壳，直接返回裸对象。用于登录页「测试连接」。 */
    @GET("api/health")
    suspend fun health(@Tag ctx: SessionHolder): Response<HealthInfo>
}
