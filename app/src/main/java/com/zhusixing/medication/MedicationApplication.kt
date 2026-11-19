package com.zhusixing.medication

import android.app.Application
import com.zhusixing.medication.di.AppContainer

class MedicationApplication : Application() {

    /** 全进程唯一的对象图，应用启动时创建。 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
