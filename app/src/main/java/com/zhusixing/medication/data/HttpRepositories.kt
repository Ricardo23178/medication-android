package com.zhusixing.medication.data

import java.time.LocalDate
import java.time.format.DateTimeParseException

/*
 * 各领域仓储的 REST 实现。
 *
 * 每个方法只做三件事：校验入参 → 发请求 → 校验返回值。协议层的判断全部交给 ApiCall，
 * 所以这里看不到 HTTP 状态码，也看不到 code 字段。
 */

// ─────────────────────────── 认证 ───────────────────────────

internal class HttpAuthRepository(private val api: ApiService) : AuthRepository {

    override suspend fun login(baseUrl: String, account: String, password: String): Session {
        val address = ApiCall.address(baseUrl)
        if (account.isBlank() || password.isBlank()) throw RepositoryException("请输入账号和密码")
        // 登录本身不需要令牌，但拦截器必须知道往哪台服务器发，所以仍要带上上下文。
        val data = ApiCall.requireData {
            api.login(SessionHolder(address, null), LoginRequest(account.trim(), password))
        }
        return data.toSession(address)
    }

    override suspend fun register(baseUrl: String, request: RegisterRequest): Session {
        val address = ApiCall.address(baseUrl)
        if (!ACCOUNT.matches(request.account)) {
            throw RepositoryException("账号只能包含字母、数字、下划线，长度 4-32 位")
        }
        if (request.password.length !in 6..32) {
            throw RepositoryException("密码长度需为 6-32 位")
        }
        if (request.password != request.confirmPassword) {
            throw RepositoryException("两次输入的密码不一致")
        }
        if (request.nickname.isBlank()) {
            throw RepositoryException("请填写昵称")
        }
        val phone = request.phone?.trim()
        if (!phone.isNullOrBlank() && !PHONE.matches(phone)) {
            throw RepositoryException("手机号格式不正确")
        }
        val normalized = request.copy(account = request.account.trim(), nickname = request.nickname.trim(), phone = phone)
        val data = ApiCall.requireData { api.register(SessionHolder(address, null), normalized) }
        return data.toSession(address)
    }

    override suspend fun me(session: Session): CurrentUser {
        val user = ApiCall.requireData { api.me(ApiCall.context(session)) }
        if (user.id <= 0) throw RepositoryException("身份信息不完整，请重新登录")
        return user
    }

    override suspend fun health(baseUrl: String): HealthInfo {
        val address = ApiCall.address(baseUrl)
        return try {
            val response = api.health(SessionHolder(address, null))
            if (!response.isSuccessful) throw RepositoryException("服务返回 HTTP ${response.code()}，请检查地址")
            response.body() ?: throw RepositoryException("服务无响应内容，请检查是否为本系统后端")
        } catch (error: RepositoryException) {
            throw error
        } catch (error: java.io.IOException) {
            throw RepositoryException("无法连接 ${address.host}:${address.port}，请检查网络与后端服务")
        }
    }

    private companion object {
        val ACCOUNT = Regex("^[a-zA-Z0-9_]{4,32}$")
        val PHONE = Regex("^1[3-9]\\d{9}$")
    }
}

// ─────────────────────────── 药品 ───────────────────────────

internal class HttpMedicineRepository(private val api: ApiService) : MedicineRepository {

    override suspend fun list(session: Session): List<Medicine> =
        ApiCall.requireData { api.medicines(ApiCall.context(session)) }
            .requireList("药品列表") { it.id }

    override suspend fun detail(session: Session, id: Long): Medicine {
        requirePositiveId(id, "药品")
        val item = ApiCall.requireData { api.medicine(ApiCall.context(session), id) }
        if (item.id != id) throw RepositoryException("返回的药品与请求不一致，请刷新")
        return item
    }

    override suspend fun create(session: Session, request: MedicineRequest): Medicine {
        val valid = request.validated()
        val created = ApiCall.requireData { api.createMedicine(ApiCall.context(session), valid) }
        if (created.id <= 0) throw RepositoryException("服务端未返回新建药品编号，请刷新列表确认")
        return created
    }

    override suspend fun update(session: Session, id: Long, request: MedicineRequest): Medicine {
        requirePositiveId(id, "药品")
        val valid = request.validated()
        val updated = ApiCall.requireData { api.updateMedicine(ApiCall.context(session), id, valid) }
        if (updated.id != id) throw RepositoryException("服务端返回的药品与修改对象不一致，请刷新")
        return updated
    }

    override suspend fun delete(session: Session, id: Long) {
        requirePositiveId(id, "药品")
        ApiCall.ignoreData { api.deleteMedicine(ApiCall.context(session), id) }
    }

    private fun MedicineRequest.validated(): MedicineRequest {
        if (name.isBlank()) throw RepositoryException("请填写药品名称")
        if (name.length > 128) throw RepositoryException("药品名称不能超过 128 个字符")
        return copy(
            name = name.trim(),
            specification = specification?.trim()?.takeIf { it.isNotEmpty() },
            dosage = dosage?.trim()?.takeIf { it.isNotEmpty() },
            unit = unit?.trim()?.takeIf { it.isNotEmpty() },
            remark = remark?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}

// ─────────────────────────── 计划 ───────────────────────────

internal class HttpPlanRepository(private val api: ApiService) : PlanRepository {

    override suspend fun list(session: Session): List<Plan> =
        ApiCall.requireData { api.plans(ApiCall.context(session)) }
            .requireList("计划列表") { it.id }

    override suspend fun detail(session: Session, id: Long): Plan {
        requirePositiveId(id, "计划")
        val plan = ApiCall.requireData { api.plan(ApiCall.context(session), id) }
        if (plan.id != id) throw RepositoryException("返回的计划与请求不一致，请刷新")
        return plan
    }

    override suspend fun create(session: Session, request: PlanRequest): Plan {
        val valid = request.validated()
        val created = ApiCall.requireData { api.createPlan(ApiCall.context(session), valid) }
        if (created.id <= 0) throw RepositoryException("服务端未返回新建计划编号，请刷新列表确认")
        return created
    }

    override suspend fun update(session: Session, id: Long, request: PlanRequest): Plan {
        requirePositiveId(id, "计划")
        val valid = request.validated()
        val updated = ApiCall.requireData { api.updatePlan(ApiCall.context(session), id, valid) }
        if (updated.id != id) throw RepositoryException("服务端返回的计划与修改对象不一致，请刷新")
        return updated
    }

    override suspend fun delete(session: Session, id: Long) {
        requirePositiveId(id, "计划")
        ApiCall.ignoreData { api.deletePlan(ApiCall.context(session), id) }
    }

    override suspend fun pause(session: Session, id: Long): Plan {
        requirePositiveId(id, "计划")
        // 状态流转是写操作：超时时服务端可能已经改了，标为结果未知不能当失败。
        val plan = ApiCall.requireData(outcomeSensitive = true) { api.pausePlan(ApiCall.context(session), id) }
        if (!plan.isPaused) throw RepositoryException("服务端未确认计划已暂停，请刷新核对")
        return plan
    }

    override suspend fun resume(session: Session, id: Long): Plan {
        requirePositiveId(id, "计划")
        val plan = ApiCall.requireData(outcomeSensitive = true) { api.resumePlan(ApiCall.context(session), id) }
        if (!plan.isActive) throw RepositoryException("服务端未确认计划已恢复，请刷新核对")
        return plan
    }

    private fun PlanRequest.validated(): PlanRequest {
        if (medicineId <= 0) throw RepositoryException("请选择药品")
        val start = parseDate(startDate, "开始日期")
        val end = endDate?.takeIf { it.isNotBlank() }?.let { parseDate(it, "结束日期") }
        if (end != null && end.isBefore(start)) throw RepositoryException("结束日期不能早于开始日期")
        if (remindTimes.isEmpty()) throw RepositoryException("请至少添加一个提醒时间")
        if (remindTimes.size > 12) throw RepositoryException("提醒时间最多 12 个")
        val normalizedTimes = remindTimes.map { it.trim() }
        normalizedTimes.forEach {
            if (!CLOCK.matches(it)) throw RepositoryException("提醒时间格式应为 08:00")
        }
        if (normalizedTimes.distinct().size != normalizedTimes.size) {
            throw RepositoryException("提醒时间有重复项，请检查后重试")
        }
        if (dosagePerTime != null && dosagePerTime.signum() <= 0) {
            throw RepositoryException("每次剂量需大于 0")
        }
        return copy(
            startDate = start.toString(),
            endDate = end?.toString(),
            remindTimes = normalizedTimes.sorted(),
            dosageUnit = dosageUnit?.trim()?.takeIf { it.isNotEmpty() },
            remark = remark?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun parseDate(value: String, label: String): LocalDate {
        if (value.isBlank()) throw RepositoryException("请选择$label")
        return try {
            LocalDate.parse(value.trim())
        } catch (error: DateTimeParseException) {
            throw RepositoryException("$label 格式应为 2026-11-19")
        }
    }

    private companion object {
        val CLOCK = Regex("^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$")
    }
}

// ─────────────────────────── 记录 ───────────────────────────

internal class HttpRecordRepository(private val api: ApiService) : RecordRepository {

    override suspend fun list(session: Session): List<MedicationRecord> =
        ApiCall.requireData { api.records(ApiCall.context(session)) }
            .requireList("服药记录") { it.id }
}

// ─────────────────────────── 消息 ───────────────────────────

internal class HttpNotificationRepository(private val api: ApiService) : NotificationRepository {

    override suspend fun page(
        session: Session,
        unreadOnly: Boolean,
        beforeId: Long?,
        limit: Int,
    ): NotificationPage {
        if (limit !in 1..100) throw RepositoryException("每页条数需在 1-100 之间")
        if (beforeId != null && beforeId <= 0) throw RepositoryException("分页参数无效，请下拉刷新")
        val page = ApiCall.requireData {
            api.notifications(ApiCall.context(session), unreadOnly, beforeId, limit)
        }
        // 游标必须严格递减，否则「加载更多」会陷入死循环。
        if (page.hasMore && (page.nextBeforeId == null || page.nextBeforeId >= (beforeId ?: Long.MAX_VALUE))) {
            throw RepositoryException("消息分页数据异常，请下拉刷新")
        }
        return page.copy(items = page.items.requireList("消息列表") { it.id })
    }

    override suspend fun unreadCount(session: Session): Long =
        ApiCall.requireData { api.unreadCount(ApiCall.context(session)) }

    override suspend fun detail(session: Session, id: Long): NotificationItem {
        requirePositiveId(id, "消息")
        val item = ApiCall.requireData { api.notification(ApiCall.context(session), id) }
        if (item.id != id) throw RepositoryException("返回的消息与请求不一致，请刷新")
        return item
    }

    override suspend fun markRead(session: Session, id: Long) {
        requirePositiveId(id, "消息")
        ApiCall.ignoreData { api.readNotification(ApiCall.context(session), id) }
    }

    override suspend fun markAllRead(session: Session): Int =
        ApiCall.requireData { api.readAllNotifications(ApiCall.context(session)) }
}

// ─────────────────────────── 家属 ───────────────────────────

internal class HttpFamilyRepository(private val api: ApiService) : FamilyRepository {

    override suspend fun invite(session: Session, request: InviteRequest): FamilyRelation {
        val relation = ApiCall.requireData { api.invite(ApiCall.context(session), request) }
        if (relation.id <= 0) throw RepositoryException("服务端未返回绑定关系编号，请刷新")
        return relation
    }

    override suspend fun acceptInvite(session: Session, inviteCode: String): FamilyRelation {
        val code = inviteCode.trim()
        if (code.isBlank()) throw RepositoryException("请输入邀请码")
        // 接受绑定会写关系表，超时时可能已经绑上了，按写操作标记结果未知。
        val relation = ApiCall.requireData(outcomeSensitive = true) {
            api.acceptInvite(ApiCall.context(session), AcceptInviteRequest(code))
        }
        if (relation.id <= 0) throw RepositoryException("绑定未生效，请刷新家属列表核对")
        return relation
    }

    override suspend fun relations(session: Session): List<FamilyRelation> =
        ApiCall.requireData { api.relations(ApiCall.context(session)) }
            .requireList("家属关系") { it.id }

    override suspend fun unbind(session: Session, relationId: Long) {
        requirePositiveId(relationId, "绑定关系")
        ApiCall.ignoreData(outcomeSensitive = true) {
            api.unbind(ApiCall.context(session), relationId)
        }
    }

    override suspend fun abnormalEvents(session: Session, includeClosed: Boolean): List<AbnormalEvent> =
        ApiCall.requireData { api.abnormalEvents(ApiCall.context(session), includeClosed) }
            .requireList("异常事件") { it.id }

    override suspend fun acknowledge(session: Session, eventId: Long) {
        requirePositiveId(eventId, "异常事件")
        ApiCall.ignoreData(outcomeSensitive = true) {
            api.acknowledgeAbnormal(ApiCall.context(session), eventId)
        }
    }

    override suspend fun close(session: Session, eventId: Long) {
        requirePositiveId(eventId, "异常事件")
        ApiCall.ignoreData(outcomeSensitive = true) {
            api.closeAbnormal(ApiCall.context(session), eventId)
        }
    }
}

// ─────────────────────────── 统计 ───────────────────────────

internal class HttpStatisticsRepository(private val api: ApiService) : StatisticsRepository {

    override suspend fun calendar(session: Session, startDate: String?, endDate: String?): CalendarStat {
        val start = startDate?.takeIf { it.isNotBlank() }
        val end = endDate?.takeIf { it.isNotBlank() }
        val parsedStart = start?.let { parseDate(it) }
        val parsedEnd = end?.let { parseDate(it) }
        if (parsedStart != null && parsedEnd != null && parsedEnd.isBefore(parsedStart)) {
            throw RepositoryException("结束日期不能早于开始日期")
        }
        return ApiCall.requireData {
            api.calendar(ApiCall.context(session), parsedStart?.toString(), parsedEnd?.toString())
        }
    }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value.trim())
    } catch (error: DateTimeParseException) {
        throw RepositoryException("日期格式应为 2026-11-19")
    }
}
