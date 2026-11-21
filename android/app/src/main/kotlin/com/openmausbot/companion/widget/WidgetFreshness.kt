package com.openmausbot.companion.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import java.util.concurrent.TimeUnit

/** Redraws the widget when its content crosses an age line: answers off at 10 minutes, stale label at 15. */
class WidgetFreshnessWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UpdatesWidget().updateAll(applicationContext)
        return Result.success()
    }
}

fun interface WidgetFreshnessScheduler {
    fun schedule(writtenAt: Long, now: Long)
}

class WorkManagerFreshness(private val context: Context) : WidgetFreshnessScheduler {
    override fun schedule(writtenAt: Long, now: Long) {
        enqueue(ANSWERS_OFF, writtenAt + WidgetSnapshot.ANSWER_MAXIMUM_AGE_MS + 1_000, now)
        enqueue(STALE, writtenAt + WidgetSnapshot.STALE_AFTER_MS + 1_000, now)
    }

    private fun enqueue(name: String, at: Long, now: Long) {
        val request = OneTimeWorkRequestBuilder<WidgetFreshnessWorker>()
            .setInitialDelay((at - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val ANSWERS_OFF = "widget-answers-off"
        const val STALE = "widget-stale"
    }
}
