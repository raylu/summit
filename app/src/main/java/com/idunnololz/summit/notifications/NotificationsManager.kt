package com.idunnololz.summit.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.idunnololz.summit.BuildConfig
import com.idunnololz.summit.R
import com.idunnololz.summit.account.Account
import com.idunnololz.summit.coroutine.CoroutineScopeFactory
import com.idunnololz.summit.lemmy.inbox.InboxItem
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.util.APP_STARTUP_DELAY_MS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class NotificationsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val coroutineScopeFactory: CoroutineScopeFactory,
) {
    companion object {
        private const val TAG = "NotificationsManager"

        private const val ChannelIdAccount = "account"
    }

    private val coroutineScope = coroutineScopeFactory.create()
    private val workManager = WorkManager.getInstance(context)
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var channelCreated = false

    fun start() {
        coroutineScope.launch {
            delay(APP_STARTUP_DELAY_MS)
            enqueueWorkersIfNeeded()
        }
    }

    fun onPreferencesChanged() {
        enqueueWorkersIfNeeded()
    }

    fun enqueueWorkersIfNeeded() {
        Log.d(TAG, "enqueueWorkersIfNeeded(): ${preferences.isNotificationsOn}")

        if (preferences.isNotificationsOn) {
            enqueueWorkers(0)
        } else {
            cancelWorkers()
        }
    }

    private fun enqueueWorkers(delayMs: Long) {
        if (!preferences.isNotificationsOn) {
            return
        }

        val repeatInterval = max(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, delayMs)
        val flexInterval = PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS

        val constraints = Constraints.Builder()
//            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work =
            PeriodicWorkRequestBuilder<NotificationsWorker>(
                repeatInterval, TimeUnit.MILLISECONDS, // repeatInterval (the period cycle)
                flexInterval, TimeUnit.MILLISECONDS // flexInterval
            )
                .setInitialDelay(repeatInterval, TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .setConstraints(constraints)
                .build()

        workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, work)

        val minuteMs = 1000 * 60
        Log.d(TAG, "Work scheduled to run in ${repeatInterval / minuteMs} minutes, every ${repeatInterval / minuteMs} minutes.")

        printDebugWorkersInfo()
    }

    private fun printDebugWorkersInfo() {
        if (!BuildConfig.DEBUG) return

        val minuteMs = 1000 * 60f

        val infos = workManager.getWorkInfosByTag(TAG).get()
        for (info in infos) {
            Log.d(TAG, "Worker ${info.id}")
            Log.d(TAG, "Work in ${(info.nextScheduleTimeMillis - System.currentTimeMillis()) / minuteMs} minutes")
        }
    }

    private fun cancelWorkers() {
        Log.d(TAG, "all workers cancelled")
        workManager.cancelAllWorkByTag(TAG)
    }

    private fun createNotificationChannelIfNeeded() {
        if (channelCreated) {
            return
        }

        channelCreated = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.account_notifications)
            val descriptionText = context.getString(R.string.account_notifications_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(ChannelIdAccount, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotificationsForItems(account: Account, newItems: List<InboxItem>) {
        createNotificationChannelIfNeeded()

        newItems.forEach {
            var title: String = it.title
            var body: String = it.content
            var authorAvatar: String? = it.authorAvatar

            when (it) {
                is InboxItem.MentionInboxItem -> {
                    title = it.title
                }
                is InboxItem.MessageInboxItem -> {
                    title = it.title
                }
                is InboxItem.ReplyInboxItem -> {
                    title = it.title
                }
                is InboxItem.ReportCommentInboxItem -> {

                }
                is InboxItem.ReportMessageInboxItem -> {

                }
                is InboxItem.ReportPostInboxItem -> {

                }
            }

//            val newMessageNotification = NotificationCompat.Builder(context, ChannelIdAccount)
//                .setSmallIcon(R.drawable.ic_logo_mono_24)
//                .setContentTitle(title)
//                .setContentText(body)
//                .setLargeIcon()
//                .setGroup(GROUP_KEY_WORK_EMAIL)
//                .build()
        }
    }
}