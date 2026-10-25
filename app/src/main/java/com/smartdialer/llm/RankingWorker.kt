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

/**
 * The scheduled background refresh, twice a day. Quiet: a failure keeps the last good ranking and retries a
 * little later; the daily cap applies like for every other trigger.
 */
class RankingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val granted = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG).all {
            ContextCompat.checkSelfPermission(applicationContext, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) return Result.success()
        val container = (applicationContext as OrbitApplication).container
        return when (container.aiSuggestions.refreshScheduled()) {
            is RefreshOutcome.Failed -> if (runAttemptCount < 2) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}

/** Schedules the two daily refreshes: around 07:00 and 18:00 local time, network required. */
object RefreshScheduler {
    const val MORNING_HOUR = 7
    const val EVENING_HOUR = 18
    private const val FLEX_HOURS = 2L

    /** The times shown (read-only) in Settings. */
    const val DESCRIPTION = "Around 07:00 and 18:00"

    /**
     * Each job repeats every 24 hours and may run in a 2-hour window centred on its time (the window is the last
     * 2 hours of each period, so the first period is anchored an hour after the target). [replace] re-anchors both
     * after a time zone or clock change.
     */
    fun schedule(context: Context, replace: Boolean = false, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()) {
        WorkManager.getInstance(context).cancelUniqueWork("orbit-ranking-refresh") // the old six-hourly job
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        for (hour in listOf(MORNING_HOUR, EVENING_HOUR)) {
            val request = PeriodicWorkRequestBuilder<RankingWorker>(24, TimeUnit.HOURS, FLEX_HOURS, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(now, hour), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("orbit-ranking-$hour", policy, request)
        }
    }

    /** Milliseconds from [now] until one hour after the next [hour]:00 local time. */
    fun delayUntil(now: java.time.ZonedDateTime, hour: Int): Long {
        var anchor = now.toLocalDate().atTime(hour, 0).plusHours(1).atZone(now.zone)
        if (!anchor.isAfter(now)) anchor = anchor.plusDays(1)
        return java.time.Duration.between(now, anchor).toMillis()
    }
}

/** Re-anchors the schedule when the time zone or the clock changes. */
class ScheduleChangedReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        RefreshScheduler.schedule(context, replace = true)
    }
}
