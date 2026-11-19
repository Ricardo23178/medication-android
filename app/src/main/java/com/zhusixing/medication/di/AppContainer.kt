package com.zhusixing.medication.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.zhusixing.medication.BuildConfig
import com.zhusixing.medication.data.ApiService
import com.zhusixing.medication.data.AuthRepository
import com.zhusixing.medication.data.DataStoreSessionStore
import com.zhusixing.medication.data.FamilyRepository
import com.zhusixing.medication.data.HttpAuthRepository
import com.zhusixing.medication.data.HttpFamilyRepository
import com.zhusixing.medication.data.HttpMedicineRepository
import com.zhusixing.medication.data.HttpMedicationRepository
import com.zhusixing.medication.data.HttpNotificationRepository
import com.zhusixing.medication.data.HttpPlanRepository
import com.zhusixing.medication.data.HttpRecordRepository
import com.zhusixing.medication.data.HttpStatisticsRepository
import com.zhusixing.medication.data.MedicationRepository
import com.zhusixing.medication.data.MedicineRepository
import com.zhusixing.medication.data.NetworkFactory
import com.zhusixing.medication.data.NotificationRepository
import com.zhusixing.medication.data.PlanRepository
import com.zhusixing.medication.data.RecordRepository
import com.zhusixing.medication.data.SessionStore
import com.zhusixing.medication.data.StatisticsRepository
import com.zhusixing.medication.ui.session.SessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * 进程内的对象图。
 *
 * 为什么不用 Hilt：本工程使用 AGP 9.3.1 的内置 Kotlin 支持，而 kapt 与该特性不兼容，
 * KSP 目前也没有对应 Kotlin 2.4 的版本。要么把整个构建退回旧的 built-in Kotlin=false 路径
 * （会连带影响 compileSdk 的新 DSL），要么用几十行代码把依赖组装清楚——这里选后者。
 *
 * 手写容器和 Hilt 解决的是同一件事：谁负责创建对象、谁持有唯一实例。
 * 区别只在"编译期生成"与"运行期拼装"。测试时把这里换成替身即可。
 *
 * OkHttp、Retrofit 与 ApiService 都是进程内单例：连接池、线程池、拦截器链全局共用一份，
 * 每个页面各自 new 一个会白白多出几套线程和连接。
 */
class AppContainer(context: Context) {

    val sessionStore: SessionStore = DataStoreSessionStore(context.sessionDataStore)

    /** 登录态的唯一持有者，跨所有页面共享。 */
    val sessionManager: SessionManager = SessionManager(sessionStore)

    private val okHttpClient: OkHttpClient =
        NetworkFactory.okHttpClient(debugLogging = BuildConfig.DEBUG)

    private val retrofit: Retrofit = NetworkFactory.retrofit(okHttpClient)

    private val api: ApiService = retrofit.create(ApiService::class.java)

    /**
     * 早期的「今日流程」仓储，只含登录 / 今日任务 / 确认三个方法。
     * 保留它是为了让已有测试继续有效；新页面一律用下面按领域拆开的仓储。
     */
    val medicationRepository: MedicationRepository = HttpMedicationRepository(retrofit)

    val authRepository: AuthRepository = HttpAuthRepository(api)
    val medicineRepository: MedicineRepository = HttpMedicineRepository(api)
    val planRepository: PlanRepository = HttpPlanRepository(api)
    val recordRepository: RecordRepository = HttpRecordRepository(api)
    val notificationRepository: NotificationRepository = HttpNotificationRepository(api)
    val familyRepository: FamilyRepository = HttpFamilyRepository(api)
    val statisticsRepository: StatisticsRepository = HttpStatisticsRepository(api)
}
