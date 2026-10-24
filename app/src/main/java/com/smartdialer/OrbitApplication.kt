package com.smartdialer

import android.app.Application
import com.smartdialer.llm.RankingWorker

class OrbitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RankingWorker.schedule(this)
    }
}
