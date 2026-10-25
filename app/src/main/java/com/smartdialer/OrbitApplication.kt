package com.smartdialer

import android.app.Application
import com.smartdialer.llm.RefreshScheduler

class OrbitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RefreshScheduler.schedule(this)
    }
}
