/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.logic.updater

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.metrolist.music.BuildConfig
import com.metrolist.music.core.R
import com.metrolist.music.constants.CheckForUpdatesKey
import com.metrolist.music.constants.UpdateNotificationsEnabledKey
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.UPDATER_AVAILABLE) return@withContext Result.success()

        val checkForUpdates = applicationContext.dataStore.get(CheckForUpdatesKey, true)
        val notificationsEnabled = applicationContext.dataStore.get(UpdateNotificationsEnabledKey, true)

        if (!checkForUpdates || !notificationsEnabled) {
            return@withContext Result.success()
        }

        Updater.checkForUpdate().onSuccess { (releaseInfo, hasUpdate) ->
            if (hasUpdate && releaseInfo != null) {
                showNotification(releaseInfo.versionName, releaseInfo)
            }
        }

        Result.success()
    }

    private fun showNotification(versionName: String, releaseInfo: com.metrolist.music.utils.ReleaseInfo) {
        val downloadUrl = Updater.getDownloadUrlForCurrentVariant(releaseInfo) ?: return
        val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
        
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(applicationContext, 1001, intent, flags)

        val notification = NotificationCompat.Builder(applicationContext, "updates")
            .setSmallIcon(R.drawable.update)
            .setContentTitle(applicationContext.getString(R.string.update_available_title))
            .setContentText(versionName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext).notify(1001, notification)
        }
    }

    companion object {
        private const val WORK_NAME = "periodic_update_check"

        fun schedule(context: Context) {
            if (!BuildConfig.UPDATER_AVAILABLE) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
        
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
