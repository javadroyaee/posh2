package com.jrweb.wcmonitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.work.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PollingService : Service() {

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var task: ScheduledFuture<*>? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = PrefsManager(this)
        startForeground(101, NotificationHelper.buildForegroundNotification(this, prefs.siteName))

        if (task == null || task?.isCancelled == true) {
            task = scheduler.scheduleAtFixedRate({
                pollNow(this)
            }, 2, 15, TimeUnit.SECONDS)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        task?.cancel(true)
        scheduler.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_NEW_EVENTS = "com.jrweb.wcmonitor.NEW_EVENTS"

        fun start(context: Context) {
            val prefs = PrefsManager(context)
            if (!prefs.isConfigured()) return
            val intent = Intent(context, PollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            val work = PeriodicWorkRequestBuilder<BackupPollWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "jrweb_backup_poll",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        fun pollNow(context: Context) {
            val prefs = PrefsManager(context)
            if (!prefs.isConfigured()) return

            val lastId = prefs.lastEventId
            val newEvents = ApiClient.fetchEvents(prefs.siteUrl, prefs.apiKey, lastId)
            if (newEvents.isNotEmpty()) {
                val existing = prefs.getSavedEvents()
                val existingIds = existing.map { it.id }.toSet()
                var maxId = lastId

                for (ev in newEvents) {
                    if (ev.id > maxId) maxId = ev.id
                    if (!existingIds.contains(ev.id)) {
                        existing.add(0, ev)
                        if (lastId > 0) {
                            NotificationHelper.showEventNotification(context, ev)
                        }
                    }
                }
                prefs.lastEventId = maxId
                prefs.saveEvents(existing)
                context.sendBroadcast(Intent(ACTION_NEW_EVENTS))
            }
        }
    }
}

class BackupPollWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        PollingService.pollNow(applicationContext)
        return Result.success()
    }
}
