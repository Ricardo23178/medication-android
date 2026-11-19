package com.zhusixing.medication.ui.navigation

/**
 * 全应用的页面清单。
 *
 * 用 sealed interface 而不是字符串路由：跳转时参数的类型和必填性在编译期就能检查，
 * 拼错一个页面名不会拖到运行期才崩。
 *
 * 列表页与编辑页共用同一个路由（medicineId 为 null 是新增，非空是编辑），
 * 因为两者的表单、校验、提交逻辑完全一致，拆成两个页面只会出现两份要同步修改的代码。
 */
sealed interface AppRoute {

    /** 登录。冷启动无本地会话时的第一页。 */
    data object Login : AppRoute

    /**
     * 注册。从登录页进入，成功后直接成为登录态。
     *
     * 带上登录页已经填好的服务器地址，避免让用户再输一遍同样的东西。
     */
    data class Register(val server: String) : AppRoute

    /** 主框架：底部五个 Tab。 */
    data object Home : AppRoute

    /** 二级页：药品新增 / 编辑。 */
    data class MedicineEdit(val medicineId: Long? = null) : AppRoute

    /** 二级页：计划新增 / 编辑。 */
    data class PlanEdit(val planId: Long? = null) : AppRoute

    /** 二级页：服药记录。 */
    data object RecordList : AppRoute

    /** 二级页：消息列表。 */
    data object NotificationList : AppRoute

    /** 二级页：消息详情。 */
    data class NotificationDetail(val id: Long) : AppRoute

    /** 二级页：家属异常事件。 */
    data object FamilyAbnormal : AppRoute

    /** 二级页：用药统计。 */
    data object Statistics : AppRoute
}
