package com.smartdialer.llm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartdialer.OrbitApplication
import java.util.concurrent.TimeUnit

/** The periodic background refresh of the ranking. Quiet: a failure keeps the last good ranking and retries later. */
class RankingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val granted = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG).all {
            ContextCompat.checkSelfPermission(applicationContext, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) return Result.success()
        val container = (applicationContext as OrbitApplication).container
        return when (container.aiSuggestions.refresh()) {
            is RefreshOutcome.Failed -> if (runAttemptCount < 2) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "orbit-ranking-refresh"

        /** About every [LlmConfig.PERIODIC_REFRESH_HOURS] hours, only with a network connection. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RankingWorker>(LlmConfig.PERIODIC_REFRESH_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
