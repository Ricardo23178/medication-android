package com.zhusixing.medication.data

import okhttp3.HttpUrl

/*
 * 按业务领域拆分的仓储接口。
 *
 * 为什么不合并成一个大接口：一个 34 方法的对象交给 ViewModel，等于让每个页面都能碰到
 * 全部接口——药品页能删计划、统计页能解绑家属。按领域切开后，每个 ViewModel 只依赖
 * 它真正需要的那一个，测试里写替身也只要实现三五个方法而不是三十个。
 *
 * 参数校验放在实现类的入口，不等到服务端返回 400：
 * 用户名 4 位、密码 6 位、剂量不能为负这些规则在前端先挡一次，用户不必等一次网络往返
 * 才知道填错了；服务端仍然会再校验一遍，前端这层不是安全边界。
 */

// ─────────────────────────── 认证 ───────────────────────────

interface AuthRepository {
    suspend fun login(baseUrl: String, account: String, password: String): Session

    /** 注册即登录，成功后直接得到可用会话。 */
    suspend fun register(baseUrl: String, request: RegisterRequest): Session

    /** 用当前令牌换取身份，用于校验本地缓存的会话是否还活着。 */
    suspend fun me(session: Session): CurrentUser

    /** 不需要令牌。登录页填完服务器地址后先探一下，把「地址错了」和「密码错了」分开。 */
    suspend fun health(baseUrl: String): HealthInfo
}

// ─────────────────────────── 药品 ───────────────────────────

interface MedicineRepository {
    suspend fun list(session: Session): List<Medicine>
    suspend fun detail(session: Session, id: Long): Medicine
    suspend fun create(session: Session, request: MedicineRequest): Medicine
    suspend fun update(session: Session, id: Long, request: MedicineRequest): Medicine
    suspend fun delete(session: Session, id: Long)
}

// ─────────────────────────── 计划 ───────────────────────────

interface PlanRepository {
    suspend fun list(session: Session): List<Plan>
    suspend fun detail(session: Session, id: Long): Plan
    suspend fun create(session: Session, request: PlanRequest): Plan
    suspend fun update(session: Session, id: Long, request: PlanRequest): Plan
    suspend fun delete(session: Session, id: Long)

    /** ACTIVE → PAUSED，版本号不变。 */
    suspend fun pause(session: Session, id: Long): Plan

    /** PAUSED → ACTIVE，版本号递增；递增后旧的在途任务会被判为过期。 */
    suspend fun resume(session: Session, id: Long): Plan
}

// ─────────────────────────── 记录 ───────────────────────────

interface RecordRepository {
    /** 后端按时间倒序返回，没有分页参数。 */
    suspend fun list(session: Session): List<MedicationRecord>
}

// ─────────────────────────── 消息 ───────────────────────────

interface NotificationRepository {
    /**
     * 游标分页。首次调用 beforeId 传 null；后续用上一次返回的 nextBeforeId，
     * hasMore 为 false 就到底了。用游标而不是页码，是因为新消息会插到表头，
     * 页码分页会把同一条消息挤到两页里或者整条漏掉。
     */
    suspend fun page(
        session: Session,
        unreadOnly: Boolean = false,
        beforeId: Long? = null,
        limit: Int = 20,
    ): NotificationPage

    suspend fun unreadCount(session: Session): Long
    suspend fun detail(session: Session, id: Long): NotificationItem
    suspend fun markRead(session: Session, id: Long)
    /** 返回本次实际标记的条数。 */
    suspend fun markAllRead(session: Session): Int
}

// ─────────────────────────── 家属 ───────────────────────────

interface FamilyRepository {
    /** 老人端发起绑定，返回的记录里带邀请码。 */
    suspend fun invite(session: Session, request: InviteRequest): FamilyRelation
    suspend fun acceptInvite(session: Session, inviteCode: String): FamilyRelation
    suspend fun relations(session: Session): List<FamilyRelation>
    suspend fun unbind(session: Session, relationId: Long)

    /** 家属视角的异常事件；includeClosed 为 true 时连已关闭的一起返回。 */
    suspend fun abnormalEvents(session: Session, includeClosed: Boolean = false): List<AbnormalEvent>
    suspend fun acknowledge(session: Session, eventId: Long)

    /** 关闭事件必须先知晓，服务端用 409 拦乱序操作。 */
    suspend fun close(session: Session, eventId: Long)
}

// ─────────────────────────── 统计 ───────────────────────────

interface StatisticsRepository {
    /** 日期格式 yyyy-MM-dd；都传 null 时后端默认最近 30 天。 */
    suspend fun calendar(session: Session, startDate: String? = null, endDate: String? = null): CalendarStat
}

// ─────────────────────────── 共用的校验与转换 ───────────────────────────

/**
 * 登录 / 注册返回体转会话。
 *
 * 令牌、用户标识、账号三项缺一项就不算登录成功：缺令牌后续全是 401，
 * 缺用户标识本地会话恢复会失败，都是过一会儿才暴露的问题，不如现在拦下。
 */
internal fun LoginData.toSession(address: HttpUrl): Session {
    val issuedToken = token
    val profile = user
    if (issuedToken.isNullOrBlank() || profile == null || profile.id <= 0 || profile.account.isNullOrBlank()) {
        throw RepositoryException("登录响应不完整，请检查后端服务")
    }
    return Session(address.toString(), issuedToken, profile)
}

/** 列表里的 null 元素与重复主键都视为数据异常，不允许被悄悄过滤后当成正常列表显示。 */
internal fun <T> List<T?>?.requireList(label: String, id: (T) -> Long): List<T> {
    if (this == null) throw RepositoryException("$label 数据不完整，请刷新")
    val items = mapNotNull { it }
    if (items.map(id).distinct().size != items.size) {
        throw RepositoryException("$label 存在重复数据，请刷新")
    }
    return items
}

internal fun requirePositiveId(id: Long, label: String) {
    if (id <= 0) throw RepositoryException("$label 参数无效，请返回列表重新进入")
}
