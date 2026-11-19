package com.jrweb.wcmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ALERTS = "jrweb_wc_alerts"
    const val CHANNEL_SERVICE = "jrweb_wc_service"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "هشدارها و سفارش‌های فروشگاه",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اعلان خرید جدید، کمبود موجودی و گزارش‌های سایت"
                enableVibration(true)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "سرویس اتصال زنده فروشگاه",
                NotificationManager.IMPORTANCE_LOW
            )

            nm.createNotificationChannel(alertChannel)
            nm.createNotificationChannel(serviceChannel)
        }
    }

    fun buildForegroundNotification(context: Context, siteName: String): Notification {
        createChannels(context)
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("JRweb مانیتور فعال است")
            .setContentText("در حال پایش لحظه‌ای: ")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    fun showEventNotification(context: Context, event: StoreEvent) {
        createChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, event.id, intent, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(event.title)
            .setContentText(event.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(event.id + 1000, notif)
    }
}
