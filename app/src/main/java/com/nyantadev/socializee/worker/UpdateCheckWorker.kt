package com.nyantadev.socializee.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nyantadev.socializee.BuildConfig
import com.nyantadev.socializee.utils.UpdateChecker
import com.nyantadev.socializee.utils.UpdateNotificationHelper

class UpdateCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val info = UpdateChecker.check(BuildConfig.VERSION_NAME)
            if (info != null) {
                UpdateNotificationHelper.showUpdateNotification(context, info)
            }
            Result.success()
        } catch (e: Exception) {
            // Retry nanti kalau gagal (misal tidak ada internet)
            Result.retry()
        }
    }
}