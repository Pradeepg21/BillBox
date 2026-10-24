package com.billbox.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.billbox.app.BillBoxApp
import com.billbox.app.MainActivity
import com.billbox.app.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Runs once a day (around 9 am) and posts any warranty, due-date, return and budget reminders. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BillBoxApp).container
        val snap = container.repository.snapshot()
        val reminders = ReminderLogic.due(LocalDate.now(), snap.products, snap.recurring, snap.bills, snap.categories)
        val sent = container.settings.sentReminderKeys()
        val fresh = reminders.filter { it.key !in sent }
        if (fresh.isEmpty()) return Result.success()

        val canPost = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (canPost) {
            // At most 3 separate notifications a day; the rest go into one summary.
            fresh.take(3).forEach { post(it.key.hashCode(), it.title, it.body) }
            if (fresh.size > 3) {
                post(SUMMARY_ID, "${fresh.size - 3} more reminders", fresh.drop(3).joinToString("\n") { it.title })
            }
        }
        container.settings.markRemindersSent(fresh.map { it.key })
        return Result.success()
    }

    private fun post(id: Int, title: String, body: String) {
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        private const val SUMMARY_ID = 9_999
        private const val WORK_NAME = "daily-reminders"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Warranty expiry, bill due dates, return windows and budgets"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(9, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Checks immediately, e.g. right after the user saves a bill that is due today. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }
    }
}
